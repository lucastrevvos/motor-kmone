package com.lucastrevvos.kmonemotor.radar.decisionoverlay

import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentation

data class DecisionOverlayState(
    val showing: Boolean = false,
    val currentPresentation: DecisionPresentation? = null,
    val shownAtMs: Long? = null
)
