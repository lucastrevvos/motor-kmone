package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCaptureBurstPolicyTest {
    private val policy = AutomaticCaptureBurstPolicy()

    @Test
    fun unknownUberWithFicarOnline_suppressesRecovery() {
        val decision = policy.evaluate(input("Ficar online", OfferTextFingerprintKind.UNKNOWN), nowMs = 2_000L)

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("map_searching_recovery_suppressed", decision.reason)
    }

    @Test
    fun unknownUberWithBuscando_suppressesRecovery() {
        val decision = policy.evaluate(input("Buscando corridas em Jurere", OfferTextFingerprintKind.UNKNOWN), nowMs = 2_000L)

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("operational_screen_recovery_suppressed", decision.reason)
    }

    @Test
    fun unknownUberWithMapText_suppressesRecovery() {
        val decision = policy.evaluate(input("Praia de Canajure Sapiens Parque SC-401", OfferTextFingerprintKind.UNKNOWN), nowMs = 2_000L)

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("map_searching_recovery_suppressed", decision.reason)
    }

    @Test
    fun unknownUberWithFloatingObstruction_schedulesBurstWithObstructionReason() {
        val decision = policy.evaluate(
            input(
                rawText = "Tela parcialmente coberta",
                fingerprintKind = OfferTextFingerprintKind.UNKNOWN,
                obstructionSuspected = true,
                obstructionOverlapsCriticalArea = true
            ),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldScheduleBurst)
        assertEquals("possible_floating_obstruction", decision.reason)
    }

    @Test
    fun unknownUberDominantCenterCardWithProbableOfferContext_schedulesFallbackBurst() {
        val decision = policy.evaluate(
            input(
                rawText = "",
                fingerprintKind = OfferTextFingerprintKind.UNKNOWN,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
                platformHint = PlatformTextHint.UNKNOWN,
                cropKind = CropKind.CENTER_CARD_AREA
            ),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldScheduleBurst)
        assertEquals("dominant_center_unknown_retry_lower_half", decision.reason)
        assertEquals(CropKind.LOWER_HALF, decision.preferredCropOrder.first())
        assertEquals(CropKind.LOWER_THIRD, decision.preferredCropOrder[1])
    }

    @Test
    fun unknownFloatingOver99WithLowerHalf_schedulesFallbackBurst() {
        val decision = policy.evaluate(
            input(
                rawText = "",
                fingerprintKind = OfferTextFingerprintKind.UNKNOWN,
                triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
                platformHint = PlatformTextHint.UNKNOWN,
                cropKind = CropKind.LOWER_HALF
            ),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldScheduleBurst)
        assertEquals("unknown_probable_offer_context", decision.reason)
        assertEquals(CropKind.LOWER_HALF, decision.preferredCropOrder.first())
        assertEquals(CropKind.CENTER_CARD_AREA, decision.preferredCropOrder[1])
    }

    @Test
    fun unknownWithStrongOfferSignal_doesNotScheduleBurst() {
        val decision = policy.evaluate(
            input("UberX R$ 7,79 5 min (1,4 km)", OfferTextFingerprintKind.UNKNOWN),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("strong_offer_signal_present", decision.reason)
    }

    @Test
    fun offerLikeNeverSchedulesBurst() {
        val decision = policy.evaluate(
            input(
                rawText = "Praia de Canajure",
                fingerprintKind = OfferTextFingerprintKind.OFFER_LIKE,
                obstructionSuspected = true,
                obstructionOverlapsCriticalArea = true
            ),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("offer_like_result", decision.reason)
    }

    @Test
    fun nonOfferWithOnlineOnStrongUber_suppressesRecovery() {
        val decision = policy.evaluate(
            input("Ficar online", OfferTextFingerprintKind.NON_OFFER),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("map_searching_recovery_suppressed", decision.reason)
    }

    @Test
    fun nonOfferWithMapHomeText_stillSchedulesBurstRecovery() {
        val decision = policy.evaluate(
            input(
                rawText = "Canajure SC-401 buscando 1-7 min",
                fingerprintKind = OfferTextFingerprintKind.NON_OFFER,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC
            ),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("operational_screen_recovery_suppressed", decision.reason)
    }

    @Test
    fun nonOfferOperationalScreen_suppressesRecovery() {
        val decision = policy.evaluate(
            input(
                rawText = "Ganhos Oportunidades +R$ 2 Pagina inicial Mensagens Menu",
                fingerprintKind = OfferTextFingerprintKind.NON_OFFER,
                triggerSource = TriggerSource.UBER_AUTO_BURST_RECOVERY
            ),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("operational_screen_recovery_suppressed", decision.reason)
    }

    @Test
    fun nonOfferFloatingOver99WithoutFuelPromoOrHome_stillSchedulesBurstRecovery() {
        val decision = policy.evaluate(
            input(
                rawText = "texto fraco de card escuro",
                fingerprintKind = OfferTextFingerprintKind.NON_OFFER,
                triggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
                platformHint = PlatformTextHint.UNKNOWN,
                cropKind = CropKind.CENTER_CARD_AREA
            ),
            nowMs = 2_000L
        )

        assertTrue(decision.shouldScheduleBurst)
        assertEquals("non_offer_probable_offer_context", decision.reason)
        assertEquals(CropKind.LOWER_HALF, decision.preferredCropOrder.first())
    }

    @Test
    fun nonOfferFuelPromo_usesExplicitReason() {
        val decision = policy.evaluate(
            input("99 Abastece Posto Santa Monica Rede Primos R$4,39", OfferTextFingerprintKind.NON_OFFER),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("non_offer_fuel_or_promo_screen", decision.reason)
    }

    @Test
    fun nonOfferFuelPromoWithObstruction_stillDoesNotScheduleBurst() {
        val decision = policy.evaluate(
            input(
                rawText = "99 Abastece Posto Santa Monica Rede Primos R$4,39 R$4,79",
                fingerprintKind = OfferTextFingerprintKind.NON_OFFER,
                obstructionSuspected = true,
                obstructionOverlapsCriticalArea = true
            ),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("non_offer_fuel_or_promo_screen", decision.reason)
    }

    @Test
    fun ninetyNineTriggerDoesNotScheduleBurst() {
        val decision = policy.evaluate(
            input("Ficar online", OfferTextFingerprintKind.UNKNOWN, triggerSource = TriggerSource.NINETY_NINE_TREE_STRUCTURE),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("non_eligible_trigger", decision.reason)
    }

    @Test
    fun manualTriggerDoesNotScheduleBurst() {
        val decision = policy.evaluate(
            input("Ficar online", OfferTextFingerprintKind.UNKNOWN, triggerSource = TriggerSource.MANUAL_SCREEN_ANALYSIS),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("non_eligible_trigger", decision.reason)
    }

    @Test
    fun attemptAboveZeroDoesNotScheduleSecondBurst() {
        val decision = policy.evaluate(
            input("Ficar online", OfferTextFingerprintKind.UNKNOWN, attempt = 1),
            nowMs = 2_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("attempt_limit", decision.reason)
    }

    @Test
    fun oldCaptureDoesNotScheduleBurst() {
        val decision = policy.evaluate(
            input("Ficar online", OfferTextFingerprintKind.UNKNOWN, captureStartedAtMs = 0L),
            nowMs = 5_000L
        )

        assertFalse(decision.shouldScheduleBurst)
        assertEquals("too_old", decision.reason)
    }

    @Test
    fun floatingOver99UsesLowerHalfFirst() {
        val decision = policy.evaluate(
            input("", OfferTextFingerprintKind.UNKNOWN),
            nowMs = 2_000L
        )

        assertEquals(CropKind.LOWER_HALF, decision.preferredCropOrder.first())
    }

    @Test
    fun dominantUsesLowerHalfFirstForBurst() {
        val decision = policy.evaluate(
            input(
                "",
                OfferTextFingerprintKind.UNKNOWN,
                triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC
            ),
            nowMs = 2_000L
        )

        assertEquals(CropKind.LOWER_HALF, decision.preferredCropOrder.first())
        assertEquals(CropKind.LOWER_THIRD, decision.preferredCropOrder[1])
    }

    private fun input(
        rawText: String,
        fingerprintKind: OfferTextFingerprintKind,
        triggerSource: TriggerSource = TriggerSource.UBER_FLOATING_OVER_99_DIAGNOSTIC,
        attempt: Int = 0,
        captureStartedAtMs: Long = 500L,
        platformHint: PlatformTextHint = PlatformTextHint.UBER,
        cropKind: CropKind = CropKind.CENTER_CARD_AREA,
        obstructionSuspected: Boolean = false,
        obstructionOverlapsCriticalArea: Boolean = false
    ) = AutomaticCaptureBurstInput(
        observationId = "obs",
        triggerSource = triggerSource,
        cropKind = cropKind,
        rawOcrText = rawText,
        fingerprintKind = fingerprintKind,
        platformHint = platformHint,
        createdAtMs = captureStartedAtMs,
        captureStartedAtMs = captureStartedAtMs,
        attempt = attempt,
        obstructionSuspected = obstructionSuspected,
        obstructionOverlapsCriticalArea = obstructionOverlapsCriticalArea
    )
}
