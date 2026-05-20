package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedSignal
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SeenOfferPersistenceProcessorTest {
    @Test
    fun rejectedBySanitizer_isNotPersisted() {
        val repository = InMemorySeenOfferRepository()
        val processor = SeenOfferPersistenceProcessor(repository)

        val result = processor.process(
            fingerprint = fingerprint(),
            observation = observation("99 Abastece Posto Santa Monica R$4,79"),
            parserResult = null
        )

        assertFalse(result.persisted)
        assertEquals("non_offer_fuel_or_promo_screen", result.reason)
        assertEquals(0, repository.saved.size)
    }

    private fun fingerprint() = OfferTextFingerprint(
        fingerprintId = "fp-1",
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
        cropKind = CropKind.CENTER_CARD_AREA,
        platformTextHint = PlatformTextHint.NINETY_NINE,
        kind = OfferTextFingerprintKind.OFFER_LIKE,
        offerLikeScore = 8,
        nonOfferScore = 0,
        positiveSignals = listOf(ExtractedSignal("offer", "99", 2)),
        negativeSignals = emptyList(),
        priceCandidates = listOf(ExtractedNumericCandidate("R$ 479", 479.0, "BRL", "PRICE", 3)),
        valuePerKmCandidates = emptyList(),
        distanceCandidates = emptyList(),
        timeCandidates = emptyList(),
        rawTextHash = "hash",
        routeTextHash = "route",
        normalizedPreview = "99 Abastece",
        reason = "offer_like_positive_signals",
        createdAtMs = 1000L
    )

    private fun observation(rawText: String) = OcrObservation(
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE,
        cropId = "crop-1",
        cropKind = CropKind.CENTER_CARD_AREA,
        startedAtMs = 1L,
        finishedAtMs = 2L,
        durationMs = 1L,
        success = true,
        rawText = rawText,
        lineCount = 1,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = OfferCycleKind.NEW_OFFER_CYCLE,
        shouldPreferForOcr = true
    )

    private class InMemorySeenOfferRepository : SeenOfferRepository {
        val saved = mutableListOf<SeenOffer>()

        override fun saveSeenOffer(offer: SeenOffer): SeenOfferSaveResult {
            saved += offer
            return SeenOfferSaveResult(true, offer, "saved")
        }

        override fun listSeenOffers(limit: Int): List<SeenOffer> = saved.take(limit)

        override fun getSeenOfferById(id: String): SeenOffer? = saved.firstOrNull { it.id == id }

        override fun updateSeenOffer(offer: SeenOffer): SeenOffer? = null

        override fun updateSeenOfferStatus(id: String, status: SeenOfferStatus): SeenOffer? = null
    }
}
