package com.einkphoto.app.feature.aialbum

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/** App-owned Seedream request. The ESP never receives the API key or source JPEG. */
class SeedreamDirectClient(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_ai_provider", Context.MODE_PRIVATE)

    suspend fun generate(prompt: String, reference: File? = null): Result<String> = withContext(Dispatchers.IO) { runCatching {
        val endpoint = preferences.getString("endpoint", "").orEmpty()
        val model = preferences.getString("model", "").orEmpty()
        val apiKey = preferences.getString("api_key", "").orEmpty()
        require(endpoint.startsWith("https://") && model.isNotBlank() && apiKey.length >= 8) { "app_ai_not_configured" }
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("response_format", "url")
            .put("size", "2K")
            .put("output_format", "jpeg")
            .put("watermark", false)
            .apply {
                if (reference != null) {
                    require(reference.isFile && reference.length() in 1..180_000L) { "reference_image_unavailable" }
                    put("image", "data:image/jpeg;base64," + Base64.encodeToString(reference.readBytes(), Base64.NO_WRAP))
                }
            }.toString()
            .toByteArray(Charsets.UTF_8)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { String(readBounded(it, MAX_JSON_BYTES), Charsets.UTF_8) }.orEmpty()
            require(status in 200..299) { "seedream_http_$status" }
            JSONObject(text).optJSONArray("data")?.optJSONObject(0)?.optString("url")
                ?.takeIf { it.startsWith("https://") } ?: error("seedream_invalid_response")
        } finally { connection.disconnect() }
    } }

    suspend fun download(url: String, destination: File): Result<File> = withContext(Dispatchers.IO) { runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 180_000 }
        try {
            require(connection.responseCode in 200..299) { "seedream_download_${connection.responseCode}" }
            require(connection.contentLengthLong < 0 || connection.contentLengthLong <= MAX_IMAGE_BYTES) {
                "seedream_image_too_large"
            }
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, ".${destination.name}.downloading")
            temporary.delete()
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_IMAGE_BYTES) { "seedream_image_too_large" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                require(temporary.length() > 0) { "seedream_empty_image" }
                destination.delete()
                check(temporary.renameTo(destination)) { "seedream_download_commit_failed" }
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            }
            destination
        } finally { connection.disconnect() }
    } }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "seedream_response_too_large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_JSON_BYTES = 1024 * 1024
        const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
    }
}
