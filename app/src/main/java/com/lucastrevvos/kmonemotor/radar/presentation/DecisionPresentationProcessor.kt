package com.lucastrevvos.kmonemotor.radar.presentation

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionKind
import com.lucastrevvos.kmonemotor.radar.decision.EconomicDecisionProcessResult

class DecisionPresentationProcessor(
    private val builder: DecisionPresentationBuilder = DecisionPresentationBuilder(),
    private val clock: RadarClock = RadarClock.System,
    private val debugWriter: PresentationDebugWriter? = null
) {
    fun process(decisionResult: EconomicDecisionProcessResult): DecisionPresentationProcessResult {
        if (decisionResult.status != "evaluated" || decisionResult.result == null) {
            val reason = when (decisionResult.reason) {
                "dedupe_weaker" -> "dedupe_weaker"
                "unknown" -> "decision_missing"
                "non_offer" -> "decision_missing"
                "parser_skipped" -> "decision_missing"
                else -> "decision_missing"
            }
            RadarLogger.i(
                "KM_V2_PRESENTATION",
                "KM_V2_DECISION_PRESENTATION_SKIPPED",
                "reason" to reason
            )
            return DecisionPresentationProcessResult(
                status = "skipped",
                reason = reason
            )
        }
        val startedAtMs = clock.nowMs()
        val presentation = builder.build(decisionResult.result)
        if ((decisionResult.result.decision == EconomicDecisionKind.UNKNOWN ||
                decisionResult.result.decision == EconomicDecisionKind.BLOCKED) &&
            presentation.kind == DecisionPresentationKind.DO_NOT_SHOW
        ) {
            val reason = when (decisionResult.result.decision) {
                EconomicDecisionKind.UNKNOWN -> "unknown_automatic"
                EconomicDecisionKind.BLOCKED -> "blocked_automatic"
                else -> "decision_missing"
            }
            RadarLogger.i(
                "KM_V2_PRESENTATION",
                "KM_V2_PRESENTATION_DO_NOT_SHOW_REASON",
                "reason" to reason,
                "platform" to null,
                "price" to decisionResult.result.metrics.price,
                "totalDistanceKm" to decisionResult.result.metrics.totalDistanceKm,
                "valuePerKm" to (decisionResult.result.metrics.grossPerTotalKm ?: decisionResult.result.metrics.valuePerKmExplicit),
                "fingerprintKind" to null,
                "parserStatus" to null
            )
            RadarLogger.i(
                "KM_V2_PRESENTATION",
                "KM_V2_DECISION_PRESENTATION_SKIPPED",
                "observationId" to decisionResult.result.observationId,
                "reason" to reason
            )
            return DecisionPresentationProcessResult(
                status = "skipped",
                reason = reason,
                presentation = presentation
            )
        }
        try {
            debugWriter?.write(presentation)
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_PRESENTATION",
                "KM_V2_DEBUG_PRESENTATION_SAVE_FAILED",
                "observationId" to presentation.observationId,
                "error" to throwable.message
            )
        }
        val durationMs = (clock.nowMs() - startedAtMs).coerceAtLeast(0L)
        RadarLogger.i(
            "KM_V2_PRESENTATION",
            "KM_V2_LATENCY_DECISION_PRESENTATION",
            "observationId" to presentation.observationId,
            "durationMs" to durationMs
        )
        return DecisionPresentationProcessResult(
            status = "built",
            reason = presentation.kind.name.lowercase(),
            presentation = presentation,
            durationMs = durationMs
        )
    }
}
