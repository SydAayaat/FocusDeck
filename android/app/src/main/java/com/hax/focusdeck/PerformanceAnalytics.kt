package com.hax.focusdeck

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*
import kotlin.math.*

internal const val EXECUTION_POLICY="100 × C × (0.60 + 0.25P + 0.15E) / available component weights. C = DONE planned milliseconds / eligible planned milliseconds. P = duration-weighted max(0, 1 − max(0, actual start − planned start)/15 minutes). E = duration-weighted max(0, 1 − abs(actual active − planned active)/planned active) for closed DONE focus blocks. Missing P/E omitted with weights normalized; coverage disclosed. Future starts excluded. Stopwatch and unknown/zero planned durations excluded. BeatScore remains independent."
@Serializable internal data class EstimationMeasurement(val planned_ms: Long,val actual_ms: Long,val signed_error_ms: Long,val absolute_error_ms: Long,val percentage_error: Double,val accuracy: Double,val interpretation: String)
internal fun estimationMeasurement(planned: Long?,actual: Long?): EstimationMeasurement? {
    if(planned==null || planned<=0 || actual==null) return null
    val delta=actual-planned
    return EstimationMeasurement(planned,actual,delta,abs(delta),100.0*delta/planned,100.0*max(0.0,1.0-abs(delta).toDouble()/planned),
        if(delta>0) "plan underestimated duration" else if(delta<0) "plan overestimated duration" else "matched estimate")
}
@Serializable internal data class PerformanceRecord(val id: String,val report_key: String?,val title: String,val project: String,val category: String,val date: String,
    val planned_ms: Long?,val original_planned_ms: Long?,val actual_ms: Long?,val done: Boolean,val eligible: Boolean,val provisional: Boolean,val start_deviation_ms: Long?,val estimation: EstimationMeasurement?,val missing_plan_blocks: Int=0,val achievement_id: String?=null,val index_topic_id: String?=null,val planned_start_ms: Long?=null,val planned_end_ms: Long?=null,val actual_start_ms: Long?=null,val actual_end_ms: Long?=null,val status: String="",val source_zone: String="UTC",val paused_ms: Long?=null,val overtime_ms: Long?=null,val pause_count: Int?=null)
@Serializable internal data class ExecutionScore(val score: Double?,val completion: Double?,val punctuality: Double?,val efficiency: Double?,val eligible_planned_ms: Long,
    val completed_planned_ms: Long,val punctuality_planned_ms: Long,val estimation_planned_ms: Long,val punctuality_coverage: Double?,val estimation_coverage: Double?,
    val missing_duration_blocks: Int,val future_blocks: Int,val partial: Boolean,val provisional: Boolean,val contributing_ids: List<String>,val policy: String=EXECUTION_POLICY)
@Serializable internal data class CalibrationBias(val samples: Int,val planned_ms: Long,val actual_ms: Long,val signed_error_ms: Long,val absolute_error_ms: Long,val accuracy: Double?)
@Serializable internal data class ConsistencyEvidence(val threshold: Int=80,val valid_planned_days: Int,val planned_days_with_done: Int,val average_daily_score: Double?,val days_meeting_threshold: Int,
    val proportion_meeting_threshold: Double?,val score_standard_deviation: Double?,val excluded_partial_or_provisional_days: Int)
@Serializable internal data class ComparisonValue(val current: Double?,val previous: Double?,val absolute_difference: Double?,val percentage_change: Double?)
@Serializable internal data class PerformanceComparison(val current_bounds: List<String>,val previous_bounds: List<String>,val comparable_coverage: Boolean,
    val focus_ms: ComparisonValue,val paused_ms: ComparisonValue,val overtime_ms: ComparisonValue,val execution_score: ComparisonValue,val estimation_accuracy: ComparisonValue,val consistency: ComparisonValue)
@Serializable internal data class StartDeviation(val id: String,val date: String,val deviation_ms: Long)
@Serializable internal data class PerformanceSummary(val begin: String,val end: String,val execution: ExecutionScore,val focus_ms: Long,val break_ms: Long,val paused_ms: Long,val overtime_ms: Long,
    val overtime_unlocated_ms: Long,val pause_count: Int?,val done_blocks: Int,val recorded_days: Int,val complete_days: Int,val unlocated_ms: Long,
    val estimation: CalibrationBias,val estimation_by_category: Map<String,CalibrationBias>,val estimation_by_project: Map<String,CalibrationBias>,val consistency: ConsistencyEvidence,
    val daily: Map<String,ExecutionScore>,val contributions: List<PerformanceRecord>,val comparison: PerformanceComparison?=null,val scope: String="",
    val start_deviations: List<StartDeviation> = emptyList(),val narrative_source: String="deterministic factual summary",val period_closed: Boolean=false,val calendar_days: Long=0,val policy: String=EXECUTION_POLICY)

internal object PerformanceAnalytics {
    fun records(data: CalendarDataset,now: Long=System.currentTimeMillis()): List<PerformanceRecord> = data.activities.values.flatMap { a ->
        if(a.type!="focus" || a.timerMode=="stopwatch") return@flatMap emptyList()
        val schedule=a.schedule;val start=schedule?.start ?: a.start
        val zone=schedule?.timezone?.let(ZoneId::of) ?: a.zone
        val date=start?.let { CalendarAnalytics.date(it,zone) } ?: a.planDate ?: return@flatMap emptyList()
        val future=start!=null && start>now
        val done=a.status=="done" && a.end!=null && a.end<=now
        val estimate=if(done) estimationMeasurement(a.plannedMs,a.focusMs) else null
        val weights=if(a.plannedMs!=null && a.plannedMs>0 && schedule?.end!=null)
            CalendarAnalytics.split(schedule.start,schedule.end!!,a.plannedMs,zone).groupBy { it.date }.mapValues { (_,parts)->parts.sumOf { it.duration } }
            else mapOf(date to a.plannedMs)
        weights.map { (day,weight)->PerformanceRecord(a.id,a.reportKey,a.title,a.project,a.category,day.toString(),weight,a.plannedMs,a.focusMs,done,!future,
            a.provisional || CalendarAnalytics.date(now,zone)<=day,a.punctuality,estimate,a.missingPlanBlocks,a.achievementId,a.indexTopicId,schedule?.start,schedule?.end,a.start,a.end,a.status,a.zone.id,a.pausedMs,a.overtimeMs,a.pauseCount) }
    }
    fun score(records: List<PerformanceRecord>): ExecutionScore {
        val rows=records.filter { it.eligible };val valid=rows.filter { (it.planned_ms ?: 0)>0 }
        val total=valid.sumOf { it.planned_ms!! };val completed=valid.filter { it.done }.sumOf { it.planned_ms!! }
        val c=if(total>0) completed.toDouble()/total else null
        val ps=valid.filter { it.start_deviation_ms!=null };val es=valid.filter { it.estimation!=null }
        val pw=ps.sumOf { it.planned_ms!! };val ew=es.sumOf { it.planned_ms!! }
        val p=if(pw>0) ps.sumOf { it.planned_ms!!*max(0.0,1.0-max(0L,it.start_deviation_ms!!)/900000.0) }/pw else null
        val e=if(ew>0) es.sumOf { it.planned_ms!!*it.estimation!!.accuracy/100 }/ew else null
        val denominator=.6+(if(p!=null) .25 else 0.0)+(if(e!=null) .15 else 0.0)
        val value=c?.let { 100*it*(.6+(p?.times(.25) ?: 0.0)+(e?.times(.15) ?: 0.0))/denominator }
        val missing=rows.size-valid.size+rows.groupBy { it.report_key }.values.sumOf { group->group.maxOf { it.missing_plan_blocks } }
        return ExecutionScore(value,c,p,e,total,completed,pw,ew,if(total>0) pw.toDouble()/total else null,if(total>0) ew.toDouble()/total else null,
            missing,records.size-rows.size,missing>0 || total>0 && (pw<total || ew<total),rows.any { it.provisional },valid.map { it.id }.distinct().sorted())
    }
    private fun bias(items: List<PerformanceRecord>): CalibrationBias {
        val total=items.sumOf { it.original_planned_ms!! }
        return CalibrationBias(items.size,total,items.sumOf { it.actual_ms!! },items.sumOf { it.estimation!!.signed_error_ms },items.sumOf { it.estimation!!.absolute_error_ms },
            if(total>0) items.sumOf { it.original_planned_ms!!*it.estimation!!.accuracy }/total else null)
    }
    fun summary(data: CalendarDataset,begin: LocalDate,end: LocalDate,records: List<PerformanceRecord> = records(data)): PerformanceSummary {
        val days=data.days.values.filter { it.hasRecord && it.date in begin..end }
        val rows=records.filter { LocalDate.parse(it.date) in begin..end };val unique=rows.filter { it.eligible }.distinctBy { it.id }
        val estimates=unique.filter { it.estimation!=null }
        val daily=rows.groupBy { it.date }.mapValues { score(it.value) }
        val valid=daily.values.filter { it.score!=null && it.missing_duration_blocks==0 && !it.provisional }
        val scores=valid.map { it.score!! };val mean=scores.takeIf { it.isNotEmpty() }?.average()
        val consistency=ConsistencyEvidence(valid_planned_days=valid.size,planned_days_with_done=valid.count { it.completed_planned_ms>0 },average_daily_score=mean,
            days_meeting_threshold=scores.count { it>=80 },proportion_meeting_threshold=if(scores.isNotEmpty()) scores.count { it>=80 }.toDouble()/scores.size else null,
            score_standard_deviation=mean?.let { m->sqrt(scores.sumOf { (it-m).pow(2) }/scores.size) },excluded_partial_or_provisional_days=daily.size-valid.size)
        return PerformanceSummary(begin.toString(),end.toString(),score(rows),days.sumOf { it.focusMs },days.sumOf { it.breakMs },days.sumOf { it.pausedMs },days.sumOf { it.overtimeMs },
            days.sumOf { it.overtimeUnlocatedMs },days.takeIf { it.isNotEmpty() && it.none { d->d.incomplete } }?.sumOf { it.pauseCount },days.sumOf { it.done },days.size,days.count { it.hourComplete },days.sumOf { it.unlocatedMs },
            bias(estimates),estimates.groupBy { it.category }.mapValues { bias(it.value) },estimates.groupBy { it.project }.mapValues { bias(it.value) },consistency,daily,unique,start_deviations=unique.filter { it.start_deviation_ms!=null }.map { StartDeviation(it.id,it.date,it.start_deviation_ms!!) },period_closed=(days.flatMap { it.zones }.toSet().ifEmpty { setOf(ZoneOffset.UTC) }).all { end<CalendarAnalytics.date(System.currentTimeMillis(),it) },calendar_days=java.time.temporal.ChronoUnit.DAYS.between(begin,end)+1)
    }
    fun compare(current: PerformanceSummary,previous: PerformanceSummary): PerformanceComparison {
        val comparable=current.recorded_days>0 && previous.recorded_days>0 && current.complete_days==current.recorded_days && previous.complete_days==previous.recorded_days && current.period_closed && previous.period_closed && current.complete_days*previous.calendar_days==previous.complete_days*current.calendar_days
        val x=current.execution;val y=previous.execution
        val scoreCoverage=x.missing_duration_blocks==0 && y.missing_duration_blocks==0 && x.punctuality_coverage==y.punctuality_coverage && x.estimation_coverage==y.estimation_coverage
        fun diff(a: Double?,b: Double?,allowed: Boolean=true)=ComparisonValue(a,b,if(a!=null && b!=null) a-b else null,if(comparable && allowed && a!=null && b!=null && b!=0.0) (a-b)/b*100 else null)
        return PerformanceComparison(listOf(current.begin,current.end),listOf(previous.begin,previous.end),comparable,
            diff(current.focus_ms.takeIf { current.recorded_days>0 }?.toDouble(),previous.focus_ms.takeIf { previous.recorded_days>0 }?.toDouble()),diff(current.paused_ms.takeIf { current.recorded_days>0 }?.toDouble(),previous.paused_ms.takeIf { previous.recorded_days>0 }?.toDouble()),diff(current.overtime_ms.takeIf { current.recorded_days>0 }?.toDouble(),previous.overtime_ms.takeIf { previous.recorded_days>0 }?.toDouble()),
            diff(current.execution.score,previous.execution.score,scoreCoverage),diff(current.estimation.accuracy,previous.estimation.accuracy),diff(current.consistency.average_daily_score,previous.consistency.average_daily_score,scoreCoverage))
    }
    fun report(data: CalendarDataset,period: HistoryPeriod,anchor: LocalDate,now: Long=System.currentTimeMillis()): PerformanceSummary {
        val records=records(data,now);val (begin,end)=analyticsRange(period,anchor,data);val current=summary(data,begin,end,records)
        val previous=if(period==HistoryPeriod.ALL) null else analyticsRange(period,begin.minusDays(1),data).let { (a,b)->summary(data,a,b,records) }
        return current.copy(comparison=previous?.let { compare(current,it) },scope=period.name.lowercase())
    }
    private val exportJson=Json { prettyPrint=true;encodeDefaults=true }
    fun export(report: PerformanceSummary): String = exportJson.encodeToString(report)
}
