package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test

class VisualTestDataProviderTest {
    @Test fun allFixtureStatesHaveStableClockAndIndependentState() {
        VisualTestState.entries.forEach { state ->
            val a=VisualTestDataProvider(state);val b=VisualTestDataProvider(state)
            a.store.cardIcons.values.forEach { CardIconPolicy.validate(it.icon) }
            assertEquals(a.now,b.now);assertEquals(a.date,b.date)
            assertEquals(a.reports,b.reports);assertEquals(a.calendar,b.calendar);assertEquals(a.store,b.store)
            if(a.date in a.calendar.days) {
                a.calendar.days[a.date]?.focusMs=999
                assertNotEquals(a.calendar.days[a.date]?.focusMs,b.calendar.days[b.date]?.focusMs)
            }
        }
    }
    @Test fun emptyAndZeroRemainDifferentAndPartialDoesNotEnterAverages() {
        val empty=VisualTestDataProvider(VisualTestState.EMPTY)
        assertTrue(empty.calendar.days.isEmpty());assertNull(empty.analytics(HistoryPeriod.DAY,empty.date))
        val zero=VisualTestDataProvider(VisualTestState.ZERO)
        assertEquals(0L,zero.calendar.days.getValue(zero.date).focusMs)
        assertEquals(0.0,hourlyFocusReference(listOf(zero.calendar.days.getValue(zero.date)))!!,0.0)
        val partial=VisualTestDataProvider(VisualTestState.PARTIAL)
        val partialDay=partial.calendar.days.getValue(partial.date)
        assertNull(hourlyFocusReference(listOf(partialDay)))
        assertEquals(partialDay.focusMs,partialDay.hours.sum()+partialDay.unlocatedMs)
    }
    @Test fun illustrativeProjectionDoesNotModifyCalculationInputs() {
        val fixture=VisualTestDataProvider()
        val before=fixture.reports.toList()
        val measured=dashboardAnalytics(fixture.calendar,fixture.reports,HistoryPeriod.DAY,fixture.date,fixture.store,fixture.now)
        val display=fixture.analytics(HistoryPeriod.DAY,fixture.date)!!
        assertEquals(86.0,display.evidence.score!!,0.0)
        assertNotEquals(measured.focus,display.evidence.focus)
        assertEquals(before,fixture.reports)
        assertEquals(measured, dashboardAnalytics(fixture.calendar,fixture.reports,HistoryPeriod.DAY,fixture.date,fixture.store,fixture.now))
    }
    @Test fun fixtureIndexAndProgressUseActualNamedStates() {
        val f=VisualTestDataProvider()
        val index=f.store.definitions.first { it.id=="ptfs" }.progress!!
        assertEquals(32.0,index.currentValue,0.0);assertEquals(126.0,index.targetValue!!,0.0)
        assertEquals(1250.0,VisualTestDataProvider(VisualTestState.COMPLETED).store.definitions.first { it.id=="german" }.progress!!.currentValue,0.0)
    }
    @Test fun finalizedRowsHavePeriodAppropriateSignedObservationsAndSeparateTrends() {
        val f=VisualTestDataProvider()
        listOf(HistoryPeriod.DAY,HistoryPeriod.WEEK,HistoryPeriod.MONTH,HistoryPeriod.YEAR).forEach { p ->
            val display=f.analytics(p,f.date)!!
            assertEquals(listOf("FOCUS","SESSIONS","BLOCKS DONE","VARIANCE"),display.kpis!!.map { it.first })
            assertEquals(3,display.kpiTrends.size)
            val trend=punctualityTrend(f.calendar,p,f.date)
            assertNotNull(trend.averageMs);assertTrue(trend.buckets.any { (it.meanMs ?: 0.0)<0 })
            if(p==HistoryPeriod.YEAR) assertEquals("Jan",trend.buckets.first().label)
        }
        val positive=VisualTestDataProvider(VisualTestState.POSITIVE)
        assertTrue(punctualityTrend(positive.calendar,HistoryPeriod.WEEK,positive.date).averageMs!!>0)
        val negative=VisualTestDataProvider(VisualTestState.NEGATIVE)
        assertTrue(punctualityTrend(negative.calendar,HistoryPeriod.WEEK,negative.date).averageMs!!<0)
    }
}
