package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.dedupe.OfferDedupeDecision
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation

data class OfferParserInput(
    val fingerprint: OfferTextFingerprint,
    val ocrObservation: OcrObservation,
    val clusterId: String?,
    val dedupeDecision: OfferDedupeDecision,
    val rawText: String,
    val normalizedText: String,
    val triggerSource: TriggerSource,
    val createdAtMs: Long
)
