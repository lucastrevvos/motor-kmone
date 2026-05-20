package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
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
}
