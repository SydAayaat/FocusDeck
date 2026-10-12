package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ActivityPresentationTest {
    private val zone=ZoneId.of("Europe/Berlin")
    private val date=LocalDate.of(2026,10,9)
    private val start=date.atTime(8,47).atZone(zone).toInstant().toEpochMilli()
    private fun plan()=Session(1,"real-attempt","Titles do not define outcomes","2026-10-09T00:00:00Z",listOf(
        Block("a","focus","Verify Skip block",4800,schedule=PlannedSchedule("2026-10-09T08:00:00+02:00","2026-10-09T09:20:00+02:00",zone.id)),
        Block("b","focus","Verify DONE",1200),Block("c","focus","End",1200),Block("d","focus","Unstarted",1200)))
    private fun report(mixed: Boolean=false): Report {
        val timer=TimerEngine();fun now(ms: Long)=ClockNow(start+ms,ms,1)
        timer.load(plan(),true,now(0));timer.done(now(89*60000L))
        if(mixed) { timer.skipBlock(now(90*60000L));timer.stop(now(91*60000L)) }
        else { timer.done(now(90*60000L));timer.done(now(91*60000L));timer.done(now(92*60000L)) }
        return timer.report(now(92*60000L))!!.copy(recordedTimezone=zone.id)
    }
    @Test fun uiAccuracyIsSeparateFromExistingCalibrationAndScores() {
        val comparison=durationComparison(80*60000L,89*60000L,true)!!
        assertEquals(89.8876404494,comparison.accuracy,1e-8)
        assertEquals(88.75,estimationMeasurement(80*60000L,89*60000L)!!.accuracy,1e-8)
        val r=report();val original=r.competition
        assertNotNull(reportDurationComparison(r));assertEquals(original,r.competition)
        assertNull(durationComparison(4800000,2000000,false))
        assertNull(durationComparison(null,0,true));assertNull(durationComparison(10,-1,true))
        assertEquals(100.0,durationComparison(0,0,true)!!.accuracy,0.0)
        assertEquals(0.0,durationComparison(0,1,true)!!.accuracy,0.0)
    }
    @Test fun terminatedAndMixedPlansDoNotGetFinalSessionAccuracyOrCompletionCredit() {
        val r=report(true)
        assertEquals(listOf("done","skipped","stopped"),r.blocks.map { it.outcome })
        assertNull(reportDurationComparison(r))
        assertEquals(1,r.blocks.count { it.outcome=="done" })
        assertTrue(r.outcomeSummary.contains("1 not started"))
        assertEquals("Completed",executionLabel(r.blocks[0].outcome))
        assertEquals("Skipped",executionLabel(r.blocks[1].outcome))
        assertEquals("Ended Early",executionLabel(r.blocks[2].outcome))
        assertNull(reportDurationComparison(report().copy(blocks=report().blocks.mapIndexed { i,b -> if(i==1) b.copy(timerMode="stopwatch",plannedMs=null) else b })))
    }
    @Test fun inheritedDelayNeedsAdjacentNonOverlappingSameAttemptEvidence() {
        val a=CalendarAnalytics.dataset(listOf(report())).activities.values.first { it.title=="Verify Skip block" }
        val previous=a.copy(id="before",start=start-3600000,end=start-7*60000L,schedule=PlannedSchedule("2026-10-09T07:00:00+02:00","2026-10-09T08:00:00+02:00",zone.id))
        val delay=delayAttribution(a,previous)!!
        assertEquals(40*60000L,delay.inherited);assertEquals(7*60000L,delay.additional)
        assertNull(delayAttribution(a,null));assertNull(delayAttribution(a,previous.copy(reportKey="other")))
        assertNull(delayAttribution(a,previous.copy(end=null)));assertNull(delayAttribution(a,previous.copy(provisional=true)))
        assertNull(delayAttribution(a,previous.copy(schedule=a.schedule)))
        assertNull(delayAttribution(a.copy(start=a.schedule!!.start-1),previous))
    }
    @Test fun sourceZoneActualAnchorAndDstHalfHoursArePreserved() {
        val r=report();val data=CalendarAnalytics.dataset(listOf(r))
        val buckets=timelineHalfHours(data,date,zone)
        val a=data.activities.values.first { it.title=="Verify Skip block" }
        assertTrue(buckets.first { a in it.activities }.start==date.atTime(8,30).atZone(zone).toInstant().toEpochMilli())
        assertEquals(48,buckets.size)
        assertEquals(46,timelineHalfHours(CalendarDataset(emptyMap(),emptyMap()),LocalDate.of(2026,3,29),zone).size)
        assertEquals(50,timelineHalfHours(CalendarDataset(emptyMap(),emptyMap()),LocalDate.of(2026,10,25),zone).size)
        assertEquals("08:47",compactClock(start,zone));assertEquals("06:47",compactClock(start,ZoneOffset.UTC));assertEquals("N/A",compactClock(null,zone))
    }
    @Test fun feedUsesRealReceiptsAndEventsWithoutInventedGptGeneration() {
        val r=report(true)
        val command=AchievementCommand(2,"upsert","receipt","2026-10-09T05:00:00Z",definition=AchievementDefinition("real","Actual Card","projects"))
        val store=AchievementStore(commands=mapOf(command.id to command))
        val records=ActivityHistory.records(listOf(r,r),store,zone)
        assertEquals(1,records.count { it.category==ActivityCategory.SESSIONS })
        assertEquals(r.events.size,records.count { it.reportKey==r.key && it.category!=ActivityCategory.SESSIONS })
        assertTrue(records.none { it.category==ActivityCategory.GPT })
        assertNotNull(records.find { it.id=="command:receipt" }!!.payload)
        assertTrue(ActivityHistory.records(emptyList(),store.copy(retiredCommands=setOf(command.id)),zone).isEmpty())
        assertTrue(ActivityHistory.records(emptyList(),store.copy(deletedIds=setOf("real")),zone).isEmpty())
        assertTrue(ActivityHistory.records(emptyList(),AchievementStore(),zone).isEmpty())
    }
    @Test fun filtersCombineCategoriesSearchInclusiveSourceDatesAndStableSort() {
        val records=ActivityHistory.records(listOf(report()),AchievementStore(),zone)
        val filtered=ActivityHistory.filter(records,setOf(ActivityCategory.SESSIONS,ActivityCategory.BLOCKS),"Verify",date,date,false)
        assertTrue(filtered.isNotEmpty());assertTrue(filtered.all { it.category in setOf(ActivityCategory.SESSIONS,ActivityCategory.BLOCKS) })
        assertEquals(filtered.sortedWith(compareByDescending<HistoryActivity> { it.at }.thenByDescending { it.sequence }.thenBy { it.id }),filtered)
        assertTrue(ActivityHistory.filter(records,emptySet(),"",date.plusDays(1),null,false).isEmpty())
        assertTrue(ActivityHistory.filter(records,setOf(ActivityCategory.GPT),"",null,null,false).isEmpty())
    }
    @Test fun equalTimestampFeedEventsUseRecordedSequenceAndSessionIsFirst() {
        val r=report()
        val records=ActivityHistory.filter(ActivityHistory.records(listOf(r),AchievementStore(),zone),emptySet(),"",null,null,false)
        assertEquals(ActivityCategory.SESSIONS,records.first().category)
        val same=records.filter { it.at==r.finishedAtMs && it.category!=ActivityCategory.SESSIONS }
        assertEquals(r.events.filter { it.atMs==r.finishedAtMs }.sortedByDescending { it.seq }.map { "event:${r.key}:${it.seq}" },same.map { it.id })
    }
    @Test fun liveEnginePhaseRefinesOnlyItsCurrentActivityAndCannotFinalizeAccuracy() {
        val timer=TimerEngine();timer.load(plan(),true,ClockNow(start,0,1));timer.pause(ClockNow(start+60000,60000,1))
        val live=timelineDataset(emptyList(),timer.state).activities.values.first { it.start!=null }
        assertEquals("paused",live.status);assertTrue(live.provisional)
        assertNull(durationComparison(live.plannedMs,live.focusMs,!live.provisional))
        assertEquals("paused",timer.state.phase);assertTrue(timer.state.results.isEmpty())
    }

    @Test fun homeFocusIncludesMeasuredSkippedWorkButNeverCountsAnActiveBreak() {
        val p=plan().copy(blocks=listOf(plan().blocks[0],Block("break","break","Rest",600)))
        val timer=TimerEngine();timer.load(p,true,ClockNow(start,0,1));timer.skipBlock(ClockNow(start+60000,60000,1))
        assertEquals(60000L,currentSessionFocus(timer.state,ClockNow(start+120000,120000,1)))
        assertEquals(0,timer.state.results.count { it.outcome=="done" })
    }

}
