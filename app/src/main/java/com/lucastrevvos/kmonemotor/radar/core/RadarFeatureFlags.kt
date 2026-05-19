package com.lucastrevvos.kmonemotor.radar.core

object RadarFeatureFlags {
    const val ENABLE_DEBUG_SCREENSHOT_SAVE = false
    const val ENABLE_DEBUG_CROP_SAVE = true
    const val ENABLE_REGIONAL_OCR = true
    const val ENABLE_OCR_ON_FOLLOWUP = true
    const val ENABLE_OCR_ON_UNKNOWN = true
    const val ENABLE_OCR_ON_POST_TRANSITION = false
    const val ENABLE_DEBUG_OCR_SAVE = true
    const val ENABLE_DEBUG_FINGERPRINT_SAVE = true
    const val ENABLE_DEBUG_DEDUPE_SAVE = true
    const val ENABLE_DEBUG_PARSER_SAVE = true
    const val ENABLE_DEBUG_DECISION_SAVE = true
    const val MANUAL_ANALYSIS_COOLDOWN_MS = 2000L
    const val MANUAL_ANALYSIS_MIN_SCREENSHOT_INTERVAL_MS = 1500L
}
