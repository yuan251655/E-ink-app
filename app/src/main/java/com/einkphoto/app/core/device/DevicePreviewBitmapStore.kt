package com.einkphoto.app.core.device

import android.graphics.Bitmap
import java.util.LinkedHashMap

/**
 * Small process-local handoff for previews that have just been decoded from the device BIN.
 *
 * The persisted PNG remains the restart-safe source of truth. This store only lets the visible
 * Compose screen render the freshly decoded Bitmap directly, without routing it through a URI.
 */
object DevicePreviewBitmapStore {
    private const val MAX_ENTRIES = 18
    private val bitmaps = LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true)

    fun get(previewUri: String): Bitmap? = synchronized(bitmaps) {
        bitmaps[previewUri]?.takeUnless { it.isRecycled }
    }

    fun put(previewUri: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        synchronized(bitmaps) {
            bitmaps[previewUri] = bitmap
            while (bitmaps.size > MAX_ENTRIES) {
                bitmaps.entries.iterator().run { if (hasNext()) { next(); remove() } }
            }
        }
    }

    fun evict(previewUri: String) {
        synchronized(bitmaps) { bitmaps.remove(previewUri) }
    }
}
