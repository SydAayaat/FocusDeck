package com.hax.focusdeck

/** Scheduling affects freshness and alert latency, never elapsed-time accounting. */
object PowerPolicy {
    fun tickMs(phase: String, visible: Boolean): Long? = when (phase) {
        "active" -> if (visible) 1_000L else 30_000L
        "paused" -> 60_000L
        else -> null // Idle, ready and finished screens need no clock polling.
    }

    fun checkpointMs(phase: String, visible: Boolean): Long? = when (phase) {
        "active" -> if (visible) 10_000L else 30_000L
        "paused" -> 60_000L
        else -> null
    }

    fun syncMs(phase: String, visible: Boolean, unsentReports: Boolean): Long =
        if (unsentReports || (phase == "active" && visible)) 15_000L else 60_000L

    fun retryCapSeconds(visible: Boolean): Long = if (visible) 30L else 120L
}
