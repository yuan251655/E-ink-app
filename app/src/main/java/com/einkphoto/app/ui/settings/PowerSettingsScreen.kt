package com.einkphoto.app.ui.settings

import java.text.DateFormat
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
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.settings.power.BatteryCalibrationStore
import com.einkphoto.app.feature.settings.power.PowerRepository
import com.einkphoto.app.feature.settings.power.PowerSnapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** P0 uses only device-authoritative observation. No App control can change charging or sleep policy. */
@Composable
fun PowerSettingsScreen(repository: PowerRepository, contentPadding: PaddingValues) {
    val context = LocalContext.current.applicationContext
    val power by repository.snapshot.collectAsState()
    val calibrationStore = remember(context) { BatteryCalibrationStore(context) }
    val calibration by calibrationStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showRecordFullConfirmation by remember { mutableStateOf(false) }
    var showClearCalibrationConfirmation by remember { mutableStateOf(false) }
    // The battery may transition between USB charging and battery discharge
    // while this page stays visible. Keep a small, bounded heartbeat so the
    // user never has to leave and re-enter the page to see that transition.
    LaunchedEffect(repository) {
        repository.refresh()
        while (true) {
            delay(5_000L)
            repository.refresh()
        }
    }
    LaunchedEffect(power, calibration.fullVoltageMv) {
        calibrationStore.observe(power)
    }
    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("电池、电源与低功耗", style = MaterialTheme.typography.headlineSmall)
        Text("状态由相框 AXP2101 电源管理芯片实时提供", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(powerModeIcon(power), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(powerModeTitle(power), style = MaterialTheme.typography.titleMedium)
                    Text(powerModeDetail(power), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("主电池", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PowerLine("检测状态", if (power.batteryPresent) "已连接" else "未检测到主电池")
                PowerLine("电池电压", power.batteryVoltageMv?.let(::voltageText) ?: "--")
                PowerLine("电量估算", power.batteryPercent?.let { "$it%（待校准）" } ?: "--")
                PowerLine("当前状态", batteryStateText(power))
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

        Text("电量校准", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (calibration.fullVoltageMv == null) {
                    Text("尚未记录满电基准", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "请保持 USB 连接，等待充电阶段显示“已充满”后再记录。App 只会记录设备确认的满电点，不会用当前百分比直接冒充校准结果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = power.batteryPresent && power.chargerState == "completed",
                        onClick = { showRecordFullConfirmation = true },
                    ) { Text("记录当前为满电") }
                } else {
                    PowerLine("满电基准", calibration.fullVoltageMv?.let(::voltageText) ?: "--")
                    PowerLine("记录时间", calibration.fullRecordedAtMillis?.let(::dateTimeText) ?: "--")
                    PowerLine("放电观察", "${calibration.observationCount} 次")
                    calibration.lowestDischargeVoltageMv?.let { PowerLine("最低已观察电压", voltageText(it)) }
                    Text(
                        "拔掉 USB 后正常使用即可；仅在此页面打开时，App 每 5 分钟记录一次放电电压。请勿为了校准刻意把电池耗尽。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showClearCalibrationConfirmation = true }) { Text("重新开始校准") }
                }
            }
        }

        Text("电源与安全", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PowerLine("USB 输入", if (power.usbPresent) power.usbVoltageMv?.let(::voltageText) ?: "已连接" else "未连接")
                PowerLine("系统电压", power.systemVoltageMv?.let(::voltageText) ?: "--")
                PowerLine("RTC 备用电池充电", if (power.rtcBackupChargeEnabled) "已开启" else "已关闭（当前安全设置）")
                PowerLine("深度睡眠", if (power.deepSleepEnabled) "已启用" else "未启用")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("App 只读取状态，不会修改充电电流、目标电压或低功耗策略。相框已固定使用 4.2V、200mA、25mA 终止保护；首次使用电池时请留意电池与插头温升。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { repository.refresh() } }) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("刷新电源状态", modifier = Modifier.padding(start = 8.dp))
        }
        power.lastErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showRecordFullConfirmation) {
        AlertDialog(
            onDismissRequest = { showRecordFullConfirmation = false },
            title = { Text("确认记录满电") },
            text = { Text("将当前 ${power.batteryVoltageMv?.let(::voltageText) ?: "--"} 记录为这块 454261 电池的满电基准。请确认充电阶段已显示“已充满”。") },
            confirmButton = {
                TextButton(onClick = {
                    calibrationStore.recordConfirmedFull(power)
                    showRecordFullConfirmation = false
                }) { Text("确认记录") }
            },
            dismissButton = { TextButton(onClick = { showRecordFullConfirmation = false }) { Text("取消") } },
        )
    }
    if (showClearCalibrationConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCalibrationConfirmation = false },
            title = { Text("重新开始校准") },
            text = { Text("将清除本机保存的满电基准和放电观察记录，不会改变相框的充电策略。") },
            confirmButton = {
                TextButton(onClick = {
                    calibrationStore.clear()
                    showClearCalibrationConfirmation = false
                }) { Text("清除记录") }
            },
            dismissButton = { TextButton(onClick = { showClearCalibrationConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PowerLine(title: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun powerModeIcon(power: PowerSnapshot): ImageVector = when {
    power.charging -> Icons.Outlined.BatteryChargingFull
    power.discharging -> Icons.Outlined.BatteryFull
    power.usbPresent -> Icons.Outlined.ElectricBolt
    else -> Icons.Outlined.Power
}

private fun powerModeTitle(power: PowerSnapshot): String = when {
    !power.pmicOnline -> "等待相框电源状态"
    power.charging -> "USB 供电，正在充电"
    power.discharging -> "正在使用主电池"
    power.usbPresent -> "USB 供电"
    else -> "相框供电状态未知"
}

private fun powerModeDetail(power: PowerSnapshot): String = when {
    !power.pmicOnline -> "连接相框后将显示 AXP2101 状态"
    power.charging -> "主电池正在以设备当前策略充电"
    power.discharging -> "拔掉 USB 后，设备已自动切换至主电池"
    power.usbPresent -> "未检测到主电池或当前未充电"
    else -> "请检查 USB 连接和主电池插头"
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

private fun dateTimeText(timestamp: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(timestamp)
