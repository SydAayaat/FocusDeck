package com.hax.focusdeck

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

enum class HistoryPeriod(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

data class HistoryWindow(val start: LocalDate?, val end: LocalDate?, val label: String)
data class HistoryBucket(val label: String, val focusedMs: Long)
data class HistorySummary(
    val reports: List<Report>, val focusedMs: Long, val breakMs: Long, val pausedMs: Long,
    val overtimeMs: Long, val plannedMs: Long?, val doneBlocks: Int, val buckets: List<HistoryBucket>
) {
    val activeMs get() = focusedMs + breakMs
}

object StudyHistory {
    fun window(period: HistoryPeriod, anchor: LocalDate): HistoryWindow {
        val start = when (period) {
            HistoryPeriod.DAY -> anchor
            HistoryPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            HistoryPeriod.MONTH -> anchor.withDayOfMonth(1)
            HistoryPeriod.YEAR -> anchor.withDayOfYear(1)
            HistoryPeriod.ALL -> return HistoryWindow(null, null, "All saved history")
        }
        val end = when (period) {
            HistoryPeriod.DAY -> start.plusDays(1)
            HistoryPeriod.WEEK -> start.plusWeeks(1)
            HistoryPeriod.MONTH -> start.plusMonths(1)
            HistoryPeriod.YEAR -> start.plusYears(1)
            HistoryPeriod.ALL -> error("Handled above")
        }
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
        val label = if (period == HistoryPeriod.DAY) start.format(fmt)
            else "${start.format(fmt)} – ${end.minusDays(1).format(fmt)}"
        return HistoryWindow(start, end, label)
    }

    fun shift(period: HistoryPeriod, anchor: LocalDate, amount: Long): LocalDate = when (period) {
        HistoryPeriod.DAY -> anchor.plusDays(amount)
        HistoryPeriod.WEEK -> anchor.plusWeeks(amount)
        HistoryPeriod.MONTH -> anchor.withDayOfMonth(1).plusMonths(amount)
        HistoryPeriod.YEAR -> anchor.withDayOfYear(1).plusYears(amount)
        HistoryPeriod.ALL -> anchor
    }

    fun summarize(source: List<Report>, period: HistoryPeriod, anchor: LocalDate, zone: ZoneId): HistorySummary {
        val window = window(period, anchor)
        val startMs = window.start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val endMs = window.end?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val reports = source.distinctBy { it.key }.filter {
            (startMs == null || it.finishedAtMs >= startMs) && (endMs == null || it.finishedAtMs < endMs)
        }.sortedByDescending { it.finishedAtMs }
        val years = reports.map { Instant.ofEpochMilli(it.finishedAtMs).atZone(zone).year }.distinct().sorted().takeLast(12)
        val labels = when (period) {
            HistoryPeriod.DAY -> (0..23).map { "%02d".format(it) }
            HistoryPeriod.WEEK -> (0..6).map { window.start!!.plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("EEE")) }
            HistoryPeriod.MONTH -> (1..window.start!!.lengthOfMonth()).map { it.toString() }
            HistoryPeriod.YEAR -> (1..12).map { window.start!!.withMonth(it).format(DateTimeFormatter.ofPattern("MMM")) }
            HistoryPeriod.ALL -> years.map { it.toString() }
        }
        val values = LongArray(labels.size)
        reports.forEach { report ->
            val finished = Instant.ofEpochMilli(report.finishedAtMs).atZone(zone)
            val index = when (period) {
                HistoryPeriod.DAY -> finished.hour
                HistoryPeriod.WEEK -> finished.dayOfWeek.value - 1
                HistoryPeriod.MONTH -> finished.dayOfMonth - 1
                HistoryPeriod.YEAR -> finished.monthValue - 1
                HistoryPeriod.ALL -> years.indexOf(finished.year)
            }
            if (index in values.indices) values[index] += report.focusedMs
        }
        return HistorySummary(reports, reports.sumOf { it.focusedMs }, reports.sumOf { it.breakMs },
            reports.sumOf { it.pausedMs }, reports.sumOf { it.overtimeMs }, if (reports.any { it.plannedMs == null }) null else reports.sumOf { it.plannedMs ?: 0 },
            reports.sumOf { r -> r.blocks.count { it.outcome == "done" } },
            labels.mapIndexed { index, label -> HistoryBucket(label, values[index]) })
    }
}

object HistoryCsv {
    private val sessionColumns = listOf("report_key", "session_id", "session_title", "status", "started_at_utc", "finished_at_utc",
        "planned_ms", "active_ms", "focused_ms", "break_ms", "overtime_ms", "paused_ms", "wall_ms",
        "done_blocks", "stopped_blocks", "timed_out_blocks", "planned_block_count", "marked_done_fraction", "recorded_timezone") + behaviorColumns() + RewardCsv.columns + RewardCsv.sessionColumns + listOf("report_version", "score_scope", "timed_planned_ms", "planned_countdown_focus_blocks", "planned_stopwatch_focus_blocks", "skipped_blocks")
    private val blockColumns = listOf("report_key", "session_id", "session_title", "session_status", "session_started_at_utc",
        "session_finished_at_utc", "row_kind", "block_index", "block_id", "block_title", "block_type",
        "started_at_utc", "finished_at_utc", "planned_ms", "budget_ms", "active_ms", "overtime_ms", "paused_ms", "outcome") + behaviorColumns() + RewardCsv.columns + listOf("report_version", "timer_mode", "block_elapsed_wall_ms", "block_focus_ratio")

    private fun behaviorColumns() = listOf("timeline_coverage", "pause_count", "extension_count", "longest_active_ms", "longest_pause_ms", "average_pause_ms", "actual_to_planned_ratio", "paused_share")
    private fun behaviorValues(report: Report, index: Int? = null): List<Any?> {
        val m = TimelineAnalysis.metrics(report, index)
        val b = index?.let { report.blocks[it] }
        val active = b?.activeMs ?: report.activeMs
        val paused = b?.pausedMs ?: report.pausedMs
        return listOf(report.timelineCoverage, m.pauseCount, m.extensionCount, m.longestActiveMs, m.longestPauseMs, m.averagePauseMs,
            TimelineAnalysis.ratio(active, b?.plannedMs ?: report.plannedMs), TimelineAnalysis.ratio(paused, active + paused))
    }

    private fun utc(ms: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneId.of("UTC")).format(Instant.ofEpochMilli(ms))

    private fun cell(value: Any?): String {
        var text = value?.toString().orEmpty()
        if (value is String && text.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) text = "'" + text
        return "\"${text.replace("\"", "\"\"")}\""
    }

    fun encode(reports: List<Report>, sessions: Boolean = false): String = buildString {
        fun row(values: List<Any?>) { append(values.joinToString(",") { cell(it) }); append("\r\n") }
        row(if (sessions) sessionColumns else blockColumns)
        reports.distinctBy { it.key }.sortedWith(compareBy<Report> { it.startedAtMs }.thenBy { it.sessionId }).forEach { r ->
            val prefix: List<Any?> = listOf(r.key, r.sessionId, r.title, r.status, utc(r.startedAtMs), utc(r.finishedAtMs))
            if (sessions) row(prefix + listOf(r.plannedMs, r.activeMs, r.focusedMs, r.breakMs, r.overtimeMs, r.pausedMs,
                r.wallMs, r.blocks.count { it.outcome == "done" }, r.blocks.count { it.outcome == "stopped" }, r.blocks.count { it.outcome == "timed_out" },
                r.plannedBlockCount, r.plannedBlockCount?.let { TimelineAnalysis.ratio(r.blocks.count { b -> b.outcome == "done" }.toLong(), it.toLong()) }, r.recordedTimezone) + behaviorValues(r) + RewardCsv.session(r.competition) + listOf(r.reportVersion, r.competition?.scoreScope ?: "all_focus", r.timedPlannedMs ?: r.plannedMs, r.plannedCountdownFocusBlocks, r.plannedStopwatchFocusBlocks, r.blocks.count { it.outcome == "skipped" }))
            else {
                if (r.blocks.isEmpty()) row(prefix + listOf("session_without_blocks") + List(12) { "" } + behaviorValues(r) + RewardCsv.block(null) + listOf(r.reportVersion, null, null, null))
                r.blocks.forEachIndexed { index, b ->
                    row(prefix + listOf("block", index + 1, b.id, b.title, b.type, utc(b.startedAtMs), utc(b.finishedAtMs),
                        b.plannedMs, b.budgetMs, b.activeMs, b.overtimeMs, b.pausedMs, b.outcome) + behaviorValues(r, index) + RewardCsv.block(b.performance) + listOf(r.reportVersion, b.timerMode, b.wallMs, b.focusRatio))
                }
            }
        }
    }
}

fun Report.validateHistory() {
    require(sessionId.isNotBlank() && sessionId.length <= 200 && title.isNotBlank() && title.length <= 200) { "Invalid report identity" }
    require(status in listOf("complete", "stopped") && blocks.size <= 100) { "Invalid report status/blocks" }
    require(reportVersion in 1..2) { "Unsupported report_version" }
    require(plannedMs == null && reportVersion == 2 || plannedMs != null && plannedMs in 0..1_000_000_000_000L) { "Invalid planned duration" }
    require(listOf(activeMs, focusedMs, breakMs, pausedMs, wallMs, overtimeMs).all { it in 0..1_000_000_000_000L }) { "Invalid report duration" }
    require(startedAtMs in 0..253_402_300_799_999L && finishedAtMs in 0..253_402_300_799_999L) { "Invalid report timestamp" }
    blocks.forEach { b ->
        require(b.id.isNotBlank() && b.id.length <= 200 && b.title.isNotBlank() && b.title.length <= 200) { "Invalid block identity" }
        require(b.type in listOf("focus", "break") && b.outcome in listOf("done", "stopped", "timed_out", "skipped")) { "Invalid block outcome/type" }
        require(listOf(b.activeMs, b.pausedMs).all { it in 0..1_000_000_000_000L }) { "Invalid block duration" }
        require(b.startedAtMs in 0..253_402_300_799_999L && b.finishedAtMs in 0..253_402_300_799_999L) { "Invalid block timestamp" }
        require(b.timerMode in setOf("countdown", "stopwatch")) { "Invalid report timer_mode" }
        if (b.timerMode == "stopwatch") require(reportVersion == 2 && b.type == "focus" && b.plannedMs == null && b.budgetMs == null && b.overtimeMs == null) { "Stopwatch has no planned/budget/overtime duration" }
        else require(b.plannedMs != null && b.budgetMs != null && b.plannedMs in 0..1_000_000_000_000L && b.budgetMs in 0..1_000_000_000_000L) { "Invalid countdown durations" }
        if (reportVersion == 2) {
            if (b.timerMode == "countdown") {
                val planned = requireNotNull(b.plannedMs)
                val budget = requireNotNull(b.budgetMs)
                require(planned in 1000..86_400_000L && budget in planned..86_400_000L && planned % 1000 == 0L && budget % 1000 == 0L) { "Invalid v2 countdown target/budget; whole seconds required and budget cannot shrink" }
            }
            require(b.wallMs == b.activeMs + b.pausedMs) { "Wall duration differs from monotonic active+paused evidence" }
            val ratio = RewardEngine.focus(b.activeMs, b.pausedMs)
            require(b.focusRatio == ratio) { "Invalid block Focus Ratio" }
            require(b.outcome != "timed_out") { "v2 never times out a block" }
            require(b.type != "focus" || b.outcome != "done" || b.performance?.quality == "confirmed") { "v2 DONE focus requires confirmed completion/reward evidence" }
        }
        require(b.overtimeMs == b.budgetMs?.let { (b.activeMs - it).coerceAtLeast(0) }) { "Invalid block overtime" }
    }
    require(status != "complete" || blocks.none { it.outcome == "skipped" }) { "Skipped blocks cannot claim a fully completed session" }
    if (reportVersion == 2) {
        require(blocks.map { it.id }.distinct().size == blocks.size) { "Duplicate report block IDs" }
        require(sessionId.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))) { "v2 report session_id must be UUID" }
        require(plannedCountdownFocusBlocks != null && plannedStopwatchFocusBlocks != null && plannedCountdownFocusBlocks in 0..100 && plannedStopwatchFocusBlocks in 0..100)
        require(plannedCountdownFocusBlocks >= blocks.count { it.type == "focus" && it.timerMode == "countdown" } && plannedStopwatchFocusBlocks >= blocks.count { it.timerMode == "stopwatch" })
        require((plannedMs == null) == (plannedStopwatchFocusBlocks > 0)) { "Untimed sessions have no whole-session planned duration" }
        require(timedPlannedMs != null && timedPlannedMs in 0..604_800_000L)
        require(competition?.plannedFocusBlocks == plannedCountdownFocusBlocks + plannedStopwatchFocusBlocks)
        require(plannedBlockCount != null && plannedBlockCount in blocks.size..100 && plannedCountdownFocusBlocks + plannedStopwatchFocusBlocks <= plannedBlockCount)
        val timed = blocks.sumOf { it.plannedMs ?: 0 }
        require(timedPlannedMs >= timed && (plannedMs == null || plannedMs == timedPlannedMs))
        if (status == "complete") require(blocks.all { it.outcome == "done" } && plannedBlockCount == blocks.size && timedPlannedMs == timed && plannedCountdownFocusBlocks == blocks.count { it.type == "focus" && it.timerMode == "countdown" } && plannedStopwatchFocusBlocks == blocks.count { it.timerMode == "stopwatch" })
    }
    require(activeMs == focusedMs + breakMs && activeMs == blocks.sumOf { it.activeMs } && pausedMs == blocks.sumOf { it.pausedMs }) { "Report totals do not match blocks" }
    require(focusedMs == blocks.filter { it.type == "focus" }.sumOf { it.activeMs } &&
        breakMs == blocks.filter { it.type == "break" }.sumOf { it.activeMs } && overtimeMs == blocks.sumOf { it.overtimeMs ?: 0 }) { "Report categories do not match blocks" }
    validateTimeline()
    validateRewards()
}
