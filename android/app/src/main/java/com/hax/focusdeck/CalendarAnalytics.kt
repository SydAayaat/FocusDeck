package com.hax.focusdeck

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.*

@Serializable data class PlannedSchedule(
    @SerialName("planned_start") val plannedStart: String,
    @SerialName("planned_end") val plannedEnd: String? = null, val timezone: String? = null
) {
    val start: Long get() = Instant.parse(plannedStart).toEpochMilli()
    val end: Long? get() = plannedEnd?.let { Instant.parse(it).toEpochMilli() }
    fun validate() {
        require(start in 0..253402300799999 && plannedStart.length <= 60)
        require(plannedEnd==null || plannedEnd.length<=60)
        require(end == null || end!! > start) { "planned_end must be after planned_start" }
        timezone?.let { require(it.length <= 100); ZoneId.of(it) }
    }
}
@Serializable data class PlannedActivity(val id: String, val title: String, val type: String,
    val category: String? = null, @SerialName("achievement_id") val achievementId: String? = null,
    @SerialName("index_topic_id") val indexTopicId: String? = null, @SerialName("planned_ms") val plannedMs: Long? = null, @SerialName("timer_mode") val timerMode: String? = null, val schedule: PlannedSchedule? = null)
@Serializable data class ScheduleSnapshot(@SerialName("session_schedule") val sessionSchedule: PlannedSchedule? = null,
    val activities: List<PlannedActivity> = emptyList())
fun Session.scheduleSnapshot() = ScheduleSnapshot(schedule, blocks.map { PlannedActivity(it.id,it.title,it.type,it.category,it.achievementId,it.indexTopicId,it.plannedMs,it.effectiveMode,it.schedule) })

data class CalendarActivity(val id: String, val reportKey: String?, val title: String, val category: String,
    val achievementId: String?, val indexTopicId: String?, val schedule: PlannedSchedule?, val start: Long?, val end: Long?,
    val focusMs: Long?, val elapsedMs: Long?, val pausedMs: Long?, val overtimeMs: Long?, val plannedMs: Long?,
    val status: String, val zone: ZoneId, val type: String = "focus", val timerMode: String? = null, val provisional: Boolean = false, val planDate: LocalDate? = null, val project: String = "", val pauseCount: Int? = null, val missingPlanBlocks: Int = 0) {
    val punctuality: Long? get() = if (start != null && schedule != null) start - schedule.start else null
}
data class CalendarSegment(val activityId: String, val track: String, val kind: String, val start: Long, val end: Long,
    val left: Float, val right: Float, val zone: ZoneId)
data class CalendarDay(val date: LocalDate, val zones: MutableSet<ZoneId> = linkedSetOf(), var focusMs: Long = 0,
    var breakMs: Long = 0, var pausedMs: Long = 0, var otherMs: Long = 0, var unlocatedMs: Long = 0,
    var hasRecord: Boolean = false, var incomplete: Boolean = false, val sessionFocus: MutableMap<String,Long> = mutableMapOf(), var done: Int = 0, val sessions: MutableSet<String> = linkedSetOf(), val punctuality: MutableList<Long> = mutableListOf(),
    var overtimeMs: Long = 0, var overtimeUnlocatedMs: Long = 0, var pauseCount: Int = 0, val hourBreaks: MutableList<Long> = MutableList(24) { 0L }, val hourPauses: MutableList<Long> = MutableList(24) { 0L }, val hourActivities: List<MutableSet<String>> = List(24) { linkedSetOf() }, val hours: MutableList<Long> = MutableList(24) { 0L }, var hourCoverage: Boolean = false,
    val segments: MutableList<CalendarSegment> = mutableListOf(), val unscheduled: MutableList<String> = mutableListOf()) {
    val trackedMs: Long get() = focusMs + breakMs + pausedMs + otherMs
    val meanPunctuality: Double? get() = punctuality.takeIf { it.isNotEmpty() }?.average()
    val hourComplete: Boolean get() = hourCoverage && unlocatedMs == 0L && !incomplete
}
data class CalendarDataset(val days: Map<LocalDate, CalendarDay>, val activities: Map<String, CalendarActivity>)
data class CalendarPiece(val date: LocalDate, val hour: Int, val start: Long, val end: Long, val duration: Long)

object CalendarAnalytics {
    fun zone(report: Report): ZoneId = runCatching { ZoneId.of(report.recordedTimezone ?: report.scheduleSnapshot?.sessionSchedule?.timezone ?: "UTC") }.getOrDefault(ZoneOffset.UTC)
    fun bounds(date: LocalDate, zone: ZoneId): Pair<Long,Long> = date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun split(start: Long, end: Long, duration: Long, zone: ZoneId): List<CalendarPiece> {
        if (end <= start || duration <= 0) return emptyList()
        val out = mutableListOf<CalendarPiece>(); var cursor = start; var allocated = 0L
        while (cursor < end) {
            val at = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = cursor + (3600-at.minute*60-at.second)*1000L-at.nano/1_000_000
            var stop = minOf(end,nextHour,bounds(at.toLocalDate(),zone).second)
            if (stop <= cursor) stop = minOf(end,cursor+3_600_000)
            // BigInteger retains exact millisecond allocation without overflowing long histories.
            val cumulative = if (stop == end) duration else java.math.BigInteger.valueOf(duration).multiply(java.math.BigInteger.valueOf(stop-start)).divide(java.math.BigInteger.valueOf(end-start)).toLong()
            out += CalendarPiece(at.toLocalDate(),at.hour,cursor,stop,cumulative-allocated)
            cursor = stop; allocated = cumulative
        }
        return out
    }
    fun observedReport(state: DeckState?): Report? {
        val s=state ?: return null;val plan=s.session ?: return null
        if(s.startedAtMs==0L || s.phase in setOf("idle","complete","stopped")) return null
        val results=s.results.toMutableList()
        if(s.phase in setOf("active","paused")) plan.blocks.getOrNull(s.index)?.let { b ->
            results+=BlockResult(b.id,b.type,b.title,b.plannedMs,s.budgetMs,s.activeMs,s.pausedMs,s.blockStartedAtMs,s.anchorWall,"In progress",timerMode=b.effectiveMode,wallMs=s.activeMs+s.pausedMs)
        }
        val events=s.events.toMutableList()
        events.lastOrNull()?.let { e -> events+=e.copy(seq=e.seq+1,kind="observation",atMs=s.anchorWall,monoMs=s.anchorMono,blockIndex=s.index,phase=s.phase,activeMs=s.activeMs,pausedMs=s.pausedMs) }
        return Report(plan.sessionId,plan.title,"stopped",null,results.sumOf { it.activeMs },results.filter { it.type=="focus" }.sumOf { it.activeMs },
            results.filter { it.type=="break" }.sumOf { it.activeMs },results.sumOf { it.pausedMs },0,s.startedAtMs,s.anchorWall,results,
            timelineCoverage="partial",events=events,recordedTimezone=s.recordedTimezone,scheduleSnapshot=plan.scheduleSnapshot())
    }
    fun dataset(reports: List<Report>, live: DeckState? = null): CalendarDataset {
        val days = linkedMapOf<LocalDate,CalendarDay>(); val activities = linkedMapOf<String,CalendarActivity>()
        fun day(date: LocalDate, zone: ZoneId): CalendarDay = days.getOrPut(date) { CalendarDay(date) }.also { it.zones += zone }
        fun segment(id: String, track: String, kind: String, start: Long, end: Long, zone: ZoneId) {
            split(start,end,end-start,zone).forEach { p ->
                val (a,b) = bounds(p.date,zone)
                day(p.date,zone).segments += CalendarSegment(id,track,kind,p.start,p.end,(p.start-a).toFloat()/(b-a),(p.end-a).toFloat()/(b-a),zone)
            }
        }
        val observed=observedReport(live)
        (reports+listOfNotNull(observed).filter { o -> reports.none { it.key==o.key } }).distinctBy { it.key }.forEach { r ->
            val zone = zone(r);day(date(r.startedAtMs,zone),zone).also { it.hasRecord=true;it.sessions+=r.key;it.incomplete=it.incomplete || r.timelineCoverage!="complete" }; val metadata = r.scheduleSnapshot?.activities.orEmpty().associateBy { it.id }
            val captured=maxOf(r.blocks.size,metadata.size)
            var missingPlan=maxOf(0,(r.plannedBlockCount ?: captured)-captured)
            if(missingPlan==0 && r.plannedMs!=null && r.plannedMs>r.blocks.sumOf { it.plannedMs ?: 0 } && metadata.isEmpty()) missingPlan=1
            val remaining = mutableMapOf("focus_ms" to r.focusedMs,"break_ms" to r.breakMs,"paused_ms" to r.pausedMs)
            r.blocks.forEach { b ->
                val m = metadata[b.id]; val id = r.key+":"+b.id
                val activity = CalendarActivity(id,r.key,b.title,m?.category ?: b.type,m?.achievementId,m?.indexTopicId,m?.schedule,
                    b.startedAtMs,b.finishedAtMs.takeIf { b.outcome!="In progress" },b.activeMs.takeIf { b.type=="focus" },b.wallMs ?: b.activeMs+b.pausedMs,
                    b.pausedMs,b.overtimeMs,b.plannedMs,b.outcome,zone,b.type,b.timerMode,b.outcome=="In progress" || observed===r,date(r.startedAtMs,zone),m?.achievementId ?: r.title,r.events.count { it.kind=="paused" && r.blocks.getOrNull(it.blockIndex)?.id==b.id }.takeIf { r.timelineCoverage=="complete" },if(b==r.blocks.firstOrNull()) missingPlan else 0)
                activities[id] = activity
                val d = day(date(b.startedAtMs,zone),zone);d.hasRecord=true;d.sessions+=r.key;d.incomplete=d.incomplete || r.timelineCoverage!="complete"
                activity.punctuality?.let { d.punctuality += it }
                if (m?.schedule == null) d.unscheduled += id
                if (b.outcome=="done") day(date(b.finishedAtMs,zone),zone).also { it.done++;it.hasRecord=true;it.incomplete=it.incomplete || r.timelineCoverage!="complete" }
            }
            metadata.values.forEach { m ->
                val id = r.key+":"+m.id
                if (id !in activities) activities[id] = CalendarActivity(id,r.key,m.title,m.category ?: m.type,m.achievementId,m.indexTopicId,m.schedule,
                    null,null,null,null,null,null,m.plannedMs,"Not started",zone,m.type,m.timerMode,observed===r,date(r.startedAtMs,zone),m.achievementId ?: r.title)
                m.schedule?.let { s -> s.end?.let { segment(id,"planned",m.type,s.start,it,s.timezone?.let(ZoneId::of) ?: zone) } }
            }
            val overtimeRemaining=r.blocks.associate { it.id to if(it.type=="focus") minOf(it.overtimeMs ?: 0,it.activeMs) else 0L }.toMutableMap()
            r.events.filter { it.kind=="paused" }.forEach { day(date(it.atMs,zone),zone).pauseCount++ }
            TimelineAnalysis.intervals(r).forEach interval@{ span ->
                val b = r.blocks.getOrNull(span.blockIndex)
                val field = when(span.kind) { "active" -> if (b?.type=="focus") "focus_ms" else "break_ms"; "paused" -> "paused_ms"; else -> "other_ms" }
                val before=r.events.firstOrNull { it.seq==span.startSeq }?.activeMs ?: 0L
                if (span.finishedAtMs<=span.startedAtMs || kotlin.math.abs(span.finishedAtMs-span.startedAtMs-span.durationMs)>1000) return@interval
                val amount = remaining[field]?.let { minOf(it,span.durationMs) } ?: span.durationMs
                if (field in remaining) remaining[field] = remaining.getValue(field)-amount
                if(field=="focus_ms" && b!=null) {
                    val threshold=b.activeMs-(b.overtimeMs ?: 0)
                    val ot=minOf(amount,maxOf(0L,before+amount-maxOf(before,threshold)),overtimeRemaining.getValue(b.id))
                    overtimeRemaining[b.id]=overtimeRemaining.getValue(b.id)-ot
                    if(ot>0) split(span.finishedAtMs-(span.finishedAtMs-span.startedAtMs)*ot/maxOf(1,amount),span.finishedAtMs,ot,zone).forEach { day(it.date,zone).overtimeMs+=it.duration }
                }
                split(span.startedAtMs,span.finishedAtMs,amount,zone).forEach { p ->
                    val d = day(p.date,zone); d.sessions += r.key; d.hourCoverage = true;d.hasRecord=true;d.incomplete=d.incomplete || r.timelineCoverage!="complete"
                    when (field) { "focus_ms" -> { d.focusMs+=p.duration; d.hours[p.hour]+=p.duration;d.sessionFocus[r.key]=(d.sessionFocus[r.key] ?: 0)+p.duration }; "break_ms" -> d.breakMs+=p.duration; "paused_ms" -> d.pausedMs+=p.duration; else -> d.otherMs+=p.duration }
                    if(field=="break_ms") d.hourBreaks[p.hour]+=p.duration
                    if(field=="paused_ms") d.hourPauses[p.hour]+=p.duration
                    if(b!=null) d.hourActivities[p.hour]+=r.key+":"+b.id
                    if (b != null) {
                        val (a,z) = bounds(p.date,zone)
                        d.segments += CalendarSegment(r.key+":"+b.id,"actual",field,p.start,p.end,(p.start-a).toFloat()/(z-a),(p.end-a).toFloat()/(z-a),zone)
                    }
                }
            }
            val d = day(date(r.finishedAtMs,zone),zone); d.sessions += r.key;d.hasRecord=true;d.incomplete=d.incomplete || r.timelineCoverage!="complete"
            d.overtimeMs+=overtimeRemaining.values.sum();d.overtimeUnlocatedMs+=overtimeRemaining.values.sum()
            d.sessionFocus[r.key]=(d.sessionFocus[r.key] ?: 0)+remaining.getValue("focus_ms")
            d.focusMs+=remaining.getValue("focus_ms");d.breakMs+=remaining.getValue("break_ms");d.pausedMs+=remaining.getValue("paused_ms")
            d.unlocatedMs+=remaining.values.sum()
        }
        live?.takeIf { observed==null && it.phase !in setOf("complete","stopped","idle") }?.session?.let { plan ->
            val zone = runCatching { ZoneId.of(live.recordedTimezone ?: plan.schedule?.timezone ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneOffset.UTC)
            plan.blocks.forEach { b ->
                val id="live:${plan.sessionId}:${b.id}"
                activities[id]=CalendarActivity(id,null,b.title,b.category ?: b.type,b.achievementId,b.indexTopicId,b.schedule,null,null,null,null,null,null,b.plannedMs,"Planned",zone,b.type,b.effectiveMode,true,null,b.achievementId ?: plan.title)
                b.schedule?.let { s -> s.end?.let { segment(id,"planned",b.type,s.start,it,s.timezone?.let(ZoneId::of) ?: zone) } }
            }
        }
        days.values.forEach { d ->
            val merged=mutableListOf<CalendarSegment>()
            d.segments.sortedWith(compareBy({it.track},{it.zone.id},{it.activityId},{it.kind},{it.start})).forEach { segment ->
                val previous=merged.lastOrNull()
                if(previous!=null && previous.track==segment.track && previous.zone==segment.zone && previous.activityId==segment.activityId && previous.kind==segment.kind && previous.end==segment.start)
                    merged[merged.lastIndex]=previous.copy(end=segment.end,right=segment.right)
                else merged+=segment
            }
            d.segments.clear();d.segments.addAll(merged)
        }
        return CalendarDataset(days,activities)
    }
    fun sessionPunctuality(r: Report): Long? = r.scheduleSnapshot?.sessionSchedule?.let { r.startedAtMs-it.start }
}
