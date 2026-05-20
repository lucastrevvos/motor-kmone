package com.lucastrevvos.kmonemotor.radar.recovery

import android.os.Handler
import android.os.Looper

class AutomaticCaptureBurstScheduler(
    private val backend: Backend = HandlerBackend()
) {
    private var pending: ScheduledTask? = null

    fun schedule(
        token: String,
        delayMs: Long,
        task: () -> Unit
    ): String? {
        val replacedToken = pending?.token
        pending?.let { backend.cancel(it.runnable) }
        val runnable = Runnable {
            if (pending?.token != token) return@Runnable
            pending = null
            task()
        }
        pending = ScheduledTask(token, runnable)
        backend.postDelayed(runnable, delayMs)
        return replacedToken
    }

    fun cancel(reason: String? = null): String? {
        val scheduled = pending ?: return null
        backend.cancel(scheduled.runnable)
        pending = null
        return reason
    }

    fun hasPending(): Boolean = pending != null

    fun destroy() {
        cancel()
    }

    interface Backend {
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun cancel(runnable: Runnable)
    }

    private data class ScheduledTask(
        val token: String,
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
