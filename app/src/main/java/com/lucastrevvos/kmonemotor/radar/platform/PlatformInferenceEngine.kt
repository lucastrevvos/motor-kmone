package com.lucastrevvos.kmonemotor.radar.platform

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint

class PlatformInferenceEngine {
    fun infer(input: PlatformInferenceInput): PlatformInferenceResult {
        RadarLogger.i(
            "KM_V2_PLATFORM",
            "KM_V2_PLATFORM_INFERENCE_STARTED",
            "triggerSource" to input.triggerSource,
            "currentPlatformHint" to input.currentPlatformHint
        )

        val strongUberSignals = STRONG_UBER_SIGNALS.filter { it.regex.containsMatchIn(input.normalizedText) }.map { it.label }
        val strongNinetyNineSignals = STRONG_NINETY_NINE_SIGNALS.filter { it.regex.containsMatchIn(input.normalizedText) }.map { it.label }
        val weakSignals = buildList {
            addAll(WEAK_UBER_SIGNALS.filter { it.regex.containsMatchIn(input.normalizedText) }.map { it.label })
            addAll(WEAK_NINETY_NINE_SIGNALS.filter { it.regex.containsMatchIn(input.normalizedText) }.map { it.label })
        }
        val contextSignals = buildList {
            triggerFallback(input.triggerSource)?.let { add(it.second) }
            packageSignal(input.dominantPackage)?.let { add(it) }
            packageSignal(input.nodeTreePackage)?.let { add(it) }
            packageSignal(input.floatingPackage)?.let { add(it) }
            input.currentPlatformHint?.takeIf { it != PlatformTextHint.UNKNOWN }?.let { add("current_hint:${it.name}") }
        }

        val hasUberStrong = strongUberSignals.isNotEmpty()
        val hasNinetyNineStrong = strongNinetyNineSignals.isNotEmpty()
        val conflict = hasUberStrong && hasNinetyNineStrong
        val enviosUberSignals = ENVIOS_UBER_SIGNALS.filter { it.regex.containsMatchIn(input.normalizedText) }.map { it.label }
        val hasUberOfferPrice = OFFER_PRICE_PATTERN.containsMatchIn(input.normalizedText)
        val hasRouteSignal = ROUTE_SIGNAL_PATTERN.containsMatchIn(input.normalizedText)
        val hasVerifiedSignal = VERIFIED_SIGNAL_PATTERN.containsMatchIn(input.normalizedText)
        val hasRatingSignal = RATING_SIGNAL_PATTERN.containsMatchIn(input.normalizedText)
        val hasUberEnviosStructure = enviosUberSignals.isNotEmpty() &&
            hasUberOfferPrice &&
            hasRouteSignal &&
            (hasVerifiedSignal || hasRatingSignal || enviosUberSignals.any { it.contains("Exclusivo") || it.contains("Envios") })

        val result = when {
            hasUberStrong -> PlatformInferenceResult(
                platform = PlatformTextHint.UBER,
                confidence = 0.95,
                reason = if (conflict) "strong_uber_text_over_99_context" else "strong_uber_text_signal",
                strongTextSignals = strongUberSignals + strongNinetyNineSignals,
                weakTextSignals = weakSignals,
                contextSignals = contextSignals,
                conflict = conflict
            )

            !hasNinetyNineStrong && hasUberEnviosStructure -> PlatformInferenceResult(
                platform = PlatformTextHint.UBER,
                confidence = 0.9,
                reason = "uber_envios_product_signal",
                strongTextSignals = enviosUberSignals,
                weakTextSignals = weakSignals,
                contextSignals = contextSignals,
                conflict = false
            )

            hasNinetyNineStrong -> PlatformInferenceResult(
                platform = PlatformTextHint.NINETY_NINE,
                confidence = 0.92,
                reason = "strong_99_text_signal",
                strongTextSignals = strongNinetyNineSignals,
                weakTextSignals = weakSignals,
                contextSignals = contextSignals,
                conflict = false
            )

            triggerFallback(input.triggerSource) != null -> {
                val fallback = triggerFallback(input.triggerSource)!!
                PlatformInferenceResult(
                    platform = fallback.first,
                    confidence = 0.55,
                    reason = "trigger_fallback",
                    strongTextSignals = emptyList(),
                    weakTextSignals = weakSignals,
                    contextSignals = contextSignals,
                    conflict = false
                )
            }

            input.currentPlatformHint != null && input.currentPlatformHint != PlatformTextHint.UNKNOWN -> PlatformInferenceResult(
                platform = input.currentPlatformHint,
                confidence = 0.45,
                reason = "current_hint_fallback",
                strongTextSignals = emptyList(),
                weakTextSignals = weakSignals,
                contextSignals = contextSignals,
                conflict = false
            )

            else -> PlatformInferenceResult(
                platform = PlatformTextHint.UNKNOWN,
                confidence = 0.2,
                reason = "no_platform_signal",
                strongTextSignals = emptyList(),
                weakTextSignals = weakSignals,
                contextSignals = contextSignals,
                conflict = false
            )
        }

        if (result.conflict) {
            RadarLogger.i(
                "KM_V2_PLATFORM",
                "KM_V2_PLATFORM_INFERENCE_CONFLICT",
                "platform" to result.platform,
                "reason" to result.reason,
                "signals" to result.strongTextSignals.joinToString(",")
            )
        }
        RadarLogger.i(
            "KM_V2_PLATFORM",
            "KM_V2_PLATFORM_INFERENCE_DECISION",
            "finalPlatform" to result.platform,
            "confidence" to result.confidence,
            "reason" to result.reason,
            "strongTextSignals" to result.strongTextSignals.joinToString(","),
            "triggerSource" to input.triggerSource,
            "conflict" to result.conflict
        )
        return result
    }

    private fun packageSignal(packageName: String?): String? {
        return when (packageName) {
            "com.ubercab.driver" -> "package:UBER"
            "com.app99.driver" -> "package:NINETY_NINE"
            else -> null
        }
    }

    private fun triggerFallback(triggerSource: TriggerSource?): Pair<PlatformTextHint, String>? {
        return when (triggerSource) {
            TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
            TriggerSource.UBER_PRE_OFFER_VISUAL_WATCHDOG,
            TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
            TriggerSource.UBER_AUTO_BURST_RECOVERY,
            TriggerSource.UBER_FLOATING_WINDOW,
            TriggerSource.UBER_STATE_TRANSITION -> PlatformTextHint.UBER to "trigger:uber"

            TriggerSource.NINETY_NINE_TREE_STRUCTURE,
            TriggerSource.NINETY_NINE_COMPACT_TREE_DIAGNOSTIC -> PlatformTextHint.NINETY_NINE to "trigger:ninety_nine"

            else -> null
        }
    }

    private data class SignalPattern(
        val label: String,
        val regex: Regex
    )

    private companion object {
        val STRONG_UBER_SIGNALS = listOf(
            SignalPattern("UberX", Regex("\\buber\\s*x\\b|\\buberx\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("UberX Exclusivo", Regex("\\buberx\\s+exclusivo\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Uber", Regex("\\buber\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Uber Product", Regex("\\buber\\s*(comfort|black|flash|moto)\\b", RegexOption.IGNORE_CASE))
        )
        val ENVIOS_UBER_SIGNALS = listOf(
            SignalPattern("Envios", Regex("\\benvios\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Envios Carro", Regex("\\benvios\\s+carro\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Envios Carro Exclusivo", Regex("\\benvios\\s+carro\\s+exclusivo\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Carro Exclusivo", Regex("\\bcarro\\s+exclusivo\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Exclusivo", Regex("\\bexclusivo\\b", RegexOption.IGNORE_CASE))
        )
        val WEAK_UBER_SIGNALS = listOf(
            SignalPattern("Priority", Regex("\\bpriority\\b|\\bprioridade\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Verificado", Regex("\\bverificado\\b", RegexOption.IGNORE_CASE))
        )
        val STRONG_NINETY_NINE_SIGNALS = listOf(
            SignalPattern("99Pop", Regex("\\b99\\s*pop\\b|\\b99pop\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Pagamento no app", Regex("pagamento no app", RegexOption.IGNORE_CASE)),
            SignalPattern("Dinheiro", Regex("\\bdinheiro\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Preco x", Regex("pre[cç]o\\s*x", RegexOption.IGNORE_CASE)),
            SignalPattern("Taxa de deslocamento", Regex("taxa de deslocamento", RegexOption.IGNORE_CASE)),
            SignalPattern("Perfil Premium", Regex("perfil premium", RegexOption.IGNORE_CASE)),
            SignalPattern("Corrida longa", Regex("corrida longa", RegexOption.IGNORE_CASE)),
            SignalPattern("Passageiro novo", Regex("passageiro novo", RegexOption.IGNORE_CASE)),
            SignalPattern("R por km", Regex("r\\$\\s*[0-9]+(?:[.,][0-9]{1,2})?\\s*/\\s*km", RegexOption.IGNORE_CASE)),
            SignalPattern("CPF verif", Regex("cpf\\s*e?\\s*cart[aã]o\\s*verif|cpf\\s*verif", RegexOption.IGNORE_CASE)),
            SignalPattern("Cartao verif", Regex("cart[aã]o\\s*verif", RegexOption.IGNORE_CASE)),
            SignalPattern("Corridas count", Regex("\\b\\d+\\s*corridas\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("99 Abastece", Regex("99 abastece|abastece", RegexOption.IGNORE_CASE))
        )
        val WEAK_NINETY_NINE_SIGNALS = listOf(
            SignalPattern("99", Regex("\\b99\\b", RegexOption.IGNORE_CASE)),
            SignalPattern("Prioritario", Regex("\\bprioritario\\b", RegexOption.IGNORE_CASE))
        )
        val OFFER_PRICE_PATTERN = Regex("r\\$\\s*\\d+[,.]\\d+", RegexOption.IGNORE_CASE)
        val ROUTE_SIGNAL_PATTERN = Regex("\\d+\\s*min(?:utos)?\\s*\\(\\s*\\d+[,.]?\\d*\\s*(?:km|m)\\s*\\)", RegexOption.IGNORE_CASE)
        val VERIFIED_SIGNAL_PATTERN = Regex("\\b[o0]?\\s*verificado\\b|\\bverificado\\b", RegexOption.IGNORE_CASE)
        val RATING_SIGNAL_PATTERN = Regex("\\b\\d+[,.]\\d{1,2}\\s*\\(\\d+\\)", RegexOption.IGNORE_CASE)
    }
}
