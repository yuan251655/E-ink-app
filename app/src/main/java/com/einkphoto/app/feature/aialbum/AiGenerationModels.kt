package com.einkphoto.app.feature.aialbum

/** Device-owned stages for the two-step AI image flow. */
enum class AiGenerationPhase {
    Idle,
    CreatingPreview,
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

enum class AiGenerationSaveStatus { Generating, PreviewReady, Saving, Saved, Failed, Cancelled }

/** Persisted App-private history. It intentionally contains no provider credential or raw API reply. */
data class AiGenerationHistoryItem(
    val id: String,
    val prompt: String,
    val createdAtEpochMillis: Long,
    val saveStatus: AiGenerationSaveStatus,
    val preview: AiGenerationPreview?,
    val mediaId: String? = null,
    /** Device job currently being observed. It differs from preview.jobId while saving. */
    val remoteJobId: String? = null,
)

data class AiGenerationUiState(
    val phase: AiGenerationPhase = AiGenerationPhase.Idle,
    val message: String? = null,
    val prompt: String? = null,
    val jobId: String? = null,
    val preview: AiGenerationPreview? = null,
    val savedMediaId: String? = null,
    val history: List<AiGenerationHistoryItem> = emptyList(),
) {
    /** Compatibility with existing host chrome: only device work is considered active. */
    val active: Boolean
        get() = phase in setOf(AiGenerationPhase.CreatingPreview, AiGenerationPhase.GeneratingPreview, AiGenerationPhase.Saving)
}
