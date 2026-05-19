package com.lucastrevvos.kmonemotor.radar.platform

import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint

data class PlatformInferenceInput(
    val rawText: String,
    val normalizedText: String,
    val triggerSource: com.lucastrevvos.kmonemotor.radar.core.TriggerSource?,
    val dominantPackage: String? = null,
    val nodeTreePackage: String? = null,
    val floatingPackage: String? = null,
    val currentPlatformHint: PlatformTextHint? = null
)

data class PlatformInferenceResult(
    val platform: PlatformTextHint,
    val confidence: Double,
    val reason: String,
    val strongTextSignals: List<String>,
    val weakTextSignals: List<String>,
    val contextSignals: List<String>,
    val conflict: Boolean
)
