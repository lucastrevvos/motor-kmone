package com.lucastrevvos.kmonemotor.radar.seenoffers

enum class RidePlatform {
    UBER,
    NINETY_NINE,
    UNKNOWN
}

enum class SeenOfferStatus {
    SEEN,
    ACCEPTED_MANUALLY,
    REJECTED_MANUALLY,
    IGNORED,
    EXPIRED
}

enum class SavedRideSource {
    SEEN_OFFER_MANUAL_ACCEPT,
    MANUAL_ENTRY
}

data class SeenOffer(
    val id: String,
    val observationId: String,
    val platform: RidePlatform,
    val sourceTrigger: String,
    val status: SeenOfferStatus,
    val price: Double?,
    val valuePerKm: Double?,
    val pickupDistanceKm: Double?,
    val pickupTimeMin: Double?,
    val tripDistanceKm: Double?,
    val tripTimeMin: Double?,
    val totalDistanceKm: Double?,
    val estimatedTotalTimeMin: Double?,
    val productName: String?,
    val originPreview: String?,
    val destinationPreview: String?,
    val rawTextPreview: String?,
    val score: Int?,
    val rawTextHash: String?,
    val routeTextHash: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class SavedRide(
    val id: String,
    val sourceSeenOfferId: String?,
    val platform: RidePlatform,
    val price: Double?,
    val valuePerKm: Double?,
    val pickupDistanceKm: Double?,
    val pickupTimeMin: Double?,
    val tripDistanceKm: Double?,
    val tripTimeMin: Double?,
    val totalDistanceKm: Double?,
    val estimatedTotalTimeMin: Double?,
    val productName: String?,
    val originPreview: String?,
    val destinationPreview: String?,
    val acceptedAtMs: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val source: SavedRideSource
)

data class SeenOffersUiState(
    val offers: List<SeenOffer> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class SeenOfferSaveResult(
    val persisted: Boolean,
    val seenOffer: SeenOffer?,
    val reason: String
)

data class SeenOfferPersistenceResult(
    val attempted: Boolean,
    val persisted: Boolean,
    val seenOffer: SeenOffer? = null,
    val reason: String
)
