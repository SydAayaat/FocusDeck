package com.hax.focusdeck

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Presentation only: never changes archived scores, timer accounting or completion receipts. */
internal fun compactClock(time: Long?, zone: ZoneId, seconds: Boolean = false): String = time?.let {
    Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern(if(seconds) "HH:mm:ss" else "HH:mm"))
} ?: "N/A"
internal fun executionLabel(status: String): String = when(status) {
    "done", "complete" -> "Completed"
    "stopped" -> "Ended Early"
    "skipped" -> "Skipped"
    "paused" -> "Paused"
    "active", "In progress" -> "In Progress"
    "ready", "between", "Planned", "Not started", "not_started" -> "Not Started"
    "timed_out" -> "Timed Out"
    else -> status.replace('_',' ')
}
internal data class DurationComparison(val estimated: Long, val actual: Long) {
    val accuracy: Double get() = if(estimated==0L && actual==0L) 100.0 else 100.0*minOf(estimated,actual)/maxOf(estimated,actual)
    val deviation: Long get() = actual-estimated
    val caption: String get() = when {
        deviation>0 -> "${humanTime(deviation)} longer than estimated"
        deviation<0 -> "${humanTime(abs(deviation))} shorter than estimated"
        else -> "Matched estimate"
    }
}
internal fun durationComparison(planned: Long?, actual: Long?, finalized: Boolean): DurationComparison? =
    if(!finalized || planned==null || actual==null || planned<0 || actual<0) null else DurationComparison(planned,actual)
internal fun reportDurationComparison(report: Report): DurationComparison? {
    // A stopped attempt and a mixed/unknown plan cannot be compared as a finalized whole session.
    if(report.status!="complete" || report.blocks.isEmpty() || report.blocks.any { it.timerMode=="stopwatch" || it.plannedMs==null || it.outcome!="done" }) return null
    if((report.plannedBlockCount ?: report.blocks.size)!=report.blocks.size) return null
    return durationComparison(report.plannedMs,report.activeMs,true)
}
internal data class DelayAttribution(val inherited: Long, val additional: Long)
internal fun delayAttribution(current: CalendarActivity, previous: CalendarActivity?): DelayAttribution? {
    val total=current.punctuality ?: return null
    val before=previous ?: return null
    val plan=current.schedule ?: return null
    val previousPlan=before.schedule ?: return null
    val previousEnd=before.end ?: return null
    // Only adjacent work in the same attempt with reliable non-overlapping schedules can support attribution.
    if(total<0 || current.reportKey==null || current.reportKey!=before.reportKey || before.provisional || previousPlan.end==null || previousPlan.end!!>plan.start || before.start==null || current.start==null || previousEnd>current.start) return null
    val inherited=(previousEnd-plan.start).coerceIn(0,total)
    return DelayAttribution(inherited,total-inherited)
}
internal enum class ActivityCategory(val label: String, val icon: String) {
    SESSIONS("Sessions","play"), GPT("GPT","sparkles"), CARDS("Cards","file-text"), INDEX("Index","chart-no-axes-column"),
    ACHIEVEMENTS("Achievements","trophy"), BLOCKS("Blocks","timer"), STATUS("Status","settings")
}
internal data class HistoryActivity(val id: String,val category: ActivityCategory,val at: Long,val zone: ZoneId,
    val title: String,val description: String,val metrics: String="",val status: String="",val reportKey: String?=null,val payload: String?=null,val punctuality: Long?=null,val sequence: Int=0)
internal object ActivityHistory {
    fun records(reports: List<Report>,store: AchievementStore,zone: ZoneId): List<HistoryActivity> = buildList {
        reports.distinctBy { it.key }.forEach { r ->
            val z=CalendarAnalytics.zone(r)
            add(HistoryActivity("session:${r.key}",ActivityCategory.SESSIONS,r.finishedAtMs,z,r.title,r.outcomeSummary,
                "${humanTime(r.focusedMs)} focus · Pace ${r.competition?.averagePace ?: "N/A"} · BeatScore ${r.competition?.beatScore ?: "N/A"}",(if(r.status=="complete") "Completed" else r.statusLabel),r.key,punctuality=CalendarAnalytics.sessionPunctuality(r),sequence=(r.events.maxOfOrNull { it.seq } ?: 0)+1))
            r.events.forEach { e ->
                val category=if(e.kind in setOf("session_started","session_complete","session_stopped","reward_offered","reward_acknowledged")) ActivityCategory.STATUS else ActivityCategory.BLOCKS
                add(HistoryActivity("event:${r.key}:${e.seq}",category,e.atMs,z,e.kind.replace('_',' ').replaceFirstChar { it.titlecase() },
                    r.blocks.getOrNull(e.blockIndex)?.title ?: r.title,"Active ${humanTime(e.activeMs)} · Paused ${humanTime(e.pausedMs)}",reportKey=r.key,sequence=e.seq))
            }
        }
        val detailedIds=(store.events.map { it.id }+store.progressHistory.map { it.id }+store.indexes.values.flatMap { it.events.map { e -> e.id } }).toSet()
        store.commands.values.filter { it.id !in detailedIds && it.id !in store.retiredCommands && (it.achievement_id ?: it.definition?.id) !in store.deletedIds }.forEach { c ->
            val at=runCatching { Instant.parse(c.timestamp).toEpochMilli() }.getOrNull() ?: return@forEach
            val category=when { c.operation in setOf("upsert","index_create") -> ActivityCategory.CARDS;c.operation.startsWith("index_")->ActivityCategory.INDEX;c.operation=="daily_target"->ActivityCategory.STATUS;else->ActivityCategory.ACHIEVEMENTS }
            val title=c.definition?.title ?: store.definitions.find { it.id==c.achievement_id }?.title ?: c.achievement_id ?: "Daily objectives"
            // A retained receipt proves processing, not creation versus replacement or a progress change.
            add(HistoryActivity("command:${c.id}",category,at,zone,title,"${c.operation.replace('_',' ')} · ${c.source}",
                "${c.definition?.let { it.category+" · "+it.kind+" · " } ?: ""}Retained command receipt · ${c.id}","Recorded",payload=WireJson.encodeToString(AchievementCommand.serializer(),c)))
        }
        store.events.filter { it.achievementId !in store.deletedIds }.forEach { e ->
            add(HistoryActivity("achievement:${e.id}",ActivityCategory.ACHIEVEMENTS,e.atMs,zone,
                store.definitions.find { it.id==e.achievementId }?.title ?: e.achievementId,e.event.replace('_',' '),"${e.amount} · ${e.source}"))
        }
        store.progressHistory.filter { it.achievementId !in store.deletedIds }.forEach { p ->
            add(HistoryActivity("progress:${p.id}",ActivityCategory.ACHIEVEMENTS,p.timestamp,zone,
                store.definitions.find { it.id==p.achievementId }?.title ?: p.achievementId,"Progress updated",
                "${p.previousValue} → ${p.newValue} ${p.unit} · change ${p.delta}"))
        }
        store.indexes.filterKeys { it !in store.deletedIds }.forEach { (id,l) -> l.events.forEach { e ->
            add(HistoryActivity("index:$id:${e.id}",ActivityCategory.INDEX,e.atMs,runCatching { ZoneId.of(e.zone) }.getOrDefault(zone),e.title,
                if(e.delta>0) "Topic completed" else "Topic reopened","${if(e.delta>0) "+" else ""}${e.delta} topic · $id"))
        } }
    }.distinctBy { it.id }
    fun filter(records: List<HistoryActivity>, categories: Set<ActivityCategory>,query: String,from: LocalDate?,to: LocalDate?,ascending: Boolean): List<HistoryActivity> = records.filter { r ->
        val date=Instant.ofEpochMilli(r.at).atZone(r.zone).toLocalDate()
        (categories.isEmpty() || r.category in categories) && (from==null || date>=from) && (to==null || date<=to) &&
            (query.isBlank() || listOf(r.title,r.description,r.metrics,r.status).any { it.contains(query,ignoreCase=true) })
    }.let { if(ascending) it.sortedWith(compareBy({r->r.at},{r->r.sequence},{r->r.id})) else it.sortedWith(compareByDescending<HistoryActivity> { r->r.at }.thenByDescending { r->r.sequence }.thenBy { r->r.id }) }
}

/** Preserve raw calendar totals; the current engine phase only refines its live display status. */
internal fun timelineDataset(reports: List<Report>,state: DeckState?): CalendarDataset {
    val data=CalendarAnalytics.dataset(reports,state)
    val block=state?.session?.blocks?.getOrNull(state.index) ?: return data
    if(state.phase !in setOf("active","paused")) return data
    val liveKey=CalendarAnalytics.observedReport(state)?.key ?: return data
    return data.copy(activities=data.activities.mapValues { (_,a) -> if(a.id=="$liveKey:${block.id}" && a.provisional) a.copy(status=state.phase) else a })
}

internal fun currentSessionFocus(state: DeckState,now: ClockNow): Long = state.results.filter { it.type=="focus" }.sumOf { it.activeMs } +
    if(state.phase in setOf("active","paused") && state.session?.blocks?.getOrNull(state.index)?.type=="focus") TimerEngine(state).currentActive(now) else 0L
