package com.hax.focusdeck

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SkipEndTest {
    private fun now(ms: Long, boot: Int = 1) = ClockNow(1_791_400_000_000L + ms, ms, boot)
    private fun plan(auto: Boolean = true) = Session(2, "11111111-1111-4111-8111-111111111111", "Skip/end regression", "2026-10-08T00:00:00Z",
        listOf(Block("s", "focus", "Untimed", timerMode = "stopwatch", rewardPolicy = ConditionalPolicy(false)),
            Block("c", "focus", "Timed", 60, timerMode = "countdown", rewardPolicy = ConditionalPolicy(false)), Block("b", "break", "Break", 10)),
        autoAdvance = auto, sessionRewardPolicy = ConditionalPolicy(true, listOf(
            RewardTier(condition = "session_completed", reward = ConditionalReward(text = "Completion reward")))))

    @Test fun pausedSkipRetainsEffortWithoutCompletionOrRewardAndSurvivesRestore() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(10_000)); e.skipBlock(now(30_000))
        assertEquals("active", e.state.phase); assertEquals(1, e.state.index)
        val b = e.state.results.single()
        assertEquals("skipped", b.outcome); assertEquals(10_000L, b.activeMs); assertEquals(20_000L, b.pausedMs)
        assertNull(b.performance); assertFalse(CompletionContext.from(e.state).completed)
        val restored = TimerEngine(WireJson.decodeFromString<DeckState>(WireJson.encodeToString(e.state)))
        restored.endBlock(now(95_000)); assertEquals(5_000L, restored.state.results[1].overtimeMs)
        assertNull(restored.state.results[1].performance)
        restored.done(now(105_000)); val report = restored.report(now(105_000))!!
        report.validateHistory()
        assertEquals("stopped", report.status); assertEquals(75_000L, report.focusedMs)
        assertEquals(0, report.competition!!.completedFocusBlocks); assertTrue(report.competition!!.reward.actions.isEmpty())
        assertEquals(75_000L, TimelineAnalysis.intervals(report).filter { it.kind == "active" && it.blockIndex < 2 }.sumOf { it.durationMs })
        File("build/test-outputs/skipped-timer-report.json").apply { parentFile?.mkdirs(); writeText(WireJson.encodeToString(report)) }
    }

    @Test fun endingSessionPreservesCompletedBlockAndCurrentPartialTimeOnly() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(10_000)); e.rewardDone(now(12_000), true)
        e.pause(now(20_000)); e.stop(now(30_000)); val report = e.report(now(30_000))!!
        report.validateHistory()
        assertEquals(listOf("done", "stopped"), report.blocks.map { it.outcome })
        assertEquals(18_000L, report.focusedMs); assertEquals(10_000L, report.pausedMs)
        assertEquals(1, report.competition!!.completedFocusBlocks); assertTrue(report.competition!!.reward.actions.isEmpty())
        assertEquals("Ended early", report.statusLabel); assertTrue(report.outcomeSummary.contains("1 not started"))
        assertNull(e.report(now(30_000))!!.blocks.last().performance)
    }

    @Test fun skipLastBreakNeverOffersSessionRewardEvenWhenAllFocusIsDone() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(10_000)); e.rewardDone(now(11_000), true)
        e.done(now(71_000)); e.rewardDone(now(72_000), true); e.skipBlock(now(73_000))
        val report = e.report(now(73_000))!!; report.validateHistory()
        assertEquals("stopped", e.state.phase); assertEquals("Finished with skips", report.statusLabel)
        assertEquals(2, report.competition!!.completedFocusBlocks); assertTrue(report.competition!!.reward.actions.isEmpty())
        assertFalse(report.competition!!.newPersonalBest); assertNull(report.competition!!.previousBenchmark)
    }

    @Test fun manualAdvanceAndReplayedSkipCannotSkipAnotherBlock() {
        val start = CommandProcessor.apply(DeckState(), emptyMap(), "start", "start", plan(false), now(0))
        val skip = CommandProcessor.apply(start.state, start.receipts, "skip", "skip_block", null, now(10_000))
        assertTrue(skip.ack.ok); assertEquals("between", skip.state.phase)
        val restored = WireJson.decodeFromString<StoredDeck>(WireJson.encodeToString(StoredDeck("test-phone", state = skip.state, acknowledgements = skip.receipts)))
        val replay = CommandProcessor.apply(restored.state, restored.acknowledgements, "skip", "skip_block", null, now(20_000))
        assertEquals(skip.state, replay.state); assertEquals(1, replay.state.results.size)
        val invalid = CommandProcessor.apply(replay.state, replay.receipts, "end", "end_block", null, now(21_000))
        assertFalse(invalid.ack.ok); assertEquals(1, invalid.state.results.size)
    }

    @Test fun rebootThenEndNeverInventsUnobservedFocus() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.checkpoint(now(10_000)); e.endBlock(now(1_000, 2))
        assertEquals(10_000L, e.state.results.single().activeMs); assertFalse(e.state.timelineComplete)
    }

    @Test fun legacyCountdownSkipProducesValidPartialReport() {
        val e = TimerEngine(); e.load(Session(1, "legacy", "Legacy", "2026-10-08T00:00:00Z", listOf(Block("f", "focus", "Task", 60))), true, now(0))
        e.skipBlock(now(5_000)); val report = e.report(now(5_000))!!; report.validateHistory()
        assertEquals("skipped", report.blocks.single().outcome); assertEquals("stopped", report.status)
    }
}
