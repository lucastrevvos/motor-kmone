package com.lucastrevvos.kmonemotor.radar.tracking

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TrackingDistanceCalculator(
    private val maxAccuracyMeters: Float = 50f,
    private val minDeltaMeters: Double = 5.0,
    private val maxDeltaMeters: Double = 2_000.0
) {
    fun addPoint(
        state: TrackingDistanceState,
        point: LocationSnapshot
    ): TrackingDistanceUpdate {
        val accuracy = point.accuracyMeters
        if (accuracy != null && accuracy > maxAccuracyMeters) {
            return TrackingDistanceUpdate(
                state = state.copy(gpsStatus = TrackingGpsStatus.LOW_ACCURACY),
                accepted = false,
                reason = "low_accuracy",
                deltaMeters = null,
                accuracyMeters = accuracy
            )
        }

        val last = state.lastLocation
        if (last == null) {
            return TrackingDistanceUpdate(
                state = state.copy(
                    lastLocation = point,
                    pointCount = state.pointCount + 1,
                    gpsStatus = TrackingGpsStatus.ACTIVE
                ),
                accepted = true,
                reason = "first_fix",
                deltaMeters = 0.0,
                accuracyMeters = accuracy
            )
        }

        if (point.timestampMs <= last.timestampMs) {
            return TrackingDistanceUpdate(
                state = state,
                accepted = false,
                reason = "stale_timestamp",
                deltaMeters = null,
                accuracyMeters = accuracy
            )
        }

        val deltaMeters = distanceMeters(last, point)
        if (deltaMeters < minDeltaMeters) {
            return TrackingDistanceUpdate(
                state = state.copy(lastLocation = point, gpsStatus = TrackingGpsStatus.ACTIVE),
                accepted = false,
                reason = "too_small_delta",
                deltaMeters = deltaMeters,
                accuracyMeters = accuracy
            )
        }
        if (deltaMeters > maxDeltaMeters) {
            return TrackingDistanceUpdate(
                state = state.copy(gpsStatus = TrackingGpsStatus.ERROR),
                accepted = false,
                reason = "too_large_delta",
                deltaMeters = deltaMeters,
                accuracyMeters = accuracy
            )
        }

        return TrackingDistanceUpdate(
            state = state.copy(
                distanceKm = state.distanceKm + (deltaMeters / 1000.0),
                lastLocation = point,
                pointCount = state.pointCount + 1,
                gpsStatus = TrackingGpsStatus.ACTIVE
            ),
            accepted = true,
            reason = "accepted",
            deltaMeters = deltaMeters,
            accuracyMeters = accuracy
        )
    }

    private fun distanceMeters(from: LocationSnapshot, to: LocationSnapshot): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(deltaLat / 2).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
