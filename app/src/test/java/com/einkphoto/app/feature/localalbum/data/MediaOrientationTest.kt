package com.einkphoto.app.feature.localalbum.data

import com.einkphoto.app.feature.localalbum.model.MediaOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaOrientationTest {
    @Test
    fun appliesQuarterTurnToUserVisibleOrientation() {
        assertEquals(MediaOrientation.Portrait, userVisibleMediaOrientation("portrait", 0))
        assertEquals(MediaOrientation.Landscape, userVisibleMediaOrientation("landscape", 0))
        assertEquals(MediaOrientation.Landscape, userVisibleMediaOrientation("portrait", 90))
        assertEquals(MediaOrientation.Portrait, userVisibleMediaOrientation("landscape", 270))
    }
}
