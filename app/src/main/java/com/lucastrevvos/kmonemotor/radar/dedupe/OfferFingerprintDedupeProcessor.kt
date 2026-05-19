package com.lucastrevvos.kmonemotor.radar.dedupe

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprint
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.ocr.rawTextPreview

class OfferFingerprintDedupeProcessor(
    private val engine: OfferFingerprintDedupeEngine,
    private val clock: RadarClock = RadarClock.System,
    private val debugWriter: OfferFingerprintDedupeDebugWriter? = null
) {
    fun process(
        fingerprint: OfferTextFingerprint,
        ocrObservation: OcrObservation
    ): OfferDedupeResult {
        val startedAtMs = clock.nowMs()
        val input = OfferFingerprintDedupeInput(
            observationId = fingerprint.observationId,
            triggerSource = fingerprint.triggerSource,
            platformHint = fingerprint.platformTextHint,
            fingerprintKind = fingerprint.kind,
            rawTextHash = fingerprint.rawTextHash,
            routeTextHash = fingerprint.routeTextHash,
            prices = fingerprint.priceCandidates,
            valuePerKm = fingerprint.valuePerKmCandidates,
            distances = fingerprint.distanceCandidates,
            times = fingerprint.timeCandidates,
            offerLikeScore = fingerprint.offerLikeScore,
            nonOfferScore = fingerprint.nonOfferScore,
            cropKind = fingerprint.cropKind,
            isManual = ocrObservation.isManual,
            analysisEpoch = ocrObservation.analysisEpoch.takeIf { it > 0L },
            capturedAtMs = ocrObservation.finishedAtMs,
            fingerprintCreatedAtMs = fingerprint.createdAtMs,
            rawTextPreview = ocrObservation.rawTextPreview()
        )
        val result = engine.process(input).copy(
            fingerprintToDedupeMs = (startedAtMs - fingerprint.createdAtMs).coerceAtLeast(0L),
            dedupeDurationMs = (clock.nowMs() - startedAtMs).coerceAtLeast(0L)
        )
        debugWriter?.write(input, result)
        RadarLogger.i(
            "KM_V2_DEDUPE",
            "KM_V2_LATENCY_DEDUPE",
            "observationId" to fingerprint.observationId,
            "fingerprintToDedupeMs" to result.fingerprintToDedupeMs,
            "dedupeDurationMs" to result.dedupeDurationMs
        )
        return result
    }
}
