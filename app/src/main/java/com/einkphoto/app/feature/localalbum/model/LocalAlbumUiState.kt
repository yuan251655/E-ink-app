package com.einkphoto.app.feature.localalbum.model

import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceSnapshot

data class LocalAlbumUiState(
    val device: DeviceSnapshot,
    val media: List<MediaItem>,
    val currentDisplay: CurrentDisplay,
    val playback: PlaybackSettings,
    val displayJob: DeviceJob?,
    val phoneSources: List<PhoneSource> = emptyList(),
    val selectedPhoneSourceId: String? = null,
    val adaptationSettings: Map<String, AdaptationSettings> = emptyMap(),
    val conversionDrafts: Map<String, ConversionDraft> = emptyMap(),
    val batchSaveTotal: Int = 0,
    val batchSaveCompleted: Int = 0,
    val batchSaveActive: Boolean = false,
    val isRefreshing: Boolean = false,
    val userMessage: String? = null,
) {
    val currentMedia: MediaItem?
        get() = currentDisplay.mediaId?.let { currentId -> media.firstOrNull { it.id == currentId } }

    val selectedPhoneSource: PhoneSource?
        get() = selectedPhoneSourceId?.let { id -> phoneSources.firstOrNull { it.sourceId == id } }

    val selectedAdaptation: AdaptationSettings
        get() = selectedPhoneSourceId?.let(adaptationSettings::get) ?: AdaptationSettings()

    val selectedDraft: ConversionDraft?
        get() = selectedPhoneSourceId?.let(conversionDrafts::get)

    val configuredSourceCount: Int
        get() = phoneSources.count { adaptationSettings[it.sourceId]?.isConfigured == true }

    val conversionSuccessCount: Int
        get() = conversionDrafts.values.count { it.stage == ConversionStage.Ready }

    val conversionFailureCount: Int
        get() = conversionDrafts.values.count { it.stage == ConversionStage.Failed }

    val conversionCancelledCount: Int
        get() = conversionDrafts.values.count { it.stage == ConversionStage.Cancelled }

    val conversionRunning: Boolean
        get() = conversionDrafts.values.any {
            it.stage in setOf(
                ConversionStage.Queued,
                ConversionStage.Preparing,
                ConversionStage.RenderingPreview,
                ConversionStage.Quantizing,
                ConversionStage.Validating,
            )
        }

    val readyToSaveCount: Int
        get() = conversionDrafts.values.count { it.stage in setOf(ConversionStage.Ready, ConversionStage.WaitingForDevice) }

    val actionsLocked: Boolean
        get() = device.displayBusy ||
            currentDisplay.result == DisplayResult.Refreshing ||
            displayJob?.state in setOf(DeviceJobState.Queued, DeviceJobState.Running)
}
