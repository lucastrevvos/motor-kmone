package com.lucastrevvos.kmonemotor.radar.debug

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DebugEventLogStore {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var logFile: File? = null
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun initialize(context: Context) {
        val file = File(context.filesDir, "radar/debug/kmone_debug_events.log")
        file.parentFile?.mkdirs()
        logFile = file
    }

    fun append(level: String, area: String, message: String) {
        val target = logFile ?: return
        val timestamp = synchronized(timestampFormat) {
            timestampFormat.format(Date())
        }
        executor.execute {
            runCatching {
                target.parentFile?.mkdirs()
                target.appendText("$timestamp $level/$area $message\n")
            }
        }
    }

    fun currentLogFile(): File? = logFile?.takeIf { it.exists() }

    fun flushBlocking(timeoutMs: Long = 1500L) {
        runCatching {
            executor.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }
}
