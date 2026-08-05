package com.einkphoto.app.feature.aialbum

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.security.MessageDigest

/**
 * Produces the only source file sent to the device for photo-style generation.
 * The picked original remains on the phone; this App-private JPEG is an
 * EXIF-corrected centre crop for the provider's fixed 5:3 composition.
 */
class PhotoStyleReferencePreprocessor(private val context: Context) {
    data class PreparedReference(val file: File, val sha256: String, val width: Int, val height: Int)

    fun prepare(uri: Uri): Result<PreparedReference> = runCatching {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "reference_image_invalid" }
        val sample = calculateSample(bounds.outWidth, bounds.outHeight)
        val source = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("reference_image_invalid")
        val oriented = source.applyOrientation(orientation)
        if (oriented !== source) source.recycle()
        val rendered = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(rendered).drawBitmap(oriented, cropMatrix(oriented.width, oriented.height), Paint(Paint.FILTER_BITMAP_FLAG))
        if (oriented !== rendered && !oriented.isRecycled) oriented.recycle()

        val directory = File(context.cacheDir, "photo-style-reference").apply { mkdirs() }
        val output = File(directory, "reference-${System.currentTimeMillis()}.jpg")
        var quality = 86
        do {
            output.outputStream().use { rendered.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            quality -= 8
        } while (output.length() > MAX_BYTES && quality >= 38)
        rendered.recycle()
        require(output.length() in 1..MAX_BYTES) { "reference_image_too_large" }
        PreparedReference(output, output.inputStream().use { HexDigest.sha256(it.readBytes()) }, WIDTH, HEIGHT)
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > WIDTH * 2 || height / sample > HEIGHT * 2) sample *= 2
        return sample
    }

    private fun cropMatrix(width: Int, height: Int): Matrix {
        val scale = maxOf(WIDTH.toFloat() / width, HEIGHT.toFloat() / height)
        return Matrix().apply {
            postScale(scale, scale)
            postTranslate((WIDTH - width * scale) / 2f, (HEIGHT - height * scale) / 2f)
        }
    }

    private fun Bitmap.applyOrientation(orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        return if (matrix.isIdentity) this else Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private object HexDigest {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        // This JPEG is only a composition/identity reference for Seedream;
        // the provider still generates the final 2K image. Keeping it compact
        // is essential on the ESP32-S3's HTTP + TF + TLS pipeline.
        const val WIDTH = 640
        const val HEIGHT = 384
        const val MAX_BYTES = 180_000L
    }
}
