package com.hax.focusdeck

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisualIsolationUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun manifestCannotLaunchProductionOrUseNetwork() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.hax.focusdeck.visualtest",context.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.INTERNET))
        assertEquals(android.app.Application::class.java,context.applicationContext.javaClass)
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
        assertFalse(info.activities.orEmpty().any { it.name.endsWith("MainActivity") })
        assertTrue(info.services.isNullOrEmpty())
    }
    @Test fun immersiveNavigationAndFullWidthPlateAreReachable() {
        val fixture=VisualTestDataProvider()
        var navigated=false
        ui.setContent {
            DeckTheme("gold","OFF",true) {
                CompositionLocalProvider(LocalDashboardPreview provides true,LocalPrimaryNavigation provides PrimaryNavigation("analytics",{navigated=true},{},{},{})) {
                    Surface { AnalyticsDashboard(fixture.calendar,fixture.reports,HistoryPeriod.DAY,fixture.date,{},{},Modifier.fillMaxSize(),openReport={},copy={},delete=null,
                        header={AnalyticsHeader(HistoryPeriod.DAY,fixture.date,{}, {},"Connected",{}, {}, {},{navigated=true},{})},archiveActions={},presentation=fixture.analytics(HistoryPeriod.DAY,fixture.date)) }
                }
            }
        }
        ui.waitUntil(10000) { ui.onAllNodesWithTag("analytics-plan-score").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("dashboard-navigation").assertDoesNotExist()
        val plate=ui.onNodeWithTag("analytics-plan-score").fetchSemanticsNode().boundsInRoot
        val split=ui.onNodeWithTag("analytics-distribution").fetchSemanticsNode().boundsInRoot
        val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(split.right-plate.left > root.width*.98f)
        assertEquals(plate.top,split.top,1f);assertEquals(plate.bottom,split.bottom,1f)
        assertTrue(plate.right<split.left);assertTrue(split.right<=root.right)
        ui.onNodeWithTag("immersive-navigation").assertDoesNotExist()
        ui.onNodeWithTag("analytics-options").performClick()
        ui.onNodeWithText("Home").performClick()
        ui.runOnIdle { assertTrue(navigated) }
    }
    @Test fun immersiveMenuUsesExistingCallbacksAndNormalModeKeepsBottomNavigation() {
        val selected=mutableStateOf("home");val immersive=mutableStateOf(true)
        ui.setContent { DeckTheme("gold","OFF",immersive.value) {
            CompositionLocalProvider(LocalPrimaryNavigation provides PrimaryNavigation(selected.value,
                {selected.value="home"},{selected.value="achievements"},{selected.value="analytics"},{selected.value="focus"})) {
                Column { AnalyticsHeader(HistoryPeriod.DAY,java.time.LocalDate.of(2026,10,12),{},{},"Offline",{},{},{},home={selected.value="home"},achievements={selected.value="achievements"});if(!immersive.value) DashboardNavigation(selected.value,{},{},{},{}) }
            }
        } }
        listOf("Achievements","Focus session","Home").forEach { destination ->
            ui.onNodeWithTag("analytics-options").performClick()
            ui.onNodeWithText(destination).performClick()
            ui.runOnIdle { assertEquals(when(destination) {"Achievements"->"achievements";"Focus session"->"focus";else->"home"},selected.value) }
        }
        ui.runOnIdle { immersive.value=false }
        ui.onNodeWithTag("immersive-navigation").assertDoesNotExist()
        ui.onNodeWithTag("dashboard-navigation").assertIsDisplayed()
    }
    @Test fun allPeriodsUseSharedPairsAndKeepTheFinalCardReachable() {
        val fixture=VisualTestDataProvider()
        val period=mutableStateOf(HistoryPeriod.DAY)
        ui.setContent { DeckTheme("gold","OFF",true) { CompositionLocalProvider(LocalDashboardPreview provides true) {
            Box(Modifier.width(411.dp).fillMaxHeight()) { AnalyticsDashboard(fixture.calendar,fixture.reports,period.value,fixture.date,{period.value=it},{},
                openReport={},copy={},delete=null,header={},archiveActions={},presentation=fixture.analytics(period.value,fixture.date),observationNow=fixture.now) }
        } } }
        listOf(HistoryPeriod.DAY,HistoryPeriod.WEEK,HistoryPeriod.MONTH,HistoryPeriod.YEAR).forEach { p ->
            ui.runOnIdle { period.value=p }
            // Month and Year share the saved aggregate scroll state; bring the plate into composition.
            ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-plan-score"))
            ui.waitUntil(10000) { ui.onAllNodesWithTag("analytics-plan-score").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-kpi-VARIANCE"))
            listOf("FOCUS","SESSIONS","BLOCKS DONE","VARIANCE").forEach { ui.onNodeWithTag("analytics-kpi-$it").assertIsDisplayed() }
            ui.onNodeWithText("BREAK TIME").assertDoesNotExist()
            ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-execution"))
            val execution=ui.onNodeWithTag("analytics-execution").fetchSemanticsNode().boundsInRoot
            val trend=ui.onNodeWithTag("analytics-punctuality").fetchSemanticsNode().boundsInRoot
            assertEquals(execution.top,trend.top,1f);assertEquals(execution.bottom,trend.bottom,1f)
            assertTrue(execution.right<trend.left);assertTrue(trend.right<=ui.onRoot().fetchSemanticsNode().boundsInRoot.right)
            ui.waitUntil(10000) { ui.onAllNodesWithTag("punctuality-bars").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("punctuality-bars").assertIsDisplayed()
            ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-telemetry"))
            ui.onNodeWithTag("analytics-telemetry").assertIsDisplayed()
        }
    }
    @Test fun longTextAndEmptyStateRemainScrollable() {
        val fixture=VisualTestDataProvider(VisualTestState.LONG_TEXT)
        ui.setContent { DeckTheme("gold","OFF") { CompositionLocalProvider(LocalDashboardPreview provides true) {
            Surface { Box(Modifier.width(320.dp).fillMaxHeight()) { AnalyticsDashboard(fixture.calendar,fixture.reports,HistoryPeriod.DAY,fixture.date,{},{},openReport={},copy={},delete=null,header={},archiveActions={},presentation=fixture.analytics(HistoryPeriod.DAY,fixture.date)) } }
        } } }
        ui.waitUntil(10000) { ui.onAllNodesWithTag("analytics-plan-score").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-telemetry"))
        ui.onNodeWithTag("analytics-telemetry").assertIsDisplayed()
    }
    @Test fun compactDateLabelsAndArrowsRemainVisibleForEveryPeriod() {
        val f=VisualTestDataProvider()
        val period=mutableStateOf(HistoryPeriod.DAY);val anchor=mutableStateOf(f.date)
        var picked=false
        ui.setContent { DeckTheme("gold","OFF",true) {
            CompositionLocalProvider(LocalPrimaryNavigation provides PrimaryNavigation("analytics",{},{},{},null)) {
                Box(Modifier.width(411.dp)) { AnalyticsHeader(period.value,anchor.value,{period.value=it},{anchor.value=it},"Connected",{picked=true},{},{}) }
            }
        } }
        listOf(HistoryPeriod.DAY,HistoryPeriod.WEEK,HistoryPeriod.MONTH,HistoryPeriod.YEAR).forEach { p ->
            ui.runOnIdle { period.value=p;anchor.value=f.date }
            val label=analyticsPeriodLabel(p,f.date)
            val node=ui.onNode(hasText(label) and hasAnyAncestor(hasTestTag("analytics-date")))
            node.assertIsDisplayed()
            val layouts=mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1,layouts.size);assertFalse("Date label must fit: $p",layouts.single().hasVisualOverflow)
            assertEquals("Compact date stays on one line: $p",1,layouts.single().lineCount)
            val plate=ui.onNodeWithTag("analytics-date").fetchSemanticsNode().boundsInRoot
            val before=ui.onNodeWithContentDescription("Previous ${p.label}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val after=ui.onNodeWithContentDescription("Next ${p.label}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(before.left>=plate.left && after.right<=plate.right)
            node.performClick();ui.runOnIdle { assertTrue(picked);picked=false }
            ui.onNodeWithContentDescription("Next ${p.label}").performClick()
            ui.runOnIdle { assertEquals(StudyHistory.shift(p,f.date,1),anchor.value) }
        }
    }
}
