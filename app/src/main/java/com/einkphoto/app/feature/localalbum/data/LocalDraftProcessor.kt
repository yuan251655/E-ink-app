package com.einkphoto.app.feature.localalbum.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.einkphoto.app.core.device.DisplayProfile
import com.einkphoto.app.feature.localalbum.conversion.CandidateSixColorConverter
import com.einkphoto.app.feature.localalbum.model.AdaptationSettings
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

data class LocalDraftRequest(
    val source: PhoneSource,
    val settings: AdaptationSettings,
    val profile: DisplayProfile?,
)

data class LocalDraftOutput(
    val previewUri: String,
    val candidateBinUri: String,
    val frameBytes: Int,
    val algorithmVersion: String,
)

/** Creates a phone-owned candidate draft. It does not upload or create a device MediaItem. */
suspend fun createLocalDraft(
    context: Context,
    request: LocalDraftRequest,
    onStage: (ConversionStage) -> Unit = {},
): LocalDraftOutput = withContext(Dispatchers.IO) {
    val profile = requireNotNull(request.profile) { "没有可用的显示规格" }
    require(request.settings.isConfigured) { "照片尚未完成适配" }
    require(profile.widthPx == 800 && profile.heightPx == 480 && profile.frameBytes == 192_000) {
        "当前显示规格与 800×480 六色开发基线不一致，需要连接设备后重新生成"
    }

    onStage(ConversionStage.Preparing)
    val composed = renderDeviceComposition(context, request.source, request.settings, profile)
    try {
        onStage(ConversionStage.Quantizing)
        val conversion = CandidateSixColorConverter().convert(composed)
        require(conversion.validation.isStructurallyValid) {
            "本地结构校验失败：${conversion.validation.errors.firstOrNull() ?: "未知错误"}"
        }

        onStage(ConversionStage.Validating)
        val draftDir = File(context.filesDir, "conversion_drafts/${request.source.sourceId}").apply { mkdirs() }
        val previewFile = File(draftDir, "image.png")
        val binFile = File(draftDir, "image.bin")
        val metadataFile = File(draftDir, "draft.json")
        atomicWrite(previewFile) { output ->
            require(conversion.previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "六色预览写入失败"
            }
        }
        atomicWrite(binFile) { it.write(conversion.candidateBin) }
        require(binFile.length() == profile.frameBytes.toLong()) { "候选 BIN 长度校验失败" }
        val metadata = JSONObject()
            .put("schema", "phone_conversion_draft_v1")
            .put("source_id", request.source.sourceId)
            .put("source_name", request.source.displayName)
            .put("width", profile.widthPx)
            .put("height", profile.heightPx)
            .put("frame_bytes", profile.frameBytes)
            .put("fit_mode", request.settings.fitMode.name)
            .put("quarter_turns_clockwise", request.settings.quarterTurnsClockwise)
            .put("algorithm_version", conversion.algorithmVersion)
            .put("profile_status", conversion.profileStatus)
            .put("local_validation_passed", true)
            .put("uploaded_to_device", false)
            .put("written_to_tf", false)
        atomicWrite(metadataFile) { it.write(metadata.toString(2).toByteArray(Charsets.UTF_8)) }

        LocalDraftOutput(
            previewUri = Uri.fromFile(previewFile).toString(),
            candidateBinUri = Uri.fromFile(binFile).toString(),
            frameBytes = conversion.candidateBin.size,
            algorithmVersion = conversion.algorithmVersion,
        )
    } finally {
        if (!composed.isRecycled) composed.recycle()
    }
}

/** Produces the same 800x480 composition used by preview and final candidate conversion. */
suspend fun renderDeviceComposition(
    context: Context,
    source: PhoneSource,
    settings: AdaptationSettings,
    profile: DisplayProfile,
): Bitmap = withContext(Dispatchers.IO) {
    val uri = Uri.parse(source.contentUri)
    val sourceBitmap = context.decodeSampledBitmap(uri)
        ?: error("照片无法读取或格式不受支持，请重新选择")
    val exifOrientation = context.readExifOrientation(uri)
    val exifCorrected = sourceBitmap.applyExifOrientation(exifOrientation)
    val oriented = exifCorrected.rotateQuarterTurns(settings.quarterTurnsClockwise)
    val output = Bitmap.createBitmap(profile.widthPx, profile.heightPx, Bitmap.Config.ARGB_8888)
    Canvas(output).apply {
        drawColor(Color.WHITE)
        val scale = when (settings.fitMode) {
            FitMode.CropToFill -> maxOf(profile.widthPx.toFloat() / oriented.width, profile.heightPx.toFloat() / oriented.height)
            FitMode.FitInside -> minOf(profile.widthPx.toFloat() / oriented.width, profile.heightPx.toFloat() / oriented.height)
        }
        val targetWidth = (oriented.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (oriented.height * scale).roundToInt().coerceAtLeast(1)
        val left = (profile.widthPx - targetWidth) / 2f
        val top = (profile.heightPx - targetHeight) / 2f
        drawBitmap(
            oriented,
            null,
            android.graphics.RectF(left, top, left + targetWidth, top + targetHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }
    if (oriented !== sourceBitmap && !oriented.isRecycled) oriented.recycle()
    if (exifCorrected !== sourceBitmap && exifCorrected !== oriented && !exifCorrected.isRecycled) exifCorrected.recycle()
    if (!sourceBitmap.isRecycled) sourceBitmap.recycle()
    output
}

private fun Context.decodeSampledBitmap(uri: Uri, maxDimension: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun Context.readExifOrientation(uri: Uri): Int = runCatching {
    contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.rotateQuarterTurns(turns: Int): Bitmap {
    val normalized = Math.floorMod(turns, 4)
    if (normalized == 0) return this
    val matrix = Matrix().apply { postRotate(normalized * 90f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private inline fun atomicWrite(target: File, writer: (FileOutputStream) -> Unit) {
    val temp = File(target.parentFile, "${target.name}.tmp")
    runCatching {
        FileOutputStream(temp).use { output ->
            writer(output)
            output.fd.sync()
        }
        if (target.exists() && !target.delete()) error("无法替换旧草稿文件")
        if (!temp.renameTo(target)) error("无法提交草稿文件")
    }.onFailure { temp.delete() }.getOrThrow()
}
