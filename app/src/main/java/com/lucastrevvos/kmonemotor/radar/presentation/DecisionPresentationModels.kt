package com.lucastrevvos.kmonemotor.radar.presentation

import com.lucastrevvos.kmonemotor.radar.decision.DecisionSource

enum class DecisionPresentationKind {
    SHOW_GOOD,
    SHOW_WARNING,
    SHOW_BAD,
    SHOW_BLOCKED,
    SHOW_UNKNOWN,
    DO_NOT_SHOW
}

enum class DecisionSemantic {
    POSITIVE,
    ATTENTION,
    NEGATIVE,
    NEUTRAL,
    BLOCKED
}

enum class DecisionPresentationSource {
    AUTOMATIC,
    MANUAL
}

data class DecisionPresentation(
    val observationId: String,
    val clusterId: String?,
    val kind: DecisionPresentationKind,
    val title: String,
    val shortReason: String,
    val details: List<String>,
    val primaryMetric: String?,
    val secondaryMetric: String?,
    val priceText: String?,
    val platformText: String?,
    val productText: String?,
    val semantic: DecisionSemantic,
    val source: DecisionPresentationSource,
    val expiresAtMs: Long,
    val createdAtMs: Long
)

data class DecisionPresentationProcessResult(
    val status: String,
    val reason: String,
    val presentation: DecisionPresentation? = null,
    val durationMs: Long? = null
)

internal fun DecisionSource.toPresentationSource(): DecisionPresentationSource {
    return when (this) {
        DecisionSource.AUTOMATIC -> DecisionPresentationSource.AUTOMATIC
        DecisionSource.MANUAL -> DecisionPresentationSource.MANUAL
    }
}
