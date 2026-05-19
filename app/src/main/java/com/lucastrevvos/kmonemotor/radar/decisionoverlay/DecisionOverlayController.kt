package com.lucastrevvos.kmonemotor.radar.decisionoverlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentation
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

interface DecisionOverlayWindowHost {
    fun add(handle: DecisionOverlayViewHandle, params: WindowManager.LayoutParams)
    fun update(handle: DecisionOverlayViewHandle, params: WindowManager.LayoutParams)
    fun remove(handle: DecisionOverlayViewHandle)
}

fun interface DecisionOverlayDelayedActionHandle {
    fun cancel()
}

fun interface DecisionOverlayDelayedActionScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): DecisionOverlayDelayedActionHandle
}

interface DecisionOverlayMainThreadDispatcher {
    fun isMainThread(): Boolean
    fun post(block: () -> Unit)
}

class AndroidDecisionOverlayWindowHost(
    context: Context
) : DecisionOverlayWindowHost {
    private val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun add(handle: DecisionOverlayViewHandle, params: WindowManager.LayoutParams) {
        windowManager.addView(handle.platformView as View, params)
    }

    override fun update(handle: DecisionOverlayViewHandle, params: WindowManager.LayoutParams) {
        windowManager.updateViewLayout(handle.platformView as View, params)
    }

    override fun remove(handle: DecisionOverlayViewHandle) {
        windowManager.removeView(handle.platformView as View)
    }
}

class DecisionOverlayController(
    private val context: Context,
    private val host: DecisionOverlayWindowHost = AndroidDecisionOverlayWindowHost(context),
    private val viewFactory: DecisionOverlayViewFactory = AndroidDecisionOverlayViewFactory(context),
    private val clock: RadarClock = RadarClock.System,
    private val overlayPermissionChecker: () -> Boolean = { Settings.canDrawOverlays(context) },
    private val screenWidthPxProvider: () -> Int = { context.resources.displayMetrics.widthPixels },
    private val topInsetPxProvider: () -> Int = { 120.dpToPx(context) },
    private val delayedActionScheduler: DecisionOverlayDelayedActionScheduler = AndroidDecisionOverlayDelayedActionScheduler(),
    private val mainThreadDispatcher: DecisionOverlayMainThreadDispatcher = AndroidDecisionOverlayMainThreadDispatcher()
) {
    private var state = DecisionOverlayState()
    private var viewHandle: DecisionOverlayViewHandle? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expiryHandle: DecisionOverlayDelayedActionHandle? = null

    fun showPresentation(presentation: DecisionPresentation): Boolean {
        RadarLogger.i(
            "KM_V2_DECISION_OVERLAY",
            "KM_V2_DECISION_OVERLAY_SHOW_REQUESTED",
            "observationId" to presentation.observationId,
            "kind" to presentation.kind,
            "expiresAtMs" to presentation.expiresAtMs
        )
        return runOnMainSync("show_presentation") {
            when {
                presentation.kind == DecisionPresentationKind.DO_NOT_SHOW -> {
                    logSkip("do_not_show", presentation)
                    false
                }
                presentation.expiresAtMs <= clock.nowMs() -> {
                    logSkip("expired", presentation)
                    false
                }
                !overlayPermissionChecker() -> {
                    RadarLogger.w("KM_V2_DECISION_OVERLAY", "KM_V2_DECISION_OVERLAY_PERMISSION_MISSING")
                    logSkip("missing_permission", presentation)
                    RadarDebugStore.updateDecisionOverlaySummary(
                        visible = false,
                        kind = presentation.kind.name,
                        title = presentation.title,
                        shortReason = presentation.shortReason,
                        shownAtMs = null,
                        expiresAtMs = presentation.expiresAtMs,
                        error = "overlay_permission_missing"
                    )
                    false
                }
                state.currentPresentation != null && presentation.createdAtMs < state.currentPresentation!!.createdAtMs -> {
                    RadarLogger.i(
                        "KM_V2_DECISION_OVERLAY",
                        "KM_V2_DECISION_OVERLAY_STALE_IGNORED",
                        "observationId" to presentation.observationId,
                        "currentObservationId" to state.currentPresentation?.observationId
                    )
                    false
                }
                else -> {
                    val handle = viewHandle ?: viewFactory.create().also { viewHandle = it }
                    val params = layoutParams ?: buildLayoutParams().also { layoutParams = it }
                    handle.render(presentation)
                    val nowMs = clock.nowMs()
                    try {
                        if (!state.showing) {
                            host.add(handle, params)
                            RadarLogger.i(
                                "KM_V2_DECISION_OVERLAY",
                                "KM_V2_DECISION_OVERLAY_SHOWN",
                                "observationId" to presentation.observationId,
                                "kind" to presentation.kind
                            )
                        } else {
                            host.update(handle, params)
                            RadarLogger.i(
                                "KM_V2_DECISION_OVERLAY",
                                "KM_V2_DECISION_OVERLAY_UPDATED",
                                "observationId" to presentation.observationId,
                                "kind" to presentation.kind
                            )
                        }
                        state = state.copy(
                            showing = true,
                            currentPresentation = presentation,
                            shownAtMs = nowMs
                        )
                        RadarDebugStore.updateDecisionOverlaySummary(
                            visible = true,
                            kind = presentation.kind.name,
                            title = presentation.title,
                            shortReason = presentation.shortReason,
                            shownAtMs = nowMs,
                            expiresAtMs = presentation.expiresAtMs,
                            error = null
                        )
                        scheduleExpiry(presentation)
                        RadarLogger.i(
                            "KM_V2_DECISION_OVERLAY",
                            "KM_V2_LATENCY_DECISION_OVERLAY",
                            "observationId" to presentation.observationId,
                            "durationMs" to 0L
                        )
                        true
                    } catch (throwable: Throwable) {
                        RadarLogger.w(
                            "KM_V2_DECISION_OVERLAY",
                            "KM_V2_DECISION_OVERLAY_UI_UPDATE_FAILED",
                            "operation" to "show_presentation",
                            "threadName" to Thread.currentThread().name,
                            "error" to throwable.message
                        )
                        RadarDebugStore.updateDecisionOverlaySummary(
                            visible = state.showing,
                            kind = presentation.kind.name,
                            title = presentation.title,
                            shortReason = presentation.shortReason,
                            shownAtMs = state.shownAtMs,
                            expiresAtMs = presentation.expiresAtMs,
                            error = throwable.message
                        )
                        false
                    }
                }
            }
        }
    }

    fun hide() {
        runOnMain("hide") {
            hideInternal("manual_hide")
        }
    }

    fun destroy() {
        runOnMain("destroy") {
            hideInternal("destroy")
            viewHandle = null
        }
    }

    private fun scheduleExpiry(presentation: DecisionPresentation) {
        expiryHandle?.cancel()
        val delayMs = (presentation.expiresAtMs - clock.nowMs()).coerceAtLeast(0L)
        expiryHandle = delayedActionScheduler.schedule(delayMs) {
            runOnMain("expire") {
                val current = state.currentPresentation
                if (current == null || current.createdAtMs != presentation.createdAtMs) {
                    return@runOnMain
                }
                RadarLogger.i(
                    "KM_V2_DECISION_OVERLAY",
                    "KM_V2_DECISION_OVERLAY_EXPIRED",
                    "observationId" to presentation.observationId
                )
                hideInternal("expired")
            }
        }
    }

    private fun hideInternal(reason: String) {
        expiryHandle?.cancel()
        expiryHandle = null
        val handle = viewHandle
        if (state.showing && handle != null) {
            try {
                host.remove(handle)
            } catch (throwable: Throwable) {
                RadarLogger.w(
                    "KM_V2_DECISION_OVERLAY",
                    "KM_V2_DECISION_OVERLAY_UI_UPDATE_FAILED",
                    "operation" to "hide",
                    "threadName" to Thread.currentThread().name,
                    "error" to throwable.message
                )
            }
            RadarLogger.i(
                "KM_V2_DECISION_OVERLAY",
                "KM_V2_DECISION_OVERLAY_HIDDEN",
                "reason" to reason
            )
        }
        state = state.copy(showing = false, currentPresentation = null, shownAtMs = null)
        layoutParams = null
        RadarDebugStore.updateDecisionOverlaySummary(
            visible = false,
            error = null
        )
    }

    private fun logSkip(reason: String, presentation: DecisionPresentation) {
        RadarLogger.i(
            "KM_V2_DECISION_OVERLAY",
            "KM_V2_DECISION_OVERLAY_SKIPPED",
            "observationId" to presentation.observationId,
            "reason" to reason,
            "kind" to presentation.kind
        )
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val width = (screenWidthPxProvider() * 0.9f).toInt()
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topInsetPxProvider()
        }
    }

    private fun runOnMain(operation: String, block: () -> Unit) {
        if (mainThreadDispatcher.isMainThread()) {
            executeUiOperation(operation, block)
        } else {
            RadarLogger.i(
                "KM_V2_DECISION_OVERLAY",
                "KM_V2_DECISION_OVERLAY_MAIN_THREAD_DISPATCHED",
                "operation" to operation,
                "threadName" to Thread.currentThread().name
            )
            mainThreadDispatcher.post {
                executeUiOperation(operation, block)
            }
        }
    }

    private fun <T> runOnMainSync(operation: String, block: () -> T): T {
        if (mainThreadDispatcher.isMainThread()) {
            return executeUiOperationSync(operation, block)
        }
        RadarLogger.i(
            "KM_V2_DECISION_OVERLAY",
            "KM_V2_DECISION_OVERLAY_MAIN_THREAD_DISPATCHED",
            "operation" to operation,
            "threadName" to Thread.currentThread().name
        )
        val latch = CountDownLatch(1)
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable?>()
        mainThreadDispatcher.post {
            try {
                result.set(executeUiOperationSync(operation, block))
            } catch (throwable: Throwable) {
                error.set(throwable)
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        error.get()?.let { throw it }
        return result.get()
    }

    private fun executeUiOperation(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_DECISION_OVERLAY",
                "KM_V2_DECISION_OVERLAY_UI_UPDATE_FAILED",
                "operation" to operation,
                "threadName" to Thread.currentThread().name,
                "error" to throwable.message
            )
        }
    }

    private fun <T> executeUiOperationSync(operation: String, block: () -> T): T {
        try {
            return block()
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_DECISION_OVERLAY",
                "KM_V2_DECISION_OVERLAY_UI_UPDATE_FAILED",
                "operation" to operation,
                "threadName" to Thread.currentThread().name,
                "error" to throwable.message
            )
            throw throwable
        }
    }
}

private class AndroidDecisionOverlayDelayedActionScheduler : DecisionOverlayDelayedActionScheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): DecisionOverlayDelayedActionHandle {
        val handler = Handler(Looper.getMainLooper())
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return DecisionOverlayDelayedActionHandle { handler.removeCallbacks(runnable) }
    }
}

private class AndroidDecisionOverlayMainThreadDispatcher : DecisionOverlayMainThreadDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    override fun post(block: () -> Unit) {
        handler.post(block)
    }
}

private fun Int.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()
