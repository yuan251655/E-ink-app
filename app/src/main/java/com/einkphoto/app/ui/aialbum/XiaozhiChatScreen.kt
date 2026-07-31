package com.einkphoto.app.ui.aialbum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import java.nio.charset.StandardCharsets
import java.util.Base64

internal enum class ChatSender { User, Xiaozhi }
internal enum class ChatDeliveryState { Sending, Sent, Failed }

internal data class XiaozhiChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val timeLabel: String,
    val deliveryState: ChatDeliveryState = ChatDeliveryState.Sent,
    val canGenerateFromReply: Boolean = false,
)

internal fun chatSendNotice(draft: String, online: Boolean, configured: Boolean): String = when {
    draft.isBlank() -> "请先输入想和小智说的话"
    !online -> "相框未连接，消息已保留在输入框中"
    !configured -> "请先完成 AI 模型配置，当前没有发送消息"
    else -> "AI 对话服务尚未接入，当前没有发送消息"
}

internal fun chatGenerationNotice(prompt: String, online: Boolean): String? = when {
    prompt.isBlank() -> "请先描述想生成的画面"
    !online -> "相框未连接，画面描述已保留"
    else -> null
}

/** Pure layout rule kept independent from Compose state so compact behavior is easy to test. */
internal fun useCompactChatInput(widthDp: Float, heightDp: Float): Boolean =
    widthDp > heightDp || heightDp < 600f

@Composable
internal fun rememberXiaozhiMessages(): SnapshotStateList<XiaozhiChatMessage> = rememberSaveable(
    saver = listSaver(
        save = { messages -> messages.map(::encodeMessage) },
        restore = { tokens -> tokens.mapNotNull(::decodeMessage).toMutableStateList() },
    ),
) { emptyList<XiaozhiChatMessage>().toMutableStateList() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun XiaozhiChatScreen(
    messages: List<XiaozhiChatMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    listState: LazyListState,
    online: Boolean,
    configured: Boolean,
    replying: Boolean,
    replyFailed: Boolean,
    notice: String?,
    onNotice: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var generationPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun requestSend() {
        onNotice(chatSendNotice(draft, online, configured))
    }

    fun requestGeneration(prompt: String) {
        val blocked = chatGenerationNotice(prompt, online)
        if (blocked != null) onNotice(blocked) else generationPrompt = prompt
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compactInput = useCompactChatInput(maxWidth.value, maxHeight.value)
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            ChatTopBar(
                online = online,
                configured = configured,
                replying = replying,
                onBack = onBack,
            )
            if (!online) {
                ChatBanner(Icons.Outlined.CloudOff, "相框未连接，草稿和聊天记录会继续保留")
            } else if (!configured) {
                ConfigurationBanner(onOpenConfig)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) item { EmptyConversation(onSuggestion = onDraftChange) }
                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        onGenerate = { requestGeneration(message.text) },
                        onRetry = {
                            onNotice(
                                if (!online) "相框未连接，暂时无法重试"
                                else "AI 对话服务尚未接入，当前没有重新发送",
                            )
                        },
                        onCopied = { onNotice("已复制小智回复") },
                    )
                }
                if (replying) item { ReplyingBubble() }
                if (replyFailed) item { ReplyFailedCard(onRetry = { onNotice("AI 对话服务尚未接入，当前没有重新请求") }) }
            }
            ChatInputBar(
                draft = draft,
                onDraftChange = onDraftChange,
                notice = notice,
                onDismissNotice = { onNotice(null) },
                onSend = ::requestSend,
                onGenerate = { requestGeneration(draft) },
                online = online,
                compact = compactInput,
            )
        }
    }

    generationPrompt?.let { prompt ->
        ModalBottomSheet(
            onDismissRequest = { generationPrompt = null },
            sheetState = sheetState,
        ) {
            GenerationConfirmationSheet(
                prompt = prompt,
                configured = configured,
                onModify = { generationPrompt = null },
                onOpenConfig = {
                    generationPrompt = null
                    onOpenConfig()
                },
                onConfirm = {
                    generationPrompt = null
                    onNotice("图片生成服务尚未接入，本次没有创建任务，也不会产生费用")
                },
            )
        }
    }
}

@Composable
private fun ChatTopBar(online: Boolean, configured: Boolean, replying: Boolean, onBack: () -> Unit) {
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回 AI 相册")
            }
            XiaozhiAvatar()
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("小智", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !online -> "相框离线"
                        !configured -> "需要配置 AI"
                        replying -> "正在回复"
                        else -> "相框在线"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun XiaozhiAvatar() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ChatBanner(icon: ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfigurationBanner(onOpenConfig: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("先完成 AI 模型配置，才能开始对话", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenConfig, modifier = Modifier.heightIn(min = 48.dp)) { Text("去配置") }
        }
    }
}

@Composable
private fun EmptyConversation(onSuggestion: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XiaozhiAvatar()
        Text("嗨，我是小智", style = MaterialTheme.typography.headlineSmall)
        Text(
            "可以陪你聊天，也可以把想法变成画面。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        listOf("帮我想一句祝福", "描述一幅山水画", "聊聊今天的心情").forEach { suggestion ->
            OutlinedButton(onClick = { onSuggestion(suggestion) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(suggestion) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatMessageBubble(
    message: XiaozhiChatMessage,
    onGenerate: () -> Unit,
    onRetry: () -> Unit,
    onCopied: () -> Unit,
) {
    val user = message.sender == ChatSender.User
    val clipboard = LocalClipboardManager.current
    val bubbleModifier = if (user) Modifier else Modifier.combinedClickable(
        onClick = {},
        onLongClick = {
            clipboard.setText(AnnotatedString(message.text))
            onCopied()
        },
        role = Role.Button,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!user) {
            XiaozhiAvatar()
            Spacer(Modifier.size(8.dp))
        }
        Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(0.78f)) {
            Surface(
                modifier = bubbleModifier,
                color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = if (user) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
                tonalElevation = if (user) 0.dp else 1.dp,
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(message.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user) Text(
                    when (message.deliveryState) {
                        ChatDeliveryState.Sending -> "正在发送"
                        ChatDeliveryState.Sent -> "已发送"
                        ChatDeliveryState.Failed -> "发送失败"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.deliveryState == ChatDeliveryState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (message.deliveryState == ChatDeliveryState.Failed) Modifier.pressFeedbackClickable(role = Role.Button, onClick = onRetry) else Modifier,
                )
            }
            if (!user && message.canGenerateFromReply) {
                OutlinedButton(onClick = onGenerate, modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.size(6.dp))
                    Text("据此生成图片")
                }
            }
        }
    }
}

@Composable
private fun ReplyingBubble() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        XiaozhiAvatar()
        Spacer(Modifier.size(8.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp), tonalElevation = 1.dp) {
            Text("小智正在回复 ···", modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReplyFailedCard(onRetry: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Text("这次没有收到小智的回复", modifier = Modifier.padding(start = 8.dp).weight(1f))
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Refresh, null)
                Spacer(Modifier.size(4.dp))
                Text("重试")
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    notice: String?,
    onDismissNotice: () -> Unit,
    onSend: () -> Unit,
    onGenerate: () -> Unit,
    online: Boolean,
    compact: Boolean,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            notice?.let {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, modifier = Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onDismissNotice, modifier = Modifier.heightIn(min = 48.dp)) { Text("知道了") }
                    }
                }
            }
            if (compact) CompactChatActions(
                draft = draft,
                onDraftChange = onDraftChange,
                online = online,
                onGenerate = onGenerate,
                onSend = onSend,
            ) else {
                OutlinedButton(onClick = onGenerate, enabled = draft.isNotBlank() && online, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.size(6.dp))
                    Text("生成图片")
                }
                ChatTextAndSendRow(draft, onDraftChange, online, onSend, compact = false)
            }
        }
    }
}

@Composable
private fun CompactChatActions(
    draft: String,
    onDraftChange: (String) -> Unit,
    online: Boolean,
    onGenerate: () -> Unit,
    onSend: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showGenerateLabel = maxWidth >= 420.dp
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onGenerate,
                enabled = draft.isNotBlank() && online,
                modifier = if (showGenerateLabel) Modifier.heightIn(min = 52.dp) else Modifier.size(52.dp),
                contentPadding = if (showGenerateLabel) PaddingValues(horizontal = 14.dp) else PaddingValues(0.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = if (showGenerateLabel) null else "生成图片")
                if (showGenerateLabel) {
                    Spacer(Modifier.size(6.dp))
                    Text("生成图片")
                }
            }
            ChatTextAndSendRow(
                draft = draft,
                onDraftChange = onDraftChange,
                online = online,
                onSend = onSend,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChatTextAndSendRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    online: Boolean,
    onSend: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { onDraftChange(it.take(500)) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = if (compact) 72.dp else 128.dp),
                    placeholder = { Text("输入文字…") },
                    minLines = 1,
                    maxLines = if (compact) 2 else 5,
                    shape = RoundedCornerShape(16.dp),
                )
                Button(onClick = onSend, enabled = draft.isNotBlank() && online, modifier = Modifier.size(52.dp), contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送文字")
                }
    }
}

@Composable
private fun GenerationConfirmationSheet(
    prompt: String,
    configured: Boolean,
    onModify: () -> Unit,
    onOpenConfig: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("确认生成图片", style = MaterialTheme.typography.headlineSmall)
        Text("画面描述", style = MaterialTheme.typography.titleMedium)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(prompt, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AI 模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (configured) "已配置" else "尚未配置", fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本次费用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("暂无法估算", fontWeight = FontWeight.Medium)
        }
        Text(
            "服务尚未接入，本次不会发起生成或产生费用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (configured) {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("确认生成") }
        } else {
            Button(onClick = onOpenConfig, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("前往模型配置") }
        }
        TextButton(onClick = onModify, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("继续修改") }
        Spacer(Modifier.size(8.dp))
    }
}

internal fun encodeMessage(message: XiaozhiChatMessage): String = listOf(
    message.id,
    message.sender.name,
    message.timeLabel,
    message.deliveryState.name,
    message.canGenerateFromReply.toString(),
    Base64.getUrlEncoder().withoutPadding().encodeToString(message.text.toByteArray(StandardCharsets.UTF_8)),
).joinToString("|")

internal fun decodeMessage(token: String): XiaozhiChatMessage? = runCatching {
    val parts = token.split('|', limit = 6)
    XiaozhiChatMessage(
        id = parts[0],
        sender = ChatSender.valueOf(parts[1]),
        timeLabel = parts[2],
        deliveryState = ChatDeliveryState.valueOf(parts[3]),
        canGenerateFromReply = parts[4].toBooleanStrict(),
        text = String(Base64.getUrlDecoder().decode(parts[5]), StandardCharsets.UTF_8),
    )
}.getOrNull()

@Preview(name = "小智对话 · 微信式", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun XiaozhiChatPreview() = EInkPhotoTheme(darkTheme = false) {
    XiaozhiChatScreen(
        messages = listOf(
            XiaozhiChatMessage("1", ChatSender.Xiaozhi, "你好呀，今天想聊点什么？", "14:30"),
            XiaozhiChatMessage("2", ChatSender.User, "帮我想一幅适合墨水屏的春日山水画。", "14:31"),
            XiaozhiChatMessage("3", ChatSender.Xiaozhi, "可以用远山、桃花和一条小河，画面保持简洁明快。", "14:31", canGenerateFromReply = true),
        ),
        draft = "",
        onDraftChange = {},
        listState = androidx.compose.foundation.lazy.rememberLazyListState(),
        online = true,
        configured = true,
        replying = false,
        replyFailed = false,
        notice = null,
        onNotice = {},
        onBack = {},
        onOpenConfig = {},
        contentPadding = PaddingValues(),
    )
}

@Preview(name = "小智对话 · 离线大字体", showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun XiaozhiChatOfflinePreview() = EInkPhotoTheme(darkTheme = true) {
    XiaozhiChatScreen(
        messages = emptyList(),
        draft = "这段草稿会继续保留",
        onDraftChange = {},
        listState = androidx.compose.foundation.lazy.rememberLazyListState(),
        online = false,
        configured = false,
        replying = false,
        replyFailed = false,
        notice = null,
        onNotice = {},
        onBack = {},
        onOpenConfig = {},
        contentPadding = PaddingValues(),
    )
}
