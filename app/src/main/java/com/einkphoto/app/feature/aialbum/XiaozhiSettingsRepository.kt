package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.core.device.DevelopmentApHttpClient

/** Device-owned settings surface for the official Xiaozhi voice service.
 * It intentionally exposes status only; no conversation or text input API exists in the App
 * while the official text protocol is not part of the product. */
data class XiaozhiSettingsStatus(
    val state: String = "unknown",
    val started: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val activationRequired: Boolean = false,
    val lastErrorCode: String? = null,
)

class XiaozhiSettingsRepository(private val client: DevelopmentApHttpClient = DevelopmentApHttpClient()) {
    suspend fun status(): Result<XiaozhiSettingsStatus> = client.get("/api/v1/xiaozhi/status").mapCatching { root ->
        val data = root.getJSONObject("data")
        XiaozhiSettingsStatus(
            state = data.optString("state", "unknown"),
            started = data.optBoolean("started", false),
            wakeWordEnabled = data.optBoolean("wake_word_enabled", false),
            activationRequired = data.optBoolean("activation_required", false),
            lastErrorCode = data.optString("last_error_code").takeIf { it.isNotBlank() },
        )
    }
}
