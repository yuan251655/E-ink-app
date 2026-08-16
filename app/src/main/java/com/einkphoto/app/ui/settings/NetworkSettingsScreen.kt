package com.einkphoto.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.einkphoto.app.ui.components.AppleAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.einkphoto.app.feature.settings.network.ApConfigDraft
import com.einkphoto.app.feature.settings.network.NetworkActionResult
import com.einkphoto.app.feature.settings.network.NetworkRepository
import com.einkphoto.app.feature.settings.network.NetworkSnapshot
import com.einkphoto.app.feature.settings.network.StaConfigDraft
import com.einkphoto.app.feature.settings.network.StaState
import com.einkphoto.app.feature.settings.network.WifiNetwork
import com.einkphoto.app.feature.settings.storage.StorageActionResult
import com.einkphoto.app.feature.settings.storage.StorageHealth
import com.einkphoto.app.feature.settings.storage.StorageRepository
import com.einkphoto.app.feature.settings.storage.StorageSnapshot
import com.einkphoto.app.feature.settings.diagnostics.LanDeviceLogRepository
import com.einkphoto.app.feature.settings.power.PowerRepository
import com.einkphoto.app.feature.settings.audio.AudioRepository
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.components.hierarchicalPageTransition
import com.einkphoto.app.ui.components.AsyncButtonContent
import com.einkphoto.app.feature.aialbum.VoiceGenerationServiceController
import kotlinx.coroutines.launch

private enum class SettingsPage { Home, Network, Storage, Power, Audio, Diagnostics, Update }

/** Settings home keeps future features as entries; network configuration is the first complete sub-page. */
@Composable
fun NetworkSettingsScreen(
    repository: NetworkRepository,
    contentPadding: PaddingValues,
    showNetworkConfiguration: Boolean,
    onOpenNetworkConfiguration: () -> Unit,
    showStorageManagement: Boolean,
    onOpenStorageManagement: () -> Unit,
    storageRepository: StorageRepository,
    showPowerSettings: Boolean,
    onOpenPowerSettings: () -> Unit,
    powerRepository: PowerRepository,
    showAudioSettings: Boolean,
    onOpenAudioSettings: () -> Unit,
    audioRepository: AudioRepository,
    showDeviceDiagnostics: Boolean,
    onOpenDeviceDiagnostics: () -> Unit,
    showAppUpdate: Boolean,
    onOpenAppUpdate: () -> Unit,
    onCloseAppUpdate: () -> Unit,
    deviceSnapshot: DeviceSnapshot,
) {
    val page = when {
        showNetworkConfiguration -> SettingsPage.Network
        showStorageManagement -> SettingsPage.Storage
        showPowerSettings -> SettingsPage.Power
        showAudioSettings -> SettingsPage.Audio
        showDeviceDiagnostics -> SettingsPage.Diagnostics
        showAppUpdate -> SettingsPage.Update
        else -> SettingsPage.Home
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            hierarchicalPageTransition(initialState == SettingsPage.Home && targetState != SettingsPage.Home)
        },
        label = "settings-page",
    ) { displayedPage ->
        when (displayedPage) {
            SettingsPage.Network -> NetworkConfigurationPage(repository, contentPadding)
            SettingsPage.Storage -> StorageManagementPage(storageRepository, contentPadding)
            SettingsPage.Power -> PowerSettingsScreen(powerRepository, contentPadding)
            SettingsPage.Audio -> AudioSettingsScreen(audioRepository, contentPadding)
            SettingsPage.Diagnostics -> DeviceDiagnosticsPage(repository, storageRepository, deviceSnapshot, contentPadding)
            SettingsPage.Update -> AppUpdateScreen(onCloseAppUpdate, contentPadding)
            SettingsPage.Home -> SettingsHome(repository, contentPadding, onOpenNetworkConfiguration, onOpenStorageManagement, onOpenPowerSettings, onOpenAudioSettings, onOpenDeviceDiagnostics, onOpenAppUpdate)
        }
    }
}

@Composable
private fun SettingsHome(
    repository: NetworkRepository,
    contentPadding: PaddingValues,
    onOpenNetwork: () -> Unit,
    onOpenStorageManagement: () -> Unit,
    onOpenPowerSettings: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenDeviceDiagnostics: () -> Unit,
    onOpenAppUpdate: () -> Unit,
) {
    val state by repository.snapshot.collectAsState()
    val context = LocalContext.current
    var voiceServiceEnabled by remember { mutableStateOf(VoiceGenerationServiceController.isEnabled(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            VoiceGenerationServiceController.setEnabled(context, true)
            voiceServiceEnabled = true
        }
    }
    LaunchedEffect(repository) { repository.refresh() }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineLarge)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("相框", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                SettingsLinkRow(
                    title = "网络配置",
                    subtitle = "管理相框热点与家庭 Wi-Fi",
                    status = "${if (state.deviceId == "unknown") "相框暂未连接" else "相框已连接"} · ${if (state.sta.state == StaState.Connected) "Wi-Fi 已连接" else "Wi-Fi 未连接"}",
                    onClick = onOpenNetwork,
                )
                SettingsDivider()
                SettingsLinkRow("电池、电源与休眠", "查看主电池、USB 充电和休眠设置", onClick = onOpenPowerSettings)
                SettingsDivider()
                SettingsLinkRow("音频与语音", "调节相框音量、静音和扬声器测试", onClick = onOpenAudioSettings)
                SettingsDivider()
                SettingsLinkRow("TF 卡管理", "查看存储状态、空间信息和设备维护", onClick = onOpenStorageManagement)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("智能服务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("语音生图服务", style = MaterialTheme.typography.titleMedium)
                        Text("允许小智确认后由手机后台生成", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = voiceServiceEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                VoiceGenerationServiceController.setEnabled(context, false)
                                voiceServiceEnabled = false
                            } else if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                VoiceGenerationServiceController.setEnabled(context, true)
                                voiceServiceEnabled = true
                            }
                        },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App 与维护", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                SettingsLinkRow("设备诊断", "查看设备、网络、TF 卡和显示状态", onClick = onOpenDeviceDiagnostics)
                SettingsDivider()
                SettingsLinkRow("应用更新", "检查并安装最新版本的相念 App", onClick = onOpenAppUpdate)
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    subtitle: String,
    status: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).pressFeedbackClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(
    modifier = Modifier.padding(start = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
private fun DeviceDiagnosticsPage(
    networkRepository: NetworkRepository,
    storageRepository: StorageRepository,
    deviceSnapshot: DeviceSnapshot,
    contentPadding: PaddingValues,
) {
    val network by networkRepository.snapshot.collectAsState()
    val storage by storageRepository.snapshot.collectAsState()
    val logRepository = remember { LanDeviceLogRepository() }
    val logs by logRepository.entries.collectAsState()
    LaunchedEffect(networkRepository, storageRepository) {
        networkRepository.refresh()
        storageRepository.refresh()
        logRepository.refresh()
    }
    DeviceDiagnosticsScreen(deviceSnapshot, network, storage, logs, contentPadding)
}

@Composable
private fun StorageManagementPage(repository: StorageRepository, contentPadding: PaddingValues) {
    val state by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var rechecking by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) { repository.refresh() }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("TF 卡管理", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TF 卡状态", style = MaterialTheme.typography.titleMedium)
                    Text(storageHealthTitle(state.health), color = storageHealthColor(state.health))
                    Text(storageHealthDetail(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val totalBytes = state.totalBytes
            val freeBytes = state.freeBytes
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("存储空间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (totalBytes != null) "已使用 ${formatBytes((totalBytes - (freeBytes ?: 0L)).coerceAtLeast(0L))} / ${formatBytes(totalBytes)}" else "容量信息暂不可用", style = MaterialTheme.typography.titleMedium)
                Text("剩余空间 ${freeBytes?.let(::formatBytes) ?: "--"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("内容概览", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            StorageOverviewRow(Icons.Outlined.FolderOpen, "本地相册", storageUsageDescription(state.localAlbumItemCount, state.localAlbumUsageBytes, "已保存照片"))
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
            StorageOverviewRow(Icons.Outlined.Storage, "临时文件", storageUsageDescription(state.stagingItemCount, state.stagingUsageBytes, "未完成上传的临时文件"))
        }

        Text("设备维护", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("重新检测 TF 卡", style = MaterialTheme.typography.titleMedium)
                        Text("仅在没有上传或显示读取任务时执行", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = !rechecking,
                    onClick = {
                        scope.launch {
                            rechecking = true
                            actionMessage = when (val result = repository.remount()) {
                                StorageActionResult.Accepted -> "TF 卡已重新检测"
                                is StorageActionResult.Rejected -> result.message
                            }
                            rechecking = false
                        }
                    },
                ) { AsyncButtonContent(rechecking, "重新检测", "正在检测…") }
            }
        }

        Text("最近状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.lastErrorCode?.let { "最近异常：$it" } ?: "暂无异常", style = MaterialTheme.typography.titleMedium)
                    Text(state.lastErrorMessage ?: "设备返回的 TF 卡状态正常", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.lastCheckAgeSeconds?.let { Text("最近检测：${ageDescription(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        actionMessage?.let { Text(it, color = if (it == "TF 卡已重新检测") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun StorageOverviewRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun storageHealthTitle(health: StorageHealth): String = when (health) {
    StorageHealth.Ready -> "正常可用"
    StorageHealth.Degraded -> "可用，但存在异常"
    StorageHealth.Missing -> "未检测到 TF 卡"
    StorageHealth.ErrorBackoff -> "暂时不可用"
    StorageHealth.Unknown -> "等待读取设备状态"
}

@Composable
private fun storageHealthColor(health: StorageHealth) = when (health) {
    StorageHealth.Ready -> MaterialTheme.colorScheme.primary
    StorageHealth.Degraded, StorageHealth.Missing, StorageHealth.ErrorBackoff -> MaterialTheme.colorScheme.error
    StorageHealth.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun storageHealthDetail(state: StorageSnapshot): String = when {
    state.health == StorageHealth.Unknown -> "连接相框后将显示挂载、读取和写入状态"
    !state.mounted -> "TF 卡未挂载"
    state.readable && state.writable -> "已挂载，可读取和写入"
    state.readable -> "已挂载，只读"
    else -> "已挂载，但当前无法读取"
}

private fun storageUsageDescription(count: Int?, bytes: Long?, suffix: String): String =
    if (count == null && bytes == null) "信息暂不可用" else "${count ?: 0} 项 · ${bytes?.let(::formatBytes) ?: "--"} · $suffix"

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun ageDescription(seconds: Long): String = when {
    seconds < 10L -> "刚刚"
    seconds < 60L -> "$seconds 秒前"
    seconds < 3600L -> "${seconds / 60} 分钟前"
    else -> "${seconds / 3600} 小时前"
}

@Composable
private fun NetworkConfigurationPage(repository: NetworkRepository, contentPadding: PaddingValues) {
    val state by repository.snapshot.collectAsState(); val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }; var networks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }; var staDialog by remember { mutableStateOf<String?>(null) }
    var testingSta by remember { mutableStateOf(false) }
    var editAp by remember { mutableStateOf(false) }; var confirmRestore by remember { mutableStateOf(false) }
    var showApSettings by remember { mutableStateOf(false) }; var confirmActivate by remember { mutableStateOf<String?>(null) }; var confirmDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) { repository.refresh() }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("网络配置", style = MaterialTheme.typography.headlineSmall)
        ConnectionSummary(state)
        Text("已保存的 Wi-Fi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SavedNetworksPanel(
            networks = state.savedNetworks,
            onActivate = { confirmActivate = it },
            onDelete = { confirmDelete = it },
        )
        Button(enabled = !scanning, onClick = {
            scope.launch {
                scanning = true; message = null
                repository.scan24Ghz().onSuccess { networks = it }.onFailure { message = "扫描失败，请确认相框在线后重试" }
                scanning = false
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { AsyncButtonContent(scanning, "添加 Wi-Fi", "正在查找 2.4 GHz Wi-Fi…") }
        if (networks.isNotEmpty()) {
            Text("附近的 2.4 GHz Wi-Fi", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth()) {
                Column {
                    networks.forEachIndexed { index, network ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).pressFeedbackClickable { staDialog = network.ssid }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(network.ssid, style = MaterialTheme.typography.titleMedium)
                                Text("信号 ${network.rssiDbm} dBm · 信道 ${network.channel} · ${network.security.name.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("连接", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { staDialog = "" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("手动添加其他 Wi-Fi") }
        Text("恢复与高级设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).pressFeedbackClickable { showApSettings = !showApSettings }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("相框热点", style = MaterialTheme.typography.titleMedium)
                        Text("${state.ap.ssid} · ${state.ap.ip}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (showApSettings) "收起" else "管理", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                if (showApSettings) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("热点始终保留，用于失联恢复。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { editAp = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("修改热点名称和密码") }
                        TextButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("恢复默认热点") }
                    }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { scope.launch { repository.refresh() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("刷新网络状态") }
    }
    if (editAp) ApDialog(state, { editAp = false }) { draft -> scope.launch { message = resultMessage(repository.saveAp(draft), "热点设置已保存。若手机通过该热点连接，请重新连接新热点。"); editAp = false } }
    staDialog?.let { initial -> StaDialog(initial, testingSta, { if (!testingSta) staDialog = null }) { draft ->
        if (!testingSta) scope.launch {
            testingSta = true
            message = null
            message = resultMessage(repository.testAndSaveSta(draft), "Wi-Fi 已连接并保存")
            testingSta = false
            staDialog = null
        }
    } }
    if (confirmRestore) ConfirmDialog("恢复默认热点？", "将恢复 esp_network。已连接该热点的手机需要重新连接。", { confirmRestore = false }) { scope.launch { message = resultMessage(repository.restoreDefaultAp(), "已恢复默认热点"); confirmRestore = false } }
    confirmActivate?.let { ssid -> ConfirmDialog("切换到 $ssid？", "相框会测试并切换网络，最多等待 12 秒。相框热点会一直保留。", { confirmActivate = null }) { scope.launch { message = resultMessage(repository.activateSavedSta(ssid), "已切换到 $ssid"); confirmActivate = null } } }
    confirmDelete?.let { ssid -> ConfirmDialog("删除 $ssid？", if (state.savedNetworks.firstOrNull { it.ssid == ssid }?.active == true) "这是当前 Wi-Fi。删除后相框将停止使用它，但相框热点仍可连接。" else "删除后需要重新输入密码才能连接此 Wi-Fi。", { confirmDelete = null }) { scope.launch { message = resultMessage(repository.forgetSavedSta(ssid), "已删除 $ssid"); confirmDelete = null } } }
}

@Composable private fun ConnectionSummary(state: NetworkSnapshot) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(if (state.deviceId == "unknown") "相框未连接" else "相框可控制", style = MaterialTheme.typography.titleMedium); Text(when (state.sta.state) { StaState.Connected -> "家庭 Wi-Fi：${state.sta.ssid ?: "已连接"}"; StaState.Connecting -> "正在切换家庭 Wi-Fi…"; StaState.Failed -> "家庭 Wi-Fi 未连接，可继续使用相框热点"; StaState.Disabled -> "未设置家庭 Wi-Fi，可继续使用相框热点" }, color = MaterialTheme.colorScheme.onSurfaceVariant); state.sta.ip?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) } } }
@Composable private fun SavedNetworksPanel(networks: List<com.einkphoto.app.feature.settings.network.SavedWifiNetwork>, onActivate: (String) -> Unit, onDelete: (String) -> Unit) = Card(Modifier.fillMaxWidth()) { if (networks.isEmpty()) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("还没有保存 Wi-Fi", style = MaterialTheme.typography.titleMedium); Text("添加后，相框可连接家庭网络并使用天气和小智。", color = MaterialTheme.colorScheme.onSurfaceVariant) } else Column { networks.forEachIndexed { index, network -> if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant); Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(network.ssid, style = MaterialTheme.typography.titleMedium); Text(if (network.active) "正在使用" else "已保存", style = MaterialTheme.typography.bodySmall, color = if (network.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; if (!network.active) TextButton(onClick = { onActivate(network.ssid) }) { Text("切换") }; TextButton(onClick = { onDelete(network.ssid) }) { Text("删除") } } } } }
private fun resultMessage(result: NetworkActionResult, success: String) = if (result is NetworkActionResult.Accepted) success else (result as NetworkActionResult.Rejected).message

@Composable private fun ApDialog(state: NetworkSnapshot, onDismiss: () -> Unit, onSave: (ApConfigDraft) -> Unit) { var ssid by remember { mutableStateOf(state.ap.ssid) }; var password by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("修改相框热点") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("请输入新的密码。密码不会在 App 中回显或保存。"); OutlinedTextField(ssid, { ssid = it }, label = { Text("热点名称") }); OutlinedTextField(password, { password = it }, label = { Text("新密码") }, visualTransformation = PasswordVisualTransformation()) } }, confirmButton = { TextButton(onClick = { onSave(ApConfigDraft(ssid, password)) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }
@Composable private fun StaDialog(initialSsid: String, testing: Boolean, onDismiss: () -> Unit, onSave: (StaConfigDraft) -> Unit) { var ssid by remember { mutableStateOf(initialSsid) }; var password by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("连接 2.4 GHz Wi-Fi") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (initialSsid.isEmpty()) OutlinedTextField(ssid, { ssid = it }, enabled = !testing, label = { Text("Wi-Fi 名称") }); OutlinedTextField(password, { password = it }, enabled = !testing, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation()); Text(if (testing) "正在测试连接，最多等待 12 秒；请勿退出此页面。" else "相框会先测试连接；失败时保留原有网络配置。", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(enabled = !testing, onClick = { onSave(StaConfigDraft(ssid, password)) }) { Text(if (testing) "正在测试…" else "测试并保存") } }, dismissButton = { TextButton(enabled = !testing, onClick = onDismiss) { Text("取消") } }) }
@Composable private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
