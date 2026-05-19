package com.lucastrevvos.kmonemotor.radar.ocr

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.vision.CropKind

data class OcrCandidate(
    val observationId: String,
    val captureRequestId: String,
    val triggerSource: TriggerSource,
    val cropId: String,
    val cropKind: CropKind,
    val rect: Rect,
    val width: Int,
    val height: Int,
    val offerCycleKind: OfferCycleKind?,
    val offerCycleShouldPreferForOcr: Boolean?,
    val acceptedForOcrFuture: Boolean,
    val reason: String,
    val analysisEpoch: Long = 0L,
    val isManual: Boolean = false,
    val manualReason: String? = null
)
