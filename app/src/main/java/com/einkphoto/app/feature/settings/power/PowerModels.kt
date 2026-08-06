package com.einkphoto.app.feature.settings.power

/** Read-only projection of the device-owned AXP2101 power snapshot. */
data class PowerSnapshot(
    val pmicOnline: Boolean = false,
    val usbPresent: Boolean = false,
    val usbVoltageMv: Int? = null,
    val systemVoltageMv: Int? = null,
    val batteryPresent: Boolean = false,
    val batteryVoltageMv: Int? = null,
    /** Fuel-gauge estimate only; it is not a calibrated capacity measurement. */
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val discharging: Boolean = false,
    val chargerState: String = "unknown",
    val configuredCurrentMa: Int? = null,
    val targetVoltageMv: Int? = null,
    val terminationCurrentMa: Int? = null,
    val terminationEnabled: Boolean = false,
    val rtcBackupChargeEnabled: Boolean = false,
    val deepSleepEnabled: Boolean = false,
    val lastErrorMessage: String? = null,
)

sealed interface PowerActionResult {
    data object Accepted : PowerActionResult
    data class Rejected(val code: String, val message: String) : PowerActionResult
}
