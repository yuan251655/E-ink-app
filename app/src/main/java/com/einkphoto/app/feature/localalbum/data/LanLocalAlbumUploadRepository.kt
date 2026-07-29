package com.einkphoto.app.feature.localalbum.data

import android.content.Context
import android.net.Uri
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceMediaDisplayProfile
import com.einkphoto.app.core.device.DeviceMediaUploadRequest
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceTransport
import com.einkphoto.app.core.device.LanTransportResult
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Writes a verified phone-private source + candidate BIN through the frozen phase-0 contract. */
class LanLocalAlbumUploadRepository(
    private val context: Context,
    private val transport: LanDeviceTransport = HttpLanDeviceTransport(),
) : UploadRepository {
    private companion object {
        // Firmware accepts at most 5 MiB of original-image bytes; the remaining
        // request budget is reserved for the fixed 192 KiB display frame and metadata.
        const val MAX_SOURCE_BYTES = 5L * 1024L * 1024L
    }

    override suspend fun submit(
        draft: ConversionDraft,
        mode: UploadMode,
        requestId: String,
    ): DeviceCommandResult<com.einkphoto.app.core.device.DeviceJobId> {
        if (mode != UploadMode.SourceAndBin || draft.generatedFrameBytes != 192_000 || !draft.localValidationPassed) {
            return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        }
        if (requestId.isBlank()) return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        val sourceFile = privateFileFromUri(draft.source.contentUri) ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        val binFile = privateFileFromUri(draft.candidateBinUri ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported))
            ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        val sourceMimeType = mimeTypeFor(sourceFile.name) ?: return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        if (binFile.length() != 192_000L || sourceFile.length() <= 0L) return DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        // Reject locally before opening the HTTP request.  Without this guard, a
        // 71 MB phone photo is streamed over Wi-Fi only for the ESP to reject it.
        if (sourceFile.length() > MAX_SOURCE_BYTES) return DeviceCommandResult.Rejected(DeviceRejection.SourceTooLarge)

        val sourceStartsLandscape = draft.source.widthPx >= draft.source.heightPx
        val sourceEndsLandscape = if (draft.quarterTurnsClockwise % 2 == 0) {
            sourceStartsLandscape
        } else {
            !sourceStartsLandscape
        }
        val request = DeviceMediaUploadRequest(
            requestId = requestId.take(64),
            displayName = safeDisplayName(draft.source.displayName),
            sourceFile = sourceFile,
            sourceMimeType = sourceMimeType,
            sourceSizeBytes = sourceFile.length(),
            sourceSha256 = sha256(sourceFile),
            imageBinFile = binFile,
            imageBinSizeBytes = binFile.length(),
            imageBinSha256 = sha256(binFile),
            displayProfile = DeviceMediaDisplayProfile(
                widthPx = draft.profile.widthPx,
                heightPx = draft.profile.heightPx,
                frameBytes = draft.profile.frameBytes,
                pixelFormat = "4bpp",
                palette = "six_color_e6",
                // Preserve the source orientation after the user's explicit rotation.
                // The fixed 800x480 output size alone cannot describe this intent.
                orientation = if (sourceEndsLandscape) "landscape" else "portrait",
                rotationDegrees = draft.quarterTurnsClockwise * 90,
                fitMode = if (draft.fitMode.name == "CropToFill") "cover" else "contain",
                converterVersion = draft.algorithmVersion,
            ),
        )
        // The ESP HTTP server releases its single request slot shortly after the response is
        // written.  A lost response is safe to retry with the same request_id: the device's
        // idempotency ledger returns the already-created job instead of storing a duplicate.
        val accepted = when (val result = uploadWithRecovery(request)) {
            is LanTransportResult.Success -> result.value
            is LanTransportResult.Failure -> return DeviceCommandResult.Rejected(result.rejection)
        }
        val completed = awaitTerminalJob(accepted.jobId) ?: return DeviceCommandResult.Rejected(DeviceRejection.Offline)
        return if (completed.state == DeviceJobState.Success && !completed.mediaId.isNullOrBlank()) {
            DeviceCommandResult.Accepted(completed.jobId)
        } else {
            DeviceCommandResult.Rejected(rejectionFor(completed.errorCode))
        }
    }

    private suspend fun uploadWithRecovery(request: DeviceMediaUploadRequest): LanTransportResult<com.einkphoto.app.core.device.DeviceJobSnapshot> {
        // The ESP owns a single HTTP worker and may be briefly unavailable while it
        // closes the preceding TF-backed multipart session. Reuse request_id: if a
        // response was lost after admission, the device returns the same job instead
        // of writing a duplicate media item.
        var result = transport.uploadMedia(request)
        val delaysMs = longArrayOf(2_000L, 5_000L, 10_000L)
        for (delayMs in delaysMs) {
            if (result !is LanTransportResult.Failure ||
                (result.rejection != DeviceRejection.Offline && result.rejection != DeviceRejection.StorageUnavailable)) break
            delay(delayMs)
            result = transport.uploadMedia(request)
        }
        return result
    }

    private suspend fun awaitTerminalJob(
        jobId: com.einkphoto.app.core.device.DeviceJobId,
    ): com.einkphoto.app.core.device.DeviceJobSnapshot? = withTimeoutOrNull(90_000L) {
        while (true) {
            when (val result = transport.jobStatus(jobId)) {
                is LanTransportResult.Success -> when (result.value.state) {
                    DeviceJobState.Success,
                    DeviceJobState.Failed,
                    DeviceJobState.Cancelled,
                    DeviceJobState.TimedOut -> return@withTimeoutOrNull result.value
                    else -> delay(500L)
                }
                is LanTransportResult.Failure -> return@withTimeoutOrNull null
            }
        }
        error("unreachable")
    }

    private fun privateFileFromUri(rawUri: String): File? = runCatching {
        val file = File(requireNotNull(Uri.parse(rawUri).path)).canonicalFile
        val privateRoot = context.filesDir.canonicalFile.path + File.separator
        require(file.path.startsWith(privateRoot) && file.isFile) { "source is not an App-private file" }
        file
    }.getOrNull()

    private fun mimeTypeFor(filename: String): String? = when (filename.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> null
    }

    private fun safeDisplayName(value: String): String = value
        .replace('/', '_').replace('\\', '_')
        .filter { it.code >= 0x20 && it.code != 0x7f }
        .trim().take(128).ifBlank { "未命名图片" }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun rejectionFor(errorCode: String?): DeviceRejection = when (errorCode) {
        "storage_no_space" -> DeviceRejection.StorageNoSpace
        "storage_unavailable", "storage_busy" -> DeviceRejection.StorageUnavailable
        else -> DeviceRejection.Unsupported
    }
}
