package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCapturer
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotFinishStatus
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassifier
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleSnapshot
import com.lucastrevvos.kmonemotor.radar.core.AnalysisEpochController
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
    private val stabilizationScheduler: StabilizationScheduler = DefaultStabilizationScheduler(),
    private val watchdogScheduler: StabilizationScheduler = DefaultStabilizationScheduler(),
    private val autoMissDiagnostics: AutoMissDiagnostics = AutoMissDiagnostics(),
    private val onObservationCreated: ((ScreenObservation, ScreenshotCaptureResult) -> Unit)? = null
) {
    private var previousNinetyNineSignature: NodeTreeSignature? = null
    private var activeCapture: CaptureRequest? = null
    private var pendingCaptureRequest: CaptureRequest? = null
    private val finishedCaptureIds = mutableSetOf<String>()
    private val uberFloatingDiagnosticCooldownBySignature = mutableMapOf<String, Long>()
    private val ninetyNineVisualProbeCooldownBySignature = mutableMapOf<String, Long>()
    private val ninetyNineVisualProbeRetryScheduledByGroupId = mutableSetOf<String>()
    private var pendingUberOfferCardStabilization: UberOfferCardStabilization? = null
    private var pendingPreOfferWatchdog: PreOfferWatchdogSession? = null
    private val autoEvidenceAccumulator = RadarAutoCaptureEvidenceAccumulator()
    private val autoStateMachine = RadarAutoCaptureStateMachine()

    fun debugBusyReason(): String? {
        return when {
            activeCapture != null -> "active_capture_busy"
            pendingCaptureRequest?.priority == CapturePriority.HIGH ||
                pendingCaptureRequest?.priority == CapturePriority.CRITICAL -> "high_priority_pending_exists"
            else -> null
        }
    }
    private val uberDominantDiagnosticCooldownBySignature = mutableMapOf<String, Long>()
    private val ninetyNineCompactDiagnosticCooldownBySignature = mutableMapOf<String, Long>()

    fun requestManualAnalysis(context: ManualAnalysisContext): CaptureRequest {
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_MANUAL_ANALYSIS_REQUESTED",
            "epoch" to context.analysisEpoch,
            "source" to context.source,
            "manualClickedAtMs" to context.requestedAtMs,
            "dominantPackage" to context.dominantPackage,
            "floatingPackage" to context.floatingPackage
        )
        clearPendingAutomaticWork()
        val request = CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = context.requestedAtMs,
            signalEmittedAtMs = context.requestedAtMs,
            createdAtMs = context.requestedAtMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS,
            platformHint = context.platformHint,
            priority = CapturePriority.CRITICAL,
            dominantPackage = context.dominantPackage,
            floatingPackage = context.floatingPackage,
            floatingBounds = context.floatingBounds,
            floatingKind = context.floatingKind,
            reason = "driver_requested_current_screen_analysis",
            analysisEpoch = context.analysisEpoch,
            isManual = true,
            manualReason = "driver_requested_current_screen_analysis"
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_MANUAL_CAPTURE_QUEUED",
            "requestId" to request.id,
            "epoch" to request.analysisEpoch,
            "activeCaptureExists" to (activeCapture != null)
        )
        RadarDebugStore.updateLastCaptureRequest(request)
        scheduleCapture(request)
        return request
    }

    fun onAutoCapturePipelineFinished(result: AutoCapturePipelineResult) {
        if (result.triggerSource != TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC &&
            result.triggerSource != TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG &&
            result.triggerSource != TriggerSource.NINETY_NINE_VISUAL_PROBE
        ) {
            return
        }
        maybeScheduleNinetyNineVisualProbeRetry(result)
        if (result.triggerSource == TriggerSource.NINETY_NINE_VISUAL_PROBE &&
            result.finalReason == "ninety_nine_map_searching_state"
        ) {
            ninetyNineVisualProbeCooldownBySignature["map_searching"] = result.timestampMs + NINETY_NINE_VISUAL_PROBE_NON_OFFER_COOLDOWN_MS
        }
        if (result.wasPersisted && result.fingerprintKind == "OFFER_LIKE") {
            cancelPreOfferVisualWatchdog("offer_captured")
        }
        applyAutoTransition(autoStateMachine.expireCooldownIfNeeded(result.timestampMs))
        autoStateMachine.onPipelineFinished(result).forEach(::applyAutoTransition)
        logAutoStateSnapshot()
    }

    fun onSignal(signal: RadarSignal) {
        applyAutoTransition(autoStateMachine.expireCooldownIfNeeded(clock.nowMs()))
        handleUberOfferCardStabilizationSignal(signal)
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
            "offerCycleId" to request.offerCycleClassification?.cycleId,
            "analysisEpoch" to request.analysisEpoch,
            "isManual" to request.isManual
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
        if (approvedRequest.isManual) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_MANUAL_CAPTURE_STARTED",
                "requestId" to approvedRequest.id,
                "epoch" to approvedRequest.analysisEpoch
            )
        }
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
        if (request.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC) {
            applyAutoTransition(
                autoStateMachine.transitionToPreOffer("capture_result_map_or_unknown")
            )
            logAutoStateSnapshot()
        }
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
        val epoch = AnalysisEpochController.current()
        return when (signal) {
            is RadarSignal.UberFloatingOverOtherApp -> buildUberFloatingCaptureRequest(signal, nowMs, epoch)
            is RadarSignal.UberStateChanged -> buildUberStateCaptureRequest(signal, nowMs, epoch)
            is RadarSignal.NinetyNineTreeStructureChanged -> buildNinetyNineCaptureRequest(signal, nowMs, epoch)
            is RadarSignal.DominantWindowChanged -> null
        }
    }

    private fun buildUberStateCaptureRequest(
        signal: RadarSignal.UberStateChanged,
        nowMs: Long,
        epoch: Long
    ): CaptureRequest {
        val dominantDiagnosticRequest = buildUberDominantDiagnosticRequest(signal, nowMs, epoch)
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
            reason = "uber_state_${signal.previousState}_to_${signal.currentState}",
            analysisEpoch = epoch
        )
    }

    private fun buildNinetyNineCaptureRequest(
        signal: RadarSignal.NinetyNineTreeStructureChanged,
        nowMs: Long,
        epoch: Long
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
            reason = "ninety_nine_tree_structure_changed",
            analysisEpoch = epoch
        )
    }

    private fun buildUberFloatingCaptureRequest(
        signal: RadarSignal.UberFloatingOverOtherApp,
        nowMs: Long,
        epoch: Long
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
            reason = "uber_floating_over_99_diagnostic_capture",
            analysisEpoch = epoch
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
        nowMs: Long,
        epoch: Long
    ): CaptureRequest? {
        val nodeCountDelta = kotlin.math.abs(signal.nodeCount - (signal.previousNodeCount ?: 0))
        val visibleTextDelta = kotlin.math.abs(
            signal.visibleTextNodeCount - (signal.previousVisibleTextNodeCount ?: 0)
        )
        val matchedConditions = buildUberDominantMatchedConditions(signal)
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_TREE_DELTA_EVALUATED",
            "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            "nodeCountDelta" to nodeCountDelta,
            "visibleTextDelta" to visibleTextDelta,
            "matchedConditions" to matchedConditions.joinToString(",")
        )
        val treeTextSignals = evaluateUberTreeTextSignals(signal)
        val mapSearchingTreeSignal = hasMapSearchingTreeSignal(signal, treeTextSignals)
        val offerCardTreeSignal = hasOfferCardTreeSignal(signal, matchedConditions, treeTextSignals)
        val evidence = RadarAutoCaptureEvidence(
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            hasPriceText = treeTextSignals.hasOfferPriceText,
            hasUberProductText = treeTextSignals.hasUberProductText,
            hasRoutePairText = treeTextSignals.hasRoutePairText,
            hasSearchingText = treeTextSignals.hasSearchingText,
            treeScore = computeUberOfferCardTreeScore(signal, treeTextSignals),
            matchedConditions = matchedConditions,
            knownStateTexts = signal.knownStateTexts,
            timestampMs = nowMs
        )
        autoEvidenceAccumulator.add(evidence)
        autoStateMachine.addEvidence(evidence)
        logAutoEvidenceAdded(evidence)
        val preOfferProbeCandidateReason = searchingDisappearedEmptyTreeProbeCandidateReason(
            signal = signal,
            matchedConditions = matchedConditions,
            treeTextSignals = treeTextSignals,
            treeScore = evidence.treeScore
        )
        autoMissDiagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = nowMs,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "tree_evaluated",
                state = autoStateMachine.snapshot().state,
                treeScore = evidence.treeScore,
                hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                hasUberProductText = treeTextSignals.hasUberProductText,
                hasRoutePairText = treeTextSignals.hasRoutePairText,
                hasSearchingText = treeTextSignals.hasSearchingText,
                isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                operationalReason = treeTextSignals.operationalScreen.reason,
                knownStateTexts = signal.knownStateTexts
            )
        )
        logAutoStateSnapshot()
        logOperationalScreenSignal(signal, treeTextSignals)
        if (pendingPreOfferWatchdog != null &&
            treeTextSignals.operationalScreen.isOperationalScreen &&
            !treeTextSignals.hasOfferPriceText &&
            !treeTextSignals.hasUberProductText &&
            !treeTextSignals.hasRoutePairText
        ) {
            cancelPreOfferVisualWatchdog("operational_screen_detected")
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_MAP_ETA_RANGE_SIGNAL",
            "knownStateTexts" to signal.knownStateTexts.joinToString(","),
            "hasPriceText" to treeTextSignals.hasOfferPriceText,
            "hasUberProductText" to treeTextSignals.hasUberProductText,
            "hasRoutePairText" to treeTextSignals.hasRoutePairText,
            "treeScore" to evidence.treeScore,
            "detected" to treeTextSignals.hasMapEtaRangeText
        )
        if (autoStateMachine.shouldBlockAutomaticCapture(nowMs)) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_AUTO_CAPTURE_BLOCKED_BY_STATE",
                "state" to autoStateMachine.snapshot().state,
                "reason" to autoStateMachine.automaticBlockReason(nowMs)
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "blocked_by_state",
                    reason = autoStateMachine.automaticBlockReason(nowMs),
                    state = autoStateMachine.snapshot().state,
                    treeScore = evidence.treeScore,
                    hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                    hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                    hasUberProductText = treeTextSignals.hasUberProductText,
                    hasRoutePairText = treeTextSignals.hasRoutePairText,
                    hasSearchingText = treeTextSignals.hasSearchingText,
                    isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                    operationalReason = treeTextSignals.operationalScreen.reason,
                    knownStateTexts = signal.knownStateTexts
                )
            )
            return null
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_MAP_SEARCHING_TREE_SIGNAL",
            "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            "detected" to mapSearchingTreeSignal,
            "currentState" to signal.currentState,
            "knownStateTexts" to signal.knownStateTexts.joinToString(",")
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_OFFER_CARD_TREE_SIGNAL",
            "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            "detected" to offerCardTreeSignal,
            "hasOfferPriceText" to treeTextSignals.hasOfferPriceText,
            "hasOperationalMoneyText" to treeTextSignals.hasOperationalMoneyText,
            "hasUberProductText" to treeTextSignals.hasUberProductText,
            "hasRoutePairText" to treeTextSignals.hasRoutePairText,
            "hasSearchingText" to treeTextSignals.hasSearchingText,
            "isOperationalScreen" to treeTextSignals.operationalScreen.isOperationalScreen,
            "operationalReason" to treeTextSignals.operationalScreen.reason,
            "numericTextNodeCount" to signal.numericTextNodeCount,
            "buttonLikeNodeCount" to signal.buttonLikeNodeCount,
            "bottomHalfTextNodeCount" to signal.bottomHalfTextNodeCount,
            "matchedConditions" to matchedConditions.joinToString(","),
            "rejectionReason" to if (offerCardTreeSignal) null else treeTextSignals.rejectionReason
        )
        val hasStrongUberDominantSignal = isStrongUberDominantSignal(signal, matchedConditions, treeTextSignals)
        val systemUiIgnored = hasStrongUberDominantSignal &&
            signal.dominantPackage == RadarConfig.UBER_DRIVER_PACKAGE &&
            signal.nodeTreePackage == RadarConfig.UBER_DRIVER_PACKAGE &&
            (signal.floatingPackage == SYSTEM_UI_PACKAGE ||
                signal.floatingKind == FloatingWindowKind.SYSTEM_UI_FLOATING)

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
        if (systemUiIgnored) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_DOMINANT_SYSTEM_UI_IGNORED",
                "reason" to "strong_uber_dominant_signal",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "nodeTreePackage" to signal.nodeTreePackage,
                "matchedConditions" to matchedConditions.joinToString(",")
            )
        }

        val forcedPreOfferReason = when {
            preOfferProbeCandidateReason != null -> preOfferProbeCandidateReason
            treeTextSignals.rejectionReason == "map_eta_range_without_offer_evidence" &&
                !PreOfferVisualWatchdog.hasHardBlacklist(signal.knownStateTexts) &&
                !treeTextSignals.hasSearchingText -> "map_eta_range_without_offer_evidence"
            mapSearchingTreeSignal && !offerCardTreeSignal -> treeTextSignals.rejectionReason ?: "map_or_searching_signal"
            else -> null
        }
        if (forcedPreOfferReason != null) {
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "trigger_rejected_pre_offer",
                    reason = forcedPreOfferReason,
                    state = autoStateMachine.snapshot().state,
                    treeScore = evidence.treeScore,
                    hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                    hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                    hasUberProductText = treeTextSignals.hasUberProductText,
                    hasRoutePairText = treeTextSignals.hasRoutePairText,
                    hasSearchingText = treeTextSignals.hasSearchingText,
                    isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                    operationalReason = treeTextSignals.operationalScreen.reason,
                    knownStateTexts = signal.knownStateTexts
                )
            )
            applyAutoTransition(
                autoStateMachine.transitionToPreOffer(
                    forcedPreOfferReason,
                    evidence
                )
            )
            maybeStartPreOfferVisualWatchdog(
                signal = signal,
                matchedConditions = matchedConditions,
                treeTextSignals = treeTextSignals,
                rejectionReason = forcedPreOfferReason,
                nowMs = nowMs,
                epoch = epoch
            )
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_TRIGGER_REJECTED_PRE_OFFER_MAP_STATE",
                "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                "reason" to forcedPreOfferReason,
                "currentState" to signal.currentState,
                "knownStateTexts" to signal.knownStateTexts.joinToString(","),
                "matchedConditions" to matchedConditions.joinToString(",")
            )
            RadarLogger.d(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_UBER_DOMINANT_DIAGNOSTIC_REJECTED",
                "dominantPackage" to signal.dominantPackage,
                "floatingPackage" to signal.floatingPackage,
                "nodeTreePackage" to signal.nodeTreePackage,
                "reason" to "pre_offer_map_state"
            )
            return null
        }

        val rejectionReason = when {
            signal.dominantPackage != RadarConfig.UBER_DRIVER_PACKAGE -> "dominant_package_not_uber"
            signal.nodeTreePackage != null && signal.nodeTreePackage != RadarConfig.UBER_DRIVER_PACKAGE -> "node_tree_package_not_uber"
            signal.floatingPackage == SYSTEM_UI_PACKAGE && !systemUiIgnored -> "uber_dominant_weak_signal"
            signal.floatingKind == FloatingWindowKind.SYSTEM_UI_FLOATING && !systemUiIgnored -> "uber_dominant_weak_signal"
            activeCapture != null -> "active_capture_exists"
            pendingCaptureRequest?.priority == CapturePriority.HIGH ||
                pendingCaptureRequest?.priority == CapturePriority.CRITICAL -> "high_priority_pending_exists"
            matchedConditions.isEmpty() -> "weak_signal"
            signal.visibleTextNodeCount < 2 -> "insufficient_visible_text"
            !hasStrongUberDominantSignal -> "insufficient_numeric_or_button_signal"
            else -> null
        }
        if (rejectionReason != null) {
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "offer_card_signal_rejected",
                    reason = treeTextSignals.rejectionReason ?: rejectionReason,
                    state = autoStateMachine.snapshot().state,
                    treeScore = evidence.treeScore,
                    hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                    hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                    hasUberProductText = treeTextSignals.hasUberProductText,
                    hasRoutePairText = treeTextSignals.hasRoutePairText,
                    hasSearchingText = treeTextSignals.hasSearchingText,
                    isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                    operationalReason = treeTextSignals.operationalScreen.reason,
                    knownStateTexts = signal.knownStateTexts
                )
            )
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
        if (offerCardTreeSignal) {
            cancelPreOfferVisualWatchdog("offer_card_signal_detected")
            applyAutoTransition(autoStateMachine.transitionToCandidate("offer_card_tree_signal", evidence))
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_TRIGGER_ACCEPTED_OFFER_CARD_TREE_CHANGE",
                "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                "currentState" to signal.currentState,
                "matchedConditions" to matchedConditions.joinToString(",")
            )
            startOrResetUberOfferCardStabilization(
                signal = signal,
                nowMs = nowMs,
                epoch = epoch,
                matchedConditions = matchedConditions,
                treeTextSignals = treeTextSignals,
                evidence = evidence
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
        val enrichedRequest = buildConfirmedUberDominantDiagnosticRequest(
            signal = signal,
            nowMs = nowMs,
            epoch = epoch,
            matchedConditions = matchedConditions
        ) ?: return null
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
            "offerCycleKind" to enrichedRequest.offerCycleClassification?.kind,
            "offerCycleId" to enrichedRequest.offerCycleClassification?.cycleId
        )
        return enrichedRequest
    }

    private fun buildConfirmedUberDominantDiagnosticRequest(
        signal: RadarSignal.UberStateChanged,
        nowMs: Long,
        epoch: Long,
        matchedConditions: List<String>
    ): CaptureRequest? {
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
            reason = "offer_card_tree_signal_stabilized",
            analysisEpoch = epoch
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
        val rawOfferCycleClassification = offerCycleClassifier.classify(offerCycleSnapshot)
        val offerCycleClassification = promoteOfferCycleClassificationByCardTreeSignal(
            classification = rawOfferCycleClassification,
            offerCardTreeSignal = true
        )
        if (offerCycleClassification != rawOfferCycleClassification) {
            offerCycleClassifier.overrideLastUberDominantClassification(offerCycleClassification)
        }
        logOfferCycleClassification(offerCycleClassification)
        RadarDebugStore.updateOfferCycle(offerCycleClassification)
        return request.copy(offerCycleClassification = offerCycleClassification)
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
                if (signal.dominantPackage != RadarConfig.UBER_DRIVER_PACKAGE) {
                    return SignalEvaluation(request = request, ignoredReason = "dominant_package_not_uber")
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
        val nowMs = request.createdAtMs
        val classifier = NinetyNineNonOfferScreenClassifier.fromTexts(signal.signature.knownStateTexts)
        autoMissDiagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = nowMs,
                triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
                stage = "ninety_nine_signal_emitted",
                reason = "tree_structure_changed",
                nodeCount = signal.signature.nodeCount,
                visibleTextNodeCount = signal.signature.visibleTextNodeCount,
                knownStateTexts = signal.signature.knownStateTexts
            )
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_SIGNAL_RECEIVED_BY_ORCHESTRATOR",
            "nodeCount" to signal.signature.nodeCount,
            "visibleTextNodeCount" to signal.signature.visibleTextNodeCount,
            "knownStateTexts" to signal.signature.knownStateTexts.joinToString(","),
            "currentState" to "foreground_active_hint",
            "floatingCoverage" to null
        )
        if (classifier.isNonOfferMapScreen) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_NON_OFFER_MAP_SCREEN_DETECTED",
                "hasSearchingText" to classifier.hasSearchingText,
                "hasMultiplierText" to classifier.hasMultiplierText,
                "hasOfferPrice" to classifier.hasOfferPrice,
                "hasValuePerKm" to classifier.hasValuePerKm,
                "hasStrong99OfferSignals" to classifier.hasStrong99OfferSignals,
                "reason" to classifier.reason
            )
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_CAPTURE_DECISION",
                "accepted" to false,
                "reason" to classifier.reason,
                "nodeCount" to signal.signature.nodeCount,
                "visibleTextNodeCount" to signal.signature.visibleTextNodeCount,
                "knownStateTexts" to signal.signature.knownStateTexts.joinToString(","),
                "hasOfferSurfaceSignal" to false,
                "hasMapSearchingSignal" to classifier.hasSearchingText,
                "isNonOfferMapScreen" to true
            )
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_CAPTURE_REJECTED",
                "reason" to classifier.reason
            )
            return SignalEvaluation(request = request, ignoredReason = classifier.reason)
        }
        val hasOfferSurfaceSignal = classifier.hasOfferPrice || classifier.hasValuePerKm || classifier.hasStrong99OfferSignals
        if (classifier.hasOperationalText && !hasOfferSurfaceSignal) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_CAPTURE_DECISION",
                "accepted" to false,
                "reason" to "ninety_nine_operational_text_without_offer_surface",
                "nodeCount" to signal.signature.nodeCount,
                "visibleTextNodeCount" to signal.signature.visibleTextNodeCount,
                "knownStateTexts" to signal.signature.knownStateTexts.joinToString(","),
                "hasOfferSurfaceSignal" to false,
                "hasMapSearchingSignal" to classifier.hasSearchingText,
                "isNonOfferMapScreen" to false
            )
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_CAPTURE_REJECTED",
                "reason" to "ninety_nine_operational_text_without_offer_surface",
                "knownStateTexts" to signal.signature.knownStateTexts.joinToString(",")
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
                    stage = "offer_card_signal_rejected",
                    reason = "ninety_nine_operational_text_without_offer_surface",
                    nodeCount = signal.signature.nodeCount,
                    visibleTextNodeCount = signal.signature.visibleTextNodeCount,
                    knownStateTexts = signal.signature.knownStateTexts
                )
            )
            return SignalEvaluation(request = request, ignoredReason = "ninety_nine_operational_text_without_offer_surface")
        }
        val qualification = qualifyNinetyNineSignal(signal)
        val decision = when {
            qualification.rejectionReason == null -> SignalEvaluation(request = request, ignoredReason = null)
            qualification.rejectionReason == "node_count_below_20" -> {
                val compactRequest = buildNinetyNineCompactDiagnosticRequest(signal, request.createdAtMs)
                if (compactRequest != null) {
                    SignalEvaluation(request = compactRequest, ignoredReason = null)
                } else {
                    buildNinetyNineVisualProbeDecision(signal, nowMs, qualification.rejectionReason, request)
                }
            }
            qualification.rejectionReason == "visible_text_node_count_zero" -> {
                buildNinetyNineVisualProbeDecision(signal, nowMs, qualification.rejectionReason, request)
            }
            qualification.rejectionReason == null && !hasOfferSurfaceSignal -> {
                buildNinetyNineVisualProbeDecision(signal, nowMs, "tree_structure_without_offer_surface", request)
            }
            else -> SignalEvaluation(request = request, ignoredReason = qualification.rejectionReason)
        }
        val accepted = decision.ignoredReason == null && decision.request != null
        val chosenReason = decision.request?.reason ?: decision.ignoredReason ?: qualification.rejectionReason
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_CAPTURE_DECISION",
            "accepted" to accepted,
            "reason" to chosenReason,
            "nodeCount" to signal.signature.nodeCount,
            "visibleTextNodeCount" to signal.signature.visibleTextNodeCount,
            "knownStateTexts" to signal.signature.knownStateTexts.joinToString(","),
            "hasOfferSurfaceSignal" to (signal.signature.visibleTextNodeCount > 0),
            "hasMapSearchingSignal" to classifier.hasSearchingText,
            "isNonOfferMapScreen" to classifier.isNonOfferMapScreen
        )
        if (accepted) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_CAPTURE_APPROVED_FROM_TREE",
                "triggerSource" to decision.request?.triggerSource,
                "reason" to chosenReason
            )
        } else {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_CAPTURE_REJECTED",
                "reason" to chosenReason
            )
        }
        return decision
    }

    private fun buildNinetyNineVisualProbeDecision(
        signal: RadarSignal.NinetyNineTreeStructureChanged,
        nowMs: Long,
        rejectionReason: String,
        fallbackRequest: CaptureRequest
    ): SignalEvaluation {
        val probeRequest = buildNinetyNineVisualProbeRequest(signal, nowMs)
        return if (probeRequest != null) {
            SignalEvaluation(request = probeRequest, ignoredReason = null)
        } else {
            SignalEvaluation(request = fallbackRequest, ignoredReason = rejectionReason)
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
        val epoch = AnalysisEpochController.current()
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
            reason = "ninety_nine_compact_tree_diagnostic_capture",
            analysisEpoch = epoch
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

    private fun buildNinetyNineVisualProbeRequest(
        signal: RadarSignal.NinetyNineTreeStructureChanged,
        nowMs: Long
    ): CaptureRequest? {
        val epoch = AnalysisEpochController.current()
        val signature = buildNinetyNineVisualProbeSignature(signal)
        val nonOfferCooldownUntil = ninetyNineVisualProbeCooldownBySignature["map_searching"]
        val suppressionReason = when {
            signal.dominantPackage != RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> "dominant_package_not_app99"
            activeCapture != null -> "active_capture_in_progress"
            pendingCaptureRequest?.priority == CapturePriority.HIGH || pendingCaptureRequest?.priority == CapturePriority.CRITICAL ->
                "high_priority_pending_exists"
            nonOfferCooldownUntil != null && nowMs < nonOfferCooldownUntil -> "non_offer_map_cooldown"
            ninetyNineVisualProbeCooldownBySignature[signature]?.let { nowMs - it < NINETY_NINE_VISUAL_PROBE_COOLDOWN_MS } == true ->
                "same_signature_cooldown"
            else -> null
        }
        if (suppressionReason != null) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_VISUAL_PROBE_SUPPRESSED",
                "reason" to suppressionReason
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                    stage = "recovery_suppressed",
                    reason = suppressionReason,
                    nodeCount = signal.signature.nodeCount,
                    visibleTextNodeCount = signal.signature.visibleTextNodeCount,
                    knownStateTexts = signal.signature.knownStateTexts
                )
            )
            return null
        }
        if (signal.floatingPackage == SYSTEM_UI_PACKAGE || signal.floatingKind == FloatingWindowKind.SYSTEM_UI_FLOATING) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_VISUAL_PROBE_SYSTEM_UI_ALLOWED",
                "floatingCoverage" to null,
                "reason" to "dominant_99_low_system_ui_coverage"
            )
        }

        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_VISUAL_PROBE_CROP_ORDER",
            "preferredCropOrder" to NINETY_NINE_VISUAL_PROBE_PREFERRED_CROP_ORDER.joinToString(",")
        )
        val request = CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = signal.eventReceivedAtMs,
            signalEmittedAtMs = signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
            platformHint = PlatformHint.NINETY_NINE,
            priority = CapturePriority.NORMAL,
            dominantPackage = signal.dominantPackage,
            floatingPackage = signal.floatingPackage,
            floatingBounds = signal.floatingBounds,
            floatingKind = signal.floatingKind,
            reason = "tree_structure_changed_without_text",
            metadataNotes = emptyMap(),
            analysisEpoch = epoch
        )
        val finalizedRequest = request.copy(
            metadataNotes = mapOf(
                "ninetyNineVisualProbePreferredCropOrder" to NINETY_NINE_VISUAL_PROBE_PREFERRED_CROP_ORDER.joinToString(","),
                "ninetyNineVisualProbeSourceGroupId" to request.id
            )
        )
        ninetyNineVisualProbeCooldownBySignature[signature] = nowMs
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_VISUAL_PROBE_REQUESTED",
            "reason" to "tree_structure_changed_without_text"
        )
        return finalizedRequest
    }

    private fun maybeScheduleNinetyNineVisualProbeRetry(result: AutoCapturePipelineResult) {
        if (result.triggerSource != TriggerSource.NINETY_NINE_VISUAL_PROBE ||
            result.wasPersisted ||
            result.retryAttempt > 0
        ) {
            return
        }
        val sourceGroupId = result.sourceGroupId ?: return
        if (ninetyNineVisualProbeRetryScheduledByGroupId.contains(sourceGroupId)) {
            return
        }
        val eligibleFailure = result.visualReason == "no_valid_crop_candidate" ||
            (result.finalReason == "fingerprint_not_offer_like" && result.fingerprintKind != "OFFER_LIKE")
        if (!eligibleFailure ||
            result.finalReason == "ninety_nine_map_searching_state" ||
            result.recentKmOneOverlayVisible
        ) {
            return
        }
        ninetyNineVisualProbeRetryScheduledByGroupId += sourceGroupId
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_99_VISUAL_PROBE_RETRY_SCHEDULED",
            "reason" to "no_valid_crop_candidate_or_unknown",
            "delayMs" to NINETY_NINE_VISUAL_PROBE_RETRY_DELAY_MS,
            "sourceObservationId" to sourceGroupId
        )
        autoMissDiagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = result.timestampMs,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "retry_scheduled",
                reason = "no_valid_crop_candidate_or_unknown"
            )
        )
        watchdogScheduler.schedule(NINETY_NINE_VISUAL_PROBE_RETRY_DELAY_MS) {
            val retryRequest = buildNinetyNineVisualProbeRetryRequest(result)
            if (retryRequest == null) {
                return@schedule
            }
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_VISUAL_PROBE_RETRY_STARTED",
                "retryAttempt" to 1,
                "preferredCropOrder" to NINETY_NINE_VISUAL_PROBE_RETRY_PREFERRED_CROP_ORDER.joinToString(",")
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = clock.nowMs(),
                    triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                    stage = "retry_started",
                    reason = "no_valid_crop_candidate_or_unknown"
                )
            )
            scheduleCapture(retryRequest)
        }
    }

    private fun buildNinetyNineVisualProbeRetryRequest(
        result: AutoCapturePipelineResult
    ): CaptureRequest? {
        val sourceGroupId = result.sourceGroupId ?: return null
        val nowMs = clock.nowMs()
        val suppressionReason = when {
            activeCapture != null -> "active_capture_in_progress"
            pendingCaptureRequest?.priority == CapturePriority.HIGH || pendingCaptureRequest?.priority == CapturePriority.CRITICAL ->
                "high_priority_pending_exists"
            result.recentKmOneOverlayVisible -> "recent_kmone_overlay_visible"
            else -> null
        }
        if (suppressionReason != null) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_99_VISUAL_PROBE_SUPPRESSED",
                "reason" to suppressionReason
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                    stage = "recovery_suppressed",
                    reason = suppressionReason
                )
            )
            return null
        }
        return CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = result.timestampMs,
            signalEmittedAtMs = result.timestampMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
            platformHint = PlatformHint.NINETY_NINE,
            priority = CapturePriority.NORMAL,
            dominantPackage = result.dominantPackage,
            floatingPackage = result.floatingPackage,
            floatingBounds = result.floatingBounds,
            floatingKind = result.floatingKind ?: FloatingWindowKind.FLOATING_BUBBLE,
            reason = "ninety_nine_visual_probe_retry_after_unknown",
            metadataNotes = mapOf(
                "ninetyNineVisualProbePreferredCropOrder" to NINETY_NINE_VISUAL_PROBE_RETRY_PREFERRED_CROP_ORDER.joinToString(","),
                "ninetyNineVisualProbeRetryAttempt" to "1",
                "ninetyNineVisualProbeSourceGroupId" to sourceGroupId
            ),
            analysisEpoch = AnalysisEpochController.current()
        )
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
        const val UBER_OFFER_CARD_STABILIZATION_DELAY_MS = 300L
        const val NINETY_NINE_COMPACT_DIAGNOSTIC_COOLDOWN_MS = 4000L
        const val NINETY_NINE_VISUAL_PROBE_COOLDOWN_MS = 2000L
        const val NINETY_NINE_VISUAL_PROBE_NON_OFFER_COOLDOWN_MS = 5000L
        const val NINETY_NINE_VISUAL_PROBE_RETRY_DELAY_MS = 800L
        val PRE_OFFER_WATCHDOG_PREFERRED_CROP_ORDER = listOf(
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.FLOATING_BOUNDS_EXPANDED,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.LOWER_HALF,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.LOWER_THIRD,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.CENTER_CARD_AREA
        )
        val NINETY_NINE_VISUAL_PROBE_PREFERRED_CROP_ORDER = listOf(
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.LOWER_HALF,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.CENTER_CARD_AREA,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.LOWER_THIRD,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.FULL_DEBUG
        )
        val NINETY_NINE_VISUAL_PROBE_RETRY_PREFERRED_CROP_ORDER = listOf(
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.LOWER_HALF,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.CENTER_CARD_AREA,
            com.lucastrevvos.kmonemotor.radar.vision.CropKind.FULL_DEBUG
        )
        val PRICE_TREE_REGEX = Regex("""(?:r\$|\$)\s*\d""", RegexOption.IGNORE_CASE)
        val PLUS_MONEY_TREE_REGEX = Regex("""\+\s*r\$\s*\d""", RegexOption.IGNORE_CASE)
        val ROUTE_PAIR_TREE_REGEX = Regex("""\b\d+\s*(?:min|minutos).*\b\d+[.,]?\d*\s*km""", RegexOption.IGNORE_CASE)
        val ETA_RANGE_TREE_REGEX = Regex("""\b\d+\s*-\s*\d+\s*min\b""", RegexOption.IGNORE_CASE)
    }

    private fun buildUberFloatingDiagnosticSignature(signal: RadarSignal.UberFloatingOverOtherApp): String {
        return listOf(
            signal.dominantPackage,
            signal.floatingPackage,
            signal.floatingBounds,
            roundedCoverage(signal.floatingCoverage)
        ).joinToString("|")
    }

    private fun buildNinetyNineVisualProbeSignature(signal: RadarSignal.NinetyNineTreeStructureChanged): String {
        val signature = signal.signature
        return listOf(
            signal.dominantPackage,
            signature.packageName,
            signature.nodeCount,
            signature.visibleTextNodeCount,
            signature.bottomHalfTextNodeCount,
            signal.floatingPackage
        ).joinToString("|")
    }

    private fun roundedCoverage(coverage: Double): String {
        return String.format(java.util.Locale.US, "%.3f", coverage)
    }

    private fun buildUberDominantMatchedConditions(signal: RadarSignal.UberStateChanged): List<String> {
        val nodeCountDelta = kotlin.math.abs(signal.nodeCount - (signal.previousNodeCount ?: 0))
        val visibleTextDelta = kotlin.math.abs(
            signal.visibleTextNodeCount - (signal.previousVisibleTextNodeCount ?: 0)
        )
        val previousSearchingText = signal.previousKnownStateTexts.any {
            it.contains("procurando corridas", ignoreCase = true) ||
                it.contains("procurando viagens", ignoreCase = true)
        }
        val currentSearchingText = signal.knownStateTexts.any {
            it.contains("procurando corridas", ignoreCase = true) ||
                it.contains("procurando viagens", ignoreCase = true)
        }
        return buildList {
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
    }

    private fun computeUberOfferCardTreeScore(
        signal: RadarSignal.UberStateChanged,
        treeTextSignals: UberTreeTextSignals
    ): Int {
        var score = 0
        if (treeTextSignals.hasOfferPriceText) score += 3
        if (treeTextSignals.hasUberProductText) score += 3
        if (treeTextSignals.hasRoutePairText) score += 3
        if (signal.numericTextNodeCount >= 2) score += 2
        if (signal.buttonLikeNodeCount >= 1) score += 1
        if (signal.bottomHalfTextNodeCount >= 4) score += 1
        if (treeTextSignals.hasSearchingText) score -= 4
        return score
    }

    private fun clearPendingAutomaticWork() {
        val previousPending = pendingCaptureRequest
        pendingCaptureRequest = null
        cancelUberOfferCardStabilization("manual_request")
        cancelPreOfferVisualWatchdog("manual_request")
        autoEvidenceAccumulator.clear()
        if (previousPending != null && !previousPending.isManual) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_ANALYSIS_PENDING_CLEARED",
                "requestId" to previousPending.id,
                "triggerSource" to previousPending.triggerSource
            )
        }
    }

    private fun logAutoEvidenceAdded(evidence: RadarAutoCaptureEvidence) {
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_AUTO_EVIDENCE_ADDED",
            "triggerSource" to evidence.triggerSource,
            "treeScore" to evidence.treeScore,
            "hasPriceText" to evidence.hasPriceText,
            "hasUberProductText" to evidence.hasUberProductText,
            "hasRoutePairText" to evidence.hasRoutePairText,
            "hasSearchingText" to evidence.hasSearchingText,
            "knownStateTexts" to evidence.knownStateTexts.joinToString(","),
            "matchedConditions" to evidence.matchedConditions.joinToString(",")
        )
    }

    private fun applyAutoTransition(transition: RadarAutoCaptureTransition?) {
        if (transition == null) {
            return
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_AUTO_STATE_TRANSITION",
            "from" to transition.from,
            "to" to transition.to,
            "reason" to transition.reason,
            "triggerSource" to (transition.evidence?.triggerSource ?: TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC),
            "treeScore" to transition.evidence?.treeScore,
            "hasPriceText" to transition.evidence?.hasPriceText,
            "hasUberProductText" to transition.evidence?.hasUberProductText,
            "hasRoutePairText" to transition.evidence?.hasRoutePairText,
            "hasSearchingText" to transition.evidence?.hasSearchingText
        )
        logAutoStateSnapshot()
    }

    private fun logAutoStateSnapshot() {
        val snapshot = autoStateMachine.snapshot()
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_AUTO_STATE_SNAPSHOT",
            "state" to snapshot.state,
            "lastStrongEvidenceAt" to snapshot.lastStrongEvidenceAtMs,
            "lastSearchingEvidenceAt" to snapshot.lastSearchingEvidenceAtMs,
            "pendingStabilization" to snapshot.pendingStabilization
        )
    }

    private data class UberOfferCardStabilization(
        val signal: RadarSignal.UberStateChanged,
        val matchedConditions: List<String>,
        val treeTextSignals: UberTreeTextSignals,
        val evidence: RadarAutoCaptureEvidence,
        val epoch: Long,
        val version: Int,
        val cancellationHandle: StabilizationScheduler.CancellationHandle
    )

    private data class PreOfferWatchdogSession(
        val sessionId: String,
        val signal: RadarSignal.UberStateChanged,
        val epoch: Long,
        val startedAtMs: Long,
        val delaysMs: List<Long>,
        val handles: MutableList<StabilizationScheduler.CancellationHandle>,
        val reason: String
    )

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

    private fun handleUberOfferCardStabilizationSignal(signal: RadarSignal) {
        val pending = pendingUberOfferCardStabilization ?: return
        if (signal !is RadarSignal.UberStateChanged) {
            return
        }
        if (signal.dominantPackage != RadarConfig.UBER_DRIVER_PACKAGE) {
            cancelUberOfferCardStabilization("weak_evidence")
            return
        }
        val matchedConditions = buildUberDominantMatchedConditions(signal)
        val treeTextSignals = evaluateUberTreeTextSignals(signal)
        val evidence = RadarAutoCaptureEvidence(
            triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            hasPriceText = treeTextSignals.hasOfferPriceText,
            hasUberProductText = treeTextSignals.hasUberProductText,
            hasRoutePairText = treeTextSignals.hasRoutePairText,
            hasSearchingText = treeTextSignals.hasSearchingText,
            treeScore = computeUberOfferCardTreeScore(signal, treeTextSignals),
            matchedConditions = matchedConditions,
            knownStateTexts = signal.knownStateTexts,
            timestampMs = clock.nowMs()
        )
        autoEvidenceAccumulator.add(evidence)
        autoStateMachine.addEvidence(evidence)
        logAutoEvidenceAdded(evidence)
        logAutoStateSnapshot()
        if (autoStateMachine.snapshot().state == RadarAutoCaptureState.CAPTURE_IN_PROGRESS) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_AUTO_CAPTURE_BLOCKED_BY_STATE",
                "state" to RadarAutoCaptureState.CAPTURE_IN_PROGRESS,
                "reason" to "capture_already_in_progress"
            )
            return
        }
        val offerCardTreeSignal = hasOfferCardTreeSignal(signal, matchedConditions, treeTextSignals)
        val mapSearchingTreeSignal = hasMapSearchingTreeSignal(signal, treeTextSignals)
        if (mapSearchingTreeSignal) {
            cancelUberOfferCardStabilization("stabilization_cancelled_operational_screen")
            return
        }
        if (offerCardTreeSignal) {
            startOrResetUberOfferCardStabilization(
                signal = signal,
                nowMs = clock.nowMs(),
                epoch = AnalysisEpochController.current(),
                matchedConditions = matchedConditions,
                treeTextSignals = treeTextSignals,
                evidence = evidence,
                isReset = true,
                previousVersion = pending.version
            )
        }
    }

    private fun startOrResetUberOfferCardStabilization(
        signal: RadarSignal.UberStateChanged,
        nowMs: Long,
        epoch: Long,
        matchedConditions: List<String>,
        treeTextSignals: UberTreeTextSignals,
        evidence: RadarAutoCaptureEvidence,
        isReset: Boolean = false,
        previousVersion: Int = 0
    ) {
        pendingUberOfferCardStabilization?.cancellationHandle?.cancel()
        val nextVersion = if (isReset) previousVersion + 1 else (pendingUberOfferCardStabilization?.version ?: 0) + 1
        val treeScore = computeUberOfferCardTreeScore(signal, treeTextSignals)
        val handle = stabilizationScheduler.schedule(UBER_OFFER_CARD_STABILIZATION_DELAY_MS) {
            confirmUberOfferCardStabilization(nextVersion)
        }
        pendingUberOfferCardStabilization = UberOfferCardStabilization(
            signal = signal,
            matchedConditions = matchedConditions,
            treeTextSignals = treeTextSignals,
            evidence = evidence,
            epoch = epoch,
            version = nextVersion,
            cancellationHandle = handle
        )
        if (isReset) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_OFFER_CARD_STABILIZATION_RESET",
                "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                "reason" to "tree_changed_still_candidate"
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "stabilization_started",
                    reason = "tree_changed_still_candidate",
                    state = autoStateMachine.snapshot().state,
                    treeScore = treeScore,
                    hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                    hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                    hasUberProductText = treeTextSignals.hasUberProductText,
                    hasRoutePairText = treeTextSignals.hasRoutePairText,
                    hasSearchingText = treeTextSignals.hasSearchingText,
                    isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                    operationalReason = treeTextSignals.operationalScreen.reason
                )
            )
        } else {
            applyAutoTransition(autoStateMachine.transitionToStabilizing("stabilization_started", evidence))
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_OFFER_CARD_STABILIZATION_STARTED",
                "triggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                "delayMs" to UBER_OFFER_CARD_STABILIZATION_DELAY_MS,
                "treeScore" to treeScore,
                "hasOfferPriceText" to treeTextSignals.hasOfferPriceText,
                "hasOperationalMoneyText" to treeTextSignals.hasOperationalMoneyText,
                "hasUberProductText" to treeTextSignals.hasUberProductText,
                "hasRoutePairText" to treeTextSignals.hasRoutePairText,
                "hasSearchingText" to treeTextSignals.hasSearchingText
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "stabilization_started",
                    reason = "stabilization_started",
                    state = autoStateMachine.snapshot().state,
                    treeScore = treeScore,
                    hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                    hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                    hasUberProductText = treeTextSignals.hasUberProductText,
                    hasRoutePairText = treeTextSignals.hasRoutePairText,
                    hasSearchingText = treeTextSignals.hasSearchingText,
                    isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                    operationalReason = treeTextSignals.operationalScreen.reason
                )
            )
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_CAPTURE_DELAYED_BY_STABILIZATION",
                "originalTriggerSource" to TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                "delayMs" to UBER_OFFER_CARD_STABILIZATION_DELAY_MS
            )
        }
    }

    private fun confirmUberOfferCardStabilization(expectedVersion: Int) {
        val pending = pendingUberOfferCardStabilization ?: return
        if (pending.version != expectedVersion) {
            return
        }
        pendingUberOfferCardStabilization = null
        val signal = pending.signal
        val matchedConditions = buildUberDominantMatchedConditions(signal)
        val treeTextSignals = evaluateUberTreeTextSignals(signal)
        val offerCardTreeSignal = hasOfferCardTreeSignal(signal, matchedConditions, treeTextSignals)
        val mapSearchingTreeSignal = hasMapSearchingTreeSignal(signal, treeTextSignals)
        if (!offerCardTreeSignal || mapSearchingTreeSignal) {
            applyAutoTransition(
                autoStateMachine.transitionToPreOffer(
                    if (mapSearchingTreeSignal) "stabilization_cancelled_operational_screen" else "weak_evidence",
                    pending.evidence
                )
            )
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_OFFER_CARD_STABILIZATION_CANCELLED",
                "reason" to if (mapSearchingTreeSignal) "operational_screen_detected" else "weak_evidence"
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = clock.nowMs(),
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "stabilization_cancelled",
                    reason = if (mapSearchingTreeSignal) "operational_screen_detected" else "weak_evidence",
                    state = autoStateMachine.snapshot().state,
                    treeScore = computeUberOfferCardTreeScore(signal, treeTextSignals),
                    hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                    hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                    hasUberProductText = treeTextSignals.hasUberProductText,
                    hasRoutePairText = treeTextSignals.hasRoutePairText,
                    hasSearchingText = treeTextSignals.hasSearchingText,
                    isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                    operationalReason = treeTextSignals.operationalScreen.reason
                )
            )
            return
        }
        applyAutoTransition(autoStateMachine.transitionToConfirmed("stabilization_confirmed", pending.evidence))
        val nowMs = clock.nowMs()
        val signature = buildUberDominantDiagnosticSignature(signal)
        val lastCapturedAt = uberDominantDiagnosticCooldownBySignature[signature]
        if (lastCapturedAt != null && nowMs - lastCapturedAt < UBER_DOMINANT_DIAGNOSTIC_COOLDOWN_MS) {
            applyAutoTransition(autoStateMachine.transitionToPreOffer("same_signature_cooldown", pending.evidence))
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_OFFER_CARD_STABILIZATION_CANCELLED",
                "reason" to "same_signature_cooldown"
            )
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = nowMs,
                    triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                    stage = "stabilization_cancelled",
                    reason = "same_signature_cooldown",
                    state = autoStateMachine.snapshot().state
                )
            )
            return
        }
        val request = buildConfirmedUberDominantDiagnosticRequest(
            signal = signal,
            nowMs = nowMs,
            epoch = pending.epoch,
            matchedConditions = matchedConditions
        ) ?: return
        applyAutoTransition(autoStateMachine.transitionToCaptureInProgress("capture_request_created", pending.evidence))
        autoMissDiagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = nowMs,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "capture_approved",
                reason = "capture_request_created",
                state = autoStateMachine.snapshot().state,
                treeScore = computeUberOfferCardTreeScore(signal, treeTextSignals),
                hasOfferPriceText = treeTextSignals.hasOfferPriceText,
                hasOperationalMoneyText = treeTextSignals.hasOperationalMoneyText,
                hasUberProductText = treeTextSignals.hasUberProductText,
                hasRoutePairText = treeTextSignals.hasRoutePairText,
                hasSearchingText = treeTextSignals.hasSearchingText,
                isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen,
                operationalReason = treeTextSignals.operationalScreen.reason
            )
        )
        uberDominantDiagnosticCooldownBySignature[signature] = nowMs
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_OFFER_CARD_STABILIZATION_CONFIRMED",
            "reason" to "offer_card_tree_signal_stabilized",
            "finalTreeScore" to computeUberOfferCardTreeScore(signal, treeTextSignals),
            "hasOfferPriceText" to treeTextSignals.hasOfferPriceText,
            "hasUberProductText" to treeTextSignals.hasUberProductText,
            "hasRoutePairText" to treeTextSignals.hasRoutePairText
        )
        RadarDebugStore.updateLastCaptureRequest(request)
        scheduleCapture(request)
    }

    private fun cancelUberOfferCardStabilization(reason: String) {
        val pending = pendingUberOfferCardStabilization ?: return
        pending.cancellationHandle.cancel()
        pendingUberOfferCardStabilization = null
        applyAutoTransition(autoStateMachine.transitionToPreOffer(reason, pending.evidence))
        autoMissDiagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = clock.nowMs(),
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "stabilization_cancelled",
                reason = reason,
                state = autoStateMachine.snapshot().state,
                treeScore = pending.evidence.treeScore,
                hasOfferPriceText = pending.treeTextSignals.hasOfferPriceText,
                hasOperationalMoneyText = pending.treeTextSignals.hasOperationalMoneyText,
                hasUberProductText = pending.treeTextSignals.hasUberProductText,
                hasRoutePairText = pending.treeTextSignals.hasRoutePairText,
                hasSearchingText = pending.treeTextSignals.hasSearchingText,
                isOperationalScreen = pending.treeTextSignals.operationalScreen.isOperationalScreen,
                operationalReason = pending.treeTextSignals.operationalScreen.reason
            )
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_OFFER_CARD_STABILIZATION_CANCELLED",
            "reason" to reason
        )
    }

    private fun maybeStartPreOfferVisualWatchdog(
        signal: RadarSignal.UberStateChanged,
        matchedConditions: List<String>,
        treeTextSignals: UberTreeTextSignals,
        rejectionReason: String?,
        nowMs: Long,
        epoch: Long
    ) {
        val plan = PreOfferVisualWatchdog.planStart(
            rejectionReason = rejectionReason,
            matchedConditions = matchedConditions,
            knownStateTexts = signal.knownStateTexts,
            isOperationalScreen = treeTextSignals.operationalScreen.isOperationalScreen
        )
        if (!plan.shouldStart) {
            if (rejectionReason == "map_eta_range_without_offer_evidence") {
                RadarLogger.i(
                    "KM_V2_ORCHESTRATOR",
                    "KM_V2_PRE_OFFER_WATCHDOG_EXPECTED_BUT_NOT_STARTED",
                    "reason" to rejectionReason
                )
            }
            return
        }
        cancelPreOfferVisualWatchdog("restarted")
        val sessionId = UUID.randomUUID().toString()
        val handles = plan.delaysMs.mapIndexed { index, delayMs ->
            watchdogScheduler.schedule(delayMs) {
                executePreOfferVisualWatchdogProbe(sessionId, index, delayMs)
            }
        }.toMutableList()
        pendingPreOfferWatchdog = PreOfferWatchdogSession(
            sessionId = sessionId,
            signal = signal,
            epoch = epoch,
            startedAtMs = nowMs,
            delaysMs = plan.delaysMs,
            handles = handles,
            reason = plan.reason ?: "pre_offer_watchdog"
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_PRE_OFFER_WATCHDOG_STARTED",
            "reason" to pendingPreOfferWatchdog?.reason,
            "delaysMs" to plan.delaysMs.joinToString(",")
        )
        autoMissDiagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = nowMs,
                triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
                stage = "watchdog_started",
                reason = pendingPreOfferWatchdog?.reason,
                state = autoStateMachine.snapshot().state
            )
        )
    }

    private fun executePreOfferVisualWatchdogProbe(
        sessionId: String,
        probeIndex: Int,
        delayMs: Long
    ) {
        val session = pendingPreOfferWatchdog ?: return
        if (session.sessionId != sessionId) {
            return
        }
        val request = buildPreOfferVisualWatchdogRequest(session, clock.nowMs(), probeIndex) ?: return
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_PRE_OFFER_WATCHDOG_PROBE_REQUESTED",
            "probeIndex" to probeIndex,
            "delayMs" to delayMs,
            "triggerSource" to request.triggerSource,
            "preferredCropOrder" to PRE_OFFER_WATCHDOG_PREFERRED_CROP_ORDER.joinToString(",")
        )
        scheduleCapture(request)
        if (probeIndex == session.delaysMs.lastIndex) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_PRE_OFFER_WATCHDOG_EXHAUSTED",
                "reason" to session.reason
            )
            pendingPreOfferWatchdog = null
        }
    }

    private fun buildPreOfferVisualWatchdogRequest(
        session: PreOfferWatchdogSession,
        nowMs: Long,
        probeIndex: Int = 0
    ): CaptureRequest? {
        val offerCycleClassification = OfferCycleClassification(
            kind = OfferCycleKind.NEW_OFFER_CYCLE,
            cycleId = "watchdog-${UUID.randomUUID()}",
            previousCycleId = session.signal.currentState?.name,
            reason = "pre_offer_visual_watchdog_probe",
            timeSincePreviousMs = nowMs - session.signal.eventReceivedAtMs,
            shouldPreferForOcr = true
        )
        return CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = session.signal.eventReceivedAtMs,
            signalEmittedAtMs = session.signal.signalEmittedAtMs,
            createdAtMs = nowMs,
            approvedAtMs = null,
            triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
            platformHint = PlatformHint.UBER,
            priority = CapturePriority.LOW,
            dominantPackage = session.signal.dominantPackage,
            floatingPackage = session.signal.floatingPackage,
            floatingBounds = session.signal.floatingBounds,
            floatingKind = session.signal.floatingKind,
            reason = "pre_offer_visual_watchdog_probe",
            offerCycleClassification = offerCycleClassification,
            metadataNotes = mapOf(
                "preOfferWatchdogProbeIndex" to probeIndex.toString(),
                "preOfferWatchdogPreferredCropOrder" to PRE_OFFER_WATCHDOG_PREFERRED_CROP_ORDER.joinToString(",")
            ),
            analysisEpoch = session.epoch
        )
    }

    private fun cancelPreOfferVisualWatchdog(reason: String) {
        val session = pendingPreOfferWatchdog ?: return
        session.handles.forEach { it.cancel() }
        pendingPreOfferWatchdog = null
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_PRE_OFFER_WATCHDOG_CANCELLED",
            "reason" to reason
        )
    }

    private fun promoteOfferCycleClassificationByCardTreeSignal(
        classification: OfferCycleClassification,
        offerCardTreeSignal: Boolean
    ): OfferCycleClassification {
        if (!offerCardTreeSignal) {
            return classification
        }
        if (classification.kind == OfferCycleKind.NEW_OFFER_CYCLE &&
            classification.shouldPreferForOcr &&
            classification.reason == "offer_card_tree_signal"
        ) {
            return classification
        }
        val promoted = classification.copy(
            kind = OfferCycleKind.NEW_OFFER_CYCLE,
            reason = "offer_card_tree_signal",
            shouldPreferForOcr = true
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_OFFER_CYCLE_PROMOTED_BY_CARD_TREE_SIGNAL",
            "previousKind" to classification.kind,
            "newKind" to promoted.kind,
            "previousReason" to classification.reason,
            "newReason" to promoted.reason,
            "shouldPreferForOcr" to promoted.shouldPreferForOcr
        )
        return promoted
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

    private fun isStrongUberDominantSignal(
        signal: RadarSignal.UberStateChanged,
        matchedConditions: List<String>,
        treeTextSignals: UberTreeTextSignals
    ): Boolean {
        if (!hasOfferCardTreeSignal(signal, matchedConditions, treeTextSignals)) {
            return false
        }
        val strongMatchedConditions = matchedConditions.count {
            it == "numeric_text_with_visible_text" ||
                it == "button_like_with_visible_text" ||
                it == "tree_delta_threshold" ||
                it == "searching_state_exit" ||
                it == "searching_text_disappeared"
        }
        return signal.nodeTreePackage == RadarConfig.UBER_DRIVER_PACKAGE &&
            signal.nodeCount >= 16 &&
            signal.visibleTextNodeCount >= 2 &&
            (
                strongMatchedConditions >= 2 ||
                    (strongMatchedConditions >= 1 && signal.numericTextNodeCount >= 1) ||
                    (signal.numericTextNodeCount >= 1 && signal.buttonLikeNodeCount >= 1) ||
                    (signal.numericTextNodeCount >= 2) ||
                    (signal.buttonLikeNodeCount >= 2)
                )
    }

    private fun hasMapSearchingTreeSignal(
        signal: RadarSignal.UberStateChanged,
        treeTextSignals: UberTreeTextSignals
    ): Boolean {
        if (treeTextSignals.operationalScreen.isOperationalScreen &&
            !treeTextSignals.hasUberProductText &&
            !treeTextSignals.hasRoutePairText
        ) {
            return true
        }
        if (treeTextSignals.hasMapEtaRangeText &&
            !treeTextSignals.hasOfferPriceText &&
            !treeTextSignals.hasUberProductText &&
            !treeTextSignals.hasRoutePairText
        ) {
            return true
        }
        if (treeTextSignals.hasSearchingText &&
            !treeTextSignals.hasOfferPriceText &&
            !treeTextSignals.hasUberProductText &&
            !treeTextSignals.hasRoutePairText
        ) {
            return true
        }
        val normalizedTexts = signal.knownStateTexts.map { it.lowercase() }
        return signal.currentState == UberReadableState.SEARCHING_RIDES ||
            normalizedTexts.any {
                it.contains("procurando corridas") ||
                    it.contains("procurando viagens") ||
                    it.contains("buscando corridas") ||
                    it.contains("buscando")
            }
    }

    private fun searchingDisappearedEmptyTreeProbeCandidateReason(
        signal: RadarSignal.UberStateChanged,
        matchedConditions: List<String>,
        treeTextSignals: UberTreeTextSignals,
        treeScore: Int
    ): String? {
        val normalizedKnownStateTexts = signal.knownStateTexts
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val hasWeakKnownStateTexts = normalizedKnownStateTexts.isEmpty() ||
            (normalizedKnownStateTexts.size == 1 && normalizedKnownStateTexts.single().length <= 3)
        val hasRequiredMatchedConditions = matchedConditions.contains("searching_text_disappeared") &&
            matchedConditions.contains("tree_delta_threshold")
        if (!hasRequiredMatchedConditions) {
            return null
        }
        if (!hasWeakKnownStateTexts || treeScore > 1) {
            return null
        }
        if (treeTextSignals.hasOfferPriceText || treeTextSignals.hasUberProductText || treeTextSignals.hasRoutePairText) {
            return null
        }
        if (treeTextSignals.operationalScreen.isOperationalScreen || PreOfferVisualWatchdog.hasHardBlacklist(signal.knownStateTexts)) {
            return null
        }
        return "searching_disappeared_empty_tree_probe_candidate"
    }

    private fun hasOfferCardTreeSignal(
        signal: RadarSignal.UberStateChanged,
        matchedConditions: List<String>,
        treeTextSignals: UberTreeTextSignals
    ): Boolean {
        if (treeTextSignals.operationalScreen.isOperationalScreen &&
            !(treeTextSignals.hasUberProductText && (treeTextSignals.hasRoutePairText || treeTextSignals.hasOfferPriceText))
        ) {
            return false
        }
        val strongTextEvidence = treeTextSignals.hasOfferPriceText ||
            treeTextSignals.hasUberProductText ||
            treeTextSignals.hasRoutePairText
        if (!strongTextEvidence) {
            if (signal.buttonLikeNodeCount >= 1 || signal.bottomHalfTextNodeCount >= 2) {
                RadarLogger.i(
                    "KM_V2_ORCHESTRATOR",
                    "KM_V2_OFFER_CARD_TREE_SIGNAL_REJECTED_WEAK_EVIDENCE",
                    "reason" to "button_like_without_price_product_or_route",
                    "numericTextNodeCount" to signal.numericTextNodeCount,
                    "buttonLikeNodeCount" to signal.buttonLikeNodeCount,
                    "bottomHalfTextNodeCount" to signal.bottomHalfTextNodeCount,
                    "knownStateTexts" to signal.knownStateTexts.joinToString(",")
                )
            }
            return false
        }
        if (treeTextSignals.hasSearchingText &&
            !treeTextSignals.hasOfferPriceText &&
            !treeTextSignals.hasUberProductText &&
            !treeTextSignals.hasRoutePairText
        ) {
            return false
        }
        val numericBottomSheet = signal.numericTextNodeCount >= 2
        val numericWithVisibleText = "numeric_text_with_visible_text" in matchedConditions &&
            signal.bottomHalfTextNodeCount >= 2
        val numericWithButtonAndRoute = signal.numericTextNodeCount >= 1 &&
            signal.buttonLikeNodeCount >= 1 &&
            treeTextSignals.hasRoutePairText
        val strongTreeDelta = "tree_delta_threshold" in matchedConditions &&
            (signal.numericTextNodeCount >= 2 ||
                (signal.numericTextNodeCount >= 1 && treeTextSignals.hasRoutePairText) ||
                treeTextSignals.hasOfferPriceText ||
                treeTextSignals.hasUberProductText) &&
            signal.bottomHalfTextNodeCount >= 2
        val searchingExitWithSurface = (
            "searching_state_exit" in matchedConditions ||
                "searching_text_disappeared" in matchedConditions
            ) && (signal.bottomHalfTextNodeCount >= 2 || signal.visibleTextNodeCount >= 3) &&
            (
                "tree_delta_threshold" in matchedConditions ||
                    signal.numericTextNodeCount >= 2 ||
                    treeTextSignals.hasOfferPriceText ||
                    treeTextSignals.hasUberProductText ||
                    treeTextSignals.hasRoutePairText
                )
        return numericBottomSheet || numericWithVisibleText || numericWithButtonAndRoute || strongTreeDelta || searchingExitWithSurface
    }

    private fun evaluateUberTreeTextSignals(signal: RadarSignal.UberStateChanged): UberTreeTextSignals {
        val operationalScreen = UberOperationalScreenClassifier.classify(signal.knownStateTexts)
        val normalizedTexts = operationalScreen.normalizedTexts
        val hasRawMoneyText = normalizedTexts.any { it.contains("r$") || PRICE_TREE_REGEX.containsMatchIn(it) }
        val hasPlusMoneyText = normalizedTexts.any { PLUS_MONEY_TREE_REGEX.containsMatchIn(it) }
        val hasUberProductText = normalizedTexts.any {
            it.contains("uberx") ||
                it.contains("priority") ||
                it.contains("flash") ||
                it.contains("comfort") ||
                it.contains("black") ||
                it.contains("moto") ||
                it.contains("exclusivo")
        }
        val hasRoutePairText = normalizedTexts.any {
            ROUTE_PAIR_TREE_REGEX.containsMatchIn(it) || (it.contains("min") && it.contains("km"))
        }
        val hasMapEtaRangeText = operationalScreen.hasMapEtaRangeText
        val hasSearchingText = operationalScreen.hasSearchingContext
        val hasOperationalMoneyText = operationalScreen.hasOperationalMoneyText ||
            (
                hasRawMoneyText &&
                    (operationalScreen.hasEarningsContext ||
                        operationalScreen.hasOpportunitiesContext ||
                        operationalScreen.hasHomeContext ||
                        operationalScreen.hasOfflineContext ||
                        operationalScreen.hasSearchingContext) &&
                    (hasPlusMoneyText || !hasUberProductText) &&
                    !hasRoutePairText
                )
        if (hasOperationalMoneyText) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_OPERATIONAL_MONEY_TEXT_DETECTED",
                "knownStateTexts" to signal.knownStateTexts.joinToString(","),
                "operationalReason" to operationalScreen.reason,
                "hasUberProductText" to hasUberProductText,
                "hasRoutePairText" to hasRoutePairText
            )
        }
        val hasOfferPriceText = hasRawMoneyText &&
            !hasOperationalMoneyText &&
            (hasUberProductText || hasRoutePairText)
        if (hasRawMoneyText && !hasOfferPriceText) {
            RadarLogger.i(
                "KM_V2_ORCHESTRATOR",
                "KM_V2_OFFER_PRICE_TEXT_REJECTED",
                "reason" to "earnings_context_plus_money",
                "knownStateTexts" to signal.knownStateTexts.joinToString(",")
            )
        }
        val rejectionReason = when {
            operationalScreen.isOperationalScreen && !hasUberProductText && !hasRoutePairText ->
                operationalScreen.reason ?: "operational_screen_without_offer_evidence"
            hasOperationalMoneyText && !hasUberProductText && !hasRoutePairText ->
                "operational_earnings_money_without_offer_evidence"
            hasMapEtaRangeText && !hasOfferPriceText && !hasUberProductText && !hasRoutePairText ->
                "map_eta_range_without_offer_evidence"
            hasSearchingText && !hasOfferPriceText && !hasUberProductText && !hasRoutePairText ->
                "searching_text_without_price_product_or_route"
            !hasOfferPriceText && !hasUberProductText && !hasRoutePairText ->
                "button_like_without_price_product_or_route"
            else -> null
        }
        return UberTreeTextSignals(
            hasOfferPriceText = hasOfferPriceText,
            hasOperationalMoneyText = hasOperationalMoneyText,
            hasUberProductText = hasUberProductText,
            hasRoutePairText = hasRoutePairText,
            hasMapEtaRangeText = hasMapEtaRangeText,
            hasSearchingText = hasSearchingText,
            rejectionReason = rejectionReason,
            operationalScreen = operationalScreen
        )
    }

    private data class UberTreeTextSignals(
        val hasOfferPriceText: Boolean,
        val hasOperationalMoneyText: Boolean,
        val hasUberProductText: Boolean,
        val hasRoutePairText: Boolean,
        val hasMapEtaRangeText: Boolean,
        val hasSearchingText: Boolean,
        val rejectionReason: String?,
        val operationalScreen: UberOperationalScreenSignal
    )

    private fun logOperationalScreenSignal(
        signal: RadarSignal.UberStateChanged,
        treeTextSignals: UberTreeTextSignals
    ) {
        if (!treeTextSignals.operationalScreen.isOperationalScreen) {
            return
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_UBER_OPERATIONAL_SCREEN_DETECTED",
            "reason" to treeTextSignals.operationalScreen.reason,
            "hasOperationalMoneyText" to treeTextSignals.hasOperationalMoneyText,
            "hasMapEtaRangeText" to treeTextSignals.hasMapEtaRangeText,
            "textsPreview" to signal.knownStateTexts.joinToString(",")
        )
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
