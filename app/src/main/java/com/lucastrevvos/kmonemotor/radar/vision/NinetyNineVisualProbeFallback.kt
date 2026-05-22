package com.lucastrevvos.kmonemotor.radar.vision

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

data class NinetyNineVisualProbeFallbackDecision(
    val visualResult: VisualOfferProbeResult,
    val applied: Boolean,
    val fallbackCropKind: CropKind?
)

object NinetyNineVisualProbeFallback {
    private val fallbackOrder = listOf(
        CropKind.CENTER_CARD_AREA,
        CropKind.LOWER_HALF,
        CropKind.FULL_DEBUG
    )

    fun applyIfNeeded(
        triggerSource: TriggerSource,
        visualResult: VisualOfferProbeResult,
        candidates: List<CropCandidate>
    ): NinetyNineVisualProbeFallbackDecision {
        if (triggerSource != TriggerSource.NINETY_NINE_VISUAL_PROBE ||
            visualResult.bestCandidate != null ||
            visualResult.reason != "no_valid_crop_candidate"
        ) {
            return NinetyNineVisualProbeFallbackDecision(
                visualResult = visualResult,
                applied = false,
                fallbackCropKind = null
            )
        }

        val selectedCandidate = fallbackOrder.asSequence()
            .mapNotNull { kind ->
                candidates.firstOrNull { candidate ->
                    candidate.kind == kind && candidate.width >= 40 && candidate.height >= 40
                }
            }
            .firstOrNull()
            ?: return NinetyNineVisualProbeFallbackDecision(
                visualResult = visualResult,
                applied = false,
                fallbackCropKind = null
            )

        val selectedProbe = visualResult.allProbeResults.firstOrNull { it.cropId == selectedCandidate.id }
        val updatedResult = visualResult.copy(
            bestCandidate = selectedCandidate,
            bestProbe = selectedProbe,
            acceptedForOcrFuture = true,
            reason = when (selectedCandidate.kind) {
                CropKind.CENTER_CARD_AREA -> "ninety_nine_visual_probe_fallback_center_card"
                CropKind.LOWER_HALF -> "ninety_nine_visual_probe_fallback_lower_half"
                CropKind.FULL_DEBUG -> "ninety_nine_visual_probe_fallback_full_debug"
                else -> "ninety_nine_visual_probe_fallback"
            },
            selectedByRule = "ninety_nine_visual_probe_no_valid_crop_fallback",
            visualFallbackApplied = true,
            originalBestRejectionReason = visualResult.reason
        )
        return NinetyNineVisualProbeFallbackDecision(
            visualResult = updatedResult,
            applied = true,
            fallbackCropKind = selectedCandidate.kind
        )
    }
}
