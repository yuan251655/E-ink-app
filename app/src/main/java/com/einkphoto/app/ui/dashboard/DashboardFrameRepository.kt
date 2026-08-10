package com.einkphoto.app.ui.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceMediaDisplayProfile
import com.einkphoto.app.core.device.DeviceMediaUploadRequest
import com.einkphoto.app.feature.localalbum.conversion.CandidateSixColorConverter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay
import org.json.JSONObject

internal class DashboardFrameRepository(private val context: Context) {
    private val client = DevelopmentApHttpClient()

    suspend fun prepare(document: DashboardDocument): String {
        val bitmap = render(document)
        val converted = CandidateSixColorConverter().convert(bitmap)
        check(converted.candidateBin.size == 192_000) { "信息看板六色 BIN 尺寸不正确" }

        val directory = File(context.filesDir, "dashboard_frames").apply { mkdirs() }
        FileOutputStream(File(directory, "latest-source.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileOutputStream(File(directory, "latest-six-color.png")).use {
            converted.previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val bin = File(directory, "dashboard-${System.currentTimeMillis()}.bin").apply {
            writeBytes(converted.candidateBin)
        }
        val requestId = "dashboard-${UUID.randomUUID()}".take(64)
        val root = client.uploadBinOnly(
            DeviceMediaUploadRequest(
                requestId = requestId,
                category = DeviceMediaCategory.Dashboard,
                displayName = when (document.layoutId) {
                    "weather_date" -> "天气日期看板"
                    "date_memo_todo" -> "日期待办看板"
                    else -> "轻量综合看板"
                },
                imageBinFile = bin,
                imageBinSizeBytes = bin.length(),
                imageBinSha256 = sha256(bin),
                displayProfile = DeviceMediaDisplayProfile(
                    widthPx = 800,
                    heightPx = 480,
                    frameBytes = 192_000,
                    pixelFormat = "4bpp",
                    palette = "six_color_e6",
                    orientation = "landscape",
                    rotationDegrees = 0,
                    fitMode = "cover",
                    converterVersion = converted.algorithmVersion,
                ),
            ),
        ).getOrThrow()
        return root.getJSONObject("data").getString("media_id")
    }

    suspend fun display(mediaId: String, expectedModeRevision: Long) {
        val display = client.postJson(
            "/api/v1/media/$mediaId/display",
            JSONObject()
                .put("request_id", "dashboard-display-${UUID.randomUUID()}".take(64))
                .put("expected_mode_revision", expectedModeRevision)
                .put("after_display", "hold"),
        ).getOrThrow()
        val jobId = display.getJSONObject("data").optString("job_id")
        check(jobId.isNotBlank()) { "设备没有返回看板显示任务" }
        awaitDisplaySuccess(jobId)
    }

    private fun render(document: DashboardDocument): Bitmap =
        Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            when (document.layoutId) {
                "weather_date" -> renderWeatherDate(canvas, document)
                "date_memo_todo" -> renderDateMemoTodo(canvas, document)
                else -> renderWeatherMemoTodo(canvas, document)
            }
        }

    private fun renderWeatherDate(canvas: Canvas, document: DashboardDocument) {
        val black = Color.BLACK
        val red = Color.RED
        val blue = Color.BLUE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val today = runCatching { LocalDate.now(ZoneId.of(document.timezone)) }.getOrElse { LocalDate.now() }
        val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        paint.color = black
        paint.textSize = 31f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val city = document.cityName.ifBlank { "当前位置" }
        canvas.drawText(city, 36f, 66f, paint)
        drawLocationPin(canvas, 48f + paint.measureText(city), 55f, paint)
        paint.textSize = 16f
        paint.typeface = Typeface.DEFAULT
        val updatedAt = runCatching {
            java.time.ZonedDateTime.now(ZoneId.of(document.timezone)).format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrElse { java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
        canvas.drawText("实时更新  $updatedAt", 36f, 104f, paint)

        val stripTop = 28f
        val cellWidth = 95f
        (0..4).forEachIndexed { index, offset ->
            val date = today.plusDays(offset.toLong())
            val left = 310f + index * cellWidth
            val isToday = offset == 0
            if (isToday) {
                paint.color = red
                canvas.drawRoundRect(RectF(left + 5f, stripTop, left + 95f, 120f), 12f, 12f, paint)
            }
            paint.color = if (isToday) Color.WHITE else black
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif", if (isToday) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = 15f
            canvas.drawText(weekdays[date.dayOfWeek.value - 1], left + 50f, 55f, paint)
            paint.textSize = 24f
            canvas.drawText("${date.monthValue}/${date.dayOfMonth}", left + 50f, 88f, paint)
            paint.textSize = 13f
            canvas.drawText(if (isToday) "今天" else "", left + 50f, 111f, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        val labels = listOf("今天", "明天", "后天")
        val forecast = document.weather.forecast.take(3)
        labels.forEachIndexed { index, label ->
            val left = 30f + index * 245f
            paint.color = Color.rgb(190, 190, 190)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(RectF(left, 141f, left + 222f, 463f), 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            val day = forecast.getOrNull(index)
            paint.color = if (index == 0) red else blue
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 23f
            canvas.drawText(label, left + 111f, 180f, paint)
            if (day == null) {
                paint.color = black
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 22f
                canvas.drawText(weatherStateText(document.weather), left + 111f, 365f, paint)
            } else {
                drawWeatherIcon(canvas, left + 111f, 270f, day.weatherCode, red, blue, paint)
                paint.color = black
                paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                paint.textSize = 27f
                canvas.drawText(weatherName(day.weatherCode), left + 111f, 365f, paint)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 28f
                paint.color = red
                canvas.drawText("${day.temperatureMaxC}°", left + 73f, 408f, paint)
                paint.color = black
                canvas.drawText("/", left + 111f, 408f, paint)
                paint.color = blue
                canvas.drawText("${day.temperatureMinC}°", left + 153f, 408f, paint)
                paint.color = black
                paint.textSize = 16f
                canvas.drawText(formatForecastDate(day.date), left + 111f, 447f, paint)
            }
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun renderDateMemoTodo(canvas: Canvas, document: DashboardDocument) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val date = dashboardDate(document)
        val pending = document.todos.filter { !it.completed && it.title.isNotBlank() }

        paint.color = Color.BLACK
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 24f
        canvas.drawText("${date.year}年 ${date.monthValue}月", 40f, 60f, paint)
        paint.color = Color.RED
        paint.textSize = 142f
        canvas.drawText(date.dayOfMonth.toString().padStart(2, '0'), 35f, 205f, paint)
        paint.color = Color.BLACK
        paint.textSize = 31f
        canvas.drawText(weekdayText(date), 44f, 252f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 18f
        canvas.drawText(document.cityName.ifBlank { "今日计划" }, 44f, 287f, paint)
        canvas.drawLine(254f, 30f, 254f, 450f, paint.apply { strokeWidth = 2f })

        paint.color = Color.BLUE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 20f
        canvas.drawText("置顶备忘录", 292f, 58f, paint)
        paint.color = Color.BLACK
        paint.textSize = 31f
        drawWrappedText(canvas, document.memo.ifBlank { "今天没有置顶备忘录" }, 292f, 100f, 458f, 38f, 2, paint)
        canvas.drawLine(292f, 184f, 752f, 184f, paint.apply { color = Color.rgb(195, 195, 195); strokeWidth = 1.5f })

        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 23f
        canvas.drawText("待办事项", 292f, 225f, paint)
        val shown = pending.take(3)
        shown.forEachIndexed { index, todo ->
            val baseline = 278f + index * 64f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = if (index == 0) Color.RED else Color.BLUE
            canvas.drawRoundRect(RectF(294f, baseline - 26f, 321f, baseline + 1f), 5f, 5f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 27f
            drawSingleLine(canvas, todo.title, 338f, baseline, 408f, paint)
            if (index < shown.lastIndex) canvas.drawLine(338f, baseline + 24f, 752f, baseline + 24f, paint.apply { color = Color.rgb(220, 220, 220); strokeWidth = 1f })
        }
        if (shown.isEmpty()) {
            paint.color = Color.rgb(90, 90, 90)
            paint.textSize = 27f
            canvas.drawText("今天的待办已全部完成", 294f, 290f, paint)
        }
        if (pending.size > shown.size) {
            paint.color = Color.BLUE
            paint.textSize = 18f
            canvas.drawText("还有 ${pending.size - shown.size} 项待办", 594f, 446f, paint)
        }
    }

    private fun renderWeatherMemoTodo(canvas: Canvas, document: DashboardDocument) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val date = dashboardDate(document)
        val pending = document.todos.filter { !it.completed && it.title.isNotBlank() }
        val forecast = document.weather.forecast.firstOrNull()

        paint.color = Color.RED
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 78f
        canvas.drawText(date.dayOfMonth.toString().padStart(2, '0'), 38f, 100f, paint)
        paint.color = Color.BLACK
        paint.textSize = 23f
        canvas.drawText("${date.monthValue}月 · ${weekdayText(date)}", 142f, 60f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 17f
        canvas.drawText("${date.year}  ${document.cityName.ifBlank { "当前位置" }}", 142f, 92f, paint)

        if (forecast != null) {
            drawWeatherIcon(canvas, 690f, 68f, forecast.weatherCode, Color.RED, Color.BLUE, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 25f
            paint.color = Color.BLACK
            canvas.drawText(weatherName(forecast.weatherCode), 596f, 56f, paint)
            paint.textSize = 21f
            paint.textAlign = Paint.Align.LEFT
            val high = "${forecast.temperatureMaxC}°"
            val separator = " / "
            val low = "${forecast.temperatureMinC}°"
            val temperatureX = 596f - paint.measureText(high + separator + low)
            paint.color = Color.RED
            canvas.drawText(high, temperatureX, 91f, paint)
            paint.color = Color.BLACK
            canvas.drawText(separator, temperatureX + paint.measureText(high), 91f, paint)
            paint.color = Color.BLUE
            canvas.drawText(low, temperatureX + paint.measureText(high + separator), 91f, paint)
        }
        canvas.drawLine(35f, 128f, 765f, 128f, paint.apply { color = Color.BLACK; strokeWidth = 2f })

        paint.color = Color.BLUE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 19f
        canvas.drawText("置顶备忘录", 40f, 169f, paint)
        paint.color = Color.BLACK
        paint.textSize = 30f
        drawWrappedText(canvas, document.memo.ifBlank { "今天没有置顶备忘录" }, 40f, 210f, 710f, 36f, 2, paint)
        canvas.drawLine(40f, 285f, 760f, 285f, paint.apply { color = Color.rgb(200, 200, 200); strokeWidth = 1.5f })

        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 21f
        canvas.drawText("待办", 40f, 326f, paint)
        val shown = pending.take(2)
        shown.forEachIndexed { index, todo ->
            val top = 345f + index * 55f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = if (index == 0) Color.RED else Color.BLUE
            canvas.drawRoundRect(RectF(42f, top, 68f, top + 26f), 5f, 5f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 27f
            drawSingleLine(canvas, todo.title, 88f, top + 24f, 520f, paint)
        }
        if (shown.isEmpty()) {
            paint.color = Color.rgb(90, 90, 90)
            paint.textSize = 26f
            canvas.drawText("今天的待办已全部完成", 42f, 382f, paint)
        }
        if (pending.size > shown.size) {
            paint.color = Color.BLUE
            paint.textSize = 18f
            canvas.drawText("还有 ${pending.size - shown.size} 项", 650f, 448f, paint)
        }
    }

    private fun dashboardDate(document: DashboardDocument): LocalDate =
        runCatching { LocalDate.now(ZoneId.of(document.timezone)) }.getOrElse { LocalDate.now() }

    private fun weekdayText(date: LocalDate): String =
        listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[date.dayOfWeek.value - 1]

    private fun drawSingleLine(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint) {
        val count = paint.breakText(text, true, maxWidth, null)
        val visible = if (count < text.length && count > 1) text.take(count - 1) + "…" else text.take(count)
        canvas.drawText(visible, x, baseline, paint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        lineHeight: Float,
        maxLines: Int,
        paint: Paint,
    ) {
        var remaining = text.trim()
        repeat(maxLines) { line ->
            if (remaining.isEmpty()) return
            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            var visible = remaining.take(count)
            remaining = remaining.drop(count).trimStart()
            if (line == maxLines - 1 && remaining.isNotEmpty() && visible.length > 1) visible = visible.dropLast(1) + "…"
            canvas.drawText(visible, x, firstBaseline + line * lineHeight, paint)
        }
    }

    private fun drawLocationPin(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.BLACK
        val pin = Path().apply {
            moveTo(cx, cy + 12f)
            cubicTo(cx - 12f, cy + 2f, cx - 9f, cy - 10f, cx, cy - 10f)
            cubicTo(cx + 9f, cy - 10f, cx + 12f, cy + 2f, cx, cy + 12f)
        }
        canvas.drawPath(pin, paint)
        canvas.drawCircle(cx, cy - 1f, 3f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawWeatherIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        code: Int,
        red: Int,
        blue: Int,
        paint: Paint,
    ) {
        paint.color = Color.rgb(55, 55, 55)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        when (code) {
            0 -> drawSun(canvas, cx, cy, red, paint)
            1, 2 -> {
                drawSun(canvas, cx + 16f, cy - 28f, red, paint)
                drawCloud(canvas, cx, cy + 7f, paint)
            }
            3 -> drawCloud(canvas, cx, cy, paint)
            45, 48 -> drawFog(canvas, cx, cy, paint)
            in 51..57, 61, 80 -> drawRain(canvas, cx, cy, 1, blue, paint)
            63, 81 -> drawRain(canvas, cx, cy, 2, blue, paint)
            65, 82 -> drawRain(canvas, cx, cy, 3, blue, paint)
            in 71..77, 85, 86 -> {
                drawCloud(canvas, cx, cy - 10f, paint)
                drawSnowflake(canvas, cx, cy + 42f, blue, paint)
            }
            in 95..99 -> drawThunderstorm(canvas, cx, cy, red, blue, paint)
            else -> drawCloud(canvas, cx, cy, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, color: Int, paint: Paint) {
        paint.color = color
        canvas.drawCircle(cx, cy, 23f, paint)
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            canvas.drawLine(
                cx + (34 * kotlin.math.cos(angle)).toFloat(), cy + (34 * kotlin.math.sin(angle)).toFloat(),
                cx + (43 * kotlin.math.cos(angle)).toFloat(), cy + (43 * kotlin.math.sin(angle)).toFloat(), paint,
            )
        }
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        paint.color = Color.rgb(55, 55, 55)
        val cloud = Path().apply {
            moveTo(cx - 52f, cy + 24f)
            cubicTo(cx - 65f, cy + 20f, cx - 66f, cy + 1f, cx - 55f, cy - 8f)
            cubicTo(cx - 49f, cy - 14f, cx - 41f, cy - 16f, cx - 33f, cy - 13f)
            cubicTo(cx - 28f, cy - 34f, cx - 10f, cy - 44f, cx + 8f, cy - 40f)
            cubicTo(cx + 24f, cy - 36f, cx + 34f, cy - 24f, cx + 37f, cy - 10f)
            cubicTo(cx + 53f, cy - 12f, cx + 64f, cy - 1f, cx + 64f, cy + 12f)
            cubicTo(cx + 64f, cy + 21f, cx + 57f, cy + 27f, cx + 48f, cy + 27f)
            lineTo(cx - 43f, cy + 27f)
            cubicTo(cx - 47f, cy + 27f, cx - 50f, cy + 26f, cx - 52f, cy + 24f)
        }
        canvas.drawPath(cloud, paint)
    }

    private fun drawRain(canvas: Canvas, cx: Float, cy: Float, count: Int, blue: Int, paint: Paint) {
        drawCloud(canvas, cx, cy - 10f, paint)
        val positions = when (count) {
            1 -> floatArrayOf(0f)
            2 -> floatArrayOf(-24f, 24f)
            else -> floatArrayOf(-38f, 0f, 38f)
        }
        positions.forEach { drawRaindrop(canvas, cx + it, cy + 43f, blue, paint) }
    }

    private fun drawRaindrop(canvas: Canvas, cx: Float, cy: Float, blue: Int, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.color = blue
        val drop = Path().apply {
            moveTo(cx, cy - 9f)
            cubicTo(cx - 6f, cy - 1f, cx - 6f, cy + 3f, cx - 4f, cy + 7f)
            cubicTo(cx - 1f, cy + 10f, cx + 3f, cy + 10f, cx + 5f, cy + 6f)
            cubicTo(cx + 7f, cy + 2f, cx + 5f, cy - 2f, cx, cy - 9f)
        }
        canvas.drawPath(drop, paint)
        paint.style = Paint.Style.STROKE
    }

    private fun drawThunderstorm(canvas: Canvas, cx: Float, cy: Float, red: Int, blue: Int, paint: Paint) {
        drawCloud(canvas, cx, cy - 10f, paint)
        paint.color = Color.WHITE
        paint.strokeWidth = 7f
        canvas.drawLine(cx - 15f, cy + 17f, cx + 15f, cy + 17f, paint)
        paint.strokeWidth = 4.5f
        drawRaindrop(canvas, cx - 38f, cy + 43f, blue, paint)
        drawRaindrop(canvas, cx + 38f, cy + 43f, blue, paint)
        paint.style = Paint.Style.FILL
        paint.color = red
        val bolt = Path().apply {
            moveTo(cx + 3f, cy + 16f)
            lineTo(cx - 13f, cy + 46f)
            lineTo(cx + 1f, cy + 43f)
            lineTo(cx - 7f, cy + 66f)
            lineTo(cx + 18f, cy + 34f)
            lineTo(cx + 5f, cy + 37f)
            close()
        }
        canvas.drawPath(bolt, paint)
        paint.style = Paint.Style.STROKE
    }

    private fun drawSnowflake(canvas: Canvas, cx: Float, cy: Float, blue: Int, paint: Paint) {
        paint.color = blue
        repeat(3) { index ->
            val angle = Math.toRadians(index * 60.0)
            val dx = (22 * kotlin.math.cos(angle)).toFloat()
            val dy = (22 * kotlin.math.sin(angle)).toFloat()
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
        }
    }

    private fun drawFog(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        drawCloud(canvas, cx, cy - 16f, paint)
        paint.color = Color.BLACK
        canvas.drawLine(cx - 48f, cy + 27f, cx + 48f, cy + 27f, paint)
        canvas.drawLine(cx - 39f, cy + 41f, cx + 39f, cy + 41f, paint)
        canvas.drawLine(cx - 28f, cy + 55f, cx + 28f, cy + 55f, paint)
    }

    private suspend fun awaitDisplaySuccess(jobId: String) {
        repeat(50) {
            val data = client.get("/api/v1/jobs/$jobId").getOrThrow().getJSONObject("data")
            when (data.opt("state")) {
                2, "2", "success", "completed" -> return
                3, "3", "failed", 4, "4", "cancelled", 5, "5", "timeout" ->
                    error(data.optString("error_code").ifBlank { "电子纸刷新失败" })
            }
            delay(1_000)
        }
        error("等待电子纸刷新超时")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal fun weatherName(code: Int): String = when (code) {
    0 -> "晴天"
    1, 2 -> "多云"
    3 -> "阴天"
    45, 48 -> "雾天"
    in 51..57, 61, 80 -> "小雨"
    63, 81 -> "中雨"
    65, 82 -> "大雨"
    in 71..77, 85, 86 -> "雪天"
    in 95..99 -> "雷雨"
    else -> "未知"
}

private fun weatherStateText(weather: DashboardWeather): String = when {
    weather.refreshing -> "更新中"
    weather.state == "waiting_location" -> "请先定位"
    weather.state == "waiting_sta" -> "等待 STA"
    weather.state == "error" -> "天气暂不可用"
    else -> "天气待同步"
}

private fun formatForecastDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("M月d日"))
}.getOrDefault(value)
