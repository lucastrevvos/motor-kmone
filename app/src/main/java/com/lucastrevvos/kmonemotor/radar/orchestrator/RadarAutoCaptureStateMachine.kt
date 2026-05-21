package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

enum class RadarAutoCaptureState {
    IDLE,
    PRE_OFFER_MAP_STATE,
    OFFER_CARD_CANDIDATE,
    OFFER_CARD_STABILIZING,
    OFFER_CARD_CONFIRMED,
    CAPTURE_IN_PROGRESS,
    OFFER_CAPTURED,
    POST_OFFER_COOLDOWN
}

data class RadarAutoCaptureEvidence(
    val triggerSource: TriggerSource,
    val hasPriceText: Boolean,
    val hasUberProductText: Boolean,
    val hasRoutePairText: Boolean,
    val hasSearchingText: Boolean,
    val treeScore: Int,
    val matchedConditions: List<String>,
    val knownStateTexts: List<String>,
    val timestampMs: Long
) {
    fun isStrongOfferEvidence(): Boolean {
        val pairedSignals = listOf(hasPriceText, hasUberProductText, hasRoutePairText).count { it }
        return pairedSignals >= 2 || treeScore >= 7
    }

    fun isSearchingEvidence(): Boolean {
        return hasSearchingText && !hasPriceText && !hasUberProductText && !hasRoutePairText
    }
}

data class RadarAutoCaptureTransition(
    val from: RadarAutoCaptureState,
    val to: RadarAutoCaptureState,
    val reason: String,
    val evidence: RadarAutoCaptureEvidence? = null
)

data class RadarAutoCaptureStateSnapshot(
    val state: RadarAutoCaptureState,
    val lastStrongEvidenceAtMs: Long?,
    val lastSearchingEvidenceAtMs: Long?,
    val pendingStabilization: Boolean,
    val cooldownUntilMs: Long?
)

data class AutoCapturePipelineResult(
    val triggerSource: TriggerSource,
    val fingerprintKind: String?,
    val wasPersisted: Boolean,
    val finalReason: String?,
    val timestampMs: Long
)

class RadarAutoCaptureStateMachine(
    private val cooldownMs: Long = 2_000L
) {
    var state: RadarAutoCaptureState = RadarAutoCaptureState.IDLE
        private set

    private var lastStrongEvidenceAtMs: Long? = null
    private var lastSearchingEvidenceAtMs: Long? = null
    private var pendingStabilization: Boolean = false
    private var cooldownUntilMs: Long? = null

    fun addEvidence(evidence: RadarAutoCaptureEvidence) {
        if (evidence.isStrongOfferEvidence()) {
            lastStrongEvidenceAtMs = evidence.timestampMs
        }
        if (evidence.isSearchingEvidence()) {
            lastSearchingEvidenceAtMs = evidence.timestampMs
        }
    }

    fun transitionToPreOffer(reason: String, evidence: RadarAutoCaptureEvidence? = null): RadarAutoCaptureTransition? {
        pendingStabilization = false
        cooldownUntilMs = if (state == RadarAutoCaptureState.POST_OFFER_COOLDOWN) cooldownUntilMs else null
        return transitionTo(RadarAutoCaptureState.PRE_OFFER_MAP_STATE, reason, evidence)
    }

    fun transitionToCandidate(reason: String, evidence: RadarAutoCaptureEvidence): RadarAutoCaptureTransition? {
        return transitionTo(RadarAutoCaptureState.OFFER_CARD_CANDIDATE, reason, evidence)
    }

    fun transitionToStabilizing(reason: String, evidence: RadarAutoCaptureEvidence): RadarAutoCaptureTransition? {
        pendingStabilization = true
        return transitionTo(RadarAutoCaptureState.OFFER_CARD_STABILIZING, reason, evidence)
    }

    fun transitionToConfirmed(reason: String, evidence: RadarAutoCaptureEvidence): RadarAutoCaptureTransition? {
        pendingStabilization = false
        return transitionTo(RadarAutoCaptureState.OFFER_CARD_CONFIRMED, reason, evidence)
    }

    fun transitionToCaptureInProgress(reason: String, evidence: RadarAutoCaptureEvidence): RadarAutoCaptureTransition? {
        return transitionTo(RadarAutoCaptureState.CAPTURE_IN_PROGRESS, reason, evidence)
    }

    fun onPipelineFinished(result: AutoCapturePipelineResult): List<RadarAutoCaptureTransition> {
        if (result.triggerSource != TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC) {
            return emptyList()
        }
        val transitions = mutableListOf<RadarAutoCaptureTransition>()
        if (result.wasPersisted && result.fingerprintKind == "OFFER_LIKE") {
            transitionTo(RadarAutoCaptureState.OFFER_CAPTURED, "offer_like_persisted")?.let {
                transitions += it
            }
            cooldownUntilMs = result.timestampMs + cooldownMs
            transitionTo(RadarAutoCaptureState.POST_OFFER_COOLDOWN, "cooldown_started")?.let {
                transitions += it
            }
        } else {
            pendingStabilization = false
            cooldownUntilMs = null
            transitionTo(RadarAutoCaptureState.PRE_OFFER_MAP_STATE, "capture_result_map_or_unknown")?.let {
                transitions += it
            }
        }
        return transitions
    }

    fun expireCooldownIfNeeded(nowMs: Long): RadarAutoCaptureTransition? {
        val until = cooldownUntilMs ?: return null
        if (state == RadarAutoCaptureState.POST_OFFER_COOLDOWN && nowMs >= until) {
            cooldownUntilMs = null
            return transitionTo(RadarAutoCaptureState.IDLE, "cooldown_finished")
        }
        return null
    }

    fun shouldBlockAutomaticCapture(nowMs: Long): Boolean {
        return state == RadarAutoCaptureState.POST_OFFER_COOLDOWN &&
            cooldownUntilMs?.let { nowMs < it } == true
    }

    fun snapshot(): RadarAutoCaptureStateSnapshot {
        return RadarAutoCaptureStateSnapshot(
            state = state,
            lastStrongEvidenceAtMs = lastStrongEvidenceAtMs,
            lastSearchingEvidenceAtMs = lastSearchingEvidenceAtMs,
            pendingStabilization = pendingStabilization,
            cooldownUntilMs = cooldownUntilMs
        )
    }

    private fun transitionTo(
        newState: RadarAutoCaptureState,
        reason: String,
        evidence: RadarAutoCaptureEvidence? = null
    ): RadarAutoCaptureTransition? {
        val previous = state
        state = newState
        return if (previous == newState) {
            null
        } else {
            RadarAutoCaptureTransition(
                from = previous,
                to = newState,
                reason = reason,
                evidence = evidence
            )
        }
    }
}
