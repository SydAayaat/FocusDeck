package com.hax.focusdeck

import java.time.*

/** Only compiled into uiqa/visualtest. No Context, repository, preferences, service or network. */
internal enum class VisualTestState { REFERENCE, EMPTY, PARTIAL, COMPLETED, INCOMPLETE, LONG_TEXT, POSITIVE, NEGATIVE, LARGE, ZERO, ACTIVE, PAUSED, STOPPED, MIXED }
internal class VisualTestDataProvider(val state: VisualTestState = VisualTestState.REFERENCE) {
    companion object {
        fun threeYears(source: (String)->java.io.InputStream): SyntheticDataset = SyntheticDataset.load(source)
    }
    val date = UiQaFixtures.date
    val zone = UiQaFixtures.zone
    val now: Long get() = sessionState?.let { maxOf(UiQaFixtures.now,it.anchorWall,it.finishedAtMs) } ?: UiQaFixtures.now
    val sessionState: DeckState? = if(state in setOf(VisualTestState.ACTIVE,VisualTestState.PAUSED,VisualTestState.STOPPED,VisualTestState.MIXED)) {
        val start=date.atTime(8,47).atZone(zone).toInstant().toEpochMilli()
        val plan=Session(1,"visual-current","PTfS · Phase 1 · Core Theory Rapid Pass","2026-10-09T00:00:00Z",listOf(
            Block("one","focus","Core Theory Rapid Pass",4800,schedule=PlannedSchedule(date.atTime(8,0).atZone(zone).toInstant().toString(),date.atTime(9,20).atZone(zone).toInstant().toString(),zone.id)),
            Block("two","focus","Chapter 2 · Single-Core",7200,schedule=PlannedSchedule(date.atTime(10,0).atZone(zone).toInstant().toString(),date.atTime(12,0).atZone(zone).toInstant().toString(),zone.id)),
            Block("three","break","Lunch Break",1800,schedule=PlannedSchedule(date.atTime(12,0).atZone(zone).toInstant().toString(),date.atTime(12,30).atZone(zone).toInstant().toString(),zone.id)),
            Block("four","focus","Unstarted Work",2400)),schedule=PlannedSchedule(date.atTime(8,0).atZone(zone).toInstant().toString(),date.atTime(13,10).atZone(zone).toInstant().toString(),zone.id))
        val timer=TimerEngine();fun at(ms: Long)=ClockNow(start+ms,ms,1)
        timer.load(plan,true,at(0));timer.pause(at(31*60000L));timer.resume(at(36*60000L))
        when(state) {
            VisualTestState.PAUSED -> timer.pause(at(40*60000L))
            VisualTestState.STOPPED -> timer.stop(at(50*60000L))
            VisualTestState.MIXED -> { timer.done(at(50*60000L));timer.skipBlock(at(56*60000L));timer.stop(at(60*60000L)) }
            else -> timer.pause(at(40*60000L)).also { timer.resume(at(40*60000L)) }
        }
        timer.state
    } else null
    val partialReport=sessionState?.takeIf { it.phase=="stopped" }?.let { st -> TimerEngine(st).report(ClockNow(st.finishedAtMs,60*60000L,1))?.copy(recordedTimezone=zone.id) }
    val reports = if(state in setOf(VisualTestState.EMPTY, VisualTestState.ZERO)) emptyList() else partialReport?.let(::listOf) ?: UiQaFixtures.reports()
    val calendar: CalendarDataset = if(state==VisualTestState.EMPTY) CalendarDataset(emptyMap(),emptyMap()) else {
        val original=timelineDataset(reports,sessionState)
        val days=original.days.toMutableMap()
        // Synthetic but internally located month evidence. Missing dates stay missing in EMPTY.
        for(n in 1..31) {
            val d=date.withDayOfMonth(n)
            days.putIfAbsent(d,CalendarDay(d,zones=linkedSetOf(zone),hasRecord=true,hourCoverage=true).apply {
                repeat(24) { h -> hours[h]=if(state==VisualTestState.ZERO) 0 else ((n*7+h*3)%50)*60000L }
                focusMs=hours.sum()
            })
        }
        if(state!=VisualTestState.ZERO) {
            // Source-day observations drive the same Day/Week/Month/Year grouping as production.
            // No reports/repository are mutated; future observations remain unavailable.
            fun addStarts(d: LocalDate,minutes: List<Int>) {
                val target=days.getOrPut(d) { CalendarDay(d,zones=linkedSetOf(zone),hasRecord=true,hourCoverage=true) }
                target.punctuality.clear()
                target.punctuality+=minutes.map { (if(state==VisualTestState.POSITIVE) kotlin.math.abs(it) else if(state==VisualTestState.NEGATIVE) -kotlin.math.abs(it) else it)*60000L }
            }
            for(month in 1..9) addStarts(date.withMonth(month).withDayOfMonth(9),listOf(month-6,-month))
            for(offset in 0..8) {
                val d=date.minusDays(offset.toLong())
                addStarts(d,listOf(listOf(-6,2,-15,-3,-8,-12,5,-2,-9)[offset]))
            }
        }
        if(state==VisualTestState.PARTIAL) days[date]?.apply { incomplete=true;unlocatedMs+=60000;focusMs+=60000 }
        CalendarDataset(days,original.activities)
    }
    val store: AchievementStore = when(state) {
        VisualTestState.EMPTY -> AchievementStore()
        else -> UiQaFixtures.store().let { base -> IndexTracker.project(base.copy(definitions=base.definitions.map { d ->
            when {
                d.id=="german" -> d.copy(streakEnabled=state!=VisualTestState.COMPLETED,progress=AchievementProgress(when(state) {
                    VisualTestState.COMPLETED -> 1250.0
                    VisualTestState.ZERO -> 0.0
                    else -> 720.0
                },1250.0,"words",updatedAt=now-86400000L))
                state==VisualTestState.LONG_TEXT -> d.copy(title=d.title+" · a long descriptive title that must remain readable")
                else -> d
            }
        },cardIcons=base.cardIcons + mapOf("porn_free" to "flame","custom_compute" to "settings","focus_3h" to "target","ptfs" to "book-open").mapValues { (_,name) -> CardIconAssignment(CardIcon("builtin",name),now,"visual-icon") })) }
    }
    val homeLatest: Report? = partialReport ?: if(reports.isEmpty()) null else {
        val started=date.atTime(4,7).atZone(zone).toInstant().toEpochMilli()
        val timer=TimerEngine()
        timer.load(Session(1,"visual-home-last","PTfS · Phase 1 · Core Theory","2026-10-09T00:00:00Z",listOf(Block("last","focus","Core Theory",7200))),true,ClockNow(started,0,1))
        timer.stop(ClockNow(started+89*60000L,89*60000L,1))
        timer.report(ClockNow(started+89*60000L,89*60000L,1))!!.copy(recordedTimezone=zone.id)
    }
    val upcoming = sessionState?.takeIf { it.phase !in setOf("stopped","complete") }?.session
    val home: HomePresentation? = if(state==VisualTestState.EMPTY) null else HomePresentation(
        if(state==VisualTestState.ZERO) 0 else if(state==VisualTestState.LARGE) 1234*3600000L else 252*60000L,
        3,14,1361*60000L,34104*60000L,if(state==VisualTestState.NEGATIVE) -18.0 else 18.0)

    /** Intentionally illustrative display state, never passed into real calculation assertions.
     * Mockups mix 18 sessions/42 DONE with 5/6 planning samples. Keep those display concepts separate.
     * German's contradictory 0/1250 + filled arc is corrected to genuine 720/1250 progress.
     */
    fun analytics(period: HistoryPeriod,anchor: LocalDate): AnalyticsPresentation? {
        if(state in setOf(VisualTestState.EMPTY,VisualTestState.PARTIAL,VisualTestState.ZERO)) return null
        val actual=dashboardAnalytics(calendar,reports,period,anchor,store,now)
        val execution=actual.performance.execution.copy(score=86.0,completion=5.0/6,punctuality=5.0/6,efficiency=.92,partial=false,provisional=false)
        val e=actual.copy(focus=252*60000L,breaks=42*60000L,tracked=(252*60000L/.76).toLong(),done=42,
            plannedCount=50,punctual=actual.punctual,score=86.0,
            performance=actual.performance.copy(execution=execution),estimation=EstimationEvidence(80*60000L,89*60000L),
            projects=listOf("PTS · Phase 1 · Core Theory","PTS · Chapter 2 · Single-Core","PTS · Chapter 3 · Parallel","Development · ProtoK","Review & Notes").mapIndexed { i,title ->
                DashboardProject(if(state==VisualTestState.LONG_TEXT) title+" · Extended interdisciplinary planning and analysis" else title,
                    listOf(182,121,134,121,121)[i]*60000L,emptySet(),CompletionContribution(.18,.14,.32,1.0,"share"))
            })
        val hourly=listOf(2,1,3,6,12,22,31,44,58,31,22,18,16,28,19,27,18,11,5,2,1,2,4,1)
        val bars=when(period) {
            HistoryPeriod.WEEK -> listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").zip(listOf(3.0,2.0,4.5,6.0,3.0,2.5,1.7).map { it*3600000 })
            HistoryPeriod.YEAR -> listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec").zip(listOf(32,40,48,60,48,36,30,38,44,48,40,33).map { it*3600000.0 })
            else -> null
        }
        return AnalyticsPresentation(e, bars, hourly.mapIndexed { h,n -> "%02d".format(java.util.Locale.ROOT,h) to n*60000.0*(if(period==HistoryPeriod.DAY) 1 else 6) },
            if(period==HistoryPeriod.WEEK) 4.5*3600000 else null, if(period==HistoryPeriod.DAY) 30*60000.0 else 2*3600000.0,
            TelemetryPresentation(
                if(state==VisualTestState.LONG_TEXT) "Recorded focus varies across projects. ".repeat(18) else "Strong output rhythm this week. Focus duration grew +18% with an 84/100 execution score. Deep work clustering in the 09:00–12:00 window accounted for 48% of successful outputs.",
                "• 5/7 days with >2h focus\n• Punctuality rate 92%", "• 8m delay on Fri start\n• 3 unplanned break spikes"),
            listOf("FOCUS" to if(state==VisualTestState.LARGE) "1234h 56m" else "4h 12m","SESSIONS" to "18","BLOCKS DONE" to "42","VARIANCE" to signedStartDelta(actual.punctual.takeIf { it.isNotEmpty() }?.average())),
            focusCaption=if(period==HistoryPeriod.YEAR) "Total 611h" else "Total 22h 41m",
            hourlyCaption=if(period==HistoryPeriod.DAY) "Total 4h 12m" else "Total 22h 41m",
            projectShares=e.projects.mapIndexed { i,p -> p.title to listOf(32,21,14,12,10)[i] }.toMap(),
            kpiTrends=mapOf("FOCUS" to 12.0,"SESSIONS" to 6.0,"BLOCKS DONE" to 17.0),scoreTrend=6.0,
            measureCaptions=mapOf("completion" to "5/6","punctuality" to "5/6","efficiency" to "1h 12m"))
    }
}
