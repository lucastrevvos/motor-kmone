package com.lucastrevvos.kmonemotor.radar.orchestrator

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

interface StabilizationScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): CancellationHandle

    interface CancellationHandle {
        fun cancel()
    }
}

class DefaultStabilizationScheduler : StabilizationScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "kmone-stabilization").apply { isDaemon = true }
    }

    override fun schedule(delayMs: Long, action: () -> Unit): StabilizationScheduler.CancellationHandle {
        val future: ScheduledFuture<*> = executor.schedule(action, delayMs, TimeUnit.MILLISECONDS)
        return object : StabilizationScheduler.CancellationHandle {
            override fun cancel() {
                future.cancel(false)
            }
        }
    }
}
