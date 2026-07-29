package com.einkphoto.app.feature.localalbum.data

import android.content.Context
import android.net.Uri
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceTransport
import com.einkphoto.app.core.device.LanTransportResult
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.DisplayResult
import com.einkphoto.app.feature.localalbum.model.MediaAvailability
import com.einkphoto.app.feature.localalbum.model.MediaCategory
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/**
 * Read-only real-device adapter for the local-album overview and device gallery.
 *
 * It obtains committed media and device-owned source images only through the versioned LAN
 * transport. It deliberately does not implement upload, display, previous/next, delete, or
 * playback writes; those operations remain unavailable until their own verified phase.
 */
class LanLocalAlbumReadRepository(
    context: Context,
    private val transport: LanDeviceTransport = HttpLanDeviceTransport(),
) : MediaRepository, PlaybackRepository, DisplayRepository {
    private val mutableMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    override val media: StateFlow<List<MediaItem>> = mutableMedia.asStateFlow()

    private val mutableCurrentDisplay = MutableStateFlow(
        CurrentDisplay(null, DeviceFeature.LocalAlbum, DisplayResult.Idle, null),
    )
    override val currentDisplay: StateFlow<CurrentDisplay> = mutableCurrentDisplay.asStateFlow()

    private val mutableSettings = MutableStateFlow(PlaybackSettings(PlayMode.Auto, PlayOrder.Sequential, 30))
    override val settings: StateFlow<PlaybackSettings> = mutableSettings.asStateFlow()

    private val mutableActiveJob = MutableStateFlow<DeviceJob?>(null)
    override val activeJob: StateFlow<DeviceJob?> = mutableActiveJob.asStateFlow()

    private val sourceCacheDirectory = File(context.cacheDir, "device-media-source").apply { mkdirs() }
    private val refreshMutex = Mutex()
    private val displayRequestMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun refresh(): DeviceCommandResult<Unit> = refreshMutex.withLock {
        val page = when (val result = listMediaWithRetry()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> {
                // A constrained ESP HTTP server may time out while completing another response.
                // Keep the last authoritative snapshot visible rather than turning the gallery
                // into an empty state after a transient refresh failure.
                return@withLock if (mutableMedia.value.isNotEmpty()) {
                    DeviceCommandResult.Accepted(Unit)
                } else {
                    DeviceCommandResult.Rejected(result.rejection)
                }
            }
        }
        val status = when (val result = transport.status()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(result.rejection)
        }

        // List is the authoritative gallery order. Detail is re-read for each item so that the
        // image name, source size and display profile are never inferred from cache state.
        val resolved = buildList {
            page.items.forEach { listItem ->
                val detailed = when (val detail = transport.mediaDetail(listItem.mediaId)) {
                    is LanTransportResult.Success -> detail.value
                    is LanTransportResult.Failure -> listItem
                }
                add(detailed.toLocalMediaItem(downloadSourcePreview(detailed.mediaId, detailed.source.present, detailed.source.mimeType)))
            }
        }.toMutableList()
        // A paged gallery may not include an older currently displayed item. Fetch it explicitly
        // so the homepage always follows the device authority rather than a page-local guess.
        status.currentMediaId?.takeIf { currentId -> resolved.none { it.id.value == currentId } }?.let { currentId ->
            when (val currentDetail = transport.mediaDetail(currentId)) {
                is LanTransportResult.Success -> {
                    val detail = currentDetail.value
                    resolved += detail.toLocalMediaItem(downloadSourcePreview(detail.mediaId, detail.source.present, detail.source.mimeType))
                }
                is LanTransportResult.Failure -> Unit
            }
        }
        mutableMedia.value = resolved
        mutableCurrentDisplay.value = CurrentDisplay(
            mediaId = status.currentMediaId?.let(::MediaId),
            feature = status.activeFeature,
            result = when {
                status.displayBusy -> DisplayResult.Refreshing
                status.currentMediaId != null -> DisplayResult.Success
                else -> DisplayResult.Idle
            },
            lastSuccessfulRefreshEpochMillis = null,
        )
        DeviceCommandResult.Accepted(Unit)
    }

    private suspend fun listMediaWithRetry(): LanTransportResult<com.einkphoto.app.core.device.DeviceMediaPage> {
        var lastFailure: LanTransportResult.Failure = LanTransportResult.Failure(DeviceRejection.Offline)
        repeat(3) { attempt ->
            when (val result = transport.listMedia(limit = 30)) {
                is LanTransportResult.Success -> return result
                is LanTransportResult.Failure -> {
                    lastFailure = result
                    if (attempt < 2) delay((attempt + 1) * 400L)
                }
            }
        }
        return lastFailure
    }

    override suspend fun delete(mediaId: MediaId): DeviceCommandResult<Unit> {
        if (mutableCurrentDisplay.value.mediaId == mediaId) return DeviceCommandResult.Rejected(DeviceRejection.MediaProtected)
        val item = mutableMedia.value.firstOrNull { it.id == mediaId }
            ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        return when (val result = transport.deleteMedia(
            mediaId = mediaId.value,
            requestId = "delete-${mediaId.value}-${System.currentTimeMillis()}",
            expectedRevision = item.revision,
        )) {
            is LanTransportResult.Success -> {
                // Re-read the authoritative index after an accepted deletion. A transient list
                // read failure must not make the already-completed device deletion look failed.
                if (refresh() is DeviceCommandResult.Rejected) {
                    mutableMedia.value = mutableMedia.value.filterNot { it.id == mediaId }
                }
                DeviceCommandResult.Accepted(Unit)
            }
            is LanTransportResult.Failure -> DeviceCommandResult.Rejected(result.rejection)
        }
    }

    override suspend fun save(settings: PlaybackSettings): DeviceCommandResult<Unit> =
        DeviceCommandResult.Rejected(DeviceRejection.Unsupported)

    override suspend fun requestDisplay(mediaId: MediaId, afterDisplay: AfterDisplay): DeviceCommandResult<DeviceJobId> =
        displayRequestMutex.withLock {
            // Status is read immediately before submission so expected_mode_revision and busy
            // are device-authoritative rather than inherited from a stale UI snapshot.
            val status = when (val result = transport.status()) {
                is LanTransportResult.Success -> result.value
                is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
            }
            if (status.activeFeature != DeviceFeature.LocalAlbum) {
                return@withLock DeviceCommandResult.Rejected(DeviceRejection.FeatureNotActive)
            }
            if (status.displayBusy) return@withLock DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)

            val requestId = "display-${mediaId.value}-${System.currentTimeMillis()}"
            val job = when (val result = transport.displayMedia(
                mediaId = mediaId.value,
                requestId = requestId,
                expectedModeRevision = status.modeRevision,
                afterDisplay = if (afterDisplay == AfterDisplay.Hold) "hold" else "continue",
            )) {
                is LanTransportResult.Success -> result.value
                is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
            }
            mutableActiveJob.value = job.toDeviceJob()
            mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(
                feature = status.activeFeature,
                result = DisplayResult.Refreshing,
            )
            repositoryScope.launch { awaitDisplayTerminalJob(job) }
            DeviceCommandResult.Accepted(job.jobId)
        }

    /** Polls the asynchronous 202 job without holding the request mutex or guessing success. */
    private suspend fun awaitDisplayTerminalJob(initialJob: com.einkphoto.app.core.device.DeviceJobSnapshot) {
        var attempt = 0
        while (attempt < 60) {
            when (val response = transport.jobStatus(initialJob.jobId)) {
                is LanTransportResult.Success -> {
                    val job = response.value
                    mutableActiveJob.value = job.toDeviceJob()
                    if (job.state in terminalJobStates) {
                        if (job.state == DeviceJobState.Success) {
                            // The job result alone is not the homepage state. Refresh media and
                            // /device/status, then select current_media_id and its source preview.
                            when (refresh()) {
                                is DeviceCommandResult.Accepted -> mutableActiveJob.value = null
                                is DeviceCommandResult.Rejected -> mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Failed)
                            }
                        } else {
                            mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Failed)
                        }
                        return
                    }
                }
                is LanTransportResult.Failure -> {
                    mutableActiveJob.value = DeviceJob(initialJob.jobId, "display_local_media", DeviceJobState.Failed, "无法读取显示任务状态")
                    mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Failed)
                    return
                }
            }
            attempt += 1
            delay(1_000L)
        }
        mutableActiveJob.value = DeviceJob(initialJob.jobId, "display_local_media", DeviceJobState.TimedOut, "等待电子纸刷新超时")
        mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Failed)
    }

    private fun com.einkphoto.app.core.device.DeviceJobSnapshot.toDeviceJob(): DeviceJob = DeviceJob(
        id = jobId,
        kind = "display_local_media",
        state = state,
        message = errorCode ?: phase.ifBlank { "等待设备刷新" },
    )

    private suspend fun downloadSourcePreview(mediaId: String, sourcePresent: Boolean, mimeType: String?): String? {
        // `source.present` is the admission authority. The source endpoint supplies the final
        // Content-Type, so a missing/older MIME metadata field must not suppress its thumbnail.
        if (!sourcePresent) return null
        val extension = if (mimeType == "image/png") "png" else "jpg"
        val destination = File(sourceCacheDirectory, "$mediaId.$extension")
        if (destination.isFile && destination.length() > 0L) return Uri.fromFile(destination).toString()
        return when (transport.downloadMediaSource(mediaId, destination)) {
            is LanTransportResult.Success -> Uri.fromFile(destination).toString()
            is LanTransportResult.Failure -> null
        }
    }

    private fun com.einkphoto.app.core.device.DeviceMediaItem.toLocalMediaItem(previewUri: String?): MediaItem = MediaItem(
        id = MediaId(mediaId),
        category = when (category) {
            com.einkphoto.app.core.device.DeviceMediaCategory.Local -> MediaCategory.Local
            com.einkphoto.app.core.device.DeviceMediaCategory.Ai -> MediaCategory.Ai
            com.einkphoto.app.core.device.DeviceMediaCategory.Dashboard -> MediaCategory.Dashboard
            com.einkphoto.app.core.device.DeviceMediaCategory.System -> MediaCategory.System
        },
        displayName = displayName,
        previewUri = previewUri,
        // The frozen API does not expose decoded source dimensions. Do not manufacture them:
        // these are only the device display profile used for layout until source metadata grows.
        sourceWidthPx = displayProfile.widthPx,
        sourceHeightPx = displayProfile.heightPx,
        sizeBytes = source.sizeBytes ?: 0L,
        availability = MediaAvailability.Ready,
        createdAtEpochMillis = createdAtEpochMillis,
        revision = revision,
    )

    private companion object {
        val terminalJobStates = setOf(DeviceJobState.Success, DeviceJobState.Failed, DeviceJobState.Cancelled, DeviceJobState.TimedOut)
    }
}
