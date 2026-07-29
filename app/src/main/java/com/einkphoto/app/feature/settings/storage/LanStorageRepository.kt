package com.einkphoto.app.feature.settings.storage

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceStorageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

/** Real-device adapter for the versioned StorageService health contract. */
class LanStorageRepository(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) : StorageRepository {
    private val mutableSnapshot = MutableStateFlow(StorageSnapshot())
    override val snapshot: StateFlow<StorageSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh(): StorageActionResult = client.get(STATUS_PATH).fold(
        onSuccess = { root ->
            parseStatus(root.optJSONObject("data"))?.let { status ->
                mutableSnapshot.value = status.toSnapshot()
                StorageActionResult.Accepted
            } ?: StorageActionResult.Rejected("invalid_storage_status", "设备返回的 TF 卡状态不完整，请稍后重试")
        },
        onFailure = { error -> error.toActionResult("无法读取 TF 卡状态，请确认已连接相框") },
    )

    override suspend fun remount(): StorageActionResult = client.postJson(
        REMOUNT_PATH,
        JSONObject().put("request_id", UUID.randomUUID().toString()),
    ).fold(
        onSuccess = { root ->
            // Firmware returns the new snapshot in the accepted response.  If an older build only
            // returns an acknowledgement, read it once more instead of inventing a healthy state.
            parseStatus(root.optJSONObject("data"))?.let { status ->
                mutableSnapshot.value = status.toSnapshot()
                StorageActionResult.Accepted
            } ?: refresh()
        },
        onFailure = { error -> error.toActionResult("无法重新检测 TF 卡") },
    )

    private fun parseStatus(data: JSONObject?): DeviceStorageStatus? {
        data ?: return null
        val storage = data.optJSONObject("storage") ?: data // tolerate early development builds
        val state = storage.optString("state").lowercase()
        if (state !in supportedStates) return null
        val mounted = storage.optBoolean("mounted", false)
        val readable = storage.optBoolean("readable", false)
        val writable = storage.optBoolean("writable", false)
        val usage = storage.optJSONObject("usage")
        val localAlbum = usage?.optJSONObject("local")
        val staging = usage?.optJSONObject("staging")
        val lastError = storage.optJSONObject("last_error")
        return DeviceStorageStatus(
            apiVersion = data.optString("api_version", "v1"),
            revision = storage.optLong("revision", 0L),
            state = state,
            mounted = mounted,
            readable = readable,
            writable = writable,
            totalBytes = nonNegativeLong(storage, "total_bytes"),
            freeBytes = nonNegativeLong(storage, "free_bytes"),
            localAlbumItemCount = nonNegativeInt(localAlbum, "item_count"),
            localAlbumUsageBytes = nonNegativeLong(localAlbum, "bytes"),
            stagingItemCount = nonNegativeInt(staging, "item_count"),
            stagingUsageBytes = nonNegativeLong(staging, "bytes"),
            lastErrorCode = lastError?.optString("code")?.trim()?.takeIf { it.isNotEmpty() },
            lastErrorMessage = lastError?.optString("message")?.trim()?.takeIf { it.isNotEmpty() },
            lastCheckAgeSeconds = nonNegativeLong(storage, "last_check_age_seconds"),
            lastRemountAgeSeconds = nonNegativeLong(storage, "last_remount_age_seconds"),
        )
    }

    private fun DeviceStorageStatus.toSnapshot() = StorageSnapshot(
        apiVersion = apiVersion,
        revision = revision,
        health = when (state) {
            "ready" -> StorageHealth.Ready
            "degraded" -> StorageHealth.Degraded
            "missing" -> StorageHealth.Missing
            "error_backoff" -> StorageHealth.ErrorBackoff
            else -> StorageHealth.Unknown
        },
        mounted = mounted,
        readable = readable,
        writable = writable,
        totalBytes = totalBytes,
        freeBytes = freeBytes,
        localAlbumItemCount = localAlbumItemCount,
        localAlbumUsageBytes = localAlbumUsageBytes,
        stagingItemCount = stagingItemCount,
        stagingUsageBytes = stagingUsageBytes,
        lastErrorCode = lastErrorCode,
        lastErrorMessage = lastErrorMessage,
        lastCheckAgeSeconds = lastCheckAgeSeconds,
        lastRemountAgeSeconds = lastRemountAgeSeconds,
    )

    private fun Throwable.toActionResult(defaultMessage: String): StorageActionResult.Rejected {
        val message = message.orEmpty()
        return when {
            message.contains("storage_busy", ignoreCase = true) ->
                StorageActionResult.Rejected("storage_busy", "TF 卡正在使用中，请等待当前操作完成后再试")
            message.contains("storage_missing", ignoreCase = true) ->
                StorageActionResult.Rejected("storage_missing", "未检测到 TF 卡，请检查卡是否插好")
            message.contains("storage", ignoreCase = true) ->
                StorageActionResult.Rejected("storage_unavailable", "TF 卡暂时不可用，请稍后重试")
            else -> StorageActionResult.Rejected("device_unreachable", defaultMessage)
        }
    }

    private fun nonNegativeLong(value: JSONObject?, key: String): Long? =
        value?.optLong(key, -1L)?.takeIf { it >= 0L }

    private fun nonNegativeInt(value: JSONObject?, key: String): Int? =
        value?.optInt(key, -1)?.takeIf { it >= 0 }

    private companion object {
        const val STATUS_PATH = "/api/v1/storage/status"
        const val REMOUNT_PATH = "/api/v1/storage/remount"
        val supportedStates = setOf("ready", "degraded", "missing", "error_backoff")
    }
}
