package com.lucastrevvos.kmonemotor.radar.decisionoverlay

import android.content.ContextWrapper
import android.view.WindowManager
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentation
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationKind
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentationSource
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionSemantic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionOverlayControllerTest {
    @Test
    fun showBadCallsShow() {
        val host = FakeDecisionOverlayWindowHost()
        val factory = FakeDecisionOverlayViewFactory()
        val scheduler = FakeDecisionOverlayScheduler()
        val controller = controller(host, factory, scheduler)

        val shown = controller.showPresentation(presentation(kind = DecisionPresentationKind.SHOW_BAD))

        assertTrue(shown)
        assertEquals(1, host.addCount)
        assertEquals("Ruim", factory.lastHandle?.title)
    }

    @Test
    fun doNotShowIsSkipped() {
        val host = FakeDecisionOverlayWindowHost()
        val controller = controller(host = host)

        val shown = controller.showPresentation(presentation(kind = DecisionPresentationKind.DO_NOT_SHOW))

        assertFalse(shown)
        assertEquals(0, host.addCount)
    }

    @Test
    fun expiredPresentationIsIgnored() {
        val host = FakeDecisionOverlayWindowHost()
        val controller = controller(host = host, nowMs = 10_000L)

        val shown = controller.showPresentation(presentation(expiresAtMs = 9_000L))

        assertFalse(shown)
        assertEquals(0, host.addCount)
    }

    @Test
    fun stalePresentationIsIgnored() {
        val host = FakeDecisionOverlayWindowHost()
        val factory = FakeDecisionOverlayViewFactory()
        val scheduler = FakeDecisionOverlayScheduler()
        val controller = controller(host = host, factory = factory, scheduler = scheduler)

        assertTrue(controller.showPresentation(presentation(createdAtMs = 2_000L)))
        val shown = controller.showPresentation(presentation(createdAtMs = 1_000L, title = "Velha"))

        assertFalse(shown)
        assertEquals(1, host.addCount)
        assertEquals("Atencao", factory.lastHandle?.title)
    }

    @Test
    fun newerPresentationUpdatesActive() {
        val host = FakeDecisionOverlayWindowHost()
        val factory = FakeDecisionOverlayViewFactory()
        val scheduler = FakeDecisionOverlayScheduler()
        val controller = controller(host, factory, scheduler)

        assertTrue(controller.showPresentation(presentation(createdAtMs = 1_000L, title = "Primeira")))
        assertTrue(controller.showPresentation(presentation(createdAtMs = 2_000L, title = "Segunda")))

        assertEquals(1, host.addCount)
        assertEquals(1, host.updateCount)
        assertEquals("Segunda", factory.lastHandle?.title)
    }

    @Test
    fun hideIsScheduledWithCorrectDelayAndExpires() {
        val host = FakeDecisionOverlayWindowHost()
        val scheduler = FakeDecisionOverlayScheduler()
        val controller = controller(host = host, scheduler = scheduler, nowMs = 1_000L)

        controller.showPresentation(presentation(createdAtMs = 1_000L, expiresAtMs = 9_000L))

        assertEquals(8_000L, scheduler.lastDelayMs)
        scheduler.runLatest()
        assertEquals(1, host.removeCount)
    }

    @Test
    fun missingPermissionDoesNotCrash() {
        val host = FakeDecisionOverlayWindowHost()
        val controller = controller(host = host, permission = false)

        val shown = controller.showPresentation(presentation())

        assertFalse(shown)
        assertEquals(0, host.addCount)
    }

    @Test
    fun mainThreadDispatcherIsUsed() {
        val host = FakeDecisionOverlayWindowHost()
        val factory = FakeDecisionOverlayViewFactory()
        val dispatcher = FakeDecisionOverlayMainThreadDispatcher(autoRunPosted = true)
        dispatcher.onMainThread = false
        val controller = controller(host = host, factory = factory, dispatcher = dispatcher)

        assertTrue(controller.showPresentation(presentation()))

        assertEquals(1, dispatcher.postCount)
        assertEquals("Atencao", factory.lastHandle?.title)
    }

    @Test
    fun uiFailureDoesNotCrashPipeline() {
        val host = FakeDecisionOverlayWindowHost(throwOnAdd = true)
        val controller = controller(host = host)

        val shown = controller.showPresentation(presentation())

        assertFalse(shown)
    }

    private fun controller(
        host: FakeDecisionOverlayWindowHost = FakeDecisionOverlayWindowHost(),
        factory: FakeDecisionOverlayViewFactory = FakeDecisionOverlayViewFactory(),
        scheduler: FakeDecisionOverlayScheduler = FakeDecisionOverlayScheduler(),
        dispatcher: FakeDecisionOverlayMainThreadDispatcher = FakeDecisionOverlayMainThreadDispatcher(autoRunPosted = true),
        permission: Boolean = true,
        nowMs: Long = 1_000L
    ) = DecisionOverlayController(
        context = ContextWrapper(null),
        host = host,
        viewFactory = factory,
        clock = RadarClock { nowMs },
        overlayPermissionChecker = { permission },
        screenWidthPxProvider = { 1080 },
        topInsetPxProvider = { 120 },
        delayedActionScheduler = scheduler,
        mainThreadDispatcher = dispatcher
    )

    private fun presentation(
        kind: DecisionPresentationKind = DecisionPresentationKind.SHOW_WARNING,
        title: String = when (kind) {
            DecisionPresentationKind.SHOW_BAD -> "Ruim"
            DecisionPresentationKind.SHOW_GOOD -> "Boa"
            DecisionPresentationKind.SHOW_BLOCKED -> "Leitura incerta"
            DecisionPresentationKind.SHOW_UNKNOWN -> "Nao deu para decidir"
            else -> "Atencao"
        },
        createdAtMs: Long = 1_000L,
        expiresAtMs: Long = 9_000L
    ) = DecisionPresentation(
        observationId = "obs-1",
        clusterId = "cluster-1",
        kind = kind,
        title = title,
        shortReason = "Corrida curta",
        details = listOf("R$ 5,50", "R$ 2,12/km total"),
        primaryMetric = "R$ 2,12/km total",
        secondaryMetric = "2,6 km total",
        priceText = "R$ 5,50",
        platformText = "UBER",
        productText = "UberX",
        semantic = DecisionSemantic.ATTENTION,
        source = DecisionPresentationSource.AUTOMATIC,
        expiresAtMs = expiresAtMs,
        createdAtMs = createdAtMs
    )

    private class FakeDecisionOverlayViewFactory : DecisionOverlayViewFactory {
        var lastHandle: FakeDecisionOverlayViewHandle? = null

        override fun create(): DecisionOverlayViewHandle {
            return FakeDecisionOverlayViewHandle().also { lastHandle = it }
        }
    }

    private class FakeDecisionOverlayViewHandle : DecisionOverlayViewHandle {
        override val platformView: Any = Any()
        var title: String = ""

        override fun render(presentation: DecisionPresentation) {
            title = presentation.title
        }
    }

    private class FakeDecisionOverlayWindowHost(
        private val throwOnAdd: Boolean = false
    ) : DecisionOverlayWindowHost {
        var addCount = 0
        var updateCount = 0
        var removeCount = 0

        override fun add(handle: DecisionOverlayViewHandle, params: WindowManager.LayoutParams) {
            if (throwOnAdd) error("boom")
            addCount += 1
        }

        override fun update(handle: DecisionOverlayViewHandle, params: WindowManager.LayoutParams) {
            updateCount += 1
        }

        override fun remove(handle: DecisionOverlayViewHandle) {
            removeCount += 1
        }
    }

    private class FakeDecisionOverlayScheduler : DecisionOverlayDelayedActionScheduler {
        var lastDelayMs: Long? = null
        private var latestAction: (() -> Unit)? = null

        override fun schedule(delayMs: Long, action: () -> Unit): DecisionOverlayDelayedActionHandle {
            lastDelayMs = delayMs
            latestAction = action
            return DecisionOverlayDelayedActionHandle { latestAction = null }
        }

        fun runLatest() {
            latestAction?.invoke()
            latestAction = null
        }
    }

    private class FakeDecisionOverlayMainThreadDispatcher(
        private val autoRunPosted: Boolean = false
    ) : DecisionOverlayMainThreadDispatcher {
        var onMainThread: Boolean = true
        var postCount: Int = 0

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
            }
        }
    }
}
