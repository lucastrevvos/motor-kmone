package com.lucastrevvos.kmonemotor.radar.ocr

import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbeResult

class OcrRunPolicy(
    private val flags: OcrFlags = OcrFlags()
) {
    fun decide(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult
    ): OcrPolicyDecision {
        val cycleKind = observation.offerCycleClassification?.kind
        val bestCandidate = visualResult.bestCandidate
        return when {
            !flags.enableRegionalOcr -> OcrPolicyDecision(false, "ocr_disabled_by_flag")
            observation.triggerSource == TriggerSource.MANUAL_SCREEN_ANALYSIS && bestCandidate != null ->
                OcrPolicyDecision(true, "ocr_allowed_manual_best_candidate")
            observation.triggerSource == TriggerSource.MANUAL_SCREEN_ANALYSIS ->
                OcrPolicyDecision(false, "ocr_skipped_manual_no_candidate")
            bestCandidate == null -> OcrPolicyDecision(false, "ocr_skipped_no_best_candidate")
            !visualResult.acceptedForOcrFuture -> OcrPolicyDecision(false, "ocr_skipped_visual_not_accepted")
            cycleKind == OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION && !flags.enableOcrOnPostTransition ->
                OcrPolicyDecision(false, "ocr_skipped_post_transition")
            cycleKind == OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP && !flags.enableOcrOnFollowup ->
                OcrPolicyDecision(false, "ocr_skipped_followup_disabled")
            cycleKind == OfferCycleKind.UNKNOWN && !flags.enableOcrOnUnknown ->
                OcrPolicyDecision(false, "ocr_skipped_unknown_disabled")
            cycleKind == OfferCycleKind.NEW_OFFER_CYCLE -> OcrPolicyDecision(true, "ocr_allowed_new_offer_cycle")
            cycleKind == OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP -> OcrPolicyDecision(true, "ocr_allowed_followup_diagnostic")
            cycleKind == OfferCycleKind.UNKNOWN || cycleKind == null -> OcrPolicyDecision(true, "ocr_allowed_unknown_diagnostic")
            cycleKind == OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION -> OcrPolicyDecision(true, "ocr_allowed_post_transition")
            else -> OcrPolicyDecision(false, "ocr_skipped_unknown_policy_state")
        }
    }

    fun decideManualFallback(fallbackKind: CropKind?): OcrPolicyDecision {
        return when (fallbackKind) {
            CropKind.CENTER_CARD_AREA -> OcrPolicyDecision(true, "ocr_allowed_manual_fallback_center_card")
            CropKind.LOWER_HALF -> OcrPolicyDecision(true, "ocr_allowed_manual_fallback_lower_half")
            else -> OcrPolicyDecision(false, "ocr_skipped_manual_no_candidate")
        }
    }

    data class OcrPolicyDecision(
        val shouldRun: Boolean,
        val reason: String
    )

    data class OcrFlags(
        val enableRegionalOcr: Boolean = RadarFeatureFlags.ENABLE_REGIONAL_OCR,
        val enableOcrOnFollowup: Boolean = RadarFeatureFlags.ENABLE_OCR_ON_FOLLOWUP,
        val enableOcrOnUnknown: Boolean = RadarFeatureFlags.ENABLE_OCR_ON_UNKNOWN,
        val enableOcrOnPostTransition: Boolean = RadarFeatureFlags.ENABLE_OCR_ON_POST_TRANSITION
    )
}
