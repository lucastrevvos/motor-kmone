package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCapturer
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotFinishStatus
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassifier
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleSnapshot
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.PlatformHint
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.RadarConfig
import com.lucastrevvos.kmonemotor.radar.core.RadarSignal
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.core.UberReadableState
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservationFactory
import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature
import java.util.UUID

class RadarCaptureOrchestrator(
    private val screenshotCapturer: ScreenshotCapturer,
    private val observationFactory: ScreenObservationFactory = ScreenObservationFactory(),
    private val clock: RadarClock = RadarClock.System,
    private val offerCycleClassifier: OfferCycleClassifier = OfferCycleClassifier(),
    private val onObservationCreated: ((ScreenObservation, ScreenshotCaptureResult) -> Unit)? = null
) {
    private var previousNinetyNineSignature: NodeTreeSignature? = null
    private var activeCapture: CaptureRequest? = null
    private var pendingCaptureRequest: CaptureRequest? = null
    private val finishedCaptureIds = mutableSetOf<String>()
    private val uberFloatingDiagnosticCooldownBySignature = mutableMapOf<String, Long>()
    private val uberDominantDiagnosticCooldownBySignature = mutableMapOf<String, Long>()
    private val ninetyNineCompactDiagnosticCooldownBySignature = mutableMapOf<String, Long>()

    fun onSignal(signal: RadarSignal) {
        val requestCandidate = toCaptureRequest(signal)
        val evaluation = evaluateSignal(signal, requestCandidate)
        if (evaluation.ignoredReason != null) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_CAPTURE_REQUEST_IGNORED",
                "signalType" to signal::class.java.simpleName,
                "dominantPackage" to evaluation.request?.dominantPackage,
                "floatingPackage" to evaluation.request?.floatingPackage,
                "reason" to evaluation.ignoredReason
            )
            updateNinetyNinePreviousSignature(signal)
            return
        }

        val request = evaluation.request
        if (request == null) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_CAPTURE_REQUEST_IGNORED",
                "signalType" to signal::class.java.simpleName,
                "reason" to "unsupported_signal"
            )
            RadarDebugStore.updateLastCaptureRequest(null)
            return
        }

        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_CAPTURE_REQUEST_CREATED",
            "requestId" to request.id,
            "triggerSource" to request.triggerSource,
            "priority" to request.priority,
            "dominantPackage" to request.dominantPackage,
            "floatingPackage" to request.floatingPackage,
            "floatingKind" to request.floatingKind,
            "reason" to request.reason,
            "offerCycleKind" to request.offerCycleClassification?.kind,
            "offerCycleId" to request.offerCycleClassification?.cycleId
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_LATENCY_CAPTURE_REQUEST",
            "requestId" to request.id,
            "signalToRequestMs" to (request.createdAtMs - request.signalEmittedAtMs)
        )
        RadarDebugStore.updateLastCaptureRequest(request)
        updateNinetyNinePreviousSignature(signal)
        scheduleCapture(request)
    }

    private fun scheduleCapture(request: CaptureRequest) {
        if (activeCapture == null) {
            approveCapture(request)
            return
        }

        if (request.priority == CapturePriority.LOW || request.priority == CapturePriority.NORMAL) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_CAPTURE_IGNORED_ACTIVE",
                "requestId" to request.id,
                "activeRequestId" to activeCapture?.id,
                "priority" to request.priority,
                "reason" to "active_capture_in_progress"
            )
            return
        }

        val previousPending = pendingCaptureRequest
        pendingCaptureRequest = request
        if (previousPending != null) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_CAPTURE_PENDING_REPLACED",
                "oldPendingRequestId" to previousPending.id,
                "newPendingRequestId" to request.id,
                "priority" to request.priority
            )
        } else {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_CAPTURE_IGNORED_ACTIVE",
                "requestId" to request.id,
                "activeRequestId" to activeCapture?.id,
                "priority" to request.priority,
                "reason" to "queued_as_pending_waiting_active"
            )
        }
    }

    private fun approveCapture(request: CaptureRequest) {
        val approvedRequest = request.copy(approvedAtMs = clock.nowMs())
        activeCapture = approvedRequest
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_CAPTURE_APPROVED",
            "requestId" to approvedRequest.id,
            "triggerSource" to approvedRequest.triggerSource,
            "priority" to approvedRequest.priority
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_LATENCY_CAPTURE_APPROVAL",
            "requestId" to approvedRequest.id,
            "requestToApprovalMs" to ((approvedRequest.approvedAtMs ?: approvedRequest.createdAtMs) - approvedRequest.createdAtMs)
        )
        RadarDebugStore.updateCaptureTiming(
            captureStatus = "approved",
            eventToSignalMs = approvedRequest.signalEmittedAtMs - approvedRequest.sourceEventAtMs,
            signalToRequestMs = approvedRequest.createdAtMs - approvedRequest.signalEmittedAtMs,
            requestToApprovalMs = (approvedRequest.approvedAtMs ?: approvedRequest.createdAtMs) - approvedRequest.createdAtMs
        )
        screenshotCapturer.capture(
            request = approvedRequest,
            onSuccess = ::onScreenshotSuccess,
            onFailure = ::onScreenshotFailure,
            onFinished = ::onCaptureFinished
        )
    }

    private fun onScreenshotSuccess(request: CaptureRequest, result: ScreenshotCaptureResult) {
        if (activeCapture?.id != request.id) {
            return
        }
        val observation = observationFactory.create(request, result)
        RadarLogger.i(
            "KM_V2_OBSERVATION",
            "KM_V2_OBSERVATION_CREATED",
            "observationId" to observation.id,
            "captureRequestId" to observation.captureRequestId,
            "triggerSource" to observation.triggerSource
        )
        RadarLogger.i(
            "KM_V2_OBSERVATION",
            "KM_V2_CAPTURE_LATENCY",
            "requestId" to request.id,
            "captureLatencyMs" to observation.captureLatencyMs
        )
        RadarLogger.i(
            "KM_V2_OBSERVATION",
            "KM_V2_LATENCY_OBSERVATION",
            "requestId" to request.id,
            "eventToObservationMs" to observation.eventToObservationMs,
            "requestToObservationMs" to (observation.observationCreatedAtMs - observation.requestCreatedAtMs)
        )
        RadarDebugStore.updateLastObservation(observation)
        RadarDebugStore.updateCaptureTiming(
            eventToObservationMs = observation.eventToObservationMs,
            screenshotDurationMs = observation.screenshotFinishedAtMs - observation.screenshotStartedAtMs,
            captureStatus = "success",
            eventToSignalMs = request.signalEmittedAtMs - request.sourceEventAtMs,
            signalToRequestMs = request.createdAtMs - request.signalEmittedAtMs,
            requestToApprovalMs = (request.approvedAtMs ?: request.createdAtMs) - request.createdAtMs
        )
        onObservationCreated?.invoke(observation, result)
    }

    private fun onScreenshotFailure(
        request: CaptureRequest,
        error: String,
        errorCode: Int?,
        screenshotStartedAtMs: Long,
        screenshotFinishedAtMs: Long,
        status: ScreenshotFinishStatus
    ) {
        if (activeCapture?.id != request.id && request.id !in finishedCaptureIds) {
            return
        }
        RadarDebugStore.updateCaptureTiming(
            screenshotDurationMs = screenshotFinishedAtMs - screenshotStartedAtMs,
            captureStatus = status.name.lowercase(),
            eventToSignalMs = request.signalEmittedAtMs - request.sourceEventAtMs,
            signalToRequestMs = request.createdAtMs - request.signalEmittedAtMs,
            requestToApprovalMs = (request.approvedAtMs ?: request.createdAtMs) - request.createdAtMs
        )
    }

    private fun onCaptureFinished(request: CaptureRequest, status: ScreenshotFinishStatus) {
        if (!finishedCaptureIds.add(request.id)) {
            return
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_CAPTURE_FINISHED",
            "requestId" to request.id,
            "status" to status.name.lowercase()
        )
        finishActiveAndMaybeStartPending(request.id)
    }

    private fun finishActiveAndMaybeStartPending(requestId: String) {
        if (activeCapture?.id == requestId) {
            activeCapture = null
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_ORCHESTRATOR_ACTIVE_CAPTURE_CLEARED",
                "requestId" to requestId
            )
        }
        val nextPending = pendingCaptureRequest
        pendingCaptureRequest = null
        if (nextPending != null) {
            approveCapture(nextPending)
        }
        finishedCaptureIds.remove(requestId)
    }

    private fun toCaptureRequest(signal: RadarSignal): CaptureRequest? {
        val nowMs = clock.nowMs()
        return when (signal) {
            is RadarSignal.UberFloatingOverOtherApp -> buildUberFloatingCaptureRequest(signal, nowMs)
            is RadarSignal.UberStateChanged -> buildUberStateCaptureRequest(signal, nowMs)
            is RadarSignal.NinetyNineTreeStructureChanged -> buildNinetyNineCaptureRequest(signal, nowMs)
            is RadarSignal.DominantWindowChanged -> null
        }
    }

    private fun buildUberStateCaptureRequest(
        signal: RadarSignal.UberStateChanged,
        nowMs: Long
    ): CaptureRequest {
        val dominantDiagnosticRequest = buildUberDominantDiagnosticRequest(signal, nowMs)
        if (dominantDiagnosticRequest != null) {
            return dominantDiagnosticRequest
        }
        return CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = signal.eventReceivedAtMs,
            signalEmittedAtMs = signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.UBER_STATE_TRANSITION,
            platformHint = PlatformHint.UBER,
            priority = if (signal.previousState == UberReadableState.SEARCHING_RIDES &&
                signal.currentState != UberReadableState.SEARCHING_RIDES
            ) {
                CapturePriority.HIGH
            } else {
                CapturePriority.NORMAL
            },
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            floatingBounds = signal.floatingBounds,
            floatingKind = signal.floatingKind,
            reason = "uber_state_${signal.previousState}_to_${signal.currentState}"
        )
    }

    private fun buildNinetyNineCaptureRequest(
        signal: RadarSignal.NinetyNineTreeStructureChanged,
        nowMs: Long
    ): CaptureRequest {
        return CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = signal.eventReceivedAtMs,
            signalEmittedAtMs = signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            platformHint = PlatformHint.NINETY_NINE,
            priority = CapturePriority.NORMAL,
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            floatingBounds = signal.floatingBounds,
            floatingKind = signal.floatingKind,
            reason = "ninety_nine_tree_structure_changed"
        )
    }

    private fun buildUberFloatingCaptureRequest(
        signal: RadarSignal.UberFloatingOverOtherApp,
        nowMs: Long
    ): CaptureRequest? {
        val isDiagnosticScenario = signal.dominantPackage == RadarConfig.NINETY_NINE_DRIVER_PACKAGE &&
            signal.floatingPackage == RadarConfig.UBER_DRIVER_PACKAGE &&
            signal.floatingKind == FloatingWindowKind.FLOATING_BUBBLE
        if (!isDiagnosticScenario) {
            return null
        }

        val signature = buildUberFloatingDiagnosticSignature(signal)
        val lastCapturedAt = uberFloatingDiagnosticCooldownBySignature[signature]
        if (lastCapturedAt != null && nowMs - lastCapturedAt < UBER_FLOATING_DIAGNOSTIC_COOLDOWN_MS) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_FLOATING_DIAGNOSTIC_SUPPRESSED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "bounds" to signal.floatingBounds,
                "roundedCoverage" to roundedCoverage(signal.floatingCoverage),
                "reason" to "same_signature_cooldown"
            )
            return null
        }
        if (activeCapture != null) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_FLOATING_DIAGNOSTIC_SUPPRESSED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "bounds" to signal.floatingBounds,
                "reason" to "active_capture_exists"
            )
            return null
        }
        if (pendingCaptureRequest?.priority == CapturePriority.HIGH ||
            pendingCaptureRequest?.priority == CapturePriority.CRITICAL
        ) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_FLOATING_DIAGNOSTIC_SUPPRESSED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "bounds" to signal.floatingBounds,
                "reason" to "high_priority_pending_exists"
            )
            return null
        }

        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_UBER_FLOATING_DIAGNOSTIC_ELIGIBLE",
            "dominantPackage" to signal.dominantPackage,
            "floatingPackage" to signal.floatingPackage,
            "bounds" to signal.floatingBounds,
            "roundedCoverage" to roundedCoverage(signal.floatingCoverage)
        )

        val request = CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = signal.eventReceivedAtMs,
            signalEmittedAtMs = signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            platformHint = PlatformHint.UBER,
            priority = CapturePriority.HIGH,
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            floatingBounds = signal.floatingBounds,
            floatingKind = signal.floatingKind,
            reason = "uber_floating_over_99_diagnostic_capture"
        )
        uberFloatingDiagnosticCooldownBySignature[signature] = nowMs
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_UBER_FLOATING_DIAGNOSTIC_REQUEST_CREATED",
            "requestId" to request.id,
            "dominantPackage" to request.dominantPackage,
            "floatingPackage" to request.floatingPackage,
            "bounds" to signal.floatingBounds,
            "roundedCoverage" to roundedCoverage(signal.floatingCoverage)
        )
        return request
    }

    private fun buildUberDominantDiagnosticRequest(
        signal: RadarSignal.UberStateChanged,
        nowMs: Long
    ): CaptureRequest? {
        val nodeCountDelta = kotlin.math.abs(signal.nodeCount - (signal.previousNodeCount ?: 0))
        val visibleTextDelta = kotlin.math.abs(
            signal.visibleTextNodeCount - (signal.previousVisibleTextNodeCount ?: 0)
        )
        val previousSearchingText = signal.previousKnownStateTexts.any {
            it.contains("procurando corridas", ignoreCase = true)
        }
        val currentSearchingText = signal.knownStateTexts.any {
            it.contains("procurando corridas", ignoreCase = true)
        }
        val matchedConditions = buildList {
            if (signal.previousState == UberReadableState.SEARCHING_RIDES &&
                signal.currentState != UberReadableState.SEARCHING_RIDES
            ) {
                add("searching_state_exit")
            }
            if (previousSearchingText && !currentSearchingText) {
                add("searching_text_disappeared")
            }
            if (signal.numericTextNodeCount >= 1 && signal.visibleTextNodeCount >= 2) {
                add("numeric_text_with_visible_text")
            }
            if (signal.buttonLikeNodeCount >= 1 && signal.visibleTextNodeCount >= 2) {
                add("button_like_with_visible_text")
            }
            if (nodeCountDelta >= 8 || visibleTextDelta >= 2) {
                add("tree_delta_threshold")
            }
        }

        RadarLogger.d(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_UBER_DOMINANT_DIAGNOSTIC_EVALUATED",
            "dominantPackage" to signal.dominantPackage,
            "floatingPackage" to signal.floatingPackage,
            "nodeTreePackage" to signal.nodeTreePackage,
            "nodeCount" to signal.nodeCount,
            "visibleTextNodeCount" to signal.visibleTextNodeCount,
            "numericTextNodeCount" to signal.numericTextNodeCount,
            "buttonLikeNodeCount" to signal.buttonLikeNodeCount,
            "matchedConditions" to matchedConditions.joinToString(",")
        )

        val rejectionReason = when {
            signal.dominantPackage != RadarConfig.UBER_DRIVER_PACKAGE -> "dominant_package_not_uber"
            signal.nodeTreePackage != null && signal.nodeTreePackage != RadarConfig.UBER_DRIVER_PACKAGE -> "node_tree_package_not_uber"
            signal.floatingPackage == SYSTEM_UI_PACKAGE -> "floating_package_system_ui"
            signal.floatingKind == FloatingWindowKind.SYSTEM_UI_FLOATING -> "floating_kind_system_ui"
            activeCapture != null -> "active_capture_exists"
            pendingCaptureRequest?.priority == CapturePriority.HIGH ||
                pendingCaptureRequest?.priority == CapturePriority.CRITICAL -> "high_priority_pending_exists"
            matchedConditions.isEmpty() -> "no_offer_like_heuristic_matched"
            else -> null
        }
        if (rejectionReason != null) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_DOMINANT_DIAGNOSTIC_REJECTED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "nodeTreePackage" to signal.nodeTreePackage,
                "reason" to rejectionReason
            )
            return null
        }

        val signature = buildUberDominantDiagnosticSignature(signal)
        val lastCapturedAt = uberDominantDiagnosticCooldownBySignature[signature]
        if (lastCapturedAt != null && nowMs - lastCapturedAt < UBER_DOMINANT_DIAGNOSTIC_COOLDOWN_MS) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_DOMINANT_DIAGNOSTIC_SUPPRESSED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "nodeTreePackage" to signal.nodeTreePackage,
                "reason" to "same_signature_cooldown"
            )
            return null
        }

        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_UBER_DOMINANT_DIAGNOSTIC_ELIGIBLE",
            "dominantPackage" to signal.dominantPackage,
            "floatingPackage" to signal.floatingPackage,
            "nodeTreePackage" to signal.nodeTreePackage,
            "matchedConditions" to matchedConditions.joinToString(",")
        )

        val request = CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = signal.eventReceivedAtMs,
            signalEmittedAtMs = signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            platformHint = PlatformHint.UBER,
            priority = CapturePriority.NORMAL,
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            floatingBounds = signal.floatingBounds,
            floatingKind = signal.floatingKind,
            reason = "uber_dominant_possible_offer_diagnostic_capture"
        )
        val offerCycleSnapshot = OfferCycleSnapshot(
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            createdAtMs = nowMs,
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            nodeTreePackage = signal.nodeTreePackage,
            nodeCount = signal.nodeCount,
            visibleTextNodeCount = signal.visibleTextNodeCount,
            numericTextNodeCount = signal.numericTextNodeCount,
            buttonLikeNodeCount = signal.buttonLikeNodeCount,
            knownStateTexts = signal.knownStateTexts,
            matchedConditions = matchedConditions,
            captureRequestId = request.id
        )
        val offerCycleClassification = offerCycleClassifier.classify(offerCycleSnapshot)
        logOfferCycleClassification(offerCycleClassification)
        RadarDebugStore.updateOfferCycle(offerCycleClassification)
        val enrichedRequest = request.copy(
            offerCycleClassification = offerCycleClassification
        )
        uberDominantDiagnosticCooldownBySignature[signature] = nowMs
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_UBER_DOMINANT_DIAGNOSTIC_REQUEST_CREATED",
            "requestId" to enrichedRequest.id,
            "dominantPackage" to enrichedRequest.dominantPackage,
            "floatingPackage" to enrichedRequest.floatingPackage,
            "nodeTreePackage" to signal.nodeTreePackage,
            "nodeCount" to signal.nodeCount,
            "visibleTextNodeCount" to signal.visibleTextNodeCount,
            "offerCycleKind" to offerCycleClassification.kind,
            "offerCycleId" to offerCycleClassification.cycleId
        )
        return enrichedRequest
    }

    private fun evaluateSignal(signal: RadarSignal, request: CaptureRequest?): SignalEvaluation {
        if (signal is RadarSignal.DominantWindowChanged) {
            return SignalEvaluation(request = request, ignoredReason = "informative_signal_only")
        }
        if (request == null) {
            return SignalEvaluation(request = null, ignoredReason = null)
        }

        val dominantPackage = request.dominantPackage
        if (dominantPackage == null) {
            return SignalEvaluation(request = request, ignoredReason = "dominant_package_null")
        }
        if (dominantPackage in IGNORED_DOMINANT_PACKAGES) {
            return SignalEvaluation(request = request, ignoredReason = "dominant_package_ignored")
        }

        return when (signal) {
            is RadarSignal.UberFloatingOverOtherApp -> {
                if (request.triggerSource == TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC) {
                    SignalEvaluation(request = request, ignoredReason = null)
                } else {
                    SignalEvaluation(request = request, ignoredReason = ignoredReason(signal))
                }
            }
            is RadarSignal.UberStateChanged -> {
                if (request.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC) {
                    return SignalEvaluation(request = request, ignoredReason = null)
                }
                if (signal.dominantPackage == RadarConfig.UBER_DRIVER_PACKAGE) {
                    return SignalEvaluation(request = request, ignoredReason = "uber_state_not_relevant")
                }
                val previous = signal.previousState
                val current = signal.currentState
                if (previous == current) {
                    SignalEvaluation(request = request, ignoredReason = "state_unchanged")
                } else if (previous == UberReadableState.SEARCHING_RIDES || current == UberReadableState.SEARCHING_RIDES) {
                    SignalEvaluation(request = request, ignoredReason = null)
                } else {
                    SignalEvaluation(request = request, ignoredReason = "uber_state_not_relevant")
                }
            }
            is RadarSignal.NinetyNineTreeStructureChanged -> evaluateNinetyNineSignal(signal, request)
            is RadarSignal.DominantWindowChanged -> SignalEvaluation(
                request = request,
                ignoredReason = "informative_signal_only"
            )
        }
    }

    private fun evaluateNinetyNineSignal(
        signal: RadarSignal.NinetyNineTreeStructureChanged,
        request: CaptureRequest
    ): SignalEvaluation {
        val qualification = qualifyNinetyNineSignal(signal)
        return when {
            qualification.rejectionReason == null -> SignalEvaluation(request = request, ignoredReason = null)
            qualification.rejectionReason == "node_count_below_20" -> {
                val compactRequest = buildNinetyNineCompactDiagnosticRequest(signal, request.createdAtMs)
                if (compactRequest != null) {
                    SignalEvaluation(request = compactRequest, ignoredReason = null)
                } else {
                    SignalEvaluation(request = request, ignoredReason = qualification.rejectionReason)
                }
            }
            else -> SignalEvaluation(request = request, ignoredReason = qualification.rejectionReason)
        }
    }

    private fun qualifyNinetyNineSignal(
        signal: RadarSignal.NinetyNineTreeStructureChanged
    ): NinetyNineQualification {
        val signature = signal.signature
        val score = computeNinetyNineSignalScore(previousNinetyNineSignature, signature)

        RadarLogger.d(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_TREE_SIGNAL_SCORE",
            "dominantPackage" to signal.dominantPackage,
            "floatingPackage" to signal.floatingPackage,
            "nodeTreePackage" to signature.packageName,
            "nodeCount" to signature.nodeCount,
            "visibleTextNodeCount" to signature.visibleTextNodeCount,
            "score" to score
        )

        val rejectionReason = when {
            signal.dominantPackage == null -> "dominant_package_null"
            signal.dominantPackage != RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> "dominant_package_not_app99"
            signature.packageName != RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> "node_tree_package_not_app99"
            signature.nodeCount < 20 -> "node_count_below_20"
            signature.visibleTextNodeCount == 0 -> "visible_text_node_count_zero"
            signature.visibleTextNodeCount < 3 -> "visible_text_node_count_below_3"
            score < NINETY_NINE_MIN_SIGNAL_SCORE -> "score_below_threshold"
            else -> null
        }

        if (rejectionReason != null) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_TREE_SIGNAL_REJECTED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "nodeTreePackage" to signature.packageName,
                "nodeCount" to signature.nodeCount,
                "visibleTextNodeCount" to signature.visibleTextNodeCount,
                "score" to score,
                "reason" to rejectionReason
            )
            return NinetyNineQualification(score = score, rejectionReason = rejectionReason)
        }

        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_TREE_SIGNAL_QUALIFIED",
            "dominantPackage" to signal.dominantPackage,
            "floatingPackage" to signal.floatingPackage,
            "nodeTreePackage" to signature.packageName,
            "nodeCount" to signature.nodeCount,
            "visibleTextNodeCount" to signature.visibleTextNodeCount,
            "score" to score
        )
        return NinetyNineQualification(score = score, rejectionReason = null)
    }

    private fun buildNinetyNineCompactDiagnosticRequest(
        signal: RadarSignal.NinetyNineTreeStructureChanged,
        nowMs: Long
    ): CaptureRequest? {
        val signature = signal.signature
        val nodeCountBucket = when {
            signature.nodeCount in 10..14 -> "10_14"
            signature.nodeCount in 15..19 -> "15_19"
            else -> "outside_compact_range"
        }
        val eligibilityReason = when {
            signal.dominantPackage != RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> "dominant_package_not_app99"
            signature.packageName != RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> "node_tree_package_not_app99"
            signature.nodeCount < 10 -> "node_count_below_10"
            signature.nodeCount >= 20 -> "node_count_not_compact"
            signature.visibleTextNodeCount < 2 -> "visible_text_node_count_below_2"
            signal.floatingPackage == SYSTEM_UI_PACKAGE -> "floating_package_system_ui"
            signal.floatingKind == FloatingWindowKind.SYSTEM_UI_FLOATING -> "floating_kind_system_ui"
            activeCapture != null -> "active_capture_exists"
            pendingCaptureRequest?.priority == CapturePriority.HIGH ||
                pendingCaptureRequest?.priority == CapturePriority.CRITICAL -> "high_priority_pending_exists"
            else -> null
        }
        if (eligibilityReason != null) {
            return null
        }

        val compactSignature = listOf(
            signal.dominantPackage,
            signature.packageName,
            nodeCountBucket,
            signature.visibleTextNodeCount,
            signal.floatingPackage
        ).joinToString("|")
        val lastCapturedAt = ninetyNineCompactDiagnosticCooldownBySignature[compactSignature]
        if (lastCapturedAt != null && nowMs - lastCapturedAt < NINETY_NINE_COMPACT_DIAGNOSTIC_COOLDOWN_MS) {
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_COMPACT_TREE_DIAGNOSTIC_SUPPRESSED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "nodeTreePackage" to signature.packageName,
                "nodeCount" to signature.nodeCount,
                "visibleTextNodeCount" to signature.visibleTextNodeCount,
                "reason" to "same_signature_cooldown"
            )
            return null
        }

        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_COMPACT_TREE_DIAGNOSTIC_ELIGIBLE",
            "dominantPackage" to signal.dominantPackage,
            "floatingPackage" to signal.floatingPackage,
            "nodeTreePackage" to signature.packageName,
            "nodeCount" to signature.nodeCount,
            "visibleTextNodeCount" to signature.visibleTextNodeCount
        )

        val request = CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = signal.eventReceivedAtMs,
            signalEmittedAtMs = signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC,
            platformHint = PlatformHint.NINETY_NINE,
            priority = CapturePriority.NORMAL,
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            floatingBounds = signal.floatingBounds,
            floatingKind = signal.floatingKind,
            reason = "ninety_nine_compact_tree_diagnostic_capture"
        )
        ninetyNineCompactDiagnosticCooldownBySignature[compactSignature] = nowMs
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_COMPACT_TREE_DIAGNOSTIC_REQUEST_CREATED",
            "requestId" to request.id,
            "dominantPackage" to request.dominantPackage,
            "floatingPackage" to request.floatingPackage,
            "nodeTreePackage" to signature.packageName,
            "nodeCount" to signature.nodeCount,
            "visibleTextNodeCount" to signature.visibleTextNodeCount
        )
        return request
    }

    private fun ignoredReason(signal: RadarSignal.UberFloatingOverOtherApp): String? {
        return when (signal.floatingKind) {
            FloatingWindowKind.FLOATING_BUBBLE -> "floating_bubble_not_offer_candidate"
            FloatingWindowKind.SYSTEM_UI_FLOATING -> "system_ui_floating_not_capture_candidate"
            else -> "floating_signal_demoted_to_operational_context"
        }
    }

    private fun computeNinetyNineSignalScore(
        previous: NodeTreeSignature?,
        current: NodeTreeSignature
    ): Int {
        var score = 0
        val previousNodeCount = previous?.nodeCount ?: 0
        val previousVisibleTextNodeCount = previous?.visibleTextNodeCount ?: 0
        val previousBottomHalfTextNodeCount = previous?.bottomHalfTextNodeCount ?: 0

        if (kotlin.math.abs(current.nodeCount - previousNodeCount) > 20) {
            score += 2
        }
        if (kotlin.math.abs(current.visibleTextNodeCount - previousVisibleTextNodeCount) > 3) {
            score += 2
        }
        if (current.bottomHalfTextNodeCount > previousBottomHalfTextNodeCount) {
            score += 2
        }
        if (current.numericTextNodeCount > 0) {
            score += 2
        }
        if (current.buttonLikeNodeCount > 0) {
            score += 2
        }
        return score
    }

    private fun updateNinetyNinePreviousSignature(signal: RadarSignal) {
        if (signal is RadarSignal.NinetyNineTreeStructureChanged) {
            previousNinetyNineSignature = signal.signature
        }
    }

    private companion object {
        val IGNORED_DOMINANT_PACKAGES = setOf(
            "com.lucastrevvos.kmonemotor",
            "com.sec.android.app.launcher",
            "com.android.settings",
            SYSTEM_UI_PACKAGE
        )
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val NINETY_NINE_MIN_SIGNAL_SCORE = 4
        const val UBER_FLOATING_DIAGNOSTIC_COOLDOWN_MS = 5000L
        const val UBER_DOMINANT_DIAGNOSTIC_COOLDOWN_MS = 4000L
        const val NINETY_NINE_COMPACT_DIAGNOSTIC_COOLDOWN_MS = 4000L
    }

    private fun buildUberFloatingDiagnosticSignature(signal: RadarSignal.UberFloatingOverOtherApp): String {
        return listOf(
            signal.dominantPackage,
            signal.floatingPackage,
            signal.floatingBounds,
            roundedCoverage(signal.floatingCoverage)
        ).joinToString("|")
    }

    private fun roundedCoverage(coverage: Double): String {
        return String.format(java.util.Locale.US, "%.3f", coverage)
    }

    private fun logOfferCycleClassification(classification: OfferCycleClassification) {
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_OFFER_CYCLE_CLASSIFIED",
            "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            "cycleId" to classification.cycleId,
            "previousCycleId" to classification.previousCycleId,
            "kind" to classification.kind,
            "reason" to classification.reason,
            "timeSincePreviousMs" to classification.timeSincePreviousMs,
            "shouldPreferForOcr" to classification.shouldPreferForOcr
        )
        val eventName = when (classification.kind) {
            OfferCycleKind.NEW_OFFER_CYCLE -> "KM_V2_OFFER_CYCLE_NEW"
            OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP -> "KM_V2_OFFER_CYCLE_FOLLOWUP"
            OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION -> "KM_V2_OFFER_CYCLE_POST_TRANSITION"
            OfferCycleKind.UNKNOWN -> "KM_V2_OFFER_CYCLE_UNKNOWN"
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            eventName,
            "cycleId" to classification.cycleId,
            "previousCycleId" to classification.previousCycleId,
            "reason" to classification.reason,
            "timeSincePreviousMs" to classification.timeSincePreviousMs,
            "shouldPreferForOcr" to classification.shouldPreferForOcr
        )
    }

    private fun buildUberDominantDiagnosticSignature(signal: RadarSignal.UberStateChanged): String {
        return listOf(
            signal.dominantPackage,
            signal.currentState?.name ?: "null",
            uberNodeCountBucket(signal.nodeCount),
            signal.visibleTextNodeCount,
            signal.numericTextNodeCount
        ).joinToString("|")
    }

    private fun uberNodeCountBucket(nodeCount: Int): String {
        return when {
            nodeCount < 10 -> "0_9"
            nodeCount < 20 -> "10_19"
            nodeCount < 30 -> "20_29"
            else -> "30_plus"
        }
    }

    private data class NinetyNineQualification(
        val score: Int,
        val rejectionReason: String?
    )

    private data class SignalEvaluation(
        val request: CaptureRequest?,
        val ignoredReason: String?
    )
}
