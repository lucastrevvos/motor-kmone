package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
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
}
