package com.einkphoto.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceSession
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.feature.settings.network.LanNetworkRepository
import com.einkphoto.app.feature.settings.storage.LanStorageRepository
import com.einkphoto.app.ui.components.DeviceConnectionBadge
import com.einkphoto.app.ui.localalbum.LocalAlbumDemoHost
import com.einkphoto.app.ui.localalbum.rememberLocalAlbumDemoRuntime
import com.einkphoto.app.ui.aialbum.AiAlbumHost
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
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AiConfigViewModel(AiConfigRepository()) as T
            }
        },
    )
    val aiConfigState by aiConfigViewModel.state.collectAsState()
    val aiGenerationViewModel: AiGenerationViewModel = viewModel(
        key = "ai-image-generation",
        factory = remember(aiImageViewModel) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AiGenerationViewModel(onCompleted = aiImageViewModel::refresh) as T
            }
        },
    )
    val aiGenerationState by aiGenerationViewModel.state.collectAsState()
    val networkRepository = remember { LanNetworkRepository() }
    val storageRepository = remember { LanStorageRepository() }
    var showNetworkConfiguration by rememberSaveable { mutableStateOf(false) }
    var showStorageManagement by rememberSaveable { mutableStateOf(false) }
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

    BackHandler(enabled = selected == AppDestination.Settings && (showNetworkConfiguration || showStorageManagement || showDeviceDiagnostics)) {
        showNetworkConfiguration = false
        showStorageManagement = false
        showDeviceDiagnostics = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("墨水屏相册") },
                    navigationIcon = {
                        if (selected == AppDestination.Settings && (showNetworkConfiguration || showStorageManagement || showDeviceDiagnostics)) {
                            IconButton(onClick = {
                                showNetworkConfiguration = false
                                showStorageManagement = false
                                showDeviceDiagnostics = false
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings")
                            }
                        }
                    },
                    actions = { DeviceConnectionBadge(snapshot) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                ModeSwitchGlobalStatus(modeSwitchState)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = {
                            // A bottom-tab selection always enters the settings home.
                            // This prevents a previously opened sub-page from being
                            // restored unexpectedly after changing tabs or restarting.
                            showNetworkConfiguration = false
                            showStorageManagement = false
                            showDeviceDiagnostics = false
                            onDestinationSelected(destination)
                        },
                        icon = { androidx.compose.material3.Icon(if (selected == destination) destination.selectedIcon else destination.icon, null) },
                        label = { Text(destination.title) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
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
                aiConfigUiState = aiConfigState,
                onRefreshAiConfig = aiConfigViewModel::refresh,
                onSaveAiConfig = { endpoint, model, key, testRequested ->
                    if (testRequested) aiConfigViewModel.saveAndTest(endpoint, model, key)
                    else aiConfigViewModel.save(endpoint, model, key)
                },
                onTestSavedAiConfig = aiConfigViewModel::testSaved,
                onDeleteAiConfig = aiConfigViewModel::clear,
                aiGenerationUiState = aiGenerationState,
                onGenerateAiImage = aiGenerationViewModel::generate,
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
                showDeviceDiagnostics = showDeviceDiagnostics,
                onOpenDeviceDiagnostics = { showDeviceDiagnostics = true },
                showAppUpdate = showAppUpdate,
                onOpenAppUpdate = { showAppUpdate = true },
                onCloseAppUpdate = { showAppUpdate = false },
                deviceSnapshot = snapshot,
            )
            else -> FeaturePlaceholderScreen(
                destination = selected,
                contentPadding = padding,
                device = snapshot,
                modeSwitchState = modeSwitchState,
                onSwitchMode = modeSwitchViewModel::switchTo,
            )
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
