package com.einkphoto.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceSnapshot
import com.einkphoto.app.core.device.DeviceModeState
import com.einkphoto.app.feature.mode.ModeSwitchPhase
import com.einkphoto.app.feature.mode.ModeSwitchUiState
import com.einkphoto.app.R

@Composable
fun ModeFeatureHeader(
    title: String,
    target: DeviceFeature,
    device: DeviceSnapshot,
    switchState: ModeSwitchUiState,
    onSwitch: (DeviceFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val switchingHere = (switchState.switching && switchState.target == target) ||
        (device.modeState == DeviceModeState.Switching && device.pendingFeature == target)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (device.activeFeature == target && !switchingHere && device.pendingFeature == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("当前模式", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        } else {
            TextButton(
                onClick = { onSwitch(target) },
                enabled = !switchState.switching && device.modeState != DeviceModeState.Switching,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (switchingHere) "正在切换…" else "切换当前模式") }
        }
    }
}

@Composable
fun ModeSwitchStatusCard(
    target: DeviceFeature,
    state: ModeSwitchUiState,
    modifier: Modifier = Modifier,
) {
    // A successful switch is already expressed by the title-bar "当前模式"
    // status and the authoritative current picture. Keep this card only for
    // an operation in progress or an actionable failure, so completed jobs do
    // not permanently occupy vertical space on every feature homepage.
    if (state.target != target || state.phase in setOf(ModeSwitchPhase.Idle, ModeSwitchPhase.Success)) return
    val failed = state.phase == ModeSwitchPhase.Failed
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            when {
                failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            },
        ),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    failed -> Icons.Outlined.ErrorOutline
                    else -> Icons.Outlined.Refresh
                },
                contentDescription = null,
                tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    when (state.phase) {
                        ModeSwitchPhase.Queued -> "准备画面"
                        ModeSwitchPhase.Preparing -> "准备画面"
                        ModeSwitchPhase.Refreshing -> "墨水屏刷新中"
                        ModeSwitchPhase.Finalizing -> "正在完成切换"
                        ModeSwitchPhase.Success -> "切换完成"
                        ModeSwitchPhase.Failed -> "切换未完成"
                        ModeSwitchPhase.Idle -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

fun DeviceFeature.displayName(): String = when (this) {
    DeviceFeature.LocalAlbum -> "本地相册"
    DeviceFeature.AiAlbum -> "AI 相册"
    DeviceFeature.InfoDashboard -> "信息看板"
}

fun DeviceFeature.modeCoverDrawableRes(): Int = when (this) {
    DeviceFeature.LocalAlbum -> R.drawable.mode_cover_local_album
    DeviceFeature.AiAlbum -> R.drawable.mode_cover_ai_album
    DeviceFeature.InfoDashboard -> R.drawable.mode_cover_info_dashboard
}

fun crossFeatureDisplayText(owner: DeviceFeature?): String = when (owner) {
    DeviceFeature.LocalAlbum -> "正在显示本地相册图片"
    DeviceFeature.AiAlbum -> "正在显示 AI 相册图片"
    DeviceFeature.InfoDashboard -> "正在显示信息看板"
    null -> "相框当前画面暂不可读取"
}
