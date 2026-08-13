package com.einkphoto.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceSession
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DeviceGlobalNoticeBus
import com.einkphoto.app.feature.settings.network.LanNetworkRepository
import com.einkphoto.app.feature.settings.storage.LanStorageRepository
import com.einkphoto.app.feature.settings.power.LanPowerRepository
import com.einkphoto.app.feature.settings.audio.LanAudioRepository
import com.einkphoto.app.feature.settings.power.PowerSnapshot
import com.einkphoto.app.ui.components.DeviceConnectionBadge
import com.einkphoto.app.ui.components.FrameBatteryIcon
import com.einkphoto.app.ui.localalbum.LocalAlbumDemoHost
import com.einkphoto.app.ui.localalbum.rememberLocalAlbumDemoRuntime
import com.einkphoto.app.ui.aialbum.AiAlbumHost
import com.einkphoto.app.ui.dashboard.InfoDashboardHost
import com.einkphoto.app.ui.model.AppDestination
import com.einkphoto.app.ui.screens.FeaturePlaceholderScreen
import com.einkphoto.app.ui.settings.NetworkSettingsScreen
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einkphoto.app.feature.mode.ModeSwitchViewModel
import com.einkphoto.app.feature.aialbum.AiImageViewModel
import com.einkphoto.app.feature.aialbum.LanAiImageRepository
import com.einkphoto.app.feature.aialbum.AiConfigRepository
import com.einkphoto.app.feature.aialbum.AiConfigViewModel
import com.einkphoto.app.feature.aialbum.AiGenerationViewModel
import com.einkphoto.app.feature.aialbum.AiGenerationRepository
import com.einkphoto.app.feature.aialbum.AiPlaybackViewModel
import com.einkphoto.app.feature.mode.ModeSwitchPhase
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EInkPhotoApp() = EInkPhotoTheme {
    var selected by rememberSaveable { mutableStateOf(AppDestination.LocalAlbum) }
    var brandTransitionComplete by rememberSaveable { mutableStateOf(false) }
    val brandProgress = remember { Animatable(if (brandTransitionComplete) 1f else 0f) }
    Box(Modifier.fillMaxSize()) {
        EInkPhotoAppContent(selected, brandTitleAlpha = if (brandTransitionComplete) 1f else 0f) { selected = it }
        if (!brandTransitionComplete) BrandSplashScreen(brandProgress.value)
    }
    LaunchedEffect(brandTransitionComplete) {
        if (!brandTransitionComplete) {
            delay(1_100L)
            brandProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(700, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)),
            )
            brandTransitionComplete = true
        }
    }
}

@Composable
private fun BrandSplashScreen(progress: Float) {
    val backgroundAlpha = (1f - ((progress - 0.12f) / 0.68f)).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(10f)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha)))
        val titleTravel = 52.dp - maxHeight / 2
        Text(
            text = "相念",
            fontSize = (42f + (16f - 42f) * progress).sp,
            lineHeight = (48f + (22f - 48f) * progress).sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (8f + ((-0.15f) - 8f) * progress).sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = titleTravel * progress),
        )
        Text(
            text = "因相而念，彼此相念。",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.78f * (1f - progress / 0.42f).coerceIn(0f, 1f),
            ),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 44.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EInkPhotoAppContent(
    selected: AppDestination,
    brandTitleAlpha: Float = 1f,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val context = LocalContext.current
    val transport = remember { HttpLanDeviceTransport() }
    val session = remember(transport) { LanDeviceSession(transport) }
    val snapshot by session.snapshot.collectAsState()
    val localAlbumRuntime = rememberLocalAlbumDemoRuntime(session)
    val localAlbumState by localAlbumRuntime.viewModel.uiState.collectAsState()
    val modeSwitchViewModel: ModeSwitchViewModel = viewModel(
        key = "device-mode-switch",
        factory = remember(session) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ModeSwitchViewModel(session) as T
            }
        },
    )
    val modeSwitchState by modeSwitchViewModel.state.collectAsState()
    val aiImageViewModel: AiImageViewModel = viewModel(
        key = "ai-image-library",
        factory = remember(session, transport, context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AiImageViewModel(
                    session,
                    LanAiImageRepository(context.applicationContext, session, transport),
                ) as T
            }
        },
    )
    val aiImageState by aiImageViewModel.state.collectAsState()
    val aiConfigViewModel: AiConfigViewModel = viewModel(
        key = "ai-provider-config",
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AiConfigViewModel(AiConfigRepository(context.applicationContext)) as T
            }
        },
    )
    val aiConfigState by aiConfigViewModel.state.collectAsState()
    val aiGenerationViewModel: AiGenerationViewModel = viewModel(
        key = "ai-image-generation",
        factory = remember(aiImageViewModel) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AiGenerationViewModel(AiGenerationRepository(context.applicationContext), onCompleted = aiImageViewModel::refresh) as T
            }
        },
    )
    val aiGenerationState by aiGenerationViewModel.state.collectAsState()
    val aiPlaybackViewModel: AiPlaybackViewModel = viewModel(key = "ai-playback")
    val aiPlaybackState by aiPlaybackViewModel.state.collectAsState()
    val networkRepository = remember { LanNetworkRepository() }
    val storageRepository = remember { LanStorageRepository() }
    val powerRepository = remember { LanPowerRepository() }
    val audioRepository = remember { LanAudioRepository() }
    val powerSnapshot by powerRepository.snapshot.collectAsState()
    var globalWarning by remember { mutableStateOf<String?>(null) }
    var observedCooldownRejectionSequence by remember { mutableStateOf<Long?>(null) }
    var showNetworkConfiguration by rememberSaveable { mutableStateOf(false) }
    var showStorageManagement by rememberSaveable { mutableStateOf(false) }
    var showPowerSettings by rememberSaveable { mutableStateOf(false) }
    var showAudioSettings by rememberSaveable { mutableStateOf(false) }
    var showDeviceDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showAppUpdate by rememberSaveable { mutableStateOf(false) }
    var handledModeSwitchJob by rememberSaveable { mutableStateOf<String?>(null) }
    // The badge is device state, not an optimistic network label.  Keep it
    // current while this composition is alive so powering off the frame (or
    // losing either AP or STA reachability) clears a stale "connected" badge.
    // LanDeviceSession only publishes Online after the health, capability and
    // status handshake all succeed; any failed heartbeat becomes Offline.
    LaunchedEffect(session) {
        while (true) {
            session.refreshSnapshot()
            delay(8_000L)
        }
    }
    LaunchedEffect(Unit) {
        DeviceGlobalNoticeBus.notices.collectLatest { message ->
            globalWarning = message
            delay(3_000L)
            globalWarning = null
        }
    }
    LaunchedEffect(snapshot.displayCooldownRejectionSequence) {
        val previous = observedCooldownRejectionSequence
        val current = snapshot.displayCooldownRejectionSequence
        if (current > (previous ?: 0L) && snapshot.displayCooldownRemainingSeconds > 0) {
            DeviceGlobalNoticeBus.displayCooldown(snapshot.displayCooldownRemainingSeconds)
        }
        observedCooldownRejectionSequence = current
    }
    // Keep the header indicator fresh while a real device is online. The
    // settings page has its own faster heartbeat; this lighter 10-second
    // cadence makes battery state visible across every App section.
    LaunchedEffect(snapshot.connection, powerRepository) {
        if (snapshot.connection != DeviceConnectionState.Online) return@LaunchedEffect
        powerRepository.refresh()
        while (true) {
            delay(10_000L)
            powerRepository.refresh()
        }
    }
    LaunchedEffect(snapshot.modeSwitchJobId, snapshot.pendingFeature) {
        val jobId = snapshot.modeSwitchJobId
        val target = snapshot.pendingFeature
        if (jobId != null && target != null) modeSwitchViewModel.resumePendingSwitch(target, jobId)
    }
    // The screen's active feature is the authority. Navigate only after the mode job is
    // terminal-success and a fresh device snapshot confirms the same target; never navigate
    // optimistically just because the user pressed a switch button.
    LaunchedEffect(modeSwitchState.jobId, modeSwitchState.phase, modeSwitchState.target, snapshot.activeFeature, snapshot.pendingFeature, snapshot.modeRevision) {
        val target = modeSwitchState.target
        if (modeSwitchState.phase != ModeSwitchPhase.Success || target == null ||
            snapshot.activeFeature != target || snapshot.pendingFeature != null
        ) return@LaunchedEffect
        val jobKey = modeSwitchState.jobId?.value ?: "confirmed-${target.apiValue}-${snapshot.modeRevision}"
        if (handledModeSwitchJob != jobKey) {
            handledModeSwitchJob = jobKey
            onDestinationSelected(target.destination())
        }
    }

    val settingsDetailVisible = selected == AppDestination.Settings && (
        showNetworkConfiguration || showStorageManagement || showPowerSettings || showAudioSettings ||
            showDeviceDiagnostics || showAppUpdate
        )
    val globalTask = when {
        modeSwitchState.switching -> GlobalTaskPresentation(
            message = modeSwitchState.message ?: "正在切换相框模式",
            destination = modeSwitchState.target?.destination() ?: selected,
        )
        aiGenerationState.active -> GlobalTaskPresentation(
            message = aiGenerationState.message ?: "正在处理 AI 图片",
            destination = AppDestination.AiAlbum,
        )
        aiImageState.activeJob?.state in setOf(DeviceJobState.Queued, DeviceJobState.Running) -> GlobalTaskPresentation(
            message = aiImageState.actionMessage ?: "正在刷新电子纸",
            destination = AppDestination.AiAlbum,
        )
        localAlbumState.batchSaveActive -> GlobalTaskPresentation(
            message = "正在保存本地图片 ${localAlbumState.batchSaveCompleted}/${localAlbumState.batchSaveTotal}",
            destination = AppDestination.LocalAlbum,
        )
        localAlbumState.conversionDrafts.values.any { it.stage in setOf(
            ConversionStage.Queued,
            ConversionStage.Preparing,
            ConversionStage.RenderingPreview,
            ConversionStage.Quantizing,
            ConversionStage.Validating,
            ConversionStage.Uploading,
            ConversionStage.DeviceValidating,
            ConversionStage.Committing,
        ) } -> GlobalTaskPresentation(
            message = "正在转换并保存本地图片",
            destination = AppDestination.LocalAlbum,
        )
        localAlbumState.displayJob?.state in setOf(DeviceJobState.Queued, DeviceJobState.Running) -> GlobalTaskPresentation(
            message = "正在刷新电子纸",
            destination = AppDestination.LocalAlbum,
        )
        else -> null
    }
    BackHandler(enabled = settingsDetailVisible) {
        showNetworkConfiguration = false
        showStorageManagement = false
        showPowerSettings = false
        showAudioSettings = false
        showDeviceDiagnostics = false
        showAppUpdate = false
    }

    val topBarState = rememberTopAppBarState()
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val topBarDepth by animateDpAsState(
        targetValue = if (topBarState.overlappedFraction > 0.01f) 6.dp else 0.dp,
        animationSpec = tween(220),
        label = "top-bar-depth",
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(
                    Modifier
                        .shadow(topBarDepth)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                    MaterialTheme.colorScheme.surface.copy(
                                        alpha = if (topBarState.overlappedFraction > 0.01f) 0.88f else 0.76f,
                                    ),
                                ),
                            ),
                        ),
                ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer { alpha = brandTitleAlpha },
                        ) {
                            Text(
                                "相念",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "一帧静好，一念长久。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 1.sp,
                            )
                        }
                    },
                    navigationIcon = {
                        if (settingsDetailVisible) {
                            IconButton(onClick = {
                                showNetworkConfiguration = false
                                showStorageManagement = false
                                showPowerSettings = false
                                showAudioSettings = false
                                showDeviceDiagnostics = false
                                showAppUpdate = false
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    scrollBehavior = topBarScrollBehavior,
                )
                if (!settingsDetailVisible) {
                    HeaderStatusBar(snapshot, powerSnapshot)
                    GlobalTaskCapsule(globalTask) { onDestinationSelected(it.destination) }
                }
                }
            },
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            val contentPadding = PaddingValues(
                start = padding.calculateLeftPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateRightPadding(layoutDirection),
                bottom = 104.dp,
            )
            AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (fadeIn(tween(190)) + slideInHorizontally(tween(260)) { direction * it / 18 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -direction * it / 22 })
            },
            label = "main-destination",
        ) { destination ->
            when (destination) {
                AppDestination.LocalAlbum -> LocalAlbumDemoHost(
                contentPadding = contentPadding,
                runtime = localAlbumRuntime,
                modeSwitchState = modeSwitchState,
                onSwitchMode = modeSwitchViewModel::switchTo,
            )
                AppDestination.AiAlbum -> AiAlbumHost(
                device = snapshot,
                modeSwitchState = modeSwitchState,
                onSwitchMode = modeSwitchViewModel::switchTo,
                onOpenNetworkSettings = {
                    showNetworkConfiguration = true
                    onDestinationSelected(AppDestination.Settings)
                },
                aiImageUiState = aiImageState,
                onRefreshAiImages = aiImageViewModel::refresh,
                onLoadMoreAiImages = aiImageViewModel::loadMore,
                onDisplayAiImage = aiImageViewModel::display,
                onDeleteAiImage = aiImageViewModel::delete,
                onDeleteAiImages = aiImageViewModel::deleteMany,
                onSaveAiImageToPhone = aiImageViewModel::saveToPhone,
                onSetAiPlaybackStart = aiImageViewModel::playbackStartUnavailable,
                aiPlayback = aiPlaybackState,
                onRefreshAiPlayback = aiPlaybackViewModel::refresh,
                onSaveAiPlayback = aiPlaybackViewModel::save,
                aiConfigUiState = aiConfigState,
                onRefreshAiConfig = aiConfigViewModel::refresh,
                onSaveAiConfig = { profileId, name, endpoint, model, key, testRequested ->
                    if (testRequested) aiConfigViewModel.saveAndTest(profileId, name, endpoint, model, key)
                    else aiConfigViewModel.save(profileId, name, endpoint, model, key)
                },
                onTestSavedAiConfig = aiConfigViewModel::testSaved,
                onDeleteAiConfig = aiConfigViewModel::deleteActiveProfile,
                onActivateAiModel = aiConfigViewModel::activateProfile,
                aiGenerationUiState = aiGenerationState,
                onGenerateAiImage = aiGenerationViewModel::generate,
                onGeneratePhotoStyle = aiGenerationViewModel::generatePhotoStyle,
                onConfirmAiSave = aiGenerationViewModel::confirmSave,
                onContinueAiHistory = aiGenerationViewModel::continueQuery,
                onRetryAiSubmission = aiGenerationViewModel::retrySubmission,
                onCancelAiWaitingSubmission = aiGenerationViewModel::cancelWaitingSubmission,
                onDiscardAiHistory = aiGenerationViewModel::discardHistory,
                onClearAiHistory = aiGenerationViewModel::clearHistory,
                contentPadding = contentPadding,
            )
                AppDestination.Settings -> NetworkSettingsScreen(
                repository = networkRepository,
                contentPadding = contentPadding,
                showNetworkConfiguration = showNetworkConfiguration,
                onOpenNetworkConfiguration = { showNetworkConfiguration = true },
                showStorageManagement = showStorageManagement,
                onOpenStorageManagement = { showStorageManagement = true },
                storageRepository = storageRepository,
                showPowerSettings = showPowerSettings,
                onOpenPowerSettings = { showPowerSettings = true },
                powerRepository = powerRepository,
                showAudioSettings = showAudioSettings,
                onOpenAudioSettings = { showAudioSettings = true },
                audioRepository = audioRepository,
                showDeviceDiagnostics = showDeviceDiagnostics,
                onOpenDeviceDiagnostics = { showDeviceDiagnostics = true },
                showAppUpdate = showAppUpdate,
                onOpenAppUpdate = { showAppUpdate = true },
                onCloseAppUpdate = { showAppUpdate = false },
                deviceSnapshot = snapshot,
            )
                AppDestination.Dashboard -> InfoDashboardHost(
                contentPadding = contentPadding,
                device = snapshot,
                modeSwitchState = modeSwitchState,
                onSwitchMode = modeSwitchViewModel::switchTo,
            )
            }
            }
        }
        FloatingGlassNavigation(
            selected = selected,
            onSelected = { destination ->
                showNetworkConfiguration = false
                showStorageManagement = false
                showPowerSettings = false
                showAudioSettings = false
                showDeviceDiagnostics = false
                onDestinationSelected(destination)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        AnimatedVisibility(
            visible = globalWarning != null,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .zIndex(20f),
            enter = fadeIn(tween(180)) + scaleIn(tween(260), initialScale = 0.96f),
            exit = fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 0.98f),
        ) {
            Surface(
                color = Color(0xFFD5222A).copy(alpha = 0.90f),
                contentColor = Color.White,
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            ) {
                Text(
                    text = globalWarning.orEmpty(),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

private data class GlobalTaskPresentation(
    val message: String,
    val destination: AppDestination,
)

@Composable
private fun GlobalTaskCapsule(task: GlobalTaskPresentation?, onClick: (GlobalTaskPresentation) -> Unit) {
    AnimatedVisibility(
        visible = task != null,
        enter = fadeIn(tween(180)) + scaleIn(tween(240), initialScale = 0.97f),
        exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.98f),
    ) {
        task?.let { current ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.clickable { onClick(current) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 5.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Text(current.message, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingGlassNavigation(
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.62f)),
            tonalElevation = 0.dp,
        ) {
            BoxWithConstraints(
                Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ),
                        ),
                    )
                    .padding(6.dp)
                    .selectableGroup(),
            ) {
                val itemWidth = maxWidth / AppDestination.entries.size
                val indicatorOffset by animateDpAsState(
                    targetValue = itemWidth * selected.ordinal,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "glass-navigation-indicator",
                )
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .height(58.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
                            RoundedCornerShape(22.dp),
                        ),
                )
                Row(Modifier.fillMaxWidth()) {
                    AppDestination.entries.forEach { destination ->
                        val isSelected = selected == destination
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val itemScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.97f else 1f,
                            animationSpec = if (isPressed) tween(70) else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "${destination.name}-item-scale",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "${destination.name}-icon-scale",
                        )
                        Column(
                            modifier = Modifier
                                .width(itemWidth)
                                .height(58.dp)
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .selectable(
                                    selected = isSelected,
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        if (!isSelected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelected(destination)
                                        }
                                    },
                                    role = Role.Tab,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.icon,
                                contentDescription = destination.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            )
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderStatusBar(snapshot: com.einkphoto.app.core.device.DeviceSnapshot, power: PowerSnapshot) {
    val sleepLabel = sleepStatusText(snapshot.connection, power)
    val sleepActive = power.automaticSleepEnabled || snapshot.connection == DeviceConnectionState.Sleeping
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FrameBatteryIcon(
                percent = power.batteryPercent,
                charging = power.charging,
                full = power.chargerState == "completed",
            )
            Text(
                text = power.batteryPercent?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = if (sleepActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            contentColor = if (sleepActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
        ) {
            Text(
                text = sleepLabel,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            DeviceConnectionBadge(snapshot)
        }
    }
}

private fun sleepStatusText(connection: DeviceConnectionState, power: PowerSnapshot): String = when {
    connection == DeviceConnectionState.Sleeping -> "休眠中"
    connection != DeviceConnectionState.Online && power.automaticSleepEnabled &&
        power.idleSleepAtEpochMillis?.let { System.currentTimeMillis() >= it } == true -> "休眠中"
    connection != DeviceConnectionState.Online && !power.pmicOnline -> "休眠未知"
    !power.automaticSleepEnabled -> "休眠关闭"
    power.automaticSleepState == "busy" -> "任务运行中"
    power.automaticSleepState == "waiting_idle" -> "等待休眠"
    power.automaticSleepState == "ready_to_sleep" -> "即将休眠"
    power.automaticSleepState == "playback_due" -> "轮播唤醒"
    power.automaticSleepState == "rtc_unavailable" -> "RTC 异常"
    else -> "自动休眠"
}

private fun DeviceFeature.destination(): AppDestination = when (this) {
    DeviceFeature.LocalAlbum -> AppDestination.LocalAlbum
    DeviceFeature.AiAlbum -> AppDestination.AiAlbum
    DeviceFeature.InfoDashboard -> AppDestination.Dashboard
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EInkPhotoAppPreview() = EInkPhotoTheme { EInkPhotoAppContent(AppDestination.LocalAlbum) {} }
