package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.einkphoto.app.core.device.DevelopmentApHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AiGenerationUiState(val active: Boolean = false, val message: String? = null)

class AiGenerationViewModel(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
    private val onCompleted: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AiGenerationUiState())
    val state = mutableState.asStateFlow()

    fun generate(prompt: String) {
        if (prompt.isBlank() || mutableState.value.active) return
        viewModelScope.launch {
            mutableState.value = AiGenerationUiState(true, "正在创建生图任务…")
            val result = client.postJson("/api/v1/ai/generation/jobs", JSONObject()
                .put("request_id", "ai-app-${System.currentTimeMillis()}")
                .put("prompt", prompt.trim()).put("display_when_active", true))
            result.onFailure { error ->
                mutableState.value = AiGenerationUiState(false, when (error.message) {
                    "ai_not_configured" -> "请先完成 AI 模型配置"
                    "ai_job_busy" -> "相框正在生成另一张图片，请稍后再试"
                    else -> "创建失败，请检查相框连接、STA 网络和模型配置"
                })
                return@launch
            }
            val jobId = result.getOrThrow().optJSONObject("data")?.optString("job_id").orEmpty()
            if (jobId.isBlank()) { mutableState.value = AiGenerationUiState(false, "设备没有返回有效任务号"); return@launch }
            repeat(240) {
                val jobResult = client.get("/api/v1/jobs/$jobId")
                if (jobResult.isFailure) { mutableState.value = AiGenerationUiState(false, "无法读取生成进度，请稍后到 AI 图片中查看结果"); return@launch }
                val job = jobResult.getOrThrow().optJSONObject("data") ?: run { mutableState.value = AiGenerationUiState(false, "设备返回的任务状态无效"); return@launch }
                when (job.opt("state")) {
                    0, "0", "queued", 1, "1", "running" -> { mutableState.value = AiGenerationUiState(true, phaseText(job.optString("phase", "queued"), job.optInt("progress_percent", 0))); delay(1_000) }
                    2, "2", "success", "completed" -> { onCompleted(); mutableState.value = AiGenerationUiState(false, "图片已生成并保存到 AI 相册"); return@launch }
                    else -> { mutableState.value = AiGenerationUiState(false, "生成失败：${job.optString("error_code", "未知错误")}"); return@launch }
                }
            }
            mutableState.value = AiGenerationUiState(false, "等待生成超时，请稍后在 AI 图片中查看")
        }
    }
    private fun phaseText(phase: String, progress: Int): String = when (phase) {
        "requesting" -> "正在请求图像模型…"; "downloading" -> "正在下载生成图片…"; "converting" -> "正在转换为六色墨水屏画面…"; "committing" -> "正在保存到 TF 卡…"; "displaying" -> "图片已保存，正在刷新墨水屏…"; else -> "正在生成图片（${progress.coerceIn(0, 100)}%）…"
    }
}
