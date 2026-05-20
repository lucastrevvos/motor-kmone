package com.lucastrevvos.kmonemotor.radar.debug

import android.content.Context
import android.util.Log

object RadarLogger {
    fun initialize(context: Context) {
        DebugEventLogStore.initialize(context.applicationContext)
    }

    fun d(area: String, event: String, vararg keyValues: Pair<String, Any?>) {
        write(level = "D", area = area, message = buildMessage(event, keyValues))
    }

    fun i(area: String, event: String, vararg keyValues: Pair<String, Any?>) {
        write(level = "I", area = area, message = buildMessage(event, keyValues))
    }

    fun w(area: String, event: String, vararg keyValues: Pair<String, Any?>) {
        write(level = "W", area = area, message = buildMessage(event, keyValues))
    }

    private fun write(level: String, area: String, message: String) {
        try {
            when (level) {
                "D" -> Log.d(area, message)
                "I" -> Log.i(area, message)
                else -> Log.w(area, message)
            }
        } catch (_: RuntimeException) {
            println("$level/$area: $message")
        }
        DebugEventLogStore.append(level = level, area = area, message = message)
    }

    private fun buildMessage(event: String, keyValues: Array<out Pair<String, Any?>>): String {
        val payload = keyValues.joinToString(" ") { (key, value) -> "$key=$value" }.trim()
        return if (payload.isEmpty()) event else "$event: $payload"
    }
}
