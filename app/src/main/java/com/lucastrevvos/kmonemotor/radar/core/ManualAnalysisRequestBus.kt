package com.lucastrevvos.kmonemotor.radar.core

data class ManualAnalysisRequest(
    val source: String,
    val clickedAtMs: Long
)

object ManualAnalysisRequestBus {
    @Volatile
    private var listener: ((ManualAnalysisRequest) -> Unit)? = null

    fun register(listener: (ManualAnalysisRequest) -> Unit) {
        this.listener = listener
    }

    fun unregister(listener: (ManualAnalysisRequest) -> Unit) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    fun requestAnalysis(
        source: String,
        clickedAtMs: Long
    ): Boolean {
        return requestAnalysisDetailed(source, clickedAtMs) is ManualAnalysisRequestResult.Sent
    }

    fun requestAnalysisDetailed(
        source: String,
        clickedAtMs: Long
    ): ManualAnalysisRequestResult {
        val currentListener = listener ?: return ManualAnalysisRequestResult.ListenerMissing
        return when (val decision = ManualAnalysisState.tryStart(clickedAtMs)) {
            is ManualStartDecision.Accepted -> {
                currentListener.invoke(
                    ManualAnalysisRequest(
                        source = source,
                        clickedAtMs = clickedAtMs
                    )
                )
                ManualAnalysisRequestResult.Sent
            }

            is ManualStartDecision.Rejected -> {
                ManualAnalysisRequestResult.Rejected(
                    reason = decision.reason,
                    cooldownRemainingMs = decision.cooldownRemainingMs
                )
            }
        }
    }
}
