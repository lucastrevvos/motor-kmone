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

    fun planStart(
        rejectionReason: String?,
        matchedConditions: List<String>,
        knownStateTexts: List<String>,
        isOperationalScreen: Boolean
    ): PreOfferVisualWatchdogPlan {
        val normalizedTexts = knownStateTexts.map { it.lowercase() }
        val hasHardBlacklist = HARD_BLACKLIST.any { blocked ->
            normalizedTexts.any { it.contains(blocked) }
        }
        val shouldStart = rejectionReason == "map_eta_range_without_offer_evidence" &&
            matchedConditions.any { it == "searching_text_disappeared" || it == "tree_delta_threshold" } &&
            !isOperationalScreen &&
            !hasHardBlacklist
        val reason = when {
            !shouldStart -> null
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
