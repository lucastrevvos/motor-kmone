package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.util.UUID
import kotlin.math.abs

class TrackingRecordSyncProcessor(
    private val trackingRecordRepository: TrackingRecordRepository,
    private val savedRideRepository: SavedRideRepository
) {
    suspend fun saveNew(record: TrackingRecord): TrackingRecord {
        if (record.type != TrackingRecordType.PRIVATE_RIDE || !TrackingRecordFactory.isValidPrivateRideAmount(record.amount)) {
            return trackingRecordRepository.save(record)
        }
        val savedRide = createSavedRide(record)
        savedRideRepository.saveRide(savedRide)
        RadarLogger.i(
            "KM_V2_TRACKING",
            "KM_V2_TRACKING_PRIVATE_RIDE_SAVED_RIDE_CREATED",
            "trackingRecordId" to record.id,
            "savedRideId" to savedRide.id,
            "amount" to record.amount
        )
        return trackingRecordRepository.save(record.copy(linkedSavedRideId = savedRide.id))
    }

    suspend fun update(record: TrackingRecord): TrackingRecord? {
        val updated = trackingRecordRepository.update(record) ?: return null
        if (updated.type == TrackingRecordType.PRIVATE_RIDE) {
            val linkedRide = findLinkedSavedRide(updated)
            if (linkedRide != null) {
                val amount = updated.amount ?: linkedRide.price
                val distanceKm = updated.distanceKm
                val synced = linkedRide.copy(
                    price = amount,
                    tripDistanceKm = distanceKm,
                    totalDistanceKm = distanceKm,
                    valuePerKm = if (amount != null && distanceKm != null && distanceKm > 0.0) amount / distanceKm else null,
                    updatedAtMs = System.currentTimeMillis()
                )
                savedRideRepository.updateRide(synced)
                RadarLogger.i(
                    "KM_V2_TRACKING",
                    "KM_V2_TRACKING_LINKED_SAVED_RIDE_UPDATED",
                    "trackingRecordId" to updated.id,
                    "savedRideId" to synced.id,
                    "amount" to synced.price,
                    "distanceKm" to synced.totalDistanceKm
                )
            } else {
                logLinkedRideNotFound(updated, "update_no_linked_saved_ride")
            }
        }
        return updated
    }

    suspend fun delete(record: TrackingRecord): Boolean {
        RadarLogger.i(
            "KM_V2_TRACKING",
            "KM_V2_TRACKING_RECORD_DELETE_REQUESTED",
            "id" to record.id,
            "type" to record.type,
            "linkedSavedRideId" to record.linkedSavedRideId
        )
        if (record.type == TrackingRecordType.PRIVATE_RIDE) {
            val linkedRide = findLinkedSavedRide(record)
            if (linkedRide != null) {
                savedRideRepository.deleteRide(linkedRide.id)
                RadarLogger.i(
                    "KM_V2_TRACKING",
                    "KM_V2_TRACKING_LINKED_SAVED_RIDE_DELETED",
                    "trackingRecordId" to record.id,
                    "savedRideId" to linkedRide.id
                )
            } else {
                logLinkedRideNotFound(record, "delete_no_linked_saved_ride")
            }
        }
        return trackingRecordRepository.delete(record.id)
    }

    fun createSavedRide(record: TrackingRecord): SavedRide {
        val amount = record.amount ?: 0.0
        val distanceKm = record.distanceKm
        return SavedRide(
            id = UUID.randomUUID().toString(),
            sourceSeenOfferId = record.id,
            platform = RidePlatform.UNKNOWN,
            price = amount,
            valuePerKm = if (distanceKm != null && distanceKm > 0.0) amount / distanceKm else null,
            pickupDistanceKm = null,
            pickupTimeMin = null,
            tripDistanceKm = distanceKm,
            tripTimeMin = record.durationSeconds / 60.0,
            totalDistanceKm = distanceKm,
            estimatedTotalTimeMin = record.durationSeconds / 60.0,
            productName = "Corrida particular",
            originPreview = null,
            destinationPreview = null,
            acceptedAtMs = record.endedAtMs,
            createdAtMs = record.createdAtMs,
            updatedAtMs = record.createdAtMs,
            source = SavedRideSource.PRIVATE_RIDE
        )
    }

    private fun findLinkedSavedRide(record: TrackingRecord): SavedRide? {
        val rides = savedRideRepository.listSavedRides(limit = 500)
        record.linkedSavedRideId?.let { linkedId ->
            rides.firstOrNull { it.id == linkedId }?.let { return it }
        }
        rides.firstOrNull {
            it.source == SavedRideSource.PRIVATE_RIDE && it.sourceSeenOfferId == record.id
        }?.let { return it }
        return rides.firstOrNull {
            it.source == SavedRideSource.PRIVATE_RIDE &&
                it.price == record.amount &&
                abs(it.acceptedAtMs - record.endedAtMs) <= FALLBACK_TIME_WINDOW_MS
        }
    }

    private fun logLinkedRideNotFound(record: TrackingRecord, reason: String) {
        RadarLogger.w(
            "KM_V2_TRACKING",
            "KM_V2_TRACKING_LINKED_SAVED_RIDE_NOT_FOUND",
            "trackingRecordId" to record.id,
            "reason" to reason
        )
    }

    private companion object {
        const val FALLBACK_TIME_WINDOW_MS = 5 * 60 * 1000L
    }
}
