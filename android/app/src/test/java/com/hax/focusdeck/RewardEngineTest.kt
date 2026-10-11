package com.hax.focusdeck

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RewardEngineTest {
    private val base = Instant.parse("2026-10-06T10:00:00Z").toEpochMilli()
    private fun now(ms: Long, boot: Int = 1) = ClockNow(base + ms, ms, boot)
    private val comparison = TaskComparison("algebra", "medium", "closed-book", 5, "v1")
    private fun option(kind: String = "walk", seconds: Int = 60, reps: Int = 0) = RewardOption(listOf(RewardAction(kind, reps, seconds)))
    private fun plan(auto: Boolean = true, strategy: RewardStrategy = RewardStrategy(), sessionReward: RewardContract? = null,
        contract: RewardContract = RewardContract(option(), option(), option(), option())) =
        Session(1, "reward-test", "Practice", "2026-10-06T10:00:00Z", listOf(
            Block("b1", "focus", "Five exercises", 100, listOf("All five checked"), comparison, contract),
            Block("b2", "focus", "More exercises", 100, comparison = comparison, rewardContract = contract)), auto,
            strategy, sessionReward, "practice-v1")
    private fun finish(e: TimerEngine, offset: Long = 0) {
        e.done(now(offset + 90000)); e.rewardDone(now(offset + 95000))
        e.done(now(offset + 185000)); e.rewardDone(now(offset + 190000))
    }
    @Test fun doneCalculatesImmediatelyAndWaitsForRewardWithoutCountingResultTime() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(40000)); e.resume(now(45000)); e.done(now(95000))
        assertEquals("reward", e.state.phase); assertEquals(0, e.state.index)
        assertEquals(90000L, e.state.results.last().activeMs); assertEquals(5000L, e.state.results.last().pausedMs)
        assertEquals("reward", e.state.phase)
        e.tick(now(180000)); assertEquals(90000L, e.currentActive(now(180000)))
        e.rewardDone(now(190000)); assertEquals(1, e.state.index); assertEquals("active", e.state.phase)
        assertEquals(0L, e.currentActive(now(190000)))
        e.done(now(280000)); e.rewardDone(now(282000))
        val r = e.report(now(282000))!!; r.validateHistory()
        assertEquals(180000L, r.activeMs); assertEquals(5000L, r.pausedMs)
        assertEquals(0L, TimelineAnalysis.intervals(r).filter { it.kind == "quality_review" }.sumOf { it.durationMs })
        assertTrue(TimelineAnalysis.intervals(r).any { it.kind == "reward" && it.durationMs == 95000L })
    }
    @Test fun doneIsCompletionAuthorityWithoutMissingWorkPenalty() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(80000))
        val p = e.state.results.last().performance!!
        assertEquals("confirmed", p.quality); assertTrue(p.beatScore!! > 0)
        assertTrue(e.state.events.none { it.kind == "quality_incomplete" })
        e.rewardDone(now(82000), true); e.stop(now(83000))
        val r = e.report(now(83000))!!; r.validateHistory()
        assertEquals(1, r.competition!!.completedFocusBlocks)
        assertEquals(p.beatScore!!, r.competition!!.beatScore!!, 0.0)
    }
    @Test fun speedContributionCapsAndExtremeSpeedNeedsRecalibration() {
        val policy = ScorePolicy()
        assertEquals(111.0, RewardEngine.score(140.0, 100.0, true, policy), 0.0)
        assertEquals(100.0, RewardEngine.score(250.0, 100.0, true, policy), 0.0)
        assertEquals(0.0, RewardEngine.score(250.0, 100.0, false, policy), 0.0)
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(30000))
        val p = e.state.results.last().performance!!
        assertTrue(p.recalibrationNeeded); assertEquals("completion", p.reward.tier); assertFalse(p.newPersonalBest)
    }
    @Test fun noHistoryNeverFabricatesPersonalBest() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(90000))
        assertNull(e.state.results.last().performance!!.previousBenchmark)
    }
    @Test fun comparableHistoryProducesPBButDifferentWorkOrEstimateDoesNot() {
        val old = TimerEngine(); old.load(plan(), true, now(0)); finish(old)
        val r = old.report(now(190000))!!
        val e = TimerEngine(history = listOf(r)); e.load(plan().copy(sessionId = "new"), true, now(300000))
        e.done(now(385000))
        assertTrue(e.state.results.last().performance!!.newPersonalBest)
        assertEquals(r.key, e.state.results.last().performance!!.previousBenchmark!!.reportKey)
        val changed = plan().copy(blocks = plan().blocks.map { it.copy(comparison = comparison.copy(complexity = "hard")) })
        val other = TimerEngine(history = listOf(r)); other.load(changed, true, now(300000)); other.done(now(385000))
        assertNull(other.state.results.last().performance!!.previousBenchmark)
        val newEstimate = plan().copy(blocks = plan().blocks.map { it.copy(durationSeconds = 101) })
        val estimate = TimerEngine(history = listOf(r)); estimate.load(newEstimate, true, now(300000)); estimate.done(now(385000))
        assertNull(estimate.state.results.last().performance!!.previousBenchmark)
    }
    @Test fun dailyRepCapUsesOnlyAcknowledgedHistoryAndConfiguredAlternative() {
        val physical = RewardOption(listOf(RewardAction("pushups", 10, 60)), listOf(listOf(RewardAction("mobility", seconds = 60))))
        val p = plan(strategy = RewardStrategy(dailyRepLimits = mapOf("pushups" to 10, "squats" to 0)), contract = RewardContract(completion = physical))
        val e = TimerEngine(); e.load(p, true, now(0)); e.done(now(90000)); e.rewardDone(now(92000))
        e.done(now(182000))
        assertEquals("mobility", e.state.results.last().performance!!.reward.actions.single().kind)
        assertEquals(10, e.state.results.last().performance!!.reward.dailyRepsBefore["pushups"])
        e.rewardDone(now(184000)); val r = e.report(now(184000))!!; r.validateHistory()
        val next = TimerEngine(history = listOf(r)); next.load(p.copy(sessionId = "later"), true, now(300000)); next.done(now(390000))
        assertEquals("mobility", next.state.results.last().performance!!.reward.actions.single().kind)
    }
    @Test fun continueDoesNotConsumeDailyVolumeOrChangeTaskCompletion() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(90000)); e.rewardDone(now(92000), true)
        val r = e.state.results.first().performance!!.reward
        assertFalse(r.skipped); assertFalse(r.acknowledged)
        e.done(now(182000))
        assertEquals(0, e.state.results.last().performance!!.reward.dailyPhysicalSecondsBefore)
    }
    @Test fun sessionRewardWaitsAndManualAdvanceStillWaitsForStart() {
        val e = TimerEngine(); e.load(plan(auto = false, sessionReward = RewardContract(completion = option("guitar", 600))), true, now(0))
        e.done(now(90000)); e.rewardDone(now(92000)); assertEquals("between", e.state.phase)
        e.resume(now(100000)); e.done(now(190000)); e.rewardDone(now(192000))
        assertEquals("session_reward", e.state.phase); assertNull(e.report(now(192000)))
        e.rewardDone(now(800000)); val r = e.report(now(800000))!!; r.validateHistory()
        assertTrue(r.competition!!.reward.acknowledged); assertEquals(180000L, r.activeMs)
    }
    @Test fun coffeeUsesProductiveTimeCutoffAndAcknowledgedSpacing() {
        val coffee = RewardOption(listOf(RewardAction("coffee")), listOf(listOf(RewardAction("guitar", seconds = 600))))
        val strategy = RewardStrategy(coffeeReward = CoffeePolicy(enabled = true))
        val session = plan(strategy = strategy, sessionReward = RewardContract(completion = coffee))
        val state = DeckState(session = session, recordedTimezone = "UTC")
        assertEquals("guitar", RewardEngine.choose(session.sessionReward, listOf("completion"), state, emptyList(), now(0), 100000).actions.single().kind)
        assertEquals("coffee", RewardEngine.choose(session.sessionReward, listOf("completion"), state, emptyList(), now(0), 10800000).actions.single().kind)
        assertEquals("guitar", RewardEngine.choose(session.sessionReward, listOf("completion"), state, emptyList(), now(6 * 3600000), 10800000).actions.single().kind)
        val acknowledged = RewardEngine.acknowledge(RewardReceipt("completion", listOf(RewardAction("coffee"))), now(0), "UTC", false)
        val blocked = state.copy(competition = SessionCompetition(plannedFocusBlocks=0,completedFocusBlocks=0,onOrAheadCount=0,completionFraction=0.0,averagePace=null,focusRatio=null,beatScore=0.0,rank="C",rewardStrategy=strategy,reward=acknowledged))
        assertEquals("guitar", RewardEngine.choose(session.sessionReward, listOf("completion"), blocked, emptyList(), now(3600000), 10800000).actions.single().kind)
    }
    @Test fun reviewStateSurvivesSerializationAndRebootWithoutInventedTime() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(90000))
        val recovered = TimerEngine(WireJson.decodeFromString<DeckState>(WireJson.encodeToString(e.state)))
        recovered.tick(ClockNow(base+200000, 1, 2)); assertEquals("reward", recovered.state.phase); assertFalse(recovered.state.timelineComplete)
        recovered.rewardDone(ClockNow(base+201000, 2, 2)); recovered.stop(ClockNow(base+202000, 3, 2))
        recovered.report(ClockNow(base+202000, 3, 2))!!.validateHistory()
    }
    @Test fun commandReplayCannotAcknowledgeTwoRewards() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(90000))
        val first = CommandProcessor.apply(e.state, emptyMap(), "ack", "reward_done", null, now(92000))
        val replay = CommandProcessor.apply(first.state, first.receipts, "ack", "reward_done", null, now(93000))
        assertEquals(first.state, replay.state); assertTrue(replay.ack.ok); assertEquals(1, replay.state.index)
    }
    @Test fun stopDuringReviewKeepsOneBlockAndUnacknowledgedReward() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.done(now(90000)); e.stop(now(91000))
        val r = e.report(now(91000))!!; r.validateHistory(); assertEquals(1, r.blocks.size)
        assertEquals("confirmed", r.blocks.single().performance!!.quality); assertEquals(r.blocks.single().performance!!.beatScore!!, r.competition!!.beatScore!!, 0.0)
    }
    @Test fun planRejectsOnlineRewardsCoffeePerBlockAndOversizedReset() {
        for (action in listOf(RewardAction("youtube", seconds=60), RewardAction("coffee"), RewardAction("pushups", 50, 120))) {
            try { plan(contract=RewardContract(completion=RewardOption(listOf(action)))).validate(); fail("Invalid reward accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun sharedDesktopFixtureChecksScoreReportAndCSV() {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("competitive-report.json")).bufferedReader().use { it.readText() }
        val report = WireJson.decodeFromString<Report>(text)
        report.validateHistory()
        assertEquals(104.8, report.blocks.single().performance!!.beatScore!!, .001)
        val csv = HistoryCsv.encode(listOf(report))
        assertTrue(csv.contains("reward_acknowledged")); assertTrue(csv.contains("104.8"))
    }
    @Test fun dailySecondsAndVarietyLimitsDoNotInventRewards() {
        val e = TimerEngine(); e.load(plan(strategy=RewardStrategy(dailyPhysicalSeconds=60)), true, now(0))
        e.done(now(90000)); e.rewardDone(now(92000))
        e.done(now(182000))
        assertTrue(e.state.results.last().performance!!.reward.actions.isEmpty())
        val strategy = RewardStrategy(maxConsecutiveSameActivity=1)
        val variety = TimerEngine(); variety.load(plan(strategy=strategy),true,now(0))
        variety.done(now(90000)); variety.rewardDone(now(92000))
        variety.done(now(182000))
        assertTrue(variety.state.results.last().performance!!.reward.actions.isEmpty())
    }
    @Test fun dailyVolumeResetsByAcknowledgementLocalDate() {
        val e = TimerEngine(); e.load(plan(strategy=RewardStrategy(dailyPhysicalSeconds=60)),true,now(0)); finish(e)
        val r=e.report(now(190000))!!
        val next=TimerEngine(history=listOf(r)); next.load(plan(strategy=RewardStrategy(dailyPhysicalSeconds=60)).copy(sessionId="tomorrow"),true,now(86400000))
        next.done(now(86490000))
        assertEquals(0,next.state.results.last().performance!!.reward.dailyPhysicalSecondsBefore)
        assertTrue(next.state.results.last().performance!!.reward.actions.isNotEmpty())
    }
    @Test fun extensionDoesNotImprovePaceAgainstOriginalPlan() {
        val e=TimerEngine(); e.load(plan(),true,now(0)); e.extend(now(10000)); e.done(now(120000))
        val p=e.state.results.last().performance!!
        assertEquals(83.33,p.paceIndex!!,.001); assertEquals("completion",p.reward.tier)
    }
    @Test fun sessionPBUsesComparableWholePlanAndCompleteQuality() {
        val old=TimerEngine(); old.load(plan(),true,now(0)); finish(old); val r=old.report(now(190000))!!
        val next=TimerEngine(history=listOf(r)); next.load(plan().copy(sessionId="new"),true,now(300000))
        next.done(now(385000)); next.rewardDone(now(390000))
        next.done(now(475000)); next.rewardDone(now(480000))
        val report=next.report(now(480000))!!; report.validateHistory()
        assertTrue(report.competition!!.newPersonalBest); assertEquals(r.key,report.competition!!.previousBenchmark!!.reportKey)
    }
    @Test fun rebootDuringEarnedPBResetKeepsAValidReport() {
        val old=TimerEngine(); old.load(plan(),true,now(0)); finish(old); val r=old.report(now(190000))!!
        val next=TimerEngine(history=listOf(r)); next.load(plan().copy(sessionId="new"),true,now(300000)); next.done(now(385000))
        assertTrue(next.state.results.last().performance!!.newPersonalBest)
        next.tick(ClockNow(base+400000,1,2)); next.rewardDone(ClockNow(base+401000,2,2)); next.stop(ClockNow(base+402000,3,2))
        next.report(ClockNow(base+402000,3,2))!!.validateHistory()
    }

    @Test fun rewardGatesNeedNoPollingOrRepeatedCheckpoint() {
        for (phase in listOf("quality","reward","session_reward")) {
            assertNull(PowerPolicy.tickMs(phase,true)); assertNull(PowerPolicy.tickMs(phase,false))
            assertNull(PowerPolicy.checkpointMs(phase,true)); assertNull(PowerPolicy.checkpointMs(phase,false))
        }
    }

}
