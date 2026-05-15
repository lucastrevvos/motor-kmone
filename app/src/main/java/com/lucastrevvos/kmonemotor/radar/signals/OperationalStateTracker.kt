package com.lucastrevvos.kmonemotor.radar.signals

import com.lucastrevvos.kmonemotor.radar.android.WindowStackSnapshot
import com.lucastrevvos.kmonemotor.radar.core.FloatingWindowKind
import com.lucastrevvos.kmonemotor.radar.core.OperationalAppState
import com.lucastrevvos.kmonemotor.radar.core.RadarConfig
import com.lucastrevvos.kmonemotor.radar.debug.RadarDebugStore
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import java.util.Locale

class OperationalStateTracker {
    private var uberState = OperationalAppState.UBER_UNKNOWN
    private var ninetyNineState = OperationalAppState.NINETY_NINE_UNKNOWN

    fun update(
        snapshot: WindowStackSnapshot,
        nodeTreeSignature: NodeTreeSignature,
        floatingKind: FloatingWindowKind
    ) {
        updateUberState(snapshot, nodeTreeSignature, floatingKind)
        updateNinetyNineState(snapshot, nodeTreeSignature, floatingKind)
    }

    private fun updateUberState(
        snapshot: WindowStackSnapshot,
        nodeTreeSignature: NodeTreeSignature,
        floatingKind: FloatingWindowKind
    ) {
        val nextState = when {
            nodeTreeSignature.packageName == RadarConfig.UBER_DRIVER_PACKAGE -> {
                when (detectUberState(nodeTreeSignature)) {
                    UberStateHint.OFFLINE -> OperationalAppState.UBER_OFFLINE
                    UberStateHint.ONLINE_IDLE -> OperationalAppState.UBER_ONLINE_IDLE
                    UberStateHint.SEARCHING -> OperationalAppState.UBER_SEARCHING
                    UberStateHint.UNKNOWN -> OperationalAppState.UBER_UNKNOWN
                }
            }
            floatingKind == FloatingWindowKind.FLOATING_BUBBLE &&
                snapshot.topFloatingWindow?.packageName == RadarConfig.UBER_DRIVER_PACKAGE -> {
                OperationalAppState.UBER_BACKGROUND_ONLINE_HINT
            }
            else -> null
        }

        if (nextState != null && nextState != uberState) {
            uberState = nextState
            RadarDebugStore.updateUberOperationalState(uberState, "app=uber state=$uberState")
            RadarLogger.i(
                "KM_V2_SIGNAL",
                "KM_V2_OPERATIONAL_STATE_UPDATED",
                "app" to "uber",
                "state" to uberState
            )
        }
    }

    private fun updateNinetyNineState(
        snapshot: WindowStackSnapshot,
        nodeTreeSignature: NodeTreeSignature,
        floatingKind: FloatingWindowKind
    ) {
        val dominantPackage = snapshot.topDominantWindow?.packageName
        val floatingPackage = snapshot.topFloatingWindow?.packageName
        val nextState = when {
            dominantPackage == RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> OperationalAppState.NINETY_NINE_FOREGROUND_ACTIVE_HINT
            nodeTreeSignature.packageName == RadarConfig.NINETY_NINE_DRIVER_PACKAGE -> OperationalAppState.NINETY_NINE_FOREGROUND_ACTIVE_HINT
            floatingKind == FloatingWindowKind.FLOATING_BUBBLE &&
                (floatingPackage == RadarConfig.NINETY_NINE_DRIVER_PACKAGE ||
                    floatingPackage == RadarConfig.NINETY_NINE_DRIVER_LEGACY_PACKAGE) -> {
                OperationalAppState.NINETY_NINE_FLOATING_OFFLINE_HINT
            }
            else -> null
        }

        if (nextState != null && nextState != ninetyNineState) {
            ninetyNineState = nextState
            RadarDebugStore.updateNinetyNineOperationalState(ninetyNineState, "app=99 state=$ninetyNineState")
            RadarLogger.i(
                "KM_V2_SIGNAL",
                "KM_V2_OPERATIONAL_STATE_UPDATED",
                "app" to "99",
                "state" to ninetyNineState
            )
        }
    }

    private fun detectUberState(signature: NodeTreeSignature): UberStateHint {
        val texts = signature.knownStateTexts.map { it.lowercase(Locale.ROOT) }
        return when {
            texts.any { it.contains("offline") } -> UberStateHint.OFFLINE
            texts.any { it.contains("procurando corridas") || it.contains("buscando corridas") } -> UberStateHint.SEARCHING
            texts.any { it.contains("online") } -> UberStateHint.ONLINE_IDLE
            else -> UberStateHint.UNKNOWN
        }
    }

    private enum class UberStateHint {
        OFFLINE,
        ONLINE_IDLE,
        SEARCHING,
        UNKNOWN
    }
}
