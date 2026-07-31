package com.einkphoto.app.feature.mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModeSwitchPhase {
    Idle,
    Queued,
    Preparing,
    Refreshing,
    Finalizing,
    Success,
    Failed,
}

data class ModeSwitchUiState(
    val target: DeviceFeature? = null,
    val jobId: DeviceJobId? = null,
    val phase: ModeSwitchPhase = ModeSwitchPhase.Idle,
    val message: String? = null,
) {
    val switching: Boolean
        get() = phase in setOf(
            ModeSwitchPhase.Queued,
            ModeSwitchPhase.Preparing,
            ModeSwitchPhase.Refreshing,
            ModeSwitchPhase.Finalizing,
        )
}

/** Coordinates one explicit device-authoritative ModeSwitchJob for all three main pages. */
class ModeSwitchViewModel(private val session: DeviceSession) : ViewModel() {
    private val mutableState = MutableStateFlow(ModeSwitchUiState())
    val state: StateFlow<ModeSwitchUiState> = mutableState.asStateFlow()

    fun switchTo(target: DeviceFeature) {
        if (mutableState.value.switching) return
        if (session.snapshot.value.activeFeature == target && session.snapshot.value.pendingFeature == null) {
            mutableState.value = ModeSwitchUiState(target = target, phase = ModeSwitchPhase.Success, message = "已经是当前模式")
            return
        }
        viewModelScope.launch {
            mutableState.value = ModeSwitchUiState(target = target, phase = ModeSwitchPhase.Queued, message = "正在提交模式切换")
            when (val submission = session.requestFeatureSwitch(target)) {
                is DeviceCommandResult.Accepted -> awaitTerminal(target, submission.value)
                is DeviceCommandResult.Rejected -> mutableState.value = ModeSwitchUiState(
                    target = target,
                    phase = ModeSwitchPhase.Failed,
                    message = rejectionText(submission.reason),
                )
            }
        }
    }

    /** Resumes a device-owned switch after App recreation without submitting a duplicate job. */
    fun resumePendingSwitch(target: DeviceFeature, jobId: DeviceJobId) {
        if (mutableState.value.switching && mutableState.value.jobId == jobId) return
        if (mutableState.value.switching) return
        viewModelScope.launch {
            mutableState.value = ModeSwitchUiState(target, jobId, ModeSwitchPhase.Queued, "正在恢复模式切换状态")
            awaitTerminal(target, jobId)
        }
    }

    fun clearFeedback() {
        if (!mutableState.value.switching) mutableState.value = ModeSwitchUiState()
    }

    private suspend fun awaitTerminal(target: DeviceFeature, jobId: DeviceJobId) {
        repeat(120) {
            when (val result = session.modeSwitchJob(jobId)) {
                is DeviceCommandResult.Accepted -> {
                    val job = result.value
                    val phase = phaseFrom(job.phase, job.state)
                    mutableState.value = ModeSwitchUiState(
                        target = target,
                        jobId = jobId,
                        phase = phase,
                        message = phaseText(phase, job.errorCode),
                    )
                    when (job.state) {
                        DeviceJobState.Success -> {
                            repeat(3) { attempt ->
                                val refreshed = session.refreshSnapshot()
                                if (refreshed is DeviceCommandResult.Accepted && refreshed.value.activeFeature == target) {
                                    mutableState.value = ModeSwitchUiState(target, jobId, ModeSwitchPhase.Success, "模式切换完成")
                                    return
                                }
                                if (attempt < 2) delay(750L)
                            }
                            mutableState.value = ModeSwitchUiState(target, jobId, ModeSwitchPhase.Failed, "相框已完成刷新，但模式状态同步失败，请稍后重试")
                            return
                        }
                        DeviceJobState.Failed, DeviceJobState.Cancelled, DeviceJobState.TimedOut, DeviceJobState.Busy -> return
                        DeviceJobState.Queued, DeviceJobState.Running -> Unit
                    }
                }
                is DeviceCommandResult.Rejected -> {
                    mutableState.value = ModeSwitchUiState(target, jobId, ModeSwitchPhase.Failed, rejectionText(result.reason))
                    return
                }
            }
            delay(1_000L)
        }
        mutableState.value = ModeSwitchUiState(target, jobId, ModeSwitchPhase.Failed, "模式切换超时，原模式和原画面保持不变")
    }

    private fun phaseFrom(phase: String, state: DeviceJobState): ModeSwitchPhase = when {
        state == DeviceJobState.Success -> ModeSwitchPhase.Success
        state in setOf(DeviceJobState.Failed, DeviceJobState.Cancelled, DeviceJobState.TimedOut, DeviceJobState.Busy) -> ModeSwitchPhase.Failed
        phase == "preparing" -> ModeSwitchPhase.Preparing
        phase == "refreshing" -> ModeSwitchPhase.Refreshing
        phase == "finalizing" -> ModeSwitchPhase.Finalizing
        else -> ModeSwitchPhase.Queued
    }

    private fun phaseText(phase: ModeSwitchPhase, errorCode: String?): String = when (phase) {
        ModeSwitchPhase.Idle -> ""
        ModeSwitchPhase.Queued -> "正在排队"
        ModeSwitchPhase.Preparing -> "正在准备提示画面"
        ModeSwitchPhase.Refreshing -> "墨水屏刷新中，请耐心等待"
        ModeSwitchPhase.Finalizing -> "正在确认设备模式"
        ModeSwitchPhase.Success -> "模式切换完成"
        ModeSwitchPhase.Failed -> when (errorCode) {
            "display_busy" -> "墨水屏正在刷新，请稍后重新切换"
            "mode_cover_unavailable" -> "模式提示画面不可用，原模式保持不变"
            else -> "切换失败，原模式和原画面保持不变"
        }
    }

    private fun rejectionText(reason: DeviceRejection): String = when (reason) {
        DeviceRejection.Offline -> "相框未连接，请恢复连接后重试"
        DeviceRejection.Sleeping -> "相框正在休眠，请唤醒后重试"
        DeviceRejection.DisplayBusy, DeviceRejection.ModeSwitchBusy -> "相框正在执行其他刷新任务，请稍后重试"
        DeviceRejection.RevisionConflict -> "设备模式刚刚发生变化，请重新点击切换"
        DeviceRejection.TimedOut -> "模式切换超时，原模式保持不变"
        else -> "暂时无法切换模式，请稍后重试"
    }
}
