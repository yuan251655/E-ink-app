package com.einkphoto.app.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.settings.network.ApConfigDraft
import com.einkphoto.app.feature.settings.network.NetworkActionResult
import com.einkphoto.app.feature.settings.network.NetworkRepository
import com.einkphoto.app.feature.settings.network.NetworkSnapshot
import com.einkphoto.app.feature.settings.network.StaConfigDraft
import com.einkphoto.app.feature.settings.network.StaState
import com.einkphoto.app.feature.settings.network.WifiNetwork
import kotlinx.coroutines.launch

/** Settings home keeps future features as entries; network configuration is the first complete sub-page. */
@Composable
fun NetworkSettingsScreen(
    repository: NetworkRepository,
    contentPadding: PaddingValues,
    showNetworkConfiguration: Boolean,
    onOpenNetworkConfiguration: () -> Unit,
) {
    if (showNetworkConfiguration) NetworkConfigurationPage(repository, contentPadding)
    else SettingsHome(repository, contentPadding, onOpenNetworkConfiguration)
}

@Composable
private fun SettingsHome(repository: NetworkRepository, contentPadding: PaddingValues, onOpenNetwork: () -> Unit) {
    val state by repository.snapshot.collectAsState()
    LaunchedEffect(repository) { repository.refresh() }
    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Text("设备管理", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenNetwork)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("网络配置", style = MaterialTheme.typography.titleMedium)
                Text("管理相框热点与家庭 Wi-Fi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${if (state.deviceId == "unknown") "相框暂未连接" else "相框已连接"}  ·  ${if (state.sta.state == StaState.Connected) "Wi-Fi 已连接" else "Wi-Fi 未连接"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.sta.ssid?.let { Text("${it}${state.sta.ip?.let { ip -> "  ·  $ip" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text("后续将在这里增加电源、轮播和系统设置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
            networks.forEach { network -> Row(Modifier.fillMaxWidth().clickable { staDialog = network.ssid }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(network.ssid); Text("信号 ${network.rssiDbm} dBm  ·  信道 ${network.channel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(network.security.name.uppercase()) } }
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
