package com.lucastrevvos.kmonemotor.radar.seenoffers

interface SeenOfferRepository {
    fun saveSeenOffer(offer: SeenOffer): SeenOfferSaveResult
    fun listSeenOffers(limit: Int = 100): List<SeenOffer>
    fun getSeenOfferById(id: String): SeenOffer?
    fun updateSeenOfferStatus(id: String, status: SeenOfferStatus): SeenOffer?
}

interface SavedRideRepository {
    fun saveRide(ride: SavedRide): SavedRide
    fun listSavedRides(limit: Int = 100): List<SavedRide>
    fun deleteRide(id: String): Boolean
}
