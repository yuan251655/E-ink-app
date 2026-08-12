package com.einkphoto.app.feature.settings.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioRepository {
    val snapshot: StateFlow<AudioSnapshot>
    suspend fun refresh(): AudioActionResult
    suspend fun save(masterVolume: Int, muted: Boolean): AudioActionResult
    suspend fun testSpeaker(): AudioActionResult
}
