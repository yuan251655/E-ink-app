package com.einkphoto.app.ui.aialbum

import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AiAlbumPresentationTest {
    @Test fun aiModeCoverUsesTheSystemCoverPresentation() {
        assertEquals(
            AiCurrentDisplayPresentation.ModeCover,
            aiCurrentDisplayPresentation(snapshot(DeviceCurrentContent(DeviceContentKind.ModeCover, DeviceFeature.AiAlbum, DeviceMediaCategory.System, null, "mode_cover_ai_album"))),
        )
    }

    @Test fun nonSystemModeCoverIsRejected() {
        assertEquals(
            AiCurrentDisplayPresentation.Unavailable,
            aiCurrentDisplayPresentation(snapshot(DeviceCurrentContent(DeviceContentKind.ModeCover, DeviceFeature.AiAlbum, DeviceMediaCategory.Ai, null, "mode_cover_ai_album"))),
        )
    }

    @Test fun wrongSystemAssetIsRejected() {
        assertEquals(
            AiCurrentDisplayPresentation.Unavailable,
            aiCurrentDisplayPresentation(snapshot(DeviceCurrentContent(DeviceContentKind.ModeCover, DeviceFeature.AiAlbum, DeviceMediaCategory.System, null, "mode_cover_local_album"))),
        )
    }

    @Test fun aiMediaUsesAiOnlyPlaceholderUntilTheAiRepositoryIsConnected() {
        assertEquals(
            AiCurrentDisplayPresentation.AiMedia,
            aiCurrentDisplayPresentation(snapshot(DeviceCurrentContent(DeviceContentKind.Media, DeviceFeature.AiAlbum, DeviceMediaCategory.Ai, "ai-1", null))),
        )
    }

    @Test fun localMediaNeverBecomesAnAiPreview() {
        assertEquals(
            AiCurrentDisplayPresentation.OtherFeature,
            aiCurrentDisplayPresentation(snapshot(DeviceCurrentContent(DeviceContentKind.Media, DeviceFeature.LocalAlbum, DeviceMediaCategory.Local, "local-1", null))),
        )
    }

    @Test fun mismatchedCategoryNeverBecomesAnAiPreview() {
        assertEquals(
            AiCurrentDisplayPresentation.Unavailable,
            aiCurrentDisplayPresentation(snapshot(DeviceCurrentContent(DeviceContentKind.Media, DeviceFeature.AiAlbum, DeviceMediaCategory.Local, "wrong-1", null))),
        )
    }

    @Test fun missingAiContentIsReportedAsUnavailable() {
        assertEquals(AiCurrentDisplayPresentation.Unavailable, aiCurrentDisplayPresentation(snapshot(null, DeviceFeature.AiAlbum)))
    }

    @Test fun readyLibraryIsPresentedAsOfflineWhenTheDeviceDisconnects() {
        assertEquals(
            AiImageLibraryState.Offline,
            presentAiImageLibraryState(
                loadState = com.einkphoto.app.feature.aialbum.AiImageLoadState.Ready,
                connection = DeviceConnectionState.Offline,
                images = emptyList(),
                errorMessage = null,
            ),
        )
    }

    private fun snapshot(content: DeviceCurrentContent?, active: DeviceFeature = content?.ownerFeature ?: DeviceFeature.LocalAlbum) = DeviceSnapshot(
        deviceId = "test",
        displayName = "test",
        isDemo = true,
        connection = DeviceConnectionState.Online,
        activeFeature = active,
        displayBusy = false,
        storageFreeBytes = null,
        capabilities = null,
        currentContent = content,
    )
}
