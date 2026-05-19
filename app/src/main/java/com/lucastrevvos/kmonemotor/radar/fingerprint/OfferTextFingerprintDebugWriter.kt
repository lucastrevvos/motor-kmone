package com.lucastrevvos.kmonemotor.radar.fingerprint

import android.content.Context
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.ocr.rawTextPreview
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OfferTextFingerprintDebugWriter(
    private val context: Context
) {
    fun write(
        fingerprint: OfferTextFingerprint,
        ocrObservation: OcrObservation
    ) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_FINGERPRINT_SAVE) {
            return
        }
        val dir = context.getExternalFilesDir("debug_fingerprint") ?: return
        dir.mkdirs()
        val file = File(dir, "${fingerprint.createdAtMs}_${fingerprint.observationId}_fingerprint_meta.json")
        try {
            file.writeText(
                JSONObject().apply {
                    put("fingerprintId", fingerprint.fingerprintId)
                    put("ocrObservationId", fingerprint.ocrObservationId)
                    put("observationId", fingerprint.observationId)
                    put("captureRequestId", fingerprint.captureRequestId)
                    put("triggerSource", fingerprint.triggerSource.name)
                    put("cropKind", fingerprint.cropKind.name)
                    put("platformTextHint", fingerprint.platformTextHint.name)
                    put("kind", fingerprint.kind.name)
                    put("offerLikeScore", fingerprint.offerLikeScore)
                    put("nonOfferScore", fingerprint.nonOfferScore)
                    put("positiveSignals", JSONArray(fingerprint.positiveSignals.map {
                        JSONObject().apply {
                            put("key", it.key)
                            put("raw", it.raw)
                            put("confidence", it.confidence)
                        }
                    }))
                    put("negativeSignals", JSONArray(fingerprint.negativeSignals.map {
                        JSONObject().apply {
                            put("key", it.key)
                            put("raw", it.raw)
                            put("confidence", it.confidence)
                        }
                    }))
                    put("priceCandidates", JSONArray(fingerprint.priceCandidates.map { it.toJson() }))
                    put("valuePerKmCandidates", JSONArray(fingerprint.valuePerKmCandidates.map { it.toJson() }))
                    put("distanceCandidates", JSONArray(fingerprint.distanceCandidates.map { it.toJson() }))
                    put("timeCandidates", JSONArray(fingerprint.timeCandidates.map { it.toJson() }))
                    put("rawTextHash", fingerprint.rawTextHash)
                    put("routeTextHash", fingerprint.routeTextHash)
                    put("rawTextPreview", ocrObservation.rawTextPreview())
                    put("normalizedPreview", fingerprint.normalizedPreview)
                    put("analysisEpoch", ocrObservation.analysisEpoch)
                    put("manual", ocrObservation.isManual)
                    put("manualReason", ocrObservation.manualReason)
                    put("reason", fingerprint.reason)
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_FINGERPRINT",
                "KM_V2_DEBUG_FINGERPRINT_META_SAVED",
                "observationId" to fingerprint.observationId,
                "path" to file.absolutePath
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_FINGERPRINT",
                "KM_V2_DEBUG_FINGERPRINT_SAVE_FAILED",
                "observationId" to fingerprint.observationId,
                "error" to throwable.message
            )
        }
    }

    private fun ExtractedNumericCandidate.toJson() = JSONObject().apply {
        put("raw", raw)
        put("normalizedValue", normalizedValue)
        put("unit", unit)
        put("kind", kind)
        put("confidence", confidence)
    }
}
