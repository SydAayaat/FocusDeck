package com.hax.focusdeck

import kotlinx.serialization.json.*
import java.io.InputStream
import java.security.MessageDigest
import java.time.*

/** Read-only asset adapter. Deliberately has no repository, archive, transport or mutation API. */
internal class SyntheticDataset private constructor(private val source: (String)->InputStream, val manifest: JsonObject,
    val reports: List<Report>, val calendar: CalendarDataset, val store: AchievementStore,
    val daily: Map<LocalDate,JsonObject>, val periods: List<JsonObject>, val scenarios: List<JsonObject>,
    private val names: Map<String,String>, private val sessionKeys: Map<String,String>) {
    val zone: ZoneId = ZoneId.of(manifest.s("timezone")!!)
    val now = Instant.parse(OffsetDateTime.parse(manifest.s("fixed_clock")).toInstant().toString()).toEpochMilli()
    val date: LocalDate = CalendarAnalytics.date(now,zone)
    private val reportsById=reports.associateBy { it.sessionId }
    val latest = reports.maxByOrNull { it.finishedAtMs }
    val lifetime = daily.values.sumOf { it.ms("focus_seconds") }
    val home: HomePresentation get() {
        val today=daily.getValue(date)
        val week=periods.first { it.s("period")=="week" && date in LocalDate.parse(it.s("start_date"))..LocalDate.parse(it.s("end_date")) }
        return HomePresentation(today.ms("focus_seconds"),today.n("session_count").toInt(),today.n("done_block_count").toInt(),week.ms("focus_seconds"),lifetime)
    }
    /** Stream the portable feed, retaining at most one page. No full feed in Compose state. */
    fun historyPage(categories: Set<ActivityCategory>,query: String,from: LocalDate?,to: LocalDate?,ascending: Boolean,offset: Int,limit: Int=80): List<HistoryActivity> {
        require(offset>=0 && limit in 1..200)
        val page=java.util.ArrayDeque<HistoryActivity>()
        fun eligible(r: JsonObject): Boolean {
            val category=feedCategory(r);val d=LocalDate.parse(r.s("date"));val entity=r.s("entity_id")!!
            val title=names[entity] ?: names[r.s("session_id")] ?: entity
            val description="Synthetic · "+r.s("action")!!.replace('_',' ')
            return (categories.isEmpty() || category in categories) && (from==null || d>=from) && (to==null || d<=to) &&
                (query.isBlank() || title.contains(query,true) || description.contains(query,true) || entity.contains(query,true))
        }
        var total=0
        if(ascending) rows(source,"history_activity.jsonl") { if(eligible(it)) total++ }
        val begin=if(ascending) (total-offset-limit).coerceAtLeast(0) else offset
        val end=if(ascending) (total-offset).coerceAtLeast(0) else offset+limit
        var matches=0
        rows(source,"history_activity.jsonl") { r ->
            val category=feedCategory(r)
            val d=LocalDate.parse(r.s("date"))
            val entity=r.s("entity_id")!!;val title=names[entity] ?: names[r.s("session_id")] ?: entity
            val description="Synthetic · "+r.s("action")!!.replace('_',' ')
            if(eligible(r)) {
                if(matches++ !in begin until end) return@rows
                val item=HistoryActivity(r.s("id")!!,category,r.time("time")!!,zone,title,description,
                    metrics=r["detail"]?.takeUnless { it is JsonNull }?.toString().orEmpty(),reportKey=sessionKeys[r.s("session_id")],punctuality=reportsById[r.s("session_id")]?.let(CalendarAnalytics::sessionPunctuality),sequence=r.n("sequence").toInt())
                page.addLast(item)
            }
        }
        return if(ascending) page.toList().asReversed() else page.toList()
    }
    companion object {
        const val ASSET_ROOT="FocusDeck_DeveloperMode_3Years"
        fun load(source: (String)->InputStream): SyntheticDataset {
            val manifest=objectFile(source,"manifest.json")
            require(manifest.s("format")=="focusdeck.synthetic-fixtures.v1" && manifest.b("synthetic") && manifest.b("fixture_only") && manifest.b("no_sync") && manifest.b("no_production_import") && manifest.b("no_network")) { "Unsafe fixture manifest" }
            manifest.getValue("files").jsonArray.forEach { entry ->
                val f=entry.jsonObject;val path=f.s("path")!!
                require(path.matches(Regex("[a-zA-Z0-9_.]+")))
                val hash=MessageDigest.getInstance("SHA-256");var bytes=0L
                source(path).use { input -> val buf=ByteArray(16384);while(true) {val n=input.read(buf);if(n<0) break;bytes+=n;hash.update(buf,0,n)} }
                require(bytes==f.n("bytes") && hash.digest().joinToString("") { "%02x".format(it) }==f.s("sha256")) { "Fixture checksum mismatch: $path" }
            }
            val zone=ZoneId.of(manifest.s("timezone"));val date=OffsetDateTime.parse(manifest.s("fixed_clock")).toLocalDate()
            val projects=arrayFile(source,"projects.json").associateBy { it.s("id")!! }
            val blocks=mutableMapOf<String,MutableList<JsonObject>>()
            rows(source,"blocks.jsonl") { blocks.getOrPut(it.s("session_id")!!) { mutableListOf() }.add(it) }
            val sessionRows=mutableListOf<JsonObject>();rows(source,"sessions.jsonl") { sessionRows+=it }
            val names=mutableMapOf<String,String>()
            projects.forEach { (id,p)->names[id]=p.s("name")!! }
            sessionRows.forEach { names[it.s("id")!!]=it.s("title")!! };blocks.values.flatten().forEach { names[it.s("id")!!]=it.s("title")!! }
            // Preserve explicit skip action time; null actual starts remain null in calendar activities.
            val actionTimes=mutableMapOf<String,Long>()
            rows(source,"timeline_events.jsonl") { if(it.s("action") in setOf("block_skipped","block_not_started")) actionTimes[it.s("entity_id")!!]=it.time("time")!! }
            val reports=sessionRows.mapNotNull { s ->
                val suppliedBlocks=blocks.getValue(s.s("id")!!)
                require(suppliedBlocks.sumOf { it.ms("active_focus_seconds") }==s.ms("focus_seconds"))
                require(suppliedBlocks.sumOf { it.ms("paused_seconds") }==s.ms("pause_seconds"))
                require(suppliedBlocks.sumOf { b->b.getValue("break_before").jsonArray.sumOf { it.jsonObject.ms("duration_seconds") } }==s.ms("break_seconds"))
                listOf("DONE" to "done_count","SKIPPED" to "skipped_count","ENDED_EARLY" to "ended_early_count","NOT_STARTED" to "not_started_count").forEach { (outcome,field)->require(suppliedBlocks.count { it.s("status")==outcome }.toLong()==s.n(field)) }
                val started=s.time("actual_start") ?: return@mapNotNull null // Unstarted plans are calendar-only, never fake reports.
                val results=mutableListOf<BlockResult>();val events=mutableListOf<TimelineEvent>();val plans=mutableListOf<PlannedActivity>()
                blocks.getValue(s.s("id")!!).sortedBy { it.n("order") }.forEach { b ->
                    val planned=b.msOrNull("planned_duration_seconds");val mode=b.s("timer_mode")!!
                    val schedule=PlannedSchedule(b.s("planned_start")!!,null,zone.id)
                    plans+=PlannedActivity(b.s("id")!!,b.s("title")!!,"focus",projects.getValue(b.s("project_id")!!).s("category"),plannedMs=planned,timerMode=mode,schedule=schedule)
                    if(b.s("status")=="NOT_STARTED") return@forEach
                    val start=b.time("actual_start") ?: actionTimes.getValue(b.s("id")!!)
                    val end=b.time("actual_end") ?: start
                    val outcome=when(b.s("status")) { "DONE"->"done";"SKIPPED"->"skipped";else->"stopped" }
                    val index=results.size;val focus=b.ms("active_focus_seconds");val pause=b.ms("paused_seconds")
                    results+=BlockResult(b.s("id")!!,"focus",b.s("title")!!,planned,planned,focus,pause,start,end,outcome,timerMode=mode,wallMs=focus+pause,
                        focusRatio=RewardEngine.focus(focus,pause))
                    fun event(kind: String,at: Long,phase: String,active: Long,paused: Long) { events+=TimelineEvent(0,kind,at,(at-started).coerceAtLeast(0),0,index,phase,active,paused,planned) }
                    if(b.time("actual_start")!=null) {
                        event("block_started",start,"active",0,0)
                        var paused=0L
                        b.getValue("pauses").jsonArray.forEach { p ->
                            val obj=p.jsonObject;val a=obj.time("start")!!;val z=obj.time("end")!!
                            event("paused",a,"paused",a-start-paused,paused);paused+=z-a
                            event("resumed",z,"active",z-start-paused,paused)
                        }
                    }
                    event(when(outcome) { "done"->"block_done";"skipped"->"block_skipped";else->"block_stopped" },end,"between",focus,pause)
                }
                val finish=s.time("actual_end")!!
                val complete=s.s("status")=="DONE"
                events+=TimelineEvent(0,if(complete) "session_complete" else "session_stopped",finish,finish-started,0,0,if(complete) "complete" else "stopped",0,0,0)
                Report(s.s("id")!!,s.s("title")!!,if(complete) "complete" else "stopped",
                    s.msOrNull("planned_focus_seconds").takeIf { s.ms("break_seconds")==0L },s.ms("focus_seconds")+s.ms("break_seconds"),s.ms("focus_seconds"),s.ms("break_seconds"),s.ms("pause_seconds"),s.ms("wall_seconds"),started,finish,results,
                    timelineCoverage="complete",events=events.sortedBy { it.atMs }.mapIndexed { i,e->e.copy(seq=i+1) },plannedBlockCount=plans.size,recordedTimezone=zone.id,reportVersion=2,
                    competition=SessionCompetition(plannedFocusBlocks=plans.size,completedFocusBlocks=s.n("done_count").toInt(),onOrAheadCount=0,
                        completionFraction=s.n("done_count").toDouble()/plans.size,averagePace=null,focusRatio=RewardEngine.focus(s.ms("focus_seconds"),s.ms("pause_seconds")),
                        beatScore=null,rank=null,rewardStrategy=RewardStrategy(),reward=RewardReceipt(reason="Synthetic preview; rewards unavailable"),scoreScope=if(s.s("timer_mode")=="stopwatch") "none" else "all_focus"),
                    timedPlannedMs=s.msOrNull("planned_focus_seconds"),scheduleSnapshot=ScheduleSnapshot(PlannedSchedule(s.s("planned_start")!!,s.s("planned_end"),zone.id),plans))
            }.sortedByDescending { it.finishedAtMs }
            val computed=CalendarAnalytics.dataset(reports)
            val daily=mutableMapOf<LocalDate,JsonObject>();rows(source,"daily_analytics.jsonl") { daily[LocalDate.parse(it.s("date"))]=it }
            require(reports.sumOf { it.focusedMs }==daily.values.sumOf { it.ms("focus_seconds") })
            daily.forEach { (d,row)->
                val raw=computed.days[d];val hours=row.getValue("focus_by_hour_seconds").jsonArray.map { it.jsonPrimitive.long*1000 }
                require((raw?.focusMs ?: 0)==row.ms("focus_seconds") && (raw?.hours ?: List(24) {0L})==hours) { "Record/aggregate mismatch: $d; ${raw?.focusMs}/${row.ms("focus_seconds")}; ${raw?.hours}/$hours" }
            }
            val days=computed.days.toMutableMap()
            daily.forEach { (d,row)->
                if(row.s("evidence")=="unknown") { require((days[d]?.focusMs ?: 0)==0L);days.remove(d) }
                else {
                    val day=days.getOrPut(d) { CalendarDay(d,zones=linkedSetOf(zone)) }
                    day.hasRecord=true;day.hourCoverage=true;day.incomplete=false
                    day.breakMs=row.ms("break_seconds");day.pausedMs=row.ms("pause_seconds");day.unlocatedMs=0
                    day.done=row.n("done_block_count").toInt()
                    day.punctuality.clear();sessionRows.filter { it.s("date")==d.toString() }.forEach { it.msOrNull("start_delta_seconds")?.let(day.punctuality::add) }
                }
            }
            val blockById=blocks.values.flatten().associateBy { it.s("id")!! }
            val activities=computed.activities.mapValues { (_,a)->
                val b=blockById[a.id.substringAfterLast(':')]
                if(b!=null && b.time("actual_start")==null) a.copy(start=null,end=null,status=if(b.s("status")=="SKIPPED") "skipped" else "Not started") else a
            }.toMutableMap()
            // Explicit observed breaks are located from the portable records, with their original durations.
            val keys=reports.associate { it.sessionId to it.key }
            blocks.forEach { (sid,bs)->
                bs.forEach { b -> b.getValue("break_before").jsonArray.forEachIndexed { i,entry ->
                    val r=entry.jsonObject;val a=r.time("start")!!;val z=r.time("end")!!;val id="$sid:${b.s("id")}:break:$i"
                    activities[id]=CalendarActivity(id,keys[sid],"Observed break","break",null,null,null,a,z,null,z-a,0,0,null,"done",zone,"break",project=names[sid].orEmpty())
                    CalendarAnalytics.split(a,z,z-a,zone).forEach { p-> val day=days[p.date] ?: return@forEach;val (begin,end)=CalendarAnalytics.bounds(p.date,zone)
                        day.segments+=CalendarSegment(id,"actual","break_ms",p.start,p.end,(p.start-begin).toFloat()/(end-begin),(p.end-begin).toFloat()/(end-begin),zone)
                        day.hourBreaks[p.hour]+=p.duration
                    }
                } }
                if(sid !in keys) bs.forEach { b->val schedule=PlannedSchedule(b.s("planned_start")!!,null,zone.id);val id="planned:${b.s("id")}"
                    activities[id]=CalendarActivity(id,null,b.s("title")!!,projects.getValue(b.s("project_id")!!).s("category")!!,null,null,schedule,null,null,null,null,null,null,b.msOrNull("planned_duration_seconds"),if(b.s("status")=="SKIPPED") "skipped" else "Not started",zone,timerMode=b.s("timer_mode"),planDate=LocalDate.parse(sessionRows.first { it.s("id")==sid }.s("date")),project=names[sid].orEmpty())
                }
            }
            val periods=mutableListOf<JsonObject>();rows(source,"period_analytics.jsonl") { p ->
                val selected=daily.filterKeys { it in LocalDate.parse(p.s("start_date"))..LocalDate.parse(p.s("end_date")) }.values
                listOf("focus_seconds","pause_seconds","break_seconds","session_count","done_block_count").forEach { f->require(p.n(f)==selected.sumOf { it.n(f) }) { "Period mismatch: ${p.s("key")}:$f" } };periods+=p
            }
            val store=ledger(source,daily,date,zone,names)
            val counts=manifest.getValue("counts").jsonObject
            listOf("sessions" to sessionRows.size,"blocks" to blocks.values.sumOf { it.size },"daily_analytics" to daily.size,"period_analytics" to periods.size).forEach { (k,v)->require(counts.n(k)==v.toLong()) }
            return SyntheticDataset(source,manifest,reports,CalendarDataset(days,activities),store,daily,periods,arrayFile(source,"developer_scenarios.json"),names,keys)
        }

        private fun ledger(source: (String)->InputStream,daily: Map<LocalDate,JsonObject>,date: LocalDate,zone: ZoneId,names: MutableMap<String,String>): AchievementStore {
            val rows=arrayFile(source,"achievement_definitions.json")
            val definitions=rows.map { r -> AchievementDefinition(r.s("id")!!,r.s("title")!!,r.s("category")!!.lowercase(),"daily","Synthetic supplied evidence",createdDate=daily.keys.min().toString(),
                streakEnabled=r.s("card_type")!="PROGRESS_ONLY",progressEnabled=r.s("card_type")!="STREAK_ONLY",progress=r.number("target")?.let { AchievementProgress(targetValue=it,unit="units") }) }.toMutableList()
            definitions.forEach { names[it.id]=it.title }
            val days=daily.map { (d,r)->d.toString() to AchievementDay(d.toString(),zone.id,r.n("done_block_count"),r.ms("focus_seconds"),finalized=d<date,
                statuses=definitions.associate { it.id to "UNMET" }) }.toMap().toMutableMap()
            val events=mutableListOf<AchievementEvent>();val history=mutableListOf<ProgressHistory>()
            rows(source,"achievement_events.jsonl") { e ->
                val id=e.s("achievement_id")!!;val at=e.time("time")!!;val d=e.s("date")!!
                if(e.s("action")=="daily_confirmed") {
                    val old=days.getValue(d);days[d]=old.copy(activities=old.activities+(id to 1L),statuses=old.statuses+(id to "MET"))
                    events+=AchievementEvent(e.s("id")!!,id,"complete",at,d,"Synthetic fixture")
                } else if(e.s("action")=="progress_delta") {
                    val index=definitions.indexOfFirst { it.id==id };val old=definitions[index].progress ?: AchievementProgress();val delta=e.number("delta")!!
                    val next=old.copy(currentValue=old.currentValue+delta,updatedAt=at,updateId=e.s("id")!!,completedAt=old.completedAt ?: at.takeIf { old.targetValue?.let { target->old.currentValue+delta>=target }==true })
                    definitions[index]=definitions[index].copy(progress=next)
                    history+=ProgressHistory(e.s("id")!!,id,at,old.currentValue,next.currentValue,next.targetValue,old.targetValue,delta,"units","Synthetic fixture",next.completedAt)
                }
            }
            val topics=mutableMapOf<String,MutableList<JsonObject>>();rows(source,"index_topics.jsonl") { topics.getOrPut(it.s("index_id")!!) { mutableListOf() }.add(it);names[it.s("id")!!]=it.s("title")!! }
            val indexes=mutableMapOf<String,IndexLedger>()
            arrayFile(source,"index_definitions.json").forEach { r ->
                val id=r.s("id")!!;val ts=topics.getValue(id);val sections=ts.map { it.s("section_id")!! }.distinct()
                val doc=IndexDocument(sections.mapIndexed { i,s->IndexTopic(s,"Synthetic section ${i+1}") }+ts.map { IndexTopic(it.s("id")!!,it.s("title")!!,it.s("section_id")) });IndexTracker.validate(doc)
                definitions+=AchievementDefinition(id,r.s("title")!!,"index","scheduled","Synthetic placeholder topics",createdDate=daily.keys.min().toString(),streakEnabled=r.s("card_type")=="HYBRID",progressEnabled=true)
                names[id]=r.s("title")!!;indexes[id]=IndexLedger(r.s("card_type")!!,doc)
            }
            rows(source,"index_events.jsonl") { e ->
                val id=e.s("index_id")!!;val topic=e.s("topic_id")!!;val old=indexes.getValue(id);val done=e.s("action")=="topic_completed";val at=e.time("time")!!
                indexes[id]=old.copy(states=old.states+(topic to IndexState(done,at,evidence="Synthetic fixture")),events=old.events+IndexEvent(e.s("id")!!,topic,names.getValue(topic),if(done) 1 else -1,at,e.s("date")!!,zone.id,evidence="Synthetic fixture"))
            }
            val indexProjection=indexes.getValue("index_ptfs_dev")
            val ptfs=definitions.indexOfFirst { it.id=="ach_ptfs_index" }
            val indexTarget=definitions[ptfs].progress!!.targetValue
            definitions[ptfs]=definitions[ptfs].copy(progress=AchievementProgress(indexProjection.completed().toDouble(),indexTarget,"topics",updatedAt=indexProjection.events.lastOrNull()?.atMs ?: 0))
            var progress=0.0
            indexProjection.events.forEach { e -> val next=progress+e.delta
                history+=ProgressHistory("projection:${e.id}","ach_ptfs_index",e.atMs,progress,next,indexTarget,indexTarget,e.delta.toDouble(),"topics","Synthetic Index replay");progress=next
            }
            val store=IndexTracker.project(AchievementStore(beganDate=daily.keys.min().toString(),definitions=definitions,events=events,days=days,progressHistory=history,progressMigration=1,indexes=indexes))
            arrayFile(source,"achievement_snapshots.json").forEach { snapshot ->
                val def=store.definitions.first { it.id==snapshot.s("id") }
                require((def.progress?.currentValue ?: 0.0)==snapshot.number("progress_current")) { "Progress snapshot mismatch: ${def.id}" }
                val confirmed=events.filter { it.achievementId==def.id }.map { LocalDate.parse(it.date) }.distinct().sorted()
                var current=0;var best=0;var previous: LocalDate?=null
                confirmed.forEach { day->current=if(previous?.plusDays(1)==day) current+1 else 1;best=maxOf(best,current);previous=day }
                if(previous==null || previous!!<date.minusDays(1)) current=0
                require(current.toLong()==snapshot.n("current_streak_days") && best.toLong()==snapshot.n("best_streak_days")) { "Streak snapshot mismatch: ${def.id}" }
                // Native record gap still compares against best completed history, not an unclosed current record.
            }
            arrayFile(source,"index_snapshots.json").forEach { require(store.indexes.getValue(it.s("id")!!).completed().toLong()==it.n("completed_topics")) }
            return store
        }
    }
}
private fun JsonObject.s(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.n(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0
private fun JsonObject.b(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull==true
private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
private fun JsonObject.ms(key: String): Long = n(key)*1000
private fun JsonObject.msOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull?.times(1000)
private fun JsonObject.time(key: String): Long? = s(key)?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
private fun objectFile(source: (String)->InputStream,name: String) = source(name).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
private fun arrayFile(source: (String)->InputStream,name: String) = source(name).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonArray.map { e->e.jsonObject } }
private fun rows(source: (String)->InputStream,name: String,consume: (JsonObject)->Unit) = source(name).bufferedReader().useLines { lines->lines.filter { it.isNotBlank() }.forEach { line->val row=Json.parseToJsonElement(line).jsonObject;require(row.b("synthetic")) { "Missing synthetic provenance: $name" };consume(row) } }

private fun feedCategory(r: JsonObject): ActivityCategory = when(r.s("category")) {
    "sessions"->ActivityCategory.SESSIONS;"gpt_sessions"->ActivityCategory.GPT;"gpt_cards"->ActivityCategory.CARDS
    "index_progress"->ActivityCategory.INDEX;"achievement_changes"->ActivityCategory.ACHIEVEMENTS
    "block_events"->ActivityCategory.BLOCKS;else->ActivityCategory.STATUS
}
