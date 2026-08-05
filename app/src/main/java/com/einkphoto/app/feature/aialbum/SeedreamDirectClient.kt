package com.einkphoto.app.feature.aialbum

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) { "seedream_http_$status" }
            JSONObject(text).optJSONArray("data")?.optJSONObject(0)?.optString("url")
                ?.takeIf { it.startsWith("https://") } ?: error("seedream_invalid_response")
        } finally { connection.disconnect() }
    } }

    suspend fun download(url: String, destination: File): Result<File> = withContext(Dispatchers.IO) { runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 180_000 }
        try {
            require(connection.responseCode in 200..299) { "seedream_download_${connection.responseCode}" }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input -> destination.outputStream().use { input.copyTo(it) } }
            require(destination.length() > 0) { "seedream_empty_image" }
            destination
        } finally { connection.disconnect() }
    } }
}
