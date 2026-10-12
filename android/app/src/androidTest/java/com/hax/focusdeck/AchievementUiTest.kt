package com.hax.focusdeck

import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Run on a disposable emulator/test installation; never requires real phone history. */
class AchievementUiTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-01-08T12:00:00Z").toEpochMilli()
    private fun ledger(positive: Boolean = false): AchievementStore {
        val day = "2026-01-01"
        var s = AchievementEngine.seed(Instant.parse("${day}T12:00:00Z").toEpochMilli(),zone)
        val days = (1..7).associate { i ->
            val key="2026-01-0$i"
            key to AchievementDay(key,zone.id,finalized=true,statuses=mapOf("german" to if(i in listOf(1,2) || i>=4) "MET" else "UNMET"))
        }
        s=s.copy(days=days)
        if (!positive) s=s.copy(days=s.days.mapValues { (k,v) -> if (k>="2026-01-05") v.copy(statuses=mapOf("german" to "UNMET")) else v })
        return s
    }
    private fun show(store: AchievementStore) {
        compose.setContent { MaterialTheme(colorScheme=darkColorScheme()) {
            AchievementPages(store,now,zone,{}, {})
        } }
    }
    @Test fun category_row_scrolls_and_filters_only_tiles() {
        show(ledger())
        compose.onNodeWithContentDescription("Category College",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Overview").assertExists()
        compose.onNodeWithText("No college achievements yet. GPT can create them through your desktop.").assertExists()
        compose.onNodeWithContentDescription("Category Learning",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Open German achievement").assertExists()
        compose.onNodeWithContentDescription("Open Gym achievement").assertDoesNotExist()
    }
    @Test fun tile_opens_detail_and_chart_selection_and_history_work() {
        show(ledger())
        compose.onNodeWithContentDescription("Category Learning",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Open German achievement").performScrollTo().performClick()
        compose.onNodeWithText("Achievement detail").assertExists()
        compose.onNodeWithContentDescription("Select streak 1, 2 days").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Streak #1 · 2 days",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("achievement-detail-list").performScrollToNode(hasText("Detailed history"))
        compose.onNodeWithText("Detailed history").assertIsDisplayed()
    }
    @Test fun signed_negative_gap_and_green_new_record_states_are_visible() {
        show(ledger(true))
        compose.onNodeWithContentDescription("Category Learning",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("+2 days").assertExists()
        compose.onNodeWithText("NEW RECORD").assertExists()
        compose.onNodeWithContentDescription("Progress 0%",useUnmergedTree=true).assertExists()
    }
    @Test fun negative_gap_has_explicit_sign_without_color_dependency() {
        show(ledger())
        compose.onNodeWithContentDescription("Category Learning",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("−2 days").assertExists()
    }
    @Test fun progress_only_and_hybrid_tiles_show_real_percent_and_completion() {
        val progressOnly = AchievementDefinition("course_index", "Course Index", "college", streakEnabled=false,
            progressEnabled=true, progress=AchievementProgress(64.0,64.0,"topics",completedAt=now),createdDate="2026-01-01")
        val store=ledger().copy(definitions=ledger().definitions.map { if(it.id=="german") it.copy(progress=AchievementProgress(200.0,1250.0,"words")) else it } + progressOnly)
        show(store)
        compose.onNodeWithContentDescription("Category Learning",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Progress 16%",useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("Category College",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("COMPLETE").assertExists()
        compose.onNodeWithText("Current").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open Course Index achievement").performClick()
        compose.onNodeWithText("Streak history").assertDoesNotExist()
        compose.onNodeWithText("Progress history").performScrollTo().assertIsDisplayed()
    }
    @Test fun category_swipe_updates_selected_tab_and_content() {
        show(ledger())
        compose.onNodeWithTag("Achievement cards All").performScrollToNode(hasContentDescription("Open German achievement"))
        compose.onNodeWithContentDescription("Open German achievement").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Category Discipline",useUnmergedTree=true).assertIsSelected()
        compose.onNodeWithContentDescription("Open Porn Free achievement").assertExists()
    }

    @Test fun shallow_landscape_height_still_scrolls_from_overview_to_cards() {
        compose.setContent { MaterialTheme(colorScheme=darkColorScheme()) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.height(220.dp)) {
                AchievementPages(ledger(),now,zone,{}, {})
            }
        } }
        compose.onNodeWithTag("Achievement cards All").performScrollToNode(hasContentDescription("Open German achievement"))
        compose.onNodeWithContentDescription("Open German achievement").assertIsDisplayed().performClick()
        compose.onNodeWithText("Achievement detail").assertExists()
    }

}
