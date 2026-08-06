package com.einkphoto.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Rounded, friendly battery mark for the frame header. It intentionally avoids
 * the sharp Material system battery glyph and follows the product's soft,
 * thick-line e-ink visual language.
 */
@Composable
fun FrameBatteryIcon(
    percent: Int?,
    charging: Boolean,
    full: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val description = when {
        charging -> "相框电池正在充电"
        full -> "相框电池已充满"
        else -> "相框电池电量"
    }
    Canvas(
        modifier = modifier
            .size(width = 42.dp, height = 23.dp)
            .semantics { contentDescription = description },
    ) {
        val stroke = with(density) { 2.4.dp.toPx() }
        val corner = with(density) { 5.dp.toPx() }
        val bodyLeft = stroke / 2f
        val bodyTop = stroke / 2f
        val bodyWidth = size.width - with(density) { 6.dp.toPx() } - stroke
        val bodyHeight = size.height - stroke
        val capWidth = with(density) { 4.5.dp.toPx() }
        val capHeight = bodyHeight * 0.46f
        val capTop = (size.height - capHeight) / 2f

        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(bodyLeft + bodyWidth + stroke * 0.15f, capTop),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(stroke, stroke),
        )

        val normalized = percent?.coerceIn(0, 100)
        val filledBars = when {
            normalized == null -> 0
            normalized >= 88 -> 4
            normalized >= 63 -> 3
            normalized >= 38 -> 2
            normalized >= 12 -> 1
            else -> 0
        }
        val fillColor = when {
            // Match the App's soft pink product tone instead of the old
            // green/yellow/red battery colors.
            charging -> Color(0xFFFF6FAE)
            normalized == null -> Color(0xFFF6D9E8)
            normalized <= 25 -> Color(0xFFFF8BB8)
            normalized <= 60 -> Color(0xFFFF9FC5)
            else -> Color(0xFFFF6FAE)
        }
        val emptyColor = Color(0xFFF2E6ED)
        val innerLeft = bodyLeft + with(density) { 5.3.dp.toPx() }
        val innerTop = bodyTop + with(density) { 4.4.dp.toPx() }
        val innerHeight = bodyHeight - with(density) { 8.8.dp.toPx() }
        val gap = with(density) { 2.6.dp.toPx() }
        val barWidth = (bodyWidth - with(density) { 11.0.dp.toPx() } - gap * 3f) / 4f

        for (index in 0 until 4) {
            drawRoundRect(
                color = if (index < filledBars) fillColor else emptyColor,
                topLeft = Offset(innerLeft + index * (barWidth + gap), innerTop),
                size = Size(barWidth, innerHeight),
                cornerRadius = CornerRadius(with(density) { 2.dp.toPx() }, with(density) { 2.dp.toPx() }),
            )
        }

        if (charging) {
            val bolt = Path().apply {
                val cx = bodyLeft + bodyWidth * 0.53f
                val top = bodyTop + bodyHeight * 0.18f
                moveTo(cx + bodyWidth * 0.08f, top)
                lineTo(cx - bodyWidth * 0.07f, top + bodyHeight * 0.40f)
                lineTo(cx + bodyWidth * 0.05f, top + bodyHeight * 0.40f)
                lineTo(cx - bodyWidth * 0.09f, top + bodyHeight * 0.82f)
                lineTo(cx + bodyWidth * 0.18f, top + bodyHeight * 0.30f)
                lineTo(cx + bodyWidth * 0.05f, top + bodyHeight * 0.30f)
                close()
            }
            drawPath(path = bolt, color = Color(0xFFE93686))
        } else if (full) {
            val checkStroke = Stroke(width = with(density) { 2.2.dp.toPx() })
            val check = Path().apply {
                moveTo(bodyLeft + bodyWidth * 0.40f, bodyTop + bodyHeight * 0.52f)
                lineTo(bodyLeft + bodyWidth * 0.49f, bodyTop + bodyHeight * 0.63f)
                lineTo(bodyLeft + bodyWidth * 0.66f, bodyTop + bodyHeight * 0.39f)
            }
            drawPath(path = check, color = Color.Black, style = checkStroke)
        }
    }
}
