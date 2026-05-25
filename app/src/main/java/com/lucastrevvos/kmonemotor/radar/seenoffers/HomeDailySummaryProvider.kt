package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

enum class HomeGoalSource {
    CONFIGURED,
    FALLBACK,
    MISSING
}

data class HomeDailySummary(
    val dailyGoal: Double?,
    val goalSource: HomeGoalSource,
    val earnedToday: Double,
    val remainingToGoal: Double?,
    val progressPercent: Int?,
    val progressFraction: Float,
    val acceptedRidesCount: Int,
    val seenOffersCount: Int,
    val totalKmToday: Double?,
    val averageValuePerKm: Double?,
    val bestRideValuePerKm: Double?,
    val bestRidePrice: Double?,
    val bestRideProductName: String?,
    val isGoalReached: Boolean
)

class HomeDailySummaryProvider(
    private val seenOfferRepository: SeenOfferRepository,
    private val savedRideRepository: SavedRideRepository,
    private val configuredDailyGoalProvider: () -> Double? = { null },
    private val fallbackDailyGoalProvider: () -> Double? = { DEFAULT_FALLBACK_DAILY_GOAL_BRL },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    fun loadSummary(nowMs: Long = System.currentTimeMillis()): HomeDailySummary {
        return summarize(
            seenOffers = seenOfferRepository.listSeenOffers(limit = 500),
            savedRides = savedRideRepository.listSavedRides(limit = 500),
            nowMs = nowMs
        )
    }

    fun summarize(
        seenOffers: List<SeenOffer>,
        savedRides: List<SavedRide>,
        nowMs: Long = System.currentTimeMillis()
    ): HomeDailySummary {
        val startedAt = System.nanoTime()
        val zoneId = zoneIdProvider()
        val todaySeenOffers = seenOffers.filter { it.createdAtMs.isSameDayAs(nowMs, zoneId) }
        val todayRides = savedRides.filter { it.acceptedAtMs.isSameDayAs(nowMs, zoneId) }
        val goal = configuredDailyGoalProvider()
        val fallbackGoal = fallbackDailyGoalProvider()
        val effectiveGoal = goal ?: fallbackGoal
        val goalSource = when {
            goal != null -> HomeGoalSource.CONFIGURED
            fallbackGoal != null -> HomeGoalSource.FALLBACK
            else -> HomeGoalSource.MISSING
        }
        val earnedToday = todayRides.mapNotNull { it.price }.sum()
        val totalKmToday = todayRides.mapNotNull { rideDistanceKm(it) }
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val averageValuePerKm = if (earnedToday > 0.0 && totalKmToday != null && totalKmToday > 0.0) {
            earnedToday / totalKmToday
        } else {
            null
        }
        val bestRide = todayRides.maxByOrNull { rideValuePerKm(it) ?: Double.NEGATIVE_INFINITY }
        val bestRideValuePerKm = bestRide?.let(::rideValuePerKm)
        val remainingToGoal = effectiveGoal?.let { (it - earnedToday).coerceAtLeast(0.0) }
        val progressPercent = effectiveGoal?.takeIf { it > 0.0 }?.let {
            ((earnedToday / it) * 100.0).roundToInt()
        } ?: 0
        val progressFraction = effectiveGoal?.takeIf { it > 0.0 }?.let {
            (earnedToday / it).coerceIn(0.0, 1.0).toFloat()
        } ?: 0f
        return HomeDailySummary(
            dailyGoal = effectiveGoal,
            goalSource = goalSource,
            earnedToday = earnedToday,
            remainingToGoal = remainingToGoal,
            progressPercent = progressPercent,
            progressFraction = progressFraction,
            acceptedRidesCount = todayRides.size,
            seenOffersCount = todaySeenOffers.size,
            totalKmToday = totalKmToday,
            averageValuePerKm = averageValuePerKm,
            bestRideValuePerKm = bestRideValuePerKm,
            bestRidePrice = bestRide?.price,
            bestRideProductName = bestRide?.productName,
            isGoalReached = effectiveGoal != null && earnedToday >= effectiveGoal
        ).also { summary ->
            RadarLogger.i(
                "KM_V2_PERF",
                "KM_V2_PERF_HOME_SUMMARY_DURATION",
                "durationMs" to elapsedDurationMs(startedAt),
                "seenOfferCount" to seenOffers.size,
                "savedRideCount" to savedRides.size,
                "todayRideCount" to summary.acceptedRidesCount,
                "earnedToday" to summary.earnedToday
            )
        }
    }

    private fun rideDistanceKm(ride: SavedRide): Double? {
        return RideEconomicsCalculator.resolveRideEconomics(
            platform = ride.platform,
            price = ride.price,
            explicitValuePerKm = ride.valuePerKm,
            totalDistanceKm = ride.totalDistanceKm,
            pickupDistanceKm = ride.pickupDistanceKm,
            tripDistanceKm = ride.tripDistanceKm
        ).totalDistanceKm
    }

    private fun rideValuePerKm(ride: SavedRide): Double? {
        return RideEconomicsCalculator.resolveRideEconomics(
            platform = ride.platform,
            price = ride.price,
            explicitValuePerKm = ride.valuePerKm,
            totalDistanceKm = ride.totalDistanceKm,
            pickupDistanceKm = ride.pickupDistanceKm,
            tripDistanceKm = ride.tripDistanceKm
        ).valuePerKm
    }

    private fun Long.isSameDayAs(otherMs: Long, zoneId: ZoneId): Boolean {
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate() ==
            Instant.ofEpochMilli(otherMs).atZone(zoneId).toLocalDate()
    }

    companion object {
        const val DEFAULT_FALLBACK_DAILY_GOAL_BRL = 150.0
    }

    private fun elapsedDurationMs(startedAtNanos: Long): Long {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L
    }
}
