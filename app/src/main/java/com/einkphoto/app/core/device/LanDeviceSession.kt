package com.einkphoto.app.core.device

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transport boundary for the future versioned LAN API. It intentionally exposes no UI or TF paths. */
interface LanDeviceTransport {
    suspend fun health(): LanTransportResult<LanHealth>
    suspend fun fastHealth(): LanTransportResult<LanHealth> = health()
    suspend fun capabilities(): LanTransportResult<DeviceCapabilities>
    suspend fun status(): LanTransportResult<LanStatus>
    suspend fun mode(): LanTransportResult<DeviceModeSnapshot> = when (val current = status()) {
        is LanTransportResult.Success -> LanTransportResult.Success(
            DeviceModeSnapshot(
                activeFeature = current.value.activeFeature,
                pendingFeature = null,
                state = DeviceModeState.Idle,
                revision = current.value.modeRevision,
                switchJobId = null,
                currentContent = current.value.currentMediaId?.let {
                    DeviceCurrentContent(DeviceContentKind.Media, current.value.activeFeature, null, it, null)
                },
            ),
        )
        is LanTransportResult.Failure -> current
    }

    /** Read-only endpoints are exposed here for later feature repositories, not directly to UI. */
    suspend fun networkStatus(): LanTransportResult<LanNetworkStatus> =
        LanTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun listMedia(
        category: DeviceMediaCategory = DeviceMediaCategory.Local,
        cursor: String? = null,
        limit: Int = 20,
    ): LanTransportResult<DeviceMediaPage> = LanTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun mediaDetail(mediaId: String): LanTransportResult<DeviceMediaItem> =
        LanTransportResult.Failure(DeviceRejection.Unsupported)

    /** Streams a source image into an App-private destination; it never returns a device file path. */
    suspend fun downloadMediaSource(mediaId: String, destination: File): LanTransportResult<DeviceMediaSource> =
        LanTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun uploadMedia(request: DeviceMediaUploadRequest): LanTransportResult<DeviceJobSnapshot> =
        LanTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun jobStatus(jobId: DeviceJobId): LanTransportResult<DeviceJobSnapshot> =
        LanTransportResult.Failure(DeviceRejection.Unsupported)

    /** Submits a single local-media display job; the device owns all physical display state. */
    suspend fun displayMedia(
        mediaId: String,
        requestId: String,
        expectedModeRevision: Long,
        afterDisplay: String,
    ): LanTransportResult<DeviceJobSnapshot> = LanTransportResult.Failure(DeviceRejection.Unsupported)

    /** Deletes one committed media item using its device-issued media revision. */
    suspend fun deleteMedia(
        mediaId: String,
        requestId: String,
        expectedRevision: Long,
    ): LanTransportResult<Unit> = LanTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun localAlbumPlayback(): PlaybackTransportResult =
        PlaybackTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun saveLocalAlbumPlayback(
        requestId: String,
        expectedRevision: Long,
        mode: String,
        intervalSeconds: Int,
        order: String,
    ): PlaybackTransportResult = PlaybackTransportResult.Failure(DeviceRejection.Unsupported)

    /** AI playback has the same wire shape as local playback, but is a separate device domain. */
    suspend fun aiAlbumPlayback(): PlaybackTransportResult =
        PlaybackTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun saveAiAlbumPlayback(
        requestId: String,
        expectedRevision: Long,
        mode: String,
        intervalSeconds: Int,
        order: String,
    ): PlaybackTransportResult = PlaybackTransportResult.Failure(DeviceRejection.Unsupported)

    suspend fun switchFeature(
        feature: DeviceFeature,
        requestId: String,
        expectedRevision: Long,
    ): LanTransportResult<DeviceJobId>
}

data class LanHealth(val deviceId: String, val displayName: String, val apiVersion: String, val ready: Boolean)
data class LanStatus(
    val activeFeature: DeviceFeature,
    val connection: DeviceConnectionState,
    val displayBusy: Boolean,
    val storageFreeBytes: Long?,
    /** Device-authoritative last successful display target; it is never inferred from phone preview state. */
    val currentMediaId: String? = null,
    /** Current mode revision required by the display request's optimistic-concurrency contract. */
    val modeRevision: Long = 0L,
    val displayCooldownRemainingSeconds: Int = 0,
    val displayCooldownRejectionSequence: Long = 0L,
)

data class DeviceModeSnapshot(
    val activeFeature: DeviceFeature,
    val pendingFeature: DeviceFeature?,
    val state: DeviceModeState,
    val revision: Long,
    val switchJobId: DeviceJobId?,
    val currentContent: DeviceCurrentContent?,
)

sealed interface LanTransportResult<out T> {
    data class Success<T>(val value: T) : LanTransportResult<T>
    data class Failure(val rejection: DeviceRejection) : LanTransportResult<Nothing>
}

/**
 * Real-session skeleton. A device is online only after health, capability and status requests all
 * succeed. No optimistic feature changes are made: the next authoritative refresh owns state.
 */
class LanDeviceSession(private val transport: LanDeviceTransport) : DeviceSession {
    private val mutex = Mutex()
    private val mutableSnapshot = MutableStateFlow(
        DeviceSnapshot("unknown", "相念", false, DeviceConnectionState.Offline, DeviceFeature.LocalAlbum, false, null, null),
    )
    override val snapshot: StateFlow<DeviceSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refreshSnapshot(): DeviceCommandResult<DeviceSnapshot> = mutex.withLock {
        val wasOnline = mutableSnapshot.value.connection == DeviceConnectionState.Online
        val health = transport.fastHealth().orReject() ?: return@withLock rejectOffline()
        if (!health.ready) return@withLock rejectOffline()
        if (!wasOnline) {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                deviceId = health.deviceId,
                displayName = health.displayName,
                connection = DeviceConnectionState.Reconnecting,
                capabilities = null,
            )
        }
        val capabilities = transport.capabilities().orReject() ?: return@withLock rejectOffline()
        val status = transport.status().orReject() ?: return@withLock rejectOffline()
        val mode = transport.mode().orReject() ?: return@withLock rejectOffline()
        val updated = DeviceSnapshot(
            deviceId = health.deviceId,
            displayName = health.displayName,
            isDemo = false,
            connection = status.connection,
            activeFeature = mode.activeFeature,
            displayBusy = status.displayBusy,
            storageFreeBytes = status.storageFreeBytes,
            capabilities = capabilities,
            pendingFeature = mode.pendingFeature,
            modeState = mode.state,
            modeRevision = mode.revision,
            modeSwitchJobId = mode.switchJobId,
            currentContent = mode.currentContent,
            displayCooldownRemainingSeconds = status.displayCooldownRemainingSeconds,
            displayCooldownRejectionSequence = status.displayCooldownRejectionSequence,
        )
        mutableSnapshot.value = updated
        DeviceCommandResult.Accepted(updated)
    }

    override suspend fun requestFeatureSwitch(feature: DeviceFeature): DeviceCommandResult<DeviceJobId> {
        val current = snapshot.value
        if (current.connection == DeviceConnectionState.Sleeping) return DeviceCommandResult.Rejected(DeviceRejection.Sleeping)
        if (current.connection != DeviceConnectionState.Online) return DeviceCommandResult.Rejected(DeviceRejection.Offline)
        if (current.modeState == DeviceModeState.Switching) return DeviceCommandResult.Rejected(DeviceRejection.ModeSwitchBusy)
        if (current.displayBusy) return DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
        if (current.activeFeature == feature) {
            return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        }
        val authoritativeMode = when (val result = transport.mode()) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(result.rejection)
        }
        if (authoritativeMode.state == DeviceModeState.Switching) {
            return DeviceCommandResult.Rejected(DeviceRejection.ModeSwitchBusy)
        }
        val requestId = "mode-${System.currentTimeMillis()}-${feature.name}"
        return when (val result = transport.switchFeature(feature, requestId, authoritativeMode.revision)) {
            is LanTransportResult.Success -> DeviceCommandResult.Accepted(result.value)
            is LanTransportResult.Failure -> DeviceCommandResult.Rejected(result.rejection)
        }
    }

    override suspend fun modeSwitchJob(jobId: DeviceJobId): DeviceCommandResult<DeviceJobSnapshot> =
        when (val result = transport.jobStatus(jobId)) {
            is LanTransportResult.Success -> DeviceCommandResult.Accepted(result.value)
            is LanTransportResult.Failure -> DeviceCommandResult.Rejected(result.rejection)
        }

    private fun <T> LanTransportResult<T>.orReject(): T? = when (this) {
        is LanTransportResult.Success -> value
        is LanTransportResult.Failure -> null
    }

    private fun rejectOffline(): DeviceCommandResult<DeviceSnapshot> {
        mutableSnapshot.value = mutableSnapshot.value.copy(connection = DeviceConnectionState.Offline, capabilities = null)
        return DeviceCommandResult.Rejected(DeviceRejection.Offline)
    }
}
