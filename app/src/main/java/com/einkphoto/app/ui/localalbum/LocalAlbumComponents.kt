package com.einkphoto.app.ui.localalbum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DisplayProfile
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.MediaProtectionReason
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.AdaptationSettings
import com.einkphoto.app.feature.localalbum.conversion.CandidateSixColorConverter
import com.einkphoto.app.feature.localalbum.data.renderDeviceComposition
import com.einkphoto.app.ui.theme.appSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal enum class ArtworkPresentation {
    DeviceCanvas,
    SourceAspect,
}

internal fun DeviceFeature.label(): String = when (this) {
    DeviceFeature.LocalAlbum -> "本地相册"
    DeviceFeature.AiAlbum -> "AI 相册"
    DeviceFeature.InfoDashboard -> "信息看板"
}

@Composable
internal fun DemoArtwork(
    seed: Int,
    description: String,
    modifier: Modifier = Modifier,
    presentation: ArtworkPresentation = ArtworkPresentation.DeviceCanvas,
    sourceAspectRatio: Float = 5f / 3f,
    deviceAspectRatio: Float = 5f / 3f,
) {
    val surface = MaterialTheme.colorScheme.surface
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    val accent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val warm = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    Box(
        modifier = modifier
            .aspectRatio(
                if (presentation == ArtworkPresentation.DeviceCanvas) deviceAspectRatio.coerceIn(0.6f, 2.1f)
                else sourceAspectRatio.coerceIn(0.6f, 2.1f),
            )
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(surface, neutral)))
            val horizon = size.height * (0.58f + (seed % 3) * 0.035f)
            drawCircle(
                color = warm,
                radius = size.minDimension * 0.15f,
                center = Offset(size.width * (0.25f + (seed % 4) * 0.13f), horizon * 0.55f),
            )
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, horizon)
                    lineTo(size.width * 0.28f, horizon * 0.63f)
                    lineTo(size.width * 0.55f, horizon * 0.92f)
                    lineTo(size.width * 0.8f, horizon * 0.58f)
                    lineTo(size.width, horizon * 0.82f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                },
                color = accent,
            )
            drawLine(outline, Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 2.dp.toPx())
        }
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(24.dp),
        )
    }
}

/** Shows a sampled, phone-owned source image without promoting it to device media. */
@Composable
internal fun PhoneSourcePreview(
    source: PhoneSource,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val previewBitmap = rememberPhonePreviewBitmap(source)
    if (previewBitmap == null) {
        DemoArtwork(
            seed = source.sourceId.hashCode(),
            description = "$contentDescription（加载中）",
            modifier = modifier,
            presentation = ArtworkPresentation.SourceAspect,
            sourceAspectRatio = source.widthPx.toFloat() / source.heightPx.toFloat(),
        )
    } else {
        Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

@Composable
private fun rememberPhonePreviewBitmap(source: PhoneSource): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, source.contentUri) {
        value = withContext(Dispatchers.IO) { context.decodePreview(Uri.parse(source.contentUri)) }
    }
    return bitmap
}

private fun Context.decodePreview(uri: Uri, maxDimension: Int = 960): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        ?: return null
    val orientation = contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    decoded.applyExifOrientation(orientation)
}.getOrNull()

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
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
        if (it !== this && !isRecycled) recycle()
    }
}

/** A local composition preview for the device canvas; it never claims to be a converted BIN. */
@Composable
internal fun DeviceAdaptationPreview(
    source: PhoneSource,
    fitMode: FitMode,
    quarterTurnsClockwise: Int,
    modifier: Modifier = Modifier,
    deviceAspectRatio: Float = 5f / 3f,
) {
    Box(
        modifier = modifier
            .aspectRatio(deviceAspectRatio.coerceIn(0.6f, 2.1f))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val previewBitmap = rememberPhonePreviewBitmap(source)
        if (previewBitmap == null) {
            DemoArtwork(
                seed = source.sourceId.hashCode(),
                description = "${source.displayName} 电子纸构图预览（加载中）",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val normalizedTurns = Math.floorMod(quarterTurnsClockwise, 4)
            val orientedBitmap = remember(previewBitmap, normalizedTurns) {
                previewBitmap.rotateQuarterTurns(normalizedTurns)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "${source.displayName} 电子纸构图预览，${if (fitMode == FitMode.CropToFill) "填充裁剪，铺满画面" else "完整适配，可能留白"}，旋转 ${quarterTurnsClockwise * 90} 度"
                    },
            ) {
                val image = orientedBitmap.asImageBitmap()
                val placement = calculatePreviewPlacement(
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    imageWidth = image.width,
                    imageHeight = image.height,
                    fitMode = fitMode,
                )
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(placement.offsetX, placement.offsetY),
                    dstSize = IntSize(placement.width, placement.height),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

/** Phone-only six-color simulation derived from the same composed pixels as the candidate draft. */
@Composable
internal fun SixColorSimulationPreview(
    source: PhoneSource,
    settings: AdaptationSettings,
    profile: DisplayProfile?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val simulation by produceState<Bitmap?>(
        initialValue = null,
        source.contentUri,
        settings.fitMode,
        settings.quarterTurnsClockwise,
        profile,
    ) {
        value = profile?.let { target ->
            runCatching {
                val composed = renderDeviceComposition(context, source, settings, target)
                try {
                    withContext(Dispatchers.Default) { CandidateSixColorConverter().convert(composed).previewBitmap }
                } finally {
                    if (!composed.isRecycled) composed.recycle()
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(profile?.let { it.widthPx.toFloat() / it.heightPx } ?: (5f / 3f))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        simulation?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${source.displayName} 图片预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("正在生成手机六色模拟效果", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal data class PreviewPlacement(
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int,
)

internal fun calculatePreviewPlacement(
    canvasWidth: Float,
    canvasHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    fitMode: FitMode,
): PreviewPlacement {
    require(canvasWidth > 0f && canvasHeight > 0f)
    require(imageWidth > 0 && imageHeight > 0)
    val scale = when (fitMode) {
        FitMode.CropToFill -> maxOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
        FitMode.FitInside -> minOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
    }
    val width = (imageWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (imageHeight * scale).roundToInt().coerceAtLeast(1)
    return PreviewPlacement(
        offsetX = ((canvasWidth - width) / 2f).roundToInt(),
        offsetY = ((canvasHeight - height) / 2f).roundToInt(),
        width = width,
        height = height,
    )
}

private fun Bitmap.rotateQuarterTurns(turns: Int): Bitmap {
    val normalizedTurns = Math.floorMod(turns, 4)
    if (normalizedTurns == 0) return this
    val matrix = Matrix().apply { postRotate(normalizedTurns * 90f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Composable
internal fun MediaCard(
    media: MediaItem,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        SavedMediaPreview(media, Modifier.fillMaxWidth())
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(media.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    current -> "当前显示"
                    media.protectionReasons.contains(MediaProtectionReason.Uploading) -> "正在上传"
                    else -> "已入库 · 可显示"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SavedMediaPreview(media: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, media.previewUri) {
        value = media.previewUri?.let { uri -> withContext(Dispatchers.IO) { context.decodePreview(Uri.parse(uri)) } }
    }
    if (bitmap == null) {
        DemoArtwork(
            seed = media.id.value.hashCode(),
            description = "${media.displayName}预览",
            modifier = modifier,
            presentation = ArtworkPresentation.DeviceCanvas,
            sourceAspectRatio = media.sourceWidthPx.toFloat() / media.sourceHeightPx,
        )
    } else {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "${media.displayName}预览",
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(media.sourceWidthPx.toFloat() / media.sourceHeightPx),
        )
    }
}

@Composable
internal fun SectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) {
            Surface(
                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onAction),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun StatusRow(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
