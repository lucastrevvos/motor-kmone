package com.lucastrevvos.kmonemotor.radar.android

import android.graphics.Bitmap
import com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest

data class ScreenshotCaptureResult(
    val screenshotStartedAtMs: Long,
    val screenshotFinishedAtMs: Long,
    val capturedAtMs: Long,
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    val screenshotBitmap: Bitmap?,
    val savedDebugPath: String? = null
)

enum class ScreenshotFinishStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    EXCEPTION
}

interface ScreenshotCapturer {
    fun capture(
        request: CaptureRequest,
        onSuccess: (CaptureRequest, ScreenshotCaptureResult) -> Unit,
        onFailure: (CaptureRequest, String, Int?, Long, Long, ScreenshotFinishStatus) -> Unit,
        onFinished: (CaptureRequest, ScreenshotFinishStatus) -> Unit
    )
}
