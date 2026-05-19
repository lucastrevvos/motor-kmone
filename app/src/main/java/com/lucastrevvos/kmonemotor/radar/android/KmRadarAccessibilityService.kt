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
import com.lucastrevvos.kmonemotor.radar.piu.PiuOverlayRuntime
import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature
import com.lucastrevvos.kmonemotor.radar.signals.FloatingWindowClassifier
import com.lucastrevvos.kmonemotor.radar.signals.OperationalStateTracker
import com.lucastrevvos.kmonemotor.radar.signals.RadarSignalLayer
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import com.lucastrevvos.kmonemotor.radar.vision.SmartCropper
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbe
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbeResult
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
    private val screenshotCapturer by lazy { AccessibilityScreenshotCapturer(this, clock) }
    private val visualOfferProbe by lazy { VisualOfferProbe(this, clock = clock) }
    private val ocrRunPolicy = OcrRunPolicy()
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
    private val manualAnalysisPlanner = ManualAnalysisPlanner()
    private val manualBitmapPreparer = ManualSecondaryOcrBitmapPreparer(AndroidManualCropFactory)
    private val visionExecutor = Executors.newSingleThreadExecutor()
    private val regionalOcrEngine by lazy { MlKitRegionalOcrEngine(this, visionExecutor, clock) }
    private val manualAnalysisListener: (ManualAnalysisRequest) -> Unit = { handleManualAnalysisRequest(it) }
    private val manualTimings = mutableMapOf<Long, ManualAnalysisTiming>()
    private val finalizedManualEpochs = mutableSetOf<Long>()
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
        RadarDebugStore.updateServiceActive(false)
        visionExecutor.shutdownNow()
        ManualAnalysisRequestBus.unregister(manualAnalysisListener)
        PiuOverlayRuntime.get(this).destroy()
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
        val policy = ocrRunPolicy.decide(observation, visualResult)
        RadarLogger.i(
            "KM_V2_OCR",
            "KM_V2_OCR_POLICY_DECISION",
            "observationId" to observation.id,
            "shouldRun" to policy.shouldRun,
            "reason" to policy.reason,
            "offerCycleKind" to observation.offerCycleClassification?.kind,
            "cropKind" to visualResult.bestCandidate?.kind
        )
        RadarDebugStore.updateOcrSummary(
            durationMs = null,
            success = null,
            cropKind = visualResult.bestCandidate?.kind?.name,
            rawTextPreview = null,
            policyReason = policy.reason
        )
        if (!policy.shouldRun) {
            return
        }

        val bestCandidate = visualResult.bestCandidate ?: return
        executeOcrCandidate(
            observation = observation,
            selectedCandidate = bestCandidate,
            screenshotBitmap = screenshotBitmap,
            acceptedForOcrFuture = visualResult.acceptedForOcrFuture,
            candidateReason = visualResult.reason,
            policyReason = policy.reason
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
            } catch (throwable: Throwable) {
                RadarLogger.w(
                    "KM_V2_DEDUPE",
                    "KM_V2_DEDUPE_FAILED",
                    "observationId" to observation.observationId,
                    "error" to throwable.message,
                    "stacktrace" to throwable.stackTraceToString()
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
}
