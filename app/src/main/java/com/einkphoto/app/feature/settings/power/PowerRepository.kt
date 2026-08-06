package com.einkphoto.app.feature.settings.power

import kotlinx.coroutines.flow.StateFlow

/** Settings-facing port for read-only PMIC telemetry. Charging and sleep policy stay device-owned. */
interface PowerRepository {
    val snapshot: StateFlow<PowerSnapshot>
    suspend fun refresh(): PowerActionResult
}
