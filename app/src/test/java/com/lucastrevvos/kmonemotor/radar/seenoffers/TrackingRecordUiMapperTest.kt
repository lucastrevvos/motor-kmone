package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingRecordUiMapperTest {
    @Test
    fun displacementRecord_mapsToDisplacementItemWithoutRevenue() {
        val model = TrackingRecordUiMapper.map(record(type = TrackingRecordType.DISPLACEMENT, amount = null))

        assertEquals("Deslocamento", model.title)
        assertNull(model.amountLabel)
        assertEquals("1 min", model.durationLabel)
        assertEquals("—", model.distanceLabel)
        assertEquals("Sem receita", model.revenueLabel)
    }

    @Test
    fun privateRideRecord_mapsToPrivateRideItemWithAmount() {
        val model = TrackingRecordUiMapper.map(record(type = TrackingRecordType.PRIVATE_RIDE, amount = 35.0))

        assertEquals("Corrida particular", model.title)
        assertEquals("R$ 35,00", model.amountLabel)
        assertEquals("Receita particular", model.revenueLabel)
    }

    @Test
    fun recordWithDistance_mapsDistanceLabel() {
        val model = TrackingRecordUiMapper.map(
            record(type = TrackingRecordType.DISPLACEMENT, amount = null, distanceKm = 6.4)
        )

        assertEquals("6,4 km", model.distanceLabel)
    }

    private fun record(
        type: TrackingRecordType,
        amount: Double?,
        distanceKm: Double? = null
    ) = TrackingRecord(
        id = "tracking",
        type = type,
        startedAtMs = 1_000L,
        endedAtMs = 61_000L,
        durationSeconds = 60L,
        distanceKm = distanceKm,
        amount = amount,
        notes = null,
        createdAtMs = 61_000L
    )
}
