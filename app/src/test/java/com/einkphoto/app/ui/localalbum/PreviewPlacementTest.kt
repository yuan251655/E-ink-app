package com.einkphoto.app.ui.localalbum

import com.einkphoto.app.feature.localalbum.model.FitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPlacementTest {
    @Test
    fun portraitCropToFillCoversEntireLandscapeCanvas() {
        val placement = calculatePreviewPlacement(800f, 480f, 400, 600, FitMode.CropToFill)

        assertEquals(800, placement.width)
        assertEquals(0, placement.offsetX)
        assertTrue(placement.height >= 480)
        assertTrue(placement.offsetY <= 0)
    }

    @Test
    fun rotatedPortraitCropToFillCoversEntireLandscapeCanvas() {
        val placement = calculatePreviewPlacement(800f, 480f, 600, 400, FitMode.CropToFill)

        assertEquals(800, placement.width)
        assertEquals(0, placement.offsetX)
        assertTrue(placement.height >= 480)
        assertTrue(placement.offsetY <= 0)
    }

    @Test
    fun portraitFitInsidePreservesWholeImageWithSideMargins() {
        val placement = calculatePreviewPlacement(800f, 480f, 400, 600, FitMode.FitInside)

        assertEquals(320, placement.width)
        assertEquals(480, placement.height)
        assertEquals(240, placement.offsetX)
        assertEquals(0, placement.offsetY)
    }
}
