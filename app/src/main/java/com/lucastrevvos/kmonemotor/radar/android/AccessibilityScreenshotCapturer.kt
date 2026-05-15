package com.lucastrevvos.kmonemotor.radar.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.orchestrator.CaptureRequest
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class AccessibilityScreenshotCapturer(
    private val service: AccessibilityService,
    private val clock: RadarClock = RadarClock.System
) : ScreenshotCapturer {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun capture(
        request: CaptureRequest,
        onSuccess: (CaptureRequest, ScreenshotCaptureResult) -> Unit,
        onFailure: (CaptureRequest, String, Int?, Long, Long, ScreenshotFinishStatus) -> Unit,
        onFinished: (CaptureRequest, ScreenshotFinishStatus) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val nowMs = clock.nowMs()
            logLatency(status = ScreenshotFinishStatus.FAILED, request = request, startedAtMs = nowMs, finishedAtMs = nowMs)
            onFailure(request, "take_screenshot_not_supported_below_api_30", null, nowMs, nowMs, ScreenshotFinishStatus.FAILED)
            onFinished(request, ScreenshotFinishStatus.FAILED)
            return
        }
        if (!hasScreenshotCapability()) {
            val nowMs = clock.nowMs()
            RadarLogger.w(
                "KM_V2_OBSERVATION",
                "KM_V2_SCREENSHOT_BLOCKED_MISSING_CAPABILITY",
                "requestId" to request.id,
                "message" to "disable_and_enable_accessibility_service_after_reinstall"
            )
            logLatency(status = ScreenshotFinishStatus.EXCEPTION, request = request, startedAtMs = nowMs, finishedAtMs = nowMs)
            onFailure(request, "missing_accessibility_screenshot_capability", null, nowMs, nowMs, ScreenshotFinishStatus.EXCEPTION)
            onFinished(request, ScreenshotFinishStatus.EXCEPTION)
            return
        }

        val startedAtMs = clock.nowMs()
        val finished = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (finished.compareAndSet(false, true)) {
                val finishedAtMs = clock.nowMs()
                RadarLogger.w(
                    "KM_V2_OBSERVATION",
                    "KM_V2_SCREENSHOT_TIMEOUT",
                    "requestId" to request.id
                )
                logLatency(status = ScreenshotFinishStatus.TIMEOUT, request = request, startedAtMs = startedAtMs, finishedAtMs = finishedAtMs)
                onFinished(request, ScreenshotFinishStatus.TIMEOUT)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, SCREENSHOT_TIMEOUT_MS)

        RadarLogger.i(
            "KM_V2_OBSERVATION",
            "KM_V2_SCREENSHOT_STARTED",
            "requestId" to request.id,
            "triggerSource" to request.triggerSource
        )

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        handleCallback(
                            request = request,
                            startedAtMs = startedAtMs,
                            finished = finished,
                            timeoutRunnable = timeoutRunnable,
                            onFailure = onFailure,
                            onFinished = onFinished
                        ) {
                            var hardwareBuffer: HardwareBuffer? = null
                            var bitmap: Bitmap? = null
                            var probeBitmap: Bitmap? = null
                            try {
                                hardwareBuffer = screenshotResult.hardwareBuffer
                                bitmap = hardwareBuffer?.toBitmap(screenshotResult.colorSpace)
                                val width = bitmap?.width ?: hardwareBuffer?.width ?: 0
                                val height = bitmap?.height ?: hardwareBuffer?.height ?: 0
                                val capturedAtMs = clock.nowMs()
                                probeBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                val savedPath = bitmap?.let { saveDebugScreenshot(request, it, capturedAtMs) }
                                val latencyMs = capturedAtMs - startedAtMs
                                RadarLogger.i(
                                    "KM_V2_OBSERVATION",
                                    "KM_V2_SCREENSHOT_SUCCESS",
                                    "requestId" to request.id,
                                    "width" to width,
                                    "height" to height,
                                    "latencyMs" to latencyMs
                                )
                                onSuccess(
                                    request,
                                    ScreenshotCaptureResult(
                                        screenshotStartedAtMs = startedAtMs,
                                        screenshotFinishedAtMs = capturedAtMs,
                                        capturedAtMs = capturedAtMs,
                                        screenshotWidth = width,
                                        screenshotHeight = height,
                                        screenshotBitmap = probeBitmap,
                                        savedDebugPath = savedPath
                                    )
                                )
                                logLatency(status = ScreenshotFinishStatus.SUCCESS, request = request, startedAtMs = startedAtMs, finishedAtMs = capturedAtMs)
                                onFinished(request, ScreenshotFinishStatus.SUCCESS)
                            } finally {
                                bitmap?.recycle()
                                hardwareBuffer?.close()
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        handleCallback(
                            request = request,
                            startedAtMs = startedAtMs,
                            finished = finished,
                            timeoutRunnable = timeoutRunnable,
                            onFailure = onFailure,
                            onFinished = onFinished
                        ) {
                            RadarLogger.w(
                                "KM_V2_OBSERVATION",
                                "KM_V2_SCREENSHOT_FAILED",
                                "requestId" to request.id,
                                "errorCode" to errorCode,
                                "message" to "take_screenshot_failed_code_$errorCode"
                            )
                            val finishedAtMs = clock.nowMs()
                            logLatency(status = ScreenshotFinishStatus.FAILED, request = request, startedAtMs = startedAtMs, finishedAtMs = finishedAtMs)
                            onFailure(request, "take_screenshot_failed_code_$errorCode", errorCode, startedAtMs, finishedAtMs, ScreenshotFinishStatus.FAILED)
                            onFinished(request, ScreenshotFinishStatus.FAILED)
                        }
                    }
                }
            )
        } catch (throwable: Throwable) {
            if (finished.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                RadarLogger.w(
                    "KM_V2_OBSERVATION",
                    "KM_V2_SCREENSHOT_CALLBACK_EXCEPTION",
                    "requestId" to request.id,
                    "error" to throwable.message,
                    "stacktrace" to throwable.stackTraceToString()
                )
                val finishedAtMs = clock.nowMs()
                logLatency(status = ScreenshotFinishStatus.EXCEPTION, request = request, startedAtMs = startedAtMs, finishedAtMs = finishedAtMs)
                onFailure(request, throwable.message ?: "take_screenshot_invocation_exception", null, startedAtMs, finishedAtMs, ScreenshotFinishStatus.EXCEPTION)
                onFinished(request, ScreenshotFinishStatus.EXCEPTION)
            }
        }
    }

    private fun handleCallback(
        request: CaptureRequest,
        startedAtMs: Long,
        finished: AtomicBoolean,
        timeoutRunnable: Runnable,
        onFailure: (CaptureRequest, String, Int?, Long, Long, ScreenshotFinishStatus) -> Unit,
        onFinished: (CaptureRequest, ScreenshotFinishStatus) -> Unit,
        block: () -> Unit
    ) {
        if (!finished.compareAndSet(false, true)) {
            return
        }
        mainHandler.removeCallbacks(timeoutRunnable)
        try {
            block()
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OBSERVATION",
                "KM_V2_SCREENSHOT_CALLBACK_EXCEPTION",
                "requestId" to request.id,
                "error" to throwable.message,
                "latencyMs" to (clock.nowMs() - startedAtMs),
                "stacktrace" to throwable.stackTraceToString()
            )
            val finishedAtMs = clock.nowMs()
            logLatency(status = ScreenshotFinishStatus.EXCEPTION, request = request, startedAtMs = startedAtMs, finishedAtMs = finishedAtMs)
            onFailure(
                request,
                throwable.message ?: "screenshot_callback_exception",
                null,
                startedAtMs,
                finishedAtMs,
                ScreenshotFinishStatus.EXCEPTION
            )
            onFinished(request, ScreenshotFinishStatus.EXCEPTION)
        }
    }

    private fun HardwareBuffer.toBitmap(colorSpace: ColorSpace?): Bitmap? {
        return Bitmap.wrapHardwareBuffer(this, colorSpace)
    }

    private fun saveDebugScreenshot(request: CaptureRequest, bitmap: Bitmap, capturedAtMs: Long): String? {
        if (!RadarFeatureFlags.ENABLE_DEBUG_SCREENSHOT_SAVE) {
            return null
        }
        return try {
            val outputDir = File(service.filesDir, "radar_debug_screenshots").apply { mkdirs() }
            val outputFile = File(outputDir, "${request.id}_$capturedAtMs.png")
            val saveBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            try {
                FileOutputStream(outputFile).use { stream ->
                    saveBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                outputFile.absolutePath
            } finally {
                if (saveBitmap !== bitmap) {
                    saveBitmap.recycle()
                }
            }
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OBSERVATION",
                "KM_V2_SCREENSHOT_DEBUG_SAVE_FAILED",
                "requestId" to request.id,
                "error" to throwable.message,
                "stacktrace" to throwable.stackTraceToString()
            )
            null
        }
    }

    private companion object {
        const val SCREENSHOT_TIMEOUT_MS = 2000L
    }

    private fun hasScreenshotCapability(): Boolean {
        return (service.serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
    }

    private fun logLatency(
        status: ScreenshotFinishStatus,
        request: CaptureRequest,
        startedAtMs: Long,
        finishedAtMs: Long
    ) {
        val screenshotDurationMs = finishedAtMs - startedAtMs
        RadarLogger.i(
            "KM_V2_OBSERVATION",
            "KM_V2_LATENCY_SCREENSHOT",
            "requestId" to request.id,
            "status" to status.name.lowercase(),
            "screenshotDurationMs" to screenshotDurationMs,
            "requestToExceptionMs" to (finishedAtMs - request.createdAtMs)
        )
    }
}
