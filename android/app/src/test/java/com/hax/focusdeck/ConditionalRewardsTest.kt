package com.hax.focusdeck

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ConditionalRewardsTest {
    private fun now(ms: Long) = ClockNow(1_000_000 + ms, ms, 1)
    private fun plan(policy: ConditionalPolicy = ConditionalPolicy(false), sessionPolicy: ConditionalPolicy? = null) =
        Session(1, "conditional", "Study", "2026-10-07T00:00:00Z",
            listOf(Block("b1", "focus", "One", 100, rewardPolicy = policy),
                Block("b2", "focus", "Two", 100, rewardPolicy = ConditionalPolicy(false))),
            sessionRewardPolicy = sessionPolicy)
    private fun walk(beat: Double = 100.0) = RewardTier(minBeatScore = beat, reward = ConditionalReward("walk", durationSeconds = 60))
    @Test fun overtimeNeverCompletesAndDoneImmediatelyCalculatesDisabledPolicy() {
        val e = TimerEngine(); e.load(plan(), true, now(0)); e.tick(now(120000))
        assertEquals("active", e.state.phase); assertTrue(e.state.results.isEmpty()); assertEquals(20000L, e.overtime(now(120000)))
        e.done(now(120000)); val p = e.state.results.single().performance!!
        assertEquals("reward", e.state.phase); assertEquals("slightly_over", p.performanceClass)
        assertEquals("confirmed", p.quality); assertEquals("No reward assigned", p.reward.reason)
        assertTrue(e.state.events.none { it.kind in setOf("review_started", "quality_incomplete", "block_stopped") })
        e.rewardDone(now(122200)); assertEquals(1, e.state.index)
    }
    @Test fun highestSatisfiedTierAndPersonalBestOrdering() {
        val policy = ConditionalPolicy(true, listOf(walk(), RewardTier(minBeatScore=108.0,reward=ConditionalReward("pushups",amount=12)),
            RewardTier(condition="personal_best",reward=ConditionalReward("walk",durationSeconds=90))))
        policy.validate(false)
        assertEquals(listOf(1,0), ConditionalRewards.eligible(policy,109.0,119.0,95.0,false,null).map { it.index })
        assertEquals(listOf(2,1,0), ConditionalRewards.eligible(policy,109.0,119.0,95.0,true,null).map { it.index })
        assertEquals(listOf(0,1,2), ConditionalRewards.eligible(policy.copy(selection="first_satisfied"),109.0,119.0,95.0,true,null).map { it.index })
    }
    @Test fun belowThresholdIsDoneWithNoUnlockedReward() {
        val e = TimerEngine(); e.load(plan(ConditionalPolicy(true,listOf(walk(108.0)))),true,now(0));e.done(now(105000))
        val p=e.state.results.single().performance!!
        assertEquals("confirmed",p.quality);assertEquals("No reward unlocked",p.reward.reason);assertTrue(p.reward.actions.isEmpty())
        e.rewardDone(now(107000));e.done(now(207000));e.rewardDone(now(209000))
        e.report(now(209000))!!.validateHistory()
    }
    @Test fun tierUsesHighestEligibleActionAndContinueDoesNotChangeDone() {
        val e=TimerEngine();e.load(plan(ConditionalPolicy(true,listOf(walk(100.0),walk(105.0)))),true,now(0));e.done(now(90000))
        assertEquals("tier_2",e.state.results.single().performance!!.reward.tier)
        e.rewardDone(now(92000),true)
        assertEquals("done",e.state.results.single().outcome);assertFalse(e.state.results.single().performance!!.reward.skipped)
        assertTrue(e.state.events.any { it.kind=="reward_continued" })
    }
    @Test fun sessionRewardsRemainIndependentFromDisabledBlockRewards() {
        val policy=ConditionalPolicy(true,listOf(RewardTier(minSessionScore=70.0,reward=ConditionalReward("guitar",durationMinutes=10))),
            eligibility=RewardEligibility(minimumOnOrAboveTargetBlocks=2,minimumFocusRatio=90.0))
        val e=TimerEngine();e.load(plan(sessionPolicy=policy),true,now(0))
        e.done(now(100000));e.rewardDone(now(102000));e.done(now(202000));e.rewardDone(now(204000))
        assertEquals("session_reward",e.state.phase);assertEquals("guitar",e.state.competition!!.reward.actions.single().kind)
        assertEquals(75.0,e.state.competition!!.weighted!!.normalizedSessionScore!!,0.0)
        e.rewardDone(now(804000));e.report(now(804000))!!.validateHistory()
    }
    @Test fun configuredWeightsCoefficientsAndCountsPreserveRawPoints() {
        val fixture=WireJson.decodeFromString<Report>(requireNotNull(javaClass.classLoader?.getResourceAsStream("conditional-report.json")).bufferedReader().use { it.readText() })
        val b=fixture.blocks.single();val p=b.performance!!
        val classes=listOf("strong","strong","on_target","on_target","slightly_over","over_target")
        val results=classes.map { b.copy(performance=p.copy(performanceClass=it,sessionWeight=1.0)) }
        val w=ConditionalRewards.weighted(results,SessionScoring())
        assertEquals(3.3,w.rawWeightedPoints,.001);assertEquals(6.0,w.totalWeight,0.0);assertEquals(55.0,w.normalizedSessionScore!!,0.0)
        assertEquals(2,w.classCounts["strong"]);assertEquals(2,w.classCounts["on_target"])
        val custom=ConditionalRewards.weighted(results,SessionScoring(mapOf("strong" to 1.0,"on_target" to 1.0,"slightly_over" to .5,"over_target" to 0.0)))
        assertEquals(75.0,custom.normalizedSessionScore!!,0.0)
    }
    @Test fun blockClassificationAndSessionScoreCannotBeFabricated() {
        val fixture=WireJson.decodeFromString<Report>(requireNotNull(javaClass.classLoader?.getResourceAsStream("conditional-report.json")).bufferedReader().use { it.readText() })
        fixture.validateHistory()
        val bad=fixture.copy(competition=fixture.competition!!.copy(weighted=fixture.competition!!.weighted!!.copy(normalizedSessionScore=99.0)))
        try { bad.validateHistory(); fail("Fabricated score accepted") } catch (_: IllegalArgumentException) { }
        val b=fixture.blocks.single()
        try { fixture.copy(blocks=listOf(b.copy(performance=b.performance!!.copy(performanceClass="failed")))).validateHistory(); fail("Invalid class accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun sessionEligibilityRejectsInsufficientOnTargetPerformance() {
        val w=WeightedSession(3.3,6.0,55.0,mapOf("strong" to 2,"on_target" to 2,"slightly_over" to 1,"over_target" to 1),95.0,SessionScoring())
        val policy=ConditionalPolicy(true,listOf(RewardTier(minSessionScore=70.0,reward=ConditionalReward("guitar",durationMinutes=10))),
            eligibility=RewardEligibility(minimumOnOrAboveTargetBlocks=4,minimumFocusRatio=90.0))
        assertTrue(ConditionalRewards.eligible(policy,100.0,null,95.0,false,w).isEmpty())
        assertEquals(1,ConditionalRewards.eligible(policy,100.0,null,95.0,false,w.copy(normalizedSessionScore=85.0)).size)
        assertTrue(ConditionalRewards.eligible(policy,100.0,null,89.0,false,w.copy(normalizedSessionScore=85.0)).isEmpty())
    }
    @Test fun disabledPolicyOverridesLegacyDefaultAndSerializes() {
        val p=plan().copy(rewardStrategy=RewardStrategy(defaultContract=RewardContract(completion=RewardOption(listOf(RewardAction("walk",seconds=60))))))
        val e=TimerEngine();e.load(p,true,now(0));e.done(now(90000))
        assertTrue(e.state.results.single().performance!!.reward.actions.isEmpty())
        val restored=WireJson.decodeFromString<DeckState>(WireJson.encodeToString(e.state))
        assertEquals(e.state,restored)
    }
    @Test fun combinedRewardRespectsConfiguredAmountsAndBounds() {
        assertEquals(2,ConditionalReward("combined",actions=listOf(ConditionalReward("pushups",amount=15),ConditionalReward("walk",durationSeconds=60))).typed(false).size)
        try { ConditionalReward("combined",actions=listOf(ConditionalReward("walk",durationSeconds=90),ConditionalReward("plank",durationSeconds=90))).typed(false);fail("Oversized reward accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun deletionMergeIsDurableMonotoneAndSpecificToAttempt() {
        val fixture=WireJson.decodeFromString<Report>(requireNotNull(javaClass.classLoader?.getResourceAsStream("conditional-report.json")).bufferedReader().use { it.readText() })
        val a=HistoryDeletion(reportKeys=setOf(fixture.key)).merge(HistoryDeletion(beforeMs=10,reportKeys=setOf("other:123")))
        assertEquals(setOf(fixture.key,"other:123"),a.reportKeys);assertEquals(a,a.merge(HistoryDeletion(reportKeys=setOf(fixture.key))))
        assertTrue(a.hides(fixture));assertFalse(a.hides(fixture.copy(startedAtMs=fixture.startedAtMs+1)))
        assertEquals(a,WireJson.decodeFromString<HistoryDeletion>(WireJson.encodeToString(a)))
    }
    @Test fun deleteAllCutoffKeepsFutureRecords() {
        val fixture=WireJson.decodeFromString<Report>(requireNotNull(javaClass.classLoader?.getResourceAsStream("conditional-report.json")).bufferedReader().use { it.readText() })
        val d=HistoryDeletion(beforeMs=fixture.finishedAtMs)
        assertTrue(d.hides(fixture));assertFalse(d.hides(fixture.copy(finishedAtMs=fixture.finishedAtMs+1)))
    }

    @Test fun gptTextRewardSurvivesSelectionAcknowledgementSerializationAndHistory() {
        val text = "5 pull-ups + move one FocusStack token.\nThen return to work."
        val policy = ConditionalPolicy(true, listOf(RewardTier(minBeatScore=100.0, maxBeatScore=111.0, reward=ConditionalReward(text=text))))
        val e=TimerEngine()
        e.load(plan(policy).copy(rewardStrategy=RewardStrategy(allowedRewards=listOf("walk"),dailyPhysicalSeconds=0,dailyRepLimits=mapOf("pushups" to 0,"squats" to 0))),true,now(0))
        e.done(now(90000))
        val r=e.state.results.single().performance!!.reward
        assertEquals(text,r.label()); assertEquals("text",r.actions.single().kind)
        assertEquals(e.state,WireJson.decodeFromString<DeckState>(WireJson.encodeToString(e.state)))
        e.rewardDone(now(150000))
        assertTrue(e.state.results.single().performance!!.reward.acknowledged)
        e.done(now(250000));e.rewardDone(now(252200))
        val report=e.report(now(252200))!!
        report.validateHistory()
        assertEquals(text,report.blocks.first().performance!!.reward.label())
        assertTrue(RewardEngine.summary(report).contains(text))
        assertTrue(RewardCsv.block(report.blocks.first().performance).contains(text))
        val b=report.blocks.first();val p=b.performance!!
        try {
            report.copy(blocks=listOf(b.copy(performance=p.copy(reward=p.reward.copy(actions=listOf(RewardAction("text",text="20 pull-ups"))))))+report.blocks.drop(1)).validateHistory()
            fail("Changed reward text accepted")
        } catch (_:IllegalArgumentException) { }
    }
    @Test fun explicitTextRangesHaveInclusiveBoundsAndNoAutomaticRewardBelowThreshold() {
        val policy=ConditionalPolicy(true,listOf(
            RewardTier(minBeatScore=100.0,maxBeatScore=106.99,reward=ConditionalReward(text="2-minute walk")),
            RewardTier(minBeatScore=107.0,maxBeatScore=111.0,reward=ConditionalReward(text="5 pull-ups + TOKEN"))))
        policy.validate(false)
        assertTrue(ConditionalRewards.eligible(policy,99.99,100.0,100.0,false,null).isEmpty())
        assertEquals(listOf(0),ConditionalRewards.eligible(policy,100.0,100.0,100.0,false,null).map { it.index })
        assertEquals(listOf(0),ConditionalRewards.eligible(policy,106.99,100.0,100.0,false,null).map { it.index })
        assertEquals(listOf(1),ConditionalRewards.eligible(policy,107.0,100.0,100.0,false,null).map { it.index })
        assertEquals(listOf(1),ConditionalRewards.eligible(policy,111.0,120.0,100.0,false,null).map { it.index })
        try { policy.copy(tiers=listOf(RewardTier(minBeatScore=108.0,maxBeatScore=100.0,reward=ConditionalReward(text="TOKEN")))).validate(false);fail("Reversed range accepted") } catch (_:IllegalArgumentException) { }
    }
    @Test fun sessionTextRewardsRemainIndependentAndUseSessionScoreRanges() {
        val policy=ConditionalPolicy(true,listOf(RewardTier(minSessionScore=70.0,maxSessionScore=84.99,reward=ConditionalReward(text="10 minutes guitar."))))
        val e=TimerEngine();e.load(plan(sessionPolicy=policy),true,now(0))
        e.done(now(100000));e.rewardDone(now(102200));e.done(now(202200));e.rewardDone(now(204400))
        assertEquals("session_reward",e.state.phase);assertEquals("10 minutes guitar.",e.state.competition!!.reward.label())
        e.rewardDone(now(804400),true)
        assertFalse(e.state.competition!!.reward.acknowledged)
        e.report(now(804400))!!.validateHistory()
    }
    @Test fun textRewardsRejectBlankMixedMeasuredFieldsAndOversizedDescriptions() {
        listOf(ConditionalReward(text=" "),ConditionalReward(text="x".repeat(1001)),ConditionalReward(text="Walk",amount=1),
            ConditionalReward(text="Walk",durationSeconds=60),ConditionalReward("walk",text="Pull-ups",durationSeconds=60)).forEach {
            try { it.typed(false);fail("Invalid text reward accepted") } catch (_:IllegalArgumentException) { }
        }
        assertEquals("🟡".repeat(1000),ConditionalReward(text="🟡".repeat(1000)).typed(false).single().text)
    }

}
