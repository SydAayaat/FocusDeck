package com.hax.focusdeck

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SyntheticDatasetUiTest {
    @get:Rule val ui=createComposeRule()
    private fun data(): SyntheticDataset {
        val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
        return VisualTestDataProvider.threeYears { assets.open("${SyntheticDataset.ASSET_ROOT}/$it") }
    }
    @Test fun realAssetsAreOfflineAndOnlyPreviewSwitchChanges() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.hax.focusdeck.visualtest",context.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.INTERNET))
        val d=data();val enabled=mutableStateOf(true);val actual="Actual preserved report · 12345ms"
        val before=d.reports.map { it.focusedMs }
        ui.setContent { DeckTheme("gold","OFF",true) {
            if(enabled.value) SyntheticPreview(d,{enabled.value=false}) else Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) { Text(actual) }
        } }
        ui.onNodeWithTag("synthetic-banner").assertIsDisplayed()
        ui.onNodeWithText("Simulated 2026-10-12 · 23:59:30 Europe/Berlin").assertIsDisplayed()
        if(ui.onAllNodesWithTag("synthetic-disable").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithTag("synthetic-disable").performClick()
        else {ui.onNodeWithTag("synthetic-menu").performClick();ui.onNodeWithText("Disable Developer Mode").performScrollTo().performClick()}
        ui.onNodeWithText(actual).assertIsDisplayed();ui.onNodeWithTag("synthetic-banner").assertDoesNotExist()
        ui.runOnIdle { assertEquals(before,d.reports.map { it.focusedMs });enabled.value=true }
        ui.onNodeWithTag("synthetic-banner").assertIsDisplayed()
    }
    @Test fun suppliedHistoryPagesFilterAndNavigateToExactSummary() {
        val d=data()
        ui.setContent { DeckTheme("gold","OFF",true) { SyntheticPreview(d,{},"history") } }
        ui.waitUntil(20000) { ui.onAllNodesWithText("Loading recorded activity…").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("history-next").performClick();ui.onNodeWithText("Page 2").assertIsDisplayed()
        ui.onNodeWithTag("history-previous").performClick();ui.onNodeWithText("Page 1").assertIsDisplayed()
        ui.onNodeWithTag("history-filter-SESSIONS").performClick()
        ui.waitUntil(20000) { ui.onAllNodesWithText("Synthetic · session finished").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("activity-history").performScrollToNode(hasText("Synthetic · session finished"))
        ui.onAllNodesWithText("Synthetic · session finished").onFirst().performClick()
        ui.onNodeWithTag("session-summary").assertIsDisplayed()
        ui.onNode(hasText(d.latest!!.title) and hasAnyAncestor(hasTestTag("session-summary"))).assertIsDisplayed()
        ui.onNodeWithText("COPY FULL REPORT").assertDoesNotExist();ui.onNodeWithText("SAVE REPORT").assertDoesNotExist();ui.onNodeWithText("SEND REPORT").assertDoesNotExist()
    }
    @Test fun allAnalyticsPeriodsAndIndexUseRealSuppliedProjections() {
        val d=data()
        ui.setContent { DeckTheme("gold","OFF",true) { SyntheticPreview(d,{},"day") } }
        ui.waitUntil(20000) { ui.onAllNodesWithTag("analytics-plan-score").fetchSemanticsNodes().isNotEmpty() }
        listOf("Week","Month","Year").forEach { p ->
            ui.onNodeWithText(p,useUnmergedTree=true).performClick()
            ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-kpi-FOCUS"))
            ui.onNodeWithTag("analytics-kpi-FOCUS").assertIsDisplayed()
        }
        ui.onNodeWithTag("synthetic-menu").performClick();ui.onNodeWithText("Index").performClick()
        ui.onNodeWithTag("Achievement cards Index").performScrollToNode(hasText("PTfS — Synthetic Demonstration Index"))
        ui.onNodeWithText("PTfS — Synthetic Demonstration Index").performClick()
        ui.onNodeWithTag("achievement-detail-list").performScrollToNode(hasText("52 / 126 topics • 41.27%"))
        ui.onNodeWithText("52 / 126 topics • 41.27%").assertExists()
        ui.onNodeWithText("Actual study today: NO_TARGET").assertExists()
        ui.onNodeWithText("CONFIRM ACTUAL STUDY TODAY").assertDoesNotExist()
    }
    @Test fun suppliedLongSummaryUsesItsActualSceneTitleAndKeepsExportsBlocked() {
        val d=data()
        ui.setContent { DeckTheme("gold","OFF",true) { SyntheticPreview(d,{},"summary","long_text") } }
        ui.onNode(hasText("Extremely Long Synthetic Programming Techniques for Supercomputers Optimization Defense Session") and hasAnyAncestor(hasTestTag("session-summary"))).assertIsDisplayed()
        ui.onNodeWithText("SAVE REPORT").assertDoesNotExist()
    }
    @Test fun suppliedActiveAndPausedScenesRetainFixedClockAndNoCommands() {
        val d=data()
        ui.setContent { DeckTheme("gold","OFF",true) { SyntheticPreview(d,{},"home","paused") } }
        ui.onNodeWithTag("home-grid").performScrollToNode(hasText("Current Session"))
        ui.onNodeWithText("Synthetic Study Sprint").assertIsDisplayed()
        ui.onNodeWithText("Paused · 31m 20s focus").assertExists()
        ui.onNodeWithText("DONE").assertDoesNotExist()
        if(ui.onAllNodesWithTag("synthetic-scenario").fetchSemanticsNodes().isNotEmpty()) {ui.onNodeWithTag("synthetic-scenario").performClick();ui.onNodeWithText("active").performClick()}
        else {ui.onNodeWithTag("synthetic-menu").performClick();ui.onNodeWithText("Scene: active").performScrollTo().performClick()}
        ui.onNodeWithTag("home-grid").performScrollToNode(hasText("Synthetic Deep Work"))
        ui.onNodeWithText("Synthetic Deep Work").assertIsDisplayed()
        ui.onNodeWithText("Simulated 2026-10-12 · 23:59:30 Europe/Berlin").assertIsDisplayed()
    }
}
