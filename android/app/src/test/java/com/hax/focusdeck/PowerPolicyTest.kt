package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test

class PowerPolicyTest {
    @Test fun idleAndCompletedSessionsDoNotPollOrCheckpoint() {
        for (phase in listOf("idle", "ready", "between", "complete", "stopped")) {
            for (visible in listOf(true, false)) {
                assertNull(PowerPolicy.tickMs(phase, visible))
                assertNull(PowerPolicy.checkpointMs(phase, visible))
            }
        }
    }

    @Test fun backgroundWorkIsReducedAndPauseIsSlow() {
        assertEquals(1_000L, PowerPolicy.tickMs("active", true))
        assertEquals(30_000L, PowerPolicy.tickMs("active", false))
        assertEquals(10_000L, PowerPolicy.checkpointMs("active", true))
        assertEquals(30_000L, PowerPolicy.checkpointMs("active", false))
        assertEquals(60_000L, PowerPolicy.tickMs("paused", true))
        assertEquals(60_000L, PowerPolicy.checkpointMs("paused", false))
    }

    @Test fun reportRetriesStayPromptWithSlowerIdleStatus() {
        assertEquals(15_000L, PowerPolicy.syncMs("active", true, false))
        assertEquals(60_000L, PowerPolicy.syncMs("active", false, false))
        assertEquals(60_000L, PowerPolicy.syncMs("idle", true, false))
        assertEquals(15_000L, PowerPolicy.syncMs("complete", false, true))
        assertEquals(120L, PowerPolicy.retryCapSeconds(false))
    }

    @Test fun sparseTicksKeepExactOvertimeAndOnlyDoneAdvances() {
        val session = Session(1, "sparse", "Power test", "2026-10-06T19:00:00Z",
            listOf(Block("b1", "focus", "First", 5), Block("b2", "break", "Second", 10)))
        val engine = TimerEngine()
        engine.load(session, true, ClockNow(1_000_000, 0, 1))
        engine.tick(ClockNow(1_090_123, 90_123, 1))
        assertEquals(0, engine.state.index)
        assertTrue(engine.state.results.isEmpty())
        assertEquals(85_123L, engine.overtime(ClockNow(1_090_123, 90_123, 1)))
        engine.done(ClockNow(1_090_456, 90_456, 1))
        assertEquals(90_456L, engine.state.results[0].activeMs)
        assertEquals(85_456L, engine.state.results[0].overtimeMs)
        assertEquals(1, engine.state.index)
    }
}
