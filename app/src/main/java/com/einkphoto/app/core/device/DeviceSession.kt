package com.einkphoto.app.core.device

import kotlinx.coroutines.flow.StateFlow

/**
 * App-facing device boundary. It deliberately contains no HTTP path, IP address,
 * TF-card path, GPIO, or provisional wire DTO.
 */
interface DeviceSession {
    val snapshot: StateFlow<DeviceSnapshot>

    suspend fun refreshSnapshot(): DeviceCommandResult<DeviceSnapshot>

    /** A feature changes only through this explicit user-triggered command. */
    suspend fun requestFeatureSwitch(feature: DeviceFeature): DeviceCommandResult<DeviceJobId>
}
