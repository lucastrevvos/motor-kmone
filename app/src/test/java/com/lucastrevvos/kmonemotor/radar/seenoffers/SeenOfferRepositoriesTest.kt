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
        assertEquals("weaker_duplicate_offer_recently_saved", result.reason)
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
    fun betterDuplicateOffer_mergesInsteadOfCreatingNewCard() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "1",
                observationId = "obs-1",
                originPreview = null,
                destinationPreview = null,
                createdAtMs = 1_000L
            )
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "2",
                observationId = "obs-2",
                originPreview = "Origem melhor",
                destinationPreview = "Destino melhor",
                createdAtMs = 5_000L
            )
        )

        assertTrue(result.persisted)
        assertEquals("merged_better_version", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals("Origem melhor", repo.listSeenOffers().first().originPreview)
    }

    @Test
    fun weakerDuplicateOffer_isIgnored() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "1",
                observationId = "obs-1",
                originPreview = "Origem melhor",
                destinationPreview = "Destino melhor",
                createdAtMs = 1_000L
            )
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "2",
                observationId = "obs-2",
                originPreview = null,
                destinationPreview = null,
                createdAtMs = 5_000L
            )
        )

        assertFalse(result.persisted)
        assertEquals("weaker_duplicate_offer_recently_saved", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals("Origem melhor", repo.listSeenOffers().first().originPreview)
    }

    @Test
    fun samePlatformSamePriceWithinShortWindow_mergesEvenWithDifferentDistance() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "manual-1",
                observationId = "obs-manual",
                price = 9.01,
                pickupDistanceKm = 3.2,
                tripDistanceKm = 5.3,
                totalDistanceKm = 8.5,
                sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
                createdAtMs = 1_000L
            )
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "auto-1",
                observationId = "obs-auto",
                price = 9.01,
                pickupDistanceKm = 8.0,
                tripDistanceKm = 9.0,
                totalDistanceKm = 17.0,
                sourceTrigger = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
                createdAtMs = 4_000L
            )
        )

        assertFalse(result.persisted)
        assertEquals("manual_recent_authority_weaker_auto_ignored", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals("obs-manual", repo.listSeenOffers().first().observationId)
        assertEquals(8.5, repo.listSeenOffers().first().totalDistanceKm ?: 0.0, 0.0)
    }

    @Test
    fun recentManualAuthority_ignoresWeakerAutomaticEquivalent() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "manual-1",
                observationId = "obs-manual",
                price = 9.01,
                pickupDistanceKm = 3.2,
                tripDistanceKm = 5.3,
                totalDistanceKm = 8.5,
                originPreview = "Origem manual",
                destinationPreview = "Destino manual",
                sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
                createdAtMs = 1_000L
            )
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "auto-1",
                observationId = "obs-auto",
                price = 9.01,
                pickupDistanceKm = 9.0,
                tripDistanceKm = 8.0,
                totalDistanceKm = 17.0,
                originPreview = null,
                destinationPreview = null,
                sourceTrigger = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
                createdAtMs = 4_000L
            )
        )

        assertFalse(result.persisted)
        assertEquals("manual_recent_authority_weaker_auto_ignored", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals("obs-manual", repo.listSeenOffers().first().observationId)
        assertEquals(8.5, repo.listSeenOffers().first().totalDistanceKm ?: 0.0, 0.0)
    }

    @Test
    fun recentManualAuthority_mergesBetterAutomaticSilently() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "manual-1",
                observationId = "obs-manual",
                price = 9.01,
                pickupDistanceKm = 3.2,
                tripDistanceKm = 5.3,
                totalDistanceKm = 8.5,
                originPreview = null,
                destinationPreview = null,
                sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
                createdAtMs = 1_000L
            )
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "auto-1",
                observationId = "obs-auto",
                price = 9.01,
                pickupDistanceKm = 3.2,
                tripDistanceKm = 5.3,
                totalDistanceKm = 8.5,
                originPreview = "Origem melhor",
                destinationPreview = "Destino melhor",
                sourceTrigger = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
                createdAtMs = 4_000L
            )
        )

        assertTrue(result.persisted)
        assertEquals("manual_recent_authority_better_auto_merged_silently", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals("Origem melhor", repo.listSeenOffers().first().originPreview)
        assertEquals("Destino melhor", repo.listSeenOffers().first().destinationPreview)
    }

    @Test
    fun manualUnknownPlatformSamePriceShortlyAfterUber_mergesInsteadOfSavingSecondCard() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "auto-1",
                observationId = "obs-auto",
                platform = RidePlatform.UBER,
                price = 7.40,
                pickupDistanceKm = 3.0,
                tripDistanceKm = 4.4,
                totalDistanceKm = 7.4,
                sourceTrigger = "UBER_AUTO_BURST_RECOVERY",
                createdAtMs = 1_000L
            )
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "manual-1",
                observationId = "obs-manual",
                platform = RidePlatform.UNKNOWN,
                price = 7.40,
                pickupDistanceKm = 3.0,
                tripDistanceKm = 4.4,
                totalDistanceKm = 7.4,
                sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
                createdAtMs = 4_000L
            )
        )

        assertFalse(result.persisted)
        assertEquals("weaker_duplicate_offer_recently_saved", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals("obs-auto", repo.listSeenOffers().first().observationId)
        assertEquals(RidePlatform.UBER, repo.listSeenOffers().first().platform)
    }

    @Test
    fun duplicateWithSuspiciousDowngradedEconomics_keepsExistingSaneNinetyNineOffer() {
        val repo = seenOfferRepository()
        repo.saveSeenOffer(
            offer(
                id = "good-99",
                observationId = "obs-good",
                platform = RidePlatform.NINETY_NINE,
                price = 21.40,
                pickupDistanceKm = 0.858,
                tripDistanceKm = 17.6,
                totalDistanceKm = 18.458,
                sourceTrigger = "UBER_AUTO_BURST_RECOVERY",
                createdAtMs = 1_000L
            ).copy(valuePerKm = 21.40 / 18.458)
        )

        val result = repo.saveSeenOffer(
            offer(
                id = "bad-99",
                observationId = "obs-bad",
                platform = RidePlatform.NINETY_NINE,
                price = 21.40,
                pickupDistanceKm = 0.858,
                tripDistanceKm = 0.025,
                totalDistanceKm = 0.883,
                sourceTrigger = "UBER_AUTO_BURST_RECOVERY",
                createdAtMs = 4_000L
            ).copy(valuePerKm = 24.235)
        )

        assertFalse(result.persisted)
        assertEquals("weaker_duplicate_offer_recently_saved", result.reason)
        assertEquals(1, repo.listSeenOffers().size)
        assertEquals(18.458, repo.listSeenOffers().first().totalDistanceKm ?: 0.0, 0.01)
        assertEquals(17.6, repo.listSeenOffers().first().tripDistanceKm ?: 0.0, 0.01)
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

    @Test
    fun deleteRide_removesSavedRide() {
        val rideRepo = savedRideRepository()
        rideRepo.saveRide(savedRide(id = "ride-1"))

        val deleted = rideRepo.deleteRide("ride-1")

        assertTrue(deleted)
        assertTrue(rideRepo.listSavedRides().isEmpty())
    }

    @Test
    fun updateRide_updatesExistingRideWithoutDuplicating() {
        val rideRepo = savedRideRepository()
        val original = savedRide(id = "ride-1")
        rideRepo.saveRide(original)

        val updated = rideRepo.updateRide(
            original.copy(
                price = 19.9,
                originPreview = "Nova origem"
            )
        )

        assertNotNull(updated)
        assertEquals(1, rideRepo.listSavedRides().size)
        assertEquals(19.9, rideRepo.listSavedRides().first().price ?: 0.0, 0.0)
        assertEquals("Nova origem", rideRepo.listSavedRides().first().originPreview)
    }

    @Test
    fun fuelEntryRepository_savesAndListsEntries() {
        val repo = fuelEntryRepository()

        repo.saveFuelEntry(
            FuelEntry(
                id = "fuel-1",
                amountBrl = 100.0,
                liters = 20.0,
                fuelType = "Gasolina",
                createdAtMs = 1_000L,
                note = "posto"
            )
        )

        assertEquals(1, repo.listFuelEntries().size)
        assertEquals(100.0, repo.listFuelEntries().first().amountBrl, 0.0)
    }

    @Test
    fun fuelEntryRepository_updatesWithoutDuplicating() {
        val repo = fuelEntryRepository()
        val original = FuelEntry(
            id = "fuel-1",
            amountBrl = 100.0,
            liters = 20.0,
            fuelType = "Gasolina",
            createdAtMs = 1_000L,
            note = "posto"
        )
        repo.saveFuelEntry(original)

        val updated = repo.updateFuelEntry(original.copy(amountBrl = 120.0, note = "ajustado"))

        assertNotNull(updated)
        assertEquals(1, repo.listFuelEntries().size)
        assertEquals(120.0, repo.listFuelEntries().first().amountBrl, 0.0)
        assertEquals("ajustado", repo.listFuelEntries().first().note)
    }

    @Test
    fun fuelEntryRepository_deletesEntry() {
        val repo = fuelEntryRepository()
        repo.saveFuelEntry(
            FuelEntry(
                id = "fuel-1",
                amountBrl = 100.0,
                liters = 20.0,
                fuelType = "Gasolina",
                createdAtMs = 1_000L,
                note = null
            )
        )

        val deleted = repo.deleteFuelEntry("fuel-1")

        assertTrue(deleted)
        assertTrue(repo.listFuelEntries().isEmpty())
    }

    @Test
    fun driverSettingsRepository_persistsDailyGoal() {
        val repo = driverSettingsRepository()

        assertEquals(null, repo.getSettings().dailyGoalBrl)

        repo.updateDailyGoal(220.0)

        assertEquals(220.0, repo.getSettings().dailyGoalBrl ?: 0.0, 0.0)
    }

    private fun seenOfferRepository(): FileSeenOfferRepository {
        return FileSeenOfferRepository(File(createTempDir(), "seen_offers.json"))
    }

    private fun savedRideRepository(): FileSavedRideRepository {
        return FileSavedRideRepository(File(createTempDir(), "saved_rides.json"))
    }

    private fun fuelEntryRepository(): FileFuelEntryRepository {
        return FileFuelEntryRepository(File(createTempDir(), "fuel_entries.json"))
    }

    private fun driverSettingsRepository(): FileDriverSettingsRepository {
        return FileDriverSettingsRepository(File(createTempDir(), "driver_settings.txt"))
    }

    private fun savedRide(id: String) = SavedRide(
        id = id,
        sourceSeenOfferId = "seen-1",
        platform = RidePlatform.UBER,
        price = 12.5,
        valuePerKm = 2.1,
        pickupDistanceKm = 1.2,
        pickupTimeMin = 4.0,
        tripDistanceKm = 4.8,
        tripTimeMin = 10.0,
        totalDistanceKm = 6.0,
        estimatedTotalTimeMin = 14.0,
        productName = "UberX",
        originPreview = "Origem",
        destinationPreview = "Destino",
        acceptedAtMs = 2_000L,
        createdAtMs = 2_000L,
        updatedAtMs = 2_000L,
        source = SavedRideSource.SEEN_OFFER_MANUAL_ACCEPT
    )

    private fun offer(
        id: String,
        observationId: String,
        platform: RidePlatform = RidePlatform.UBER,
        price: Double = 12.5,
        pickupDistanceKm: Double? = 1.2,
        tripDistanceKm: Double? = 4.8,
        totalDistanceKm: Double? = tripDistanceKm?.plus(pickupDistanceKm ?: 0.0),
        rawTextHash: String = "hash-1",
        routeTextHash: String = "route-1",
        originPreview: String? = null,
        destinationPreview: String? = null,
        sourceTrigger: String = "UBER_DOMINANT_OFFER_DIAGNOSTIC",
        createdAtMs: Long
    ) = SeenOffer(
        id = id,
        observationId = observationId,
        platform = platform,
        sourceTrigger = sourceTrigger,
        status = SeenOfferStatus.SEEN,
        price = price,
        valuePerKm = 2.1,
        pickupDistanceKm = pickupDistanceKm,
        pickupTimeMin = 4.0,
        tripDistanceKm = tripDistanceKm,
        tripTimeMin = 10.0,
        totalDistanceKm = totalDistanceKm,
        estimatedTotalTimeMin = 14.0,
        productName = "UberX",
        originPreview = originPreview,
        destinationPreview = destinationPreview,
        rawTextPreview = "UberX R$ 12,50",
        score = 9,
        rawTextHash = rawTextHash,
        routeTextHash = routeTextHash,
        createdAtMs = createdAtMs,
        updatedAtMs = createdAtMs
    )
}
