package com.einkphoto.app.feature.settings.power

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-local calibration notebook. The PMIC remains the device authority for
 * voltage and charge state; this store only records user-confirmed reference
 * points and the observations made while this screen is open.
 */
class BatteryCalibrationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(load())
    val state: StateFlow<BatteryCalibrationState> = mutableState.asStateFlow()

    fun recordConfirmedFull(snapshot: PowerSnapshot): Boolean {
        val voltage = snapshot.batteryVoltageMv ?: return false
        if (!snapshot.batteryPresent || snapshot.chargerState != CHARGER_COMPLETED) return false
        val timestamp = System.currentTimeMillis()
        preferences.edit()
            .putInt(KEY_FULL_VOLTAGE_MV, voltage)
            .putLong(KEY_FULL_RECORDED_AT, timestamp)
            .putInt(KEY_OBSERVATION_COUNT, 0)
            .remove(KEY_LOWEST_DISCHARGE_VOLTAGE_MV)
            .remove(KEY_LAST_OBSERVATION_AT)
            .apply()
        mutableState.value = BatteryCalibrationState(fullVoltageMv = voltage, fullRecordedAtMillis = timestamp)
        return true
    }

    /** Collects a bounded local observation while the power page is visible. */
    fun observe(snapshot: PowerSnapshot) {
        val voltage = snapshot.batteryVoltageMv ?: return
        if (!snapshot.batteryPresent || !snapshot.discharging || mutableState.value.fullVoltageMv == null) return
        val now = System.currentTimeMillis()
        val current = mutableState.value
        if (now - current.lastObservationAtMillis < OBSERVATION_INTERVAL_MS) return
        val lowest = minOf(current.lowestDischargeVoltageMv ?: voltage, voltage)
        val next = current.copy(
            observationCount = current.observationCount + 1,
            lowestDischargeVoltageMv = lowest,
            lastObservationAtMillis = now,
        )
        preferences.edit()
            .putInt(KEY_OBSERVATION_COUNT, next.observationCount)
            .putInt(KEY_LOWEST_DISCHARGE_VOLTAGE_MV, lowest)
            .putLong(KEY_LAST_OBSERVATION_AT, now)
            .apply()
        mutableState.value = next
    }

    fun clear() {
        preferences.edit().clear().apply()
        mutableState.value = BatteryCalibrationState()
    }

    private fun load() = BatteryCalibrationState(
        fullVoltageMv = preferences.getInt(KEY_FULL_VOLTAGE_MV, -1).takeIf { it > 0 },
        fullRecordedAtMillis = preferences.getLong(KEY_FULL_RECORDED_AT, 0L).takeIf { it > 0L },
        observationCount = preferences.getInt(KEY_OBSERVATION_COUNT, 0),
        lowestDischargeVoltageMv = preferences.getInt(KEY_LOWEST_DISCHARGE_VOLTAGE_MV, -1).takeIf { it > 0 },
        lastObservationAtMillis = preferences.getLong(KEY_LAST_OBSERVATION_AT, 0L),
    )

    private companion object {
        const val PREFERENCES_NAME = "battery_calibration"
        const val KEY_FULL_VOLTAGE_MV = "full_voltage_mv"
        const val KEY_FULL_RECORDED_AT = "full_recorded_at"
        const val KEY_OBSERVATION_COUNT = "observation_count"
        const val KEY_LOWEST_DISCHARGE_VOLTAGE_MV = "lowest_discharge_voltage_mv"
        const val KEY_LAST_OBSERVATION_AT = "last_observation_at"
        const val CHARGER_COMPLETED = "completed"
        const val OBSERVATION_INTERVAL_MS = 5 * 60 * 1000L
    }
}

data class BatteryCalibrationState(
    val fullVoltageMv: Int? = null,
    val fullRecordedAtMillis: Long? = null,
    val observationCount: Int = 0,
    val lowestDischargeVoltageMv: Int? = null,
    val lastObservationAtMillis: Long = 0L,
)
