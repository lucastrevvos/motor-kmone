package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind

object AutomaticCaptureFrameFilter {
    private val SELF_OVERLAY_PATTERNS = listOf(
        "km one",
        "r$/km total",
        "total do dia",
        "busca:",
        "corrida:",
        "deslocamento maior que a corrida",
        "abaixo de r$"
    )

    fun isSelfOverlayContaminated(rawText: String): Boolean {
        val normalized = rawText.lowercase()
        return SELF_OVERLAY_PATTERNS.any { normalized.contains(it) }
    }

    fun canWinMicroBurst(
        fingerprintKind: OfferTextFingerprintKind?,
        rawText: String
    ): Boolean {
        return fingerprintKind == OfferTextFingerprintKind.OFFER_LIKE &&
            !isSelfOverlayContaminated(rawText)
    }
}
