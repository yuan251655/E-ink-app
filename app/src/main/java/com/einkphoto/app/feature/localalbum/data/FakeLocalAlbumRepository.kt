package com.einkphoto.app.feature.localalbum.data

import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceSession
import com.einkphoto.app.core.device.InMemoryJobTracker
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.DisplayResult
import com.einkphoto.app.feature.localalbum.model.MediaAvailability
import com.einkphoto.app.feature.localalbum.model.MediaCategory
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.MediaProtectionReason
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class FakeLocalAlbumRepository(
    private val session: DeviceSession,
    private val jobTracker: InMemoryJobTracker = InMemoryJobTracker(),
) : MediaRepository, PlaybackRepository, DisplayRepository, UploadRepository, DemoLocalAlbumController {
    /** A newly connected device has no assumed media history. */
    private val mutableMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    override val media: StateFlow<List<MediaItem>> = mutableMedia.asStateFlow()

    private val mutableCurrentDisplay = MutableStateFlow(
        CurrentDisplay(
            mediaId = null,
            feature = DeviceFeature.LocalAlbum,
            result = DisplayResult.Idle,
            lastSuccessfulRefreshEpochMillis = null,
        ),
    )
    override val currentDisplay: StateFlow<CurrentDisplay> = mutableCurrentDisplay.asStateFlow()

    private val mutableSettings = MutableStateFlow(
        PlaybackSettings(mode = PlayMode.Auto, order = PlayOrder.Sequential, intervalMinutes = 30),
    )
    override val settings: StateFlow<PlaybackSettings> = mutableSettings.asStateFlow()

    private val mutableActiveJob = MutableStateFlow<DeviceJob?>(null)
    override val activeJob: StateFlow<DeviceJob?> = mutableActiveJob.asStateFlow()
    private var displayJobSequence = 0
    private var uploadJobSequence = 0
    private var pendingDisplay: PendingDisplay? = null
    /** One active TF-style transaction plus a FIFO of accepted upload jobs. */
    private var activeUpload: PendingUpload? = null
    private val queuedUploads = ArrayDeque<PendingUpload>()
    private val completedUploadRequests = mutableMapOf<String, DeviceJob>()
    private val acceptedUploadRequests = mutableMapOf<String, DeviceJobId>()

    override suspend fun refresh(): DeviceCommandResult<Unit> = DeviceCommandResult.Accepted(Unit)

    override suspend fun delete(mediaId: MediaId): DeviceCommandResult<Unit> {
        val item = mutableMedia.value.firstOrNull { it.id == mediaId }
            ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        if (!item.canDelete) return DeviceCommandResult.Rejected(DeviceRejection.MediaProtected)
        mutableMedia.value = mutableMedia.value.filterNot { it.id == mediaId }
        return DeviceCommandResult.Accepted(Unit)
    }

    override suspend fun save(settings: PlaybackSettings): DeviceCommandResult<Unit> {
        mutableSettings.value = settings
        return DeviceCommandResult.Accepted(Unit)
    }

    override suspend fun requestDisplay(
        mediaId: MediaId,
        afterDisplay: AfterDisplay,
    ): DeviceCommandResult<DeviceJobId> {
        val device = session.snapshot.value
        val rejection = when {
            device.connection == com.einkphoto.app.core.device.DeviceConnectionState.Sleeping -> DeviceRejection.Sleeping
            device.connection != com.einkphoto.app.core.device.DeviceConnectionState.Online -> DeviceRejection.Offline
            device.displayBusy || mutableActiveJob.value != null -> DeviceRejection.DisplayBusy
            device.activeFeature != DeviceFeature.LocalAlbum -> DeviceRejection.FeatureNotActive
            mutableMedia.value.none { it.id == mediaId && it.availability == MediaAvailability.Ready } -> DeviceRejection.Unsupported
            else -> null
        }
        if (rejection != null) return DeviceCommandResult.Rejected(rejection)

        val id = DeviceJobId("fake-display-${++displayJobSequence}")
        val job = DeviceJob(id, "display_local_media", DeviceJobState.Queued, "等待电子纸刷新")
        pendingDisplay = PendingDisplay(mediaId, afterDisplay)
        mutableActiveJob.value = job
        jobTracker.record(job)
        return DeviceCommandResult.Accepted(id)
    }

    override suspend fun submit(draft: ConversionDraft, mode: UploadMode, requestId: String): DeviceCommandResult<DeviceJobId> {
        if (draft.stage != com.einkphoto.app.feature.localalbum.model.ConversionStage.Ready) return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        val device = session.snapshot.value
        val rejection = when {
            device.connection == com.einkphoto.app.core.device.DeviceConnectionState.Sleeping -> DeviceRejection.Sleeping
            device.connection != com.einkphoto.app.core.device.DeviceConnectionState.Online -> DeviceRejection.Offline
            device.storageFreeBytes == null -> DeviceRejection.StorageUnavailable
            device.storageFreeBytes < (draft.generatedFrameBytes ?: Int.MAX_VALUE).toLong() -> DeviceRejection.StorageNoSpace
            device.displayBusy || mutableActiveJob.value != null -> DeviceRejection.DisplayBusy
            else -> null
        }
        if (rejection != null) return DeviceCommandResult.Rejected(rejection)
        completedUploadRequests[requestId]?.let { return DeviceCommandResult.Accepted(it.id) }
        acceptedUploadRequests[requestId]?.let { return DeviceCommandResult.Accepted(it) }

        val job = DeviceJob(
            DeviceJobId("fake-upload-${++uploadJobSequence}"),
            "mock_media_upload",
            DeviceJobState.Queued,
            "Mock upload accepted; waiting to transfer",
        )
        val pending = PendingUpload(draft, mode, requestId, job, UploadPhase.Transfer)
        if (activeUpload == null) {
            activeUpload = pending
        } else {
            queuedUploads.addLast(pending)
        }
        acceptedUploadRequests[requestId] = job.id
        jobTracker.record(job)
        return DeviceCommandResult.Accepted(job.id)
    }

    /** Legacy immediate-admission path retained only for source history; it is not called. */
    private suspend fun legacySubmit(draft: ConversionDraft, mode: UploadMode, requestId: String): DeviceCommandResult<DeviceJobId> {
        if (draft.stage != com.einkphoto.app.feature.localalbum.model.ConversionStage.Ready) return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        val id = DeviceJobId("fake-upload-${++uploadJobSequence}")
        val mediaId = MediaId("mock-${draft.draftId}")
        if (mutableMedia.value.none { it.id == mediaId }) {
            mutableMedia.value = mutableMedia.value + MediaItem(
                id = mediaId,
                category = MediaCategory.Local,
                displayName = draft.source.displayName,
                previewUri = draft.previewUri,
                sourceWidthPx = draft.source.widthPx,
                sourceHeightPx = draft.source.heightPx,
                sizeBytes = draft.generatedFrameBytes?.toLong() ?: 0L,
                availability = MediaAvailability.Ready,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        }
        jobTracker.record(DeviceJob(id, "mock_media_upload", DeviceJobState.Success, "Mock 入库完成，未写入真实 TF"))
        return DeviceCommandResult.Accepted(id)
    }

    override fun advanceUploadJob(): DeviceJob? {
        val pending = activeUpload ?: return null
        return advanceUploadJob(pending.job.id)
    }

    override fun advanceUploadJob(jobId: DeviceJobId): DeviceJob? {
        val pending = activeUpload?.takeIf { it.job.id == jobId } ?: return null
        val next = when (pending.phase) {
            UploadPhase.Transfer -> pending.copy(
                phase = UploadPhase.Validation,
                job = pending.job.copy(state = DeviceJobState.Running, message = "Mock upload in progress"),
            )
            UploadPhase.Validation -> pending.copy(
                phase = UploadPhase.Commit,
                job = pending.job.copy(state = DeviceJobState.Running, message = "Mock device validation in progress"),
            )
            UploadPhase.Commit -> pending.copy(
                phase = UploadPhase.ReadyToFinish,
                job = pending.job.copy(state = DeviceJobState.Running, message = "Mock atomic media commit in progress"),
            )
            UploadPhase.ReadyToFinish -> return null
        }
        activeUpload = next
        jobTracker.record(next.job)
        return next.job
    }

    override fun finishUploadJob(success: Boolean, rejection: DeviceRejection): DeviceJob? {
        val pending = activeUpload ?: return null
        return finishUploadJob(pending.job.id, success, rejection)
    }

    override fun finishUploadJob(
        jobId: DeviceJobId,
        success: Boolean,
        rejection: DeviceRejection,
    ): DeviceJob? {
        val pending = activeUpload?.takeIf { it.job.id == jobId } ?: return null
        if (success && pending.phase != UploadPhase.ReadyToFinish) return null
        val completed = pending.job.copy(
            state = if (success) DeviceJobState.Success else DeviceJobState.Failed,
            message = if (success) "Mock atomic admission completed; no real TF write" else "Mock upload failed: $rejection",
        )
        if (success) {
            val mediaId = MediaId("mock-${pending.draft.draftId}")
            if (mutableMedia.value.none { it.id == mediaId }) {
                mutableMedia.value = mutableMedia.value + MediaItem(
                    id = mediaId,
                    category = MediaCategory.Local,
                    displayName = pending.draft.source.displayName,
                    previewUri = pending.draft.previewUri,
                    sourceWidthPx = pending.draft.source.widthPx,
                    sourceHeightPx = pending.draft.source.heightPx,
                    sizeBytes = pending.draft.generatedFrameBytes?.toLong() ?: 0L,
                    availability = MediaAvailability.Ready,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
            }
            completedUploadRequests[pending.requestId] = completed
        }
        acceptedUploadRequests.remove(pending.requestId)
        activeUpload = if (queuedUploads.isEmpty()) null else queuedUploads.removeFirst()
        jobTracker.record(completed)
        return completed
    }

    override fun advanceDisplayJob(): DeviceJob? {
        val active = mutableActiveJob.value ?: return null
        if (active.state != DeviceJobState.Queued) return null
        val running = active.copy(state = DeviceJobState.Running, message = "模拟电子纸刷新中")
        mutableActiveJob.value = running
        jobTracker.record(running)
        mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Refreshing)
        return running
    }

    /** Deterministic Fake control used by previews/tests; it never pretends to be hardware timing. */
    override fun finishDisplayJob(success: Boolean): DeviceJob? {
        val active = mutableActiveJob.value ?: return null
        val request = pendingDisplay ?: return null
        if (active.state != DeviceJobState.Running) return null
        val completed = active.copy(
            state = if (success) DeviceJobState.Success else DeviceJobState.Failed,
            message = if (success) "模拟刷新完成" else "模拟刷新失败，保留上一张画面",
        )
        jobTracker.record(completed)
        if (success) {
            mutableMedia.value = mutableMedia.value.map { item ->
                val withoutCurrent = item.protectionReasons - MediaProtectionReason.CurrentDisplay
                item.copy(
                    protectionReasons = if (item.id == request.mediaId) {
                        withoutCurrent + MediaProtectionReason.CurrentDisplay
                    } else {
                        withoutCurrent
                    },
                )
            }
            mutableCurrentDisplay.value = CurrentDisplay(
                mediaId = request.mediaId,
                feature = DeviceFeature.LocalAlbum,
                result = DisplayResult.Success,
                lastSuccessfulRefreshEpochMillis = 1_752_889_625_000,
            )
            mutableSettings.value = mutableSettings.value.copy(
                mode = when (request.afterDisplay) {
                    AfterDisplay.Continue -> PlayMode.Auto
                    AfterDisplay.Hold -> PlayMode.ManualHold
                },
            )
        } else {
            mutableCurrentDisplay.value = mutableCurrentDisplay.value.copy(result = DisplayResult.Failed)
        }
        pendingDisplay = null
        mutableActiveJob.value = null
        return completed
    }

    private data class PendingDisplay(
        val mediaId: MediaId,
        val afterDisplay: AfterDisplay,
    )

    private data class PendingUpload(
        val draft: ConversionDraft,
        val mode: UploadMode,
        val requestId: String,
        val job: DeviceJob,
        val phase: UploadPhase,
    )

    private enum class UploadPhase { Transfer, Validation, Commit, ReadyToFinish }

}
