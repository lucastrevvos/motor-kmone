package com.lucastrevvos.kmonemotor.radar.seenoffers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenOfferOverlayPolicyTest {
    @Test
    fun manualDuplicate_overlayIsSuppressedAndFinalReasonUsesPersistReason() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = true,
            persisted = false,
            seenOffer = SeenOffer(
                id = "seen-1",
                observationId = "obs-1",
                platform = RidePlatform.UBER,
                sourceTrigger = "MANUAL_SCREEN_ANALYSIS",
                status = SeenOfferStatus.SEEN,
                price = 9.01,
                valuePerKm = 1.2,
                pickupDistanceKm = 2.0,
                pickupTimeMin = 4.0,
                tripDistanceKm = 5.0,
                tripTimeMin = 8.0,
                totalDistanceKm = 7.0,
                estimatedTotalTimeMin = 12.0,
                productName = "UberX",
                originPreview = null,
                destinationPreview = null,
                rawTextPreview = null,
                score = null,
                rawTextHash = null,
                routeTextHash = null,
                createdAtMs = 1L,
                updatedAtMs = 1L
            ),
            reason = "weaker_duplicate_offer_recently_saved"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = "SHOW_WARNING",
            isManual = true
        )

        assertFalse(decision.shouldShowOverlay)
        assertNull(decision.overlayKind)
        assertEquals("weaker_duplicate_offer_recently_saved", decision.finalReasonOverride)
        assertEquals("seen-1", decision.reShowExistingSeenOfferId)
    }

    @Test
    fun mergedManualAuthority_overlayIsSuppressedAndFinalReasonUsesPersistReason() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = true,
            persisted = true,
            seenOffer = null,
            reason = "manual_recent_authority_better_auto_merged_silently"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = "SHOW_BAD"
        )

        assertFalse(decision.shouldShowOverlay)
        assertNull(decision.overlayKind)
        assertEquals("manual_recent_authority_better_auto_merged_silently", decision.finalReasonOverride)
    }

    @Test
    fun weakerAutoAfterManualAuthority_overlayIsSuppressed() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = true,
            persisted = false,
            seenOffer = null,
            reason = "manual_recent_authority_weaker_auto_ignored"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = "SHOW_BAD"
        )

        assertFalse(decision.shouldShowOverlay)
        assertNull(decision.overlayKind)
        assertEquals("manual_recent_authority_weaker_auto_ignored", decision.finalReasonOverride)
    }

    @Test
    fun rejectedPersistenceReason_suppressesOverlay() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = true,
            persisted = false,
            seenOffer = null,
            reason = "suspicious_distance_time_mismatch"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = "SHOW_WARNING"
        )

        assertFalse(decision.shouldShowOverlay)
        assertNull(decision.overlayKind)
        assertNull(decision.reShowExistingSeenOfferId)
        assertEquals("suspicious_distance_time_mismatch", decision.finalReasonOverride)
    }

    @Test
    fun savedOffer_overlayKeepsComputedKind() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = true,
            persisted = true,
            seenOffer = null,
            reason = "saved"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = "SHOW_GOOD"
        )

        assertTrue(decision.shouldShowOverlay)
        assertEquals("SHOW_GOOD", decision.overlayKind)
        assertNull(decision.finalReasonOverride)
    }

    @Test
    fun manualDuplicateWithoutExistingSeenOffer_fallsBackToSuppression() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = true,
            persisted = false,
            seenOffer = null,
            reason = "weaker_duplicate_offer_recently_saved"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = "SHOW_WARNING",
            isManual = true
        )

        assertFalse(decision.shouldShowOverlay)
        assertNull(decision.overlayKind)
        assertNull(decision.reShowExistingSeenOfferId)
        assertEquals("weaker_duplicate_offer_recently_saved", decision.finalReasonOverride)
    }

    @Test
    fun manualOwnOverlayCapture_withExistingSeenOfferReusesSavedOffer() {
        val persistenceResult = SeenOfferPersistenceResult(
            attempted = false,
            persisted = false,
            seenOffer = SeenOffer(
                id = "seen-overlay",
                observationId = "obs-overlay",
                platform = RidePlatform.UBER,
                sourceTrigger = "UBER_PRE_OFFER_VISUAL_WATCHDOG",
                status = SeenOfferStatus.SEEN,
                price = 20.22,
                valuePerKm = 1.28,
                pickupDistanceKm = 5.4,
                pickupTimeMin = 8.0,
                tripDistanceKm = 10.4,
                tripTimeMin = 19.0,
                totalDistanceKm = 15.8,
                estimatedTotalTimeMin = 27.0,
                productName = "UberX",
                originPreview = null,
                destinationPreview = null,
                rawTextPreview = null,
                score = null,
                rawTextHash = null,
                routeTextHash = null,
                createdAtMs = 1L,
                updatedAtMs = 1L
            ),
            reason = "own_overlay_capture"
        )

        val decision = SeenOfferOverlayPolicy.resolve(
            persistenceResult = persistenceResult,
            computedOverlayKind = null,
            isManual = true
        )

        assertFalse(decision.shouldShowOverlay)
        assertEquals("seen-overlay", decision.reShowExistingSeenOfferId)
        assertEquals("own_overlay_capture", decision.finalReasonOverride)
    }
}
