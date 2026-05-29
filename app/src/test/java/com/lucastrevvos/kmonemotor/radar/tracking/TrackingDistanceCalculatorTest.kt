package com.lucastrevvos.kmonemotor.radar.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingDistanceCalculatorTest {
    private val calculator = TrackingDistanceCalculator()

    @Test
    fun firstPointDoesNotAddDistance() {
        val update = calculator.addPoint(TrackingDistanceState(), point(lat = -23.0, lon = -46.0, time = 1_000L))

        assertTrue(update.accepted)
        assertEquals(0.0, update.state.distanceKm, 0.0)
        assertEquals(1, update.state.pointCount)
    }

    @Test
    fun plausibleSecondPointAddsDistance() {
        val first = calculator.addPoint(TrackingDistanceState(), point(lat = -23.0, lon = -46.0, time = 1_000L)).state
        val second = calculator.addPoint(first, point(lat = -23.0001, lon = -46.0, time = 3_000L))

        assertTrue(second.accepted)
        assertTrue(second.state.distanceKm > 0.005)
    }

    @Test
    fun lowAccuracyPointIsRejected() {
        val update = calculator.addPoint(TrackingDistanceState(), point(accuracy = 80f))

        assertFalse(update.accepted)
        assertEquals("low_accuracy", update.reason)
        assertEquals(TrackingGpsStatus.LOW_ACCURACY, update.state.gpsStatus)
    }

    @Test
    fun tinyDeltaIsIgnored() {
        val first = calculator.addPoint(TrackingDistanceState(), point(lat = -23.0, lon = -46.0, time = 1_000L)).state
        val second = calculator.addPoint(first, point(lat = -23.000001, lon = -46.0, time = 3_000L))

        assertFalse(second.accepted)
        assertEquals("too_small_delta", second.reason)
        assertEquals(0.0, second.state.distanceKm, 0.0)
    }

    @Test
    fun tooLargeDeltaIsRejected() {
        val first = calculator.addPoint(TrackingDistanceState(), point(lat = -23.0, lon = -46.0, time = 1_000L)).state
        val second = calculator.addPoint(first, point(lat = -23.1, lon = -46.0, time = 3_000L))

        assertFalse(second.accepted)
        assertEquals("too_large_delta", second.reason)
    }

    @Test
    fun staleTimestampIsRejected() {
        val first = calculator.addPoint(TrackingDistanceState(), point(time = 3_000L)).state
        val second = calculator.addPoint(first, point(time = 2_000L))

        assertFalse(second.accepted)
        assertEquals("stale_timestamp", second.reason)
    }

    private fun point(
        lat: Double = -23.0,
        lon: Double = -46.0,
        accuracy: Float? = 10f,
        time: Long = 1_000L
    ) = LocationSnapshot(
        latitude = lat,
        longitude = lon,
        accuracyMeters = accuracy,
        timestampMs = time
    )
}
