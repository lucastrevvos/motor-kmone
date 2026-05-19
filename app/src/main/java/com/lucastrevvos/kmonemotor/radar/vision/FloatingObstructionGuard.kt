package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import kotlin.math.roundToInt

class FloatingObstructionGuard(
    private val enabled: Boolean = RadarFeatureFlags.ENABLE_FLOATING_OBSTRUCTION_GUARD
) {
    fun evaluate(
        observation: ScreenObservation,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>
    ): FloatingObstructionResult {
        if (!enabled) {
            return FloatingObstructionResult(
                detected = false,
                obstructionRect = null,
                overlapsCriticalOfferArea = false,
                confidence = 0,
                reason = "guard_disabled",
                suggestedAction = FloatingObstructionAction.NONE
            )
        }

        val selectedCandidate = visualResult.bestCandidate
        val obstructionRect = parseBounds(observation.floatingBounds)
            ?: estimateObstructionRect(observation)
        val criticalRect = criticalOfferRect(observation, selectedCandidate, candidates)
        val detected = obstructionRect != null ||
            (selectedCandidate?.kind == CropKind.FLOATING_BOUNDS_EXPANDED &&
                observation.floatingKind != FloatingWindowKind.UNKNOWN_FLOATING)
        val overlapsCriticalArea = obstructionRect?.let { intersects(it, criticalRect) }
            ?: (selectedCandidate?.kind == CropKind.FLOATING_BOUNDS_EXPANDED &&
                observation.floatingKind != FloatingWindowKind.UNKNOWN_FLOATING)
        val confidence = when {
            obstructionRect != null && overlapsCriticalArea -> 90
            obstructionRect != null -> 70
            detected && overlapsCriticalArea -> 55
            detected -> 35
            else -> 0
        }
        val suggestedAction = when {
            !detected -> FloatingObstructionAction.NONE
            overlapsCriticalArea && selectedCandidate?.kind == CropKind.FLOATING_BOUNDS_EXPANDED ->
                FloatingObstructionAction.TRY_ALTERNATIVE_CROP
            overlapsCriticalArea -> FloatingObstructionAction.MARK_SUSPECT
            else -> FloatingObstructionAction.NONE
        }
        val reason = when {
            !detected -> "no_floating_obstruction_signal"
            obstructionRect != null && overlapsCriticalArea -> "floating_bounds_overlap_critical_area"
            obstructionRect != null -> "floating_bounds_non_critical"
            selectedCandidate?.kind == CropKind.FLOATING_BOUNDS_EXPANDED -> "floating_bounds_expanded_selected"
            else -> "floating_obstruction_heuristic"
        }
        return FloatingObstructionResult(
            detected = detected,
            obstructionRect = obstructionRect,
            overlapsCriticalOfferArea = overlapsCriticalArea,
            confidence = confidence,
            reason = reason,
            suggestedAction = suggestedAction
        )
    }

    private fun criticalOfferRect(
        observation: ScreenObservation,
        selectedCandidate: CropCandidate?,
        candidates: List<CropCandidate>
    ): Rect {
        val baseRect = selectedCandidate?.rect
            ?: candidates.firstOrNull { it.kind == CropKind.CENTER_CARD_AREA }?.rect
            ?: Rect(
                0,
                (observation.screenshotHeight * DEFAULT_CRITICAL_TOP_RATIO).roundToInt(),
                observation.screenshotWidth,
                (observation.screenshotHeight * DEFAULT_CRITICAL_BOTTOM_RATIO).roundToInt()
            )
        val baseHeight = rectHeight(baseRect)
        val top = baseRect.top + (baseHeight * CRITICAL_TOP_INSET_RATIO).roundToInt()
        val bottom = baseRect.top + (baseHeight * CRITICAL_BOTTOM_RATIO).roundToInt()
        return Rect(
            baseRect.left,
            top.coerceAtLeast(baseRect.top),
            baseRect.right,
            bottom.coerceAtMost(baseRect.bottom)
        )
    }

    private fun intersects(first: Rect, second: Rect): Boolean {
        return first.left < second.right &&
            second.left < first.right &&
            first.top < second.bottom &&
            second.top < first.bottom
    }

    private fun rectHeight(rect: Rect): Int = rect.bottom - rect.top

    private fun parseBounds(bounds: String?): Rect? {
        val parts = bounds?.trim()?.split(" ")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.size != 4) return null
        return Rect(parts[0], parts[1], parts[2], parts[3])
    }

    private fun estimateObstructionRect(observation: ScreenObservation): Rect? {
        val hasMonitoredFloating = observation.floatingKind == FloatingWindowKind.FLOATING_BUBBLE ||
            observation.floatingKind == FloatingWindowKind.FLOATING_PANEL_CANDIDATE
        if (!hasMonitoredFloating) return null
        val width = observation.screenshotWidth
        val height = observation.screenshotHeight
        if (width <= 0 || height <= 0) return null
        return Rect(
            (width * ESTIMATED_BUBBLE_LEFT_RATIO).roundToInt(),
            (height * ESTIMATED_BUBBLE_TOP_RATIO).roundToInt(),
            (width * ESTIMATED_BUBBLE_RIGHT_RATIO).roundToInt(),
            (height * ESTIMATED_BUBBLE_BOTTOM_RATIO).roundToInt()
        )
    }

    private companion object {
        const val DEFAULT_CRITICAL_TOP_RATIO = 0.20
        const val DEFAULT_CRITICAL_BOTTOM_RATIO = 0.88
        const val CRITICAL_TOP_INSET_RATIO = 0.10
        const val CRITICAL_BOTTOM_RATIO = 0.88
        const val ESTIMATED_BUBBLE_LEFT_RATIO = 0.78
        const val ESTIMATED_BUBBLE_RIGHT_RATIO = 0.96
        const val ESTIMATED_BUBBLE_TOP_RATIO = 0.34
        const val ESTIMATED_BUBBLE_BOTTOM_RATIO = 0.54
    }
}

data class FloatingObstructionResult(
    val detected: Boolean,
    val obstructionRect: Rect?,
    val overlapsCriticalOfferArea: Boolean,
    val confidence: Int,
    val reason: String,
    val suggestedAction: FloatingObstructionAction
)

enum class FloatingObstructionAction {
    NONE,
    MARK_SUSPECT,
    RETRY_AFTER_SHORT_DELAY,
    TRY_ALTERNATIVE_CROP
}
