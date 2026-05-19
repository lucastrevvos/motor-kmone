package com.lucastrevvos.kmonemotor.radar.presentation

import com.lucastrevvos.kmonemotor.radar.decision.DecisionSource
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionKind
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionReason
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionResult
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.util.Locale
import kotlin.math.roundToInt

class DecisionPresentationBuilder {
    fun build(result: EconomicDecisionResult): DecisionPresentation {
        RadarLogger.i(
            "KM_V2_PRESENTATION",
            "KM_V2_DECISION_PRESENTATION_STARTED",
            "observationId" to result.observationId,
            "clusterId" to result.clusterId,
            "decision" to result.decision,
            "source" to result.source
        )
        val source = result.source.toPresentationSource()
        val mapping = mapKind(result.decision, result.source)
        val title = when (mapping.kind) {
            DecisionPresentationKind.SHOW_GOOD -> "Boa"
            DecisionPresentationKind.SHOW_WARNING -> "Atencao"
            DecisionPresentationKind.SHOW_BAD -> "Ruim"
            DecisionPresentationKind.SHOW_BLOCKED -> "Leitura incerta"
            DecisionPresentationKind.SHOW_UNKNOWN -> "Nao deu para decidir"
            DecisionPresentationKind.DO_NOT_SHOW -> when (result.decision) {
                EconomicDecisionKind.BLOCKED -> "Leitura incerta"
                EconomicDecisionKind.UNKNOWN -> "Nao deu para decidir"
                EconomicDecisionKind.GOOD -> "Boa"
                EconomicDecisionKind.WARNING -> "Atencao"
                EconomicDecisionKind.BAD -> "Ruim"
            }
        }
        val shortReason = selectShortReason(result)
        val priceText = result.metrics.price?.let(::formatMoney)
        val primaryMetric = result.metrics.grossPerTotalKm?.let { "${formatPerKm(it)} total" }
            ?: result.metrics.grossPerTripKm?.let { "${formatPerKm(it)} corrida" }
        val secondaryMetric = result.metrics.totalDistanceKm?.let { "${formatKm(it)} total" }
        val details = buildDetails(result, priceText, primaryMetric, secondaryMetric)
        val createdAtMs = result.createdAtMs
        val expiresAtMs = createdAtMs + if (result.source == DecisionSource.MANUAL) MANUAL_TTL_MS else AUTOMATIC_TTL_MS
        val presentation = DecisionPresentation(
            observationId = result.observationId,
            clusterId = result.clusterId,
            kind = mapping.kind,
            title = title,
            shortReason = shortReason,
            details = details,
            primaryMetric = primaryMetric,
            secondaryMetric = secondaryMetric,
            priceText = priceText,
            platformText = null,
            productText = null,
            semantic = mapping.semantic,
            source = source,
            expiresAtMs = expiresAtMs,
            createdAtMs = createdAtMs
        )
        RadarLogger.i(
            "KM_V2_PRESENTATION",
            "KM_V2_DECISION_PRESENTATION_BUILT",
            "observationId" to presentation.observationId,
            "clusterId" to presentation.clusterId,
            "kind" to presentation.kind,
            "title" to presentation.title,
            "shortReason" to presentation.shortReason,
            "primaryMetric" to presentation.primaryMetric,
            "expiresInMs" to (presentation.expiresAtMs - presentation.createdAtMs)
        )
        return presentation
    }

    fun formatMoney(value: Double): String = "R$ ${brNumber(value, 2)}"

    fun formatKm(value: Double): String = "${brNumber(value, 1)} km"

    fun formatPerKm(value: Double): String = "R$ ${brNumber(value, 2)}/km"

    fun formatMinutes(value: Double): String = "${value.roundToInt()} min"

    private fun selectShortReason(result: EconomicDecisionResult): String {
        return when (result.decision) {
            EconomicDecisionKind.GOOD -> "Acima da meta por km"
            EconomicDecisionKind.WARNING -> when {
                result.reasons.contains(EconomicDecisionReason.SHORT_TRIP) ||
                    result.reasons.contains(EconomicDecisionReason.VERY_SHORT_TRIP) -> "Corrida curta"
                result.reasons.contains(EconomicDecisionReason.LONG_PICKUP_DISTANCE) ||
                    result.reasons.contains(EconomicDecisionReason.VERY_LONG_PICKUP_DISTANCE) ||
                    result.reasons.contains(EconomicDecisionReason.LONG_PICKUP_TIME) ||
                    result.reasons.contains(EconomicDecisionReason.VERY_LONG_PICKUP_TIME) -> "Deslocamento longo"
                else -> "Vale analisar melhor"
            }
            EconomicDecisionKind.BAD -> when {
                result.reasons.contains(EconomicDecisionReason.PICKUP_DISTANCE_GREATER_THAN_TRIP) ||
                    result.reasons.contains(EconomicDecisionReason.PICKUP_TIME_GREATER_THAN_TRIP) -> "Deslocamento maior que a corrida"
                result.reasons.contains(EconomicDecisionReason.BELOW_MIN_TOTAL_KM) -> "Abaixo de R$ 1,50/km total"
                else -> "Abaixo da meta por km"
            }
            EconomicDecisionKind.BLOCKED -> "Dados suspeitos"
            EconomicDecisionKind.UNKNOWN -> "Dados insuficientes"
        }
    }

    private fun buildDetails(
        result: EconomicDecisionResult,
        priceText: String?,
        primaryMetric: String?,
        secondaryMetric: String?
    ): List<String> {
        val details = mutableListOf<String>()
        if (priceText != null) {
            details += priceText
        }
        val pickup = if (result.metrics.pickupTimeMin != null || result.metrics.pickupDistanceKm != null) {
            "Busca: ${result.metrics.pickupTimeMin?.let(::formatMinutes) ?: "-"} / ${result.metrics.pickupDistanceKm?.let(::formatKm) ?: "-"}"
        } else null
        val trip = if (result.metrics.tripTimeMin != null || result.metrics.tripDistanceKm != null) {
            "Corrida: ${result.metrics.tripTimeMin?.let(::formatMinutes) ?: "-"} / ${result.metrics.tripDistanceKm?.let(::formatKm) ?: "-"}"
        } else null
        if (pickup != null && details.size < 3) details += pickup
        if (trip != null && details.size < 3) details += trip
        if (primaryMetric != null && details.size < 3) details += primaryMetric
        if (secondaryMetric != null && details.size < 3) details += secondaryMetric
        return details.take(3)
    }

    private fun mapKind(
        decision: EconomicDecisionKind,
        source: DecisionSource
    ): PresentationMapping {
        return when (decision) {
            EconomicDecisionKind.GOOD -> PresentationMapping(DecisionPresentationKind.SHOW_GOOD, DecisionSemantic.POSITIVE)
            EconomicDecisionKind.WARNING -> PresentationMapping(DecisionPresentationKind.SHOW_WARNING, DecisionSemantic.ATTENTION)
            EconomicDecisionKind.BAD -> PresentationMapping(DecisionPresentationKind.SHOW_BAD, DecisionSemantic.NEGATIVE)
            EconomicDecisionKind.BLOCKED -> if (source == DecisionSource.MANUAL) {
                PresentationMapping(DecisionPresentationKind.SHOW_BLOCKED, DecisionSemantic.BLOCKED)
            } else {
                PresentationMapping(DecisionPresentationKind.DO_NOT_SHOW, DecisionSemantic.BLOCKED)
            }
            EconomicDecisionKind.UNKNOWN -> if (source == DecisionSource.MANUAL) {
                PresentationMapping(DecisionPresentationKind.SHOW_UNKNOWN, DecisionSemantic.NEUTRAL)
            } else {
                PresentationMapping(DecisionPresentationKind.DO_NOT_SHOW, DecisionSemantic.NEUTRAL)
            }
        }
    }

    private fun brNumber(value: Double, fractionDigits: Int): String {
        return String.format(Locale.US, "%.${fractionDigits}f", value).replace(".", ",")
    }

    private data class PresentationMapping(
        val kind: DecisionPresentationKind,
        val semantic: DecisionSemantic
    )

    companion object {
        const val AUTOMATIC_TTL_MS = 8000L
        const val MANUAL_TTL_MS = 10000L
    }
}
