package com.lucastrevvos.kmonemotor.radar.core

fun interface RadarClock {
    fun nowMs(): Long

    companion object {
        val System: RadarClock = RadarClock { java.lang.System.currentTimeMillis() }
    }
}
