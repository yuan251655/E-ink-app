package com.einkphoto.app.feature.localalbum

import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.FakeDeviceSession
import com.einkphoto.app.feature.localalbum.data.FakeLocalAlbumRepository
import com.einkphoto.app.feature.localalbum.model.LocalAlbumUiState
import com.einkphoto.app.core.device.DisplayProfile
import com.einkphoto.app.feature.localalbum.model.AdaptationSettings
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAlbumUiStateTest {
    @Test
    fun queuedDisplayJobLocksConflictingActions() {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val state = LocalAlbumUiState(
            device = session.snapshot.value,
            media = repository.media.value,
            currentDisplay = repository.currentDisplay.value,
            playback = repository.settings.value,
            displayJob = DeviceJob(
                DeviceJobId("queued-1"),
                "display_local_media",
                DeviceJobState.Queued,
                "等待刷新",
            ),
        )

        assertTrue(state.actionsLocked)
    }

    @Test
    fun localConversionSummaryKeepsDraftSeparateFromDeviceMedia() {
        val session = FakeDeviceSession()
        val repository = FakeLocalAlbumRepository(session)
        val source = PhoneSource("phone-1", "file:///phone-1.jpg", "照片.jpg", 1200, 1600)
        val profile = DisplayProfile(800, 480, 192_000, listOf("black", "white", "green", "blue", "red", "yellow"), "candidate")
        val draft = ConversionDraft(
            draftId = "draft-phone-1",
            source = source,
            profile = profile,
            fitMode = FitMode.CropToFill,
            quarterTurnsClockwise = 1,
            stage = ConversionStage.Ready,
            previewUri = "file:///draft/image.png",
            candidateBinUri = "file:///draft/image.bin",
            generatedFrameBytes = 192_000,
            algorithmVersion = "candidate-v1",
            localValidationPassed = true,
        )
        val state = LocalAlbumUiState(
            device = session.snapshot.value,
            media = repository.media.value,
            currentDisplay = repository.currentDisplay.value,
            playback = repository.settings.value,
            displayJob = null,
            phoneSources = listOf(source),
            selectedPhoneSourceId = source.sourceId,
            adaptationSettings = mapOf(source.sourceId to AdaptationSettings(isConfigured = true)),
            conversionDrafts = mapOf(source.sourceId to draft),
        )

        assertEquals(1, state.configuredSourceCount)
        assertEquals(1, state.conversionSuccessCount)
        assertEquals(null, state.currentDisplay.mediaId?.takeIf { it.value == draft.draftId })
    }
}
