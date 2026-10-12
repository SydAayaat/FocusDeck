package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DashboardAnalyticsTest {
    private val zone=ZoneId.of("UTC")
    private val date=LocalDate.of(2026,10,9)
    private fun at(day: Int)=LocalDate.of(2026,10,day).atTime(12,0).atZone(zone).toInstant().toEpochMilli()
    private fun store()=AchievementStore(definitions=listOf(AchievementDefinition("project","Project","projects",progressEnabled=true,progress=AchievementProgress(78.0,100.0,"topics",updatedAt=at(9)))),progressHistory=listOf(
        ProgressHistory("old","project",at(2),0.0,46.0,100.0,100.0,46.0,"topics","GPT"),
        ProgressHistory("new","project",at(9),46.0,78.0,100.0,100.0,32.0,"topics","GPT")))
    @Test fun projectContributionUsesWholeTargetAndHistoricalReceipts() {
        val p=completionContribution(store(),"project",date,date,zone)!!
        assertEquals(.46f,p.earlierFraction,.001f);assertEquals(.32f,p.periodFraction,.001f);assertEquals(78.0,p.total,.001)
        val old=completionContribution(store(),"project",date.minusDays(7),date.minusDays(7),zone)!!
        assertEquals(0.0,old.before,.001);assertEquals(46.0,old.within,.001)
    }
    @Test fun changedUnitsTargetsAndReopenCannotInventPositiveContribution() {
        val s=store();val h=s.progressHistory.last()
        assertNull(completionContribution(s.copy(progressHistory=s.progressHistory.dropLast(1)+h.copy(unit="words")),"project",date,date,zone))
        assertNull(completionContribution(s.copy(progressHistory=s.progressHistory.dropLast(1)+h.copy(targetValueAtThatTime=200.0)),"project",date,date,zone))
        assertNull(completionContribution(s.copy(progressHistory=s.progressHistory.dropLast(1)+h.copy(newValue=30.0,delta=-16.0)),"project",date,date,zone))
        assertNull(completionContribution(AchievementStore(),"unknown",date,date,zone))
    }
    @Test fun indexContributionUsesNamedLeafReceiptsAndRetainsReopenEvidence() {
        val topics=IndexDocument(listOf(IndexTopic("a","A"),IndexTopic("b","B")))
        val events=listOf(IndexEvent("1","a","A",1,at(2),"2026-10-02","UTC"),IndexEvent("2","b","B",1,at(9),"2026-10-09","UTC"))
        val s=AchievementStore(definitions=listOf(AchievementDefinition("project","Index","index",progressEnabled=true)),indexes=mapOf("project" to IndexLedger(document=topics,events=events)))
        val p=completionContribution(s,"project",date,date,zone)!!
        assertEquals(.5f,p.earlierFraction,.001f);assertEquals(.5f,p.periodFraction,.001f)
        val ledger=s.indexes.getValue("project")
        val changed=ledger.copy(revisions=listOf(
            IndexRevision("before",at(2),topics),
            IndexRevision("removed",at(9)-1000,IndexDocument(listOf(IndexTopic("a","A")))),
            IndexRevision("restored",at(9),topics)))
        assertNull(completionContribution(s.copy(indexes=mapOf("project" to changed)),"project",date,date,zone))
        assertNull(completionContribution(s.copy(indexes=mapOf("project" to s.indexes.getValue("project").copy(events=listOf(events.first(),events.last().copy(topicId="a",delta=-1))))),"project",date,date,zone))
    }
    @Test fun emptyEvidenceIsUnknownAndNeverInventsAPlanScore() {
        val e=dashboardAnalytics(CalendarDataset(emptyMap(),emptyMap()),emptyList(),HistoryPeriod.DAY,date,AchievementStore())
        assertNull(e.score);assertNull(e.completion);assertNull(e.estimation);assertNull(e.focusShare);assertTrue(e.projects.isEmpty())
    }
    @Test fun partialDaysRetainMeasuredTotalsWithoutClaimingAverageCoverage() {
        val d=CalendarDay(date,focusMs=60000,hasRecord=true,incomplete=true,hourCoverage=true)
        d.hours[6]=60000
        val e=dashboardAnalytics(CalendarDataset(mapOf(date to d),emptyMap()),emptyList(),HistoryPeriod.DAY,date,AchievementStore())
        assertEquals(60000L,e.focus);assertFalse(e.days.single().hourComplete);assertNull(e.score)
    }
    @Test fun estimationAndPunctualityNeverReverseLateOrInventUntimedTargets() {
        assertEquals("3m late",startDeltaDescription(180000));assertEquals("3m early",startDeltaDescription(-180000));assertEquals("Start comparison N/A",startDeltaDescription(null))
        val estimate=EstimationEvidence(80*60000,89*60000)
        assertEquals(9*60000L,estimate.delta);assertEquals(88.75,estimate.accuracy!!,.01)
        assertEquals("Oct 2026",analyticsPeriodLabel(HistoryPeriod.MONTH,date));assertEquals("2026",analyticsPeriodLabel(HistoryPeriod.YEAR,date))
    }
    @Test fun monthSlotsAreMondayFirstAndPreserveObservedZeroVersusMissing() {
        val slots=monthCalendarSlots(LocalDate.of(2026,10,9))
        assertEquals(35,slots.size);assertNull(slots[0]);assertNull(slots[2])
        assertEquals(LocalDate.of(2026,10,1),slots[3]);assertEquals(31,slots.filterNotNull().size)
        val leap=monthCalendarSlots(LocalDate.of(2024,2,15));assertEquals(29,leap.filterNotNull().size)
        val zero=CalendarDay(date,hasRecord=true)
        val buckets=analyticsBuckets(HistoryPeriod.WEEK,date,CalendarDataset(mapOf(date to zero),emptyMap()))
        assertEquals(0.0,buckets[4].value!!,0.0);assertNull(buckets[3].value)
    }

    @Test fun yearHasTwelveMonthsAndMeasuredHourTotalsWithoutFillingMissingMonths() {
        val observed=CalendarDay(date,focusMs=7_200_000,hasRecord=true)
        val b=analyticsBuckets(HistoryPeriod.YEAR,date,CalendarDataset(mapOf(date to observed),emptyMap()))
        assertEquals(12,b.size);assertEquals(1,b.first().start.monthValue);assertEquals(12,b.last().start.monthValue)
        assertEquals(7_200_000.0,b[9].value!!,0.0);assertNull(b[8].value);assertNull(b[10].value)
        assertEquals(7_200_000.0,b.mapNotNull { it.value }.sum(),0.0)
    }

    @Test fun ringsRepresentZeroPartialAndCompleteStoredProgress() {
        val seed=AchievementEngine.seed(date.atStartOfDay(zone).toInstant().toEpochMilli(),zone)
        val german=seed.definitions.first { it.id=="german" }
        fun stats(value: Double,target: Double)=AchievementEngine.stats(seed,german.copy(progress=AchievementProgress(value,target,"words")),date.atStartOfDay(zone).toInstant().toEpochMilli(),zone)
        assertEquals(0f,achievementRingFraction(stats(0.0,1250.0)),0f)
        assertEquals(32f/126,achievementRingFraction(stats(32.0,126.0)),.0001f)
        assertEquals(1f,achievementRingFraction(stats(1250.0,1250.0)),0f)
        val porn=seed.definitions.first { it.id=="porn_free" }
        assertEquals(0f,achievementRingFraction(AchievementEngine.stats(seed,porn,date.atStartOfDay(zone).toInstant().toEpochMilli(),zone)),0f)
    }

    @Test fun intermediateUnitOrTargetChangesCannotMergeSeparateMeasurementScopes() {
        val s=store();val h=s.progressHistory.last()
        val intervening=h.copy(id="unit-change",timestamp=h.timestamp-1000,unit="words")
        assertNull(completionContribution(s.copy(progressHistory=s.progressHistory+intervening),"project",date,date,zone))
        assertNull(completionContribution(s.copy(progressHistory=s.progressHistory+intervening.copy(unit=h.unit,targetValueAtThatTime=200.0)),"project",date,date,zone))
    }

    @Test fun focusReferencesExcludePartialLiveAndUnlocatedDaysWithoutInventingMissingZeroes() {
        fun day(offset: Long, minutes: Long, incomplete: Boolean=false, unlocated: Long=0) =
            CalendarDay(date.plusDays(offset),hasRecord=true,focusMs=minutes*60000,hourCoverage=true,
                incomplete=incomplete,unlocatedMs=unlocated).apply { hours[9]=minutes*60000-unlocated }
        val recorded=day(0,120)
        val partial=day(1,600,incomplete=true)
        val unlocated=day(2,600,unlocated=60000)
        val days=listOf(recorded,partial,unlocated,CalendarDay(date.plusDays(3)))
        assertEquals(120*60000.0,dailyFocusReference(days)!!,0.0)
        assertEquals(5*60000.0,hourlyFocusReference(days)!!,0.0)
        assertNull(hourlyFocusReference(listOf(partial,unlocated)))
        assertNull(dailyFocusReference(emptyList()))
    }

    @Test fun eligibleObservedZeroParticipatesInReferenceDenominator() {
        val active=CalendarDay(date,hasRecord=true,hourCoverage=true,focusMs=48*60000).apply { hours[8]=48*60000 }
        val zero=CalendarDay(date.plusDays(1),hasRecord=true,hourCoverage=true)
        assertEquals(24*60000.0,dailyFocusReference(listOf(active,zero))!!,0.0)
        assertEquals(60000.0,hourlyFocusReference(listOf(active,zero))!!,0.0)
        assertEquals(0.0,hourlyFocusReference(listOf(zero))!!,0.0)
    }

    @Test fun referenceLabelsAlwaysRetainHoursAndMinutes() {
        assertEquals("0h 30m",referenceDuration(30*60000.0))
        assertEquals("2h 15m",referenceDuration(135*60000.0))
        assertEquals("0h 00m",referenceDuration(0.0))
    }

    @Test fun calendarBinsConserveLocatedHoursAndNeverInventUnlocatedZeroes() {
        assertEquals(listOf(null,null,null),calendarFocusBins(null))
        assertEquals(listOf(null,null,null),calendarFocusBins(CalendarDay(date,hasRecord=true)))
        val d=CalendarDay(date,hasRecord=true,hourCoverage=true).apply { hours[7]=3600000;hours[8]=1800000;hours[23]=7200000 }
        assertEquals(listOf(1.0,.5,2.0),calendarFocusBins(d))
        assertEquals(d.hours.sum()/3600000.0,calendarFocusBins(d).filterNotNull().sum(),0.0)
        assertEquals(listOf(0.0,0.0,0.0),calendarFocusBins(CalendarDay(date,hasRecord=true,hourCoverage=true)))
    }
    @Test fun suppliedObservationClockAlsoOwnsPeriodClosure() {
        val observed=CalendarDataset(mapOf(date to CalendarDay(date,zones=linkedSetOf(zone),hasRecord=true)),emptyMap())
        val before=date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val after=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertFalse(PerformanceAnalytics.report(observed,HistoryPeriod.DAY,date,before).period_closed)
        assertTrue(PerformanceAnalytics.report(observed,HistoryPeriod.DAY,date,after).period_closed)
    }

}
