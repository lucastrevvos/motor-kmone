package com.lucastrevvos.kmonemotor.radar.debug

class LatencyTracker {
    private val startedAt = mutableMapOf<String, Long>()

    fun markStart(id: String, nowMs: Long) {
        startedAt[id] = nowMs
    }

    fun markEnd(id: String, nowMs: Long): Long? {
        val started = startedAt.remove(id) ?: return null
        return nowMs - started
    }
}
