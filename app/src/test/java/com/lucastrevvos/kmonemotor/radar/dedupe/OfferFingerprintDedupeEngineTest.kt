package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.ExtractedNumericCandidate
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferFingerprintDedupeEngineTest {
    private val store = OfferFingerprintDedupeStore()
    private val engine = OfferFingerprintDedupeEngine(store)

    @Test
    fun offerLike_createsNewCluster() {
        val result = engine.process(offerInput("obs-1"))

        assertEquals(OfferDedupeDecision.NEW_OFFER_CANDIDATE, result.decision)
        assertEquals(1, result.activeClusterCount)
        assertNotNull(result.clusterId)
    }

    @Test
    fun nonOffer_doesNotCreateCluster() {
        val result = engine.process(
            offerInput("obs-non", kind = OfferTextFingerprintKind.NON_OFFER)
        )

        assertEquals(OfferDedupeDecision.NON_OFFER_IGNORED, result.decision)
        assertEquals(0, result.activeClusterCount)
        assertNull(result.clusterId)
    }

    @Test
    fun unknown_doesNotCreateCluster() {
        val result = engine.process(
            offerInput("obs-unknown", kind = OfferTextFingerprintKind.UNKNOWN)
        )

        assertEquals(OfferDedupeDecision.UNKNOWN_IGNORED, result.decision)
        assertEquals(0, result.activeClusterCount)
    }

    @Test
    fun samePriceDistanceTimeWithinWindow_groupsIntoSameCluster() {
        val first = engine.process(offerInput("obs-1", createdAtMs = 1_000L))
        val second = engine.process(offerInput("obs-2", createdAtMs = 2_000L))

        assertEquals(OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER, second.decision)
        assertEquals(first.clusterId, second.clusterId)
    }

    @Test
    fun differentPrice_createsNewCluster() {
        val first = engine.process(offerInput("obs-1", createdAtMs = 1_000L, price = 12.10))
        val second = engine.process(offerInput("obs-2", createdAtMs = 2_000L, price = 54.50))

        assertEquals(OfferDedupeDecision.NEW_OFFER_CANDIDATE, second.decision)
        assertNotEquals(first.clusterId, second.clusterId)
        assertEquals(2, second.activeClusterCount)
    }

    @Test
    fun veryDifferentDistanceAndTime_createsNewCluster() {
        val first = engine.process(
            offerInput("obs-1", createdAtMs = 1_000L, distanceKm = 1.8, timeMinutes = 4.0, routeHash = "route-a")
        )
        val second = engine.process(
            offerInput("obs-2", createdAtMs = 2_000L, distanceKm = 43.8, timeMinutes = 63.0, routeHash = "route-b")
        )

        assertEquals(OfferDedupeDecision.NEW_OFFER_CANDIDATE, second.decision)
        assertNotEquals(first.clusterId, second.clusterId)
    }

    @Test
    fun manualHigherQuality_updatesClusterBest() {
        val first = engine.process(
            offerInput(
                "obs-auto",
                createdAtMs = 1_000L,
                platform = PlatformTextHint.UNKNOWN,
                offerLikeScore = 7,
                isManual = false,
                preview = "R$ 12,10 4 min"
            )
        )
        val second = engine.process(
            offerInput(
                "obs-manual",
                createdAtMs = 2_000L,
                platform = PlatformTextHint.UBER,
                offerLikeScore = 10,
                isManual = true,
                preview = "UberX R$ 12,10 4 min 1,8 km"
            )
        )

        assertEquals(OfferDedupeDecision.SAME_OFFER_UPDATED, second.decision)
        assertEquals(first.clusterId, second.clusterId)
        assertTrue(second.isBestForCluster)
        assertEquals("UberX R$ 12,10 4 min 1,8 km", second.bestOfferPreview)
    }

    @Test
    fun weakerAutomaticReading_doesNotReplaceBest() {
        engine.process(
            offerInput(
                "obs-best",
                createdAtMs = 1_000L,
                offerLikeScore = 12,
                preview = "R$ 12,10 4 min 1,8 km"
            )
        )
        val second = engine.process(
            offerInput(
                "obs-weaker",
                createdAtMs = 2_000L,
                offerLikeScore = 6,
                preview = "R$ 12,10"
            )
        )

        assertEquals(OfferDedupeDecision.SAME_OFFER_IGNORED_WEAKER, second.decision)
        assertEquals("R$ 12,10 4 min 1,8 km", second.bestOfferPreview)
    }

    @Test
    fun clusterExpiresAfterTtl() {
        val first = engine.process(offerInput("obs-1", createdAtMs = 1_000L))
        val second = engine.process(offerInput("obs-2", createdAtMs = 32_000L))

        assertEquals(OfferDedupeDecision.NEW_OFFER_CANDIDATE, second.decision)
        assertNotEquals(first.clusterId, second.clusterId)
        assertEquals(1, second.activeClusterCount)
    }

    @Test
    fun routeHashEqualHelpsGrouping() {
        val first = engine.process(
            offerInput("obs-1", createdAtMs = 1_000L, distanceKm = 2.0, routeHash = "route-a")
        )
        val second = engine.process(
            offerInput("obs-2", createdAtMs = 2_000L, distanceKm = 9.0, routeHash = "route-a")
        )

        assertEquals(first.clusterId, second.clusterId)
        assertEquals("same_price_route", second.reason)
    }

    @Test
    fun platformUnknownIsCompatibleWhenFieldsMatch() {
        val first = engine.process(
            offerInput("obs-1", createdAtMs = 1_000L, platform = PlatformTextHint.UNKNOWN)
        )
        val second = engine.process(
            offerInput("obs-2", createdAtMs = 2_000L, platform = PlatformTextHint.NINETY_NINE)
        )

        assertEquals(first.clusterId, second.clusterId)
    }

    @Test
    fun strongDifferentPlatformsDoNotMatch() {
        val first = engine.process(
            offerInput("obs-1", createdAtMs = 1_000L, platform = PlatformTextHint.UBER)
        )
        val second = engine.process(
            offerInput("obs-2", createdAtMs = 2_000L, platform = PlatformTextHint.NINETY_NINE)
        )

        assertEquals(OfferDedupeDecision.NEW_OFFER_CANDIDATE, second.decision)
        assertNotEquals(first.clusterId, second.clusterId)
    }

    @Test
    fun mainPricePrefersHigherPlausibleValueOverAuxiliaryFee() {
        val key = engine.buildKey(
            offerInput(
                "obs-price",
                prices = listOf(price(54.50), price(1.04))
            )
        )

        assertEquals(54.50, key.mainPrice ?: 0.0, 0.0)
    }

    @Test
    fun valuePerKmDoesNotBecomeMainPrice() {
        val key = engine.buildKey(
            offerInput(
                "obs-vpk",
                prices = listOf(price(8.61)),
                valuePerKm = listOf(valuePerKm(1.90), valuePerKm(1.41))
            )
        )

        assertEquals(8.61, key.mainPrice ?: 0.0, 0.0)
    }

    private fun offerInput(
        observationId: String,
        createdAtMs: Long = 1_000L,
        kind: OfferTextFingerprintKind = OfferTextFingerprintKind.OFFER_LIKE,
        platform: PlatformTextHint = PlatformTextHint.UBER,
        price: Double = 12.10,
        distanceKm: Double = 1.8,
        timeMinutes: Double = 4.0,
        offerLikeScore: Int = 10,
        isManual: Boolean = false,
        routeHash: String? = "route-default",
        preview: String = "R$ 12,10 4 min 1,8 km",
        prices: List<ExtractedNumericCandidate> = listOf(price(price)),
        valuePerKm: List<ExtractedNumericCandidate> = listOf(valuePerKm(2.30)),
        distances: List<ExtractedNumericCandidate> = listOf(distance(distanceKm)),
        times: List<ExtractedNumericCandidate> = listOf(time(timeMinutes))
    ) = OfferFingerprintDedupeInput(
        observationId = observationId,
        triggerSource = if (platform == PlatformTextHint.NINETY_NINE) {
            TriggerSource.NINETY_NINE_TREE_STRUCTURE
        } else {
            TriggerSource.UBER_STATE_TRANSITION
        },
        platformHint = platform,
        fingerprintKind = kind,
        rawTextHash = "raw-$observationId",
        routeTextHash = routeHash,
        prices = prices,
        valuePerKm = valuePerKm,
        distances = distances,
        times = times,
        offerLikeScore = offerLikeScore,
        nonOfferScore = 0,
        cropKind = CropKind.CENTER_CARD_AREA,
        isManual = isManual,
        analysisEpoch = if (isManual) 1L else 0L,
        capturedAtMs = createdAtMs,
        fingerprintCreatedAtMs = createdAtMs,
        rawTextPreview = preview
    )

    private fun price(value: Double) = ExtractedNumericCandidate(
        raw = value.toString(),
        normalizedValue = value,
        unit = "currency",
        kind = "price",
        confidence = 8
    )

    private fun valuePerKm(value: Double) = ExtractedNumericCandidate(
        raw = value.toString(),
        normalizedValue = value,
        unit = "currency_per_km",
        kind = "value_per_km",
        confidence = 8
    )

    private fun distance(valueKm: Double) = ExtractedNumericCandidate(
        raw = valueKm.toString(),
        normalizedValue = valueKm,
        unit = "km",
        kind = "distance",
        confidence = 8
    )

    private fun time(valueMinutes: Double) = ExtractedNumericCandidate(
        raw = valueMinutes.toString(),
        normalizedValue = valueMinutes,
        unit = "min",
        kind = "time",
        confidence = 8
    )
}
