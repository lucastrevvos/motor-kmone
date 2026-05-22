package com.lucastrevvos.kmonemotor.radar.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NinetyNineNonOfferScreenClassifierTest {
    @Test
    fun searchingWithMultipliers_isNonOfferMapScreen() {
        val signal = NinetyNineNonOfferScreenClassifier.fromRawText(
            "Buscando 1,1X-1,5X 1,3X-1,7X heatmap"
        )

        assertTrue(signal.isNonOfferMapScreen)
        assertTrue(signal.hasSearchingText)
        assertTrue(signal.hasMultiplierText)
    }

    @Test
    fun heatmapWithoutOfferSignals_isNonOffer() {
        val signal = NinetyNineNonOfferScreenClassifier.fromRawText(
            "surge 1,2X-1,6X mapa"
        )

        assertTrue(signal.isNonOfferMapScreen)
        assertFalse(signal.hasStrong99OfferSignals)
    }

    @Test
    fun realOfferSignals_areNotClassifiedAsNonOffer() {
        val signal = NinetyNineNonOfferScreenClassifier.fromRawText(
            "Pagamento no app R$31,37 CPF e Cartão verif. 9min (2,3km) 35min (20,4km)"
        )

        assertFalse(signal.isNonOfferMapScreen)
        assertTrue(signal.hasStrong99OfferSignals)
        assertTrue(signal.hasOfferPrice)
    }
}
