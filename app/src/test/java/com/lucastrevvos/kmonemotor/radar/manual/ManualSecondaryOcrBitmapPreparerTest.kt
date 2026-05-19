package com.lucastrevvos.kmonemotor.radar.manual

import android.graphics.Rect
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate
import com.lucastrevvos.kmonemotor.radar.vision.CropKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSecondaryOcrBitmapPreparerTest {
    private val preparer = ManualSecondaryOcrBitmapPreparer(FakeCropFactory())

    @Test
    fun prepare_createsIndependentCrops() {
        val source = FakeBitmap("source")

        val result = preparer.prepare(
            source = source,
            candidates = listOf(candidate("center", CropKind.CENTER_CARD_AREA), candidate("lower", CropKind.LOWER_HALF))
        )

        assertTrue(result is ManualSecondaryOcrPreparationResult.Prepared)
        val prepared = result as ManualSecondaryOcrPreparationResult.Prepared
        assertEquals(2, prepared.preparedByCropId.size)
        assertEquals("center", prepared.preparedByCropId["center"]?.bitmap?.id)
        assertEquals("lower", prepared.preparedByCropId["lower"]?.bitmap?.id)
    }

    @Test
    fun prepare_skipsWhenSourceAlreadyRecycled() {
        val source = FakeBitmap("source", recycled = true)

        val result = preparer.prepare(
            source = source,
            candidates = listOf(candidate("lower", CropKind.LOWER_HALF))
        )

        assertTrue(result is ManualSecondaryOcrPreparationResult.SourceRecycled)
    }

    private fun candidate(id: String, kind: CropKind): CropCandidate {
        return CropCandidate(
            id = id,
            observationId = "obs-$id",
            kind = kind,
            rect = Rect(0, 0, 100, 100),
            width = 100,
            height = 100,
            reason = "test"
        )
    }

    private data class FakeBitmap(
        val id: String,
        var recycled: Boolean = false
    )

    private class FakeCropFactory : ManualCropFactory<FakeBitmap> {
        override fun isRecycled(source: FakeBitmap): Boolean = source.recycled

        override fun createCrop(source: FakeBitmap, candidate: CropCandidate): FakeBitmap {
            return FakeBitmap(candidate.id)
        }

        override fun recycle(bitmap: FakeBitmap) {
            bitmap.recycled = true
        }
    }
}
