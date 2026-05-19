package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.io.File
import java.util.Base64
import kotlin.math.abs

class FileSeenOfferRepository(
    private val storageFile: File,
    private val duplicateWindowMs: Long = 30_000L
) : SeenOfferRepository {
    private val lock = Any()

    override fun saveSeenOffer(offer: SeenOffer): SeenOfferSaveResult = synchronized(lock) {
        val offers = loadOffers().toMutableList()
        val existing = offers.firstOrNull { it.observationId == offer.observationId }
        if (existing != null) {
            return SeenOfferSaveResult(false, existing, "existing_observation_already_saved")
        }
        val duplicate = offers
            .asReversed()
            .firstOrNull { isRecentDuplicate(candidate = offer, existing = it) }
        if (duplicate != null) {
            val refreshed = duplicate.copy(updatedAtMs = offer.updatedAtMs)
            offers.replaceAll { current -> if (current.id == duplicate.id) refreshed else current }
            persistOffers(offers)
            return SeenOfferSaveResult(false, refreshed, "similar_offer_recently_saved")
        }
        offers += offer
        persistOffers(offers)
        return SeenOfferSaveResult(true, offer, "saved")
    }

    override fun listSeenOffers(limit: Int): List<SeenOffer> = synchronized(lock) {
        loadOffers().sortedByDescending { it.createdAtMs }.take(limit)
    }

    override fun getSeenOfferById(id: String): SeenOffer? = synchronized(lock) {
        loadOffers().firstOrNull { it.id == id }
    }

    override fun updateSeenOfferStatus(id: String, status: SeenOfferStatus): SeenOffer? = synchronized(lock) {
        val offers = loadOffers().toMutableList()
        val existing = offers.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(status = status, updatedAtMs = System.currentTimeMillis())
        offers.replaceAll { current -> if (current.id == id) updated else current }
        persistOffers(offers)
        updated
    }

    private fun isRecentDuplicate(candidate: SeenOffer, existing: SeenOffer): Boolean {
        if (candidate.platform != existing.platform) return false
        if (abs(candidate.createdAtMs - existing.createdAtMs) > duplicateWindowMs) return false
        if (candidate.rawTextHash != null && candidate.rawTextHash == existing.rawTextHash) return true
        if (candidate.routeTextHash != null && candidate.routeTextHash == existing.routeTextHash &&
            similarMoney(candidate.price, existing.price)
        ) {
            return true
        }
        if (!similarMoney(candidate.price, existing.price)) return false
        if (!compatibleText(candidate.productName, existing.productName)) return false
        return similarDistance(candidate.pickupDistanceKm, existing.pickupDistanceKm) ||
            similarDistance(candidate.tripDistanceKm, existing.tripDistanceKm) ||
            similarDistance(candidate.totalDistanceKm, existing.totalDistanceKm)
    }

    private fun similarMoney(first: Double?, second: Double?): Boolean {
        if (first == null || second == null) return false
        return abs(first - second) <= 0.20
    }

    private fun similarDistance(first: Double?, second: Double?): Boolean {
        if (first == null || second == null) return false
        return abs(first - second) <= 0.30
    }

    private fun compatibleText(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return true
        return first.equals(second, ignoreCase = true)
    }

    private fun loadOffers(): List<SeenOffer> {
        if (!storageFile.exists()) return emptyList()
        return storageFile.readLines()
            .filter { it.isNotBlank() }
            .map { line -> line.toSeenOffer() }
    }

    private fun persistOffers(offers: List<SeenOffer>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(offers.joinToString(separator = "\n") { it.serialize() })
    }

    private fun SeenOffer.serialize(): String = listOf(
        encode(id),
        encode(observationId),
        platform.name,
        encode(sourceTrigger),
        status.name,
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
        encodeNullable(rawTextPreview),
        encodeNullable(score),
        encodeNullable(rawTextHash),
        encodeNullable(routeTextHash),
        createdAtMs.toString(),
        updatedAtMs.toString()
    ).joinToString(separator = "\t")

    private fun String.toSeenOffer(): SeenOffer {
        val parts = split('\t')
        return SeenOffer(
            id = decode(parts[0]),
            observationId = decode(parts[1]),
            platform = RidePlatform.valueOf(parts[2]),
            sourceTrigger = decode(parts[3]),
            status = SeenOfferStatus.valueOf(parts[4]),
            price = decodeDouble(parts[5]),
            valuePerKm = decodeDouble(parts[6]),
            pickupDistanceKm = decodeDouble(parts[7]),
            pickupTimeMin = decodeDouble(parts[8]),
            tripDistanceKm = decodeDouble(parts[9]),
            tripTimeMin = decodeDouble(parts[10]),
            totalDistanceKm = decodeDouble(parts[11]),
            estimatedTotalTimeMin = decodeDouble(parts[12]),
            productName = decodeNullable(parts[13]),
            originPreview = decodeNullable(parts[14]),
            destinationPreview = decodeNullable(parts[15]),
            rawTextPreview = decodeNullable(parts[16]),
            score = decodeInt(parts[17]),
            rawTextHash = decodeNullable(parts[18]),
            routeTextHash = decodeNullable(parts[19]),
            createdAtMs = parts[20].toLong(),
            updatedAtMs = parts[21].toLong()
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

    private fun decodeInt(value: String): Int? {
        return decodeNullable(value)?.toIntOrNull()
    }
}
