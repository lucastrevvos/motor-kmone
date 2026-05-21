package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticCaptureMicroBurstTimingTest {
    @Test
    fun floatingOver99_usesConservativeFrameDelays() {
        assertEquals(
            listOf(0L, 700L, 1200L),
            AutomaticCaptureMicroBurstTiming.frameDelaysMs(TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC)
        )
    }

    @Test
    fun selfOverlayCooldown_appliesLongerDelayBeforeNextFrame() {
        assertEquals(1_900L, AutomaticCaptureMicroBurstTiming.applySelfOverlayCooldown(1_000L))
    }

    @Test
    fun busyCode3_usesLongerRetryDelay() {
        assertEquals(500L, AutomaticCaptureMicroBurstTiming.busyRetryDelayMs())
    }
}
