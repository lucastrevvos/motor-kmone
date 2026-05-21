package com.lucastrevvos.kmonemotor.radar.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UberOperationalScreenClassifierTest {
    @Test
    fun gainsAndPlusMoney_isOperationalScreen() {
        val signal = UberOperationalScreenClassifier.classify(
            listOf(
                "Ganhos",
                "Oportunidades",
                "+R$ 2",
                "Pagina inicial",
                "Mensagens",
                "Menu"
            )
        )

        assertTrue(signal.isOperationalScreen)
        assertTrue(signal.hasOperationalMoneyText)
        assertTrue(signal.hasHomeContext)
    }

    @Test
    fun uberOfferTexts_areNotOperationalScreen() {
        val signal = UberOperationalScreenClassifier.classify(
            listOf(
                "Priority",
                "R$ 12",
                "4 min (2.2 km)",
                "9 minutos (6.3 km)"
            )
        )

        assertFalse(signal.isOperationalScreen)
        assertFalse(signal.hasOperationalMoneyText)
    }
}
