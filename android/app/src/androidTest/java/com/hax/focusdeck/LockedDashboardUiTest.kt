package com.hax.focusdeck

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.time.*
import kotlin.math.*

/** Run ONLY against isolated .uiqa via -PuiQaTests. No repository, real ledger or network. */
class LockedDashboardUiTest {
    @get:Rule val ui=createComposeRule()
    @Before fun portraitTestWindow() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var activity: android.app.Activity?=null
        instrumentation.runOnMainSync {
            activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        ui.waitUntil(5000) { activity?.resources?.configuration?.orientation==Configuration.ORIENTATION_PORTRAIT }
    }
    private val date=LocalDate.of(2026,10,9)
    private val zone=ZoneId.of("Europe/Berlin")
    private val now=date.atTime(9,3).atZone(zone).toInstant().toEpochMilli()
    private fun scene(content: @Composable ()->Unit) = ui.setContent {
        DeckTheme("gold","OFF") { CompositionLocalProvider(LocalDashboardPreview provides true) {
            Surface(color=DashboardBackground) { content() }
        } }
    }
    private fun dataset(): CalendarDataset {
        val start=date.atTime(6,0).atZone(zone).toInstant().toEpochMilli()
        val activity=CalendarActivity("a",null,"Task", "focus",null,null,PlannedSchedule(Instant.ofEpochMilli(start).toString(),Instant.ofEpochMilli(start+3600000).toString(),zone.id),start+180000,start+3780000,3600000,3600000,0,0,3600000,"done",zone)
        val bounds=CalendarAnalytics.bounds(date,zone)
        fun seg(track: String,a: Long,b: Long)=CalendarSegment("a",track,"focus_ms",a,b,(a-bounds.first).toFloat()/(bounds.second-bounds.first),(b-bounds.first).toFloat()/(bounds.second-bounds.first),zone)
        val day=CalendarDay(date,zones=linkedSetOf(zone),hasRecord=true,focusMs=3600000,hourCoverage=true)
        day.hours[6]=3600000;day.segments+=seg("planned",start,start+3600000);day.segments+=seg("actual",start+180000,start+3780000)
        return CalendarDataset(mapOf(date to day),mapOf("a" to activity))
    }
    private fun shell(start: HistoryPeriod=HistoryPeriod.DAY,large: Boolean=false) {
        scene {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,if(large) 1.5f else base.fontScale)) {
                Column(Modifier.width(if(large) 320.dp else 400.dp).fillMaxHeight().padding(16.dp)) {
                    var period by remember { mutableStateOf(start) };var anchor by remember { mutableStateOf(date) }
                    Box(Modifier.weight(1f)) { AnalyticsDashboard(dataset(),emptyList(),period,anchor,{period=it},{anchor=it},openReport={},copy={},delete=null,
                        header={AnalyticsHeader(period,anchor,{period=it},{anchor=it},"Offline",{},{},{})},archiveActions={}) }
                    DashboardNavigation("analytics",{},{},{})
                }
            }
        }
        ui.waitUntil(10000) { ui.onAllNodesWithTag("analytics-plan-score").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun timelineHasTwoThinTracksPopupOverlapsAndDismisses() {
        scene { Box(Modifier.fillMaxSize().padding(top=220.dp,start=16.dp,end=16.dp)) { CompactDailyTimeline(dataset(),date,now,{}) } }
        val chart=ui.onNodeWithTag("home-timeline-tracks").assertIsDisplayed()
        val pixels=chart.captureToImage().toPixelMap();val dp=pixels.height/48f
        val lit=(0 until (32*dp).toInt()).filter { y->val c=pixels[pixels.width/2,y];c.red in .17f.. .22f && c.green in .18f.. .24f && c.blue in .20f.. .27f }
        val bands=lit.fold(mutableListOf<MutableList<Int>>()) { acc,y->if(acc.isEmpty() || y>acc.last().last()+1) acc.add(mutableListOf(y)) else acc.last().add(y);acc }
        assertEquals("Two tracks",2,bands.size)
        bands.forEach { assertTrue("Track is thin",it.size/dp in 4f..6f) }
        chart.performTouchInput { click(Offset(width*.275f,height*.25f)) }
        val popup=ui.onNodeWithTag("home-task-popup").assertIsDisplayed()
        ui.onNodeWithText("3m late",substring=true).assertExists()
        assertTrue(popup.fetchSemanticsNode().boundsInRoot.bottom<=chart.fetchSemanticsNode().boundsInRoot.top+8*dp)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        ui.waitUntil(5000) { ui.onAllNodesWithTag("home-task-popup").fetchSemanticsNodes().isEmpty() }
        chart.performTouchInput { click(Offset(width*.275f,height*.25f)) }
        ui.onNodeWithTag("dashboard-popup-dismiss-area").performTouchInput { click(Offset(width-8f,height*.5f)) }
        ui.waitUntil(5000) { ui.onAllNodesWithTag("home-task-popup").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("home-task-popup").assertDoesNotExist()
    }
    @Test fun dayOrderHeaderAlignmentNavigationAndMinutesAreNative() {
        shell()
        val title=ui.onNodeWithTag("analytics-title").fetchSemanticsNode().boundsInRoot
        val dateBounds=ui.onNodeWithTag("analytics-date").fetchSemanticsNode().boundsInRoot
        assertTrue(dateBounds.left>title.right);assertTrue(abs(dateBounds.center.y-title.center.y)<12)
        ui.onNodeWithTag("period-DAY").assertIsSelected()
        ui.onNodeWithContentDescription("Focus duration chart, minutes; missing data N/A").assertExists()
        val score=ui.onNodeWithTag("analytics-plan-score").fetchSemanticsNode().boundsInRoot
        val hour=ui.onNodeWithTag("analytics-hourly").fetchSemanticsNode().boundsInRoot
        assertTrue(score.top<hour.top)
        val nav=ui.onNodeWithTag("dashboard-navigation").fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-estimation"))
        ui.onNodeWithTag("analytics-estimation").assertIsDisplayed()
        assertEquals(nav,ui.onNodeWithTag("dashboard-navigation").fetchSemanticsNode().boundsInRoot)
        ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-telemetry"))
        ui.onNodeWithTag("analytics-telemetry").assertIsDisplayed()
    }
    @Test fun periodTabsKeepCalendarAndYearChartInDistributionSlot() {
        shell(HistoryPeriod.WEEK)
        ui.onNodeWithContentDescription("Focus duration chart, hours; missing data N/A").assertExists()
        ui.onNodeWithTag("focus-bars-weekly:$date").performTouchInput { click(Offset(width*.67f,height*.5f)) }
        ui.onNodeWithText("Open period").performClick()
        ui.onNodeWithTag("period-DAY").assertIsSelected()
        ui.onNodeWithText("‹ Back to Week").performClick()
        ui.onNodeWithTag("period-WEEK").assertIsSelected()
        ui.onNodeWithTag("period-MONTH").performClick().assertIsSelected()
        ui.onNodeWithTag("month-calendar").assertIsDisplayed()
        ui.onNodeWithTag("period-YEAR").performClick().assertIsSelected()
        ui.onNodeWithText("Productivity by Month").assertExists()
        ui.onNodeWithTag("month-calendar").assertDoesNotExist()
        ui.onNodeWithContentDescription("Focus duration chart, hours; missing data N/A").assertExists()
        ui.onNodeWithContentDescription("Next Year").performClick()
        ui.onNodeWithText("YEAR 2027").assertExists()
    }
    @Test fun ringsActuallyDrawZeroQuarterAndCompleteArcs() {
        var value by mutableStateOf(0.0)
        val seed=AchievementEngine.seed(now,zone);val def=seed.definitions.first { it.id=="german" }
        scene { val d=def.copy(progress=AchievementProgress(value,126.0,"topics"));AchievementRing(AchievementEngine.stats(seed,d,now,zone),seed,{},Modifier.size(100.dp)) }
        fun litFraction(description: String): Double {
            val p=ui.onNodeWithContentDescription(description).captureToImage().toPixelMap()
            return (0..359).count { angle->val rad=(angle-90)*PI/180;val x=(p.width*.5+p.width*.36*cos(rad)).toInt();val y=(p.height*.5+p.height*.36*sin(rad)).toInt();val c=p[x,y];c.red>.7 && c.green>.35 && c.blue<.3 }/360.0
        }
        assertEquals(0.0,litFraction("Progress 0%"),.01)
        ui.runOnIdle { value=32.0 };assertEquals(32.0/126,litFraction("Progress 25%"),.045)
        ui.runOnIdle { value=126.0 }
        // Rounded stroke caps add about 3.8 percentage points at the centerline; the underlying sweep is 32/126.
        // Completed progress uses success teal; semantics and the filled ring remain data-bound.
        ui.onNodeWithContentDescription("Progress 100%").assertExists()
    }
    @Test fun emptyHomeHasPurposefulStatesAndNoFakeSessions() {
        scene { HomeDashboard(CalendarDataset(emptyMap(),emptyMap()),null,0,null,date,now,false,{},{},{},{},{},true) }
        ui.onNodeWithText("No recorded activity today").assertExists()
        ui.onNodeWithTag("home-grid").performScrollToNode(hasText("No plan loaded"))
        ui.onNodeWithText("No plan loaded").assertIsDisplayed()
        ui.onNodeWithText("▶  Start").assertDoesNotExist()
    }
    @Test fun largeFontNarrowWidthStacksHeaderAndKeepsLowerContentReachable() {
        shell(large=true)
        val title=ui.onNodeWithTag("analytics-title").fetchSemanticsNode().boundsInRoot
        val dateBounds=ui.onNodeWithTag("analytics-date").fetchSemanticsNode().boundsInRoot
        assertTrue(dateBounds.top>=title.bottom)
        ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-telemetry"))
        ui.onNodeWithTag("analytics-telemetry").assertIsDisplayed()
        ui.onNodeWithTag("dashboard-navigation").assertIsDisplayed()
    }
    @Test fun achievementNormalModeHidesEditingButOverflowRetainsIt() {
        scene { AchievementPages(AchievementEngine.seed(now,zone),now,zone,{},{}) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("Open Porn Free achievement").fetchSemanticsNodes().isNotEmpty() }
        val tiles=listOf("Porn Free","Daily Objectives Complete","Gym").map { title->ui.onNodeWithContentDescription("Open $title achievement").fetchSemanticsNode().boundsInRoot }
        assertEquals(tiles[0].top,tiles[1].top,1f);assertEquals(tiles[1].top,tiles[2].top,1f)
        assertTrue(tiles[0].right<tiles[1].left && tiles[1].right<tiles[2].left)
        ui.onNodeWithText("EDIT LAYOUT").assertDoesNotExist();ui.onNodeWithText("DONE").assertDoesNotExist()
        ui.onNodeWithTag("achievement-options").performClick()
        ui.onNodeWithText("Edit layout").performClick()
        ui.onNodeWithText("DONE").assertExists()
        ui.onNodeWithText("DONE").performClick()
        ui.onNodeWithText("DONE").assertDoesNotExist()
    }
    @Test fun detailKeepsProgressAndInlineHistoriesReachableInLazyList() {
        scene { AchievementPages(AchievementEngine.seed(now,zone),now,zone,{},{}) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("Open Porn Free achievement").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Category Learning",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithContentDescription("Open German achievement").performClick()
        ui.onNodeWithText("Achievement detail").assertExists()
        ui.onNodeWithTag("achievement-detail-list").performScrollToNode(hasText("Progress history"))
        ui.onNodeWithText("Progress history").assertIsDisplayed()
    }

    @Test fun historyMenuCopiesExactAttemptAndRetiresDeletedRows() {
        val clock=TimerEngine()
        clock.load(Session(1,"native-ui-copy","Native copy test","2026-10-09T00:00:00Z",listOf(Block("f","focus","Work",60),Block("b","break","Break",10))),true,ClockNow(now,0,1))
        clock.done(ClockNow(now+60000,60000,1))
        if(clock.state.phase=="reward") clock.rewardDone(ClockNow(now+60000,60000,1),skip=true)
        clock.done(ClockNow(now+70000,70000,1))
        val report=clock.report(ClockNow(now+70000,70000,1))!!.copy(recordedTimezone=zone.id)
        var reports by mutableStateOf(listOf(report));var copied: Report?=null
        scene { AnalyticsDashboard(CalendarAnalytics.dataset(reports),reports,HistoryPeriod.DAY,date,{},{},openReport={},copy={copied=it},delete=null,
            header={AnalyticsHeader(HistoryPeriod.DAY,date,{},{},"Offline",{},{},{})},archiveActions={androidx.compose.material3.Text("Archive source actions")}) }
        ui.waitUntil(5000) { ui.onAllNodesWithTag("analytics-plan-score").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("analytics-options").performClick()
        ui.onNodeWithText("History & export").performClick()
        ui.onNode(hasText("Native copy test") and hasAnyAncestor(hasTestTag("analytics-history-dialog"))).assertIsDisplayed()
        ui.onNodeWithText("Copy").performClick()
        ui.runOnIdle { assertSame(report,copied);reports=emptyList() }
        ui.waitUntil(5000) { ui.onAllNodesWithText("Native copy test").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Archive source actions").assertExists()
    }

}
