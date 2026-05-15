package com.lucastrevvos.kmonemotor.radar.cycle

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferCycleClassifierTest {
    @Test
    fun firstCycle_isNewOfferCycle() {
        val classifier = OfferCycleClassifier()

        val result = classifier.classify(snapshot(createdAtMs = 1_000L))

        assertEquals(OfferCycleKind.NEW_OFFER_CYCLE, result.kind)
        assertEquals("no_previous_cycle", result.reason)
        assertTrue(result.shouldPreferForOcr)
    }

    @Test
    fun numericHighAt7316ms_isFollowupNotNew() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L, numericTextNodeCount = 7))

        val result = classifier.classify(snapshot(createdAtMs = 8_316L, numericTextNodeCount = 6))

        assertEquals(OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP, result.kind)
        assertNotEquals(OfferCycleKind.NEW_OFFER_CYCLE, result.kind)
        assertEquals("within_offer_lifetime_progress_update", result.reason)
    }

    @Test
    fun numericHighAt9963ms_isFollowupNotNew() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L, numericTextNodeCount = 14))

        val result = classifier.classify(snapshot(createdAtMs = 10_963L, numericTextNodeCount = 9))

        assertEquals(OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP, result.kind)
        assertNotEquals(OfferCycleKind.NEW_OFFER_CYCLE, result.kind)
    }

    @Test
    fun nodeShiftAt11363ms_isNotNew() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L, nodeCount = 60, numericTextNodeCount = 1, buttonLikeNodeCount = 0, matchedConditions = emptyList()))

        val result = classifier.classify(
            snapshot(
                createdAtMs = 12_363L,
                nodeCount = 120,
                numericTextNodeCount = 1,
                buttonLikeNodeCount = 0,
                matchedConditions = emptyList()
            )
        )

        assertNotEquals(OfferCycleKind.NEW_OFFER_CYCLE, result.kind)
        assertTrue(result.kind == OfferCycleKind.UNKNOWN || result.kind == OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP)
    }

    @Test
    fun numericShiftAt12590ms_isNotNew() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L, numericTextNodeCount = 7))

        val result = classifier.classify(snapshot(createdAtMs = 13_590L, numericTextNodeCount = 12))

        assertEquals(OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP, result.kind)
        assertEquals("near_boundary_offer_followup", result.reason)
        assertNotEquals(OfferCycleKind.NEW_OFFER_CYCLE, result.kind)
    }

    @Test
    fun operationalTextWithin12s_isPostTransition() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L))

        val result = classifier.classify(
            snapshot(
                createdAtMs = 8_000L,
                numericTextNodeCount = 1,
                buttonLikeNodeCount = 1,
                visibleTextNodeCount = 3,
                knownStateTexts = listOf("voce esta offline")
            )
        )

        assertEquals(OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION, result.kind)
        assertEquals("operational_text_post_transition", result.reason)
    }

    @Test
    fun lowNumericHighButtonWithin20s_isPostTransition() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L, numericTextNodeCount = 7))

        val result = classifier.classify(
            snapshot(
                createdAtMs = 14_000L,
                numericTextNodeCount = 2,
                buttonLikeNodeCount = 4,
                visibleTextNodeCount = 7,
                matchedConditions = listOf("button_like_with_visible_text")
            )
        )

        assertEquals(OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION, result.kind)
        assertEquals("low_numeric_high_button_post_transition", result.reason)
        assertFalse(result.shouldPreferForOcr)
    }

    @Test
    fun nearBoundaryNumericHigh_isFollowup() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L, numericTextNodeCount = 7))

        val result = classifier.classify(snapshot(createdAtMs = 14_500L, numericTextNodeCount = 5))

        assertEquals(OfferCycleKind.SAME_OFFER_CYCLE_FOLLOWUP, result.kind)
        assertEquals("near_boundary_offer_followup", result.reason)
    }

    @Test
    fun after20s_isNewOfferCycle() {
        val classifier = OfferCycleClassifier()
        val first = classifier.classify(snapshot(createdAtMs = 1_000L))

        val result = classifier.classify(snapshot(createdAtMs = 22_000L))

        assertEquals(OfferCycleKind.NEW_OFFER_CYCLE, result.kind)
        assertEquals(first.cycleId, result.previousCycleId)
        assertEquals("previous_cycle_older_than_20s", result.reason)
        assertTrue(result.shouldPreferForOcr)
    }

    @Test
    fun brokenEncodingOfflineText_isPostTransition() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L))

        val result = classifier.classify(
            snapshot(
                createdAtMs = 9_000L,
                numericTextNodeCount = 1,
                buttonLikeNodeCount = 1,
                visibleTextNodeCount = 3,
                knownStateTexts = listOf("n├úo ├® poss├¡vel ficar offline")
            )
        )

        assertEquals(OfferCycleKind.POSSIBLE_POST_OFFER_TRANSITION, result.kind)
        assertEquals("operational_text_post_transition", result.reason)
    }

    @Test
    fun withinOfferLifetimeWithoutSignals_isUnknown() {
        val classifier = OfferCycleClassifier()
        classifier.classify(snapshot(createdAtMs = 1_000L))

        val result = classifier.classify(
            snapshot(
                createdAtMs = 4_000L,
                numericTextNodeCount = 0,
                visibleTextNodeCount = 1,
                buttonLikeNodeCount = 0,
                matchedConditions = emptyList()
            )
        )

        assertEquals(OfferCycleKind.UNKNOWN, result.kind)
        assertEquals("within_offer_lifetime_uncertain", result.reason)
        assertFalse(result.shouldPreferForOcr)
    }

    private fun snapshot(
        createdAtMs: Long,
        numericTextNodeCount: Int = 6,
        visibleTextNodeCount: Int = 6,
        buttonLikeNodeCount: Int = 2,
        nodeCount: Int = 90,
        knownStateTexts: List<String> = emptyList(),
        matchedConditions: List<String> = listOf("numeric_text_with_visible_text")
    ) = OfferCycleSnapshot(
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        createdAtMs = createdAtMs,
        dominantPackage = "com.ubercab.driver",
        floatingPackage = "com.app99.driver",
        nodeTreePackage = "com.ubercab.driver",
        nodeCount = nodeCount,
        visibleTextNodeCount = visibleTextNodeCount,
        numericTextNodeCount = numericTextNodeCount,
        buttonLikeNodeCount = buttonLikeNodeCount,
        knownStateTexts = knownStateTexts,
        matchedConditions = matchedConditions,
        captureRequestId = "req-$createdAtMs"
    )
}
