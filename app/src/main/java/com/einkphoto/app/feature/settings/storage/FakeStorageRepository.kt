package com.einkphoto.app.feature.settings.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deterministic in-memory repository for Settings previews and emulator-only UI tests. */
class FakeStorageRepository(
    initialSnapshot: StorageSnapshot = StorageSnapshot(),
) : StorageRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)
    override val snapshot: StateFlow<StorageSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh(): StorageActionResult = StorageActionResult.Accepted

    override suspend fun remount(): StorageActionResult = StorageActionResult.Accepted

    fun replaceSnapshot(value: StorageSnapshot) {
        mutableSnapshot.value = value
    }
}
