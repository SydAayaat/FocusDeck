package com.hax.focusdeck

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

class AchievementProgressTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-07T17:00:00Z").toEpochMilli()
    private fun seed() = AchievementEngine.seed(now,zone)
    private fun command(id: String = "set-1", achievement: String = "german", current: Double? = 200.0, target: Double? = null,
        unit: String? = null, operation: String = "progress_set", amount: Long = 1, at: Long = now) = AchievementCommand(
            achievement_version=3,operation=operation,id=id,timestamp=Instant.ofEpochMilli(at).toString(),achievement_id=achievement,
            current_value=current,target_value=target,unit=unit,amount=amount)
    private fun apply(s: AchievementStore,c: AchievementCommand) = AchievementEngine.apply(s,c,now,zone)
    private fun progress(s: AchievementStore,id: String = "german") = s.definitions.first { it.id==id }.progress!!
    private fun create(streak: Boolean,category: String="college") = AchievementCommand(achievement_version=3,operation="upsert",id="create",timestamp=Instant.ofEpochMilli(now).toString(),definition=AchievementDefinition("course","Actual course",category,
        streakEnabled=streak,progressEnabled=true,progress=AchievementProgress(18.0,64.0,"topics")))
    @Test fun german_hybrid_has_correct_percent_and_does_not_complete_a_day() {
        val s=apply(seed(),command());assertEquals(16.0,progress(s).percent!!,0.0)
        assertTrue(s.definitions.first { it.id=="german" }.streakEnabled)
        assertEquals("PENDING",AchievementEngine.status(s,s.definitions.first { it.id=="german" },AchievementEngine.date(now,zone)))
    }
    @Test fun dynamic_projects_and_college_support_both_capabilities() {
        for (category in listOf("projects","college")) for(streak in listOf(false,true)) {
            val s=apply(seed(),create(streak,category));val d=s.definitions.last()
            assertEquals(streak,d.streakEnabled);assertEquals(28.125,d.progress!!.percent!!,0.0)
            if(!streak) assertEquals("NO_TARGET",AchievementEngine.status(s,d,AchievementEngine.date(now,zone)))
        }
    }
    @Test fun custom_compute_target_is_unconfigured_until_gpt_supplies_it() {
        assertNull(progress(seed(),"custom_compute").targetValue)
        var s=apply(seed(),command(achievement="custom_compute",current=20.0,target=100.0))
        assertEquals(20.0,progress(s,"custom_compute").percent!!,0.0)
        s=apply(s,command(id="set-2",achievement="custom_compute",current=73.0,target=146.0))
        assertEquals(50.0,progress(s,"custom_compute").percent!!,0.0);assertEquals(2,s.progressHistory.size)
        assertEquals(100.0,s.progressHistory.last().previousTarget!!,0.0)
    }
    @Test fun duplicate_set_and_increment_are_idempotent() {
        val c=command();var s=apply(seed(),c);assertEquals(s,apply(s,c))
        val i=command(id="increment-1",operation="progress_increment",current=null,amount=25)
        s=apply(s,i);assertEquals(225.0,progress(s).currentValue,0.0);assertEquals(s,apply(s,i));assertEquals(2,s.progressHistory.size)
    }
    @Test fun equal_timestamp_unique_increments_apply_even_in_reverse_id_order() {
        var s=apply(seed(),command(id="zzz"));s=apply(s,command(id="b",operation="progress_increment",current=null,amount=5))
        s=apply(s,command(id="a",operation="progress_increment",current=null,amount=5));assertEquals(210.0,progress(s).currentValue,0.0)
    }
    @Test fun conflicting_receipt_fails() {
        val s=apply(seed(),command());assertThrows(IllegalArgumentException::class.java) { apply(s,command(current=201.0)) }
    }
    @Test fun completion_clamps_visual_and_preserves_actual_then_reopens_expanded_goal() {
        var s=apply(seed(),command(current=1300.0));assertEquals(100.0,progress(s).percent!!,0.0)
        assertEquals(1300.0,progress(s).currentValue,0.0);assertEquals(now,progress(s).completedAt);assertTrue(progress(s).complete)
        s=apply(s,command(id="set-2",current=null,target=1500.0));assertFalse(progress(s).complete);assertNull(progress(s).completedAt)
        assertEquals(now,s.progressHistory.first().completedAt);assertEquals(1250.0,s.progressHistory.first().targetValueAtThatTime!!,0.0)
        assertFalse(s.definitions.first { it.id=="german" }.archived)
    }
    @Test fun invalid_zero_negative_nonfinite_and_oversized_targets_are_rejected() {
        for(value in listOf(0.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY,1e13))
            assertThrows(IllegalArgumentException::class.java) { apply(seed(),command(target=value)) }
        assertNull(AchievementProgress(1.0,0.0).percent)
    }
    @Test fun negative_nonfinite_current_is_rejected() {
        for(value in listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY)) assertThrows(IllegalArgumentException::class.java) { apply(seed(),command(current=value)) }
    }
    @Test fun stale_set_keeps_latest_total_and_receipt() {
        var s=apply(seed(),command(id="new",current=200.0));s=apply(s,command(id="old",current=100.0,at=now-1000))
        assertEquals(200.0,progress(s).currentValue,0.0);assertEquals(1,s.progressHistory.size);assertTrue("old" in s.commands)
    }
    @Test fun stale_increment_is_rejected_and_semantic_noop_does_not_add_history() {
        var s=apply(seed(),command());assertThrows(IllegalArgumentException::class.java) { apply(s,command(id="old",operation="progress_increment",current=null,at=now-1000)) }
        s=apply(s,command(id="set-2"));assertEquals(1,s.progressHistory.size)
    }
    @Test fun serialization_restores_history_totals_completion_and_capabilities() {
        val s=apply(seed(),command(current=1250.0));assertEquals(s,WireJson.decodeFromString<AchievementStore>(WireJson.encodeToString(s)))
    }
    @Test fun migration_preserves_streaks_layout_archives_and_deleted_cards_and_runs_once() {
        val old=seed().copy(progressMigration=0,definitions=seed().definitions.filterNot { it.id=="custom_compute" }.map { it.copy(progressEnabled=false,progress=null,archived=it.id=="german") },deletedIds=setOf("custom_compute"),cardOrder=listOf("german"),cardSizes=mapOf("german" to "full"))
        val s=AchievementEngine.migrateProgress(old);assertTrue(s.definitions.first { it.id=="german" }.archived)
        assertEquals(old.cardSizes,s.cardSizes);assertFalse(s.definitions.any { it.id=="custom_compute" });assertEquals(s,AchievementEngine.migrateProgress(s))
    }
    @Test fun archive_restore_preserves_progress_and_delete_retires_increment_receipts() {
        val inc=command(id="inc",operation="progress_increment",current=null,amount=25)
        var s=apply(seed(),inc)
        fun management(action:String)=AchievementCommand(achievement_version=2,operation=action,id=action,timestamp=Instant.ofEpochMilli(now).toString(),achievement_id="german",confirm=action=="delete")
        s=apply(s,management("archive"));assertEquals(1,s.progressHistory.size)
        s=apply(s,management("restore"));assertEquals(25.0,progress(s).currentValue,0.0)
        s=apply(s,management("delete"));assertTrue(s.progressHistory.isEmpty());assertTrue("inc" in s.retiredCommands);assertEquals(s,apply(s,inc))
    }
    @Test fun contract_two_project_edit_retains_progress() {
        var s=apply(seed(),command(achievement="custom_compute",target=146.0))
        s=apply(s,AchievementCommand(achievement_version=2,operation="upsert",id="legacy",timestamp=Instant.ofEpochMilli(now).toString(),definition=AchievementDefinition("custom_compute","Renamed project","projects")))
        assertTrue(s.definitions.first { it.id=="custom_compute" }.progressEnabled);assertEquals(146.0,progress(s,"custom_compute").targetValue!!,0.0)
    }
    @Test fun capabilities_require_contract_three() {
        assertThrows(IllegalArgumentException::class.java) { apply(seed(),create(false).copy(achievement_version=2)) }
        assertThrows(IllegalArgumentException::class.java) { apply(seed(),command().copy(achievement_version=2)) }
    }
    @Test fun native_hello_keeps_session_and_achievement_contracts_independent() {
        val hello=deviceHello("test-device")
        assertEquals(listOf(1,2),hello.getValue("supported_session_versions").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(listOf(1,2,3,4,5,6),hello.getValue("supported_achievement_versions").jsonArray.map { it.jsonPrimitive.int })
        assertEquals(1,hello.getValue("supported_schedule_version").jsonPrimitive.int)
        assertEquals(1,hello.getValue("protocol_version").jsonPrimitive.int)
    }

}
