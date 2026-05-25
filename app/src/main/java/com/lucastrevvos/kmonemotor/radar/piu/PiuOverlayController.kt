package com.lucastrevvos.kmonemotor.radar.piu

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequestResult
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequestBus
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisSnapshot
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisState
import com.lucastrevvos.kmonemotor.radar.core.ManualRejectReason
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

interface OverlayWindowHost {
    fun add(handle: PiuOverlayViewHandle, params: WindowManager.LayoutParams)
    fun update(handle: PiuOverlayViewHandle, params: WindowManager.LayoutParams)
    fun remove(handle: PiuOverlayViewHandle)
}

fun interface DelayedActionHandle {
    fun cancel()
}

fun interface DelayedActionScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): DelayedActionHandle
}

interface MainThreadDispatcher {
    fun isMainThread(): Boolean
    fun post(block: () -> Unit)
}

class AndroidOverlayWindowHost(
    context: Context
) : OverlayWindowHost {
    private val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun add(handle: PiuOverlayViewHandle, params: WindowManager.LayoutParams) {
        windowManager.addView(handle.platformView as View, params)
    }

    override fun update(handle: PiuOverlayViewHandle, params: WindowManager.LayoutParams) {
        windowManager.updateViewLayout(handle.platformView as View, params)
    }

    override fun remove(handle: PiuOverlayViewHandle) {
        windowManager.removeView(handle.platformView as View)
    }
}

class PiuOverlayController(
    private val context: Context,
    private val host: OverlayWindowHost = AndroidOverlayWindowHost(context),
    private val viewFactory: PiuOverlayViewFactory = AndroidPiuOverlayViewFactory(context),
    private val positionStore: PiuOverlayPositionStore = SharedPrefsPiuOverlayPositionStore(context),
    private val clock: RadarClock = RadarClock.System,
    private val overlayPermissionChecker: () -> Boolean = { Settings.canDrawOverlays(context) },
    private val screenWidthPxProvider: () -> Int = { context.resources.displayMetrics.widthPixels },
    private val topInsetPxProvider: () -> Int = { 12.dpToPx(context) },
    private val defaultOverlayWidthPxProvider: () -> Int = { 92.dpToPx(context) },
    private val manualRequestSender: (String, Long) -> ManualAnalysisRequestResult = { source, clickedAtMs ->
        ManualAnalysisRequestBus.requestAnalysisDetailed(
            source = source,
            clickedAtMs = clickedAtMs
        )
    },
    private val delayedActionScheduler: DelayedActionScheduler = AndroidDelayedActionScheduler(),
    private val mainThreadDispatcher: MainThreadDispatcher = AndroidMainThreadDispatcher()
) {
    private companion object {
        const val DRAG_POSITION_PERSIST_DEBOUNCE_MS = 220L
    }

    private var state = PiuOverlayState()
    private var viewHandle: PiuOverlayViewHandle? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pendingEnableHandle: DelayedActionHandle? = null
    private var pendingPersistHandle: DelayedActionHandle? = null
    private val manualStateListener: (ManualAnalysisSnapshot) -> Unit = { snapshot ->
        runOnMain("manual_state_listener") {
            refreshAnalyzeButtonInternal(snapshot)
        }
    }

    fun show(): Boolean {
        return runOnMainSync("show") {
            if (!overlayPermissionChecker()) {
                RadarLogger.w(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_OVERLAY_PERMISSION_MISSING"
                )
                RadarDebugStore.updatePiuOverlayState(
                    permissionGranted = false,
                    showing = false,
                    x = state.x,
                    error = "overlay_permission_missing"
                )
                return@runOnMainSync false
            }
            RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_OVERLAY_PERMISSION_GRANTED")
            if (state.showing) {
                return@runOnMainSync true
            }
            val handle = viewHandle ?: viewFactory.create(state.earningsText).also { created ->
                RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_CREATED", "mode" to "overlay_real")
                created.bindAnalyzeClick(::handleAnalyzeClick)
                created.bindHorizontalDrag(
                    currentXProvider = { state.x },
                    listener = ::handleHorizontalDrag
                )
                viewHandle = created
            }
            ManualAnalysisState.registerListener(manualStateListener)
            refreshAnalyzeButtonInternal(ManualAnalysisState.snapshot(clock.nowMs()))
            val restoredX = positionStore.restoreX(defaultValue = defaultX())
            RadarLogger.i(
                "KM_V2_OVERLAY",
                "KM_V2_PIU_POSITION_RESTORED",
                "x" to restoredX
            )
            val params = buildLayoutParams(handle, restoredX)
            try {
                host.add(handle, params)
                layoutParams = params
                state = state.copy(showing = true, x = restoredX)
                RadarLogger.i(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_SHOWN",
                    "x" to restoredX
                )
                RadarDebugStore.updatePiuOverlayState(
                    permissionGranted = true,
                    showing = true,
                    x = restoredX
                )
                true
            } catch (throwable: Throwable) {
                ManualAnalysisState.unregisterListener(manualStateListener)
                cancelPendingEnable()
                RadarLogger.w(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_ERROR",
                    "message" to throwable.message,
                    "stacktrace" to throwable.stackTraceToString()
                )
                RadarDebugStore.updatePiuOverlayState(
                    permissionGranted = true,
                    showing = false,
                    x = restoredX,
                    error = throwable.message
                )
                false
            }
        }
    }

    fun hide() {
        runOnMain("hide") {
            val handle = viewHandle ?: return@runOnMain
            if (!state.showing) {
                return@runOnMain
            }
            try {
                host.remove(handle)
                RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_HIDDEN")
            } catch (throwable: Throwable) {
                RadarLogger.w(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_ERROR",
                    "message" to throwable.message
                )
            } finally {
                persistOverlayPosition(immediate = true)
                state = state.copy(showing = false)
                layoutParams = null
                ManualAnalysisState.unregisterListener(manualStateListener)
                cancelPendingEnable()
                cancelPendingPersist()
                RadarDebugStore.updatePiuOverlayState(
                    permissionGranted = overlayPermissionChecker(),
                    showing = false,
                    x = state.x
                )
            }
        }
    }

    fun toggle(): Boolean {
        return if (state.showing) {
            hide()
            false
        } else {
            show()
        }
    }

    fun isShowing(): Boolean = state.showing

    fun updateEarningsText(value: String) {
        state = state.copy(earningsText = value)
        runOnMain("update_earnings_text") {
            viewHandle?.updateEarningsText(value)
        }
    }

    fun destroy() {
        runOnMain("destroy") {
            hide()
            ManualAnalysisState.unregisterListener(manualStateListener)
            cancelPendingEnable()
            viewHandle = null
            RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_DESTROYED")
        }
    }

    private fun handleAnalyzeClick() {
        val clickedAtMs = clock.nowMs()
        RadarLogger.i(
            "KM_V2_OVERLAY",
            "KM_V2_PIU_ANALYZE_CLICKED",
            "clickedAtMs" to clickedAtMs
        )
        RadarDebugStore.updatePiuOverlayState(
            permissionGranted = overlayPermissionChecker(),
            showing = state.showing,
            x = state.x,
            analyzeClickedAtMs = clickedAtMs
        )
        when (val result = manualRequestSender("piu_overlay", clickedAtMs)) {
            is ManualAnalysisRequestResult.Sent -> {
                RadarLogger.i("KM_V2_MANUAL", "KM_V2_MANUAL_ANALYSIS_CLICK_ACCEPTED", "source" to "piu_overlay")
                refreshAnalyzeButton(ManualAnalysisState.snapshot(clickedAtMs))
                RadarLogger.i(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_MANUAL_REQUEST_SENT",
                    "source" to "piu_overlay",
                    "clickedAtMs" to clickedAtMs
                )
            }

            is ManualAnalysisRequestResult.Rejected -> {
                val eventName = when (result.reason) {
                    ManualRejectReason.THROTTLED -> "KM_V2_MANUAL_ANALYSIS_CLICK_THROTTLED"
                    ManualRejectReason.ALREADY_RUNNING -> "KM_V2_MANUAL_ANALYSIS_ALREADY_RUNNING"
                }
                RadarLogger.i(
                    "KM_V2_MANUAL",
                    eventName,
                    "source" to "piu_overlay",
                    "cooldownRemainingMs" to result.cooldownRemainingMs
                )
                RadarLogger.i(
                    "KM_V2_MANUAL",
                    "KM_V2_LATENCY_MANUAL_ANALYSIS_SKIPPED",
                    "reason" to result.reason.name.lowercase(),
                    "source" to "piu_overlay"
                )
                RadarDebugStore.updateManualControlState(
                    lastRejectedReason = result.reason.name.lowercase(),
                    cooldownRemainingMs = result.cooldownRemainingMs
                )
                refreshAnalyzeButton(ManualAnalysisState.snapshot(clickedAtMs))
            }

            is ManualAnalysisRequestResult.ListenerMissing -> {
                RadarLogger.w(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_MANUAL_REQUEST_FAILED",
                    "source" to "piu_overlay",
                    "clickedAtMs" to clickedAtMs
                )
            }
        }
    }

    private fun handleHorizontalDrag(targetX: Int) {
        runOnMain("horizontal_drag") {
            val handle = viewHandle ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            val clampedX = clampOverlayX(
                targetX = targetX,
                screenWidthPx = screenWidthPxProvider(),
                overlayWidthPx = handle.currentWidthPx
            )
            params.x = clampedX
            try {
                host.update(handle, params)
                state = state.copy(x = clampedX)
                schedulePersistOverlayPosition()
            } catch (throwable: Throwable) {
                RadarLogger.w(
                    "KM_V2_OVERLAY",
                    "KM_V2_PIU_ERROR",
                    "message" to throwable.message
                )
                RadarDebugStore.updatePiuOverlayState(
                    permissionGranted = overlayPermissionChecker(),
                    showing = state.showing,
                    x = state.x,
                    error = throwable.message
                )
            }
        }
    }

    private fun buildLayoutParams(
        handle: PiuOverlayViewHandle,
        x: Int
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
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
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = topInsetPx()
        }
    }

    private fun defaultX(): Int {
        val overlayWidthPx = viewHandle?.currentWidthPx ?: defaultOverlayWidthPxProvider()
        return defaultOverlayX(
            screenWidthPx = screenWidthPxProvider(),
            overlayWidthPx = overlayWidthPx
        )
    }

    private fun topInsetPx(): Int = topInsetPxProvider()

    private fun refreshAnalyzeButton(snapshot: ManualAnalysisSnapshot) {
        runOnMain("refresh_analyze_button") {
            refreshAnalyzeButtonInternal(snapshot)
        }
    }

    private fun refreshAnalyzeButtonInternal(snapshot: ManualAnalysisSnapshot) {
        val handle = viewHandle ?: return
        val enabled = !snapshot.running && snapshot.cooldownRemainingMs <= 0L
        val label = if (snapshot.running) "Lendo..." else if (enabled) "Analisar" else "Aguarde"
        handle.updateAnalyzeButton(enabled = enabled, label = label)
        RadarDebugStore.updateManualControlState(
            running = snapshot.running,
            acceptedAtMs = snapshot.lastAcceptedAtMs.takeIf { it > 0L },
            lastRejectedReason = snapshot.lastRejectedReason?.name?.lowercase(),
            cooldownRemainingMs = snapshot.cooldownRemainingMs
        )
        if (enabled) {
            cancelPendingEnable()
            RadarLogger.i("KM_V2_OVERLAY", "KM_V2_PIU_ANALYZE_BUTTON_ENABLED")
        } else {
            RadarLogger.i(
                "KM_V2_OVERLAY",
                "KM_V2_PIU_ANALYZE_BUTTON_DISABLED",
                "running" to snapshot.running,
                "cooldownRemainingMs" to snapshot.cooldownRemainingMs
            )
            scheduleEnableIfNeeded(snapshot.cooldownRemainingMs)
        }
    }

    private fun scheduleEnableIfNeeded(delayMs: Long) {
        cancelPendingEnable()
        if (delayMs <= 0L) {
            return
        }
        val runnable = Runnable {
            refreshAnalyzeButton(ManualAnalysisState.snapshot(clock.nowMs()))
        }
        pendingEnableHandle = delayedActionScheduler.schedule(delayMs) {
            runnable.run()
        }
    }

    private fun cancelPendingEnable() {
        pendingEnableHandle?.cancel()
        pendingEnableHandle = null
    }

    private fun schedulePersistOverlayPosition() {
        cancelPendingPersist()
        pendingPersistHandle = delayedActionScheduler.schedule(DRAG_POSITION_PERSIST_DEBOUNCE_MS) {
            runOnMain("persist_overlay_position") {
                persistOverlayPosition(immediate = false)
            }
        }
    }

    private fun persistOverlayPosition(immediate: Boolean) {
        cancelPendingPersist()
        positionStore.saveX(state.x)
        RadarLogger.i(
            "KM_V2_OVERLAY",
            "KM_V2_PIU_POSITION_SAVED",
            "x" to state.x,
            "immediate" to immediate
        )
        RadarDebugStore.updatePiuOverlayState(
            permissionGranted = overlayPermissionChecker(),
            showing = state.showing,
            x = state.x
        )
    }

    private fun cancelPendingPersist() {
        pendingPersistHandle?.cancel()
        pendingPersistHandle = null
    }

    private fun runOnMain(operation: String, block: () -> Unit) {
        if (mainThreadDispatcher.isMainThread()) {
            executeUiOperation(operation, block)
        } else {
            RadarLogger.i(
                "KM_V2_OVERLAY",
                "KM_V2_PIU_MAIN_THREAD_DISPATCHED",
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
            "KM_V2_OVERLAY",
            "KM_V2_PIU_MAIN_THREAD_DISPATCHED",
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
            RadarLogger.d("KM_V2_OVERLAY", "KM_V2_PIU_MAIN_THREAD_EXECUTED", "operation" to operation)
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OVERLAY",
                "KM_V2_PIU_UI_UPDATE_FAILED",
                "operation" to operation,
                "threadName" to Thread.currentThread().name,
                "error" to throwable.message
            )
        }
    }

    private fun <T> executeUiOperationSync(operation: String, block: () -> T): T {
        try {
            val result = block()
            RadarLogger.d("KM_V2_OVERLAY", "KM_V2_PIU_MAIN_THREAD_EXECUTED", "operation" to operation)
            return result
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_OVERLAY",
                "KM_V2_PIU_UI_UPDATE_FAILED",
                "operation" to operation,
                "threadName" to Thread.currentThread().name,
                "error" to throwable.message
            )
            throw throwable
        }
    }
}

internal fun defaultOverlayX(
    screenWidthPx: Int,
    overlayWidthPx: Int
): Int {
    return (screenWidthPx - overlayWidthPx).coerceAtLeast(0)
}

internal fun clampOverlayX(
    targetX: Int,
    screenWidthPx: Int,
    overlayWidthPx: Int
): Int {
    val maxX = defaultOverlayX(
        screenWidthPx = screenWidthPx,
        overlayWidthPx = overlayWidthPx
    )
    return targetX.coerceIn(0, maxX)
}

private class AndroidDelayedActionScheduler : DelayedActionScheduler {
    override fun schedule(delayMs: Long, action: () -> Unit): DelayedActionHandle {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return DelayedActionHandle { handler.removeCallbacks(runnable) }
    }
}

private class AndroidMainThreadDispatcher : MainThreadDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    override fun post(block: () -> Unit) {
        handler.post(block)
    }
}

private fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
