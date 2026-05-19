package com.lucastrevvos.kmonemotor.radar.parser

import android.content.Context
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface ParserDebugWriter {
    fun write(result: OfferParserResult)
}

class OfferParserDebugWriter(
    private val context: Context
) : ParserDebugWriter {
    override fun write(result: OfferParserResult) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_PARSER_SAVE) {
            return
        }
        val draft = result.draft ?: return
        val dir = context.getExternalFilesDir("debug_parser") ?: return
        dir.mkdirs()
        val file = File(dir, "${draft.createdAtMs}_${draft.observationId}_parser_meta.json")
        try {
            file.writeText(
                JSONObject().apply {
                    put("observationId", draft.observationId)
                    put("clusterId", draft.clusterId)
                    put("platform", draft.platform.name)
                    put("product", draft.product)
                    put("paymentMethod", draft.paymentMethod)
                    put("price", draft.price?.value)
                    put("valuePerKm", draft.valuePerKm?.value)
                    put("pickupTimeMinutes", draft.pickupTimeMinutes?.value)
                    put("pickupDistanceKm", draft.pickupDistanceKm?.value)
                    put("tripTimeMinutes", draft.tripTimeMinutes?.value)
                    put("tripDistanceKm", draft.tripDistanceKm?.value)
                    put("multiplier", draft.multiplier?.value)
                    put("passengerInfo", draft.passengerInfo)
                    put("rawTextPreview", draft.rawTextPreview)
                    put("confidence", JSONObject().apply {
                        put("overall", draft.confidence.overall)
                        put("price", draft.confidence.price)
                        put("route", draft.confidence.route)
                        put("platform", draft.confidence.platform)
                        put("product", draft.confidence.product)
                    })
                    put("warnings", JSONArray(draft.warnings))
                    put("sanity", JSONObject().apply {
                        put("status", draft.sanityStatus.name)
                        put("issues", JSONArray(draft.sanityIssues.map { it.name }))
                        put("shouldBlockEconomicDecisionFuture", draft.shouldBlockEconomicDecisionFuture)
                    })
                    put("status", result.status)
                    put("reason", result.reason)
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_DEBUG_PARSER_META_SAVED",
                "observationId" to draft.observationId,
                "path" to file.absolutePath
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_PARSER",
                "KM_V2_DEBUG_PARSER_SAVE_FAILED",
                "observationId" to draft.observationId,
                "error" to throwable.message
            )
        }
    }
}
