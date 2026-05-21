package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SeenOfferUiModel(
    val id: String,
    val platformLabel: String,
    val productName: String?,
    val priceLabel: String,
    val timeLabel: String,
    val valuePerKmLabel: String,
    val pickupLabel: String,
    val tripLabel: String,
    val totalDistanceLabel: String,
    val originPreview: String?,
    val destinationPreview: String?,
    val warnings: List<String>
)

class SeenOfferUiMapper(
    private val auditor: SeenOfferConsistencyAuditor = SeenOfferConsistencyAuditor()
) {
    fun map(offer: SeenOffer): SeenOfferUiModel {
        val audit = auditor.audit(offer)
        val normalized = audit.normalizedOffer
        val economics = RideEconomicsCalculator.resolveRideEconomics(
            platform = normalized.platform,
            price = normalized.price,
            explicitValuePerKm = normalized.valuePerKm,
            totalDistanceKm = normalized.totalDistanceKm,
            pickupDistanceKm = normalized.pickupDistanceKm,
            tripDistanceKm = normalized.tripDistanceKm
        )

        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_VIEWS_CARD_RENDERED",
            "seenOfferId" to offer.id,
            "price" to normalized.price,
            "pickupDistanceKm" to normalized.pickupDistanceKm,
            "tripDistanceKm" to normalized.tripDistanceKm,
            "rawTotalDistanceKm" to offer.totalDistanceKm,
            "resolvedKm" to economics.totalDistanceKm,
            "displayedValuePerKm" to economics.valuePerKm
        )

        return SeenOfferUiModel(
            id = offer.id,
            platformLabel = platformLabel(normalized.platform),
            productName = normalized.productName?.takeUnless {
                it.equals(platformLabel(normalized.platform), ignoreCase = true)
            },
            priceLabel = formatMoney(normalized.price),
            timeLabel = formatTime(normalized.createdAtMs),
            valuePerKmLabel = economics.valuePerKm?.let(::formatPerKm) ?: "R$/km —",
            pickupLabel = formatMetric(normalized.pickupTimeMin, economics.pickupDistanceKm),
            tripLabel = formatMetric(normalized.tripTimeMin, economics.tripDistanceKm),
            totalDistanceLabel = economics.totalDistanceKm?.let(::formatKm) ?: "—",
            originPreview = normalized.originPreview ?: "—",
            destinationPreview = normalized.destinationPreview ?: "—",
            warnings = audit.warnings
        )
    }

    private fun platformLabel(platform: RidePlatform): String {
        return when (platform) {
            RidePlatform.UBER -> "Uber"
            RidePlatform.NINETY_NINE -> "99"
            RidePlatform.UNKNOWN -> "Indefinida"
        }
    }

    private fun formatMetric(timeMin: Double?, distanceKm: Double?): String {
        val parts = listOfNotNull(
            timeMin?.let { "${it.toInt()} min" },
            distanceKm?.takeIf { it > 0.0 }?.let(::formatKm)
        )
        return parts.joinToString(" • ").ifBlank { "—" }
    }

    private fun formatMoney(value: Double?): String {
        if (value == null) return "--"
        return "R$ " + String.format(Locale.US, "%.2f", value).replace(".", ",")
    }

    private fun formatPerKm(value: Double): String = "${formatMoney(value)}/km"

    private fun formatKm(value: Double): String {
        return String.format(Locale.US, "%.1f km", value).replace(".", ",")
    }

    private fun formatTime(timestampMs: Long): String {
        return SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestampMs))
    }
}
