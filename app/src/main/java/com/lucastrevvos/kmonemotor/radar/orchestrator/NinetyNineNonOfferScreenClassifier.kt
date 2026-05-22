package com.lucastrevvos.kmonemotor.radar.orchestrator

data class NinetyNineNonOfferScreenSignal(
    val isNonOfferMapScreen: Boolean,
    val reason: String?,
    val hasSearchingText: Boolean,
    val hasMultiplierText: Boolean,
    val hasOperationalText: Boolean,
    val hasOfferPrice: Boolean,
    val hasValuePerKm: Boolean,
    val hasStrong99OfferSignals: Boolean
)

object NinetyNineNonOfferScreenClassifier {
    private val MULTIPLIER_REGEX = Regex("""\b\d+[.,]?\d*\s*x\s*-\s*\d+[.,]?\d*\s*x\b""", RegexOption.IGNORE_CASE)
    private val MONEY_REGEX = Regex("""(?<!\+)\br\$\s*\d""", RegexOption.IGNORE_CASE)
    private val OPERATIONAL_TERMS = listOf(
        "desconectar",
        "buscando",
        "conectar",
        "online",
        "offline",
        "menu",
        "configuracoes",
        "configurações"
    )

    fun fromTexts(texts: List<String>): NinetyNineNonOfferScreenSignal {
        return fromRawText(texts.joinToString(" "))
    }

    fun fromRawText(rawText: String): NinetyNineNonOfferScreenSignal {
        val normalized = rawText.lowercase()
        val hasSearchingText = normalized.contains("buscando")
        val hasMultiplierText = MULTIPLIER_REGEX.containsMatchIn(normalized) ||
            normalized.contains("heatmap") ||
            normalized.contains("multiplicador") ||
            normalized.contains("surge")
        val hasOperationalText = OPERATIONAL_TERMS.any { normalized.contains(it) }
        val hasOfferPrice = MONEY_REGEX.containsMatchIn(normalized)
        val hasValuePerKm = normalized.contains("r$/km")
        val hasStrong99OfferSignals = normalized.contains("cpf") ||
            normalized.contains("cartao verif") ||
            normalized.contains("cartão verif") ||
            normalized.contains("dinheiro") ||
            normalized.contains("pagamento no app") ||
            normalized.contains("taxa de deslocamento") ||
            normalized.contains("corrida longa") ||
            normalized.contains("passageiro novo")
        val isNonOfferMapScreen = (hasSearchingText || hasMultiplierText) &&
            !hasOfferPrice &&
            !hasValuePerKm &&
            !hasStrong99OfferSignals
        return NinetyNineNonOfferScreenSignal(
            isNonOfferMapScreen = isNonOfferMapScreen,
            reason = if (isNonOfferMapScreen) "ninety_nine_map_searching_state" else null,
            hasSearchingText = hasSearchingText,
            hasMultiplierText = hasMultiplierText,
            hasOperationalText = hasOperationalText,
            hasOfferPrice = hasOfferPrice,
            hasValuePerKm = hasValuePerKm,
            hasStrong99OfferSignals = hasStrong99OfferSignals
        )
    }
}
