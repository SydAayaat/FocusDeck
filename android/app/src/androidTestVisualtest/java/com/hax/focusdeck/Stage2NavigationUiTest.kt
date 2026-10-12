package com.hax.focusdeck

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Stage2NavigationUiTest {
    @get:Rule val ui=createEmptyComposeRule()
    private fun launch(screen: String="home",immersive: Boolean=true)=ActivityScenario.launch<UiQaActivity>(Intent(InstrumentationRegistry.getInstrumentation().targetContext,UiQaActivity::class.java).putExtra("fixture","REFERENCE_3Y").putExtra("screen",screen).putExtra("immersive",immersive).putExtra("motion","OFF"))
    private fun ready(tag: String) { ui.waitUntil(20000) {ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()};ui.waitForIdle() }
    private fun menu(label: String) {ui.onNodeWithTag("synthetic-menu").performClick();ui.onNodeWithText(label).performScrollTo().performClick()}
    private fun back(scenario: ActivityScenario<UiQaActivity>) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use { descriptor->java.io.FileInputStream(descriptor.fileDescriptor).use {it.readBytes()} }
        ui.waitForIdle()
    }
    private fun rotate(scenario: ActivityScenario<UiQaActivity>,landscape: Boolean) {
        val tag=listOf("session-summary","expanded-timeline","achievement-detail-list","activity-history","analytics-grid").first {ui.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty()}
        var previous: UiQaActivity?=null;var changes=false
        scenario.onActivity {
            previous=it;changes=(it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)!=landscape
            it.requestedOrientation=if(landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        ui.waitUntil(20000) {var match=false;scenario.onActivity {match=(!changes || it!==previous) && (it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)==landscape};match}
        ready(tag)
    }
    @Test fun homeMenusSettingsDeveloperControlsDevicesAndManualFallbackInBothModes() {
        listOf(true,false).forEach { immersive ->launch(immersive=immersive).use {scenario->
            ready("home-grid");menu("Display settings");ui.onNodeWithText("Display & connection").assertIsDisplayed();back(scenario)
            menu("Developer Mode settings");ui.onNodeWithText("Developer mode").performScrollTo().assertIsDisplayed();back(scenario)
            menu("Devices");ui.onNodeWithText("No desktop paired").assertIsDisplayed();back(scenario)
            menu("Pair desktop");ui.onNodeWithTag("pair-scan").assertIsDisplayed();ui.onNodeWithTag("pair-json").performScrollTo().assertIsDisplayed();back(scenario)
            ui.onNodeWithTag("immersive-navigation").assertDoesNotExist()
        } }
    }
    @Test fun analyticsRotationPreservesPeriodAndDateLongPressKeepsGranularityAndBackReturnsHome() {
        launch("day").use {scenario->
            ready("analytics-grid");ui.onNodeWithTag("period-WEEK").performClick();ui.onNodeWithContentDescription("Previous Week").performClick()
            val historical=analyticsPeriodLabel(HistoryPeriod.WEEK,java.time.LocalDate.of(2026,10,5))
            ui.onNodeWithText(historical).assertIsDisplayed()
            rotate(scenario,true);ui.onNodeWithTag("period-WEEK").assertIsSelected();ui.onNodeWithText(historical).assertIsDisplayed()
            rotate(scenario,false);ui.onNodeWithText(historical).assertIsDisplayed()
            ui.onNodeWithTag("analytics-date-action").performTouchInput {longClick()}
            ui.onNodeWithTag("period-WEEK").assertIsSelected();ui.onNodeWithText(analyticsPeriodLabel(HistoryPeriod.WEEK,java.time.LocalDate.of(2026,10,12))).assertIsDisplayed()
            ui.onNodeWithTag("analytics-date-action").performClick();ui.onNodeWithText("Apply").assertIsDisplayed();back(scenario)
            ui.onNodeWithTag("immersive-navigation").assertDoesNotExist();back(scenario);ready("home-grid")
        }
    }
    @Test fun historyRotationPreservesSearchCategoryAndPage() {
        launch("history").use {scenario->
            ready("activity-history");ui.onNodeWithTag("history-filter-SESSIONS").performClick()
            ui.onNodeWithTag("history-search").performClick();ui.onNodeWithTag("history-query").performTextInput("Synthetic")
            ui.onNodeWithTag("history-query").performImeAction();ui.waitForIdle()
            ui.waitUntil(20000) {ui.onAllNodesWithText("Synthetic · session finished").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("history-next").performClick();ui.onNodeWithText("Page 2").assertExists()
            rotate(scenario,true);ui.onNodeWithTag("history-query").assertTextContains("Synthetic");ui.onNodeWithTag("history-filter-SESSIONS").assertIsSelected();ui.onNodeWithText("Page 2").assertExists()
            rotate(scenario,false);ui.onNodeWithTag("history-query").assertTextContains("Synthetic");ui.onNodeWithText("Page 2").assertExists()
        }
    }
    @Test fun achievementsRotationKeepsIndexCategoryAndSelectedCard() {
        launch("index").use {scenario->
            ready("Achievement cards Index")
            ui.onNodeWithTag("Achievement cards Index").performScrollToNode(hasText("PTfS — Synthetic Demonstration Index"))
            ui.onNodeWithText("PTfS — Synthetic Demonstration Index").performClick();ready("achievement-detail-list")
            rotate(scenario,true);ready("achievement-detail-list");rotate(scenario,false);ready("achievement-detail-list")
            back(scenario);ready("Achievement cards Index")
        }
    }
    @Test fun timelineExpansionAndSummaryIdentitySurviveBothRotations() {
        launch("timeline").use {scenario->
            ready("expanded-timeline")
            val event=ui.onAllNodes(hasTestTagPrefix("timeline-event-")).onFirst().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
            ui.onNodeWithTag(event).performClick()
            val summary="timeline-summary-"+event.removePrefix("timeline-event-")
            ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag(summary));ui.onNodeWithTag(summary).performScrollTo().assertIsDisplayed()
            rotate(scenario,true);ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag(summary));ui.onNodeWithTag(summary).performScrollTo().assertIsDisplayed()
            rotate(scenario,false);ui.onNodeWithTag(summary).performScrollTo().performClick();ready("session-summary")
            val selectedReportTag=ui.onNode(hasTestTagPrefix("summary-report-")).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
            rotate(scenario,true);ui.onNodeWithTag(selectedReportTag).assertExists()
            rotate(scenario,false);ui.onNodeWithTag(selectedReportTag).assertExists()
            back(scenario);ready("expanded-timeline");ui.onNodeWithTag("expanded-timeline").performScrollToNode(hasTestTag(summary));ui.onNodeWithTag(summary).performScrollTo().assertIsDisplayed()
        }
    }
    @Test fun offlineScannerOpensAndCancellationReturnsToManualFallback() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("pm grant com.hax.focusdeck.visualtest android.permission.CAMERA").use {descriptor->java.io.FileInputStream(descriptor.fileDescriptor).use {it.readBytes()} }
        launch().use {scenario->
            ready("home-grid");menu("Pair desktop");ui.onNodeWithTag("pair-scan").performClick()
            ui.waitUntil(10000) {
                automation.executeShellCommand("dumpsys activity activities").use {descriptor->java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText().lineSequence().any { line->line.contains("ResumedActivity") && line.contains("CaptureActivity") } } }
            }
            back(scenario)
            ready("pair-json");ui.onNodeWithTag("pair-json").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("pair-error").assertTextContains("QR scanning interrupted",substring=true)
        }
    }
    @Test fun estimationCardHasNoRemovedShortcuts() {
        launch("day").use {ready("analytics-grid");ui.onNodeWithTag("analytics-grid").performScrollToNode(hasTestTag("analytics-estimation"));ui.onNodeWithTag("analytics-estimation").assertIsDisplayed();ui.onNodeWithText("Daily Timeline & Session Detail").assertDoesNotExist();ui.onAllNodes(hasText("By project type",substring=true,ignoreCase=true)).assertCountEquals(0)}
    }
    @Test fun homeDoubleBackFinishesOnlyActivity() {
        launch().use {scenario->ready("home-grid");back(scenario);ready("home-grid");Thread.sleep(2150);back(scenario);ready("home-grid");back(scenario);ui.waitUntil(5000) {scenario.state==androidx.lifecycle.Lifecycle.State.DESTROYED};assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,scenario.state)}
    }
    private fun hasTestTagPrefix(prefix: String)=SemanticsMatcher("tag starts with $prefix") { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix)==true }
}
