package com.lucastrevvos.kmonemotor.radar.core

import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature

sealed class RadarSignal : RadarEvent {
    data class DominantWindowChanged(
        val dominantPackage: String?,
        val floatingPackage: String?,
        val eventReceivedAtMs: Long,
        val signalEmittedAtMs: Long
    ) : RadarSignal()

    data class UberFloatingOverOtherApp(
        val dominantPackage: String?,
        val floatingPackage: String?,
        val floatingBounds: String?,
        val floatingCoverage: Double,
        val floatingKind: FloatingWindowKind,
        val eventReceivedAtMs: Long,
        val signalEmittedAtMs: Long
    ) : RadarSignal()

    data class UberStateChanged(
        val previousState: UberReadableState?,
        val currentState: UberReadableState?,
        val dominantPackage: String?,
        val floatingPackage: String?,
        val nodeTreePackage: String?,
        val nodeCount: Int,
        val visibleTextNodeCount: Int,
        val bottomHalfTextNodeCount: Int,
        val numericTextNodeCount: Int,
        val buttonLikeNodeCount: Int,
        val knownStateTexts: List<String>,
        val previousKnownStateTexts: List<String>,
        val previousNodeCount: Int?,
        val previousVisibleTextNodeCount: Int?,
        val floatingBounds: String?,
        val floatingKind: FloatingWindowKind,
        val eventReceivedAtMs: Long,
        val signalEmittedAtMs: Long
    ) : RadarSignal()

    data class NinetyNineTreeStructureChanged(
        val signature: NodeTreeSignature,
        val dominantPackage: String?,
        val floatingPackage: String?,
        val floatingBounds: String?,
        val floatingKind: FloatingWindowKind,
        val eventReceivedAtMs: Long,
        val signalEmittedAtMs: Long
    ) : RadarSignal()
}
