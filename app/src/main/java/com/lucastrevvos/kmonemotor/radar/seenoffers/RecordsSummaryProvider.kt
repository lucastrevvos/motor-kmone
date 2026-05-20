package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

enum class RecordsPeriodFilter {
    DAY,
    WEEK,
    MONTH
}

data class RecordsSummary(
    val period: RecordsPeriodFilter,
    val totalEarned: Double,
    val ridesCount: Int,
    val totalKm: Double?,
    val averageValuePerKm: Double?,
    val fuelSpentAmount: Double?
)

class RecordsSummaryProvider(
    private val savedRideRepository: SavedRideRepository,
    private val fuelEntriesProvider: () -> List<FuelEntry> = { emptyList() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    fun loadSummary(
        period: RecordsPeriodFilter,
        nowMs: Long = System.currentTimeMillis()
    ): RecordsSummary {
        return summarize(
            rides = savedRideRepository.listSavedRides(limit = 500),
            fuelEntries = fuelEntriesProvider(),
            period = period,
            nowMs = nowMs
        )
    }

    fun summarize(
        rides: List<SavedRide>,
        fuelEntries: List<FuelEntry> = emptyList(),
        period: RecordsPeriodFilter,
        nowMs: Long = System.currentTimeMillis()
    ): RecordsSummary {
        val zoneId = zoneIdProvider()
        val filtered = rides.filter { it.acceptedAtMs.isInside(period, nowMs, zoneId) }
        val filteredFuelEntries = fuelEntries.filter { it.createdAtMs.isInside(period, nowMs, zoneId) }
        val totalEarned = filtered.mapNotNull { it.price }.sum()
        val totalKm = filtered.mapNotNull(::rideDistanceKm)
            .takeIf { it.isNotEmpty() }
            ?.sum()
        return RecordsSummary(
            period = period,
            totalEarned = totalEarned,
            ridesCount = filtered.size,
            totalKm = totalKm,
            averageValuePerKm = if (totalKm != null && totalKm > 0.0) totalEarned / totalKm else null,
            fuelSpentAmount = filteredFuelEntries.sumOf { it.amountBrl }
        )
    }

    fun filterRides(
        rides: List<SavedRide>,
        period: RecordsPeriodFilter,
        nowMs: Long = System.currentTimeMillis()
    ): List<SavedRide> {
        val zoneId = zoneIdProvider()
        return rides.filter { it.acceptedAtMs.isInside(period, nowMs, zoneId) }
            .sortedByDescending { it.acceptedAtMs }
    }

    fun filterFuelEntries(
        fuelEntries: List<FuelEntry>,
        period: RecordsPeriodFilter,
        nowMs: Long = System.currentTimeMillis()
    ): List<FuelEntry> {
        val zoneId = zoneIdProvider()
        return fuelEntries.filter { it.createdAtMs.isInside(period, nowMs, zoneId) }
            .sortedByDescending { it.createdAtMs }
    }

    private fun rideDistanceKm(ride: SavedRide): Double? {
        return RideEconomicsCalculator.resolveTotalDistanceKm(
            totalDistanceKm = ride.totalDistanceKm,
            pickupDistanceKm = ride.pickupDistanceKm,
            tripDistanceKm = ride.tripDistanceKm
        )
    }

    private fun Long.isInside(
        period: RecordsPeriodFilter,
        nowMs: Long,
        zoneId: ZoneId
    ): Boolean {
        val date = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
        val nowDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return when (period) {
            RecordsPeriodFilter.DAY -> date == nowDate
            RecordsPeriodFilter.WEEK -> {
                val startOfWeek = nowDate.with(DayOfWeek.MONDAY)
                val endOfWeek = startOfWeek.plusDays(6)
                !date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)
            }
            RecordsPeriodFilter.MONTH -> date.year == nowDate.year && date.month == nowDate.month
        }
    }
}
