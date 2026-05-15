package com.lucastrevvos.kmonemotor.radar.signals

import com.lucastrevvos.kmonemotor.radar.android.WindowDescriptor
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarConfig
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import kotlin.math.abs

class FloatingWindowClassifier {
    private var previousFloating: WindowDescriptor? = null
    private val lastLogTimesBySignature = mutableMapOf<String, Long>()
    private val lastThrottleLogTimesBySignature = mutableMapOf<String, Long>()
    private var lastClassifiedSignature: String? = null

    fun classify(floating: WindowDescriptor?, nowMs: Long): FloatingWindowKind {
        if (floating == null) {
            previousFloating = null
            return FloatingWindowKind.UNKNOWN_FLOATING
        }

        if (floating.packageName == SYSTEM_UI_PACKAGE) {
            val kind = FloatingWindowKind.SYSTEM_UI_FLOATING
            logClassificationIfNeeded(floating, kind, nowMs)
            logSpecificIfNeeded(
                floating = floating,
                kind = kind,
                nowMs = nowMs,
                event = "KM_V2_SYSTEM_UI_FLOATING_DETECTED",
                reason = "system_ui_window_ignored"
            )
            previousFloating = floating
            return kind
        }

        val monitoredFloating = floating.packageName == RadarConfig.UBER_DRIVER_PACKAGE ||
            floating.packageName == RadarConfig.NINETY_NINE_DRIVER_PACKAGE ||
            floating.packageName == RadarConfig.NINETY_NINE_DRIVER_LEGACY_PACKAGE
        val isSmallBounds = floating.widthPx <= MAX_BUBBLE_WIDTH_PX && floating.heightPx <= MAX_BUBBLE_HEIGHT_PX
        val abruptCoverageChange = abs(floating.coverage - (previousFloating?.coverage ?: 0.0)) >= MIN_ABRUPT_COVERAGE_DELTA
        val abruptBoundsChange = previousFloating != null &&
            (abs(floating.widthPx - previousFloating!!.widthPx) >= MIN_ABRUPT_SIZE_DELTA_PX ||
                abs(floating.heightPx - previousFloating!!.heightPx) >= MIN_ABRUPT_SIZE_DELTA_PX)
        val boundsRelevant = floating.widthPx >= MIN_PANEL_WIDTH_PX || floating.heightPx >= MIN_PANEL_HEIGHT_PX

        val kind = when {
            monitoredFloating && floating.coverage <= MAX_BUBBLE_COVERAGE && isSmallBounds -> FloatingWindowKind.FLOATING_BUBBLE
            floating.coverage >= MIN_PANEL_COVERAGE || boundsRelevant || abruptCoverageChange || abruptBoundsChange -> FloatingWindowKind.FLOATING_PANEL_CANDIDATE
            else -> FloatingWindowKind.UNKNOWN_FLOATING
        }

        logClassificationIfNeeded(floating, kind, nowMs)

        if (kind == FloatingWindowKind.FLOATING_BUBBLE) {
            logSpecificIfNeeded(
                floating = floating,
                kind = kind,
                nowMs = nowMs,
                event = "KM_V2_FLOATING_BUBBLE_DETECTED",
                reason = "small_persistent_floating_window"
            )
        }
        if (kind == FloatingWindowKind.FLOATING_PANEL_CANDIDATE) {
            logSpecificIfNeeded(
                floating = floating,
                kind = kind,
                nowMs = nowMs,
                event = "KM_V2_FLOATING_PANEL_CANDIDATE"
            )
        }

        previousFloating = floating
        return kind
    }

    private fun logClassificationIfNeeded(
        floating: WindowDescriptor,
        kind: FloatingWindowKind,
        nowMs: Long
    ) {
        val signature = signatureFor(floating, kind)
        if (lastClassifiedSignature == signature &&
            nowMs - (lastLogTimesBySignature[signature] ?: 0L) < FLOATING_LOG_THROTTLE_MS
        ) {
            maybeLogThrottled(signature, floating, kind, nowMs)
            return
        }

        lastClassifiedSignature = signature
        lastLogTimesBySignature[signature] = nowMs
        RadarLogger.d(
            "KM_V2_SIGNAL",
            "KM_V2_FLOATING_CLASSIFIED",
            "package" to floating.packageName,
            "coverage" to floating.coverage,
            "bounds" to floating.bounds,
            "widthPx" to floating.widthPx,
            "heightPx" to floating.heightPx,
            "kind" to kind
        )
    }

    private fun logSpecificIfNeeded(
        floating: WindowDescriptor,
        kind: FloatingWindowKind,
        nowMs: Long,
        event: String,
        reason: String? = null
    ) {
        val signature = "$event|${signatureFor(floating, kind)}"
        val lastLoggedAt = lastLogTimesBySignature[signature]
        if (lastLoggedAt != null && nowMs - lastLoggedAt < FLOATING_LOG_THROTTLE_MS) {
            maybeLogThrottled(signature, floating, kind, nowMs)
            return
        }

        lastLogTimesBySignature[signature] = nowMs
        RadarLogger.d(
            "KM_V2_SIGNAL",
            event,
            "package" to floating.packageName,
            "coverage" to floating.coverage,
            "bounds" to floating.bounds,
            "reason" to reason
        )
    }

    private fun maybeLogThrottled(
        signature: String,
        floating: WindowDescriptor,
        kind: FloatingWindowKind,
        nowMs: Long
    ) {
        val lastThrottleLogAt = lastThrottleLogTimesBySignature[signature]
        if (lastThrottleLogAt != null && nowMs - lastThrottleLogAt < FLOATING_LOG_THROTTLE_MS) {
            return
        }
        lastThrottleLogTimesBySignature[signature] = nowMs
        RadarLogger.d(
            "KM_V2_SIGNAL",
            "KM_V2_FLOATING_LOG_THROTTLED",
            "package" to floating.packageName,
            "bounds" to floating.bounds,
            "kind" to kind,
            "reason" to "same_package_kind_bounds_under_${FLOATING_LOG_THROTTLE_MS}ms"
        )
    }

    private fun signatureFor(floating: WindowDescriptor, kind: FloatingWindowKind): String {
        return "${floating.packageName}|$kind|${floating.bounds}"
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val MAX_BUBBLE_COVERAGE = 0.04
        const val MAX_BUBBLE_WIDTH_PX = 260
        const val MAX_BUBBLE_HEIGHT_PX = 260
        const val MIN_PANEL_COVERAGE = 0.12
        const val MIN_PANEL_WIDTH_PX = 320
        const val MIN_PANEL_HEIGHT_PX = 320
        const val MIN_ABRUPT_COVERAGE_DELTA = 0.06
        const val MIN_ABRUPT_SIZE_DELTA_PX = 120
        const val FLOATING_LOG_THROTTLE_MS = 2000L
    }
}
