package com.einkphoto.app.core.device

import java.io.File

/**
 * Versioned `/api/v1` data owned by the PhotoPainter product API.
 *
 * These types intentionally contain no TF paths, GPIO values, or legacy endpoint DTOs.
 * UI-facing feature repositories can map them to their own presentation models later.
 */
enum class DeviceMediaCategory(val apiValue: String) {
    Local("local"),
    Ai("ai"),
    Dashboard("dashboard"),
    System("system");

    companion object {
        fun fromApi(value: String): DeviceMediaCategory? = entries.firstOrNull { it.apiValue == value }
    }
}

data class DeviceMediaAsset(
    val present: Boolean,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
)

data class DeviceMediaDisplayProfile(
    val widthPx: Int,
    val heightPx: Int,
    val frameBytes: Int,
    val pixelFormat: String,
    val palette: String,
    val orientation: String,
    val rotationDegrees: Int,
    val fitMode: String,
    val converterVersion: String? = null,
)

data class DeviceMediaItem(
    val mediaId: String,
    val displayName: String,
    val category: DeviceMediaCategory,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val displayProfile: DeviceMediaDisplayProfile,
    val source: DeviceMediaAsset,
    val preview: DeviceMediaAsset,
    val imageBin: DeviceMediaAsset,
    val manifestVersion: Int,
    val revision: Long,
) {
    init {
        require(mediaId.isNotBlank())
        require(displayName.isNotBlank())
    }
}

data class DeviceMediaPage(
    val items: List<DeviceMediaItem>,
    val nextCursor: String?,
    val revision: Long,
)

/** Result of a streamed `/media/{id}/source` download into App-private cache storage. */
data class DeviceMediaSource(
    val mediaId: String,
    val mimeType: String,
    val sizeBytes: Long,
    val eTag: String?,
    val cachedFile: File,
)

/** Fully validated, App-private files used for one `source_plus_bin` admission request. */
data class DeviceMediaUploadRequest(
    val requestId: String,
    val displayName: String,
    val sourceFile: File,
    val sourceMimeType: String,
    val sourceSizeBytes: Long,
    val sourceSha256: String,
    val imageBinFile: File,
    val imageBinSizeBytes: Long,
    val imageBinSha256: String,
    val displayProfile: DeviceMediaDisplayProfile,
)

data class DeviceJobSnapshot(
    val jobId: DeviceJobId,
    val state: DeviceJobState,
    val phase: String,
    val progressPercent: Int,
    val errorCode: String?,
    val mediaId: String?,
)

data class LanNetworkStatus(
    val apiVersion: String,
    val deviceId: String?,
    val revision: Long,
    val apEnabled: Boolean,
    val apSsid: String?,
    val apIp: String?,
    val apChannel: Int?,
    val apConnectedClients: Int?,
    val staEnabled: Boolean,
    val staState: String,
    val staSsid: String?,
    val staIp: String?,
    val staGateway: String?,
    val staRssiDbm: Int?,
    val staLastErrorCode: String?,
    val staLastErrorMessage: String?,
    val internetState: String,
    val reconnectInProgress: Boolean,
    val reconnectAttempt: Int?,
    val reconnectBackoffSeconds: Int?,
)
