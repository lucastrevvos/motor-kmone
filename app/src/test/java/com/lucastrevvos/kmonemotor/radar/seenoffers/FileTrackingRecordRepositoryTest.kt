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
