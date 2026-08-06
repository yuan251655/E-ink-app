package com.einkphoto.app.ui.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceMediaDisplayProfile
import com.einkphoto.app.core.device.DeviceMediaUploadRequest
import com.einkphoto.app.feature.localalbum.conversion.CandidateSixColorConverter
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.delay
import org.json.JSONObject

internal class DashboardFrameRepository(private val context: Context) {
    private val client = DevelopmentApHttpClient()
    suspend fun uploadAndDisplay(layout: String, memo: String, todos: List<String>, expectedModeRevision: Long): String {
        val bitmap = Bitmap.createBitmap(800, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(254, 253, 249))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 31, 30) }
        paint.textSize = 40f; paint.isFakeBoldText = true; canvas.drawText("信息看板", 48f, 70f, paint)
        paint.isFakeBoldText = false; paint.textSize = 28f; paint.color = Color.rgb(73, 101, 121)
        if (layout != "DateMemoTodo") canvas.drawText("多云  28°  ·  上海市", 48f, 122f, paint)
        paint.color = Color.rgb(35, 31, 30); paint.textSize = 26f; canvas.drawText("今日重点", 48f, 190f, paint)
        paint.textSize = 30f; canvas.drawText(memo.take(24), 48f, 232f, paint)
        paint.color = Color.rgb(220, 210, 205); canvas.drawRect(48f, 254f, 752f, 256f, paint)
        paint.color = Color.rgb(35, 31, 30); paint.textSize = 26f; canvas.drawText("待办", 48f, 300f, paint)
        paint.textSize = 28f; todos.filter { it.isNotBlank() }.take(if (layout == "WeatherMemoTodo") 2 else 3).forEachIndexed { i, title -> canvas.drawText("□ ${title.take(22)}", 56f, 344f + i * 46f, paint) }
        val converted = CandidateSixColorConverter().convert(bitmap)
        val directory = File(context.filesDir, "dashboard_frames").apply { mkdirs() }
        val bin = File(directory, "dashboard-${System.currentTimeMillis()}.bin").apply { writeBytes(converted.candidateBin) }
        val requestId = "dashboard-${UUID.randomUUID()}".take(64)
        val root = client.uploadBinOnly(DeviceMediaUploadRequest(requestId, DeviceMediaCategory.Dashboard, "信息看板", bin, bin.length(), sha256(bin), DeviceMediaDisplayProfile(800,480,192000,"4bpp","six_color_e6","landscape",0,"cover",converted.algorithmVersion))).getOrThrow()
        val mediaId = root.getJSONObject("data").getString("media_id")
        val display = client.postJson("/api/v1/media/$mediaId/display", JSONObject().put("request_id", "dashboard-display-${UUID.randomUUID()}".take(64)).put("expected_mode_revision", expectedModeRevision).put("after_display", "hold")).getOrThrow()
        val jobId = display.getJSONObject("data").optString("job_id")
        check(jobId.isNotBlank()) { "设备没有返回看板显示任务" }
        awaitDisplaySuccess(jobId)
        return mediaId
    }

    private suspend fun awaitDisplaySuccess(jobId: String) {
        repeat(50) {
            val data = client.get("/api/v1/jobs/$jobId").getOrThrow().getJSONObject("data")
            when (data.opt("state")) {
                2, "2", "success", "completed" -> return
                3, "3", "failed", 4, "4", "cancelled", 5, "5", "timeout" -> {
                    error(data.optString("error_code").ifBlank { "电子纸刷新失败" })
                }
            }
            delay(1_000)
        }
        error("等待电子纸刷新超时")
    }
    private fun sha256(file: File): String { val digest=MessageDigest.getInstance("SHA-256"); FileInputStream(file).use { input -> val b=ByteArray(8192); while(true){val n=input.read(b); if(n<=0)break; digest.update(b,0,n)} }; return digest.digest().joinToString(""){"%02x".format(it)} }
}
