package com.einkphoto.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einkphoto.app.feature.settings.appupdate.AppUpdatePhase
import com.einkphoto.app.feature.settings.appupdate.AppUpdateUiState
import com.einkphoto.app.feature.settings.appupdate.AppUpdateViewModel

@Composable
fun AppUpdateScreen(onBack: () -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val viewModel: AppUpdateViewModel = viewModel(key = "app-update") {
        AppUpdateViewModel(context.applicationContext)
    }
    val state by viewModel.state.collectAsState()
    AppUpdateContent(
        state = state,
        onBack = onBack,
        onCheck = viewModel::check,
        onDownload = viewModel::download,
        onInstall = {
            if (viewModel.canInstallPackages()) viewModel.install()?.let(context::startActivity)
            else context.startActivity(viewModel.unknownSourcesSettingsIntent())
        },
        contentPadding = contentPadding,
    )
}

@Composable
private fun AppUpdateContent(
    state: AppUpdateUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
            }
            Spacer(Modifier.size(8.dp))
            Text("应用更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("下载完成后将由系统安装器确认安装。更新不会影响相框中的照片、网络配置或设备数据。", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("当前版本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${state.currentVersionName} · build ${state.currentVersionCode}", style = MaterialTheme.typography.titleLarge)
                    Text("稳定通道", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            state.release?.let { release ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (state.phase == AppUpdatePhase.UpToDate) "版本状态" else "发现新版本", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("${release.versionName} · build ${release.versionCode}", style = MaterialTheme.typography.titleLarge)
                        if (release.releaseNotes.isNotBlank()) Text(release.releaseNotes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("更新包 ${formatUpdateBytes(release.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.phase == AppUpdatePhase.Downloading) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("正在下载并校验", style = MaterialTheme.typography.titleMedium)
                        val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${formatUpdateBytes(state.downloadedBytes)} / ${if (state.totalBytes > 0) formatUpdateBytes(state.totalBytes) else "正在获取大小"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            state.message?.let { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (state.phase == AppUpdatePhase.Failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (state.phase == AppUpdatePhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Text(message, color = if (state.phase == AppUpdatePhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            when (state.phase) {
                AppUpdatePhase.UpdateAvailable -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("下载更新") }
                AppUpdatePhase.ReadyToInstall -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("立即安装") }
                AppUpdatePhase.Checking, AppUpdatePhase.Downloading -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(if (state.phase == AppUpdatePhase.Checking) "正在检查…" else "正在下载…") }
                else -> Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("检查更新") }
            }
        }
    }
}

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
