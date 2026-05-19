package com.lucastrevvos.kmonemotor.radar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ManualAnalysisRequestBusTest {
    private var receivedCount = 0
    private var lastEpoch = 0L

    private val listener: (ManualAnalysisRequest) -> Unit = {
        receivedCount += 1
        lastEpoch = AnalysisEpochController.bumpManualEpoch()
    }

    @Before
    fun setUp() {
        ManualAnalysisState.resetForTests()
        ManualAnalysisRequestBus.unregister(listener)
        ManualAnalysisRequestBus.register(listener)
        receivedCount = 0
        lastEpoch = AnalysisEpochController.current()
    }

    @Test
    fun rejectedClick_doesNotBumpEpochOrInvokeListener() {
        val first = ManualAnalysisRequestBus.requestAnalysisDetailed("test", 1_000L)
        ManualAnalysisState.finish(nowMs = 1_100L)
        val beforeRejectedEpoch = lastEpoch

        val second = ManualAnalysisRequestBus.requestAnalysisDetailed("test", 1_500L)

        assertTrue(first is ManualAnalysisRequestResult.Sent)
        assertTrue(second is ManualAnalysisRequestResult.Rejected)
        assertEquals(1, receivedCount)
        assertEquals(beforeRejectedEpoch, lastEpoch)
    }
}
