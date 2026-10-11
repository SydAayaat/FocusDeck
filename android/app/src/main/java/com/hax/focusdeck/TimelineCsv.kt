package com.hax.focusdeck

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimelineCsv {
    private val eventColumns = listOf("report_key", "session_id", "session_title", "timeline_coverage", "recorded_timezone", "seq", "kind", "at_utc", "block_index", "block_id", "block_title", "phase", "mono_ms", "boot", "active_ms", "paused_ms", "budget_ms")
    private val intervalColumns = listOf("report_key", "session_id", "session_title", "timeline_coverage", "recorded_timezone", "block_index", "block_id", "block_title", "activity_kind", "started_at_utc", "finished_at_utc", "duration_ms", "start_seq", "end_seq", "boot")
    private fun utc(ms: Long) = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC")).format(Instant.ofEpochMilli(ms))
    private fun cell(value: Any?): String {
        var text = value?.toString().orEmpty()
        if (value is String && text.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) text = "'" + text
        return "\"${text.replace("\"", "\"\"")}\""
    }
    fun encode(reports: List<Report>, intervals: Boolean = false): String = buildString {
        fun row(values: List<Any?>) { append(values.joinToString(",") { cell(it) }); append("\r\n") }
        row(if (intervals) intervalColumns else eventColumns)
        reports.distinctBy { it.key }.sortedWith(compareBy<Report> { it.startedAtMs }.thenBy { it.sessionId }).forEach { r ->
            val prefix = listOf(r.key, r.sessionId, r.title, r.timelineCoverage, r.recordedTimezone)
            if (intervals) TimelineAnalysis.intervals(r).forEach { i ->
                val b = r.blocks.getOrNull(i.blockIndex)
                row(prefix + listOf(i.blockIndex + 1, b?.id, b?.title, i.kind, utc(i.startedAtMs), utc(i.finishedAtMs), i.durationMs, i.startSeq, i.endSeq, i.boot))
            } else r.events.forEach { e ->
                val b = r.blocks.getOrNull(e.blockIndex)
                row(prefix + listOf(e.seq, e.kind, utc(e.atMs), e.blockIndex + 1, b?.id, b?.title, e.phase, e.monoMs, e.boot, e.activeMs, e.pausedMs, e.budgetMs))
            }
        }
    }
}
