package com.lucastrevvos.kmonemotor.radar.decisionoverlay

import android.content.Context

object DecisionOverlayRuntime {
    @Volatile
    private var controller: DecisionOverlayController? = null

    fun get(context: Context): DecisionOverlayController {
        return controller ?: synchronized(this) {
            controller ?: DecisionOverlayController(context.applicationContext).also { controller = it }
        }
    }
}
