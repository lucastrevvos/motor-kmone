package com.lucastrevvos.kmonemotor.radar.core

import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class ManualRejectReason {
    THROTTLED,
    ALREADY_RUNNING
}

sealed class ManualStartDecision {
    data class Accepted(
        val acceptedAtMs: Long
    ) : ManualStartDecision()

    data class Rejected(
        val reason: ManualRejectReason,
        val cooldownRemainingMs: Long
    ) : ManualStartDecision()
}

data class ManualAnalysisSnapshot(
    val running: Boolean = false,
    val lastAcceptedAtMs: Long = 0L,
    val cooldownRemainingMs: Long = 0L,
    val lastRejectedReason: ManualRejectReason? = null
)

object ManualAnalysisState {
    private val running = AtomicBoolean(false)
    private val lastAcceptedAtMs = AtomicLong(0L)
    @Volatile
    private var lastRejectedReason: ManualRejectReason? = null
    private val listeners = CopyOnWriteArraySet<(ManualAnalysisSnapshot) -> Unit>()

    fun tryStart(nowMs: Long): ManualStartDecision = synchronized(this) {
        if (running.get()) {
            lastRejectedReason = ManualRejectReason.ALREADY_RUNNING
            val snapshot = snapshotLocked(nowMs)
            notifyListeners(snapshot)
            return ManualStartDecision.Rejected(
                reason = ManualRejectReason.ALREADY_RUNNING,
                cooldownRemainingMs = snapshot.cooldownRemainingMs
            )
        }
        val cooldownRemainingMs = cooldownRemainingLocked(nowMs)
        if (cooldownRemainingMs > 0L) {
            lastRejectedReason = ManualRejectReason.THROTTLED
            val snapshot = snapshotLocked(nowMs)
            notifyListeners(snapshot)
            return ManualStartDecision.Rejected(
                reason = ManualRejectReason.THROTTLED,
                cooldownRemainingMs = cooldownRemainingMs
            )
        }
        running.set(true)
        lastAcceptedAtMs.set(nowMs)
        lastRejectedReason = null
        notifyListeners(snapshotLocked(nowMs))
        ManualStartDecision.Accepted(acceptedAtMs = nowMs)
    }

    fun finish(nowMs: Long) {
        synchronized(this) {
            running.set(false)
            notifyListeners(snapshotLocked(nowMs))
        }
    }

    fun isRunning(): Boolean = running.get()

    fun snapshot(nowMs: Long): ManualAnalysisSnapshot = synchronized(this) {
        snapshotLocked(nowMs)
    }

    fun registerListener(listener: (ManualAnalysisSnapshot) -> Unit) {
        listeners += listener
    }

    fun unregisterListener(listener: (ManualAnalysisSnapshot) -> Unit) {
        listeners -= listener
    }

    fun resetForTests() {
        synchronized(this) {
            running.set(false)
            lastAcceptedAtMs.set(0L)
            lastRejectedReason = null
            listeners.clear()
            notifyListeners(snapshotLocked(0L))
        }
    }

    private fun cooldownRemainingLocked(nowMs: Long): Long {
        val acceptedAtMs = lastAcceptedAtMs.get()
        if (acceptedAtMs <= 0L) {
            return 0L
        }
        val elapsedMs = nowMs - acceptedAtMs
        val requiredCooldownMs = maxOf(
            RadarFeatureFlags.MANUAL_ANALYSIS_COOLDOWN_MS,
            RadarFeatureFlags.MANUAL_ANALYSIS_MIN_SCREENSHOT_INTERVAL_MS
        )
        return (requiredCooldownMs - elapsedMs).coerceAtLeast(0L)
    }

    private fun snapshotLocked(nowMs: Long): ManualAnalysisSnapshot {
        return ManualAnalysisSnapshot(
            running = running.get(),
            lastAcceptedAtMs = lastAcceptedAtMs.get(),
            cooldownRemainingMs = cooldownRemainingLocked(nowMs),
            lastRejectedReason = lastRejectedReason
        )
    }

    private fun notifyListeners(snapshot: ManualAnalysisSnapshot) {
        listeners.forEach { it(snapshot) }
    }
}
