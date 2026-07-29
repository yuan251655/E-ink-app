package com.einkphoto.app.feature.localalbum.model

import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DisplayProfile

@JvmInline
value class MediaId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

/** A phone-owned source selected through Android Photo Picker. */
data class PhoneSource(
    val sourceId: String,
    val contentUri: String,
    val displayName: String,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(sourceId.isNotBlank())
        require(contentUri.isNotBlank())
        require(displayName.isNotBlank())
        require(widthPx > 0 && heightPx > 0)
    }
}

enum class ConversionStage {
    Selected,
    Adapted,
    Queued,
    Preparing,
    RenderingPreview,
    Quantizing,
    Validating,
    Ready,
    WaitingForDevice,
    Uploading,
    DeviceValidating,
    Committing,
    Admitted,
    Failed,
    Cancelled,
    Stale,
}

enum class FitMode {
    CropToFill,
    FitInside,
}

/** Per-phone-photo editing state. It remains local until a real conversion job is created. */
data class AdaptationSettings(
    val fitMode: FitMode = FitMode.CropToFill,
    val quarterTurnsClockwise: Int = 0,
    val isConfigured: Boolean = false,
) {
    init {
        require(quarterTurnsClockwise in 0..3)
    }
}

/** App-owned work in progress. It is not a device media item. */
data class ConversionDraft(
    val draftId: String,
    val source: PhoneSource,
    val profile: DisplayProfile,
    val fitMode: FitMode,
    val quarterTurnsClockwise: Int,
    val stage: ConversionStage,
    val previewUri: String? = null,
    val candidateBinUri: String? = null,
    val generatedFrameBytes: Int? = null,
    val algorithmVersion: String? = null,
    val localValidationPassed: Boolean = false,
    val errorMessage: String? = null,
) {
    init {
        require(draftId.isNotBlank())
        require(quarterTurnsClockwise in 0..3)
        if (stage == ConversionStage.Ready) {
            require(!previewUri.isNullOrBlank())
            require(!candidateBinUri.isNullOrBlank())
            require(generatedFrameBytes == profile.frameBytes)
            require(!algorithmVersion.isNullOrBlank())
            require(localValidationPassed)
        }
    }
}

enum class MediaCategory {
    Local,
    Ai,
    Dashboard,
    System,
}

enum class MediaAvailability {
    Ready,
    Uploading,
    Invalid,
}

enum class MediaProtectionReason {
    CurrentDisplay,
    OnlyFallback,
    Uploading,
    Refreshing,
}

/** Device-owned, atomically committed media. It never exposes a TF-card path. */
data class MediaItem(
    val id: MediaId,
    val category: MediaCategory,
    val displayName: String,
    val previewUri: String?,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val sizeBytes: Long,
    val availability: MediaAvailability,
    val protectionReasons: Set<MediaProtectionReason> = emptySet(),
    val createdAtEpochMillis: Long,
    /** Device-issued optimistic-concurrency token; it is never a file-system revision. */
    val revision: Long = 0L,
) {
    init {
        require(displayName.isNotBlank())
        require(sourceWidthPx > 0 && sourceHeightPx > 0)
        require(sizeBytes >= 0)
        require(revision >= 0)
    }

    val canDelete: Boolean get() = availability == MediaAvailability.Ready && protectionReasons.isEmpty()
}

enum class DisplayResult {
    Idle,
    Refreshing,
    Success,
    Failed,
}

/** Device-authoritative physical screen state, distinct from MediaItem. */
data class CurrentDisplay(
    val mediaId: MediaId?,
    val feature: DeviceFeature,
    val result: DisplayResult,
    val lastSuccessfulRefreshEpochMillis: Long?,
)

enum class PlayMode {
    Auto,
    Paused,
}

enum class PlayOrder {
    Sequential,
    Random,
}

enum class PlaybackSyncState { Loading, Ready, Offline, Conflict, Saving }

data class PlaybackSettings(
    val mode: PlayMode,
    val order: PlayOrder,
    val intervalSeconds: Int,
    val currentMediaId: MediaId? = null,
    /** Device-reported countdown. The App converts this to local wall time for display. */
    val nextPlayInSeconds: Long? = null,
    val nextPlayAtEpochMillis: Long? = null,
    val revision: Long = 0L,
    val stateRevision: Long = 0L,
    val syncState: PlaybackSyncState = PlaybackSyncState.Loading,
) {
    init {
        require(intervalSeconds in setOf(300, 900, 1800, 3600, 10800, 21600, 43200, 86400))
    }

    val intervalMinutes: Int get() = intervalSeconds / 60
}

enum class AfterDisplay {
    Continue,
    Hold,
}
