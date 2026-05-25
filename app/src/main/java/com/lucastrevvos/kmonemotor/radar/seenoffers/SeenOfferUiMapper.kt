package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionConfig
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
    val sourceBadgeLabel: String,
    val decisionBadgeLabel: String,
    val pickupLabel: String,
    val tripLabel: String,
    val totalDistanceLabel: String,
    val collapsedSummaryLabel: String,
    val originLabel: String,
    val destinationLabel: String,
    val warnings: List<String>
)

class SeenOfferUiMapper(
    private val auditor: SeenOfferConsistencyAuditor = SeenOfferConsistencyAuditor(),
    private val decisionConfig: EconomicDecisionConfig = EconomicDecisionConfig()
) {
    fun map(offer: SeenOffer): SeenOfferUiModel {
        val audit = auditor.audit(offer)
        val normalized = audit.normalizedOffer

        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_VIEWS_CARD_RENDERED",
            "seenOfferId" to offer.id,
            "price" to normalized.price,
            "pickupDistanceKm" to normalized.pickupDistanceKm,
            "tripDistanceKm" to normalized.tripDistanceKm,
            "rawTotalDistanceKm" to offer.totalDistanceKm,
            "resolvedKm" to normalized.totalDistanceKm,
            "displayedValuePerKm" to normalized.valuePerKm
        )

        return SeenOfferUiModel(
            id = offer.id,
            platformLabel = platformLabel(normalized.platform),
            productName = normalized.productName?.takeUnless {
                it.equals(platformLabel(normalized.platform), ignoreCase = true)
            },
            priceLabel = formatMoney(normalized.price),
            timeLabel = formatTime(normalized.createdAtMs),
            valuePerKmLabel = normalized.valuePerKm?.let(::formatPerKm) ?: "R$/km -",
            sourceBadgeLabel = sourceBadge(normalized.sourceTrigger),
            decisionBadgeLabel = decisionBadge(normalized),
            pickupLabel = formatMetric(normalized.pickupTimeMin, normalized.pickupDistanceKm),
            tripLabel = formatMetric(normalized.tripTimeMin, normalized.tripDistanceKm),
            totalDistanceLabel = normalized.totalDistanceKm?.let(::formatKm) ?: "-",
            collapsedSummaryLabel = collapsedSummary(normalized),
            originLabel = normalized.originPreview?.takeUnless(String::isBlank) ?: "N\u00E3o identificada",
            destinationLabel = normalized.destinationPreview?.takeUnless(String::isBlank) ?: "N\u00E3o identificado",
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
        return parts.joinToString(" \u2022 ").ifBlank { "-" }
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

    private fun collapsedSummary(offer: SeenOffer): String {
        val parts = buildList {
            offer.totalDistanceKm?.let { add("${formatKm(it)} total") }
            offer.pickupTimeMin?.let { add("${it.toInt()} min busca") }
            offer.tripTimeMin?.let { add("${it.toInt()} min viagem") }
        }
        return parts.joinToString(" \u2022 ").ifBlank {
            offer.totalDistanceKm?.let { "${formatKm(it)} total" } ?: "Dados parciais"
        }
    }

    private fun sourceBadge(sourceTrigger: String): String {
        val normalized = sourceTrigger.trim().uppercase(Locale.US)
        return when {
            normalized == "MANUAL_SCREEN_ANALYSIS" || normalized == "MANUAL_DEBUG" -> "MANUAL"
            normalized.contains("RECOVERY") || normalized.contains("RETRY") -> "RETRY"
            else -> "AUTO"
        }
    }

    private fun decisionBadge(offer: SeenOffer): String {
        val metric = when {
            offer.totalDistanceKm != null && offer.totalDistanceKm > 0.0 && offer.price != null ->
                offer.valuePerKm ?: (offer.price / offer.totalDistanceKm)
            else -> offer.valuePerKm
        }
        return when {
            offer.price == null || metric == null -> "INCOMPLETA"
            metric >= decisionConfig.goodGrossPerKm -> "BOA"
            metric >= decisionConfig.minAcceptableGrossPerKm -> "ATEN\u00C7\u00C3O"
            else -> "RUIM"
        }
    }
}
