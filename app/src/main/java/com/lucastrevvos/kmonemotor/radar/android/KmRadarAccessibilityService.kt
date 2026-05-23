package com.lucastrevvos.kmonemotor.radar.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Process
import android.view.accessibility.AccessibilityEvent
import com.lucastrevvos.kmonemotor.radar.core.AnalysisEpochController
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequest
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequestBus
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisState
import com.lucastrevvos.kmonemotor.radar.core.PlatformHint
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.decision.DecisionSource
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionDebugWriter
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionProcessor
import com.lucastrevvos.kmonemotor.radar.decisionoverlay.DecisionOverlayRuntime
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferFingerprintDedupeDebugWriter
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferFingerprintDedupeEngine
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferFingerprintDedupeProcessor
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferFingerprintDedupeStore
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintDebugWriter
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintExtractor
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.manual.ManualAnalysisPlanner
import com.lucastrevvos.kmonemotor.radar.manual.AndroidManualCropFactory
import com.lucastrevvos.kmonemotor.radar.manual.ManualSecondaryOcrBitmapPreparer
import com.lucastrevvos.kmonemotor.radar.manual.PreparedManualCrop
import com.lucastrevvos.kmonemotor.radar.manual.ManualSecondaryOcrPreparationResult
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservationFactory
import com.lucastrevvos.kmonemotor.radar.ocr.MlKitRegionalOcrEngine
import com.lucastrevvos.kmonemotor.radar.ocr.OcrCandidate
import com.lucastrevvos.kmonemotor.radar.ocr.OcrDebugWriter
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.ocr.OcrRunPolicy
import com.lucastrevvos.kmonemotor.radar.orchestrator.ManualAnalysisContext
import com.lucastrevvos.kmonemotor.radar.orchestrator.AutoCapturePipelineResult
import com.lucastrevvos.kmonemotor.radar.orchestrator.AutoAttemptTrace
import com.lucastrevvos.kmonemotor.radar.orchestrator.AutoMissDiagnostics
import com.lucastrevvos.kmonemotor.radar.orchestrator.RadarCaptureOrchestrator
import com.lucastrevvos.kmonemotor.radar.orchestrator.NinetyNineNonOfferScreenClassifier
import com.lucastrevvos.kmonemotor.radar.parser.OfferParser
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserDebugWriter
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationDebugWriter
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationProcessor
import com.lucastrevvos.kmonemotor.radar.presentation.OverlayPresentationStatePolicy
import com.lucastrevvos.kmonemotor.radar.piu.PiuOverlayRuntime
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureBurstInput
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureBurstPolicy
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureFrameFilter
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureBurstScheduler
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureMicroBurstScheduler
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureMicroBurstTiming
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferPersistenceProcessor
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferPersistenceResult
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferOverlayPolicy
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferPresentationFactory
import com.lucastrevvos.kmonemotor.radar.seenoffers.RideEconomicsCalculator
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOffer
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferRuntime
import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature
import com.lucastrevvos.kmonemotor.radar.signals.FloatingWindowClassifier
import com.lucastrevvos.kmonemotor.radar.signals.OperationalStateTracker
import com.lucastrevvos.kmonemotor.radar.signals.RadarSignalLayer
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import com.lucastrevvos.kmonemotor.radar.vision.AutomaticVisionRecoveryPolicy
import com.lucastrevvos.kmonemotor.radar.vision.FloatingObstructionAction
import com.lucastrevvos.kmonemotor.radar.vision.FloatingObstructionGuard
import com.lucastrevvos.kmonemotor.radar.vision.FloatingObstructionResult
import com.lucastrevvos.kmonemotor.radar.vision.NinetyNineVisualProbeFallback
import com.lucastrevvos.kmonemotor.radar.vision.SmartCropper
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbe
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbeResult
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

class KmRadarAccessibilityService : AccessibilityService() {
    private val instanceId = INSTANCE_COUNTER.incrementAndGet()
    private val clock = RadarClock.System
    private val windowStackReader = WindowStackReader()
    private val nodeTreeReader = NodeTreeReader()
    private val floatingWindowClassifier = FloatingWindowClassifier()
    private val operationalStateTracker = OperationalStateTracker()
    private val signalLayer = RadarSignalLayer()
    private val smartCropper = SmartCropper()
    private val screenObservationFactory = ScreenObservationFactory()
    private val screenshotCapturer by lazy { AccessibilityScreenshotCapturer(this, clock) }
    private val visualOfferProbe by lazy { VisualOfferProbe(this, clock = clock) }
    private val ocrRunPolicy = OcrRunPolicy()
    private val automaticVisionRecoveryPolicy = AutomaticVisionRecoveryPolicy()
    private val floatingObstructionGuard = FloatingObstructionGuard()
    private val automaticCaptureBurstPolicy = AutomaticCaptureBurstPolicy()
    private val automaticCaptureBurstScheduler = AutomaticCaptureBurstScheduler()
    private val automaticCaptureMicroBurstScheduler = AutomaticCaptureMicroBurstScheduler()
    private val ocrDebugWriter by lazy { OcrDebugWriter(this) }
    private val fingerprintExtractor = OfferTextFingerprintExtractor(clock = clock)
    private val fingerprintDebugWriter by lazy { OfferTextFingerprintDebugWriter(this) }
    private val dedupeStore = OfferFingerprintDedupeStore()
    private val dedupeProcessor by lazy {
        OfferFingerprintDedupeProcessor(
            engine = OfferFingerprintDedupeEngine(dedupeStore),
            clock = clock,
            debugWriter = OfferFingerprintDedupeDebugWriter(this)
        )
    }
    private val offerParser by lazy {
        OfferParser(
            clock = clock,
            debugWriter = OfferParserDebugWriter(this)
        )
    }
    private val economicDecisionProcessor by lazy {
        EconomicDecisionProcessor(
            clock = clock,
            debugWriter = EconomicDecisionDebugWriter(this)
        )
    }
    private val decisionPresentationProcessor by lazy {
        DecisionPresentationProcessor(
            clock = clock,
            debugWriter = DecisionPresentationDebugWriter(this)
        )
    }
    private val seenOfferPersistenceProcessor: SeenOfferPersistenceProcessor by lazy {
        SeenOfferRuntime.get(this).persistenceProcessor
    }
    private val seenOfferPresentationFactory = SeenOfferPresentationFactory()
    private val manualAnalysisPlanner = ManualAnalysisPlanner()
    private val manualBitmapPreparer = ManualSecondaryOcrBitmapPreparer(AndroidManualCropFactory)
    private val visionExecutor = Executors.newSingleThreadExecutor()
    private val regionalOcrEngine by lazy { MlKitRegionalOcrEngine(this, visionExecutor, clock) }
    private val manualAnalysisListener: (ManualAnalysisRequest) -> Unit = { handleManualAnalysisRequest(it) }
    private val manualTimings = mutableMapOf<Long, ManualAnalysisTiming>()
    private val finalizedManualEpochs = mutableSetOf<Long>()
    private val autoBurstCaptureInFlight = AtomicBoolean(false)
    private val serviceDestroyed = AtomicBoolean(false)
    private val burstContextByObservationId = mutableMapOf<String, AutoBurstContext>()
    private val initialMicroBurstStateBySourceId = mutableMapOf<String, AutoInitialMicroBurstState>()
    private val initialMicroBurstFrameContextByObservationId = mutableMapOf<String, AutoInitialMicroBurstFrameContext>()
    private val observationsById = mutableMapOf<String, ScreenObservation>()
    private val floatingObstructionByObservationId = mutableMapOf<String, FloatingObstructionResult>()
    private val automaticCaptureSourcesByObservationId = mutableMapOf<String, AutomaticCaptureSourceSnapshot>()
    private val autoMissDiagnostics = AutoMissDiagnostics()
    private var latestWindowSnapshot: WindowStackSnapshot? = null
    private var latestNodeSignature: NodeTreeSignature? = null
    private var latestFloatingKind: FloatingWindowKind = FloatingWindowKind.UNKNOWN_FLOATING
    private var lastKmOneOverlayShownAtMs: Long = Long.MIN_VALUE
    private val orchestrator by lazy {
        RadarCaptureOrchestrator(
            screenshotCapturer = screenshotCapturer,
            clock = clock,
            autoMissDiagnostics = autoMissDiagnostics,
            onObservationCreated = ::onObservationCreated
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val screenshotCapability = hasScreenshotCapability(serviceInfo)
        RadarDebugStore.updateServiceActive(true)
        RadarLogger.i(
            "KM_V2_SIGNAL",
            "KM_V2_ACCESSIBILITY_SERVICE_CONNECTED",
            "pid" to Process.myPid(),
            "instanceId" to instanceId
        )
        RadarLogger.i(
            "KM_V2_SIGNAL",
            "KM_V2_ACCESSIBILITY_SERVICE_CAPABILITIES",
            "capabilities" to serviceInfo.capabilities,
            "canRetrieveWindowContent" to ((serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0),
            "canTakeScreenshot" to screenshotCapability
        )
        if (!screenshotCapability) {
            RadarLogger.w(
                "KM_V2_SIGNAL",
                "KM_V2_SCREENSHOT_CAPABILITY_MISSING",
                "message" to "disable_and_enable_accessibility_service_after_reinstall"
            )
        }
        ManualAnalysisRequestBus.register(manualAnalysisListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val timestampMs = clock.nowMs()
        RadarLogger.d(
            "KM_V2_SIGNAL",
            "KM_V2_ACCESSIBILITY_EVENT",
            "eventType" to event.eventType,
            "package" to event.packageName
        )

        val root = rootInActiveWindow
        val screenBounds = Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val snapshot = windowStackReader.read(windows.orEmpty(), screenBounds, timestampMs)
        val signature = nodeTreeReader.read(root)
        val floatingKind = floatingWindowClassifier.classify(snapshot.topFloatingWindow, timestampMs)
        latestWindowSnapshot = snapshot
        latestNodeSignature = signature
        latestFloatingKind = floatingKind
        RadarDebugStore.updateWindowSnapshot(snapshot, floatingKind)
        RadarDebugStore.updateNodeTree(signature)
        operationalStateTracker.update(snapshot, signature, floatingKind)

        RadarLogger.d(
            "KM_V2_NODE_TREE",
            "KM_V2_NODE_TREE_SIGNATURE",
            "package" to signature.packageName,
            "nodeCount" to signature.nodeCount,
            "visibleTextNodeCount" to signature.visibleTextNodeCount,
            "knownStateTexts" to signature.knownStateTexts.joinToString(",")
        )

        signalLayer.evaluate(event, timestampMs, snapshot, signature, floatingKind).forEach { signal ->
            orchestrator.onSignal(signal)
        }
    }

    override fun onInterrupt() {
        RadarLogger.w(
            "KM_V2_SIGNAL",
            "KM_V2_ACCESSIBILITY_SERVICE_INTERRUPTED",
            "pid" to Process.myPid(),
            "instanceId" to instanceId
        )
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        RadarLogger.w(
            "KM_V2_SIGNAL",
            "KM_V2_ACCESSIBILITY_SERVICE_UNBOUND",
            "pid" to Process.myPid(),
            "instanceId" to instanceId
        )
        RadarDebugStore.updateServiceActive(false)
        ManualAnalysisRequestBus.unregister(manualAnalysisListener)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceDestroyed.set(true)
        automaticCaptureBurstScheduler.destroy()
        automaticCaptureMicroBurstScheduler.destroy()
        RadarDebugStore.updateServiceActive(false)
        visionExecutor.shutdownNow()
        ManualAnalysisRequestBus.unregister(manualAnalysisListener)
        PiuOverlayRuntime.get(this).destroy()
        DecisionOverlayRuntime.get(this).destroy()
        RadarLogger.w(
            "KM_V2_SIGNAL",
            "KM_V2_ACCESSIBILITY_SERVICE_DESTROYED",
            "pid" to Process.myPid(),
            "instanceId" to instanceId
        )
        super.onDestroy()
    }

    private companion object {
        val INSTANCE_COUNTER = AtomicInteger(0)
    }

    private fun hasScreenshotCapability(serviceInfo: AccessibilityServiceInfo): Boolean {
        return (serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
    }

    private fun handleManualAnalysisRequest(request: ManualAnalysisRequest) {
        if (automaticCaptureBurstScheduler.hasPending()) {
            automaticCaptureBurstScheduler.cancel("manual")
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_CANCELLED_BY_MANUAL",
                "source" to request.source
            )
        }
        val requestedAtMs = request.clickedAtMs
        val epoch = AnalysisEpochController.bumpManualEpoch()
        manualTimings[epoch] = ManualAnalysisTiming(clickedAtMs = requestedAtMs)
        synchronized(finalizedManualEpochs) {
            finalizedManualEpochs.remove(epoch)
        }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_ANALYSIS_EPOCH_BUMPED",
            "epoch" to epoch,
            "requestedAtMs" to requestedAtMs,
            "manualClickedSource" to request.source
        )
        RadarDebugStore.updateManualAnalysis(
            epoch = epoch,
            status = "requested"
        )
        RadarDebugStore.updateManualControlState(
            acceptedAtMs = requestedAtMs,
            running = true,
            cooldownRemainingMs = 0L
        )
        val snapshot = latestWindowSnapshot
        val context = ManualAnalysisContext(
            requestedAtMs = requestedAtMs,
            analysisEpoch = epoch,
            source = request.source,
            dominantPackage = snapshot?.topDominantWindow?.packageName,
            floatingPackage = snapshot?.topFloatingWindow?.packageName,
            floatingBounds = snapshot?.topFloatingWindow?.bounds,
            floatingKind = latestFloatingKind,
            platformHint = inferPlatformHint(
                dominantPackage = snapshot?.topDominantWindow?.packageName,
                floatingPackage = snapshot?.topFloatingWindow?.packageName,
                nodeTreePackage = latestNodeSignature?.packageName
            )
        )
        orchestrator.requestManualAnalysis(context)
    }

    private fun onObservationCreated(observation: ScreenObservation, result: com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult) {
        val bitmap = result.screenshotBitmap ?: return
        observationsById[observation.id] = observation
        registerAutomaticCaptureSource(observation)
        registerInitialMicroBurstFrameContext(observation)
        startInitialMicroBurstIfNeeded(observation)
        visionExecutor.execute {
            try {
                if (isStale(observation.analysisEpoch)) {
                    RadarLogger.i(
                        "KM_V2_ORCHESTRATOR",
                        "KM_V2_ANALYSIS_RESULT_STALE_IGNORED",
                        "observationId" to observation.id,
                        "epoch" to observation.analysisEpoch,
                        "triggerSource" to observation.triggerSource
                    )
                    if (observation.isManual) {
                        finalizeManualAnalysis(observation, null, "stale_ignored")
                    }
                    return@execute
                }
                if (observation.isManual) {
                    processManualObservation(observation, bitmap)
                } else {
                    if (isRecentKmOneOverlayVisibleForNinetyNine(observation)) {
                        RadarLogger.i(
                            "KM_V2_AUTO",
                            "KM_V2_99_VISUAL_PROBE_SUPPRESSED",
                            "reason" to "recent_kmone_overlay_visible"
                        )
                        autoMissDiagnostics.recordAutoTrace(
                            AutoAttemptTrace(
                                timestampMs = clock.nowMs(),
                                triggerSource = observation.triggerSource,
                                stage = "recovery_suppressed",
                                reason = "recent_kmone_overlay_visible"
                            )
                        )
                        return@execute
                    }
                    val visualResult = visualOfferProbe.run(observation, bitmap)
                    maybeRunOcr(observation, visualResult, bitmap)
                }
            } catch (throwable: Throwable) {
                RadarLogger.w(
                    "KM_V2_VISION",
                    "KM_V2_VISUAL_PROBE_FAILED",
                    "observationId" to observation.id,
                    "error" to throwable.message,
                    "stacktrace" to throwable.stackTraceToString()
                )
                if (observation.isManual) {
                    finalizeManualAnalysis(observation, null, "failed")
                }
            } finally {
                burstContextByObservationId.remove(observation.id)
                observationsById.remove(observation.id)
                floatingObstructionByObservationId.remove(observation.id)
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun processManualObservation(
        observation: ScreenObservation,
        screenshotBitmap: Bitmap
    ) {
        updateManualTiming(observation.analysisEpoch) {
            copy(
                captureStartedAtMs = observation.screenshotStartedAtMs,
                observationAtMs = observation.observationCreatedAtMs
            )
        }
        RadarDebugStore.updateManualAnalysis(
            epoch = observation.analysisEpoch,
            status = "observation_created"
        )
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_MANUAL_VISION_STARTED",
            "observationId" to observation.id,
            "epoch" to observation.analysisEpoch
        )
        val visualResult = visualOfferProbe.run(observation, screenshotBitmap)
        updateManualTiming(observation.analysisEpoch) {
            copy(visionFinishedAtMs = clock.nowMs())
        }
        val candidates = smartCropper.createCandidates(observation)
        val manualSequenceCompleted = AtomicBoolean(false)
        val primarySelection = manualAnalysisPlanner.selectPrimaryCandidate(
            observation = observation,
            visualResult = visualResult,
            candidates = candidates
        )
        if (primarySelection == null) {
            RadarLogger.w(
                "KM_V2_OCR",
                "KM_V2_OCR_POLICY_DECISION",
                "observationId" to observation.id,
                "shouldRun" to false,
                "reason" to "ocr_skipped_manual_no_candidate",
                "offerCycleKind" to observation.offerCycleClassification?.kind,
                "cropKind" to null
            )
            finalizeManualAnalysis(observation, null, "no_candidate")
            return
        }
        val manualSecondaryCandidate = candidates.firstOrNull {
            it.kind == CropKind.LOWER_HALF && it.id != primarySelection.candidate.id
        }
        val preparedBitmaps = when (
            val preparation = manualBitmapPreparer.prepare(
                source = screenshotBitmap,
                candidates = listOfNotNull(primarySelection.candidate, manualSecondaryCandidate)
            )
        ) {
            is ManualSecondaryOcrPreparationResult.Prepared -> {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_PREPARED",
                    "observationId" to observation.id,
                    "preparedCount" to preparation.preparedByCropId.size
                )
                preparation.preparedByCropId
            }

            is ManualSecondaryOcrPreparationResult.SourceRecycled -> {
                RadarLogger.w(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED_SOURCE_RECYCLED",
                    "observationId" to observation.id,
                    "epoch" to observation.analysisEpoch
                )
                RadarLogger.w(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_BITMAP_LIFECYCLE_WARNING",
                    "observationId" to observation.id,
                    "warning" to "source_bitmap_recycled_before_manual_fallback_preparation"
                )
                RadarDebugStore.updateManualAnalysis(
                    epoch = observation.analysisEpoch,
                    status = "secondary_failed",
                    secondaryOcrStatus = "source_recycled",
                    bitmapWarning = "source_bitmap_recycled_before_manual_fallback_preparation"
                )
                finalizeManualAnalysis(observation, null, "secondary_failed")
                return
            }
        }
        val preparedBitmapsReleased = AtomicBoolean(false)
        val releasePreparedBitmapsOnce = {
            if (preparedBitmapsReleased.compareAndSet(false, true)) {
                releaseManualPreparedBitmaps(preparedBitmaps)
            } else {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_BITMAP_RELEASE_SKIPPED_ALREADY_RELEASED",
                    "observationId" to observation.id,
                    "epoch" to observation.analysisEpoch
                )
            }
        }
        val finalizeManualOnce = { fingerprint: OfferTextFingerprint?, status: String ->
            if (manualSequenceCompleted.compareAndSet(false, true)) {
                finalizeManualAnalysis(observation, fingerprint, status)
            } else {
                RadarLogger.i(
                    "KM_V2_MANUAL",
                    "KM_V2_MANUAL_DUPLICATE_COMPLETION_IGNORED",
                    "observationId" to observation.id,
                    "epoch" to observation.analysisEpoch,
                    "status" to status
                )
                RadarLogger.i(
                    "KM_V2_MANUAL",
                    "KM_V2_LATENCY_MANUAL_ANALYSIS_DUPLICATE_SKIPPED",
                    "epoch" to observation.analysisEpoch,
                    "status" to status
                )
            }
        }
        if (primarySelection.reason != "manual_best_candidate") {
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_MANUAL_VISION_FALLBACK_APPLIED",
                "observationId" to observation.id,
                "cropKind" to primarySelection.candidate.kind,
                "reason" to primarySelection.reason
            )
        }
        val primaryPolicy = if (primarySelection.reason == "manual_best_candidate") {
            ocrRunPolicy.decide(observation, visualResult)
        } else {
            ocrRunPolicy.decideManualFallback(primarySelection.candidate.kind)
        }
        RadarLogger.i(
            "KM_V2_OCR",
            "KM_V2_OCR_POLICY_DECISION",
            "observationId" to observation.id,
            "shouldRun" to primaryPolicy.shouldRun,
            "reason" to primaryPolicy.reason,
            "offerCycleKind" to observation.offerCycleClassification?.kind,
            "cropKind" to primarySelection.candidate.kind
        )
        if (!primaryPolicy.shouldRun) {
            releasePreparedBitmapsOnce()
            finalizeManualOnce(null, primaryPolicy.reason)
            return
        }
        RadarLogger.i(
            "KM_V2_OCR",
            "KM_V2_MANUAL_OCR_CANDIDATE_SELECTED",
            "observationId" to observation.id,
            "cropKind" to primarySelection.candidate.kind,
            "reason" to primarySelection.reason
        )
        val primaryPreparedBitmap = preparedBitmaps[primarySelection.candidate.id]?.bitmap
        if (primaryPreparedBitmap == null) {
            releasePreparedBitmapsOnce()
            RadarDebugStore.updateManualAnalysis(
                epoch = observation.analysisEpoch,
                status = "secondary_failed",
                secondaryOcrStatus = "missing_primary_prepared_crop"
            )
            finalizeManualOnce(null, "secondary_failed")
            return
        }
        executePreparedOcrCandidate(
            observation = observation,
            selectedCandidate = primarySelection.candidate,
            croppedBitmap = primaryPreparedBitmap,
            acceptedForOcrFuture = visualResult.acceptedForOcrFuture,
            candidateReason = primarySelection.reason,
            policyReason = primaryPolicy.reason
        ) { firstFingerprint ->
            if (isStale(observation.analysisEpoch)) {
                releasePreparedBitmapsOnce()
                finalizeManualOnce(firstFingerprint, "stale_ignored")
                return@executePreparedOcrCandidate
            }
            if (manualSequenceCompleted.get()) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED",
                    "reason" to "sequence_completed",
                    "epoch" to observation.analysisEpoch
                )
                releasePreparedBitmapsOnce()
                return@executePreparedOcrCandidate
            }
            if (firstFingerprint?.kind == OfferTextFingerprintKind.OFFER_LIKE) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED_PRIMARY_OFFER_LIKE",
                    "observationId" to observation.id,
                    "epoch" to observation.analysisEpoch
                )
                releasePreparedBitmapsOnce()
                finalizeManualOnce(firstFingerprint, "primary_completed")
                return@executePreparedOcrCandidate
            }
            val secondarySelection = manualAnalysisPlanner.selectSecondaryCandidate(
                primary = primarySelection.candidate,
                fingerprintKind = firstFingerprint?.kind,
                candidates = candidates
            )
            if (secondarySelection == null) {
                releasePreparedBitmapsOnce()
                finalizeManualOnce(firstFingerprint, "primary_completed")
                return@executePreparedOcrCandidate
            }
            if (!AnalysisEpochController.isCurrent(observation.analysisEpoch)) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED",
                    "reason" to "stale_epoch",
                    "epoch" to observation.analysisEpoch
                )
                releasePreparedBitmapsOnce()
                finalizeManualOnce(firstFingerprint, "stale_ignored")
                return@executePreparedOcrCandidate
            }
            val secondaryPolicy = ocrRunPolicy.decideManualFallback(secondarySelection.candidate.kind)
            if (!secondaryPolicy.shouldRun) {
                releasePreparedBitmapsOnce()
                finalizeManualOnce(firstFingerprint, secondaryPolicy.reason)
                return@executePreparedOcrCandidate
            }
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_MANUAL_SECONDARY_OCR_STARTED",
                "observationId" to observation.id,
                "cropKind" to secondarySelection.candidate.kind,
                "reason" to secondarySelection.reason
            )
            RadarDebugStore.updateManualAnalysis(
                epoch = observation.analysisEpoch,
                status = "ocr_completed",
                secondaryOcrStatus = "secondary_started"
            )
            val secondaryPreparedBitmap = preparedBitmaps[secondarySelection.candidate.id]?.bitmap
            if (secondaryPreparedBitmap == null) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED",
                    "reason" to "candidate_missing",
                    "epoch" to observation.analysisEpoch
                )
                releasePreparedBitmapsOnce()
                RadarDebugStore.updateManualAnalysis(
                    epoch = observation.analysisEpoch,
                    status = "secondary_failed",
                    secondaryOcrStatus = "missing_prepared_crop"
                )
                finalizeManualOnce(firstFingerprint, "secondary_failed")
                return@executePreparedOcrCandidate
            }
            if (secondaryPreparedBitmap.isRecycled || preparedBitmapsReleased.get()) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED",
                    "reason" to "bitmap_released",
                    "epoch" to observation.analysisEpoch
                )
                releasePreparedBitmapsOnce()
                finalizeManualOnce(firstFingerprint, "secondary_failed")
                return@executePreparedOcrCandidate
            }
            executePreparedOcrCandidate(
                observation = observation,
                selectedCandidate = secondarySelection.candidate,
                croppedBitmap = secondaryPreparedBitmap,
                acceptedForOcrFuture = true,
                candidateReason = secondarySelection.reason,
                policyReason = secondaryPolicy.reason
            ) { secondaryFingerprint ->
                if (manualSequenceCompleted.get()) {
                    RadarLogger.i(
                        "KM_V2_OCR",
                        "KM_V2_MANUAL_SECONDARY_OCR_SKIPPED",
                        "reason" to "sequence_completed",
                        "epoch" to observation.analysisEpoch
                    )
                    releasePreparedBitmapsOnce()
                    return@executePreparedOcrCandidate
                }
                releasePreparedBitmapsOnce()
                RadarDebugStore.updateManualAnalysis(
                    epoch = observation.analysisEpoch,
                    status = "secondary_completed",
                    secondaryOcrStatus = "secondary_completed"
                )
                finalizeManualOnce(secondaryFingerprint ?: firstFingerprint, "secondary_completed")
            }
        }
    }

    private fun maybeRunOcr(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        screenshotBitmap: Bitmap
    ) {
        val candidates = smartCropper.createCandidates(observation)
        val obstructionResult = evaluateFloatingObstruction(observation, visualResult, candidates)
        val adjustedVisualResult = applyFloatingObstructionAdjustment(
            observation = observation,
            visualResult = visualResult,
            candidates = candidates,
            obstructionResult = obstructionResult
        )
        val recoveryDecision = evaluateAutomaticVisionRecovery(observation, adjustedVisualResult, candidates)
        if (!observation.isManual &&
            observation.triggerSource == TriggerSource.NINETY_NINE_VISUAL_PROBE &&
            adjustedVisualResult.reason == "no_valid_crop_candidate"
        ) {
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = clock.nowMs(),
                    triggerSource = observation.triggerSource,
                    stage = "vision_no_valid_crop_candidate",
                    reason = "no_valid_crop_candidate"
                )
            )
        }
        val recoveredVisualResult = buildEffectiveVisualResult(adjustedVisualResult, recoveryDecision)
        val ninetyNineFallback = NinetyNineVisualProbeFallback.applyIfNeeded(
            triggerSource = observation.triggerSource,
            visualResult = recoveredVisualResult,
            candidates = candidates
        )
        val effectiveVisualResult = ninetyNineFallback.visualResult
        if (ninetyNineFallback.applied) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_99_VISUAL_PROBE_FALLBACK_CROP_APPLIED",
                "reason" to "no_valid_crop_candidate",
                "fallbackCropKind" to ninetyNineFallback.fallbackCropKind
            )
        }
        updateAutomaticCaptureSource(
            observationId = observation.id,
            selectedCropKind = effectiveVisualResult.bestCandidate?.kind
        )
        if (!observation.isManual) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_CAPTURE_SELECTED_CROP",
                "observationId" to observation.id,
                "selectedCropKind" to effectiveVisualResult.bestCandidate?.kind,
                "reason" to effectiveVisualResult.reason
            )
        }
        var policy = ocrRunPolicy.decide(observation, effectiveVisualResult)
        if (recoveryDecision.overridePostTransition && policy.reason == "ocr_skipped_post_transition") {
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_AUTO_POST_TRANSITION_OVERRIDDEN",
                "observationId" to observation.id,
                "triggerSource" to observation.triggerSource,
                "cropKind" to effectiveVisualResult.bestCandidate?.kind
            )
            policy = OcrRunPolicy.OcrPolicyDecision(
                shouldRun = true,
                reason = recoveryDecision.reason
            )
        } else if (policy.shouldRun && recoveryDecision.shouldForceOcr && recoveryDecision.reason != policy.reason) {
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_AUTO_OCR_ALLOWED_BY_STRONG_TRIGGER",
                "observationId" to observation.id,
                "triggerSource" to observation.triggerSource,
                "cropKind" to effectiveVisualResult.bestCandidate?.kind,
                "reason" to recoveryDecision.reason
            )
            policy = policy.copy(reason = recoveryDecision.reason)
        }
        RadarLogger.i(
            "KM_V2_OCR",
            "KM_V2_OCR_POLICY_DECISION",
            "observationId" to observation.id,
            "shouldRun" to policy.shouldRun,
            "reason" to policy.reason,
            "offerCycleKind" to observation.offerCycleClassification?.kind,
            "cropKind" to effectiveVisualResult.bestCandidate?.kind
        )
        RadarDebugStore.updateOcrSummary(
            durationMs = null,
            success = null,
            cropKind = effectiveVisualResult.bestCandidate?.kind?.name,
            rawTextPreview = null,
            policyReason = policy.reason
        )
        if (observation.triggerSource == TriggerSource.NINETY_NINE_VISUAL_PROBE &&
            effectiveVisualResult.bestCandidate == null
        ) {
            orchestrator.onAutoCapturePipelineFinished(
                AutoCapturePipelineResult(
                    triggerSource = observation.triggerSource,
                    fingerprintKind = null,
                    wasPersisted = false,
                    finalReason = "no_valid_crop_candidate",
                    timestampMs = clock.nowMs(),
                    visualReason = "no_valid_crop_candidate",
                    sourceGroupId = observation.metadata.notes["ninetyNineVisualProbeSourceGroupId"] ?: observation.captureRequestId,
                    retryAttempt = observation.metadata.notes["ninetyNineVisualProbeRetryAttempt"]?.toIntOrNull() ?: 0,
                    dominantPackage = observation.dominantPackage,
                    floatingPackage = observation.floatingPackage,
                    floatingBounds = observation.floatingBounds,
                    floatingKind = observation.floatingKind,
                    recentKmOneOverlayVisible = isRecentKmOneOverlayVisibleForNinetyNine(observation)
                )
            )
            return
        }
        if (!policy.shouldRun) {
            return
        }

        val bestCandidate = effectiveVisualResult.bestCandidate ?: return
        executeOcrCandidate(
            observation = observation,
            selectedCandidate = bestCandidate,
            screenshotBitmap = screenshotBitmap,
            acceptedForOcrFuture = effectiveVisualResult.acceptedForOcrFuture,
            candidateReason = effectiveVisualResult.reason,
            policyReason = policy.reason
        )
    }

    private fun evaluateAutomaticVisionRecovery(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>
    ): AutomaticVisionRecoveryPolicy.AutomaticVisionRecoveryDecision {
        val recoveryDecision = automaticVisionRecoveryPolicy.decide(
            observation = observation,
            visualResult = visualResult,
            candidates = candidates,
            nowMs = clock.nowMs()
        )
        val recoveryReason = if (
            visualResult.reason == "floating_obstruction_alternative_crop" &&
            recoveryDecision.reason == "not_needed"
        ) {
            "alternative_crop_applied"
        } else {
            recoveryDecision.reason
        }
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_AUTO_VISION_RECOVERY_EVALUATED",
            "observationId" to observation.id,
            "triggerSource" to observation.triggerSource,
            "visualReason" to visualResult.reason,
            "recoveryReason" to recoveryReason
        )
        when (recoveryDecision.suppressionReason) {
            "cooldown_suppressed" -> {
                RadarLogger.i(
                    "KM_V2_VISION",
                    "KM_V2_AUTO_RECOVERY_COOLDOWN_SUPPRESSED",
                    "observationId" to observation.id,
                    "triggerSource" to observation.triggerSource
                )
            }

            "attempt_limit_reached" -> {
                RadarLogger.i(
                    "KM_V2_VISION",
                    "KM_V2_AUTO_RECOVERY_ATTEMPT_LIMIT_REACHED",
                    "observationId" to observation.id,
                    "triggerSource" to observation.triggerSource
                )
            }
        }
        if (recoveryDecision.recoveryApplied && visualResult.bestCandidate == null && recoveryDecision.selectedCandidate != null) {
            val fallbackReason = when (recoveryDecision.selectedCandidate.kind) {
                CropKind.LOWER_HALF -> "auto_recovery_lower_half"
                CropKind.CENTER_CARD_AREA -> "auto_recovery_center_card"
                else -> "auto_recovery_candidate"
            }
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_AUTO_VISION_FALLBACK_APPLIED",
                "observationId" to observation.id,
                "triggerSource" to observation.triggerSource,
                "fromReason" to visualResult.reason,
                "cropKind" to recoveryDecision.selectedCandidate.kind,
                "reason" to fallbackReason
            )
        } else if (!recoveryDecision.shouldForceOcr && recoveryDecision.suppressionReason != "not_needed") {
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_AUTO_VISION_RECOVERY_SKIPPED",
                "observationId" to observation.id,
                "triggerSource" to observation.triggerSource,
                "reason" to recoveryDecision.suppressionReason
            )
        }
        RadarDebugStore.updateAutomaticRecoverySummary(
            applied = recoveryDecision.recoveryApplied || recoveryDecision.overridePostTransition,
            reason = recoveryReason,
            cropKind = recoveryDecision.selectedCandidate?.kind?.name,
            postTransitionOverridden = recoveryDecision.overridePostTransition,
            suppressedReason = recoveryDecision.suppressionReason
        )
        return recoveryDecision
    }

    private fun evaluateFloatingObstruction(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>
    ): FloatingObstructionResult {
        val result = floatingObstructionGuard.evaluate(
            observation = observation,
            visualResult = visualResult,
            candidates = candidates
        )
        floatingObstructionByObservationId[observation.id] = result
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_FLOATING_OBSTRUCTION_EVALUATED",
            "observationId" to observation.id,
            "triggerSource" to observation.triggerSource,
            "enabled" to com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags.ENABLE_FLOATING_OBSTRUCTION_GUARD,
            "detected" to result.detected,
            "obstructionRect" to result.obstructionRect?.flattenToString(),
            "selectedCropKind" to visualResult.bestCandidate?.kind,
            "overlapsCriticalOfferArea" to result.overlapsCriticalOfferArea,
            "confidence" to result.confidence,
            "reason" to result.reason,
            "suggestedAction" to result.suggestedAction
        )
        RadarDebugStore.updateFloatingObstructionSummary(
            detected = result.detected,
            reason = result.reason,
            cropKind = visualResult.bestCandidate?.kind?.name,
            confidence = result.confidence
        )
        updateAutomaticCaptureSource(
            observationId = observation.id,
            obstructionResult = result,
            obstructionAction = result.suggestedAction
        )
        val selectedCandidate = visualResult.bestCandidate
        if (
            observation.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC &&
            selectedCandidate?.kind == CropKind.LOWER_HALF &&
            result.detected &&
            result.overlapsCriticalOfferArea &&
            result.obstructionRect != null &&
            !rectsIntersect(selectedCandidate.rect, result.obstructionRect)
        ) {
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_UBER_DOMINANT_OBSTRUCTION_OVERRIDE_BLOCKED",
                "observationId" to observation.id,
                "originalSelectedCropKind" to selectedCandidate.kind,
                "attemptedAlternativeCropKind" to CropKind.FLOATING_BOUNDS_EXPANDED,
                "obstructionRect" to result.obstructionRect.flattenToString(),
                "reason" to "lower_half_safe_keep_priority"
            )
        }
        return result
    }

    private fun applyFloatingObstructionAdjustment(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>,
        obstructionResult: FloatingObstructionResult
    ): VisualOfferProbeResult {
        val selectedCandidate = visualResult.bestCandidate ?: return visualResult
        if (!obstructionResult.detected ||
            !obstructionResult.overlapsCriticalOfferArea ||
            obstructionResult.suggestedAction != FloatingObstructionAction.TRY_ALTERNATIVE_CROP ||
            selectedCandidate.kind != CropKind.FLOATING_BOUNDS_EXPANDED
        ) {
            return visualResult
        }
        val alternativeCandidate = selectAlternativeObstructionCandidate(
            observation = observation,
            selectedCandidate = selectedCandidate,
            candidates = candidates,
            visualResult = visualResult
        ) ?: return visualResult
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_FLOATING_OBSTRUCTION_ALTERNATIVE_CROP_SELECTED",
            "observationId" to observation.id,
            "fromCropKind" to selectedCandidate.kind,
            "toCropKind" to alternativeCandidate.kind,
            "reason" to "floating_bounds_expanded_obstructed"
        )
        if (observation.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC) {
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_UBER_DOMINANT_OBSTRUCTION_ALTERNATIVE_SELECTED",
                "observationId" to observation.id,
                "selectedAlternativeCropKind" to alternativeCandidate.kind,
                "reason" to "floating_bounds_expanded_obstructed"
            )
        }
        updateAutomaticCaptureSource(
            observationId = observation.id,
            selectedCropKind = alternativeCandidate.kind,
            obstructionResult = obstructionResult,
            obstructionAction = obstructionResult.suggestedAction,
            alternativeCropApplied = true,
            originalCropKind = selectedCandidate.kind
        )
        return visualResult.copy(
            bestCandidate = alternativeCandidate,
            reason = "floating_obstruction_alternative_crop"
        )
    }

    private fun selectAlternativeObstructionCandidate(
        observation: ScreenObservation,
        selectedCandidate: CropCandidate,
        candidates: List<CropCandidate>,
        visualResult: VisualOfferProbeResult
    ): CropCandidate? {
        val probeByCropId = visualResult.allProbeResults.associateBy { it.cropId }
        val preferredKinds = when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC -> listOf(
                CropKind.LOWER_HALF,
                CropKind.LOWER_THIRD,
                CropKind.CENTER_CARD_AREA,
                CropKind.FLOATING_BOUNDS_EXPANDED,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE
            )
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE
            )
            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC,
            TriggerSource.NINETY_NINE_VISUAL_PROBE -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
                CropKind.LOWER_THIRD,
                CropKind.FULL_DEBUG,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE
            )
            else -> listOf(CropKind.CENTER_CARD_AREA, CropKind.PLATFORM_SPECIFIC_CANDIDATE, CropKind.LOWER_HALF)
        }
        return preferredKinds.asSequence()
            .mapNotNull { preferredKind ->
                candidates.firstOrNull { candidate ->
                    candidate.kind == preferredKind &&
                        candidate.id != selectedCandidate.id &&
                        candidate.width >= 40 &&
                        candidate.height >= 40 &&
                        candidateSafeFromObstruction(
                            candidate = candidate,
                            obstructionRect = floatingObstructionByObservationId[observation.id]?.obstructionRect
                        ) &&
                        probeByCropId[candidate.id]?.rejectionReason == null
                }
            }
            .firstOrNull()
    }

    private fun buildEffectiveVisualResult(
        visualResult: VisualOfferProbeResult,
        recoveryDecision: AutomaticVisionRecoveryPolicy.AutomaticVisionRecoveryDecision
    ): VisualOfferProbeResult {
        if (!recoveryDecision.shouldForceOcr) {
            return visualResult
        }
        return visualResult.copy(
            bestCandidate = recoveryDecision.selectedCandidate ?: visualResult.bestCandidate,
            acceptedForOcrFuture = if (recoveryDecision.forcedAcceptance) true else visualResult.acceptedForOcrFuture,
            reason = recoveryDecision.reason
        )
    }

    private fun executeOcrCandidate(
        observation: ScreenObservation,
        selectedCandidate: CropCandidate,
        screenshotBitmap: Bitmap,
        acceptedForOcrFuture: Boolean,
        candidateReason: String,
        policyReason: String,
        onFingerprintReady: (OfferTextFingerprint?) -> Unit = {}
    ) {
        val croppedBitmap = try {
            Bitmap.createBitmap(
                screenshotBitmap,
                selectedCandidate.rect.left,
                selectedCandidate.rect.top,
                selectedCandidate.rect.width().coerceAtLeast(1),
                selectedCandidate.rect.height().coerceAtLeast(1)
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OCR",
                "KM_V2_OCR_FAILED",
                "observationId" to observation.id,
                "cropKind" to selectedCandidate.kind,
                "error" to throwable.message
            )
            RadarDebugStore.updateOcrSummary(
                durationMs = null,
                success = false,
                cropKind = selectedCandidate.kind.name,
                rawTextPreview = null,
                policyReason = policyReason
            )
            handleInitialMicroBurstFrameResult(
                observationId = observation.id,
                selectedCropKind = selectedCandidate.kind,
                fingerprint = null
            )
            onFingerprintReady(null)
            return
        }
        executePreparedOcrCandidate(
            observation = observation,
            selectedCandidate = selectedCandidate,
            croppedBitmap = croppedBitmap,
            acceptedForOcrFuture = acceptedForOcrFuture,
            candidateReason = candidateReason,
            policyReason = policyReason,
            releaseBitmapOnFinish = true,
            onFingerprintReady = onFingerprintReady
        )
    }

    private fun executePreparedOcrCandidate(
        observation: ScreenObservation,
        selectedCandidate: CropCandidate,
        croppedBitmap: Bitmap,
        acceptedForOcrFuture: Boolean,
        candidateReason: String,
        policyReason: String,
        releaseBitmapOnFinish: Boolean = false,
        onFingerprintReady: (OfferTextFingerprint?) -> Unit = {}
    ) {
        val ocrCandidate = OcrCandidate(
            observationId = observation.id,
            captureRequestId = observation.captureRequestId,
            triggerSource = observation.triggerSource,
            cropId = selectedCandidate.id,
            cropKind = selectedCandidate.kind,
            rect = Rect(selectedCandidate.rect),
            width = selectedCandidate.width,
            height = selectedCandidate.height,
            offerCycleKind = observation.offerCycleClassification?.kind,
            offerCycleShouldPreferForOcr = observation.offerCycleClassification?.shouldPreferForOcr,
            acceptedForOcrFuture = acceptedForOcrFuture,
            reason = candidateReason,
            analysisEpoch = observation.analysisEpoch,
            isManual = observation.isManual,
            manualReason = observation.manualReason
        )
        RadarLogger.i(
            "KM_V2_OCR",
            "KM_V2_OCR_STARTED",
            "observationId" to observation.id,
            "cropKind" to selectedCandidate.kind,
            "triggerSource" to observation.triggerSource
        )
        regionalOcrEngine.recognize(ocrCandidate, croppedBitmap) { ocrObservation ->
            handleOcrResult(
                candidate = ocrCandidate,
                croppedBitmap = croppedBitmap,
                observation = ocrObservation,
                policyReason = policyReason,
                releaseBitmapOnFinish = releaseBitmapOnFinish,
                onFingerprintReady = onFingerprintReady
            )
        }
    }

    private fun handleOcrResult(
        candidate: OcrCandidate,
        croppedBitmap: Bitmap,
        observation: OcrObservation,
        policyReason: String,
        releaseBitmapOnFinish: Boolean,
        onFingerprintReady: (OfferTextFingerprint?) -> Unit
    ) {
        try {
            if (isStale(observation.analysisEpoch)) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_STALE_OCR_RESULT_IGNORED",
                    "observationId" to observation.observationId,
                    "epoch" to observation.analysisEpoch,
                    "cropKind" to observation.cropKind
                )
                handleInitialMicroBurstFrameResult(
                    observationId = observation.observationId,
                    selectedCropKind = observation.cropKind,
                    fingerprint = null,
                    rawOcrText = observation.rawText
                )
                onFingerprintReady(null)
                return
            }
            if (observation.success) {
                RadarLogger.i(
                    "KM_V2_OCR",
                    "KM_V2_OCR_SUCCESS",
                    "observationId" to observation.observationId,
                    "durationMs" to observation.durationMs,
                    "lineCount" to observation.lineCount,
                    "blockCount" to observation.blockCount,
                    "rawTextLength" to observation.rawText.length
                )
            } else {
                RadarLogger.w(
                    "KM_V2_OCR",
                    "KM_V2_OCR_FAILED",
                    "observationId" to observation.observationId,
                    "durationMs" to observation.durationMs,
                    "error" to observation.errorMessage
                )
            }
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_OCR_RESULT",
                "observationId" to observation.observationId,
                "rawTextPreview" to observation.rawTextPreview()
            )
            if (!observation.isManual) {
                autoMissDiagnostics.recordAutoTrace(
                    AutoAttemptTrace(
                        timestampMs = observation.finishedAtMs,
                        triggerSource = observation.triggerSource,
                        stage = "ocr_result",
                        selectedCropKind = observation.cropKind
                    )
                )
            }
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_LATENCY_OCR",
                "observationId" to observation.observationId,
                "durationMs" to observation.durationMs
            )
            RadarDebugStore.updateOcrSummary(
                durationMs = observation.durationMs,
                success = observation.success,
                cropKind = observation.cropKind.name,
                rawTextPreview = observation.rawTextPreview(),
                policyReason = policyReason
            )
            if (observation.isManual) {
                updateManualTiming(observation.analysisEpoch) {
                    copy(ocrFinishedAtMs = observation.finishedAtMs)
                }
                RadarDebugStore.updateManualAnalysis(
                    epoch = observation.analysisEpoch,
                    status = "ocr_completed"
                )
            }
            ocrDebugWriter.write(candidate, croppedBitmap, observation, policyReason)
            val fingerprint = handleFingerprint(observation)
            handleInitialMicroBurstFrameResult(
                observationId = observation.observationId,
                selectedCropKind = observation.cropKind,
                fingerprint = fingerprint,
                rawOcrText = observation.rawText
            )
            onFingerprintReady(fingerprint)
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OCR",
                "KM_V2_OCR_FAILED",
                "observationId" to observation.observationId,
                "error" to throwable.message,
                "stacktrace" to throwable.stackTraceToString()
            )
            handleInitialMicroBurstFrameResult(
                observationId = observation.observationId,
                selectedCropKind = observation.cropKind,
                fingerprint = null,
                rawOcrText = observation.rawText
            )
            onFingerprintReady(null)
        } finally {
            if (releaseBitmapOnFinish && !croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
        }
    }

    private fun handleFingerprint(observation: OcrObservation): OfferTextFingerprint? {
        try {
            if (isStale(observation.analysisEpoch)) {
                RadarLogger.i(
                    "KM_V2_FINGERPRINT",
                    "KM_V2_STALE_FINGERPRINT_RESULT_IGNORED",
                    "observationId" to observation.observationId,
                    "epoch" to observation.analysisEpoch
                )
                return null
            }
            val startedAtMs = clock.nowMs()
            RadarLogger.i(
                "KM_V2_FINGERPRINT",
                "KM_V2_FINGERPRINT_STARTED",
                "observationId" to observation.observationId,
                "ocrObservationId" to observation.ocrObservationId,
                "cropKind" to observation.cropKind
            )
            val matchedOwnOverlayTerms = AutomaticCaptureFrameFilter.matchedSelfOverlayTerms(observation.rawText)
            if (AutomaticCaptureFrameFilter.isSelfOverlayContaminated(observation.rawText)) {
                return handleOwnOverlayCapture(observation, matchedOwnOverlayTerms)
            }
            val fingerprint = fingerprintExtractor.extract(observation)
            val finishedAtMs = clock.nowMs()
            val pricePreview = fingerprint.priceCandidates.firstOrNull()?.normalizedValue?.toString()
            RadarLogger.i(
                "KM_V2_FINGERPRINT",
                "KM_V2_FINGERPRINT_RESULT",
                "observationId" to fingerprint.observationId,
                "kind" to fingerprint.kind,
                "platform" to fingerprint.platformTextHint,
                "offerLikeScore" to fingerprint.offerLikeScore,
                "nonOfferScore" to fingerprint.nonOfferScore,
                "prices" to fingerprint.priceCandidates.map { it.normalizedValue },
                "valuePerKm" to fingerprint.valuePerKmCandidates.map { it.normalizedValue },
                "distances" to fingerprint.distanceCandidates.map { "${it.normalizedValue}${it.unit}" },
                "times" to fingerprint.timeCandidates.map { "${it.normalizedValue}${it.unit}" }
            )
            when (fingerprint.kind) {
                OfferTextFingerprintKind.OFFER_LIKE -> RadarLogger.i(
                    "KM_V2_FINGERPRINT",
                    "KM_V2_FINGERPRINT_OFFER_LIKE",
                    "observationId" to fingerprint.observationId,
                    "reason" to fingerprint.reason
                )
                OfferTextFingerprintKind.NON_OFFER -> RadarLogger.i(
                    "KM_V2_FINGERPRINT",
                    "KM_V2_FINGERPRINT_NON_OFFER",
                    "observationId" to fingerprint.observationId,
                    "reason" to fingerprint.reason,
                    "negativeSignals" to fingerprint.negativeSignals.joinToString(",") { it.key }
                )
                OfferTextFingerprintKind.UNKNOWN -> RadarLogger.i(
                    "KM_V2_FINGERPRINT",
                    "KM_V2_FINGERPRINT_UNKNOWN",
                    "observationId" to fingerprint.observationId,
                    "reason" to fingerprint.reason
                )
            }
            RadarLogger.i(
                "KM_V2_FINGERPRINT",
                "KM_V2_LATENCY_FINGERPRINT",
                "observationId" to fingerprint.observationId,
                "durationMs" to (finishedAtMs - startedAtMs)
            )
            RadarDebugStore.updateFingerprintSummary(
                kind = fingerprint.kind.name,
                platformHint = fingerprint.platformTextHint.name,
                offerLikeScore = fingerprint.offerLikeScore,
                nonOfferScore = fingerprint.nonOfferScore,
                pricePreview = pricePreview,
                reason = fingerprint.reason
            )
            if (!observation.isManual) {
                autoMissDiagnostics.recordAutoTrace(
                    AutoAttemptTrace(
                        timestampMs = finishedAtMs,
                        triggerSource = observation.triggerSource,
                        stage = "fingerprint_result",
                        reason = fingerprint.reason,
                        selectedCropKind = observation.cropKind,
                        fingerprintKind = fingerprint.kind.name,
                        platform = fingerprint.platformTextHint.name
                    )
                )
            }
            fingerprintDebugWriter.write(fingerprint, observation)
            if (observation.triggerSource == TriggerSource.NINETY_NINE_VISUAL_PROBE) {
                val nonOfferSignal = NinetyNineNonOfferScreenClassifier.fromRawText(observation.rawText)
                if (nonOfferSignal.isNonOfferMapScreen) {
                    RadarLogger.i(
                        "KM_V2_AUTO",
                        "KM_V2_99_NON_OFFER_MAP_SCREEN_DETECTED",
                        "hasSearchingText" to nonOfferSignal.hasSearchingText,
                        "hasMultiplierText" to nonOfferSignal.hasMultiplierText,
                        "hasOfferPrice" to nonOfferSignal.hasOfferPrice,
                        "hasValuePerKm" to nonOfferSignal.hasValuePerKm,
                        "hasStrong99OfferSignals" to nonOfferSignal.hasStrong99OfferSignals,
                        "reason" to nonOfferSignal.reason
                    )
                    RadarLogger.i(
                        "KM_V2_AUTO",
                        "KM_V2_99_VISUAL_PROBE_RESULT",
                        "fingerprintKind" to fingerprint.kind,
                        "platform" to fingerprint.platformTextHint,
                        "persistReason" to nonOfferSignal.reason,
                        "nonOfferReason" to nonOfferSignal.reason
                    )
                    logOfferPipelineFinalResult(
                        observation = observation,
                        fingerprint = fingerprint,
                        parserStatus = "skipped",
                        parserReason = nonOfferSignal.reason,
                        decisionStatus = "skipped",
                        decisionReason = nonOfferSignal.reason,
                        presentationStatus = "skipped",
                        presentationReason = nonOfferSignal.reason,
                        wasOverlayShown = false,
                        overlayKind = null,
                        persistenceResult = SeenOfferPersistenceResult(
                            attempted = false,
                            persisted = false,
                            reason = nonOfferSignal.reason ?: "ninety_nine_map_searching_state"
                        ),
                        finalReason = nonOfferSignal.reason ?: "ninety_nine_map_searching_state"
                    )
                    return fingerprint
                }
            }
            if (observation.triggerSource == TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG &&
                fingerprint.kind != OfferTextFingerprintKind.OFFER_LIKE
            ) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_PRE_OFFER_WATCHDOG_PROBE_RESULT",
                    "probeIndex" to null,
                    "fingerprintKind" to fingerprint.kind,
                    "platform" to fingerprint.platformTextHint,
                    "persistReason" to "watchdog_non_offer"
                )
                logOfferPipelineFinalResult(
                    observation = observation,
                    fingerprint = fingerprint,
                    parserStatus = "skipped",
                    parserReason = "watchdog_non_offer",
                    decisionStatus = "skipped",
                    decisionReason = "watchdog_non_offer",
                    presentationStatus = "skipped",
                    presentationReason = "watchdog_non_offer",
                    wasOverlayShown = false,
                    overlayKind = null,
                    persistenceResult = SeenOfferPersistenceResult(
                        attempted = false,
                        persisted = false,
                        reason = "watchdog_non_offer"
                    ),
                    finalReason = "watchdog_non_offer"
                )
                return fingerprint
            }
            if (observation.triggerSource == TriggerSource.NINETY_NINE_VISUAL_PROBE &&
                fingerprint.kind != OfferTextFingerprintKind.OFFER_LIKE
            ) {
                val sourceSnapshot = automaticCaptureSourcesByObservationId[observation.observationId]
                val retryAttempt = sourceSnapshot?.sourceObservation?.metadata?.notes
                    ?.get("ninetyNineVisualProbeRetryAttempt")
                    ?.toIntOrNull() ?: 0
                if (retryAttempt > 0) {
                    autoMissDiagnostics.recordAutoTrace(
                        AutoAttemptTrace(
                            timestampMs = clock.nowMs(),
                            triggerSource = observation.triggerSource,
                            stage = "retry_result_failed",
                            reason = "fingerprint_not_offer_like",
                            fingerprintKind = fingerprint.kind.name,
                            platform = fingerprint.platformTextHint.name
                        )
                    )
                }
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_99_VISUAL_PROBE_RESULT",
                    "fingerprintKind" to fingerprint.kind,
                    "platform" to fingerprint.platformTextHint,
                    "persistReason" to "fingerprint_not_offer_like",
                    "nonOfferReason" to "fingerprint_not_offer_like"
                )
                logOfferPipelineFinalResult(
                    observation = observation,
                    fingerprint = fingerprint,
                    parserStatus = "skipped",
                    parserReason = "fingerprint_not_offer_like",
                    decisionStatus = "skipped",
                    decisionReason = "fingerprint_not_offer_like",
                    presentationStatus = "skipped",
                    presentationReason = "fingerprint_not_offer_like",
                    wasOverlayShown = false,
                    overlayKind = null,
                    persistenceResult = SeenOfferPersistenceResult(
                        attempted = false,
                        persisted = false,
                        reason = "fingerprint_not_offer_like"
                    ),
                    finalReason = "fingerprint_not_offer_like"
                )
                return fingerprint
            }
              val burstContext = burstContextByObservationId[observation.observationId]
              if (burstContext != null) {
                  RadarLogger.i(
                      "KM_V2_AUTO",
                      "KM_V2_AUTO_BURST_FINGERPRINT_CAPTURED",
                      "sourceObservationId" to burstContext.sourceObservation.id,
                      "burstObservationId" to observation.observationId,
                      "triggerSource" to observation.triggerSource,
                      "cropKind" to observation.cropKind,
                      "rawOcrText" to observation.rawTextPreview(),
                      "fingerprintKind" to fingerprint.kind,
                      "fingerprintReason" to fingerprint.reason
                  )
              }
              val burstOutcome = if (!observation.isManual) {
                  maybeScheduleAutomaticBurst(observation, fingerprint)
              } else {
                  AutoBurstOutcome()
              }
              var persistenceResult = SeenOfferPersistenceResult(
                  attempted = false,
                  persisted = false,
                  reason = "not_attempted"
              )
              try {
                  val dedupeResult = dedupeProcessor.process(fingerprint, observation)
                RadarDebugStore.updateDedupeSummary(
                    result = dedupeResult.decision.name,
                    clusterId = dedupeResult.clusterId,
                    quality = dedupeResult.qualityScore,
                    reason = dedupeResult.reason,
                    isBest = dedupeResult.isBestForCluster,
                    activeClusterCount = dedupeResult.activeClusterCount,
                    bestOfferPreview = dedupeResult.bestOfferPreview,
                    bestOfferMainPrice = dedupeResult.bestOfferMainPrice?.toString(),
                    bestOfferPlatform = dedupeResult.bestOfferPlatform?.name
                )
                val parserResult = offerParser.process(fingerprint, observation, dedupeResult)
                  RadarDebugStore.updateParserSummary(
                      status = parserResult.status,
                    clusterId = parserResult.draft?.clusterId,
                    platform = parserResult.draft?.platform?.name,
                    product = parserResult.draft?.product,
                    price = parserResult.draft?.price?.value?.toString(),
                    valuePerKm = parserResult.draft?.valuePerKm?.value?.toString(),
                    pickupTime = parserResult.draft?.pickupTimeMinutes?.let { "${it.value} ${it.unit}" },
                    pickupDistance = parserResult.draft?.pickupDistanceKm?.let { "${it.value} ${it.unit}" },
                    tripTime = parserResult.draft?.tripTimeMinutes?.let { "${it.value} ${it.unit}" },
                    tripDistance = parserResult.draft?.tripDistanceKm?.let { "${it.value} ${it.unit}" },
                    confidence = parserResult.draft?.confidence?.overall?.toString(),
                    warnings = parserResult.draft?.warnings?.joinToString(", ") ?: parserResult.reason,
                    sanityStatus = parserResult.draft?.sanityStatus?.name,
                      sanityIssues = parserResult.draft?.sanityIssues?.joinToString(", ") { it.name },
                      shouldBlockEconomicDecision = parserResult.draft?.shouldBlockEconomicDecisionFuture
                  )
                  persistenceResult = seenOfferPersistenceProcessor.process(
                      fingerprint = fingerprint,
                      observation = observation,
                      parserResult = parserResult
                  )
                  val preDecisionOverlayDecision = SeenOfferOverlayPolicy.resolve(
                      persistenceResult = persistenceResult,
                      computedOverlayKind = null,
                      isManual = observation.isManual
                  )
                  if (preDecisionOverlayDecision.reShowExistingSeenOfferId != null) {
                      val existingSeenOfferId = preDecisionOverlayDecision.reShowExistingSeenOfferId
                      RadarLogger.i(
                          "KM_V2_SEEN",
                          "KM_V2_SEEN_OFFER_REPRESENTATION_REQUESTED",
                          "triggerSource" to observation.triggerSource,
                          "existingSeenOfferId" to existingSeenOfferId,
                          "persistReason" to persistenceResult.reason
                      )
                      val existingSeenOffer = SeenOfferRuntime.get(this).seenOfferRepository
                          .getSeenOfferById(existingSeenOfferId)
                      if (existingSeenOffer != null) {
                          val representation = seenOfferPresentationFactory.buildFromSeenOffer(
                              seenOffer = existingSeenOffer,
                              createdAtMs = clock.nowMs()
                          )
                          RadarLogger.i(
                              "KM_V2_SEEN",
                              "KM_V2_SEEN_OFFER_REPRESENTATION_BUILT",
                              "existingSeenOfferId" to existingSeenOfferId,
                              "price" to existingSeenOffer.price,
                              "valuePerKm" to RideEconomicsCalculator.calculateValuePerKm(
                                  price = existingSeenOffer.price,
                                  totalDistanceKm = existingSeenOffer.totalDistanceKm,
                                  pickupDistanceKm = existingSeenOffer.pickupDistanceKm,
                                  tripDistanceKm = existingSeenOffer.tripDistanceKm
                              ),
                              "totalDistanceKm" to RideEconomicsCalculator.resolveTotalDistanceKm(
                                  totalDistanceKm = existingSeenOffer.totalDistanceKm,
                                  pickupDistanceKm = existingSeenOffer.pickupDistanceKm,
                                  tripDistanceKm = existingSeenOffer.tripDistanceKm
                              ),
                              "overlayKind" to representation.kind
                          )
                          RadarDebugStore.updateDecisionPresentationSummary(
                              kind = representation.kind.name,
                              title = representation.title,
                              shortReason = representation.shortReason,
                              primaryMetric = representation.primaryMetric,
                              secondaryMetric = representation.secondaryMetric,
                              expiresAtMs = representation.expiresAtMs,
                              source = representation.source.name
                          )
                          val representationShown =
                              DecisionOverlayRuntime.get(this).showPresentation(representation)
                          RadarLogger.i(
                              "KM_V2_SEEN",
                              "KM_V2_SEEN_OFFER_REPRESENTATION_SHOWN",
                              "existingSeenOfferId" to existingSeenOfferId,
                              "overlayKind" to representation.kind
                          )
                          val representationPresentationStatus =
                              if (representationShown) "shown_from_saved_offer" else "skipped"
                          logOfferPipelineFinalResult(
                              observation = observation,
                              fingerprint = fingerprint,
                              parserStatus = parserResult.status,
                              parserReason = parserResult.reason,
                              decisionStatus = "reused_seen_offer",
                              decisionReason = persistenceResult.reason,
                              presentationStatus = representationPresentationStatus,
                              presentationReason = representation.kind.name,
                              wasOverlayShown = OverlayPresentationStatePolicy.wasOverlayShown(
                                  overlayKind = representation.kind.name,
                                  presentationStatus = representationPresentationStatus,
                                  overlayShownByController = representationShown
                              ),
                              overlayKind = representation.kind.name,
                              burstOutcome = burstOutcome,
                              persistenceResult = persistenceResult,
                              finalReason = persistenceResult.reason
                          )
                          return fingerprint
                      }
                      RadarLogger.w(
                          "KM_V2_SEEN",
                          "KM_V2_SEEN_OFFER_REPRESENTATION_FAILED",
                          "existingSeenOfferId" to existingSeenOfferId,
                          "reason" to "seen_offer_not_found"
                      )
                  }
                  val decisionResult = economicDecisionProcessor.process(
                      parserResult = parserResult,
                      source = if (observation.isManual) DecisionSource.MANUAL else DecisionSource.AUTOMATIC
                )
                RadarDebugStore.updateEconomicDecisionSummary(
                    decision = decisionResult.result?.decision?.name ?: decisionResult.reason.uppercase(),
                    score = decisionResult.result?.score,
                    confidence = decisionResult.result?.confidence?.toString(),
                    reasons = decisionResult.result?.reasons?.joinToString(", ") { it.name } ?: decisionResult.reason,
                    grossPerTripKm = decisionResult.result?.metrics?.grossPerTripKm?.toString(),
                    grossPerTotalKm = decisionResult.result?.metrics?.grossPerTotalKm?.toString(),
                    totalDistanceKm = decisionResult.result?.metrics?.totalDistanceKm?.toString(),
                    totalTimeMin = decisionResult.result?.metrics?.totalTimeMin?.toString(),
                    clusterId = decisionResult.result?.clusterId
                )
                val presentationResult = decisionPresentationProcessor.process(decisionResult)
                RadarDebugStore.updateDecisionPresentationSummary(
                    kind = presentationResult.presentation?.kind?.name ?: "DO_NOT_SHOW",
                    title = presentationResult.presentation?.title,
                    shortReason = presentationResult.presentation?.shortReason ?: presentationResult.reason,
                    primaryMetric = presentationResult.presentation?.primaryMetric,
                    secondaryMetric = presentationResult.presentation?.secondaryMetric,
                    expiresAtMs = presentationResult.presentation?.expiresAtMs,
                    source = presentationResult.presentation?.source?.name
                )
                val overlayDecision = SeenOfferOverlayPolicy.resolve(
                    persistenceResult = persistenceResult,
                    computedOverlayKind = presentationResult.presentation?.kind?.name,
                    isManual = observation.isManual
                )
                var overlayShownByController = false
                presentationResult.presentation?.takeIf { overlayDecision.shouldShowOverlay }?.let { presentation ->
                    overlayShownByController =
                        DecisionOverlayRuntime.get(this).showPresentation(presentation)
                }
                val resolvedPresentationStatus =
                    if (overlayDecision.shouldShowOverlay) presentationResult.status else "skipped"
                val resolvedWasOverlayShown = OverlayPresentationStatePolicy.wasOverlayShown(
                    overlayKind = overlayDecision.overlayKind,
                    presentationStatus = resolvedPresentationStatus,
                    overlayShownByController = overlayShownByController
                )
                if (!overlayDecision.shouldShowOverlay) {
                    RadarLogger.i(
                        "KM_V2_PRESENTATION",
                        "KM_V2_OVERLAY_SUPPRESSED_BY_PERSIST_REASON",
                        "observationId" to observation.observationId,
                        "persistReason" to persistenceResult.reason,
                        "isManual" to observation.isManual
                    )
                    RadarLogger.i(
                        "KM_V2_SEEN",
                        "KM_V2_SEEN_OFFER_DEDUPE_SKIPPED",
                        "observationId" to observation.observationId,
                        "existingSeenOfferId" to persistenceResult.seenOffer?.id,
                        "reason" to persistenceResult.reason
                    )
                    if (observation.isManual) {
                        RadarLogger.i(
                            "KM_V2_SEEN",
                            "KM_V2_SEEN_OFFER_ALREADY_REGISTERED_IN_VIEWS",
                            "observationId" to observation.observationId,
                            "seenOfferId" to persistenceResult.seenOffer?.id,
                            "message" to "Oferta ja registrada em Vistas",
                            "reason" to persistenceResult.reason
                        )
                    }
                }
                logOfferPipelineFinalResult(
                    observation = observation,
                    fingerprint = fingerprint,
                    parserStatus = parserResult.status,
                    parserReason = parserResult.reason,
                    decisionStatus = decisionResult.status,
                    decisionReason = decisionResult.result?.decision?.name ?: decisionResult.reason,
                    presentationStatus = resolvedPresentationStatus,
                    presentationReason = presentationResult.presentation?.kind?.name ?: presentationResult.reason,
                      wasOverlayShown = resolvedWasOverlayShown,
                      overlayKind = overlayDecision.overlayKind,
                      burstOutcome = burstOutcome,
                      persistenceResult = persistenceResult,
                      finalReason = overlayDecision.finalReasonOverride
                  )
              } catch (throwable: Throwable) {
                  if (!persistenceResult.attempted) {
                      persistenceResult = seenOfferPersistenceProcessor.process(
                          fingerprint = fingerprint,
                          observation = observation,
                          parserResult = null
                      )
                  }
                  RadarLogger.w(
                      "KM_V2_DEDUPE",
                      "KM_V2_DEDUPE_FAILED",
                    "observationId" to observation.observationId,
                    "error" to throwable.message,
                    "stacktrace" to throwable.stackTraceToString()
                )
                logOfferPipelineFinalResult(
                      observation = observation,
                      fingerprint = fingerprint,
                      finalReason = "dedupe_or_downstream_failed",
                      burstOutcome = burstOutcome,
                      persistenceResult = persistenceResult
                  )
              }
            if (observation.isManual) {
                updateManualTiming(observation.analysisEpoch) {
                    copy(fingerprintFinishedAtMs = finishedAtMs)
                }
                RadarLogger.i(
                    "KM_V2_FINGERPRINT",
                    "KM_V2_MANUAL_FINGERPRINT_RESULT",
                    "observationId" to fingerprint.observationId,
                    "epoch" to observation.analysisEpoch,
                    "kind" to fingerprint.kind,
                    "reason" to fingerprint.reason
                )
                RadarDebugStore.updateManualAnalysis(
                    epoch = observation.analysisEpoch,
                    status = "fingerprint_completed",
                    fingerprintKind = fingerprint.kind.name,
                    fingerprintPreview = observation.rawTextPreview()
                )
            }
            return fingerprint
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_FINGERPRINT",
                "KM_V2_FINGERPRINT_FAILED",
                "observationId" to observation.observationId,
                "error" to throwable.message,
                "stacktrace" to throwable.stackTraceToString()
            )
            return null
        }
    }

    private fun finalizeManualAnalysis(
        observation: ScreenObservation,
        fingerprint: OfferTextFingerprint?,
        status: String
    ) {
        synchronized(finalizedManualEpochs) {
            if (!finalizedManualEpochs.add(observation.analysisEpoch)) {
                RadarLogger.i(
                    "KM_V2_MANUAL",
                    "KM_V2_MANUAL_DUPLICATE_COMPLETION_IGNORED",
                    "observationId" to observation.id,
                    "epoch" to observation.analysisEpoch,
                    "status" to status
                )
                RadarLogger.i(
                    "KM_V2_MANUAL",
                    "KM_V2_LATENCY_MANUAL_ANALYSIS_DUPLICATE_SKIPPED",
                    "epoch" to observation.analysisEpoch,
                    "status" to status
                )
                return
            }
        }
        val nowMs = clock.nowMs()
        updateManualTiming(observation.analysisEpoch) {
            copy(fingerprintFinishedAtMs = fingerprintFinishedAtMs ?: nowMs)
        }
        val timing = manualTimings.remove(observation.analysisEpoch) ?: ManualAnalysisTiming(clickedAtMs = observation.requestCreatedAtMs)
        val finishedTiming = timing.copy(
            fingerprintFinishedAtMs = timing.fingerprintFinishedAtMs ?: nowMs
        )
        val totalMs = ((finishedTiming.fingerprintFinishedAtMs ?: nowMs) - finishedTiming.clickedAtMs).coerceAtLeast(0L)
        val screenshotMs = observation.screenshotFinishedAtMs - observation.screenshotStartedAtMs
        val visionMs = finishedTiming.visionFinishedAtMs?.let { it - (finishedTiming.observationAtMs ?: observation.observationCreatedAtMs) }
        val ocrMs = finishedTiming.ocrFinishedAtMs?.let { it - (finishedTiming.visionFinishedAtMs ?: observation.observationCreatedAtMs) }
        val fingerprintMs = finishedTiming.fingerprintFinishedAtMs?.let { it - (finishedTiming.ocrFinishedAtMs ?: finishedTiming.visionFinishedAtMs ?: observation.observationCreatedAtMs) }
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_MANUAL_ANALYSIS_RESULT",
            "observationId" to observation.id,
            "epoch" to observation.analysisEpoch,
            "status" to status,
            "fingerprintKind" to fingerprint?.kind,
            "reason" to fingerprint?.reason
        )
        RadarLogger.i(
            "KM_V2_ORCHESTRATOR",
            "KM_V2_LATENCY_MANUAL_ANALYSIS",
            "epoch" to observation.analysisEpoch,
            "totalMs" to totalMs,
            "screenshotMs" to screenshotMs,
            "visionMs" to visionMs,
            "ocrMs" to ocrMs,
            "fingerprintMs" to fingerprintMs
        )
        RadarDebugStore.updateManualAnalysis(
            epoch = observation.analysisEpoch,
            status = status,
            durationMs = totalMs,
            fingerprintKind = fingerprint?.kind?.name,
            fingerprintPreview = fingerprint?.normalizedPreview
        )
        ManualAnalysisState.finish(nowMs)
        RadarDebugStore.updateManualControlState(
            running = false,
            cooldownRemainingMs = ManualAnalysisState.snapshot(nowMs).cooldownRemainingMs
        )
    }

    private fun handleOwnOverlayCapture(
        observation: OcrObservation,
        matchedTerms: List<String>
    ): OfferTextFingerprint {
        val abortedFingerprint = buildOwnOverlayCaptureFingerprint(observation)
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_OWN_OVERLAY_CAPTURE_DETECTED",
            "triggerSource" to observation.triggerSource,
            "reason" to "kmone_overlay_text_detected",
            "matchedTerms" to matchedTerms.joinToString(",")
        )
        RadarDebugStore.updateFingerprintSummary(
            kind = abortedFingerprint.kind.name,
            platformHint = abortedFingerprint.platformTextHint.name,
            offerLikeScore = 0,
            nonOfferScore = 0,
            pricePreview = null,
            reason = abortedFingerprint.reason
        )
        if (observation.isManual) {
            val existingSeenOffer = findRecentSeenOfferForOverlayReuse()
            if (existingSeenOffer != null) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_OWN_OVERLAY_CAPTURE_REUSED_SEEN_OFFER",
                    "existingSeenOfferId" to existingSeenOffer.id,
                    "triggerSource" to observation.triggerSource
                )
                val representation = seenOfferPresentationFactory.buildFromSeenOffer(
                    seenOffer = existingSeenOffer,
                    createdAtMs = clock.nowMs()
                )
                val representationShown = DecisionOverlayRuntime.get(this).showPresentation(representation)
                val presentationStatus = if (representationShown) "shown_from_saved_offer" else "skipped"
                logOfferPipelineFinalResult(
                    observation = observation,
                    fingerprint = abortedFingerprint,
                    parserStatus = "skipped",
                    parserReason = "own_overlay_capture",
                    decisionStatus = "reused_seen_offer",
                    decisionReason = "own_overlay_capture",
                    presentationStatus = presentationStatus,
                    presentationReason = representation.kind.name,
                    wasOverlayShown = OverlayPresentationStatePolicy.wasOverlayShown(
                        overlayKind = representation.kind.name,
                        presentationStatus = presentationStatus,
                        overlayShownByController = representationShown
                    ),
                    overlayKind = representation.kind.name,
                    burstOutcome = AutoBurstOutcome(),
                    persistenceResult = SeenOfferPersistenceResult(
                        attempted = false,
                        persisted = false,
                        seenOffer = existingSeenOffer,
                        reason = "weaker_duplicate_offer_recently_saved"
                    ),
                    finalReason = "weaker_duplicate_offer_recently_saved"
                )
                return abortedFingerprint
            }
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_OWN_OVERLAY_CAPTURE_IGNORED",
                "triggerSource" to observation.triggerSource,
                "persistReason" to "own_overlay_capture"
            )
        } else {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_SELF_OVERLAY_PIPELINE_ABORTED",
                "observationId" to observation.observationId,
                "triggerSource" to observation.triggerSource,
                "cropKind" to observation.cropKind,
                "rawTextPreview" to observation.rawTextPreview(),
                "reason" to "own_overlay_capture"
            )
        }
        logOfferPipelineFinalResult(
            observation = observation,
            fingerprint = abortedFingerprint,
            parserStatus = "skipped",
            parserReason = "own_overlay_capture",
            decisionStatus = "skipped",
            decisionReason = "own_overlay_capture",
            presentationStatus = "skipped",
            presentationReason = "own_overlay_capture",
            wasOverlayShown = false,
            overlayKind = null,
            burstOutcome = AutoBurstOutcome(),
            persistenceResult = SeenOfferPersistenceResult(
                attempted = false,
                persisted = false,
                reason = "own_overlay_capture"
            ),
            finalReason = "own_overlay_capture"
        )
        return abortedFingerprint
    }

    private fun buildOwnOverlayCaptureFingerprint(observation: OcrObservation): OfferTextFingerprint {
        return OfferTextFingerprint(
            fingerprintId = UUID.randomUUID().toString(),
            ocrObservationId = observation.ocrObservationId,
            observationId = observation.observationId,
            captureRequestId = observation.captureRequestId,
            triggerSource = observation.triggerSource,
            cropKind = observation.cropKind,
            platformTextHint = PlatformTextHint.UNKNOWN,
            kind = OfferTextFingerprintKind.UNKNOWN,
            offerLikeScore = 0,
            nonOfferScore = 0,
            positiveSignals = emptyList(),
            negativeSignals = emptyList(),
            priceCandidates = emptyList(),
            valuePerKmCandidates = emptyList(),
            distanceCandidates = emptyList(),
            timeCandidates = emptyList(),
            rawTextHash = "",
            routeTextHash = null,
            normalizedPreview = observation.rawTextPreview(),
            reason = "own_overlay_capture",
            createdAtMs = clock.nowMs()
        )
    }

    private fun findRecentSeenOfferForOverlayReuse(maxAgeMs: Long = 30_000L): SeenOffer? {
        val nowMs = clock.nowMs()
        return SeenOfferRuntime.get(this).seenOfferRepository
            .listSeenOffers(limit = 10)
            .firstOrNull { nowMs - it.updatedAtMs <= maxAgeMs }
    }

    private fun releaseManualPreparedBitmaps(preparedBitmaps: Map<String, PreparedManualCrop<Bitmap>>) {
        manualBitmapPreparer.releaseAll(preparedBitmaps)
        RadarLogger.i("KM_V2_OCR", "KM_V2_MANUAL_BITMAP_RELEASED", "count" to preparedBitmaps.size)
    }

    private fun isRecentKmOneOverlayVisibleForNinetyNine(observation: ScreenObservation): Boolean {
        if (
            observation.triggerSource != TriggerSource.NINETY_NINE_TREE_STRUCTURE &&
            observation.triggerSource != TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC &&
            observation.triggerSource != TriggerSource.NINETY_NINE_VISUAL_PROBE
        ) {
            return false
        }
        if (lastKmOneOverlayShownAtMs == Long.MIN_VALUE) {
            return false
        }
        return clock.nowMs() - lastKmOneOverlayShownAtMs < 3_000L
    }

    private fun updateManualTiming(epoch: Long, transform: ManualAnalysisTiming.() -> ManualAnalysisTiming) {
        val current = manualTimings[epoch] ?: return
        manualTimings[epoch] = transform(current)
    }

    private fun isStale(epoch: Long): Boolean {
        return epoch > 0L && !AnalysisEpochController.isCurrent(epoch)
    }

    private fun inferPlatformHint(
        dominantPackage: String?,
        floatingPackage: String?,
        nodeTreePackage: String?
    ): PlatformHint? {
        return when {
            dominantPackage == "com.ubercab.driver" || floatingPackage == "com.ubercab.driver" || nodeTreePackage == "com.ubercab.driver" ->
                PlatformHint.UBER
            dominantPackage == "com.app99.driver" || floatingPackage == "com.app99.driver" || nodeTreePackage == "com.app99.driver" ->
                PlatformHint.NINETY_NINE
            else -> null
        }
    }

    private fun registerAutomaticCaptureSource(observation: ScreenObservation) {
        if (observation.isManual) return
        val burstContext = burstContextByObservationId[observation.id]
        val snapshot = AutomaticCaptureSourceSnapshot(
            observationId = observation.id,
            triggerSource = observation.triggerSource,
            selectedCropKind = null,
            attempt = burstContext?.attempt ?: 0,
            createdAtMs = observation.createdAtMs,
            sourceObservation = burstContext?.sourceObservation ?: observation,
            originalTriggerSource = burstContext?.originalTriggerSource ?: observation.triggerSource
        )
        automaticCaptureSourcesByObservationId[observation.id] = snapshot
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_SOURCE_REGISTERED",
            "observationId" to observation.id,
            "triggerSource" to observation.triggerSource,
            "selectedCropKind" to snapshot.selectedCropKind,
            "attempt" to snapshot.attempt,
            "createdAtMs" to snapshot.createdAtMs
        )
    }

    private fun updateAutomaticCaptureSource(
        observationId: String,
        selectedCropKind: CropKind? = null,
        obstructionResult: FloatingObstructionResult? = null,
        obstructionAction: FloatingObstructionAction? = null,
        alternativeCropApplied: Boolean? = null,
        originalCropKind: CropKind? = null
    ) {
        val current = automaticCaptureSourcesByObservationId[observationId] ?: return
        automaticCaptureSourcesByObservationId[observationId] = current.copy(
            selectedCropKind = selectedCropKind ?: current.selectedCropKind,
            obstructionResult = obstructionResult ?: current.obstructionResult,
            obstructionAction = obstructionAction ?: current.obstructionAction,
            alternativeCropApplied = alternativeCropApplied ?: current.alternativeCropApplied,
            originalCropKind = originalCropKind ?: current.originalCropKind
        )
    }

    private fun consumeAutomaticCaptureSource(observationId: String): AutomaticCaptureSourceSnapshot? {
        return automaticCaptureSourcesByObservationId.remove(observationId)
    }

    private fun registerInitialMicroBurstFrameContext(observation: ScreenObservation) {
        val sourceObservationId = observation.metadata.notes["autoInitialMicroBurstSourceObservationId"] ?: return
        val frameIndex = observation.metadata.notes["autoInitialMicroBurstFrameIndex"]?.toIntOrNull() ?: return
        val delayMs = observation.metadata.notes["autoInitialMicroBurstDelayMs"]?.toLongOrNull() ?: 0L
        initialMicroBurstFrameContextByObservationId[observation.id] = AutoInitialMicroBurstFrameContext(
            sourceObservationId = sourceObservationId,
            frameIndex = frameIndex,
            delayMs = delayMs
        )
    }

    private fun startInitialMicroBurstIfNeeded(observation: ScreenObservation) {
        if (observation.isManual) return
        if (
            observation.triggerSource != TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC &&
            observation.triggerSource != TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC
        ) return
        if (observation.metadata.notes.containsKey("autoInitialMicroBurstSourceObservationId")) return
        if (burstContextByObservationId.containsKey(observation.id)) return
        if (initialMicroBurstStateBySourceId.containsKey(observation.id)) return
        val frameDelays = initialMicroBurstFrameDelays(observation.triggerSource)
        initialMicroBurstStateBySourceId[observation.id] = AutoInitialMicroBurstState(
            sourceObservation = observation,
            frameDelaysMs = frameDelays,
            startedAtMs = clock.nowMs()
        )
        initialMicroBurstFrameContextByObservationId[observation.id] = AutoInitialMicroBurstFrameContext(
            sourceObservationId = observation.id,
            frameIndex = 0,
            delayMs = 0L
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_INITIAL_MICRO_BURST_STARTED",
            "sourceObservationId" to observation.id,
            "triggerSource" to observation.triggerSource,
            "frameDelaysMs" to frameDelays.joinToString(",")
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_STARTED",
            "sourceObservationId" to observation.id,
            "frameIndex" to 0,
            "delayMs" to 0L
        )
        scheduleNextInitialMicroBurstFrame(observation.id)
    }

    private fun scheduleNextInitialMicroBurstFrame(sourceObservationId: String) {
        val state = initialMicroBurstStateBySourceId[sourceObservationId] ?: return
        if (state.offerLikeFound || state.finished) return
        val nextFrameIndex = (1 until state.frameDelaysMs.size).firstOrNull { frameIndex ->
            frameIndex !in state.completedFrames &&
                automaticCaptureMicroBurstScheduler.currentState(sourceObservationId, frameIndex) !in setOf(
                    AutomaticCaptureMicroBurstScheduler.FrameState.PENDING,
                    AutomaticCaptureMicroBurstScheduler.FrameState.RUNNING,
                    AutomaticCaptureMicroBurstScheduler.FrameState.RETRYING
                )
        } ?: return
        val targetDelayMs = state.frameDelaysMs[nextFrameIndex]
        val delayMs = (maxOf(state.startedAtMs + targetDelayMs, state.nextAllowedAtMs) - clock.nowMs()).coerceAtLeast(0L)
        val scheduled = automaticCaptureMicroBurstScheduler.schedule(
            sourceObservationId = sourceObservationId,
            frameIndex = nextFrameIndex,
            delayMs = delayMs
        ) {
            val refreshedState = initialMicroBurstStateBySourceId[sourceObservationId] ?: return@schedule
            if (refreshedState.offerLikeFound || refreshedState.finished) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_CANCELLED_BURST_ALREADY_FINISHED",
                    "sourceObservationId" to sourceObservationId,
                    "frameIndex" to nextFrameIndex
                )
                automaticCaptureMicroBurstScheduler.markState(
                    sourceObservationId,
                    nextFrameIndex,
                    AutomaticCaptureMicroBurstScheduler.FrameState.CANCELLED
                )
                return@schedule
            }
            if (!automaticCaptureMicroBurstScheduler.markRunning(sourceObservationId, nextFrameIndex)) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_SKIPPED_ALREADY_RUNNING",
                    "sourceObservationId" to sourceObservationId,
                    "frameIndex" to nextFrameIndex,
                    "state" to automaticCaptureMicroBurstScheduler.currentState(sourceObservationId, nextFrameIndex)
                )
                return@schedule
            }
            executeInitialMicroBurstFrame(
                sourceObservation = refreshedState.sourceObservation,
                frameIndex = nextFrameIndex,
                delayMs = delayMs
            )
        }
        if (!scheduled) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_SKIPPED_ALREADY_RUNNING",
                "sourceObservationId" to sourceObservationId,
                "frameIndex" to nextFrameIndex,
                "state" to automaticCaptureMicroBurstScheduler.currentState(sourceObservationId, nextFrameIndex)
            )
        }
    }

    private fun scheduleInitialMicroBurstRetry(
        sourceObservation: ScreenObservation,
        frameIndex: Int,
        reason: String
    ): Boolean {
        val state = initialMicroBurstStateBySourceId[sourceObservation.id] ?: return false
        val currentRetries = state.retryCountByFrame[frameIndex] ?: 0
        if (currentRetries >= 1) return false
        val retryDelayMs = AutomaticCaptureMicroBurstTiming.busyRetryDelayMs()
        if (clock.nowMs() + retryDelayMs > state.startedAtMs + state.maxDurationMs) {
            return false
        }
        state.retryCountByFrame[frameIndex] = currentRetries + 1
        state.nextAllowedAtMs = maxOf(state.nextAllowedAtMs, clock.nowMs() + retryDelayMs)
        automaticCaptureMicroBurstScheduler.markState(
            sourceObservation.id,
            frameIndex,
            AutomaticCaptureMicroBurstScheduler.FrameState.FAILED
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_INITIAL_MICRO_BURST_RETRY_SCHEDULED",
            "sourceObservationId" to sourceObservation.id,
            "frameIndex" to frameIndex,
            "delayMs" to retryDelayMs,
            "reason" to reason
        )
        val scheduled = automaticCaptureMicroBurstScheduler.schedule(
            sourceObservationId = sourceObservation.id,
            frameIndex = frameIndex,
            delayMs = retryDelayMs,
            state = AutomaticCaptureMicroBurstScheduler.FrameState.RETRYING
        ) {
            val refreshedState = initialMicroBurstStateBySourceId[sourceObservation.id] ?: return@schedule
            if (refreshedState.offerLikeFound || refreshedState.finished) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_CANCELLED_BURST_ALREADY_FINISHED",
                    "sourceObservationId" to sourceObservation.id,
                    "frameIndex" to frameIndex
                )
                automaticCaptureMicroBurstScheduler.markState(
                    sourceObservation.id,
                    frameIndex,
                    AutomaticCaptureMicroBurstScheduler.FrameState.CANCELLED
                )
                return@schedule
            }
            if (!automaticCaptureMicroBurstScheduler.markRunning(sourceObservation.id, frameIndex)) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_SKIPPED_ALREADY_RUNNING",
                    "sourceObservationId" to sourceObservation.id,
                    "frameIndex" to frameIndex,
                    "state" to automaticCaptureMicroBurstScheduler.currentState(sourceObservation.id, frameIndex)
                )
                return@schedule
            }
            executeInitialMicroBurstFrame(
                sourceObservation = refreshedState.sourceObservation,
                frameIndex = frameIndex,
                delayMs = retryDelayMs
            )
        }
        if (!scheduled) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_SKIPPED_ALREADY_RUNNING",
                "sourceObservationId" to sourceObservation.id,
                "frameIndex" to frameIndex,
                "state" to automaticCaptureMicroBurstScheduler.currentState(sourceObservation.id, frameIndex)
            )
        }
        return scheduled
    }

    private fun executeInitialMicroBurstFrame(
        sourceObservation: ScreenObservation,
        frameIndex: Int,
        delayMs: Long
    ) {
        val state = initialMicroBurstStateBySourceId[sourceObservation.id] ?: return
        if (state.offerLikeFound || state.finished) return
        if (serviceDestroyed.get()) {
            completeInitialMicroBurstFailure(
                sourceObservationId = sourceObservation.id,
                frameIndex = frameIndex,
                reason = "service_destroyed"
            )
            return
        }
        if (ManualAnalysisState.isRunning()) {
            completeInitialMicroBurstFailure(
                sourceObservationId = sourceObservation.id,
                frameIndex = frameIndex,
                reason = "manual_epoch_changed"
            )
            return
        }
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_STARTED",
            "sourceObservationId" to sourceObservation.id,
            "frameIndex" to frameIndex,
            "delayMs" to delayMs
        )
        val request = buildInitialMicroBurstRequest(sourceObservation, frameIndex, delayMs)
        screenshotCapturer.capture(
            request = request,
            onSuccess = { burstRequest, result ->
                val baseObservation = screenObservationFactory.create(burstRequest, result)
                val enrichedObservation = baseObservation.copy(
                    metadata = baseObservation.metadata.copy(
                        notes = baseObservation.metadata.notes + mapOf(
                            "autoInitialMicroBurstSourceObservationId" to sourceObservation.id,
                            "autoInitialMicroBurstFrameIndex" to frameIndex.toString(),
                            "autoInitialMicroBurstDelayMs" to delayMs.toString(),
                            "autoBurstPreferredCropOrder" to microBurstPreferredCropOrder().joinToString(","),
                            "autoBurstOriginalTriggerSource" to sourceObservation.triggerSource.name
                        )
                    )
                )
                scheduleNextInitialMicroBurstFrame(sourceObservation.id)
                onObservationCreated(enrichedObservation, result)
            },
            onFailure = { _, error, errorCode, _, _, _ ->
                val busyReason = if (errorCode == 3 || error.contains("code_3")) {
                    resolveInitialMicroBurstBusyReason()
                } else {
                    null
                }
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_SCREENSHOT_FAILED",
                    "sourceObservationId" to sourceObservation.id,
                    "frameIndex" to frameIndex,
                    "delayMs" to delayMs,
                    "error" to error,
                    "errorCode" to errorCode,
                    "activeReason" to busyReason,
                    "autoBurstCaptureInFlight" to autoBurstCaptureInFlight.get()
                )
                if ((errorCode == 3 || error.contains("code_3")) &&
                    scheduleInitialMicroBurstRetry(
                        sourceObservation = sourceObservation,
                        frameIndex = frameIndex,
                        reason = busyReason ?: "take_screenshot_busy_code_3"
                    )
                ) {
                    return@capture
                }
                automaticCaptureMicroBurstScheduler.markState(
                    sourceObservation.id,
                    frameIndex,
                    AutomaticCaptureMicroBurstScheduler.FrameState.FAILED
                )
                completeInitialMicroBurstFailure(
                    sourceObservationId = sourceObservation.id,
                    frameIndex = frameIndex,
                    reason = "screenshot_failed:$error"
                )
                scheduleNextInitialMicroBurstFrame(sourceObservation.id)
            },
            onFinished = { _, _ -> }
        )
    }

    private fun resolveInitialMicroBurstBusyReason(): String {
        return when {
            autoBurstCaptureInFlight.get() -> "auto_burst_capture_busy"
            orchestrator.debugBusyReason() != null -> orchestrator.debugBusyReason().orEmpty()
            else -> "take_screenshot_busy_code_3"
        }
    }

    private fun buildInitialMicroBurstRequest(
        sourceObservation: ScreenObservation,
        frameIndex: Int,
        delayMs: Long
    ): com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest {
        val nowMs = clock.nowMs()
        return com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = sourceObservation.createdAtMs,
            signalEmittedAtMs = sourceObservation.createdAtMs,
            createdAtMs = nowMs,
            approvedAtMs = nowMs,
            triggerSource = TriggerSource.UBER_AUTO_BURST_RECOVERY,
            platformHint = sourceObservation.visualPlatformHint ?: inferPlatformHint(
                dominantPackage = sourceObservation.dominantPackage,
                floatingPackage = sourceObservation.floatingPackage,
                nodeTreePackage = null
            ),
            priority = com.lucastrevvos.kmonemotor.radar.orchestrator.CapturePriority.HIGH,
            dominantPackage = sourceObservation.dominantPackage,
            floatingPackage = sourceObservation.floatingPackage,
            floatingBounds = sourceObservation.floatingBounds,
            floatingKind = sourceObservation.floatingKind,
            reason = "auto_initial_micro_burst_frame_$frameIndex" + "_${delayMs}ms",
            offerCycleClassification = sourceObservation.offerCycleClassification
        )
    }

    private fun microBurstPreferredCropOrder(): List<CropKind> {
        return listOf(
            CropKind.LOWER_HALF,
            CropKind.CENTER_CARD_AREA,
            CropKind.LOWER_THIRD,
            CropKind.FLOATING_BOUNDS_EXPANDED
        )
    }

    private fun handleInitialMicroBurstFrameResult(
        observationId: String,
        selectedCropKind: CropKind?,
        fingerprint: OfferTextFingerprint?,
        rawOcrText: String? = null
    ) {
        val frameContext = initialMicroBurstFrameContextByObservationId.remove(observationId) ?: return
        val state = initialMicroBurstStateBySourceId[frameContext.sourceObservationId] ?: return
        val rawText = rawOcrText?.take(160)
        val selfOverlayContaminated = rawOcrText?.let(AutomaticCaptureFrameFilter::isSelfOverlayContaminated) == true
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_RESULT",
            "sourceObservationId" to frameContext.sourceObservationId,
            "frameIndex" to frameContext.frameIndex,
            "observationId" to observationId,
            "selectedCropKind" to selectedCropKind,
            "fingerprintKind" to (fingerprint?.kind ?: "NONE"),
            "offerLikeScore" to fingerprint?.offerLikeScore,
            "nonOfferScore" to fingerprint?.nonOfferScore,
            "priceCount" to fingerprint?.priceCandidates?.size,
            "distanceCount" to fingerprint?.distanceCandidates?.size,
            "timeCount" to fingerprint?.timeCandidates?.size,
            "rawOcrText" to rawText
        )
        if (selfOverlayContaminated) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_SELF_OVERLAY_CONTAMINATION_DETECTED",
                "sourceObservationId" to frameContext.sourceObservationId,
                "observationId" to observationId,
                "frameIndex" to frameContext.frameIndex,
                "rawOcrText" to rawText
            )
        }
        state.completedFrames += frameContext.frameIndex
        automaticCaptureMicroBurstScheduler.markState(
            frameContext.sourceObservationId,
            frameContext.frameIndex,
            AutomaticCaptureMicroBurstScheduler.FrameState.DONE
        )
        if (selfOverlayContaminated) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_REJECTED_SELF_OVERLAY",
                "sourceObservationId" to frameContext.sourceObservationId,
                "observationId" to observationId,
                "frameIndex" to frameContext.frameIndex,
                "fingerprintKind" to fingerprint?.kind
            )
            if (frameContext.frameIndex == 0) {
                val cooldownStartedAtMs = clock.nowMs()
                val cooldownUntilMs = AutomaticCaptureMicroBurstTiming.applySelfOverlayCooldown(cooldownStartedAtMs)
                state.nextAllowedAtMs = maxOf(state.nextAllowedAtMs, cooldownUntilMs)
                val cancelled = automaticCaptureMicroBurstScheduler.cancelPending(frameContext.sourceObservationId)
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_COOLDOWN_APPLIED",
                    "sourceObservationId" to frameContext.sourceObservationId,
                    "reason" to "self_overlay_aborted",
                    "cooldownMs" to (cooldownUntilMs - cooldownStartedAtMs),
                    "cancelledPendingFrames" to cancelled
                )
                scheduleNextInitialMicroBurstFrame(frameContext.sourceObservationId)
            }
        }
        if (
            !state.offerLikeFound &&
            AutomaticCaptureFrameFilter.canWinMicroBurst(
                fingerprintKind = fingerprint?.kind,
                rawText = rawOcrText.orEmpty()
            )
        ) {
            state.offerLikeFound = true
            state.bestObservationId = observationId
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_INITIAL_MICRO_BURST_BEST_SELECTED",
                "sourceObservationId" to frameContext.sourceObservationId,
                "bestObservationId" to observationId,
                "frameIndex" to frameContext.frameIndex,
                "reason" to "offer_like_found"
            )
            val cancelled = automaticCaptureMicroBurstScheduler.cancel(frameContext.sourceObservationId)
            if (cancelled > 0) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_INITIAL_MICRO_BURST_CANCELLED_REMAINING",
                    "sourceObservationId" to frameContext.sourceObservationId,
                    "reason" to "offer_like_found"
                )
            }
            state.finished = true
            initialMicroBurstStateBySourceId.remove(frameContext.sourceObservationId)
            return
        }
        if (state.completedFrames.size >= state.frameDelaysMs.size && !state.offerLikeFound) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_INITIAL_MICRO_BURST_FINAL_FAILURE",
                "sourceObservationId" to frameContext.sourceObservationId,
                "reason" to (fingerprint?.reason ?: "all_frames_failed")
            )
            state.finished = true
            automaticCaptureMicroBurstScheduler.cancelPending(frameContext.sourceObservationId)
            initialMicroBurstStateBySourceId.remove(frameContext.sourceObservationId)
        }
    }

    private fun completeInitialMicroBurstFailure(
        sourceObservationId: String,
        frameIndex: Int,
        reason: String
    ) {
        val state = initialMicroBurstStateBySourceId[sourceObservationId] ?: return
        state.completedFrames += frameIndex
        automaticCaptureMicroBurstScheduler.markState(
            sourceObservationId,
            frameIndex,
            AutomaticCaptureMicroBurstScheduler.FrameState.FAILED
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_INITIAL_MICRO_BURST_FRAME_RESULT",
            "sourceObservationId" to sourceObservationId,
            "frameIndex" to frameIndex,
            "observationId" to null,
            "selectedCropKind" to null,
            "fingerprintKind" to "NONE",
            "offerLikeScore" to null,
            "nonOfferScore" to null,
            "priceCount" to null,
            "distanceCount" to null,
            "timeCount" to null,
            "rawOcrText" to null,
            "reason" to reason
        )
        if (state.completedFrames.size >= state.frameDelaysMs.size && !state.offerLikeFound) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_INITIAL_MICRO_BURST_FINAL_FAILURE",
                "sourceObservationId" to sourceObservationId,
                "reason" to reason
            )
            state.finished = true
            automaticCaptureMicroBurstScheduler.cancelPending(sourceObservationId)
            initialMicroBurstStateBySourceId.remove(sourceObservationId)
        }
    }

    private fun initialMicroBurstFrameDelays(triggerSource: TriggerSource): List<Long> {
        return AutomaticCaptureMicroBurstTiming.frameDelaysMs(triggerSource)
    }

    private fun logOfferPipelineFinalResult(
        observation: OcrObservation,
        fingerprint: OfferTextFingerprint,
        parserStatus: String? = null,
        parserReason: String? = null,
        decisionStatus: String? = null,
        decisionReason: String? = null,
        presentationStatus: String? = null,
        presentationReason: String? = null,
        wasOverlayShown: Boolean = false,
        overlayKind: String? = null,
        burstOutcome: AutoBurstOutcome = AutoBurstOutcome(),
        persistenceResult: SeenOfferPersistenceResult = SeenOfferPersistenceResult(
            attempted = false,
            persisted = false,
            reason = "not_attempted"
        ),
        finalReason: String? = null
    ) {
        val sourceSnapshot = consumeAutomaticCaptureSource(observation.observationId)
        val sourceObservation = sourceSnapshot?.sourceObservation
        val sourceGroupId = sourceObservation?.metadata?.notes?.get("ninetyNineVisualProbeSourceGroupId")
            ?: observation.captureRequestId
        val retryAttempt = sourceObservation?.metadata?.notes?.get("ninetyNineVisualProbeRetryAttempt")
            ?.toIntOrNull() ?: 0
        val obstructionResult = sourceSnapshot?.obstructionResult
        val resolvedFinalReason = finalReason
            ?: burstOutcome.reason?.takeIf { burstOutcome.scheduled }
            ?: presentationReason
            ?: decisionReason
            ?: parserReason
            ?: fingerprint.reason
        orchestrator.onAutoCapturePipelineFinished(
            AutoCapturePipelineResult(
                triggerSource = observation.triggerSource,
                fingerprintKind = fingerprint.kind.name,
                wasPersisted = persistenceResult.persisted,
                finalReason = resolvedFinalReason,
                timestampMs = clock.nowMs(),
                sourceGroupId = sourceGroupId,
                retryAttempt = retryAttempt,
                dominantPackage = sourceObservation?.dominantPackage,
                floatingPackage = sourceObservation?.floatingPackage,
                floatingBounds = sourceObservation?.floatingBounds,
                floatingKind = sourceObservation?.floatingKind,
                recentKmOneOverlayVisible = sourceObservation?.let { isRecentKmOneOverlayVisibleForNinetyNine(it) } ?: false
            )
        )
        if (!observation.isManual) {
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = clock.nowMs(),
                    triggerSource = observation.triggerSource,
                    stage = "pipeline_final",
                    reason = resolvedFinalReason,
                    selectedCropKind = sourceSnapshot?.selectedCropKind ?: observation.cropKind,
                    fingerprintKind = fingerprint.kind.name,
                    platform = fingerprint.platformTextHint.name,
                    persistReason = persistenceResult.reason
                )
            )
            if (observation.triggerSource == TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_PRE_OFFER_WATCHDOG_PROBE_RESULT",
                    "probeIndex" to sourceSnapshot?.sourceObservation?.metadata?.notes?.get("preOfferWatchdogProbeIndex"),
                    "fingerprintKind" to fingerprint.kind,
                    "platform" to fingerprint.platformTextHint,
                    "persistReason" to persistenceResult.reason
                )
            } else if (observation.triggerSource == TriggerSource.NINETY_NINE_VISUAL_PROBE) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_99_VISUAL_PROBE_RESULT",
                    "fingerprintKind" to fingerprint.kind,
                    "platform" to fingerprint.platformTextHint,
                    "persistReason" to persistenceResult.reason,
                    "nonOfferReason" to resolvedFinalReason
                )
                if (retryAttempt > 0) {
                    RadarLogger.i(
                        "KM_V2_AUTO",
                        "KM_V2_99_VISUAL_PROBE_RETRY_RESULT",
                        "fingerprintKind" to fingerprint.kind,
                        "platform" to fingerprint.platformTextHint,
                        "persistReason" to persistenceResult.reason
                    )
                    autoMissDiagnostics.recordAutoTrace(
                        AutoAttemptTrace(
                            timestampMs = clock.nowMs(),
                            triggerSource = observation.triggerSource,
                            stage = if (persistenceResult.persisted) "retry_result_success" else "retry_result_failed",
                            reason = resolvedFinalReason,
                            fingerprintKind = fingerprint.kind.name,
                            platform = fingerprint.platformTextHint.name,
                            persistReason = persistenceResult.reason
                        )
                    )
                }
            }
        } else if (
            fingerprint.kind == OfferTextFingerprintKind.OFFER_LIKE &&
            persistenceResult.persisted
        ) {
            val persistedOffer = persistenceResult.seenOffer
            autoMissDiagnostics.reportManualOracleSuccess(
                manualObservationId = observation.observationId,
                manualPlatform = persistedOffer?.platform?.name ?: fingerprint.platformTextHint.name,
                manualPrice = persistedOffer?.price ?: fingerprint.priceCandidates.firstOrNull()?.normalizedValue,
                manualDistances = fingerprint.distanceCandidates.joinToString(",") { "${it.normalizedValue}${it.unit}" },
                manualTimes = fingerprint.timeCandidates.joinToString(",") { "${it.normalizedValue}${it.unit}" },
                manualSelectedCropKind = observation.cropKind.name,
                manualTriggerSource = observation.triggerSource.name,
                timestampMs = clock.nowMs()
            )
        }
        if (wasOverlayShown) {
            lastKmOneOverlayShownAtMs = clock.nowMs()
        }
        RadarLogger.i(
            "KM_V2_PIPELINE",
            "KM_V2_OFFER_PIPELINE_FINAL_RESULT",
            "observationId" to observation.observationId,
            "triggerSource" to observation.triggerSource,
            "selectedCropKind" to (sourceSnapshot?.selectedCropKind ?: observation.cropKind),
            "fingerprintKind" to fingerprint.kind,
            "platform" to fingerprint.platformTextHint,
            "wasObstructionSuspected" to (obstructionResult?.detected == true),
            "obstructionReason" to obstructionResult?.reason,
            "obstructionAction" to sourceSnapshot?.obstructionAction,
            "alternativeCropApplied" to sourceSnapshot?.alternativeCropApplied,
            "originalCropKind" to sourceSnapshot?.originalCropKind,
            "wasRetryScheduled" to burstOutcome.scheduled,
            "wasOverlayShown" to wasOverlayShown,
            "overlayKind" to overlayKind,
            "wasPersisted" to persistenceResult.persisted,
            "persistedSeenOfferId" to persistenceResult.seenOffer?.id,
            "persistReason" to persistenceResult.reason,
            "parserStatus" to parserStatus,
            "decisionStatus" to decisionStatus,
            "presentationStatus" to presentationStatus,
            "finalReason" to resolvedFinalReason
        )
    }

    private fun maybeScheduleAutomaticBurst(
        observation: OcrObservation,
        fingerprint: OfferTextFingerprint
    ): AutoBurstOutcome {
        val context = burstContextByObservationId[observation.observationId]
        val attempt = context?.attempt ?: 0
        val sourceSnapshot = automaticCaptureSourcesByObservationId[observation.observationId]
        val obstructionResult = sourceSnapshot?.obstructionResult ?: floatingObstructionByObservationId[observation.observationId]
        if (attempt > 0) {
            val sourceObservationId = context?.sourceObservation?.id ?: sourceSnapshot?.sourceObservation?.id
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_RESULT",
                "observationId" to observation.observationId,
                "sourceObservationId" to sourceObservationId,
                "kind" to fingerprint.kind,
                "attempt" to attempt
            )
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_CAPTURE_UNKNOWN_FALLBACK_RESULT",
                "observationId" to observation.observationId,
                "sourceObservationId" to sourceObservationId,
                "fallbackCropKind" to observation.cropKind,
                "fingerprintKind" to fingerprint.kind,
                "offerLikeScore" to fingerprint.offerLikeScore,
                "rawOcrText" to observation.rawTextPreview(),
                "fingerprintReason" to fingerprint.reason
            )
            if (
                context?.originalTriggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC &&
                observation.cropKind == CropKind.LOWER_HALF
            ) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_UBER_DOMINANT_LOWER_HALF_RESULT",
                    "observationId" to observation.observationId,
                    "sourceObservationId" to sourceObservationId,
                    "fingerprintKind" to fingerprint.kind,
                    "rawTextPreview" to observation.rawTextPreview()
                )
            }
            RadarDebugStore.updateAutoBurstSummary(
                attempt = attempt,
                result = fingerprint.kind.name.lowercase()
            )
            if (fingerprint.kind == OfferTextFingerprintKind.UNKNOWN || fingerprint.kind == OfferTextFingerprintKind.NON_OFFER) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_BURST_FINAL_FAILURE",
                    "sourceObservationId" to sourceObservationId,
                    "burstObservationId" to observation.observationId,
                    "selectedCropKind" to observation.cropKind,
                    "rawOcrText" to observation.rawTextPreview(),
                    "fingerprintKind" to fingerprint.kind,
                    "fingerprintReason" to fingerprint.reason
                )
            }
        }
        val decision = automaticCaptureBurstPolicy.evaluate(
            AutomaticCaptureBurstInput(
                observationId = observation.observationId,
                triggerSource = observation.triggerSource,
                cropKind = observation.cropKind,
                rawOcrText = observation.rawText,
                fingerprintKind = fingerprint.kind,
                platformHint = fingerprint.platformTextHint,
                createdAtMs = observation.finishedAtMs,
                captureStartedAtMs = observation.startedAtMs,
                attempt = attempt,
                obstructionSuspected = obstructionResult?.detected == true,
                obstructionOverlapsCriticalArea = obstructionResult?.overlapsCriticalOfferArea == true
            ),
            nowMs = clock.nowMs()
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_EVALUATED",
            "observationId" to observation.observationId,
            "triggerSource" to observation.triggerSource,
            "fingerprintKind" to fingerprint.kind,
            "attempt" to attempt,
            "reason" to decision.reason
        )
        if (fingerprint.kind == OfferTextFingerprintKind.UNKNOWN &&
            obstructionResult?.detected == true &&
            obstructionResult.overlapsCriticalOfferArea
        ) {
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_FLOATING_OBSTRUCTION_UNKNOWN_GUARD_APPLIED",
                "observationId" to observation.observationId,
                "triggerSource" to observation.triggerSource,
                "fingerprintKind" to fingerprint.kind,
                "previousReason" to fingerprint.reason,
                "newReason" to "possible_floating_obstruction",
                "retryScheduled" to decision.shouldScheduleBurst
            )
        }
        if (!decision.shouldScheduleBurst) {
            when (decision.reason) {
                "operational_screen_recovery_suppressed" -> RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_RECOVERY_SUPPRESSED_OPERATIONAL_SCREEN",
                    "observationId" to observation.observationId,
                    "triggerSource" to observation.triggerSource,
                    "rawTextPreview" to observation.rawTextPreview(),
                    "fingerprintKind" to fingerprint.kind
                )
                "map_searching_recovery_suppressed" -> RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_RECOVERY_SUPPRESSED_MAP_SEARCHING",
                    "observationId" to observation.observationId,
                    "triggerSource" to observation.triggerSource,
                    "rawTextPreview" to observation.rawTextPreview(),
                    "fingerprintKind" to fingerprint.kind
                )
            }
            autoMissDiagnostics.recordAutoTrace(
                AutoAttemptTrace(
                    timestampMs = clock.nowMs(),
                    triggerSource = observation.triggerSource,
                    stage = "recovery_suppressed",
                    reason = decision.reason,
                    selectedCropKind = observation.cropKind,
                    fingerprintKind = fingerprint.kind.name,
                    platform = fingerprint.platformTextHint.name,
                    persistReason = if (decision.reason?.contains("suppressed") == true) decision.reason else null
                )
            )
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_SKIPPED",
                "observationId" to observation.observationId,
                "reason" to decision.reason
            )
            RadarDebugStore.updateAutoBurstSummary(
                scheduled = false,
                reason = decision.reason,
                attempt = attempt,
                suppressedReason = decision.reason
            )
            if (attempt > 0) {
                RadarDebugStore.updateAutoBurstSummary(result = fingerprint.kind.name.lowercase())
            }
            return AutoBurstOutcome(
                scheduled = false,
                reason = decision.reason,
                attempt = attempt
            )
        }
        val sourceObservation = context?.sourceObservation
            ?: sourceSnapshot?.sourceObservation
            ?: observationsById[observation.observationId]
        if (sourceObservation == null) {
            val missingField = when {
                sourceSnapshot == null && context == null -> "source_snapshot"
                observationsById[observation.observationId] == null -> "source_observation"
                else -> "unknown"
            }
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_SOURCE_MISSING_DETAIL",
                "observationId" to observation.observationId,
                "missingField" to missingField,
                "triggerSource" to observation.triggerSource,
                "selectedCropKind" to (sourceSnapshot?.selectedCropKind ?: observation.cropKind),
                "attempt" to attempt
            )
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_SUPPRESSED",
                "observationId" to observation.observationId,
                "reason" to "source_observation_missing"
            )
            return AutoBurstOutcome(
                scheduled = false,
                reason = "source_observation_missing",
                attempt = attempt
            )
        }
        val burstContext = AutoBurstContext(
            sourceObservation = sourceObservation,
            originalTriggerSource = context?.originalTriggerSource ?: observation.triggerSource,
            attempt = attempt + 1,
            preferredCropOrder = decision.preferredCropOrder,
            scheduledAtMs = clock.nowMs(),
            obstructionResult = obstructionResult
        )
        val token = "${sourceObservation.captureRequestId}:${burstContext.attempt}"
        if (decision.reason == "unknown_center_card_probable_offer_context") {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_CAPTURE_UNKNOWN_FALLBACK_REQUESTED",
                "observationId" to observation.observationId,
                "fromCropKind" to observation.cropKind,
                "fallbackCropKind" to decision.preferredCropOrder.firstOrNull(),
                "reason" to decision.reason
            )
        }
        if (decision.reason == "dominant_center_unknown_retry_lower_half") {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_UBER_DOMINANT_CENTER_UNKNOWN_RETRY_LOWER_HALF",
                "observationId" to observation.observationId,
                "fromCropKind" to observation.cropKind,
                "fallbackCropKind" to decision.preferredCropOrder.firstOrNull(),
                "rawTextPreview" to observation.rawTextPreview(),
                "fingerprintKind" to fingerprint.kind
            )
        }
        val replacedToken = automaticCaptureBurstScheduler.schedule(
            token = token,
            delayMs = decision.delayMs
        ) {
            executeAutomaticBurst(token, burstContext)
        }
        if (replacedToken != null) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_SUPPRESSED",
                "reason" to "superseded_by_newer_burst",
                "replacedToken" to replacedToken,
                "newToken" to token,
                "sourceObservationId" to sourceObservation.id
            )
        }
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_RETRY_SCHEDULED",
            "observationId" to observation.observationId,
            "triggerSource" to observation.triggerSource,
            "attempt" to burstContext.attempt,
            "delayMs" to decision.delayMs,
            "reason" to decision.reason,
            "sourceObservationId" to sourceObservation.id
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_SCHEDULED",
              "observationId" to observation.observationId,
              "sourceObservationId" to sourceObservation.id,
              "delayMs" to decision.delayMs,
              "preferredCropOrder" to decision.preferredCropOrder.joinToString(","),
              "attempt" to burstContext.attempt,
              "reason" to decision.reason
          )
        if (decision.reason == "possible_floating_obstruction") {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_FLOATING_OBSTRUCTION_RETRY_REQUESTED",
                "observationId" to observation.observationId,
                "triggerSource" to observation.triggerSource,
                "delayMs" to decision.delayMs,
                "reason" to "possible_floating_obstruction"
            )
        }
        RadarDebugStore.updateAutoBurstSummary(
            scheduled = true,
            reason = decision.reason,
            delayMs = decision.delayMs,
            attempt = burstContext.attempt,
            preferredCropOrder = decision.preferredCropOrder.joinToString(",")
        )
        return AutoBurstOutcome(
            scheduled = true,
            reason = decision.reason,
            delayMs = decision.delayMs,
            attempt = burstContext.attempt
        )
    }

    private fun executeAutomaticBurst(
        token: String,
        burstContext: AutoBurstContext
    ) {
        if (serviceDestroyed.get()) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_STALE_IGNORED", "reason" to "service_destroyed", "token" to token, "sourceObservationId" to burstContext.sourceObservation.id)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "service_destroyed")
            return
        }
        if (ManualAnalysisState.isRunning()) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_STALE_IGNORED", "reason" to "manual_epoch_changed", "token" to token, "sourceObservationId" to burstContext.sourceObservation.id)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "manual_epoch_changed")
            return
        }
        val ageMs = clock.nowMs() - burstContext.sourceObservation.requestCreatedAtMs
        if (ageMs > com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags.AUTO_CAPTURE_BURST_MAX_AGE_MS) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_STALE_IGNORED", "reason" to "too_old", "token" to token, "ageMs" to ageMs, "sourceObservationId" to burstContext.sourceObservation.id)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "too_old")
            return
        }
        if (!autoBurstCaptureInFlight.compareAndSet(false, true)) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_SUPPRESSED", "reason" to "active_capture_busy", "token" to token, "sourceObservationId" to burstContext.sourceObservation.id)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "active_capture_busy")
            return
        }
        val request = buildAutomaticBurstRequest(burstContext)
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_STARTED",
            "requestId" to request.id,
            "sourceObservationId" to burstContext.sourceObservation.id,
            "attempt" to burstContext.attempt,
            "originalTriggerSource" to burstContext.originalTriggerSource
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_SCREENSHOT_STARTED",
            "requestId" to request.id,
            "triggerSource" to request.triggerSource,
            "sourceObservationId" to burstContext.sourceObservation.id,
            "preferredCropOrder" to burstContext.preferredCropOrder.joinToString(","),
            "newScreenshot" to true
        )
        screenshotCapturer.capture(
            request = request,
            onSuccess = { burstRequest, result ->
                val baseObservation = screenObservationFactory.create(burstRequest, result)
                val enrichedObservation = baseObservation.copy(
                    metadata = baseObservation.metadata.copy(
                        notes = baseObservation.metadata.notes + mapOf(
                            "autoBurstAttempt" to burstContext.attempt.toString(),
                            "autoBurstOriginalTriggerSource" to burstContext.originalTriggerSource.name,
                            "autoBurstPreferredCropOrder" to burstContext.preferredCropOrder.joinToString(","),
                            "autoBurstSourceObservationId" to burstContext.sourceObservation.id,
                            "floatingObstructionDetected" to (burstContext.obstructionResult?.detected?.toString() ?: "false"),
                            "floatingObstructionReason" to (burstContext.obstructionResult?.reason ?: "none")
                        )
                    )
                )
                burstContextByObservationId[enrichedObservation.id] = burstContext
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_BURST_CROP_ORDER_APPLIED",
                    "observationId" to enrichedObservation.id,
                    "sourceObservationId" to burstContext.sourceObservation.id,
                    "preferredCropOrder" to burstContext.preferredCropOrder.joinToString(",")
                )
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_BURST_OBSERVATION_LINKED",
                    "sourceObservationId" to burstContext.sourceObservation.id,
                    "burstObservationId" to enrichedObservation.id,
                    "requestId" to burstRequest.id,
                    "triggerSource" to enrichedObservation.triggerSource,
                    "preferredCropOrder" to burstContext.preferredCropOrder.joinToString(",")
                )
                onObservationCreated(enrichedObservation, result)
            },
            onFailure = { burstRequest, error, _, _, _, _ ->
                RadarLogger.w(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_BURST_SUPPRESSED",
                    "requestId" to burstRequest.id,
                    "sourceObservationId" to burstContext.sourceObservation.id,
                    "reason" to "screenshot_failed",
                    "error" to error
                )
                RadarDebugStore.updateAutoBurstSummary(suppressedReason = "screenshot_failed")
            },
            onFinished = { _, _ ->
                autoBurstCaptureInFlight.set(false)
            }
        )
    }

    private fun buildAutomaticBurstRequest(burstContext: AutoBurstContext): com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest {
        val nowMs = clock.nowMs()
        val cycleId = burstContext.sourceObservation.offerCycleClassification?.cycleId ?: "burst-${UUID.randomUUID()}"
        val classification = com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleClassification(
            kind = com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind.UNKNOWN,
            cycleId = cycleId,
            previousCycleId = burstContext.sourceObservation.offerCycleClassification?.cycleId,
            reason = "automatic_burst_recovery",
            timeSincePreviousMs = nowMs - burstContext.sourceObservation.capturedAtMs,
            shouldPreferForOcr = true
        )
        return com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest(
            id = UUID.randomUUID().toString(),
            sourceEventAtMs = burstContext.sourceObservation.createdAtMs,
            signalEmittedAtMs = burstContext.sourceObservation.createdAtMs,
            createdAtMs = nowMs,
            approvedAtMs = nowMs,
            triggerSource = TriggerSource.UBER_AUTO_BURST_RECOVERY,
            platformHint = burstContext.sourceObservation.visualPlatformHint ?: inferPlatformHint(
                dominantPackage = burstContext.sourceObservation.dominantPackage,
                floatingPackage = burstContext.sourceObservation.floatingPackage,
                nodeTreePackage = null
            ),
            priority = com.lucastrevvos.kmonemotor.radar.orchestrator.CapturePriority.HIGH,
            dominantPackage = burstContext.sourceObservation.dominantPackage,
            floatingPackage = burstContext.sourceObservation.floatingPackage,
            floatingBounds = burstContext.sourceObservation.floatingBounds,
            floatingKind = burstContext.sourceObservation.floatingKind,
            reason = "uber_auto_burst_recovery_capture",
            offerCycleClassification = classification
        )
    }

    private data class AutoBurstContext(
        val sourceObservation: ScreenObservation,
        val originalTriggerSource: TriggerSource,
        val attempt: Int,
        val preferredCropOrder: List<CropKind>,
        val scheduledAtMs: Long,
        val obstructionResult: FloatingObstructionResult? = null
    )

    private data class AutoInitialMicroBurstState(
        val sourceObservation: ScreenObservation,
        val frameDelaysMs: List<Long>,
        val startedAtMs: Long,
        val maxDurationMs: Long = 800L,
        val completedFrames: MutableSet<Int> = mutableSetOf(),
        val retryCountByFrame: MutableMap<Int, Int> = mutableMapOf(),
        var nextAllowedAtMs: Long = startedAtMs,
        var finished: Boolean = false,
        var offerLikeFound: Boolean = false,
        var bestObservationId: String? = null
    )

    private data class AutoInitialMicroBurstFrameContext(
        val sourceObservationId: String,
        val frameIndex: Int,
        val delayMs: Long
    )

    private data class AutoBurstOutcome(
        val scheduled: Boolean = false,
        val reason: String? = null,
        val delayMs: Long? = null,
        val attempt: Int = 0
    )

    private data class AutomaticCaptureSourceSnapshot(
        val observationId: String,
        val triggerSource: TriggerSource,
        val selectedCropKind: CropKind?,
        val attempt: Int,
        val createdAtMs: Long,
        val sourceObservation: ScreenObservation,
        val originalTriggerSource: TriggerSource,
        val obstructionResult: FloatingObstructionResult? = null,
        val obstructionAction: FloatingObstructionAction? = null,
        val alternativeCropApplied: Boolean = false,
        val originalCropKind: CropKind? = null
    )

    private fun candidateSafeFromObstruction(candidate: CropCandidate, obstructionRect: Rect?): Boolean {
        return obstructionRect == null || !rectsIntersect(candidate.rect, obstructionRect)
    }

    private fun rectsIntersect(first: Rect, second: Rect): Boolean {
        return first.left < second.right &&
            second.left < first.right &&
            first.top < second.bottom &&
            second.top < first.bottom
    }

    private fun OcrObservation.rawTextPreview(maxLength: Int = 120): String {
        return rawText.replace("\n", " ").trim().take(maxLength)
    }
}
