package com.einkphoto.app.ui.localalbum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceConnectionState
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceJobState
import com.einkphoto.app.core.device.DisplayProfile
import com.einkphoto.app.feature.localalbum.LocalAlbumViewModel
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.LocalAlbumUiState
import com.einkphoto.app.feature.localalbum.model.MediaId
import com.einkphoto.app.feature.localalbum.model.MediaItem
import com.einkphoto.app.feature.localalbum.model.MediaProtectionReason
import com.einkphoto.app.feature.localalbum.model.PlayMode
import com.einkphoto.app.feature.localalbum.model.PlayOrder
import com.einkphoto.app.feature.localalbum.model.PlaybackSettings
import com.einkphoto.app.feature.localalbum.model.PlaybackSyncState
import com.einkphoto.app.feature.localalbum.model.DisplayResult
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import com.einkphoto.app.feature.localalbum.model.AdaptationSettings
import com.einkphoto.app.feature.localalbum.model.FitMode
import com.einkphoto.app.feature.localalbum.model.ConversionDraft
import com.einkphoto.app.feature.localalbum.model.ConversionStage
import com.einkphoto.app.ui.components.pressFeedbackClickable
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun LocalAlbumOverviewScreen(
    state: LocalAlbumUiState,
    viewModel: LocalAlbumViewModel,
    onOpenLibrary: () -> Unit,
    onImport: () -> Unit,
    onPlayback: () -> Unit,
    onBatch: () -> Unit,
    onMedia: (MediaId) -> Unit,
) {
    val currentMedia = state.currentMedia
    // The effect is disposed automatically when this overview leaves composition. The device is
    // authoritative; polling keeps the status card in sync after an automatic display change.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshPlaybackStatus()
            delay(15_000)
        }
    }
    ScreenList(title = "本地相册", subtitle = "管理手机导入与设备中的照片") {
        item {
            SectionTitle("设备当前画面")
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlaybackStatusCard(state.playback)
                    currentMedia?.let { media ->
                        // In real mode this is the source image streamed from the device and
                        // associated with its authoritative current_media_id, never a phone draft.
                        SavedMediaPreview(media, Modifier.fillMaxWidth())
                    } ?: DemoArtwork(
                        seed = 1,
                        description = "设备当前电子纸画面",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(currentMedia?.displayName ?: "当前画面暂不可用", style = MaterialTheme.typography.titleLarge)
                    Text(
                        currentDisplaySummary(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.displayJob?.state in setOf(DeviceJobState.Queued, DeviceJobState.Running)) {
                        StatusRow(Icons.Outlined.Refresh, "正在切换图片", "电子纸正在刷新，请耐心等待完成")
                    }
                    Text(
                        "最近成功：${formatTime(state.currentDisplay.lastSuccessfulRefreshEpochMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    currentMedia?.let { media ->
                        Text(
                            "原图：${media.sourceWidthPx} × ${media.sourceHeightPx} · ${media.orientationLabel()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.displayPrevious() },
                            enabled = !state.actionsLocked && state.media.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("上一张") }
                        OutlinedButton(
                            onClick = { viewModel.displayNext() },
                            enabled = !state.actionsLocked && state.media.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("下一张") }
                    }
                }
            }
        }
        if (state.device.activeFeature != DeviceFeature.LocalAlbum) {
            item {
                OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("设备当前是${state.device.activeFeature.label()}", style = MaterialTheme.typography.titleMedium)
                        Text("进入本页面不会自动切换。只有确认后才会向设备提交功能切换任务。")
                        Button(onClick = viewModel::switchToLocalAlbum, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("切换到本地相册")
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = !state.actionsLocked,
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("从手机导入")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPlayback, enabled = !state.actionsLocked, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("轮播设置")
                }
                OutlinedButton(onClick = onBatch, enabled = !state.actionsLocked, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Collections, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("管理图片")
                }
            }
        }
        if (state.media.isEmpty()) {
            item {
                SectionTitle("最近图片")
                OutlinedCard {
                    Text(
                        if (state.device.connection == DeviceConnectionState.Online) {
                            "TF 卡中还没有保存的照片"
                        } else {
                            "暂时无法读取 TF 卡信息：相框未连接或设备没有响应"
                        },
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item { SectionTitle("最近图片", "查看全部", onOpenLibrary) }
            items(state.media.take(4).chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { media ->
                        MediaCard(media, media.id == state.currentDisplay.mediaId, { onMedia(media.id) }, Modifier.weight(1f), enabled = !state.actionsLocked)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        state.userMessage?.let { message ->
            item {
                OutlinedCard {
                    StatusRow(Icons.Outlined.Info, "设备消息", message, Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}

/** Device-authoritative playback state from GET /api/v1/local-album/playback. */
@Composable
private fun PlaybackStatusCard(playback: PlaybackSettings) {
    val isAuto = playback.mode == PlayMode.Auto
    val title = if (isAuto) "正在轮播" else "轮播已暂停"
    val detail = if (isAuto) {
        "${playbackIntervalLabel(playback.intervalSeconds)} · ${if (playback.order == PlayOrder.Sequential) "顺序播放" else "随机播放"}"
    } else {
        "保持当前图片，手动切换后仍不会自动播放"
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // A countdown does not itself cause a Flow emission. Refresh the local wall-clock label at
    // minute precision while this card is visible, without making extra device requests.
    LaunchedEffect(playback.nextPlayInSeconds, playback.nextPlayAtEpochMillis, playback.stateRevision) {
        nowMillis = System.currentTimeMillis()
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val nextTime = playback.nextPlaybackWallClock(nowMillis)
    val accessibilityText = buildString {
        append(title)
        append("，")
        append(detail)
        if (isAuto) append(if (nextTime != null) "，下次切换时间 $nextTime" else "，下次切换时间暂未同步")
    }

    OutlinedCard(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isAuto) 0.75f else 0.45f)),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityText },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isAuto) Icons.Outlined.Schedule else Icons.Outlined.Pause,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isAuto) {
                    Text(
                        nextTime?.let { "下次切换：$it" } ?: "下次切换时间正在与设备同步",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun PlaybackSettings.nextPlaybackWallClock(nowMillis: Long): String? = when {
    nextPlayInSeconds != null -> formatPlaybackTime(nowMillis + nextPlayInSeconds.coerceAtLeast(0) * 1_000L)
    nextPlayAtEpochMillis != null -> formatPlaybackTime(nextPlayAtEpochMillis)
    else -> null
}

private fun formatPlaybackTime(epochMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

@Composable
internal fun DeviceLibraryScreen(state: LocalAlbumUiState, onBack: () -> Unit, onBatch: () -> Unit, onMedia: (MediaId) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubpageHeader("设备中的图片", "只显示已由设备原子入库的本地媒体", onBack, "选择", onBatch, actionEnabled = !state.actionsLocked)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill("本地图片", "${state.media.size} 张", Modifier.weight(1f))
            InfoPill("可用空间", formatBytes(state.device.storageFreeBytes), Modifier.weight(1f))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.media, key = { it.id.value }) { media ->
                MediaCard(media, media.id == state.currentDisplay.mediaId, { onMedia(media.id) }, enabled = !state.actionsLocked)
            }
        }
    }
}

@Composable
internal fun PhoneImportScreen(
    sources: List<PhoneSource>,
    selectedSourceId: String?,
    adaptations: Map<String, AdaptationSettings>,
    drafts: Map<String, ConversionDraft>,
    onBack: () -> Unit,
    onPickPhotos: () -> Unit,
    onRemoveSource: (String) -> Unit,
    onSelectSource: (String) -> Unit,
    onNext: () -> Unit,
) {
    ScreenList("从手机导入", "使用 Android 系统照片选择器", onBack) {
        item {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (sources.isEmpty()) "还未选择照片" else "已选择 ${sources.size} 张照片",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onPickPhotos, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (sources.isEmpty()) "选择照片" else "重新选择")
                    }
                }
            }
        }
        items(sources, key = { it.sourceId }) { source ->
            Card(
                modifier = Modifier.pressFeedbackClickable { onSelectSource(source.sourceId) },
                colors = CardDefaults.cardColors(
                    containerColor = if (source.sourceId == selectedSourceId) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneSourcePreview(
                        source = source,
                        contentDescription = "${source.displayName} 手机原图缩略图",
                        modifier = Modifier.weight(0.42f).height(140.dp),
                    )
                    Column(Modifier.weight(0.58f)) {
                        Text(source.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${source.widthPx} × ${source.heightPx} · ${source.orientationLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when {
                                source.sourceId == selectedSourceId -> "当前适配对象"
                                drafts[source.sourceId]?.stage == ConversionStage.Ready -> "手机六色草稿已生成 · 未上传"
                                drafts[source.sourceId]?.stage == ConversionStage.Failed -> "本地转换失败 · 可重试"
                                drafts[source.sourceId]?.stage == ConversionStage.Stale -> "构图已变化 · 需要重新生成"
                                adaptations[source.sourceId]?.isConfigured == true -> "已配置 · 待生成六色图片"
                                else -> "点按此卡选择适配"
                            },
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    IconButton(onClick = { onRemoveSource(source.sourceId) }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除 ${source.displayName}")
                    }
                }
            }
        }
        item {
            val configuredCount = sources.count { adaptations[it.sourceId]?.isConfigured == true }
            val allConfigured = sources.isNotEmpty() && configuredCount == sources.size
            Button(
                onClick = onNext,
                enabled = sources.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(
                    if (allConfigured) "查看六色效果（共 ${sources.size} 张）"
                    else "适配下一张（已完成 $configuredCount/${sources.size}）",
                )
            }
        }
    }
}

@Composable
internal fun ImageAdaptScreen(
    source: PhoneSource?,
    settings: AdaptationSettings,
    onBack: () -> Unit,
    onFitModeChange: (FitMode) -> Unit,
    onRotate: () -> Unit,
    onNext: () -> Unit,
) {
    ScreenList("图片适配", "第 2 步，共 3 步", onBack) {
        item {
            Text("原图预览", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            source?.let {
                PhoneSourcePreview(
                    source = it,
                    contentDescription = "${it.displayName} 原图预览",
                    modifier = Modifier.fillMaxWidth().aspectRatio(it.widthPx.toFloat() / it.heightPx.toFloat()),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } ?: DemoArtwork(9, "暂未选择原图", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            source?.let {
                Text("正在编辑：${it.displayName} · ${it.widthPx} × ${it.heightPx} · ${it.orientationLabel()}")
            }
            Text("这是本地构图预览；尚未生成六色图片，也没有上传到墨水屏。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        source?.let { selected ->
            item {
                Text("电子纸构图预览", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DeviceAdaptationPreview(
                    source = selected,
                    fitMode = settings.fitMode,
                    quarterTurnsClockwise = settings.quarterTurnsClockwise,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "800 × 480 横向画布 · ${if (settings.fitMode == FitMode.CropToFill) "填充裁剪" else "完整适配"} · 旋转 ${settings.quarterTurnsClockwise * 90}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("显示规格", style = MaterialTheme.typography.titleMedium)
                    Text("800 × 480 · 黑、白、绿、蓝、红、黄")
                    Text("显示帧 192000 bytes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("适配方式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FitMode.CropToFill, FitMode.FitInside).forEach { mode ->
                    FilterChip(
                        selected = settings.fitMode == mode,
                        onClick = { onFitModeChange(mode) },
                        label = { Text(if (mode == FitMode.CropToFill) "填充裁剪" else "完整适配") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onRotate,
                    label = { Text("旋转 ${settings.quarterTurnsClockwise * 90}°") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        item {
            Button(
                onClick = onNext,
                enabled = source != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("保存并继续") }
        }
    }
}

@Composable
internal fun SixColorPreviewScreen(
    state: LocalAlbumUiState,
    onBack: () -> Unit,
    onAdjust: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGenerate: () -> Unit,
) {
    val source = state.selectedPhoneSource
    val settings = state.selectedAdaptation
    var showSixColor by remember { mutableStateOf(true) }
    val previewProfile = state.device.capabilities?.displayProfile ?: localPreviewProfile
    val allConfigured = state.phoneSources.isNotEmpty() && state.configuredSourceCount == state.phoneSources.size
    ScreenList("六色效果预览", "第 3 步，共 3 步", onBack) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("照片效果预览", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "这里可以查看照片适配后的画面效果。保存到相框将在后续连接设备后完成。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        source?.let { selected ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PhoneSourcePreview(
                            source = selected,
                            contentDescription = "${selected.displayName} 来源缩略图",
                            modifier = Modifier.size(72.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(selected.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "第 ${state.phoneSources.indexOfFirst { it.sourceId == selected.sourceId } + 1} 张，共 ${state.phoneSources.size} 张",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${if (settings.fitMode == FitMode.CropToFill) "填充裁剪" else "完整适配"} · 旋转 ${settings.quarterTurnsClockwise * 90}°",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onAdjust, modifier = Modifier.heightIn(min = 48.dp)) { Text("调整构图") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrevious, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上一张") }
                    OutlinedButton(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("下一张") }
                }
            }
            item {
                Text("效果对比", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showSixColor,
                        onClick = { showSixColor = false },
                        label = { Text("彩色构图") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = showSixColor,
                        onClick = { showSixColor = true },
                        label = { Text("六色预览") },
                        leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null, Modifier.size(18.dp)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (showSixColor) {
                    SixColorSimulationPreview(
                        source = selected,
                        settings = settings,
                        profile = previewProfile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DeviceAdaptationPreview(
                        source = selected,
                        fitMode = settings.fitMode,
                        quarterTurnsClockwise = settings.quarterTurnsClockwise,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (!allConfigured) {
            item {
                Text(
                    "还有 ${state.phoneSources.size - state.configuredSourceCount} 张照片未完成适配，请先逐张配置。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Button(
                onClick = onGenerate,
                enabled = allConfigured,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("生成已配置的 ${state.configuredSourceCount} 张照片") }
        }
    }
}

private val localPreviewProfile = DisplayProfile(
    widthPx = 800,
    heightPx = 480,
    frameBytes = 192_000,
    palette = listOf("black", "white", "green", "blue", "red", "yellow"),
    orientationKey = "local_preview",
)

@Composable
internal fun LocalConversionTaskScreen(
    state: LocalAlbumUiState,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onCancelQueued: () -> Unit,
    onSave: (String) -> Unit,
    onSaveAll: () -> Unit,
    onDone: () -> Unit,
) {
    val orderedDrafts = state.phoneSources.mapNotNull { state.conversionDrafts[it.sourceId] }
    ScreenList("本地转换任务", "图片处理与保存状态", onBack) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (state.conversionRunning) "正在处理照片" else "处理结果",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text("成功 ${state.conversionSuccessCount} 张 · 失败 ${state.conversionFailureCount} 张")
                }
            }
        }
        items(orderedDrafts, key = { it.source.sourceId }) { draft ->
            ConversionDraftCard(draft, onRetry, onSave, !state.actionsLocked && !state.batchSaveActive)
        }
        if (state.conversionRunning) {
            item {
                OutlinedButton(
                    onClick = onCancelQueued,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("停止尚未开始的任务") }
            }
        }
        if (!state.conversionRunning && orderedDrafts.isNotEmpty()) {
            item {
                if (state.batchSaveActive) {
                    Text(
                        "正在保存 ${state.batchSaveCompleted.coerceAtLeast(0) + 1}/${state.batchSaveTotal} 张到相框",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (state.readyToSaveCount > 0) {
                    Button(
                        onClick = onSaveAll,
                        enabled = !state.batchSaveActive,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(
                            if (state.batchSaveActive) "正在保存…" else "全部保存到相框（${state.readyToSaveCount} 张）",
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onDone,
                    enabled = !state.batchSaveActive,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("完成") }
            }
        }
    }
}

@Composable
private fun ConversionDraftCard(
    draft: ConversionDraft,
    onRetry: (String) -> Unit,
    onSave: (String) -> Unit,
    saveEnabled: Boolean,
) {
    val failed = draft.stage == ConversionStage.Failed
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhoneSourcePreview(draft.source, "${draft.source.displayName} 转换任务缩略图", Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(draft.source.displayName, style = MaterialTheme.typography.titleMedium)
                Text(conversionStageLabel(draft.stage), color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                draft.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (draft.stage in setOf(ConversionStage.Ready, ConversionStage.WaitingForDevice)) {
                    TextButton(onClick = { onSave(draft.source.sourceId) }, enabled = saveEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("保存到相框") }
                }
            }
            if (failed) TextButton(onClick = { onRetry(draft.source.sourceId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试") }
        }
    }
}

private fun conversionStageLabel(stage: ConversionStage): String = when (stage) {
    ConversionStage.Admitted -> "已保存（可显示）"
    ConversionStage.Failed -> "保存失败（可重试）"
    ConversionStage.Uploading, ConversionStage.DeviceValidating, ConversionStage.Committing -> "保存中"
    else -> "准备保存"
}

private fun PhoneSource.orientationLabel(): String = when {
    widthPx > heightPx -> "横屏"
    heightPx > widthPx -> "竖屏"
    else -> "方图"
}

@Composable
internal fun MediaDetailScreen(
    state: LocalAlbumUiState,
    media: MediaItem,
    onBack: () -> Unit,
    onDisplay: (AfterDisplay) -> Unit,
    onDelete: () -> Unit,
) {
    var afterDisplay by remember { mutableStateOf(AfterDisplay.Continue) }
    var confirmDelete by remember { mutableStateOf(false) }
    val displayJob = state.displayJob
    val displayInProgress = displayJob?.state == DeviceJobState.Queued || displayJob?.state == DeviceJobState.Running
    val displaySucceeded = displayJob?.state == DeviceJobState.Success || media.id == state.currentDisplay.mediaId
    val displayFailed = displayJob?.state in setOf(DeviceJobState.Failed, DeviceJobState.Cancelled, DeviceJobState.TimedOut)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除设备图片？") },
            text = { Text("将从设备媒体库删除“${media.displayName}”。电子纸当前画面和受保护图片不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    ScreenList(media.displayName, "设备媒体详情", onBack) {
        item { SavedMediaPreview(media, Modifier.fillMaxWidth()) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailLine("设备状态", if (media.id == state.currentDisplay.mediaId) "当前正在显示" else "已入库，可显示")
                    DetailLine("显示规格", "${media.sourceWidthPx} × ${media.sourceHeightPx}")
                    DetailLine("文件占用", formatBytes(media.sizeBytes))
                    DetailLine("媒体编号", media.id.value.takeLast(12))
                }
            }
        }
        item {
            Text("显示后策略", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = afterDisplay == AfterDisplay.Continue,
                    onClick = { afterDisplay = AfterDisplay.Continue },
                    enabled = !displayInProgress,
                    label = { Text("继续轮播") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = afterDisplay == AfterDisplay.Hold,
                    onClick = { afterDisplay = AfterDisplay.Hold },
                    enabled = !displayInProgress,
                    label = { Text("固定显示") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        item {
            Button(
                onClick = { onDisplay(afterDisplay) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = !state.actionsLocked && !displayInProgress && !displaySucceeded,
            ) {
                Text(
                    when {
                        displayInProgress -> "正在显示…"
                        displaySucceeded -> "已显示到相框"
                        displayFailed -> "重新显示到相框"
                        else -> "显示到相框"
                    },
                )
            }
            when {
                displayInProgress -> {
                    Spacer(Modifier.height(10.dp))
                    StatusRow(Icons.Outlined.Refresh, "正在显示", "电子纸正在刷新，请耐心等待完成")
                }
                displaySucceeded -> {
                    Spacer(Modifier.height(10.dp))
                    StatusRow(Icons.Outlined.CheckCircle, "已显示到相框", "设备已完成刷新，你可以返回图库继续浏览")
                }
                displayFailed -> {
                    Spacer(Modifier.height(10.dp))
                    StatusRow(
                        Icons.Outlined.Info,
                        "显示失败",
                        when (displayJob?.state) {
                            DeviceJobState.TimedOut -> "等待设备刷新超时，请确认相框在线后重试"
                            DeviceJobState.Cancelled -> "设备取消了本次显示，请重新尝试"
                            else -> "设备未完成刷新，当前画面保持不变；请确认连接后重试"
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = media.canDelete,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(if (media.canDelete) Icons.Outlined.DeleteOutline else Icons.Outlined.Lock, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (media.canDelete) "删除设备图片" else protectionText(media.protectionReasons))
            }
            state.userMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                StatusRow(Icons.Outlined.Info, "操作反馈", message)
            }
        }
    }
}

@Composable
internal fun PlaybackSettingsScreen(state: LocalAlbumUiState, viewModel: LocalAlbumViewModel, onBack: () -> Unit) {
    var mode by remember(state.playback.mode) { mutableStateOf(state.playback.mode) }
    var order by remember(state.playback.order) { mutableStateOf(state.playback.order) }
    var intervalSeconds by remember(state.playback.intervalSeconds) { mutableIntStateOf(state.playback.intervalSeconds) }
    val canSave = !state.actionsLocked && state.playback.syncState == PlaybackSyncState.Ready
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回本地相册")
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text("轮播设置", style = MaterialTheme.typography.titleLarge)
                Text("设置保存到相册设备", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        item {
            Text("播放模式", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple(PlayMode.Auto, "自动轮播", "按间隔自动显示下一张"),
                Triple(PlayMode.Paused, "暂停轮播", "保留进度，停止自动换图"),
            ).forEach { (value, title, detail) ->
                ChoiceCard(mode == value, title, detail) { mode = value }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Text("轮播间隔", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                listOf(300 to "5 分钟", 900 to "15 分钟", 1800 to "30 分钟", 3600 to "1 小时"),
                listOf(10800 to "3 小时", 21600 to "6 小时", 43200 to "12 小时", 86400 to "24 小时"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (seconds, label) ->
                    FilterChip(
                            selected = intervalSeconds == seconds,
                            onClick = { intervalSeconds = seconds },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    )
                }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Text("播放顺序", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = order == PlayOrder.Sequential,
                    onClick = { order = PlayOrder.Sequential },
                    label = { Text("顺序") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = order == PlayOrder.Random,
                    onClick = { order = PlayOrder.Random },
                    label = { Text("随机") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        item {
            Button(
                onClick = { viewModel.savePlayback(state.playback.copy(mode = mode, order = order, intervalSeconds = intervalSeconds)) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("保存到设备") }
            when (state.playback.syncState) {
                PlaybackSyncState.Loading -> StatusRow(Icons.Outlined.HourglassTop, "轮播设置", "正在读取设备设置")
                PlaybackSyncState.Offline -> StatusRow(Icons.Outlined.ErrorOutline, "轮播设置", "设备未连接，无法保存")
                PlaybackSyncState.Conflict -> StatusRow(Icons.Outlined.ErrorOutline, "轮播设置", "设备设置已变更，已加载最新配置，请确认后重新保存")
                PlaybackSyncState.Saving -> StatusRow(Icons.Outlined.HourglassTop, "轮播设置", "正在保存到设备")
                PlaybackSyncState.Ready -> StatusRow(
                    Icons.Outlined.CheckCircle,
                    "设备状态",
                    if (state.playback.mode == PlayMode.Auto) "已开启 · ${playbackIntervalLabel(state.playback.intervalSeconds)} · ${if (state.playback.order == PlayOrder.Sequential) "顺序播放" else "随机播放"}" else "已暂停，保持当前图片",
                )
            }
            state.userMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                StatusRow(Icons.Outlined.Info, "保存反馈", message)
            }
        }
        }
    }
}

private fun playbackIntervalLabel(seconds: Int): String = when (seconds) {
    300 -> "每 5 分钟"; 900 -> "每 15 分钟"; 1800 -> "每 30 分钟"; 3600 -> "每 1 小时"
    10800 -> "每 3 小时"; 21600 -> "每 6 小时"; 43200 -> "每 12 小时"; 86400 -> "每 24 小时"
    else -> "间隔未知"
}

@Composable
internal fun BatchManageScreen(state: LocalAlbumUiState, viewModel: LocalAlbumViewModel, onBack: () -> Unit) {
    val selectedIds = remember { mutableStateListOf<MediaId>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var resultSummary by remember { mutableStateOf<String?>(null) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${selectedIds.size} 张图片？") },
            text = { Text("设备会逐项检查保护状态。部分图片删除失败时，其余成功项不会回滚。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val targets = selectedIds.toList()
                    var completed = 0
                    var succeeded = 0
                    targets.forEach { id ->
                        viewModel.delete(id) { result ->
                            completed += 1
                            if (result is DeviceCommandResult.Accepted) {
                                succeeded += 1
                                selectedIds.remove(id)
                            }
                            if (completed == targets.size) resultSummary = "已删除 $succeeded 张，失败 ${targets.size - succeeded} 张"
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("确认删除")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    ScreenList("批量管理", "只管理设备中已入库的本地图片", onBack) {
        items(state.media) { media ->
            val protected = !media.canDelete
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
                    .semantics {
                        selected = media.id in selectedIds
                        stateDescription = if (protected) "受保护，不可选择" else if (media.id in selectedIds) "已选择" else "未选择"
                    }
                    .pressFeedbackClickable(enabled = !protected && !state.actionsLocked, role = Role.Checkbox) {
                        if (media.id in selectedIds) selectedIds.remove(media.id) else selectedIds.add(media.id)
                    },
                border = BorderStroke(1.dp, if (media.id in selectedIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Reuse the device-source thumbnail cache used by the recent-media cards;
                    // batch management must never fall back to a hard-coded demo image.
                    SavedMediaPreview(media, Modifier.weight(0.38f))
                    Column(Modifier.weight(0.5f)) {
                        Text(media.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(if (protected) protectionText(media.protectionReasons) else "可选择删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (protected) Icons.Outlined.Lock else if (media.id in selectedIds) Icons.Outlined.CheckCircle else Icons.Outlined.MoreHoriz, contentDescription = null)
                }
            }
        }
        item {
            Button(
                onClick = { confirmDelete = true },
                enabled = selectedIds.isNotEmpty() && !state.actionsLocked,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("删除选中的 ${selectedIds.size} 张图片")
            }
            Text(
                "删除前设备会再次检查当前画面、回退图片、上传和刷新保护状态。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            resultSummary?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ScreenList(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (onBack != null) SubpageHeader(title, subtitle, onBack)
            else {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SubpageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null && onAction != null) TextButton(onClick = onAction, enabled = actionEnabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().pressFeedbackClickable(role = Role.RadioButton, onClick = onClick),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatBytes(bytes: Long?): String = when {
    bytes == null -> "不可用"
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "$bytes B"
}

private fun currentDisplaySummary(state: LocalAlbumUiState): String = when (state.currentDisplay.result) {
    DisplayResult.Idle -> "设备当前画面 · 空闲"
    DisplayResult.Refreshing -> "设备当前画面 · 电子纸刷新中"
    DisplayResult.Success -> "设备权威状态 · 刷新成功"
    DisplayResult.Failed -> "最近刷新失败 · 屏幕保留上一张有效画面"
}

private fun formatTime(epochMillis: Long?): String = epochMillis?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
} ?: "暂无记录"

private fun relativeMedia(state: LocalAlbumUiState, offset: Int): MediaItem? {
    if (state.media.isEmpty()) return null
    val current = state.media.indexOfFirst { it.id == state.currentDisplay.mediaId }
    val target = if (current < 0) {
        if (offset > 0) 0 else state.media.lastIndex
    } else {
        Math.floorMod(current + offset, state.media.size)
    }
    return state.media[target]
}

private fun protectionText(reasons: Set<MediaProtectionReason>): String {
    if (reasons.isEmpty()) return "可选择删除"
    return "受保护 · " + reasons.joinToString("、") { reason ->
        when (reason) {
            MediaProtectionReason.CurrentDisplay -> "当前正在显示"
            MediaProtectionReason.OnlyFallback -> "唯一有效回退图片"
            MediaProtectionReason.Uploading -> "正在上传"
            MediaProtectionReason.Refreshing -> "正在刷新"
        }
    }
}

private fun MediaItem.sourceAspectRatio(): Float = sourceWidthPx.toFloat() / sourceHeightPx.toFloat()

private fun MediaItem.orientationLabel(): String = when {
    sourceWidthPx > sourceHeightPx -> "横屏"
    sourceWidthPx < sourceHeightPx -> "竖屏"
    else -> "正方形"
}
