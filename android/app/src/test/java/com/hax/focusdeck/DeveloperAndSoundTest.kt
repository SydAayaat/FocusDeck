package com.hax.focusdeck

import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class DeveloperAndSoundTest {
    private val now=Instant.parse("2026-10-08T01:00:00Z").toEpochMilli()
    private val zone=ZoneId.of("UTC")
    @Test fun preview_uses_actual_renderer_statistics_and_does_not_mutate_real_store() {
        val real=AchievementEngine.seed(now,zone)
        val snapshot=WireJson.encodeToString(real)
        val preview=DeveloperFixtures.samples(now,zone)
        val record=preview.definitions.first {it.title=="Record streak"}
        val stats=AchievementEngine.stats(preview,record,now,zone)
        assertEquals(12.0,stats.current,0.0); assertEquals(8.0,stats.previousBest!!,0.0)
        assertEquals(listOf(3.0,5.0,8.0),stats.completed.map {it.value}); assertTrue(stats.newRecord)
        val negative=AchievementEngine.stats(preview,preview.definitions.first {it.title=="Below best"},now,zone)
        assertEquals(-8.0,negative.gap!!,0.0)
        val completed=preview.definitions.first {it.title=="Completed index"}
        assertFalse(completed.streakEnabled);assertTrue(completed.progress!!.complete)
        assertTrue(preview.progressHistory.any {it.achievementId==completed.id})
        assertTrue(preview.commands.isEmpty()); assertTrue(preview.focusCursors.isEmpty())
        assertEquals(snapshot,WireJson.encodeToString(real))
    }
    @Test fun sandbox_limits_and_fresh_state_are_enforced() {
        assertTrue(AchievementStore().definitions.isEmpty())
        for(streak in listOf(-1,91)) assertThrows(IllegalArgumentException::class.java) {DeveloperFixtures.add(AchievementStore(),now,zone,"Card","Learning","STREAK_ONLY",streak,listOf(3),0.0,100.0)}
        assertThrows(IllegalArgumentException::class.java) {DeveloperFixtures.add(AchievementStore(),now,zone,"Card","Learning","PROGRESS_ONLY",0,emptyList(),Double.NaN,100.0)}
    }
    @Test fun old_storage_is_silent_and_new_settings_roundtrip() {
        val old=WireJson.decodeFromString<StoredDeck>("{\"deviceId\":\"p\",\"sound\":true}")
        assertFalse(old.developerMode);assertEquals("off",old.audio.timer);assertEquals("off",old.audio.stopwatch)
        assertEquals("off",old.audio.ui);assertEquals("chime",old.audio.completion);assertTrue(old.sound)
        val changed=old.copy(developerMode=true,audio=SoundPreferences(timer="wood_tick",stopwatch="digital_tick",ui="click",volume=.6f))
        assertEquals(changed,WireJson.decodeFromString<StoredDeck>(WireJson.encodeToString(changed)))
    }
    @Test fun choices_require_valid_local_custom_file_and_bounded_volume() {
        SoundPreferences().validate()
        assertThrows(IllegalArgumentException::class.java) {SoundPreferences(timer="custom").validate()}
        assertThrows(IllegalArgumentException::class.java) {SoundPreferences(volume=Float.NaN).validate()}
        assertThrows(IllegalArgumentException::class.java) {SoundPreferences(ui="wood_tick").validate()}
        SoundPreferences(timer="custom",customFiles=mapOf("timer" to "12345678-1234-1234-1234-123456789abc.audio")).validate()
        assertThrows(IllegalArgumentException::class.java) {SoundPreferences(timer="custom",customFiles=mapOf("timer" to "../../secret")).validate()}
    }
    @Test fun ticking_is_active_only_and_chooses_the_actual_block_mode() {
        val session=Session(2,"preview","Test","2026-10-08T00:00:00Z",listOf(Block("a","focus","A",60,timerMode="countdown"),Block("b","focus","B",timerMode="stopwatch")))
        val state=DeckState(session=session,phase="active")
        assertEquals("timer",SoundPolicy.tickSlot(state))
        assertEquals("stopwatch",SoundPolicy.tickSlot(state.copy(index=1)))
        for(phase in listOf("paused","ready","between","reward","complete","stopped")) assertNull(SoundPolicy.tickSlot(state.copy(phase=phase)))
        assertEquals("timer",SoundPolicy.tickSlot(state.copy(overtimeNotified=true)))
        assertNull(SoundPolicy.tickSlot(DeckState(phase="active")))
    }

}
