package com.lucastrevvos.kmonemotor.radar.manual

import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import com.lucastrevvos.kmonemotor.radar.vision.VisualOfferProbeResult

class ManualAnalysisPlanner {
    fun selectPrimaryCandidate(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>
    ): ManualCandidateSelection? {
        if (observation.triggerSource != com.lucastrevvos.kmonemotor.radar.core.TriggerSource.MANUAL_SCREEN_ANALYSIS) {
            return visualResult.bestCandidate?.let {
                ManualCandidateSelection(it, "automatic_best_candidate")
            }
        }
        if (visualResult.acceptedForOcrFuture && visualResult.bestCandidate != null) {
            return ManualCandidateSelection(visualResult.bestCandidate, "manual_best_candidate")
        }
        return candidates.firstOrNull { it.kind == CropKind.CENTER_CARD_AREA && it.width >= 40 && it.height >= 40 }
            ?.let { ManualCandidateSelection(it, "manual_fallback_center_card") }
            ?: candidates.firstOrNull { it.kind == CropKind.LOWER_HALF && it.width >= 40 && it.height >= 40 }
                ?.let { ManualCandidateSelection(it, "manual_fallback_lower_half") }
    }

    fun selectSecondaryCandidate(
        primary: CropCandidate?,
        fingerprintKind: OfferTextFingerprintKind?,
        candidates: List<CropCandidate>
    ): ManualCandidateSelection? {
        if (fingerprintKind == OfferTextFingerprintKind.OFFER_LIKE) {
            return null
        }
        return candidates.firstOrNull {
            it.kind == CropKind.LOWER_HALF &&
                it.id != primary?.id &&
                it.width >= 40 &&
                it.height >= 40
        }?.let { ManualCandidateSelection(it, "manual_fallback_lower_half") }
    }
}

data class ManualCandidateSelection(
    val candidate: CropCandidate,
    val reason: String
)
