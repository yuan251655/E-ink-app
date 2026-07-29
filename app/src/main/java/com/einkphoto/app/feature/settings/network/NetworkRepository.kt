package com.einkphoto.app.feature.settings.network

import kotlinx.coroutines.flow.StateFlow

sealed interface NetworkActionResult {
    data object Accepted : NetworkActionResult
    data class Rejected(val code: String, val message: String) : NetworkActionResult
}

interface NetworkRepository {
    val snapshot: StateFlow<NetworkSnapshot>
    suspend fun refresh(): NetworkActionResult
    suspend fun scan24Ghz(): Result<List<WifiNetwork>>
    suspend fun testAndSaveSta(draft: StaConfigDraft): NetworkActionResult
    suspend fun disableSta(): NetworkActionResult
    suspend fun saveAp(draft: ApConfigDraft): NetworkActionResult
    suspend fun restoreDefaultAp(): NetworkActionResult
}
