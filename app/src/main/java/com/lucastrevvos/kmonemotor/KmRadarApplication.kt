package com.lucastrevvos.kmonemotor

import android.app.Application
import android.os.Process
import com.lucastrevvos.kmonemotor.radar.debug.RadarLogger
import kotlin.system.exitProcess

class KmRadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RadarLogger.initialize(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RadarLogger.w(
                "KM_V2_SIGNAL",
                "KM_V2_UNCAUGHT_EXCEPTION",
                "thread" to thread.name,
                "error" to throwable.message,
                "stacktrace" to throwable.stackTraceToString()
            )
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                throwable.printStackTrace()
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
