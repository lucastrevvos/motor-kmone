package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferRoutePreviewExtractorTest {
    private val extractor = OfferRoutePreviewExtractor()

    @Test
    fun extractsUberOriginAndDestination() {
        val result = extractor.extract(
            rawText = "UberX R$ 10,33 12 min (5.6 km) Avenida dos Merlins, Jurere Oeste, Florianopolis 4 minutos (1.5 km) Rua X",
            platform = RidePlatform.UBER
        )

        assertTrue(result.originPreview?.contains("Avenida dos Merlins") == true)
        assertEquals("Rua X", result.destinationPreview)
    }

    @Test
    fun extractsSingleOriginFor99() {
        val result = extractor.extract(
            rawText = "R$7,00 Dinheiro 6min (1,7km) R$1,73/km 4min (2,4km) Rod. Francisco Germano da Costa",
            platform = RidePlatform.NINETY_NINE
        )

        assertTrue(result.originPreview?.contains("Rod. Francisco Germano da Costa") == true)
        assertNull(result.destinationPreview)
    }

    @Test
    fun ignoresKmOneOverlayText() {
        val result = extractor.extract(
            rawText = "KM ONE R$ 1,71/km total Deslocamento maior que a corrida 4,1 km total R$ 7,00 Busca: 6 min / 1,7 km Corrida",
            platform = RidePlatform.UNKNOWN
        )

        assertNull(result.originPreview)
        assertNull(result.destinationPreview)
    }

    @Test
    fun doesNotInventRouteFromMapNoise() {
        val result = extractor.extract(
            rawText = "JURERE VARGEM DE FORA VARGEM GRANDE Buscando",
            platform = RidePlatform.UNKNOWN
        )

        assertNull(result.originPreview)
        assertNull(result.destinationPreview)
    }
}
