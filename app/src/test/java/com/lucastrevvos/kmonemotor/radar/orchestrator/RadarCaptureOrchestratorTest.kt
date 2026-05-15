package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCapturer
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotFinishStatus
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.RadarSignal
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarCaptureOrchestratorTest {
    @Test
    fun uberFloatingOver99Eligible_createsDiagnosticCaptureRequest() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 1_000L }
        )

        orchestrator.onSignal(
            RadarSignal.UberFloatingOverOtherApp(
                dominantPackage = "com.app99.driver",
                floatingPackage = "com.ubercab.driver",
                floatingBounds = "901 392 1080 571",
                floatingCoverage = 0.013,
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                eventReceivedAtMs = 1_000L,
                signalEmittedAtMs = 1_010L
            )
        )

        assertEquals(1, capturer.requests.size)
        assertEquals(
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            capturer.requests.single().triggerSource
        )
        assertEquals(
            "uber_floating_over_99_diagnostic_capture",
            capturer.requests.single().reason
        )
    }

    @Test
    fun uberFloatingBubbleOutside99_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 1_000L }
        )

        orchestrator.onSignal(
            RadarSignal.UberFloatingOverOtherApp(
                dominantPackage = "com.whatsapp",
                floatingPackage = "com.ubercab.driver",
                floatingBounds = "901 392 1080 571",
                floatingCoverage = 0.013,
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                eventReceivedAtMs = 1_000L,
                signalEmittedAtMs = 1_010L
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun ninetyNineCompactTreeEligible_createsDiagnosticCaptureRequest() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 2_000L }
        )

        orchestrator.onSignal(
            ninetyNineSignal(
                nodeCount = 15,
                visibleTextNodeCount = 2,
                floatingPackage = "com.ubercab.driver",
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE
            )
        )

        assertEquals(1, capturer.requests.size)
        assertEquals(
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC,
            capturer.requests.single().triggerSource
        )
        assertEquals(
            "ninety_nine_compact_tree_diagnostic_capture",
            capturer.requests.single().reason
        )
    }

    @Test
    fun ninetyNineCompactTreeWithoutVisibleText_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 2_000L }
        )

        orchestrator.onSignal(
            ninetyNineSignal(
                nodeCount = 15,
                visibleTextNodeCount = 0,
                floatingPackage = "com.ubercab.driver",
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun ninetyNineCompactTreeWithSystemUiFloating_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 2_000L }
        )

        orchestrator.onSignal(
            ninetyNineSignal(
                nodeCount = 15,
                visibleTextNodeCount = 2,
                floatingPackage = "com.android.systemui",
                floatingKind = FloatingWindowKind.SYSTEM_UI_FLOATING
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun ninetyNineStrongPathStillWorksWhenNodeCountAtLeastTwenty() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 2_000L }
        )

        orchestrator.onSignal(
            ninetyNineSignal(
                nodeCount = 25,
                visibleTextNodeCount = 4,
                numericTextNodeCount = 1,
                buttonLikeNodeCount = 1,
                floatingPackage = "com.ubercab.driver",
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE
            )
        )

        assertEquals(1, capturer.requests.size)
        assertEquals(
            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            capturer.requests.single().triggerSource
        )
    }

    @Test
    fun uberDominantSearchingExit_createsDiagnosticCaptureRequest() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE
            )
        )

        assertEquals(1, capturer.requests.size)
        assertEquals(
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            capturer.requests.single().triggerSource
        )
        assertEquals(
            com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind.NEW_OFFER_CYCLE,
            capturer.requests.single().offerCycleClassification?.kind
        )
    }

    @Test
    fun uberDominantNumericText_createsDiagnosticCaptureRequest() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 16,
                visibleTextNodeCount = 2,
                numericTextNodeCount = 1
            )
        )

        assertEquals(1, capturer.requests.size)
        assertEquals(
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            capturer.requests.single().triggerSource
        )
    }

    @Test
    fun uberStateGenericWithoutOfferSignals_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.OFFLINE,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                nodeCount = 10,
                visibleTextNodeCount = 1,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantWithSystemUiFloating_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                floatingPackage = "com.android.systemui",
                floatingKind = FloatingWindowKind.SYSTEM_UI_FLOATING
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantCooldown_suppressesSecondDiagnosticCapture() {
        var nowMs = 3_000L
        val capturer = RecordingScreenshotCapturer(autoFinish = true)
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { nowMs }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE
            )
        )
        nowMs = 6_000L
        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE
            )
        )

        assertEquals(1, capturer.requests.size)
    }

    private fun ninetyNineSignal(
        nodeCount: Int,
        visibleTextNodeCount: Int,
        floatingPackage: String,
        floatingKind: FloatingWindowKind,
        numericTextNodeCount: Int = 0,
        buttonLikeNodeCount: Int = 0
    ) = RadarSignal.NinetyNineTreeStructureChanged(
        signature = NodeTreeSignature(
            packageName = "com.app99.driver",
            nodeCount = nodeCount,
            visibleTextNodeCount = visibleTextNodeCount,
            clickableNodeCount = 1,
            maxDepth = 4,
            bottomHalfTextNodeCount = visibleTextNodeCount,
            numericTextNodeCount = numericTextNodeCount,
            buttonLikeNodeCount = buttonLikeNodeCount,
            knownStateTexts = emptyList()
        ),
        dominantPackage = "com.app99.driver",
        floatingPackage = floatingPackage,
        floatingBounds = "901 392 1080 571",
        floatingKind = floatingKind,
        eventReceivedAtMs = 2_000L,
        signalEmittedAtMs = 2_010L
    )

    private fun uberStateSignal(
        previousState: com.lucastrevvos.kmonemotor.radar.core.UberReadableState,
        currentState: com.lucastrevvos.kmonemotor.radar.core.UberReadableState,
        nodeCount: Int = 18,
        visibleTextNodeCount: Int = 3,
        previousNodeCount: Int = 9,
        previousVisibleTextNodeCount: Int = 1,
        numericTextNodeCount: Int = 0,
        buttonLikeNodeCount: Int = 0,
        floatingPackage: String = "com.app99.driver",
        floatingKind: FloatingWindowKind = FloatingWindowKind.FLOATING_BUBBLE,
        knownStateTexts: List<String> = emptyList(),
        previousKnownStateTexts: List<String> = emptyList()
    ) = RadarSignal.UberStateChanged(
        previousState = previousState,
        currentState = currentState,
        dominantPackage = "com.ubercab.driver",
        floatingPackage = floatingPackage,
        nodeTreePackage = "com.ubercab.driver",
        nodeCount = nodeCount,
        visibleTextNodeCount = visibleTextNodeCount,
        bottomHalfTextNodeCount = visibleTextNodeCount,
        numericTextNodeCount = numericTextNodeCount,
        buttonLikeNodeCount = buttonLikeNodeCount,
        knownStateTexts = knownStateTexts,
        previousKnownStateTexts = previousKnownStateTexts,
        previousNodeCount = previousNodeCount,
        previousVisibleTextNodeCount = previousVisibleTextNodeCount,
        floatingBounds = "200 300 900 1600",
        floatingKind = floatingKind,
        eventReceivedAtMs = 3_000L,
        signalEmittedAtMs = 3_010L
    )

    private class RecordingScreenshotCapturer(
        private val autoFinish: Boolean = false
    ) : ScreenshotCapturer {
        val requests = mutableListOf<CaptureRequest>()

        override fun capture(
            request: CaptureRequest,
            onSuccess: (CaptureRequest, ScreenshotCaptureResult) -> Unit,
            onFailure: (CaptureRequest, String, Int?, Long, Long, ScreenshotFinishStatus) -> Unit,
            onFinished: (CaptureRequest, ScreenshotFinishStatus) -> Unit
        ) {
            requests += request
            if (autoFinish) {
                onFinished(request, ScreenshotFinishStatus.SUCCESS)
            }
        }
    }
}
