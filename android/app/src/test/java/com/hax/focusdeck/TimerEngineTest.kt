package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    private fun now(ms: Long, boot: Int = 1) = ClockNow(1_000_000 + ms, ms, boot)
    private fun plan(auto: Boolean = true) = Session(1, "test", "Index", "2026-10-06T19:00:00Z",
        listOf(Block("b1", "focus", "Topic", 60), Block("b2", "break", "Break", 10)), auto)

    @Test fun doneEarlyPreservesPlanAndActual() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(35000))
        assertEquals(1, e.state.index); assertEquals(60000L, e.state.results[0].plannedMs)
        assertEquals(35000L, e.state.results[0].activeMs); assertEquals("done", e.state.results[0].outcome)
    }
    @Test fun pauseExcludesPausedTime() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(10000))
        assertEquals(50000L, e.remaining(now(40000))); e.resume(now(40000)); e.done(now(50000))
        assertEquals(20000L, e.state.results[0].activeMs); assertEquals(30000L, e.state.results[0].pausedMs)
    }
    @Test fun elapsedSleepStaysOnSameBlockInOvertime() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.tick(now(100000))
        assertEquals("active", e.state.phase); assertEquals(0, e.state.index)
        assertTrue(e.state.results.isEmpty()); assertEquals(40000L, e.overtime(now(100000)))
        assertNull(e.report(now(100000)))
        e.done(now(100000)); assertEquals(1, e.state.index)
        assertEquals(100000L, e.state.results[0].activeMs); assertEquals(40000L, e.state.results[0].overtimeMs)
    }
    @Test fun processRecreationKeepsMonotonicTimer() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.checkpoint(now(10000))
        val recreated = TimerEngine(WireJson.decodeFromString<DeckState>(WireJson.encodeToString(DeckState.serializer(), e.state)))
        assertEquals(30000L, recreated.remaining(now(30000)))
        recreated.tick(now(95000)); assertEquals(35000L, recreated.overtime(now(95000)))
        assertEquals(0, recreated.state.index)
    }
    @Test fun wallClockChangesDoNotAlterRemaining() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        assertEquals(50000L, e.remaining(ClockNow(999999999, 10000, 1)))
    }
    @Test fun rebootRecoversPausedWithoutInventingFocus() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.checkpoint(now(20000))
        e.tick(ClockNow(2_000_000, 1000, 2)); assertEquals("paused", e.state.phase)
        assertEquals(40000L, e.remaining(ClockNow(2_010_000, 11000, 2)))
    }
    @Test fun manualAdvanceWaitsForStart() {
        val e = TimerEngine(); e.load(plan(false), true, now(0)); e.done(now(100000))
        assertEquals("between", e.state.phase); assertEquals(1, e.state.results.size)
        e.resume(now(110000)); e.tick(now(120000)); assertEquals("active", e.state.phase)
        e.done(now(125000)); assertEquals("complete", e.state.phase)
    }
    @Test fun readyDoesNotTick() {
        val e = TimerEngine(); e.load(plan(), false, now(0)); e.tick(now(100000))
        assertEquals("ready", e.state.phase); assertEquals(60000L, e.remaining(now(100000)))
    }
    @Test fun stoppingIncludesPartialFocus() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.stop(now(10000))
        val r = e.report(now(10000))!!; assertEquals("stopped", r.status); assertEquals(10000L, r.activeMs)
        assertEquals("stopped", r.blocks[0].outcome)
    }
    @Test fun extensionRetainsOriginalPlan() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.extend(now(10000)); e.done(now(70000))
        assertEquals(60000L, e.state.results[0].plannedMs); assertEquals(360000L, e.state.results[0].budgetMs)
    }
    @Test fun duplicateBlockIdsAreRejected() {
        val p = plan().copy(blocks = listOf(Block("x", "focus", "A", 5), Block("x", "focus", "B", 5)))
        try { p.validate(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
    }
    @Test fun commandReplayAfterRecreationCannotAdvanceTwice() {
        val first = CommandProcessor.apply(DeckState(), emptyMap(), "start-1", "start", plan(), now(0))
        val done = CommandProcessor.apply(first.state, first.receipts, "done-1", "next", null, now(10000))
        val persisted = StoredDeck("phone", state = done.state, acknowledgements = done.receipts)
        val restored = WireJson.decodeFromString<StoredDeck>(WireJson.encodeToString(StoredDeck.serializer(), persisted))
        val replay = CommandProcessor.apply(restored.state, restored.acknowledgements, "done-1", "next", null, now(12000))
        assertEquals(1, replay.state.index); assertEquals(1, replay.state.results.size)
        assertEquals(done.ack, replay.ack); assertEquals(done.state, replay.state)
    }
    @Test fun rejectedCommandAlsoHasStableReceipt() {
        val rejection = CommandProcessor.apply(DeckState(), emptyMap(), "p1", "pause", null, now(0))
        assertFalse(rejection.ack.ok)
        val start = CommandProcessor.apply(rejection.state, rejection.receipts, "s1", "start", plan(), now(1000))
        val replay = CommandProcessor.apply(start.state, start.receipts, "p1", "pause", null, now(2000))
        assertFalse(replay.ack.ok); assertEquals("active", replay.state.phase)
    }
    @Test fun pauseFreezesOvertimeAndDoneStillWorks() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(90000))
        assertEquals(30000L, e.overtime(now(190000)))
        e.resume(now(190000)); e.done(now(200000))
        assertEquals(40000L, e.state.results[0].overtimeMs)
        assertEquals(100000L, e.state.results[0].pausedMs)
    }
    @Test fun extensionCanClearRedOvertimeState() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.tick(now(90000))
        assertTrue(e.isOvertime(now(90000))); e.extend(now(90000))
        assertFalse(e.isOvertime(now(90000))); assertFalse(e.state.overtimeNotified)
        assertEquals(270000L, e.remaining(now(90000)))
    }
    @Test fun breakAlsoRequiresDoneAndFinalReportKeepsPlanId() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(65000))
        e.tick(now(90000)); assertEquals(1, e.state.index); assertEquals("active", e.state.phase)
        e.done(now(90000)); val r = e.report(now(90000))!!
        assertEquals("test", r.sessionId); assertEquals(20000L, r.overtimeMs)
        assertEquals(90000L, r.activeMs); assertEquals("complete", r.status)
    }
    @Test fun exactDeadlineDoesNotCreateResultOrAdvance() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.tick(now(60000))
        assertTrue(e.isOvertime(now(60000))); assertEquals(0L, e.overtime(now(60000)))
        assertEquals(0, e.state.index); assertTrue(e.state.results.isEmpty())
        e.done(now(60000)); assertEquals("done", e.state.results[0].outcome)
    }
}
