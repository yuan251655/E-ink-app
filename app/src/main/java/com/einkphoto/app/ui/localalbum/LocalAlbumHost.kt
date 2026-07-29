package com.einkphoto.app.ui.localalbum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceSession
import com.einkphoto.app.feature.localalbum.LocalAlbumViewModel
import com.einkphoto.app.feature.localalbum.data.DemoLocalAlbumController
import com.einkphoto.app.feature.localalbum.data.LocalAlbumDemoDependencies
import com.einkphoto.app.feature.localalbum.data.LocalDraftRequest
import com.einkphoto.app.feature.localalbum.data.createLocalDraft
import com.einkphoto.app.feature.localalbum.model.AfterDisplay
import com.einkphoto.app.feature.localalbum.model.PhoneSource
import com.einkphoto.app.ui.theme.EInkPhotoTheme
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalAlbumDemoRuntime(
    val viewModel: LocalAlbumViewModel,
    val demoController: DemoLocalAlbumController?,
)

@Composable
fun LocalAlbumHost(
    viewModel: LocalAlbumViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    demoController: DemoLocalAlbumController? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack = rememberSaveable(saver = localAlbumBackStackSaver) {
        mutableStateListOf<LocalAlbumRoute>(LocalAlbumRoute.Overview)
    }
    val route = backStack.last()
    val navigate: (LocalAlbumRoute) -> Unit = { destination -> backStack.add(destination) }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val backToOverview: () -> Unit = { backStack.clear(); backStack.add(LocalAlbumRoute.Overview) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris ->
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> context.toPhoneSource(uri) }
            }
            viewModel.setPhoneSources(imported)
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(route) {
        if (route == LocalAlbumRoute.Playback) viewModel.clearMessage()
        if (route == LocalAlbumRoute.Overview || route == LocalAlbumRoute.Library) viewModel.refresh()
    }
    BackHandler(enabled = backStack.size > 1, onBack = back)

    Box(modifier.fillMaxSize().padding(contentPadding)) {
        when (route) {
            LocalAlbumRoute.Overview -> LocalAlbumOverviewScreen(
                state = state,
                viewModel = viewModel,
                onOpenLibrary = { navigate(LocalAlbumRoute.Library) },
                onImport = { navigate(LocalAlbumRoute.Import) },
                onPlayback = { navigate(LocalAlbumRoute.Playback) },
                onBatch = { navigate(LocalAlbumRoute.Batch) },
                onMedia = { navigate(LocalAlbumRoute.Detail(it)) },
            )
            LocalAlbumRoute.Library -> DeviceLibraryScreen(state, back, { navigate(LocalAlbumRoute.Batch) }) {
                navigate(LocalAlbumRoute.Detail(it))
            }
            LocalAlbumRoute.Import -> PhoneImportScreen(
                sources = state.phoneSources,
                selectedSourceId = state.selectedPhoneSourceId,
                adaptations = state.adaptationSettings,
                drafts = state.conversionDrafts,
                onBack = backToOverview,
                onPickPhotos = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveSource = viewModel::removePhoneSource,
                onSelectSource = viewModel::selectPhoneSource,
                onNext = {
                    val nextUnconfigured = state.phoneSources.firstOrNull {
                        state.adaptationSettings[it.sourceId]?.isConfigured != true
                    }
                    if (nextUnconfigured != null) {
                        viewModel.selectPhoneSource(nextUnconfigured.sourceId)
                        navigate(LocalAlbumRoute.Adapt)
                    } else {
                        navigate(LocalAlbumRoute.SixColorPreview)
                    }
                },
            )
            LocalAlbumRoute.Adapt -> ImageAdaptScreen(
                source = state.selectedPhoneSource,
                settings = state.selectedAdaptation,
                onBack = back,
                onFitModeChange = { fitMode ->
                    state.selectedPhoneSource?.let { viewModel.updateAdaptation(it.sourceId, fitMode = fitMode) }
                },
                onRotate = {
                    state.selectedPhoneSource?.let { source ->
                        viewModel.updateAdaptation(
                            source.sourceId,
                            quarterTurnsClockwise = (state.selectedAdaptation.quarterTurnsClockwise + 1) % 4,
                        )
                    }
                },
                onNext = {
                    state.selectedPhoneSource?.let { current ->
                        viewModel.markAdaptationConfigured(current.sourceId)
                        val nextUnconfigured = state.phoneSources.firstOrNull { source ->
                            source.sourceId != current.sourceId &&
                                state.adaptationSettings[source.sourceId]?.isConfigured != true
                        }
                        if (nextUnconfigured != null) {
                            viewModel.selectPhoneSource(nextUnconfigured.sourceId)
                        } else {
                            navigate(LocalAlbumRoute.SixColorPreview)
                        }
                    }
                },
            )
            LocalAlbumRoute.SixColorPreview -> SixColorPreviewScreen(
                state = state,
                onBack = back,
                onAdjust = { navigate(LocalAlbumRoute.Adapt) },
                onPrevious = {
                    val current = state.phoneSources.indexOfFirst { it.sourceId == state.selectedPhoneSourceId }
                    if (state.phoneSources.isNotEmpty()) {
                        viewModel.selectPhoneSource(state.phoneSources[Math.floorMod(current - 1, state.phoneSources.size)].sourceId)
                    }
                },
                onNext = {
                    val current = state.phoneSources.indexOfFirst { it.sourceId == state.selectedPhoneSourceId }
                    if (state.phoneSources.isNotEmpty()) {
                        viewModel.selectPhoneSource(state.phoneSources[Math.floorMod(current + 1, state.phoneSources.size)].sourceId)
                    }
                },
                onGenerate = {
                    val requests = state.phoneSources.mapNotNull { source ->
                        state.adaptationSettings[source.sourceId]
                            ?.takeIf { it.isConfigured }
                            ?.let { settings -> LocalDraftRequest(source, settings, state.device.capabilities?.displayProfile) }
                    }
                    val ids = viewModel.queueConfiguredConversions(requests.map { it.source.sourceId })
                    if (ids.isNotEmpty()) {
                        navigate(LocalAlbumRoute.LocalConversion)
                        scope.launch {
                            requests.filter { it.source.sourceId in ids }.forEach { request ->
                                processLocalDraft(context, request, viewModel)
                            }
                        }
                    }
                },
            )
            LocalAlbumRoute.LocalConversion -> LocalConversionTaskScreen(
                state = state,
                onBack = back,
                onRetry = { sourceId ->
                    state.conversionDrafts[sourceId]?.let { draft ->
                        viewModel.queueConfiguredConversions(listOf(sourceId))
                        scope.launch {
                            processLocalDraft(
                                context,
                                LocalDraftRequest(draft.source, state.adaptationSettings.getValue(sourceId), draft.profile),
                                viewModel,
                            )
                        }
                    }
                },
                onCancelQueued = viewModel::cancelQueuedConversions,
                onSave = viewModel::submitDraftToDevice,
                onSaveAll = viewModel::saveAllReadyDrafts,
                // Finishing phone-side conversion returns to the local-album overview. Import is
                // a child route, so leaving it as the root would make its back arrow a no-op.
                onDone = backToOverview,
            )
            is LocalAlbumRoute.Detail -> state.media.firstOrNull { it.id == route.mediaId }?.let { media ->
                MediaDetailScreen(
                    state = state,
                    media = media,
                    onBack = back,
                    onDisplay = { afterDisplay ->
                        // Keep the user on the media detail page. It owns the real preview and
                        // observes the device job state inline rather than opening a second,
                        // implementation-oriented status page.
                        viewModel.display(media.id, afterDisplay)
                    },
                    onDelete = {
                        viewModel.delete(media.id) { result ->
                            if (result is DeviceCommandResult.Accepted) back()
                        }
                    },
                )
            }
            LocalAlbumRoute.Playback -> PlaybackSettingsScreen(state, viewModel, back)
            LocalAlbumRoute.Batch -> BatchManageScreen(state, viewModel, back)
        }
    }
}

private fun Context.toPhoneSource(uri: Uri): PhoneSource? = runCatching {
    val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }
                ?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
        ?: "未命名图片"
    val importsDir = File(filesDir, "phone_sources").apply { mkdirs() }
    val originalSafeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { "photo.jpg" }
    val sourceExtension = originalSafeName.substringAfterLast('.', "").lowercase()
    // The device's source+BIN admission contract stores source only as JPEG/PNG.  BMP is
    // supported by phone-side rendering, so normalize its stored source copy to PNG here.
    val safeName = if (sourceExtension in setOf("jpg", "jpeg", "png")) originalSafeName else "$originalSafeName.png"
    val localFile = File(importsDir, "${System.currentTimeMillis()}-${uri.hashCode()}-$safeName")
    if (sourceExtension in setOf("jpg", "jpeg", "png")) {
        contentResolver.openInputStream(uri)?.use { input -> localFile.outputStream().use(input::copyTo) } ?: return null
    } else {
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        localFile.outputStream().use { output ->
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "无法转换图片格式" }
        }
        bitmap.recycle()
    }
    val localUri = Uri.fromFile(localFile)
    val dimensions = localFile.inputStream().use { stream ->
        BitmapFactory.Options().also { options ->
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(stream, null, options)
        }
    }
    if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) return null
    val exifOrientation = ExifInterface(localFile).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val swapsDimensions = exifOrientation in setOf(
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
    )
    val width = if (swapsDimensions) dimensions.outHeight else dimensions.outWidth
    val height = if (swapsDimensions) dimensions.outWidth else dimensions.outHeight
    PhoneSource(
        sourceId = "picker-${localFile.name.hashCode()}",
        contentUri = localUri.toString(),
        displayName = displayName,
        widthPx = width,
        heightPx = height,
    )
}.getOrNull()

private suspend fun processLocalDraft(
    context: Context,
    request: LocalDraftRequest,
    viewModel: LocalAlbumViewModel,
) {
    runCatching {
        createLocalDraft(context, request) { stage ->
            viewModel.updateConversionStage(request.source.sourceId, stage)
        }
    }.onSuccess { output ->
        viewModel.completeConversion(
            sourceId = request.source.sourceId,
            previewUri = output.previewUri,
            candidateBinUri = output.candidateBinUri,
            frameBytes = output.frameBytes,
            algorithmVersion = output.algorithmVersion,
        )
    }.onFailure { error ->
        val message = when (error) {
            is OutOfMemoryError -> "手机内存不足，已停止此照片处理；请关闭其他应用后重试"
            is SecurityException -> "照片已不可访问，请重新选择此照片"
            else -> error.message ?: "本地转换失败，请重试"
        }
        viewModel.failConversion(request.source.sourceId, message)
    }
}

@Composable
fun rememberLocalAlbumDemoRuntime(session: DeviceSession? = null): LocalAlbumDemoRuntime {
    val appContext = LocalContext.current.applicationContext
    val factory = remember(appContext, session) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dependencies = LocalAlbumDemoDependencies.create(appContext, session ?: com.einkphoto.app.core.device.FakeDeviceSession())
                return LocalAlbumViewModel(
                    session = dependencies.session,
                    mediaRepository = dependencies.mediaRepository,
                    playbackRepository = dependencies.playbackRepository,
                    displayRepository = dependencies.displayRepository,
                    uploadRepository = dependencies.uploadRepository,
                    demoController = dependencies.demoController,
                ) as T
            }
        }
    }
    val albumViewModel: LocalAlbumViewModel = viewModel(factory = factory)
    return remember(albumViewModel) {
        LocalAlbumDemoRuntime(albumViewModel, albumViewModel.demoController)
    }
}

private val localAlbumBackStackSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<LocalAlbumRoute>, String>(
    save = { routes -> routes.map(::encodeRoute) },
    restore = { tokens ->
        val restored = tokens.map(::decodeRoute)
        mutableStateListOf<LocalAlbumRoute>().apply {
            if (restored.firstOrNull() == LocalAlbumRoute.Overview) addAll(restored) else add(LocalAlbumRoute.Overview)
        }
    },
)

private fun encodeRoute(route: LocalAlbumRoute): String = when (route) {
    LocalAlbumRoute.Overview -> "overview"
    LocalAlbumRoute.Library -> "library"
    LocalAlbumRoute.Import -> "import"
    LocalAlbumRoute.Adapt -> "adapt"
    LocalAlbumRoute.SixColorPreview -> "six_color_preview"
    LocalAlbumRoute.LocalConversion -> "local_conversion"
    LocalAlbumRoute.Playback -> "playback"
    LocalAlbumRoute.Batch -> "batch"
    is LocalAlbumRoute.Detail -> "detail:${route.mediaId.value}"
}

private fun decodeRoute(token: String): LocalAlbumRoute = when {
    token == "overview" -> LocalAlbumRoute.Overview
    token == "library" -> LocalAlbumRoute.Library
    token == "import" -> LocalAlbumRoute.Import
    token == "adapt" -> LocalAlbumRoute.Adapt
    token == "six_color_preview" -> LocalAlbumRoute.SixColorPreview
    token == "local_conversion" -> LocalAlbumRoute.LocalConversion
    token == "playback" -> LocalAlbumRoute.Playback
    token == "batch" -> LocalAlbumRoute.Batch
    token.startsWith("detail:") -> LocalAlbumRoute.Detail(com.einkphoto.app.feature.localalbum.model.MediaId(token.removePrefix("detail:")))
    // Older builds persisted display:<id>. That screen has been retired; returning to the
    // overview prevents process restoration from reopening an obsolete task-status page.
    token.startsWith("display:") -> LocalAlbumRoute.Overview
    else -> LocalAlbumRoute.Overview
}

@Composable
fun LocalAlbumDemoHost(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    runtime: LocalAlbumDemoRuntime = rememberLocalAlbumDemoRuntime(),
) {
    LocalAlbumHost(
        viewModel = runtime.viewModel,
        contentPadding = contentPadding,
        modifier = modifier,
        demoController = runtime.demoController,
    )
}

@Preview(name = "本地相册 · 第一版", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LocalAlbumHostPreview() {
    EInkPhotoTheme(darkTheme = false) {
        LocalAlbumDemoHost(PaddingValues())
    }
}
