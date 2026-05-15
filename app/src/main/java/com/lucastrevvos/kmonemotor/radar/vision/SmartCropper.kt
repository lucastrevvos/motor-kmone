package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import java.util.UUID
import kotlin.math.roundToInt

class SmartCropper {
    fun createCandidates(observation: ScreenObservation): List<CropCandidate> {
        val width = observation.screenshotWidth
        val height = observation.screenshotHeight
        if (width <= 0 || height <= 0) return emptyList()

        val candidates = mutableListOf<CropCandidate>()
        candidates += candidate(
            observation = observation,
            kind = CropKind.FULL_DEBUG,
            rect = Rect(0, 0, width, height),
            reason = "full_reference_debug"
        )
        candidates += candidate(
            observation = observation,
            kind = CropKind.LOWER_HALF,
            rect = Rect(0, (height * 0.45).roundToInt(), width, height),
            reason = "lower_half_default"
        )
        candidates += candidate(
            observation = observation,
            kind = CropKind.LOWER_THIRD,
            rect = Rect(0, (height * 0.60).roundToInt(), width, height),
            reason = "lower_third_default"
        )
        candidates += candidate(
            observation = observation,
            kind = CropKind.CENTER_CARD_AREA,
            rect = Rect(
                (width * 0.05).roundToInt(),
                (height * 0.25).roundToInt(),
                (width * 0.95).roundToInt(),
                (height * 0.85).roundToInt()
            ),
            reason = "center_card_area_default"
        )

        parseBounds(observation.floatingBounds)?.let { bounds ->
            val expanded = Rect(
                0,
                maxOf(0, bounds.top - (height * 0.15).roundToInt()),
                width,
                minOf(height, bounds.bottom + (height * 0.55).roundToInt())
            )
            candidates += candidate(
                observation = observation,
                kind = CropKind.FLOATING_BOUNDS_EXPANDED,
                rect = expanded,
                reason = "expanded_from_floating_bounds"
            )
        }

        val platformSpecificRect = when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> Rect(
                0,
                (height * 0.20).roundToInt(),
                width,
                minOf(height, (height * 0.88).roundToInt())
            )
            TriggerSource.NINETY_NINE_TREE_STRUCTURE -> Rect(
                0,
                (height * 0.40).roundToInt(),
                width,
                height
            )
            else -> null
        }
        platformSpecificRect?.let {
            candidates += candidate(
                observation = observation,
                kind = CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                rect = it,
                reason = "platform_specific_trigger_candidate"
            )
        }
        return candidates
    }

    private fun candidate(
        observation: ScreenObservation,
        kind: CropKind,
        rect: Rect,
        reason: String
    ): CropCandidate {
        val normalized = Rect(
            maxOf(0, rect.left),
            maxOf(0, rect.top),
            maxOf(rect.left + 1, rect.right),
            maxOf(rect.top + 1, rect.bottom)
        )
        return CropCandidate(
            id = UUID.randomUUID().toString(),
            observationId = observation.id,
            kind = kind,
            rect = normalized,
            width = normalized.width(),
            height = normalized.height(),
            reason = reason
        )
    }

    private fun parseBounds(bounds: String?): Rect? {
        val parts = bounds?.trim()?.split(" ")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (parts.size != 4) return null
        return Rect(parts[0], parts[1], parts[2], parts[3])
    }
}
