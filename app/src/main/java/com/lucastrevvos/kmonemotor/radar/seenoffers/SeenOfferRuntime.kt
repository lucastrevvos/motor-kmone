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
        return SeenOfferModule(
            seenOfferRepository = seenOfferRepository,
            savedRideRepository = savedRideRepository,
            persistenceProcessor = SeenOfferPersistenceProcessor(seenOfferRepository),
            manualActions = SeenOfferManualActions(seenOfferRepository, savedRideRepository)
        )
    }
}

data class SeenOfferModule(
    val seenOfferRepository: SeenOfferRepository,
    val savedRideRepository: SavedRideRepository,
    val persistenceProcessor: SeenOfferPersistenceProcessor,
    val manualActions: SeenOfferManualActions
)
