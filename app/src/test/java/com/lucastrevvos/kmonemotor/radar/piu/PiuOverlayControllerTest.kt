package com.lucastrevvos.kmonemotor.radar.piu

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisRequestResult
import com.lucastrevvos.kmonemotor.radar.core.ManualAnalysisState
import com.lucastrevvos.kmonemotor.radar.core.ManualRejectReason
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

class PiuOverlayControllerTest {
    private val context: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.lucastrevvos.kmonemotor"
    }

    @Before
    fun setUp() {
        ManualAnalysisState.resetForTests()
    }

    @Test
    fun missingPermission_doesNotShowOverlay() {
        val host = FakeOverlayWindowHost()
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = FakePiuOverlayViewFactory(),
            positionStore = FakePiuOverlayPositionStore(),
            overlayPermissionChecker = { false },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        val shown = controller.show()

        assertFalse(shown)
        assertFalse(controller.isShowing())
        assertEquals(0, host.addCount)
    }

    @Test
    fun showAndHide_updatesShowingState() {
        val host = FakeOverlayWindowHost()
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = FakePiuOverlayViewFactory(),
            positionStore = FakePiuOverlayPositionStore(),
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        assertTrue(controller.show())
        assertTrue(controller.isShowing())

        controller.hide()

        assertFalse(controller.isShowing())
        assertEquals(1, host.removeCount)
    }

    @Test
    fun analyzeClick_sendsManualRequestWithPiuSource() {
        val host = FakeOverlayWindowHost()
        val factory = FakePiuOverlayViewFactory()
        var sentSource: String? = null
        var sentAtMs: Long? = null
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            clock = RadarClock { 1234L },
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { source, clickedAtMs ->
                sentSource = source
                sentAtMs = clickedAtMs
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        controller.show()
        factory.lastHandle?.performAnalyzeClick()

        assertEquals("piu_overlay", sentSource)
        assertEquals(1234L, sentAtMs)
    }

    @Test
    fun drag_savesAndRestoresX() {
        val host = FakeOverlayWindowHost()
        val factory = FakePiuOverlayViewFactory()
        val positionStore = FakePiuOverlayPositionStore(initialX = 50)
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = factory,
            positionStore = positionStore,
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        controller.show()
        factory.lastHandle?.performDragTo(180)

        assertEquals(180, positionStore.savedX ?: -1)
        assertTrue(host.updatedXs.contains(180))
    }

    @Test
    fun show_defaultsToRightEdgeUsingHandleWidth() {
        val host = FakeOverlayWindowHost()
        val factory = FakePiuOverlayViewFactory(handleWidthPx = 268)
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(initialX = null),
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        controller.show()

        assertEquals(812, host.addedXs.single())
    }

    @Test
    fun drag_clampsAgainstCurrentHandleWidth() {
        val host = FakeOverlayWindowHost()
        val factory = FakePiuOverlayViewFactory(handleWidthPx = 268)
        val positionStore = FakePiuOverlayPositionStore(initialX = 0)
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = factory,
            positionStore = positionStore,
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        controller.show()
        factory.lastHandle?.performDragTo(2_000)

        assertEquals(812, positionStore.savedX ?: -1)
        assertTrue(host.updatedXs.contains(812))
    }

    @Test
    fun acceptedClick_disablesButton() {
        val factory = FakePiuOverlayViewFactory()
        val dispatcher = FakeMainThreadDispatcher()
        val controller = PiuOverlayController(
            context = context,
            host = FakeOverlayWindowHost(),
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            clock = RadarClock { 1_000L },
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = dispatcher
        )

        controller.show()
        factory.lastHandle?.performAnalyzeClick()

        assertFalse(factory.lastHandle?.analyzeEnabled ?: true)
        assertEquals("Lendo...", factory.lastHandle?.analyzeLabel)
    }

    @Test
    fun throttledClick_keepsButtonWaiting() {
        val factory = FakePiuOverlayViewFactory()
        ManualAnalysisState.tryStart(1_000L)
        ManualAnalysisState.finish(1_100L)
        val controller = PiuOverlayController(
            context = context,
            host = FakeOverlayWindowHost(),
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            clock = RadarClock { 1_500L },
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, _ ->
                ManualAnalysisRequestResult.Rejected(
                    reason = ManualRejectReason.THROTTLED,
                    cooldownRemainingMs = 500L
                )
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = FakeMainThreadDispatcher()
        )

        controller.show()
        factory.lastHandle?.performAnalyzeClick()

        assertFalse(factory.lastHandle?.analyzeEnabled ?: true)
        assertEquals("Aguarde", factory.lastHandle?.analyzeLabel)
    }

    @Test
    fun stateFinish_reEnablesButtonAfterCooldownTask() {
        val factory = FakePiuOverlayViewFactory()
        val scheduler = FakeDelayedActionScheduler()
        val dispatcher = FakeMainThreadDispatcher()
        var nowMs = 1_000L
        val controller = PiuOverlayController(
            context = context,
            host = FakeOverlayWindowHost(),
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            clock = RadarClock { nowMs },
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = scheduler,
            mainThreadDispatcher = dispatcher
        )

        controller.show()
        factory.lastHandle?.performAnalyzeClick()
        ManualAnalysisState.finish(nowMs = 1_100L)
        nowMs = 3_100L
        scheduler.runAll()

        assertTrue(factory.lastHandle?.analyzeEnabled ?: false)
        assertEquals("Analisar", factory.lastHandle?.analyzeLabel)
    }

    @Test
    fun backgroundManualStateFinish_dispatchesButtonUpdateToMain() {
        val factory = FakePiuOverlayViewFactory()
        val dispatcher = FakeMainThreadDispatcher()
        val controller = PiuOverlayController(
            context = context,
            host = FakeOverlayWindowHost(),
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            clock = RadarClock { 1_000L },
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = dispatcher
        )

        controller.show()
        factory.lastHandle?.performAnalyzeClick()
        dispatcher.onMainThread = false

        val worker = thread {
            ManualAnalysisState.finish(nowMs = 3_000L)
        }
        worker.join()

        assertFalse(factory.lastHandle?.mainThreadOnlyViolation ?: false)
        assertEquals(1, dispatcher.pendingCount())

        dispatcher.runAllPosted()

        assertTrue(factory.lastHandle?.analyzeEnabled ?: false)
        assertEquals("Analisar", factory.lastHandle?.analyzeLabel)
        assertFalse(factory.lastHandle?.mainThreadOnlyViolation ?: false)
    }

    @Test
    fun showHideAndDrag_canDispatchFromBackground() {
        val host = FakeOverlayWindowHost()
        val factory = FakePiuOverlayViewFactory()
        val dispatcher = FakeMainThreadDispatcher(autoRunPosted = true)
        dispatcher.onMainThread = false
        val controller = PiuOverlayController(
            context = context,
            host = host,
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = dispatcher
        )

        assertTrue(controller.show())
        factory.lastHandle?.performDragTo(240)
        controller.hide()

        assertEquals(1, host.addCount)
        assertEquals(1, host.removeCount)
        assertTrue(host.updatedXs.contains(240))
        assertTrue(dispatcher.postCount >= 3)
    }

    @Test
    fun updateEarningsText_dispatchesToMain() {
        val factory = FakePiuOverlayViewFactory()
        val dispatcher = FakeMainThreadDispatcher(autoRunPosted = true)
        val controller = PiuOverlayController(
            context = context,
            host = FakeOverlayWindowHost(),
            viewFactory = factory,
            positionStore = FakePiuOverlayPositionStore(),
            overlayPermissionChecker = { true },
            screenWidthPxProvider = { 1080 },
            topInsetPxProvider = { 12 },
            defaultOverlayWidthPxProvider = { 220 },
            manualRequestSender = { _, clickedAtMs ->
                ManualAnalysisState.tryStart(clickedAtMs)
                ManualAnalysisRequestResult.Sent
            },
            delayedActionScheduler = FakeDelayedActionScheduler(),
            mainThreadDispatcher = dispatcher
        )

        controller.show()
        dispatcher.onMainThread = false
        controller.updateEarningsText("R$ 12,34")

        assertEquals("R$ 12,34", factory.lastHandle?.earningsText)
        assertFalse(factory.lastHandle?.mainThreadOnlyViolation ?: false)
    }

    private class FakePiuOverlayPositionStore(
        initialX: Int? = 0
    ) : PiuOverlayPositionStore {
        private var currentX = initialX
        var savedX: Int? = initialX

        override fun restoreX(defaultValue: Int): Int = currentX ?: defaultValue

        override fun saveX(value: Int) {
            currentX = value
            savedX = value
        }
    }

    private class FakePiuOverlayViewFactory(
        private val handleWidthPx: Int = 220
    ) : PiuOverlayViewFactory {
        var lastHandle: FakePiuOverlayViewHandle? = null

        override fun create(initialEarningsText: String): PiuOverlayViewHandle {
            return FakePiuOverlayViewHandle(handleWidthPx).also { lastHandle = it }
        }
    }

    private class FakePiuOverlayViewHandle(
        private val handleWidthPx: Int
    ) : PiuOverlayViewHandle {
        override val platformView: Any = Any()
        override val estimatedWidthPx: Int = handleWidthPx
        override val currentWidthPx: Int = handleWidthPx
        private var analyzeListener: (() -> Unit)? = null
        private var dragListener: ((Int) -> Unit)? = null
        var analyzeEnabled: Boolean = true
        var analyzeLabel: String = "Analisar"
        var earningsText: String = ""
        var mainThreadOnlyViolation: Boolean = false

        override fun updateEarningsText(value: String) {
            earningsText = value
        }

        override fun updateAnalyzeButton(enabled: Boolean, label: String) {
            analyzeEnabled = enabled
            analyzeLabel = label
        }

        override fun bindAnalyzeClick(listener: () -> Unit) {
            analyzeListener = listener
        }

        override fun bindHorizontalDrag(currentXProvider: () -> Int, listener: (Int) -> Unit) {
            dragListener = listener
        }

        fun performAnalyzeClick() {
            analyzeListener?.invoke()
        }

        fun performDragTo(x: Int) {
            dragListener?.invoke(x)
        }
    }

    private class FakeOverlayWindowHost : OverlayWindowHost {
        var addCount = 0
        var removeCount = 0
        val addedXs = mutableListOf<Int>()
        val updatedXs = mutableListOf<Int>()

        override fun add(handle: PiuOverlayViewHandle, params: WindowManager.LayoutParams) {
            addCount += 1
            addedXs += params.x
        }

        override fun update(handle: PiuOverlayViewHandle, params: WindowManager.LayoutParams) {
            updatedXs += params.x
        }

        override fun remove(handle: PiuOverlayViewHandle) {
            removeCount += 1
        }
    }

    private class FakeDelayedActionScheduler : DelayedActionScheduler {
        private val tasks = mutableListOf<() -> Unit>()

        override fun schedule(delayMs: Long, action: () -> Unit): DelayedActionHandle {
            tasks += action
            return DelayedActionHandle { tasks.remove(action) }
        }

        fun runAll() {
            val pending = tasks.toList()
            tasks.clear()
            pending.forEach { it() }
        }
    }

    private class FakeMainThreadDispatcher(
        private val autoRunPosted: Boolean = false
    ) : MainThreadDispatcher {
        var onMainThread: Boolean = true
        var postCount: Int = 0
        private val pending = mutableListOf<() -> Unit>()

        override fun isMainThread(): Boolean = onMainThread

        override fun post(block: () -> Unit) {
            postCount += 1
            if (autoRunPosted) {
                val previous = onMainThread
                onMainThread = true
                try {
                    block()
                } finally {
                    onMainThread = previous
                }
                return
            }
            pending += {
                val previous = onMainThread
                onMainThread = true
                try {
                    block()
                } finally {
                    onMainThread = previous
                }
            }
        }

        fun pendingCount(): Int = pending.size

        fun runAllPosted() {
            val tasks = pending.toList()
            pending.clear()
            tasks.forEach { it() }
        }
    }
}
