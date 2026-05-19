package com.lucastrevvos.kmonemotor.radar.android

data class ManualAnalysisTiming(
    val clickedAtMs: Long,
    val captureStartedAtMs: Long? = null,
    val observationAtMs: Long? = null,
    val visionFinishedAtMs: Long? = null,
    val ocrFinishedAtMs: Long? = null,
    val fingerprintFinishedAtMs: Long? = null
)
