package com.einkphoto.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.einkphoto.app.ui.components.ModeFeatureHeader
import com.einkphoto.app.ui.components.ModeSwitchStatusCard
import com.einkphoto.app.ui.components.crossFeatureDisplayText
import com.einkphoto.app.ui.components.modeCoverDrawableRes
import com.einkphoto.app.ui.model.AppDestination
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.feature.mode.ModeSwitchUiState

@Composable
fun FeaturePlaceholderScreen(
    destination: AppDestination,
    contentPadding: PaddingValues,
    device: DeviceSnapshot,
    modeSwitchState: ModeSwitchUiState,
    onSwitchMode: (DeviceFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = when (destination) {
        AppDestination.AiAlbum -> DeviceFeature.AiAlbum
        AppDestination.Dashboard -> DeviceFeature.InfoDashboard
        else -> DeviceFeature.LocalAlbum
    }
    val owner = device.currentContent?.ownerFeature
    val ownsContent = owner == target
    val screenContentLabel = if (!ownsContent) crossFeatureDisplayText(owner ?: device.activeFeature) else when (device.currentContent?.kind) {
        DeviceContentKind.ModeCover -> "${destination.title}模式提示画面"
        DeviceContentKind.Media -> "${destination.title}图片"
        DeviceContentKind.Dashboard -> "信息看板"
        else -> "相框当前画面暂不可读取"
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModeFeatureHeader(destination.title, target, device, modeSwitchState, onSwitchMode, screenContentLabel)
            ModeSwitchStatusCard(target, modeSwitchState)
            Text("当前画面", style = MaterialTheme.typography.titleMedium)
            OutlinedCard(modifier = Modifier.fillMaxWidth().aspectRatio(5f / 3f)) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    if (ownsContent && device.currentContent?.kind == DeviceContentKind.ModeCover) {
                        Image(
                            painter = painterResource(target.modeCoverDrawableRes()),
                            contentDescription = "${destination.title}模式提示画面",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else Text(
                        if (!ownsContent) crossFeatureDisplayText(owner ?: device.activeFeature)
                        else when (device.currentContent?.kind) {
                            DeviceContentKind.ModeCover -> "${destination.title}模式提示画面"
                            DeviceContentKind.Media -> "正在显示${destination.title}图片"
                            DeviceContentKind.Dashboard -> "正在显示信息看板"
                            else -> "相框当前画面暂不可读取"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (destination == AppDestination.AiAlbum) Icons.Outlined.AutoAwesome else Icons.Outlined.Dashboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(if (destination == AppDestination.AiAlbum) "AI 相册即将开始" else "信息看板即将开始", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "页面浏览不会改变设备模式。点击标题栏的“切换当前模式”，待墨水屏提示画面真实刷新完成后才会进入${destination.title}模式。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
