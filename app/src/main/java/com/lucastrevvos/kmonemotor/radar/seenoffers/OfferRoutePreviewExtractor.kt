package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.text.Normalizer

data class OfferRoutePreview(
    val originPreview: String?,
    val destinationPreview: String?,
    val confidence: Int,
    val reason: String
)

class OfferRoutePreviewExtractor {
    fun extract(rawText: String, platform: RidePlatform): OfferRoutePreview {
        val cleaned = rawText
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) {
            return OfferRoutePreview(null, null, 0, "empty_text")
        }

        val extracted = mutableListOf<String>()
        val addressStarts = addressStartRegex.findAll(cleaned)
            .map { it.range.first }
            .toList()
        val routeMarkers = routeMarkerRegex.findAll(cleaned)
            .map { it.range.first }
            .toList()

        addressStarts.forEachIndexed { index, start ->
            val nextAddressStart = addressStarts.getOrNull(index + 1)
            val nextRouteMarker = routeMarkers.firstOrNull { it > start }
            val end = listOfNotNull(nextAddressStart, nextRouteMarker, cleaned.length)
                .filter { it > start }
                .minOrNull()
                ?: cleaned.length
            normalizeAddressCandidate(cleaned.substring(start, end))
                ?.takeUnless(::containsIgnoredRouteText)
                ?.takeUnless(::isMapOnlyNoise)
                ?.let(extracted::add)
        }

        if (extracted.isEmpty()) {
            addressRegex.findAll(cleaned).forEach { match ->
                normalizeAddressCandidate(match.value)
                    ?.takeUnless(::containsIgnoredRouteText)
                    ?.takeUnless(::isMapOnlyNoise)
                    ?.let(extracted::add)
            }
        }
        val segments = extracted.distinct()

        if (segments.isEmpty()) {
            return OfferRoutePreview(null, null, 0, "no_address_like_segments")
        }

        val origin = segments.getOrNull(0)
        val destination = segments.getOrNull(1)
        val confidence = when {
            origin != null && destination != null -> 80
            origin != null -> if (platform == RidePlatform.UBER) 65 else 55
            else -> 0
        }
        val reason = when {
            origin != null && destination != null -> "two_address_segments_detected"
            origin != null -> "single_address_segment_detected"
            else -> "no_address_like_segments"
        }
        return OfferRoutePreview(origin, destination, confidence, reason)
    }

    private fun normalizeAddressCandidate(candidate: String): String? {
        var value = candidate
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', ';', '|')
        value = value.replace(Regex("\\b\\d+\\s*(min|minutos?|km|m|r\\$)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', ';', '|')
        if (value.isBlank()) return null
        if (value.length > 64) {
            value = value.take(64).trimEnd()
            value = value.substringBeforeLast(',').ifBlank { value }
        }
        val words = value.split(" ")
        if (words.size > 2 && words.last().length == 1) {
            value = words.dropLast(1).joinToString(" ")
        }
        return value.takeIf { it.length >= 4 }
    }

    private fun containsIgnoredRouteText(value: String): Boolean {
        val normalized = normalize(value)
        return ignoredFragments.any { normalized.contains(it) }
    }

    private fun isMapOnlyNoise(value: String): Boolean {
        val normalized = normalize(value)
        val hasAddressPrefix = addressPrefixes.any { normalized.contains(it) }
        val hasStrongLocation = locationTerms.any { normalized.contains(it) }
        return !hasAddressPrefix && !hasStrongLocation
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.lowercase()
    }

    companion object {
        private val addressStartRegex = Regex(
            "(?i)\\b(?:rua|r\\.|avenida|av\\.|rod\\.|rodovia|servid[aã]o|estrada|travessa|sc-\\d+)"
        )

        private val addressRegex = Regex(
            "(?i)\\b(?:rua|r\\.|avenida|av\\.|rod\\.|rodovia|servid[aã]o|estrada|travessa|sc-\\d+)" +
                "[^\\n\\r]{1,80}"
        )

        private val routeMarkerRegex = Regex(
            "(?i)\\b\\d+\\s*(?:min|minuto|minutos)\\s*\\([\\d.,]+\\s*(?:km|m)\\)"
        )

        private val addressPrefixes = listOf(
            "rua",
            "r.",
            "avenida",
            "av.",
            "rod.",
            "rodovia",
            "servidao",
            "estrada",
            "travessa",
            "sc-"
        )

        private val locationTerms = listOf(
            "jurere",
            "canasvieiras",
            "vargem",
            "florianopolis"
        )

        private val ignoredFragments = listOf(
            "km one",
            "meta",
            "analisar",
            "total do dia",
            "deslocamento maior que a corrida",
            "r$/km total",
            "busca:",
            "corrida:",
            "aceitei",
            "recusei",
            "ignorar",
            "99 abastece",
            "posto",
            "gasolina",
            "etanol",
            "litro",
            "buscando"
        )
    }
}
