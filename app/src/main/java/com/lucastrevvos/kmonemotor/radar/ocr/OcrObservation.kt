package com.lucastrevvos.kmonemotor.radar.ocr

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.vision.CropKind

data class OcrObservation(
    val ocrObservationId: String,
    val observationId: String,
    val captureRequestId: String,
    val triggerSource: TriggerSource,
    val cropId: String,
    val cropKind: CropKind,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val durationMs: Long,
    val success: Boolean,
    val rawText: String,
    val lineCount: Int,
    val blockCount: Int,
    val errorMessage: String?,
    val offerCycleKind: OfferCycleKind?,
    val shouldPreferForOcr: Boolean?,
    val analysisEpoch: Long = 0L,
    val isManual: Boolean = false,
    val manualReason: String? = null
)
