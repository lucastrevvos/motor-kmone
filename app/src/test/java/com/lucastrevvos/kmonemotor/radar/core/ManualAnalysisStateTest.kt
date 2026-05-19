package com.lucastrevvos.kmonemotor.radar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ManualAnalysisStateTest {
    @Before
    fun setUp() {
        ManualAnalysisState.resetForTests()
    }

    @Test
    fun firstClick_isAccepted() {
        val decision = ManualAnalysisState.tryStart(nowMs = 1_000L)

        assertTrue(decision is ManualStartDecision.Accepted)
        assertTrue(ManualAnalysisState.isRunning())
    }

    @Test
    fun secondClickWithinCooldown_isRejectedAsThrottled() {
        ManualAnalysisState.tryStart(nowMs = 1_000L)
        ManualAnalysisState.finish(nowMs = 1_100L)

        val decision = ManualAnalysisState.tryStart(nowMs = 2_000L)

        assertTrue(decision is ManualStartDecision.Rejected)
        val rejected = decision as ManualStartDecision.Rejected
        assertEquals(ManualRejectReason.THROTTLED, rejected.reason)
        assertFalse(ManualAnalysisState.isRunning())
    }

    @Test
    fun clickWhileRunning_isRejectedAsAlreadyRunning() {
        ManualAnalysisState.tryStart(nowMs = 1_000L)

        val decision = ManualAnalysisState.tryStart(nowMs = 1_100L)

        assertTrue(decision is ManualStartDecision.Rejected)
        val rejected = decision as ManualStartDecision.Rejected
        assertEquals(ManualRejectReason.ALREADY_RUNNING, rejected.reason)
        assertTrue(ManualAnalysisState.isRunning())
    }

    @Test
    fun finish_marksRunningFalseAndKeepsCooldown() {
        ManualAnalysisState.tryStart(nowMs = 1_000L)

        ManualAnalysisState.finish(nowMs = 1_500L)
        val snapshot = ManualAnalysisState.snapshot(nowMs = 1_500L)

        assertFalse(snapshot.running)
        assertEquals(1500L, snapshot.cooldownRemainingMs)
    }
}
