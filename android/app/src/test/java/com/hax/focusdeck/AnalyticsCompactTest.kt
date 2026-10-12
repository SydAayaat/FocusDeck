package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AnalyticsCompactTest {
    private val date=LocalDate.of(2026,10,9)
    private fun data(vararg values: Pair<LocalDate,List<Long>>) = CalendarDataset(values.associate { (d,starts) -> d to CalendarDay(d,hasRecord=true,punctuality=starts.toMutableList()) },emptyMap())
    @Test fun dayUsesTrailingContextButVarianceKeepsSelectedDay() {
        val data=data(date.minusDays(6) to listOf(-60000),date to listOf(120000,0),date.minusDays(7) to listOf(900000),date.plusDays(1) to listOf(900000))
        val trend=punctualityTrend(data,HistoryPeriod.DAY,date)
        assertEquals(7,trend.buckets.size);assertEquals("Sat",trend.buckets.first().label);assertEquals("Fri",trend.buckets.last().label)
        assertEquals(20000.0,trend.averageMs!!,0.0);assertEquals(60000.0,trend.buckets.last().meanMs!!,0.0)
        assertNull(trend.buckets[1].meanMs)
        val e=dashboardAnalytics(data,emptyList(),HistoryPeriod.DAY,date,AchievementStore())
        assertEquals("+1m",analyticsKpis(data,emptyList(),e).last().value)
    }
    @Test fun weekAveragesObservationsAndPreservesSignedZeroAndMissing() {
        val data=data(date.minusDays(4) to listOf(-60000,-60000,-60000),date to listOf(180000),date.minusDays(3) to listOf(0))
        val trend=punctualityTrend(data,HistoryPeriod.WEEK,date)
        assertEquals("Mon",trend.buckets.first().label);assertEquals("Sun",trend.buckets.last().label)
        assertEquals(0.0,trend.averageMs!!,0.0)
        assertEquals(3,trend.buckets.first().samples);assertEquals(-60000.0,trend.buckets.first().meanMs!!,0.0)
        assertEquals(0.0,trend.buckets[1].meanMs!!,0.0);assertNull(trend.buckets[2].meanMs)
        assertEquals("+30s",signedStartDelta(30000.0));assertEquals("−2m",signedStartDelta(-120000.0))
    }
    @Test fun monthUsesIntersectingMondayWeeksWithoutLosingEdgeDates() {
        val start=date.withDayOfMonth(1);val end=start.plusMonths(1).minusDays(1)
        val trend=punctualityTrend(data(start to listOf(-120000),end to listOf(60000),start.minusDays(1) to listOf(900000)),HistoryPeriod.MONTH,date)
        assertEquals(listOf("1","5","12","19","26"),trend.buckets.map { it.label })
        assertEquals(2,trend.buckets.sumOf { it.samples });assertEquals(-30000.0,trend.averageMs!!,0.0)
        assertNull(trend.buckets[1].meanMs)
    }
    @Test fun yearUsesMonthsAndSourceDatesAcrossDstWithoutReattribution() {
        val dst=LocalDate.of(2026,3,29)
        val trend=punctualityTrend(data(dst to listOf(60000),dst.plusDays(1) to listOf(-60000),date to listOf(180000)),HistoryPeriod.YEAR,date)
        assertEquals(12,trend.buckets.size);assertEquals("Mar",trend.buckets[2].label)
        assertEquals(0.0,trend.buckets[2].meanMs!!,0.0);assertNull(trend.buckets[1].meanMs)
        assertEquals(60000.0,trend.averageMs!!,0.0)
        assertNull(punctualityTrend(data(),HistoryPeriod.YEAR,date).averageMs)
    }
    private fun report(id: String,day: LocalDate): Report {
        val start=day.atTime(9,0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val timer=TimerEngine()
        timer.load(Session(1,id,id,"2026-10-09T00:00:00Z",listOf(Block("f","focus","Work",60))),true,ClockNow(start,0,1))
        timer.done(ClockNow(start+60000,60000,1))
        return timer.report(ClockNow(start+60000,60000,1))!!.copy(recordedTimezone="UTC")
    }
    @Test fun kpisUseDistinctAttemptsAndCoverageGatedPreviousPeriod() {
        val a=report("a",date);val b=report("b",date);val old=report("old",date.minusDays(1))
        val reports=listOf(a,b,old,a)
        val data=CalendarAnalytics.dataset(reports)
        val now=date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val e=dashboardAnalytics(data,reports,HistoryPeriod.DAY,date,AchievementStore(),now)
        val kpis=analyticsKpis(data,reports,e)
        assertEquals(listOf("FOCUS","SESSIONS","BLOCKS DONE","VARIANCE"),kpis.map { it.label })
        assertEquals("2",kpis[1].value);assertEquals("2",kpis[2].value)
        assertEquals(100.0,kpis[1].trend!!,0.0);assertEquals(100.0,kpis[2].trend!!,0.0)
        assertNull(kpis.last().varianceMs)
        data.days.getValue(date).incomplete=true
        val partial=dashboardAnalytics(data,reports,HistoryPeriod.DAY,date,AchievementStore(),now)
        assertTrue(analyticsKpis(data,reports,partial).take(3).all { it.trend==null })
    }
    @Test fun unavailableKpisDoNotInventZeroOrCompareAgainstZeroDenominator() {
        val data=data()
        val e=dashboardAnalytics(data,emptyList(),HistoryPeriod.DAY,date,AchievementStore())
        assertTrue(analyticsKpis(data,emptyList(),e).all { it.value=="N/A" && it.trend==null })
        val zero=data(date to emptyList(),date.minusDays(1) to emptyList())
        val e0=dashboardAnalytics(zero,emptyList(),HistoryPeriod.DAY,date,AchievementStore(),date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val metrics=analyticsKpis(zero,emptyList(),e0)
        assertEquals("0",metrics[1].value);assertEquals("0",metrics[2].value)
        assertTrue(metrics.all { it.trend==null });assertEquals("N/A",metrics.last().value)
    }
}
