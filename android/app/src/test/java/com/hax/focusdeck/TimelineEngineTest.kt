package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test

class TimelineEngineTest {
    private fun now(ms: Long) = ClockNow(1_000_000 + ms, ms, 1)
    private fun plan(auto: Boolean = true) = Session(1, "timeline", "Study", "2026-10-06T19:00:00Z",
        listOf(Block("a", "focus", "First", 60), Block("b", "break", "Second", 10)), auto)

    @Test fun multiplePausesKeepExactIntervalsAndTaskTransition() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        e.pause(now(10_000)); e.resume(now(20_000)); e.pause(now(30_000)); e.resume(now(35_000))
        e.done(now(45_000)); e.done(now(55_000))
        val r = e.report(now(55_000))!!; r.validateHistory()
        val pauses = TimelineAnalysis.intervals(r).filter { it.kind == "paused" }
        assertEquals(listOf(10_000L, 5_000L), pauses.map { it.durationMs })
        assertEquals(1_010_000L, pauses[0].startedAtMs); assertEquals(1_020_000L, pauses[0].finishedAtMs)
        assertEquals(2, TimelineAnalysis.metrics(r, 0).pauseCount)
        assertEquals(30_000L, r.blocks[0].activeMs)
        val transition = r.events.filter { it.atMs == 1_045_000L }
        assertEquals(listOf("block_done", "block_started"), transition.map { it.kind })
    }

    @Test fun doneWhilePausedClosesPauseWithoutInventingResume() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(10_000)); e.done(now(25_000)); e.stop(now(30_000))
        val r = e.report(now(30_000))!!; r.validateHistory()
        assertEquals(15_000L, TimelineAnalysis.intervals(r).single { it.kind == "paused" }.durationMs)
        assertEquals(0, r.events.count { it.kind == "resumed" })
        assertEquals(1, TimelineAnalysis.metrics(r).pauseCount)
    }

    @Test fun extensionAndOvertimeDoNotSplitUninterruptedStretch() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.tick(now(65_000)); e.extend(now(70_000)); e.stop(now(90_000))
        val r = e.report(now(90_000))!!; r.validateHistory()
        assertEquals(90_000L, TimelineAnalysis.metrics(r).longestActiveMs)
        assertEquals(1, TimelineAnalysis.metrics(r).extensionCount)
        assertEquals(1, r.events.count { it.kind == "overtime_observed" })
        assertEquals(1, TimelineAnalysis.intervals(r).count { it.kind == "active" })
    }

    @Test fun manualWaitingIsSeparateFromPausedAndActiveTime() {
        val e = TimerEngine(); e.load(plan(false), true, now(0)); e.done(now(10_000)); e.resume(now(25_000)); e.done(now(35_000))
        val r = e.report(now(35_000))!!; r.validateHistory()
        assertEquals(15_000L, TimelineAnalysis.intervals(r).single { it.kind == "waiting" }.durationMs)
        assertEquals(0L, r.pausedMs); assertEquals(20_000L, r.activeMs)
    }

    @Test fun rebootMarksGapInsteadOfMeasuringAcrossBoots() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.checkpoint(now(10_000))
        e.tick(ClockNow(2_000_000, 500, 2)); e.resume(ClockNow(2_005_000, 5_500, 2)); e.stop(ClockNow(2_010_000, 10_500, 2))
        val r = e.report(now(0))!!; r.validateHistory()
        assertEquals("partial", r.timelineCoverage)
        assertNull(TimelineAnalysis.metrics(r).pauseCount)
        assertEquals(15_000L, r.activeMs)
        assertTrue(TimelineAnalysis.intervals(r).all { it.boot == 2 })
    }

    @Test fun upgradedActiveSessionHasPartialTimeline() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        e.state = e.state.copy(events = emptyList(), timelineComplete = false)
        e.pause(now(10_000)); e.resume(now(20_000)); e.stop(now(30_000))
        val r = e.report(now(30_000))!!; r.validateHistory()
        assertEquals("partial", r.timelineCoverage)
        assertEquals(10_000L, TimelineAnalysis.intervals(r).first { it.kind == "paused" }.durationMs)
        assertNull(TimelineAnalysis.metrics(r).longestActiveMs)
    }

    @Test fun eventLimitKeepsTimerWorkingAndMarksPartial() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        repeat(1_001) { i -> e.pause(now(i * 2L + 1)); e.resume(now(i * 2L + 2)) }
        e.stop(now(3_000))
        val r = e.report(now(3_000))!!; r.validateHistory()
        assertEquals(2_000, r.events.size); assertEquals("partial", r.timelineCoverage)
        assertEquals(1_001L, r.pausedMs); assertEquals(1_999L, r.activeMs)
    }

    @Test fun wallClockRollbackDoesNotChangeIntervalDuration() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(10_000))
        e.resume(ClockNow(900_000, 20_000, 1)); e.stop(ClockNow(905_000, 25_000, 1))
        val r = e.report(now(25_000))!!; r.validateHistory()
        val pause = TimelineAnalysis.intervals(r).single { it.kind == "paused" }
        assertTrue(pause.finishedAtMs < pause.startedAtMs); assertEquals(10_000L, pause.durationMs)
        assertEquals(15_000L, r.activeMs)
    }
}
