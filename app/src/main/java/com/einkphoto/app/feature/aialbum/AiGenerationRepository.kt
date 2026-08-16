package com.einkphoto.app.feature.aialbum

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceMediaDisplayProfile
import com.einkphoto.app.core.device.DeviceMediaUploadRequest
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DisplayProfile
import com.einkphoto.app.core.device.DownloadedFile
import com.einkphoto.app.feature.localalbum.data.LocalDraftRequest
import com.einkphoto.app.feature.localalbum.data.createLocalDraft
import com.einkphoto.app.feature.localalbum.model.AdaptationSettings
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * App-owned photo-style flow: Seedream receives the phone-prepared JPEG, while the ESP receives
 * only the final validated six-color BIN for its AI media library.
 */
class AiGenerationRepository(
    private val context: Context,
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
    private val historyStore: AiGenerationHistoryStore = AiGenerationHistoryStore(context.applicationContext),
    private val previewDirectory: File = historyStore.previewDirectory,
) {
    private val directClient = SeedreamDirectClient(context.applicationContext)
    fun preparePhotoStyleReference(uri: Uri): Result<PhotoStyleReferencePreprocessor.PreparedReference> =
        PhotoStyleReferencePreprocessor(context.applicationContext).prepare(uri)

    suspend fun createDirectPreview(prompt: String, historyId: String): Result<AiGenerationPreview> = runCatching {
        val normalized = prompt.trim()
        require(normalized.isNotEmpty()) { "prompt_required" }
        require(normalized.length <= MAX_PROMPT_CHARS) { "prompt_too_long" }
        AiGenerationCoordinator.run(historyId) {
            saveDirectPreview(normalized, historyId, directClient.generate(normalized).getOrThrow())
        }
    }

    suspend fun createDirectPhotoStylePreview(prompt: String, historyId: String, reference: PhotoStyleReferencePreprocessor.PreparedReference): Result<AiGenerationPreview> = runCatching {
        AiGenerationCoordinator.run(historyId) {
            val url = directClient.generate(prompt.trim(), reference.file).getOrThrow()
            saveDirectPreview(prompt, historyId, url)
        }
    }

    private suspend fun saveDirectPreview(prompt: String, historyId: String, url: String): AiGenerationPreview {
        previewDirectory.mkdirs()
        val file = directClient.download(url, File(previewDirectory, "$historyId.seedream.jpg")).getOrThrow()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "preview_decode_failed" }
        require(bounds.outWidth <= MAX_IMAGE_EDGE && bounds.outHeight <= MAX_IMAGE_EDGE &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) { "seedream_image_too_large" }
        return AiGenerationPreview("app-$historyId", prompt, Uri.fromFile(file).toString(), "image/jpeg", file.length())
    }

    suspend fun job(jobId: String): Result<AiGenerationJob> {
        if (!isSafeJobId(jobId)) return Result.failure(IllegalArgumentException("invalid_generation_job"))
        return client.get("/api/v1/jobs/$jobId").mapCatching { root ->
            val data = root.optJSONObject("data") ?: error("invalid_generation_status")
            AiGenerationJob(
                jobId = data.optString("job_id", jobId).takeIf(::isSafeJobId) ?: error("invalid_generation_job"),
                state = data.opt("state")?.toString().orEmpty(),
                phase = data.optString("phase").lowercase(),
                progressPercent = data.optInt("progress_percent", 0).coerceIn(0, 100),
                errorCode = data.optString("error_code").takeIf(String::isNotBlank),
                mediaId = data.optString("media_id")
                    .ifBlank { data.optJSONObject("result")?.optString("media_id").orEmpty() }
                    .takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Device-owned recovery point. It deliberately returns only the device's
     * redacted task summary, never a provider response, credential or full
     * original prompt.
     */
    @Deprecated("ESP AI generation was removed; generation is App-owned.")
    suspend fun activeJob(): Result<AiGenerationActiveTask?> = Result.success(null)

    @Deprecated("ESP AI generation was removed; generation is App-owned.")
    suspend fun lastTaskDiagnostic(): Result<AiGenerationLastTaskDiagnostic?> = Result.success(null)

    @Deprecated("ESP AI generation was removed; previews are App-private.")
    suspend fun downloadPreview(jobId: String): Result<AiGenerationPreviewFile> =
        Result.failure(IllegalStateException("legacy_device_generation_removed"))

    /** Converts the App-private generated image, then uploads only its final BIN as `category=ai`. */
    suspend fun confirmSave(preview: AiGenerationPreview, historyId: String): Result<String> = runCatching {
        val previewFile = File(requireNotNull(Uri.parse(preview.uri).path)).canonicalFile
        val previewRoot = previewDirectory.canonicalFile.path + File.separator
        require(previewFile.path.startsWith(previewRoot) && previewFile.isFile) { "preview_unavailable" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(previewFile.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "preview_decode_failed" }
        val source = PhoneSource(
            sourceId = "ai-$historyId".take(64),
            contentUri = Uri.fromFile(previewFile).toString(),
            displayName = "照片风格转换",
            widthPx = bounds.outWidth,
            heightPx = bounds.outHeight,
        )
        val draft = createLocalDraft(
            context = context.applicationContext,
            request = LocalDraftRequest(
                source = source,
                settings = AdaptationSettings(isConfigured = true),
                profile = AI_DISPLAY_PROFILE,
            ),
        )
        val bin = File(requireNotNull(Uri.parse(draft.candidateBinUri).path)).canonicalFile
        require(bin.isFile && bin.length() == AI_DISPLAY_PROFILE.frameBytes.toLong()) { "bin_conversion_failed" }
        val request = DeviceMediaUploadRequest(
            requestId = "ai-save-$historyId".take(64),
            category = DeviceMediaCategory.Ai,
            displayName = "AI 图片",
            imageBinFile = bin,
            imageBinSizeBytes = bin.length(),
            imageBinSha256 = sha256(bin),
            displayProfile = DeviceMediaDisplayProfile(
                widthPx = AI_DISPLAY_PROFILE.widthPx,
                heightPx = AI_DISPLAY_PROFILE.heightPx,
                frameBytes = AI_DISPLAY_PROFILE.frameBytes,
                pixelFormat = "4bpp",
                palette = "six_color_e6",
                orientation = "landscape",
                rotationDegrees = 0,
                fitMode = "cover",
                converterVersion = draft.algorithmVersion,
            ),
        )
        val upload = client.uploadBinOnly(request)
        // The ESP's multipart reader can reject one interrupted LAN request before it starts
        // the TF transaction. Reusing the same id is idempotent if it actually did admit it.
        val response = if (upload.exceptionOrNull()?.message == "invalid_request") {
            delay(700)
            client.uploadBinOnly(request).getOrThrow()
        } else upload.getOrThrow()
        response.optJSONObject("data")?.optString("job_id")
            ?.takeIf(::isSafeJobId)
            ?: error("invalid_upload_job")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    suspend fun loadHistory(): List<AiGenerationHistoryItem> = historyStore.load()

    suspend fun saveHistory(item: AiGenerationHistoryItem): List<AiGenerationHistoryItem> = historyStore.upsert(item)

    suspend fun clearHistory() = historyStore.clear()

    private fun DownloadedFile.toPreviewFile(file: File): AiGenerationPreviewFile =
        AiGenerationPreviewFile(Uri.fromFile(file).toString(), mimeType, sizeBytes)

    private fun requestId(kind: String): String = "ai-$kind-${UUID.randomUUID()}"

    private fun isSafeJobId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,64}"))

    private companion object {
        const val MAX_PROMPT_CHARS = 500
        const val MAX_IMAGE_EDGE = 10_000
        const val MAX_IMAGE_PIXELS = 40_000_000L
        val AI_DISPLAY_PROFILE = DisplayProfile(
            widthPx = 800,
            heightPx = 480,
            frameBytes = 192_000,
            palette = listOf("black", "white", "green", "blue", "red", "yellow"),
            orientationKey = "landscape",
        )
    }
}

data class AiGenerationPreviewFile(val uri: String, val mimeType: String, val sizeBytes: Long)

data class AiGenerationActiveTask(
    val jobId: String,
    val phase: String,
    val kind: String,
    val promptSummary: String,
)

data class AiGenerationJob(
    val jobId: String,
    val state: String,
    val phase: String,
    val progressPercent: Int,
    val errorCode: String?,
    val mediaId: String?,
) {
    val inProgress: Boolean get() = state in setOf("0", "1", "queued", "running", "preparing", "refreshing", "finalizing")
    val completed: Boolean get() = state in setOf("2", "success", "completed")
}
