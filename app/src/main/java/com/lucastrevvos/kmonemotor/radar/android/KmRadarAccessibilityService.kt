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
import com.lucastrevvos.kmonemotor.radar.ocr.rawTextPreview
import com.lucastrevvos.kmonemotor.radar.orchestrator.ManualAnalysisContext
import com.lucastrevvos.kmonemotor.radar.orchestrator.RadarCaptureOrchestrator
import com.lucastrevvos.kmonemotor.radar.parser.OfferParser
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserDebugWriter
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationDebugWriter
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationProcessor
import com.lucastrevvos.kmonemotor.radar.piu.PiuOverlayRuntime
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureBurstInput
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureBurstPolicy
import com.lucastrevvos.kmonemotor.radar.recovery.AutomaticCaptureBurstScheduler
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferPersistenceProcessor
import com.lucastrevvos.kmonemotor.radar.seenoffers.SeenOfferPersistenceResult
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
    private val observationsById = mutableMapOf<String, ScreenObservation>()
    private val floatingObstructionByObservationId = mutableMapOf<String, FloatingObstructionResult>()
    private val automaticCaptureSourcesByObservationId = mutableMapOf<String, AutomaticCaptureSourceSnapshot>()
    private var latestWindowSnapshot: WindowStackSnapshot? = null
    private var latestNodeSignature: NodeTreeSignature? = null
    private var latestFloatingKind: FloatingWindowKind = FloatingWindowKind.UNKNOWN_FLOATING
    private val orchestrator by lazy {
        RadarCaptureOrchestrator(
            screenshotCapturer = screenshotCapturer,
            clock = clock,
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
        val effectiveVisualResult = buildEffectiveVisualResult(adjustedVisualResult, recoveryDecision)
        updateAutomaticCaptureSource(
            observationId = observation.id,
            selectedCropKind = effectiveVisualResult.bestCandidate?.kind
        )
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
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_AUTO_VISION_RECOVERY_EVALUATED",
            "observationId" to observation.id,
            "triggerSource" to observation.triggerSource,
            "visualReason" to visualResult.reason,
            "recoveryReason" to recoveryDecision.reason
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
            reason = recoveryDecision.reason,
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
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> listOf(
                CropKind.CENTER_CARD_AREA,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                CropKind.LOWER_HALF
            )
            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
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
            onFingerprintReady(fingerprint)
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OCR",
                "KM_V2_OCR_FAILED",
                "observationId" to observation.observationId,
                "error" to throwable.message,
                "stacktrace" to throwable.stackTraceToString()
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
            fingerprintDebugWriter.write(fingerprint, observation)
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
                presentationResult.presentation?.let { presentation ->
                    DecisionOverlayRuntime.get(this).showPresentation(presentation)
                }
                logOfferPipelineFinalResult(
                    observation = observation,
                    fingerprint = fingerprint,
                    parserStatus = parserResult.status,
                    parserReason = parserResult.reason,
                    decisionStatus = decisionResult.status,
                    decisionReason = decisionResult.result?.decision?.name ?: decisionResult.reason,
                    presentationStatus = presentationResult.status,
                    presentationReason = presentationResult.presentation?.kind?.name ?: presentationResult.reason,
                      wasOverlayShown = presentationResult.presentation != null,
                      overlayKind = presentationResult.presentation?.kind?.name,
                      burstOutcome = burstOutcome,
                      persistenceResult = persistenceResult
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

    private fun releaseManualPreparedBitmaps(preparedBitmaps: Map<String, PreparedManualCrop<Bitmap>>) {
        manualBitmapPreparer.releaseAll(preparedBitmaps)
        RadarLogger.i("KM_V2_OCR", "KM_V2_MANUAL_BITMAP_RELEASED", "count" to preparedBitmaps.size)
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
        val obstructionResult = sourceSnapshot?.obstructionResult
        val resolvedFinalReason = finalReason
            ?: burstOutcome.reason?.takeIf { burstOutcome.scheduled }
            ?: presentationReason
            ?: decisionReason
            ?: parserReason
            ?: fingerprint.reason
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
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_BURST_RESULT",
                "observationId" to observation.observationId,
                "kind" to fingerprint.kind,
                "attempt" to attempt
            )
            RadarDebugStore.updateAutoBurstSummary(
                attempt = attempt,
                result = fingerprint.kind.name.lowercase()
            )
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
        automaticCaptureBurstScheduler.schedule(
            token = token,
            delayMs = decision.delayMs
        ) {
            executeAutomaticBurst(token, burstContext)
        }
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_RETRY_SCHEDULED",
            "observationId" to observation.observationId,
            "triggerSource" to observation.triggerSource,
            "attempt" to burstContext.attempt,
            "delayMs" to decision.delayMs,
            "reason" to decision.reason
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_SCHEDULED",
              "observationId" to observation.observationId,
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
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_STALE_IGNORED", "reason" to "service_destroyed", "token" to token)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "service_destroyed")
            return
        }
        if (ManualAnalysisState.isRunning()) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_STALE_IGNORED", "reason" to "manual_epoch_changed", "token" to token)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "manual_epoch_changed")
            return
        }
        val ageMs = clock.nowMs() - burstContext.sourceObservation.requestCreatedAtMs
        if (ageMs > com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags.AUTO_CAPTURE_BURST_MAX_AGE_MS) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_STALE_IGNORED", "reason" to "too_old", "token" to token, "ageMs" to ageMs)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "too_old")
            return
        }
        if (!autoBurstCaptureInFlight.compareAndSet(false, true)) {
            RadarLogger.i("KM_V2_AUTO", "KM_V2_AUTO_BURST_SUPPRESSED", "reason" to "active_capture_busy", "token" to token)
            RadarDebugStore.updateAutoBurstSummary(suppressedReason = "active_capture_busy")
            return
        }
        val request = buildAutomaticBurstRequest(burstContext)
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_STARTED",
            "requestId" to request.id,
            "attempt" to burstContext.attempt,
            "originalTriggerSource" to burstContext.originalTriggerSource
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_BURST_SCREENSHOT_STARTED",
            "requestId" to request.id,
            "triggerSource" to request.triggerSource
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
                    "preferredCropOrder" to burstContext.preferredCropOrder.joinToString(",")
                )
                onObservationCreated(enrichedObservation, result)
            },
            onFailure = { burstRequest, error, _, _, _, _ ->
                RadarLogger.w(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_BURST_SUPPRESSED",
                    "requestId" to burstRequest.id,
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
}
