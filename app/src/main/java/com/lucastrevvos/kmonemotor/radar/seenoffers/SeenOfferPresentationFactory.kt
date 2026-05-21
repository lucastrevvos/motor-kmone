package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentation
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationBuilder
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationKind
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationSource
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionSemantic

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
        com.lucastrevvos.kmonemotor.radar.debug.RadarLogger.i(
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
        val details = buildList {
            if (seenOffer.pickupTimeMin != null || resolved.pickupDistanceKm != null) {
                add(
                    "Busca: ${seenOffer.pickupTimeMin?.let(formatter::formatMinutes) ?: "-"} / " +
                        "${resolved.pickupDistanceKm?.let(formatter::formatKm) ?: "-"}"
                )
            }
            if (seenOffer.tripTimeMin != null || resolved.tripDistanceKm != null) {
                add(
                    "Corrida: ${seenOffer.tripTimeMin?.let(formatter::formatMinutes) ?: "-"} / " +
                        "${resolved.tripDistanceKm?.let(formatter::formatKm) ?: "-"}"
                )
            }
            resolved.totalDistanceKm?.let { add("${formatter.formatKm(it)} total") }
            seenOffer.originPreview?.takeIf { it.isNotBlank() }?.let { add("Origem: $it") }
            seenOffer.destinationPreview?.takeIf { it.isNotBlank() }?.let { add("Destino: $it") }
        }
        return DecisionPresentation(
            observationId = seenOffer.observationId,
            clusterId = null,
            kind = DecisionPresentationKind.SHOW_WARNING,
            title = "Oferta ja registrada",
            shortReason = "Reapresentando oferta salva",
            details = details.take(4),
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
