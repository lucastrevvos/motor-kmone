package com.lucastrevvos.kmonemotor.radar.cycle

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import java.text.Normalizer
import java.util.UUID

class OfferCycleClassifier {
    private var lastUberDominantSnapshot: OfferCycleSnapshot? = null
    private var lastUberDominantClassification: OfferCycleClassification? = null

    fun classify(snapshot: OfferCycleSnapshot): OfferCycleClassification {
        if (snapshot.triggerSource != TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC) {
            return OfferCycleClassification(
                kind = OfferCycleKind.UNKNOWN,
                cycleId = UUID.randomUUID().toString(),
                previousCycleId = null,
                reason = "unsupported_trigger_source",
                timeSincePreviousMs = null,
                shouldPreferForOcr = false
            )
        }

        val previousSnapshot = lastUberDominantSnapshot
        val previousClassification = lastUberDominantClassification
        val timeSincePreviousMs = previousSnapshot?.let { snapshot.createdAtMs - it.createdAtMs }

        val classification = when {
            previousSnapshot == null || previousClassification == null -> buildNewCycle(
                previousClassification = null,
                timeSincePreviousMs = null,
                reason = "no_previous_cycle"
            )
            isPostTransition(snapshot, timeSincePreviousMs) -> OfferCycleClassification(
                kind = OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION,
                cycleId = previousClassification.cycleId,
                previousCycleId = previousClassification.cycleId,
                reason = postTransitionReason(snapshot, timeSincePreviousMs),
                timeSincePreviousMs = timeSincePreviousMs,
                shouldPreferForOcr = false
            )
            isFollowupWithinOfferLifetime(snapshot, timeSincePreviousMs) -> OfferCycleClassification(
                kind = OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP,
                cycleId = previousClassification.cycleId,
                previousCycleId = previousClassification.cycleId,
                reason = followupReasonWithinOfferLifetime(snapshot),
                timeSincePreviousMs = timeSincePreviousMs,
                shouldPreferForOcr = false
            )
            isWithinOfferLifetime(timeSincePreviousMs) -> OfferCycleClassification(
                kind = OfferCycleKind.UNKNOWN,
                cycleId = previousClassification.cycleId,
                previousCycleId = previousClassification.cycleId,
                reason = "within_offer_lifetime_uncertain",
                timeSincePreviousMs = timeSincePreviousMs,
                shouldPreferForOcr = false
            )
            isFollowupNearBoundary(snapshot, timeSincePreviousMs) -> OfferCycleClassification(
                kind = OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP,
                cycleId = previousClassification.cycleId,
                previousCycleId = previousClassification.cycleId,
                reason = "near_boundary_offer_followup",
                timeSincePreviousMs = timeSincePreviousMs,
                shouldPreferForOcr = false
            )
            isNearBoundary(timeSincePreviousMs) -> OfferCycleClassification(
                kind = OfferCycleKind.UNKNOWN,
                cycleId = previousClassification.cycleId,
                previousCycleId = previousClassification.cycleId,
                reason = "near_boundary_uncertain",
                timeSincePreviousMs = timeSincePreviousMs,
                shouldPreferForOcr = false
            )
            timeSincePreviousMs != null && timeSincePreviousMs > AUTO_NEW_CYCLE_TIMEOUT_MS -> buildNewCycle(
                previousClassification = previousClassification,
                timeSincePreviousMs = timeSincePreviousMs,
                reason = "previous_cycle_older_than_20s"
            )
            else -> OfferCycleClassification(
                kind = OfferCycleKind.UNKNOWN,
                cycleId = previousClassification.cycleId,
                previousCycleId = previousClassification.cycleId,
                reason = "near_boundary_uncertain",
                timeSincePreviousMs = timeSincePreviousMs,
                shouldPreferForOcr = false
            )
        }

        lastUberDominantSnapshot = snapshot
        lastUberDominantClassification = classification
        return classification
    }

    fun overrideLastUberDominantClassification(classification: OfferCycleClassification) {
        lastUberDominantClassification = classification
    }

    private fun buildNewCycle(
        previousClassification: OfferCycleClassification?,
        timeSincePreviousMs: Long?,
        reason: String
    ) = OfferCycleClassification(
        kind = OfferCycleKind.NEW_OFFER_CYCLE,
        cycleId = UUID.randomUUID().toString(),
        previousCycleId = previousClassification?.cycleId,
        reason = reason,
        timeSincePreviousMs = timeSincePreviousMs,
        shouldPreferForOcr = true
    )

    private fun isWithinOfferLifetime(timeSincePreviousMs: Long?): Boolean {
        return timeSincePreviousMs != null && timeSincePreviousMs <= OFFER_LIFETIME_MS
    }

    private fun isNearBoundary(timeSincePreviousMs: Long?): Boolean {
        return timeSincePreviousMs != null &&
            timeSincePreviousMs > OFFER_LIFETIME_MS &&
            timeSincePreviousMs <= POST_TRANSITION_TIMEOUT_MS
    }

    private fun isFollowupWithinOfferLifetime(
        current: OfferCycleSnapshot,
        timeSincePreviousMs: Long?
    ): Boolean {
        if (!isWithinOfferLifetime(timeSincePreviousMs)) {
            return false
        }
        return current.numericTextNodeCount >= 3 ||
            current.buttonLikeNodeCount >= 1 ||
            "numeric_text_with_visible_text" in current.matchedConditions
    }

    private fun followupReasonWithinOfferLifetime(current: OfferCycleSnapshot): String {
        return when {
            current.numericTextNodeCount >= 3 -> "within_offer_lifetime_progress_update"
            current.buttonLikeNodeCount >= 1 -> "within_offer_lifetime_button_progress_update"
            "numeric_text_with_visible_text" in current.matchedConditions -> "within_offer_lifetime_numeric_signal"
            else -> "within_offer_lifetime_uncertain"
        }
    }

    private fun isFollowupNearBoundary(
        current: OfferCycleSnapshot,
        timeSincePreviousMs: Long?
    ): Boolean {
        return isNearBoundary(timeSincePreviousMs) && current.numericTextNodeCount >= 3
    }

    private fun isPostTransition(
        snapshot: OfferCycleSnapshot,
        timeSincePreviousMs: Long?
    ): Boolean {
        val operationalTextPresent = snapshot.knownStateTexts.any { text ->
            val normalized = normalizeText(text)
            OPERATIONAL_TEXT_HINTS.any { hint -> normalized.contains(hint) }
        }
        val lowNumericHighButton = isNearBoundary(timeSincePreviousMs) &&
            snapshot.numericTextNodeCount <= 2 &&
            snapshot.buttonLikeNodeCount >= 3 &&
            snapshot.visibleTextNodeCount >= 6
        return operationalTextPresent || lowNumericHighButton
    }

    private fun postTransitionReason(snapshot: OfferCycleSnapshot, timeSincePreviousMs: Long?): String {
        val operationalTextPresent = snapshot.knownStateTexts.any { text ->
            val normalized = normalizeText(text)
            OPERATIONAL_TEXT_HINTS.any { hint -> normalized.contains(hint) }
        }
        return if (operationalTextPresent) {
            "operational_text_post_transition"
        } else if (isNearBoundary(timeSincePreviousMs)) {
            "low_numeric_high_button_post_transition"
        } else {
            "operational_text_post_transition"
        }
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
    }

    private companion object {
        const val OFFER_LIFETIME_MS = 12_000L
        const val POST_TRANSITION_TIMEOUT_MS = 20_000L
        const val AUTO_NEW_CYCLE_TIMEOUT_MS = 20_000L

        val OPERATIONAL_TEXT_HINTS = listOf(
            "offline",
            "online",
            "procurando",
            "possivel ficar offline",
            "possivel",
            "ficar offline"
        )
    }
}
