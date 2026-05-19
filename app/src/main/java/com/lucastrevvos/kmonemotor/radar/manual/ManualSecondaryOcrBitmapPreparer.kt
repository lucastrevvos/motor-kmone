package com.lucastrevvos.kmonemotor.radar.manual

import android.graphics.Bitmap
import com.lucastrevvos.kmonemotor.radar.vision.CropCandidate

sealed class ManualSecondaryOcrPreparationResult<T> {
    data class Prepared<T>(
        val preparedByCropId: Map<String, PreparedManualCrop<T>>
    ) : ManualSecondaryOcrPreparationResult<T>()

    class SourceRecycled<T> : ManualSecondaryOcrPreparationResult<T>()
}

data class PreparedManualCrop<T>(
    val candidate: CropCandidate,
    val bitmap: T
)

interface ManualCropFactory<T> {
    fun isRecycled(source: T): Boolean
    fun createCrop(source: T, candidate: CropCandidate): T
    fun recycle(bitmap: T)
}

class ManualSecondaryOcrBitmapPreparer<T>(
    private val cropFactory: ManualCropFactory<T>
) {
    fun prepare(
        source: T,
        candidates: List<CropCandidate>
    ): ManualSecondaryOcrPreparationResult<T> {
        if (cropFactory.isRecycled(source)) {
            return ManualSecondaryOcrPreparationResult.SourceRecycled()
        }
        val prepared = LinkedHashMap<String, PreparedManualCrop<T>>()
        try {
            candidates.distinctBy { it.id }.forEach { candidate ->
                prepared[candidate.id] = PreparedManualCrop(
                    candidate = candidate,
                    bitmap = cropFactory.createCrop(source, candidate)
                )
            }
        } catch (throwable: Throwable) {
            prepared.values.forEach { cropFactory.recycle(it.bitmap) }
            throw throwable
        }
        return ManualSecondaryOcrPreparationResult.Prepared(prepared)
    }

    fun releaseAll(preparedByCropId: Map<String, PreparedManualCrop<T>>) {
        preparedByCropId.values.forEach { cropFactory.recycle(it.bitmap) }
    }
}

object AndroidManualCropFactory : ManualCropFactory<Bitmap> {
    override fun isRecycled(source: Bitmap): Boolean = source.isRecycled

    override fun createCrop(source: Bitmap, candidate: CropCandidate): Bitmap {
        return Bitmap.createBitmap(
            source,
            candidate.rect.left,
            candidate.rect.top,
            candidate.rect.width().coerceAtLeast(1),
            candidate.rect.height().coerceAtLeast(1)
        )
    }

    override fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}
