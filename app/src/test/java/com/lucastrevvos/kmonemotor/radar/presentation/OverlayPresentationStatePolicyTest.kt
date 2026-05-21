package com.lucastrevvos.kmonemotor.radar.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPresentationStatePolicyTest {
    @Test
    fun doNotShowSkipped_isNotShown() {
        assertFalse(
            OverlayPresentationStatePolicy.wasOverlayShown(
                overlayKind = "DO_NOT_SHOW",
                presentationStatus = "skipped",
                overlayShownByController = true
            )
        )
    }

    @Test
    fun showBadBuilt_isShown() {
        assertTrue(
            OverlayPresentationStatePolicy.wasOverlayShown(
                overlayKind = "SHOW_BAD",
                presentationStatus = "built",
                overlayShownByController = true
            )
        )
    }

    @Test
    fun showWarningBuilt_isShown() {
        assertTrue(
            OverlayPresentationStatePolicy.wasOverlayShown(
                overlayKind = "SHOW_WARNING",
                presentationStatus = "built",
                overlayShownByController = true
            )
        )
    }

    @Test
    fun nullOverlaySkipped_isNotShown() {
        assertFalse(
            OverlayPresentationStatePolicy.wasOverlayShown(
                overlayKind = null,
                presentationStatus = "skipped",
                overlayShownByController = false
            )
        )
    }

    @Test
    fun duplicateSkipped_isNotShown() {
        assertFalse(
            OverlayPresentationStatePolicy.wasOverlayShown(
                overlayKind = null,
                presentationStatus = "skipped",
                overlayShownByController = false
            )
        )
    }
}
