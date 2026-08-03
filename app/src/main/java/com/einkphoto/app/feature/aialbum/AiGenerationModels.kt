package com.einkphoto.app.feature.aialbum

/** Device-owned stages for the two-step AI image flow. */
enum class AiGenerationPhase {
    Idle,
    CreatingPreview,
    WaitingToSubmit,
    GeneratingPreview,
    PreviewReady,
    Saving,
    Saved,
    Failed,
}

data class AiGenerationPreview(
    val jobId: String,
    val prompt: String,
    /** App-private local file URI. It is only a temporary review copy, never a gallery item. */
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
)

/**
 * `Submitting` is intentionally durable: the user's bubble is created before
 * any network call, so a rejected/busy request can never silently disappear.
 */
enum class AiGenerationSaveStatus { Submitting, WaitingToSubmit, Generating, PreviewReady, Saving, Saved, Failed, Cancelled }

/** Persisted App-private history. It intentionally contains no provider credential or raw API reply. */
data class AiGenerationHistoryItem(
    val id: String,
    val prompt: String,
    val createdAtEpochMillis: Long,
    val saveStatus: AiGenerationSaveStatus,
    val preview: AiGenerationPreview?,
    val mediaId: String? = null,
    /** Safe device error code or user-facing diagnosis; never contains a credential or raw provider body. */
    val failureReason: String? = null,
    /** Device job currently being observed. It differs from preview.jobId while saving. */
    val remoteJobId: String? = null,
)

/** Redacted device diagnostic for the most recent terminal task. */
data class AiGenerationLastTaskDiagnostic(
    val available: Boolean,
    val jobId: String,
    val kind: String,
    val state: String,
    val phase: String,
    val errorCode: String?,
    /** Boot-local device uptime when the task ended; this is not a wall-clock timestamp. */
    val finishedAtUptimeMillis: Long,
    val profileId: String?,
    val profileName: String?,
)

data class AiGenerationUiState(
    val phase: AiGenerationPhase = AiGenerationPhase.Idle,
    val message: String? = null,
    val prompt: String? = null,
    /** Stable App-side conversation id. It is not the device job id. */
    val historyId: String? = null,
    val jobId: String? = null,
    val preview: AiGenerationPreview? = null,
    val savedMediaId: String? = null,
    val history: List<AiGenerationHistoryItem> = emptyList(),
    val lastTaskDiagnostic: AiGenerationLastTaskDiagnostic? = null,
) {
    /** Compatibility with existing host chrome: only device work is considered active. */
    val active: Boolean
        get() = phase in setOf(AiGenerationPhase.CreatingPreview, AiGenerationPhase.WaitingToSubmit, AiGenerationPhase.GeneratingPreview, AiGenerationPhase.Saving)
}
