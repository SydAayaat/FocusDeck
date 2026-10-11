package com.hax.focusdeck

import java.time.Instant
import java.util.Locale

data class ActivityInterval(val kind: String, val blockIndex: Int, val startedAtMs: Long,
    val finishedAtMs: Long, val durationMs: Long, val startSeq: Int, val endSeq: Int, val boot: Int)

data class BehaviorMetrics(val pauseCount: Int?, val extensionCount: Int?,
    val longestActiveMs: Long?, val longestPauseMs: Long?, val averagePauseMs: Long?)

object TimelineAnalysis {
    val kinds = setOf("block_started", "paused", "resumed", "extended", "block_done", "block_stopped", "block_skipped",
        "review_started", "quality_confirmed", "quality_incomplete", "reward_offered", "reward_acknowledged", "reward_skipped", "reward_continued", "session_reward_offered", "session_reward_acknowledged", "session_reward_skipped", "session_reward_continued", "review_recovered_after_reboot",
        "waiting_for_start", "session_complete", "session_stopped", "overtime_observed", "recovered_after_reboot")
    private val closed = setOf("block_done", "block_stopped", "block_skipped", "session_complete", "session_stopped", "reward_acknowledged", "reward_skipped", "reward_continued", "session_reward_acknowledged", "session_reward_skipped", "session_reward_continued")

    fun intervals(report: Report): List<ActivityInterval> {
        val result = mutableListOf<ActivityInterval>()
        report.events.zipWithNext().forEach { (start, end) ->
            if (start.kind in closed || start.boot != end.boot || start.blockIndex != end.blockIndex) return@forEach
            val kind = when (start.phase) { "active" -> "active"; "paused" -> "paused"; "between" -> "waiting"; "quality" -> "quality_review"; "reward", "session_reward" -> "reward"; else -> return@forEach }
            val duration = when (kind) {
                "active" -> end.activeMs - start.activeMs
                "paused" -> end.pausedMs - start.pausedMs
                else -> end.monoMs - start.monoMs
            }
            if (duration < 0) return@forEach
            val interval = ActivityInterval(kind, start.blockIndex, start.atMs, end.atMs, duration, start.seq, end.seq, start.boot)
            val previous = result.lastOrNull()
            if (previous != null && previous.kind == kind && previous.blockIndex == start.blockIndex &&
                previous.boot == start.boot && previous.endSeq == start.seq) {
                result[result.lastIndex] = previous.copy(finishedAtMs = end.atMs, durationMs = previous.durationMs + duration, endSeq = end.seq)
            } else result += interval
        }
        return result
    }

    fun metrics(report: Report, blockIndex: Int? = null): BehaviorMetrics {
        if (report.timelineCoverage != "complete") return BehaviorMetrics(null, null, null, null, null)
        val events = report.events.filter { blockIndex == null || it.blockIndex == blockIndex }
        val intervals = intervals(report).filter { blockIndex == null || it.blockIndex == blockIndex }
        val pauses = intervals.filter { it.kind == "paused" }
        return BehaviorMetrics(events.count { it.kind == "paused" }, events.count { it.kind == "extended" },
            intervals.filter { it.kind == "active" }.maxOfOrNull { it.durationMs } ?: 0,
            pauses.maxOfOrNull { it.durationMs } ?: 0,
            if (pauses.isEmpty()) 0 else pauses.sumOf { it.durationMs } / pauses.size)
    }

    fun ratio(numerator: Long, denominator: Long?): String =
        if (denominator == null || denominator == 0L) "" else String.format(Locale.ROOT, "%.4f", numerator.toDouble() / denominator)

    fun summary(report: Report): String = buildString {
        appendLine("Timeline coverage: ${report.timelineCoverage} | recorded time zone: ${report.recordedTimezone ?: "unknown"}")
        val metrics = metrics(report)
        if (metrics.pauseCount != null) {
            appendLine("Pauses: ${metrics.pauseCount} | extensions: ${metrics.extensionCount} | longest uninterrupted timer stretch: ${humanTime(metrics.longestActiveMs!!)}")
            appendLine("Longest pause: ${humanTime(metrics.longestPauseMs!!)} | average pause: ${humanTime(metrics.averagePauseMs!!)}")
        }
        appendLine("Actual/planned active ratio: ${ratio(report.activeMs, report.plannedMs)} | paused share of active+paused: ${ratio(report.pausedMs, report.activeMs + report.pausedMs)}")
        if (report.events.isEmpty()) { appendLine("No individual pause/resume timestamps were recorded for this session."); return@buildString }
        appendLine("Activity intervals (UTC; durations from monotonic counters):")
        intervals(report).forEach { interval ->
            val block = report.blocks.getOrNull(interval.blockIndex)
            appendLine("${Instant.ofEpochMilli(interval.startedAtMs)} → ${Instant.ofEpochMilli(interval.finishedAtMs)} | block ${interval.blockIndex + 1} ${block?.id ?: "not started"} | ${interval.kind} | ${interval.durationMs} ms")
        }
        appendLine("Event sequence (UTC; sequence is authoritative if the wall clock changes):")
        report.events.forEach { e ->
            appendLine("${e.seq}. ${Instant.ofEpochMilli(e.atMs)} | block ${e.blockIndex + 1} | ${e.kind} | active ${e.activeMs} ms | paused ${e.pausedMs} ms | budget ${e.budgetMs} ms")
        }
        if (report.timelineCoverage != "complete") appendLine("Partial timeline: migration, reboot or the event limit left a gap. Missing intervals are not invented; full behavior metrics are unavailable.")
    }
}

fun Report.validateTimeline() {
    scheduleSnapshot?.let { snapshot ->
        snapshot.sessionSchedule?.validate()
        require(snapshot.activities.size <= 100 && snapshot.activities.map { it.id }.distinct().size == snapshot.activities.size)
        snapshot.activities.forEach { require(it.id.isNotBlank() && it.id.length <= 100 && it.title.isNotBlank() && it.title.length <= 200 && it.type in setOf("focus","break")); require(it.plannedMs==null || it.plannedMs in 0..1_000_000_000_000L);require(it.timerMode==null || it.timerMode in setOf("countdown","stopwatch"));require(it.timerMode!="stopwatch" || it.plannedMs==null);it.schedule?.validate();listOfNotNull(it.category,it.achievementId,it.indexTopicId).forEach { metadata -> require(metadata.isNotBlank() && metadata.length<=200) } }
    }
    require(timelineCoverage in setOf("complete", "partial", "unavailable")) { "Invalid timeline coverage" }
    require(events.size <= 2_000 && (timelineCoverage != "unavailable" || events.isEmpty())) { "Invalid timeline size/coverage" }
    require(plannedBlockCount == null || plannedBlockCount in blocks.size..100) { "Invalid planned block count" }
    require(recordedTimezone == null || (recordedTimezone.isNotBlank() && recordedTimezone.length <= 100)) { "Invalid recorded time zone" }
    events.forEachIndexed { i, e ->
        require(e.seq == i + 1 && e.kind in TimelineAnalysis.kinds && e.blockIndex in 0..99) { "Invalid timeline event identity" }
        require(e.atMs in 0..253_402_300_799_999L && e.monoMs in 0..1_000_000_000_000_000L && e.boot >= -1) { "Invalid event timestamp/boot" }
        require(e.phase in setOf("active", "paused", "between", "complete", "stopped", "quality", "reward", "session_reward")) { "Invalid event phase" }
        require(listOf(e.activeMs, e.pausedMs).all { it in 0..1_000_000_000_000L }) { "Invalid event counters" }
        require(e.budgetMs == null && reportVersion == 2 || e.budgetMs != null && e.budgetMs in 0..1_000_000_000_000L) { "Invalid event budget" }
        val expected = when (e.kind) {
            "block_started", "resumed" -> "active"
            "paused", "recovered_after_reboot" -> "paused"
            "waiting_for_start" -> "between"
            "review_started", "quality_confirmed", "quality_incomplete" -> "quality"
            "reward_offered", "reward_acknowledged", "reward_skipped", "reward_continued" -> "reward"
            "session_reward_offered", "session_reward_acknowledged", "session_reward_skipped", "session_reward_continued" -> "session_reward"
            "session_complete" -> "complete"
            "session_stopped" -> "stopped"
            else -> null
        }
        require(expected == null || e.phase == expected) { "Event kind does not match phase" }
        require(plannedBlockCount == null || e.blockIndex < plannedBlockCount) { "Event outside planned blocks" }
    }
    events.zipWithNext().forEach { (start, end) ->
        require(start.boot != end.boot || end.monoMs >= start.monoMs) { "Invalid monotonic event order" }
    }
    if (timelineCoverage == "complete") {
        require(events.map { it.boot }.distinct().size <= 1 && events.none { it.kind == "recovered_after_reboot" }) { "Rebooted timeline must be partial" }
        require(status != "complete" || plannedBlockCount == null || plannedBlockCount == blocks.size) { "Missing planned blocks" }
        val intervals = TimelineAnalysis.intervals(this)
        blocks.forEachIndexed { i, b ->
            require(events.count { it.blockIndex == i && it.kind == "block_started" && it.activeMs == 0L && it.pausedMs == 0L && it.atMs == b.startedAtMs } == 1) { "Missing block start" }
            require(events.count { it.blockIndex == i && it.kind == (when (b.outcome) { "done" -> "block_done"; "skipped" -> "block_skipped"; else -> "block_stopped" }) &&
                it.activeMs == b.activeMs && it.pausedMs == b.pausedMs && it.budgetMs == b.budgetMs && it.atMs == b.finishedAtMs } == 1) { "Missing block finish" }
            require(intervals.filter { it.blockIndex == i && it.kind == "active" }.sumOf { it.durationMs } == b.activeMs &&
                intervals.filter { it.blockIndex == i && it.kind == "paused" }.sumOf { it.durationMs } == b.pausedMs) { "Timeline intervals do not match block totals" }
        }
        require(events.lastOrNull()?.kind == (if (status == "complete") "session_complete" else "session_stopped") &&
            events.lastOrNull()?.atMs == finishedAtMs) { "Missing session finish" }
    }
}
