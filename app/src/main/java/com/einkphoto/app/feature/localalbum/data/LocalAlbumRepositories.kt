package com.einkphoto.app.feature.localalbum.data

import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import kotlinx.coroutines.flow.StateFlow

interface MediaRepository {
    val media: StateFlow<List<MediaItem>>
    suspend fun refresh(): DeviceCommandResult<Unit>
    suspend fun delete(mediaId: MediaId): DeviceCommandResult<Unit>
}

interface PlaybackRepository {
    val settings: StateFlow<PlaybackSettings>
    /** Re-reads device runtime state without changing its saved playback configuration. */
    suspend fun refreshPlayback(): DeviceCommandResult<Unit> = DeviceCommandResult.Accepted(Unit)
    suspend fun save(settings: PlaybackSettings): DeviceCommandResult<Unit>
}

interface DisplayRepository {
    val currentDisplay: StateFlow<CurrentDisplay>
    val activeJob: StateFlow<DeviceJob?>

    suspend fun requestDisplay(
        mediaId: MediaId,
        afterDisplay: AfterDisplay,
    ): DeviceCommandResult<DeviceJobId>
}

/**
 * Device-admission boundary. A successful call only means the device accepted an asynchronous
 * staging/validation job; it never creates a local MediaItem or implies a screen refresh.
 */
interface UploadRepository {
    suspend fun submit(
        draft: ConversionDraft,
        mode: UploadMode,
        requestId: String,
    ): DeviceCommandResult<DeviceJobId>
}

enum class UploadMode { SourceOnly, SourceAndBin }

/** Optional deterministic controls for Demo/Preview hosts; production sessions do not implement it. */
interface DemoLocalAlbumController {
    fun advanceDisplayJob(): DeviceJob?
    fun finishDisplayJob(success: Boolean): DeviceJob?
    /**
     * Advances the currently active demo upload.  Kept for the existing single-item UI;
     * batch callers should always address the job explicitly.
     */
    fun advanceUploadJob(): DeviceJob?
    fun advanceUploadJob(jobId: DeviceJobId): DeviceJob?
    fun finishUploadJob(
        success: Boolean,
        rejection: DeviceRejection = DeviceRejection.Unsupported,
    ): DeviceJob?
    fun finishUploadJob(
        jobId: DeviceJobId,
        success: Boolean,
        rejection: DeviceRejection = DeviceRejection.Unsupported,
    ): DeviceJob?
}
