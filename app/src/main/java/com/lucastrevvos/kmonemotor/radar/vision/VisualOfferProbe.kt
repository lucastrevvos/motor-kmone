package com.lucastrevvos.kmonemotor.radar.vision

import android.content.Context
import android.graphics.Bitmap
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.observation.ScreenObservation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class VisualOfferProbe(
    private val context: Context?,
    private val cropper: SmartCropper = SmartCropper(),
    private val pixelProbe: PixelProbe = PixelProbe(),
    private val clock: RadarClock = RadarClock.System
) {
    fun run(observation: ScreenObservation, screenshotBitmap: Bitmap): VisualOfferProbeResult {
        val startedAtMs = clock.nowMs()
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_VISION_STARTED",
            "observationId" to observation.id,
            "triggerSource" to observation.triggerSource
        )
        val candidates = cropper.createCandidates(observation)
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_CROP_CANDIDATES_CREATED",
            "observationId" to observation.id,
            "count" to candidates.size
        )
        if (!observation.isManual) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_CAPTURE_CROP_CANDIDATES",
                "observationId" to observation.id,
                "triggerSource" to observation.triggerSource,
                "candidateCropKinds" to candidates.joinToString(",") { it.kind.name }
            )
        }
        candidates.forEach { candidate ->
            RadarLogger.d(
                "KM_V2_VISION",
                "KM_V2_CROP_CANDIDATE",
                "observationId" to observation.id,
                "cropKind" to candidate.kind,
                "rect" to candidate.rect.flattenToString(),
                "reason" to candidate.reason
            )
        }

        val probeResults = candidates.map { candidate ->
            val bonus = cropTriggerBonus(observation.triggerSource, candidate.kind)
            val result = pixelProbe.probe(candidate, screenshotBitmap, bonus)
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_PIXEL_PROBE_RESULT",
                "observationId" to observation.id,
                "cropKind" to result.cropKind,
                "darkPixelRatio" to result.darkPixelRatio,
                "lightPixelRatio" to result.lightPixelRatio,
                "contrastScore" to result.contrastScore,
                "edgeDensityHint" to result.edgeDensityHint,
                "offerLikeScore" to result.offerLikeScore,
                "rejectionReason" to result.rejectionReason
            )
            if (!observation.isManual) {
                RadarLogger.i(
                    "KM_V2_AUTO",
                    "KM_V2_AUTO_CAPTURE_CROP_SCORE",
                    "observationId" to observation.id,
                    "cropKind" to result.cropKind,
                    "visionScore" to result.offerLikeScore,
                    "ocrSignalScore" to result.edgeDensityHint,
                    "fingerprintKind" to "PENDING",
                    "offerLikeScore" to result.offerLikeScore,
                    "nonOfferScore" to 0,
                    "priceCount" to 0,
                    "distanceCount" to 0,
                    "timeCount" to 0
                )
            }
            result
        }
        val rankingDecision = rankCandidates(observation, candidates, probeResults)
        val finishedAtMs = clock.nowMs()
        val result = VisualOfferProbeResult(
            observationId = observation.id,
            startedAtMs = startedAtMs,
            finishedAtMs = finishedAtMs,
            durationMs = finishedAtMs - startedAtMs,
            bestCandidate = rankingDecision.bestCandidate,
            bestProbe = rankingDecision.bestProbe,
            rankedCandidates = rankingDecision.rankedCandidates.map { it.kind },
            allProbeResults = probeResults,
            acceptedForOcrFuture = rankingDecision.acceptedForOcrFuture,
            reason = rankingDecision.reason,
            cropPriorityReason = rankingDecision.cropPriorityReason,
            selectedByRule = rankingDecision.selectedByRule,
            previousBestByRawScore = rankingDecision.previousBestByRawScore,
            visualFallbackApplied = rankingDecision.visualFallbackApplied,
            originalBestRejectionReason = rankingDecision.originalBestRejectionReason
        )

        saveDebugArtifacts(observation, screenshotBitmap, candidates, probeResults, result)

        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_VISUAL_OFFER_PROBE_RESULT",
            "observationId" to observation.id,
            "bestCropKind" to rankingDecision.bestCandidate?.kind,
            "acceptedForOcrFuture" to result.acceptedForOcrFuture,
            "reason" to result.reason
        )
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_LATENCY_VISION",
            "observationId" to observation.id,
            "durationMs" to result.durationMs
        )
        RadarDebugStore.updateVisionSummary(
            durationMs = result.durationMs,
            bestCropKind = rankingDecision.bestCandidate?.kind?.name,
            visualOfferLikeScore = rankingDecision.bestProbe?.offerLikeScore,
            acceptedForOcrFuture = result.acceptedForOcrFuture,
            reason = result.reason
        )
        return result
    }

    internal fun rankCandidates(
        observation: ScreenObservation,
        candidates: List<CropCandidate>,
        probeResults: List<PixelProbeResult>
    ): RankingDecision {
        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_CROP_RANKING_STARTED",
            "observationId" to observation.id,
            "triggerSource" to observation.triggerSource
        )

        val candidateById = candidates.associateBy { it.id }
        val rawBest = probeResults.maxByOrNull { it.offerLikeScore }?.cropKind
        val ranked = probeResults
            .mapNotNull { probe ->
                val candidate = candidateById[probe.cropId] ?: return@mapNotNull null
                toRankedCandidate(observation, candidate, probe)
            }
            .onEach { rankedCandidate ->
                if (rankedCandidate.kind == CropKind.FULL_DEBUG) {
                    RadarLogger.d(
                        "KM_V2_VISION",
                        "KM_V2_CROP_RANKING_REJECTED_FULL_DEBUG",
                        "observationId" to observation.id,
                        "cropKind" to rankedCandidate.kind
                    )
                } else {
                    RadarLogger.d(
                        "KM_V2_VISION",
                        "KM_V2_CROP_RANKING_CANDIDATE",
                        "observationId" to observation.id,
                        "cropKind" to rankedCandidate.kind,
                        "score" to rankedCandidate.probe.offerLikeScore,
                        "priority" to rankedCandidate.priority,
                        "rejectionReason" to rankedCandidate.probe.rejectionReason,
                        "valid" to rankedCandidate.valid
                    )
                }
            }
            .filter { it.kind != CropKind.FULL_DEBUG }
            .sortedWith(
                compareByDescending<RankedCandidate> { it.valid }
                    .thenByDescending { it.probe.offerLikeScore }
                    .thenBy { it.priority }
                    .thenByDescending { visualHintWeight(it.probe.dominantVisualHint) }
                    .thenByDescending { it.candidate.width * it.candidate.height }
            )

        val forced = when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC -> ranked.firstOrNull {
                it.kind == CropKind.CENTER_CARD_AREA && it.valid && it.probe.offerLikeScore >= 5
            } ?: ranked.firstOrNull {
                it.kind == CropKind.LOWER_HALF && it.valid && it.probe.offerLikeScore >= 5
            }
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> preferredCropOrder(observation).asSequence()
                .mapNotNull { preferredKind ->
                    ranked.firstOrNull { it.kind == preferredKind && it.valid && it.probe.offerLikeScore >= 5 }
                }
                .firstOrNull()
            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC -> ranked.firstOrNull {
                it.kind == CropKind.LOWER_HALF && it.valid && it.probe.offerLikeScore >= 5
            } ?: ranked.firstOrNull {
                it.kind == CropKind.LOWER_THIRD && it.valid && it.probe.offerLikeScore >= 5
            }
            else -> null
        }

        val selected = forced ?: ranked.firstOrNull { it.valid }
        val fallbackCandidate = if (selected == null &&
            observation.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC
        ) {
            ranked.firstOrNull { it.kind == CropKind.CENTER_CARD_AREA && it.largeEnough }
        } else {
            null
        }
        if (fallbackCandidate != null) {
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_UBER_DOMINANT_VISUAL_FALLBACK_APPLIED",
                "observationId" to observation.id,
                "bestCropKind" to fallbackCandidate.kind,
                "originalRejectionReason" to fallbackCandidate.probe.rejectionReason
            )
        }
        val finalSelected = fallbackCandidate ?: selected
        val accepted = when {
            fallbackCandidate != null -> true
            finalSelected != null -> finalSelected.probe.offerLikeScore >= 5
            else -> false
        }
        val reason = when {
            fallbackCandidate != null -> "accepted_by_strong_uber_dominant_signal"
            finalSelected == null -> "no_valid_crop_candidate"
            forced?.kind == CropKind.CENTER_CARD_AREA -> "selected_by_trigger_priority_center_card_area"
            forced?.kind == CropKind.LOWER_HALF -> "selected_by_trigger_priority_lower_half"
            else -> "selected_by_score_after_priority"
        }
        val cropPriorityReason = when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> "uber_over_99_priority"
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC -> "uber_dominant_priority"
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> "uber_auto_burst_priority"
            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC -> "ninety_nine_priority"
            else -> "default_priority"
        }
        val selectedByRule = when {
            fallbackCandidate != null -> "uber_dominant_signal_fallback"
            finalSelected == null -> "no_valid_crop_candidate"
            forced != null -> "trigger_priority_override"
            else -> "score_then_priority"
        }

        RadarLogger.i(
            "KM_V2_VISION",
            "KM_V2_CROP_RANKING_SELECTED",
            "observationId" to observation.id,
            "bestCropKind" to finalSelected?.kind,
            "selectedByRule" to selectedByRule,
            "reason" to reason
        )
        if (!observation.isManual) {
            RadarLogger.i(
                "KM_V2_AUTO",
                "KM_V2_AUTO_CAPTURE_SELECTED_CROP",
                "observationId" to observation.id,
                "selectedCropKind" to finalSelected?.kind,
                "reason" to reason
            )
        }

        return RankingDecision(
            bestCandidate = finalSelected?.candidate,
            bestProbe = finalSelected?.probe,
            rankedCandidates = ranked,
            acceptedForOcrFuture = accepted,
            reason = reason,
            cropPriorityReason = cropPriorityReason,
            selectedByRule = selectedByRule,
            previousBestByRawScore = rawBest,
            visualFallbackApplied = fallbackCandidate != null,
            originalBestRejectionReason = fallbackCandidate?.probe?.rejectionReason
        )
    }

    private fun toRankedCandidate(
        observation: ScreenObservation,
        candidate: CropCandidate,
        probe: PixelProbeResult
    ): RankedCandidate {
        val priority = cropPriority(observation, candidate.kind)
        val largeEnough = candidate.width >= 40 && candidate.height >= 40
        val isNinetyNineTrigger = observation.triggerSource == TriggerSource.NINETY_NINE_TREE_STRUCTURE ||
            observation.triggerSource == TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC
        val blockedBySystemUiRule = isNinetyNineTrigger &&
            observation.floatingKind == FloatingWindowKind.SYSTEM_UI_FLOATING &&
            candidate.kind == CropKind.FLOATING_BOUNDS_EXPANDED
        val valid = largeEnough &&
            probe.rejectionReason == null &&
            candidate.kind != CropKind.FULL_DEBUG &&
            !blockedBySystemUiRule
        return RankedCandidate(
            candidate = candidate,
            probe = probe,
            priority = priority,
            valid = valid,
            largeEnough = largeEnough
        )
    }

    private fun cropPriority(observation: ScreenObservation, kind: CropKind): Int {
        val metadataPriority = preferredCropOrder(observation)
        val priority = when {
            metadataPriority.isNotEmpty() -> metadataPriority
            else -> when (observation.triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> listOf(
                CropKind.CENTER_CARD_AREA,
                CropKind.LOWER_HALF,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                CropKind.FLOATING_BOUNDS_EXPANDED,
                CropKind.LOWER_THIRD
            )
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC -> buildList {
                add(CropKind.CENTER_CARD_AREA)
                add(CropKind.LOWER_HALF)
                add(CropKind.PLATFORM_SPECIFIC_CANDIDATE)
                add(CropKind.LOWER_THIRD)
                if (observation.floatingKind != FloatingWindowKind.SYSTEM_UI_FLOATING) {
                    add(CropKind.FLOATING_BOUNDS_EXPANDED)
                }
            }
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                CropKind.FLOATING_BOUNDS_EXPANDED,
                CropKind.LOWER_THIRD
            )
            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC -> buildList {
                add(CropKind.LOWER_HALF)
                add(CropKind.LOWER_THIRD)
                add(CropKind.PLATFORM_SPECIFIC_CANDIDATE)
                add(CropKind.CENTER_CARD_AREA)
                if (observation.floatingKind != FloatingWindowKind.SYSTEM_UI_FLOATING) {
                    add(CropKind.FLOATING_BOUNDS_EXPANDED)
                }
            }
            else -> listOf(
                CropKind.CENTER_CARD_AREA,
                CropKind.LOWER_HALF,
                CropKind.LOWER_THIRD,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                CropKind.FLOATING_BOUNDS_EXPANDED
            )
            }
        }
        return priority.indexOf(kind).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun cropTriggerBonus(triggerSource: TriggerSource, cropKind: CropKind): Int {
        var score = 0
        if (triggerSource == TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC && cropKind == CropKind.FLOATING_BOUNDS_EXPANDED) {
            score += 2
        }
        if (triggerSource == TriggerSource.UBER_AUTO_BURST_RECOVERY && cropKind == CropKind.LOWER_HALF) {
            score += 2
        }
        if ((triggerSource == TriggerSource.NINETY_NINE_TREE_STRUCTURE ||
                triggerSource == TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC) &&
            cropKind == CropKind.LOWER_HALF
        ) {
            score += 2
        }
        return score
    }

    private fun preferredCropOrder(observation: ScreenObservation): List<CropKind> {
        val raw = observation.metadata.notes["autoBurstPreferredCropOrder"] ?: return emptyList()
        return raw.split(",")
            .mapNotNull { token -> runCatching { CropKind.valueOf(token.trim()) }.getOrNull() }
    }

    private fun visualHintWeight(hint: VisualPlatformHint): Int {
        return when (hint) {
            VisualPlatformHint.UBER_LIGHT_OR_DARK_CARD -> 3
            VisualPlatformHint.NINETY_NINE_DARK_CARD -> 2
            VisualPlatformHint.UNKNOWN -> 1
            VisualPlatformHint.MAP_OR_HOME_SCREEN -> 0
            VisualPlatformHint.SYSTEM_UI_OR_EMPTY -> -1
        }
    }

    private fun saveDebugArtifacts(
        observation: ScreenObservation,
        screenshotBitmap: Bitmap,
        candidates: List<CropCandidate>,
        probeResults: List<PixelProbeResult>,
        result: VisualOfferProbeResult
    ) {
        if (!RadarFeatureFlags.ENABLE_DEBUG_CROP_SAVE || context == null) {
            return
        }
        val baseDir = context.getExternalFilesDir("debug_visual_probe") ?: return
        baseDir.mkdirs()
        val prefix = "${result.startedAtMs}_${observation.id}"

        candidates
            .filter { it.kind != CropKind.FULL_DEBUG }
            .forEach { candidate ->
                val file = File(baseDir, "${prefix}_${candidate.kind}.png")
                try {
                    val cropped = Bitmap.createBitmap(
                        screenshotBitmap,
                        candidate.rect.left,
                        candidate.rect.top,
                        candidate.rect.width().coerceAtLeast(1),
                        candidate.rect.height().coerceAtLeast(1)
                    )
                    FileOutputStream(file).use { stream ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    cropped.recycle()
                    RadarLogger.i(
                        "KM_V2_VISION",
                        "KM_V2_DEBUG_CROP_SAVED",
                        "observationId" to observation.id,
                        "cropKind" to candidate.kind,
                        "path" to file.absolutePath
                    )
                } catch (throwable: Throwable) {
                    RadarLogger.w(
                        "KM_V2_VISION",
                        "KM_V2_DEBUG_CROP_SAVE_FAILED",
                        "observationId" to observation.id,
                        "cropKind" to candidate.kind,
                        "error" to throwable.message
                    )
                }
            }

        try {
            val metaFile = File(baseDir, "${prefix}_visual_probe_meta.json")
            metaFile.writeText(
                JSONObject().apply {
                    put("observationId", observation.id)
                    put("captureRequestId", observation.captureRequestId)
                    put("triggerSource", observation.triggerSource.name)
                    put("dominantPackage", observation.dominantPackage)
                    put("floatingPackage", observation.floatingPackage)
                    put("floatingBounds", observation.floatingBounds)
                    put("floatingKind", observation.floatingKind.name)
                    put("screenshotWidth", observation.screenshotWidth)
                    put("screenshotHeight", observation.screenshotHeight)
                    put("cropCandidates", JSONArray(candidates.map { candidate ->
                        JSONObject().apply {
                            put("id", candidate.id)
                            put("kind", candidate.kind.name)
                            put("rect", candidate.rect.flattenToString())
                            put("reason", candidate.reason)
                        }
                    }))
                    put("probeResults", JSONArray(probeResults.map { probe ->
                        JSONObject().apply {
                            put("cropId", probe.cropId)
                            put("cropKind", probe.cropKind.name)
                            put("darkPixelRatio", probe.darkPixelRatio)
                            put("lightPixelRatio", probe.lightPixelRatio)
                            put("contrastScore", probe.contrastScore)
                            put("edgeDensityHint", probe.edgeDensityHint)
                            put("dominantVisualHint", probe.dominantVisualHint.name)
                            put("offerLikeScore", probe.offerLikeScore)
                            put("rejectionReason", probe.rejectionReason)
                        }
                    }))
                    put("rankedCandidates", JSONArray(result.rankedCandidates.map { it.name }))
                    put("cropPriorityReason", result.cropPriorityReason)
                    put("selectedByRule", result.selectedByRule)
                    put("previousBestByRawScore", result.previousBestByRawScore?.name)
                    put("visualFallbackApplied", result.visualFallbackApplied)
                    put("originalBestRejectionReason", result.originalBestRejectionReason)
                    put("bestCandidate", result.bestCandidate?.kind?.name)
                    put("durationMs", result.durationMs)
                    put("acceptedForOcrFuture", result.acceptedForOcrFuture)
                    put("reason", result.reason)
                    put("analysisEpoch", observation.analysisEpoch)
                    put("manual", observation.isManual)
                    put("manualReason", observation.manualReason)
                    observation.offerCycleClassification?.let { classification ->
                        put("offerCycle", JSONObject().apply {
                            put("kind", classification.kind.name)
                            put("cycleId", classification.cycleId)
                            put("previousCycleId", classification.previousCycleId)
                            put("reason", classification.reason)
                            put("timeSincePreviousMs", classification.timeSincePreviousMs)
                            put("shouldPreferForOcr", classification.shouldPreferForOcr)
                        })
                    }
                }.toString(2)
            )
            RadarLogger.i(
                "KM_V2_VISION",
                "KM_V2_DEBUG_VISUAL_META_SAVED",
                "observationId" to observation.id,
                "path" to metaFile.absolutePath
            )
        } catch (throwable: Throwable) {
            RadarLogger.w(
                "KM_V2_VISION",
                "KM_V2_DEBUG_CROP_SAVE_FAILED",
                "observationId" to observation.id,
                "cropKind" to "META_JSON",
                "error" to throwable.message
            )
        }
    }

    internal data class RankedCandidate(
        val candidate: CropCandidate,
        val probe: PixelProbeResult,
        val priority: Int,
        val valid: Boolean,
        val largeEnough: Boolean
    ) {
        val kind: CropKind get() = candidate.kind
    }

    internal data class RankingDecision(
        val bestCandidate: CropCandidate?,
        val bestProbe: PixelProbeResult?,
        val rankedCandidates: List<RankedCandidate>,
        val acceptedForOcrFuture: Boolean,
        val reason: String,
        val cropPriorityReason: String,
        val selectedByRule: String,
        val previousBestByRawScore: CropKind?,
        val visualFallbackApplied: Boolean,
        val originalBestRejectionReason: String?
    )
}
