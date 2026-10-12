package com.hax.focusdeck

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OmssHandoffUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun homeHistoryTimelineSummaryAndBackKeepTheirCallbacks() {
        val f=VisualTestDataProvider()
        val screen=mutableStateOf("home");var selectedReport: Report?=null
        val nav=TimelineNavigationState()
        ui.setContent { DeckTheme("gold","FULL",true) { CompositionLocalProvider(LocalDashboardPreview provides true,LocalDashboardDate provides f.date,
            LocalTimelineNavigation provides nav,LocalPrimaryNavigation provides PrimaryNavigation("home",{screen.value="home"},{screen.value="achievements"},{screen.value="analytics"},null)) {
            Surface { when(screen.value) {
                "home" -> HomeDashboard(f.calendar,f.homeLatest,0,null,f.date,f.now,false,{screen.value="history"},{},{selectedReport=it;screen.value="summary"},{screen.value="timeline"},{},true)
                "timeline" -> ExpandedTimelineScreen(f.calendar,f.reports,f.date,{screen.value="home"},{selectedReport=it;screen.value="summary"})
                "history" -> ActivityHistoryScreen(f.reports,f.store,f.zone,f.date,{screen.value="home"},{selectedReport=it;screen.value="summary"},{})
                "summary" -> ReportScreen(selectedReport!!,{screen.value="timeline"})
            } }
        } } }
        ui.onNodeWithTag("home-grid").performScrollToNode(hasText("Current Session"))
        ui.onNodeWithText("No active session").assertIsDisplayed()
        ui.onNodeWithTag("home-grid").performScrollToNode(hasText("View all  →"))
        ui.onNodeWithText("View all  →").performClick()
        ui.onNodeWithTag("activity-history").assertIsDisplayed()
        ui.runOnIdle { screen.value="home" }
        ui.onNodeWithTag("home-grid").performScrollToNode(hasText("Daily Timeline"))
        ui.onNodeWithText("9 Oct  ›").performClick()
        ui.onNodeWithTag("expanded-timeline").assertIsDisplayed()
        val first=timelineHalfHours(f.calendar,f.date,f.zone).flatMap { it.activities }.first()
        ui.onNodeWithTag("timeline-event-${first.id}").performClick()
        ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag("timeline-summary-${first.id}"))
        ui.onNodeWithTag("timeline-summary-${first.id}").performClick()
        ui.onNodeWithTag("session-summary").assertIsDisplayed()
        ui.onNodeWithTag("session-summary").performScrollToNode(hasText("OVERALL FOCUS RATIO"))
        ui.onNodeWithText("OVERALL FOCUS RATIO").assertIsDisplayed()
        ui.onNodeWithTag("session-summary").performScrollToNode(hasText("Time Estimation Accuracy"))
        ui.onNodeWithText("Time Estimation Accuracy").assertIsDisplayed()
        ui.runOnIdle { assertEquals(first.reportKey,selectedReport!!.key) }
    }
    @Test fun historyMultiSelectSearchAndEmptyCategoryAreTruthful() {
        val f=VisualTestDataProvider()
        ui.setContent { DeckTheme("gold","OFF",true) { Surface { ActivityHistoryScreen(f.reports,f.store,f.zone,f.date,{},{},{}) } } }
        ui.onNodeWithTag("history-filter-GPT").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithText("No recorded activity matches these filters.").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("history-filter-SESSIONS").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithText("No recorded activity matches these filters.").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("history-search").performClick()
        ui.onNodeWithTag("history-query").performTextInput("No such session exists")
        ui.waitUntil(5000) { ui.onAllNodesWithText("No recorded activity matches these filters.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun partialSummaryKeepsDistinctOutcomesAndExports() {
        val f=VisualTestDataProvider(VisualTestState.MIXED)
        val report=f.partialReport!!;var copied=false
        ui.setContent { DeckTheme("gold","REDUCED",true) { Surface { ReportScreen(report,{},copy={copied=true}) } } }
        ui.onNodeWithTag("session-summary").performScrollToNode(hasText("Skipped · no completion credit"))
        ui.onNodeWithText("Skipped · no completion credit").assertIsDisplayed()
        ui.onNodeWithTag("session-summary").performScrollToNode(hasText("Not Started · no completion credit"))
        ui.onNodeWithText("Not Started · no completion credit").assertIsDisplayed()
        ui.onNodeWithTag("session-summary").performScrollToNode(hasText("COPY FULL REPORT"))
        ui.onNodeWithText("SAVE REPORT").assertIsDisplayed()
        ui.onNodeWithText("COPY FULL REPORT").performClick()
        ui.runOnIdle { assertTrue(copied) }
    }
    @Test fun expansionReflowRetainsTimestampAndIdentityForAllMotionModes() {
        val f=VisualTestDataProvider()
        val first=timelineHalfHours(f.calendar,f.date,f.zone).flatMap { it.activities }.first()
        val mode=mutableStateOf("FULL")
        ui.setContent { DeckTheme("gold",mode.value,true) { Surface { ExpandedTimelineScreen(f.calendar,f.reports,f.date,{},{}) } } }
        listOf("FULL","REDUCED","OFF").forEach { m ->
            ui.runOnIdle { mode.value=m }
            ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag("timeline-event-${first.id}"))
            val before=ui.onNodeWithTag("timeline-event-${first.id}").fetchSemanticsNode().boundsInRoot
            ui.onNodeWithTag("timeline-event-${first.id}").performClick()
            ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag("timeline-summary-${first.id}"))
            ui.onNodeWithText("Time Estimation Accuracy").assertIsDisplayed()
            ui.onNodeWithTag("timeline-event-${first.id}").performClick()
            ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag("timeline-event-${first.id}"))
            val after=ui.onNodeWithTag("timeline-event-${first.id}").fetchSemanticsNode().boundsInRoot
            assertEquals(before.height,after.height,2f)
        }
    }
}
