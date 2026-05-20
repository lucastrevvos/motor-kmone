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
        val resolvedKm = RideEconomicsCalculator.resolveTotalDistanceKm(
            totalDistanceKm = seenOffer.totalDistanceKm,
            pickupDistanceKm = seenOffer.pickupDistanceKm,
            tripDistanceKm = seenOffer.tripDistanceKm
        )
        val valuePerKm = RideEconomicsCalculator.calculateValuePerKm(
            price = seenOffer.price,
            totalDistanceKm = seenOffer.totalDistanceKm,
            pickupDistanceKm = seenOffer.pickupDistanceKm,
            tripDistanceKm = seenOffer.tripDistanceKm
        ) ?: seenOffer.valuePerKm
        val details = buildList {
            if (seenOffer.pickupTimeMin != null || seenOffer.pickupDistanceKm != null) {
                add(
                    "Busca: ${seenOffer.pickupTimeMin?.let(formatter::formatMinutes) ?: "-"} / " +
                        "${seenOffer.pickupDistanceKm?.let(formatter::formatKm) ?: "-"}"
                )
            }
            if (seenOffer.tripTimeMin != null || seenOffer.tripDistanceKm != null) {
                add(
                    "Corrida: ${seenOffer.tripTimeMin?.let(formatter::formatMinutes) ?: "-"} / " +
                        "${seenOffer.tripDistanceKm?.let(formatter::formatKm) ?: "-"}"
                )
            }
            resolvedKm?.let { add("${formatter.formatKm(it)} total") }
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
            primaryMetric = valuePerKm?.let(formatter::formatPerKm)
                ?: seenOffer.price?.let(formatter::formatMoney),
            secondaryMetric = resolvedKm?.let(formatter::formatKm)?.plus(" total"),
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
