package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Two-step device-authoritative generation flow:
 * prompt -> generated preview -> explicit TF conversion/save -> independent AI library refresh.
 * It never creates a display request; showing a committed item remains the gallery's own action.
 */
class AiGenerationViewModel(
    private val repository: AiGenerationRepository,
    private val onCompleted: () -> Unit,
) : ViewModel() {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(AiGenerationUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val history = runCatching { repository.loadHistory() }.getOrDefault(emptyList())
            mutableState.value = mutableState.value.copy(history = history)
        }
    }

    /** Existing call-site name retained while its behavior becomes preview-only. */
    fun generate(prompt: String) {
        if (prompt.isBlank() || mutableState.value.active) return
        viewModelScope.launch {
            val normalized = prompt.trim()
            mutableState.value = AiGenerationUiState(
                phase = AiGenerationPhase.CreatingPreview,
                message = "正在创建预览任务…",
                prompt = normalized,
                history = mutableState.value.history,
            )
            val result = repository.createPreview(normalized)
            if (result.isFailure) {
                fail(normalized, generationFailureText(result.exceptionOrNull()?.message))
                return@launch
            }
            val jobId = result.getOrThrow()
            persistHistory(AiGenerationHistoryItem(
                id = jobId,
                prompt = normalized,
                createdAtEpochMillis = System.currentTimeMillis(),
                saveStatus = AiGenerationSaveStatus.Generating,
                preview = null,
                remoteJobId = jobId,
            ))
            awaitPreview(jobId, normalized)
        }
    }

    /** Must only be exposed by the UI after [AiGenerationPhase.PreviewReady]. */
    fun confirmSave() {
        val before = mutableState.value
        val preview = before.preview ?: return
        if (before.phase != AiGenerationPhase.PreviewReady) return
        viewModelScope.launch {
            mutableState.value = before.copy(
                phase = AiGenerationPhase.Saving,
                message = "正在转换并保存到 AI 相册…",
            )
            persistHistory(historyItem(before, AiGenerationSaveStatus.Saving))
            val result = repository.confirmSave(preview.jobId)
            if (result.isFailure) {
                mutableState.value = before.copy(
                    phase = AiGenerationPhase.PreviewReady,
                    message = "尚未保存：${saveFailureText(result.exceptionOrNull()?.message)}",
                )
                persistHistory(historyItem(before, AiGenerationSaveStatus.PreviewReady))
                return@launch
            }
            val saveJobId = result.getOrThrow()
            val saving = mutableState.value.copy(jobId = saveJobId)
            mutableState.value = saving
            persistHistory(historyItem(saving, AiGenerationSaveStatus.Saving))
            awaitSave(saveJobId, saving)
        }
    }

    fun dismissResult() {
        if (mutableState.value.active) return
        mutableState.value = AiGenerationUiState()
    }

    /** Resume a device-owned pending job after App process recreation. Never creates a new task. */
    fun continueQuery(historyId: String) {
        val item = mutableState.value.history.firstOrNull { it.id == historyId } ?: return
        val remoteJobId = item.remoteJobId ?: return
        if (mutableState.value.active) return
        viewModelScope.launch {
            when (item.saveStatus) {
                AiGenerationSaveStatus.Generating -> {
                    mutableState.value = AiGenerationUiState(
                        phase = AiGenerationPhase.GeneratingPreview,
                        message = "正在继续查询生成进度…",
                        prompt = item.prompt,
                        jobId = remoteJobId,
                        history = mutableState.value.history,
                    )
                    awaitPreview(remoteJobId, item.prompt)
                }
                AiGenerationSaveStatus.Saving -> {
                    val preview = item.preview ?: return@launch
                    val before = AiGenerationUiState(
                        phase = AiGenerationPhase.Saving,
                        message = "正在继续查询保存进度…",
                        prompt = item.prompt,
                        jobId = remoteJobId,
                        preview = preview,
                        history = mutableState.value.history,
                    )
                    mutableState.value = before
                    awaitSave(remoteJobId, before)
                }
                AiGenerationSaveStatus.PreviewReady -> item.preview?.let { preview ->
                    mutableState.value = AiGenerationUiState(
                        phase = AiGenerationPhase.PreviewReady,
                        message = "已恢复临时预览。确认后才会保存到 AI 相册。",
                        prompt = item.prompt,
                        jobId = preview.jobId,
                        preview = preview,
                        history = mutableState.value.history,
                    )
                }
                else -> Unit
            }
        }
    }

    /** Stops local tracking and removes the private preview. The device-side job is not cancelled. */
    fun discardHistory(historyId: String) {
        val current = mutableState.value
        val item = current.history.firstOrNull { it.id == historyId } ?: return
        if (current.active && (current.jobId == item.remoteJobId || current.preview?.jobId == item.id)) return
        viewModelScope.launch {
            persistHistory(item.copy(saveStatus = AiGenerationSaveStatus.Cancelled, preview = null))
        }
    }

    private suspend fun awaitPreview(jobId: String, prompt: String) {
        repeat(MAX_POLL_ATTEMPTS) {
            val jobResult = repository.job(jobId)
            if (jobResult.isFailure) {
                fail(prompt, "无法读取生成进度，请稍后重试")
                return
            }
            val job = jobResult.getOrThrow()
            if (job.inProgress) {
                mutableState.value = AiGenerationUiState(
                    phase = AiGenerationPhase.GeneratingPreview,
                    message = previewPhaseText(job.phase, job.progressPercent),
                    prompt = prompt,
                    jobId = jobId,
                    history = mutableState.value.history,
                )
                delay(POLL_INTERVAL_MS)
                return@repeat
            }
            if (job.completed && (job.phase == "preview_ready" || job.phase == "generated" || job.phase == "completed")) {
                val previewResult = repository.downloadPreview(jobId)
                if (previewResult.isFailure) {
                    fail(prompt, "预览生成完成，但暂时无法下载预览图")
                    return
                }
                val previewFile = previewResult.getOrThrow()
                val ready = AiGenerationUiState(
                    phase = AiGenerationPhase.PreviewReady,
                    message = "预览已生成。确认后才会转换并保存到 AI 相册。",
                    prompt = prompt,
                    jobId = jobId,
                    preview = AiGenerationPreview(jobId, prompt, previewFile.uri, previewFile.mimeType, previewFile.sizeBytes),
                    history = mutableState.value.history,
                )
                mutableState.value = ready
                persistHistory(
                    AiGenerationHistoryItem(
                        id = jobId,
                        prompt = prompt,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        saveStatus = AiGenerationSaveStatus.PreviewReady,
                        preview = ready.preview,
                        remoteJobId = jobId,
                    ),
                )
                return
            }
            fail(prompt, "生成失败：${job.errorCode ?: "未知错误"}")
            return
        }
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.GeneratingPreview,
            message = "生成仍在相框端继续处理。返回或重启 App 后会自动恢复此任务，请稍后查看。",
            prompt = prompt,
            jobId = jobId,
            history = mutableState.value.history,
        )
        persistHistory(AiGenerationHistoryItem(
            id = jobId,
            prompt = prompt,
            createdAtEpochMillis = mutableState.value.history.firstOrNull { it.id == jobId }?.createdAtEpochMillis
                ?: System.currentTimeMillis(),
            saveStatus = AiGenerationSaveStatus.Generating,
            preview = null,
            remoteJobId = jobId,
        ))
    }

    private suspend fun awaitSave(saveJobId: String, before: AiGenerationUiState) {
        repeat(MAX_POLL_ATTEMPTS) {
            val jobResult = repository.job(saveJobId)
            if (jobResult.isFailure) {
                mutableState.value = before.copy(phase = AiGenerationPhase.PreviewReady, message = "保存状态无法读取，预览仍可重新保存")
                persistHistory(historyItem(before, AiGenerationSaveStatus.PreviewReady))
                return
            }
            val job = jobResult.getOrThrow()
            if (job.inProgress) {
                mutableState.value = before.copy(
                    phase = AiGenerationPhase.Saving,
                    jobId = saveJobId,
                    message = savePhaseText(job.phase, job.progressPercent),
                )
                delay(POLL_INTERVAL_MS)
                return@repeat
            }
            val mediaId = job.mediaId
            if (job.completed && !mediaId.isNullOrBlank()) {
                onCompleted()
                val saved = before.copy(
                    phase = AiGenerationPhase.Saved,
                    jobId = saveJobId,
                    savedMediaId = mediaId,
                    message = "已保存到 AI 相册。需要显示时，请在图库中选择这张图片。",
                )
                mutableState.value = saved
                persistHistory(
                    AiGenerationHistoryItem(
                        id = before.preview?.jobId ?: saveJobId,
                        prompt = before.prompt.orEmpty(),
                        createdAtEpochMillis = before.history.firstOrNull { it.id == before.preview?.jobId }?.createdAtEpochMillis
                            ?: System.currentTimeMillis(),
                        saveStatus = AiGenerationSaveStatus.Saved,
                        preview = before.preview,
                        mediaId = mediaId,
                        remoteJobId = saveJobId,
                    ),
                )
                return
            }
            mutableState.value = before.copy(
                phase = AiGenerationPhase.PreviewReady,
                message = if (job.completed) "设备未确认已保存的图片，预览仍可重新保存" else "保存失败：${job.errorCode ?: "未知错误"}",
            )
            persistHistory(historyItem(before, AiGenerationSaveStatus.PreviewReady))
            return
        }
        mutableState.value = before.copy(phase = AiGenerationPhase.PreviewReady, message = "保存等待超时，预览仍可重新保存")
        persistHistory(historyItem(before, AiGenerationSaveStatus.PreviewReady))
    }

    private suspend fun fail(prompt: String, message: String) {
        val before = mutableState.value
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.Failed,
            message = message,
            prompt = prompt,
            history = before.history,
        )
        persistHistory(
            AiGenerationHistoryItem(
                id = before.jobId ?: "failed-${System.currentTimeMillis()}",
                prompt = prompt,
                createdAtEpochMillis = System.currentTimeMillis(),
                saveStatus = AiGenerationSaveStatus.Failed,
                preview = null,
            ),
        )
    }

    private suspend fun persistHistory(item: AiGenerationHistoryItem) {
        val history = runCatching { repository.saveHistory(item) }.getOrNull() ?: return
        mutableState.value = mutableState.value.copy(history = history)
    }

    private fun historyItem(state: AiGenerationUiState, status: AiGenerationSaveStatus): AiGenerationHistoryItem {
        val preview = requireNotNull(state.preview)
        return AiGenerationHistoryItem(
            id = preview.jobId,
            prompt = state.prompt.orEmpty(),
            createdAtEpochMillis = state.history.firstOrNull { it.id == preview.jobId }?.createdAtEpochMillis
                ?: System.currentTimeMillis(),
            saveStatus = status,
            preview = preview,
            mediaId = state.savedMediaId,
            remoteJobId = state.jobId ?: preview.jobId,
        )
    }

    private fun generationFailureText(code: String?): String = when (code) {
        "ai_not_configured" -> "请先完成 AI 模型配置"
        "ai_job_busy" -> "相框正在处理另一项 AI 任务，请稍后再试"
        "offline" -> "相框未连接"
        else -> "无法创建预览任务，请检查相框连接、STA 网络和模型配置"
    }

    private fun saveFailureText(code: String?): String = when (code) {
        "storage_no_space" -> "TF 卡空间不足"
        "ai_job_busy" -> "相框正在处理其他任务"
        else -> "请检查相框连接后重试"
    }

    private fun previewPhaseText(phase: String, progress: Int): String = when (phase) {
        "requesting" -> "正在请求图像模型…"
        "downloading" -> "正在下载生成图片…"
        else -> "正在生成预览（${progress.coerceIn(0, 100)}%）…"
    }

    private fun savePhaseText(phase: String, progress: Int): String = when (phase) {
        "converting" -> "正在转换为六色墨水屏画面…"
        "committing" -> "正在保存到 TF 卡…"
        else -> "正在转换并保存（${progress.coerceIn(0, 100)}%）…"
    }

    private companion object {
        // The device may spend 180 seconds waiting for Ark and another minute
        // streaming the generated source. Keep App polling beyond that window
        // and preserve the pending job instead of treating a paid request as a
        // failed image.
        const val MAX_POLL_ATTEMPTS = 600
        const val POLL_INTERVAL_MS = 1_000L
    }
}
