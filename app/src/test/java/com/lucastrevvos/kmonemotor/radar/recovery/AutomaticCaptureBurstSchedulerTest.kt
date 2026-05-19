package com.lucastrevvos.kmonemotor.radar.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCaptureBurstSchedulerTest {
    @Test
    fun scheduleStoresPendingAndExecutes() {
        val backend = FakeBackend()
        val scheduler = AutomaticCaptureBurstScheduler(backend)
        var executed = false

        scheduler.schedule("a", 300L) { executed = true }

        assertTrue(scheduler.hasPending())
        assertEquals(300L, backend.lastDelayMs)
        backend.runLatest()
        assertTrue(executed)
        assertFalse(scheduler.hasPending())
    }

    @Test
    fun cancelRemovesPendingTask() {
        val backend = FakeBackend()
        val scheduler = AutomaticCaptureBurstScheduler(backend)

        scheduler.schedule("a", 300L) {}
        scheduler.cancel("manual")

        assertFalse(scheduler.hasPending())
        assertTrue(backend.cancelled)
    }

    @Test
    fun newScheduleReplacesPreviousTask() {
        val backend = FakeBackend()
        val scheduler = AutomaticCaptureBurstScheduler(backend)
        var executed = ""

        scheduler.schedule("a", 300L) { executed = "first" }
        scheduler.schedule("b", 300L) { executed = "second" }
        backend.runLatest()

        assertEquals("second", executed)
        assertTrue(backend.cancelled)
    }

    private class FakeBackend : AutomaticCaptureBurstScheduler.Backend {
        var lastRunnable: Runnable? = null
        var lastDelayMs: Long? = null
        var cancelled = false

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            lastRunnable = runnable
            lastDelayMs = delayMs
        }

        override fun cancel(runnable: Runnable) {
            cancelled = true
            if (lastRunnable === runnable) {
                lastRunnable = null
            }
        }

        fun runLatest() {
            lastRunnable?.run()
        }
    }
}
