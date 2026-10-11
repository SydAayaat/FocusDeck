package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarAnalyticsTest {
    private val zone=ZoneId.of("UTC")
    private val start=Instant.parse("2026-10-08T23:59:40Z").toEpochMilli()
    private fun report(schedule: PlannedSchedule? = null): Report {
        val e=TimerEngine()
        val plan=Session(1,"calendar","Study","2026-10-08T12:00:00Z",listOf(Block("a","focus","First",60,schedule=schedule),Block("b","break","Second",10)))
        fun now(ms: Long)=ClockNow(start+ms,ms,1)
        e.load(plan,true,now(0));e.pause(now(10000));e.resume(now(20000));e.pause(now(30000));e.resume(now(35000));e.done(now(45000));e.done(now(55000))
        return e.report(now(55000))!!.copy(recordedTimezone="UTC")
    }
    @Test fun midnightAndReplayConserveMeasuredTotals() {
        val r=report();val d=CalendarAnalytics.dataset(listOf(r,r)).days.values
        assertEquals(2,d.size);assertEquals(30000L,d.sumOf { it.focusMs });assertEquals(15000L,d.sumOf { it.pausedMs });assertEquals(10000L,d.sumOf { it.breakMs });assertEquals(55000L,d.sumOf { it.trackedMs });assertEquals(2,d.sumOf { it.done })
        assertTrue(d.all { it.hourComplete });assertEquals(30000L,d.sumOf { it.sessionFocus.values.sum() })
    }
    @Test fun dstUsesTwentyThreeAndTwentyFiveElapsedHours() {
        val z=ZoneId.of("Europe/Berlin")
        for((date,hours) in listOf("2026-03-29" to 23,"2026-10-25" to 25)) {
            val (a,b)=CalendarAnalytics.bounds(LocalDate.parse(date),z);assertEquals(hours*3600000L,b-a)
            val pieces=CalendarAnalytics.split(a,b,b-a,z);assertEquals(hours,pieces.size);assertEquals(b-a,pieces.sumOf { it.duration })
            assertEquals((if(hours==23) 0 else 2)*3600000L,pieces.filter { it.hour==2 }.sumOf { it.duration })
        }
    }
    @Test fun scheduleSnapshotsDoNotGuessDurationOnlyPlans() {
        val r=report();assertNull(r.scheduleSnapshot!!.activities.first().schedule)
        val planned=PlannedSchedule("2026-10-08T23:59:48Z");val scheduled=report(planned)
        assertEquals(-8000L,CalendarAnalytics.dataset(listOf(scheduled)).activities.values.first().punctuality)
        assertNull(CalendarAnalytics.sessionPunctuality(scheduled))
        val late=report(PlannedSchedule("2026-10-08T23:59:32Z"));assertEquals(8000L,CalendarAnalytics.dataset(listOf(late)).activities.values.first().punctuality)
    }
    @Test fun unlocatedLegacyTotalsNeverSupplyHourBuckets() {
        val r=report().copy(events=emptyList(),timelineCoverage="unavailable");val d=CalendarAnalytics.dataset(listOf(r)).days.values
        assertEquals(55000L,d.sumOf { it.unlocatedMs });assertTrue(d.none { it.hourComplete });assertEquals(0L,d.sumOf { it.hours.sum() })
    }
    @Test fun partialCoverageNeverClaimsFullyObservedClockHours() {
        val d=CalendarAnalytics.dataset(listOf(report().copy(timelineCoverage="partial"))).days.values
        assertEquals(0L,d.sumOf { it.unlocatedMs });assertTrue(d.none { it.hourComplete });assertEquals(30000L,d.sumOf { it.hours.sum() })
    }
    @Test fun recordedTimezoneKeepsSourceDate() {
        val r=report().copy(recordedTimezone="America/Los_Angeles");assertEquals(setOf(LocalDate.parse("2026-10-08")),CalendarAnalytics.dataset(listOf(r)).days.keys)
    }
    @Test fun invalidScheduleDoesNotInventAnEndpoint() {
        PlannedSchedule("2026-10-09T10:00:00Z").validate()
        assertNull(PlannedSchedule("2026-10-09T10:00:00Z").end)
        try { PlannedSchedule("2026-10-09T10:00:00Z","2026-10-09T09:00:00Z").validate();fail("Reversed interval") } catch(expected: IllegalArgumentException) {}
    }
    @Test fun synchronizedOrderIsIndependentOfDeviceDimensionsAndReplaySafe() {
        val now=Instant.parse("2026-10-09T12:00:00Z").toEpochMilli();val base=AchievementEngine.seed(now,zone)
        val command=AchievementCommand(6,"layout_order","order-1",Instant.ofEpochMilli(now).toString(),order=listOf("gym","german"),expected_store_revision=base.revision)
        val next=AchievementEngine.apply(base,command,now,zone);assertEquals(listOf("gym","german"),next.cardOrder);assertEquals(base.cardSizes,next.cardSizes)
        assertEquals(next,AchievementEngine.apply(next,command,now,zone))
        try { AchievementEngine.apply(next,command.copy(id="stale"),now,zone);fail("Stale revision") } catch(expected: IllegalArgumentException) {}
        val reset=command.copy(id="reset",order=emptyList(),expected_store_revision=next.revision);assertEquals(emptyList<String>(),AchievementEngine.apply(next,reset,now,zone).cardOrder)
    }
}
