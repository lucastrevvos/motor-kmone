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
    val lastAutoVisionRecoveryApplied: Boolean? = null,
    val lastAutoVisionRecoveryReason: String? = null,
    val lastAutoVisionRecoveryCropKind: String? = null,
    val lastAutoPostTransitionOverridden: Boolean? = null,
    val lastAutoRecoverySuppressedReason: String? = null,
    val lastAutoBurstScheduled: Boolean? = null,
    val lastAutoBurstReason: String? = null,
    val lastAutoBurstDelayMs: Long? = null,
    val lastAutoBurstAttempt: Int? = null,
    val lastAutoBurstResult: String? = null,
    val lastAutoBurstSuppressedReason: String? = null,
    val lastAutoBurstPreferredCropOrder: String? = null,
    val lastFloatingObstructionDetected: Boolean? = null,
    val lastFloatingObstructionReason: String? = null,
    val lastFloatingObstructionCropKind: String? = null,
    val lastFloatingObstructionConfidence: Int? = null,
    val lastOcrDurationMs: Long? = null,
    val lastOcrSuccess: Boolean? = null,
    val lastOcrCropKind: String? = null,
    val lastOcrRawTextPreview: String? = null,
    val lastOcrPolicyReason: String? = null,
    val lastFingerprintKind: String? = null,
    val lastFingerprintPlatformHint: String? = null,
    val lastFingerprintOfferLikeScore: Int? = null,
    val lastFingerprintNonOfferScore: Int? = null,
    val lastFingerprintPricePreview: String? = null,
    val lastFingerprintReason: String? = null,
    val lastDedupeResult: String? = null,
    val lastDedupeClusterId: String? = null,
    val lastDedupeQuality: Int? = null,
    val lastDedupeReason: String? = null,
    val lastDedupeIsBest: Boolean? = null,
    val activeOfferClusterCount: Int = 0,
    val lastBestOfferPreview: String? = null,
    val lastBestOfferMainPrice: String? = null,
    val lastBestOfferPlatform: String? = null,
    val lastParserResultStatus: String? = null,
    val lastParsedClusterId: String? = null,
    val lastParsedPlatform: String? = null,
    val lastParsedProduct: String? = null,
    val lastParsedPrice: String? = null,
    val lastParsedValuePerKm: String? = null,
    val lastParsedPickupTime: String? = null,
    val lastParsedPickupDistance: String? = null,
    val lastParsedTripTime: String? = null,
    val lastParsedTripDistance: String? = null,
    val lastParsedConfidence: String? = null,
    val lastParserWarnings: String? = null,
    val lastParsedSanityStatus: String? = null,
    val lastParsedSanityIssues: String? = null,
    val lastParsedShouldBlockEconomicDecision: Boolean? = null,
    val lastEconomicDecision: String? = null,
    val lastEconomicDecisionScore: Int? = null,
    val lastEconomicDecisionConfidence: String? = null,
    val lastEconomicDecisionReasons: String? = null,
    val lastEconomicGrossPerTripKm: String? = null,
    val lastEconomicGrossPerTotalKm: String? = null,
    val lastEconomicTotalDistanceKm: String? = null,
    val lastEconomicTotalTimeMin: String? = null,
    val lastEconomicDecisionClusterId: String? = null,
    val lastPresentationKind: String? = null,
    val lastPresentationTitle: String? = null,
    val lastPresentationShortReason: String? = null,
    val lastPresentationPrimaryMetric: String? = null,
    val lastPresentationSecondaryMetric: String? = null,
    val lastPresentationExpiresAtMs: Long? = null,
    val lastPresentationSource: String? = null,
    val lastDecisionOverlayVisible: Boolean = false,
    val lastDecisionOverlayKind: String? = null,
    val lastDecisionOverlayTitle: String? = null,
    val lastDecisionOverlayShortReason: String? = null,
    val lastDecisionOverlayShownAtMs: Long? = null,
    val lastDecisionOverlayExpiresAtMs: Long? = null,
    val lastDecisionOverlayError: String? = null,
    val lastOfferCycleKind: String? = null,
    val lastOfferCycleId: String? = null,
    val lastOfferCycleReason: String? = null,
    val lastOfferCycleShouldPreferForOcr: Boolean? = null,
    val lastOfferCycleTimeSincePreviousMs: Long? = null,
    val lastManualAnalysisEpoch: Long? = null,
    val lastManualAnalysisStatus: String? = null,
    val lastManualAnalysisDurationMs: Long? = null,
    val lastManualFingerprintKind: String? = null,
    val lastManualFingerprintPreview: String? = null,
    val lastManualClickAcceptedAtMs: Long? = null,
    val lastManualClickRejectedReason: String? = null,
    val manualAnalysisRunning: Boolean = false,
    val manualCooldownRemainingMs: Long = 0L,
    val lastManualSecondaryOcrStatus: String? = null,
    val lastManualBitmapWarning: String? = null,
    val piuOverlayPermissionGranted: Boolean = false,
    val piuOverlayShowing: Boolean = false,
    val piuLastX: Int = 0,
    val piuLastAnalyzeClickedAtMs: Long? = null,
    val piuLastError: String? = null
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

    fun updateAutomaticRecoverySummary(
        applied: Boolean? = null,
        reason: String? = null,
        cropKind: String? = null,
        postTransitionOverridden: Boolean? = null,
        suppressedReason: String? = null
    ) {
        mutableState.update {
            it.copy(
                lastAutoVisionRecoveryApplied = applied ?: it.lastAutoVisionRecoveryApplied,
                lastAutoVisionRecoveryReason = reason ?: it.lastAutoVisionRecoveryReason,
                lastAutoVisionRecoveryCropKind = cropKind ?: it.lastAutoVisionRecoveryCropKind,
                lastAutoPostTransitionOverridden = postTransitionOverridden ?: it.lastAutoPostTransitionOverridden,
                lastAutoRecoverySuppressedReason = suppressedReason ?: it.lastAutoRecoverySuppressedReason
            )
        }
    }

    fun updateAutoBurstSummary(
        scheduled: Boolean? = null,
        reason: String? = null,
        delayMs: Long? = null,
        attempt: Int? = null,
        result: String? = null,
        suppressedReason: String? = null,
        preferredCropOrder: String? = null
    ) {
        mutableState.update {
            it.copy(
                lastAutoBurstScheduled = scheduled ?: it.lastAutoBurstScheduled,
                lastAutoBurstReason = reason ?: it.lastAutoBurstReason,
                lastAutoBurstDelayMs = delayMs ?: it.lastAutoBurstDelayMs,
                lastAutoBurstAttempt = attempt ?: it.lastAutoBurstAttempt,
                lastAutoBurstResult = result ?: it.lastAutoBurstResult,
                lastAutoBurstSuppressedReason = suppressedReason ?: it.lastAutoBurstSuppressedReason,
                lastAutoBurstPreferredCropOrder = preferredCropOrder ?: it.lastAutoBurstPreferredCropOrder
            )
        }
    }

    fun updateFloatingObstructionSummary(
        detected: Boolean? = null,
        reason: String? = null,
        cropKind: String? = null,
        confidence: Int? = null
    ) {
        mutableState.update {
            it.copy(
                lastFloatingObstructionDetected = detected ?: it.lastFloatingObstructionDetected,
                lastFloatingObstructionReason = reason ?: it.lastFloatingObstructionReason,
                lastFloatingObstructionCropKind = cropKind ?: it.lastFloatingObstructionCropKind,
                lastFloatingObstructionConfidence = confidence ?: it.lastFloatingObstructionConfidence
            )
        }
    }

    fun updateOcrSummary(
        durationMs: Long?,
        success: Boolean?,
        cropKind: String?,
        rawTextPreview: String?,
        policyReason: String
    ) {
        mutableState.update {
            it.copy(
                lastOcrDurationMs = durationMs,
                lastOcrSuccess = success,
                lastOcrCropKind = cropKind,
                lastOcrRawTextPreview = rawTextPreview,
                lastOcrPolicyReason = policyReason
            )
        }
    }

    fun updateFingerprintSummary(
        kind: String,
        platformHint: String,
        offerLikeScore: Int,
        nonOfferScore: Int,
        pricePreview: String?,
        reason: String
    ) {
        mutableState.update {
            it.copy(
                lastFingerprintKind = kind,
                lastFingerprintPlatformHint = platformHint,
                lastFingerprintOfferLikeScore = offerLikeScore,
                lastFingerprintNonOfferScore = nonOfferScore,
                lastFingerprintPricePreview = pricePreview,
                lastFingerprintReason = reason
            )
        }
    }

    fun updateDedupeSummary(
        result: String,
        clusterId: String?,
        quality: Int?,
        reason: String,
        isBest: Boolean,
        activeClusterCount: Int,
        bestOfferPreview: String?,
        bestOfferMainPrice: String?,
        bestOfferPlatform: String?
    ) {
        mutableState.update {
            it.copy(
                lastDedupeResult = result,
                lastDedupeClusterId = clusterId,
                lastDedupeQuality = quality,
                lastDedupeReason = reason,
                lastDedupeIsBest = isBest,
                activeOfferClusterCount = activeClusterCount,
                lastBestOfferPreview = bestOfferPreview,
                lastBestOfferMainPrice = bestOfferMainPrice,
                lastBestOfferPlatform = bestOfferPlatform
            )
        }
    }

    fun updateParserSummary(
        status: String,
        clusterId: String? = null,
        platform: String? = null,
        product: String? = null,
        price: String? = null,
        valuePerKm: String? = null,
        pickupTime: String? = null,
        pickupDistance: String? = null,
        tripTime: String? = null,
        tripDistance: String? = null,
        confidence: String? = null,
        warnings: String? = null,
        sanityStatus: String? = null,
        sanityIssues: String? = null,
        shouldBlockEconomicDecision: Boolean? = null
    ) {
        mutableState.update {
            it.copy(
                lastParserResultStatus = status,
                lastParsedClusterId = clusterId ?: it.lastParsedClusterId,
                lastParsedPlatform = platform ?: it.lastParsedPlatform,
                lastParsedProduct = product ?: it.lastParsedProduct,
                lastParsedPrice = price ?: it.lastParsedPrice,
                lastParsedValuePerKm = valuePerKm ?: it.lastParsedValuePerKm,
                lastParsedPickupTime = pickupTime ?: it.lastParsedPickupTime,
                lastParsedPickupDistance = pickupDistance ?: it.lastParsedPickupDistance,
                lastParsedTripTime = tripTime ?: it.lastParsedTripTime,
                lastParsedTripDistance = tripDistance ?: it.lastParsedTripDistance,
                lastParsedConfidence = confidence ?: it.lastParsedConfidence,
                lastParserWarnings = warnings ?: it.lastParserWarnings,
                lastParsedSanityStatus = sanityStatus ?: it.lastParsedSanityStatus,
                lastParsedSanityIssues = sanityIssues ?: it.lastParsedSanityIssues,
                lastParsedShouldBlockEconomicDecision = shouldBlockEconomicDecision
                    ?: it.lastParsedShouldBlockEconomicDecision
            )
        }
    }

    fun updateEconomicDecisionSummary(
        decision: String,
        score: Int? = null,
        confidence: String? = null,
        reasons: String? = null,
        grossPerTripKm: String? = null,
        grossPerTotalKm: String? = null,
        totalDistanceKm: String? = null,
        totalTimeMin: String? = null,
        clusterId: String? = null
    ) {
        mutableState.update {
            it.copy(
                lastEconomicDecision = decision,
                lastEconomicDecisionScore = score,
                lastEconomicDecisionConfidence = confidence ?: it.lastEconomicDecisionConfidence,
                lastEconomicDecisionReasons = reasons ?: it.lastEconomicDecisionReasons,
                lastEconomicGrossPerTripKm = grossPerTripKm ?: it.lastEconomicGrossPerTripKm,
                lastEconomicGrossPerTotalKm = grossPerTotalKm ?: it.lastEconomicGrossPerTotalKm,
                lastEconomicTotalDistanceKm = totalDistanceKm ?: it.lastEconomicTotalDistanceKm,
                lastEconomicTotalTimeMin = totalTimeMin ?: it.lastEconomicTotalTimeMin,
                lastEconomicDecisionClusterId = clusterId ?: it.lastEconomicDecisionClusterId
            )
        }
    }

    fun updateDecisionPresentationSummary(
        kind: String,
        title: String? = null,
        shortReason: String? = null,
        primaryMetric: String? = null,
        secondaryMetric: String? = null,
        expiresAtMs: Long? = null,
        source: String? = null
    ) {
        mutableState.update {
            it.copy(
                lastPresentationKind = kind,
                lastPresentationTitle = title ?: it.lastPresentationTitle,
                lastPresentationShortReason = shortReason ?: it.lastPresentationShortReason,
                lastPresentationPrimaryMetric = primaryMetric ?: it.lastPresentationPrimaryMetric,
                lastPresentationSecondaryMetric = secondaryMetric ?: it.lastPresentationSecondaryMetric,
                lastPresentationExpiresAtMs = expiresAtMs ?: it.lastPresentationExpiresAtMs,
                lastPresentationSource = source ?: it.lastPresentationSource
            )
        }
    }

    fun updateDecisionOverlaySummary(
        visible: Boolean,
        kind: String? = null,
        title: String? = null,
        shortReason: String? = null,
        shownAtMs: Long? = null,
        expiresAtMs: Long? = null,
        error: String? = null
    ) {
        mutableState.update {
            it.copy(
                lastDecisionOverlayVisible = visible,
                lastDecisionOverlayKind = kind ?: it.lastDecisionOverlayKind,
                lastDecisionOverlayTitle = title ?: it.lastDecisionOverlayTitle,
                lastDecisionOverlayShortReason = shortReason ?: it.lastDecisionOverlayShortReason,
                lastDecisionOverlayShownAtMs = shownAtMs ?: it.lastDecisionOverlayShownAtMs,
                lastDecisionOverlayExpiresAtMs = expiresAtMs ?: it.lastDecisionOverlayExpiresAtMs,
                lastDecisionOverlayError = error
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

    fun updateManualAnalysis(
        epoch: Long,
        status: String,
        durationMs: Long? = null,
        fingerprintKind: String? = null,
        fingerprintPreview: String? = null,
        secondaryOcrStatus: String? = null,
        bitmapWarning: String? = null
    ) {
        mutableState.update {
            it.copy(
                lastManualAnalysisEpoch = epoch,
                lastManualAnalysisStatus = status,
                lastManualAnalysisDurationMs = durationMs ?: it.lastManualAnalysisDurationMs,
                lastManualFingerprintKind = fingerprintKind ?: it.lastManualFingerprintKind,
                lastManualFingerprintPreview = fingerprintPreview ?: it.lastManualFingerprintPreview,
                lastManualSecondaryOcrStatus = secondaryOcrStatus ?: it.lastManualSecondaryOcrStatus,
                lastManualBitmapWarning = bitmapWarning ?: it.lastManualBitmapWarning
            )
        }
    }

    fun updateManualControlState(
        acceptedAtMs: Long? = null,
        lastRejectedReason: String? = null,
        running: Boolean? = null,
        cooldownRemainingMs: Long? = null
    ) {
        mutableState.update {
            it.copy(
                lastManualClickAcceptedAtMs = acceptedAtMs ?: it.lastManualClickAcceptedAtMs,
                lastManualClickRejectedReason = lastRejectedReason ?: it.lastManualClickRejectedReason,
                manualAnalysisRunning = running ?: it.manualAnalysisRunning,
                manualCooldownRemainingMs = cooldownRemainingMs ?: it.manualCooldownRemainingMs
            )
        }
    }

    fun updatePiuOverlayState(
        permissionGranted: Boolean,
        showing: Boolean,
        x: Int,
        analyzeClickedAtMs: Long? = null,
        error: String? = null
    ) {
        mutableState.update {
            it.copy(
                piuOverlayPermissionGranted = permissionGranted,
                piuOverlayShowing = showing,
                piuLastX = x,
                piuLastAnalyzeClickedAtMs = analyzeClickedAtMs ?: it.piuLastAnalyzeClickedAtMs,
                piuLastError = error
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
