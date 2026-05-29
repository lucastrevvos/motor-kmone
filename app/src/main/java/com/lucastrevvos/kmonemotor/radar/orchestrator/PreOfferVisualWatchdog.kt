package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.UberReadableState

data class PreOfferVisualWatchdogPlan(
    val shouldStart: Boolean,
    val reason: String?,
    val delaysMs: List<Long>
)

object PreOfferVisualWatchdog {
    private val DEFAULT_DELAYS_MS = listOf(1_200L, 3_000L, 6_000L)
    private val STALE_OPERATIONAL_DELAYS_MS = listOf(1_500L, 3_500L, 6_500L)
    private val HARD_BLACKLIST = listOf(
        "ganhos",
        "oportunidades",
        "página inicial",
        "pagina inicial",
        "mensagens",
        "menu",
        "ficar online",
        "ficar offline",
        "r$ 0,00",
        "confira as tendências de ganhos",
        "confira as tendencias de ganhos"
    )
    private val STALE_OPERATIONAL_HARD_BLACKLIST = listOf(
        "página inicial",
        "pagina inicial",
        "mensagens",
        "menu",
        "ficar online",
        "você está offline",
        "voce esta offline",
        "ganhos das viagens"
    )
    private val STALE_OPERATIONAL_CONDITIONAL_BLACKLIST = listOf(
        "confira as tendências de ganhos",
        "confira as tendencias de ganhos"
    )
    private val ETA_RANGE_REGEX = Regex("""\b\d+\s*-\s*\d+\s*(min|minutos?)\b""", RegexOption.IGNORE_CASE)

    fun hasHardBlacklist(knownStateTexts: List<String>): Boolean {
        val normalizedTexts = knownStateTexts.map { it.lowercase() }
        return HARD_BLACKLIST.any { blocked ->
            normalizedTexts.any { it.contains(blocked) }
        }
    }

    fun planStart(
        rejectionReason: String?,
        matchedConditions: List<String>,
        knownStateTexts: List<String>,
        isOperationalScreen: Boolean,
        visibleTextDelta: Int = 0,
        mapSearchingTreeSignal: Boolean = false,
        currentState: UberReadableState? = null,
        autoState: RadarAutoCaptureState? = null
    ): PreOfferVisualWatchdogPlan {
        val normalizedTexts = knownStateTexts.map { it.lowercase() }
        val hasHardBlacklist = hasHardBlacklist(knownStateTexts)
        val isStaleOperationalCandidate =
            rejectionReason == "stale_operational_earnings_probe_candidate"

        val shouldStart = when {
            isStaleOperationalCandidate ->
                shouldStartStaleOperationalProbe(
                    matchedConditions = matchedConditions,
                    normalizedTexts = normalizedTexts,
                    visibleTextDelta = visibleTextDelta,
                    mapSearchingTreeSignal = mapSearchingTreeSignal,
                    currentState = currentState,
                    autoState = autoState
                )
            rejectionReason == "weak_tree_delta_visual_probe_candidate" ->
                matchedConditions.contains("tree_delta_threshold") &&
                    matchedConditions.contains("button_like_with_visible_text") &&
                    visibleTextDelta >= 6 &&
                    !isOperationalScreen &&
                    !hasHardBlacklist
            else -> {
                val allowsOperationalPreOffer =
                    rejectionReason == "map_eta_range_without_offer_evidence"
                (
                    rejectionReason == "map_eta_range_without_offer_evidence" ||
                        rejectionReason == "searching_disappeared_empty_tree_probe_candidate"
                    ) &&
                    matchedConditions.any {
                        it == "searching_text_disappeared" || it == "tree_delta_threshold"
                    } &&
                    (!isOperationalScreen || allowsOperationalPreOffer) &&
                    !hasHardBlacklist
            }
        }

        val reason = when {
            !shouldStart -> null
            isStaleOperationalCandidate -> "stale_operational_earnings_probe_candidate"
            rejectionReason == "weak_tree_delta_visual_probe_candidate" ->
                "weak_tree_delta_visual_probe_candidate"
            rejectionReason == "searching_disappeared_empty_tree_probe_candidate" ->
                "searching_disappeared_empty_tree_probe_candidate"
            rejectionReason == "map_eta_range_without_offer_evidence" ->
                "map_eta_range_without_offer_evidence"
            matchedConditions.contains("searching_text_disappeared") ->
                "map_eta_range_after_searching_disappeared"
            else -> "map_eta_range_after_tree_delta"
        }

        return PreOfferVisualWatchdogPlan(
            shouldStart = shouldStart,
            reason = reason,
            delaysMs = if (isStaleOperationalCandidate) {
                STALE_OPERATIONAL_DELAYS_MS
            } else {
                DEFAULT_DELAYS_MS
            }
        )
    }

    private fun shouldStartStaleOperationalProbe(
        matchedConditions: List<String>,
        normalizedTexts: List<String>,
        visibleTextDelta: Int,
        mapSearchingTreeSignal: Boolean,
        currentState: UberReadableState?,
        autoState: RadarAutoCaptureState?
    ): Boolean {
        val hasEtaRange = normalizedTexts.any { ETA_RANGE_REGEX.containsMatchIn(it) }
        if (!hasEtaRange) {
            return false
        }
        if (hasStaleOperationalHardBlacklist(normalizedTexts, hasEtaRange)) {
            return false
        }

        val hasSearchingDisappeared = matchedConditions.contains("searching_text_disappeared")
        val hasStrongVisibleTextDelta =
            matchedConditions.contains("tree_delta_threshold") && visibleTextDelta >= 6
        val hasMapSearchingTransition = mapSearchingTreeSignal &&
            (
                currentState == UberReadableState.UNKNOWN ||
                    currentState == UberReadableState.SEARCHING_RIDES ||
                    autoState == RadarAutoCaptureState.PRE_OFFER_MAP_STATE
                )

        return hasSearchingDisappeared || hasStrongVisibleTextDelta || hasMapSearchingTransition
    }

    private fun hasStaleOperationalHardBlacklist(
        normalizedTexts: List<String>,
        hasEtaRange: Boolean
    ): Boolean {
        if (normalizedTexts.any { text ->
                STALE_OPERATIONAL_HARD_BLACKLIST.any { blocked -> text.contains(blocked) }
            }
        ) {
            return true
        }
        return !hasEtaRange && normalizedTexts.any { text ->
            STALE_OPERATIONAL_CONDITIONAL_BLACKLIST.any { blocked -> text.contains(blocked) }
        }
    }
}
