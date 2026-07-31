package com.einkphoto.app.ui.aialbum

import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.feature.aialbum.AiImageLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImageLibraryStateTest {
    @Test fun libraryFilteringNeverIncludesOtherProductCategories() {
        val records = listOf(
            record("ai", DeviceMediaCategory.Ai),
            record("local", DeviceMediaCategory.Local),
            record("dashboard", DeviceMediaCategory.Dashboard),
            record("system", DeviceMediaCategory.System),
        )

        assertEquals(listOf("ai"), filterAiImageRecords(records).map { it.id })
    }

    @Test fun currentDisplayRequiresExactAiMediaOwnershipAndId() {
        val valid = content(DeviceFeature.AiAlbum, DeviceContentKind.Media, DeviceMediaCategory.Ai, "ai-1")
        assertTrue(isAiImageCurrentlyDisplayed(valid, "ai-1"))
        assertFalse(isAiImageCurrentlyDisplayed(valid, "ai-2"))
        assertFalse(isAiImageCurrentlyDisplayed(content(DeviceFeature.LocalAlbum, DeviceContentKind.Media, DeviceMediaCategory.Ai, "ai-1"), "ai-1"))
        assertFalse(isAiImageCurrentlyDisplayed(content(DeviceFeature.AiAlbum, DeviceContentKind.ModeCover, DeviceMediaCategory.System, null), "ai-1"))
        assertFalse(isAiImageCurrentlyDisplayed(content(DeviceFeature.AiAlbum, DeviceContentKind.Media, DeviceMediaCategory.Local, "ai-1"), "ai-1"))
        assertFalse(isAiImageCurrentlyDisplayed(content(DeviceFeature.AiAlbum, DeviceContentKind.Media, DeviceMediaCategory.Ai, ""), ""))
        assertFalse(isAiImageCurrentlyDisplayed(null, "ai-1"))
    }

    @Test fun detailsAndLibraryBackChainIsStable() {
        assertEquals(AiAlbumRoute.Images, aiBackDestination(AiAlbumRoute.ImageDetail, AiAlbumRoute.Home))
        assertEquals(AiAlbumRoute.Home, aiBackDestination(AiAlbumRoute.Images, AiAlbumRoute.Home))
    }

    @Test fun imageSizeFormattingIsReadableAndBounded() {
        assertEquals("未知", formatAiImageSize(-1))
        assertEquals("800 B", formatAiImageSize(800))
        assertEquals("1.0 KB", formatAiImageSize(1024))
        assertEquals("1.0 MB", formatAiImageSize(1024 * 1024))
    }

    @Test fun idleOrLoadingStateFollowsTheAuthoritativeConnection() {
        assertEquals(
            AiImageLibraryState.Offline,
            presentAiImageLibraryState(AiImageLoadState.Idle, DeviceConnectionState.Offline, emptyList(), null),
        )
        assertEquals(
            AiImageLibraryState.Offline,
            presentAiImageLibraryState(AiImageLoadState.Loading, DeviceConnectionState.Reconnecting, emptyList(), null),
        )
        assertEquals(
            AiImageLibraryState.Loading,
            presentAiImageLibraryState(AiImageLoadState.Loading, DeviceConnectionState.Online, emptyList(), null),
        )
    }

    private fun record(id: String, category: DeviceMediaCategory) = AiImageRecord(
        id = id,
        category = category,
        name = id,
        prompt = "prompt",
        generatedAtLabel = "2026-07-31 16:00",
        modelLabel = "model",
        sourceLabel = "source",
        sizeBytes = 1024,
        previewUri = null,
        sourceAvailable = false,
        syncLabel = "未读取",
    )

    private fun content(
        owner: DeviceFeature,
        kind: DeviceContentKind,
        category: DeviceMediaCategory,
        mediaId: String?,
    ) = DeviceCurrentContent(kind, owner, category, mediaId, null)
}
