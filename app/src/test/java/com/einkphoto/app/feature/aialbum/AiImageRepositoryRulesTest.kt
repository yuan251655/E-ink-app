package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceMediaAsset
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceMediaDisplayProfile
import com.einkphoto.app.core.device.DeviceMediaItem
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImageRepositoryRulesTest {
    @Test fun detailMustMatchRequestedIdAndAiCategory() {
        assertTrue(isVerifiedAiDetail("ai-1", media("ai-1", DeviceMediaCategory.Ai)))
        assertFalse(isVerifiedAiDetail("ai-2", media("ai-1", DeviceMediaCategory.Ai)))
        assertFalse(isVerifiedAiDetail("ai-1", media("ai-1", DeviceMediaCategory.Local)))
    }

    @Test fun onlyExactCurrentAiMediaIsProtectedFromDeletion() {
        val current = DeviceCurrentContent(DeviceContentKind.Media, DeviceFeature.AiAlbum, DeviceMediaCategory.Ai, "ai-1", null)
        assertTrue(isProtectedAiMedia(current, "ai-1"))
        assertFalse(isProtectedAiMedia(current, "ai-2"))
        assertFalse(isProtectedAiMedia(current.copy(ownerFeature = DeviceFeature.LocalAlbum), "ai-1"))
        assertFalse(isProtectedAiMedia(current.copy(kind = DeviceContentKind.ModeCover), "ai-1"))
    }

    @Test fun pagesKeepDeviceOrderAndRemoveDuplicateIds() {
        val merged = mergeAiImagePages(listOf(image("a"), image("b")), listOf(image("b"), image("c")))
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test fun sdk26Through28UseLegacyExportAnd29UsesMediaStore() {
        assertEquals(ExportStrategy.LegacyExternalStorage, exportStrategyForSdk(26))
        assertEquals(ExportStrategy.LegacyExternalStorage, exportStrategyForSdk(28))
        assertEquals(ExportStrategy.MediaStore, exportStrategyForSdk(29))
    }

    @Test fun sourceDeclarationRequiresSupportedMimeExactHashAndFiveMiBLimit() {
        val hash = "a".repeat(64)
        assertTrue(isValidAiSourceDeclaration("image/jpeg", 1024L, hash))
        assertTrue(isValidAiSourceDeclaration("image/png", 5L * 1024L * 1024L, hash))
        assertFalse(isValidAiSourceDeclaration("image/webp", 1024L, hash))
        assertFalse(isValidAiSourceDeclaration("application/octet-stream", 1024L, hash))
        assertFalse(isValidAiSourceDeclaration("image/png", 5L * 1024L * 1024L + 1L, hash))
        assertFalse(isValidAiSourceDeclaration("image/png", 1024L, "short"))
    }

    @Test fun downloadedSourceMustMatchDeclaredMimeSizeFileLengthAndSha256() {
        val hash = "0123456789abcdef".repeat(4)
        fun matches(
            actualMime: String = "image/jpeg",
            actualSize: Long = 2048L,
            fileSize: Long = 2048L,
            actualHash: String = hash,
        ) = downloadedAiSourceMatches("image/jpeg", 2048L, hash.uppercase(), actualMime, actualSize, fileSize, actualHash)

        assertTrue(matches())
        assertFalse(matches(actualMime = "image/png"))
        assertFalse(matches(actualSize = 2047L))
        assertFalse(matches(fileSize = 2047L))
        assertFalse(matches(actualHash = "f".repeat(64)))
    }

    @Test fun previewJobKeyIncludesCategoryIdAndRevision() {
        assertEquals("ai:shared-id:7", aiPreviewJobKey("shared-id", 7L))
        assertFalse(aiPreviewJobKey("shared-id", 7L) == aiPreviewJobKey("shared-id", 8L))
    }

    @Test fun completePhoneExportWrapperRunsOnIoDispatcher() = runTest {
        val dispatcher = runAiExportOnIo { currentCoroutineContext()[ContinuationInterceptor] }

        assertEquals(Dispatchers.IO, dispatcher)
    }

    private fun image(id: String) = AiImageItem(id, id, 0L, 192_000, 1L, null, false)

    private fun media(id: String, category: DeviceMediaCategory) = DeviceMediaItem(
        mediaId = id,
        displayName = id,
        category = category,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        displayProfile = DeviceMediaDisplayProfile(800, 480, 192_000, "packed_4bpp", "e6", "landscape", 0, "fit", null),
        source = DeviceMediaAsset(false),
        preview = DeviceMediaAsset(true, "application/octet-stream", 192_000),
        imageBin = DeviceMediaAsset(true, "application/octet-stream", 192_000),
        manifestVersion = 1,
        revision = 1L,
    )
}
