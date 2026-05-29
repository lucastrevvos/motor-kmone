package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRecordTest {
    @Test
    fun durationSeconds_usesStartedAndEndedAt() {
        assertEquals(60L, TrackingRecordFactory.durationSeconds(startedAtMs = 1_000L, endedAtMs = 61_000L))
    }

    @Test
    fun typeLabels_areDriverFriendly() {
        assertEquals("Deslocamento", trackingRecordTypeLabel(TrackingRecordType.DISPLACEMENT))
        assertEquals("Corrida particular", trackingRecordTypeLabel(TrackingRecordType.PRIVATE_RIDE))
    }

    @Test
    fun privateRideAmountValidation_requiresPositiveAmount() {
        assertFalse(TrackingRecordFactory.isValidPrivateRideAmount(null))
        assertFalse(TrackingRecordFactory.isValidPrivateRideAmount(0.0))
        assertFalse(TrackingRecordFactory.isValidPrivateRideAmount(-1.0))
        assertTrue(TrackingRecordFactory.isValidPrivateRideAmount(35.0))
    }
}
