package com.lucastrevvos.kmonemotor.radar.fingerprint

import java.text.Normalizer

class OfferTextNormalizer {
    fun normalize(rawText: String): NormalizedOcrText {
        val repaired = OCR_REPAIRS.entries.fold(rawText) { acc, (from, to) ->
            acc.replace(from, to, ignoreCase = true)
        }
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

    private fun normalizeLine(value: String): String {
        val collapsed = value.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        val noAccents = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return noAccents
    }

    private companion object {
        val OCR_REPAIRS = linkedMapOf(
            "solicita├º├Áes" to "solicitacoes",
            "pre├ºo" to "preco",
            "inclu├¡do" to "incluido",
            "priorit├írio" to "prioritario",
            "n├úo" to "nao",
            "n├úo ├® poss├¡vel" to "nao e possivel",
            "poss├¡vel" to "possivel",
            "você" to "voce"
        )
    }
}
