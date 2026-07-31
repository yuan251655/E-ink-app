package com.einkphoto.app.ui.aialbum

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.mode.ModeSwitchUiState
import com.einkphoto.app.feature.aialbum.AiImageLoadState
import com.einkphoto.app.feature.aialbum.AiImageUiState
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.ui.components.ModeFeatureHeader
import com.einkphoto.app.ui.components.ModeSwitchStatusCard
import com.einkphoto.app.ui.components.crossFeatureDisplayText
import com.einkphoto.app.ui.components.modeCoverDrawableRes
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class AiAlbumRoute(val title: String, val description: String) {
    Home("AI 相册", ""),
    Chat("小智", ""),
    Images("AI 图片", "这里将只管理 AI 相册生成的图片，不会混入本地相册内容。"),
    ImageDetail("图片详情", "查看 AI 图片信息与可用操作。"),
    Create("创建 AI 图片", "生图任务将在模型配置与设备接口接入后开放。"),
    Playback("AI 轮播设置", "AI 轮播拥有独立的开关、间隔和播放顺序。"),
    ModelConfig("AI 模型配置", "这里将提供模型配置教程、密钥保存与连通性测试。"),
    ModelTutorial(MODEL_TUTORIAL_TITLE, "按照七个步骤完成模型服务配置。"),
    Usage("Token 与计费", "这里将区分实际用量、估算费用与官方账户余额。"),
}

internal fun isImmersiveAiConversation(route: AiAlbumRoute): Boolean = route == AiAlbumRoute.Chat

internal fun aiBackDestination(current: AiAlbumRoute, configReturnRoute: AiAlbumRoute): AiAlbumRoute = when (current) {
    AiAlbumRoute.ModelTutorial -> AiAlbumRoute.ModelConfig
    AiAlbumRoute.ModelConfig -> if (configReturnRoute == AiAlbumRoute.Chat) AiAlbumRoute.Chat else AiAlbumRoute.Home
    AiAlbumRoute.ImageDetail -> AiAlbumRoute.Images
    AiAlbumRoute.Images -> AiAlbumRoute.Home
    else -> AiAlbumRoute.Home
}

internal enum class AiCurrentDisplayPresentation {
    ModeCover,
    AiMedia,
    OtherFeature,
    Unavailable,
}

internal fun aiCurrentDisplayPresentation(device: DeviceSnapshot): AiCurrentDisplayPresentation {
    val current = device.currentContent ?: return if (device.activeFeature == DeviceFeature.AiAlbum) {
        AiCurrentDisplayPresentation.Unavailable
    } else {
        AiCurrentDisplayPresentation.OtherFeature
    }
    if (current.ownerFeature != DeviceFeature.AiAlbum) return AiCurrentDisplayPresentation.OtherFeature
    return when (current.kind) {
        DeviceContentKind.ModeCover -> if (
            current.category == DeviceMediaCategory.System &&
            current.systemAssetId == "mode_cover_ai_album"
        ) {
            AiCurrentDisplayPresentation.ModeCover
        } else {
            AiCurrentDisplayPresentation.Unavailable
        }
        DeviceContentKind.Media -> if (current.category == DeviceMediaCategory.Ai) {
            AiCurrentDisplayPresentation.AiMedia
        } else {
            AiCurrentDisplayPresentation.Unavailable
        }
        else -> AiCurrentDisplayPresentation.Unavailable
    }
}

@Composable
fun AiAlbumHost(
    device: DeviceSnapshot,
    modeSwitchState: ModeSwitchUiState,
    onSwitchMode: (DeviceFeature) -> Unit,
    onOpenNetworkSettings: () -> Unit,
    aiImageUiState: AiImageUiState = AiImageUiState(),
    onRefreshAiImages: () -> Unit = {},
    onLoadMoreAiImages: () -> Unit = {},
    onDisplayAiImage: (String) -> Unit = {},
    onDeleteAiImage: (String) -> Unit = {},
    onSaveAiImageToPhone: (String) -> Unit = {},
    onSetAiPlaybackStart: () -> Unit = {},
    onConversationActiveChanged: (Boolean) -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var routeName by rememberSaveable { mutableStateOf(AiAlbumRoute.Home.name) }
    var configReturnRouteName by rememberSaveable { mutableStateOf(AiAlbumRoute.Home.name) }
    var tutorialCurrentStep by rememberSaveable { mutableStateOf(0) }
    var tutorialCompletedSteps by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var selectedAiImageId by rememberSaveable { mutableStateOf<String?>(null) }
    // Secret is intentionally ordinary in-memory state: Config <-> Tutorial keeps it, but SavedState,
    // rotation/process recreation and a disposed AI host cannot restore it.
    var pendingApiKey by remember { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var interactionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var chatNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val homeListState = rememberLazyListState()
    val chatListState = rememberLazyListState()
    val aiImageGridState = rememberLazyGridState()
    val chatMessages = rememberXiaozhiMessages()
    val stateHolder = rememberSaveableStateHolder()
    val route = AiAlbumRoute.entries.firstOrNull { it.name == routeName } ?: AiAlbumRoute.Home
    val runtimeAiImages = aiImageUiState.images.map { item ->
        AiImageRecord(
            id = item.id,
            category = DeviceMediaCategory.Ai,
            name = item.displayName,
            prompt = item.prompt,
            generatedAtLabel = formatAiImageTime(item.createdAtEpochMillis),
            modelLabel = item.model,
            sourceLabel = item.source,
            sizeBytes = item.sizeBytes ?: -1L,
            previewUri = item.previewUri,
            sourceAvailable = item.sourceAvailable,
            syncLabel = item.syncStatus,
        )
    }
    val aiImageState = presentAiImageLibraryState(
        loadState = aiImageUiState.loadState,
        connection = device.connection,
        images = runtimeAiImages,
        errorMessage = aiImageUiState.errorMessage,
    )
    val navigate: (AiAlbumRoute) -> Unit = { routeName = it.name }
    val openConfig: (AiAlbumRoute) -> Unit = { from ->
        configReturnRouteName = from.name
        routeName = AiAlbumRoute.ModelConfig.name
    }
    val back = {
        val configReturnRoute = AiAlbumRoute.entries.firstOrNull { it.name == configReturnRouteName } ?: AiAlbumRoute.Home
        routeName = aiBackDestination(route, configReturnRoute).name
    }

    LaunchedEffect(route, device.connection) {
        onConversationActiveChanged(isImmersiveAiConversation(route))
        if (route == AiAlbumRoute.Images && device.connection == DeviceConnectionState.Online) onRefreshAiImages()
    }
    DisposableEffect(Unit) {
        onDispose { onConversationActiveChanged(false) }
    }

    BackHandler(enabled = route != AiAlbumRoute.Home, onBack = back)
    if (route == AiAlbumRoute.Home) {
        AiAlbumHomeScreen(
            device = device,
            modeSwitchState = modeSwitchState,
            onSwitchMode = onSwitchMode,
            contentPadding = contentPadding,
            draft = draft,
            onDraftChange = {
                draft = it
                interactionMessage = null
            },
            interactionMessage = interactionMessage,
            onSend = {
                if (draft.isBlank()) interactionMessage = "请先输入想和小智说的话"
                else {
                    chatNotice = chatSendNotice(
                        draft,
                        device.connection == DeviceConnectionState.Online,
                        configured = false,
                    )
                    navigate(AiAlbumRoute.Chat)
                }
            },
            onGenerate = {
                if (draft.isBlank()) interactionMessage = "请先描述想生成的画面"
                else navigate(AiAlbumRoute.Chat)
            },
            onOpenChat = { navigate(AiAlbumRoute.Chat) },
            onOpenConfig = { openConfig(AiAlbumRoute.Home) },
            onOpenNetworkSettings = onOpenNetworkSettings,
            onNavigate = navigate,
            listState = homeListState,
            modifier = modifier,
        )
    } else if (route == AiAlbumRoute.Chat) {
        XiaozhiChatScreen(
            messages = chatMessages,
            draft = draft,
            onDraftChange = {
                draft = it
                chatNotice = null
            },
            listState = chatListState,
            online = device.connection == DeviceConnectionState.Online,
            configured = false,
            replying = false,
            replyFailed = false,
            notice = chatNotice,
            onNotice = { chatNotice = it },
            onBack = back,
            onOpenConfig = { openConfig(AiAlbumRoute.Chat) },
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (route == AiAlbumRoute.Images) {
        AiImageLibraryScreen(
            state = aiImageState,
            currentContent = device.currentContent,
            listState = aiImageGridState,
            onBack = back,
            onCreate = { routeName = AiAlbumRoute.Chat.name },
            onOpenDetails = { mediaId ->
                val selected = filterAiImageRecords(runtimeAiImages).firstOrNull { it.id == mediaId }
                if (selected != null) {
                    selectedAiImageId = selected.id
                    routeName = AiAlbumRoute.ImageDetail.name
                }
            },
            onRetry = onRefreshAiImages,
            hasMore = aiImageUiState.hasMore,
            loadingMore = aiImageUiState.loadingMore,
            onLoadMore = onLoadMoreAiImages,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (route == AiAlbumRoute.ImageDetail) {
        AiImageDetailScreen(
            image = filterAiImageRecords(runtimeAiImages).firstOrNull { it.id == selectedAiImageId },
            currentContent = device.currentContent,
            connected = device.connection == DeviceConnectionState.Online,
            actionMessage = aiImageUiState.actionMessage,
            displayInProgress = aiImageUiState.activeJob?.state in setOf(DeviceJobState.Queued, DeviceJobState.Running),
            onBack = back,
            onDisplay = { selectedAiImageId?.let(onDisplayAiImage) },
            onSaveToPhone = { selectedAiImageId?.let(onSaveAiImageToPhone) },
            onSetPlaybackStart = onSetAiPlaybackStart,
            onDelete = { selectedAiImageId?.let(onDeleteAiImage) },
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (route == AiAlbumRoute.ModelConfig) {
        stateHolder.SaveableStateProvider(AiAlbumRoute.ModelConfig.name) {
            AiModelConfigScreen(
                snapshot = AiConfigSnapshot.unconfigured(),
                onBack = back,
                newApiKey = pendingApiKey,
                onNewApiKeyChange = { pendingApiKey = it },
                tutorialCurrentStep = tutorialCurrentStep,
                tutorialCompletedSteps = tutorialCompletedSteps.size,
                onOpenTutorial = { routeName = AiAlbumRoute.ModelTutorial.name },
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }
    } else if (route == AiAlbumRoute.ModelTutorial) {
        AiModelTutorialScreen(
            currentStep = tutorialCurrentStep,
            completedSteps = tutorialCompletedSteps.toSet(),
            onCurrentStepChange = { tutorialCurrentStep = normalizeTutorialStep(it) },
            onCompletedStepsChange = { steps -> tutorialCompletedSteps = steps.sorted() },
            onBack = back,
            onFinish = { routeName = AiAlbumRoute.ModelConfig.name },
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else {
        AiAlbumSubpageSkeleton(route, back, contentPadding, modifier)
    }
}

internal fun presentAiImageLibraryState(
    loadState: AiImageLoadState,
    connection: DeviceConnectionState,
    images: List<AiImageRecord>,
    errorMessage: String?,
): AiImageLibraryState = if (connection != DeviceConnectionState.Online) {
    AiImageLibraryState.Offline
} else when (loadState) {
    AiImageLoadState.Idle, AiImageLoadState.Loading -> AiImageLibraryState.Loading
    AiImageLoadState.Ready -> AiImageLibraryState.Ready(images)
    AiImageLoadState.Offline -> AiImageLibraryState.Offline
    AiImageLoadState.Error -> AiImageLibraryState.Error(errorMessage ?: "请稍后重试。")
}

private fun formatAiImageTime(epochMillis: Long): String = if (epochMillis > 0L) {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
} else {
    "未提供"
}

@Composable
private fun AiAlbumHomeScreen(
    device: DeviceSnapshot,
    modeSwitchState: ModeSwitchUiState,
    onSwitchMode: (DeviceFeature) -> Unit,
    contentPadding: PaddingValues,
    draft: String,
    onDraftChange: (String) -> Unit,
    interactionMessage: String?,
    onSend: () -> Unit,
    onGenerate: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onNavigate: (AiAlbumRoute) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ModeFeatureHeader("AI 相册", DeviceFeature.AiAlbum, device, modeSwitchState, onSwitchMode)
            }
            item { ModeSwitchStatusCard(DeviceFeature.AiAlbum, modeSwitchState) }
            item { AiCurrentDisplayCard(device) }
            item {
                XiaozhiInputCard(
                    draft = draft,
                    onDraftChange = onDraftChange,
                    interactionMessage = interactionMessage,
                    onSend = onSend,
                    onGenerate = onGenerate,
                    onOpenChat = onOpenChat,
                    onOpenConfig = onOpenConfig,
                    onOpenNetworkSettings = onOpenNetworkSettings,
                    activeAiMode = device.activeFeature == DeviceFeature.AiAlbum,
                    deviceOnline = device.connection == DeviceConnectionState.Online,
                )
            }
            item {
                Text("快捷功能", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AiShortcutCard(Icons.Outlined.Collections, "AI 图片", "查看生成记录", Modifier.weight(1f)) { onNavigate(AiAlbumRoute.Images) }
                        AiShortcutCard(Icons.Outlined.Schedule, "轮播设置", "仅播放 AI 图片", Modifier.weight(1f)) { onNavigate(AiAlbumRoute.Playback) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AiShortcutCard(Icons.Outlined.SettingsSuggest, "模型配置", "服务与密钥", Modifier.weight(1f), onClick = onOpenConfig)
                        AiShortcutCard(Icons.Outlined.Paid, "用量计费", "Token 与预算", Modifier.weight(1f)) { onNavigate(AiAlbumRoute.Usage) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("最近生成", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onNavigate(AiAlbumRoute.Images) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("查看全部") }
                }
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("还没有 AI 图片", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "完成模型配置后，可以从文字描述创建第一张图片。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { onNavigate(AiAlbumRoute.Create) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Spacer(Modifier.size(8.dp))
                            Text("创建第一张")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCurrentDisplayCard(device: DeviceSnapshot) {
    val presentation = aiCurrentDisplayPresentation(device)
    val owner = device.currentContent?.ownerFeature ?: device.activeFeature
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("当前画面", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(5f / 3f).semantics { contentDescription = "相框当前电子纸画面" },
                contentAlignment = Alignment.Center,
            ) {
                when (presentation) {
                    AiCurrentDisplayPresentation.ModeCover -> Image(
                        painter = painterResource(DeviceFeature.AiAlbum.modeCoverDrawableRes()),
                        contentDescription = "AI 相册模式提示画面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    AiCurrentDisplayPresentation.AiMedia -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text("正在显示 AI 相册图片", style = MaterialTheme.typography.titleMedium)
                        Text("图片预览暂不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AiCurrentDisplayPresentation.OtherFeature -> Text(
                        crossFeatureDisplayText(owner),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AiCurrentDisplayPresentation.Unavailable -> Text(
                        "相框当前画面暂不可读取",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                when (presentation) {
                    AiCurrentDisplayPresentation.ModeCover -> "AI 相册模式提示画面"
                    AiCurrentDisplayPresentation.AiMedia -> "AI 相册正在显示"
                    AiCurrentDisplayPresentation.OtherFeature -> crossFeatureDisplayText(owner)
                    AiCurrentDisplayPresentation.Unavailable -> "暂无可读取画面"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun XiaozhiInputCard(
    draft: String,
    onDraftChange: (String) -> Unit,
    interactionMessage: String?,
    onSend: () -> Unit,
    onGenerate: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    activeAiMode: Boolean,
    deviceOnline: Boolean,
) {
    val actionsEnabled = draft.isNotBlank() && deviceOnline
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("小智", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenChat, modifier = Modifier.heightIn(min = 48.dp)) { Text("进入对话") }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(500)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                label = { Text("想和小智聊点什么？") },
                placeholder = { Text("输入文字…") },
                minLines = 2,
                maxLines = 5,
                trailingIcon = {
                    IconButton(onClick = onSend, enabled = actionsEnabled, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送文字")
                    }
                },
            )
            Button(onClick = onGenerate, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.size(8.dp))
                Text("生成图片")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        !deviceOnline -> "相框未连接，恢复连接后即可使用小智"
                        activeAiMode -> "可在相框端说“你好，小智”进行语音唤醒"
                        else -> "切换到 AI 相册模式后，可在相框端语音唤醒小智"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            interactionMessage?.let { message ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onOpenConfig, modifier = Modifier.heightIn(min = 48.dp)) { Text("前往模型配置") }
                    }
                }
            }
            if (!deviceOnline) {
                TextButton(onClick = onOpenNetworkSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("前往网络配置")
                }
            }
        }
    }
}

@Composable
private fun AiShortcutCard(icon: ImageVector, title: String, detail: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier.pressFeedbackClickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AiAlbumSubpageSkeleton(route: AiAlbumRoute, onBack: () -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回 AI 相册")
                }
                Text(route.title, style = MaterialTheme.typography.headlineSmall)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Text("功能即将开放", style = MaterialTheme.typography.titleLarge)
                            Text(route.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("后续完成相关设置后，即可在这里使用完整功能。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun previewDevice(content: DeviceCurrentContent) = DeviceSnapshot(
    deviceId = "preview",
    displayName = "墨相框",
    isDemo = true,
    connection = DeviceConnectionState.Online,
    activeFeature = content.ownerFeature,
    displayBusy = false,
    storageFreeBytes = 2_000_000_000,
    capabilities = null,
    currentContent = content,
)

@Preview(name = "AI 相册首页 · 当前模式", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiAlbumHomePreview() = EInkPhotoTheme(darkTheme = false) {
    AiAlbumHost(
        device = previewDevice(DeviceCurrentContent(DeviceContentKind.ModeCover, DeviceFeature.AiAlbum, DeviceMediaCategory.System, null, "mode_cover_ai_album")),
        modeSwitchState = ModeSwitchUiState(),
        onSwitchMode = {},
        onOpenNetworkSettings = {},
        contentPadding = PaddingValues(),
    )
}

@Preview(name = "AI 相册首页 · 显示本地图片", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiAlbumCrossFeaturePreview() = EInkPhotoTheme(darkTheme = false) {
    AiAlbumHost(
        device = previewDevice(DeviceCurrentContent(DeviceContentKind.Media, DeviceFeature.LocalAlbum, DeviceMediaCategory.Local, "local-1", null)),
        modeSwitchState = ModeSwitchUiState(),
        onSwitchMode = {},
        onOpenNetworkSettings = {},
        contentPadding = PaddingValues(),
    )
}
