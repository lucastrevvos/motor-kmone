package com.lucastrevvos.kmonemotor.radar.fingerprint

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.text.Normalizer

class OfferTextNormalizer {
    fun normalize(rawText: String): NormalizedOcrText {
        val repaired = repair(rawText)
        val normalizedLines = repaired
            .lines()
            .map { normalizeLine(it) }
            .filter { it.isNotBlank() }
        val normalizedText = normalizedLines.joinToString("\n")
        val tokens = normalizedText
            .replace("\n", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return NormalizedOcrText(
            originalText = rawText,
            normalizedText = normalizedText,
            lines = normalizedLines,
            tokens = tokens
        )
    }

    fun repair(rawText: String, emitUberRouteDiagnostics: Boolean = false): String {
        val basicRepaired = OCR_REPAIRS.entries.fold(rawText) { acc, (from, to) ->
            acc.replace(from, to, ignoreCase = true)
        }
        val routeMinuteRepaired = basicRepaired
            .replace(UBER_MINUTE_OCR_REGEX) { match ->
                "${match.groupValues[1]}1"
            }
        val routeRepaired = routeMinuteRepaired
            .replace(UBER_DECIMAL_OCR_REGEX, "0.1")
        if (emitUberRouteDiagnostics && routeRepaired != rawText) {
            RadarLogger.i(
                "KM_V2_PARSER",
                "KM_V2_UBER_ROUTE_OCR_NORMALIZED",
                "original" to rawText.take(180),
                "normalized" to routeRepaired.take(180),
                "reason" to "uber_l_1_ocr_confusion"
            )
        }
        return routeRepaired
    }

    private fun normalizeLine(value: String): String {
        val collapsed = value.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        val noAccents = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return noAccents
            .replace(Regex("solicita\\S*es"), "solicitacoes")
            .replace(Regex("pre\\S*o"), "preco")
            .replace(Regex("inclu\\S*do"), "incluido")
            .replace(Regex("priorit\\S*rio"), "prioritario")
            .replace(Regex("\\bn\\S{2,}o\\b"), "nao")
            .replace(Regex("poss\\S*vel"), "possivel")
    }

    private companion object {
        val UBER_MINUTE_OCR_REGEX = Regex(
            """(^|[\s(])(?:l|i|\|)(?=\s*min\b)""",
            RegexOption.IGNORE_CASE
        )
        val UBER_DECIMAL_OCR_REGEX = Regex(
            """(?<=\b0[.,])(?:l|i|\|)(?=\s*km\b)""",
            RegexOption.IGNORE_CASE
        )
        val OCR_REPAIRS = linkedMapOf(
            "solicitaâ”œÂºâ”œÃes" to "solicitacoes",
            "preâ”œÂºo" to "preco",
            "incluâ”œÂ¡do" to "incluido",
            "prioritâ”œÃ­rio" to "prioritario",
            "nâ”œÃºo" to "nao",
            "nâ”œÃºo â”œÂ® possâ”œÂ¡vel" to "nao e possivel",
            "possâ”œÂ¡vel" to "possivel",
            "vocÃª" to "voce"
        )
    }
}
