package com.lucastrevvos.kmonemotor.radar.recovery

import com.lucastrevvos.kmonemotor.radar.core.TriggerSource
import com.lucastrevvos.kmonemotor.radar.fingerprint.OfferTextFingerprintKind
import com.lucastrevvos.kmonemotor.radar.fingerprint.PlatformTextHint
import com.lucastrevvos.kmonemotor.radar.vision.CropKind

data class AutomaticCaptureBurstInput(
    val observationId: String,
    val triggerSource: TriggerSource,
    val cropKind: CropKind?,
    val rawOcrText: String,
    val fingerprintKind: OfferTextFingerprintKind,
    val platformHint: PlatformTextHint?,
    val createdAtMs: Long,
    val captureStartedAtMs: Long?,
    val attempt: Int,
    val obstructionSuspected: Boolean = false,
    val obstructionOverlapsCriticalArea: Boolean = false
)

data class AutomaticCaptureBurstDecision(
    val shouldScheduleBurst: Boolean,
    val delayMs: Long,
    val preferredCropOrder: List<CropKind>,
    val reason: String
)
