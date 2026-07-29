package com.einkphoto.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import com.einkphoto.app.ui.theme.appSemanticColors

enum class AppMessageKind { Loading, Empty, Offline, Success, Warning, Error }

@Composable
fun AppMessageCard(
    kind: AppMessageKind,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val semantic = MaterialTheme.appSemanticColors
    val icon: ImageVector
    val tint: Color
    when (kind) {
        AppMessageKind.Loading -> { icon = Icons.Outlined.HourglassTop; tint = MaterialTheme.colorScheme.primary }
        AppMessageKind.Empty -> { icon = Icons.Outlined.Info; tint = MaterialTheme.colorScheme.secondary }
        AppMessageKind.Offline -> { icon = Icons.Outlined.CloudOff; tint = MaterialTheme.colorScheme.onSurfaceVariant }
        AppMessageKind.Success -> { icon = Icons.Outlined.CheckCircle; tint = semantic.success }
        AppMessageKind.Warning -> { icon = Icons.Outlined.WarningAmber; tint = semantic.warning }
        AppMessageKind.Error -> { icon = Icons.Outlined.ErrorOutline; tint = MaterialTheme.colorScheme.error }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(name = "通用组件 · 浅色", showBackground = true, widthDp = 393)
@Composable
private fun ComponentGalleryLightPreview() {
    EInkPhotoTheme(darkTheme = false) { ComponentGalleryContent() }
}

@Preview(name = "通用组件 · 深色", showBackground = true, widthDp = 393)
@Composable
private fun ComponentGalleryDarkPreview() {
    EInkPhotoTheme(darkTheme = true) { ComponentGalleryContent() }
}

@Composable
private fun ComponentGalleryContent() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppMessageCard(AppMessageKind.Loading, "正在连接设备", "请稍候，连接完成后会自动更新。")
        AppMessageCard(AppMessageKind.Empty, "还没有照片", "添加照片后即可在相框中显示。")
        AppMessageCard(AppMessageKind.Offline, "设备未连接", "请确认手机已连接相框热点。")
        AppMessageCard(AppMessageKind.Success, "操作已完成", "设备状态已经同步。")
        AppMessageCard(AppMessageKind.Warning, "设备正在忙碌", "电子纸刷新完成后可再次操作。")
        AppMessageCard(AppMessageKind.Error, "连接失败", "请检查网络后重试。")
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("设备名称") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = true, onCheckedChange = {})
            Text("演示模式")
            Button(onClick = {}) { Text("主要操作") }
        }
    }
}
