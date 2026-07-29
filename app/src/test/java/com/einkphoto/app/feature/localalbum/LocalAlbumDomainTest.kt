package com.einkphoto.app.feature.localalbum

import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DisplayProfile
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.DisplayResult
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.MediaAvailability
import com.einkphoto.app.feature.localalbum.model.MediaCategory
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalAlbumDomainTest {
    @Test
    fun phoneSourceDraftMediaAndCurrentDisplayRemainDifferentObjects() {
        val source = PhoneSource("phone-1", "content://photo/1", "花园.jpg", 2400, 1440)
        val profile = DisplayProfile(800, 480, 192_000, listOf("black", "white"), "reported")
        val draft = ConversionDraft(
            draftId = "draft-1",
            source = source,
            profile = profile,
            fitMode = FitMode.CropToFill,
            quarterTurnsClockwise = 0,
            stage = ConversionStage.Ready,
            previewUri = "cache://draft/1",
            candidateBinUri = "cache://draft/1.bin",
            generatedFrameBytes = 192_000,
            algorithmVersion = "candidate-v1",
            localValidationPassed = true,
        )
        val media = MediaItem(
            id = MediaId("media-1"),
            category = MediaCategory.Local,
            displayName = "花园",
            previewUri = "device://preview/1",
            sourceWidthPx = 2400,
            sourceHeightPx = 1440,
            sizeBytes = 3_000_000,
            availability = MediaAvailability.Ready,
            createdAtEpochMillis = 1,
        )
        val current = CurrentDisplay(media.id, DeviceFeature.LocalAlbum, DisplayResult.Success, 2)

        assertEquals("phone-1", draft.source.sourceId)
        assertEquals("media-1", current.mediaId?.value)
        assertNotEquals(source.contentUri, media.previewUri)
    }
}
