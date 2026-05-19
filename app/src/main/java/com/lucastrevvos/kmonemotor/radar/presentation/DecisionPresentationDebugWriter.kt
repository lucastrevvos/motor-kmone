package com.lucastrevvos.kmonemotor.radar.presentation

import android.content.Context
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface PresentationDebugWriter {
    fun write(presentation: DecisionPresentation)
}

class DecisionPresentationDebugWriter(
    private val context: Context
) : PresentationDebugWriter {
    override fun write(presentation: DecisionPresentation) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_PRESENTATION_SAVE) {
            return
        }
        val dir = context.getExternalFilesDir("debug_presentation") ?: return
        dir.mkdirs()
        val file = File(dir, "${presentation.createdAtMs}_${presentation.observationId}_presentation_meta.json")
        try {
            file.writeText(
                JSONObject().apply {
                    put("observationId", presentation.observationId)
                    put("clusterId", presentation.clusterId)
                    put("kind", presentation.kind.name)
                    put("title", presentation.title)
                    put("shortReason", presentation.shortReason)
                    put("details", JSONArray(presentation.details))
                    put("primaryMetric", presentation.primaryMetric)
                    put("secondaryMetric", presentation.secondaryMetric)
                    put("priceText", presentation.priceText)
                    put("platformText", presentation.platformText)
                    put("productText", presentation.productText)
                    put("semantic", presentation.semantic.name)
                    put("source", presentation.source.name)
                    put("expiresAtMs", presentation.expiresAtMs)
                    put("createdAtMs", presentation.createdAtMs)
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_PRESENTATION",
                "KM_V2_DEBUG_PRESENTATION_META_SAVED",
                "observationId" to presentation.observationId,
                "path" to file.absolutePath
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_PRESENTATION",
                "KM_V2_DEBUG_PRESENTATION_SAVE_FAILED",
                "observationId" to presentation.observationId,
                "error" to throwable.message
            )
        }
    }
}
