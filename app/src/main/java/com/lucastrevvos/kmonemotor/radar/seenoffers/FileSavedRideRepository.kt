package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.io.File
import java.util.Base64

class FileSavedRideRepository(
    private val storageFile: File
) : SavedRideRepository {
    private val lock = Any()

    override fun saveRide(ride: SavedRide): SavedRide = synchronized(lock) {
        val rides = loadRides().toMutableList()
        rides += ride
        persistRides(rides)
        ride
    }

    override fun listSavedRides(limit: Int): List<SavedRide> = synchronized(lock) {
        loadRides().sortedByDescending { it.createdAtMs }.take(limit)
    }

    override fun deleteRide(id: String): Boolean = synchronized(lock) {
        val rides = loadRides()
        val filtered = rides.filterNot { it.id == id }
        if (filtered.size == rides.size) {
            return false
        }
        persistRides(filtered)
        true
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
}
