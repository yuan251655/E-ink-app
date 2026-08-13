package com.einkphoto.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Smooth UI countdown, recalibrated whenever the device heartbeat supplies a new value. */
@Composable
fun rememberDisplayCooldown(deviceSeconds: Int): Int {
    var remaining by remember { mutableIntStateOf(deviceSeconds.coerceAtLeast(0)) }
    LaunchedEffect(deviceSeconds) {
        remaining = deviceSeconds.coerceAtLeast(0)
        while (remaining > 0) {
            delay(1_000L)
            remaining--
        }
    }
    return remaining
}
