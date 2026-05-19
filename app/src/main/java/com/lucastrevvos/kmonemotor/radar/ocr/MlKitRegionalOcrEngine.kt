package com.lucastrevvos.kmonemotor.radar.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import java.util.UUID
import java.util.concurrent.Executor

class MlKitRegionalOcrEngine(
    context: Context,
    private val callbackExecutor: Executor,
    private val clock: RadarClock = RadarClock.System
) : RegionalOcrEngine {
    @Suppress("UnusedPrivateProperty")
    private val appContext = context.applicationContext
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun recognize(
        candidate: OcrCandidate,
        bitmap: Bitmap,
        callback: (OcrObservation) -> Unit
    ) {
        val startedAtMs = clock.nowMs()
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener(callbackExecutor) { result ->
                    val finishedAtMs = clock.nowMs()
                    val lines = result.textBlocks.sumOf { it.lines.size }
                    callback(
                        OcrObservation(
                            ocrObservationId = UUID.randomUUID().toString(),
                            observationId = candidate.observationId,
                            captureRequestId = candidate.captureRequestId,
                            triggerSource = candidate.triggerSource,
                            cropId = candidate.cropId,
                            cropKind = candidate.cropKind,
                            startedAtMs = startedAtMs,
                            finishedAtMs = finishedAtMs,
                            durationMs = finishedAtMs - startedAtMs,
                            success = true,
                            rawText = result.text.orEmpty(),
                            lineCount = lines,
                            blockCount = result.textBlocks.size,
                            errorMessage = null,
                            offerCycleKind = candidate.offerCycleKind,
                            shouldPreferForOcr = candidate.offerCycleShouldPreferForOcr,
                            analysisEpoch = candidate.analysisEpoch,
                            isManual = candidate.isManual,
                            manualReason = candidate.manualReason
                        )
                    )
                }
                .addOnFailureListener(callbackExecutor) { throwable ->
                    val finishedAtMs = clock.nowMs()
                    callback(
                        OcrObservation(
                            ocrObservationId = UUID.randomUUID().toString(),
                            observationId = candidate.observationId,
                            captureRequestId = candidate.captureRequestId,
                            triggerSource = candidate.triggerSource,
                            cropId = candidate.cropId,
                            cropKind = candidate.cropKind,
                            startedAtMs = startedAtMs,
                            finishedAtMs = finishedAtMs,
                            durationMs = finishedAtMs - startedAtMs,
                            success = false,
                            rawText = "",
                            lineCount = 0,
                            blockCount = 0,
                            errorMessage = throwable.message ?: "mlkit_text_recognition_failed",
                            offerCycleKind = candidate.offerCycleKind,
                            shouldPreferForOcr = candidate.offerCycleShouldPreferForOcr,
                            analysisEpoch = candidate.analysisEpoch,
                            isManual = candidate.isManual,
                            manualReason = candidate.manualReason
                        )
                    )
                }
        } catch (throwable: Throwable) {
            val finishedAtMs = clock.nowMs()
            callback(
                OcrObservation(
                    ocrObservationId = UUID.randomUUID().toString(),
                    observationId = candidate.observationId,
                    captureRequestId = candidate.captureRequestId,
                    triggerSource = candidate.triggerSource,
                    cropId = candidate.cropId,
                    cropKind = candidate.cropKind,
                    startedAtMs = startedAtMs,
                    finishedAtMs = finishedAtMs,
                    durationMs = finishedAtMs - startedAtMs,
                    success = false,
                    rawText = "",
                    lineCount = 0,
                    blockCount = 0,
                    errorMessage = throwable.message ?: "mlkit_text_recognition_exception",
                    offerCycleKind = candidate.offerCycleKind,
                    shouldPreferForOcr = candidate.offerCycleShouldPreferForOcr,
                    analysisEpoch = candidate.analysisEpoch,
                    isManual = candidate.isManual,
                    manualReason = candidate.manualReason
                )
            )
        }
    }
}
