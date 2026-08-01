package com.einkphoto.app.feature.localalbum.data

import android.content.Context
import android.util.Log
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceMediaPreviewCache
import com.einkphoto.app.core.device.previewCacheFileName
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceTransport
import com.einkphoto.app.core.device.LanTransportResult
import com.einkphoto.app.core.device.PlaybackTransportResult
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.DisplayResult
import com.einkphoto.app.feature.localalbum.model.MediaAvailability
import com.einkphoto.app.feature.localalbum.model.MediaCategory
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.PlaybackSyncState
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
    private val previewClient: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) : MediaRepository, PlaybackRepository, DisplayRepository {
    private val mutableMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    override val media: StateFlow<List<MediaItem>> = mutableMedia.asStateFlow()

    private val mutableCurrentDisplay = MutableStateFlow(
        CurrentDisplay(null, DeviceFeature.LocalAlbum, DisplayResult.Idle, null),
    )
    override val currentDisplay: StateFlow<CurrentDisplay> = mutableCurrentDisplay.asStateFlow()

    private val mutableSettings = MutableStateFlow(
        PlaybackSettings(com.einkphoto.app.feature.localalbum.model.PlayMode.Auto, PlayOrder.Sequential, 1800),
    )
    override val settings: StateFlow<PlaybackSettings> = mutableSettings.asStateFlow()

    private val mutableActiveJob = MutableStateFlow<DeviceJob?>(null)
    override val activeJob: StateFlow<DeviceJob?> = mutableActiveJob.asStateFlow()

    private val previewCache = DeviceMediaPreviewCache(context.applicationContext, previewClient)
    private val refreshMutex = Mutex()
    private val displayRequestMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val previewJobs = ConcurrentHashMap<String, Job>()

    override suspend fun refresh(): DeviceCommandResult<Unit> = refreshMutex.withLock {
        refreshPlayback()
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
        val mode = when (val result = transport.mode()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(result.rejection)
        }
        val localCurrentMediaId = mode.currentContent
            ?.takeIf { it.ownerFeature == DeviceFeature.LocalAlbum && it.category == com.einkphoto.app.core.device.DeviceMediaCategory.Local }
            ?.mediaId

        // List is the authoritative gallery order. Detail is re-read for each item so that the
        // image name, source size and display profile are never inferred from cache state.
        val detailedItems = mutableListOf<com.einkphoto.app.core.device.DeviceMediaItem>()
        val resolved = buildList {
            page.items.forEach { listItem ->
                val detailed = when (val detail = transport.mediaDetail(listItem.mediaId)) {
                    is LanTransportResult.Success -> detail.value
                    is LanTransportResult.Failure -> listItem
                }
                detailedItems += detailed
                add(detailed.toLocalMediaItem(previewCache.cachedUri(detailed.category, detailed.mediaId, detailed.revision)))
            }
        }.toMutableList()
        // A paged gallery may not include an older currently displayed item. Fetch it explicitly
        // so the homepage always follows the device authority rather than a page-local guess.
        localCurrentMediaId?.takeIf { currentId -> resolved.none { it.id.value == currentId } }?.let { currentId ->
            when (val currentDetail = transport.mediaDetail(currentId)) {
                is LanTransportResult.Success -> {
                    val detail = currentDetail.value
                    detailedItems += detail
                    resolved += detail.toLocalMediaItem(previewCache.cachedUri(detail.category, detail.mediaId, detail.revision))
                }
                is LanTransportResult.Failure -> Unit
            }
        }
        mutableMedia.value = resolved
        mutableCurrentDisplay.value = CurrentDisplay(
            mediaId = localCurrentMediaId?.let(::MediaId),
            feature = mode.currentContent?.ownerFeature ?: mode.activeFeature,
            result = when {
                status.displayBusy -> DisplayResult.Refreshing
                mode.currentContent != null -> DisplayResult.Success
                else -> DisplayResult.Idle
            },
            lastSuccessfulRefreshEpochMillis = null,
        )
        // Network download and PNG encoding intentionally happen after the authoritative state
        // is published. A gallery refresh, display request, or navigation never waits on preview
        // generation; each cache miss is processed once in the repository background scope.
        detailedItems.distinctBy { it.mediaId }.forEach(::schedulePreviewDecode)
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

    override suspend fun save(settings: PlaybackSettings): DeviceCommandResult<Unit> {
        val requestId = "playback-${System.currentTimeMillis()}"
        mutableSettings.value = settings.copy(syncState = PlaybackSyncState.Saving)
        return when (val result = transport.saveLocalAlbumPlayback(
            requestId = requestId,
            expectedRevision = settings.revision,
            mode = if (settings.mode == com.einkphoto.app.feature.localalbum.model.PlayMode.Auto) "auto" else "paused",
            intervalSeconds = settings.intervalSeconds,
            order = if (settings.order == PlayOrder.Sequential) "sequential" else "random",
        )) {
            is PlaybackTransportResult.Success -> {
                applyPlaybackSnapshot(result.snapshot, PlaybackSyncState.Ready)
                DeviceCommandResult.Accepted(Unit)
            }
            is PlaybackTransportResult.RevisionConflict -> {
                result.snapshot?.let { mutableSettings.value = it.toPlaybackSettings(PlaybackSyncState.Conflict) }
                    ?: run { mutableSettings.value = settings.copy(syncState = PlaybackSyncState.Conflict) }
                DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
            }
            is PlaybackTransportResult.Failure -> {
                mutableSettings.value = settings.copy(syncState = PlaybackSyncState.Offline)
                DeviceCommandResult.Rejected(result.rejection)
            }
        }
    }

    override suspend fun refreshPlayback(): DeviceCommandResult<Unit> {
        when (val result = transport.localAlbumPlayback()) {
            is PlaybackTransportResult.Success -> {
                applyPlaybackSnapshot(result.snapshot, PlaybackSyncState.Ready)
                return DeviceCommandResult.Accepted(Unit)
            }
            is PlaybackTransportResult.RevisionConflict -> return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
            is PlaybackTransportResult.Failure -> {
                mutableSettings.value = mutableSettings.value.copy(syncState = PlaybackSyncState.Offline)
                return DeviceCommandResult.Rejected(result.rejection)
            }
        }
    }

    private fun com.einkphoto.app.core.device.DevicePlaybackSnapshot.toPlaybackSettings(sync: PlaybackSyncState) = PlaybackSettings(
        mode = if (mode == "auto") com.einkphoto.app.feature.localalbum.model.PlayMode.Auto else com.einkphoto.app.feature.localalbum.model.PlayMode.Paused,
        order = if (order == "sequential") PlayOrder.Sequential else PlayOrder.Random,
        intervalSeconds = intervalSeconds,
        currentMediaId = currentMediaId?.let(::MediaId),
        nextPlayInSeconds = nextPlayInSeconds,
        nextPlayAtEpochMillis = nextPlayAtEpochMillis,
        revision = revision,
        stateRevision = stateRevision,
        syncState = sync,
    )

    /** Playback is the authority after an automatic switch, so keep the overview preview in sync. */
    private fun applyPlaybackSnapshot(
        snapshot: com.einkphoto.app.core.device.DevicePlaybackSnapshot,
        sync: PlaybackSyncState,
    ) {
        mutableSettings.value = snapshot.toPlaybackSettings(sync)
        mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(
            mediaId = snapshot.currentMediaId?.let(::MediaId),
            feature = DeviceFeature.LocalAlbum,
            result = if (snapshot.currentMediaId == null) DisplayResult.Idle else DisplayResult.Success,
        )
    }

    override suspend fun requestDisplay(mediaId: MediaId, afterDisplay: AfterDisplay): DeviceCommandResult<DeviceJobId> =
        displayRequestMutex.withLock {
            // A display action takes priority over optional thumbnail work. Cancellation removes
            // queued cache jobs before they can acquire the shared ESP HTTP request slot.
            previewJobs.values.forEach { it.cancel() }
            previewJobs.clear()
            // Status is read immediately before submission so expected_mode_revision and busy
            // are device-authoritative rather than inherited from a stale UI snapshot.
            val status = when (val result = transport.status()) {
                is LanTransportResult.Success -> result.value
                is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
            }
            val mode = when (val result = transport.mode()) {
                is LanTransportResult.Success -> result.value
                is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
            }
            if (mode.activeFeature != DeviceFeature.LocalAlbum || mode.state == com.einkphoto.app.core.device.DeviceModeState.Switching) {
                return@withLock DeviceCommandResult.Rejected(DeviceRejection.FeatureNotActive)
            }
            if (status.displayBusy) return@withLock DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)

            val requestId = "display-${mediaId.value}-${System.currentTimeMillis()}"
            val job = when (val result = transport.displayMedia(
                mediaId = mediaId.value,
                requestId = requestId,
                expectedModeRevision = mode.revision,
                afterDisplay = if (afterDisplay == AfterDisplay.Hold) "hold" else "continue",
            )) {
                is LanTransportResult.Success -> result.value
                is LanTransportResult.Failure -> return@withLock DeviceCommandResult.Rejected(result.rejection)
            }
            mutableActiveJob.value = job.toDeviceJob()
            mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(
                feature = mode.activeFeature,
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

    private fun schedulePreviewDecode(item: com.einkphoto.app.core.device.DeviceMediaItem) {
        val mediaId = item.mediaId
        val key = previewCacheFileName(item.category.name, mediaId, item.revision)
        if (previewCache.cachedUri(item.category, mediaId, item.revision) != null || previewJobs[key]?.isActive == true) return
        val job = repositoryScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Yield to direct user actions (especially a display request) before optional I/O.
                delay(500L)
                var previewUri: String? = null
                for (attempt in 0..2) {
                    previewUri = previewCache.load(item.category, mediaId, item.revision, item.displayProfile)
                    if (previewUri != null || attempt == 2) break
                    // A freshly committed TF item can briefly contend with the device index/SD
                    // reader. Retry locally rather than leaving a permanent visual placeholder.
                    delay((attempt + 1) * 1_000L)
                }
                if (previewUri == null) {
                    Log.w(PREVIEW_LOG_TAG, "Preview cache unavailable for $mediaId revision=${item.revision}")
                    return@launch
                }
                mutableMedia.value = mutableMedia.value.map { media ->
                    if (media.id.value == mediaId && media.revision == item.revision) media.copy(previewUri = previewUri) else media
                }
            } finally {
                previewJobs.remove(key, coroutineContext[Job])
            }
        }
        if (previewJobs.putIfAbsent(key, job) == null) job.start() else job.cancel()
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
        const val PREVIEW_LOG_TAG = "EInkLocalAlbum"
        val terminalJobStates = setOf(DeviceJobState.Success, DeviceJobState.Failed, DeviceJobState.Cancelled, DeviceJobState.TimedOut)
    }
}
