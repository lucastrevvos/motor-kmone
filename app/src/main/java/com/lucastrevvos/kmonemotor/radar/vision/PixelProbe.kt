package com.lucastrevvos.kmonemotor.radar.vision

import android.graphics.Bitmap
import kotlin.math.abs

class PixelProbe {
    fun probe(crop: CropCandidate, bitmap: Bitmap, triggerBonus: Int = 0): PixelProbeResult {
        if (crop.width < 40 || crop.height < 40) {
            return PixelProbeResult(
                cropId = crop.id,
                cropKind = crop.kind,
                width = crop.width,
                height = crop.height,
                darkPixelRatio = 0.0,
                lightPixelRatio = 0.0,
                contrastScore = 0.0,
                edgeDensityHint = 0.0,
                dominantVisualHint = VisualPlatformHint.SYSTEM_UI_OR_EMPTY,
                offerLikeScore = 0,
                rejectionReason = "crop_too_small"
            )
        }

        val stepX = maxOf(1, crop.width / 48)
        val stepY = maxOf(1, crop.height / 48)
        var sampled = 0
        var darkPixels = 0
        var lightPixels = 0
        var edgeCount = 0
        var luminanceSum = 0.0

        for (y in 0 until crop.height step stepY) {
            for (x in 0 until crop.width step stepX) {
                val px = bitmap.getPixel(crop.rect.left + x, crop.rect.top + y)
                val luminance = luminance(px)
                luminanceSum += luminance
                sampled += 1
                if (luminance > 210) lightPixels += 1
                if (luminance < 60) darkPixels += 1

                if (x + stepX < crop.width) {
                    val next = bitmap.getPixel(crop.rect.left + x + stepX, crop.rect.top + y)
                    if (abs(luminance - luminance(next)) > 28) {
                        edgeCount += 1
                    }
                }
                if (y + stepY < crop.height) {
                    val below = bitmap.getPixel(crop.rect.left + x, crop.rect.top + y + stepY)
                    if (abs(luminance - luminance(below)) > 28) {
                        edgeCount += 1
                    }
                }
            }
        }

        val darkRatio = darkPixels.toDouble() / sampled.coerceAtLeast(1)
        val lightRatio = lightPixels.toDouble() / sampled.coerceAtLeast(1)
        val contrast = abs(lightRatio - darkRatio)
        val edgeDensity = edgeCount.toDouble() / (sampled * 2).coerceAtLeast(1)
        val visualHint = classifyVisualHint(darkRatio, lightRatio, edgeDensity)

        var score = triggerBonus
        if (contrast >= 0.08) score += 2
        if (darkRatio in 0.15..0.85) score += 2
        if (lightRatio in 0.10..0.85) score += 2
        if (edgeDensity >= 0.12) score += 2

        val rejectionReason = when {
            darkRatio > 0.95 -> "almost_all_dark"
            lightRatio > 0.95 -> "almost_all_light"
            edgeDensity < 0.03 -> "edge_density_too_low"
            visualHint == VisualPlatformHint.MAP_OR_HOME_SCREEN -> "looks_like_map_or_home"
            visualHint == VisualPlatformHint.SYSTEM_UI_OR_EMPTY -> "looks_like_system_ui_or_empty"
            else -> null
        }

        return PixelProbeResult(
            cropId = crop.id,
            cropKind = crop.kind,
            width = crop.width,
            height = crop.height,
            darkPixelRatio = darkRatio,
            lightPixelRatio = lightRatio,
            contrastScore = contrast,
            edgeDensityHint = edgeDensity,
            dominantVisualHint = visualHint,
            offerLikeScore = if (rejectionReason == null) score else score.coerceAtMost(2),
            rejectionReason = rejectionReason
        )
    }

    private fun classifyVisualHint(
        darkRatio: Double,
        lightRatio: Double,
        edgeDensity: Double
    ): VisualPlatformHint {
        return when {
            edgeDensity < 0.03 -> VisualPlatformHint.SYSTEM_UI_OR_EMPTY
            darkRatio > 0.45 && lightRatio < 0.25 -> VisualPlatformHint.NINETY_NINE_DARK_CARD
            darkRatio in 0.15..0.65 && edgeDensity > 0.12 -> VisualPlatformHint.UBER_LIGHT_OR_DARK_CARD
            lightRatio > 0.55 && edgeDensity < 0.10 -> VisualPlatformHint.MAP_OR_HOME_SCREEN
            else -> VisualPlatformHint.UNKNOWN
        }
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r + g + b) / 3
    }
}
