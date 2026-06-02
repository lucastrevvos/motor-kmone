package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.util.UUID

data class TrackingRecord(
    val id: String,
    val type: TrackingRecordType,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationSeconds: Long,
    val distanceKm: Double?,
    val amount: Double?,
    val notes: String?,
    val createdAtMs: Long,
    val linkedSavedRideId: String? = null
)

enum class TrackingRecordType {
    DISPLACEMENT,
    PRIVATE_RIDE
}

object TrackingRecordFactory {
    fun durationSeconds(startedAtMs: Long, endedAtMs: Long): Long {
        return ((endedAtMs - startedAtMs).coerceAtLeast(0L)) / 1000L
    }

    fun create(
        type: TrackingRecordType,
        startedAtMs: Long,
        endedAtMs: Long,
        distanceKm: Double? = null,
        amount: Double? = null,
        notes: String? = null,
        createdAtMs: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
        linkedSavedRideId: String? = null
    ): TrackingRecord {
        return TrackingRecord(
            id = id,
            type = type,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            durationSeconds = durationSeconds(startedAtMs, endedAtMs),
            distanceKm = distanceKm,
            amount = amount,
            notes = notes,
            createdAtMs = createdAtMs,
            linkedSavedRideId = linkedSavedRideId
        )
    }

    fun isValidPrivateRideAmount(amount: Double?): Boolean {
        return amount != null && amount > 0.0
    }
}

fun trackingRecordTypeLabel(type: TrackingRecordType): String {
    return when (type) {
        TrackingRecordType.DISPLACEMENT -> "Deslocamento"
        TrackingRecordType.PRIVATE_RIDE -> "Corrida particular"
    }
}
