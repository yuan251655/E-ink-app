package com.einkphoto.app.core.device

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDeviceSessionTest {
    @Test
    fun observingSessionDoesNotChangeActiveFeature() = runTest {
        val session = FakeDeviceSession()

        repeat(4) { session.refreshSnapshot() }

        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)
    }

    @Test
    fun acceptedSwitchDoesNotChangeFeatureUntilJobSucceeds() = runTest {
        val session = FakeDeviceSession()

        val result = session.requestFeatureSwitch(DeviceFeature.AiAlbum)

        assertTrue(result is DeviceCommandResult.Accepted)
        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)

        session.finishFeatureSwitch(success = true)

        assertEquals(DeviceFeature.AiAlbum, session.snapshot.value.activeFeature)
    }

    @Test
    fun failedSwitchKeepsPreviousFeature() = runTest {
        val session = FakeDeviceSession()
        session.requestFeatureSwitch(DeviceFeature.InfoDashboard)

        session.finishFeatureSwitch(success = false)

        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)
    }
}
