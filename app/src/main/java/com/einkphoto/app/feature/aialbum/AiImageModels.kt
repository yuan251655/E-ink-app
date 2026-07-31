package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.core.device.DeviceJob

data class AiImageItem(
    val id: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val sizeBytes: Long?,
    val revision: Long,
    val previewUri: String?,
    val sourceAvailable: Boolean,
    val prompt: String? = null,
    val model: String? = null,
    val source: String? = null,
    val syncStatus: String? = null,
)

enum class AiImageLoadState { Idle, Loading, Ready, Offline, Error }

data class AiImageUiState(
    val loadState: AiImageLoadState = AiImageLoadState.Idle,
    val images: List<AiImageItem> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val activeJob: DeviceJob? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

enum class AiImageExportKind { Source, SixColorPreview }

data class AiImageExportResult(val kind: AiImageExportKind, val displayName: String)
