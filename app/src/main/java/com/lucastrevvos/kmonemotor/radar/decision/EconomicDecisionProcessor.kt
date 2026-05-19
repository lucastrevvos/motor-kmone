package com.lucastrevvos.kmonemotor.radar.decision

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.parser.OfferParserResult

class EconomicDecisionProcessor(
    private val engine: EconomicDecisionEngine = EconomicDecisionEngine(),
    private val clock: RadarClock = RadarClock.System,
    private val debugWriter: DecisionDebugWriter? = null
) {
    fun process(
        parserResult: OfferParserResult,
        source: DecisionSource
    ): EconomicDecisionProcessResult {
        if (parserResult.status != "parsed" || parserResult.draft == null) {
            val reason = when {
                parserResult.reason == "dedupe_weaker" -> "dedupe_weaker"
                parserResult.reason == "unknown" -> "unknown"
                parserResult.reason == "non_offer" -> "non_offer"
                parserResult.status == "skipped" -> "parser_skipped"
                else -> "parser_failed"
            }
            RadarLogger.i(
                "KM_V2_ECONOMIC",
                "KM_V2_ECONOMIC_DECISION_SKIPPED",
                "reason" to reason
            )
            return EconomicDecisionProcessResult(
                status = "skipped",
                reason = reason
            )
        }
        val startedAtMs = clock.nowMs()
        val result = engine.evaluate(
            EconomicDecisionInput(
                observationId = parserResult.draft.observationId,
                clusterId = parserResult.draft.clusterId,
                parsedOffer = parserResult.draft,
                source = source,
                createdAtMs = clock.nowMs()
            )
        )
        try {
            debugWriter?.write(result)
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_ECONOMIC",
                "KM_V2_DEBUG_DECISION_SAVE_FAILED",
                "observationId" to result.observationId,
                "error" to throwable.message
            )
        }
        val durationMs = (clock.nowMs() - startedAtMs).coerceAtLeast(0L)
        RadarLogger.i(
            "KM_V2_ECONOMIC",
            "KM_V2_LATENCY_ECONOMIC_DECISION",
            "observationId" to result.observationId,
            "durationMs" to durationMs
        )
        return EconomicDecisionProcessResult(
            status = "evaluated",
            reason = result.decision.name.lowercase(),
            result = result,
            durationMs = durationMs
        )
    }
}
