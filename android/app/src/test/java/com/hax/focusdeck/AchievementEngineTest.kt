package com.hax.focusdeck

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AchievementEngineTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(day: String, hour: String = "12:00") = Instant.parse("${day}T${hour}:00Z").toEpochMilli()
    private fun seed() = AchievementEngine.seed(at("2026-01-01"),zone)
    private fun def(store: AchievementStore,id: String) = store.definitions.first { it.id == id }
    private fun stats(s: AchievementStore,id: String,day: String) = AchievementEngine.stats(s,def(s,id),at(day),zone)
    private fun event(s: AchievementStore,id: String,day: String, key: String = "$id-$day", event: String = "completed", hour: String = "12:00") =
        AchievementEngine.apply(s,AchievementCommand(operation="event",id=key,timestamp=Instant.ofEpochMilli(at(day,hour)).toString(),achievement_id=id,event=event),at(day,hour),zone)
    private fun roll(s: AchievementStore,day: String) = AchievementEngine.finalize(s,at(day),zone)
    private fun target(s: AchievementStore,day: String,count: Long,key: String,hour: String = "12:00",more: List<AchievementObjective> = emptyList()) =
        AchievementEngine.apply(s,AchievementCommand(operation="daily_target",id=key,timestamp=Instant.ofEpochMilli(at(day,hour)).toString(),date=day,
            objectives=listOf(AchievementObjective("focus_blocks",count))+more),at(day,"20:00"),zone)
    private fun blocks(s: AchievementStore,day: String,count: Long) = s.copy(days=s.days+(day to AchievementDay(day,zone.id,focusBlocks=count)))
    private fun project(s: AchievementStore,id: String="project",category: String="projects",daily: Boolean=false) = AchievementEngine.apply(s,
        AchievementCommand(operation="upsert",id="create-$id",timestamp=Instant.ofEpochMilli(at("2026-01-01")).toString(),definition=
            AchievementDefinition(id,"A real subject/project",category,dailyParticipation=daily)),at("2026-01-01"),zone)

    @Test fun initial_library_and_filters_are_data_driven() {
        val s=seed();assertEquals(10,s.definitions.size)
        assertEquals(listOf("German","Guitar"),AchievementEngine.filtered(s,"Learning").map { it.title })
        assertTrue(AchievementEngine.filtered(s,"College").isEmpty());assertFalse(s.definitions.any { it.title.contains("YouTube") })
    }
    @Test fun new_daily_achievement_has_no_fabricated_history() {
        val a=stats(seed(),"german","2026-01-01")
        assertEquals(0.0,a.current,0.0);assertNull(a.gap);assertNull(a.average);assertFalse(a.newRecord)
    }
    @Test fun active_streak_advances_on_successful_days() {
        var s=event(seed(),"german","2026-01-01");s=event(s,"german","2026-01-02")
        assertEquals(2.0,stats(s,"german","2026-01-02").current,0.0)
    }
    @Test fun pending_today_does_not_close_a_streak() {
        val s=roll(event(seed(),"german","2026-01-01"),"2026-01-02")
        assertEquals(1.0,stats(s,"german","2026-01-02").current,0.0)
        assertTrue(stats(s,"german","2026-01-02").completed.isEmpty())
    }
    private fun benchmark(): AchievementStore {
        var s=event(seed(),"german","2026-01-01");s=event(s,"german","2026-01-02")
        return roll(s,"2026-01-04") // Jan 3 closed UNMET; completed benchmark = 2.
    }
    @Test fun streak_break_keeps_best_average_and_raw_segment() {
        val s=benchmark();val a=stats(s,"german","2026-01-04")
        assertEquals(0.0,a.current,0.0);assertEquals(2.0,a.previousBest!!,0.0);assertEquals(2.0,a.average!!,0.0)
        assertEquals(1,a.completed.size);assertTrue(s.segments.any { it.achievementId=="german" })
    }
    @Test fun negative_record_gap_is_signed() {
        val a=stats(event(benchmark(),"german","2026-01-04"),"german","2026-01-04")
        assertEquals(-1.0,a.gap!!,0.0);assertEquals("−1 days",AchievementEngine.value(a.gap,false,true));assertFalse(a.newRecord)
    }
    @Test fun equal_record_gap_is_neutral() {
        var s=event(benchmark(),"german","2026-01-04");s=event(s,"german","2026-01-05")
        val a=stats(s,"german","2026-01-05");assertEquals(0.0,a.gap!!,0.0);assertFalse(a.newRecord)
    }
    @Test fun positive_record_gap_uses_previous_best_and_current_is_excluded_from_average() {
        var s=event(benchmark(),"german","2026-01-04");s=event(s,"german","2026-01-05");s=event(s,"german","2026-01-06")
        val a=stats(s,"german","2026-01-06");assertEquals(1.0,a.gap!!,0.0);assertEquals(3.0,a.best!!,0.0)
        assertEquals(2.0,a.average!!,0.0);assertTrue(a.newRecord);assertEquals("+1 days",AchievementEngine.value(a.gap,false,true))
    }
    @Test fun abstinence_requires_explicit_initialization() { assertFalse(stats(seed(),"porn_free","2026-01-01").initialized) }
    @Test fun timestamp_streak_preserves_exact_duration_and_starts_at_break() {
        var s=event(seed(),"porn_free","2026-01-01",event="initialize",hour="10:00")
        s=event(s,"porn_free","2026-01-03",event="streak_break",hour="11:42")
        val a=stats(s,"porn_free","2026-01-03")
        assertEquals(178920000L,a.completed.single().endMs!!-a.completed.single().startMs!!)
        assertEquals("2d 1h 42m",AchievementEngine.value(a.previousBest,true))
        assertEquals("0h 18m",AchievementEngine.value(a.current,true))
    }
    @Test fun abstinence_breaks_must_be_chronological() {
        val s=event(seed(),"porn_free","2026-01-01",event="initialize")
        assertThrows(IllegalArgumentException::class.java) { event(s,"porn_free","2026-01-01",key="second",event="streak_break") }
    }
    @Test fun events_are_idempotent_and_conflicting_replay_rejected() {
        val s=event(seed(),"german","2026-01-01");assertEquals(s,event(s,"german","2026-01-01"))
        assertThrows(IllegalArgumentException::class.java) { event(s,"gym","2026-01-01",key="german-2026-01-01") }
    }
    @Test fun target_creation_and_midnight_met() {
        var s=target(blocks(seed(),"2026-01-01",4),"2026-01-01",3,"target")
        assertEquals("MET",AchievementEngine.status(s,def(s,"daily_objectives"),"2026-01-01"))
        s=roll(s,"2026-01-02");assertEquals("MET",s.days.getValue("2026-01-01").statuses["daily_objectives"])
        assertEquals("target",s.days.getValue("2026-01-01").targetId)
    }
    @Test fun easier_then_harder_replacement_immediately_recomputes_and_keeps_activity() {
        var s=event(blocks(seed(),"2026-01-01",4),"german","2026-01-01")
        val german=listOf(AchievementObjective("achievement",achievementId="german"))
        s=target(s,"2026-01-01",8,"a","10:00",german)
        assertEquals("PENDING",AchievementEngine.status(s,def(s,"daily_objectives"),"2026-01-01"))
        s=target(s,"2026-01-01",3,"b","15:00",german)
        assertEquals("MET",AchievementEngine.status(s,def(s,"daily_objectives"),"2026-01-01"))
        s=target(s,"2026-01-01",8,"c","18:00",german)
        assertEquals("PENDING",AchievementEngine.status(s,def(s,"daily_objectives"),"2026-01-01"))
        assertEquals(4L,s.days.getValue("2026-01-01").focusBlocks);assertEquals(1L,s.days.getValue("2026-01-01").activities["german"])
        assertEquals(3,s.targets.size)
    }
    @Test fun latest_write_wins_by_timestamp_not_receipt_order() {
        var s=target(seed(),"2026-01-01",3,"new","18:00");s=target(s,"2026-01-01",8,"old","10:00")
        assertEquals("new",AchievementEngine.latest(s,"2026-01-01")!!.id)
    }
    @Test fun same_timestamp_uses_deterministic_id_tiebreak() {
        var s=target(seed(),"2026-01-01",3,"z");s=target(s,"2026-01-01",8,"a")
        assertEquals("z",AchievementEngine.latest(s,"2026-01-01")!!.id)
    }
    @Test fun midnight_unmet_is_permanent() {
        val s=roll(target(seed(),"2026-01-01",3,"target"),"2026-01-02")
        assertEquals("UNMET",s.days.getValue("2026-01-01").statuses["daily_objectives"])
        assertThrows(IllegalArgumentException::class.java) { target(s,"2026-01-01",1,"late") }
    }
    @Test fun no_target_days_are_finalized_and_neutral() {
        var s=target(blocks(seed(),"2026-01-01",4),"2026-01-01",3,"target");s=roll(s,"2026-01-03")
        assertEquals("NO_TARGET",s.days.getValue("2026-01-02").statuses["daily_objectives"])
        assertEquals(1.0,stats(s,"daily_objectives","2026-01-03").current,0.0)
    }
    @Test fun unknown_and_duplicate_target_objectives_are_rejected() {
        assertThrows(IllegalArgumentException::class.java) { target(seed(),"2026-01-01",3,"a",more=listOf(AchievementObjective("achievement",achievementId="unknown"))) }
        assertThrows(IllegalArgumentException::class.java) { target(seed(),"2026-01-01",3,"b",more=listOf(AchievementObjective("focus_blocks",2))) }
    }
    @Test fun dynamic_project_creation_completion_and_filtering() {
        val s=event(project(seed()),"project","2026-01-01",event="objective_completed")
        assertEquals(1.0,stats(s,"project","2026-01-01").current,0.0)
        assertTrue(AchievementEngine.filtered(s,"Projects").any { it.id=="project" })
    }
    @Test fun project_unscheduled_days_do_not_break_streak() {
        var s=event(project(seed()),"project","2026-01-01");s=roll(s,"2026-01-05");s=event(s,"project","2026-01-05")
        assertEquals(2.0,stats(s,"project","2026-01-05").current,0.0)
    }
    @Test fun scheduled_unmet_project_day_breaks_streak() {
        var s=event(project(seed()),"project","2026-01-01")
        s=target(s,"2026-01-02",1,"target",more=listOf(AchievementObjective("achievement",achievementId="project")))
        s=roll(s,"2026-01-03");assertEquals(0.0,stats(s,"project","2026-01-03").current,0.0)
        assertEquals(1,stats(s,"project","2026-01-03").completed.size)
    }
    @Test fun explicit_daily_participation_breaks_on_missing_day() {
        val s=roll(event(project(seed(),daily=true),"project","2026-01-01"),"2026-01-03")
        assertEquals(0.0,stats(s,"project","2026-01-03").current,0.0)
    }
    @Test fun college_creation_and_neutral_unscheduled_days() {
        var s=event(project(seed(),"subject","college"),"subject","2026-01-01",event="objective_completed")
        s=roll(s,"2026-01-04");assertEquals(1.0,stats(s,"subject","2026-01-04").current,0.0)
        assertEquals("subject",AchievementEngine.filtered(s,"College").single().id)
    }
    @Test fun archived_definition_keeps_events_segments_and_is_not_visible() {
        var s=event(project(seed(),daily=true),"project","2026-01-01");s=roll(s,"2026-01-03")
        s=AchievementEngine.apply(s,AchievementCommand(operation="archive",id="archive",timestamp=Instant.ofEpochMilli(at("2026-01-03")).toString(),achievement_id="project"),at("2026-01-03"),zone)
        assertFalse(AchievementEngine.filtered(s,"All").any { it.id=="project" });assertTrue(s.events.isNotEmpty());assertTrue(s.segments.any { it.achievementId=="project" })
    }
    private fun plan() = Session(2,"11111111-1111-4111-8111-111111111111","Study","2026-01-01T00:00:00Z",
        (1..8).map { Block("b$it","focus","Block $it",timerMode="stopwatch") })
    @Test fun automatic_five_eight_and_three_hours_work_without_gpt_and_replay_is_neutral() {
        val start=at("2026-01-01");val timer=TimerEngine();timer.load(plan(),true,ClockNow(start,0,1));var s=seed()
        for (i in 1..8) {
            val now=ClockNow(start+i*1_800_000L,i*1_800_000L,1);timer.done(now)
            s=AchievementEngine.capture(s,timer.state,now,zone)
            if(i==5) assertEquals("MET",AchievementEngine.status(s,def(s,"focus_5"),"2026-01-01"))
            timer.rewardDone(now, skip = true) // Preserve the existing result/reward-then-advance workflow.
        }
        assertEquals("MET",AchievementEngine.status(s,def(s,"focus_8"),"2026-01-01"));assertEquals("MET",AchievementEngine.status(s,def(s,"focus_3h"),"2026-01-01"))
        assertEquals(8L,s.days.getValue("2026-01-01").focusBlocks);assertEquals(14_400_000,s.days.getValue("2026-01-01").focusMs)
        assertEquals(s,AchievementEngine.capture(s,timer.state,ClockNow(start+14_400_000,14_400_000,1),zone))
    }
    @Test fun active_threshold_updates_before_done_and_pause_is_excluded() {
        val start=at("2026-01-01");val e=TimerEngine();e.load(plan(),true,ClockNow(start,0,1))
        var s=AchievementEngine.capture(seed(),e.state,ClockNow(start+10_800_000,10_800_000,1),zone)
        assertEquals("MET",AchievementEngine.status(s,def(s,"focus_3h"),"2026-01-01"));assertEquals(0L,s.days.getValue("2026-01-01").focusBlocks)
        e.pause(ClockNow(start+10_800_000,10_800_000,1));s=AchievementEngine.capture(s,e.state,ClockNow(start+14_400_000,14_400_000,1),zone)
        assertEquals(10_800_000,s.days.getValue("2026-01-01").focusMs)
    }
    @Test fun midnight_splits_active_duration_and_done_counts_on_completion_day() {
        val start=Instant.parse("2026-01-01T22:30:00Z").toEpochMilli() // 23:30 local
        val e=TimerEngine();e.load(plan(),true,ClockNow(start,0,1))
        e.done(ClockNow(start+3_600_000,3_600_000,1))
        val s=AchievementEngine.capture(seed(),e.state,ClockNow(start+3_600_000,3_600_000,1),zone)
        assertEquals(1_800_000,s.days.getValue("2026-01-01").focusMs);assertEquals(1_800_000,s.days.getValue("2026-01-02").focusMs)
        assertEquals(0L,s.days.getValue("2026-01-01").focusBlocks);assertEquals(1L,s.days.getValue("2026-01-02").focusBlocks)
    }
    @Test fun reboot_gap_is_not_counted() {
        val start=at("2026-01-01");val e=TimerEngine();e.load(plan(),true,ClockNow(start,0,1));e.checkpoint(ClockNow(start+60000,60000,1))
        var s=AchievementEngine.capture(seed(),e.state,ClockNow(start+60000,60000,1),zone)
        e.tick(ClockNow(start+600000,100,2));s=AchievementEngine.capture(s,e.state,ClockNow(start+600000,100,2),zone)
        assertEquals(60000L,s.days.getValue("2026-01-01").focusMs)
    }
    @Test fun persisted_store_recovers_all_definitions_days_targets_events_and_record_acknowledgements() {
        var s=target(event(seed(),"german","2026-01-01"),"2026-01-01",3,"a");s=roll(s,"2026-01-03").copy(seenRecords=setOf("german:2026-01-01"))
        assertEquals(s,WireJson.decodeFromString<AchievementStore>(WireJson.encodeToString(s)))
    }
    @Test fun timezone_and_dst_boundaries_use_local_calendar_days() {
        assertEquals("2026-01-02",AchievementEngine.date(Instant.parse("2026-01-01T23:30:00Z").toEpochMilli(),zone))
        val before=Instant.parse("2026-03-28T23:00:00Z").toEpochMilli();val after=Instant.parse("2026-03-29T22:00:00Z").toEpochMilli()
        assertEquals(23*3_600_000L,after-before);assertEquals("2026-03-29",AchievementEngine.date(before,zone));assertEquals("2026-03-30",AchievementEngine.date(after,zone))
    }
    @Test fun snapshot_chunking_preserves_unicode_at_boundaries() {
        val raw="x".repeat(49999)+"🎸"+"y".repeat(50010)
        val chunks=AchievementEngine.snapshotChunks(raw)
        assertEquals(raw,chunks.joinToString(""));assertTrue(chunks.all { it.length<=50000 && !it.last().isHighSurrogate() })
    }
    @Test fun mixed_timers_count_focus_only_and_stopped_focus_only_contributes_time() {
        val session=Session(2,"11111111-1111-4111-8111-111111111111","Study","2026-01-01T00:00:00Z",listOf(
            Block("count","focus","Timed",60,timerMode="countdown"),Block("break","break","Break",60),Block("last","focus","Last",timerMode="stopwatch")))
        val start=at("2026-01-01");val e=TimerEngine();e.load(session,true,ClockNow(start,0,1));var s=seed()
        e.done(ClockNow(start+60000,60000,1));s=AchievementEngine.capture(s,e.state,ClockNow(start+60000,60000,1),zone)
        e.rewardDone(ClockNow(start+60000,60000,1),skip=true)
        e.done(ClockNow(start+120000,120000,1));s=AchievementEngine.capture(s,e.state,ClockNow(start+120000,120000,1),zone)
        e.stop(ClockNow(start+180000,180000,1));s=AchievementEngine.capture(s,e.state,ClockNow(start+180000,180000,1),zone)
        assertEquals(1L,s.days.getValue("2026-01-01").focusBlocks);assertEquals(120000L,s.days.getValue("2026-01-01").focusMs)
    }
    @Test fun focus_duration_splits_at_a_dst_day_boundary_without_assuming_twenty_four_hours() {
        val start=Instant.parse("2026-03-28T23:00:00Z").toEpochMilli();val e=TimerEngine();e.load(plan(),true,ClockNow(start,0,1))
        val end=ClockNow(start+24*3600000L,24*3600000L,1)
        val s=AchievementEngine.capture(AchievementEngine.seed(start,zone),e.state,end,zone)
        assertEquals(23*3600000L,s.days.getValue("2026-03-29").focusMs)
        assertEquals(3600000L,s.days.getValue("2026-03-30").focusMs)
    }
    @Test fun contract_two_creates_manual_cards_in_every_category_and_old_contract_stays_restricted() {
        for (category in AchievementEngine.categories.drop(1).map { it.lowercase() }.filter { it != "index" }) {
            val c=AchievementCommand(achievement_version=2,operation="upsert",id="add-$category",timestamp=Instant.ofEpochMilli(at("2026-01-01")).toString(),
                definition=AchievementDefinition("custom_$category","User card",category,kind="daily"))
            val s=AchievementEngine.apply(seed(),c,at("2026-01-01"),zone)
            assertTrue(AchievementEngine.filtered(s,category).any { it.id=="custom_$category" })
            assertThrows(IllegalArgumentException::class.java) { AchievementEngine.apply(seed(),c.copy(achievement_version=1),at("2026-01-01"),zone) }
        }
        val index=AchievementCommand(achievement_version=2,operation="upsert",id="manual-index",timestamp=Instant.ofEpochMilli(at("2026-01-01")).toString(),
            definition=AchievementDefinition("custom_index","Invalid manual Index","index",kind="daily"))
        assertThrows(IllegalArgumentException::class.java) { AchievementEngine.apply(seed(),index,at("2026-01-01"),zone) }
    }
    private fun manage(s: AchievementStore, id: String, operation: String, key: String=operation, confirm: Boolean=false) = AchievementEngine.apply(s,
        AchievementCommand(achievement_version=2,operation=operation,id=key,timestamp=Instant.ofEpochMilli(at("2026-01-04")).toString(),achievement_id=id,confirm=confirm),at("2026-01-04"),zone)
    @Test fun builtin_archive_and_restore_keep_history() {
        val before=benchmark();val hidden=manage(before,"german","archive")
        assertFalse(AchievementEngine.filtered(hidden,"All").any { it.id=="german" });assertEquals(before.events,hidden.events)
        val restored=manage(hidden,"german","restore")
        assertTrue(AchievementEngine.filtered(restored,"Learning").any { it.id=="german" });assertEquals(before.segments,restored.segments)
    }
    @Test fun permanent_deletion_removes_card_evidence_but_replay_cannot_resurrect_it() {
        val before=event(project(seed()),"project","2026-01-01")
        val deleted=manage(before,"project","delete",confirm=true)
        assertTrue(deleted.definitions.none { it.id=="project" });assertTrue(deleted.events.none { it.achievementId=="project" })
        assertTrue(deleted.days.values.all { "project" !in it.activities && "project" !in it.statuses })
        assertTrue("project" in deleted.deletedIds);assertTrue("create-project" in deleted.retiredCommands)
        assertEquals(deleted,AchievementEngine.apply(deleted,before.commands.getValue("create-project"),at("2026-01-04"),zone))
    }
    @Test fun deleted_id_cannot_be_reused_and_delete_requires_confirmation() {
        assertThrows(IllegalArgumentException::class.java) { manage(seed(),"german","delete") }
        val deleted=manage(project(seed()),"project","delete",confirm=true)
        val create=AchievementCommand(achievement_version=2,operation="upsert",id="fresh",timestamp=Instant.ofEpochMilli(at("2026-01-04")).toString(),definition=AchievementDefinition("project","Project","projects"))
        assertThrows(IllegalArgumentException::class.java) { AchievementEngine.apply(deleted,create,at("2026-01-04"),zone) }
    }
    @Test fun card_referenced_by_current_target_cannot_be_removed_until_target_is_replaced() {
        var s=target(seed(),"2026-01-04",1,"target",more=listOf(AchievementObjective("achievement",achievementId="german")))
        assertThrows(IllegalArgumentException::class.java) { manage(s,"german","archive") }
        assertThrows(IllegalArgumentException::class.java) { manage(s,"german","delete",confirm=true) }
        s=target(s,"2026-01-04",1,"replacement","18:00")
        assertTrue(manage(s,"german","archive").definitions.first { it.id=="german" }.archived)
    }
    @Test fun card_order_resize_and_tombstones_survive_storage_recovery() {
        var s=AchievementEngine.moveCard(seed(),"guitar","gym")
        val ids=AchievementEngine.filtered(s,"All").map { it.id }
        assertTrue(ids.indexOf("guitar")<ids.indexOf("gym"))
        s=AchievementEngine.resizeCard(s,"guitar","full")
        val recovered=WireJson.decodeFromString<AchievementStore>(WireJson.encodeToString(s))
        assertEquals(s,recovered);assertEquals("full",recovered.cardSizes["guitar"])
        val moved=AchievementEngine.moveCard(recovered,"guitar","gym")
        assertTrue(AchievementEngine.filtered(moved,"All").indexOfFirst { it.id=="guitar" }>AchievementEngine.filtered(moved,"All").indexOfFirst { it.id=="gym" })
    }
}
