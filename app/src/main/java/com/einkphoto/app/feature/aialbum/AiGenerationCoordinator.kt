package com.einkphoto.app.feature.aialbum

import kotlinx.coroutines.sync.Mutex

/** One paid Seedream request at a time across App UI and the voice foreground service. */
object AiGenerationCoordinator {
    private val mutex = Mutex()
    private val attemptedRequestIds = LinkedHashSet<String>()

    suspend fun <T> run(requestId: String, block: suspend () -> T): T {
        check(mutex.tryLock()) { "ai_generation_busy" }
        synchronized(attemptedRequestIds) {
            if (!attemptedRequestIds.add(requestId)) {
                mutex.unlock()
                error("ai_generation_already_submitted")
            }
            while (attemptedRequestIds.size > 64) attemptedRequestIds.remove(attemptedRequestIds.first())
        }
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
