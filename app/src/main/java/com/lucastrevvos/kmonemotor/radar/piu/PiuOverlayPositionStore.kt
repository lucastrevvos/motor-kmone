package com.lucastrevvos.kmonemotor.radar.piu

import android.content.Context

interface PiuOverlayPositionStore {
    fun restoreX(defaultValue: Int): Int
    fun saveX(value: Int)
}

class SharedPrefsPiuOverlayPositionStore(
    context: Context
) : PiuOverlayPositionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun restoreX(defaultValue: Int): Int {
        return prefs.getInt(KEY_PIU_X, defaultValue)
    }

    override fun saveX(value: Int) {
        prefs.edit().putInt(KEY_PIU_X, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "km_radar_piu_overlay"
        const val KEY_PIU_X = "piu_x"
    }
}
