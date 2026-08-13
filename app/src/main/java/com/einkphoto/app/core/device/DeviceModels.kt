package com.einkphoto.app.core.device

enum class DeviceFeature(val apiValue: String) {
    LocalAlbum("local_album"),
    AiAlbum("ai_album"),
    InfoDashboard("info_dashboard"),
}

enum class DeviceModeState { Idle, Switching }

enum class DeviceContentKind { Media, ModeCover, Dashboard, Unknown }

data class DeviceCurrentContent(
    val kind: DeviceContentKind,
    val ownerFeature: DeviceFeature,
    val category: DeviceMediaCategory?,
    val mediaId: String?,
    val systemAssetId: String?,
)

enum class DeviceConnectionState {
    Online,
    Connecting,
    Reconnecting,
    Offline,
    Sleeping,
}

data class DisplayProfile(
    val widthPx: Int,
    val heightPx: Int,
    val frameBytes: Int,
    val palette: List<String>,
    val orientationKey: String,
) {
    init {
        require(widthPx > 0 && heightPx > 0)
        require(frameBytes > 0)
        require(palette.isNotEmpty())
        require(orientationKey.isNotBlank())
    }
}

data class DeviceCapabilities(
    val displayProfile: DisplayProfile,
    val supportsSourceOnlyUpload: Boolean,
    val supportsSourceAndBinUpload: Boolean,
    val supportsMediaPreview: Boolean,
)

data class DeviceSnapshot(
    val deviceId: String,
    val displayName: String,
    val isDemo: Boolean,
    val connection: DeviceConnectionState,
    val activeFeature: DeviceFeature,
    val displayBusy: Boolean,
    val storageFreeBytes: Long?,
    val capabilities: DeviceCapabilities?,
    val pendingFeature: DeviceFeature? = null,
    val modeState: DeviceModeState = DeviceModeState.Idle,
    val modeRevision: Long = 0L,
    val modeSwitchJobId: DeviceJobId? = null,
    val currentContent: DeviceCurrentContent? = null,
    val displayCooldownRemainingSeconds: Int = 0,
    val displayCooldownRejectionSequence: Long = 0L,
)

@JvmInline
value class DeviceJobId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

enum class DeviceJobState {
    Queued,
    Running,
    Success,
    Failed,
    Cancelled,
    TimedOut,
    Busy,
}

data class DeviceJob(
    val id: DeviceJobId,
    val kind: String,
    val state: DeviceJobState,
    val message: String,
)

sealed interface DeviceCommandResult<out T> {
    data class Accepted<T>(val value: T) : DeviceCommandResult<T>
    data class Rejected(val reason: DeviceRejection) : DeviceCommandResult<Nothing>
}

enum class DeviceRejection {
    Offline,
    Sleeping,
    DisplayBusy,
    FeatureNotActive,
    StorageUnavailable,
    StorageNoSpace,
    SourceTooLarge,
    MediaProtected,
    ModeSwitchBusy,
    RevisionConflict,
    TimedOut,
    /** The device no longer has the asynchronous job requested by the App. */
    JobNotFound,
    Unsupported,
}
