package com.lucastrevvos.kmonemotor.radar.seenoffers

import android.content.Context
import java.io.File

object SeenOfferRuntime {
    @Volatile
    private var instance: SeenOfferModule? = null

    fun get(context: Context): SeenOfferModule {
        return instance ?: synchronized(this) {
            instance ?: buildModule(context.applicationContext.filesDir).also { instance = it }
        }
    }

    private fun buildModule(filesDir: File): SeenOfferModule {
        val seenOfferRepository = FileSeenOfferRepository(File(filesDir, "radar/seen_offers.json"))
        val savedRideRepository = FileSavedRideRepository(File(filesDir, "radar/saved_rides.json"))
        val fuelEntryRepository = FileFuelEntryRepository(File(filesDir, "radar/fuel_entries.json"))
        val driverSettingsRepository = FileDriverSettingsRepository(File(filesDir, "radar/driver_settings.txt"))
        return SeenOfferModule(
            seenOfferRepository = seenOfferRepository,
            savedRideRepository = savedRideRepository,
            fuelEntryRepository = fuelEntryRepository,
            driverSettingsRepository = driverSettingsRepository,
            persistenceProcessor = SeenOfferPersistenceProcessor(seenOfferRepository),
            manualActions = SeenOfferManualActions(seenOfferRepository, savedRideRepository)
        )
    }
}

data class SeenOfferModule(
    val seenOfferRepository: SeenOfferRepository,
    val savedRideRepository: SavedRideRepository,
    val fuelEntryRepository: FuelEntryRepository,
    val driverSettingsRepository: DriverSettingsRepository,
    val persistenceProcessor: SeenOfferPersistenceProcessor,
    val manualActions: SeenOfferManualActions
)
