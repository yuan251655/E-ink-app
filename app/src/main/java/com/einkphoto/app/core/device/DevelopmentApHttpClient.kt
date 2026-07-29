package com.einkphoto.app.core.device

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Versioned device HTTP client. The endpoint is read for every request so AP/STA switching takes effect immediately. */
class DevelopmentApHttpClient {
    private val baseUrl: String get() = DeviceEndpointConfig.apiBaseUrl
    suspend fun get(path: String): Result<JSONObject> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            // Reading the TF-backed media index can take noticeably longer
            // than the small health/status JSON responses, especially just
            // after an e-paper refresh. Do not let a valid, late list reply
            // turn an existing TF gallery into an empty App screen.
            val readTimeoutMs = if (path.startsWith("/api/v1/media?")) 30_000 else 12_000
            var lastError: Throwable? = null
            for (attempt in 0..2) {
                try {
                val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                    // The ESP may take several seconds to re-open its single HTTP slot after a
                    // TF transaction. Three seconds turned a valid, late 200 response into a
                    // false "offline" result before batch upload could even start.
                    requestMethod = "GET"; connectTimeout = 8_000; readTimeout = readTimeoutMs
                    setRequestProperty("Connection", "close")
                }
                try {
                    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                        ?: error("HTTP ${connection.responseCode}")
                    val json = stream.bufferedReader().use { it.readText() }
                    val root = JSONObject(json)
                    if (!root.optBoolean("ok", false)) error(root.optString("code", "request_failed"))
                    return@runCatching root
                } finally {
                    connection.disconnect()
                }
                } catch (error: Throwable) {
                    lastError = error
                    if (attempt < 2) Thread.sleep(500L * (attempt + 1))
                }
            }
            throw requireNotNull(lastError)
        }.onFailure { error ->
            Log.w("EInkDeviceHttp", "GET $baseUrl$path failed", error)
        }
    } }

    /** Downloads only the fixed, device-owned 4bpp preview frame for one safe media id. */
    suspend fun downloadMediaPreview(mediaId: String): Result<ByteArray> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            require(mediaId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "invalid media id" }
            val connection = (URL(baseUrl + "/api/v1/media/$mediaId/preview").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 5_000; readTimeout = 15_000
                setRequestProperty("Connection", "close")
            }
            try {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) { "preview unavailable" }
                val declared = connection.contentLengthLong
                // ESP-IDF sends this fixed-size resource with chunked HTTP.
                require(declared < 0L || declared == 192_000L) { "invalid preview size" }
                connection.inputStream.use { input ->
                    input.readNBytes(192_001).also { require(it.size == 192_000) { "invalid preview frame" } }
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { error -> Log.w("EInkDeviceHttp", "PREVIEW $baseUrl/api/v1/media/$mediaId/preview failed", error) }
    } }

    /** Uploads the device's fixed-size 4bpp frame using the current v1 multipart contract. */
    suspend fun uploadLocalFrame(requestId: String, mediaId: String, displayName: String, frameFile: File): Result<JSONObject> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            require(frameFile.isFile) { "processed image is unavailable" }
            val boundary = "EinkPhoto-${System.currentTimeMillis()}"
            val metadata = JSONObject()
                .put("request_id", requestId)
                .put("media_id", mediaId)
                .put("category", "local")
                .put("upload_mode", "source+bin")
                .put("display_name", displayName.take(64).replace('"', ' ').replace('\\', ' '))
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            val opening = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"metadata\"\r\n" +
                "Content-Type: application/json\r\n\r\n"
            val imageHeader = "\r\n--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"image_bin\"; filename=\"image.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
            val closing = "\r\n--$boundary--\r\n"
            val openingBytes = opening.toByteArray(StandardCharsets.UTF_8)
            val imageHeaderBytes = imageHeader.toByteArray(StandardCharsets.UTF_8)
            val closingBytes = closing.toByteArray(StandardCharsets.UTF_8)
            val contentLength = openingBytes.size.toLong() + metadata.size + imageHeaderBytes.size + frameFile.length() + closingBytes.size
            require(contentLength <= Int.MAX_VALUE) { "upload is too large" }

            val connection = (URL(baseUrl + "/api/v1/media/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Connection", "close")
                setFixedLengthStreamingMode(contentLength)
            }
            try {
                connection.outputStream.buffered().use { output ->
                    output.write(openingBytes)
                    output.write(metadata)
                    output.write(imageHeaderBytes)
                    frameFile.inputStream().buffered().use { it.copyTo(output) }
                    output.write(closingBytes)
                }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    ?: error("HTTP ${connection.responseCode}")
                val root = JSONObject(stream.bufferedReader().use { it.readText() })
                if (!root.optBoolean("ok", false)) error(root.optString("code", "upload_failed"))
                root
            } finally {
                connection.disconnect()
            }
        }
    } }

    /**
     * Compact multipart contract: metadata -> image_bin. Preview/source files are phone-only;
     * the device uses the fixed 4bpp frame and App can later decode its preview endpoint.
     */
    suspend fun uploadBinOnly(request: DeviceMediaUploadRequest): Result<JSONObject> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            require(request.imageBinFile.isFile) { "BIN file is unavailable" }
            require(request.imageBinSizeBytes == request.imageBinFile.length() && request.imageBinSizeBytes == 192_000L) { "BIN size changed" }
            val boundary = "EinkPhoto-${System.currentTimeMillis()}"
            val metadata = JSONObject()
                .put("request_id", request.requestId)
                .put("category", "local")
                .put("upload_mode", "bin_only")
                .put("display_name", request.displayName)
                .put("image_bin", JSONObject()
                    .put("size_bytes", request.imageBinSizeBytes)
                    .put("bytes", request.imageBinSizeBytes)
                    .put("sha256", request.imageBinSha256))
                .put("display_profile", JSONObject()
                    .put("width", request.displayProfile.widthPx)
                    .put("height", request.displayProfile.heightPx)
                    .put("frame_bytes", request.displayProfile.frameBytes)
                    .put("pixel_format", request.displayProfile.pixelFormat)
                    .put("palette", request.displayProfile.palette)
                    .put("orientation", request.displayProfile.orientation)
                    .put("rotation_degrees", request.displayProfile.rotationDegrees)
                    .put("fit_mode", request.displayProfile.fitMode)
                    .put("converter_version", request.displayProfile.converterVersion ?: "unknown"))
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            val metadataHeader = multipartHeader(boundary, "metadata", "metadata.json", "application/json")
            val binHeader = multipartHeader(boundary, "image_bin", "image.bin", "application/octet-stream")
            val closing = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
            val contentLength = metadataHeader.size.toLong() + metadata.size + 2L + binHeader.size + request.imageBinSizeBytes + closing.size
            val connection = (URL(baseUrl + "/api/v1/media/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Connection", "close")
                setFixedLengthStreamingMode(contentLength)
            }
            try {
                connection.outputStream.buffered(16 * 1024).use { output ->
                    output.write(metadataHeader); output.write(metadata); output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
                    output.write(binHeader); request.imageBinFile.inputStream().buffered(16 * 1024).use { it.copyTo(output, 16 * 1024) }
                    output.write(closing)
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    ?: error("HTTP ${connection.responseCode}")
                val root = JSONObject(stream.bufferedReader().use { it.readText() })
                if (!root.optBoolean("ok", false)) error(root.optString("code", "upload_failed"))
                Log.i("EInkDeviceHttp", "UPLOAD ${request.requestId} HTTP $status in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
                root
            } finally {
                connection.disconnect()
            }
        }.onFailure { error ->
            Log.w("EInkDeviceHttp", "UPLOAD ${request.requestId} $baseUrl/api/v1/media/upload failed", error)
        }
    } }

    suspend fun postJson(path: String, body: JSONObject): Result<JSONObject> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Connection", "close")
                setFixedLengthStreamingMode(payload.size)
            }
            try {
                connection.outputStream.use { it.write(payload) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    ?: error("HTTP ${connection.responseCode}")
                val root = JSONObject(stream.bufferedReader().use { it.readText() })
                if (!root.optBoolean("ok", false)) error(root.optString("code", "request_failed"))
                root
            } finally {
                connection.disconnect()
            }
        }
    } }

    suspend fun deleteJson(path: String, body: JSONObject): Result<JSONObject> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Connection", "close")
                setFixedLengthStreamingMode(payload.size)
            }
            try {
                connection.outputStream.use { it.write(payload) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    ?: error("HTTP ${connection.responseCode}")
                val root = JSONObject(stream.bufferedReader().use { it.readText() })
                if (!root.optBoolean("ok", false)) error(root.optString("code", "delete_failed"))
                root
            } finally {
                connection.disconnect()
            }
        }.onFailure { error ->
            Log.w("EInkDeviceHttp", "DELETE $baseUrl$path failed", error)
        }
    } }

    /** Streams a binary response into App-private storage without buffering an image in RAM. */
    suspend fun downloadToFile(path: String, destination: File): Result<DownloadedFile> = deviceHttpMutex.withLock { withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.part")
            temporary.delete()
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Connection", "close")
                connectTimeout = 5_000
                readTimeout = 30_000
            }
            try {
                require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                val mimeType = connection.contentType?.substringBefore(';')?.trim().orEmpty()
                require(mimeType == "image/jpeg" || mimeType == "image/png") { "unexpected source MIME type" }
                val expectedLength = connection.contentLengthLong
                val written = connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output -> input.copyTo(output, bufferSize = 16 * 1024) }
                }
                require(expectedLength < 0 || expectedLength == written) { "incomplete source response" }
                require(written > 0L) { "empty source response" }
                destination.delete()
                require(temporary.renameTo(destination)) { "unable to commit source cache" }
                DownloadedFile(mimeType, written, connection.getHeaderField("ETag"))
            } catch (error: Throwable) {
                temporary.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }
    } }

    private companion object {
        /** ESP HTTP is single-resource constrained; serialize every request across all clients. */
        val deviceHttpMutex = Mutex()
    }
}

private fun multipartHeader(boundary: String, name: String, filename: String, contentType: String): ByteArray =
    ("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n" +
        "Content-Type: $contentType\r\n\r\n").toByteArray(StandardCharsets.UTF_8)

private fun safeFilename(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "source.jpg" }

data class DownloadedFile(val mimeType: String, val sizeBytes: Long, val eTag: String?)

class HttpLanDeviceTransport(private val client: DevelopmentApHttpClient = DevelopmentApHttpClient()) : LanDeviceTransport {
    override suspend fun health(): LanTransportResult<LanHealth> = client.get("/api/v1/health").fold(
        onSuccess = { root ->
            val data = root.getJSONObject("data")
            val apiVersion = data.optString("api_version")
            LanTransportResult.Success(LanHealth("unknown", "墨水屏相册", apiVersion, apiVersion == "v1"))
        }, onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
    )

    override suspend fun capabilities(): LanTransportResult<DeviceCapabilities> = client.get("/api/v1/device/capabilities").fold(
        onSuccess = { root ->
            val data = root.getJSONObject("data")
            val modes = data.optJSONArray("media_upload_modes")
            // The legacy model name remains for binary compatibility, but its value now means
            // the only App-supported compact admission mode: metadata + image_bin.
            val sourceAndBin = (0 until (modes?.length() ?: 0)).any { modes?.optString(it) == "bin_only" }
            LanTransportResult.Success(DeviceCapabilities(
                displayProfile = DisplayProfile(
                    widthPx = data.optInt("display_width", 800).coerceAtLeast(1),
                    heightPx = data.optInt("display_height", 480).coerceAtLeast(1),
                    frameBytes = data.optInt("frame_bytes", 192_000).coerceAtLeast(1),
                    palette = listOf("black", "white", "green", "blue", "red", "yellow"),
                    orientationKey = "reported_by_media_profile",
                ),
                supportsSourceOnlyUpload = (0 until (modes?.length() ?: 0)).any { modes?.optString(it) == "source_only" },
                supportsSourceAndBinUpload = sourceAndBin,
                supportsMediaPreview = data.optBoolean("supports_media_preview", false),
            ))
        }, onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
    )

    override suspend fun status(): LanTransportResult<LanStatus> = client.get("/api/v1/device/status").fold(
        onSuccess = { root ->
            val data = root.getJSONObject("data")
            val mode = data.optJSONObject("mode")
            val display = data.optJSONObject("display")
            val storage = data.optJSONObject("storage")
            LanTransportResult.Success(
                LanStatus(
                    activeFeature = featureFromApi(mode?.opt("active_feature")),
                    connection = DeviceConnectionState.Online,
                    // ESP DisplayState: idle=0, queued=1, loading=2,
                    // refreshing=3, finalizing=4, success=5, failed=6.
                    // Only the in-flight states block another display request.
                    displayBusy = (display?.optInt("state", 0) ?: 0) in 1..4,
                    storageFreeBytes = storage?.takeIf { it.optInt("state", 0) == 2 }?.optLong("free_bytes"),
                    currentMediaId = display?.optString("current_media_id").takeIf { !it.isNullOrBlank() },
                    modeRevision = mode?.optLong("revision", 0L) ?: 0L,
                ),
            )
        }, onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
    )

    override suspend fun networkStatus(): LanTransportResult<LanNetworkStatus> = client.get("/api/v1/network/status").fold(
        onSuccess = { root ->
            val data = root.getJSONObject("data")
            val ap = data.optJSONObject("ap")
            val sta = data.optJSONObject("sta")
            val internet = data.optJSONObject("internet")
            val reconnect = data.optJSONObject("reconnect")
            val connected = sta?.optBoolean("connected", false) == true
            LanTransportResult.Success(
                LanNetworkStatus(
                    apiVersion = data.optString("api_version", "v1"),
                    deviceId = data.optString("device_id").takeIf { it.isNotBlank() },
                    revision = data.optLong("revision", 0L),
                    apEnabled = ap?.optBoolean("enabled", false) == true,
                    apSsid = ap?.optString("ssid").takeIf { !it.isNullOrBlank() },
                    apIp = ap?.optString("ip").takeIf { !it.isNullOrBlank() },
                    apChannel = ap?.let { it.optInt("channel").takeIf { channel -> channel > 0 } },
                    apConnectedClients = ap?.let { it.optInt("connected_clients").takeIf { clients -> clients >= 0 } },
                    staEnabled = sta?.let { it.optBoolean("enabled", it.optBoolean("configured", false)) } == true,
                    staState = sta?.optString("state")?.takeIf { it.isNotBlank() }
                        ?: if (connected) "connected" else if (sta?.optBoolean("configured", false) == true) "connecting" else "disabled",
                    staSsid = sta?.optString("ssid").takeIf { !it.isNullOrBlank() },
                    staIp = sta?.optString("ip").takeIf { !it.isNullOrBlank() },
                    staGateway = sta?.optString("gateway").takeIf { !it.isNullOrBlank() },
                    staRssiDbm = sta?.let { it.optInt("rssi_dbm").takeIf { rssi -> rssi != 0 } },
                    staLastErrorCode = sta?.optString("last_error_code").takeIf { !it.isNullOrBlank() },
                    staLastErrorMessage = sta?.optString("last_error_message").takeIf { !it.isNullOrBlank() },
                    internetState = internet?.optString("state")?.takeIf { it.isNotBlank() } ?: "unknown",
                    reconnectInProgress = reconnect?.optBoolean("in_progress", false) == true,
                    reconnectAttempt = reconnect?.let { it.optInt("attempt").takeIf { attempt -> attempt >= 0 } },
                    reconnectBackoffSeconds = reconnect?.let { it.optInt("backoff_seconds").takeIf { seconds -> seconds >= 0 } },
                ),
            )
        }, onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
    )

    override suspend fun listMedia(category: DeviceMediaCategory, cursor: String?, limit: Int): LanTransportResult<DeviceMediaPage> {
        if (limit !in 1..30) return LanTransportResult.Failure(DeviceRejection.Unsupported)
        val query = buildList {
            add("category=${category.apiValue}")
            if (!cursor.isNullOrBlank()) add("cursor=${java.net.URLEncoder.encode(cursor, StandardCharsets.UTF_8.name())}")
            add("limit=$limit")
        }.joinToString("&")
        return client.get("/api/v1/media?$query").fold(
            onSuccess = { root ->
                val data = root.getJSONObject("data")
                val rawItems = data.optJSONArray("items")
                val items = buildList {
                    for (index in 0 until (rawItems?.length() ?: 0)) {
                        rawItems?.optJSONObject(index)?.let(::parseMediaItem)?.let(::add)
                    }
                }
                LanTransportResult.Success(DeviceMediaPage(items, data.optString("next_cursor").takeIf { it.isNotBlank() }, data.optLong("revision", 0L)))
            }, onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
        )
    }

    override suspend fun mediaDetail(mediaId: String): LanTransportResult<DeviceMediaItem> =
        safeMediaId(mediaId)?.let { safeId ->
            client.get("/api/v1/media/$safeId").fold(
                onSuccess = { root -> parseMediaItem(root.getJSONObject("data"))?.let { LanTransportResult.Success<DeviceMediaItem>(it) }
                    ?: LanTransportResult.Failure(DeviceRejection.Unsupported) },
                onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
            )
        } ?: LanTransportResult.Failure(DeviceRejection.Unsupported)

    override suspend fun downloadMediaSource(mediaId: String, destination: File): LanTransportResult<DeviceMediaSource> {
        val safeId = safeMediaId(mediaId) ?: return LanTransportResult.Failure(DeviceRejection.Unsupported)
        return client.downloadToFile("/api/v1/media/$safeId/source", destination).fold(
            onSuccess = { file -> LanTransportResult.Success(DeviceMediaSource(safeId, file.mimeType, file.sizeBytes, file.eTag, destination)) },
            onFailure = { LanTransportResult.Failure(DeviceRejection.Offline) },
        )
    }

    override suspend fun uploadMedia(request: DeviceMediaUploadRequest): LanTransportResult<DeviceJobSnapshot> =
        client.uploadBinOnly(request).fold(
            onSuccess = { root -> parseJob(root.optJSONObject("data"))?.let { LanTransportResult.Success(it) }
                ?: LanTransportResult.Failure(DeviceRejection.Unsupported) },
            onFailure = { error -> LanTransportResult.Failure(rejectionFromError(error)) },
        )

    override suspend fun jobStatus(jobId: DeviceJobId): LanTransportResult<DeviceJobSnapshot> =
        client.get("/api/v1/jobs/${jobId.value}").fold(
            onSuccess = { root -> parseJob(root.optJSONObject("data"))?.let { LanTransportResult.Success(it) }
                ?: LanTransportResult.Failure(DeviceRejection.Unsupported) },
            onFailure = { error -> LanTransportResult.Failure(rejectionFromError(error)) },
        )

    override suspend fun displayMedia(
        mediaId: String,
        requestId: String,
        expectedModeRevision: Long,
        afterDisplay: String,
    ): LanTransportResult<DeviceJobSnapshot> {
        val safeId = safeMediaId(mediaId) ?: return LanTransportResult.Failure(DeviceRejection.Unsupported)
        if (afterDisplay !in setOf("continue", "hold")) return LanTransportResult.Failure(DeviceRejection.Unsupported)
        return client.postJson(
            "/api/v1/media/$safeId/display",
            JSONObject()
                .put("request_id", requestId)
                .put("expected_mode_revision", expectedModeRevision)
                .put("after_display", afterDisplay),
        ).fold(
            onSuccess = { root -> parseJob(root.optJSONObject("data"))?.let { LanTransportResult.Success(it) }
                ?: LanTransportResult.Failure(DeviceRejection.Unsupported) },
            onFailure = { error -> LanTransportResult.Failure(rejectionFromError(error)) },
        )
    }

    override suspend fun deleteMedia(mediaId: String, requestId: String, expectedRevision: Long): LanTransportResult<Unit> {
        val safeId = safeMediaId(mediaId) ?: return LanTransportResult.Failure(DeviceRejection.Unsupported)
        return client.deleteJson(
            "/api/v1/media/$safeId",
            JSONObject().put("request_id", requestId).put("expected_revision", expectedRevision),
        ).fold(
            onSuccess = { LanTransportResult.Success(Unit) },
            onFailure = { error -> LanTransportResult.Failure(rejectionFromError(error)) },
        )
    }

    override suspend fun localAlbumPlayback(): PlaybackTransportResult =
        client.get("/api/v1/local-album/playback").fold(
            onSuccess = { root -> parsePlayback(root.optJSONObject("data"))?.let(PlaybackTransportResult::Success)
                ?: PlaybackTransportResult.Failure(DeviceRejection.Unsupported) },
            onFailure = { error -> PlaybackTransportResult.Failure(rejectionFromError(error)) },
        )

    override suspend fun saveLocalAlbumPlayback(
        requestId: String,
        expectedRevision: Long,
        mode: String,
        intervalSeconds: Int,
        order: String,
    ): PlaybackTransportResult {
        if (mode !in setOf("auto", "paused") || order !in setOf("sequential", "random") ||
            intervalSeconds !in setOf(300, 900, 1800, 3600, 10800, 21600, 43200, 86400)
        ) return PlaybackTransportResult.Failure(DeviceRejection.Unsupported)
        return client.postJson(
            "/api/v1/local-album/playback",
            JSONObject()
                .put("request_id", requestId)
                .put("expected_revision", expectedRevision)
                .put("mode", mode)
                .put("interval_seconds", intervalSeconds)
                .put("order", order),
        ).fold(
            onSuccess = { root -> parsePlayback(root.optJSONObject("data"))?.let(PlaybackTransportResult::Success)
                ?: PlaybackTransportResult.Failure(DeviceRejection.Unsupported) },
            onFailure = { error ->
                // DevelopmentApHttpClient retains the ESP's JSON error code in the exception
                // message. Re-read the authority snapshot after a revision conflict.
                if (error.message?.contains("revision_conflict", ignoreCase = true) == true) {
                    when (val latest = localAlbumPlayback()) {
                        is PlaybackTransportResult.Success -> PlaybackTransportResult.RevisionConflict(latest.snapshot)
                        else -> PlaybackTransportResult.RevisionConflict(null)
                    }
                } else PlaybackTransportResult.Failure(rejectionFromError(error))
            },
        )
    }


    override suspend fun switchFeature(feature: DeviceFeature, requestId: String): LanTransportResult<DeviceJobId> =
        LanTransportResult.Failure(DeviceRejection.Unsupported)

    private fun safeMediaId(mediaId: String): String? = mediaId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }

    private fun featureFromApi(raw: Any?): DeviceFeature = when (raw) {
        0, "0", "local_album" -> DeviceFeature.LocalAlbum
        1, "1", "ai_album" -> DeviceFeature.AiAlbum
        2, "2", "info_dashboard" -> DeviceFeature.InfoDashboard
        else -> DeviceFeature.LocalAlbum
    }

    private fun parseMediaItem(raw: JSONObject): DeviceMediaItem? {
        val mediaId = raw.optString("media_id").takeIf { it.isNotBlank() } ?: return null
        val category = DeviceMediaCategory.fromApi(raw.optString("category")) ?: return null
        val profile = raw.optJSONObject("display_profile") ?: return null
        val width = profile.optInt("width", 0)
        val height = profile.optInt("height", 0)
        val frameBytes = profile.optInt("frame_bytes", 0)
        if (width <= 0 || height <= 0 || frameBytes <= 0) return null
        return DeviceMediaItem(
            mediaId = mediaId,
            displayName = raw.optString("display_name").trim().ifBlank { "未命名图片" },
            category = category,
            createdAtEpochMillis = raw.optLong("created_at_ms", 0L),
            updatedAtEpochMillis = raw.optLong("updated_at_ms", 0L),
            displayProfile = DeviceMediaDisplayProfile(width, height, frameBytes, profile.optString("pixel_format"), profile.optString("palette"), profile.optString("orientation"), profile.optInt("rotation_degrees", 0), profile.optString("fit_mode"), profile.optString("converter_version").takeIf { it.isNotBlank() }),
            source = parseAsset(raw.optJSONObject("source")),
            preview = parseAsset(raw.optJSONObject("preview")),
            imageBin = parseAsset(raw.optJSONObject("image_bin") ?: raw.optJSONObject("frame")),
            manifestVersion = raw.optInt("manifest_version", 0),
            revision = raw.optLong("revision", 0L),
        )
    }

    private fun parseAsset(raw: JSONObject?): DeviceMediaAsset = DeviceMediaAsset(
        present = raw?.optBoolean("present", false) == true,
        mimeType = raw?.optString("mime_type").takeIf { !it.isNullOrBlank() },
        sizeBytes = raw?.let { it.optLong("size_bytes", it.optLong("bytes", -1L)).takeIf { bytes -> bytes >= 0L } },
        sha256 = raw?.optString("sha256").takeIf { !it.isNullOrBlank() },
    )

    private fun parseJob(raw: JSONObject?): DeviceJobSnapshot? {
        val data = raw ?: return null
        val jobId = data.optString("job_id").takeIf { it.isNotBlank() } ?: return null
        return DeviceJobSnapshot(
            jobId = DeviceJobId(jobId),
            state = when (data.optInt("state", -1)) {
                0 -> DeviceJobState.Queued
                1 -> DeviceJobState.Running
                2 -> DeviceJobState.Success
                3 -> DeviceJobState.Failed
                4 -> DeviceJobState.Cancelled
                5 -> DeviceJobState.TimedOut
                else -> DeviceJobState.Failed
            },
            phase = data.optString("phase").ifBlank { "unknown" },
            progressPercent = data.optInt("progress_percent", 0).coerceIn(0, 100),
            errorCode = data.optString("error_code").takeIf { it.isNotBlank() },
            mediaId = data.optString("media_id").takeIf { it.isNotBlank() },
        )
    }

    private fun parsePlayback(raw: JSONObject?): DevicePlaybackSnapshot? {
        val data = raw ?: return null
        val mode = data.optString("mode")
        val order = data.optString("order")
        val interval = data.optInt("interval_seconds", -1)
        if (mode !in setOf("auto", "paused") || order !in setOf("sequential", "random") ||
            interval !in setOf(300, 900, 1800, 3600, 10800, 21600, 43200, 86400)
        ) return null
        return DevicePlaybackSnapshot(
            mode = mode,
            intervalSeconds = interval,
            order = order,
            currentMediaId = data.optString("current_media_id").takeIf { it.isNotBlank() },
            // New firmware reports a countdown because its scheduler clock is monotonic, not
            // Unix time. Never render that monotonic value as a wall-clock timestamp.
            nextPlayInSeconds = data.optLong("next_play_in_seconds", -1L).takeIf { it >= 0L },
            // Old firmware may expose a monotonic next_play_at_ms. Only accept an unambiguous
            // Unix epoch millisecond value; otherwise the UI will say it is waiting to sync.
            nextPlayAtEpochMillis = data.optLong("next_play_at_ms", -1L)
                .takeIf { it >= 1_577_836_800_000L },
            revision = data.optLong("revision", 0L),
            stateRevision = data.optLong("state_revision", 0L),
        )
    }

    private fun rejectionFromError(error: Throwable): DeviceRejection = when {
        error.message?.contains("storage_no_space", ignoreCase = true) == true -> DeviceRejection.StorageNoSpace
        error.message?.contains("source_too_large", ignoreCase = true) == true || error.message?.contains("request_too_large", ignoreCase = true) == true -> DeviceRejection.SourceTooLarge
        error.message?.contains("storage", ignoreCase = true) == true -> DeviceRejection.StorageUnavailable
        error.message?.contains("display_busy", ignoreCase = true) == true -> DeviceRejection.DisplayBusy
        error.message?.contains("media_protected", ignoreCase = true) == true -> DeviceRejection.MediaProtected
        error.message?.contains("unsupported", ignoreCase = true) == true -> DeviceRejection.Unsupported
        else -> DeviceRejection.Offline
    }
}
