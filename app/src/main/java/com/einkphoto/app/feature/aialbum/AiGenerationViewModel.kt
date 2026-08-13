package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** App-owned generation flow: Seedream preview on the phone, then explicit BIN-only AI album upload. */
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
            // Restore unfinished local history without silently submitting a second billable request.
            history.firstOrNull { it.saveStatus in setOf(AiGenerationSaveStatus.WaitingToSubmit, AiGenerationSaveStatus.Generating, AiGenerationSaveStatus.Saving) }
                ?.let { item ->
                    // Do not route the durable startup recovery through the UI retry guard.
                    // A previously persisted wait must always be checked again after an App
                    // restart; otherwise a now-idle device could leave it stranded forever.
                    if (item.saveStatus == AiGenerationSaveStatus.WaitingToSubmit) resumeWaitingSubmission(item)
                    else continueQuery(item.id)
                }
        }
    }

    /** Existing call-site name retained while its behavior becomes preview-only. */
    fun generate(prompt: String) {
        val normalized = prompt.trim()
        if (normalized.isBlank()) return
        // Insert the user's bubble before touching the network. This is the
        // source of truth for the whole conversation and prevents a busy or
        // failed request from swallowing what the user just typed.
        val historyId = "message-${UUID.randomUUID()}"
        val submitted = AiGenerationHistoryItem(
            id = historyId,
            prompt = normalized,
            createdAtEpochMillis = System.currentTimeMillis(),
            saveStatus = AiGenerationSaveStatus.Submitting,
            preview = null,
        )
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.CreatingPreview,
            message = "正在连接 Seedream…",
            prompt = normalized,
            historyId = historyId,
            history = mergeHistory(mutableState.value.history, submitted),
        )
        viewModelScope.launch {
            persistHistory(submitted)
            val result = repository.createDirectPreview(normalized, historyId)
            if (result.isFailure) {
                fail(normalized, generationFailureText(result.exceptionOrNull()?.message), historyId)
                return@launch
            }
            val preview = result.getOrThrow()
            updateHistory(historyId) {
                it.copy(
                    saveStatus = AiGenerationSaveStatus.PreviewReady,
                    preview = preview,
                    remoteJobId = null,
                    failureReason = null,
                )
            }
            mutableState.value = AiGenerationUiState(
                phase = AiGenerationPhase.PreviewReady,
                message = "预览已生成并保存在手机。确认后才会转换并上传到 AI 相册。",
                prompt = normalized,
                historyId = historyId,
                jobId = preview.jobId,
                preview = preview,
                history = mutableState.value.history,
            )
        }
    }

    /** Photo-style mode is App-owned: Seedream receives the prepared JPEG and the ESP is not
     * contacted until the user explicitly saves the final six-color BIN. */
    fun generatePhotoStyle(prompt: String, sourceUri: Uri) {
        val normalized = prompt.trim()
        if (normalized.isBlank()) return
        val historyId = "style-${UUID.randomUUID()}"
        val submitted = AiGenerationHistoryItem(
            id = historyId,
            prompt = "照片风格转换",
            createdAtEpochMillis = System.currentTimeMillis(),
            saveStatus = AiGenerationSaveStatus.Submitting,
            preview = null,
        )
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.CreatingPreview,
            message = "正在准备照片并连接 Seedream…",
            prompt = "照片风格转换",
            historyId = historyId,
            history = mergeHistory(mutableState.value.history, submitted),
        )
        viewModelScope.launch {
            persistHistory(submitted)
            val reference = withContext(Dispatchers.Default) { repository.preparePhotoStyleReference(sourceUri) }
            if (reference.isFailure) {
                fail("照片风格转换", generationFailureText(reference.exceptionOrNull()?.message), historyId)
                return@launch
            }
            val result = repository.createDirectPhotoStylePreview(normalized, historyId, reference.getOrThrow())
            if (result.isFailure) {
                fail("照片风格转换", generationFailureText(result.exceptionOrNull()?.message), historyId)
                return@launch
            }
            val preview = result.getOrThrow()
            updateHistory(historyId) {
                it.copy(
                    saveStatus = AiGenerationSaveStatus.PreviewReady,
                    preview = preview,
                    remoteJobId = null,
                    failureReason = null,
                )
            }
            val ready = AiGenerationUiState(
                phase = AiGenerationPhase.PreviewReady,
                message = "预览已生成并保存在手机。确认后才会转换并上传到 AI 相册。",
                prompt = "照片风格转换",
                historyId = historyId,
                jobId = preview.jobId,
                preview = preview,
                history = mutableState.value.history,
            )
            mutableState.value = ready
        }
    }

    /** Reuses the original chat card and queries device state immediately before a billable submit. */
    fun retrySubmission(historyId: String) {
        val item = mutableState.value.history.firstOrNull { it.id == historyId } ?: return
        if (item.saveStatus !in setOf(AiGenerationSaveStatus.Failed, AiGenerationSaveStatus.WaitingToSubmit) ||
            (item.saveStatus == AiGenerationSaveStatus.Failed && !item.failureReason.orEmpty().startsWith("未提交"))) return
        resumeWaitingSubmission(item)
    }

    /** One durable waiting message is allowed to resume even after process recreation. */
    private fun resumeWaitingSubmission(item: AiGenerationHistoryItem) {
        Log.i("AiGenerationFlow", "resume waiting message=${item.id.takeLast(12)}")
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.CreatingPreview,
            message = "正在恢复手机上的生成请求…",
            prompt = item.prompt,
            historyId = item.id,
            history = mutableState.value.history,
        )
        viewModelScope.launch {
            Log.i("AiGenerationFlow", "checking device before submit message=${item.id.takeLast(12)}")
            updateHistory(item.id) { it.copy(saveStatus = AiGenerationSaveStatus.Submitting, failureReason = null, remoteJobId = null) }
            submitExisting(item.id, item.prompt)
        }
    }

    /** Cancels only the App-side waiting request. It never cancels another device-owned task. */
    fun cancelWaitingSubmission(historyId: String) {
        val item = mutableState.value.history.firstOrNull { it.id == historyId } ?: return
        if (item.saveStatus != AiGenerationSaveStatus.WaitingToSubmit) return
        viewModelScope.launch {
            updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.Cancelled, failureReason = "已取消等待提交；未调用模型") }
            if (mutableState.value.historyId == historyId) mutableState.value = AiGenerationUiState(history = mutableState.value.history)
        }
    }

    /** Clearing history is intentionally unavailable while a request or TF save is active. */
    fun clearHistory() {
        if (mutableState.value.active) return
        viewModelScope.launch {
            val cleared = runCatching { repository.clearHistory() }.isSuccess
            if (cleared) {
                mutableState.value = AiGenerationUiState(message = "已清理本机对话记录和临时预览")
            } else {
                mutableState.value = mutableState.value.copy(message = "清理本机对话记录失败，请重试")
            }
        }
    }

    private suspend fun submitExisting(historyId: String, prompt: String) {
        val result = repository.createDirectPreview(prompt, historyId)
        if (result.isFailure) {
            fail(prompt, generationFailureText(result.exceptionOrNull()?.message), historyId)
            return
        }
        val preview = result.getOrThrow()
        updateHistory(historyId) {
            it.copy(
                saveStatus = AiGenerationSaveStatus.PreviewReady,
                preview = preview,
                remoteJobId = null,
                failureReason = null,
            )
        }
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.PreviewReady,
            message = "预览已生成并保存在手机。确认后才会转换并上传到 AI 相册。",
            prompt = prompt,
            historyId = historyId,
            jobId = preview.jobId,
            preview = preview,
            history = mutableState.value.history,
        )
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
            val result = repository.confirmSave(preview, requireNotNull(before.historyId))
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
        mutableState.value = AiGenerationUiState(history = mutableState.value.history)
    }

    /** Resume a device-owned pending job after App process recreation. Never creates a new task. */
    fun continueQuery(historyId: String) {
        val item = mutableState.value.history.firstOrNull { it.id == historyId } ?: return
        val remoteJobId = item.remoteJobId ?: return
        if (mutableState.value.active) return
        viewModelScope.launch {
            when (item.saveStatus) {
                AiGenerationSaveStatus.Generating -> {
                    fail(
                        item.prompt,
                        "该任务来自已移除的相框端生成链路，无法继续查询。请重新生成；新任务将由手机直连 Seedream。",
                        item.id,
                    )
                }
                AiGenerationSaveStatus.Saving -> {
                    val preview = item.preview ?: return@launch
                    val before = AiGenerationUiState(
                        phase = AiGenerationPhase.Saving,
                        message = "正在继续查询保存进度…",
                        prompt = item.prompt,
                        historyId = item.id,
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
                        historyId = item.id,
                        jobId = preview.jobId,
                        preview = preview,
                        history = mutableState.value.history,
                    )
                }
                else -> Unit
            }
        }
    }

    /** Stops local tracking and removes the private preview. */
    fun discardHistory(historyId: String) {
        val current = mutableState.value
        val item = current.history.firstOrNull { it.id == historyId } ?: return
        if (current.active && (current.jobId == item.remoteJobId || current.preview?.jobId == item.id)) return
        viewModelScope.launch {
            persistHistory(item.copy(saveStatus = AiGenerationSaveStatus.Cancelled, preview = null))
        }
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
                updateHistory(requireNotNull(before.historyId)) {
                    it.copy(saveStatus = AiGenerationSaveStatus.Saved, preview = before.preview, mediaId = mediaId, remoteJobId = saveJobId, failureReason = null)
                }
                return
            }
            if (job.errorCode in setOf("ai_source_invalid", "ai_conversion_failed")) {
                fail(
                    prompt = before.prompt.orEmpty(),
                    message = "相框未能接收转换后的六色图片，尚未保存到 TF 卡；手机预览仍然保留。",
                    historyId = requireNotNull(before.historyId),
                    errorCode = job.errorCode,
                )
                return
            }
            mutableState.value = before.copy(
                phase = AiGenerationPhase.PreviewReady,
                message = if (job.completed) "相框未确认图片已写入 TF 卡，预览仍可重新保存" else "保存失败：${job.errorCode ?: "未知错误"}",
            )
            persistHistory(historyItem(before, AiGenerationSaveStatus.PreviewReady))
            return
        }
        mutableState.value = before.copy(phase = AiGenerationPhase.PreviewReady, message = "保存等待超时，预览仍可重新保存")
        persistHistory(historyItem(before, AiGenerationSaveStatus.PreviewReady))
    }

    private suspend fun fail(prompt: String, message: String, historyId: String, errorCode: String? = null) {
        val before = mutableState.value
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.Failed,
            message = message,
            prompt = prompt,
            historyId = historyId,
            history = before.history,
        )
        updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.Failed, failureReason = message) }
        if (errorCode != null) loadLastTaskDiagnostic()
    }

    private suspend fun loadLastTaskDiagnostic() {
        // This endpoint is diagnostic-only. It cannot recreate a task and
        // returns no full prompt, provider body or credentials.
        if (mutableState.value.active || mutableState.value.phase != AiGenerationPhase.Failed) return
        repository.lastTaskDiagnostic().onSuccess { diagnostic ->
            mutableState.value = mutableState.value.copy(lastTaskDiagnostic = diagnostic)
        }
    }

    private suspend fun persistHistory(item: AiGenerationHistoryItem) {
        val history = runCatching { repository.saveHistory(item) }.getOrNull() ?: return
        mutableState.value = mutableState.value.copy(history = history)
    }

    private suspend fun updateHistory(id: String, transform: (AiGenerationHistoryItem) -> AiGenerationHistoryItem) {
        val existing = mutableState.value.history.firstOrNull { it.id == id } ?: return
        val updated = transform(existing)
        mutableState.value = mutableState.value.copy(history = mergeHistory(mutableState.value.history, updated))
        persistHistory(updated)
    }

    private fun mergeHistory(history: List<AiGenerationHistoryItem>, item: AiGenerationHistoryItem): List<AiGenerationHistoryItem> =
        (history.filterNot { it.id == item.id } + item).sortedByDescending { it.createdAtEpochMillis }

    private fun historyItem(state: AiGenerationUiState, status: AiGenerationSaveStatus): AiGenerationHistoryItem {
        val preview = requireNotNull(state.preview)
        return AiGenerationHistoryItem(
            id = state.historyId ?: preview.jobId,
            prompt = state.prompt.orEmpty(),
            createdAtEpochMillis = state.history.firstOrNull { it.id == state.historyId }?.createdAtEpochMillis
                ?: System.currentTimeMillis(),
            saveStatus = status,
            preview = preview,
            mediaId = state.savedMediaId,
            remoteJobId = state.jobId ?: preview.jobId,
        )
    }

    private fun generationFailureText(code: String?): String = when (code) {
        "app_ai_not_configured" -> "请先在“小智 AI 设置”中保存 Seedream 的 HTTPS 接入点、模型和 API Key"
        "prompt_required" -> "缺少生成提示词"
        "prompt_too_long" -> "提示词过长，请重新选择风格后再试"
        "ai_generation_busy" -> "已有图片正在生成，请等待当前任务完成"
        "ai_generation_already_submitted" -> "这次生成请求已经提交过，为避免重复扣费不会再次提交；如需重试请新建一次创作"
        "seedream_response_too_large", "seedream_image_too_large" -> "Seedream 返回内容过大，已为安全起见停止处理"
        "seedream_download_commit_failed" -> "生成图片保存失败，请检查手机存储空间"
        "reference_image_unavailable" -> "所选照片无法读取，请重新选择一张图片"
        "seedream_http_401" -> "Seedream API Key 无效或已失效"
        "seedream_http_403" -> "当前 API Key 没有该模型的访问权限"
        "seedream_http_404" -> "未找到 Seedream 接入点或模型，请检查设置"
        "seedream_http_429" -> "Seedream 当前限流，请稍后重试"
        "seedream_invalid_response" -> "Seedream 返回格式不兼容，请检查接入点设置"
        "seedream_empty_image", "preview_decode_failed" -> "Seedream 返回的图片无效，请重新生成"
        else -> "手机直连 Seedream 失败：${code ?: "未知错误"}"
    }

    private fun saveFailureText(code: String?): String = when (code) {
        "storage_no_space" -> "TF 卡空间不足"
        "storage_busy" -> "TF 卡正在处理其他操作，请稍后重试"
        "storage_unavailable", "storage_write_failed" -> "TF 卡当前不可写，请检查相框存储状态"
        "unsupported" -> "当前相框固件不支持 AI 相册上传，请升级固件后重试"
        "checksum_mismatch", "media_incomplete" -> "图片传输校验失败，请重试保存"
        "ai_job_busy" -> "相框正在处理其他任务"
        else -> "请检查相框连接后重试"
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
        const val WAIT_POLL_INTERVAL_MS = 2_000L
        const val MAX_WAIT_ATTEMPTS = 240
    }
}
