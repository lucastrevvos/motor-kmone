package com.lucastrevvos.kmonemotor.radar.ocr

import android.content.Context
import android.graphics.Bitmap
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class OcrDebugWriter(
    private val context: Context
) {
    fun write(
        candidate: OcrCandidate,
        bitmap: Bitmap,
        observation: OcrObservation,
        policyReason: String
    ) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_OCR_SAVE) {
            return
        }
        val dir = context.getExternalFilesDir("debug_ocr") ?: return
        dir.mkdirs()
        val prefix = "${observation.startedAtMs}_${observation.observationId}_${candidate.cropKind}"
        try {
            FileOutputStream(File(dir, "${prefix}_ocr_crop.png")).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_DEBUG_OCR_CROP_SAVED",
                "observationId" to observation.observationId,
                "cropKind" to candidate.cropKind
            )
            File(dir, "${prefix}_ocr_raw_text.txt").writeText(observation.rawText)
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_DEBUG_OCR_TEXT_SAVED",
                "observationId" to observation.observationId,
                "cropKind" to candidate.cropKind
            )
            File(dir, "${prefix}_ocr_meta.json").writeText(
                JSONObject().apply {
                    put("ocrObservationId", observation.ocrObservationId)
                    put("observationId", observation.observationId)
                    put("captureRequestId", observation.captureRequestId)
                    put("triggerSource", observation.triggerSource.name)
                    put("cropKind", observation.cropKind.name)
                    put("rect", candidate.rect.flattenToString())
                    put("durationMs", observation.durationMs)
                    put("success", observation.success)
                    put("lineCount", observation.lineCount)
                    put("blockCount", observation.blockCount)
                    put("offerCycleKind", observation.offerCycleKind?.name)
                    put("shouldPreferForOcr", observation.shouldPreferForOcr)
                    put("analysisEpoch", observation.analysisEpoch)
                    put("manual", observation.isManual)
                    put("manualReason", observation.manualReason)
                    put("policyReason", policyReason)
                    put("rawTextPreview", observation.rawTextPreview())
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_OCR",
                "KM_V2_DEBUG_OCR_META_SAVED",
                "observationId" to observation.observationId,
                "cropKind" to candidate.cropKind
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OCR",
                "KM_V2_DEBUG_OCR_SAVE_FAILED",
                "observationId" to observation.observationId,
                "cropKind" to candidate.cropKind,
                "error" to throwable.message
            )
        }
    }
}

fun OcrObservation.rawTextPreview(maxLength: Int = 120): String {
    return rawText.replace("\n", " ").trim().take(maxLength)
}
