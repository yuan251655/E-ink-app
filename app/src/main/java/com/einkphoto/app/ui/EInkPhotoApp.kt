package com.einkphoto.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceSession
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.feature.settings.network.LanNetworkRepository
import com.einkphoto.app.feature.settings.storage.LanStorageRepository
import com.einkphoto.app.feature.settings.power.LanPowerRepository
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
import com.einkphoto.app.ui.components.ModeSwitchGlobalStatus
import kotlinx.coroutines.delay

@Composable
fun EInkPhotoApp() = EInkPhotoTheme {
    var selected by rememberSaveable { mutableStateOf(AppDestination.LocalAlbum) }
    EInkPhotoAppContent(selected) { selected = it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EInkPhotoAppContent(selected: AppDestination, onDestinationSelected: (AppDestination) -> Unit) {
    val context = LocalContext.current
    val transport = remember { HttpLanDeviceTransport() }
    val session = remember(transport) { LanDeviceSession(transport) }
    val snapshot by session.snapshot.collectAsState()
    val localAlbumRuntime = rememberLocalAlbumDemoRuntime(session)
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
    val powerSnapshot by powerRepository.snapshot.collectAsState()
    var showNetworkConfiguration by rememberSaveable { mutableStateOf(false) }
    var showStorageManagement by rememberSaveable { mutableStateOf(false) }
    var showPowerSettings by rememberSaveable { mutableStateOf(false) }
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

    BackHandler(enabled = selected == AppDestination.Settings && (showNetworkConfiguration || showStorageManagement || showPowerSettings || showDeviceDiagnostics)) {
        showNetworkConfiguration = false
        showStorageManagement = false
        showPowerSettings = false
        showDeviceDiagnostics = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (snapshot.connection == DeviceConnectionState.Online && powerSnapshot.batteryPresent) {
                                FrameBatteryIcon(
                                    percent = powerSnapshot.batteryPercent,
                                    charging = powerSnapshot.charging,
                                    full = powerSnapshot.chargerState == "completed",
                                )
                                Text(
                                    text = powerSnapshot.batteryPercent?.let { "$it%" } ?: "--",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 4.dp, end = 10.dp),
                                )
                            }
                            Text("墨水屏相册", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    navigationIcon = {
                        if (selected == AppDestination.Settings && (showNetworkConfiguration || showStorageManagement || showPowerSettings || showDeviceDiagnostics)) {
                            IconButton(onClick = {
                                showNetworkConfiguration = false
                                showStorageManagement = false
                                showPowerSettings = false
                                showDeviceDiagnostics = false
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings")
                            }
                        }
                    },
                    actions = { DeviceConnectionBadge(snapshot) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    ),
                )
                ModeSwitchGlobalStatus(modeSwitchState)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
            ) {
                AppDestination.entries.forEach { destination ->
                    val isSelected = selected == destination
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "${destination.name}-icon-scale",
                    )
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            // A bottom-tab selection always enters the settings home.
                            // This prevents a previously opened sub-page from being
                            // restored unexpectedly after changing tabs or restarting.
                            showNetworkConfiguration = false
                            showStorageManagement = false
                            showPowerSettings = false
                            showDeviceDiagnostics = false
                            onDestinationSelected(destination)
                        },
                        icon = {
                            Icon(
                                if (isSelected) destination.selectedIcon else destination.icon,
                                contentDescription = destination.title,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            )
                        },
                        label = { Text(destination.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (fadeIn(tween(180)) + slideInHorizontally(tween(320)) { direction * it / 10 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { -direction * it / 12 })
            },
            label = "main-destination",
        ) { destination ->
            when (destination) {
                AppDestination.LocalAlbum -> LocalAlbumDemoHost(
                contentPadding = padding,
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
                contentPadding = padding,
            )
                AppDestination.Settings -> NetworkSettingsScreen(
                repository = networkRepository,
                contentPadding = padding,
                showNetworkConfiguration = showNetworkConfiguration,
                onOpenNetworkConfiguration = { showNetworkConfiguration = true },
                showStorageManagement = showStorageManagement,
                onOpenStorageManagement = { showStorageManagement = true },
                storageRepository = storageRepository,
                showPowerSettings = showPowerSettings,
                onOpenPowerSettings = { showPowerSettings = true },
                powerRepository = powerRepository,
                showDeviceDiagnostics = showDeviceDiagnostics,
                onOpenDeviceDiagnostics = { showDeviceDiagnostics = true },
                showAppUpdate = showAppUpdate,
                onOpenAppUpdate = { showAppUpdate = true },
                onCloseAppUpdate = { showAppUpdate = false },
                deviceSnapshot = snapshot,
            )
                AppDestination.Dashboard -> InfoDashboardHost(
                contentPadding = padding,
                device = snapshot,
                modeSwitchState = modeSwitchState,
                onSwitchMode = modeSwitchViewModel::switchTo,
            )
            }
        }
    }
}

private fun DeviceFeature.destination(): AppDestination = when (this) {
    DeviceFeature.LocalAlbum -> AppDestination.LocalAlbum
    DeviceFeature.AiAlbum -> AppDestination.AiAlbum
    DeviceFeature.InfoDashboard -> AppDestination.Dashboard
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EInkPhotoAppPreview() = EInkPhotoTheme { EInkPhotoAppContent(AppDestination.LocalAlbum) {} }
