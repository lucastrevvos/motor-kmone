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
            override fun updateRide(ride: SavedRide) = throw UnsupportedOperationException()
            override fun deleteRide(id: String) = throw UnsupportedOperationException()
        },
        fuelEntriesProvider = { emptyList() },
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
        assertEquals(0.0, summary.fuelSpentAmount ?: -1.0, 0.0)
        assertNull(summary.totalKm)
    }

    @Test
    fun daySummary_calculatesTotalKmAndAverage() {
        val nowMs = 1_715_000_000_000L
        val rides = listOf(
            ride(id = "1", acceptedAtMs = nowMs, price = 20.0, pickupDistanceKm = 2.0, tripDistanceKm = 8.0, totalDistanceKm = 10.0),
            ride(id = "2", acceptedAtMs = nowMs - 60_000L, price = 30.0, pickupDistanceKm = 3.0, tripDistanceKm = 12.0, totalDistanceKm = 15.0)
        )

        val summary = provider.summarize(rides = rides, period = RecordsPeriodFilter.DAY, nowMs = nowMs)

        assertEquals(50.0, summary.totalEarned, 0.0)
        assertEquals(2, summary.ridesCount)
        assertEquals(25.0, summary.totalKm ?: 0.0, 0.0)
        assertEquals(2.0, summary.averageValuePerKm ?: 0.0, 0.0)
    }

    @Test
    fun daySummary_sumsFuelEntriesOfTheDay() {
        val nowMs = millis(2024, 5, 15, 12, 0)
        val summary = provider.summarize(
            rides = listOf(ride(id = "1", acceptedAtMs = nowMs, price = 20.0)),
            fuelEntries = listOf(
                fuel("fuel-1", millis(2024, 5, 15, 8, 0), 50.0),
                fuel("fuel-2", millis(2024, 5, 14, 8, 0), 25.0)
            ),
            period = RecordsPeriodFilter.DAY,
            nowMs = nowMs
        )

        assertEquals(50.0, summary.fuelSpentAmount ?: 0.0, 0.0)
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

        val summary = provider.summarize(rides = rides, period = RecordsPeriodFilter.DAY, nowMs = nowMs)

        assertEquals(15.0, summary.totalEarned, 0.0)
        assertEquals(10.0, summary.totalKm ?: 0.0, 0.0)
    }

    @Test
    fun manualRide_isCountedLikeNormalRide() {
        val nowMs = millis(2024, 5, 15, 12, 0)
        val summary = provider.summarize(
            rides = listOf(
                ride(
                    id = "manual-1",
                    acceptedAtMs = nowMs,
                    price = 30.0,
                    pickupDistanceKm = 2.0,
                    tripDistanceKm = 8.0,
                    totalDistanceKm = 10.0,
                    source = SavedRideSource.MANUAL_ENTRY
                )
            ),
            period = RecordsPeriodFilter.DAY,
            nowMs = nowMs
        )

        assertEquals(30.0, summary.totalEarned, 0.0)
        assertEquals(1, summary.ridesCount)
        assertEquals(10.0, summary.totalKm ?: 0.0, 0.0)
    }

    @Test
    fun weekAndMonth_filtersAffectFuelToo() {
        val nowMs = millis(2024, 5, 15, 12, 0)
        val fuelEntries = listOf(
            fuel("fuel-1", millis(2024, 5, 15, 7, 0), 40.0),
            fuel("fuel-2", millis(2024, 5, 13, 10, 0), 20.0),
            fuel("fuel-3", millis(2024, 4, 30, 18, 0), 99.0)
        )

        val weekSummary = provider.summarize(
            rides = emptyList(),
            fuelEntries = fuelEntries,
            period = RecordsPeriodFilter.WEEK,
            nowMs = nowMs
        )
        val monthSummary = provider.summarize(
            rides = emptyList(),
            fuelEntries = fuelEntries,
            period = RecordsPeriodFilter.MONTH,
            nowMs = nowMs
        )

        assertEquals(60.0, weekSummary.fuelSpentAmount ?: 0.0, 0.0)
        assertEquals(60.0, monthSummary.fuelSpentAmount ?: 0.0, 0.0)
    }

    private fun ride(
        id: String,
        acceptedAtMs: Long,
        price: Double? = 10.0,
        pickupDistanceKm: Double? = 1.0,
        tripDistanceKm: Double? = 3.0,
        totalDistanceKm: Double? = 4.0,
        source: SavedRideSource = SavedRideSource.SEEN_OFFER_MANUAL_ACCEPT
    ) = SavedRide(
        id = id,
        sourceSeenOfferId = null,
        platform = RidePlatform.UBER,
        price = price,
        valuePerKm = null,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = 3.0,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = 8.0,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 11.0,
        productName = "UberX",
        originPreview = "Origem",
        destinationPreview = "Destino",
        acceptedAtMs = acceptedAtMs,
        createdAtMs = acceptedAtMs,
        updatedAtMs = acceptedAtMs,
        source = source
    )

    private fun fuel(
        id: String,
        createdAtMs: Long,
        amountBrl: Double
    ) = FuelEntry(
        id = id,
        amountBrl = amountBrl,
        liters = null,
        fuelType = null,
        createdAtMs = createdAtMs,
        note = null
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
