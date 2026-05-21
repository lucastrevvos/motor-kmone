package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentation
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationBuilder
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationKind
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationSource
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionSemantic
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger

class SeenOfferPresentationFactory(
    private val formatter: DecisionPresentationBuilder = DecisionPresentationBuilder()
) {
    fun buildFromSeenOffer(
        seenOffer: SeenOffer,
        createdAtMs: Long
    ): DecisionPresentation {
        val resolved = RideEconomicsCalculator.resolveRideEconomics(
            platform = seenOffer.platform,
            price = seenOffer.price,
            explicitValuePerKm = seenOffer.valuePerKm,
            totalDistanceKm = seenOffer.totalDistanceKm,
            pickupDistanceKm = seenOffer.pickupDistanceKm,
            tripDistanceKm = seenOffer.tripDistanceKm
        )
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SAVED_OFFER_ECONOMICS_RESOLVED",
            "observationId" to seenOffer.observationId,
            "platform" to seenOffer.platform,
            "price" to seenOffer.price,
            "pickupDistanceKm" to resolved.pickupDistanceKm,
            "tripDistanceKm" to resolved.tripDistanceKm,
            "totalDistanceKm" to resolved.totalDistanceKm,
            "valuePerKm" to resolved.valuePerKm,
            "warnings" to resolved.warnings.joinToString(",")
        )
        RadarLogger.i(
            "KM_V2_SEEN",
            "KM_V2_SEEN_OFFER_RESHOW_SOURCE",
            "source" to "saved_seen_offer",
            "seenOfferId" to seenOffer.id,
            "hasPrice" to (seenOffer.price != null),
            "hasTotalDistance" to (resolved.totalDistanceKm != null),
            "hasValuePerKm" to (resolved.valuePerKm != null),
            "hasPickup" to (resolved.pickupDistanceKm != null),
            "hasTrip" to (resolved.tripDistanceKm != null)
        )
        if (seenOffer.valuePerKm == null && resolved.valuePerKm != null) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_RESHOW_FALLBACK_APPLIED",
                "reason" to "missing_field_recomputed",
                "field" to "valuePerKm",
                "seenOfferId" to seenOffer.id
            )
        }
        if (seenOffer.totalDistanceKm == null && resolved.totalDistanceKm != null) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SEEN_OFFER_RESHOW_FALLBACK_APPLIED",
                "reason" to "missing_field_recomputed",
                "field" to "totalDistanceKm",
                "seenOfferId" to seenOffer.id
            )
        }
        val details = buildList {
            if (seenOffer.pickupTimeMin != null || resolved.pickupDistanceKm != null) {
                add(
                    "Busca: ${seenOffer.pickupTimeMin?.let(formatter::formatMinutes) ?: "—"} / " +
                        "${resolved.pickupDistanceKm?.let(formatter::formatKm) ?: "—"}"
                )
            }
            if (seenOffer.tripTimeMin != null || resolved.tripDistanceKm != null) {
                add(
                    "Corrida: ${seenOffer.tripTimeMin?.let(formatter::formatMinutes) ?: "—"} / " +
                        "${resolved.tripDistanceKm?.let(formatter::formatKm) ?: "—"}"
                )
            }
            resolved.totalDistanceKm?.let { add("${formatter.formatKm(it)} total") }
            add("Origem: ${seenOffer.originPreview?.takeIf { it.isNotBlank() } ?: "—"}")
            add("Destino: ${seenOffer.destinationPreview?.takeIf { it.isNotBlank() } ?: "—"}")
        }
        return DecisionPresentation(
            observationId = seenOffer.observationId,
            clusterId = null,
            kind = DecisionPresentationKind.SHOW_WARNING,
            title = "Oferta ja registrada",
            shortReason = "Reapresentando oferta salva",
            details = details,
            primaryMetric = resolved.valuePerKm?.let(formatter::formatPerKm)
                ?: seenOffer.price?.let(formatter::formatMoney),
            secondaryMetric = resolved.totalDistanceKm?.let(formatter::formatKm)?.plus(" total"),
            priceText = seenOffer.price?.let(formatter::formatMoney),
            platformText = when (seenOffer.platform) {
                RidePlatform.UBER -> "Uber"
                RidePlatform.NINETY_NINE -> "99"
                RidePlatform.UNKNOWN -> "Oferta"
            },
            productText = seenOffer.productName,
            semantic = DecisionSemantic.ATTENTION,
            source = DecisionPresentationSource.MANUAL,
            expiresAtMs = createdAtMs + DecisionPresentationBuilder.MANUAL_TTL_MS,
            createdAtMs = createdAtMs
        )
    }
}
