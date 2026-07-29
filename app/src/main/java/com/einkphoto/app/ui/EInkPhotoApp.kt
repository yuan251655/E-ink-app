package com.einkphoto.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.tooling.preview.Preview
import com.einkphoto.app.core.device.HttpLanDeviceTransport
import com.einkphoto.app.core.device.LanDeviceSession
import com.einkphoto.app.feature.settings.network.LanNetworkRepository
import com.einkphoto.app.feature.settings.storage.LanStorageRepository
import com.einkphoto.app.ui.components.DeviceConnectionBadge
import com.einkphoto.app.ui.localalbum.LocalAlbumDemoHost
import com.einkphoto.app.ui.localalbum.rememberLocalAlbumDemoRuntime
import com.einkphoto.app.ui.model.AppDestination
import com.einkphoto.app.ui.screens.FeaturePlaceholderScreen
import com.einkphoto.app.ui.settings.NetworkSettingsScreen
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import kotlinx.coroutines.delay

@Composable
fun EInkPhotoApp() = EInkPhotoTheme {
    var selected by rememberSaveable { mutableStateOf(AppDestination.LocalAlbum) }
    EInkPhotoAppContent(selected) { selected = it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EInkPhotoAppContent(selected: AppDestination, onDestinationSelected: (AppDestination) -> Unit) {
    val session = remember { LanDeviceSession(HttpLanDeviceTransport()) }
    val snapshot by session.snapshot.collectAsState()
    val localAlbumRuntime = rememberLocalAlbumDemoRuntime(session)
    val networkRepository = remember { LanNetworkRepository() }
    val storageRepository = remember { LanStorageRepository() }
    var showNetworkConfiguration by rememberSaveable { mutableStateOf(false) }
    var showStorageManagement by rememberSaveable { mutableStateOf(false) }
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

    BackHandler(enabled = selected == AppDestination.Settings && (showNetworkConfiguration || showStorageManagement)) {
        showNetworkConfiguration = false
        showStorageManagement = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("墨水屏相册") },
                navigationIcon = {
                    if (selected == AppDestination.Settings && (showNetworkConfiguration || showStorageManagement)) {
                        IconButton(onClick = {
                            showNetworkConfiguration = false
                            showStorageManagement = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings")
                        }
                    }
                },
                actions = { DeviceConnectionBadge(snapshot) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = {
                            if (destination != AppDestination.Settings) {
                                showNetworkConfiguration = false
                                showStorageManagement = false
                            }
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
            AppDestination.LocalAlbum -> LocalAlbumDemoHost(contentPadding = padding, runtime = localAlbumRuntime)
            AppDestination.Settings -> NetworkSettingsScreen(
                repository = networkRepository,
                contentPadding = padding,
                showNetworkConfiguration = showNetworkConfiguration,
                onOpenNetworkConfiguration = { showNetworkConfiguration = true },
                showStorageManagement = showStorageManagement,
                onOpenStorageManagement = { showStorageManagement = true },
                storageRepository = storageRepository,
            )
            else -> FeaturePlaceholderScreen(destination = selected, contentPadding = padding)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun EInkPhotoAppPreview() = EInkPhotoTheme { EInkPhotoAppContent(AppDestination.LocalAlbum) {} }
