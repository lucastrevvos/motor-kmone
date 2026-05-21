package com.lucastrevvos.kmonemotor.radar.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCaptureMicroBurstSchedulerTest {
    @Test
    fun sameSourceAndFrame_cannotBeScheduledTwiceWhilePending() {
        val backend = FakeBackend()
        val scheduler = AutomaticCaptureMicroBurstScheduler(backend)

        val first = scheduler.schedule("source", 1, 200L) {}
        val second = scheduler.schedule("source", 1, 0L) {}

        assertTrue(first)
        assertFalse(second)
        assertEquals(AutomaticCaptureMicroBurstScheduler.FrameState.PENDING, scheduler.currentState("source", 1))
    }

    @Test
    fun frameInRetrying_blocksDuplicateImmediateStart() {
        val backend = FakeBackend()
        val scheduler = AutomaticCaptureMicroBurstScheduler(backend)

        assertTrue(
            scheduler.schedule(
                sourceObservationId = "source",
                frameIndex = 1,
                delayMs = 500L,
                state = AutomaticCaptureMicroBurstScheduler.FrameState.RETRYING
            ) {}
        )

        assertFalse(scheduler.schedule("source", 1, 0L) {})
        assertEquals(AutomaticCaptureMicroBurstScheduler.FrameState.RETRYING, scheduler.currentState("source", 1))
    }

    @Test
    fun burstCancellation_marksPendingFramesCancelled() {
        val backend = FakeBackend()
        val scheduler = AutomaticCaptureMicroBurstScheduler(backend)

        scheduler.schedule("source", 1, 200L) {}
        scheduler.schedule("source", 2, 450L) {}

        val cancelled = scheduler.cancelPending("source")

        assertEquals(2, cancelled)
        assertEquals(AutomaticCaptureMicroBurstScheduler.FrameState.CANCELLED, scheduler.currentState("source", 1))
        assertEquals(AutomaticCaptureMicroBurstScheduler.FrameState.CANCELLED, scheduler.currentState("source", 2))
    }

    private class FakeBackend : AutomaticCaptureMicroBurstScheduler.Backend {
        override fun postDelayed(runnable: Runnable, delayMs: Long) = Unit
        override fun cancel(runnable: Runnable) = Unit
    }
}
