package com.lucastrevvos.kmonemotor.radar.signals

import com.lucastrevvos.kmonemotor.radar.android.WindowStackSnapshot
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarConfig
import com.lucastrevvos.kmonemotor.radar.core.RadarSignal
import com.lucastrevvos.kmonemotor.radar.core.UberReadableState
import java.util.Locale

class UberSignalExtractor(
    private val config: RadarConfig = RadarConfig.Default
) {
    private var previousState: UberReadableState? = null
    private var previousSignature: NodeTreeSignature? = null

    fun extract(
        snapshot: WindowStackSnapshot,
        nodeTreeSignature: NodeTreeSignature,
        floatingKind: FloatingWindowKind,
        eventReceivedAtMs: Long,
        signalEmittedAtMs: Long
    ): List<RadarSignal> {
        val signals = mutableListOf<RadarSignal>()
        val dominantPackage = snapshot.topDominantWindow?.packageName
        val floating = snapshot.topFloatingWindow

        if (dominantPackage != RadarConfig.UBER_DRIVER_PACKAGE &&
            floating?.packageName == RadarConfig.UBER_DRIVER_PACKAGE &&
            floating.coverage >= config.uberFloatingMinCoverage &&
            floatingKind != FloatingWindowKind.UNKNOWN_FLOATING
        ) {
            signals += RadarSignal.UberFloatingOverOtherApp(
                dominantPackage = dominantPackage,
                floatingPackage = floating.packageName,
                floatingBounds = floating.bounds,
                floatingCoverage = floating.coverage,
                floatingKind = floatingKind,
                eventReceivedAtMs = eventReceivedAtMs,
                signalEmittedAtMs = signalEmittedAtMs
            )
        }

        val currentState = detectState(nodeTreeSignature)
        if (currentState != null && currentState != previousState) {
            val previousKnownStateTexts = previousSignature?.knownStateTexts.orEmpty()
            signals += RadarSignal.UberStateChanged(
                previousState = previousState,
                currentState = currentState,
                dominantPackage = dominantPackage,
                floatingPackage = floating?.packageName,
                nodeTreePackage = nodeTreeSignature.packageName,
                nodeCount = nodeTreeSignature.nodeCount,
                visibleTextNodeCount = nodeTreeSignature.visibleTextNodeCount,
                bottomHalfTextNodeCount = nodeTreeSignature.bottomHalfTextNodeCount,
                numericTextNodeCount = nodeTreeSignature.numericTextNodeCount,
                buttonLikeNodeCount = nodeTreeSignature.buttonLikeNodeCount,
                knownStateTexts = nodeTreeSignature.knownStateTexts,
                previousKnownStateTexts = previousKnownStateTexts,
                previousNodeCount = previousSignature?.nodeCount,
                previousVisibleTextNodeCount = previousSignature?.visibleTextNodeCount,
                floatingBounds = floating?.bounds,
                floatingKind = floatingKind,
                eventReceivedAtMs = eventReceivedAtMs,
                signalEmittedAtMs = signalEmittedAtMs
            )
            previousState = currentState
        }
        if (nodeTreeSignature.packageName == RadarConfig.UBER_DRIVER_PACKAGE) {
            previousSignature = nodeTreeSignature
        }

        return signals
    }

    fun currentState(): UberReadableState? = previousState

    private fun detectState(signature: NodeTreeSignature): UberReadableState? {
        if (signature.packageName != RadarConfig.UBER_DRIVER_PACKAGE) {
            return null
        }
        val texts = signature.knownStateTexts.map { it.lowercase(Locale.ROOT) }
        return when {
            texts.any { it.contains("offline") } -> UberReadableState.OFFLINE
            texts.any { it.contains("procurando corridas") || it.contains("buscando corridas") } -> UberReadableState.SEARCHING_RIDES
            texts.any { it.contains("online") } -> UberReadableState.ONLINE_IDLE
            else -> UberReadableState.UNKNOWN
        }
    }
}
