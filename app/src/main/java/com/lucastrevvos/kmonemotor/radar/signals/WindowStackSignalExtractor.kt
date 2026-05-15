package com.lucastrevvos.kmonemotor.radar.signals

import com.lucastrevvos.kmonemotor.radar.android.WindowStackSnapshot
import com.lucastrevvos.kmonemotor.radar.core.RadarSignal

class WindowStackSignalExtractor {
    private var previousDominantPackage: String? = null
    private var previousFloatingPackage: String? = null

    fun extract(snapshot: WindowStackSnapshot, eventReceivedAtMs: Long, signalEmittedAtMs: Long): List<RadarSignal> {
        val dominantPackage = snapshot.topDominantWindow?.packageName
        val floatingPackage = snapshot.topFloatingWindow?.packageName
        val changed = dominantPackage != previousDominantPackage || floatingPackage != previousFloatingPackage
        previousDominantPackage = dominantPackage
        previousFloatingPackage = floatingPackage

        if (!changed) return emptyList()
        return listOf(
            RadarSignal.DominantWindowChanged(
                dominantPackage = dominantPackage,
                floatingPackage = floatingPackage,
                eventReceivedAtMs = eventReceivedAtMs,
                signalEmittedAtMs = signalEmittedAtMs
            )
        )
    }
}
