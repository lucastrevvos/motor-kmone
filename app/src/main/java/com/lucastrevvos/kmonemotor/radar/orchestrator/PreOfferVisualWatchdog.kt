package com.lucastrevvos.kmonemotor.radar.orchestrator

data class PreOfferVisualWatchdogPlan(
    val shouldStart: Boolean,
    val reason: String?,
    val delaysMs: List<Long>
)

object PreOfferVisualWatchdog {
    private val DEFAULT_DELAYS_MS = listOf(1_200L, 3_000L, 6_000L)
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
        isOperationalScreen: Boolean
    ): PreOfferVisualWatchdogPlan {
        val hasHardBlacklist = hasHardBlacklist(knownStateTexts)
        val allowsOperationalPreOffer = rejectionReason == "map_eta_range_without_offer_evidence"
        val shouldStart = (
            rejectionReason == "map_eta_range_without_offer_evidence" ||
                rejectionReason == "searching_disappeared_empty_tree_probe_candidate"
            ) &&
            matchedConditions.any { it == "searching_text_disappeared" || it == "tree_delta_threshold" } &&
            (!isOperationalScreen || allowsOperationalPreOffer) &&
            !hasHardBlacklist
        val reason = when {
            !shouldStart -> null
            rejectionReason == "searching_disappeared_empty_tree_probe_candidate" ->
                "searching_disappeared_empty_tree_probe_candidate"
            rejectionReason == "map_eta_range_without_offer_evidence" ->
                "map_eta_range_without_offer_evidence"
            matchedConditions.contains("searching_text_disappeared") -> "map_eta_range_after_searching_disappeared"
            else -> "map_eta_range_after_tree_delta"
        }
        return PreOfferVisualWatchdogPlan(
            shouldStart = shouldStart,
            reason = reason,
            delaysMs = DEFAULT_DELAYS_MS
        )
    }
}
