package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.core.device.DeviceCommandResult
import com.einkphoto.app.core.device.DeviceJob
import com.einkphoto.app.core.device.DeviceJobId
import kotlinx.coroutines.flow.StateFlow

interface AiImageRepository {
    val images: StateFlow<List<AiImageItem>>
    val activeJob: StateFlow<DeviceJob?>
    val hasMore: StateFlow<Boolean>

    suspend fun refresh(): DeviceCommandResult<Unit>
    suspend fun loadMore(): DeviceCommandResult<Unit>
    suspend fun display(mediaId: String): DeviceCommandResult<DeviceJobId>
    suspend fun delete(mediaId: String): DeviceCommandResult<Unit>
    suspend fun exportToPhone(mediaId: String): DeviceCommandResult<AiImageExportResult>
    fun close()
}
