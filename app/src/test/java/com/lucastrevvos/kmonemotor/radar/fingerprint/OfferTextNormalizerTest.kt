package com.lucastrevvos.kmonemotor.radar.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferTextNormalizerTest {
    private val normalizer = OfferTextNormalizer()

    @Test
    fun removesAccentsAndRepairsCommonEncodingNoise() {
        val normalized = normalizer.normalize("Solicita├º├Áes Pre├ºo Inclu├¡do priorit├írio n├úo poss├¡vel")

        assertTrue(normalized.normalizedText.contains("solicitacoes"))
        assertTrue(normalized.normalizedText.contains("preco"))
        assertTrue(normalized.normalizedText.contains("incluido"))
        assertTrue(normalized.normalizedText.contains("prioritario"))
        assertTrue(normalized.normalizedText.contains("nao"))
        assertTrue(normalized.normalizedText.contains("possivel"))
    }

    @Test
    fun keepsUsefulLinesAndTokens() {
        val normalized = normalizer.normalize("Priority R$ 11,55\n6 min (2.6 km)")

        assertEquals(2, normalized.lines.size)
        assertTrue(normalized.tokens.contains("priority"))
        assertTrue(normalized.tokens.contains("11,55"))
    }

    @Test
    fun emptyTextDoesNotExplode() {
        val normalized = normalizer.normalize("")

        assertEquals("", normalized.normalizedText)
        assertTrue(normalized.lines.isEmpty())
        assertTrue(normalized.tokens.isEmpty())
    }
}
