package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.orchestrator.UberOperationalScreenClassifier
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import kotlin.math.min

class AutomaticCaptureBurstPolicy(
    private val config: Config = Config()
) {
    fun evaluate(input: AutomaticCaptureBurstInput, nowMs: Long): AutomaticCaptureBurstDecision {
        val ageMs = nowMs - (input.captureStartedAtMs ?: input.createdAtMs)
        val eligibleTrigger = input.triggerSource == TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC ||
            input.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC ||
            input.triggerSource == TriggerSource.UBER_AUTO_BURST_RECOVERY
        if (!eligibleTrigger) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "non_eligible_trigger")
        }
        if (input.fingerprintKind == OfferTextFingerprintKind.OFFER_LIKE) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "offer_like_result")
        }
        if (input.attempt >= config.maxAttempts) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "attempt_limit")
        }
        if (ageMs > config.maxAgeMs) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "too_old")
        }
        if (input.fingerprintKind == OfferTextFingerprintKind.NON_OFFER &&
            looksLikeFuelOrPromoScreen(input.rawOcrText)
        ) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "non_offer_fuel_or_promo_screen")
        }
        if (shouldSuppressRecoveryForOperationalScreen(input.rawOcrText)) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "operational_screen_recovery_suppressed")
        }
        if (shouldSuppressRecoveryForMapSearching(input.rawOcrText)) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "map_searching_recovery_suppressed")
        }
        if (input.obstructionSuspected && input.obstructionOverlapsCriticalArea) {
            return AutomaticCaptureBurstDecision(
                shouldScheduleBurst = true,
                delayMs = min(config.delayMs, config.maxDelayMs),
                preferredCropOrder = preferredCropOrder(input.triggerSource),
                reason = "possible_floating_obstruction"
            )
        }
        if (containsStrongOfferSignal(input.rawOcrText)) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "strong_offer_signal_present")
        }
        if (looksLikeMapOrHomeContamination(input.rawOcrText)) {
            return AutomaticCaptureBurstDecision(
                shouldScheduleBurst = true,
                delayMs = min(config.delayMs, config.maxDelayMs),
                preferredCropOrder = preferredCropOrder(input.triggerSource),
                reason = "map_home_contamination"
            )
        }
        if (
            input.fingerprintKind == OfferTextFingerprintKind.UNKNOWN &&
            (input.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC ||
                input.triggerSource == TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC) &&
            (input.cropKind == CropKind.CENTER_CARD_AREA || input.cropKind == CropKind.LOWER_HALF) &&
            hasProbableOfferContext(input.rawOcrText, input.platformHint, input.triggerSource)
        ) {
            return AutomaticCaptureBurstDecision(
                shouldScheduleBurst = true,
                delayMs = min(config.delayMs, config.maxDelayMs),
                preferredCropOrder = preferredCropOrder(input.triggerSource),
                reason = if (
                    input.triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC &&
                    input.cropKind == CropKind.CENTER_CARD_AREA &&
                    shouldForceLowerHalfRetry(input.rawOcrText)
                ) {
                    "dominant_center_unknown_retry_lower_half"
                } else {
                    "unknown_probable_offer_context"
                }
            )
        }
        if (
            input.fingerprintKind == OfferTextFingerprintKind.NON_OFFER &&
            input.triggerSource == TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC &&
            (input.cropKind == CropKind.CENTER_CARD_AREA || input.cropKind == CropKind.LOWER_HALF) &&
            !looksLikeFuelOrPromoScreen(input.rawOcrText) &&
            !looksLikeMapOrHomeContamination(input.rawOcrText)
        ) {
            return AutomaticCaptureBurstDecision(
                shouldScheduleBurst = true,
                delayMs = min(config.delayMs, config.maxDelayMs),
                preferredCropOrder = preferredCropOrder(input.triggerSource),
                reason = "non_offer_probable_offer_context"
            )
        }
        if (!looksLikeMapOrHomeContamination(input.rawOcrText)) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "not_map_home_contamination")
        }
        return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "not_map_home_contamination")
    }

    fun looksLikeMapOrHomeContamination(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        return MAP_HOME_PATTERNS.any { it.containsMatchIn(normalized) }
    }

    fun looksLikeFuelOrPromoScreen(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        return FUEL_PROMO_PATTERNS.any { it.containsMatchIn(normalized) }
    }

    private fun containsStrongOfferSignal(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        return OFFER_PATTERNS.any { it.containsMatchIn(normalized) }
    }

    private fun hasProbableOfferContext(
        rawText: String,
        platformHint: PlatformTextHint?,
        triggerSource: TriggerSource
    ): Boolean {
        if (platformHint == PlatformTextHint.UBER || platformHint == PlatformTextHint.NINETY_NINE) {
            return true
        }
        if (triggerSource == TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC ||
            triggerSource == TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC
        ) {
            if (rawText.isBlank()) return true
            val normalized = rawText.lowercase()
            return PROBABLE_OFFER_CONTEXT_PATTERNS.any { it.containsMatchIn(normalized) }
        }
        return false
    }

    private fun shouldForceLowerHalfRetry(rawText: String): Boolean {
        if (rawText.isBlank()) return true
        val normalized = rawText.lowercase()
        val noPrices = !normalized.contains("r$")
        val noDistances = !normalized.contains("km") && !normalized.contains(" m")
        val noProduct = !normalized.contains("uberx") && !normalized.contains("comfort") && !normalized.contains("black")
        return looksLikeMapOrHomeContamination(rawText) || (noPrices && noDistances && noProduct)
    }

    private fun preferredCropOrder(triggerSource: TriggerSource): List<CropKind> {
        return when (triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
                CropKind.FLOATING_BOUNDS_EXPANDED,
                CropKind.LOWER_THIRD,
                CropKind.FULL_DEBUG
            )

            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> listOf(
                CropKind.LOWER_HALF,
                CropKind.LOWER_THIRD,
                CropKind.CENTER_CARD_AREA,
                CropKind.FLOATING_BOUNDS_EXPANDED,
                CropKind.FULL_DEBUG
            )

            else -> emptyList()
        }
    }

    data class Config(
        val delayMs: Long = RadarFeatureFlags.AUTO_CAPTURE_BURST_DELAY_MS,
        val maxDelayMs: Long = RadarFeatureFlags.AUTO_CAPTURE_BURST_MAX_DELAY_MS,
        val maxAttempts: Int = RadarFeatureFlags.AUTO_CAPTURE_BURST_MAX_ATTEMPTS,
        val maxAgeMs: Long = RadarFeatureFlags.AUTO_CAPTURE_BURST_MAX_AGE_MS
    )

    private fun shouldSuppressRecoveryForOperationalScreen(rawText: String): Boolean {
        val signal = UberOperationalScreenClassifier.classify(listOf(rawText))
        return signal.isOperationalScreen && !containsStrongOfferSignal(rawText)
    }

    private fun shouldSuppressRecoveryForMapSearching(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        val hasSearching = normalized.contains("buscando") || normalized.contains("procurando viagens")
        val hasMapLike = MAP_HOME_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasOfferSemantics = containsStrongOfferSignal(rawText) || hasRoutePairText(rawText)
        return (hasSearching || hasMapLike) && !hasOfferSemantics
    }

    private fun hasRoutePairText(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        return Regex("""\d+\s*min(?:utos?)?\s*\(\s*\d+[,.]?\d*\s*(km|m)\s*\)""").containsMatchIn(normalized)
    }

    private companion object {
        val MAP_HOME_PATTERNS = listOf(
            Regex("ficar online"),
            Regex("\\bbuscando\\b"),
            Regex("sapiens parque"),
            Regex("sc-401"),
            Regex("sc-403"),
            Regex("jurere|jurerê"),
            Regex("canasvieiras"),
            Regex("\\bvargem\\b"),
            Regex("ratones"),
            Regex("praia de"),
            Regex("\\brua\\b"),
            Regex("\\brod\\.?\\b"),
            Regex("papaguaro"),
            Regex("praia|bairro|terminal")
        )

        val OFFER_PATTERNS = listOf(
            Regex("\\buberx\\b|\\buber\\s*x\\b"),
            Regex("r\\$\\s*\\d+[,.]\\d+"),
            Regex("\\d+\\s*min(?:utos)?\\s*\\(\\s*\\d+[,.]?\\d*\\s*(?:km|m)\\s*\\)"),
            Regex("r\\$\\s*\\d+[,.]\\d+\\s*/\\s*km"),
            Regex("dinheiro"),
            Regex("pagamento no app"),
            Regex("taxa de deslocamento"),
            Regex("perfil premium"),
            Regex("priorit[áa]rio")
        )

        val FUEL_PROMO_PATTERNS = listOf(
            Regex("99 abastece"),
            Regex("\\babastece\\b"),
            Regex("\\bposto\\b"),
            Regex("rede primos"),
            Regex("economize"),
            Regex("combust[ií]vel"),
            Regex("gasolina"),
            Regex("etanol"),
            Regex("\\blitro\\b")
        )

        val PROBABLE_OFFER_CONTEXT_PATTERNS = listOf(
            Regex("\\buber\\b|\\buberx\\b"),
            Regex("\\b99\\b"),
            Regex("r\\$"),
            Regex("\\bmin\\b|minutos"),
            Regex("\\bkm\\b"),
            Regex("priority|premium"),
            Regex("dinheiro|pagamento no app|taxa de deslocamento")
        )
    }
}
