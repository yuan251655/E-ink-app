package com.einkphoto.app.feature.settings.storage

/** User-readable projection of the device-owned StorageService health state. */
enum class StorageHealth {
    Ready,
    Degraded,
    Missing,
    ErrorBackoff,
    Unknown,
}

data class StorageSnapshot(
    val apiVersion: String = "v1",
    val revision: Long = 0L,
    val health: StorageHealth = StorageHealth.Unknown,
    val mounted: Boolean = false,
    val readable: Boolean = false,
    val writable: Boolean = false,
    val totalBytes: Long? = null,
    val freeBytes: Long? = null,
    val localAlbumItemCount: Int? = null,
    val localAlbumUsageBytes: Long? = null,
    val stagingItemCount: Int? = null,
    val stagingUsageBytes: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    /** Elapsed durations from the device; they are not wall-clock timestamps. */
    val lastCheckAgeSeconds: Long? = null,
    val lastRemountAgeSeconds: Long? = null,
)

sealed interface StorageActionResult {
    data object Accepted : StorageActionResult
    data class Rejected(val code: String, val message: String) : StorageActionResult
}
