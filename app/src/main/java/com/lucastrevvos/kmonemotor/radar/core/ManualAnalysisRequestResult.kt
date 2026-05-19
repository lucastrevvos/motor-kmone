package com.lucastrevvos.kmonemotor.radar.core

sealed class ManualAnalysisRequestResult {
    data object Sent : ManualAnalysisRequestResult()

    data object ListenerMissing : ManualAnalysisRequestResult()

    data class Rejected(
        val reason: ManualRejectReason,
        val cooldownRemainingMs: Long
    ) : ManualAnalysisRequestResult()
}
