package com.einkphoto.app.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMediaPreviewCacheKeyTest {
    @Test fun categoryAndRevisionArePartOfTheCacheKey() {
        val local = previewCacheFileName("Local", "same-id", 7)
        val ai = previewCacheFileName("Ai", "same-id", 7)
        val updated = previewCacheFileName("Local", "same-id", 8)

        assertNotEquals(local, ai)
        assertNotEquals(local, updated)
        assertEquals("local-same-id-7.png", local)
    }

    @Test fun unsafePathCharactersNeverReachTheCacheFileName() {
        val name = previewCacheFileName("AI/../../", "../bad:id", -1)

        assertFalse(name.contains("/"))
        assertFalse(name.contains(".."))
        assertEquals("ai_______-___bad_id-0.png", name)
    }

    @Test fun cleanupMatchesOnlyOlderRevisionsOfTheSameCategoryAndMediaId() {
        assertTrue(isStalePreviewCacheFileName("ai-shared-id-6.png", "Ai", "shared-id", 7))
        assertTrue(isStalePreviewCacheFileName("ai-shared-id-6.png.tmp", "Ai", "shared-id", 7))
        assertTrue(isStalePreviewCacheFileName("ai-shared-id-6.part", "Ai", "shared-id", 7))

        assertFalse(isStalePreviewCacheFileName("ai-shared-id-7.png", "Ai", "shared-id", 7))
        assertFalse(isStalePreviewCacheFileName("local-shared-id-6.png", "Ai", "shared-id", 7))
        assertFalse(isStalePreviewCacheFileName("ai-shared-id-2-6.png", "Ai", "shared-id", 7))
        assertFalse(isStalePreviewCacheFileName("ai-shared-6.png", "Ai", "shared-id", 7))
    }
}
