package com.lucastrevvos.kmonemotor.radar.seenoffers

import android.os.Looper
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.io.File
import java.util.Base64

class FileSavedRideRepository(
    private val storageFile: File
) : SavedRideRepository {
    private val lock = Any()

    override fun saveRide(ride: SavedRide): SavedRide = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("saveRide")
        val rides = loadRides().toMutableList()
        rides += ride
        persistRides(rides)
        ride.also {
            logDuration("saveRide", startedAt, "count" to rides.size)
        }
    }

    override fun listSavedRides(limit: Int): List<SavedRide> = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("listSavedRides")
        loadRides().sortedByDescending { it.createdAtMs }.take(limit).also {
            logDuration("listSavedRides", startedAt, "count" to it.size, "limit" to limit)
        }
    }

    override fun updateRide(ride: SavedRide): SavedRide? = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("updateRide")
        val rides = loadRides().toMutableList()
        val index = rides.indexOfFirst { it.id == ride.id }
        if (index == -1) {
            return null.also {
                logDuration("updateRide", startedAt, "updated" to false)
            }
        }
        rides[index] = ride
        persistRides(rides)
        ride.also {
            logDuration("updateRide", startedAt, "updated" to true)
        }
    }

    override fun deleteRide(id: String): Boolean = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("deleteRide")
        val rides = loadRides()
        val filtered = rides.filterNot { it.id == id }
        if (filtered.size == rides.size) {
            return false.also {
                logDuration("deleteRide", startedAt, "deleted" to false)
            }
        }
        persistRides(filtered)
        true.also {
            logDuration("deleteRide", startedAt, "deleted" to true, "remaining" to filtered.size)
        }
    }

    private fun loadRides(): List<SavedRide> {
        if (!storageFile.exists()) return emptyList()
        return storageFile.readLines()
            .filter { it.isNotBlank() }
            .map { line -> line.toSavedRide() }
    }

    private fun persistRides(rides: List<SavedRide>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(rides.joinToString(separator = "\n") { it.serialize() })
    }

    private fun SavedRide.serialize(): String = listOf(
        encode(id),
        encodeNullable(sourceSeenOfferId),
        platform.name,
        encodeNullable(price),
        encodeNullable(valuePerKm),
        encodeNullable(pickupDistanceKm),
        encodeNullable(pickupTimeMin),
        encodeNullable(tripDistanceKm),
        encodeNullable(tripTimeMin),
        encodeNullable(totalDistanceKm),
        encodeNullable(estimatedTotalTimeMin),
        encodeNullable(productName),
        encodeNullable(originPreview),
        encodeNullable(destinationPreview),
        acceptedAtMs.toString(),
        createdAtMs.toString(),
        updatedAtMs.toString(),
        source.name
    ).joinToString(separator = "\t")

    private fun String.toSavedRide(): SavedRide {
        val parts = split('\t')
        val hasRoutePreview = parts.size >= 18
        return SavedRide(
            id = decode(parts[0]),
            sourceSeenOfferId = decodeNullable(parts[1]),
            platform = RidePlatform.valueOf(parts[2]),
            price = decodeDouble(parts[3]),
            valuePerKm = decodeDouble(parts[4]),
            pickupDistanceKm = decodeDouble(parts[5]),
            pickupTimeMin = decodeDouble(parts[6]),
            tripDistanceKm = decodeDouble(parts[7]),
            tripTimeMin = decodeDouble(parts[8]),
            totalDistanceKm = decodeDouble(parts[9]),
            estimatedTotalTimeMin = decodeDouble(parts[10]),
            productName = decodeNullable(parts[11]),
            originPreview = if (hasRoutePreview) decodeNullable(parts[12]) else null,
            destinationPreview = if (hasRoutePreview) decodeNullable(parts[13]) else null,
            acceptedAtMs = parts[if (hasRoutePreview) 14 else 12].toLong(),
            createdAtMs = parts[if (hasRoutePreview) 15 else 13].toLong(),
            updatedAtMs = parts[if (hasRoutePreview) 16 else 14].toLong(),
            source = SavedRideSource.valueOf(parts[if (hasRoutePreview) 17 else 15])
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
        RadarLogger.i(
            "KM_V2_PERF",
            "KM_V2_PERF_FILE_SAVED_RIDE_REPOSITORY_OPERATION",
            *arrayOf(
                "operation" to operation,
                "durationMs" to elapsedDurationMs(startedAt),
                *extras
            )
        )
    }

    private fun logMainThreadIoIfNeeded(operation: String) {
        if (isMainThreadSafe()) {
            RadarLogger.w(
                "KM_V2_PERF",
                "KM_V2_PERF_MAIN_THREAD_IO_DETECTED",
                "repository" to "FileSavedRideRepository",
                "operation" to operation
            )
        }
    }

    private fun elapsedDurationMs(startedAtNanos: Long): Long {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L
    }

    private fun isMainThreadSafe(): Boolean {
        return runCatching { Looper.myLooper() == Looper.getMainLooper() }
            .getOrDefault(Thread.currentThread().name == "main")
    }
}
