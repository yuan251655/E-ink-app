package com.einkphoto.app.core.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CapabilityRegistry(
    session: DeviceSession,
    scope: CoroutineScope,
) {
    val capabilities: StateFlow<DeviceCapabilities?> = session.snapshot
        .map { snapshot: DeviceSnapshot -> snapshot.capabilities }
        .stateIn(scope, SharingStarted.Eagerly, session.snapshot.value.capabilities)
}
