package com.einkphoto.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.settings.network.NetworkRepository
import com.einkphoto.app.feature.settings.network.NetworkSnapshot
import com.einkphoto.app.feature.settings.network.StaState
import com.einkphoto.app.core.device.DeviceEndpointConfig
import kotlinx.coroutines.launch

@Composable
fun NetworkSettingsScreen(
    repository: NetworkRepository,
    contentPadding: PaddingValues,
    onOpenCurrentDisplay: () -> Unit,
) {
    val state by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(DeviceEndpointConfig.apiBaseUrl) }
    var endpointMessage by remember { mutableStateOf("") }
    LaunchedEffect(repository) { repository.refresh() }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        DeviceConnectionCard(state, onReconnect = { scope.launch { repository.refresh() } })
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("相框地址", style = MaterialTheme.typography.titleMedium)
                Text("AP 默认地址为 192.168.4.1；相框接入家庭 Wi-Fi 后，填写它在局域网中的 STA 地址。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("设备地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    endpointMessage = if (DeviceEndpointConfig.saveApiBaseUrl(endpoint)) {
                        scope.launch { repository.refresh() }
                        "地址已保存，正在重新连接"
                    } else "地址格式不正确，请输入 http://加 IP 地址"
                }, modifier = Modifier.fillMaxWidth()) { Text("保存并重新连接") }
                OutlinedButton(onClick = {
                    DeviceEndpointConfig.useApAddress()
                    endpoint = DeviceEndpointConfig.apiBaseUrl
                    endpointMessage = "已切换到 AP 地址，正在重新连接"
                    scope.launch { repository.refresh() }
                }, modifier = Modifier.fillMaxWidth()) { Text("使用 AP 默认地址") }
                if (endpointMessage.isNotBlank()) Text(endpointMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CurrentDisplayCard(onOpenCurrentDisplay)
        NetworkSummaryCard(state)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Wi-Fi 配置", style = MaterialTheme.typography.titleMedium)
                Text(
                    "连接设备的 esp_network 网络后，可在这里查看已保存的网络连接信息。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "设备端网络配置完成后，这里会提供选择和保存 Wi-Fi 的操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceConnectionCard(state: NetworkSnapshot, onReconnect: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    val connected = state.deviceId != "unknown"
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("设备连接", style = MaterialTheme.typography.titleMedium)
        Text(if (connected) "已连接 ${state.deviceId}" else "未连接墨相框")
        Text(
            if (connected) "设备信息已同步。" else "请先在手机 Wi-Fi 中连接 esp_network，再返回此页。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) { Text("重新连接") }
    }
}

@Composable
private fun CurrentDisplayCard(onOpen: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("当前画面", style = MaterialTheme.typography.titleMedium)
        Text("在本地相册中查看设备当前显示的照片和刷新状态。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("查看当前画面") }
    }
}

@Composable
private fun NetworkSummaryCard(state: NetworkSnapshot) = Card(Modifier.fillMaxWidth()) {
    val staText = when (state.sta.state) {
        StaState.Connected -> "已连接 ${state.sta.ssid ?: "Wi-Fi"}"
        StaState.Connecting -> "正在连接"
        StaState.Failed -> "连接未完成"
        StaState.Disabled -> "尚未连接 Wi-Fi"
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("网络", style = MaterialTheme.typography.titleMedium)
        Text("设备网络：${if (state.ap.enabled) state.ap.ssid else "未连接"}")
        Text("Wi-Fi：$staText", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.sta.ip != null) Text("设备地址：${state.sta.ip}", style = MaterialTheme.typography.bodySmall)
    }
}
