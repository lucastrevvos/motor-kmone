package com.lucastrevvos.kmonemotor.radar.decision

import android.content.Context
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface DecisionDebugWriter {
    fun write(result: EconomicDecisionResult)
}

class EconomicDecisionDebugWriter(
    private val context: Context
) : DecisionDebugWriter {
    override fun write(result: EconomicDecisionResult) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_DECISION_SAVE) {
            return
        }
        val dir = context.getExternalFilesDir("debug_decision") ?: return
        dir.mkdirs()
        val file = File(dir, "${result.createdAtMs}_${result.observationId}_decision_meta.json")
        try {
            file.writeText(
                JSONObject().apply {
                    put("observationId", result.observationId)
                    put("clusterId", result.clusterId)
                    put("decision", result.decision.name)
                    put("score", result.score)
                    put("confidence", result.confidence)
                    put("source", result.source.name)
                    put("metrics", JSONObject().apply {
                        put("price", result.metrics.price)
                        put("pickupDistanceKm", result.metrics.pickupDistanceKm)
                        put("pickupTimeMin", result.metrics.pickupTimeMin)
                        put("tripDistanceKm", result.metrics.tripDistanceKm)
                        put("tripTimeMin", result.metrics.tripTimeMin)
                        put("valuePerKmExplicit", result.metrics.valuePerKmExplicit)
                        put("grossPerTripKm", result.metrics.grossPerTripKm)
                        put("grossPerTotalKm", result.metrics.grossPerTotalKm)
                        put("totalDistanceKm", result.metrics.totalDistanceKm)
                        put("totalTimeMin", result.metrics.totalTimeMin)
                    })
                    put("reasons", JSONArray(result.reasons.map { it.name }))
                    put("warnings", JSONArray(result.warnings))
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_ECONOMIC",
                "KM_V2_DEBUG_DECISION_META_SAVED",
                "observationId" to result.observationId,
                "path" to file.absolutePath
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_ECONOMIC",
                "KM_V2_DEBUG_DECISION_SAVE_FAILED",
                "observationId" to result.observationId,
                "error" to throwable.message
            )
        }
    }
}
