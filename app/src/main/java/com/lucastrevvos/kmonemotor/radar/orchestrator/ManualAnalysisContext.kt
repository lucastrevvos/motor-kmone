package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.PlatformHint

data class ManualAnalysisContext(
    val requestedAtMs: Long,
    val analysisEpoch: Long,
    val source: String,
    val dominantPackage: String?,
    val floatingPackage: String?,
    val floatingBounds: String?,
    val floatingKind: FloatingWindowKind,
    val platformHint: PlatformHint?
)
