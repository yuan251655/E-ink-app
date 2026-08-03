package com.einkphoto.app.feature.aialbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiImageViewModel(
    private val session: DeviceSession,
    private val repository: AiImageRepository,
) : ViewModel() {
    private val loadState = MutableStateFlow(AiImageLoadState.Idle)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val actionMessage = MutableStateFlow<String?>(null)
    private val loadingMore = MutableStateFlow(false)

    private val coreState = combine(
        loadState,
        repository.images,
        errorMessage,
        actionMessage,
        repository.activeJob,
    ) { load, images, error, action, job ->
        AiImageUiState(load, images, error, displayJobMessage(job) ?: action, job)
    }

    val state = combine(coreState, repository.hasMore, loadingMore, session.snapshot) { core, hasMore, loading, snapshot ->
        core.copy(
            loadState = if (snapshot.connection == DeviceConnectionState.Online) core.loadState else AiImageLoadState.Offline,
            errorMessage = if (snapshot.connection == DeviceConnectionState.Online) core.errorMessage else null,
            hasMore = hasMore,
            loadingMore = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AiImageUiState())

    fun refresh() {
        if (loadState.value == AiImageLoadState.Loading) return
        viewModelScope.launch {
            val snapshot = session.snapshot.value
            if (snapshot.connection != DeviceConnectionState.Online) {
                loadState.value = AiImageLoadState.Offline
                errorMessage.value = null
                return@launch
            }
            // Keep the previous gallery visible while it is refreshed.  The
            // AI album is preloaded from its home page, so replacing a usable
            // grid with a full-page spinner on every navigation feels much
            // slower than the local album and provides no useful feedback.
            if (repository.images.value.isEmpty()) {
                loadState.value = AiImageLoadState.Loading
            }
            errorMessage.value = null
            when (val result = repository.refresh()) {
                is DeviceCommandResult.Accepted -> loadState.value = AiImageLoadState.Ready
                is DeviceCommandResult.Rejected -> {
                    val sessionOnline = session.snapshot.value.connection == DeviceConnectionState.Online
                    loadState.value = if (
                        result.reason == DeviceRejection.Offline &&
                        !sessionOnline
                    ) AiImageLoadState.Offline else AiImageLoadState.Error
                    errorMessage.value = if (result.reason == DeviceRejection.Offline && sessionOnline) {
                        "暂时无法读取 AI 图片，请稍后重试。"
                    } else {
                        rejectionText(result.reason)
                    }
                }
            }
        }
    }

    fun display(mediaId: String) = runAction {
        when (val result = repository.display(mediaId)) {
            is DeviceCommandResult.Accepted -> actionMessage.value = "已提交显示请求，正在等待相框刷新"
            is DeviceCommandResult.Rejected -> actionMessage.value = "暂时无法显示到相框：${rejectionText(result.reason)}"
        }
    }

    fun loadMore() {
        if (loadingMore.value || !repository.hasMore.value) return
        viewModelScope.launch {
            loadingMore.value = true
            when (val result = repository.loadMore()) {
                is DeviceCommandResult.Accepted -> Unit
                is DeviceCommandResult.Rejected -> {
                    if (result.reason == DeviceRejection.RevisionConflict) {
                        refresh()
                    } else {
                        actionMessage.value = "暂时无法加载更多：${rejectionText(result.reason)}"
                    }
                }
            }
            loadingMore.value = false
        }
    }

    fun delete(mediaId: String) = runAction {
        when (val result = repository.delete(mediaId)) {
            is DeviceCommandResult.Accepted -> actionMessage.value = "图片已删除"
            is DeviceCommandResult.Rejected -> actionMessage.value = "暂时无法删除：${rejectionText(result.reason)}"
        }
    }

    fun saveToPhone(mediaId: String) = runAction {
        when (val result = repository.exportToPhone(mediaId)) {
            is DeviceCommandResult.Accepted -> actionMessage.value = when (result.value.kind) {
                AiImageExportKind.Source -> "原图已保存到手机：${result.value.displayName}"
                AiImageExportKind.SixColorPreview -> "设备未保存原图，六色预览图已保存到手机：${result.value.displayName}"
            }
            is DeviceCommandResult.Rejected -> actionMessage.value = "暂时无法保存到手机：${rejectionText(result.reason)}"
        }
    }

    fun playbackStartUnavailable() {
        actionMessage.value = "暂时无法设置轮播起点，请稍后再试。"
    }

    fun clearMessage() {
        actionMessage.value = null
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            actionMessage.value = null
            block()
        }
    }

    private fun displayJobMessage(job: com.einkphoto.app.core.device.DeviceJob?): String? = when (job?.state) {
        DeviceJobState.Queued, DeviceJobState.Running -> "相框正在刷新，请耐心等待"
        DeviceJobState.Success -> "已显示到相框"
        DeviceJobState.Failed -> "显示失败，当前画面保持不变"
        DeviceJobState.Cancelled -> "显示请求已取消"
        DeviceJobState.TimedOut -> "等待相框刷新超时，请稍后重试"
        DeviceJobState.Busy -> "相框正忙，请稍后再试"
        null -> null
    }

    private fun rejectionText(reason: DeviceRejection): String = when (reason) {
        DeviceRejection.Offline -> "相框未连接"
        DeviceRejection.Sleeping -> "相框正在休眠"
        DeviceRejection.DisplayBusy -> "相框正在刷新"
        DeviceRejection.FeatureNotActive -> "请先切换到 AI 相册模式"
        DeviceRejection.StorageUnavailable -> "设备或手机存储暂时不可用"
        DeviceRejection.StorageNoSpace -> "存储空间不足"
        DeviceRejection.MediaProtected -> "当前正在显示的图片不能删除"
        DeviceRejection.RevisionConflict -> "图片信息已更新，请刷新后重试"
        DeviceRejection.TimedOut -> "操作超时"
        else -> "请刷新后重试"
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
