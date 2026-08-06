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

    override suspend fun refresh(): PowerActionResult = client.get(STATUS_PATH).fold(
        onSuccess = { root ->
            parse(root.optJSONObject("data"))?.let { snapshot ->
                mutableSnapshot.value = snapshot
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

    private fun parse(data: JSONObject?): PowerSnapshot? {
        data ?: return null
        if (!data.has("pmic_online")) return null
        val usb = data.optJSONObject("usb")
        val battery = data.optJSONObject("battery")
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

    private companion object { const val STATUS_PATH = "/api/v1/power/status" }
}
