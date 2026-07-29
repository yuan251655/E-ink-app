package com.einkphoto.app.feature.settings.storage

import kotlinx.coroutines.flow.StateFlow

/**
 * Settings-facing port for TF health only. Media browse/delete/upload remains in local-album
 * repositories; neither implementation grants the App direct access to TF files.
 */
interface StorageRepository {
    val snapshot: StateFlow<StorageSnapshot>

    suspend fun refresh(): StorageActionResult

    /** Safe device-side re-detection/remount. Firmware must reject this with `storage_busy` during a transaction. */
    suspend fun remount(): StorageActionResult
}
