package com.einkphoto.app.core.device

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.os.SystemClock

object DeviceGlobalNoticeBus {
    private val mutableNotices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices = mutableNotices.asSharedFlow()
    private var lastCooldownNoticeAt = 0L

    @Synchronized
    fun displayCooldown(remainingSeconds: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCooldownNoticeAt < 5_000L) return
        lastCooldownNoticeAt = now
        mutableNotices.tryEmit("电子纸刚完成刷新，请等待 ${remainingSeconds.coerceAtLeast(1)} 秒后再试")
    }
}
