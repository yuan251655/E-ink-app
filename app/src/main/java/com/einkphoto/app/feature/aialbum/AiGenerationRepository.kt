package com.einkphoto.app.feature.aialbum

import android.content.Context
import android.net.Uri
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DownloadedFile
import java.io.File
import java.util.UUID
import org.json.JSONObject

/**
 * Adapter for the staged AI generation API. A generated preview is not media-library content:
 * only `confirmSave()` asks the device to convert it and atomically commit BIN + manifest to TF.
 */
class AiGenerationRepository(
    context: Context,
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
    private val historyStore: AiGenerationHistoryStore = AiGenerationHistoryStore(context.applicationContext),
    private val previewDirectory: File = historyStore.previewDirectory,
) {
    suspend fun createPreview(prompt: String): Result<String> {
        val normalized = prompt.trim()
        if (normalized.isEmpty()) return Result.failure(IllegalArgumentException("prompt_required"))
        if (normalized.length > MAX_PROMPT_CHARS) return Result.failure(IllegalArgumentException("prompt_too_long"))
        return client.postJson(
            "/api/v1/ai/generation/jobs",
            JSONObject()
                .put("request_id", requestId("preview"))
                .put("prompt", normalized)
                .put("stage_only", true)
                .put("display_when_active", false),
        ).mapCatching { root ->
            root.optJSONObject("data")?.optString("job_id")
                ?.takeIf(::isSafeJobId)
                ?: error("invalid_generation_job")
        }
    }

    suspend fun job(jobId: String): Result<AiGenerationJob> {
        if (!isSafeJobId(jobId)) return Result.failure(IllegalArgumentException("invalid_generation_job"))
        return client.get("/api/v1/jobs/$jobId").mapCatching { root ->
            val data = root.optJSONObject("data") ?: error("invalid_generation_status")
            AiGenerationJob(
                jobId = data.optString("job_id", jobId).takeIf(::isSafeJobId) ?: error("invalid_generation_job"),
                state = data.opt("state")?.toString().orEmpty(),
                phase = data.optString("phase").lowercase(),
                progressPercent = data.optInt("progress_percent", 0).coerceIn(0, 100),
                errorCode = data.optString("error_code").takeIf(String::isNotBlank),
                mediaId = data.optString("media_id")
                    .ifBlank { data.optJSONObject("result")?.optString("media_id").orEmpty() }
                    .takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun downloadPreview(jobId: String): Result<AiGenerationPreviewFile> {
        if (!isSafeJobId(jobId)) return Result.failure(IllegalArgumentException("invalid_generation_job"))
        previewDirectory.mkdirs()
        val destination = File(previewDirectory, "$jobId.preview")
        return client.downloadToFile("/api/v1/ai/generation/jobs/$jobId/preview", destination)
            .mapCatching { file -> file.toPreviewFile(destination) }
    }

    suspend fun confirmSave(jobId: String): Result<String> {
        if (!isSafeJobId(jobId)) return Result.failure(IllegalArgumentException("invalid_generation_job"))
        return client.postJson(
            "/api/v1/ai/generation/jobs/$jobId/confirm-save",
            JSONObject()
                .put("request_id", requestId("save"))
                .put("display_after_save", false),
        ).mapCatching { root ->
            root.optJSONObject("data")?.optString("job_id")
                ?.takeIf(::isSafeJobId)
                ?: error("invalid_generation_job")
        }
    }

    suspend fun loadHistory(): List<AiGenerationHistoryItem> = historyStore.load()

    suspend fun saveHistory(item: AiGenerationHistoryItem): List<AiGenerationHistoryItem> = historyStore.upsert(item)

    private fun DownloadedFile.toPreviewFile(file: File): AiGenerationPreviewFile =
        AiGenerationPreviewFile(Uri.fromFile(file).toString(), mimeType, sizeBytes)

    private fun requestId(kind: String): String = "ai-$kind-${UUID.randomUUID()}"

    private fun isSafeJobId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,64}"))

    private companion object {
        const val MAX_PROMPT_CHARS = 500
    }
}

data class AiGenerationPreviewFile(val uri: String, val mimeType: String, val sizeBytes: Long)

data class AiGenerationJob(
    val jobId: String,
    val state: String,
    val phase: String,
    val progressPercent: Int,
    val errorCode: String?,
    val mediaId: String?,
) {
    val inProgress: Boolean get() = state in setOf("0", "1", "queued", "running", "preparing", "refreshing", "finalizing")
    val completed: Boolean get() = state in setOf("2", "success", "completed")
}
