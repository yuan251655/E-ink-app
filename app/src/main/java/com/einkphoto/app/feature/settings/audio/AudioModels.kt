package com.einkphoto.app.feature.settings.audio

data class AudioSnapshot(
    val masterVolume: Int = 70,
    val muted: Boolean = false,
    val outputEnabled: Boolean = false,
    val playing: Boolean = false,
    val source: String = "idle",
    val connected: Boolean = false,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val lastErrorMessage: String? = null,
)

sealed interface AudioActionResult {
    data object Accepted : AudioActionResult
    data class Rejected(val code: String, val message: String) : AudioActionResult
}
