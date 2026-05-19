package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeenOfferRepositoriesTest {
    @Test
    fun saveNewOffer_persists() {
        val repo = seenOfferRepository()

        val result = repo.saveSeenOffer(offer(id = "1", observationId = "obs-1", createdAtMs = 1_000L))

        assertTrue(result.persisted)
        assertEquals(1, repo.listSeenOffers().size)
    }

    @Test
    fun similarRecentOffer_isDeduped() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(offer(id = "1", observationId = "obs-1", createdAtMs = 1_000L))

        val result = repo.saveSeenOffer(offer(id = "2", observationId = "obs-2", createdAtMs = 5_000L))

        assertFalse(result.persisted)
        assertEquals("similar_offer_recently_saved", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
    }

    @Test
    fun differentOffer_isSaved() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(offer(id = "1", observationId = "obs-1", createdAtMs = 1_000L))

        val result = repo.saveSeenOffer(
            offer(
                id = "2",
                observationId = "obs-2",
                price = 19.0,
                tripDistanceKm = 9.0,
                rawTextHash = "hash-2",
                routeTextHash = "route-2",
                createdAtMs = 5_000L
            )
        )

        assertTrue(result.persisted)
        assertEquals(2, repo.listSeenOffers().size)
    }

    @Test
    fun acceptManually_createsSavedRideAndUpdatesStatus() {
        val seenRepo = seenOfferRepository()
        val rideRepo = savedRideRepository()
        val actions = SeenOfferManualActions(seenRepo, rideRepo) { 10_000L }
        seenRepo.saveSeenOffer(offer(id = "1", observationId = "obs-1", createdAtMs = 1_000L))

        val ride = actions.acceptSeenOfferManually("1")

        assertNotNull(ride)
        assertEquals(1, rideRepo.listSavedRides().size)
        assertEquals(SeenOfferStatus.ACCEPTED_MANUALLY, seenRepo.getSeenOfferById("1")?.status)
    }

    @Test
    fun rejectManually_updatesStatus() {
        val seenRepo = seenOfferRepository()
        val actions = SeenOfferManualActions(seenRepo, savedRideRepository()) { 10_000L }
        seenRepo.saveSeenOffer(offer(id = "1", observationId = "obs-1", createdAtMs = 1_000L))

        actions.rejectSeenOfferManually("1")

        assertEquals(SeenOfferStatus.REJECTED_MANUALLY, seenRepo.getSeenOfferById("1")?.status)
    }

    @Test
    fun ignore_updatesStatus() {
        val seenRepo = seenOfferRepository()
        val actions = SeenOfferManualActions(seenRepo, savedRideRepository()) { 10_000L }
        seenRepo.saveSeenOffer(offer(id = "1", observationId = "obs-1", createdAtMs = 1_000L))

        actions.ignoreSeenOffer("1")

        assertEquals(SeenOfferStatus.IGNORED, seenRepo.getSeenOfferById("1")?.status)
    }

    private fun seenOfferRepository(): FileSeenOfferRepository {
        return FileSeenOfferRepository(File(createTempDir(), "seen_offers.json"))
    }

    private fun savedRideRepository(): FileSavedRideRepository {
        return FileSavedRideRepository(File(createTempDir(), "saved_rides.json"))
    }

    private fun offer(
        id: String,
        observationId: String,
        price: Double = 12.5,
        tripDistanceKm: Double? = 4.8,
        rawTextHash: String = "hash-1",
        routeTextHash: String = "route-1",
        createdAtMs: Long
    ) = SeenOffer(
        id = id,
        observationId = observationId,
        platform = RidePlatform.UBER,
        sourceTrigger = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = 2.1,
        pickupDistanceKm = 1.2,
        pickupTimeMin = 4.0,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = 10.0,
        totalDistanceKm = tripDistanceKm?.plus(1.2),
        estimatedTotalTimeMin = 14.0,
        productName = "UberX",
        originPreview = null,
        destinationPreview = null,
        rawTextPreview = "UberX R$ 12,50",
        score = 9,
        rawTextHash = rawTextHash,
        routeTextHash = routeTextHash,
        createdAtMs = createdAtMs,
        updatedAtMs = createdAtMs
    )
}
