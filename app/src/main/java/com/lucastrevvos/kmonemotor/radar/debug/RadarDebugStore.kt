package com.lucastrevvos.kmonemotor.radar.debug

import com.lucastrevvos.kmonemotor.radar.android.WindowStackSnapshot
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.OperationalAppState
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest
import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class RadarDebugState(
    val serviceActive: Boolean = false,
    val topDominantWindowPackage: String? = null,
    val topFloatingWindowPackage: String? = null,
    val floatingCoverage: Double? = null,
    val floatingKind: FloatingWindowKind = FloatingWindowKind.UNKNOWN_FLOATING,
    val nodeTreePackage: String? = null,
    val nodeCount: Int = 0,
    val visibleTextNodeCount: Int = 0,
    val knownStateTexts: List<String> = emptyList(),
    val uberOperationalState: OperationalAppState = OperationalAppState.UBER_UNKNOWN,
    val ninetyNineOperationalState: OperationalAppState = OperationalAppState.NINETY_NINE_UNKNOWN,
    val lastOperationalStateUpdate: String? = null,
    val lastSignalSummary: String? = null,
    val lastCaptureRequestSummary: String? = null,
    val lastObservationSummary: String? = null,
    val lastEventToObservationMs: Long? = null,
    val lastScreenshotDurationMs: Long? = null,
    val lastCaptureStatus: String? = null,
    val lastLatencyBottleneck: String? = null,
    val lastVisionDurationMs: Long? = null,
    val lastBestCropKind: String? = null,
    val lastVisualOfferLikeScore: Int? = null,
    val lastAcceptedForOcrFuture: Boolean? = null,
    val lastVisualProbeReason: String? = null,
    val lastOfferCycleKind: String? = null,
    val lastOfferCycleId: String? = null,
    val lastOfferCycleReason: String? = null,
    val lastOfferCycleShouldPreferForOcr: Boolean? = null,
    val lastOfferCycleTimeSincePreviousMs: Long? = null
)

object RadarDebugStore {
    private val mutableState = MutableStateFlow(RadarDebugState())
    val state: StateFlow<RadarDebugState> = mutableState

    fun updateServiceActive(active: Boolean) {
        mutableState.update { it.copy(serviceActive = active) }
    }

    fun updateWindowSnapshot(snapshot: WindowStackSnapshot, floatingKind: FloatingWindowKind) {
        mutableState.update {
            it.copy(
                topDominantWindowPackage = snapshot.topDominantWindow?.packageName,
                topFloatingWindowPackage = snapshot.topFloatingWindow?.packageName,
                floatingCoverage = snapshot.topFloatingWindow?.coverage,
                floatingKind = floatingKind
            )
        }
    }

    fun updateNodeTree(signature: NodeTreeSignature) {
        mutableState.update {
            it.copy(
                nodeTreePackage = signature.packageName,
                nodeCount = signature.nodeCount,
                visibleTextNodeCount = signature.visibleTextNodeCount,
                knownStateTexts = signature.knownStateTexts
            )
        }
    }

    fun updateLastSignal(summary: String) {
        mutableState.update { it.copy(lastSignalSummary = summary) }
    }

    fun updateLastCaptureRequest(request: CaptureRequest?) {
        val summary = request?.let {
            "id=${it.id.take(8)} source=${it.triggerSource} priority=${it.priority} dominant=${it.dominantPackage} floating=${it.floatingPackage} floatingKind=${it.floatingKind} reason=${it.reason} cycle=${it.offerCycleClassification?.kind}"
        }
        mutableState.update { it.copy(lastCaptureRequestSummary = summary) }
    }

    fun updateLastObservation(observation: ScreenObservation) {
        val screenshotDurationMs = observation.screenshotFinishedAtMs - observation.screenshotStartedAtMs
        val summary = "id=${observation.id.take(8)} request=${observation.captureRequestId.take(8)} trigger=${observation.triggerSource} size=${observation.screenshotWidth}x${observation.screenshotHeight} eventToObservationMs=${observation.eventToObservationMs} screenshotDurationMs=$screenshotDurationMs floatingKind=${observation.floatingKind} cycle=${observation.offerCycleClassification?.kind}"
        mutableState.update {
            it.copy(
                lastObservationSummary = summary,
                lastEventToObservationMs = observation.eventToObservationMs,
                lastScreenshotDurationMs = screenshotDurationMs,
                lastCaptureStatus = "success",
                lastOfferCycleKind = observation.offerCycleClassification?.kind?.name ?: it.lastOfferCycleKind,
                lastOfferCycleId = observation.offerCycleClassification?.cycleId ?: it.lastOfferCycleId,
                lastOfferCycleReason = observation.offerCycleClassification?.reason ?: it.lastOfferCycleReason,
                lastOfferCycleShouldPreferForOcr = observation.offerCycleClassification?.shouldPreferForOcr
                    ?: it.lastOfferCycleShouldPreferForOcr,
                lastOfferCycleTimeSincePreviousMs = observation.offerCycleClassification?.timeSincePreviousMs
                    ?: it.lastOfferCycleTimeSincePreviousMs,
                lastLatencyBottleneck = detectBottleneck(
                    eventToSignalMs = null,
                    signalToRequestMs = null,
                    requestToApprovalMs = observation.captureApprovedAtMs - observation.requestCreatedAtMs,
                    screenshotDurationMs = screenshotDurationMs
                )
            )
        }
    }

    fun updateCaptureTiming(
        eventToObservationMs: Long? = null,
        screenshotDurationMs: Long? = null,
        captureStatus: String? = null,
        eventToSignalMs: Long? = null,
        signalToRequestMs: Long? = null,
        requestToApprovalMs: Long? = null
    ) {
        mutableState.update {
            it.copy(
                lastEventToObservationMs = eventToObservationMs ?: it.lastEventToObservationMs,
                lastScreenshotDurationMs = screenshotDurationMs ?: it.lastScreenshotDurationMs,
                lastCaptureStatus = captureStatus ?: it.lastCaptureStatus,
                lastLatencyBottleneck = detectBottleneck(
                    eventToSignalMs = eventToSignalMs,
                    signalToRequestMs = signalToRequestMs,
                    requestToApprovalMs = requestToApprovalMs,
                    screenshotDurationMs = screenshotDurationMs ?: it.lastScreenshotDurationMs
                )
            )
        }
    }

    fun updateUberOperationalState(state: OperationalAppState, updateSummary: String) {
        mutableState.update {
            it.copy(
                uberOperationalState = state,
                lastOperationalStateUpdate = updateSummary
            )
        }
    }

    fun updateNinetyNineOperationalState(state: OperationalAppState, updateSummary: String) {
        mutableState.update {
            it.copy(
                ninetyNineOperationalState = state,
                lastOperationalStateUpdate = updateSummary
            )
        }
    }

    fun updateVisionSummary(
        durationMs: Long,
        bestCropKind: String?,
        visualOfferLikeScore: Int?,
        acceptedForOcrFuture: Boolean,
        reason: String
    ) {
        mutableState.update {
            it.copy(
                lastVisionDurationMs = durationMs,
                lastBestCropKind = bestCropKind,
                lastVisualOfferLikeScore = visualOfferLikeScore,
                lastAcceptedForOcrFuture = acceptedForOcrFuture,
                lastVisualProbeReason = reason
            )
        }
    }

    fun updateOfferCycle(classification: OfferCycleClassification) {
        mutableState.update {
            it.copy(
                lastOfferCycleKind = classification.kind.name,
                lastOfferCycleId = classification.cycleId,
                lastOfferCycleReason = classification.reason,
                lastOfferCycleShouldPreferForOcr = classification.shouldPreferForOcr,
                lastOfferCycleTimeSincePreviousMs = classification.timeSincePreviousMs
            )
        }
    }

    private fun detectBottleneck(
        eventToSignalMs: Long?,
        signalToRequestMs: Long?,
        requestToApprovalMs: Long?,
        screenshotDurationMs: Long?
    ): String? {
        val candidates = listOfNotNull(
            eventToSignalMs?.let { "event_to_signal" to it },
            signalToRequestMs?.let { "signal_to_request" to it },
            requestToApprovalMs?.let { "request_to_approval" to it },
            screenshotDurationMs?.let { "screenshot" to it }
        )
        return candidates.maxByOrNull { it.second }?.first
    }
}
