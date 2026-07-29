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
    suspend fun capabilities(): LanTransportResult<DeviceCapabilities>
    suspend fun status(): LanTransportResult<LanStatus>

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

    suspend fun switchFeature(feature: DeviceFeature, requestId: String): LanTransportResult<DeviceJobId>
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
        DeviceSnapshot("unknown", "墨相框", false, DeviceConnectionState.Offline, DeviceFeature.LocalAlbum, false, null, null),
    )
    override val snapshot: StateFlow<DeviceSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refreshSnapshot(): DeviceCommandResult<DeviceSnapshot> = mutex.withLock {
        val health = transport.health().orReject() ?: return@withLock rejectOffline()
        if (!health.ready) return@withLock rejectOffline()
        val capabilities = transport.capabilities().orReject() ?: return@withLock rejectOffline()
        val status = transport.status().orReject() ?: return@withLock rejectOffline()
        val updated = DeviceSnapshot(health.deviceId, health.displayName, false, status.connection, status.activeFeature, status.displayBusy, status.storageFreeBytes, capabilities)
        mutableSnapshot.value = updated
        DeviceCommandResult.Accepted(updated)
    }

    override suspend fun requestFeatureSwitch(feature: DeviceFeature): DeviceCommandResult<DeviceJobId> {
        val current = snapshot.value
        if (current.connection == DeviceConnectionState.Sleeping) return DeviceCommandResult.Rejected(DeviceRejection.Sleeping)
        if (current.connection != DeviceConnectionState.Online) return DeviceCommandResult.Rejected(DeviceRejection.Offline)
        if (current.displayBusy) return DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
        val requestId = "mode-${System.currentTimeMillis()}-${feature.name}"
        return when (val result = transport.switchFeature(feature, requestId)) {
            is LanTransportResult.Success -> DeviceCommandResult.Accepted(result.value)
            is LanTransportResult.Failure -> DeviceCommandResult.Rejected(result.rejection)
        }
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
