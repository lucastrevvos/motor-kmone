package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint

data class OfferCandidateKey(
    val platform: PlatformTextHint,
    val mainPrice: Double?,
    val mainPriceBucket: Double?,
    val primaryDistanceKm: Double?,
    val primaryDistanceBucketKm: Double?,
    val primaryTimeMinutes: Double?,
    val primaryTimeBucketMinutes: Int?,
    val valuePerKm: Double?,
    val valuePerKmBucket: Double?,
    val routeTextHash: String?
)
