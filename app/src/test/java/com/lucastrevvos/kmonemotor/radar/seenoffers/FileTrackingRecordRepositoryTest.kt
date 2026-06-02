package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTrackingRecordRepositoryTest {
    @Test
    fun saveAndListTrackingRecord() = runBlocking {
        val repository = repository()
        val record = record(id = "tracking-1")

        repository.save(record)

        assertEquals(listOf(record), repository.list())
    }

    @Test
    fun deleteTrackingRecord() = runBlocking {
        val repository = repository()
        val record = record(id = "tracking-1")
        repository.save(record)

        assertTrue(repository.delete("tracking-1"))

        assertEquals(emptyList<TrackingRecord>(), repository.list())
    }

    @Test
    fun deleteAllClearsRecords() = runBlocking {
        val repository = repository()
        repository.save(record(id = "tracking-1"))
        repository.save(record(id = "tracking-2"))

        repository.deleteAll()

        assertEquals(emptyList<TrackingRecord>(), repository.list())
    }

    @Test
    fun updateTrackingRecord_changesAmountDistanceAndNotes() = runBlocking {
        val repository = repository()
        val original = record(id = "tracking-1")
        repository.save(original)

        val updated = repository.update(
            original.copy(distanceKm = 7.2, amount = 35.0, notes = "ajustado")
        )

        assertEquals(7.2, updated?.distanceKm ?: 0.0, 0.001)
        assertEquals(35.0, repository.list().single().amount ?: 0.0, 0.001)
        assertEquals("ajustado", repository.list().single().notes)
    }

    @Test
    fun linkedSavedRideId_roundTripsAsNullableAndFilled() = runBlocking {
        val repository = repository()
        val withoutLink = record(id = "tracking-1")
        val withLink = record(id = "tracking-2").copy(linkedSavedRideId = "ride-2")

        repository.save(withoutLink)
        repository.save(withLink)

        val records = repository.list().associateBy { it.id }
        assertEquals(null, records["tracking-1"]?.linkedSavedRideId)
        assertEquals("ride-2", records["tracking-2"]?.linkedSavedRideId)
    }

    private fun repository(): FileTrackingRecordRepository {
        val file = Files.createTempFile("tracking-records", ".txt").toFile()
        file.delete()
        return FileTrackingRecordRepository(file)
    }

    private fun record(id: String) = TrackingRecord(
        id = id,
        type = TrackingRecordType.DISPLACEMENT,
        startedAtMs = 1_000L,
        endedAtMs = 61_000L,
        durationSeconds = 60L,
        distanceKm = null,
        amount = null,
        notes = null,
        createdAtMs = 61_000L
    )
}
