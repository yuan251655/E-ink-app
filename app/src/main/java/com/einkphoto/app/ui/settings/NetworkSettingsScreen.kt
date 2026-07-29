package com.einkphoto.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import com.einkphoto.app.ui.components.pressFeedbackClickable
import kotlinx.coroutines.launch

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
) {
    if (showNetworkConfiguration) NetworkConfigurationPage(repository, contentPadding)
    else if (showStorageManagement) StorageManagementPage(storageRepository, contentPadding)
    else SettingsHome(repository, contentPadding, onOpenNetworkConfiguration, onOpenStorageManagement)
}

@Composable
private fun SettingsHome(
    repository: NetworkRepository,
    contentPadding: PaddingValues,
    onOpenNetwork: () -> Unit,
    onOpenStorageManagement: () -> Unit,
) {
    val state by repository.snapshot.collectAsState()
    LaunchedEffect(repository) { repository.refresh() }
    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Text("设备管理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth().pressFeedbackClickable(onClick = onOpenNetwork)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("网络配置", style = MaterialTheme.typography.titleMedium)
                Text("管理相框热点与家庭 Wi-Fi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${if (state.deviceId == "unknown") "相框暂未连接" else "相框已连接"}  ·  ${if (state.sta.state == StaState.Connected) "Wi-Fi 已连接" else "Wi-Fi 未连接"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.sta.ssid?.let { Text("${it}${state.sta.ip?.let { ip -> "  ·  $ip" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Card(Modifier.fillMaxWidth().pressFeedbackClickable(onClick = onOpenStorageManagement)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TF 卡管理", style = MaterialTheme.typography.titleMedium)
                    Text("查看存储状态、空间信息和设备维护", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("后续将在这里增加电源、轮播和系统设置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StorageManagementPage(repository: StorageRepository, contentPadding: PaddingValues) {
    val state by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var rechecking by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) { repository.refresh() }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        }

        Text("存储空间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            val totalBytes = state.totalBytes
            val freeBytes = state.freeBytes
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (totalBytes != null) "已使用 ${formatBytes((totalBytes - (freeBytes ?: 0L)).coerceAtLeast(0L))} / ${formatBytes(totalBytes)}" else "容量信息暂不可用", style = MaterialTheme.typography.titleMedium)
                Text("剩余空间 ${freeBytes?.let(::formatBytes) ?: "--"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("由设备端 TF 服务实时统计", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("内容概览", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StorageOverviewRow(Icons.Outlined.FolderOpen, "本地相册", storageUsageDescription(state.localAlbumItemCount, state.localAlbumUsageBytes, "已保存照片"))
                StorageOverviewRow(Icons.Outlined.Storage, "临时文件", storageUsageDescription(state.stagingItemCount, state.stagingUsageBytes, "未完成上传的临时文件"))
            }
        }

        Text("设备维护", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                    modifier = Modifier.fillMaxWidth(),
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
                ) { Text(if (rechecking) "正在检测…" else "重新检测") }
            }
        }

        Text("最近状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
    var editAp by remember { mutableStateOf(false) }; var confirmRestore by remember { mutableStateOf(false) }; var confirmForget by remember { mutableStateOf(false) }
    LaunchedEffect(repository) { repository.refresh() }
    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("网络配置", style = MaterialTheme.typography.headlineSmall)
        ConnectionSummary(state)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("相框热点（AP）", style = MaterialTheme.typography.titleMedium)
            Text("${state.ap.ssid}  ·  ${state.ap.ip}")
            Text("信道 ${state.ap.channel}  ·  已连接设备 ${state.ap.clientCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { editAp = true }, modifier = Modifier.fillMaxWidth()) { Text("修改热点名称和密码") }
            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("恢复默认热点") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("家庭 Wi-Fi（STA）", style = MaterialTheme.typography.titleMedium)
            Text(staDescription(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.sta.ip != null) Text("IP：${state.sta.ip}    网关：${state.sta.gateway ?: "—"}")
            Button(enabled = !scanning, onClick = { scope.launch { scanning = true; message = null; repository.scan24Ghz().onSuccess { networks = it }.onFailure { message = "扫描失败，请确认相框在线后重试" }; scanning = false } }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "正在扫描…" else "扫描 2.4 GHz Wi-Fi") }
            networks.forEach { network -> Row(Modifier.fillMaxWidth().pressFeedbackClickable { staDialog = network.ssid }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(network.ssid); Text("信号 ${network.rssiDbm} dBm  ·  信道 ${network.channel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(network.security.name.uppercase()) } }
            OutlinedButton(onClick = { staDialog = "" }, modifier = Modifier.fillMaxWidth()) { Text("其他网络") }
            if (state.sta.state != StaState.Disabled || state.sta.ssid != null) OutlinedButton(onClick = { confirmForget = true }, modifier = Modifier.fillMaxWidth()) { Text("忘记已保存的 Wi-Fi") }
        } }
        Text("AP 网页配网会一直保留，无法通过 App 连接时仍可连接相框热点后访问 192.168.4.1。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { scope.launch { repository.refresh() } }, modifier = Modifier.fillMaxWidth()) { Text("刷新状态") }
    }
    if (editAp) ApDialog(state, { editAp = false }) { draft -> scope.launch { message = resultMessage(repository.saveAp(draft), "热点设置已保存。若手机通过该热点连接，请重新连接新热点。"); editAp = false } }
    staDialog?.let { initial -> StaDialog(initial, { staDialog = null }) { draft -> scope.launch { message = resultMessage(repository.testAndSaveSta(draft), "Wi-Fi 已连接并保存"); staDialog = null } } }
    if (confirmRestore) ConfirmDialog("恢复默认热点？", "将恢复 esp_network。已连接该热点的手机需要重新连接。", { confirmRestore = false }) { scope.launch { message = resultMessage(repository.restoreDefaultAp(), "已恢复默认热点"); confirmRestore = false } }
    if (confirmForget) ConfirmDialog("忘记 Wi-Fi？", "只清除相框保存的 STA 配置，不影响热点和本地相册。", { confirmForget = false }) { scope.launch { message = resultMessage(repository.disableSta(), "已清除已保存的 Wi-Fi"); confirmForget = false } }
}

@Composable private fun ConnectionSummary(state: NetworkSnapshot) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(if (state.deviceId == "unknown") "相框未连接" else "相框已连接", style = MaterialTheme.typography.titleMedium); Text("AP、STA 和互联网是三个独立状态", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun staDescription(state: NetworkSnapshot) = when (state.sta.state) { StaState.Connected -> "已连接 ${state.sta.ssid ?: "Wi-Fi"}"; StaState.Connecting -> "正在测试连接，原有配置不会被覆盖"; StaState.Failed -> "连接失败：${state.sta.errorCode ?: "请检查密码或 2.4 GHz 信号"}"; StaState.Disabled -> "尚未配置" }
private fun resultMessage(result: NetworkActionResult, success: String) = if (result is NetworkActionResult.Accepted) success else (result as NetworkActionResult.Rejected).message

@Composable private fun ApDialog(state: NetworkSnapshot, onDismiss: () -> Unit, onSave: (ApConfigDraft) -> Unit) { var ssid by remember { mutableStateOf(state.ap.ssid) }; var password by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("修改相框热点") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("请输入新的密码。密码不会在 App 中回显或保存。"); OutlinedTextField(ssid, { ssid = it }, label = { Text("热点名称") }); OutlinedTextField(password, { password = it }, label = { Text("新密码") }, visualTransformation = PasswordVisualTransformation()) } }, confirmButton = { TextButton(onClick = { onSave(ApConfigDraft(ssid, password)) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }
@Composable private fun StaDialog(initialSsid: String, onDismiss: () -> Unit, onSave: (StaConfigDraft) -> Unit) { var ssid by remember { mutableStateOf(initialSsid) }; var password by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("连接 2.4 GHz Wi-Fi") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (initialSsid.isEmpty()) OutlinedTextField(ssid, { ssid = it }, label = { Text("Wi-Fi 名称") }); OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation()); Text("相框会先测试连接；失败时保留原有网络配置。", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { onSave(StaConfigDraft(ssid, password)) }) { Text("测试并保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }
@Composable private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
