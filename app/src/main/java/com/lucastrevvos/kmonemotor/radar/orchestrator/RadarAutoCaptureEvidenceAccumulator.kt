package com.lucastrevvos.kmonemotor.radar.orchestrator

class RadarAutoCaptureEvidenceAccumulator {
    private val evidences = mutableListOf<RadarAutoCaptureEvidence>()

    fun add(evidence: RadarAutoCaptureEvidence) {
        evidences += evidence
    }

    fun latest(): RadarAutoCaptureEvidence? = evidences.lastOrNull()

    fun recentStrongOfferEvidence(windowMs: Long = 1_000L): Boolean {
        val latestTimestamp = evidences.lastOrNull()?.timestampMs ?: return false
        return evidences.asReversed().any {
            latestTimestamp - it.timestampMs <= windowMs && it.isStrongOfferEvidence()
        }
    }

    fun recentSearchingEvidence(windowMs: Long = 1_000L): Boolean {
        val latestTimestamp = evidences.lastOrNull()?.timestampMs ?: return false
        return evidences.asReversed().any {
            latestTimestamp - it.timestampMs <= windowMs && it.isSearchingEvidence()
        }
    }

    fun clear() {
        evidences.clear()
    }
}
