package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import kotlin.math.abs
import kotlin.math.round

class OfferFingerprintDedupeEngine(
    private val store: OfferFingerprintDedupeStore = OfferFingerprintDedupeStore()
) {
    fun process(input: OfferFingerprintDedupeInput): OfferDedupeResult {
        val nowMs = input.fingerprintCreatedAtMs
        RadarLogger.i(
            "KM_V2_DEDUPE",
            "KM_V2_DEDUPE_INPUT_RECEIVED",
            "observationId" to input.observationId,
            "kind" to input.fingerprintKind,
            "platform" to input.platformHint,
            "manual" to input.isManual
        )
        val expired = store.removeExpired(nowMs)
        expired.forEach {
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_CLUSTER_EXPIRED",
                "clusterId" to it.clusterId,
                "lastObservationId" to it.bestFingerprint.observationId
            )
        }
        if (expired.isNotEmpty()) {
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_STORE_CLEANED",
                "expiredCount" to expired.size,
                "activeClusterCount" to store.snapshot().size
            )
        }

        when (input.fingerprintKind) {
            OfferTextFingerprintKind.NON_OFFER -> {
                RadarLogger.i(
                    "KM_V2_DEDUPE",
                    "KM_V2_DEDUPE_NON_OFFER_IGNORED",
                    "observationId" to input.observationId
                )
                return ignoredResult(OfferDedupeDecision.NON_OFFER_IGNORED, "non_offer", store.snapshot().size)
            }

            OfferTextFingerprintKind.UNKNOWN -> {
                RadarLogger.i(
                    "KM_V2_DEDUPE",
                    "KM_V2_DEDUPE_UNKNOWN_IGNORED",
                    "observationId" to input.observationId
                )
                return ignoredResult(OfferDedupeDecision.UNKNOWN_IGNORED, "unknown", store.snapshot().size)
            }

            OfferTextFingerprintKind.OFFER_LIKE -> Unit
        }

        val key = buildKey(input)
        RadarLogger.i(
            "KM_V2_DEDUPE",
            "KM_V2_DEDUPE_KEY_BUILT",
            "observationId" to input.observationId,
            "mainPrice" to key.mainPrice,
            "distanceKm" to key.primaryDistanceKm,
            "timeMin" to key.primaryTimeMinutes,
            "routeTextHash" to key.routeTextHash
        )
        val quality = calculateQuality(input, key)
        RadarLogger.i(
            "KM_V2_DEDUPE",
            "KM_V2_DEDUPE_QUALITY_CALCULATED",
            "observationId" to input.observationId,
            "quality" to quality.score,
            "reasons" to quality.reasons.joinToString(",")
        )
        val match = store.snapshot()
            .mapNotNull { cluster -> evaluateMatch(cluster, input, key, nowMs) }
            .maxWithOrNull(compareBy<MatchEvaluation> { it.score }.thenByDescending { it.cluster.updatedAtMs })

        if (match == null) {
            val cluster = OfferCandidateCluster(
                clusterId = store.newClusterId(),
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
                platform = key.platform,
                currentKey = key,
                bestFingerprint = input,
                bestQuality = quality,
                allObservationIds = listOf(input.observationId),
                updateCount = 1,
                manualUpdateCount = if (input.isManual) 1 else 0,
                automaticUpdateCount = if (input.isManual) 0 else 1,
                lastTriggerSource = input.triggerSource,
                status = OfferDedupeDecision.NEW_OFFER_CANDIDATE.name
            )
            store.upsert(cluster)
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_NEW_CLUSTER_CREATED",
                "clusterId" to cluster.clusterId,
                "observationId" to input.observationId,
                "quality" to quality.score
            )
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_CLUSTER_BEST_SELECTED",
                "clusterId" to cluster.clusterId,
                "observationId" to input.observationId
            )
            val result = OfferDedupeResult(
                decision = OfferDedupeDecision.NEW_OFFER_CANDIDATE,
                clusterId = cluster.clusterId,
                qualityScore = quality.score,
                reason = "no_match",
                isBestForCluster = true,
                activeClusterCount = store.snapshot().size,
                bestOfferPreview = input.rawTextPreview,
                bestOfferMainPrice = key.mainPrice,
                bestOfferPlatform = key.platform
            )
            logResult(result)
            return result
        }

        RadarLogger.i(
            "KM_V2_DEDUPE",
            "KM_V2_DEDUPE_MATCH_EVALUATED",
            "clusterId" to match.cluster.clusterId,
            "observationId" to input.observationId,
            "reason" to match.reason,
            "score" to match.score
        )
        val isBetter = quality.score > match.cluster.bestQuality.score
        val updatedCluster = match.cluster.copy(
            updatedAtMs = nowMs,
            platform = if (match.cluster.platform == PlatformTextHint.UNKNOWN) key.platform else match.cluster.platform,
            currentKey = key,
            bestFingerprint = if (isBetter) input else match.cluster.bestFingerprint,
            bestQuality = if (isBetter) quality else match.cluster.bestQuality,
            allObservationIds = (match.cluster.allObservationIds + input.observationId).distinct(),
            updateCount = match.cluster.updateCount + 1,
            manualUpdateCount = match.cluster.manualUpdateCount + if (input.isManual) 1 else 0,
            automaticUpdateCount = match.cluster.automaticUpdateCount + if (input.isManual) 0 else 1,
            lastTriggerSource = input.triggerSource,
            status = if (isBetter) OfferDedupeDecision.SAME_OFFER_UPDATED.name else OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER.name
        )
        store.upsert(updatedCluster)
        val decision = if (isBetter) {
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_SAME_OFFER_UPDATED",
                "clusterId" to updatedCluster.clusterId,
                "observationId" to input.observationId,
                "quality" to quality.score
            )
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_CLUSTER_BEST_SELECTED",
                "clusterId" to updatedCluster.clusterId,
                "observationId" to input.observationId
            )
            OfferDedupeDecision.SAME_OFFER_UPDATED
        } else {
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_SAME_OFFER_IGNORED_WEAKER",
                "clusterId" to updatedCluster.clusterId,
                "observationId" to input.observationId,
                "quality" to quality.score,
                "bestQuality" to updatedCluster.bestQuality.score
            )
            OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER
        }
        val result = OfferDedupeResult(
            decision = decision,
            clusterId = updatedCluster.clusterId,
            qualityScore = quality.score,
            reason = match.reason,
            matchedPreviousObservationId = match.cluster.bestFingerprint.observationId,
            isBestForCluster = isBetter,
            activeClusterCount = store.snapshot().size,
            bestOfferPreview = updatedCluster.bestFingerprint.rawTextPreview,
            bestOfferMainPrice = updatedCluster.currentKey.mainPrice ?: buildKey(updatedCluster.bestFingerprint).mainPrice,
            bestOfferPlatform = updatedCluster.platform
        )
        logResult(result)
        return result
    }

    fun buildKey(input: OfferFingerprintDedupeInput): OfferCandidateKey {
        val mainPrice = selectMainPrice(input.prices)
        val primaryDistanceKm = selectPrimaryDistanceKm(input.distances)
        val primaryTimeMinutes = selectPrimaryTimeMinutes(input.times)
        val valuePerKm = selectValuePerKm(input.valuePerKm)
        return OfferCandidateKey(
            platform = input.platformHint,
            mainPrice = mainPrice,
            mainPriceBucket = mainPrice?.let { roundToStep(it, 0.2) },
            primaryDistanceKm = primaryDistanceKm,
            primaryDistanceBucketKm = primaryDistanceKm?.let { roundToStep(it, 0.2) },
            primaryTimeMinutes = primaryTimeMinutes,
            primaryTimeBucketMinutes = primaryTimeMinutes?.let { roundToStep(it, 1.0).toInt() },
            valuePerKm = valuePerKm,
            valuePerKmBucket = valuePerKm?.let { roundToStep(it, 0.1) },
            routeTextHash = input.routeTextHash
        )
    }

    fun calculateQuality(input: OfferFingerprintDedupeInput, key: OfferCandidateKey): OfferCandidateQuality {
        var score = input.offerLikeScore - (input.nonOfferScore * 2)
        val reasons = mutableListOf<String>()
        if (key.mainPrice != null) {
            score += 5
            reasons += "main_price"
        }
        if (key.valuePerKm != null) {
            score += 5
            reasons += "value_per_km"
        }
        if (key.primaryDistanceKm != null) {
            score += 3
            reasons += "distance"
        }
        if (key.primaryTimeMinutes != null) {
            score += 3
            reasons += "time"
        }
        if (input.platformHint != PlatformTextHint.UNKNOWN) {
            score += 2
            reasons += "platform_known"
        }
        if (input.cropKind == CropKind.CENTER_CARD_AREA || input.cropKind == CropKind.LOWER_HALF) {
            score += 2
            reasons += "expected_crop"
        }
        if (input.isManual) {
            score += 2
            reasons += "manual_bonus"
        }
        if ((input.rawTextPreview?.length ?: 0) < 20) {
            score -= 3
            reasons += "short_preview_penalty"
        }
        if (input.prices.size >= 3 && key.mainPrice == null) {
            score -= 2
            reasons += "unclear_price_penalty"
        }
        return OfferCandidateQuality(score = score, reasons = reasons)
    }

    fun selectMainPrice(candidates: List<ExtractedNumericCandidate>): Double? {
        val normalized = candidates.mapNotNull { it.normalizedValue }
        return normalized.filter { it >= 5.0 }.maxOrNull() ?: normalized.maxOrNull()
    }

    fun selectPrimaryDistanceKm(candidates: List<ExtractedNumericCandidate>): Double? {
        val normalized = candidates.mapNotNull { candidate ->
            val value = candidate.normalizedValue ?: return@mapNotNull null
            when (candidate.unit?.lowercase()) {
                "km" -> value
                "m" -> value / 1000.0
                else -> null
            }
        }
        return normalized.firstOrNull { it > 0.3 && it <= 100.0 } ?: normalized.maxOrNull()
    }

    fun selectPrimaryTimeMinutes(candidates: List<ExtractedNumericCandidate>): Double? {
        val normalized = candidates.mapNotNull { it.normalizedValue }
        return normalized.firstOrNull { it in 1.0..90.0 } ?: normalized.maxOrNull()
    }

    private fun selectValuePerKm(candidates: List<ExtractedNumericCandidate>): Double? {
        return candidates.mapNotNull { it.normalizedValue }.maxOrNull()
    }

    private fun evaluateMatch(
        cluster: OfferCandidateCluster,
        input: OfferFingerprintDedupeInput,
        key: OfferCandidateKey,
        nowMs: Long
    ): MatchEvaluation? {
        if (nowMs - cluster.updatedAtMs > SAME_OFFER_WINDOW_MS) {
            return null
        }
        val previousKey = buildKey(cluster.bestFingerprint)
        val platformCompatible = key.platform == previousKey.platform ||
            key.platform == PlatformTextHint.UNKNOWN ||
            previousKey.platform == PlatformTextHint.UNKNOWN
        if (!platformCompatible) {
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEDUPE_PLATFORM_CONFLICT",
                "observationId" to input.observationId,
                "currentPlatform" to key.platform,
                "previousPlatform" to previousKey.platform,
                "clusterId" to cluster.clusterId
            )
            return null
        }
        val priceClose = bothPresentAndClose(key.mainPrice, previousKey.mainPrice, 0.2)
        val distanceClose = bothPresentAndClose(key.primaryDistanceKm, previousKey.primaryDistanceKm, 0.3)
        val timeClose = bothPresentAndClose(key.primaryTimeMinutes, previousKey.primaryTimeMinutes, 2.0)
        val routeMatch = !key.routeTextHash.isNullOrBlank() && key.routeTextHash == previousKey.routeTextHash
        val triggerFamilyMatch = triggerFamily(input.triggerSource) == triggerFamily(cluster.lastTriggerSource)
        val reason = when {
            routeMatch && priceClose -> "same_price_route"
            priceClose && distanceClose && timeClose -> "same_price_distance_time"
            triggerFamilyMatch && priceClose && (distanceClose || timeClose) -> "same_trigger_family_short_window"
            else -> null
        } ?: return null
        return MatchEvaluation(
            cluster = cluster,
            reason = reason,
            score = when (reason) {
                "same_price_route" -> 3
                "same_price_distance_time" -> 2
                else -> 1
            }
        )
    }

    private fun bothPresentAndClose(first: Double?, second: Double?, tolerance: Double): Boolean {
        return first != null && second != null && abs(first - second) <= tolerance
    }

    private fun roundToStep(value: Double, step: Double): Double {
        return round(value / step) * step
    }

    private fun triggerFamily(triggerSource: TriggerSource): String {
        return when (triggerSource) {
            TriggerSource.UBER_FLOATING_WINDOW,
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            TriggerSource.UBER_AUTO_BURST_RECOVERY,
            TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
            TriggerSource.UBER_STATE_TRANSITION -> "uber"

            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC,
            TriggerSource.NINETY_NINE_VISUAL_PROBE -> "ninety_nine"

            TriggerSource.MANUAL_SCREEN_ANALYSIS,
            TriggerSource.MANUAL_DEBUG -> "manual"

            TriggerSource.DOMINANT_WINDOW_CHANGE -> "dominant_window"
        }
    }

    private fun ignoredResult(
        decision: OfferDedupeDecision,
        reason: String,
        activeClusterCount: Int
    ): OfferDedupeResult {
        val result = OfferDedupeResult(
            decision = decision,
            clusterId = null,
            qualityScore = null,
            reason = reason,
            activeClusterCount = activeClusterCount
        )
        logResult(result)
        return result
    }

    private fun logResult(result: OfferDedupeResult) {
        RadarLogger.i(
            "KM_V2_DEDUPE",
            "KM_V2_DEDUPE_RESULT",
            "decision" to result.decision,
            "clusterId" to result.clusterId,
            "quality" to result.qualityScore,
            "reason" to result.reason,
            "isBest" to result.isBestForCluster,
            "activeClusterCount" to result.activeClusterCount
        )
    }

    private data class MatchEvaluation(
        val cluster: OfferCandidateCluster,
        val reason: String,
        val score: Int
    )

    companion object {
        const val SAME_OFFER_WINDOW_MS = 15_000L
        const val OFFER_CLUSTER_TTL_MS = 30_000L
    }
}
