package com.lucastrevvos.kmonemotor.radar.seenoffers

import java.text.Normalizer

data class SeenOfferSanitizationResult(
    val shouldPersist: Boolean,
    val sanitizedOffer: SeenOffer?,
    val reason: String,
    val warnings: List<String> = emptyList()
)

class SeenOfferSanitizer {
    fun sanitize(candidate: SeenOffer): SeenOfferSanitizationResult {
        if (SeenOfferSanitizationRules.hasFuelOrPromoSignals(candidate)) {
            return SeenOfferSanitizationResult(
                shouldPersist = false,
                sanitizedOffer = null,
                reason = "non_offer_fuel_or_promo_screen"
            )
        }

        var warnings = emptyList<String>()
        var adjusted = candidate

        adjusted = adjusted.normalizeZeroDistances().also {
            if (it != adjusted) warnings += "zero_distance_normalized_to_null"
        }
        adjusted = adjusted.normalizeValuePerKm().also {
            if (it != adjusted) warnings += "value_per_km_normalized"
        }
        adjusted = adjusted.normalizeProductName().also {
            if (it != adjusted) warnings += "product_name_normalized"
        }

        return SeenOfferSanitizationResult(
            shouldPersist = true,
            sanitizedOffer = adjusted,
            reason = if (warnings.isEmpty()) "accepted" else "adjusted",
            warnings = warnings
        )
    }

    private fun SeenOffer.normalizeZeroDistances(): SeenOffer {
        return copy(
            pickupDistanceKm = pickupDistanceKm.takeIf { it == null || it > 0.0 },
            tripDistanceKm = tripDistanceKm.takeIf { it == null || it > 0.0 },
            totalDistanceKm = totalDistanceKm.takeIf { it == null || it > 0.0 }
        )
    }

    private fun SeenOffer.normalizeValuePerKm(): SeenOffer {
        val normalized = valuePerKm?.takeIf { it > 0.0 && it <= 20.0 }
        return if (normalized == valuePerKm) this else copy(valuePerKm = normalized)
    }

    private fun SeenOffer.normalizeProductName(): SeenOffer {
        val normalized = productName?.takeUnless { SeenOfferSanitizationRules.isBadProductName(it) }
        return if (normalized == productName) this else copy(productName = normalized)
    }
}

object SeenOfferSanitizationRules {
    private val fuelTerms = listOf(
        "99 abastece",
        "abastece",
        "posto",
        "rede primos",
        "economize",
        "combustivel",
        "gasolina",
        "etanol",
        "litro"
    )

    fun hasFuelOrPromoSignals(offer: SeenOffer): Boolean {
        return listOfNotNull(
            offer.rawTextPreview,
            offer.productName,
            offer.originPreview,
            offer.destinationPreview
        ).any { text ->
            val normalized = normalize(text)
            fuelTerms.any { normalized.contains(it) }
        }
    }

    fun isBadProductName(productName: String): Boolean {
        val normalized = normalize(productName)
        if (normalized.isBlank()) return true
        if (normalized.contains("km one")) return true
        if (normalized.contains("analisar")) return true
        if (normalized.contains("analisar")) return true
        if (normalized.contains("sapiens parque")) return true
        if (normalized.contains("sc-401")) return true
        if (normalized.contains("jurere")) return true
        if (normalized.contains("vargem")) return true
        return false
    }

    fun isSuspiciousForPendingQueue(offer: SeenOffer): Boolean {
        if (hasFuelOrPromoSignals(offer)) return true
        if ((offer.price ?: 0.0) > 300.0) return true
        if ((offer.totalDistanceKm ?: 0.0) > 150.0) return true
        if ((offer.tripDistanceKm ?: 0.0) > 100.0 && (offer.tripTimeMin ?: Double.MAX_VALUE) <= 30.0) return true
        if (offer.productName?.let(::isBadProductName) == true) return true
        return false
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.lowercase()
    }
}
