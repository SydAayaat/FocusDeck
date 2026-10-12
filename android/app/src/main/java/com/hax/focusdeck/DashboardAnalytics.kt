package com.hax.focusdeck

import java.time.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Presentation projections only. Stored totals, policy scores and source zones remain authoritative. */
internal data class EstimationEvidence(val planned: Long,val actual: Long,val absoluteError: Long=abs(actual-planned)) {
    val delta get()=actual-planned
    val accuracy get()=if(planned>0) (100.0*(1.0-absoluteError.toDouble()/planned)).coerceIn(0.0,100.0) else null
}
internal data class CompletionContribution(val before: Double,val within: Double,val total: Double,val target: Double,val unit: String) {
    val earlierFraction get()=(before/target).coerceIn(0.0,1.0).toFloat()
    val periodFraction get()=(within/target).coerceIn(0.0,1.0-earlierFraction).toFloat()
}
internal data class DashboardProject(val title: String,val focusMs: Long,val reportKeys: Set<String>,val progress: CompletionContribution?)
internal data class DashboardAnalyticsEvidence(val days: List<CalendarDay>,val reports: List<Report>,val activities: List<CalendarActivity>,
    val focus: Long,val breaks: Long,val tracked: Long,val done: Int,val plannedCount: Int?,val punctual: List<Long>,
    val score: Double?,val performance: PerformanceSummary,val calendarScores: Map<LocalDate,ExecutionScore>,val estimation: EstimationEvidence?,val estimationGroups: Map<String,EstimationEvidence>,val projects: List<DashboardProject>) {
    val completion get()=performance.execution.completion?.toFloat()
    val punctualFraction get()=punctual.takeIf { it.isNotEmpty() }?.let { it.count { d->d<=0 }.toFloat()/it.size }
    val focusShare get()=tracked.takeIf { it>0 }?.let { focus.toFloat()/it }
}
internal fun completionContribution(store: AchievementStore,id: String,start: LocalDate,end: LocalDate,zone: ZoneId): CompletionContribution? {
    val def=store.definitions.find { it.id==id && !it.archived } ?: return null
    val startMs=start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMs=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val index=store.indexes[id]
    if(index!=null) {
        val revision=index.revisions.filter { it.atMs<endMs }.lastOrNull()
        if(index.revisions.isNotEmpty() && revision==null) return null
        val doc=revision?.document ?: index.document
        val parents=doc.topics.mapNotNull { it.parent_id }.toSet();val leaves=doc.topics.filter { it.id !in parents }.map { it.id }.toSet()
        if(leaves.isEmpty()) return null
        fun count(cutoff: Long): Double = leaves.count { topic ->
            val event=index.events.filter { it.topicId==topic && it.atMs<cutoff }.lastOrNull()
            if(event!=null) event.delta>0 else index.states[topic]?.let { it.atMs<cutoff && it.completed }==true
        }.toDouble()
        val prior=index.revisions.filter { it.atMs<startMs }.lastOrNull()?.document
        if(prior!=null) {
            val oldParents=prior.topics.mapNotNull { it.parent_id }.toSet()
            if(prior.topics.filter { it.id !in oldParents }.map { it.id }.toSet()!=leaves) return null
        } else if(index.revisions.any { it.atMs in startMs until endMs }) return null
        if(index.revisions.any { r ->
            val parentsAtRevision=r.document.topics.mapNotNull { it.parent_id }.toSet()
            r.atMs in startMs until endMs && r.document.topics.filter { it.id !in parentsAtRevision }.map { it.id }.toSet()!=leaves
        }) return null
        val before=count(startMs);val total=count(endMs)
        // A reopened/removed topic is evidence, not new completion. Do not fabricate a positive segment.
        if(total<before) return null
        return CompletionContribution(before,total-before,total,leaves.size.toDouble(),"topics")
    }
    val p=def.progress?.takeIf { def.progressEnabled } ?: return null
    val history=store.progressHistory.filter { it.achievementId==id }.sortedBy { it.timestamp }
    fun valueAt(cutoff: Long): Triple<Double,Double?,String>? {
        val h=history.lastOrNull { it.timestamp<cutoff }
        if(h!=null) return Triple(h.newValue,h.targetValueAtThatTime,h.unit)
        if(p.updatedAt<cutoff) return Triple(p.currentValue,p.targetValue,p.unit)
        // Baseline can only be established by a supplied receipt retaining the previous value/target.
        return history.firstOrNull()?.takeIf { it.timestamp>=cutoff }?.let { Triple(it.previousValue,it.previousTarget,it.unit) }
    }
    val before=valueAt(startMs) ?: return null;val after=valueAt(endMs) ?: return null
    val target=after.second?.takeIf { it>0 && it.isFinite() } ?: return null
    if(before.third!=after.third || before.second!=after.second || after.first<before.first || before.first<0) return null
    if(history.any { it.timestamp in startMs until endMs && (it.unit!=after.third || it.targetValueAtThatTime!=target || it.previousTarget!=target) }) return null
    return CompletionContribution(before.first,after.first-before.first,after.first,target,after.third)
}
internal fun dashboardAnalytics(data: CalendarDataset,reports: List<Report>,period: HistoryPeriod,anchor: LocalDate,store: AchievementStore,now: Long=System.currentTimeMillis()): DashboardAnalyticsEvidence {
    val (start,end)=analyticsRange(period,anchor,data)
    val days=data.days.values.filter { it.hasRecord && it.date>=start && it.date<=end }
    val keys=days.flatMap { it.sessions }.toSet()
    val matching=reports.distinctBy { it.key }.filter { it.key in keys }
    val activities=data.activities.values.filter { a ->
        val time=a.start ?: a.schedule?.start
        time!=null && CalendarAnalytics.date(time,if(a.start==null) a.schedule?.timezone?.let(ZoneId::of) ?: a.zone else a.zone) in start..end
    }
    val whole=matching.filter { CalendarAnalytics.date(it.startedAtMs,CalendarAnalytics.zone(it))>=start && CalendarAnalytics.date(it.finishedAtMs,CalendarAnalytics.zone(it))<=end }
    val count=if(whole.size==matching.size && matching.isNotEmpty() && matching.all { it.plannedBlockCount!=null }) matching.sumOf { it.plannedBlockCount!! } else null
    val projects=matching.groupBy { it.title }.map { (title,rs) ->
        val ids=rs.map { it.key }.toSet()
        val associations=data.activities.values.filter { it.reportKey in ids && it.focusMs!=null }.mapNotNull { it.achievementId }.distinct()
        val z=rs.map { CalendarAnalytics.zone(it) }.distinct()
        val progress=if(associations.size==1 && z.size==1) completionContribution(store,associations.single(),start,end,z.single()) else null
        DashboardProject(title,days.sumOf { d->ids.sumOf { d.sessionFocus[it] ?: 0 } },ids,progress)
    }.sortedByDescending { it.focusMs }
    val performance=PerformanceAnalytics.report(data,period,anchor,now)
    return DashboardAnalyticsEvidence(days,matching.sortedByDescending { it.finishedAtMs },activities,days.sumOf { it.focusMs },days.sumOf { it.breakMs },days.sumOf { it.trackedMs },days.sumOf { it.done },
        count,days.flatMap { it.punctuality },performance.execution.score,performance,PerformanceAnalytics.records(data,now).groupBy { LocalDate.parse(it.date) }.mapValues { PerformanceAnalytics.score(it.value) },
        performance.estimation.takeIf { it.samples>0 }?.let { EstimationEvidence(it.planned_ms,it.actual_ms,it.absolute_error_ms) },
        performance.estimation_by_category.mapValues { (_,it)->EstimationEvidence(it.planned_ms,it.actual_ms,it.absolute_error_ms) },projects)
}

/** Normal-card ring is earned measurement only; missing/uninitialized state has no lit arc. */
internal fun achievementRingFraction(stats: AchievementStats): Float = if(stats.definition.progressEnabled)
    ((stats.definition.progress?.percent ?: 0.0)/100).toFloat().coerceIn(0f,1f)
    else if(stats.initialized && stats.current>0) 1f else 0f

/** Reference projections use the same complete, located-day denominator as hour evidence. */
internal fun hourlyFocusReference(days: List<CalendarDay>): Double? = days.filter { it.hourComplete }
    .takeIf { it.isNotEmpty() }?.let { eligible -> eligible.sumOf { it.hours.sum() }.toDouble() / eligible.size / 24 }
internal fun dailyFocusReference(days: List<CalendarDay>): Double? = days.filter { it.hourComplete }
    .takeIf { it.isNotEmpty() }?.map { it.focusMs.toDouble() }?.average()
internal fun referenceDuration(ms: Double): String {
    val minutes = (ms.coerceAtLeast(0.0) / 60000).toLong()
    return "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}

internal data class PunctualityBucket(val label: String, val meanMs: Double?, val samples: Int)
internal data class PunctualityTrend(val buckets: List<PunctualityBucket>, val averageMs: Double?)

/** Calendar punctuality already owns actual-start attribution in the captured source zone.
 * Average observations, not daily averages: a busy day must not lose its sample weight.
 */
internal fun punctualityTrend(data: CalendarDataset, period: HistoryPeriod, anchor: LocalDate): PunctualityTrend {
    val (begin,end)=analyticsRange(period,anchor,data)
    val ranges: List<Triple<String,LocalDate,LocalDate>> = when(period) {
        HistoryPeriod.DAY,HistoryPeriod.WEEK -> {
            val start=if(period==HistoryPeriod.DAY) anchor.minusDays(6) else begin
            (0..6).map { start.plusDays(it.toLong()).let { d -> Triple(d.format(java.time.format.DateTimeFormatter.ofPattern("EEE")),d,d) } }
        }
        HistoryPeriod.MONTH -> generateSequence(begin) { it.plusDays(7-it.dayOfWeek.value+1L) }
            .takeWhile { it<=end }.map { start -> Triple("${start.dayOfMonth}",start,minOf(end,start.plusDays(7-start.dayOfWeek.value.toLong()))) }.toList()
        HistoryPeriod.YEAR -> (1..12).map { month -> LocalDate.of(anchor.year,month,1).let { d -> Triple(d.format(java.time.format.DateTimeFormatter.ofPattern("MMM")),d,d.plusMonths(1).minusDays(1)) } }
        HistoryPeriod.ALL -> (begin.year..end.year).map { y -> Triple(y.toString(),LocalDate.of(y,1,1),LocalDate.of(y,12,31)) }
    }
    fun observations(start: LocalDate,finish: LocalDate)=data.days.values.filter { it.date in start..finish }.flatMap { it.punctuality }
    val buckets=ranges.map { (label,start,finish) -> observations(start,finish).let { values -> PunctualityBucket(label,values.takeIf { it.isNotEmpty() }?.average(),values.size) } }
    val values=observations(ranges.first().second,ranges.last().third)
    return PunctualityTrend(buckets,values.takeIf { it.isNotEmpty() }?.average())
}

internal data class AnalyticsKpi(val label: String,val value: String,val trend: Double?=null,val varianceMs: Double?=null)
internal data class CompactAnalyticsEvidence(val kpis: List<AnalyticsKpi>,val punctuality: PunctualityTrend,val captions: Map<String,String>)
internal fun compactAnalytics(data: CalendarDataset,reports: List<Report>,period: HistoryPeriod,anchor: LocalDate,e: DashboardAnalyticsEvidence): CompactAnalyticsEvidence {
    val valid=e.performance.contributions.filter { it.eligible && (it.planned_ms ?: 0)>0 }
    val captions=mapOf(
        "completion" to valid.takeIf { it.isNotEmpty() }?.let { "${it.count { r->r.done }}/${it.size}" }.orEmpty(),
        "punctuality" to valid.count { it.start_deviation_ms!=null }.takeIf { it>0 }?.let { "$it starts" }.orEmpty(),
        "efficiency" to e.performance.estimation.takeIf { it.samples>0 }?.let { signedStartDelta(it.signed_error_ms.toDouble()) }.orEmpty())
    return CompactAnalyticsEvidence(analyticsKpis(data,reports,e),punctualityTrend(data,period,anchor),captions)
}
/** Same previous-period window and coverage gate as the existing performance comparison. */
internal fun analyticsKpis(data: CalendarDataset,reports: List<Report>,e: DashboardAnalyticsEvidence): List<AnalyticsKpi> {
    val previous=e.performance.comparison?.previous_bounds
    val priorDays=if(previous==null) emptyList() else data.days.values.filter { it.hasRecord && it.date in LocalDate.parse(previous[0])..LocalDate.parse(previous[1]) }
    val keys=priorDays.flatMap { it.sessions }.toSet()
    val priorSessions=reports.distinctBy { it.key }.count { it.key in keys }
    fun change(current: Int,prior: Int)=if(e.performance.comparison?.comparable_coverage==true && prior>0) (current-prior)*100.0/prior else null
    val variance=e.punctual.takeIf { it.isNotEmpty() }?.average()
    return listOf(AnalyticsKpi("FOCUS",dashboardDuration(e.focus.takeIf { e.days.isNotEmpty() }),e.performance.comparison?.focus_ms?.percentage_change),
        AnalyticsKpi("SESSIONS",e.reports.size.takeIf { e.days.isNotEmpty() }?.toString() ?: "N/A",change(e.reports.size,priorSessions)),
        AnalyticsKpi("BLOCKS DONE",e.done.takeIf { e.days.isNotEmpty() }?.toString() ?: "N/A",change(e.done,priorDays.sumOf { it.done })),
        AnalyticsKpi("VARIANCE",signedStartDelta(variance),varianceMs=variance))
}
internal fun signedStartDelta(ms: Double?): String = ms?.let {
    val magnitude=abs(it)
    (if(it>0) "+" else if(it<0) "−" else "") + if(magnitude in 1.0..<60000.0) "${(magnitude/1000).roundToInt()}s" else dashboardDuration(magnitude.toLong())
} ?: "N/A"
