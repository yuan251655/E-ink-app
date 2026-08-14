package com.einkphoto.app.feature.mode

import com.einkphoto.app.MainDispatcherRule
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.FakeDeviceSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeSwitchViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun successIsPublishedOnlyAfterDeviceJobAndAuthoritativeRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val session = FakeDeviceSession()
        val viewModel = ModeSwitchViewModel(session)

        viewModel.switchTo(DeviceFeature.AiAlbum)
        runCurrent()
        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)
        session.finishFeatureSwitch(success = true)
        advanceUntilIdle()

        assertEquals(ModeSwitchPhase.Success, viewModel.state.value.phase)
        assertEquals(DeviceFeature.AiAlbum, session.snapshot.value.activeFeature)
    }

    @Test fun failureKeepsPreviousModeAndProvidesRecoveryMessage() = runTest(mainDispatcherRule.dispatcher) {
        val session = FakeDeviceSession()
        val viewModel = ModeSwitchViewModel(session)

        viewModel.switchTo(DeviceFeature.InfoDashboard)
        runCurrent()
        session.finishFeatureSwitch(success = false)
        advanceUntilIdle()

        assertEquals(ModeSwitchPhase.Failed, viewModel.state.value.phase)
        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)
        assertEquals("墨水屏未能完成模式提示画面刷新，原模式和原画面保持不变", viewModel.state.value.message)
    }

    @Test fun missingDeviceJobIsExplainedWithoutChangingTheConfirmedMode() = runTest(mainDispatcherRule.dispatcher) {
        val session = FakeDeviceSession()
        val viewModel = ModeSwitchViewModel(session)

        viewModel.switchTo(DeviceFeature.AiAlbum)
        runCurrent()
        session.expireFeatureSwitchJob()
        advanceUntilIdle()

        assertEquals(ModeSwitchPhase.Failed, viewModel.state.value.phase)
        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)
        assertEquals("设备未找到本次切换任务，模式没有确认完成。请刷新设备状态后重试", viewModel.state.value.message)
    }
    @Test fun staleFailureIsResolvedOnlyByMatchingOnlineDeviceState() {
        val failed = ModeSwitchUiState(target = DeviceFeature.InfoDashboard, phase = ModeSwitchPhase.Failed)

        assertEquals(true, failed.isResolvedBy(DeviceConnectionState.Online, DeviceFeature.InfoDashboard, null))
        assertEquals(false, failed.isResolvedBy(DeviceConnectionState.Offline, DeviceFeature.InfoDashboard, null))
        assertEquals(false, failed.isResolvedBy(DeviceConnectionState.Online, DeviceFeature.LocalAlbum, null))
        assertEquals(false, failed.isResolvedBy(DeviceConnectionState.Online, DeviceFeature.InfoDashboard, DeviceFeature.InfoDashboard))
    }
}
