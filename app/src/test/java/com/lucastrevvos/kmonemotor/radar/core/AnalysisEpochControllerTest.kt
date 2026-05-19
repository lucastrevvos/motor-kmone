package com.lucastrevvos.kmonemotor.radar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEpochControllerTest {
    @Test
    fun bumpManualEpoch_incrementsAndMarksPreviousAsStale() {
        val previous = AnalysisEpochController.current()
        val bumped = AnalysisEpochController.bumpManualEpoch()

        assertEquals(previous + 1, bumped)
        assertTrue(AnalysisEpochController.isCurrent(bumped))
        assertFalse(AnalysisEpochController.isCurrent(previous))
    }
}
