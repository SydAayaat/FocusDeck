package com.hax.focusdeck

import java.time.*
import kotlin.math.abs

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
internal fun dashboardAnalytics(data: CalendarDataset,reports: List<Report>,period: HistoryPeriod,anchor: LocalDate,store: AchievementStore): DashboardAnalyticsEvidence {
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
    val performance=PerformanceAnalytics.report(data,period,anchor)
    return DashboardAnalyticsEvidence(days,matching.sortedByDescending { it.finishedAtMs },activities,days.sumOf { it.focusMs },days.sumOf { it.breakMs },days.sumOf { it.trackedMs },days.sumOf { it.done },
        count,days.flatMap { it.punctuality },performance.execution.score,performance,PerformanceAnalytics.records(data).groupBy { LocalDate.parse(it.date) }.mapValues { PerformanceAnalytics.score(it.value) },
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
    return "${minutes / 60}h ${minutes % 60}m"
}
