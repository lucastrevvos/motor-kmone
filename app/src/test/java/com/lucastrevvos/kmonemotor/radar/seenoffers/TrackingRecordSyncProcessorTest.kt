package com.lucastrevvos.kmonemotor.radar.seenoffers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRecordSyncProcessorTest {
    @Test
    fun deleteDisplacement_removesOnlyTrackingRecord() = runBlocking {
        val trackingRepo = MemoryTrackingRecordRepository()
        val rideRepo = MemorySavedRideRepository()
        val processor = TrackingRecordSyncProcessor(trackingRepo, rideRepo)
        val record = trackingRecord(type = TrackingRecordType.DISPLACEMENT)
        trackingRepo.save(record)

        processor.delete(record)

        assertTrue(trackingRepo.records.isEmpty())
        assertTrue(rideRepo.rides.isEmpty())
    }

    @Test
    fun deletePrivateRide_removesTrackingRecordAndLinkedSavedRide() = runBlocking {
        val trackingRepo = MemoryTrackingRecordRepository()
        val rideRepo = MemorySavedRideRepository()
        val processor = TrackingRecordSyncProcessor(trackingRepo, rideRepo)
        val record = trackingRecord(type = TrackingRecordType.PRIVATE_RIDE, amount = 35.0, linkedSavedRideId = "ride-1")
        trackingRepo.save(record)
        rideRepo.saveRide(privateRide(id = "ride-1", trackingRecordId = record.id, price = 35.0))

        processor.delete(record)

        assertTrue(trackingRepo.records.isEmpty())
        assertTrue(rideRepo.rides.isEmpty())
    }

    @Test
    fun deletePrivateRideWithoutLinkedSavedRide_doesNotCrashAndDeletesTrackingRecord() = runBlocking {
        val trackingRepo = MemoryTrackingRecordRepository()
        val rideRepo = MemorySavedRideRepository()
        val processor = TrackingRecordSyncProcessor(trackingRepo, rideRepo)
        val record = trackingRecord(type = TrackingRecordType.PRIVATE_RIDE, amount = 35.0)
        trackingRepo.save(record)

        processor.delete(record)

        assertTrue(trackingRepo.records.isEmpty())
    }

    @Test
    fun editDisplacementDistance_doesNotCreateRevenue() = runBlocking {
        val trackingRepo = MemoryTrackingRecordRepository()
        val rideRepo = MemorySavedRideRepository()
        val processor = TrackingRecordSyncProcessor(trackingRepo, rideRepo)
        val record = trackingRecord(type = TrackingRecordType.DISPLACEMENT, distanceKm = 2.0)
        trackingRepo.save(record)

        processor.update(record.copy(distanceKm = 6.4))

        assertEquals(6.4, trackingRepo.records.single().distanceKm ?: 0.0, 0.001)
        assertTrue(rideRepo.rides.isEmpty())
    }

    @Test
    fun editPrivateRideAmountAndDistance_updatesLinkedSavedRide() = runBlocking {
        val trackingRepo = MemoryTrackingRecordRepository()
        val rideRepo = MemorySavedRideRepository()
        val processor = TrackingRecordSyncProcessor(trackingRepo, rideRepo)
        val record = trackingRecord(
            type = TrackingRecordType.PRIVATE_RIDE,
            amount = 30.0,
            distanceKm = 5.0,
            linkedSavedRideId = "ride-1"
        )
        trackingRepo.save(record)
        rideRepo.saveRide(privateRide(id = "ride-1", trackingRecordId = record.id, price = 30.0, distanceKm = 5.0))

        processor.update(record.copy(amount = 45.0, distanceKm = 9.0))

        val ride = rideRepo.rides.single()
        assertEquals(45.0, ride.price ?: 0.0, 0.001)
        assertEquals(9.0, ride.totalDistanceKm ?: 0.0, 0.001)
        assertEquals("ride-1", trackingRepo.records.single().linkedSavedRideId)
    }

    private class MemoryTrackingRecordRepository : TrackingRecordRepository {
        val records = mutableListOf<TrackingRecord>()

        override suspend fun list(limit: Int) = records.take(limit)
        override suspend fun save(record: TrackingRecord): TrackingRecord {
            records.removeAll { it.id == record.id }
            records += record
            return record
        }
        override suspend fun update(record: TrackingRecord): TrackingRecord? {
            val index = records.indexOfFirst { it.id == record.id }
            if (index < 0) return null
            records[index] = record
            return record
        }
        override suspend fun delete(id: String): Boolean = records.removeIf { it.id == id }
        override suspend fun deleteAll() {
            records.clear()
        }
    }

    private class MemorySavedRideRepository : SavedRideRepository {
        val rides = mutableListOf<SavedRide>()

        override fun saveRide(ride: SavedRide): SavedRide {
            rides.removeAll { it.id == ride.id }
            rides += ride
            return ride
        }
        override fun listSavedRides(limit: Int) = rides.take(limit)
        override fun updateRide(ride: SavedRide): SavedRide? {
            val index = rides.indexOfFirst { it.id == ride.id }
            if (index < 0) return null
            rides[index] = ride
            return ride
        }
        override fun deleteRide(id: String): Boolean = rides.removeIf { it.id == id }
    }

    private fun trackingRecord(
        type: TrackingRecordType,
        amount: Double? = null,
        distanceKm: Double? = null,
        linkedSavedRideId: String? = null
    ) = TrackingRecord(
        id = "tracking-1",
        type = type,
        startedAtMs = 1_000L,
        endedAtMs = 61_000L,
        durationSeconds = 60L,
        distanceKm = distanceKm,
        amount = amount,
        notes = null,
        createdAtMs = 61_000L,
        linkedSavedRideId = linkedSavedRideId
    )

    private fun privateRide(
        id: String,
        trackingRecordId: String,
        price: Double,
        distanceKm: Double? = null
    ) = SavedRide(
        id = id,
        sourceSeenOfferId = trackingRecordId,
        platform = RidePlatform.UNKNOWN,
        price = price,
        valuePerKm = null,
        pickupDistanceKm = null,
        pickupTimeMin = null,
        tripDistanceKm = distanceKm,
        tripTimeMin = null,
        totalDistanceKm = distanceKm,
        estimatedTotalTimeMin = null,
        productName = "Corrida particular",
        originPreview = null,
        destinationPreview = null,
        acceptedAtMs = 61_000L,
        createdAtMs = 61_000L,
        updatedAtMs = 61_000L,
        source = SavedRideSource.PRIVATE_RIDE
    )
}
