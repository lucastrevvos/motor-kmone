package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarAutoCaptureEvidenceAccumulatorTest {
    @Test
    fun latestReturnsLastEvidence() {
        val accumulator = RadarAutoCaptureEvidenceAccumulator()
        accumulator.add(evidence(timestampMs = 1_000L, hasPriceText = true))
        accumulator.add(evidence(timestampMs = 1_500L, hasPriceText = true, hasRoutePairText = true))

        assertEquals(1_500L, accumulator.latest()?.timestampMs)
    }

    @Test
    fun recentStrongOfferEvidence_detectsWithinWindow() {
        val accumulator = RadarAutoCaptureEvidenceAccumulator()
        accumulator.add(evidence(timestampMs = 1_000L, hasPriceText = true, hasRoutePairText = true))
        accumulator.add(evidence(timestampMs = 1_800L, hasSearchingText = true))

        assertTrue(accumulator.recentStrongOfferEvidence(windowMs = 1_000L))
    }

    @Test
    fun recentSearchingEvidence_detectsWithinWindow() {
        val accumulator = RadarAutoCaptureEvidenceAccumulator()
        accumulator.add(evidence(timestampMs = 1_000L, hasPriceText = true, hasRoutePairText = true))
        accumulator.add(evidence(timestampMs = 1_800L, hasSearchingText = true))

        assertTrue(accumulator.recentSearchingEvidence(windowMs = 500L))
    }

    @Test
    fun clearRemovesEvidence() {
        val accumulator = RadarAutoCaptureEvidenceAccumulator()
        accumulator.add(evidence(timestampMs = 1_000L, hasPriceText = true))
        accumulator.clear()

        assertFalse(accumulator.recentStrongOfferEvidence())
        assertEquals(null, accumulator.latest())
    }

    private fun evidence(
        timestampMs: Long,
        hasPriceText: Boolean = false,
        hasUberProductText: Boolean = false,
        hasRoutePairText: Boolean = false,
        hasSearchingText: Boolean = false,
        treeScore: Int = 0
    ) = RadarAutoCaptureEvidence(
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        hasPriceText = hasPriceText,
        hasUberProductText = hasUberProductText,
        hasRoutePairText = hasRoutePairText,
        hasSearchingText = hasSearchingText,
        treeScore = treeScore,
        matchedConditions = emptyList(),
        knownStateTexts = emptyList(),
        timestampMs = timestampMs
    )
}
