package com.hax.focusdeck

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName
import java.util.UUID

/** Monotone tombstones: reconnection/import cannot resurrect deleted finalized reports. */
@Serializable data class HistoryDeletion(@SerialName("before_ms") val beforeMs: Long = 0,
    @SerialName("report_keys") val reportKeys: Set<String> = emptySet(), val revision: String = "") {
    fun validate() {
        require(beforeMs in 0..253_402_300_799_999L && reportKeys.size <= 3_000)
        require(reportKeys.all { it.isNotBlank() && it.length <= 250 })
        require(revision.length <= 100)
        require(WireJson.encodeToString(this).toByteArray(Charsets.UTF_8).size <= 786432) { "Deletion metadata exceeds sync limit" }
    }
    fun hides(report: Report) = report.key in reportKeys || beforeMs > 0 && report.finishedAtMs <= beforeMs
    fun merge(other: HistoryDeletion): HistoryDeletion {
        other.validate()
        val before = maxOf(beforeMs, other.beforeMs); val keys = reportKeys + other.reportKeys
        require(keys.size <= 3_000) { "Deletion record limit reached" }
        return if (before == beforeMs && keys == reportKeys) this else HistoryDeletion(before, keys, UUID.randomUUID().toString()).also { it.validate() }
    }
}
