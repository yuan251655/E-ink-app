package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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
            // A process restart must not make a billable device task disappear.
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
            message = "正在提交生成任务…",
            prompt = normalized,
            historyId = historyId,
            history = mergeHistory(mutableState.value.history, submitted),
        )
        viewModelScope.launch {
            persistHistory(submitted)
            submitExisting(historyId, normalized)
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
            message = "正在重新确认相框状态…",
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

    private suspend fun submitExisting(historyId: String, prompt: String) {
        // Do not put a billable request behind a best-effort status read. The
        // ESP endpoint is the sole admission authority and request_id makes
        // this submission idempotent across retries/restarts. A stale AP
        // probe or another low-priority read must never strand a durable
        // message in "waiting to submit" while the device is actually idle.
        Log.i("AiGenerationFlow", "submitting message=${historyId.takeLast(12)}")
        val result = repository.createPreview(prompt, requestIdFor(historyId))
        if (result.isFailure) {
            val code = result.exceptionOrNull()?.message
            if (code == "ai_job_busy") {
                // The device can reject in the final milliseconds of its
                // previous worker cleanup. Do not freeze this user message as
                // a failure: enter the durable wait path and retry only after
                // a fresh authoritative idle observation.
                waitForDeviceSlot(historyId, prompt, repository.activeJob().getOrNull())
            } else {
                fail(prompt, generationFailureText(code), historyId)
            }
            return
        }
        val jobId = result.getOrThrow()
        updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.Generating, remoteJobId = jobId, failureReason = null) }
        mutableState.value = mutableState.value.copy(
            phase = AiGenerationPhase.GeneratingPreview,
            message = "正在生成预览…",
            jobId = jobId,
            historyId = historyId,
        )
        awaitPreview(jobId, prompt, historyId)
    }

    private suspend fun waitForDeviceSlot(historyId: String, prompt: String, active: AiGenerationActiveTask?) {
        updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.WaitingToSubmit, failureReason = null) }
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.WaitingToSubmit,
            message = active?.let(::waitingMessage) ?: "相框正在释放上一项任务；本条等待提交，尚未调用模型",
            prompt = prompt,
            historyId = historyId,
            history = mutableState.value.history,
        )
        repeat(MAX_WAIT_ATTEMPTS) {
            delay(WAIT_POLL_INTERVAL_MS)
            if (mutableState.value.history.firstOrNull { it.id == historyId }?.saveStatus != AiGenerationSaveStatus.WaitingToSubmit) return
            val current = repository.activeJob()
            if (current.isFailure) {
                mutableState.value = mutableState.value.copy(message = "暂时无法读取上一任务状态，正在安全重试提交")
                submitExisting(historyId, prompt)
                return
            }
            val task = current.getOrNull()
            if (task == null) {
                submitExisting(historyId, prompt)
                return
            }
            mutableState.value = mutableState.value.copy(message = waitingMessage(task))
        }
        // Keep this durable wait card instead of pretending the model request failed.
        mutableState.value = mutableState.value.copy(message = "仍在等待上一项任务结束；本条尚未提交、不会扣费")
    }

    private fun waitingMessage(task: AiGenerationActiveTask): String =
        "上一项任务正在${activePhaseLabel(task.phase)}；本条等待提交，尚未调用模型"

    private fun activePhaseLabel(phase: String): String = when (phase) {
        "requesting" -> "请求模型"
        "downloading_preview", "downloading" -> "下载生成图片"
        "converting" -> "转换六色画面"
        "committing" -> "写入 TF 卡"
        "queued" -> "排队等待"
        else -> "处理中"
    }

    private fun requestIdFor(historyId: String): String = "ai-preview-$historyId".take(64)

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
                        historyId = item.id,
                        jobId = remoteJobId,
                        history = mutableState.value.history,
                    )
                    awaitPreview(remoteJobId, item.prompt, item.id)
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

    /** Stops local tracking and removes the private preview. The device-side job is not cancelled. */
    fun discardHistory(historyId: String) {
        val current = mutableState.value
        val item = current.history.firstOrNull { it.id == historyId } ?: return
        if (current.active && (current.jobId == item.remoteJobId || current.preview?.jobId == item.id)) return
        viewModelScope.launch {
            persistHistory(item.copy(saveStatus = AiGenerationSaveStatus.Cancelled, preview = null))
        }
    }

    /**
     * A busy reply means the App has no authority to infer failure or cancel
     * anything. Ask the device for its single active job and restore that
     * device-owned task only when the endpoint confirms one exists.
     */
    private suspend fun recoverActiveTaskAfterBusy() {
        val history = mutableState.value.history
        repository.activeJob().onSuccess { active ->
            if (active == null) {
                mutableState.value = AiGenerationUiState(
                    message = "相框上的生成任务已结束，可以重新尝试。",
                    history = history,
                )
                return@onSuccess
            }
            val prompt = active.promptSummary.ifBlank { "正在恢复相框上的生成任务" }
            mutableState.value = AiGenerationUiState(
                message = "已找到相框正在处理的任务，正在继续查询…",
                history = history,
            )
            persistHistory(
                AiGenerationHistoryItem(
                    id = active.jobId,
                    prompt = prompt,
                    createdAtEpochMillis = history.firstOrNull { it.id == active.jobId }?.createdAtEpochMillis
                        ?: System.currentTimeMillis(),
                    saveStatus = AiGenerationSaveStatus.Generating,
                    preview = null,
                    remoteJobId = active.jobId,
                ),
            )
            // Reuse the normal job polling path; it does not create another
            // generation request or change the device-side task.
            continueQuery(active.jobId)
        }.onFailure {
            mutableState.value = AiGenerationUiState(
                message = "相框正在处理任务，但暂时无法读取进度。请稍后再次打开本页继续查询。",
                history = history,
            )
        }
    }

    private suspend fun awaitPreview(jobId: String, prompt: String, historyId: String) {
        repeat(MAX_POLL_ATTEMPTS) {
            val jobResult = repository.job(jobId)
            if (jobResult.isFailure) {
                if (recoverLostTerminalPreview(jobId, prompt, historyId)) return
                markPendingUnknown(jobId, prompt, historyId)
                return
            }
            val job = jobResult.getOrThrow()
            if (job.inProgress) {
                mutableState.value = AiGenerationUiState(
                    phase = AiGenerationPhase.GeneratingPreview,
                    message = previewPhaseText(job.phase, job.progressPercent),
                    prompt = prompt,
                    historyId = historyId,
                    jobId = jobId,
                    history = mutableState.value.history,
                )
                delay(POLL_INTERVAL_MS)
                return@repeat
            }
            if (job.completed && (job.phase == "preview_ready" || job.phase == "generated" || job.phase == "completed")) {
                val previewResult = repository.downloadPreview(jobId)
                if (previewResult.isFailure) {
                    if (recoverLostTerminalPreview(jobId, prompt, historyId)) return
                    if (previewResult.exceptionOrNull()?.message == "preview_decode_failed") {
                        fail(
                            prompt = prompt,
                            message = "相框收到的生成图片不完整，无法预览或转换保存。本次任务已结束，不会自动重新生成或再次扣费。",
                            historyId = historyId,
                        )
                        return
                    }
                    fail(prompt, "预览已生成，但暂时无法下载到手机；请稍后继续查询，不会自动重新生成", historyId = historyId)
                    return
                }
                val previewFile = previewResult.getOrThrow()
                val ready = AiGenerationUiState(
                    phase = AiGenerationPhase.PreviewReady,
                    message = "预览已生成。确认后才会转换并保存到 AI 相册。",
                    prompt = prompt,
                    historyId = historyId,
                    jobId = jobId,
                    preview = AiGenerationPreview(jobId, prompt, previewFile.uri, previewFile.mimeType, previewFile.sizeBytes),
                    history = mutableState.value.history,
                )
                mutableState.value = ready
                updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.PreviewReady, preview = ready.preview, remoteJobId = jobId, failureReason = null) }
                return
            }
            fail(prompt, generationFailureMessage(job.errorCode), historyId, job.errorCode)
            return
        }
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.GeneratingPreview,
            message = "生成仍在相框端继续处理。返回或重启 App 后会自动恢复此任务，请稍后查看。",
            prompt = prompt,
            historyId = historyId,
            jobId = jobId,
            history = mutableState.value.history,
        )
        updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.Generating, remoteJobId = jobId) }
    }

    /**
     * A terminal job can outlive its RAM record after a device restart. If the
     * device confirms that this exact preview-generation job already completed
     * but its temporary source is gone, keeping the composer locked would
     * falsely imply that a billable task is still running. Never resubmit here:
     * that would create a second model charge without the user's approval.
     */
    private suspend fun recoverLostTerminalPreview(jobId: String, prompt: String, historyId: String): Boolean {
        val diagnostic = repository.lastTaskDiagnostic().getOrNull() ?: return false
        val isSameCompletedPreview = diagnostic.jobId == jobId &&
            diagnostic.kind == "generate_preview" &&
            diagnostic.state in setOf("success", "completed", "2")
        if (!isSameCompletedPreview) return false

        fail(
            prompt = prompt,
            message = "设备在重启前已完成生成，但临时预览未能恢复，尚未保存到 AI 相册。此任务已结束；如需重新生成会再次产生模型费用。",
            historyId = historyId,
        )
        return true
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
                    message = "相框返回的生成图片无法解析，未保存到 AI 相册。本次任务已结束，不会自动重新生成或再次扣费。",
                    historyId = requireNotNull(before.historyId),
                    errorCode = job.errorCode,
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
        loadLastTaskDiagnostic()
    }

    private suspend fun loadLastTaskDiagnostic() {
        // This endpoint is diagnostic-only. It cannot recreate a task and
        // returns no full prompt, provider body or credentials.
        if (mutableState.value.active || mutableState.value.phase != AiGenerationPhase.Failed) return
        repository.lastTaskDiagnostic().onSuccess { diagnostic ->
            mutableState.value = mutableState.value.copy(lastTaskDiagnostic = diagnostic)
        }
    }

    private suspend fun markPendingUnknown(jobId: String, prompt: String, historyId: String) {
        val message = "状态暂时无法读取，请继续查询；请勿重复生成。"
        mutableState.value = AiGenerationUiState(
            phase = AiGenerationPhase.GeneratingPreview,
            message = message,
            prompt = prompt,
            historyId = historyId,
            jobId = jobId,
            history = mutableState.value.history,
        )
        updateHistory(historyId) { it.copy(saveStatus = AiGenerationSaveStatus.Generating, failureReason = message, remoteJobId = jobId) }
    }

    private fun generationFailureMessage(code: String?): String = when (code) {
        "ai_request_timeout" -> "生成超时：模型服务响应较慢，请稍后重试"
        "ai_http_401" -> "生成失败：API Key 无效或已失效"
        "ai_http_403" -> "生成失败：当前 Key 没有该模型权限"
        "ai_http_400" -> "生成失败：模型 ID 或请求参数不兼容"
        "ai_http_404" -> "生成失败：未找到当前模型或接口地址"
        "ai_http_429" -> "生成失败：模型服务限流，请稍后再试"
        "ai_network_failed" -> "生成失败：相框无法访问模型服务"
        "ai_tls_failed" -> "生成失败：安全连接异常，请检查网络时间"
        "ai_invalid_provider_response" -> "生成失败：模型服务返回格式不兼容"
        "ai_preview_commit_failed" -> "生成成功，但临时预览写入 TF 卡时发生冲突；请重新生成"
        "ai_download_failed" -> "生成成功，但图片下载到相框失败"
        "ai_download_tls_failed" -> "生成成功，但相框与图片服务器的安全连接失败"
        "ai_download_timeout" -> "生成成功，但相框下载图片超时"
        "ai_download_network_failed" -> "生成成功，但相框无法连接图片服务器"
        "ai_download_http_4xx" -> "生成成功，但图片临时链接已失效或无权限"
        "ai_download_http_5xx" -> "生成成功，但图片服务器暂时异常"
        "ai_download_redirect_failed" -> "生成成功，但图片下载跳转失败"
        "ai_download_storage_failed" -> "生成成功，但相框写入临时图片失败"
        "ai_source_too_large" -> "生成图片超过相框可处理大小"
        "ai_conversion_memory" -> "相框内存不足，无法处理生成图片"
        "ai_conversion_failed" -> "生成图片无法转换为电子纸预览"
        else -> "生成失败：${code ?: "设备未返回错误码"}"
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
        const val WAIT_POLL_INTERVAL_MS = 2_000L
        const val MAX_WAIT_ATTEMPTS = 240
    }
}
