package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCaptureFrameFilterTest {
    @Test
    fun kmOneOverlayText_isDetectedAndCannotWinMicroBurst() {
        val rawText = "KM ONE R$ 1,26/km total Busca: 6 min / 1,7 km Corrida: 4 min / 2,4 km"

        assertTrue(AutomaticCaptureFrameFilter.isSelfOverlayContaminated(rawText))
        assertFalse(
            AutomaticCaptureFrameFilter.canWinMicroBurst(
                fingerprintKind = OfferTextFingerprintKind.OFFER_LIKE,
                rawText = rawText
            )
        )
    }

    @Test
    fun manualOverlayText_detectsMatchedTerms() {
        val rawText = "R$ 1,28/km total KM ONE Abaixo de R$ 1,50/km Busca: 8 min / 5,4 km Corrida: 19 min / 10,4 km Salvar Ignorar"

        val matchedTerms = AutomaticCaptureFrameFilter.matchedSelfOverlayTerms(rawText)

        assertTrue(AutomaticCaptureFrameFilter.isSelfOverlayContaminated(rawText))
        assertEquals(listOf("KM ONE", "R$/km total", "Busca", "Corrida", "Abaixo de R$", "Salvar", "Ignorar"), matchedTerms)
    }
}
