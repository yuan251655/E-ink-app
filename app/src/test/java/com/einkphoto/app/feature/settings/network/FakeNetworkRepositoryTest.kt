package com.einkphoto.app.feature.settings.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNetworkRepositoryTest {
    @Test fun `failed STA keeps previous STA and AP available`() = runTest {
        val repository = FakeNetworkRepository()
        repository.testAndSaveSta(StaConfigDraft("Studio-2.4G", "ok"))
        val before = repository.snapshot.value

        val result = repository.testAndSaveSta(StaConfigDraft("Lab-IoT", "wrong"))

        assertTrue(result is NetworkActionResult.Rejected)
        assertEquals(before.sta, repository.snapshot.value.sta)
        assertTrue(repository.snapshot.value.ap.enabled)
    }

    @Test fun `AP save preserves STA and restore returns default AP`() = runTest {
        val repository = FakeNetworkRepository()
        repository.testAndSaveSta(StaConfigDraft("Studio-2.4G", "ok"))
        val savedSta = repository.snapshot.value.sta

        assertEquals(NetworkActionResult.Accepted, repository.saveAp(ApConfigDraft("My-PhotoPainter", "secret")))
        assertEquals("My-PhotoPainter", repository.snapshot.value.ap.ssid)
        assertEquals(savedSta, repository.snapshot.value.sta)

        assertEquals(NetworkActionResult.Accepted, repository.restoreDefaultAp())
        assertEquals("PhotoPainter-Setup", repository.snapshot.value.ap.ssid)
        assertEquals(savedSta, repository.snapshot.value.sta)
    }

    @Test fun `scan results model 2 point 4 GHz metadata`() = runTest {
        val networks = FakeNetworkRepository().scan24Ghz().getOrThrow()
        assertTrue(networks.all { it.ssid.isNotBlank() && it.channel in 1..14 })
    }
}
