package com.hax.focusdeck

import java.time.*
import kotlin.math.*

internal fun performancePercent(value: Double?): String=value?.let { "${(it*100).roundToInt()}%" } ?: "N/A"
internal fun signedActive(value: Long): String=(if(value>0) "+" else if(value<0) "−" else "")+dashboardDuration(abs(value))
internal fun executionDetails(p: PerformanceSummary): String {
    val x=p.execution
    return "${x.policy}\n\nScore ${x.score?.let { "%.2f".format(java.util.Locale.ROOT,it) } ?: "N/A"}\nPlan Completion ${performancePercent(x.completion)} · Start Punctuality ${performancePercent(x.punctuality)} · Time Efficiency ${performancePercent(x.efficiency)}\nEligible planned ${dashboardDuration(x.eligible_planned_ms)} · DONE planned ${dashboardDuration(x.completed_planned_ms)}\nStart coverage ${performancePercent(x.punctuality_coverage)} · estimation coverage ${performancePercent(x.estimation_coverage)}\nUnknown duration blocks ${x.missing_duration_blocks} · future excluded ${x.future_blocks}\n\n"+
        p.contributions.joinToString("\n") { "${it.date} · ${it.title} · ${dashboardDuration(it.original_planned_ms)} planned / ${dashboardDuration(it.actual_ms)} active · ${if(it.done) "DONE" else "No DONE"} · start ${punctualityLabel(it.start_deviation_ms?.toDouble())} · planned start ${it.planned_start_ms?.let(Instant::ofEpochMilli) ?: "N/A"} / actual ${it.actual_start_ms?.let(Instant::ofEpochMilli) ?: "N/A"}" }
}
internal fun sessionDetailText(a: CalendarActivity): String {
    fun at(ms: Long?,zone: ZoneId=a.zone)=ms?.let { Instant.ofEpochMilli(it).atZone(zone).toString() } ?: "N/A"
    val est=estimationMeasurement(a.plannedMs,a.focusMs)?.takeIf { a.status=="done" }
    return "${a.project} / ${a.title}\nCategory ${a.category} · Achievement ${a.achievementId ?: "N/A"} · Index ${a.indexTopicId ?: "N/A"}\nPlanned ${at(a.schedule?.start,a.schedule?.timezone?.let(ZoneId::of) ?: a.zone)} → ${at(a.schedule?.end,a.schedule?.timezone?.let(ZoneId::of) ?: a.zone)}\nActual ${at(a.start)} → ${at(a.end)}\nStart deviation ${punctualityLabel(a.punctuality?.toDouble())} (positive late)\nPlanned active ${dashboardDuration(a.plannedMs)} · Actual active ${dashboardDuration(a.focusMs)}\nSigned error ${est?.let { signedActive(it.signed_error_ms) } ?: "N/A"} · absolute ${dashboardDuration(est?.absolute_error_ms)}\nPercentage error ${est?.percentage_error ?: "N/A"}% · Accuracy ${est?.accuracy ?: "N/A"}%\n${est?.interpretation ?: "No comparable closed estimate"}\nPauses ${dashboardDuration(a.pausedMs)} · count ${a.pauseCount ?: "N/A"} · Overtime ${dashboardDuration(a.overtimeMs)}\nCompletion ${a.status}"
}
internal fun focusBucketDetails(data: CalendarDataset,begin: LocalDate,end: LocalDate): String {
    val ds=data.days.values.filter { it.hasRecord && it.date in begin..end }
    val planned=PerformanceAnalytics.records(data).filter { LocalDate.parse(it.date) in begin..end && it.eligible && (it.planned_ms ?: 0)>0 }
    return "$begin — $end\nActual active ${dashboardDuration(ds.takeIf { it.isNotEmpty() }?.sumOf { it.focusMs })}\nOvertime ${dashboardDuration(ds.takeIf { it.isNotEmpty() }?.sumOf { it.overtimeMs })} (included in active)\nPauses ${dashboardDuration(ds.takeIf { it.isNotEmpty() }?.sumOf { it.pausedMs })} · count ${if(ds.isNotEmpty() && ds.none { it.incomplete }) ds.sumOf { it.pauseCount } else "N/A"}\nPlanned active ${if(planned.isNotEmpty()) dashboardDuration(planned.sumOf { it.planned_ms!! }) else "N/A"}"
}
internal fun startTrendDetails(data: CalendarDataset,begin: LocalDate,end: LocalDate): String {
    val items=data.activities.values.filter { it.start!=null && CalendarAnalytics.date(it.start,it.zone) in begin..end && it.punctuality!=null }
    return "$begin — $end\nAverage start deviation ${punctualityLabel(items.mapNotNull { it.punctuality }.takeIf { it.isNotEmpty() }?.average())}\nPositive late · negative early · untimed excluded\n"+items.joinToString("\n") { "${it.project} / ${it.title}: ${punctualityLabel(it.punctuality?.toDouble())}" }
}
internal fun hourDetails(data: CalendarDataset,p: PerformanceSummary,hour: Int): String {
    val ds=data.days.values.filter { it.hasRecord && it.date.toString() in p.begin..p.end && it.hourCoverage }
    if(ds.isEmpty()) return "%02d:00".format(java.util.Locale.ROOT,hour)+"\n${p.begin} — ${p.end}\nTotal focus N/A · no reliably located hour evidence. Break, pause, session, block, category and score metadata unavailable."
    val ids=ds.flatMap { it.hourActivities[hour] }.distinct();val activities=ids.mapNotNull { data.activities[it] }
    val complete=ds.filter { it.hourComplete }
    val scores=activities.mapNotNull { a -> a.start?.let { CalendarAnalytics.date(it,a.zone).toString() }?.let { p.daily[it] } }.distinct().filter { it.score!=null && it.missing_duration_blocks==0 && !it.provisional }
    return "%02d:00 — %02d:00".format(java.util.Locale.ROOT,hour,(hour+1)%24)+"\n${p.begin} — ${p.end} · ${ds.size} recorded days; ${ds.count { !it.hourComplete }} partial/live; ${dashboardDuration(p.unlocated_ms)} unlocated in period\nFocus (located evidence) ${dashboardDuration(ds.sumOf { it.hours[hour] })}\nBreak ${dashboardDuration(ds.sumOf { it.hourBreaks[hour] })} · Pause ${dashboardDuration(ds.sumOf { it.hourPauses[hour] })}\n${activities.mapNotNull { it.reportKey }.distinct().size} sessions · ${activities.size} blocks\nProjects/categories: ${activities.map { it.project+" / "+it.category }.distinct().joinToString().ifBlank { "N/A" }}\nAverage focus ${dashboardDuration(complete.takeIf { it.isNotEmpty() }?.let { it.sumOf { d->d.hours[hour] }/it.size })} · ${complete.size} fully located complete days\nAverage execution score ${scores.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()?.let { "%.1f".format(java.util.Locale.ROOT,it) } ?: "N/A"} · ${scores.size} valid planned day samples"
}
internal fun comparisonDetails(p: PerformanceSummary): String {
    val c=p.comparison ?: return "Lifetime trend · no previous all-time period\n"+p.daily.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}: ${it.value.score ?: "N/A"}/100" }
    fun detail(label: String,v: ComparisonValue)= "$label: ${v.current ?: "N/A"} vs ${v.previous ?: "N/A"}\nAbsolute difference ${v.absolute_difference ?: "N/A"} · percent ${v.percentage_change ?: "N/A"}%"
    return "Current ${c.current_bounds.joinToString(" — ")}\nPrevious ${c.previous_bounds.joinToString(" — ")}\nComparable complete coverage: ${c.comparable_coverage}\n\n"+
        listOf(detail("Focus milliseconds",c.focus_ms),detail("Execution score",c.execution_score),detail("Estimation accuracy",c.estimation_accuracy),detail("Consistency average score",c.consistency)).joinToString("\n\n")
}
internal fun calibrationDetails(p: PerformanceSummary): String {
    val c=p.consistency
    return "Estimation accuracy ${p.estimation.accuracy ?: "N/A"}% · signed bias ${if(p.estimation.samples>0) signedActive(p.estimation.signed_error_ms) else "N/A"}\nAbsolute error ${dashboardDuration(p.estimation.absolute_error_ms.takeIf { p.estimation.samples>0 })} · ${p.estimation.samples} closed DONE estimates\n"+
        p.estimation_by_project.entries.joinToString("\n") { "${it.key}: signed bias ${signedActive(it.value.signed_error_ms)} · accuracy ${it.value.accuracy ?: "N/A"}%" }+
        "\n\nValid planned days ${c.valid_planned_days} · days with DONE ${c.planned_days_with_done}\nDaily score average ${c.average_daily_score ?: "N/A"} · meeting 80/100: ${c.days_meeting_threshold} (${performancePercent(c.proportion_meeting_threshold)})\nStandard deviation ${c.score_standard_deviation ?: "N/A"} · provisional/missing duration days excluded ${c.excluded_partial_or_provisional_days}"
}
