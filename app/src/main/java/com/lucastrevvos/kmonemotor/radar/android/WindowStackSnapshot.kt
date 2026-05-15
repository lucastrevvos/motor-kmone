package com.lucastrevvos.kmonemotor.radar.android

data class WindowDescriptor(
    val packageName: String?,
    val type: Int,
    val layer: Int,
    val coverage: Double,
    val bounds: String,
    val widthPx: Int,
    val heightPx: Int,
    val isActive: Boolean,
    val isFocused: Boolean
)

data class WindowStackSnapshot(
    val topDominantWindow: WindowDescriptor?,
    val topFloatingWindow: WindowDescriptor?,
    val topSystemWindow: WindowDescriptor?,
    val timestampMs: Long
)
