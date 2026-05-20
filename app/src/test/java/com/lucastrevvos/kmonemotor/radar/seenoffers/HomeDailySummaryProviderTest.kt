package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class HomeDailySummaryProviderTest {
    private val nowMs = 1_710_000_000_000L

    @Test
    fun noRides_returnsZeroSummary() {
        val provider = provider(goal = 150.0)

        val summary = provider.summarize(
            seenOffers = emptyList(),
            savedRides = emptyList(),
            nowMs = nowMs
        )

        assertEquals(0.0, summary.earnedToday, 0.001)
        assertEquals(0, summary.acceptedRidesCount)
        assertEquals(0, summary.progressPercent)
        assertFalse(summary.isGoalReached)
    }

    @Test
    fun goalAndRevenue_calculatesRemainingAndProgress() {
        val provider = provider(goal = 150.0)

        val summary = provider.summarize(
            seenOffers = listOf(seenOffer("seen-1", nowMs)),
            savedRides = listOf(savedRide("ride-1", price = 65.0, totalDistanceKm = 20.0, acceptedAtMs = nowMs)),
            nowMs = nowMs
        )

        assertEquals(85.0, summary.remainingToGoal ?: 0.0, 0.001)
        assertEquals(43, summary.progressPercent)
        assertEquals(1, summary.acceptedRidesCount)
        assertEquals(1, summary.seenOffersCount)
    }

    @Test
    fun goalReached_setsFlagAndRemainingZero() {
        val provider = provider(goal = 150.0)

        val summary = provider.summarize(
            seenOffers = emptyList(),
            savedRides = listOf(savedRide("ride-1", price = 180.0, totalDistanceKm = 80.0, acceptedAtMs = nowMs)),
            nowMs = nowMs
        )

        assertTrue(summary.isGoalReached)
        assertEquals(0.0, summary.remainingToGoal ?: -1.0, 0.001)
    }

    @Test
    fun totalKmAndAverageValuePerKm_areCalculated() {
        val provider = provider(goal = 150.0)

        val summary = provider.summarize(
            seenOffers = emptyList(),
            savedRides = listOf(
                savedRide("ride-1", price = 30.0, totalDistanceKm = 10.0, acceptedAtMs = nowMs),
                savedRide("ride-2", price = 20.0, totalDistanceKm = 5.0, acceptedAtMs = nowMs)
            ),
            nowMs = nowMs
        )

        assertEquals(15.0, summary.totalKmToday ?: 0.0, 0.001)
        assertEquals(50.0 / 15.0, summary.averageValuePerKm ?: 0.0, 0.001)
    }

    @Test
    fun ridesWithoutPrice_areIgnoredInFinancialTotal() {
        val provider = provider(goal = 150.0)

        val summary = provider.summarize(
            seenOffers = emptyList(),
            savedRides = listOf(
                savedRide("ride-1", price = null, totalDistanceKm = 10.0, acceptedAtMs = nowMs),
                savedRide("ride-2", price = 20.0, totalDistanceKm = 5.0, acceptedAtMs = nowMs)
            ),
            nowMs = nowMs
        )

        assertEquals(20.0, summary.earnedToday, 0.001)
    }

    private fun provider(goal: Double?): HomeDailySummaryProvider {
        return HomeDailySummaryProvider(
            seenOfferRepository = object : SeenOfferRepository {
                override fun saveSeenOffer(offer: SeenOffer) = throw UnsupportedOperationException()
                override fun listSeenOffers(limit: Int) = emptyList<SeenOffer>()
                override fun getSeenOfferById(id: String) = null
                override fun updateSeenOfferStatus(id: String, status: SeenOfferStatus) = null
            },
            savedRideRepository = object : SavedRideRepository {
                override fun saveRide(ride: SavedRide) = throw UnsupportedOperationException()
                override fun listSavedRides(limit: Int) = emptyList<SavedRide>()
                override fun deleteRide(id: String) = throw UnsupportedOperationException()
            },
            configuredDailyGoalProvider = { goal },
            fallbackDailyGoalProvider = { null },
            zoneIdProvider = { ZoneId.of("America/Sao_Paulo") }
        )
    }

    private fun seenOffer(id: String, createdAtMs: Long) = SeenOffer(
        id = id,
        observationId = "obs-$id",
        platform = RidePlatform.UBER,
        sourceTrigger = "manual",
        status = SeenOfferStatus.SEEN,
        price = 12.0,
        valuePerKm = 2.0,
        pickupDistanceKm = 1.0,
        pickupTimeMin = 4.0,
        tripDistanceKm = 3.0,
        tripTimeMin = 7.0,
        totalDistanceKm = 4.0,
        estimatedTotalTimeMin = 11.0,
        productName = "UberX",
        originPreview = null,
        destinationPreview = null,
        rawTextPreview = "UberX R$ 12,00",
        score = 1,
        rawTextHash = null,
        routeTextHash = null,
        createdAtMs = createdAtMs,
        updatedAtMs = createdAtMs
    )

    private fun savedRide(
        id: String,
        price: Double?,
        totalDistanceKm: Double?,
        acceptedAtMs: Long
    ) = SavedRide(
        id = id,
        sourceSeenOfferId = null,
        platform = RidePlatform.UBER,
        price = price,
        valuePerKm = null,
        pickupDistanceKm = 1.0,
        pickupTimeMin = 4.0,
        tripDistanceKm = 3.0,
        tripTimeMin = 7.0,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 11.0,
        productName = "UberX",
        originPreview = null,
        destinationPreview = null,
        acceptedAtMs = acceptedAtMs,
        createdAtMs = acceptedAtMs,
        updatedAtMs = acceptedAtMs,
        source = SavedRideSource.SEEN_OFFER_MANUAL_ACCEPT
    )
}
