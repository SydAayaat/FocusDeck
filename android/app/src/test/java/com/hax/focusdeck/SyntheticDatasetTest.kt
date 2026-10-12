package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SyntheticDatasetTest {
    private fun root()=File("../../fixtures/developer-mode-3years/${SyntheticDataset.ASSET_ROOT}")
    private fun load()=SyntheticDataset.load { File(root(),it).inputStream() }
    @Test fun suppliedRecordsReconcileToCachesAndUseNativeFormulas() {
        val d=load()
        assertEquals(LocalDate.of(2026,10,12),d.date)
        assertEquals(1097,d.daily.size);assertEquals(1299,d.reports.size);assertEquals(199,d.periods.size)
        assertEquals(d.lifetime,d.reports.sumOf { it.focusedMs })
        assertTrue(d.reports.any { it.blocks.any { b->b.timerMode=="stopwatch" && b.plannedMs==null } })
        assertTrue(d.reports.any { it.blocks.any { b->b.outcome=="skipped" } })
        assertTrue(d.reports.filter { it.breakMs>0 }.all { it.plannedMs==null && reportDurationComparison(it)==null })
        assertTrue(d.reports.filter { it.breakMs==0L && it.status=="complete" && it.blocks.all { b->b.timerMode=="countdown" } }.all { reportDurationComparison(it)!=null })
        assertTrue(d.reports.all { it.competition!!.focusRatio==RewardEngine.focus(it.focusedMs,it.pausedMs) && it.competition.reward.actions.isEmpty() })
        assertTrue(d.calendar.activities.values.filter { it.status=="skipped" }.all { it.start==null })
        assertTrue(d.calendar.activities.values.filter { it.status=="Not started" }.all { it.start==null })
        listOf(HistoryPeriod.DAY,HistoryPeriod.WEEK,HistoryPeriod.MONTH,HistoryPeriod.YEAR,HistoryPeriod.ALL).forEach { p->
            val e=dashboardAnalytics(d.calendar,d.reports,p,d.date,d.store,d.now)
            assertEquals(e.days.sumOf { it.focusMs },e.focus)
            assertEquals(PerformanceAnalytics.report(d.calendar,p,d.date,d.now).execution.score,e.score)
        }
        assertEquals(4680000L,d.home.focus);assertEquals(52,d.store.indexes.getValue("index_ptfs_dev").completed())
        assertEquals(63,d.store.indexes.getValue("index_embedded_dev").completed())
        d.store.indexes.keys.forEach { id->
            assertTrue(d.store.days.values.none { id in it.activities || it.statuses[id]=="MET" })
            val def=d.store.definitions.first { it.id==id }
            assertEquals(0.0,AchievementEngine.stats(d.store,def,d.now,d.zone).current,0.0)
        }
        val unknown=d.daily.entries.first { it.value["evidence"].toString()=="\"unknown\"" }.key
        assertFalse(unknown in d.calendar.days)
        val zero=d.daily.entries.first { it.value["evidence"].toString()=="\"observed_zero\"" }.key
        assertTrue(d.calendar.days.getValue(zero).hourComplete);assertEquals(0L,d.calendar.days.getValue(zero).focusMs)
        d.store.definitions.filter { it.id.startsWith("ach_") }.forEach { def->
            val stats=AchievementEngine.stats(d.store,def,d.now,d.zone)
            assertTrue(stats.current>=0);assertTrue(def.progress?.currentValue?.isFinite() ?: true)
        }
    }
    @Test fun feedPagesKeepStableIdentityOrderingAndOriginalReportLinks() {
        val d=load();val a=d.historyPage(emptySet(),"",null,null,false,0)
        val b=d.historyPage(emptySet(),"",null,null,false,80)
        assertEquals(80,a.size);assertEquals(80,b.size);assertTrue(a.map { it.id }.toSet().intersect(b.map { it.id }.toSet()).isEmpty())
        assertTrue(a.zipWithNext().all { (x,y)->x.at>=y.at })
        val oldest=d.historyPage(emptySet(),"",null,null,true,0)
        assertEquals(LocalDate.of(2023,10,12),CalendarAnalytics.date(oldest.first().at,d.zone))
        assertTrue(oldest.zipWithNext().all { (x,y)->x.at<=y.at })
        val filtered=d.historyPage(setOf(ActivityCategory.SESSIONS),"Control",d.date,d.date,false,0)
        assertTrue(filtered.isNotEmpty());assertTrue(filtered.all { r->d.reports.any { it.key==r.reportKey } });assertTrue(filtered.all { it.punctuality==1140000L })
        assertTrue(d.historyPage(emptySet(),"no-such-fixture-xyz",null,null,false,0).isEmpty())
    }
    @Test fun corruptAssetsFailClosedBeforeDataBecomesAccessible() {
        try { SyntheticDataset.load { name->if(name=="projects.json") "[]".byteInputStream() else File(root(),name).inputStream() };fail("Corruption accepted") }
        catch(expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("checksum")) }
    }
}
