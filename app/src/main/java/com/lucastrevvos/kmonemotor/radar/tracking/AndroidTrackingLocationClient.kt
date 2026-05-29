package com.lucastrevvos.kmonemotor.radar.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class AndroidTrackingLocationClient(
    context: Context
) : TrackingLocationClient {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(
        onPoint: (LocationSnapshot) -> Unit,
        onStatus: (TrackingGpsStatus) -> Unit,
        onError: (Throwable) -> Unit
    ): Boolean {
        stop()
        if (!hasLocationPermission()) {
            onStatus(TrackingGpsStatus.PERMISSION_DENIED)
            return false
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onStatus(TrackingGpsStatus.PROVIDER_DISABLED)
            return false
        }

        val nextListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onPoint(
                    LocationSnapshot(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                        timestampMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    )
                )
            }

            override fun onProviderDisabled(provider: String) {
                onStatus(TrackingGpsStatus.PROVIDER_DISABLED)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = nextListener
        return runCatching {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MS,
                    MIN_UPDATE_DISTANCE_METERS,
                    nextListener
                )
            }
            onStatus(TrackingGpsStatus.WAITING_FIRST_FIX)
            true
        }.getOrElse { error ->
            listener = null
            onError(error)
            false
        }
    }

    override fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 2_000L
        const val MIN_UPDATE_DISTANCE_METERS = 5f
    }
}
