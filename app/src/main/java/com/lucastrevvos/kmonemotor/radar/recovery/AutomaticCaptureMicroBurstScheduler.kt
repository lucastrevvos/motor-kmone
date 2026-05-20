package com.lucastrevvos.kmonemotor.radar.recovery

import android.os.Handler
import android.os.Looper

class AutomaticCaptureMicroBurstScheduler(
    private val backend: Backend = HandlerBackend()
) {
    private val pendingBySource = linkedMapOf<String, MutableList<ScheduledTask>>()

    @Synchronized
    fun schedule(
        sourceObservationId: String,
        frameIndex: Int,
        delayMs: Long,
        task: () -> Unit
    ) {
        val runnable = Runnable {
            synchronized(this) {
                pendingBySource[sourceObservationId]?.removeAll { it.frameIndex == frameIndex }
                if (pendingBySource[sourceObservationId].isNullOrEmpty()) {
                    pendingBySource.remove(sourceObservationId)
                }
            }
            task()
        }
        pendingBySource.getOrPut(sourceObservationId) { mutableListOf() }
            .add(ScheduledTask(frameIndex, runnable))
        backend.postDelayed(runnable, delayMs)
    }

    @Synchronized
    fun cancel(sourceObservationId: String): Int {
        val pending = pendingBySource.remove(sourceObservationId).orEmpty()
        pending.forEach { backend.cancel(it.runnable) }
        return pending.size
    }

    @Synchronized
    fun destroy() {
        pendingBySource.values.flatten().forEach { backend.cancel(it.runnable) }
        pendingBySource.clear()
    }

    interface Backend {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun cancel(runnable: Runnable)
    }

    private data class ScheduledTask(
        val frameIndex: Int,
        val runnable: Runnable
    )

    private class HandlerBackend : Backend {
        private val handler = Handler(Looper.getMainLooper())

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            handler.postDelayed(runnable, delayMs)
        }

        override fun cancel(runnable: Runnable) {
            handler.removeCallbacks(runnable)
        }
    }
}
