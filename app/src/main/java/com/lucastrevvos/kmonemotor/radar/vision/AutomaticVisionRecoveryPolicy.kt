package com.lucastrevvos.kmonemotor.radar.vision

import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation

class AutomaticVisionRecoveryPolicy(
    private val config: Config = Config()
) {
    private val attemptedObservationIds = linkedSetOf<String>()
    private var lastRecoveryAtMs: Long = Long.MIN_VALUE

    fun decide(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>,
        nowMs: Long
    ): AutomaticVisionRecoveryDecision {
        val eligibleTrigger = when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC -> true
            else -> false
        }
        if (!eligibleTrigger || observation.isManual) {
            return AutomaticVisionRecoveryDecision(
                reason = "not_eligible_trigger",
                suppressionReason = "not_eligible_trigger"
            )
        }

        val shouldRecoverNoCandidate = visualResult.bestCandidate == null &&
            visualResult.reason == "no_valid_crop_candidate"
        val shouldRecoverVisualNotAccepted = visualResult.bestCandidate != null &&
            !visualResult.acceptedForOcrFuture
        val shouldOverridePostTransition = observation.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC &&
            observation.offerCycleClassification?.kind == OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION &&
            visualResult.bestCandidate != null &&
            (
                visualResult.reason == "accepted_by_strong_uber_dominant_signal" ||
                    visualResult.visualFallbackApplied
            ) &&
            nowMs - observation.requestCreatedAtMs <= config.autoPostTransitionOverrideMaxAgeMs

        if (!shouldRecoverNoCandidate && !shouldRecoverVisualNotAccepted && !shouldOverridePostTransition) {
            return AutomaticVisionRecoveryDecision(
                reason = "not_needed",
                suppressionReason = "not_needed"
            )
        }

        if (attemptedObservationIds.contains(observation.id)) {
            return AutomaticVisionRecoveryDecision(
                reason = "attempt_limit_reached",
                suppressionReason = "attempt_limit_reached"
            )
        }

        if (lastRecoveryAtMs != Long.MIN_VALUE && nowMs - lastRecoveryAtMs < config.autoRecoveryCooldownMs) {
            return AutomaticVisionRecoveryDecision(
                reason = "cooldown_suppressed",
                suppressionReason = "cooldown_suppressed"
            )
        }

        val fallbackCandidate = when {
            shouldRecoverNoCandidate -> chooseFallbackCandidate(observation, candidates)
            else -> null
        }
        val selectedCandidate = fallbackCandidate ?: visualResult.bestCandidate
        if (shouldRecoverNoCandidate && selectedCandidate == null) {
            return AutomaticVisionRecoveryDecision(
                reason = "candidate_missing",
                suppressionReason = "candidate_missing"
            )
        }

        val forceOcr = shouldRecoverNoCandidate || shouldRecoverVisualNotAccepted || shouldOverridePostTransition
        if (!forceOcr) {
            return AutomaticVisionRecoveryDecision(
                reason = "not_needed",
                suppressionReason = "not_needed"
            )
        }

        attemptedObservationIds += observation.id
        while (attemptedObservationIds.size > config.maxRecoveryAttemptsTracked) {
            attemptedObservationIds.remove(attemptedObservationIds.first())
        }
        lastRecoveryAtMs = nowMs

        val reason = when {
            shouldOverridePostTransition -> "ocr_allowed_auto_post_transition_override"
            shouldRecoverNoCandidate && selectedCandidate?.kind == CropKind.LOWER_HALF ->
                "ocr_allowed_auto_recovery_lower_half"
            shouldRecoverNoCandidate && selectedCandidate?.kind == CropKind.CENTER_CARD_AREA ->
                "ocr_allowed_auto_recovery_center_card"
            shouldRecoverNoCandidate -> "ocr_allowed_auto_recovery_candidate"
            shouldRecoverVisualNotAccepted -> "ocr_allowed_auto_recovery_visual_not_accepted"
            else -> "ocr_allowed_auto_recovery"
        }

        return AutomaticVisionRecoveryDecision(
            shouldForceOcr = true,
            selectedCandidate = selectedCandidate,
            reason = reason,
            overridePostTransition = shouldOverridePostTransition,
            recoveryApplied = shouldRecoverNoCandidate || shouldRecoverVisualNotAccepted,
            forcedAcceptance = shouldRecoverNoCandidate || shouldRecoverVisualNotAccepted
        )
    }

    private fun chooseFallbackCandidate(
        observation: ScreenObservation,
        candidates: List<CropCandidate>
    ): CropCandidate? {
        val orderedKinds = when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE
            )
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC -> listOf(
                CropKind.CENTER_CARD_AREA,
                CropKind.LOWER_HALF
            )
            else -> emptyList()
        }
        return orderedKinds.asSequence()
            .mapNotNull { kind ->
                candidates.firstOrNull { candidate ->
                    candidate.kind == kind && candidate.width >= config.minCandidateSizePx && candidate.height >= config.minCandidateSizePx
                }
            }
            .firstOrNull()
    }

    data class AutomaticVisionRecoveryDecision(
        val shouldForceOcr: Boolean = false,
        val selectedCandidate: CropCandidate? = null,
        val reason: String,
        val overridePostTransition: Boolean = false,
        val recoveryApplied: Boolean = false,
        val forcedAcceptance: Boolean = false,
        val suppressionReason: String? = null
    )

    data class Config(
        val autoPostTransitionOverrideMaxAgeMs: Long = RadarFeatureFlags.AUTO_POST_TRANSITION_OVERRIDE_MAX_AGE_MS,
        val autoRecoveryCooldownMs: Long = RadarFeatureFlags.AUTO_RECOVERY_COOLDOWN_MS,
        val minCandidateSizePx: Int = 40,
        val maxRecoveryAttemptsTracked: Int = 64
    )
}
