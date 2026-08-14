package com.einkphoto.app.core.device

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
        val transport = FakeTransport(failHealth = true)
        val session = LanDeviceSession(transport)
        assertTrue(session.refreshSnapshot() is DeviceCommandResult.Rejected)
        assertEquals(DeviceConnectionState.Offline, session.snapshot.value.connection)
        assertEquals(1, transport.fastHealthCalls)
    }

    @Test fun recoveredHealthShowsWakingUntilHandshakeCompletes() = runTest {
        val allowCapabilities = CompletableDeferred<Unit>()
        val capabilitiesStarted = CompletableDeferred<Unit>()
        val transport = FakeTransport(
            beforeCapabilities = {
                capabilitiesStarted.complete(Unit)
                allowCapabilities.await()
            },
        )
        val session = LanDeviceSession(transport)

        val refresh = async { session.refreshSnapshot() }
        capabilitiesStarted.await()
        assertEquals(DeviceConnectionState.Reconnecting, session.snapshot.value.connection)

        allowCapabilities.complete(Unit)
        assertTrue(refresh.await() is DeviceCommandResult.Accepted)
        assertEquals(DeviceConnectionState.Online, session.snapshot.value.connection)
    }

    @Test fun onlineHeartbeatKeepsUsingFastHealthProbe() = runTest {
        val transport = FakeTransport()
        val session = LanDeviceSession(transport)

        session.refreshSnapshot()
        session.refreshSnapshot()

        assertEquals(2, transport.fastHealthCalls)
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

    private class FakeTransport(
        private val failHealth: Boolean = false,
        private val beforeCapabilities: suspend () -> Unit = {},
    ) : LanDeviceTransport {
        var fastHealthCalls = 0
        override suspend fun health() = if (failHealth) LanTransportResult.Failure(DeviceRejection.Offline) else LanTransportResult.Success(LanHealth("p1", "test", "v1", true))
        override suspend fun fastHealth(): LanTransportResult<LanHealth> {
            fastHealthCalls++
            return health()
        }
        override suspend fun capabilities(): LanTransportResult<DeviceCapabilities> {
            beforeCapabilities()
            return LanTransportResult.Success(DeviceCapabilities(DisplayProfile(800,480,192000,listOf("black"),"unverified"),true,true,true))
        }
        var lastExpectedRevision: Long? = null
        override suspend fun status() = LanTransportResult.Success(LanStatus(DeviceFeature.LocalAlbum,DeviceConnectionState.Online,false,100L, modeRevision = 7L))
        override suspend fun switchFeature(feature: DeviceFeature, requestId: String, expectedRevision: Long): LanTransportResult<DeviceJobId> {
            lastExpectedRevision = expectedRevision
            return LanTransportResult.Success(DeviceJobId("job-1"))
        }
    }
}
