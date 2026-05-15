package com.lucastrevvos.kmonemotor.radar.signals

import com.lucastrevvos.kmonemotor.radar.android.WindowStackSnapshot
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarConfig
import com.lucastrevvos.kmonemotor.radar.core.RadarSignal

class NinetyNineSignalExtractor {
    private var previousSignature: NodeTreeSignature? = null

    fun extract(
        signature: NodeTreeSignature,
        windowStackSnapshot: WindowStackSnapshot,
        floatingKind: FloatingWindowKind,
        eventReceivedAtMs: Long,
        signalEmittedAtMs: Long
    ): List<RadarSignal> {
        val isNinetyNinePackage = signature.packageName == RadarConfig.NINETY_NINE_DRIVER_PACKAGE ||
            signature.packageName == RadarConfig.NINETY_NINE_DRIVER_LEGACY_PACKAGE
        if (!isNinetyNinePackage) {
            previousSignature = signature
            return emptyList()
        }

        val hasMeaningfulChange = previousSignature == null || !signature.roughlyMatches(previousSignature)
        previousSignature = signature
        if (!hasMeaningfulChange) return emptyList()

        return listOf(
            RadarSignal.NinetyNineTreeStructureChanged(
                signature = signature,
                dominantPackage = windowStackSnapshot.topDominantWindow?.packageName,
                floatingPackage = windowStackSnapshot.topFloatingWindow?.packageName,
                floatingBounds = windowStackSnapshot.topFloatingWindow?.bounds,
                floatingKind = floatingKind,
                eventReceivedAtMs = eventReceivedAtMs,
                signalEmittedAtMs = signalEmittedAtMs
            )
        )
    }
}
