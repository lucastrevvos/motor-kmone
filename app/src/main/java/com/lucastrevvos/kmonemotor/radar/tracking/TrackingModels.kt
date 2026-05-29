package com.lucastrevvos.kmonemotor.radar.tracking

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMs: Long
)

enum class TrackingGpsStatus {
    IDLE,
    WAITING_PERMISSION,
    WAITING_FIRST_FIX,
    ACTIVE,
    LOW_ACCURACY,
    PERMISSION_DENIED,
    PROVIDER_DISABLED,
    ERROR
}

data class TrackingDistanceState(
    val distanceKm: Double = 0.0,
    val lastLocation: LocationSnapshot? = null,
    val pointCount: Int = 0,
    val gpsStatus: TrackingGpsStatus = TrackingGpsStatus.IDLE
)

data class TrackingDistanceUpdate(
    val state: TrackingDistanceState,
    val accepted: Boolean,
    val reason: String,
    val deltaMeters: Double?,
    val accuracyMeters: Float?
)

fun trackingGpsStatusLabel(status: TrackingGpsStatus): String {
    return when (status) {
        TrackingGpsStatus.IDLE -> "GPS inativo"
        TrackingGpsStatus.WAITING_PERMISSION -> "GPS: aguardando permissao"
        TrackingGpsStatus.WAITING_FIRST_FIX -> "GPS: aguardando sinal"
        TrackingGpsStatus.ACTIVE -> "GPS: ativo"
        TrackingGpsStatus.LOW_ACCURACY -> "GPS: baixa precisao"
        TrackingGpsStatus.PERMISSION_DENIED -> "GPS: permissao negada"
        TrackingGpsStatus.PROVIDER_DISABLED -> "GPS: desativado"
        TrackingGpsStatus.ERROR -> "GPS: erro"
    }
}
