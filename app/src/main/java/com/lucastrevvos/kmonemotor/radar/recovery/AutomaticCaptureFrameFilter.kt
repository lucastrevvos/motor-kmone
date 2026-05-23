package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind

object AutomaticCaptureFrameFilter {
    private val selfOverlayPatterns = listOf(
        "KM ONE" to Regex("""km\s*one""", RegexOption.IGNORE_CASE),
        "R$/km total" to Regex("""(?:r\$\s*[\d.,]+\s*)?/km\s*total""", RegexOption.IGNORE_CASE),
        "total do dia" to Regex("""total\s+do\s+dia""", RegexOption.IGNORE_CASE),
        "Busca" to Regex("""busca:""", RegexOption.IGNORE_CASE),
        "Corrida" to Regex("""corrida:""", RegexOption.IGNORE_CASE),
        "deslocamento maior que a corrida" to Regex("""deslocamento maior que a corrida""", RegexOption.IGNORE_CASE),
        "Abaixo de R$" to Regex("""abaixo de r\$""", RegexOption.IGNORE_CASE),
        "Acima de R$" to Regex("""acima de r\$""", RegexOption.IGNORE_CASE),
        "Salvar" to Regex("""\bsalvar\b""", RegexOption.IGNORE_CASE),
        "Ignorar" to Regex("""\bignorar\b""", RegexOption.IGNORE_CASE)
    )

    fun isSelfOverlayContaminated(rawText: String): Boolean {
        val matches = matchedSelfOverlayTerms(rawText)
        return matches.contains("KM ONE") ||
            (matches.contains("R$/km total") && (matches.contains("Busca") || matches.contains("Corrida"))) ||
            matches.size >= 3
    }

    fun matchedSelfOverlayTerms(rawText: String): List<String> {
        return selfOverlayPatterns.mapNotNull { (label, regex) ->
            label.takeIf { regex.containsMatchIn(rawText) }
        }
    }

    fun canWinMicroBurst(
        fingerprintKind: OfferTextFingerprintKind?,
        rawText: String
    ): Boolean {
        return fingerprintKind == OfferTextFingerprintKind.OFFER_LIKE &&
            !isSelfOverlayContaminated(rawText)
    }
}
