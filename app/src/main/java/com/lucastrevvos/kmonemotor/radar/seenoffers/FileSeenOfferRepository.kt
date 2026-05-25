package com.lucastrevvos.kmonemotor.radar.seenoffers

import android.os.Looper
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.io.File
import java.util.Base64

class FileSeenOfferRepository(
    private val storageFile: File,
    private val mergePolicy: SeenOfferMergePolicy = SeenOfferMergePolicy()
) : SeenOfferRepository {
    private val lock = Any()

    override fun saveSeenOffer(offer: SeenOffer): SeenOfferSaveResult = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("saveSeenOffer")
        val offers = loadOffers().toMutableList()
        val existing = offers.firstOrNull { it.observationId == offer.observationId }
        if (existing != null) {
            return SeenOfferSaveResult(false, existing, "existing_observation_already_saved").also {
                logDuration("saveSeenOffer", startedAt)
            }
        }
        val duplicate = offers
            .asReversed()
            .firstOrNull { mergePolicy.isSameOffer(candidate = offer, existing = it) }
        if (duplicate != null) {
            val platformResolution = mergePolicy.resolvePlatformResolution(
                candidate = offer,
                existing = duplicate
            )
            if (platformResolution != null && offer.platform != platformResolution.effectiveCandidatePlatform) {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_PLATFORM_CONTEXTUAL_OVERRIDE",
                    "from" to offer.platform,
                    "to" to platformResolution.effectiveCandidatePlatform,
                    "reason" to if (
                        platformResolution.effectiveCandidatePlatform == RidePlatform.NINETY_NINE
                    ) {
                        "strong_99_text_signals_or_recent_99_offer"
                    } else {
                        platformResolution.reason
                    },
                    "newObservationId" to offer.observationId,
                    "existingSeenOfferId" to duplicate.id
                )
            }
            val existingQuality = mergePolicy.qualityScore(duplicate)
            val newQuality = mergePolicy.qualityScore(offer)
            val manualAuthority = mergePolicy.isManualRecentAuthorityCandidate(
                candidate = offer,
                existing = duplicate
            )
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_MERGE_CANDIDATE",
                "newObservationId" to offer.observationId,
                "existingSeenOfferId" to duplicate.id,
                "existingQuality" to existingQuality,
                "newQuality" to newQuality,
                "existingTriggerSource" to duplicate.sourceTrigger,
                "newTriggerSource" to offer.sourceTrigger,
                "reason" to if (manualAuthority) "manual_recent_authority" else "duplicate_window_match"
            )
            if (mergePolicy.isEconomicsDowngrade(existing = duplicate, candidate = offer)) {
                if (duplicate.platform == RidePlatform.UBER && offer.platform == RidePlatform.UBER) {
                    RadarLogger.i(
                        "KM_V2_SEEN",
                        "KM_V2_UBER_ECONOMICS_SUSPICIOUS_VALUE_IGNORED",
                        "existingSeenOfferId" to duplicate.id,
                        "candidateObservationId" to offer.observationId,
                        "candidateValuePerKm" to offer.valuePerKm,
                        "candidateTotalDistanceKm" to offer.totalDistanceKm,
                        "reason" to "candidate_uber_economics_suspicious"
                    )
                } else {
                    RadarLogger.i(
                        "KM_V2_SEEN",
                        "KM_V2_SAVED_OFFER_ECONOMICS_DOWNGRADE_IGNORED",
                        "existingSeenOfferId" to duplicate.id,
                        "newObservationId" to offer.observationId,
                        "existingTotalDistanceKm" to duplicate.totalDistanceKm,
                        "incomingTotalDistanceKm" to offer.totalDistanceKm,
                        "existingValuePerKm" to duplicate.valuePerKm,
                        "incomingValuePerKm" to offer.valuePerKm
                    )
                }
                return SeenOfferSaveResult(false, duplicate, "weaker_duplicate_offer_recently_saved").also {
                    logDuration("saveSeenOffer", startedAt)
                }
            }
            if (mergePolicy.isPartialEconomicsDowngrade(existing = duplicate, candidate = offer)) {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SAVED_OFFER_PARTIAL_ECONOMICS_DOWNGRADE_IGNORED",
                    "existingSeenOfferId" to duplicate.id,
                    "candidateObservationId" to offer.observationId,
                    "existingTotalDistanceKm" to duplicate.totalDistanceKm,
                    "candidateTotalDistanceKm" to offer.totalDistanceKm,
                    "existingHasPickup" to (duplicate.pickupDistanceKm != null),
                    "existingHasTrip" to (duplicate.tripDistanceKm != null),
                    "candidateHasPickup" to (offer.pickupDistanceKm != null),
                    "candidateHasTrip" to (offer.tripDistanceKm != null),
                    "reason" to "existing_complete_candidate_partial"
                )
                return SeenOfferSaveResult(false, duplicate, "weaker_duplicate_offer_recently_saved").also {
                    logDuration("saveSeenOffer", startedAt)
                }
            }
            if (newQuality > existingQuality) {
                val merged = mergePolicy.mergeBetter(duplicate, offer)
                offers.replaceAll { current -> if (current.id == duplicate.id) merged else current }
                persistOffers(offers)
                val reason = if (manualAuthority) {
                    "manual_recent_authority_better_auto_merged_silently"
                } else {
                    "merged_better_version"
                }
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SEEN_OFFER_MERGED_BETTER_VERSION",
                    "seenOfferId" to duplicate.id,
                    "oldQuality" to existingQuality,
                    "newQuality" to newQuality,
                    "reason" to if (manualAuthority) "manual_recent_authority" else "quality_better_version"
                )
                return SeenOfferSaveResult(true, merged, reason).also {
                    logDuration("saveSeenOffer", startedAt)
                }
            }
            val reason = if (manualAuthority) {
                "manual_recent_authority_weaker_auto_ignored"
            } else {
                "weaker_duplicate_offer_recently_saved"
            }
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_DEDUPE_SKIPPED_WEAKER_VERSION",
                "existingSeenOfferId" to duplicate.id,
                "oldQuality" to existingQuality,
                "newQuality" to newQuality,
                "reason" to if (manualAuthority) "manual_recent_authority" else "weaker_duplicate_offer_recently_saved"
            )
            return SeenOfferSaveResult(false, duplicate, reason).also {
                logDuration("saveSeenOffer", startedAt)
            }
        }
        offers += offer
        persistOffers(offers)
        return SeenOfferSaveResult(true, offer, "saved").also {
            logDuration("saveSeenOffer", startedAt)
        }
    }

    override fun listSeenOffers(limit: Int): List<SeenOffer> = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("listSeenOffers")
        loadOffers().sortedByDescending { it.createdAtMs }.take(limit).also {
            logDuration("listSeenOffers", startedAt, "count" to it.size, "limit" to limit)
        }
    }

    override fun getSeenOfferById(id: String): SeenOffer? = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("getSeenOfferById")
        loadOffers().firstOrNull { it.id == id }.also {
            logDuration("getSeenOfferById", startedAt, "found" to (it != null))
        }
    }

    override fun updateSeenOffer(offer: SeenOffer): SeenOffer? = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("updateSeenOffer")
        val offers = loadOffers().toMutableList()
        val index = offers.indexOfFirst { it.id == offer.id }
        if (index == -1) return null.also {
            logDuration("updateSeenOffer", startedAt, "updated" to false)
        }
        offers[index] = offer
        persistOffers(offers)
        offer.also {
            logDuration("updateSeenOffer", startedAt, "updated" to true)
        }
    }

    override fun updateSeenOfferStatus(id: String, status: SeenOfferStatus): SeenOffer? = synchronized(lock) {
        val startedAt = System.nanoTime()
        logMainThreadIoIfNeeded("updateSeenOfferStatus")
        val offers = loadOffers().toMutableList()
        val existing = offers.firstOrNull { it.id == id } ?: return null.also {
            logDuration("updateSeenOfferStatus", startedAt, "updated" to false)
        }
        val updated = existing.copy(status = status, updatedAtMs = System.currentTimeMillis())
        offers.replaceAll { current -> if (current.id == id) updated else current }
        persistOffers(offers)
        updated.also {
            logDuration("updateSeenOfferStatus", startedAt, "updated" to true, "status" to status.name)
        }
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

    private fun logDuration(operation: String, startedAt: Long, vararg extras: Pair<String, Any?>) {
        RadarLogger.i(
            "KM_V2_PERF",
            "KM_V2_PERF_FILE_SEEN_OFFER_REPOSITORY_OPERATION",
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
                "repository" to "FileSeenOfferRepository",
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
