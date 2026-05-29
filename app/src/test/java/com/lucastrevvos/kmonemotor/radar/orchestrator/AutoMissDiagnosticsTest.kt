package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMissDiagnosticsTest {
    private val diagnostics = AutoMissDiagnostics(lookbackMs = 15_000L, retentionMs = 30_000L)

    @Test
    fun manualSuccessWithoutRecentAutoAttempts_reportsNoAutoAttempt() {
        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-1",
            manualPlatform = "UBER",
            manualPrice = 5.5,
            manualDistances = "1.3km,1.4km",
            manualTimes = "3min,4min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("no_auto_attempt_before_manual", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessAfterOperationalRejection_reportsOperationalCause() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 10_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "map_eta_range_without_offer_evidence",
                isOperationalScreen = true,
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE,
                knownStateTexts = listOf("1-15 min", "1-11 min", "1-5 min")
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 10_000L,
                triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
                stage = "watchdog_started",
                reason = "map_eta_range_without_offer_evidence",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-2",
            manualPlatform = "UBER",
            manualPrice = 16.67,
            manualDistances = "5.4km,9.9km",
            manualTimes = "8min,22min",
            manualSelectedCropKind = "FLOATING_BOUNDS_EXPANDED",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("no_card_signal_after_pre_offer_state", diagnosis.likelyCause)
        assertEquals("FLOATING_BOUNDS_EXPANDED", diagnosis.manualSelectedCropKind)
        assertEquals("map_eta_range_without_offer_evidence", diagnosis.lastPreOfferReason)
        assertEquals(10_000L, diagnosis.lastPreOfferAgeMs)
        assertTrue(diagnosis.watchdogStartedAfterPreOffer)
    }

    @Test
    fun manualSuccessAfterStabilizationCancelled_reportsCancellation() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 10_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "stabilization_cancelled",
                reason = "operational_screen_detected"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-3",
            manualPlatform = "UBER",
            manualPrice = 10.3,
            manualDistances = "5.4km,6.7km",
            manualTimes = "7min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("stabilization_cancelled", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessAfterUnknownFingerprint_reportsCapturedButUnknown() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 10_000L,
                triggerSource = TriggerSource.UBER_AUTO_BURST_RECOVERY,
                stage = "capture_approved"
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 10_200L,
                triggerSource = TriggerSource.UBER_AUTO_BURST_RECOVERY,
                stage = "fingerprint_result",
                fingerprintKind = "UNKNOWN"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-4",
            manualPlatform = "UBER",
            manualPrice = 18.41,
            manualDistances = "5.6km,11.7km",
            manualTimes = "7min,17min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("captured_but_ocr_unknown", diagnosis.likelyCause)
    }

    @Test
    fun oldEventsOutsideLookbackAreIgnored() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 1_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "map_eta_range_without_offer_evidence",
                isOperationalScreen = true
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-5",
            manualPlatform = "UBER",
            manualPrice = 12.0,
            manualDistances = "2.2km,6.3km",
            manualTimes = "4min,9min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("no_auto_attempt_before_manual", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessSoonAfterOperationalRejection_reportsOperationalCause() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 18_500L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "operational_earnings_money_without_offer_evidence",
                isOperationalScreen = true,
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-6",
            manualPlatform = "UBER",
            manualPrice = 8.34,
            manualDistances = "1.7km,4.0km",
            manualTimes = "6min,9min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("rejected_as_operational_screen", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessAfterStaleOperationalRejectionWithoutWatchdog_reportsWatchdogNotStartedAfterStaleOperationalState() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 11_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "operational_earnings_money_without_offer_evidence",
                isOperationalScreen = true,
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE,
                knownStateTexts = listOf("+R$ 6,25", "1-8min", "1-4min")
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-6-stale",
            manualPlatform = "UBER",
            manualPrice = 7.14,
            manualDistances = "0.4km,4.0km",
            manualTimes = "2min,9min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("watchdog_not_started_after_stale_operational_state", diagnosis.likelyCause)
        assertEquals(9_000L, diagnosis.lastOperationalRejectionAgeMs)
        assertEquals("operational_earnings_money_without_offer_evidence", diagnosis.staleOperationalReason)
    }

    @Test
    fun manualSuccessAfterStaleOperationalWatchdogFailure_reportsWatchdogFailedAfterStaleOperationalState() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 11_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "stale_operational_earnings_probe_candidate",
                isOperationalScreen = true,
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE,
                knownStateTexts = listOf("+R$ 19,50", "1-19min", "1-4min")
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 11_100L,
                triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
                stage = "watchdog_started",
                reason = "stale_operational_earnings_probe_candidate",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_200L,
                triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
                stage = "pipeline_final",
                fingerprintKind = "UNKNOWN",
                persistReason = "watchdog_non_offer"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-6-stale-watchdog",
            manualPlatform = "UBER",
            manualPrice = 21.39,
            manualDistances = "1.6km,18.6km",
            manualTimes = "4min,18min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("watchdog_failed_after_stale_operational_state", diagnosis.likelyCause)
        assertTrue(diagnosis.watchdogStartedAfterPreOffer)
        assertEquals("stale_operational_earnings_probe_candidate", diagnosis.lastPreOfferReason)
    }

    @Test
    fun manualSuccessAfterWeakTreeDeltaWithoutWatchdog_reportsWatchdogNotStartedAfterWeakTreeDelta() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 11_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "weak_tree_delta_visual_probe_candidate",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE,
                treeScore = 2,
                hasOfferPriceText = false,
                hasUberProductText = false,
                hasRoutePairText = false
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-weak-tree",
            manualPlatform = "UBER",
            manualPrice = 10.10,
            manualDistances = "6.0km,3.8km",
            manualTimes = "8min,6min",
            manualSelectedCropKind = "CENTER_CARD_AREA",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("watchdog_not_started_after_weak_tree_delta", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessAfterWeakTreeDeltaWatchdogFailure_reportsWatchdogFailedAfterWeakTreeDelta() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 11_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "weak_tree_delta_visual_probe_candidate",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 11_100L,
                triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
                stage = "watchdog_started",
                reason = "weak_tree_delta_visual_probe_candidate",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_200L,
                triggerSource = TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
                stage = "pipeline_final",
                fingerprintKind = "UNKNOWN",
                persistReason = "watchdog_non_offer"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-weak-tree-watchdog",
            manualPlatform = "UBER",
            manualPrice = 10.10,
            manualDistances = "6.0km,3.8km",
            manualTimes = "8min,6min",
            manualSelectedCropKind = "CENTER_CARD_AREA",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("watchdog_failed_after_weak_tree_delta", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessAfterSearchingDisappearedEmptyTreeProbeCandidate_reportsNoCardSignalAfterPreOffer() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 10_500L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "searching_disappeared_empty_tree_probe_candidate",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE,
                knownStateTexts = emptyList()
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-6b",
            manualPlatform = "UBER",
            manualPrice = 31.20,
            manualDistances = "0.3km,27.3km",
            manualTimes = "3min,29min",
            manualSelectedCropKind = "FLOATING_BOUNDS_EXPANDED",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("no_card_signal_after_pre_offer_state", diagnosis.likelyCause)
    }

    @Test
    fun manualSuccessAfterMapEtaPreOfferWithoutWatchdog_reportsWatchdogNotStarted() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_000L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "trigger_rejected_pre_offer",
                reason = "map_eta_range_without_offer_evidence",
                state = RadarAutoCaptureState.PRE_OFFER_MAP_STATE,
                knownStateTexts = listOf("1-10 min", "1-8 min", "1-9 min", "1-6 min")
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-6c",
            manualPlatform = "UBER",
            manualPrice = 17.69,
            manualDistances = "6.0km,9.2km",
            manualTimes = "8min,18min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("watchdog_not_started_after_map_eta_pre_offer", diagnosis.likelyCause)
        assertEquals("map_eta_range_without_offer_evidence", diagnosis.lastPreOfferReason)
        assertTrue(!diagnosis.watchdogStartedAfterPreOffer)
    }

    @Test
    fun manualSuccessAfterCaptureApprovedAndUnknown_reportsCapturedButUnknown() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 17_500L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "capture_approved"
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 17_900L,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                stage = "fingerprint_result",
                fingerprintKind = "UNKNOWN"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-7",
            manualPlatform = "UBER",
            manualPrice = 6.15,
            manualDistances = "1.7km,1.8km",
            manualTimes = "5min,5min",
            manualSelectedCropKind = "FLOATING_BOUNDS_EXPANDED",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("captured_but_ocr_unknown", diagnosis.likelyCause)
        assertEquals("FLOATING_BOUNDS_EXPANDED", diagnosis.manualSelectedCropKind)
    }

    @Test
    fun manualNinetyNineSuccessAfterRecentSignalsWithoutCapture_reportsSignalNotRouted() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_000L,
                triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
                stage = "ninety_nine_signal_emitted",
                reason = "tree_structure_changed",
                nodeCount = 11,
                visibleTextNodeCount = 0
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_100L,
                triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
                stage = "offer_card_signal_rejected",
                reason = "visible_text_node_count_zero",
                nodeCount = 11,
                visibleTextNodeCount = 0
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-99-1",
            manualPlatform = "NINETY_NINE",
            manualPrice = 31.37,
            manualDistances = "2.3km,20.4km",
            manualTimes = "9min,35min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("ninety_nine_signal_not_routed_to_capture", diagnosis.likelyCause)
        assertTrue(diagnosis.recent99SignalCount > 0)
        assertEquals(11, diagnosis.last99SignalNodeCount)
        assertEquals(0, diagnosis.last99SignalVisibleTextNodeCount)
    }

    @Test
    fun manualNinetyNineSuccessAfterProbeNoValidCropWithoutRetry_reportsRetryNotStarted() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_000L,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "capture_approved",
                reason = "tree_structure_changed_without_text"
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_100L,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "vision_no_valid_crop_candidate",
                reason = "no_valid_crop_candidate"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-99-2",
            manualPlatform = "NINETY_NINE",
            manualPrice = 20.70,
            manualDistances = "2.3km,20.4km",
            manualTimes = "9min,35min",
            manualSelectedCropKind = "CENTER_CARD_AREA",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("ninety_nine_probe_retry_not_started", diagnosis.likelyCause)
    }

    @Test
    fun manualNinetyNineSuccessAfterRetryEligibleFailureWithoutRetry_reportsRetryNotStarted() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_000L,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "pipeline_final",
                reason = "fingerprint_not_offer_like",
                persistReason = "fingerprint_not_offer_like"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-99-3",
            manualPlatform = "NINETY_NINE",
            manualPrice = 10.10,
            manualDistances = "4.4km,5.6km",
            manualTimes = "11min,10min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("ninety_nine_probe_retry_not_started", diagnosis.likelyCause)
    }

    @Test
    fun manualNinetyNineSuccessAfterRetryFailure_reportsRetryFailed() {
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_000L,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "pipeline_final",
                reason = "fingerprint_not_offer_like",
                persistReason = "fingerprint_not_offer_like"
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 12_800L,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "retry_started",
                reason = "no_valid_crop_candidate_or_unknown"
            )
        )
        diagnostics.recordAutoTrace(
            AutoAttemptTrace(
                timestampMs = 13_100L,
                triggerSource = TriggerSource.NINETY_NINE_VISUAL_PROBE,
                stage = "retry_result_failed",
                reason = "fingerprint_not_offer_like",
                persistReason = "fingerprint_not_offer_like"
            )
        )

        val diagnosis = diagnostics.reportManualOracleSuccess(
            manualObservationId = "manual-99-4",
            manualPlatform = "NINETY_NINE",
            manualPrice = 10.10,
            manualDistances = "4.4km,5.6km",
            manualTimes = "11min,10min",
            manualSelectedCropKind = "LOWER_HALF",
            manualTriggerSource = "MANUAL_SCREEN_ANALYSIS",
            timestampMs = 20_000L
        )

        assertEquals("ninety_nine_probe_retry_failed", diagnosis.likelyCause)
    }
}
