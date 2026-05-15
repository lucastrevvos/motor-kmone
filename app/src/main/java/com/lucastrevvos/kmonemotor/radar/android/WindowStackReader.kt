package com.lucastrevvos.kmonemotor.radar.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import com.lucastrevvos.kmonemotor.radar.core.RadarConfig
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import kotlin.math.max

class WindowStackReader(
    private val config: RadarConfig = RadarConfig.Default
) {
    fun read(
        windows: List<AccessibilityWindowInfo>,
        screenBounds: Rect,
        timestampMs: Long
    ): WindowStackSnapshot {
        val descriptors = windows.map { window -> toDescriptor(window, screenBounds) }.sortedByDescending { it.layer }
        val dominant = descriptors
            .filter { it.coverage >= config.dominantCoverageThreshold }
            .sortedWith(
                compareByDescending<WindowDescriptor> { it.packageName in config.monitoredPackages }
                    .thenByDescending { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .thenByDescending { it.isActive || it.isFocused }
                    .thenByDescending { it.coverage }
                    .thenByDescending { it.layer }
            )
            .firstOrNull()
        val floating = descriptors
            .filter { it.coverage < config.floatingCoverageThreshold }
            .sortedWith(
                compareByDescending<WindowDescriptor> { it.packageName in config.monitoredPackages }
                    .thenByDescending { it.layer }
                    .thenByDescending { it.coverage }
            )
            .firstOrNull()
        val system = descriptors
            .filter { it.packageName == null || it.type != AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }

        dominant?.let {
            RadarLogger.d(
                "KM_V2_WINDOW_STACK",
                "KM_V2_WINDOW_STACK_TOP_DOMINANT",
                "package" to it.packageName,
                "coverage" to it.coverage,
                "layer" to it.layer,
                "bounds" to it.bounds
            )
        }
        floating?.let {
            RadarLogger.d(
                "KM_V2_WINDOW_STACK",
                "KM_V2_WINDOW_STACK_TOP_FLOATING",
                "package" to it.packageName,
                "coverage" to it.coverage,
                "layer" to it.layer,
                "bounds" to it.bounds
            )
        }
        if (dominant != null && floating != null) {
            RadarLogger.d(
                "KM_V2_WINDOW_STACK",
                "KM_V2_WINDOW_STACK_FLOATING_OVER_DOMINANT",
                "dominantPackage" to dominant.packageName,
                "floatingPackage" to floating.packageName,
                "floatingCoverage" to floating.coverage,
                "dominantCoverage" to dominant.coverage
            )
        }

        return WindowStackSnapshot(
            topDominantWindow = dominant,
            topFloatingWindow = floating,
            topSystemWindow = system,
            timestampMs = timestampMs
        )
    }

    private fun toDescriptor(window: AccessibilityWindowInfo, screenBounds: Rect): WindowDescriptor {
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        val coverage = coverage(bounds, screenBounds)
        return WindowDescriptor(
            packageName = window.root?.packageName?.toString(),
            type = window.type,
            layer = window.layer,
            coverage = coverage,
            bounds = bounds.flattenToString(),
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            isActive = window.isActive,
            isFocused = window.isFocused
        )
    }

    private fun coverage(windowBounds: Rect, screenBounds: Rect): Double {
        val safeWidth = max(screenBounds.width(), 1)
        val safeHeight = max(screenBounds.height(), 1)
        val totalArea = safeWidth.toLong() * safeHeight.toLong()
        val width = max(windowBounds.width(), 0)
        val height = max(windowBounds.height(), 0)
        return (width.toLong() * height.toLong()).toDouble() / totalArea.toDouble()
    }
}
