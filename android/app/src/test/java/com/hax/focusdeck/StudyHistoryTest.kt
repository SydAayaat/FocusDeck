package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StudyHistoryTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun record(id: String, finished: String, active: Long = 60_000): Report {
        val finish = Instant.parse(finished).toEpochMilli()
        val block = BlockResult("b1", "focus", "Task", 30_000, 30_000, active, 0, finish - active, finish, "done")
        return Report(id, "Study", "complete", 30_000, active, active, 0, 0, active, finish - active, finish, listOf(block))
    }

    @Test fun calendarWeeksStartOnMondayAndCrossYear() {
        val window = StudyHistory.window(HistoryPeriod.WEEK, LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), window.start)
        assertEquals(LocalDate.of(2027, 1, 4), window.end)
    }

    @Test fun monthNavigationHandlesLeapDayWithoutDrift() {
        val feb = StudyHistory.window(HistoryPeriod.MONTH, LocalDate.of(2028, 2, 29))
        assertEquals(LocalDate.of(2028, 3, 1), feb.end)
        assertEquals(LocalDate.of(2028, 3, 1), StudyHistory.shift(HistoryPeriod.MONTH, LocalDate.of(2028, 2, 29), 1))
    }

    @Test fun localFinishDateAndExclusiveEndDefinePeriod() {
        val beforeMidnight = record("a", "2026-10-06T21:59:59Z")
        val midnight = record("b", "2026-10-06T22:00:00Z")
        val day = StudyHistory.summarize(listOf(beforeMidnight, midnight), HistoryPeriod.DAY, LocalDate.of(2026, 10, 6), berlin)
        assertEquals(listOf(beforeMidnight), day.reports)
        assertEquals(60_000L, day.focusedMs)
        assertEquals(60_000L, day.buckets[23].focusedMs)
    }

    @Test fun daylightSavingDayUsesLocalMidnights() {
        val record = record("dst", "2026-10-25T22:59:59Z")
        val after = record("next", "2026-10-25T23:00:00Z")
        val day = StudyHistory.summarize(listOf(record, after), HistoryPeriod.DAY, LocalDate.of(2026, 10, 25), berlin)
        assertEquals(1, day.reports.size)
        assertEquals(60_000L, day.buckets.sumOf { it.focusedMs })
    }

    @Test fun duplicateReportsDoNotInflateLifetimeAndAttemptsRemainSeparate() {
        val first = record("same-id", "2026-10-06T10:00:00Z")
        val second = record("same-id", "2026-10-06T11:00:00Z")
        val all = StudyHistory.summarize(listOf(first, first, second), HistoryPeriod.ALL, LocalDate.of(2026, 10, 6), berlin)
        assertEquals(2, all.reports.size)
        assertEquals(120_000L, all.focusedMs)
        assertEquals(60_000L, all.overtimeMs)
    }

    @Test fun yearsOfHistoryAreNotLimitedTo100Sessions() {
        val records = (0..150).map { record("session-$it", "2026-10-06T10:00:00Z") }
        val all = StudyHistory.summarize(records, HistoryPeriod.ALL, LocalDate.of(2026, 10, 6), berlin)
        assertEquals(151, all.reports.size)
        assertEquals(151 * 60_000L, all.focusedMs)
    }

    @Test fun csvPreservesQuotesNewlinesAndMillisecondsAndProtectsFormulaText() {
        val original = record("safe", "2026-10-06T10:00:00Z", 61_234)
        val r = original.copy(title = "=SUM(1,2)", blocks = original.blocks.map { it.copy(title = "A, \"B\"\nC") })
        val csv = HistoryCsv.encode(listOf(r, r))
        assertTrue(csv.contains("\"'=SUM(1,2)\""))
        assertTrue(csv.contains("\"A, \"\"B\"\"\nC\""))
        assertTrue(csv.contains("\"61234\""))
        assertEquals(2, csv.split("\r\n").filter { it.isNotEmpty() }.size)
        r.validateHistory()
    }

    @Test fun importedTotalsMustMatchCategoriesAndBlocks() {
        val valid = record("check", "2026-10-06T10:00:00Z")
        valid.validateHistory()
        assertThrows(IllegalArgumentException::class.java) { valid.copy(focusedMs = 0, breakMs = valid.activeMs).validateHistory() }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(overtimeMs = 1).validateHistory() }
    }
}
