package com.lucastrevvos.kmonemotor.radar.presentation

object OverlayPresentationStatePolicy {
    fun wasOverlayShown(
        overlayKind: String?,
        presentationStatus: String?,
        overlayShownByController: Boolean
    ): Boolean {
        return overlayShownByController &&
            presentationStatus != null &&
            presentationStatus != "skipped" &&
            overlayKind != null &&
            overlayKind != DecisionPresentationKind.DO_NOT_SHOW.name
    }
}
