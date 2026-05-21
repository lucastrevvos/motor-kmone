package com.lucastrevvos.kmonemotor.radar.orchestrator

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import com.lucastrevvos.kmonemotor.radar.vision.CropKind

data class AutoAttemptTrace(
    val timestampMs: Long,
    val triggerSource: TriggerSource,
    val stage: String,
    val reason: String? = null,
    val state: RadarAutoCaptureState? = null,
    val treeScore: Int? = null,
    val hasOfferPriceText: Boolean? = null,
    val hasOperationalMoneyText: Boolean? = null,
    val hasUberProductText: Boolean? = null,
    val hasRoutePairText: Boolean? = null,
    val hasSearchingText: Boolean? = null,
    val isOperationalScreen: Boolean? = null,
    val operationalReason: String? = null,
    val selectedCropKind: CropKind? = null,
    val fingerprintKind: String? = null,
    val platform: String? = null,
    val persistReason: String? = null,
    val knownStateTexts: List<String> = emptyList()
)

data class AutoMissDiagnosis(
    val lookbackMs: Long,
    val autoAttemptCount: Int,
    val lastAutoStage: String?,
    val lastAutoTriggerSource: TriggerSource?,
    val lastAutoState: RadarAutoCaptureState?,
    val lastAutoReason: String?,
    val lastAutoFingerprintKind: String?,
    val lastAutoPersistReason: String?,
    val lastFailureStage: String?,
    val lastFailureReason: String?,
    val lastFailureAgeMs: Long?,
    val timeSinceLastAutoTraceMs: Long?,
    val timeSinceLastRejectedPreOfferMs: Long?,
    val timeSinceLastCaptureApprovedMs: Long?,
    val manualSelectedCropKind: String?,
    val manualTriggerSource: String?,
    val lastRejectedReason: String?,
    val lastRejectedKnownStateTexts: String?,
    val likelyCause: String
)

class AutoMissDiagnostics(
    private val lookbackMs: Long = 15_000L,
    private val retentionMs: Long = 30_000L,
    private val maxTraces: Int = 200
) {
    private val traces = mutableListOf<AutoAttemptTrace>()

    @Synchronized
    fun recordAutoTrace(trace: AutoAttemptTrace) {
        prune(nowMs = trace.timestampMs)
        traces += trace
        if (traces.size > maxTraces) {
            traces.removeAt(0)
        }
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_ATTEMPT_TRACE",
            "stage" to trace.stage,
            "triggerSource" to trace.triggerSource,
            "state" to trace.state,
            "reason" to trace.reason,
            "treeScore" to trace.treeScore,
            "hasOfferPriceText" to trace.hasOfferPriceText,
            "hasOperationalMoneyText" to trace.hasOperationalMoneyText,
            "hasUberProductText" to trace.hasUberProductText,
            "hasRoutePairText" to trace.hasRoutePairText,
            "hasSearchingText" to trace.hasSearchingText,
            "isOperationalScreen" to trace.isOperationalScreen,
            "operationalReason" to trace.operationalReason,
            "selectedCropKind" to trace.selectedCropKind,
            "fingerprintKind" to trace.fingerprintKind,
            "platform" to trace.platform,
            "persistReason" to trace.persistReason,
            "knownStateTexts" to trace.knownStateTexts.joinToString(",")
        )
    }

    @Synchronized
    fun reportManualOracleSuccess(
        manualObservationId: String,
        manualPlatform: String?,
        manualPrice: Double?,
        manualDistances: String?,
        manualTimes: String?,
        manualSelectedCropKind: String?,
        manualTriggerSource: String?,
        timestampMs: Long
    ): AutoMissDiagnosis {
        prune(timestampMs)
        val recent = traces.filter { timestampMs - it.timestampMs in 0..lookbackMs }
        val diagnosis = buildDiagnosis(
            manualPlatform = manualPlatform,
            manualSelectedCropKind = manualSelectedCropKind,
            manualTriggerSource = manualTriggerSource,
            timestampMs = timestampMs,
            recent = recent
        )
        RadarLogger.i(
            "KM_V2_AUTO",
            "KM_V2_AUTO_MISS_DIAGNOSIS",
            "manualObservationId" to manualObservationId,
            "manualPlatform" to manualPlatform,
            "manualPrice" to manualPrice,
            "manualDistances" to manualDistances,
            "manualTimes" to manualTimes,
            "manualSelectedCropKind" to diagnosis.manualSelectedCropKind,
            "manualTriggerSource" to diagnosis.manualTriggerSource,
            "lookbackMs" to diagnosis.lookbackMs,
            "autoAttemptCount" to diagnosis.autoAttemptCount,
            "lastAutoStage" to diagnosis.lastAutoStage,
            "lastAutoTriggerSource" to diagnosis.lastAutoTriggerSource,
            "lastAutoState" to diagnosis.lastAutoState,
            "lastAutoReason" to diagnosis.lastAutoReason,
            "lastAutoFingerprintKind" to diagnosis.lastAutoFingerprintKind,
            "lastAutoPersistReason" to diagnosis.lastAutoPersistReason,
            "lastFailureStage" to diagnosis.lastFailureStage,
            "lastFailureReason" to diagnosis.lastFailureReason,
            "lastFailureAgeMs" to diagnosis.lastFailureAgeMs,
            "timeSinceLastAutoTraceMs" to diagnosis.timeSinceLastAutoTraceMs,
            "timeSinceLastRejectedPreOfferMs" to diagnosis.timeSinceLastRejectedPreOfferMs,
            "timeSinceLastCaptureApprovedMs" to diagnosis.timeSinceLastCaptureApprovedMs,
            "lastRejectedReason" to diagnosis.lastRejectedReason,
            "lastRejectedKnownStateTexts" to diagnosis.lastRejectedKnownStateTexts,
            "likelyCause" to diagnosis.likelyCause
        )
        return diagnosis
    }

    @Synchronized
    fun snapshot(): List<AutoAttemptTrace> = traces.toList()

    private fun buildDiagnosis(
        manualPlatform: String?,
        manualSelectedCropKind: String?,
        manualTriggerSource: String?,
        timestampMs: Long,
        recent: List<AutoAttemptTrace>
    ): AutoMissDiagnosis {
        val last = recent.maxByOrNull { it.timestampMs }
        val lastRejectedPreOffer = recent.filter { it.stage == "trigger_rejected_pre_offer" }.maxByOrNull { it.timestampMs }
        val lastCaptureApproved = recent.filter { it.stage == "capture_approved" }.maxByOrNull { it.timestampMs }
        val lastFailure = recent.filter {
            it.stage in setOf(
                "trigger_rejected_pre_offer",
                "offer_card_signal_rejected",
                "stabilization_cancelled",
                "blocked_by_state",
                "recovery_suppressed",
                "fingerprint_result",
                "pipeline_final"
            )
        }.maxByOrNull { it.timestampMs }
        val timeSinceLastAutoTraceMs = last?.let { timestampMs - it.timestampMs }
        val timeSinceLastRejectedPreOfferMs = lastRejectedPreOffer?.let { timestampMs - it.timestampMs }
        val timeSinceLastCaptureApprovedMs = lastCaptureApproved?.let { timestampMs - it.timestampMs }
        val likelyCause = when {
            recent.isEmpty() -> "no_auto_attempt_before_manual"
            timeSinceLastAutoTraceMs != null &&
                timeSinceLastAutoTraceMs > 3_000L &&
                last?.state == RadarAutoCaptureState.PRE_OFFER_MAP_STATE &&
                lastRejectedPreOffer != null &&
                recent.none { it.timestampMs > lastRejectedPreOffer.timestampMs && it.stage in setOf("stabilization_started", "capture_approved") } ->
                "no_card_signal_after_pre_offer_state"
            recent.any { it.persistReason?.isDuplicateOrMergeReason() == true } -> "duplicate_or_merge_suppressed"
            recent.any {
                manualPlatform != null &&
                    it.platform != null &&
                    it.platform != manualPlatform &&
                    (it.stage == "fingerprint_result" || it.stage == "pipeline_final")
            } -> "platform_unknown_or_wrong"
            recent.any { it.stage == "recovery_suppressed" } -> "recovery_suppressed"
            recent.any { it.stage == "blocked_by_state" } -> "capture_blocked_by_state"
            recent.any { it.stage == "stabilization_cancelled" } -> "stabilization_cancelled"
            recent.any {
                (it.stage == "offer_card_signal_rejected" || it.stage == "trigger_rejected_pre_offer") &&
                    (it.isOperationalScreen == true || it.reason.isOperationalRejectReason())
            } -> "rejected_as_operational_screen"
            recent.any {
                (it.stage == "fingerprint_result" && (it.fingerprintKind == "UNKNOWN" || it.fingerprintKind == "NON_OFFER")) ||
                    (it.stage == "pipeline_final" && (
                        it.persistReason == "fingerprint_not_offer_like" ||
                            it.fingerprintKind == "UNKNOWN" ||
                            it.fingerprintKind == "NON_OFFER"
                        ))
            } -> "captured_but_ocr_unknown"
            else -> "auto_attempt_without_clear_failure"
        }
        return AutoMissDiagnosis(
            lookbackMs = lookbackMs,
            autoAttemptCount = recent.size,
            lastAutoStage = last?.stage,
            lastAutoTriggerSource = last?.triggerSource,
            lastAutoState = last?.state,
            lastAutoReason = last?.reason,
            lastAutoFingerprintKind = last?.fingerprintKind,
            lastAutoPersistReason = last?.persistReason,
            lastFailureStage = lastFailure?.stage,
            lastFailureReason = lastFailure?.reason ?: lastFailure?.persistReason,
            lastFailureAgeMs = lastFailure?.let { timestampMs - it.timestampMs },
            timeSinceLastAutoTraceMs = timeSinceLastAutoTraceMs,
            timeSinceLastRejectedPreOfferMs = timeSinceLastRejectedPreOfferMs,
            timeSinceLastCaptureApprovedMs = timeSinceLastCaptureApprovedMs,
            manualSelectedCropKind = manualSelectedCropKind,
            manualTriggerSource = manualTriggerSource,
            lastRejectedReason = lastRejectedPreOffer?.reason,
            lastRejectedKnownStateTexts = lastRejectedPreOffer?.knownStateTexts?.joinToString(","),
            likelyCause = likelyCause
        )
    }

    private fun prune(nowMs: Long) {
        traces.removeAll { nowMs - it.timestampMs > retentionMs }
        while (traces.size > maxTraces) {
            traces.removeAt(0)
        }
    }

    private fun String?.isOperationalRejectReason(): Boolean {
        val normalized = this?.trim()?.lowercase() ?: return false
        return normalized == "operational_earnings_money_without_offer_evidence" ||
            normalized == "searching_text_without_price_product_or_route" ||
            normalized == "map_eta_range_without_offer_evidence" ||
            normalized == "button_like_without_price_product_or_route" ||
            normalized.contains("operational") ||
            normalized.contains("map_state") ||
            normalized.contains("searching")
    }

    private fun String.isDuplicateOrMergeReason(): Boolean {
        val normalized = trim().lowercase()
        return normalized.contains("duplicate") || normalized.contains("merged")
    }
}
