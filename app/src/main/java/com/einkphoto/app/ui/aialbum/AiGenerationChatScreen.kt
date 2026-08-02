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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.einkphoto.app.feature.aialbum.AiGenerationPhase
import com.einkphoto.app.feature.aialbum.AiGenerationPreview
import com.einkphoto.app.feature.aialbum.AiGenerationHistoryItem
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
    onDiscardHistory: (String) -> Unit = {},
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var expandedPreview by rememberSaveable { mutableStateOf(false) }
    var expandedHistoryPreview by rememberSaveable { mutableStateOf<AiGenerationPreview?>(null) }
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    val inputEnabled = state.phase !in setOf(
        AiGenerationPhase.CreatingPreview,
        AiGenerationPhase.GeneratingPreview,
        AiGenerationPhase.Saving,
    )

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { GenerationIntro() }
            state.prompt?.let { prompt ->
                item { PromptBubble(prompt) }
            }
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
            if (state.history.isNotEmpty()) {
                item {
                    Text("创作记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                items(state.history, key = { it.id }) { item ->
                    GenerationHistoryCard(
                        item = item,
                        onExpandPreview = { expandedHistoryPreview = it },
                        onContinue = { onContinueHistory(item.id) },
                        onDiscard = { onDiscardHistory(item.id) },
                        onOpenAiImages = onOpenAiImages,
                    )
                }
            }
        }
        GenerationInputBar(
            draft = draft,
            onDraftChange = { draft = it.take(500) },
            enabled = inputEnabled,
            // Once there is an existing preview or any recovered history, keep the composer
            // collapsed so the conversation list remains the primary scrollable area.
            compact = (state.preview != null || state.history.isNotEmpty()) && !composerExpanded,
            onExpand = { composerExpanded = true },
            onGenerate = {
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
    onExpandPreview: (AiGenerationPreview) -> Unit,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    onOpenAiImages: () -> Unit,
) {
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
                            when (item.saveStatus) {
                                AiGenerationSaveStatus.Saved -> Icons.Outlined.CheckCircle
                                AiGenerationSaveStatus.Failed -> Icons.Outlined.Image
                                AiGenerationSaveStatus.Cancelled -> Icons.Outlined.Image
                                else -> Icons.Outlined.AutoAwesome
                            },
                            contentDescription = null,
                            tint = if (item.saveStatus == AiGenerationSaveStatus.Saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(historyStatusLabel(item.saveStatus), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    item.preview?.let { preview ->
                        Card(
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).pressFeedbackClickable(role = Role.Button, onClick = { onExpandPreview(preview) }),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            GenerationBitmap(preview, Modifier.fillMaxSize(), "历史生成预览，点击放大")
                        }
                    }
                    historyDetail(item.saveStatus)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (item.saveStatus) {
                        AiGenerationSaveStatus.Generating,
                        AiGenerationSaveStatus.Saving,
                        AiGenerationSaveStatus.PreviewReady,
                        -> OutlinedButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (item.saveStatus == AiGenerationSaveStatus.PreviewReady) "继续保存" else "继续查询")
                        }
                        AiGenerationSaveStatus.Saved -> OutlinedButton(onClick = onOpenAiImages, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("前往 AI 图片")
                        }
                        else -> Unit
                    }
                    if (item.saveStatus != AiGenerationSaveStatus.Cancelled) {
                        TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = 48.dp)) { Text("丢弃此记录") }
                    }
                }
            }
        }
    }
}

private fun historyStatusLabel(status: AiGenerationSaveStatus): String = when (status) {
    AiGenerationSaveStatus.Generating -> "生成中"
    AiGenerationSaveStatus.PreviewReady -> "预览已生成"
    AiGenerationSaveStatus.Saving -> "保存中"
    AiGenerationSaveStatus.Saved -> "已保存到 AI 相册"
    AiGenerationSaveStatus.Failed -> "生成失败"
    AiGenerationSaveStatus.Cancelled -> "已取消"
}

private fun historyDetail(status: AiGenerationSaveStatus): String? = when (status) {
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
