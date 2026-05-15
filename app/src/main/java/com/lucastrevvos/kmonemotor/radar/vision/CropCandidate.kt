package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Rect

data class CropCandidate(
    val id: String,
    val observationId: String,
    val kind: CropKind,
    val rect: Rect,
    val width: Int,
    val height: Int,
    val reason: String
)
