package com.lucastrevvos.kmonemotor.radar.signals

import android.view.accessibility.AccessibilityEvent
import com.lucastrevvos.kmonemotor.radar.android.WindowStackSnapshot
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarSignal
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

class RadarSignalLayer(
    private val windowStackSignalExtractor: WindowStackSignalExtractor = WindowStackSignalExtractor(),
    private val uberSignalExtractor: UberSignalExtractor = UberSignalExtractor(),
    private val ninetyNineSignalExtractor: NinetyNineSignalExtractor = NinetyNineSignalExtractor()
) {
    private val lastSignalTimestamps = mutableMapOf<String, Long>()

    fun evaluate(
        event: AccessibilityEvent,
        eventReceivedAtMs: Long,
        windowStackSnapshot: WindowStackSnapshot,
        nodeTreeSignature: NodeTreeSignature,
        floatingKind: FloatingWindowKind
    ): List<RadarSignal> {
        val signalEmittedAtMs = windowStackSnapshot.timestampMs
        val rawSignals = buildList {
            addAll(windowStackSignalExtractor.extract(windowStackSnapshot, eventReceivedAtMs, signalEmittedAtMs))
            addAll(uberSignalExtractor.extract(windowStackSnapshot, nodeTreeSignature, floatingKind, eventReceivedAtMs, signalEmittedAtMs))
            addAll(ninetyNineSignalExtractor.extract(nodeTreeSignature, windowStackSnapshot, floatingKind, eventReceivedAtMs, signalEmittedAtMs))
        }
        val signals = rawSignals.filter { signal ->
            shouldEmit(signal, windowStackSnapshot, nodeTreeSignature)
        }

        RadarLogger.d(
            "KM_V2_SIGNAL",
            "KM_V2_SIGNAL_EVENT_RECEIVED",
            "type" to event.eventType,
            "package" to event.packageName,
            "dominantPackage" to windowStackSnapshot.topDominantWindow?.packageName,
            "floatingPackage" to windowStackSnapshot.topFloatingWindow?.packageName,
            "floatingCoverage" to windowStackSnapshot.topFloatingWindow?.coverage,
            "floatingKind" to floatingKind,
            "nodeTreePackage" to nodeTreeSignature.packageName,
            "nodeCount" to nodeTreeSignature.nodeCount,
            "visibleTextNodeCount" to nodeTreeSignature.visibleTextNodeCount,
            "knownStateTexts" to nodeTreeSignature.knownStateTexts.joinToString(","),
            "signalCount" to signals.size
        )

        signals.forEach { signal ->
            val type = signalType(signal)
            RadarLogger.d(
                "KM_V2_SIGNAL",
                "KM_V2_SIGNAL_EMITTED",
                "type" to type,
                "dominantPackage" to windowStackSnapshot.topDominantWindow?.packageName,
                "floatingPackage" to windowStackSnapshot.topFloatingWindow?.packageName,
                "floatingCoverage" to windowStackSnapshot.topFloatingWindow?.coverage,
                "floatingKind" to floatingKind,
                "nodeTreePackage" to nodeTreeSignature.packageName,
                "nodeCount" to nodeTreeSignature.nodeCount,
                "visibleTextNodeCount" to nodeTreeSignature.visibleTextNodeCount,
                "knownStateTexts" to nodeTreeSignature.knownStateTexts.joinToString(",")
            )
            RadarLogger.i(
                "KM_V2_SIGNAL",
                "KM_V2_LATENCY_SIGNAL",
                "type" to type,
                "eventToSignalMs" to (signalTimestamp(signal) - eventReceivedAtMs)
            )
            RadarDebugStore.updateLastSignal(
                "type=$type dominant=${windowStackSnapshot.topDominantWindow?.packageName} floating=${windowStackSnapshot.topFloatingWindow?.packageName} coverage=${windowStackSnapshot.topFloatingWindow?.coverage} kind=$floatingKind nodePackage=${nodeTreeSignature.packageName}"
            )
        }
        return signals
    }

    private fun shouldEmit(
        signal: RadarSignal,
        windowStackSnapshot: WindowStackSnapshot,
        nodeTreeSignature: NodeTreeSignature
    ): Boolean {
        val signalType = signalType(signal)
        val dominantPackage = windowStackSnapshot.topDominantWindow?.packageName
        val floatingPackage = windowStackSnapshot.topFloatingWindow?.packageName
        val timestampMs = signalTimestamp(signal)
        val dedupeKey = "$signalType|$dominantPackage|$floatingPackage"
        val lastTimestamp = lastSignalTimestamps[dedupeKey]
        if (lastTimestamp != null && timestampMs - lastTimestamp < SIGNAL_DEDUPE_WINDOW_MS) {
            RadarLogger.d(
                "KM_V2_SIGNAL",
                "KM_V2_SIGNAL_DEDUPED",
                "type" to signalType,
                "dominantPackage" to dominantPackage,
                "floatingPackage" to floatingPackage,
                "reason" to "same_type_same_window_under_${SIGNAL_DEDUPE_WINDOW_MS}ms"
            )
            return false
        }

        if (signal is RadarSignal.DominantWindowChanged) {
            lastSignalTimestamps[dedupeKey] = timestampMs
            RadarLogger.d(
                "KM_V2_SIGNAL",
                "KM_V2_SIGNAL_IGNORED",
                "type" to signalType,
                "dominantPackage" to dominantPackage,
                "floatingPackage" to floatingPackage,
                "nodeTreePackage" to nodeTreeSignature.packageName,
                "reason" to "informative_only"
            )
            return true
        }

        lastSignalTimestamps[dedupeKey] = timestampMs
        return true
    }

    private fun signalType(signal: RadarSignal): String {
        return when (signal) {
            is RadarSignal.UberFloatingOverOtherApp -> "UBER_FLOATING_OVER_OTHER_APP"
            is RadarSignal.UberStateChanged -> "UBER_STATE_CHANGED"
            is RadarSignal.NinetyNineTreeStructureChanged -> "NINETY_NINE_TREE_STRUCTURE_CHANGED"
            is RadarSignal.DominantWindowChanged -> "DOMINANT_WINDOW_CHANGED"
        }
    }

    private fun signalTimestamp(signal: RadarSignal): Long {
        return when (signal) {
            is RadarSignal.UberFloatingOverOtherApp -> signal.signalEmittedAtMs
            is RadarSignal.UberStateChanged -> signal.signalEmittedAtMs
            is RadarSignal.NinetyNineTreeStructureChanged -> signal.signalEmittedAtMs
            is RadarSignal.DominantWindowChanged -> signal.signalEmittedAtMs
        }
    }

    private companion object {
        const val SIGNAL_DEDUPE_WINDOW_MS = 500L
    }
}
