package com.lucastrevvos.kmonemotor.radar.orchestrator

import java.text.Normalizer

data class UberOperationalScreenSignal(
    val isOperationalScreen: Boolean,
    val reason: String?,
    val hasEarningsContext: Boolean,
    val hasOpportunitiesContext: Boolean,
    val hasHomeContext: Boolean,
    val hasOfflineContext: Boolean,
    val hasSearchingContext: Boolean,
    val hasOperationalMoneyText: Boolean,
    val hasMapEtaRangeText: Boolean,
    val normalizedTexts: List<String>
)

object UberOperationalScreenClassifier {
    private val etaRangeRegex = Regex("""\b\d+\s*-\s*\d+\s*(min|minuto|minutos)\b""")
    private val plusMoneyRegex = Regex("""\+\s*r\$\s*\d""")
    private val earningsTerms = listOf(
        "agora seus ganhos sao mais altos",
        "ganhos",
        "os ganhos das viagens sao altos",
        "confira as tendencias de ganhos",
        "ver tempo ao volante",
        "registro de viagens"
    )
    private val opportunitiesTerms = listOf(
        "oportunidades",
        "veja as proximas promocoes"
    )
    private val homeTerms = listOf(
        "pagina inicial",
        "mensagens",
        "menu",
        "conectar",
        "status perfeito",
        "indicado para voce"
    )
    private val offlineTerms = listOf(
        "ficar online",
        "ficar offline",
        "voce esta online",
        "voce esta offline",
        "comecar"
    )
    private val searchingTerms = listOf(
        "procurando viagens",
        "procurando corridas",
        "procurando",
        "buscando"
    )

    fun classify(texts: List<String>): UberOperationalScreenSignal {
        val normalizedTexts = texts.map(::normalize).filter { it.isNotBlank() }
        val hasEarningsContext = normalizedTexts.any { text -> earningsTerms.any(text::contains) }
        val hasOpportunitiesContext = normalizedTexts.any { text -> opportunitiesTerms.any(text::contains) }
        val hasHomeContext = normalizedTexts.any { text -> homeTerms.any(text::contains) }
        val hasOfflineContext = normalizedTexts.any { text -> offlineTerms.any(text::contains) }
        val hasSearchingContext = normalizedTexts.any { text -> searchingTerms.any(text::contains) }
        val hasMapEtaRangeText = normalizedTexts.any { etaRangeRegex.containsMatchIn(it) }
        val hasOperationalMoneyText = normalizedTexts.any { plusMoneyRegex.containsMatchIn(it) } &&
            (hasEarningsContext || hasOpportunitiesContext || hasHomeContext || hasOfflineContext || hasSearchingContext || hasMapEtaRangeText)

        val isOperationalScreen = hasOperationalMoneyText ||
            hasSearchingContext ||
            ((hasEarningsContext || hasOpportunitiesContext || hasHomeContext || hasOfflineContext) && hasMapEtaRangeText) ||
            (hasEarningsContext && hasHomeContext)

        val reason = when {
            hasOperationalMoneyText -> "operational_earnings_money_without_offer_evidence"
            hasSearchingContext -> "searching_text_without_price_product_or_route"
            hasOfflineContext -> "offline_operational_screen"
            hasEarningsContext || hasOpportunitiesContext || hasHomeContext -> "operational_home_or_earnings_screen"
            hasMapEtaRangeText -> "map_eta_range_without_offer_evidence"
            else -> null
        }

        return UberOperationalScreenSignal(
            isOperationalScreen = isOperationalScreen,
            reason = reason,
            hasEarningsContext = hasEarningsContext,
            hasOpportunitiesContext = hasOpportunitiesContext,
            hasHomeContext = hasHomeContext,
            hasOfflineContext = hasOfflineContext,
            hasSearchingContext = hasSearchingContext,
            hasOperationalMoneyText = hasOperationalMoneyText,
            hasMapEtaRangeText = hasMapEtaRangeText,
            normalizedTexts = normalizedTexts
        )
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.lowercase()
    }
}
