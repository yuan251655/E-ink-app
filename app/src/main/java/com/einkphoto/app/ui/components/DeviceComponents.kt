package com.einkphoto.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.einkphoto.app.ui.theme.appSemanticColors
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceSnapshot

@Composable
fun DeviceConnectionBadge(snapshot: DeviceSnapshot, modifier: Modifier = Modifier) {
    val connected = snapshot.connection == DeviceConnectionState.Online
    Surface(
        modifier = modifier.padding(end = 8.dp),
        color = if (connected) MaterialTheme.appSemanticColors.success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (connected) MaterialTheme.appSemanticColors.success else MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(if (connected) "● 已连接" else "● 未连接", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun DemoDeviceCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appSemanticColors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        BoxWithConstraints(Modifier.padding(20.dp)) {
            val compact = maxWidth < 360.dp
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("客厅墨相框", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "演示设备 · 数据仅用于界面预览",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                DemoModeBadge()
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            Spacer(Modifier.height(16.dp))
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeviceStatusItems(colors.success)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeviceStatusItems(colors.success, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoModeBadge() {
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { contentDescription = "演示模式，状态标签" },
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Science, contentDescription = null, Modifier.size(18.dp))
            Text("演示模式", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DeviceStatusItems(successColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    StatusItem(
        icon = Icons.Outlined.CheckCircle,
        title = "设备在线",
        detail = "状态正常",
        tint = successColor,
        modifier = modifier,
    )
    StatusItem(
        icon = Icons.Outlined.Info,
        title = "当前功能",
        detail = "本地相册",
        tint = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    )
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    title: String,
    detail: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$title，$detail" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NavigationDoesNotSwitchFeatureNotice(modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text("页面与设备功能相互独立", style = MaterialTheme.typography.titleMedium)
                Text(
                    "打开或切换这个页面不会改变电子纸当前功能。以后需要由你明确确认，才会切换设备。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
