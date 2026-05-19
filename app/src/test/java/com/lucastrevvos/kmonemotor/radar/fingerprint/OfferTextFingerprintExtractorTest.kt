package com.lucastrevvos.kmonemotor.radar.fingerprint

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferTextFingerprintExtractorTest {
    private val extractor = OfferTextFingerprintExtractor(clock = RadarClock { 1_000L })

    @Test
    fun ninetyNineRealOffer_becomesOfferLike() {
        val fingerprint = extractor.extract(
            ocr(
                "Dinheiro R$7,40 R$2,26/km Taxa de deslocamento R$1,04 5min (961m) CPF verif. Rua do Lamim"
            )
        )

        assertEquals(OfferTextFingerprintKind.OFFER_LIKE, fingerprint.kind)
        assertEquals(PlatformTextHint.NINETY_NINE, fingerprint.platformTextHint)
        assertTrue(fingerprint.priceCandidates.any { it.normalizedValue == 7.40 })
        assertTrue(fingerprint.valuePerKmCandidates.any { it.normalizedValue == 2.26 })
        assertTrue(fingerprint.distanceCandidates.any { it.normalizedValue == 961.0 && it.unit == "m" })
        assertTrue(fingerprint.timeCandidates.any { it.normalizedValue == 5.0 })
        assertTrue(fingerprint.positiveSignals.any { it.key == "dinheiro" })
        assertTrue(fingerprint.positiveSignals.any { it.key == "taxa_deslocamento" })
        assertTrue(fingerprint.positiveSignals.any { it.key == "cpf_verif" })
    }

    @Test
    fun uberRealOffer_becomesOfferLike() {
        val fingerprint = extractor.extract(
            ocr("Priority R$ 11,55 6 min (2.6 km) Verificado +R$ 1,90 incluido para prioridade")
        )

        assertEquals(OfferTextFingerprintKind.OFFER_LIKE, fingerprint.kind)
        assertEquals(PlatformTextHint.UBER, fingerprint.platformTextHint)
        assertTrue(fingerprint.priceCandidates.any { it.normalizedValue == 11.55 })
        assertTrue(fingerprint.distanceCandidates.any { it.normalizedValue == 2.6 && it.unit == "km" })
        assertTrue(fingerprint.timeCandidates.any { it.normalizedValue == 6.0 })
        assertTrue(fingerprint.positiveSignals.any { it.key == "priority" })
        assertTrue(fingerprint.positiveSignals.any { it.key == "verificado" })
    }

    @Test
    fun integerPriceWithoutDecimal_isNormalizedInOfferContext() {
        val fingerprint = extractor.extract(
            ocr("UberX R$ 746 3 min (1.7 km)")
        )

        assertTrue(fingerprint.priceCandidates.any { it.normalizedValue == 7.46 })
    }

    @Test
    fun fourDigitIntegerPriceWithoutDecimal_isNormalizedInOfferContext() {
        val fingerprint = extractor.extract(
            ocr("Uber R$ 1071 11 min (4.7 km)")
        )

        assertTrue(fingerprint.priceCandidates.any { it.normalizedValue == 10.71 })
    }

    @Test
    fun uberXTextBeatsNinetyNineTrigger() {
        val fingerprint = extractor.extract(
            ocr(
                rawText = "2 UberX R$ 5,81 4 min (2.3 km) 4 minutos (1.4 km)",
                triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE
            )
        )

        assertEquals(OfferTextFingerprintKind.OFFER_LIKE, fingerprint.kind)
        assertEquals(PlatformTextHint.UBER, fingerprint.platformTextHint)
    }

    @Test
    fun uberLongRideOffer_becomesOfferLike() {
        val fingerprint = extractor.extract(
            ocr("R$14,60 Corrida longa 10min (5,3km) Dinheiro Preco x1,2 R$3,63")
        )

        assertEquals(OfferTextFingerprintKind.OFFER_LIKE, fingerprint.kind)
        assertTrue(fingerprint.priceCandidates.any { it.normalizedValue == 14.60 })
        assertTrue(fingerprint.distanceCandidates.any { it.normalizedValue == 5.3 && it.unit == "km" })
        assertTrue(fingerprint.timeCandidates.any { it.normalizedValue == 10.0 })
        assertTrue(fingerprint.positiveSignals.any { it.key == "corrida_longa" })
        assertTrue(fingerprint.positiveSignals.any { it.key == "dinheiro" })
        assertTrue(fingerprint.positiveSignals.any { it.key == "preco_x" })
    }

    @Test
    fun operationalScreen_becomesNonOffer() {
        val fingerprint = extractor.extract(
            ocr("Continuar aceitando solicitacoes Desconectar")
        )

        assertEquals(OfferTextFingerprintKind.NON_OFFER, fingerprint.kind)
        assertTrue(fingerprint.negativeSignals.any { it.key == "continuar_aceitando_solicitacoes" })
        assertTrue(fingerprint.negativeSignals.any { it.key == "desconectar" })
        assertTrue(fingerprint.offerLikeScore < fingerprint.nonOfferScore)
    }

    @Test
    fun fuelPromo_becomesNonOffer() {
        val fingerprint = extractor.extract(
            ocr("Nova 99 Abastece Posto LLX unidade 08 R$4,39 R$4,79")
        )

        assertEquals(OfferTextFingerprintKind.NON_OFFER, fingerprint.kind)
        assertTrue(fingerprint.negativeSignals.any { it.key == "99_abastece" })
        assertTrue(fingerprint.negativeSignals.any { it.key == "posto" })
    }

    @Test
    fun fuelPromoIntegerPrice_isNotNormalizedAsOfferPrice() {
        val fingerprint = extractor.extract(
            ocr("99 Abastece Posto Santa Monica Rede Primos R$ 479")
        )

        assertTrue(fingerprint.priceCandidates.any { it.normalizedValue == 479.0 })
        assertEquals(OfferTextFingerprintKind.NON_OFFER, fingerprint.kind)
    }

    @Test
    fun unrelatedRouteText_becomesUnknown() {
        val fingerprint = extractor.extract(
            ocr("Rod Tertuliar CANASVIEIRAS VARGEM DE FORA")
        )

        assertEquals(OfferTextFingerprintKind.UNKNOWN, fingerprint.kind)
    }

    private fun ocr(rawText: String, triggerSource: TriggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC) = OcrObservation(
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = triggerSource,
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
}
