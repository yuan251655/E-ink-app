package com.einkphoto.app.ui.aialbum

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import com.einkphoto.app.ui.components.AppleAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.einkphoto.app.core.device.DeviceContentKind
import com.einkphoto.app.core.device.DeviceCurrentContent
import com.einkphoto.app.core.device.DeviceFeature
import com.einkphoto.app.core.device.DeviceMediaCategory
import com.einkphoto.app.ui.components.pressFeedbackClickable
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val AI_IMAGE_EMPTY_TITLE = "还没有 AI 图片"

internal data class AiImageRecord(
    val id: String,
    val category: DeviceMediaCategory,
    val name: String,
    val prompt: String?,
    val generatedAtLabel: String,
    val modelLabel: String?,
    val sourceLabel: String?,
    val sizeBytes: Long,
    val previewUri: String?,
    val sourceAvailable: Boolean,
    val syncLabel: String?,
)

internal sealed interface AiImageLibraryState {
    data object Loading : AiImageLibraryState
    data object Offline : AiImageLibraryState
    data class Error(val message: String) : AiImageLibraryState
    data class Ready(val images: List<AiImageRecord>) : AiImageLibraryState
}

internal fun filterAiImageRecords(records: List<AiImageRecord>): List<AiImageRecord> =
    records.filter { it.category == DeviceMediaCategory.Ai }

internal fun isAiImageCurrentlyDisplayed(content: DeviceCurrentContent?, mediaId: String): Boolean =
    mediaId.isNotBlank() &&
        content?.mediaId?.isNotBlank() == true &&
        content.ownerFeature == DeviceFeature.AiAlbum &&
        content.kind == DeviceContentKind.Media &&
        content.category == DeviceMediaCategory.Ai &&
        content.mediaId == mediaId

internal fun formatAiImageSize(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
internal fun AiImageLibraryScreen(
    state: AiImageLibraryState,
    currentContent: DeviceCurrentContent?,
    listState: LazyGridState,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 760.dp)) {
            AiImageHeader(title = "AI 图片", onBack = onBack)
            when (state) {
                AiImageLibraryState.Loading -> CenterState(
                    icon = { CircularProgressIndicator(Modifier.size(34.dp)) },
                    title = "正在读取 AI 图片",
                    detail = "请稍候，正在向相框请求图片列表。",
                )
                AiImageLibraryState.Offline -> CenterState(
                    icon = { Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error) },
                    title = "相框未连接",
                    detail = "连接相框后才能读取 TF 卡中的 AI 图片。",
                )
                is AiImageLibraryState.Error -> CenterState(
                    icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error) },
                    title = "AI 图片读取失败",
                    detail = state.message,
                    action = {
                        OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("重试")
                        }
                    },
                )
                is AiImageLibraryState.Ready -> {
                    val aiImages = filterAiImageRecords(state.images)
                    if (aiImages.isEmpty()) {
                        CenterState(
                            icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) },
                            title = AI_IMAGE_EMPTY_TITLE,
                            detail = "AI 图片生成功能准备中。完成模型配置并接入图片生成服务后，生成结果会出现在这里。",
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = listState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(aiImages, key = { it.id }) { image ->
                                AiImageCard(
                                    image = image,
                                    currentlyDisplayed = isAiImageCurrentlyDisplayed(currentContent, image.id),
                                    onClick = { onOpenDetails(image.id) },
                                )
                            }
                            if (hasMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    OutlinedButton(
                                        onClick = onLoadMore,
                                        enabled = !loadingMore,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    ) {
                                        if (loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                        else Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiImageDetailScreen(
    image: AiImageRecord?,
    currentContent: DeviceCurrentContent?,
    connected: Boolean,
    actionMessage: String?,
    displayInProgress: Boolean,
    onBack: () -> Unit,
    onDisplay: () -> Unit,
    onSaveToPhone: () -> Unit,
    onSetPlaybackStart: () -> Unit,
    onDelete: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var localNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val savePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onSaveToPhone() else localNotice = "未获得存储权限，无法保存到手机。"
    }

    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 760.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回 AI 图片")
                }
                Text("图片详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = image != null, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("设为轮播起点") },
                            leadingIcon = { Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSetPlaybackStart()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                showDeleteConfirmation = true
                            },
                        )
                    }
                }
            }

            if (image == null) {
                CenterState(
                    icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = "图片不可用",
                    detail = "图片可能已删除，或者暂时无法读取。",
                    action = { OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("返回 AI 图片") } },
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val landscape = maxWidth > maxHeight
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            if (landscape) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                                    AiImagePreview(image, Modifier.weight(1f))
                                    AiImageMetadata(image, currentContent, connected, Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AiImagePreview(image)
                                    AiImageMetadata(image, currentContent, connected)
                                }
                            }
                        }
                        item {
                            (actionMessage ?: localNotice)?.let {
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = onDisplay,
                                enabled = connected && !displayInProgress,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(if (displayInProgress) "正在显示…" else "显示到相框")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                    ) savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    else onSaveToPhone()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(if (image.sourceAvailable) "保存原图到手机" else "保存六色预览图")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation && image != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除这张 AI 图片？") },
            text = { Text("删除后将从 AI 相册中移除“${image.name}”。此操作需要再次确认。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AiImageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回 AI 相册")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CenterState(
    icon: @Composable () -> Unit,
    title: String,
    detail: String,
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 480.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = if (compactHeight) 18.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compactHeight) Arrangement.spacedBy(0.dp) else Arrangement.Center,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    icon()
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    action?.invoke()
                }
            }
        }
    }
}

@Composable
private fun AiImageCard(image: AiImageRecord, currentlyDisplayed: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().pressFeedbackClickable(role = Role.Button, onClick = onClick),
        border = BorderStroke(
            width = 1.dp,
            color = if (currentlyDisplayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            AiImagePreviewSurface(image, Modifier.fillMaxWidth().aspectRatio(5f / 3f))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(image.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (currentlyDisplayed) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(image.prompt ?: "未提供", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${image.generatedAtLabel} · ${formatAiImageSize(image.sizeBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (currentlyDisplayed) Text("当前显示", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AiImagePreview(image: AiImageRecord, modifier: Modifier = Modifier) {
    AiImagePreviewSurface(image, modifier.fillMaxWidth().aspectRatio(5f / 3f))
}

@Composable
private fun AiImagePreviewSurface(image: AiImageRecord, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.previewUri) {
        value = image.previewUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
                }.getOrNull()
            }
        }
    }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "${image.name} 六色预览图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text("预览图暂不可用", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun AiImageMetadata(
    image: AiImageRecord,
    currentContent: DeviceCurrentContent?,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(image.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(image.prompt ?: "未提供", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            DetailLine("生成时间", image.generatedAtLabel)
            DetailLine("生成模型", image.modelLabel ?: "未提供")
            DetailLine("来源", image.sourceLabel ?: "未提供")
            DetailLine("大小", formatAiImageSize(image.sizeBytes))
            DetailLine("同步状态", if (connected) image.syncLabel ?: "未提供" else "相框未连接")
            DetailLine("相框画面", if (isAiImageCurrentlyDisplayed(currentContent, image.id)) "当前正在显示" else "未显示")
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.widthIn(min = 72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Medium)
    }
}

private val previewAiImages = listOf(
    AiImageRecord("ai-preview-1", DeviceMediaCategory.Ai, "春日山谷", "粉色晨雾中的远山与河流", "2026-07-31 15:20", "Doubao-Seedream-4.0", "小智文字创作", 186_420, null, true, "已保存到相框"),
    AiImageRecord("ai-preview-2", DeviceMediaCategory.Ai, "月夜花园", "月光照亮安静的玫瑰花园", "2026-07-31 14:08", "Doubao-Seedream-4.0", "小智语音创作", 174_080, null, false, "已保存到相框"),
    AiImageRecord("local-preview", DeviceMediaCategory.Local, "不应出现", "本地相册数据", "2026-07-31 13:00", null, null, 100, null, false, null),
)

private val previewCurrentContent = DeviceCurrentContent(DeviceContentKind.Media, DeviceFeature.AiAlbum, DeviceMediaCategory.Ai, "ai-preview-1", null)

@Preview(name = "AI 图片 空态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiImageLibraryEmptyPreview() = EInkPhotoTheme(darkTheme = false) {
    AiImageLibraryScreen(AiImageLibraryState.Ready(emptyList()), null, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), {}, {}, {}, PaddingValues())
}

@Preview(name = "AI 图片 两列数据", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiImageLibraryDataPreview() = EInkPhotoTheme(darkTheme = false) {
    AiImageLibraryScreen(AiImageLibraryState.Ready(previewAiImages), previewCurrentContent, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), {}, {}, {}, PaddingValues())
}

@Preview(name = "AI 图片 离线深色大字体", showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun AiImageLibraryOfflinePreview() = EInkPhotoTheme(darkTheme = true) {
    AiImageLibraryScreen(AiImageLibraryState.Offline, null, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), {}, {}, {}, PaddingValues())
}

@Preview(name = "AI 图片 加载态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiImageLibraryLoadingPreview() = EInkPhotoTheme(darkTheme = false) {
    AiImageLibraryScreen(AiImageLibraryState.Loading, null, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), {}, {}, {}, PaddingValues())
}

@Preview(name = "AI 图片 错误态", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiImageLibraryErrorPreview() = EInkPhotoTheme(darkTheme = false) {
    AiImageLibraryScreen(AiImageLibraryState.Error("相框返回的数据无法读取，请稍后重试。"), null, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), {}, {}, {}, PaddingValues())
}

@Preview(name = "AI 图片 横屏大字体可滚动", showBackground = true, widthDp = 720, heightDp = 360, fontScale = 1.5f)
@Composable
private fun AiImageLibraryCompactLargePreview() = EInkPhotoTheme(darkTheme = false) {
    AiImageLibraryScreen(AiImageLibraryState.Error("暂时无法读取 AI 图片，请稍后再试。"), null, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), {}, {}, {}, PaddingValues())
}

@Preview(name = "AI 图片详情 横屏", showBackground = true, widthDp = 720, heightDp = 360)
@Composable
private fun AiImageDetailLandscapePreview() = EInkPhotoTheme(darkTheme = false) {
    AiImageDetailScreen(previewAiImages.first(), previewCurrentContent, true, null, false, {}, {}, {}, {}, {}, PaddingValues())
}
