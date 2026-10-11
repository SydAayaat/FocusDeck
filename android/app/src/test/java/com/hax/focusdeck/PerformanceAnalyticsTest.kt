package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.File
import java.time.*

class PerformanceAnalyticsTest {
    @Test fun sharedGoldenScoresHaveEquivalentWeightsMissingValuesAndCoverage() {
        val file=listOf("../../protocol/fixtures/performance-score.json","protocol/fixtures/performance-score.json").map(::File).first { it.exists() }
        for(case in Json.parseToJsonElement(file.readText()).jsonArray) {
            val obj=case.jsonObject;val rows=Json.decodeFromString<List<PerformanceRecord>>(obj.getValue("records").toString());val actual=PerformanceAnalytics.score(rows)
            val values=mapOf("score" to actual.score,"completion" to actual.completion,"punctuality" to actual.punctuality,"efficiency" to actual.efficiency)
            obj.getValue("expected").jsonObject.forEach { (key,value)->if(value==JsonNull) assertNull(values[key]) else assertEquals(obj.getValue("name").jsonPrimitive.content,value.jsonPrimitive.double,values[key]!!,.0000001) }
        }
    }
    @Test fun estimationIsSignedCappedAndNeverClaimsAStopwatchTarget() {
        assertEquals(-30000L,estimationMeasurement(60000,30000)!!.signed_error_ms)
        assertEquals("plan overestimated duration",estimationMeasurement(60000,30000)!!.interpretation)
        assertEquals(0.0,estimationMeasurement(60000,180000)!!.accuracy,0.0)
        assertNull(estimationMeasurement(0,10));assertNull(estimationMeasurement(null,10))
    }
    private fun at(value: String)=Instant.parse(value).toEpochMilli()
    private fun data(schedule: PlannedSchedule,mode: String="countdown",planned: Long?=25000): CalendarDataset {
        val a=CalendarActivity("a","attempt","Task","learning",null,null,schedule,null,null,null,null,null,null,planned,"Not started",ZoneId.of("Europe/Berlin"),"focus",mode,false,LocalDate.of(2026,10,24),"P")
        return CalendarDataset(emptyMap(),mapOf("a" to a))
    }
    @Test fun sourceZoneDstAndPlannedCrossMidnightWeightsConserveTotals() {
        val s=PlannedSchedule("2026-10-24T23:00:00+02:00","2026-10-26T00:00:00+01:00","Europe/Berlin")
        val rows=PerformanceAnalytics.records(data(s),at("2026-10-30T00:00:00Z"))
        assertEquals(25000L,rows.sumOf { it.planned_ms!! });assertEquals(listOf("2026-10-24","2026-10-25"),rows.map { it.date });assertEquals(24039L,rows.last().planned_ms)
        assertTrue(PerformanceAnalytics.records(data(s,"stopwatch",null)).isEmpty())
    }
    @Test fun futureWorkIsExcludedCurrentPlanIsProvisionalAndZeroRemainsValid() {
        val s=PlannedSchedule("2026-10-24T10:00:00Z","2026-10-24T11:00:00Z","UTC")
        val before=PerformanceAnalytics.score(PerformanceAnalytics.records(data(s),at("2026-10-24T09:00:00Z")))
        assertNull(before.score);assertEquals(1,before.future_blocks)
        val after=PerformanceAnalytics.score(PerformanceAnalytics.records(data(s),at("2026-10-24T12:00:00Z")))
        assertEquals(0.0,after.score!!,0.0);assertTrue(after.provisional)
    }
    @Test fun overtimeAndPausesStayInsideTheirMeasuredComponentsAcrossMidnight() {
        val start=at("2026-10-08T23:59:40Z");fun now(ms: Long)=ClockNow(start+ms,ms,1)
        val timer=TimerEngine();timer.load(Session(1,"attempt","Focus","2026-10-08T12:00:00Z",listOf(Block("a","focus","Task",20))),true,now(0))
        timer.pause(now(10000));timer.resume(now(20000));timer.done(now(40000))
        val r=timer.report(now(40000))!!.copy(recordedTimezone="UTC");val ds=CalendarAnalytics.dataset(listOf(r,r)).days.values
        assertEquals(30000L,ds.sumOf { it.focusMs });assertEquals(10000L,ds.sumOf { it.overtimeMs });assertEquals(10000L,ds.sumOf { it.pausedMs });assertEquals(1,ds.sumOf { it.pauseCount })
        assertEquals(10000L,ds.sumOf { it.hourPauses.sum() });assertTrue(ds.all { it.overtimeMs<=it.focusMs })
        val old=CalendarAnalytics.dataset(listOf(r.copy(events=emptyList(),timelineCoverage="unavailable")))
        assertEquals(10000L,old.days.values.sumOf { it.overtimeUnlocatedMs })
    }
    @Test fun comparisonRespectsCalendarMonthsMissingEvidenceAndAllTime() {
        val d=CalendarDataset(emptyMap(),emptyMap());val anchor=LocalDate.of(2024,3,15)
        val p=PerformanceAnalytics.report(d,HistoryPeriod.MONTH,anchor)
        assertEquals(listOf("2024-02-01","2024-02-29"),p.comparison!!.previous_bounds);assertNull(p.comparison.focus_ms.percentage_change)
        assertNull(PerformanceAnalytics.report(d,HistoryPeriod.ALL,anchor).comparison)
        val old=p.copy(focus_ms=0,recorded_days=1);val current=p.copy(focus_ms=30000,recorded_days=1)
        assertNull(PerformanceAnalytics.compare(current,old).focus_ms.percentage_change);assertEquals(30000.0,PerformanceAnalytics.compare(current,old).focus_ms.absolute_difference!!,0.0)
    }
    @Test fun calendarMetricsKeepUnknownZeroPartialAndFutureSeparate() {
        val date=LocalDate.of(2026,10,1);val day=CalendarDay(date,focusMs=3600000,hasRecord=true)
        assertEquals(1.0,calendarMetricValue(day,null,CalendarMetric.FOCUS_HOURS)!!,0.0)
        assertNull(calendarMetricValue(day,null,CalendarMetric.EXECUTION_SCORE))
        assertEquals("no valid plan",calendarEvidenceState(date,day,null,CalendarMetric.EXECUTION_SCORE,date))
        day.focusMs=0;assertEquals("valid zero",calendarEvidenceState(date,day,null,CalendarMetric.FOCUS_HOURS,date))
        day.incomplete=true;assertEquals("incomplete records",calendarEvidenceState(date,day,null,CalendarMetric.FOCUS_HOURS,date))
        assertEquals("future",calendarEvidenceState(date.plusDays(1),null,null,CalendarMetric.FOCUS_HOURS,date))
    }
    @Test fun immutableSnapshotRetainsUnstartedCountdownAndNullStopwatchDurations() {
        val plan=Session(2,"91ad1377-3240-4211-9087-4c68e1195276","Plan","2026-10-01T10:00:00Z",listOf(Block("a","focus","A",60,timerMode="countdown"),Block("b","focus","B",timerMode="stopwatch")))
        val captured=plan.scheduleSnapshot();assertEquals(60000L,captured.activities[0].plannedMs);assertNull(captured.activities[1].plannedMs)
        assertEquals(60000L,captured.activities[0].plannedMs);assertEquals(120000L,plan.copy(blocks=listOf(plan.blocks[0].copy(durationSeconds=120))).scheduleSnapshot().activities[0].plannedMs)
    }
    @Test fun legacyUnstartedPlanGapIsPartialInsteadOfInventingCompleteCoverage() {
        val start=at("2026-10-01T10:00:00Z");val timer=TimerEngine();timer.load(Session(1,"old","Old","2026-10-01T09:00:00Z",listOf(Block("a","focus","A",60),Block("b","focus","B",60),Block("c","focus","C",60))),true,ClockNow(start,0,1));timer.done(ClockNow(start+60000,60000,1));timer.stop(ClockNow(start+61000,61000,1))
        val r=timer.report(ClockNow(start+61000,61000,1))!!.copy(scheduleSnapshot=null)
        val x=PerformanceAnalytics.report(CalendarAnalytics.dataset(listOf(r)),HistoryPeriod.DAY,LocalDate.of(2026,10,1)).execution
        assertTrue(x.partial);assertEquals(1,x.missing_duration_blocks)
    }

    @Test fun archivedAttemptWinsOverStaleLiveCheckpointWithoutProvisionalLeak() {
        val start=at("2026-10-01T10:00:00Z");val timer=TimerEngine();timer.load(Session(1,"stale","Focus","2026-10-01T09:00:00Z",listOf(Block("a","focus","A",60))),true,ClockNow(start,0,1))
        val live=timer.state;timer.done(ClockNow(start+60000,60000,1));val report=timer.report(ClockNow(start+60000,60000,1))!!
        val data=CalendarAnalytics.dataset(listOf(report),live)
        assertEquals(report.focusedMs,data.days.values.sumOf { it.focusMs });assertTrue(data.activities.values.none { it.provisional })
    }

}
