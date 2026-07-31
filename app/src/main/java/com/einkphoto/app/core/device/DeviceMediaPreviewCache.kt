package com.einkphoto.app.core.device

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Decodes one device-owned packed 4bpp frame into an App-private PNG preview cache. */
class DeviceMediaPreviewCache(
    context: Context,
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) {
    private val directory = File(context.applicationContext.cacheDir, "device-media-preview").apply { mkdirs() }
    private val mutex = Mutex()

    fun cachedUri(category: DeviceMediaCategory, mediaId: String, revision: Long): String? = target(category, mediaId, revision)
        .takeIf { it.isFile && it.length() > 0L }
        ?.let(Uri::fromFile)
        ?.toString()

    suspend fun load(
        category: DeviceMediaCategory,
        mediaId: String,
        revision: Long,
        profile: DeviceMediaDisplayProfile,
    ): String? = mutex.withLock {
        if (!mediaId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return@withLock null
        cachedUri(category, mediaId, revision)?.let { return@withLock it }
        if (profile.widthPx != 800 || profile.heightPx != 480 || profile.frameBytes != 192_000) return@withLock null
        val frame = client.downloadMediaPreview(mediaId).getOrElse { error ->
            if (error is CancellationException) throw error
            return@withLock null
        }
        if (frame.size != profile.frameBytes) return@withLock null
        val destination = target(category, mediaId, revision)
        val temporary = File(directory, "${destination.name}.tmp")
        var bitmap: Bitmap? = null
        try {
            bitmap = Bitmap.createBitmap(profile.widthPx, profile.heightPx, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(profile.widthPx * profile.heightPx)
            frame.forEachIndexed { index, packed ->
                val pixel = index * 2
                pixels[pixel] = einkColor((packed.toInt() ushr 4) and 0x0f)
                pixels[pixel + 1] = einkColor(packed.toInt() and 0x0f)
            }
            bitmap.setPixels(pixels, 0, profile.widthPx, 0, 0, profile.widthPx, profile.heightPx)
            temporary.delete()
            FileOutputStream(temporary).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 92, it)) }
            destination.delete()
            check(temporary.renameTo(destination))
            val uri = Uri.fromFile(destination).toString()
            deleteStalePreviewRevisions(category.name, mediaId, revision)
            uri
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        } finally {
            bitmap?.recycle()
            temporary.delete()
        }
    }

    private fun target(category: DeviceMediaCategory, mediaId: String, revision: Long): File =
        File(directory, previewCacheFileName(category.name, mediaId, revision))

    private fun deleteStalePreviewRevisions(category: String, mediaId: String, revision: Long) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && isStalePreviewCacheFileName(file.name, category, mediaId, revision)) {
                file.delete()
            }
        }
    }

    private fun einkColor(index: Int): Int = when (index) {
        0 -> 0xff000000.toInt()
        1 -> 0xffffffff.toInt()
        2 -> 0xffffe600.toInt()
        3 -> 0xffd92d2d.toInt()
        5 -> 0xff245bc6.toInt()
        6 -> 0xff1c9b54.toInt()
        else -> 0xffffffff.toInt()
    }
}

internal fun previewCacheFileName(category: String, mediaId: String, revision: Long): String {
    return "${previewCacheComponent(category, "unknown")}-${previewCacheComponent(mediaId, "invalid")}-${revision.coerceAtLeast(0L)}.png"
}

internal fun isStalePreviewCacheFileName(
    fileName: String,
    category: String,
    mediaId: String,
    currentRevision: Long,
): Boolean {
    val prefix = "${previewCacheComponent(category, "unknown")}-${previewCacheComponent(mediaId, "invalid")}-"
    val match = Regex("^${Regex.escape(prefix)}(\\d+)(?:\\.png(?:\\.(?:tmp|part))?|\\.(?:tmp|part))$")
        .matchEntire(fileName) ?: return false
    val fileRevision = match.groupValues[1].toLongOrNull() ?: return false
    return fileRevision != currentRevision.coerceAtLeast(0L)
}

private fun previewCacheComponent(value: String, fallback: String): String = value
    .lowercase()
    .replace(Regex("[^a-z0-9_-]"), "_")
    .take(64)
    .ifBlank { fallback }
