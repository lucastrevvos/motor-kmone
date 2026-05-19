package com.lucastrevvos.kmonemotor.radar.piu

import android.content.Context

object PiuOverlayRuntime {
    @Volatile
    private var controller: PiuOverlayController? = null

    fun get(context: Context): PiuOverlayController {
        return controller ?: synchronized(this) {
            controller ?: PiuOverlayController(context.applicationContext).also { controller = it }
        }
    }
}
