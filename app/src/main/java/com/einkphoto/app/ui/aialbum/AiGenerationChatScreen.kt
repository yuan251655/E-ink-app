package com.einkphoto.app.ui.aialbum

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.einkphoto.app.feature.aialbum.AiGenerationPhase
import com.einkphoto.app.feature.aialbum.AiGenerationPreview
import com.einkphoto.app.feature.aialbum.AiGenerationHistoryItem
import com.einkphoto.app.feature.aialbum.AiGenerationLastTaskDiagnostic
import com.einkphoto.app.feature.aialbum.AiGenerationSaveStatus
import com.einkphoto.app.feature.aialbum.AiGenerationUiState
import com.einkphoto.app.ui.components.pressFeedbackClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI image creation is intentionally a two-step conversation: generate a temporary preview,
 * then let the user explicitly convert and save it to the device's AI gallery.
 */
@Composable
internal fun AiGenerationChatScreen(
    state: AiGenerationUiState,
    onGenerate: (String) -> Unit,
    onConfirmSave: () -> Unit,
    onOpenAiImages: () -> Unit,
    onContinueHistory: (String) -> Unit = {},
    onRetrySubmission: (String) -> Unit = {},
    onCancelWaitingSubmission: (String) -> Unit = {},
    onDiscardHistory: (String) -> Unit = {},
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var expandedPreview by rememberSaveable { mutableStateOf(false) }
    var expandedHistoryPreview by rememberSaveable { mutableStateOf<AiGenerationPreview?>(null) }
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTemplateCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val promptTemplates by produceState<List<AiPromptTemplate>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.IO) { AiPromptTemplateCatalog.load(context) }
    }
    val selectedTemplateCategory = selectedTemplateCategoryName?.let { name ->
        AiPromptTemplateCategory.entries.firstOrNull { it.name == name }
    }
    val inputEnabled = state.phase !in setOf(
        AiGenerationPhase.CreatingPreview,
        AiGenerationPhase.WaitingToSubmit,
        AiGenerationPhase.GeneratingPreview,
        AiGenerationPhase.Saving,
    )

    LaunchedEffect(state.historyId, state.phase, state.history.size) {
        if (state.historyId != null && state.history.isNotEmpty()) {
            listState.animateScrollToItem(state.history.size - 1)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GenerationTopBar(onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.history.isEmpty()) item { GenerationIntro() }
            /* Current task used to be rendered here and once again in history.
             * Keep it disabled: every task now has exactly one durable chat card.
            when (state.phase) {
                AiGenerationPhase.CreatingPreview,
                AiGenerationPhase.GeneratingPreview,
                -> item { GenerationProgress(state.message ?: "正在生成预览…") }

                AiGenerationPhase.PreviewReady -> {
                    state.preview?.let { preview ->
                        item { PreviewCard(preview, onExpand = { expandedPreview = true }) }
                        item {
                            Button(
                                onClick = onConfirmSave,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("转换并保存到 AI 相册")
                            }
                        }
                        item {
                            Text(
                                "确认后才会在相框中转换为六色画面并写入 TF 卡；本步骤不会自动刷新屏幕。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } ?: item { GenerationNotice("预览正在准备中，请稍后重试。") }
                }

                AiGenerationPhase.Saving -> item { GenerationProgress(state.message ?: "正在保存…") }
                AiGenerationPhase.Saved -> item {
                    SavedCard(
                        message = state.message ?: "已保存到 AI 相册",
                        onOpenAiImages = onOpenAiImages,
                    )
                }

                AiGenerationPhase.Failed -> item { GenerationNotice(state.message ?: "生成失败，请修改描述后重试") }
                AiGenerationPhase.Idle -> Unit
            }
            */
            if (!state.active && state.phase == AiGenerationPhase.Failed) {
                state.lastTaskDiagnostic?.takeIf { it.available }?.let { diagnostic ->
                    item { LastTaskDiagnosticCard(diagnostic) }
                }
            }
            if (state.history.isNotEmpty()) {
                // Persisted history is newest-first for efficient updates, but the chat surface
                // is chronological: the newest entry stays near the composer and earlier work
                // is reached by scrolling upward.
                items(state.history.asReversed(), key = { it.id }) { item ->
                    GenerationHistoryCard(
                        item = item,
                        activeState = state.takeIf { it.historyId == item.id },
                        onConfirmSave = onConfirmSave,
                        onExpandPreview = { expandedHistoryPreview = it },
                        onContinue = { onContinueHistory(item.id) },
                        onRetrySubmission = { onRetrySubmission(item.id) },
                        onCancelWaiting = { onCancelWaitingSubmission(item.id) },
                        onDiscard = { onDiscardHistory(item.id) },
                        onOpenAiImages = onOpenAiImages,
                    )
                }
            }
        }
        GenerationInputBar(
            draft = draft,
            onDraftChange = { draft = it.take(500) },
            promptTemplates = promptTemplates,
            selectedTemplateCategory = selectedTemplateCategory,
            onChooseTemplate = { category, useAnother ->
                val template = AiPromptTemplateCatalog.choose(
                    templates = promptTemplates,
                    category = category,
                    previousId = if (useAnother) selectedTemplateId else null,
                )
                if (template != null) {
                    selectedTemplateCategoryName = category.name
                    selectedTemplateId = template.id
                    draft = template.text.take(500)
                    composerExpanded = true
                }
            },
            enabled = inputEnabled,
            // Once there is an existing preview or any recovered history, keep the composer
            // collapsed so the conversation list remains the primary scrollable area.
            compact = (state.preview != null || state.history.isNotEmpty()) && !composerExpanded,
            onExpand = { composerExpanded = true },
            onGenerate = {
                focusManager.clearFocus(force = true)
                onGenerate(draft)
                draft = ""
                composerExpanded = false
            },
        )
    }

    if (expandedPreview) {
        state.preview?.let { preview ->
            PreviewDialog(preview, onDismiss = { expandedPreview = false })
        }
    }
    expandedHistoryPreview?.let { preview ->
        PreviewDialog(preview, onDismiss = { expandedHistoryPreview = null })
    }
}

@Composable
private fun LastTaskDiagnosticCard(diagnostic: AiGenerationLastTaskDiagnostic) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("最近任务诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("用于确认最近一次任务的设备状态；不包含 API Key 或完整图片描述。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DiagnosticLine("完成时设备已运行", diagnosticUptimeLabel(diagnostic.finishedAtUptimeMillis))
            DiagnosticLine("模型", diagnostic.profileName ?: diagnostic.profileId ?: "未提供")
            DiagnosticLine("状态", diagnosticStateLabel(diagnostic.state, diagnostic.phase))
            DiagnosticLine("错误说明", diagnosticErrorLabel(diagnostic.errorCode))
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 20.dp))
    }
}

private fun diagnosticUptimeLabel(uptimeMillis: Long): String {
    if (uptimeMillis <= 0L) return "设备未提供"
    val totalSeconds = uptimeMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d小时%02d分".format(hours, minutes) else "%d分%02d秒".format(minutes, seconds)
}

private fun diagnosticStateLabel(state: String, phase: String): String = when {
    state in setOf("success", "completed", "2") -> "已完成"
    state in setOf("failed", "error", "3") -> "未完成"
    phase == "converting" -> "转换中断"
    phase == "downloading" -> "下载中断"
    else -> "已结束"
}

private fun diagnosticErrorLabel(code: String?): String = when (code) {
    "ai_http_400" -> "请求参数不兼容（ai_http_400）"
    "ai_http_401" -> "API Key 无效或已失效（ai_http_401）"
    "ai_http_403" -> "当前模型没有访问权限（ai_http_403）"
    "ai_http_404" -> "未找到模型或服务地址（ai_http_404）"
    "ai_http_429" -> "服务暂时限流（ai_http_429）"
    "ai_network_failed" -> "相框无法访问模型服务（ai_network_failed）"
    "ai_tls_failed" -> "安全连接异常（ai_tls_failed）"
    "ai_request_timeout" -> "模型服务响应超时（ai_request_timeout）"
    "ai_invalid_provider_response" -> "模型服务返回内容无法识别（ai_invalid_provider_response）"
    "ai_download_failed" -> "生成成功但图片下载失败（ai_download_failed）"
    "ai_download_tls_failed" -> "图片服务器安全连接失败（ai_download_tls_failed）"
    "ai_download_timeout" -> "下载生成图片超时（ai_download_timeout）"
    "ai_download_network_failed" -> "相框无法连接图片服务器（ai_download_network_failed）"
    "ai_download_http_4xx" -> "图片临时链接已失效或无权限（ai_download_http_4xx）"
    "ai_download_http_5xx" -> "图片服务器暂时异常（ai_download_http_5xx）"
    "ai_download_redirect_failed" -> "图片下载跳转失败（ai_download_redirect_failed）"
    "ai_download_storage_failed" -> "写入临时图片失败（ai_download_storage_failed）"
    "ai_source_too_large" -> "生成图片超过相框可处理大小（ai_source_too_large）"
    "ai_conversion_memory" -> "相框内存不足，无法转换六色图（ai_conversion_memory）"
    "ai_conversion_failed" -> "生成图片无法转换为六色电子纸画面（ai_conversion_failed）"
    "ai_preview_commit_failed" -> "临时预览保存失败（ai_preview_commit_failed）"
    "ai_commit_failed" -> "图片写入 AI 相册失败（ai_commit_failed）"
    "storage_no_space" -> "TF 卡空间不足（storage_no_space）"
    null -> "设备未提供安全错误码"
    else -> "任务未完成（$code）"
}

@Composable
private fun GenerationTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回 AI 相册")
        }
        Column(Modifier.padding(start = 6.dp)) {
            Text("创作图片", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("先看预览，再确认保存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GenerationIntro() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("描述你想看到的画面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("生成结果会先作为临时预览展示，不会立即写入 TF 卡或刷新相框。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun PromptBubble(prompt: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("我的图片描述", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                Text(prompt, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun GenerationProgress(message: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("正在处理", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GenerationNotice(message: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewCard(preview: AiGenerationPreview, onExpand: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("生成预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .pressFeedbackClickable(role = Role.Button, onClick = onExpand),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                GenerationBitmap(preview, Modifier.fillMaxSize(), "生成预览，点击放大")
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("点击放大", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Text("临时预览仅保存在手机应用私有存储中。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SavedCard(message: String, onOpenAiImages: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Text("已保存", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onOpenAiImages, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("前往 AI 图片")
            }
        }
    }
}

@Composable
private fun GenerationHistoryCard(
    item: AiGenerationHistoryItem,
    activeState: AiGenerationUiState?,
    onConfirmSave: () -> Unit,
    onExpandPreview: (AiGenerationPreview) -> Unit,
    onContinue: () -> Unit,
    onRetrySubmission: () -> Unit,
    onCancelWaiting: () -> Unit,
    onDiscard: () -> Unit,
    onOpenAiImages: () -> Unit,
) {
    val activePhase = activeState?.phase
    val livePreview = activeState?.preview ?: item.preview
    val status = when (activePhase) {
        AiGenerationPhase.CreatingPreview -> AiGenerationSaveStatus.Submitting
        AiGenerationPhase.WaitingToSubmit -> AiGenerationSaveStatus.WaitingToSubmit
        AiGenerationPhase.GeneratingPreview -> AiGenerationSaveStatus.Generating
        AiGenerationPhase.PreviewReady -> AiGenerationSaveStatus.PreviewReady
        AiGenerationPhase.Saving -> AiGenerationSaveStatus.Saving
        AiGenerationPhase.Saved -> AiGenerationSaveStatus.Saved
        AiGenerationPhase.Failed -> AiGenerationSaveStatus.Failed
        null, AiGenerationPhase.Idle -> item.saveStatus
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(historyTime(item.createdAtEpochMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        PromptBubble(item.prompt)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            when (status) {
                                AiGenerationSaveStatus.Saved -> Icons.Outlined.CheckCircle
                                AiGenerationSaveStatus.Failed -> Icons.Outlined.Image
                                AiGenerationSaveStatus.Cancelled -> Icons.Outlined.Image
                                else -> Icons.Outlined.AutoAwesome
                            },
                            contentDescription = null,
                            tint = if (status == AiGenerationSaveStatus.Saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(historyStatusLabel(status), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    livePreview?.let { preview ->
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).pressFeedbackClickable(role = Role.Button, onClick = { onExpandPreview(preview) }),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            GenerationBitmap(preview, Modifier.fillMaxSize(), "历史生成预览，点击放大")
                        }
                    }
                    (activeState?.message ?: item.failureReason ?: historyDetail(status))?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (status) {
                        AiGenerationSaveStatus.Submitting,
                        AiGenerationSaveStatus.Generating,
                        AiGenerationSaveStatus.Saving,
                        -> if (activeState != null) {
                            GenerationProgress(activeState.message ?: "正在处理…")
                        } else OutlinedButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("继续查询")
                        }
                        AiGenerationSaveStatus.WaitingToSubmit -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GenerationProgress(activeState?.message ?: "正在等待上一项任务结束…")
                            TextButton(onClick = onCancelWaiting, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("取消等待")
                            }
                        }
                        AiGenerationSaveStatus.PreviewReady -> if (activeState != null) {
                            Button(onClick = onConfirmSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("转换并保存到 AI 相册")
                            }
                        } else OutlinedButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("继续保存")
                        }
                        AiGenerationSaveStatus.Saved -> OutlinedButton(onClick = onOpenAiImages, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("前往 AI 图片")
                        }
                        AiGenerationSaveStatus.Failed -> if (item.failureReason.orEmpty().startsWith("未提交")) {
                            OutlinedButton(onClick = onRetrySubmission, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("重新提交")
                            }
                        }
                        else -> Unit
                    }
                    if (status !in setOf(AiGenerationSaveStatus.Saved, AiGenerationSaveStatus.Cancelled)) {
                        TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = 48.dp)) { Text("丢弃此记录") }
                    }
                }
            }
        }
    }
}

private fun historyStatusLabel(status: AiGenerationSaveStatus): String = when (status) {
    AiGenerationSaveStatus.Submitting -> "正在提交"
    AiGenerationSaveStatus.WaitingToSubmit -> "等待提交"
    AiGenerationSaveStatus.Generating -> "生成中"
    AiGenerationSaveStatus.PreviewReady -> "预览已生成"
    AiGenerationSaveStatus.Saving -> "保存中"
    AiGenerationSaveStatus.Saved -> "已保存到 AI 相册"
    AiGenerationSaveStatus.Failed -> "生成失败"
    AiGenerationSaveStatus.Cancelled -> "已取消"
}

private fun historyDetail(status: AiGenerationSaveStatus): String? = when (status) {
    AiGenerationSaveStatus.Submitting -> "正在将本次请求发送到相框。"
    AiGenerationSaveStatus.WaitingToSubmit -> "等待上一项任务结束；本条尚未调用模型。"
    AiGenerationSaveStatus.Generating -> "任务仍在相框端处理，可继续查询进度。"
    AiGenerationSaveStatus.PreviewReady -> "临时预览保存在本机；确认保存后才会写入 TF 卡。"
    AiGenerationSaveStatus.Saving -> "正在转换并写入 AI 相册，可继续查询进度。"
    AiGenerationSaveStatus.Failed -> "这次未生成成功，可重新输入新的图片描述。"
    AiGenerationSaveStatus.Cancelled -> "已停止在手机端继续跟踪该任务。"
    AiGenerationSaveStatus.Saved -> null
}

private fun historyTime(epochMillis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

@Composable
private fun GenerationInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    promptTemplates: List<AiPromptTemplate>,
    selectedTemplateCategory: AiPromptTemplateCategory?,
    onChooseTemplate: (AiPromptTemplateCategory, Boolean) -> Unit,
    enabled: Boolean,
    compact: Boolean,
    onExpand: () -> Unit,
    onGenerate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (compact) {
            TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 56.dp)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("创作另一张图片")
            }
        } else Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("图片描述", style = MaterialTheme.typography.labelLarge)
            TemplatePromptPicker(
                templatesAvailable = promptTemplates.isNotEmpty(),
                selectedCategory = selectedTemplateCategory,
                enabled = enabled,
                onChoose = onChooseTemplate,
            )
            androidx.compose.material3.OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 132.dp),
                placeholder = { Text(if (enabled) "例如：粉色晨雾中的山谷和小河" else "请先完成当前预览或保存") },
                supportingText = { Text("最多 500 字；生成后先预览，确认后才保存") },
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = onGenerate,
                enabled = enabled && draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("生成预览")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TemplatePromptPicker(
    templatesAvailable: Boolean,
    selectedCategory: AiPromptTemplateCategory?,
    enabled: Boolean,
    onChoose: (AiPromptTemplateCategory, Boolean) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("模板生图", style = MaterialTheme.typography.labelLarge)
                    Text(
                        selectedCategory?.let { "已选择${it.title}模板，可继续编辑描述" } ?: "从模板随机填入一段图片描述",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedCategory != null) {
                    TextButton(
                        onClick = { onChoose(selectedCategory, true) },
                        enabled = enabled && templatesAvailable,
                    ) { Text("换一条") }
                } else {
                    TextButton(
                        onClick = { showPicker = true },
                        enabled = enabled && templatesAvailable,
                    ) { Text("选择") }
                }
            }
        }
    }
    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("选择模板类别", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("随机填入一条模板；填入后仍可自由编辑。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AiPromptTemplateCategory.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { category ->
                            OutlinedButton(
                                onClick = {
                                    onChoose(category, false)
                                    showPicker = false
                                },
                                enabled = templatesAvailable && enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(category.title)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationBitmap(
    preview: AiGenerationPreview,
    modifier: Modifier,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, preview.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { Uri.parse(preview.uri).path?.let(BitmapFactory::decodeFile) }.getOrNull()
        }
    }
    if (bitmap == null) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text("预览图暂不可用", color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    } else {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

@Composable
private fun PreviewDialog(preview: AiGenerationPreview, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("生成预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭") }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
                    GenerationBitmap(
                        preview = preview,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "放大的生成预览",
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
