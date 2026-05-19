package com.lucastrevvos.kmonemotor.radar.fingerprint

data class ExtractedSignal(
    val key: String,
    val raw: String,
    val confidence: Int
)
