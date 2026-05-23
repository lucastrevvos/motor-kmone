package com.lucastrevvos.kmonemotor.radar.fingerprint

import com.lucastrevvos.kmonemotor.radar.core.RadarClock
import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.ocr.OcrObservation
import com.lucastrevvos.kmonemotor.radar.platform.PlatformInferenceEngine
import com.lucastrevvos.kmonemotor.radar.platform.PlatformInferenceInput
import java.security.MessageDigest
import java.util.UUID

class OfferTextFingerprintExtractor(
    private val normalizer: OfferTextNormalizer = OfferTextNormalizer(),
    private val platformInferenceEngine: PlatformInferenceEngine = PlatformInferenceEngine(),
    private val clock: RadarClock = RadarClock.System
) {
    fun extract(observation: OcrObservation): OfferTextFingerprint {
        val normalized = normalizer.normalize(observation.rawText)
        if (!observation.success || normalized.normalizedText.isBlank()) {
            return buildUnknownFingerprint(
                observation = observation,
                normalized = normalized,
                reason = if (!observation.success) "ocr_failed" else "raw_text_empty"
            )
        }

        val priceCandidates = extractPriceCandidates(normalized, observation.observationId)
        val valuePerKmCandidates = extractValuePerKmCandidates(normalized)
        val distanceCandidates = extractDistanceCandidates(normalized)
        val timeCandidates = extractTimeCandidates(normalized)
        val positiveSignals = extractPositiveSignals(normalized, priceCandidates, valuePerKmCandidates, distanceCandidates, timeCandidates)
        val negativeSignals = extractNegativeSignals(normalized, priceCandidates)
        val platformInference = platformInferenceEngine.infer(
            PlatformInferenceInput(
                rawText = observation.rawText,
                normalizedText = normalized.normalizedText,
                triggerSource = observation.triggerSource
            )
        )
        val platformTextHint = platformInference.platform

        var offerLikeScore = positiveSignals.sumOf { it.confidence }
        var nonOfferScore = negativeSignals.sumOf { it.confidence }

        if (priceCandidates.isNotEmpty() && timeCandidates.isNotEmpty() && distanceCandidates.isNotEmpty()) {
            offerLikeScore += 5
        }
        if (priceCandidates.isNotEmpty() && valuePerKmCandidates.isNotEmpty()) {
            offerLikeScore += 4
        }
        if (valuePerKmCandidates.isNotEmpty()) {
            offerLikeScore += 3
        }
        if (priceCandidates.isNotEmpty() && distanceCandidates.isNotEmpty()) {
            offerLikeScore += 2
        }
        if (containsAny(normalized.normalizedText, listOf("dinheiro")) && priceCandidates.isNotEmpty()) {
            offerLikeScore += 2
        }
        if (containsAny(normalized.normalizedText, listOf("priority", "prioritario")) && priceCandidates.isNotEmpty()) {
            offerLikeScore += 2
        }

        if (containsAny(normalized.normalizedText, listOf("continuar aceitando solicitacoes")) &&
            containsAny(normalized.normalizedText, listOf("desconectar"))
        ) {
            nonOfferScore += 6
        }
        if ((containsAny(normalized.normalizedText, listOf("99 abastece")) ||
                containsAny(normalized.normalizedText, listOf("posto", "gasolina", "etanol"))) &&
            priceCandidates.isNotEmpty()
        ) {
            nonOfferScore += 6
        }
        if (priceCandidates.isEmpty() &&
            containsAny(normalized.normalizedText, listOf("online", "offline", "procurando corridas", "continuar aceitando solicitacoes"))
        ) {
            nonOfferScore += 4
        }

        val kind = when {
            offerLikeScore >= 6 && offerLikeScore > nonOfferScore -> OfferTextFingerprintKind.OFFER_LIKE
            nonOfferScore >= 5 && nonOfferScore >= offerLikeScore -> OfferTextFingerprintKind.NON_OFFER
            else -> OfferTextFingerprintKind.UNKNOWN
        }
        val reason = when {
            kind == OfferTextFingerprintKind.NON_OFFER &&
                containsAny(normalized.normalizedText, listOf("continuar aceitando solicitacoes", "desconectar")) ->
                "operational_disconnect_screen"
            kind == OfferTextFingerprintKind.NON_OFFER &&
                containsAny(normalized.normalizedText, listOf("99 abastece", "posto", "gasolina", "etanol")) ->
                "fuel_promo_or_abastece"
            kind == OfferTextFingerprintKind.OFFER_LIKE -> "offer_like_positive_signals"
            else -> "insufficient_offer_signals"
        }

        return OfferTextFingerprint(
            fingerprintId = UUID.randomUUID().toString(),
            ocrObservationId = observation.ocrObservationId,
            observationId = observation.observationId,
            captureRequestId = observation.captureRequestId,
            triggerSource = observation.triggerSource,
            cropKind = observation.cropKind,
            platformTextHint = platformTextHint,
            kind = kind,
            offerLikeScore = offerLikeScore,
            nonOfferScore = nonOfferScore,
            positiveSignals = positiveSignals,
            negativeSignals = negativeSignals,
            priceCandidates = priceCandidates,
            valuePerKmCandidates = valuePerKmCandidates,
            distanceCandidates = distanceCandidates,
            timeCandidates = timeCandidates,
            rawTextHash = sha256(normalized.normalizedText),
            routeTextHash = buildRouteTextHash(normalized.normalizedText),
            normalizedPreview = normalized.normalizedText.replace("\n", " ").take(160),
            reason = reason,
            createdAtMs = clock.nowMs()
        )
    }

    private fun buildUnknownFingerprint(
        observation: OcrObservation,
        normalized: NormalizedOcrText,
        reason: String
    ) = OfferTextFingerprint(
        fingerprintId = UUID.randomUUID().toString(),
        ocrObservationId = observation.ocrObservationId,
        observationId = observation.observationId,
        captureRequestId = observation.captureRequestId,
        triggerSource = observation.triggerSource,
        cropKind = observation.cropKind,
        platformTextHint = PlatformTextHint.UNKNOWN,
        kind = OfferTextFingerprintKind.UNKNOWN,
        offerLikeScore = 0,
        nonOfferScore = 0,
        positiveSignals = emptyList(),
        negativeSignals = emptyList(),
        priceCandidates = emptyList(),
        valuePerKmCandidates = emptyList(),
        distanceCandidates = emptyList(),
        timeCandidates = emptyList(),
        rawTextHash = sha256(normalized.normalizedText),
        routeTextHash = null,
        normalizedPreview = normalized.normalizedText.replace("\n", " ").take(160),
        reason = reason,
        createdAtMs = clock.nowMs()
    )

    private fun extractPriceCandidates(
        normalized: NormalizedOcrText,
        observationId: String
    ): List<ExtractedNumericCandidate> {
        val text = normalized.normalizedText
        return PRICE_REGEX.findAll(text)
            .mapNotNull { match ->
                val full = match.value
                val immediateSuffix = text.substring((match.range.last + 1).coerceAtMost(text.length), (match.range.last + 5).coerceAtMost(text.length))
                if ("/km" in immediateSuffix) return@mapNotNull null
                val numericToken = match.groupValues[1]
                ExtractedNumericCandidate(
                    raw = full,
                    normalizedValue = parsePriceDecimal(
                        rawToken = full,
                        numericToken = numericToken,
                        normalizedText = text,
                        observationId = observationId
                    ),
                    unit = "BRL",
                    kind = "PRICE",
                    confidence = if ("r$" in full) 3 else 2
                )
            }
            .toList()
    }

    private fun extractValuePerKmCandidates(normalized: NormalizedOcrText): List<ExtractedNumericCandidate> {
        return VALUE_PER_KM_REGEX.findAll(normalized.normalizedText)
            .map { match ->
                ExtractedNumericCandidate(
                    raw = match.value,
                    normalizedValue = parseDecimal(match.groupValues[1]),
                    unit = "BRL_PER_KM",
                    kind = "VALUE_PER_KM",
                    confidence = 3
                )
            }
            .toList()
    }

    private fun extractDistanceCandidates(normalized: NormalizedOcrText): List<ExtractedNumericCandidate> {
        val meters = DISTANCE_M_REGEX.findAll(normalized.normalizedText).map {
            ExtractedNumericCandidate(
                raw = it.value,
                normalizedValue = parseDecimal(it.groupValues[1]),
                unit = "m",
                kind = "DISTANCE_M",
                confidence = 2
            )
        }
        val km = DISTANCE_KM_REGEX.findAll(normalized.normalizedText).map {
            ExtractedNumericCandidate(
                raw = it.value,
                normalizedValue = parseDecimal(it.groupValues[1]),
                unit = "km",
                kind = "DISTANCE_KM",
                confidence = 2
            )
        }
        return (meters + km).toList()
    }

    private fun extractTimeCandidates(normalized: NormalizedOcrText): List<ExtractedNumericCandidate> {
        return TIME_REGEX.findAll(normalized.normalizedText).map {
            ExtractedNumericCandidate(
                raw = it.value,
                normalizedValue = parseDecimal(it.groupValues[1]),
                unit = "min",
                kind = "TIME_MINUTES",
                confidence = 2
            )
        }.toList()
    }

    private fun extractPositiveSignals(
        normalized: NormalizedOcrText,
        priceCandidates: List<ExtractedNumericCandidate>,
        valuePerKmCandidates: List<ExtractedNumericCandidate>,
        distanceCandidates: List<ExtractedNumericCandidate>,
        timeCandidates: List<ExtractedNumericCandidate>
    ): List<ExtractedSignal> {
        val text = normalized.normalizedText
        val signals = mutableListOf<ExtractedSignal>()
        POSITIVE_SIGNAL_DEFS.forEach { def ->
            if (containsAny(text, def.phrases)) {
                signals += ExtractedSignal(def.key, def.phrases.first { it in text }, def.confidence)
            }
        }
        if (priceCandidates.isNotEmpty() && distanceCandidates.isNotEmpty() && timeCandidates.isNotEmpty()) {
            signals += ExtractedSignal("price_time_distance_combo", "price+time+distance", 3)
        }
        if (valuePerKmCandidates.isNotEmpty()) {
            signals += ExtractedSignal("value_per_km_present", valuePerKmCandidates.first().raw, 3)
        }
        return signals.distinctBy { it.key }
    }

    private fun extractNegativeSignals(
        normalized: NormalizedOcrText,
        priceCandidates: List<ExtractedNumericCandidate>
    ): List<ExtractedSignal> {
        val text = normalized.normalizedText
        val signals = mutableListOf<ExtractedSignal>()
        NEGATIVE_SIGNAL_DEFS.forEach { def ->
            if (containsAny(text, def.phrases)) {
                signals += ExtractedSignal(def.key, def.phrases.first { it in text }, def.confidence)
            }
        }
        if ((containsAny(text, listOf("posto", "gasolina", "etanol")) || containsAny(text, listOf("99 abastece"))) &&
            priceCandidates.any { (it.normalizedValue ?: 0.0) in 3.5..7.0 }
        ) {
            signals += ExtractedSignal("fuel_price_pattern", "posto+price", 3)
        }
        return signals.distinctBy { it.key }
    }

    private fun buildRouteTextHash(normalizedText: String): String? {
        val stripped = normalizedText
            .replace(PRICE_REGEX, " ")
            .replace(VALUE_PER_KM_REGEX, " ")
            .replace(DISTANCE_M_REGEX, " ")
            .replace(DISTANCE_KM_REGEX, " ")
            .replace(TIME_REGEX, " ")
            .replace(MULTIPLIER_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return stripped.takeIf { it.isNotBlank() }?.let(::sha256)
    }

    private fun parsePriceDecimal(
        rawToken: String,
        numericToken: String,
        normalizedText: String,
        observationId: String
    ): Double? {
        val direct = parseDecimal(numericToken) ?: return null
        if (numericToken.contains(",") || numericToken.contains(".")) {
            return direct
        }
        val integerValue = direct.toInt()
        val inSuspiciousIntegerRange = direct >= 100.0 && direct <= 9999.0 && direct % 1.0 == 0.0
        if (!inSuspiciousIntegerRange) {
            return direct
        }
        if (!hasStrongOfferPriceContext(normalizedText) || looksLikeFuelPromoContext(normalizedText)) {
            return direct
        }
        val normalizedValue = direct / 100.0
        if (normalizedValue !in 3.0..300.0) {
            return direct
        }
        RadarLogger.i(
            "KM_V2_FINGERPRINT",
            "KM_V2_PRICE_NORMALIZED",
            "observationId" to observationId,
            "rawToken" to rawToken,
            "rawValue" to direct,
            "normalizedValue" to normalizedValue,
            "reason" to "missing_decimal_separator_in_offer_context"
        )
        return normalizedValue
    }

    private fun parseDecimal(raw: String): Double? {
        val normalized = raw.replace(",", ".").replace(Regex("[^0-9\\.]"), "")
        return normalized.toDoubleOrNull()
    }

    private fun hasStrongOfferPriceContext(text: String): Boolean {
        return OFFER_PRICE_CONTEXT_PATTERNS.any { it.containsMatchIn(text) }
    }

    private fun looksLikeFuelPromoContext(text: String): Boolean {
        return FUEL_PROMO_PATTERNS.any { it.containsMatchIn(text) }
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun containsAny(text: String, phrases: List<String>): Boolean {
        return phrases.any { it in text }
    }

    private data class SignalDef(
        val key: String,
        val phrases: List<String>,
        val confidence: Int
    )

    private companion object {
        val PRICE_REGEX = Regex("""r\$ ?([0-9]+(?:[.,][0-9]{1,2})?)""")
        val VALUE_PER_KM_REGEX = Regex("""(?:r\$ ?)?([0-9]+(?:[.,][0-9]{1,2})?)\s*/\s*km""")
        val DISTANCE_M_REGEX = Regex("""\(?([0-9]{2,4})\s*m(?!in)\b\)?""")
        val DISTANCE_KM_REGEX = Regex("""\(?([0-9]+(?:[.,][0-9])?)\s*km\)?""")
        val TIME_REGEX = Regex("""([0-9]{1,3})\s*min""")
        val MULTIPLIER_REGEX = Regex("""x\s*([0-9]+(?:[.,][0-9])?)""")

        val POSITIVE_SIGNAL_DEFS = listOf(
            SignalDef("dinheiro", listOf("dinheiro"), 2),
            SignalDef("corrida_longa", listOf("corrida longa"), 2),
            SignalDef("taxa_deslocamento", listOf("taxa de deslocamento"), 3),
            SignalDef("cpf_verif", listOf("cpf verif"), 2),
            SignalDef("cartao_verif", listOf("cartao verif"), 2),
            SignalDef("verificado", listOf("verificado"), 2),
            SignalDef("prioritario", listOf("prioritario"), 2),
            SignalDef("priority", listOf("priority"), 2),
            SignalDef("exclusivo", listOf("exclusivo"), 2),
            SignalDef("preco_x", listOf("preco x"), 2),
            SignalDef("r_por_km", listOf("r$/km", "/km"), 2),
            SignalDef("parada", listOf("1 parada", "paradas", "parada"), 1),
            SignalDef("corridas", listOf("corridas"), 1),
            SignalDef("incluido_prioridade", listOf("incluido para prioridade"), 2),
            SignalDef("solicitacoes", listOf("solicitacoes"), 1)
        )

        val NEGATIVE_SIGNAL_DEFS = listOf(
            SignalDef("continuar_aceitando_solicitacoes", listOf("continuar aceitando solicitacoes"), 3),
            SignalDef("desconectar", listOf("desconectar"), 3),
            SignalDef("99_abastece", listOf("99 abastece", "abastece"), 3),
            SignalDef("posto", listOf("posto"), 3),
            SignalDef("gasolina", listOf("gasolina"), 2),
            SignalDef("etanol", listOf("etanol"), 2),
            SignalDef("comecar_99", listOf("comecar 99", "comecar 99"), 2),
            SignalDef("online", listOf("voce esta online", "online"), 1),
            SignalDef("offline", listOf("voce esta offline", "offline"), 1),
            SignalDef("procurando_corridas", listOf("procurando corridas"), 2)
        )

        val OFFER_PRICE_CONTEXT_PATTERNS = listOf(
            Regex("\\buberx\\b|\\buber\\b|\\bentrega\\b|\\bcarro\\b|\\b99\\b"),
            Regex("corrida|verificado|exclusivo|priority|prioritario"),
            Regex("\\d+\\s*min(?:utos)?"),
            Regex("\\d+[,.]?\\d*\\s*km")
        )

        val FUEL_PROMO_PATTERNS = listOf(
            Regex("99 abastece"),
            Regex("\\babastece\\b"),
            Regex("\\bposto\\b"),
            Regex("rede primos"),
            Regex("\\blitro\\b"),
            Regex("gasolina"),
            Regex("etanol")
        )

    }
}
