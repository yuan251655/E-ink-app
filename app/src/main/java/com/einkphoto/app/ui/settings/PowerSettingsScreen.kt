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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.settings.power.PowerRepository
import com.einkphoto.app.feature.settings.power.PowerSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Device power policy stays device-owned; this screen only presents it. */
@Composable
fun PowerSettingsScreen(repository: PowerRepository, contentPadding: PaddingValues) {
    val power by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var showSleepTimeoutDialog by remember { mutableStateOf(false) }
    var automaticSleepSaving by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        repository.refresh()
        while (true) {
            delay(5_000L)
            repository.refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("电池、电源与休眠", style = MaterialTheme.typography.headlineSmall)
        Text(
            "状态由相框 AXP2101 电源管理芯片实时提供",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("休眠", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("启动自动休眠", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = power.automaticSleepEnabled,
                        enabled = !automaticSleepSaving,
                        onCheckedChange = { enabled ->
                            automaticSleepSaving = true
                            scope.launch {
                                repository.saveAutomaticSleep(enabled, power.idleTimeoutSeconds, power.wakeForPlayback)
                                automaticSleepSaving = false
                            }
                        },
                    )
                }
                PowerLine("未操作多久后进入休眠", sleepTimeoutText(power.idleTimeoutSeconds))
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { showSleepTimeoutDialog = true }) {
                    Text("设置休眠时间")
                }
            }
        }

        Text("主电池", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PowerLine("检测状态", if (power.batteryPresent) "已连接" else "未检测到主电池")
                PowerLine("电池电压", power.batteryVoltageMv?.let(::voltageText) ?: "--")
                PowerLine("电量估算", power.batteryPercent?.let { "$it%（AXP 估算）" } ?: "--")
                PowerLine("当前状态", batteryStateText(power))
            }
        }

        Text("电子纸电量显示", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("显示电池图标", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = power.batteryDisplayEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.saveBatteryDisplay(enabled, power.batteryDisplayRevision) }
                        },
                    )
                }
                Text(
                    "电量不高于 30% 时显示，恢复到 35% 后隐藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (power.batteryPresent) {
            Text("充电参数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PowerLine("充电阶段", chargerStateText(power.chargerState))
                    PowerLine("恒流设置", power.configuredCurrentMa?.let { "$it mA" } ?: "设备未提供")
                    PowerLine("目标电压", power.targetVoltageMv?.let(::voltageText) ?: "设备未提供")
                    PowerLine("终止电流", power.terminationCurrentMa?.let { "$it mA" } ?: "设备未提供")
                    PowerLine("满充终止保护", if (power.terminationEnabled) "已启用" else "设备未提供")
                }
            }
        }

        Text("电池与安全", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PowerLine("USB 输入", if (power.usbPresent) power.usbVoltageMv?.let(::voltageText) ?: "已连接" else "未连接")
                PowerLine("系统电压", power.systemVoltageMv?.let(::voltageText) ?: "--")
                PowerLine("RTC 备用电池充电", if (power.rtcBackupChargeEnabled) "已开启" else "已关闭（当前安全设置）")
                PowerLine("深度睡眠", if (power.deepSleepEnabled) "已启用" else "未启用")
            }
        }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { repository.refresh() } }) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("刷新电源状态", modifier = Modifier.padding(start = 8.dp))
        }
        power.lastErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (showSleepTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimeoutDialog = false },
            title = { Text("未操作多久后进入休眠") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(10, 60, 120, 300, 600, 900, 1800, 3600).forEach { seconds ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    repository.saveAutomaticSleep(power.automaticSleepEnabled, seconds, power.wakeForPlayback)
                                    showSleepTimeoutDialog = false
                                }
                            },
                        ) { Text(sleepTimeoutText(seconds)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimeoutDialog = false }) { Text("取消") } },
        )
    }
}

private fun sleepTimeoutText(seconds: Int): String =
    if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分钟"

@Composable
private fun PowerLine(title: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun batteryStateText(power: PowerSnapshot): String = when {
    power.charging -> "正在充电"
    power.discharging -> "正在供电"
    power.batteryPresent -> "已连接，当前未充放电"
    else -> "--"
}

private fun chargerStateText(value: String): String = when (value) {
    "constant_current" -> "恒流充电"
    "constant_voltage" -> "恒压充电"
    "pre_charge" -> "预充电"
    "trickle" -> "涓流充电"
    "completed" -> "已充满"
    "not_charging" -> "未充电"
    else -> "设备未提供"
}

private fun voltageText(millivolts: Int): String = "%.3f V".format(millivolts / 1000.0)
