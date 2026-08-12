package com.einkphoto.app.feature.aialbum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.einkphoto.app.R
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object VoiceGenerationServiceController {
    private const val preferencesName = "voice_generation_service"
    private const val enabledKey = "enabled"

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        .getBoolean(enabledKey, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().putBoolean(enabledKey, enabled).apply()
        val intent = Intent(context, VoiceGenerationForegroundService::class.java)
        if (enabled) context.startForegroundService(intent) else context.stopService(intent)
    }

    fun restoreIfEnabled(context: Context) {
        if (isEnabled(context)) context.startForegroundService(Intent(context, VoiceGenerationForegroundService::class.java))
    }
}

class VoiceGenerationForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = DevelopmentApHttpClient()
    private lateinit var repository: AiGenerationRepository
    private var worker: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = AiGenerationRepository(applicationContext)
        createNotificationChannel()
        startForeground(notificationId, notification("等待相框语音请求"))
        worker = scope.launch { runWorker() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runWorker() {
        while (scope.isActive) {
            runCatching {
                client.postJson("/api/v1/voice-generation/heartbeat", JSONObject(), userActivity = false).getOrThrow()
                val task = client.get("/api/v1/voice-generation/task").getOrThrow().getJSONObject("data")
                when (task.optString("state")) {
                    "pending_app" -> process(task)
                    "app_generating", "uploading", "displaying" -> update(task.optString("id"), "failed", "app_service_restarted")
                }
            }
            delay(pollIntervalMs)
        }
    }

    private suspend fun process(task: JSONObject) {
        val id = task.optString("id").takeIf { it.matches(safeId) } ?: return
        val prompt = task.optString("prompt").trim().takeIf { it.isNotEmpty() && it.length <= 500 } ?: return
        if (client.postJson("/api/v1/voice-generation/task/claim", JSONObject().put("id", id), userActivity = false).isFailure) return

        var preview: AiGenerationPreview? = null
        try {
            notifyStatus("正在生成图片")
            preview = repository.createDirectPreview(prompt, "voice-$id").getOrThrow()
            update(id, "uploading")

            notifyStatus("正在转换并保存到 TF 卡")
            val uploadJobId = repository.confirmSave(preview, "voice-$id").getOrThrow()
            val mediaId = awaitJob(uploadJobId, uploadPollAttempts).mediaId ?: error("upload_missing_media")

            val mode = client.get("/api/v1/mode").getOrThrow().getJSONObject("data")
            if (mode.optString("active_feature") != "ai_album" || mode.optString("state") != "idle") {
                update(id, "success", "saved_not_displayed")
                notifyStatus("图片已保存，当前未显示")
                return
            }

            update(id, "displaying")
            notifyStatus("正在刷新电子墨水屏")
            val displayJob = client.postJson(
                "/api/v1/media/$mediaId/display",
                JSONObject()
                    .put("request_id", "voice-display-$id".take(64))
                    .put("expected_mode_revision", mode.getLong("revision"))
                    .put("after_display", "continue"),
                userActivity = false,
            ).getOrThrow().getJSONObject("data").getString("job_id")
            awaitJob(displayJob, displayPollAttempts)
            update(id, "success")
            notifyStatus("图片已生成并显示")
        } catch (_: Throwable) {
            update(id, "failed", "app_generation_failed")
            notifyStatus("语音生图失败")
        } finally {
            preview?.uri?.let { uri -> runCatching { File(requireNotNull(Uri.parse(uri).path)).delete() } }
        }
    }

    private suspend fun awaitJob(jobId: String, attempts: Int): AiGenerationJob {
        repeat(attempts) {
            val job = repository.job(jobId).getOrThrow()
            if (job.completed) return job
            if (!job.inProgress) error(job.errorCode ?: "job_failed")
            delay(jobPollIntervalMs)
        }
        error("job_timeout")
    }

    private suspend fun update(id: String, state: String, errorCode: String? = null) {
        val body = JSONObject().put("id", id).put("state", state)
        if (errorCode != null) body.put("error_code", errorCode)
        client.postJson("/api/v1/voice-generation/task", body, userActivity = false)
    }

    private fun notifyStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(notificationId, notification(text))
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_app_icon)
        .setContentTitle("相念语音生图")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "语音生图服务", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private companion object {
        const val channelId = "voice_generation"
        const val notificationId = 3104
        const val pollIntervalMs = 5_000L
        const val jobPollIntervalMs = 1_500L
        const val uploadPollAttempts = 80
        const val displayPollAttempts = 60
        val safeId = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
