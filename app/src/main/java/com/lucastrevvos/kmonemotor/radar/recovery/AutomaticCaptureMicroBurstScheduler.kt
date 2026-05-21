package com.lucastrevvos.kmonemotor.radar.recovery

import android.os.Handler
import android.os.Looper

class AutomaticCaptureMicroBurstScheduler(
    private val backend: Backend = HandlerBackend()
) {
    private val framesBySource = linkedMapOf<String, MutableMap<Int, ScheduledTask>>()

    @Synchronized
    fun schedule(
        sourceObservationId: String,
        frameIndex: Int,
        delayMs: Long,
        state: FrameState = FrameState.PENDING,
        task: () -> Unit
    ): Boolean {
        val sourceFrames = framesBySource.getOrPut(sourceObservationId) { linkedMapOf() }
        val existing = sourceFrames[frameIndex]
        if (existing?.state in setOf(FrameState.PENDING, FrameState.RUNNING, FrameState.RETRYING)) {
            return false
        }
        val runnable = Runnable {
            task()
        }
        sourceFrames[frameIndex] = ScheduledTask(frameIndex, runnable, state)
        backend.postDelayed(runnable, delayMs)
        return true
    }

    @Synchronized
    fun currentState(sourceObservationId: String, frameIndex: Int): FrameState? {
        return framesBySource[sourceObservationId]?.get(frameIndex)?.state
    }

    @Synchronized
    fun markRunning(sourceObservationId: String, frameIndex: Int): Boolean {
        val task = framesBySource[sourceObservationId]?.get(frameIndex) ?: return false
        if (task.state !in setOf(FrameState.PENDING, FrameState.RETRYING)) {
            return false
        }
        task.state = FrameState.RUNNING
        return true
    }

    @Synchronized
    fun markState(sourceObservationId: String, frameIndex: Int, state: FrameState) {
        val task = framesBySource[sourceObservationId]?.get(frameIndex) ?: return
        task.state = state
    }

    @Synchronized
    fun cancelPending(sourceObservationId: String): Int {
        val sourceFrames = framesBySource[sourceObservationId].orEmpty()
        var cancelled = 0
        sourceFrames.values.forEach { task ->
            if (task.state in setOf(FrameState.PENDING, FrameState.RETRYING)) {
                backend.cancel(task.runnable)
                task.state = FrameState.CANCELLED
                cancelled++
            }
        }
        return cancelled
    }

    @Synchronized
    fun cancel(sourceObservationId: String): Int {
        val sourceFrames = framesBySource.remove(sourceObservationId).orEmpty()
        sourceFrames.values.forEach { task ->
            backend.cancel(task.runnable)
            task.state = FrameState.CANCELLED
        }
        return sourceFrames.size
    }

    @Synchronized
    fun destroy() {
        framesBySource.values.flatMap { it.values }.forEach { task ->
            backend.cancel(task.runnable)
            task.state = FrameState.CANCELLED
        }
        framesBySource.clear()
    }

    enum class FrameState {
        PENDING,
        RUNNING,
        RETRYING,
        DONE,
        FAILED,
        CANCELLED
    }

    interface Backend {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun cancel(runnable: Runnable)
    }

    private data class ScheduledTask(
        val frameIndex: Int,
        val runnable: Runnable,
        var state: FrameState
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
