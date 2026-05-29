package com.lucastrevvos.kmonemotor.radar.parser

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource

data class ManualCropHomeContextResult(
    val detected: Boolean,
    val matchedTerms: List<String>,
    val hasOfferSignals: Boolean
)

object ManualCropHomeContextDetector {
    private val HOME_TERMS = listOf(
        "km one",
        "meta diaria",
        "meta diária",
        "faltam r$",
        "sem base de ontem",
        "total do dia",
        "ocultar radar",
        "abrir radar",
        "tracking ao vivo",
        "r$ 0,00",
        "para bater a meta"
    )
    private val OFFER_TERMS = listOf(
        "uberx",
        "uber black",
        "comfort",
        "flash",
        "priority",
        "prioritario",
        "99",
        "corrida"
    )
    private val ROUTE_OR_PRICE_REGEX = Regex("""(r\$ ?\d+[,.]\d{1,2})|(\d+\s*min)|(\d+[,.]?\d*\s*km)""")

    fun inspect(
        rawText: String,
        normalizedText: String,
        triggerSource: TriggerSource
    ): ManualCropHomeContextResult {
        if (triggerSource != TriggerSource.MANUAL_SCREEN_ANALYSIS) {
            return ManualCropHomeContextResult(false, emptyList(), false)
        }
        val text = normalizedText.ifBlank { rawText.lowercase() }.lowercase()
        val matchedTerms = HOME_TERMS.filter { text.contains(it) }.distinct()
        val hasOfferSignals = OFFER_TERMS.any { text.contains(it) } && ROUTE_OR_PRICE_REGEX.containsMatchIn(text)
        return ManualCropHomeContextResult(
            detected = matchedTerms.isNotEmpty() && hasOfferSignals,
            matchedTerms = matchedTerms,
            hasOfferSignals = hasOfferSignals
        )
    }

    fun isHomeGoalPrice(rawText: String, candidateRaw: String, value: Double): Boolean {
        val text = rawText.lowercase()
        val candidateIndex = findCandidateIndex(text, candidateRaw.lowercase(), value)
        if (candidateIndex < 0) {
            return false
        }
        val windowStart = (candidateIndex - 40).coerceAtLeast(0)
        val windowEnd = (candidateIndex + candidateRaw.length + 60).coerceAtMost(text.length)
        val window = text.substring(windowStart, windowEnd)
        val textBeforeCandidate = text.substring(0, candidateIndex)
        val lastHomeMarker = listOf("faltam", "meta diaria", "meta diária", "total do dia", "sem base de ontem")
            .maxOfOrNull { textBeforeCandidate.lastIndexOf(it) }
            ?: -1
        val lastOfferMarker = listOf("uberx", "uber black", "comfort", "flash", "priority", "99")
            .maxOfOrNull { textBeforeCandidate.lastIndexOf(it) }
            ?: -1
        val hasOfferMarkerAfterHomeMarker = lastOfferMarker > lastHomeMarker
        if (hasOfferMarkerAfterHomeMarker && value > 0.0) {
            return false
        }
        return window.contains("faltam") ||
            window.contains("para bater a meta") ||
            window.contains("meta diaria") ||
            window.contains("meta diária") ||
            window.contains("total do dia") ||
            window.contains("sem base de ontem") ||
            value == 0.0
    }

    private fun findCandidateIndex(text: String, candidateRaw: String, value: Double): Int {
        val direct = text.indexOf(candidateRaw)
        if (direct >= 0) {
            return direct
        }
        val decimal = String.format(java.util.Locale.US, "%.2f", value)
        val comma = decimal.replace(".", ",")
        return text.indexOf("r$ $comma").takeIf { it >= 0 }
            ?: text.indexOf("r$$comma").takeIf { it >= 0 }
            ?: text.indexOf(comma)
    }
}
