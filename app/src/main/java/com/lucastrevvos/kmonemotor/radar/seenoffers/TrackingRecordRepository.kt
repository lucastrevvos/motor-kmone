package com.lucastrevvos.kmonemotor.radar.seenoffers

interface TrackingRecordRepository {
    suspend fun list(limit: Int = 500): List<TrackingRecord>
    suspend fun save(record: TrackingRecord): TrackingRecord
    suspend fun delete(id: String): Boolean
    suspend fun deleteAll()
}
