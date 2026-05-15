package com.lucastrevvos.kmonemotor.radar.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Process
import android.view.accessibility.AccessibilityEvent
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.orchestrator.RadarCaptureOrchestrator
import com.lucastrevvos.kmonemotor.radar.signals.FloatingWindowClassifier
import com.lucastrevvos.kmonemotor.radar.signals.OperationalStateTracker
import com.lucastrevvos.kmonemotor.radar.signals.RadarSignalLayer
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbe
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors

class KmRadarAccessibilityService : AccessibilityService() {
    private val instanceId = INSTANCE_COUNTER.incrementAndGet()
    private val clock = RadarClock.System
    private val windowStackReader = WindowStackReader()
    private val nodeTreeReader = NodeTreeReader()
    private val floatingWindowClassifier = FloatingWindowClassifier()
    private val operationalStateTracker = OperationalStateTracker()
    private val signalLayer = RadarSignalLayer()
    private val screenshotCapturer by lazy { AccessibilityScreenshotCapturer(this, clock) }
    private val visualOfferProbe by lazy { VisualOfferProbe(this, clock = clock) }
    private val visionExecutor = Executors.newSingleThreadExecutor()
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
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        RadarDebugStore.updateServiceActive(false)
        visionExecutor.shutdownNow()
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

    private fun onObservationCreated(observation: ScreenObservation, result: com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult) {
        val bitmap = result.screenshotBitmap ?: return
        visionExecutor.execute {
            try {
                visualOfferProbe.run(observation, bitmap)
            } catch (throwable: Throwable) {
                RadarLogger.w(
                    "KM_V2_VISION",
                    "KM_V2_VISUAL_PROBE_FAILED",
                    "observationId" to observation.id,
                    "error" to throwable.message,
                    "stacktrace" to throwable.stackTraceToString()
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
}
