package com.hax.focusdeck

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class StopwatchTest {
    private fun now(ms: Long, boot: Int = 1) = ClockNow(1_791_310_000_000L + ms, ms, boot)
    private fun block(id: String = "S1", policy: ConditionalPolicy = ConditionalPolicy(false)) =
        Block(id, "focus", "Untimed topic", rewardPolicy = policy, timerMode = "stopwatch")
    private fun plan(blocks: List<Block> = listOf(block()), sessionPolicy: ConditionalPolicy? = null) =
        Session(2, "11111111-1111-4111-8111-111111111111", "Study", "2026-10-07T00:00:00Z", blocks,
            sessionRewardPolicy = sessionPolicy)
    private fun completion(text: String, focus: Double = 80.0) = ConditionalPolicy(true, listOf(
        RewardTier(condition = "completed", minFocusRatio = focus, reward = ConditionalReward(text = text))))

    @Test fun hour_dial_steps_on_minutes_and_wraps_without_resetting_elapsed_text() {
        assertEquals(0f, stopwatchMinuteFraction(59_999), .0001f)
        assertEquals(1f / 60, stopwatchMinuteFraction(60_000), .0001f)
        assertEquals(.25f, stopwatchMinuteFraction(15 * 60_000L), .0001f)
        assertEquals(.5f, stopwatchMinuteFraction(30 * 60_000L), .0001f)
        assertEquals(.75f, stopwatchMinuteFraction(45 * 60_000L), .0001f)
        assertEquals(59f / 60, stopwatchMinuteFraction(3_599_999), .0001f)
        assertEquals(0f, stopwatchMinuteFraction(3_600_000), .0001f)
        assertEquals(1f / 60, stopwatchMinuteFraction(3_660_000), .0001f)
        assertEquals("01:01:00", stopwatchText(3_660_000))
    }

    @Test fun hour_dial_follows_active_time_and_freezes_through_long_pause() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        e.pause(now(15 * 60_000L))
        val pausedUntil = now(75 * 60_000L)
        assertEquals(.25f, stopwatchMinuteFraction(e.currentActive(pausedUntil)), .0001f)
        e.resume(pausedUntil)
        assertEquals(.5f, stopwatchMinuteFraction(e.currentActive(now(90 * 60_000L))), .0001f)
        assertEquals("active", e.state.phase)
        assertNull(e.state.budgetMs)
        assertTrue(e.state.results.isEmpty())
    }

    @Test fun starts_at_zero_and_never_has_deadline_or_overtime() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        assertNull(e.state.budgetMs); assertNull(e.remaining(now(0))); assertEquals(0L, e.currentActive(now(0)))
        e.tick(now(4_000_000)); assertEquals("active", e.state.phase)
        assertFalse(e.isOvertime(now(4_000_000))); assertTrue(e.state.results.isEmpty())
        assertFalse(e.state.events.any { it.kind == "overtime_observed" })
        try { e.extend(now(4_000_000)); fail("Stopwatch extension accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun pause_resume_done_measure_active_paused_and_wall() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.pause(now(100_000))
        assertEquals(100_000L, e.currentActive(now(125_000)))
        e.resume(now(125_000)); e.done(now(150_000))
        val b = e.state.results.single(); assertEquals(125_000L, b.activeMs); assertEquals(25_000L, b.pausedMs)
        assertEquals(150_000L, b.wallMs); assertEquals(83.33, b.focusRatio!!, .001)
        assertEquals("done", b.outcome); assertEquals("stopwatch", b.timerMode)
        assertNull(b.plannedMs); assertNull(b.budgetMs); assertNull(b.overtimeMs)
        val p = b.performance!!; assertNull(p.paceIndex); assertNull(p.beatScore); assertNull(p.rank); assertNull(p.performanceClass)
        e.rewardDone(now(151_000)); val report = e.report(now(151_000))!!; report.validateHistory()
        assertEquals("none", report.competition!!.scoreScope); assertNull(report.competition!!.weighted!!.normalizedSessionScore)
    }

    @Test fun done_while_paused_completes_without_counting_pause_as_focus() {
        val e=TimerEngine();e.load(plan(),true,now(0));e.pause(now(10_000));e.done(now(30_000))
        assertEquals(10_000L,e.state.results.single().activeMs);assertEquals(20_000L,e.state.results.single().pausedMs)
        assertEquals("reward",e.state.phase)
    }

    @Test fun civil_clock_rollback_does_not_change_stopwatch_elapsed_duration() {
        val e = TimerEngine(); e.load(plan(), true, now(0))
        val done = ClockNow(now(0).wall - 5_000, 30_000, 1)
        e.done(done)
        val b = e.state.results.single()
        assertEquals(30_000L, b.activeMs); assertEquals(30_000L, b.wallMs)
        assertEquals(30_000L, b.performance!!.wallMs)
        assertTrue(b.finishedAtMs < b.startedAtMs)
        e.rewardDone(done.copy(mono = 31_000))
        e.report(done)!!.validateHistory()
    }

    @Test fun serialization_recreation_retains_count_up_anchor() {
        val e=TimerEngine();e.load(plan(),true,now(0));e.checkpoint(now(10_000))
        val persisted=WireJson.encodeToString(StoredDeck("phone",state=e.state))
        val restored=TimerEngine(WireJson.decodeFromString<StoredDeck>(persisted).state)
        assertEquals(25_000L,restored.currentActive(now(25_000)));assertNull(restored.state.budgetMs)
        restored.pause(now(25_000));restored.done(now(30_000));restored.rewardDone(now(31_000))
        restored.report(now(31_000))!!.validateHistory()
    }

    @Test fun reboot_recovers_paused_without_inventing_device_off_focus() {
        val e=TimerEngine();e.load(plan(),true,now(0));e.checkpoint(now(10_000));e.tick(now(1_000,2))
        assertEquals("paused",e.state.phase);assertEquals(10_000L,e.state.activeMs);assertFalse(e.state.timelineComplete)
        assertNull(e.state.budgetMs);e.resume(now(2_000,2));e.done(now(5_000,2));e.rewardDone(now(6_000,2))
        val r=e.report(now(6_000,2))!!;r.validateHistory();assertEquals(13_000L,r.focusedMs);assertEquals("partial",r.timelineCoverage)
    }

    @Test fun mixed_scoring_excludes_stopwatch_weight_and_preserves_countdown_math() {
        val sw=block().copy(sessionWeight=10.0)
        val timed=Block("C1","focus","Timed",120,rewardPolicy=ConditionalPolicy(false),sessionWeight=2.0,timerMode="countdown")
        val e=TimerEngine();e.load(plan(listOf(sw,timed)),true,now(0));e.done(now(100_000));e.rewardDone(now(101_000))
        assertEquals("active",e.state.phase);assertEquals(1,e.state.index)
        e.done(now(201_000));e.rewardDone(now(202_000));val r=e.report(now(202_000))!!;r.validateHistory()
        val c=r.competition!!;assertEquals("countdown_only",c.scoreScope);assertEquals(111.0,c.beatScore!!,.001)
        assertEquals(2.0,c.weighted!!.totalWeight,.001);assertEquals(100.0,c.weighted!!.normalizedSessionScore!!,.001)
        assertEquals(2,c.completedFocusBlocks);assertNull(r.plannedMs);assertEquals(120_000L,r.timedPlannedMs)
    }

    @Test fun completion_reward_focus_threshold_and_exact_text() {
        val e=TimerEngine();e.load(plan(listOf(block(policy=completion("Move one token.",80.0)))),true,now(0))
        e.pause(now(80_000));e.done(now(100_000));assertEquals("Move one token.",e.state.results.single().performance!!.reward.label())
        e.rewardDone(now(101_000));val r=e.report(now(101_000))!!;r.validateHistory()
        assertTrue(r.blocks.single().performance!!.reward.acknowledged)
        val below=TimerEngine();below.load(plan(listOf(block(policy=completion("Exact reward",80.1)))),true,now(0))
        below.pause(now(80_000));below.done(now(100_000));assertTrue(below.state.results.single().performance!!.reward.actions.isEmpty())
    }

    @Test fun module_reward_uses_done_ids_after_the_final_topic() {
        val milestone=ConditionalPolicy(true,listOf(RewardTier(condition="module_completed",blockIds=listOf("S1","S2"),reward=ConditionalReward(text="Module complete: one token."))))
        val e=TimerEngine();e.load(plan(listOf(block(),block("S2",milestone))),true,now(0))
        e.done(now(10_000));e.rewardDone(now(11_000));e.done(now(21_000))
        assertEquals("Module complete: one token.",e.state.results.last().performance!!.reward.label())
        e.rewardDone(now(22_000));e.report(now(22_000))!!.validateHistory()
    }

    @Test fun untimed_session_reward_uses_overall_focus_and_completion() {
        val policy=ConditionalPolicy(true,listOf(RewardTier(condition="session_completed",minFocusRatio=80.0,minCompletedBlocks=1,reward=ConditionalReward(text="Play guitar for ten minutes."))),
            eligibility=RewardEligibility(minimumCompletedBlocks=1,requireAllFocusCompleted=true))
        val e=TimerEngine();e.load(plan(sessionPolicy=policy),true,now(0));e.done(now(10_000));e.rewardDone(now(11_000))
        assertEquals("session_reward",e.state.phase);assertNull(e.state.competition!!.beatScore)
        assertEquals("Play guitar for ten minutes.",e.state.competition!!.reward.label());e.rewardDone(now(12_000))
        e.report(now(12_000))!!.validateHistory()
    }

    @Test fun legacy_json_remains_countdown_and_expires_only_to_overtime() {
        val old="""{"protocol_version":1,"session_id":"old-id","title":"Legacy","created_at":"2026-10-07T00:00:00Z","blocks":[{"id":"b","type":"focus","title":"Topic","duration_seconds":30}]}"""
        val s=WireJson.decodeFromString<Session>(old);s.validate();assertEquals("countdown",s.blocks.single().effectiveMode)
        val e=TimerEngine();e.load(s,true,now(0));e.tick(now(31_000));assertTrue(e.isOvertime(now(31_000)))
        assertEquals("active",e.state.phase);assertTrue(e.state.results.isEmpty());e.done(now(32_000))
        assertEquals("complete",e.state.phase);assertEquals(2_000L,e.state.results.single().overtimeMs)
    }

    @Test fun countdown_zero_threshold_compatibility_does_not_invent_stopwatch_metrics() {
        val tier = RewardTier(minFocusRatio = 0.0, minPaceIndex = 0.0, reward = ConditionalReward(text = "Exact reward"))
        val policy = ConditionalPolicy(true, listOf(tier))
        assertEquals(listOf(0), ConditionalRewards.eligible(policy, 0.0, null, null, false, null).map { it.index })
        val untimed = policy.copy(tiers = listOf(tier.copy(condition = "completed", minPaceIndex = null)))
        assertTrue(ConditionalRewards.eligible(untimed, null, null, null, false, null, CompletionContext(completed = true)).isEmpty())
    }

    @Test fun unsupported_stopwatch_time_conditions_and_future_milestones_rejected() {
        val bad=completion("Reward").copy(tiers=listOf(RewardTier(condition="completed",minBeatScore=100.0,reward=ConditionalReward(text="Reward"))))
        try { plan(listOf(block(policy=bad))).validate();fail("Timed condition accepted") } catch (_: IllegalArgumentException) { }
        val future=ConditionalPolicy(true,listOf(RewardTier(condition="module_completed",blockIds=listOf("S2"),reward=ConditionalReward(text="Future"))))
        try { plan(listOf(block(policy=future),block("S2"))).validate();fail("Future milestone accepted") } catch (_: IllegalArgumentException) { }
        try { plan(listOf(block().copy(durationSeconds=100))).validate();fail("Stopwatch duration accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun shared_report_serialization_history_and_csv_preserve_null_metrics() {
        val raw=javaClass.getResource("/mixed-mode-report.json")!!.readText()
        val r=WireJson.decodeFromString<Report>(raw);r.validateHistory()
        val copy=WireJson.decodeFromString<Report>(WireJson.encodeToString(r));assertEquals(r,copy)
        val csv=HistoryCsv.encode(listOf(r));assertTrue(csv.contains("\"stopwatch\""));assertTrue(csv.contains("\"timer_mode\""))
        assertTrue(r.summary().contains("Countdown-only scoring"));assertTrue(r.summary().contains("N/A"))
    }

    @Test fun v2_report_rejects_corrupt_categories_and_countdown_budgets() {
        val r = WireJson.decodeFromString<Report>(javaClass.getResource("/mixed-mode-report.json")!!.readText())
        r.validateHistory()
        fun reject(bad: Report) {
            try { bad.validateHistory(); fail("Invalid report accepted") } catch (_: IllegalArgumentException) { }
        }
        reject(r.copy(focusedMs = 100_000, breakMs = 100_000))
        val timed = r.blocks[1]
        listOf(0L to 120_000L, 120_001L to 120_001L, 120_000L to 119_000L,
            120_000L to 120_001L, 86_401_000L to 86_401_000L).forEach { (planned, budget) ->
            reject(r.copy(timelineCoverage = "unavailable", events = emptyList(),
                blocks = listOf(r.blocks[0], timed.copy(plannedMs = planned, budgetMs = budget))))
        }
    }

    @Test fun stopwatch_rounding_matches_inclusive_reward_boundary() {
        val e = TimerEngine()
        e.load(plan(listOf(block(policy = completion("Exact token reward.", 2.68)))), true, now(0))
        e.pause(now(107)); e.done(now(4000))
        val b = e.state.results.single()
        assertEquals(2.68, b.focusRatio!!, .0001)
        assertEquals("Exact token reward.", b.performance!!.reward.label())
        e.rewardDone(now(4100))
        val r = e.report(now(4100))!!; r.validateHistory()
        try { r.copy(blocks = listOf(b.copy(focusRatio = 2.67))).validateHistory(); fail("Incorrect rounded Focus Ratio accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
