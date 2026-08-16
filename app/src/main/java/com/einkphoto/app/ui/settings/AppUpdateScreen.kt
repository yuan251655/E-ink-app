package com.einkphoto.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.einkphoto.app.ui.components.AsyncButtonContent

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
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("安全下载并由系统确认安装，不影响照片和设备设置。", color = MaterialTheme.colorScheme.onSurfaceVariant)

            val release = state.release
            val statusTitle = when (state.phase) {
                AppUpdatePhase.Idle -> "应用已准备就绪"
                AppUpdatePhase.Checking -> "正在检查更新"
                AppUpdatePhase.UpToDate -> "已是最新版本"
                AppUpdatePhase.UpdateAvailable -> "发现新版本"
                AppUpdatePhase.Downloading -> "正在下载更新"
                AppUpdatePhase.ReadyToInstall -> "可以安装了"
                AppUpdatePhase.Failed -> "更新检查失败"
            }
            val displayedVersion = when (state.phase) {
                AppUpdatePhase.UpdateAvailable, AppUpdatePhase.Downloading, AppUpdatePhase.ReadyToInstall -> release?.versionName
                else -> state.currentVersionName
            } ?: state.currentVersionName
            val statusIcon = if (state.phase == AppUpdatePhase.Failed) Icons.Outlined.ErrorOutline
            else if (state.phase == AppUpdatePhase.UpToDate) Icons.Outlined.CheckCircle
            else Icons.Outlined.SystemUpdate
            val statusColor = if (state.phase == AppUpdatePhase.Failed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = if (state.phase == AppUpdatePhase.Failed) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            contentColor = statusColor,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(statusIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(statusTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(displayedVersion, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (displayedVersion == state.currentVersionName) "稳定版" else "当前版本 ${state.currentVersionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.phase == AppUpdatePhase.Downloading) {
                        val progress = if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "${formatUpdateBytes(state.downloadedBytes)} / ${if (state.totalBytes > 0) formatUpdateBytes(state.totalBytes) else "正在获取大小"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.phase == AppUpdatePhase.Failed) {
                        Text(state.message ?: "请检查手机网络后重试", color = MaterialTheme.colorScheme.error)
                    } else if (state.phase == AppUpdatePhase.ReadyToInstall) {
                        Text("更新包已完成完整性与签名校验。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.phase in setOf(AppUpdatePhase.UpdateAvailable, AppUpdatePhase.Downloading, AppUpdatePhase.ReadyToInstall) && release != null) {
                Text("更新内容", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (release.releaseNotes.isNotBlank()) {
                            Text(cleanReleaseNotes(release.releaseNotes, release.versionName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("安装包 · ${formatUpdateBytes(release.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            when (state.phase) {
                AppUpdatePhase.UpdateAvailable -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("下载更新") }
                AppUpdatePhase.ReadyToInstall -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("立即安装") }
                AppUpdatePhase.Checking, AppUpdatePhase.Downloading -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    AsyncButtonContent(
                        loading = true,
                        idleText = "",
                        loadingText = if (state.phase == AppUpdatePhase.Checking) "正在检查…" else "正在下载…",
                    )
                }
                else -> Button(onClick = onCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (state.phase == AppUpdatePhase.UpToDate) "重新检查" else "检查更新") }
            }
        }
    }
}

private fun cleanReleaseNotes(notes: String, versionName: String): String =
    notes.removePrefix("相念 v$versionName：").trim()

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
