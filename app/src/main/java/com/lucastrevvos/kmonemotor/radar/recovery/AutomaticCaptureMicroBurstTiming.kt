package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

object AutomaticCaptureMicroBurstTiming {
    fun frameDelaysMs(triggerSource: TriggerSource): List<Long> {
        return when (triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> listOf(0L, 700L, 1200L)
            else -> listOf(0L, 200L, 450L)
        }
    }

    fun busyRetryDelayMs(): Long = 500L

    fun applySelfOverlayCooldown(nowMs: Long): Long = nowMs + 900L
}
