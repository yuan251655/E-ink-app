package com.einkphoto.app.feature.settings.power

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Real-device adapter for the product's read-only `/api/v1/power/status` contract. */
class LanPowerRepository(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) : PowerRepository {
    private val mutableSnapshot = MutableStateFlow(PowerSnapshot())
    override val snapshot: StateFlow<PowerSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh(): PowerActionResult = client.get(STATUS_PATH, fastProbe = true).fold(
        onSuccess = { root ->
            parse(root.optJSONObject("data"))?.let { snapshot ->
                val sleep = client.get(SLEEP_CONFIG_PATH, fastProbe = true).getOrNull()?.optJSONObject("data")
                val sleepStatus = client.get(SLEEP_STATUS_PATH, fastProbe = true).getOrNull()?.optJSONObject("data")
                mutableSnapshot.value = snapshot.copy(
                    automaticSleepEnabled = sleep?.optBoolean("enabled", false) ?: false,
                    idleTimeoutSeconds = sleep?.let(::idleTimeoutSeconds) ?: 15 * 60,
                    wakeForPlayback = sleep?.optBoolean("wake_for_playback", true) ?: true,
                    automaticSleepState = sleepStatus?.optString("state", "unknown") ?: "unknown",
                    idleSleepAtEpochMillis = sleepStatus?.optLong("idle_sleep_at_epoch_ms", 0L)?.takeIf { it > 0L },
                    nextPlaybackAtEpochMillis = sleepStatus?.optLong("next_play_at_epoch_ms", 0L)?.takeIf { it > 0L },
                )
                PowerActionResult.Accepted
            } ?: run {
                mutableSnapshot.value = mutableSnapshot.value.copy(lastErrorMessage = "设备返回的电源状态不完整，请稍后重试")
                PowerActionResult.Rejected("invalid_power_status", "设备返回的电源状态不完整，请稍后重试")
            }
        },
        onFailure = {
            mutableSnapshot.value = mutableSnapshot.value.copy(lastErrorMessage = "无法读取电池状态，请确认已连接相框")
            PowerActionResult.Rejected("device_unreachable", "无法读取电池状态，请确认已连接相框")
        },
    )

    override suspend fun saveAutomaticSleep(
        enabled: Boolean,
        idleTimeoutSeconds: Int,
        wakeForPlayback: Boolean,
    ): PowerActionResult {
        if (idleTimeoutSeconds !in ALLOWED_IDLE_TIMEOUTS) return PowerActionResult.Rejected("invalid_sleep_config", "休眠时间无效")
        return client.postJson(
            SLEEP_CONFIG_PATH,
            JSONObject()
                .put("enabled", enabled)
                .put("idle_timeout_seconds", idleTimeoutSeconds)
                .put("wake_for_playback", wakeForPlayback),
        ).fold(
            onSuccess = { root ->
                val sleep = root.optJSONObject("data")
                    ?: return@fold PowerActionResult.Rejected("invalid_sleep_config", "设备返回的休眠设置不完整")
                mutableSnapshot.value = mutableSnapshot.value.copy(
                    automaticSleepEnabled = sleep.optBoolean("enabled", false),
                    idleTimeoutSeconds = idleTimeoutSeconds(sleep),
                    wakeForPlayback = sleep.optBoolean("wake_for_playback", true),
                    lastErrorMessage = null,
                )
                PowerActionResult.Accepted
            },
            onFailure = {
                mutableSnapshot.value = mutableSnapshot.value.copy(lastErrorMessage = "无法保存休眠设置，请确认相框已连接")
                PowerActionResult.Rejected("device_unreachable", "无法保存休眠设置，请确认相框已连接")
            },
        )
    }

    override suspend fun saveBatteryDisplay(enabled: Boolean, expectedRevision: Long): PowerActionResult =
        client.postJson(
            BATTERY_DISPLAY_PATH,
            JSONObject()
                .put("enabled", enabled)
                .put("expected_revision", expectedRevision),
        ).fold(
            onSuccess = { root ->
                val config = root.optJSONObject("data")
                    ?: return@fold PowerActionResult.Rejected("invalid_battery_display", "设备返回的电子纸电量显示设置不完整")
                mutableSnapshot.value = mutableSnapshot.value.copy(
                    batteryDisplayEnabled = config.optBoolean("enabled", true),
                    batteryDisplayRevision = config.optLong("revision", expectedRevision),
                    lastErrorMessage = null,
                )
                PowerActionResult.Accepted
            },
            onFailure = {
                mutableSnapshot.value = mutableSnapshot.value.copy(lastErrorMessage = "无法保存电子纸电量显示设置，请确认相框已连接")
                PowerActionResult.Rejected("device_unreachable", "无法保存电子纸电量显示设置，请确认相框已连接")
            },
        )

    private fun parse(data: JSONObject?): PowerSnapshot? {
        data ?: return null
        if (!data.has("pmic_online")) return null
        val usb = data.optJSONObject("usb")
        val battery = data.optJSONObject("battery")
        val batteryDisplay = data.optJSONObject("battery_display")
        val rtcBackup = data.optJSONObject("rtc_backup")
        val policy = data.optJSONObject("policy")
        return PowerSnapshot(
            pmicOnline = data.optBoolean("pmic_online", false),
            usbPresent = usb?.optBoolean("present", false) ?: false,
            usbVoltageMv = positiveInt(usb, "voltage_mv"),
            systemVoltageMv = positiveInt(data, "system_voltage_mv"),
            batteryPresent = battery?.optBoolean("present", false) ?: false,
            batteryVoltageMv = positiveInt(battery, "voltage_mv"),
            batteryPercent = battery?.optInt("percent", -1)?.takeIf { it in 0..100 },
            batteryDisplayEnabled = batteryDisplay?.optBoolean("enabled", true) ?: true,
            batteryDisplayRevision = batteryDisplay?.optLong("revision", 0L) ?: 0L,
            charging = battery?.optBoolean("charging", false) ?: false,
            discharging = battery?.optBoolean("discharging", false) ?: false,
            chargerState = battery?.optString("charger_state", "unknown")?.ifBlank { "unknown" } ?: "unknown",
            configuredCurrentMa = positiveInt(battery, "configured_current_ma"),
            targetVoltageMv = positiveInt(battery, "target_voltage_mv"),
            terminationCurrentMa = positiveInt(battery, "termination_current_ma"),
            terminationEnabled = battery?.optBoolean("termination_enabled", false) ?: false,
            rtcBackupChargeEnabled = rtcBackup?.optBoolean("charge_enabled", false) ?: false,
            deepSleepEnabled = policy?.optBoolean("deep_sleep_enabled", false) ?: false,
        )
    }

    private fun positiveInt(value: JSONObject?, key: String): Int? =
        value?.optInt(key, -1)?.takeIf { it > 0 }

    private fun idleTimeoutSeconds(value: JSONObject): Int =
        value.optInt("idle_timeout_seconds", value.optInt("idle_timeout_minutes", 15) * 60)
            .takeIf { it in ALLOWED_IDLE_TIMEOUTS } ?: 15 * 60

    private companion object {
        const val STATUS_PATH = "/api/v1/power/status"
        const val BATTERY_DISPLAY_PATH = "/api/v1/power/battery-display"
        const val SLEEP_CONFIG_PATH = "/api/v1/power/sleep-config"
        const val SLEEP_STATUS_PATH = "/api/v1/power/sleep-status"
        val ALLOWED_IDLE_TIMEOUTS = setOf(10, 60, 120, 300, 600, 900, 1800, 3600)
    }
}
