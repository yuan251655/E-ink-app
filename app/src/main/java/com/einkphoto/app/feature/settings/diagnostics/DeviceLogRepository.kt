package com.einkphoto.app.feature.settings.diagnostics

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceLogEntry(
    val uptimeMs: Long,
    val severity: LogSeverity,
    val component: String,
    val code: String,
    val message: String,
)

enum class LogSeverity { Info, Warning, Error }

class LanDeviceLogRepository(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) {
    private val mutableEntries = MutableStateFlow<List<DeviceLogEntry>>(emptyList())
    val entries: StateFlow<List<DeviceLogEntry>> = mutableEntries.asStateFlow()

    suspend fun refresh(): Result<Unit> = client.get("/api/v1/logs?limit=30").mapCatching { root ->
        val source = root.optJSONObject("data")?.optJSONArray("entries") ?: error("invalid_log_response")
        mutableEntries.value = buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val severity = when (item.optString("severity").lowercase()) {
                    "warning" -> LogSeverity.Warning
                    "error" -> LogSeverity.Error
                    else -> LogSeverity.Info
                }
                add(DeviceLogEntry(
                    uptimeMs = item.optLong("uptime_ms", 0L).coerceAtLeast(0L),
                    severity = severity,
                    component = item.optString("component").ifBlank { "system" },
                    code = item.optString("code").ifBlank { "event" },
                    message = item.optString("message").ifBlank { "设备状态已更新" },
                ))
            }
        }
    }
}
