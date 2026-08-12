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
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.einkphoto.app.feature.settings.audio.AudioRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AudioSettingsScreen(repository: AudioRepository, contentPadding: PaddingValues) {
    val audio by repository.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var draftVolume by remember { mutableFloatStateOf(audio.masterVolume.toFloat()) }

    LaunchedEffect(audio.masterVolume) { draftVolume = audio.masterVolume.toFloat() }
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
        Text("音频与语音", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (audio.muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("主音量", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("${draftVolume.roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = draftVolume,
                    onValueChange = { draftVolume = it },
                    onValueChangeFinished = {
                        scope.launch { repository.save(draftVolume.roundToInt().coerceIn(1, 100), audio.muted) }
                    },
                    valueRange = 1f..100f,
                    enabled = audio.connected && !audio.saving,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { val next = (draftVolume.roundToInt() - 5).coerceAtLeast(1); draftVolume = next.toFloat(); scope.launch { repository.save(next, audio.muted) } },
                        enabled = audio.connected && !audio.saving,
                        modifier = Modifier.weight(1f),
                    ) { Text("−5") }
                    OutlinedButton(
                        onClick = { val next = (draftVolume.roundToInt() + 5).coerceAtMost(100); draftVolume = next.toFloat(); scope.launch { repository.save(next, audio.muted) } },
                        enabled = audio.connected && !audio.saving,
                        modifier = Modifier.weight(1f),
                    ) { Text("＋5") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("静音模式", style = MaterialTheme.typography.titleMedium)
                    Text("静音后仍可唤醒小智，但不会播放回复和提示音", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = audio.muted,
                    enabled = audio.connected && !audio.saving,
                    onCheckedChange = { muted -> scope.launch { repository.save(draftVolume.roundToInt().coerceIn(1, 100), muted) } },
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("扬声器测试", style = MaterialTheme.typography.titleMedium)
                Text("播放设备内置短提示音，不需要连接互联网", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { scope.launch { repository.testSpeaker() } },
                    enabled = audio.connected && !audio.muted && !audio.playing && !audio.testing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (audio.testing) "正在播放…" else "播放测试音") }
            }
        }

        if (!audio.connected) Text("相框未连接，当前显示的是上次读取的音量", color = MaterialTheme.colorScheme.onSurfaceVariant)
        audio.lastErrorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { scope.launch { repository.refresh() } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("刷新音频状态", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
