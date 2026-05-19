package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.core.RadarFeatureFlags
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
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
        if (!looksLikeMapOrHomeContamination(input.rawOcrText)) {
            return AutomaticCaptureBurstDecision(false, 0L, emptyList(), "not_map_home_contamination")
        }
        return AutomaticCaptureBurstDecision(
            shouldScheduleBurst = true,
            delayMs = min(config.delayMs, config.maxDelayMs),
            preferredCropOrder = preferredCropOrder(input.triggerSource),
            reason = "map_home_contamination"
        )
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

    private fun preferredCropOrder(triggerSource: TriggerSource): List<CropKind> {
        return when (triggerSource) {
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC -> listOf(
                CropKind.LOWER_HALF,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                CropKind.FLOATING_BOUNDS_EXPANDED,
                CropKind.CENTER_CARD_AREA
            )

            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            TriggerSource.UBER_AUTO_BURST_RECOVERY -> listOf(
                CropKind.LOWER_HALF,
                CropKind.CENTER_CARD_AREA,
                CropKind.PLATFORM_SPECIFIC_CANDIDATE,
                CropKind.FLOATING_BOUNDS_EXPANDED
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
    }
}
