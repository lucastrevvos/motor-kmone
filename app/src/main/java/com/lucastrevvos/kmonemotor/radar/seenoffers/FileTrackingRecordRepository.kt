package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileTrackingRecordRepository(
    private val storageFile: File
) : TrackingRecordRepository {
    private val lock = Any()

    override suspend fun list(limit: Int): List<TrackingRecord> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val startedAt = System.nanoTime()
            loadRecords().sortedByDescending { it.createdAtMs }.take(limit).also {
                logDuration("list", startedAt, "count" to it.size, "limit" to limit)
            }
        }
    }

    override suspend fun save(record: TrackingRecord): TrackingRecord = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val startedAt = System.nanoTime()
            val records = loadRecords().filterNot { it.id == record.id } + record
            persistRecords(records)
            record.also {
                logDuration("save", startedAt, "count" to records.size)
            }
        }
    }

    override suspend fun update(record: TrackingRecord): TrackingRecord? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val startedAt = System.nanoTime()
            val records = loadRecords().toMutableList()
            val index = records.indexOfFirst { it.id == record.id }
            if (index < 0) {
                null.also { logDuration("update", startedAt, "updated" to false) }
            } else {
                records[index] = record
                persistRecords(records)
                record.also { logDuration("update", startedAt, "updated" to true) }
            }
        }
    }

    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val startedAt = System.nanoTime()
            val records = loadRecords()
            val filtered = records.filterNot { it.id == id }
            if (filtered.size == records.size) {
                false.also { logDuration("delete", startedAt, "deleted" to false) }
            } else {
                persistRecords(filtered)
                true.also { logDuration("delete", startedAt, "deleted" to true, "remaining" to filtered.size) }
            }
        }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val startedAt = System.nanoTime()
                persistRecords(emptyList())
                logDuration("deleteAll", startedAt)
            }
        }
    }

    private fun loadRecords(): List<TrackingRecord> {
        if (!storageFile.exists()) return emptyList()
        return storageFile.readLines()
            .filter { it.isNotBlank() }
            .map { it.toTrackingRecord() }
    }

    private fun persistRecords(records: List<TrackingRecord>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(records.joinToString(separator = "\n") { it.serialize() })
    }

    private fun TrackingRecord.serialize(): String = listOf(
        encode(id),
        type.name,
        startedAtMs.toString(),
        endedAtMs.toString(),
        durationSeconds.toString(),
        encodeNullable(distanceKm),
        encodeNullable(amount),
        encodeNullable(notes),
        createdAtMs.toString(),
        encodeNullable(linkedSavedRideId)
    ).joinToString(separator = "\t")

    private fun String.toTrackingRecord(): TrackingRecord {
        val parts = split('\t')
        return TrackingRecord(
            id = decode(parts[0]),
            type = TrackingRecordType.valueOf(parts[1]),
            startedAtMs = parts[2].toLong(),
            endedAtMs = parts[3].toLong(),
            durationSeconds = parts[4].toLong(),
            distanceKm = decodeDouble(parts[5]),
            amount = decodeDouble(parts[6]),
            notes = decodeNullable(parts[7]),
            createdAtMs = parts[8].toLong(),
            linkedSavedRideId = parts.getOrNull(9)?.let(::decodeNullable)
        )
    }

    private fun encode(value: String): String {
        return Base64.getEncoder().encodeToString(value.toByteArray())
    }

    private fun encodeNullable(value: Any?): String {
        return value?.toString()?.let(::encode) ?: "-"
    }

    private fun decode(value: String): String {
        return String(Base64.getDecoder().decode(value))
    }

    private fun decodeNullable(value: String): String? {
        return if (value == "-") null else decode(value)
    }

    private fun decodeDouble(value: String): Double? {
        return decodeNullable(value)?.toDoubleOrNull()
    }

    private fun logDuration(operation: String, startedAt: Long, vararg extras: Pair<String, Any?>) {
        val fields = mutableListOf<Pair<String, Any?>>(
            "operation" to operation,
            "durationMs" to ((System.nanoTime() - startedAt) / 1_000_000.0)
        )
        fields += extras
        RadarLogger.i(
            "KM_V2_PERF",
            "KM_V2_PERF_FILE_TRACKING_RECORD_REPOSITORY_OPERATION",
            *fields.toTypedArray()
        )
    }
}
