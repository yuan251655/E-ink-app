package com.einkphoto.app.core.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FakeDeviceScenario {
    OnlineLocalAlbum,
    Offline,
    Sleeping,
    DisplayBusy,
    StorageMissing,
    StorageNoSpace,
}

class FakeDeviceSession(
    scenario: FakeDeviceScenario = FakeDeviceScenario.OnlineLocalAlbum,
) : DeviceSession {
    private val capabilities = DeviceCapabilities(
        displayProfile = DisplayProfile(
            widthPx = 800,
            heightPx = 480,
            frameBytes = 192_000,
            palette = listOf("black", "white", "green", "blue", "red", "yellow"),
            orientationKey = "device_reported_landscape",
        ),
        supportsSourceOnlyUpload = true,
        supportsSourceAndBinUpload = true,
        supportsMediaPreview = true,
    )

    private val mutableSnapshot = MutableStateFlow(snapshotFor(scenario))
    override val snapshot: StateFlow<DeviceSnapshot> = mutableSnapshot.asStateFlow()

    private var jobSequence = 0
    private var pendingFeatureSwitch: PendingFeatureSwitch? = null

    override suspend fun refreshSnapshot(): DeviceCommandResult<DeviceSnapshot> =
        DeviceCommandResult.Accepted(mutableSnapshot.value)

    override suspend fun requestFeatureSwitch(feature: DeviceFeature): DeviceCommandResult<DeviceJobId> {
        val current = mutableSnapshot.value
        val rejection = when {
            current.connection == DeviceConnectionState.Sleeping -> DeviceRejection.Sleeping
            current.connection != DeviceConnectionState.Online -> DeviceRejection.Offline
            current.displayBusy -> DeviceRejection.DisplayBusy
            pendingFeatureSwitch != null -> DeviceRejection.DisplayBusy
            else -> null
        }
        if (rejection != null) return DeviceCommandResult.Rejected(rejection)

        val jobId = DeviceJobId("fake-mode-${++jobSequence}")
        pendingFeatureSwitch = PendingFeatureSwitch(jobId, feature)
        return DeviceCommandResult.Accepted(jobId)
    }

    /** Applies the authoritative feature only when the simulated async job succeeds. */
    fun finishFeatureSwitch(success: Boolean): DeviceJob? {
        val pending = pendingFeatureSwitch ?: return null
        val completed = DeviceJob(
            id = pending.jobId,
            kind = "switch_feature",
            state = if (success) DeviceJobState.Success else DeviceJobState.Failed,
            message = if (success) "模拟功能切换完成" else "模拟功能切换失败",
        )
        if (success) {
            mutableSnapshot.value = mutableSnapshot.value.copy(activeFeature = pending.feature)
        }
        pendingFeatureSwitch = null
        return completed
    }

    private data class PendingFeatureSwitch(
        val jobId: DeviceJobId,
        val feature: DeviceFeature,
    )

    private fun snapshotFor(scenario: FakeDeviceScenario): DeviceSnapshot {
        val base = DeviceSnapshot(
            deviceId = "fake-photopainter-001",
            displayName = "客厅墨相框",
            isDemo = true,
            connection = DeviceConnectionState.Online,
            activeFeature = DeviceFeature.LocalAlbum,
            displayBusy = false,
            storageFreeBytes = 2_576_980_377,
            capabilities = capabilities,
        )
        return when (scenario) {
            FakeDeviceScenario.OnlineLocalAlbum -> base
            FakeDeviceScenario.Offline -> base.copy(connection = DeviceConnectionState.Offline)
            FakeDeviceScenario.Sleeping -> base.copy(connection = DeviceConnectionState.Sleeping)
            FakeDeviceScenario.DisplayBusy -> base.copy(displayBusy = true)
            FakeDeviceScenario.StorageMissing -> base.copy(storageFreeBytes = null)
            FakeDeviceScenario.StorageNoSpace -> base.copy(storageFreeBytes = 0)
        }
    }
}
