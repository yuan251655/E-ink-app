package com.einkphoto.app.ui.aialbum

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.mode.ModeSwitchUiState
import com.einkphoto.app.feature.aialbum.AiImageLoadState
import com.einkphoto.app.feature.aialbum.AiImageUiState
import com.einkphoto.app.feature.aialbum.AiConfigUiState
import com.einkphoto.app.feature.aialbum.AiModelProfile
import com.einkphoto.app.feature.aialbum.AiGenerationUiState
import com.einkphoto.app.feature.aialbum.PhotoStyleCatalog
import com.einkphoto.app.feature.aialbum.PhotoStylePreset
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import com.einkphoto.app.feature.localalbum.model.PlaybackSyncState
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.ui.components.ModeFeatureHeader
import com.einkphoto.app.ui.components.ModeSwitchStatusCard
import com.einkphoto.app.ui.components.DeviceConnectionBadge
import com.einkphoto.app.ui.components.crossFeatureDisplayText
import com.einkphoto.app.ui.components.modeCoverDrawableRes
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.components.hierarchicalPageTransition
import com.einkphoto.app.ui.components.AsyncButtonContent
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class AiAlbumRoute(val title: String, val description: String) {
    Home("AI 相册", ""),
    Images("AI 图片", "这里将只管理 AI 相册生成的图片，不会混入本地相册内容。"),
    ImageDetail("图片详情", "查看 AI 图片信息与可用操作。"),
    Create("创建 AI 图片", "生图任务将在模型配置与设备接口接入后开放。"),
    StyleGallery("照片风格转换", "选择一种风格，导入照片后生成预览。"),
    StyleDetail("风格转换", "导入一张手机照片并开始转换。"),
    Playback("AI 轮播设置", "AI 轮播拥有独立的开关、间隔和播放顺序。"),
    ModelConfig("模型管理", ""),
    ModelEditor("模型配置", ""),
    ModelSwitch("切换模型", ""),
    ModelTutorial(MODEL_TUTORIAL_TITLE, "按照七个步骤完成模型服务配置。"),
}

internal fun aiBackDestination(current: AiAlbumRoute): AiAlbumRoute = when (current) {
    AiAlbumRoute.ModelTutorial -> AiAlbumRoute.ModelEditor
    AiAlbumRoute.ModelEditor, AiAlbumRoute.ModelSwitch -> AiAlbumRoute.ModelConfig
    AiAlbumRoute.ModelConfig -> AiAlbumRoute.Home
    AiAlbumRoute.ImageDetail -> AiAlbumRoute.Images
    AiAlbumRoute.Images -> AiAlbumRoute.Home
    AiAlbumRoute.StyleDetail -> AiAlbumRoute.StyleGallery
    AiAlbumRoute.StyleGallery -> AiAlbumRoute.Home
    else -> AiAlbumRoute.Home
}

private fun aiRouteDepth(route: AiAlbumRoute): Int = when (route) {
    AiAlbumRoute.Home -> 0
    AiAlbumRoute.Images,
    AiAlbumRoute.ModelConfig,
    AiAlbumRoute.StyleGallery,
    AiAlbumRoute.Playback -> 1
    AiAlbumRoute.ImageDetail,
    AiAlbumRoute.ModelEditor,
    AiAlbumRoute.ModelSwitch,
    AiAlbumRoute.StyleDetail -> 2
    AiAlbumRoute.Create,
    AiAlbumRoute.ModelTutorial -> 3
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
    onDeleteAiImages: (Set<String>) -> Unit = {},
    onSaveAiImageToPhone: (String) -> Unit = {},
    onSetAiPlaybackStart: () -> Unit = {},
    aiPlayback: PlaybackSettings = PlaybackSettings(PlayMode.Paused, PlayOrder.Sequential, 1800),
    onRefreshAiPlayback: () -> Unit = {},
    onSaveAiPlayback: (PlayMode, PlayOrder, Int) -> Unit = { _, _, _ -> },
    aiConfigUiState: AiConfigUiState = AiConfigUiState(),
    onRefreshAiConfig: () -> Unit = {},
    onSaveAiConfig: (String?, String, String, String, String, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onTestSavedAiConfig: () -> Unit = {},
    onDeleteAiConfig: () -> Unit = {},
    onActivateAiModel: (String) -> Unit = {},
    aiGenerationUiState: AiGenerationUiState = AiGenerationUiState(),
    onGenerateAiImage: (String) -> Unit = {},
    onGeneratePhotoStyle: (String, Uri) -> Unit = { _, _ -> },
    onConfirmAiSave: () -> Unit = {},
    onContinueAiHistory: (String) -> Unit = {},
    onRetryAiSubmission: (String) -> Unit = {},
    onCancelAiWaitingSubmission: (String) -> Unit = {},
    onDiscardAiHistory: (String) -> Unit = {},
    onClearAiHistory: () -> Unit = {},
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var routeName by rememberSaveable { mutableStateOf(AiAlbumRoute.Home.name) }
    var tutorialCurrentStep by rememberSaveable { mutableStateOf(0) }
    var tutorialCompletedSteps by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var selectedAiImageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPhotoStyleId by rememberSaveable { mutableStateOf<String?>(null) }
    var photoStyleCreate by rememberSaveable { mutableStateOf(false) }
    // Secret is intentionally ordinary in-memory state: Config <-> Tutorial keeps it, but SavedState,
    // rotation/process recreation and a disposed AI host cannot restore it.
    var pendingApiKey by remember { mutableStateOf("") }
    val homeListState = rememberLazyListState()
    val aiImageGridState = rememberLazyGridState()
    val stateHolder = rememberSaveableStateHolder()
    val route = AiAlbumRoute.entries.firstOrNull { it.name == routeName } ?: AiAlbumRoute.Home
    val runtimeAiImages = aiImageUiState.images.mapIndexed { index, item ->
        AiImageRecord(
            id = item.id,
            category = DeviceMediaCategory.Ai,
            name = "AI 图片 ${String.format(Locale.ROOT, "%03d", index + 1)}",
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
    val openConfig = { routeName = AiAlbumRoute.ModelConfig.name }
    val back = {
        routeName = aiBackDestination(route).name
    }

    androidx.compose.runtime.LaunchedEffect(route, device.connection, device.currentContent?.mediaId) {
        // Home preloads the AI library.  Do not issue a second list request
        // merely because the user opens the Images subpage; that request used
        // to replace a ready gallery with a visible loading loop.
        if (route == AiAlbumRoute.Home && device.connection == DeviceConnectionState.Online) onRefreshAiImages()
        if (route in setOf(AiAlbumRoute.ModelConfig, AiAlbumRoute.ModelEditor, AiAlbumRoute.ModelSwitch) && device.connection == DeviceConnectionState.Online) onRefreshAiConfig()
        if (route in setOf(AiAlbumRoute.Home, AiAlbumRoute.Playback) && device.connection == DeviceConnectionState.Online) onRefreshAiPlayback()
    }
    BackHandler(enabled = route != AiAlbumRoute.Home, onBack = back)
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            hierarchicalPageTransition(aiRouteDepth(targetState) > aiRouteDepth(initialState))
        },
        label = "ai-album-page",
    ) { displayedRoute ->
    if (displayedRoute == AiAlbumRoute.Home) {
        AiAlbumHomeScreen(
            device = device,
            aiImages = runtimeAiImages,
            playback = aiPlayback,
            modeSwitchState = modeSwitchState,
            onSwitchMode = onSwitchMode,
            contentPadding = contentPadding,
            onOpenXiaozhiSettings = {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://xiaozhi.me/")))
            },
            onOpenConfig = openConfig,
            onNavigate = { destination ->
                if (destination == AiAlbumRoute.Create) photoStyleCreate = false
                navigate(destination)
            },
            listState = homeListState,
            modifier = modifier,
        )
    } else if (displayedRoute == AiAlbumRoute.Images) {
        AiImageLibraryScreen(
            state = aiImageState,
            currentContent = device.currentContent,
            listState = aiImageGridState,
            onBack = back,
            onOpenDetails = { mediaId ->
                val selected = filterAiImageRecords(runtimeAiImages).firstOrNull { it.id == mediaId }
                if (selected != null) {
                    selectedAiImageId = selected.id
                    routeName = AiAlbumRoute.ImageDetail.name
                }
            },
            onRetry = onRefreshAiImages,
            onDeleteSelected = onDeleteAiImages,
            hasMore = aiImageUiState.hasMore,
            loadingMore = aiImageUiState.loadingMore,
            onLoadMore = onLoadMoreAiImages,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (displayedRoute == AiAlbumRoute.ImageDetail) {
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
    } else if (displayedRoute == AiAlbumRoute.ModelConfig) {
        AiModelManagerScreen(
            configured = aiConfigUiState.configuration.configured,
            activeName = aiConfigUiState.configuration.profileName.ifBlank { aiConfigUiState.configuration.imageModel },
            profiles = aiConfigUiState.profiles,
            profilesAvailable = aiConfigUiState.profilesAvailable,
            onEdit = { editingModelId = aiConfigUiState.configuration.profileId.takeIf { it.isNotBlank() }; routeName = AiAlbumRoute.ModelEditor.name },
            onCreate = { editingModelId = null; pendingApiKey = ""; routeName = AiAlbumRoute.ModelEditor.name },
            onSwitch = { routeName = AiAlbumRoute.ModelSwitch.name },
            onBack = back,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (displayedRoute == AiAlbumRoute.ModelEditor) {
        val profile = aiConfigUiState.profiles.firstOrNull { it.id == editingModelId }
        val editorSnapshot = when {
            profile != null -> AiConfigSnapshot(
                configured = true,
                profileId = profile.id,
                enabled = profile.active,
                mode = AiServiceMode.Direct,
                provider = "火山方舟",
                profileName = profile.name,
                serviceUrl = profile.endpoint,
                chatModel = "文本推理模型将在图片生成服务中使用",
                imageModel = profile.imageModel,
                apiKeySuffix = profile.keyLast4,
                lastVerifiedLabel = null,
            )
            editingModelId != null && aiConfigUiState.configuration.configured -> AiConfigSnapshot(
                configured = true,
                profileId = aiConfigUiState.configuration.profileId,
                enabled = true,
                mode = AiServiceMode.Direct,
                provider = "火山方舟",
                profileName = aiConfigUiState.configuration.profileName.ifBlank { aiConfigUiState.configuration.imageModel },
                serviceUrl = aiConfigUiState.configuration.endpoint,
                chatModel = "文本推理模型将在图片生成服务中使用",
                imageModel = aiConfigUiState.configuration.imageModel,
                apiKeySuffix = aiConfigUiState.configuration.keyLast4,
                lastVerifiedLabel = null,
            )
            else -> AiConfigSnapshot.unconfigured()
        }
        stateHolder.SaveableStateProvider("${AiAlbumRoute.ModelEditor.name}-${editingModelId ?: "new"}") {
            AiModelConfigScreen(
                snapshot = editorSnapshot,
                onBack = back,
                newApiKey = pendingApiKey,
                onNewApiKeyChange = { pendingApiKey = it },
                onSave = { name, endpoint, model, key, testRequested -> onSaveAiConfig(editorSnapshot.profileId.takeIf { it.isNotBlank() }, name, endpoint, model, key, testRequested) },
                onTestSaved = onTestSavedAiConfig,
                onDelete = onDeleteAiConfig,
                operationMessage = aiConfigUiState.message,
                testResult = aiConfigUiState.testResult,
                saving = aiConfigUiState.saving,
                tutorialCurrentStep = tutorialCurrentStep,
                tutorialCompletedSteps = tutorialCompletedSteps.size,
                onOpenTutorial = { routeName = AiAlbumRoute.ModelTutorial.name },
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }
    } else if (displayedRoute == AiAlbumRoute.ModelSwitch) {
        AiModelSwitchScreen(
            profiles = aiConfigUiState.profiles,
            profilesAvailable = aiConfigUiState.profilesAvailable,
            saving = aiConfigUiState.saving,
            message = aiConfigUiState.message,
            onActivate = onActivateAiModel,
            onBack = back,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (displayedRoute == AiAlbumRoute.Create) {
        AiGenerationChatScreen(
            state = aiGenerationUiState,
            onGenerate = onGenerateAiImage,
            onConfirmSave = onConfirmAiSave,
            onOpenAiImages = { routeName = AiAlbumRoute.Images.name },
            onContinueHistory = onContinueAiHistory,
            onRetrySubmission = onRetryAiSubmission,
            onCancelWaitingSubmission = onCancelAiWaitingSubmission,
            onDiscardHistory = onDiscardAiHistory,
            onClearHistory = onClearAiHistory,
            photoStyleOnly = photoStyleCreate,
            onBack = back,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (displayedRoute == AiAlbumRoute.StyleGallery) {
        PhotoStyleGalleryScreen(
            onBack = back,
            onSelect = { preset ->
                selectedPhotoStyleId = preset.id
                routeName = AiAlbumRoute.StyleDetail.name
            },
            contentPadding = contentPadding,
            modifier = modifier,
        )
    } else if (displayedRoute == AiAlbumRoute.StyleDetail) {
        val preset = PhotoStyleCatalog.presets.firstOrNull { it.id == selectedPhotoStyleId }
        if (preset == null) {
            routeName = AiAlbumRoute.StyleGallery.name
        } else {
            PhotoStyleDetailScreen(
                preset = preset,
                onBack = back,
                onStart = { selected, uri ->
                    onGeneratePhotoStyle(selected.prompt, uri)
                    photoStyleCreate = true
                    routeName = AiAlbumRoute.Create.name
                },
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }
    } else if (displayedRoute == AiAlbumRoute.ModelTutorial) {
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
    } else if (displayedRoute == AiAlbumRoute.Playback) {
        AiPlaybackSettingsScreen(aiPlayback, onSaveAiPlayback, back, contentPadding, modifier)
    } else {
        AiAlbumSubpageSkeleton(displayedRoute, back, contentPadding, modifier)
    }
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

private fun formatAiImageTime(epochMillis: Long): String = if (epochMillis >= 1_577_836_800_000L) {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
} else {
    "时间未记录"
}

@Composable
private fun AiAlbumHomeScreen(
    device: DeviceSnapshot,
    aiImages: List<AiImageRecord>,
    playback: PlaybackSettings,
    modeSwitchState: ModeSwitchUiState,
    onSwitchMode: (DeviceFeature) -> Unit,
    contentPadding: PaddingValues,
    onOpenXiaozhiSettings: () -> Unit,
    onOpenConfig: () -> Unit,
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
            item {
                AiCurrentDisplayCard(
                    device = device,
                    currentAiImage = aiImages.firstOrNull { it.id == device.currentContent?.mediaId },
                    playback = playback,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    AiPrimaryActionRow(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "创建图片",
                        detail = "用文字生成一张新的 AI 图片",
                        onClick = { onNavigate(AiAlbumRoute.Create) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    AiPrimaryActionRow(
                        icon = Icons.Outlined.Collections,
                        title = "照片风格转换",
                        detail = "导入照片并选择一种艺术风格",
                        onClick = { onNavigate(AiAlbumRoute.StyleGallery) },
                    )
                }
            }
            item {
                XiaozhiInputCard(
                    onOpenSettings = onOpenXiaozhiSettings,
                    activeAiMode = device.activeFeature == DeviceFeature.AiAlbum,
                    deviceOnline = device.connection == DeviceConnectionState.Online,
                )
            }
            item {
                Text("快捷功能", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    AiPrimaryActionRow(Icons.Outlined.Collections, "AI 图片", "查看生成记录") { onNavigate(AiAlbumRoute.Images) }
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    AiPrimaryActionRow(Icons.Outlined.Schedule, "轮播设置", "仅播放 AI 图片") { onNavigate(AiAlbumRoute.Playback) }
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    AiPrimaryActionRow(Icons.Outlined.SettingsSuggest, "模型配置", "服务与密钥", onOpenConfig)
                }
            }
        }
    }
}

@Composable
private fun AiPrimaryActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).pressFeedbackClickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun AiModelManagerScreen(configured: Boolean, activeName: String, profiles: List<AiModelProfile>, profilesAvailable: Boolean, onEdit: () -> Unit, onCreate: () -> Unit, onSwitch: () -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回 AI 相册") }; Text("模型管理", style = MaterialTheme.typography.titleLarge) }
        if (configured) Text("当前：$activeName", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        Card(modifier = Modifier.fillMaxWidth(), onClick = onEdit) { Column(Modifier.padding(20.dp)) { Text("编辑当前模型", style = MaterialTheme.typography.titleMedium); Text("修改当前模型的名称、服务和 Key", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
        Card(modifier = Modifier.fillMaxWidth(), onClick = onCreate) { Column(Modifier.padding(20.dp)) { Text("新增模型", style = MaterialTheme.typography.titleMedium); Text("为另一套服务配置单独保存名称", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
        Card(modifier = Modifier.fillMaxWidth(), onClick = onSwitch) { Column(Modifier.padding(20.dp)) { Text("切换模型", style = MaterialTheme.typography.titleMedium); Text(if (profilesAvailable) "已保存 ${profiles.size} 个模型" else "读取已保存模型后可切换", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
    }
}

@Composable
private fun AiModelSwitchScreen(profiles: List<AiModelProfile>, profilesAvailable: Boolean, saving: Boolean, message: String?, onActivate: (String) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回模型管理") }; Text("切换模型", style = MaterialTheme.typography.titleLarge) }
        if (!profilesAvailable) {
            Card(Modifier.fillMaxWidth()) { Text("暂时无法读取已保存模型。请确认相框已更新并保持连接。", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (profiles.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Text("还没有已保存模型。请先返回新增一个模型。", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            profiles.forEach { profile ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { if (!profile.active && !saving) onActivate(profile.id) }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(profile.imageModel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (profile.active) Text("当前使用", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) else Text("切换", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AiPlaybackSettingsScreen(state: PlaybackSettings, onSave: (PlayMode, PlayOrder, Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues, modifier: Modifier) {
    var mode by remember(state.mode) { mutableStateOf(state.mode) }; var order by remember(state.order) { mutableStateOf(state.order) }; var interval by remember(state.intervalSeconds) { mutableStateOf(state.intervalSeconds) }
    Column(modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回 AI 相册") }; Column { Text("AI 轮播设置", style = MaterialTheme.typography.titleLarge); Text("仅播放 AI 相册图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item { Text(if (state.mode == PlayMode.Auto) "正在轮播" else "轮播已暂停", style = MaterialTheme.typography.titleMedium); Text(if (state.mode == PlayMode.Auto) "下次切换：${state.nextPlayInSeconds?.let { "约 ${it / 60 + 1} 分钟后" } ?: "等待设备计时"}" else "当前图片将保持不变", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Text("播放模式", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(mode == PlayMode.Auto, { mode = PlayMode.Auto }, { Text("自动轮播") }, Modifier.weight(1f)); FilterChip(mode == PlayMode.Paused, { mode = PlayMode.Paused }, { Text("暂停轮播") }, Modifier.weight(1f)) } }
            item { Text("轮播间隔", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.size(6.dp)); listOf(300 to "5 分钟",900 to "15 分钟",1800 to "30 分钟",3600 to "1 小时",10800 to "3 小时",21600 to "6 小时",43200 to "12 小时",86400 to "24 小时").chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { (v,l) -> FilterChip(interval == v, { interval = v }, { Text(l) }, Modifier.weight(1f).heightIn(min = 48.dp)) } }; Spacer(Modifier.size(10.dp)) } }
            item { Text("播放顺序", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(order == PlayOrder.Sequential, { order = PlayOrder.Sequential }, { Text("顺序") }); FilterChip(order == PlayOrder.Random, { order = PlayOrder.Random }, { Text("随机") }) } }
            item { Button(onClick = { onSave(mode, order, interval) }, enabled = state.syncState == PlaybackSyncState.Ready, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { AsyncButtonContent(state.syncState == PlaybackSyncState.Saving, "保存到相册", "正在保存…") }; Text(when (state.syncState) { PlaybackSyncState.Offline -> "相框未连接，无法保存"; PlaybackSyncState.Conflict -> "设备设置已变化，请确认后重新保存"; PlaybackSyncState.Loading -> "正在读取设备设置"; else -> "设置将独立保存到 AI 相册" }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AiCurrentDisplayCard(
    device: DeviceSnapshot,
    currentAiImage: AiImageRecord?,
    playback: PlaybackSettings,
) {
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
                    AiCurrentDisplayPresentation.AiMedia -> AiCurrentImagePreview(currentAiImage)
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
                    AiCurrentDisplayPresentation.AiMedia -> currentAiImage?.name ?: "AI 相册正在显示"
                    AiCurrentDisplayPresentation.OtherFeature -> crossFeatureDisplayText(owner)
                    AiCurrentDisplayPresentation.Unavailable -> "暂无可读取画面"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (presentation == AiCurrentDisplayPresentation.AiMedia && playback.mode == PlayMode.Auto) {
                val nextTime = playback.nextPlayInSeconds?.let { seconds ->
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                        Date(System.currentTimeMillis() + seconds.coerceAtLeast(0) * 1_000L),
                    )
                }
                Text(
                    nextTime?.let { "下一次切换：$it" } ?: "下一次切换：等待设备计时",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AiCurrentImagePreview(image: AiImageRecord?) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image?.previewUri) {
        value = image?.previewUri?.let { previewUri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(previewUri))?.use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
        }
    }
    Crossfade(bitmap, animationSpec = tween(220), label = "ai-current-image") { loadedBitmap ->
    if (loadedBitmap != null) {
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = "相框当前显示的 AI 图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text("正在显示 AI 相册图片", style = MaterialTheme.typography.titleMedium)
        }
    }
    }
}

@Composable
private fun XiaozhiInputCard(
    onOpenSettings: () -> Unit,
    activeAiMode: Boolean,
    deviceOnline: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("小智", style = MaterialTheme.typography.titleLarge)
                    Text("语音唤醒、播报与服务状态", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.SettingsSuggest, null)
                Spacer(Modifier.size(8.dp))
                Text("小智 AI 设置")
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
    displayName = "相念",
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
