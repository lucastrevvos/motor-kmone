package com.lucastrevvos.kmonemotor.radar.core

import java.util.concurrent.atomic.AtomicLong

object AnalysisEpochController {
    private val epoch = AtomicLong(0)

    fun current(): Long = epoch.get()

    fun bumpManualEpoch(): Long = epoch.incrementAndGet()

    fun isCurrent(value: Long): Boolean = value == epoch.get()
}
