package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCapturer
import com.lucastrevvos.kmonemotor.radar.android.ScreenshotFinishStatus
import com.lucastrevvos.kmonemotor.radar.core.AnalysisEpochController
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.PlatformHint
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
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                knownStateTexts = listOf("uberx", "r$ 10,16", "4 min (1.3 km)")
            )
        )

        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(300L)
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
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 16,
                visibleTextNodeCount = 2,
                numericTextNodeCount = 1,
                knownStateTexts = listOf("r$ 5,50", "4 min (1.3 km)")
            )
        )

        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(300L)
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
    fun uberDominantSearchingMapState_doesNotCreateDiagnosticCapture() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                nodeCount = 18,
                visibleTextNodeCount = 3,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 0,
                buttonLikeNodeCount = 0,
                bottomHalfTextNodeCount = 1,
                knownStateTexts = listOf("Buscando corridas")
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantOfferCardTreeChange_createsDiagnosticCapture() {
        val capturer = RecordingScreenshotCapturer()
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 4,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 1,
                bottomHalfTextNodeCount = 3,
                knownStateTexts = listOf("uberx", "r$ 5,50", "4 min (1.3 km)")
            )
        )

        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(300L)
        assertEquals(1, capturer.requests.size)
        assertEquals(TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC, capturer.requests.single().triggerSource)
        assertTrue(capturer.requests.single().offerCycleClassification?.shouldPreferForOcr == true)
    }

    @Test
    fun uberDominantButtonLikeWithoutPriceProductOrRoute_doesNotCreateDiagnosticCapture() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 9,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 1,
                buttonLikeNodeCount = 4,
                bottomHalfTextNodeCount = 9,
                knownStateTexts = listOf("procurando viagens", "ganhos")
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantPriceAndRouteWithoutProduct_createsDiagnosticCapture() {
        val capturer = RecordingScreenshotCapturer()
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 6,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 1,
                bottomHalfTextNodeCount = 6,
                knownStateTexts = listOf("r$ 5,50", "4 min (1.3 km)")
            )
        )

        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(300L)
        assertEquals(1, capturer.requests.size)
        assertEquals(TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC, capturer.requests.single().triggerSource)
    }

    @Test
    fun uberDominantUberProductPriceAndRoute_createsDiagnosticCapture() {
        val capturer = RecordingScreenshotCapturer()
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 7,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 2,
                bottomHalfTextNodeCount = 7,
                knownStateTexts = listOf("uberx", "r$ 5,50", "4 min (1.3 km)")
            )
        )

        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(300L)
        assertEquals(1, capturer.requests.size)
        assertEquals(TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC, capturer.requests.single().triggerSource)
        assertTrue(capturer.requests.single().offerCycleClassification?.shouldPreferForOcr == true)
    }

    @Test
    fun uberDominantOfferCardTreeSignal_overridesOperationalPostTransitionClassification() {
        var nowMs = 3_000L
        val capturer = RecordingScreenshotCapturer(autoFinish = true)
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { nowMs },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 4,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 1,
                bottomHalfTextNodeCount = 3,
                knownStateTexts = listOf("uberx", "r$ 5,50", "4 min (1.3 km)")
            )
        )
        scheduler.advanceBy(300L)

        nowMs = 6_000L
        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.OFFLINE,
                nodeCount = 32,
                visibleTextNodeCount = 13,
                previousNodeCount = 20,
                previousVisibleTextNodeCount = 4,
                numericTextNodeCount = 4,
                buttonLikeNodeCount = 5,
                bottomHalfTextNodeCount = 13,
                knownStateTexts = listOf("offline", "uberx", "r$ 5,50", "4 min (1.3 km)")
            )
        )
        scheduler.advanceBy(300L)

        val classification = capturer.requests.last().offerCycleClassification
        assertEquals(com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind.NEW_OFFER_CYCLE, classification?.kind)
        assertEquals("offer_card_tree_signal", classification?.reason)
        assertTrue(classification?.shouldPreferForOcr == true)
    }

    @Test
    fun uberDominantOfferCardCandidate_cancelledWhenSearchingTextAppearsBeforeDelay() {
        var nowMs = 3_000L
        val capturer = RecordingScreenshotCapturer()
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { nowMs },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 7,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 2,
                bottomHalfTextNodeCount = 7,
                knownStateTexts = listOf("uberx", "r$ 5,50", "4 min (1.3 km)")
            )
        )

        nowMs = 3_100L
        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                nodeCount = 24,
                visibleTextNodeCount = 6,
                previousNodeCount = 24,
                previousVisibleTextNodeCount = 7,
                numericTextNodeCount = 0,
                buttonLikeNodeCount = 3,
                bottomHalfTextNodeCount = 6,
                knownStateTexts = listOf("procurando viagens", "ganhos")
            )
        )

        scheduler.advanceBy(600L)
        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantOfferCardCandidate_resetsDebounceOnRelevantTreeChange() {
        var nowMs = 3_000L
        val capturer = RecordingScreenshotCapturer()
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { nowMs },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 24,
                visibleTextNodeCount = 6,
                previousNodeCount = 10,
                previousVisibleTextNodeCount = 1,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 1,
                bottomHalfTextNodeCount = 6,
                knownStateTexts = listOf("r$ 5,50", "4 min (1.3 km)")
            )
        )

        nowMs = 3_150L
        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 26,
                visibleTextNodeCount = 7,
                previousNodeCount = 24,
                previousVisibleTextNodeCount = 6,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 2,
                bottomHalfTextNodeCount = 7,
                knownStateTexts = listOf("uberx", "r$ 5,50", "4 min (1.3 km)")
            )
        )

        scheduler.advanceBy(299L)
        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(1L)
        assertEquals(1, capturer.requests.size)
    }

    @Test
    fun uberDominantStrongWithSystemUiFloating_createsDiagnosticCaptureRequest() {
        val capturer = RecordingScreenshotCapturer()
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                nodeCount = 125,
                visibleTextNodeCount = 11,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 4,
                knownStateTexts = listOf("uberx", "r$ 10,16", "4 min (1.3 km)"),
                floatingPackage = "com.android.systemui",
                floatingKind = FloatingWindowKind.SYSTEM_UI_FLOATING
            )
        )

        assertTrue(capturer.requests.isEmpty())
        scheduler.advanceBy(300L)
        assertEquals(1, capturer.requests.size)
        assertEquals(
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            capturer.requests.single().triggerSource
        )
    }

    @Test
    fun uberDominantWeakWithSystemUiFloating_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.UNKNOWN,
                nodeCount = 12,
                visibleTextNodeCount = 1,
                numericTextNodeCount = 0,
                buttonLikeNodeCount = 0,
                previousNodeCount = 12,
                previousVisibleTextNodeCount = 1,
                floatingPackage = "com.android.systemui",
                floatingKind = FloatingWindowKind.SYSTEM_UI_FLOATING
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantWithDifferentNodeTreePackage_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                nodeTreePackage = "com.app99.driver",
                floatingPackage = "com.android.systemui",
                floatingKind = FloatingWindowKind.SYSTEM_UI_FLOATING
            )
        )

        assertTrue(capturer.requests.isEmpty())
    }

    @Test
    fun uberDominantWithDifferentDominantPackage_staysIgnored() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 3_000L }
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                dominantPackage = "com.app99.driver",
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
        val scheduler = FakeStabilizationScheduler()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { nowMs },
            stabilizationScheduler = scheduler
        )

        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                knownStateTexts = listOf("uberx", "r$ 10,16", "4 min (1.3 km)")
            )
        )
        scheduler.advanceBy(300L)
        nowMs = 6_000L
        orchestrator.onSignal(
            uberStateSignal(
                previousState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.SEARCHING_RIDES,
                currentState = com.lucastrevvos.kmonemotor.radar.core.UberReadableState.ONLINE_IDLE,
                knownStateTexts = listOf("uberx", "r$ 10,16", "4 min (1.3 km)")
            )
        )
        scheduler.advanceBy(300L)

        assertEquals(1, capturer.requests.size)
    }

    @Test
    fun manualRequest_hasCriticalPriority() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 4_000L }
        )
        val epoch = AnalysisEpochController.bumpManualEpoch()

        val request = orchestrator.requestManualAnalysis(
            ManualAnalysisContext(
                requestedAtMs = 4_000L,
                analysisEpoch = epoch,
                source = "test",
                dominantPackage = "com.ubercab.driver",
                floatingPackage = "com.app99.driver",
                floatingBounds = "0 0 100 100",
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                platformHint = PlatformHint.UBER
            )
        )

        assertEquals(CapturePriority.CRITICAL, request.priority)
        assertEquals(TriggerSource.MANUAL_SCREEN_ANALYSIS, request.triggerSource)
        assertTrue(request.isManual)
    }

    @Test
    fun manualRequest_waitsForActiveCaptureAndRunsAfterRelease() {
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { 5_000L }
        )
        orchestrator.onSignal(
            RadarSignal.UberFloatingOverOtherApp(
                dominantPackage = "com.app99.driver",
                floatingPackage = "com.ubercab.driver",
                floatingBounds = "901 392 1080 571",
                floatingCoverage = 0.013,
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                eventReceivedAtMs = 5_000L,
                signalEmittedAtMs = 5_010L
            )
        )
        val epoch = AnalysisEpochController.bumpManualEpoch()
        orchestrator.requestManualAnalysis(
            ManualAnalysisContext(
                requestedAtMs = 5_100L,
                analysisEpoch = epoch,
                source = "test",
                dominantPackage = "com.ubercab.driver",
                floatingPackage = "com.app99.driver",
                floatingBounds = "0 0 100 100",
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                platformHint = PlatformHint.UBER
            )
        )

        assertEquals(1, capturer.requests.size)
        capturer.finish(0, ScreenshotFinishStatus.SUCCESS)

        assertEquals(2, capturer.requests.size)
        assertEquals(TriggerSource.MANUAL_SCREEN_ANALYSIS, capturer.requests[1].triggerSource)
    }

    @Test
    fun manualRequest_replacesPendingAutomaticCapture() {
        var nowMs = 6_000L
        val capturer = RecordingScreenshotCapturer()
        val orchestrator = RadarCaptureOrchestrator(
            screenshotCapturer = capturer,
            clock = RadarClock { nowMs }
        )
        orchestrator.onSignal(
            RadarSignal.UberFloatingOverOtherApp(
                dominantPackage = "com.app99.driver",
                floatingPackage = "com.ubercab.driver",
                floatingBounds = "901 392 1080 571",
                floatingCoverage = 0.013,
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                eventReceivedAtMs = 6_000L,
                signalEmittedAtMs = 6_010L
            )
        )
        nowMs = 6_500L
        orchestrator.onSignal(
            RadarSignal.UberFloatingOverOtherApp(
                dominantPackage = "com.app99.driver",
                floatingPackage = "com.ubercab.driver",
                floatingBounds = "800 300 1080 650",
                floatingCoverage = 0.021,
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                eventReceivedAtMs = 6_500L,
                signalEmittedAtMs = 6_510L
            )
        )
        val epoch = AnalysisEpochController.bumpManualEpoch()
        orchestrator.requestManualAnalysis(
            ManualAnalysisContext(
                requestedAtMs = 6_600L,
                analysisEpoch = epoch,
                source = "test",
                dominantPackage = "com.ubercab.driver",
                floatingPackage = "com.app99.driver",
                floatingBounds = "0 0 100 100",
                floatingKind = FloatingWindowKind.FLOATING_BUBBLE,
                platformHint = PlatformHint.UBER
            )
        )

        capturer.finish(0, ScreenshotFinishStatus.SUCCESS)

        assertEquals(2, capturer.requests.size)
        assertEquals(TriggerSource.MANUAL_SCREEN_ANALYSIS, capturer.requests[1].triggerSource)
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
        dominantPackage: String = "com.ubercab.driver",
        nodeTreePackage: String = "com.ubercab.driver",
        nodeCount: Int = 18,
        visibleTextNodeCount: Int = 3,
        bottomHalfTextNodeCount: Int = visibleTextNodeCount,
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
        dominantPackage = dominantPackage,
        floatingPackage = floatingPackage,
        nodeTreePackage = nodeTreePackage,
        nodeCount = nodeCount,
        visibleTextNodeCount = visibleTextNodeCount,
        bottomHalfTextNodeCount = bottomHalfTextNodeCount,
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
        private val finishedCallbacks = mutableListOf<(CaptureRequest, ScreenshotFinishStatus) -> Unit>()

        override fun capture(
            request: CaptureRequest,
            onSuccess: (CaptureRequest, ScreenshotCaptureResult) -> Unit,
            onFailure: (CaptureRequest, String, Int?, Long, Long, ScreenshotFinishStatus) -> Unit,
            onFinished: (CaptureRequest, ScreenshotFinishStatus) -> Unit
        ) {
            requests += request
            finishedCallbacks += onFinished
            if (autoFinish) {
                onFinished(request, ScreenshotFinishStatus.SUCCESS)
            }
        }

        fun finish(index: Int, status: ScreenshotFinishStatus) {
            finishedCallbacks[index](requests[index], status)
        }
    }

    private class FakeStabilizationScheduler : StabilizationScheduler {
        private data class Task(
            val runAtMs: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false
        )

        private var nowMs: Long = 0L
        private val tasks = mutableListOf<Task>()

        override fun schedule(delayMs: Long, action: () -> Unit): StabilizationScheduler.CancellationHandle {
            val task = Task(runAtMs = nowMs + delayMs, action = action)
            tasks += task
            return object : StabilizationScheduler.CancellationHandle {
                override fun cancel() {
                    task.cancelled = true
                }
            }
        }

        fun advanceBy(deltaMs: Long) {
            nowMs += deltaMs
            val ready = tasks.filter { !it.cancelled && it.runAtMs <= nowMs }.sortedBy { it.runAtMs }
            tasks.removeAll(ready.toSet())
            ready.forEach { it.action() }
            tasks.removeAll { it.cancelled }
        }
    }
}
