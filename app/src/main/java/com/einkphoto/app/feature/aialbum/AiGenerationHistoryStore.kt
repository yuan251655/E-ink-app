package com.einkphoto.app.feature.aialbum

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-private durable history for staged image generation.
 * Preview files are deliberately in `filesDir`, not cacheDir, so an App restart or routine cache
 * cleanup cannot silently remove a preview that is still waiting for the user's save decision.
 */
class AiGenerationHistoryStore(context: Context) {
    private val directory = File(context.filesDir, "ai-generation-history").apply { mkdirs() }
    internal val previewDirectory = File(directory, "previews").apply { mkdirs() }
    private val indexFile = File(directory, "history.json")
    private val mutex = Mutex()

    suspend fun load(): List<AiGenerationHistoryItem> = mutex.withLock {
        withContext(Dispatchers.IO) { readIndex() }
    }

    suspend fun upsert(item: AiGenerationHistoryItem): List<AiGenerationHistoryItem> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val updated = (readIndex().filterNot { it.id == item.id } + item)
                .sortedByDescending { it.createdAtEpochMillis }
                .take(MAX_HISTORY)
            writeIndex(updated)
            prunePreviewFiles(updated)
            updated
        }
    }

    private fun readIndex(): List<AiGenerationHistoryItem> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val root = JSONObject(indexFile.readText(Charsets.UTF_8))
        val raw = root.optJSONArray("items") ?: return emptyList()
        buildList {
            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(::isSafeId) ?: continue
                val prompt = item.optString("prompt").trim().takeIf { it.isNotEmpty() } ?: continue
                val createdAt = item.optLong("created_at_ms", 0L).takeIf { it > 0L } ?: continue
                val status = runCatching { AiGenerationSaveStatus.valueOf(item.optString("save_status")) }.getOrNull() ?: continue
                val preview = item.optJSONObject("preview")?.let { rawPreview ->
                    val fileName = rawPreview.optString("file").takeIf(::isSafePreviewFileName) ?: return@let null
                    val file = File(previewDirectory, fileName).takeIf { it.isFile && it.length() > 0L } ?: return@let null
                    val jobId = rawPreview.optString("job_id").takeIf(::isSafeId) ?: return@let null
                    AiGenerationPreview(
                        jobId = jobId,
                        prompt = prompt,
                        uri = Uri.fromFile(file).toString(),
                        mimeType = rawPreview.optString("mime_type").takeIf { it.startsWith("image/") } ?: return@let null,
                        sizeBytes = rawPreview.optLong("size_bytes", file.length()).coerceAtLeast(1L),
                    )
                }
                add(AiGenerationHistoryItem(
                    id = id,
                    prompt = prompt,
                    createdAtEpochMillis = createdAt,
                    saveStatus = status,
                    preview = preview,
                    mediaId = item.optString("media_id").takeIf(::isSafeId),
                    remoteJobId = item.optString("remote_job_id").takeIf(::isSafeId),
                ))
            }
        }.sortedByDescending { it.createdAtEpochMillis }.take(MAX_HISTORY)
    }.getOrElse {
        // A damaged local index must never block a new generation. Do not expose raw JSON or keys.
        emptyList()
    }

    private fun writeIndex(items: List<AiGenerationHistoryItem>) {
        directory.mkdirs()
        val root = JSONObject().put("version", 1).put("items", JSONArray().apply {
            items.forEach { item ->
                put(JSONObject()
                    .put("id", item.id)
                    .put("prompt", item.prompt)
                    .put("created_at_ms", item.createdAtEpochMillis)
                    .put("save_status", item.saveStatus.name)
                    .put("media_id", item.mediaId)
                    .put("remote_job_id", item.remoteJobId)
                    .put("preview", item.preview?.let(::previewJson)))
            }
        })
        val temporary = File(directory, "history.json.part")
        temporary.delete()
        FileOutputStream(temporary).bufferedWriter(Charsets.UTF_8).use { it.write(root.toString()) }
        if (indexFile.exists()) indexFile.delete()
        check(temporary.renameTo(indexFile)) { "history_commit_failed" }
    }

    private fun previewJson(preview: AiGenerationPreview): JSONObject? {
        val fileName = Uri.parse(preview.uri).path?.let(::File)?.name?.takeIf(::isSafePreviewFileName) ?: return null
        return JSONObject()
            .put("file", fileName)
            .put("job_id", preview.jobId)
            .put("mime_type", preview.mimeType)
            .put("size_bytes", preview.sizeBytes)
    }

    private fun prunePreviewFiles(items: List<AiGenerationHistoryItem>) {
        val kept = items.mapNotNull { item -> item.preview?.uri?.let { Uri.parse(it).path }?.let(::File)?.name }.toSet()
        previewDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in kept) file.delete()
        }
    }

    private fun isSafeId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,64}"))
    private fun isSafePreviewFileName(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{1,64}\\.preview"))

    private companion object { const val MAX_HISTORY = 30 }
}
