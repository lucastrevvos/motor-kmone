package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.util.UUID

class SeenOfferManualActions(
    private val seenOfferRepository: SeenOfferRepository,
    private val savedRideRepository: SavedRideRepository,
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() }
) {
    fun acceptSeenOfferManually(seenOfferId: String): SavedRide? {
        val seenOffer = seenOfferRepository.getSeenOfferById(seenOfferId) ?: return null
        val nowMs = nowMsProvider()
        val resolved = RideEconomicsCalculator.resolveRideEconomics(
            platform = seenOffer.platform,
            price = seenOffer.price,
            explicitValuePerKm = seenOffer.valuePerKm,
            totalDistanceKm = seenOffer.totalDistanceKm,
            pickupDistanceKm = seenOffer.pickupDistanceKm,
            tripDistanceKm = seenOffer.tripDistanceKm
        )
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SAVED_OFFER_ECONOMICS_RESOLVED",
            "observationId" to seenOffer.observationId,
            "platform" to seenOffer.platform,
            "price" to seenOffer.price,
            "pickupDistanceKm" to resolved.pickupDistanceKm,
            "tripDistanceKm" to resolved.tripDistanceKm,
            "totalDistanceKm" to resolved.totalDistanceKm,
            "valuePerKm" to resolved.valuePerKm,
            "warnings" to resolved.warnings.joinToString(",")
        )
        val ride = SavedRide(
            id = UUID.randomUUID().toString(),
            sourceSeenOfferId = seenOffer.id,
            platform = seenOffer.platform,
            price = seenOffer.price,
            valuePerKm = resolved.valuePerKm,
            pickupDistanceKm = resolved.pickupDistanceKm,
            pickupTimeMin = seenOffer.pickupTimeMin,
            tripDistanceKm = resolved.tripDistanceKm,
            tripTimeMin = seenOffer.tripTimeMin,
            totalDistanceKm = resolved.totalDistanceKm,
            estimatedTotalTimeMin = seenOffer.estimatedTotalTimeMin,
            productName = seenOffer.productName,
            originPreview = seenOffer.originPreview,
            destinationPreview = seenOffer.destinationPreview,
            acceptedAtMs = nowMs,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            source = SavedRideSource.SEEN_OFFER_MANUAL_ACCEPT
        )
        val savedRide = savedRideRepository.saveRide(ride)
        seenOfferRepository.updateSeenOfferStatus(seenOfferId, SeenOfferStatus.ACCEPTED_MANUALLY)
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SEEN_OFFER_ACCEPTED_MANUALLY",
            "seenOfferId" to seenOfferId,
            "savedRideId" to savedRide.id,
            "platform" to seenOffer.platform,
            "price" to seenOffer.price
        )
        return savedRide
    }

    fun rejectSeenOfferManually(seenOfferId: String): SeenOffer? {
        val updated = seenOfferRepository.updateSeenOfferStatus(seenOfferId, SeenOfferStatus.REJECTED_MANUALLY)
        if (updated != null) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_REJECTED_MANUALLY",
                "seenOfferId" to seenOfferId
            )
        }
        return updated
    }

    fun ignoreSeenOffer(seenOfferId: String): SeenOffer? {
        val updated = seenOfferRepository.updateSeenOfferStatus(seenOfferId, SeenOfferStatus.IGNORED)
        if (updated != null) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_IGNORED",
                "seenOfferId" to seenOfferId
            )
        }
        return updated
    }
}
