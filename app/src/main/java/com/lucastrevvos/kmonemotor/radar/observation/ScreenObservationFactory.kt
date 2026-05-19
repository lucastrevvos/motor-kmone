package com.lucastrevvos.kmonemotor.radar.observation

import com.lucastrevvos.kmonemotor.radar.android.ScreenshotCaptureResult
import com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest
import java.util.UUID

class ScreenObservationFactory {
    fun create(request: CaptureRequest, result: ScreenshotCaptureResult): ScreenObservation {
        val observationCreatedAtMs = result.screenshotFinishedAtMs
        return ScreenObservation(
            id = UUID.randomUUID().toString(),
            createdAtMs = request.createdAtMs,
            requestCreatedAtMs = request.createdAtMs,
            captureApprovedAtMs = request.approvedAtMs ?: request.createdAtMs,
            screenshotStartedAtMs = result.screenshotStartedAtMs,
            screenshotFinishedAtMs = result.screenshotFinishedAtMs,
            observationCreatedAtMs = observationCreatedAtMs,
            capturedAtMs = result.capturedAtMs,
            captureRequestId = request.id,
            triggerSource = request.triggerSource,
            dominantPackage = request.dominantPackage,
            floatingPackage = request.floatingPackage,
            floatingBounds = request.floatingBounds,
            floatingKind = request.floatingKind,
            screenshotWidth = result.screenshotWidth,
            screenshotHeight = result.screenshotHeight,
            captureLatencyMs = result.screenshotFinishedAtMs - (request.approvedAtMs ?: request.createdAtMs),
            eventToObservationMs = observationCreatedAtMs - request.sourceEventAtMs,
            visualPlatformHint = request.platformHint,
            offerCycleClassification = request.offerCycleClassification,
            analysisEpoch = request.analysisEpoch,
            isManual = request.isManual,
            manualReason = request.manualReason,
            metadata = ObservationMetadata(
                notes = buildMap {
                    result.savedDebugPath?.let { put("savedDebugPath", it) }
                },
                offerCycleClassification = request.offerCycleClassification,
                analysisEpoch = request.analysisEpoch,
                isManual = request.isManual,
                manualReason = request.manualReason
            )
        )
    }
}
