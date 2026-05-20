package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecordsSummaryProviderTest {
    private val zoneId = ZoneId.of("America/Sao_Paulo")
    private val provider = RecordsSummaryProvider(
        savedRideRepository = object : SavedRideRepository {
            override fun saveRide(ride: SavedRide) = throw UnsupportedOperationException()
            override fun listSavedRides(limit: Int) = emptyList<SavedRide>()
            override fun deleteRide(id: String) = throw UnsupportedOperationException()
        },
        zoneIdProvider = { zoneId }
    )

    @Test
    fun dayWithoutRides_returnsZeroSummary() {
        val summary = provider.summarize(
            rides = emptyList(),
            period = RecordsPeriodFilter.DAY,
            nowMs = 1_715_000_000_000L
        )

        assertEquals(0.0, summary.totalEarned, 0.0)
        assertEquals(0, summary.ridesCount)
        assertNull(summary.totalKm)
    }

    @Test
    fun daySummary_calculatesTotalKmAndAverage() {
        val nowMs = 1_715_000_000_000L
        val rides = listOf(
            ride(id = "1", acceptedAtMs = nowMs, price = 20.0, totalDistanceKm = 10.0),
            ride(id = "2", acceptedAtMs = nowMs - 60_000L, price = 30.0, totalDistanceKm = 15.0)
        )

        val summary = provider.summarize(rides, RecordsPeriodFilter.DAY, nowMs)

        assertEquals(50.0, summary.totalEarned, 0.0)
        assertEquals(2, summary.ridesCount)
        assertEquals(25.0, summary.totalKm ?: 0.0, 0.0)
        assertEquals(2.0, summary.averageValuePerKm ?: 0.0, 0.0)
    }

    @Test
    fun weekFilter_includesCurrentWeekOnly() {
        val nowMs = millis(2024, 5, 15, 12, 0)
        val rides = listOf(
            ride(id = "1", acceptedAtMs = nowMs, price = 20.0),
            ride(id = "2", acceptedAtMs = millis(2024, 5, 13, 9, 0), price = 30.0),
            ride(id = "3", acceptedAtMs = millis(2024, 5, 5, 20, 0), price = 99.0)
        )

        val filtered = provider.filterRides(rides, RecordsPeriodFilter.WEEK, nowMs)

        assertEquals(listOf("1", "2"), filtered.map { it.id })
    }

    @Test
    fun monthFilter_excludesOtherMonth() {
        val nowMs = millis(2024, 5, 15, 12, 0)
        val rides = listOf(
            ride(id = "1", acceptedAtMs = nowMs, price = 20.0),
            ride(id = "2", acceptedAtMs = millis(2024, 4, 30, 18, 0), price = 30.0)
        )

        val filtered = provider.filterRides(rides, RecordsPeriodFilter.MONTH, nowMs)

        assertEquals(listOf("1"), filtered.map { it.id })
    }

    @Test
    fun ridesWithoutPrice_doNotEnterFinancialTotal() {
        val nowMs = 1_715_000_000_000L
        val rides = listOf(
            ride(id = "1", acceptedAtMs = nowMs, price = null, totalDistanceKm = 5.0),
            ride(id = "2", acceptedAtMs = nowMs, price = 15.0, totalDistanceKm = 5.0)
        )

        val summary = provider.summarize(rides, RecordsPeriodFilter.DAY, nowMs)

        assertEquals(15.0, summary.totalEarned, 0.0)
        assertEquals(10.0, summary.totalKm ?: 0.0, 0.0)
    }

    private fun ride(
        id: String,
        acceptedAtMs: Long,
        price: Double? = 10.0,
        totalDistanceKm: Double? = 4.0
    ) = SavedRide(
        id = id,
        sourceSeenOfferId = null,
        platform = RidePlatform.UBER,
        price = price,
        valuePerKm = null,
        pickupDistanceKm = 1.0,
        pickupTimeMin = 3.0,
        tripDistanceKm = 3.0,
        tripTimeMin = 8.0,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 11.0,
        productName = "UberX",
        originPreview = "Origem",
        destinationPreview = "Destino",
        acceptedAtMs = acceptedAtMs,
        createdAtMs = acceptedAtMs,
        updatedAtMs = acceptedAtMs,
        source = SavedRideSource.SEEN_OFFER_MANUAL_ACCEPT
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
