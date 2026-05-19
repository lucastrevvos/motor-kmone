package com.lucastrevvos.kmonemotor.radar.ocr

import android.graphics.Bitmap

interface RegionalOcrEngine {
    fun recognize(
        candidate: OcrCandidate,
        bitmap: Bitmap,
        callback: (OcrObservation) -> Unit
    )
}
