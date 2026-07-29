package com.einkphoto.app.feature.localalbum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJobId
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceRejection
import com.einkphoto.app.core.device.DeviceSession
import com.einkphoto.app.feature.localalbum.data.DisplayRepository
import com.einkphoto.app.feature.localalbum.data.DemoLocalAlbumController
import com.einkphoto.app.feature.localalbum.data.MediaRepository
import com.einkphoto.app.feature.localalbum.data.PlaybackRepository
import com.einkphoto.app.feature.localalbum.data.UploadRepository
import com.einkphoto.app.feature.localalbum.data.UploadMode
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.AdaptationSettings
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.CurrentDisplay
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import com.einkphoto.app.feature.localalbum.model.LocalAlbumUiState
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalAlbumViewModel(
    private val session: DeviceSession,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val displayRepository: DisplayRepository,
    private val uploadRepository: UploadRepository? = null,
    val demoController: DemoLocalAlbumController? = null,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val phoneSources = MutableStateFlow<List<PhoneSource>>(emptyList())
    private val selectedPhoneSourceId = MutableStateFlow<String?>(null)
    private val adaptationSettings = MutableStateFlow<Map<String, AdaptationSettings>>(emptyMap())
    private val conversionDrafts = MutableStateFlow<Map<String, ConversionDraft>>(emptyMap())
    private val batchSaveState = MutableStateFlow(BatchSaveState())
    private val mockUploadJobs = mutableMapOf<String, DeviceJobId>()
    private val mockUploadSteps = mutableMapOf<String, Int>()

    private data class AlbumData(
        val media: List<MediaItem>,
        val display: CurrentDisplay,
        val playback: PlaybackSettings,
        val job: com.einkphoto.app.core.device.DeviceJob?,
    )

    private data class ImportData(
        val sources: List<PhoneSource>,
        val selectedSourceId: String?,
        val adaptations: Map<String, AdaptationSettings>,
        val drafts: Map<String, ConversionDraft>,
        val batchSave: BatchSaveState,
    )

    private data class BatchSaveState(
        val total: Int = 0,
        val completed: Int = 0,
        val active: Boolean = false,
    )

    private val albumData = combine(
        mediaRepository.media,
        displayRepository.currentDisplay,
        playbackRepository.settings,
        displayRepository.activeJob,
    ) { media, display, playback, job -> AlbumData(media, display, playback, job) }

    private val interaction = combine(refreshing, message) { isRefreshing, userMessage ->
        isRefreshing to userMessage
    }

    private val importData = combine(phoneSources, selectedPhoneSourceId, adaptationSettings, conversionDrafts, batchSaveState) {
            sources, selectedSourceId, adaptations, drafts, batchSave ->
        ImportData(sources, selectedSourceId, adaptations, drafts, batchSave)
    }

    val uiState: StateFlow<LocalAlbumUiState> = combine(
        session.snapshot,
        albumData,
        interaction,
        importData,
    ) { device, album, interactionState, imported ->
        LocalAlbumUiState(
            device = device,
            media = album.media,
            currentDisplay = album.display,
            playback = album.playback,
            displayJob = album.job,
            phoneSources = imported.sources,
            selectedPhoneSourceId = imported.selectedSourceId,
            adaptationSettings = imported.adaptations,
            conversionDrafts = imported.drafts,
            batchSaveTotal = imported.batchSave.total,
            batchSaveCompleted = imported.batchSave.completed,
            batchSaveActive = imported.batchSave.active,
            isRefreshing = interactionState.first,
            userMessage = interactionState.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalAlbumUiState(
            device = session.snapshot.value,
            media = mediaRepository.media.value,
            currentDisplay = displayRepository.currentDisplay.value,
            playback = playbackRepository.settings.value,
            displayJob = displayRepository.activeJob.value,
            phoneSources = phoneSources.value,
            selectedPhoneSourceId = selectedPhoneSourceId.value,
            adaptationSettings = adaptationSettings.value,
            conversionDrafts = conversionDrafts.value,
            batchSaveTotal = batchSaveState.value.total,
            batchSaveCompleted = batchSaveState.value.completed,
            batchSaveActive = batchSaveState.value.active,
        ),
    )

    /** Opening this ViewModel is observational and cannot switch the device feature. */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val sessionResult = session.refreshSnapshot()
            val mediaResult = mediaRepository.refresh()
            message.value = when {
                sessionResult is DeviceCommandResult.Rejected -> "设备状态读取失败：${sessionResult.reason}"
                mediaResult is DeviceCommandResult.Rejected -> "设备图片读取失败：${mediaResult.reason}"
                else -> null
            }
            refreshing.value = false
        }
    }

    fun switchToLocalAlbum() {
        viewModelScope.launch {
            message.value = when (val result = session.requestFeatureSwitch(DeviceFeature.LocalAlbum)) {
                is DeviceCommandResult.Accepted -> "已提交切换到本地相册"
                is DeviceCommandResult.Rejected -> "无法切换：${result.reason}"
            }
        }
    }

    fun display(
        mediaId: MediaId,
        afterDisplay: AfterDisplay = AfterDisplay.Continue,
        onResult: (DeviceCommandResult<DeviceJobId>) -> Unit = {},
    ) {
        if (actionsLocked()) {
            val result = DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
            message.value = "无法显示：${result.reason}"
            onResult(result)
            return
        }
        viewModelScope.launch {
            val result = displayRepository.requestDisplay(mediaId, afterDisplay)
            message.value = when (result) {
                is DeviceCommandResult.Accepted -> "显示任务已提交，等待电子纸实际刷新"
                is DeviceCommandResult.Rejected -> "无法显示：${result.reason}"
            }
            onResult(result)
        }
    }

    fun displayPrevious(
        afterDisplay: AfterDisplay = AfterDisplay.Continue,
        onResult: (DeviceCommandResult<DeviceJobId>) -> Unit = {},
    ) = displayRelative(offset = -1, afterDisplay = afterDisplay, onResult = onResult)

    fun displayNext(
        afterDisplay: AfterDisplay = AfterDisplay.Continue,
        onResult: (DeviceCommandResult<DeviceJobId>) -> Unit = {},
    ) = displayRelative(offset = 1, afterDisplay = afterDisplay, onResult = onResult)

    fun delete(
        mediaId: MediaId,
        onResult: (DeviceCommandResult<Unit>) -> Unit = {},
    ) {
        if (actionsLocked()) {
            val result = DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
            message.value = "无法删除：${result.reason}"
            onResult(result)
            return
        }
        viewModelScope.launch {
            val result = mediaRepository.delete(mediaId)
            message.value = when (result) {
                is DeviceCommandResult.Accepted -> "图片已从设备删除"
                is DeviceCommandResult.Rejected -> "无法删除：${deleteFailureText(result.reason)}"
            }
            onResult(result)
        }
    }

    fun savePlayback(settings: PlaybackSettings) {
        viewModelScope.launch {
            message.value = when (val result = playbackRepository.save(settings)) {
                is DeviceCommandResult.Accepted -> "轮播设置已保存到设备"
                is DeviceCommandResult.Rejected -> "保存失败：${result.reason}"
            }
        }
    }

    /** Called only while the local-album overview is visible to follow device auto-play changes. */
    fun refreshPlaybackStatus() {
        viewModelScope.launch { playbackRepository.refreshPlayback() }
    }

    fun clearMessage() {
        message.value = null
    }

    /** Replaces the session-scoped selection returned by Android's system Photo Picker. */
    fun setPhoneSources(sources: List<PhoneSource>) {
        phoneSources.value = sources.distinctBy { it.contentUri }
        selectedPhoneSourceId.value = phoneSources.value.firstOrNull()?.sourceId
        adaptationSettings.value = adaptationSettings.value.filterKeys { id -> phoneSources.value.any { it.sourceId == id } }
        conversionDrafts.value = conversionDrafts.value.filterKeys { id -> phoneSources.value.any { it.sourceId == id } }
        message.value = if (sources.isEmpty()) "未选择照片" else "已选择 ${phoneSources.value.size} 张手机照片，尚未上传"
    }

    fun removePhoneSource(sourceId: String) {
        phoneSources.value = phoneSources.value.filterNot { it.sourceId == sourceId }
        adaptationSettings.value = adaptationSettings.value - sourceId
        conversionDrafts.value = conversionDrafts.value - sourceId
        if (selectedPhoneSourceId.value == sourceId) {
            selectedPhoneSourceId.value = phoneSources.value.firstOrNull()?.sourceId
        }
        message.value = "已移除照片，当前选择 ${phoneSources.value.size} 张"
    }

    fun selectPhoneSource(sourceId: String) {
        if (phoneSources.value.any { it.sourceId == sourceId }) selectedPhoneSourceId.value = sourceId
    }

    fun updateAdaptation(sourceId: String, fitMode: FitMode? = null, quarterTurnsClockwise: Int? = null) {
        if (phoneSources.value.none { it.sourceId == sourceId }) return
        val current = adaptationSettings.value[sourceId] ?: AdaptationSettings()
        adaptationSettings.value = adaptationSettings.value + (sourceId to current.copy(
            fitMode = fitMode ?: current.fitMode,
            quarterTurnsClockwise = quarterTurnsClockwise ?: current.quarterTurnsClockwise,
            isConfigured = false,
        ))
        conversionDrafts.value[sourceId]?.let { existing ->
            conversionDrafts.value = conversionDrafts.value + (sourceId to existing.copy(stage = ConversionStage.Stale))
        }
    }

    fun markAdaptationConfigured(sourceId: String) {
        val current = adaptationSettings.value[sourceId] ?: AdaptationSettings()
        adaptationSettings.value = adaptationSettings.value + (sourceId to current.copy(isConfigured = true))
        message.value = "已保存本地适配参数，尚未生成六色图片或上传"
    }

    fun queueConfiguredConversions(sourceIds: List<String>? = null): List<String> {
        val requested = sourceIds?.toSet()
        // Phone-side conversion must remain available while the device is temporarily
        // offline. The physical panel contract is fixed at 800x480/192000 bytes;
        // real upload still requires an online device and its later validation.
        val profile = session.snapshot.value.capabilities?.displayProfile
            ?: com.einkphoto.app.core.device.DisplayProfile(
                widthPx = 800,
                heightPx = 480,
                frameBytes = 192_000,
                palette = listOf("black", "white", "red", "green", "blue", "yellow"),
                orientationKey = "unverified",
            )
        val eligible = phoneSources.value.filter { source ->
            (requested == null || source.sourceId in requested) &&
                adaptationSettings.value[source.sourceId]?.isConfigured == true
        }
        if (eligible.isEmpty()) {
            message.value = "没有已完成适配的照片"
            return emptyList()
        }
        val updates = eligible.associate { source ->
            val settings = adaptationSettings.value.getValue(source.sourceId)
            source.sourceId to ConversionDraft(
                draftId = "draft-${source.sourceId}",
                source = source,
                profile = profile,
                fitMode = settings.fitMode,
                quarterTurnsClockwise = settings.quarterTurnsClockwise,
                stage = ConversionStage.Queued,
            )
        }
        conversionDrafts.value = conversionDrafts.value + updates
        message.value = "已创建 ${eligible.size} 个手机本地转换任务，尚未上传"
        return eligible.map { it.sourceId }
    }

    fun updateConversionStage(sourceId: String, stage: ConversionStage) {
        val current = conversionDrafts.value[sourceId] ?: return
        conversionDrafts.value = conversionDrafts.value + (sourceId to current.copy(stage = stage, errorMessage = null))
    }

    fun completeConversion(
        sourceId: String,
        previewUri: String,
        candidateBinUri: String,
        frameBytes: Int,
        algorithmVersion: String,
    ) {
        val current = conversionDrafts.value[sourceId] ?: return
        conversionDrafts.value = conversionDrafts.value + (sourceId to current.copy(
            stage = ConversionStage.Ready,
            previewUri = previewUri,
            candidateBinUri = candidateBinUri,
            generatedFrameBytes = frameBytes,
            algorithmVersion = algorithmVersion,
            localValidationPassed = true,
            errorMessage = null,
        ))
    }

    fun failConversion(sourceId: String, userReadableReason: String) {
        val current = conversionDrafts.value[sourceId] ?: return
        conversionDrafts.value = conversionDrafts.value + (sourceId to current.copy(
            stage = ConversionStage.Failed,
            localValidationPassed = false,
            errorMessage = userReadableReason,
        ))
    }

    fun cancelQueuedConversions() {
        conversionDrafts.value = conversionDrafts.value.mapValues { (_, draft) ->
            if (draft.stage == ConversionStage.Queued) draft.copy(stage = ConversionStage.Cancelled) else draft
        }
        message.value = "已取消尚未开始的本地转换任务，已完成草稿会保留"
    }

    fun submitDraftToDevice(sourceId: String) {
        val draft = conversionDrafts.value[sourceId] ?: return
        if (draft.stage !in setOf(ConversionStage.Ready, ConversionStage.WaitingForDevice)) return
        viewModelScope.launch {
            // A conversion draft may outlive AP/STA reconnection. Always re-read the three-step
            // device handshake here instead of relying on the snapshot from page entry.
            val device = when (val refreshed = session.refreshSnapshot()) {
                is DeviceCommandResult.Accepted -> refreshed.value
                is DeviceCommandResult.Rejected -> {
                    conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.WaitingForDevice))
                    message.value = "暂时无法连接墨水屏；草稿已保留，请确认网络后重试"
                    return@launch
                }
            }
            if (device.connection != com.einkphoto.app.core.device.DeviceConnectionState.Online) {
                conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.WaitingForDevice))
                message.value = "暂时无法连接墨水屏；草稿已保留，请确认网络后重试"
                return@launch
            }
            val repository = uploadRepository ?: run {
                message.value = "设备已连接，但局域网上传实现尚未配置"
                return@launch
            }
            conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.Uploading))
            when (val result = repository.submit(draft, UploadMode.SourceAndBin, "upload-${draft.draftId}")) {
                is DeviceCommandResult.Accepted -> {
                    conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.Admitted))
                    if (device.isDemo) {
                        mockUploadJobs[sourceId] = result.value
                        mockUploadSteps[sourceId] = 0
                        message.value = "Mock 上传已提交；请依次演示传输、校验、入库"
                    } else {
                        // The upload repository only returns Accepted after the device job is
                        // terminal-success and includes a media_id. Refresh is read-only and
                        // does not request an e-paper display operation.
                        message.value = when (mediaRepository.refresh()) {
                            is DeviceCommandResult.Accepted -> "照片已保存到相框图库"
                            is DeviceCommandResult.Rejected -> "已保存到相框，但图库刷新失败；稍后重新进入图库即可查看"
                        }
                    }
                }
                is DeviceCommandResult.Rejected -> {
                    conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.Ready))
                    message.value = uploadFailureText(result.reason)
                }
            }
        }
    }

    /**
     * Saves the currently prepared drafts in phone-list order. UploadRepository waits for each
     * device admission job before returning, so this loop never overlaps TF/network work.
     */
    fun saveAllReadyDrafts() {
        if (batchSaveState.value.active) return
        val sourceIds = phoneSources.value.mapNotNull { source ->
            source.sourceId.takeIf { conversionDrafts.value[it]?.stage in setOf(ConversionStage.Ready, ConversionStage.WaitingForDevice) }
        }
        if (sourceIds.isEmpty()) {
            message.value = "没有可保存的转换结果"
            return
        }
        viewModelScope.launch {
            val device = when (val refreshed = session.refreshSnapshot()) {
                is DeviceCommandResult.Accepted -> refreshed.value
                is DeviceCommandResult.Rejected -> {
                    message.value = "暂时无法连接墨水屏；待保存图片已保留"
                    return@launch
                }
            }
            if (device.connection != com.einkphoto.app.core.device.DeviceConnectionState.Online) {
                message.value = "暂时无法连接墨水屏；待保存图片已保留"
                return@launch
            }
            val repository = uploadRepository ?: run {
                message.value = "设备已连接，但局域网上传尚未配置"
                return@launch
            }
            batchSaveState.value = BatchSaveState(total = sourceIds.size, active = true)
            var successCount = 0
            sourceIds.forEachIndexed { index, sourceId ->
                val draft = conversionDrafts.value[sourceId] ?: return@forEachIndexed
                if (draft.stage !in setOf(ConversionStage.Ready, ConversionStage.WaitingForDevice)) return@forEachIndexed
                conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.Uploading, errorMessage = null))
                val uploadResult = repository.submit(draft, UploadMode.SourceAndBin, "batch-${draft.draftId}")
                when (uploadResult) {
                    is DeviceCommandResult.Accepted -> {
                        // Demo uploads use the same explicit state machine as the single-item
                        // flow. Advance it deterministically here so one-click batch saving does
                        // not ask the user to manually complete each mock transaction.
                        val completed = if (device.isDemo) {
                            val jobId = uploadResult.value
                            repeat(3) { demoController?.advanceUploadJob(jobId) }
                            demoController?.finishUploadJob(jobId, success = true)
                        } else {
                            null
                        }
                        if (!device.isDemo || completed?.state == DeviceJobState.Success) {
                            conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(stage = ConversionStage.Admitted, errorMessage = null))
                            successCount += 1
                        } else {
                            conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(
                                stage = ConversionStage.Ready,
                                errorMessage = "演示入库未完成，请重试",
                            ))
                        }
                    }
                    is DeviceCommandResult.Rejected -> {
                        conversionDrafts.value = conversionDrafts.value + (sourceId to draft.copy(
                            stage = ConversionStage.Ready,
                            errorMessage = uploadFailureText(uploadResult.reason),
                        ))
                    }
                }
                batchSaveState.value = BatchSaveState(total = sourceIds.size, completed = index + 1, active = true)
                // Give the ESP HTTP service a short slot-release window before the next
                // multipart connection. Uploads remain strictly serial.
                if (index + 1 < sourceIds.size) kotlinx.coroutines.delay(1_500L)
            }
            // Admission never requests an e-paper refresh. This is only an authoritative gallery
            // read, performed after the serial batch has reached its terminal per-item states.
            val galleryUpdated = if (successCount > 0) mediaRepository.refresh() is DeviceCommandResult.Accepted else true
            batchSaveState.value = BatchSaveState(total = sourceIds.size, completed = sourceIds.size, active = false)
            message.value = when {
                successCount == sourceIds.size && galleryUpdated && device.isDemo -> "演示模式已保存 ${successCount} 张到模拟图库"
                successCount == sourceIds.size && galleryUpdated -> "${successCount} 张照片已保存到相框图库"
                successCount > 0 && galleryUpdated -> "已保存 ${successCount}/${sourceIds.size} 张；失败项可单独重试"
                successCount > 0 -> "已保存 ${successCount} 张，但图库刷新失败；稍后可重新进入图库查看"
                else -> "保存未完成；失败项已保留，可逐张重试"
            }
        }
    }

    fun advanceMockUpload(sourceId: String) {
        val jobId = mockUploadJobs[sourceId] ?: return
        demoController?.advanceUploadJob(jobId) ?: return
        val current = conversionDrafts.value[sourceId] ?: return
        val step = (mockUploadSteps[sourceId] ?: 0) + 1
        mockUploadSteps[sourceId] = step
        val stage = if (step == 1) ConversionStage.DeviceValidating else ConversionStage.Committing
        conversionDrafts.value = conversionDrafts.value + (sourceId to current.copy(stage = stage))
    }

    fun finishMockUpload(sourceId: String, success: Boolean) {
        val jobId = mockUploadJobs[sourceId] ?: return
        val current = conversionDrafts.value[sourceId] ?: return
        val completed = demoController?.finishUploadJob(jobId, success) ?: return
        conversionDrafts.value = conversionDrafts.value + (sourceId to current.copy(
            stage = if (success && completed.state == DeviceJobState.Success) ConversionStage.Admitted else ConversionStage.Ready,
            errorMessage = if (success) null else "Mock 设备校验失败；未入库，可重试",
        ))
        mockUploadJobs.remove(sourceId)
        mockUploadSteps.remove(sourceId)
        message.value = if (success) "Mock 入库完成：未写入真实 TF，未显示到墨水屏" else "Mock 上传失败：设备媒体库未新增图片"
    }

    private fun displayRelative(
        offset: Int,
        afterDisplay: AfterDisplay,
        onResult: (DeviceCommandResult<DeviceJobId>) -> Unit,
    ) {
        if (actionsLocked()) {
            val result = DeviceCommandResult.Rejected(DeviceRejection.DisplayBusy)
            message.value = "无法显示：${result.reason}"
            onResult(result)
            return
        }
        val items = mediaRepository.media.value.filter { it.availability == com.einkphoto.app.feature.localalbum.model.MediaAvailability.Ready }
        if (items.isEmpty()) {
            val result = DeviceCommandResult.Rejected(DeviceRejection.Unsupported)
            message.value = "设备中没有可显示的图片"
            onResult(result)
            return
        }
        val currentIndex = items.indexOfFirst { it.id == displayRepository.currentDisplay.value.mediaId }
        val targetIndex = if (currentIndex < 0) {
            if (offset > 0) 0 else items.lastIndex
        } else {
            Math.floorMod(currentIndex + offset, items.size)
        }
        display(items[targetIndex].id, afterDisplay, onResult)
    }

    private fun actionsLocked(): Boolean {
        val jobState = displayRepository.activeJob.value?.state
        return session.snapshot.value.displayBusy ||
            displayRepository.currentDisplay.value.result == com.einkphoto.app.feature.localalbum.model.DisplayResult.Refreshing ||
            jobState == DeviceJobState.Queued ||
            jobState == DeviceJobState.Running
    }

    private fun deleteFailureText(reason: DeviceRejection): String = when (reason) {
        DeviceRejection.MediaProtected -> "当前正在显示的图片不能删除"
        DeviceRejection.DisplayBusy -> "相框正在刷新，请稍后再试"
        DeviceRejection.Offline -> "暂时无法连接相框"
        DeviceRejection.StorageUnavailable -> "设备存储暂时不可用"
        DeviceRejection.Unsupported -> "设备暂不支持此删除操作"
        else -> "设备拒绝了本次删除，请刷新图库后重试"
    }

    private fun uploadFailureText(reason: DeviceRejection): String = when (reason) {
        DeviceRejection.SourceTooLarge -> "原图超过 5 MB，无法保存到相框；请压缩或换一张照片"
        DeviceRejection.Offline -> "无法连接相框，请检查设备地址和局域网后重试"
        DeviceRejection.StorageNoSpace -> "TF 卡空间不足，请先删除不需要的照片"
        DeviceRejection.StorageUnavailable -> "TF 卡暂时不可用，请稍后重试"
        else -> "保存失败：${reason.name}"
    }
}
