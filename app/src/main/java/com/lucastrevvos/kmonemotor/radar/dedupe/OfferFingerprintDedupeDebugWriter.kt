package com.lucastrevvos.kmonemotor.radar.dedupe

import android.content.Context
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfferFingerprintDedupeDebugWriter(
    private val context: Context
) {
    fun write(
        input: OfferFingerprintDedupeInput,
        result: OfferDedupeResult
    ) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_DEDUPE_SAVE) {
            return
        }
        val dir = context.getExternalFilesDir("debug_dedupe") ?: return
        dir.mkdirs()
        val file = File(dir, "${input.fingerprintCreatedAtMs}_${input.observationId}_dedupe_meta.json")
        try {
            file.writeText(
                JSONObject().apply {
                    put("observationId", input.observationId)
                    put("triggerSource", input.triggerSource.name)
                    put("platformHint", input.platformHint.name)
                    put("fingerprintKind", input.fingerprintKind.name)
                    put("rawTextHash", input.rawTextHash)
                    put("routeTextHash", input.routeTextHash)
                    put("offerLikeScore", input.offerLikeScore)
                    put("nonOfferScore", input.nonOfferScore)
                    put("rawTextPreview", input.rawTextPreview)
                    put("prices", JSONArray(input.prices.map { it.normalizedValue }))
                    put("valuePerKm", JSONArray(input.valuePerKm.map { it.normalizedValue }))
                    put("distances", JSONArray(input.distances.map { it.normalizedValue }))
                    put("times", JSONArray(input.times.map { it.normalizedValue }))
                    put("dedupe", JSONObject().apply {
                        put("result", result.decision.name)
                        put("clusterId", result.clusterId)
                        put("quality", result.qualityScore)
                        put("matchedPreviousObservationId", result.matchedPreviousObservationId)
                        put("isBestForCluster", result.isBestForCluster)
                        put("reason", result.reason)
                    })
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_DEDUPE",
                "KM_V2_DEBUG_DEDUPE_META_SAVED",
                "observationId" to input.observationId,
                "path" to file.absolutePath
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_DEDUPE",
                "KM_V2_DEBUG_DEDUPE_SAVE_FAILED",
                "observationId" to input.observationId,
                "error" to throwable.message
            )
        }
    }
}
