package com.einkphoto.app.feature.aialbum

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobSnapshot
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceMediaItem
import com.einkphoto.app.core.device.DeviceMediaPreviewCache
import com.einkphoto.app.core.device.DeviceModeState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceSession
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceTransport
import com.einkphoto.app.core.device.LanTransportResult
import com.einkphoto.app.core.device.MAX_MEDIA_SOURCE_BYTES
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LanAiImageRepository(
    private val context: Context,
    private val session: DeviceSession,
    private val transport: LanDeviceTransport = HttpLanDeviceTransport(),
    private val previewCache: DeviceMediaPreviewCache = DeviceMediaPreviewCache(context),
) : AiImageRepository {
    private val appContext = context.applicationContext
    private val mutableImages = MutableStateFlow<List<AiImageItem>>(emptyList())
    override val images: StateFlow<List<AiImageItem>> = mutableImages.asStateFlow()

    private val mutableActiveJob = MutableStateFlow<DeviceJob?>(null)
    override val activeJob: StateFlow<DeviceJob?> = mutableActiveJob.asStateFlow()

    private val mutableHasMore = MutableStateFlow(false)
    override val hasMore: StateFlow<Boolean> = mutableHasMore.asStateFlow()
    private var nextCursor: String? = null
    private var listRevision: Long? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val displayMutex = Mutex()
    private val previewJobs = ConcurrentHashMap<String, Job>()

    override suspend fun refresh(): DeviceCommandResult<Unit> = refreshMutex.withLock {
        val page = when (val result = readVerifiedPage(cursor = null)) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
        }
        listRevision = page.revision
        nextCursor = page.nextCursor
        mutableHasMore.value = nextCursor != null
        mutableImages.value = page.items.map { item ->
            item.toAiImage(previewCache.cachedUri(DeviceMediaCategory.Ai, item.mediaId, item.revision))
        }
        page.items.forEach(::schedulePreview)
        DeviceCommandResult.Accepted(Unit)
    }

    override suspend fun loadMore(): DeviceCommandResult<Unit> = refreshMutex.withLock {
        val cursor = nextCursor ?: return@withLock DeviceCommandResult.Accepted(Unit)
        val page = when (val result = readVerifiedPage(cursor)) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
        }
        if (listRevision != null && page.revision != listRevision) {
            // Never combine pages from different authoritative media revisions.
            nextCursor = null
            listRevision = null
            mutableHasMore.value = false
            return@withLock DeviceCommandResult.Rejected(DeviceRejection.RevisionConflict)
        }
        nextCursor = page.nextCursor
        mutableHasMore.value = nextCursor != null
        val additions = page.items.map { item ->
            item.toAiImage(previewCache.cachedUri(DeviceMediaCategory.Ai, item.mediaId, item.revision))
        }
        mutableImages.value = mergeAiImagePages(mutableImages.value, additions)
        page.items.forEach(::schedulePreview)
        DeviceCommandResult.Accepted(Unit)
    }

    override suspend fun display(mediaId: String): DeviceCommandResult<DeviceJobId> = displayMutex.withLock {
        val item = when (val detail = verifiedDetail(mediaId)) {
            is LanTransportResult.Success -> detail.value
            is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(detail.rejection)
        }
        previewJobs.values.forEach { it.cancel() }
        previewJobs.clear()
        val status = when (val result = transport.status()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
        }
        val mode = when (val result = transport.mode()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
        }
        if (mode.activeFeature != DeviceFeature.AiAlbum || mode.state == DeviceModeState.Switching) {
            return@withLock DeviceCommandResult.Rejected(DeviceRejection.FeatureNotActive)
        }
        if (status.displayBusy) return@withLock DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
        val job = when (val result = transport.displayMedia(
            mediaId = item.mediaId,
            requestId = "display-ai-${item.mediaId}-${System.currentTimeMillis()}",
            expectedModeRevision = mode.revision,
            afterDisplay = "hold",
        )) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
        }
        mutableActiveJob.value = job.toDeviceJob()
        scope.launch { awaitDisplay(job) }
        DeviceCommandResult.Accepted(job.jobId)
    }

    override suspend fun delete(mediaId: String): DeviceCommandResult<Unit> {
        val status = when (val result = transport.status()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(result.rejection)
        }
        if (status.displayBusy || mutableActiveJob.value?.state in setOf(DeviceJobState.Queued, DeviceJobState.Running)) {
            return DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
        }
        val mode = when (val result = transport.mode()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(result.rejection)
        }
        if (isProtectedAiMedia(mode.currentContent, mediaId)) return DeviceCommandResult.Rejected(DeviceRejection.MediaProtected)

        val detail = when (val verified = verifiedDetail(mediaId)) {
            is LanTransportResult.Success -> verified.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(verified.rejection)
        }
        return when (val result = transport.deleteMedia(
            mediaId = detail.mediaId,
            requestId = "delete-ai-${detail.mediaId}-${System.currentTimeMillis()}",
            expectedRevision = detail.revision,
        )) {
            is LanTransportResult.Success -> {
                if (refresh() is DeviceCommandResult.Rejected) {
                    mutableImages.value = mutableImages.value.filterNot { it.id == mediaId }
                }
                DeviceCommandResult.Accepted(Unit)
            }
            is LanTransportResult.Failure -> DeviceCommandResult.Rejected(result.rejection)
        }
    }

    override suspend fun exportToPhone(mediaId: String): DeviceCommandResult<AiImageExportResult> =
        runAiExportOnIo { exportToPhoneOnIo(mediaId) }

    /** Keeps detail reads, downloads, hashing, preview decoding and final phone writes off Main. */
    private suspend fun exportToPhoneOnIo(mediaId: String): DeviceCommandResult<AiImageExportResult> {
        val detail = when (val verified = verifiedDetail(mediaId)) {
            is LanTransportResult.Success -> verified.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(verified.rejection)
        }
        val exportDirectory = File(appContext.cacheDir, "ai-media-export").apply { mkdirs() }
        return if (detail.source.present) {
            val declaration = detail.source
            if (!isValidAiSourceDeclaration(declaration.mimeType, declaration.sizeBytes, declaration.sha256)) {
                return DeviceCommandResult.Rejected(
                    if ((declaration.sizeBytes ?: 0L) > MAX_MEDIA_SOURCE_BYTES) DeviceRejection.SourceTooLarge
                    else DeviceRejection.Unsupported,
                )
            }
            val temporary = File(exportDirectory, "${detail.mediaId}-${detail.revision}.source")
            try {
                when (val downloaded = transport.downloadMediaSource(detail.mediaId, temporary)) {
                    is LanTransportResult.Success -> {
                        val actualSha256 = try {
                            sha256(temporary)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
                        }
                        if (!downloadedAiSourceMatches(
                                declaredMimeType = declaration.mimeType,
                                declaredSizeBytes = declaration.sizeBytes,
                                declaredSha256 = declaration.sha256,
                                actualMimeType = downloaded.value.mimeType,
                                actualSizeBytes = downloaded.value.sizeBytes,
                                actualFileSizeBytes = temporary.length(),
                                actualSha256 = actualSha256,
                            )
                        ) return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
                        exportFile(temporary, detail.displayName, normalizeImageMime(declaration.mimeType)!!)?.let {
                            DeviceCommandResult.Accepted(AiImageExportResult(AiImageExportKind.Source, it))
                        } ?: DeviceCommandResult.Rejected(DeviceRejection.StorageUnavailable)
                    }
                    is LanTransportResult.Failure -> DeviceCommandResult.Rejected(downloaded.rejection)
                }
            } finally {
                temporary.delete()
            }
        } else {
            val previewUri = previewCache.load(DeviceMediaCategory.Ai, detail.mediaId, detail.revision, detail.displayProfile)
                ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
            val previewFile = Uri.parse(previewUri).path?.let(::File)?.takeIf { it.isFile }
                ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
            exportFile(previewFile, "${detail.displayName}-六色预览", "image/png")?.let {
                DeviceCommandResult.Accepted(AiImageExportResult(AiImageExportKind.SixColorPreview, it))
            } ?: DeviceCommandResult.Rejected(DeviceRejection.StorageUnavailable)
        }
    }

    private suspend fun readVerifiedPage(cursor: String?): LanTransportResult<VerifiedPage> {
        val page = when (val result = transport.listMedia(DeviceMediaCategory.Ai, cursor, PAGE_SIZE)) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return result
        }
        val verified = mutableListOf<DeviceMediaItem>()
        page.items.forEach { listed ->
            if (listed.category != DeviceMediaCategory.Ai) return@forEach
            when (val detail = verifiedDetail(listed.mediaId)) {
                is LanTransportResult.Success -> verified += detail.value
                is LanTransportResult.Failure -> return detail
            }
        }
        return LanTransportResult.Success(VerifiedPage(verified.distinctBy { it.mediaId }, page.nextCursor, page.revision))
    }

    private suspend fun verifiedDetail(mediaId: String): LanTransportResult<DeviceMediaItem> = when (val result = transport.mediaDetail(mediaId)) {
        is LanTransportResult.Success -> result.value
            .takeIf { isVerifiedAiDetail(mediaId, it) }
            ?.let { LanTransportResult.Success(it) }
            ?: LanTransportResult.Failure(DeviceRejection.Unsupported)
        is LanTransportResult.Failure -> result
    }

    private fun schedulePreview(item: DeviceMediaItem) {
        val key = aiPreviewJobKey(item.mediaId, item.revision)
        if (previewCache.cachedUri(DeviceMediaCategory.Ai, item.mediaId, item.revision) != null) return
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                delay(400L)
                val uri = previewCache.load(DeviceMediaCategory.Ai, item.mediaId, item.revision, item.displayProfile)
                    ?: return@launch
                mutableImages.value = mutableImages.value.map { image ->
                    if (image.id == item.mediaId && image.revision == item.revision) image.copy(previewUri = uri) else image
                }
            } finally {
                previewJobs.remove(key, coroutineContext[Job])
            }
        }
        if (previewJobs.putIfAbsent(key, newJob) == null) newJob.start() else newJob.cancel()
    }

    private suspend fun awaitDisplay(initial: DeviceJobSnapshot) {
        repeat(60) {
            when (val result = transport.jobStatus(initial.jobId)) {
                is LanTransportResult.Success -> {
                    val job = result.value
                    mutableActiveJob.value = job.toDeviceJob()
                    if (job.state in TERMINAL_STATES) {
                        if (job.state == DeviceJobState.Success) {
                            session.refreshSnapshot()
                            refresh()
                        }
                        return
                    }
                }
                is LanTransportResult.Failure -> {
                    mutableActiveJob.value = DeviceJob(initial.jobId, "display_ai_media", DeviceJobState.Failed, "无法读取显示任务状态")
                    return
                }
            }
            delay(1_000L)
        }
        mutableActiveJob.value = DeviceJob(initial.jobId, "display_ai_media", DeviceJobState.TimedOut, "等待电子纸刷新超时")
    }

    private fun DeviceJobSnapshot.toDeviceJob() = DeviceJob(jobId, "display_ai_media", state, errorCode ?: phase.ifBlank { "等待设备刷新" })

    private fun DeviceMediaItem.toAiImage(previewUri: String?) = AiImageItem(
        id = mediaId,
        displayName = displayName,
        createdAtEpochMillis = createdAtEpochMillis,
        sizeBytes = source.sizeBytes ?: imageBin.sizeBytes ?: preview.sizeBytes,
        revision = revision,
        previewUri = previewUri,
        sourceAvailable = source.present,
    )

    private fun exportFile(source: File, requestedName: String, mimeType: String): String? {
        return try {
        val extension = when (mimeType.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val safeRequested = requestedName.replace(Regex("[\\/:*?\"<>|]"), "_").take(80).ifBlank { "AI图片" }
        val displayName = safeRequested.substringBeforeLast('.', safeRequested) + extension
        if (exportStrategyForSdk(Build.VERSION.SDK_INT) == ExportStrategy.LegacyExternalStorage) {
            exportLegacy(source, displayName, mimeType)
        } else {
            // API 26-28 must not evaluate API 29-only MediaStore fields.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/墨水屏相册/AI相册")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("media_store_insert_failed")
            try {
                resolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                    ?: error("media_store_output_failed")
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
            displayName
        }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(16 * 1024).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(source: File, displayName: String, mimeType: String): String {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "墨水屏相册/AI相册").apply { mkdirs() }
        val destination = uniqueFile(directory, displayName)
        source.copyTo(destination, overwrite = false)
        MediaScannerConnection.scanFile(appContext, arrayOf(destination.absolutePath), arrayOf(mimeType), null)
        return destination.name
    }

    private fun uniqueFile(directory: File, displayName: String): File {
        val stem = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var candidate = File(directory, displayName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$stem-$suffix${if (extension.isBlank()) "" else ".$extension"}")
            suffix += 1
        }
        return candidate
    }

    override fun close() {
        previewJobs.values.forEach(Job::cancel)
        previewJobs.clear()
        scope.cancel()
    }

    private companion object {
        const val PAGE_SIZE = 30
        val TERMINAL_STATES = setOf(DeviceJobState.Success, DeviceJobState.Failed, DeviceJobState.Cancelled, DeviceJobState.TimedOut)
    }

    private data class VerifiedPage(val items: List<DeviceMediaItem>, val nextCursor: String?, val revision: Long)
}

internal fun isVerifiedAiDetail(requestedMediaId: String, item: DeviceMediaItem): Boolean =
    requestedMediaId.isNotBlank() && item.mediaId == requestedMediaId && item.category == DeviceMediaCategory.Ai

internal fun isProtectedAiMedia(content: DeviceCurrentContent?, mediaId: String): Boolean =
    mediaId.isNotBlank() && content?.ownerFeature == DeviceFeature.AiAlbum &&
        content.kind == DeviceContentKind.Media && content.category == DeviceMediaCategory.Ai && content.mediaId == mediaId

internal fun mergeAiImagePages(existing: List<AiImageItem>, additions: List<AiImageItem>): List<AiImageItem> =
    (existing + additions).distinctBy { it.id }

internal enum class ExportStrategy { LegacyExternalStorage, MediaStore }

internal fun exportStrategyForSdk(sdk: Int): ExportStrategy =
    if (sdk >= Build.VERSION_CODES.Q) ExportStrategy.MediaStore else ExportStrategy.LegacyExternalStorage

internal fun normalizeImageMime(mimeType: String?): String? = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
    "image/jpeg", "image/jpg" -> "image/jpeg"
    "image/png" -> "image/png"
    else -> null
}

internal suspend fun <T> runAiExportOnIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

internal fun isValidAiSourceDeclaration(mimeType: String?, sizeBytes: Long?, sha256: String?): Boolean =
    normalizeImageMime(mimeType) != null && sizeBytes != null && sizeBytes in 1..MAX_MEDIA_SOURCE_BYTES &&
        sha256?.matches(Regex("[A-Fa-f0-9]{64}")) == true

internal fun downloadedAiSourceMatches(
    declaredMimeType: String?,
    declaredSizeBytes: Long?,
    declaredSha256: String?,
    actualMimeType: String?,
    actualSizeBytes: Long,
    actualFileSizeBytes: Long,
    actualSha256: String,
): Boolean = isValidAiSourceDeclaration(declaredMimeType, declaredSizeBytes, declaredSha256) &&
    normalizeImageMime(declaredMimeType) == normalizeImageMime(actualMimeType) &&
    declaredSizeBytes == actualSizeBytes && actualSizeBytes == actualFileSizeBytes &&
    declaredSha256.equals(actualSha256, ignoreCase = true)

internal fun aiPreviewJobKey(mediaId: String, revision: Long): String =
    "${DeviceMediaCategory.Ai.apiValue}:$mediaId:$revision"
