package com.einkphoto.app.feature.mode

import com.einkphoto.app.MainDispatcherRule
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
        assertEquals("切换失败，原模式和原画面保持不变", viewModel.state.value.message)
    }
}
