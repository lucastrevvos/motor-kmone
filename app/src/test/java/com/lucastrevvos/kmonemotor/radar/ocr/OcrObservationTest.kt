package com.lucastrevvos.kmonemotor.radar.ocr

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.cycle.OfferCycleKind
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrObservationTest {
    @Test
    fun rawTextPreview_handlesEmptyText() {
        val observation = ocrObservation(rawText = "")

        assertEquals("", observation.rawTextPreview())
    }

    @Test
    fun rawTextPreview_truncatesLongText() {
        val observation = ocrObservation(rawText = "Linha 1\nLinha 2\nLinha 3".repeat(20))

        val preview = observation.rawTextPreview()

        assertTrue(preview.length <= 120)
        assertTrue(preview.contains("Linha 1"))
    }

    private fun ocrObservation(rawText: String) = OcrObservation(
        ocrObservationId = "ocr-1",
        observationId = "obs-1",
        captureRequestId = "req-1",
        triggerSource = TriggerSource.UBER_DOMINANT_OFFER_DIAGNOSTIC,
        cropId = "crop-1",
        cropKind = CropKind.CENTER_CARD_AREA,
        startedAtMs = 1L,
        finishedAtMs = 2L,
        durationMs = 1L,
        success = true,
        rawText = rawText,
        lineCount = 1,
        blockCount = 1,
        errorMessage = null,
        offerCycleKind = OfferCycleKind.NEW_OFFER_CYCLE,
        shouldPreferForOcr = true
    )
}
