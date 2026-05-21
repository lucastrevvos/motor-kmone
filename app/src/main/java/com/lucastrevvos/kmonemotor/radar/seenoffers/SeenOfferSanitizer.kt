package com.lucastrevvos.kmonemotor.radar.seenoffers

import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.text.Normalizer

data class SeenOfferSanitizationResult(
    val shouldPersist: Boolean,
    val sanitizedOffer: SeenOffer?,
    val reason: String,
    val warnings: List<String> = emptyList()
)

class SeenOfferSanitizer {
    fun sanitize(candidate: SeenOffer): SeenOfferSanitizationResult {
        if (SeenOfferSanitizationRules.hasOperationalMoneySignals(candidate) &&
            !SeenOfferSanitizationRules.hasStrongUberOfferSignals(candidate)
        ) {
            RadarLogger.i(
                "KM_V2_SEEN",
                "KM_V2_SANITIZER_REJECTED_REASON_DETAIL",
                "observationId" to candidate.observationId,
                "reason" to "operational_earnings_money_without_offer_evidence",
                "rawTextPreview" to candidate.rawTextPreview,
                "productName" to candidate.productName
            )
            return SeenOfferSanitizationResult(
                shouldPersist = false,
                sanitizedOffer = null,
                reason = "non_offer_fuel_or_promo_screen"
            )
        }

        if (SeenOfferSanitizationRules.hasFuelOrPromoSignals(candidate)) {
            if (SeenOfferSanitizationRules.hasStrongUberOfferSignals(candidate)) {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SANITIZER_UBER_PRIORITY_PROMO_TEXT_ALLOWED",
                    "observationId" to candidate.observationId,
                    "platform" to candidate.platform,
                    "productName" to candidate.productName,
                    "rawTextPreview" to candidate.rawTextPreview
                )
            } else {
                RadarLogger.i(
                    "KM_V2_SEEN",
                    "KM_V2_SANITIZER_REJECTED_REASON_DETAIL",
                    "observationId" to candidate.observationId,
                    "reason" to "non_offer_fuel_or_promo_screen",
                    "rawTextPreview" to candidate.rawTextPreview,
                    "productName" to candidate.productName
                )
                return SeenOfferSanitizationResult(
                    shouldPersist = false,
                    sanitizedOffer = null,
                    reason = "non_offer_fuel_or_promo_screen"
                )
            }
        }

        if (SeenOfferSanitizationRules.hasStrongUberOfferSignals(candidate)) {
            return SeenOfferSanitizationResult(
                shouldPersist = true,
                sanitizedOffer = candidate.normalizeZeroDistances().normalizeValuePerKm().normalizeProductName(),
                reason = "accepted"
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
    private val uberOfferTerms = listOf(
        "uberx",
        "priority",
        "comfort",
        "black",
        "flash",
        "moto",
        "exclusivo"
    )
    private val operationalMoneyTerms = listOf(
        "ganhos",
        "oportunidades",
        "agora seus ganhos sao mais altos",
        "e um bom momento para ficar online",
        "os ganhos das viagens sao altos",
        "pagina inicial",
        "mensagens",
        "menu",
        "ficar online",
        "voce esta online",
        "voce esta offline"
    )
    private val plusMoneyRegex = Regex("""\+\s*r\$\s*\d""", RegexOption.IGNORE_CASE)

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

    fun hasStrongUberOfferSignals(offer: SeenOffer): Boolean {
        val normalizedTexts = listOfNotNull(
            offer.rawTextPreview,
            offer.productName,
            offer.originPreview,
            offer.destinationPreview
        ).map(::normalize)
        val hasUberProduct = offer.platform == RidePlatform.UBER || normalizedTexts.any { text ->
            uberOfferTerms.any { text.contains(it) }
        }
        val hasPrice = offer.price != null
        val hasRoute = offer.tripDistanceKm != null ||
            offer.pickupDistanceKm != null ||
            offer.tripTimeMin != null ||
            offer.pickupTimeMin != null ||
            offer.totalDistanceKm != null
        return hasUberProduct && hasPrice && hasRoute
    }

    fun hasOperationalMoneySignals(offer: SeenOffer): Boolean {
        val normalizedTexts = listOfNotNull(
            offer.rawTextPreview,
            offer.productName,
            offer.originPreview,
            offer.destinationPreview
        ).map(::normalize)
        val hasMoney = normalizedTexts.any { text ->
            text.contains("r$") || plusMoneyRegex.containsMatchIn(text)
        }
        val hasOperationalContext = normalizedTexts.any { text ->
            operationalMoneyTerms.any { text.contains(it) }
        }
        return hasMoney && hasOperationalContext
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
