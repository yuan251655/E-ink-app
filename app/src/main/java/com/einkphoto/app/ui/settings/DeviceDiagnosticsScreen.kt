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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.settings.diagnostics.DeviceLogEntry
import com.einkphoto.app.feature.settings.diagnostics.LogSeverity
import com.einkphoto.app.feature.settings.network.NetworkSnapshot
import com.einkphoto.app.feature.settings.network.StaState
import com.einkphoto.app.feature.settings.storage.StorageHealth
import com.einkphoto.app.feature.settings.storage.StorageSnapshot
import com.einkphoto.app.ui.theme.appSemanticColors

/** Read-only overview. Real device event history will be supplied by /api/v1/logs in the next stage. */
@Composable
fun DeviceDiagnosticsScreen(
    device: DeviceSnapshot,
    network: NetworkSnapshot,
    storage: StorageSnapshot,
    logs: List<DeviceLogEntry>,
    contentPadding: PaddingValues,
) {
    val healthy = device.connection == DeviceConnectionState.Online && storage.health !in setOf(
        StorageHealth.Degraded, StorageHealth.Missing, StorageHealth.ErrorBackoff,
    )
    val accent = if (healthy) MaterialTheme.appSemanticColors.success else MaterialTheme.colorScheme.error
    val headline = if (healthy) "设备运行正常" else "设备需要检查"
    val summary = if (healthy) "相框在线，TF 卡状态正常，电子纸当前空闲" else "请查看下方状态，确认网络与 TF 卡是否可用"

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设备诊断", style = MaterialTheme.typography.headlineSmall)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        ) {
            Row(
                Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = accent.copy(alpha = 0.16f), contentColor = accent, shape = MaterialTheme.shapes.medium) {
                    Icon(
                        if (healthy) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(headline, style = MaterialTheme.typography.titleLarge, color = accent)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("核心状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                DiagnosticStatusRow(
                    icon = Icons.Outlined.Wifi,
                    title = "网络连接",
                    detail = networkDetail(device, network),
                    tint = if (device.connection == DeviceConnectionState.Online) MaterialTheme.appSemanticColors.success else MaterialTheme.colorScheme.error,
                )
                DiagnosticStatusRow(
                    icon = Icons.Outlined.SdCard,
                    title = "TF 卡",
                    detail = storageDetail(storage),
                    tint = storageTint(storage),
                )
                DiagnosticStatusRow(
                    icon = Icons.Outlined.PhotoSizeSelectLarge,
                    title = "电子纸显示",
                    detail = "空闲，可随时显示相册中的图片",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text("最近事件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            if (logs.isEmpty()) {
                DiagnosticEvent(Icons.Outlined.Wifi, "暂无运行事件", "连接相框后将显示设备最近记录", MaterialTheme.colorScheme.onSurfaceVariant)
            } else Column(Modifier.padding(vertical = 4.dp)) {
                logs.take(8).forEach { event -> DiagnosticEvent(
                    icon = when (event.severity) {
                        LogSeverity.Info -> Icons.Outlined.CheckCircle
                        LogSeverity.Warning, LogSeverity.Error -> Icons.Outlined.ErrorOutline
                    },
                    title = event.message,
                    detail = "${event.component} · ${event.code} · ${uptimeDescription(event.uptimeMs)}",
                    tint = when (event.severity) {
                        LogSeverity.Info -> MaterialTheme.appSemanticColors.success
                        LogSeverity.Warning -> MaterialTheme.colorScheme.primary
                        LogSeverity.Error -> MaterialTheme.colorScheme.error
                    },
                ) }
            }
        }
        Text("此页面只读取状态，不会刷新屏幕、重挂载 TF 卡或影响正在进行的任务。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun uptimeDescription(uptimeMs: Long): String = when {
    uptimeMs < 60_000L -> "刚刚"
    uptimeMs < 3_600_000L -> "设备启动后 ${uptimeMs / 60_000L} 分钟"
    else -> "设备启动后 ${uptimeMs / 3_600_000L} 小时"
}

@Composable
private fun DiagnosticStatusRow(icon: ImageVector, title: String, detail: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticEvent(icon: ImageVector, title: String, detail: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun networkDetail(device: DeviceSnapshot, state: NetworkSnapshot): String = when {
    device.connection != DeviceConnectionState.Online -> "相框未连接"
    state.sta.state == StaState.Connected -> "已通过 ${state.sta.ssid ?: "Wi-Fi"} 连接"
    else -> "相框已连接，可通过热点管理"
}

private fun storageDetail(state: StorageSnapshot): String = when (state.health) {
    StorageHealth.Ready -> "正常，可读写"
    StorageHealth.Degraded -> "可用，但有异常需要留意"
    StorageHealth.Missing -> "未检测到 TF 卡"
    StorageHealth.ErrorBackoff -> "暂时不可用，等待设备恢复"
    StorageHealth.Unknown -> "等待读取设备状态"
}

@Composable
private fun storageTint(state: StorageSnapshot): Color = when (state.health) {
    StorageHealth.Ready -> MaterialTheme.appSemanticColors.success
    StorageHealth.Degraded, StorageHealth.Missing, StorageHealth.ErrorBackoff -> MaterialTheme.colorScheme.error
    StorageHealth.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}
