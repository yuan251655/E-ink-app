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
    private val completedJobs = mutableMapOf<DeviceJobId, DeviceJobSnapshot>()
    private val expiredJobs = mutableSetOf<DeviceJobId>()

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
        mutableSnapshot.value = current.copy(
            pendingFeature = feature,
            modeState = DeviceModeState.Switching,
            modeSwitchJobId = jobId,
        )
        return DeviceCommandResult.Accepted(jobId)
    }

    override suspend fun modeSwitchJob(jobId: DeviceJobId): DeviceCommandResult<DeviceJobSnapshot> {
        completedJobs[jobId]?.let { return DeviceCommandResult.Accepted(it) }
        if (jobId in expiredJobs) return DeviceCommandResult.Rejected(DeviceRejection.JobNotFound)
        val pending = pendingFeatureSwitch
        return if (pending?.jobId == jobId) {
            DeviceCommandResult.Accepted(DeviceJobSnapshot(jobId, DeviceJobState.Running, "refreshing", 55, null, null))
        } else {
            DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
        }
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
            mutableSnapshot.value = mutableSnapshot.value.copy(
                activeFeature = pending.feature,
                pendingFeature = null,
                modeState = DeviceModeState.Idle,
                modeRevision = mutableSnapshot.value.modeRevision + 1,
                modeSwitchJobId = null,
                currentContent = DeviceCurrentContent(
                    kind = DeviceContentKind.ModeCover,
                    ownerFeature = pending.feature,
                    category = DeviceMediaCategory.System,
                    mediaId = null,
                    systemAssetId = "mode_cover_${pending.feature.apiValue}",
                ),
            )
        } else {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                pendingFeature = null,
                modeState = DeviceModeState.Idle,
                modeSwitchJobId = null,
            )
        }
        completedJobs[pending.jobId] = DeviceJobSnapshot(
            jobId = pending.jobId,
            state = if (success) DeviceJobState.Success else DeviceJobState.Failed,
            phase = if (success) "completed" else "failed",
            progressPercent = if (success) 100 else 0,
            errorCode = if (success) null else "display_failed",
            mediaId = null,
        )
        pendingFeatureSwitch = null
        return completed
    }

    /** Simulates a device restart or job-record cleanup while an App is polling a switch. */
    fun expireFeatureSwitchJob() {
        val pending = pendingFeatureSwitch ?: return
        expiredJobs += pending.jobId
        mutableSnapshot.value = mutableSnapshot.value.copy(
            pendingFeature = null,
            modeState = DeviceModeState.Idle,
            modeSwitchJobId = null,
        )
        pendingFeatureSwitch = null
    }

    private data class PendingFeatureSwitch(
        val jobId: DeviceJobId,
        val feature: DeviceFeature,
    )

    private fun snapshotFor(scenario: FakeDeviceScenario): DeviceSnapshot {
        val base = DeviceSnapshot(
            deviceId = "fake-photopainter-001",
            displayName = "客厅相念",
            isDemo = true,
            connection = DeviceConnectionState.Online,
            activeFeature = DeviceFeature.LocalAlbum,
            displayBusy = false,
            storageFreeBytes = 2_576_980_377,
            capabilities = capabilities,
            currentContent = DeviceCurrentContent(
                kind = DeviceContentKind.Media,
                ownerFeature = DeviceFeature.LocalAlbum,
                category = DeviceMediaCategory.Local,
                mediaId = "media-001",
                systemAssetId = null,
            ),
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
