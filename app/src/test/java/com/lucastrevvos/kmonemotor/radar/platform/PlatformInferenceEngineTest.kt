package com.lucastrevvos.kmonemotor.radar.platform

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformInferenceEngineTest {
    private val engine = PlatformInferenceEngine()

    @Test
    fun uberXWinsOverNinetyNineTrigger() {
        val result = engine.infer(
            PlatformInferenceInput(
                rawText = "2 UberX R$ 9,36 5 min (3.9 km)",
                normalizedText = "2 uberx r$ 9,36 5 min (3.9 km)",
                triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE
            )
        )

        assertEquals(PlatformTextHint.UBER, result.platform)
        assertEquals("strong_uber_text_signal", result.reason)
    }

    @Test
    fun uberXWinsOverStrongNinetyNineConflict() {
        val result = engine.infer(
            PlatformInferenceInput(
                rawText = "99 Abastece UberX R$ 8,38",
                normalizedText = "99 abastece uberx r$ 8,38",
                triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC
            )
        )

        assertEquals(PlatformTextHint.UBER, result.platform)
        assertTrue(result.conflict)
    }

    @Test
    fun pagamentoNoAppIsNinetyNine() {
        val result = engine.infer(
            PlatformInferenceInput(
                rawText = "Pagamento no app R$12,70",
                normalizedText = "pagamento no app r$12,70",
                triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS
            )
        )

        assertEquals(PlatformTextHint.NINETY_NINE, result.platform)
    }

    @Test
    fun unknownWithoutSignalsStaysUnknown() {
        val result = engine.infer(
            PlatformInferenceInput(
                rawText = "Terminal Google Centro",
                normalizedText = "terminal google centro",
                triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS
            )
        )

        assertEquals(PlatformTextHint.UNKNOWN, result.platform)
    }
}
