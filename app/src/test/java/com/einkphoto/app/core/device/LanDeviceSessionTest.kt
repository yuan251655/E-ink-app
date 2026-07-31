package com.einkphoto.app.core.device

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanDeviceSessionTest {
    @Test fun onlineOnlyAfterHandshakeAndCapabilities() = runTest {
        val session = LanDeviceSession(FakeTransport())
        val result = session.refreshSnapshot()
        assertTrue(result is DeviceCommandResult.Accepted)
        assertEquals(DeviceConnectionState.Online, session.snapshot.value.connection)
        assertEquals(192_000, session.snapshot.value.capabilities?.displayProfile?.frameBytes)
    }

    @Test fun rejectedHandshakeNeverPretendsOnline() = runTest {
        val session = LanDeviceSession(FakeTransport(failHealth = true))
        assertTrue(session.refreshSnapshot() is DeviceCommandResult.Rejected)
        assertEquals(DeviceConnectionState.Offline, session.snapshot.value.connection)
    }

    @Test fun switchUsesAuthoritativeModeRevisionAndDoesNotChangeFeatureOptimistically() = runTest {
        val transport = FakeTransport()
        val session = LanDeviceSession(transport)
        session.refreshSnapshot()

        val result = session.requestFeatureSwitch(DeviceFeature.AiAlbum)

        assertTrue(result is DeviceCommandResult.Accepted)
        assertEquals(7L, transport.lastExpectedRevision)
        assertEquals(DeviceFeature.LocalAlbum, session.snapshot.value.activeFeature)
    }

    private class FakeTransport(private val failHealth: Boolean = false) : LanDeviceTransport {
        override suspend fun health() = if (failHealth) LanTransportResult.Failure(DeviceRejection.Offline) else LanTransportResult.Success(LanHealth("p1", "test", "v1", true))
        override suspend fun capabilities() = LanTransportResult.Success(DeviceCapabilities(DisplayProfile(800,480,192000,listOf("black"),"unverified"),true,true,true))
        var lastExpectedRevision: Long? = null
        override suspend fun status() = LanTransportResult.Success(LanStatus(DeviceFeature.LocalAlbum,DeviceConnectionState.Online,false,100L, modeRevision = 7L))
        override suspend fun switchFeature(feature: DeviceFeature, requestId: String, expectedRevision: Long): LanTransportResult<DeviceJobId> {
            lastExpectedRevision = expectedRevision
            return LanTransportResult.Success(DeviceJobId("job-1"))
        }
    }
}
