package com.hax.focusdeck

import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.nio.ByteBuffer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class CardAppearanceTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-07T20:00:00Z").toEpochMilli()
    private fun command(id: String = "icon-1", time: String = "2026-10-07T18:00:00Z", icon: CardIcon = CardIcon("builtin","laptop")) =
        AchievementCommand(4,"icon_set",id,time,achievement_id="german",icon=icon)
    private fun apply(s: AchievementStore, c: AchievementCommand) = AchievementEngine.apply(s,c,now,zone)
    @Test fun builtin_icon_changes_only_appearance_and_retries_are_idempotent() {
        val original = AchievementEngine.seed(now,zone)
        val edited = apply(original,command())
        assertEquals(original.definitions,edited.definitions);assertEquals(original.events,edited.events)
        assertEquals("laptop",edited.cardIcons["german"]?.icon?.name)
        assertEquals(edited,apply(edited,command()))
        assertThrows(IllegalArgumentException::class.java) { apply(edited,command(icon=CardIcon("builtin","heart"))) }
    }
    @Test fun stale_set_cannot_undo_an_ordered_default_reset() {
        var s = apply(AchievementEngine.seed(now,zone),command())
        s=apply(s,command("reset","2026-10-07T19:00:00Z",CardIcon("default")))
        s=apply(s,command("stale","2026-10-07T18:30:00Z",CardIcon("builtin","heart")))
        assertEquals("default",s.cardIcons["german"]?.icon?.kind)
        s=apply(s,command("zzz","2026-10-07T19:00:00Z",CardIcon("builtin","book-open")))
        assertEquals("book-open",s.cardIcons["german"]?.icon?.name)
    }
    @Test fun archive_restores_icon_and_delete_retires_receipts() {
        var s=apply(AchievementEngine.seed(now,zone),command())
        for (action in listOf("archive","restore")) s=apply(s,AchievementCommand(2,action,action,"2026-10-07T19:00:00Z",achievement_id="german"))
        assertEquals("laptop",s.cardIcons["german"]?.icon?.name)
        s=apply(s,AchievementCommand(2,"delete","delete","2026-10-07T19:00:00Z",achievement_id="german",confirm=true))
        assertFalse(s.cardIcons.containsKey("german"));assertEquals(s,apply(s,command()))
    }
    @Test fun legacy_storage_defaults_and_new_icon_survive_serialization() {
        val old=WireJson.decodeFromString<StoredDeck>("{\"deviceId\":\"phone\"}")
        assertEquals("gold",old.theme);assertEquals(60,old.timerRefreshRate)
        val s=apply(AchievementEngine.seed(now,zone),command())
        assertEquals(s,WireJson.decodeFromString<AchievementStore>(WireJson.encodeToString(s)))
    }
    @Test fun malformed_and_oversized_icons_and_old_contracts_are_rejected() {
        for (icon in listOf(CardIcon("builtin","missing"),CardIcon("default","laptop"),CardIcon("image",dataBase64="bad")))
            assertThrows(Exception::class.java) { CardIconPolicy.validate(icon) }
        val bytes=ByteArray(33);byteArrayOf(-119,80,78,71,13,10,26,10).copyInto(bytes)
        "IHDR".toByteArray().copyInto(bytes,12);ByteBuffer.wrap(bytes,16,4).putInt(257);ByteBuffer.wrap(bytes,20,4).putInt(1)
        assertThrows(IllegalArgumentException::class.java) { CardIconPolicy.validate(CardIcon("image",dataBase64=Base64.getEncoder().encodeToString(bytes))) }
        assertThrows(IllegalArgumentException::class.java) { apply(AchievementEngine.seed(now,zone),command().copy(achievement_version=3)) }
    }
    @Test fun refresh_is_high_for_navigation_even_during_an_active_timer() {
        assertEquals(120f,NavigationRefresh.requested(false,true,30,120f),0f)
        assertEquals(60f,NavigationRefresh.requested(false,false,120,60f),0f)
        assertEquals(30f,NavigationRefresh.requested(true,true,120,120f),0f)
        assertEquals(60f,NavigationRefresh.requested(true,false,60,120f),0f)
    }
    @Test fun monochrome_palette_is_grayscale_and_all_ids_unique() {
        val p=DeckPalettes.find("monochrome")
        for (c in listOf(p.accent,p.secondary,p.success,p.error,p.muted)) { assertEquals(c.red,c.green,0f);assertEquals(c.green,c.blue,0f) }
        assertEquals(DeckPalettes.all.size,DeckPalettes.all.map { it.id }.distinct().size)
        assertEquals("cyan",DeckPalettes.find("unknown").id)
    }
}
