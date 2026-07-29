package com.einkphoto.app.core.device

enum class DeviceFeature {
    LocalAlbum,
    AiAlbum,
    InfoDashboard,
}

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
    Unsupported,
}
