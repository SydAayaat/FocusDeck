package com.hax.focusdeck

import java.time.*

/** Synthetic test-only evidence is run through the actual timer/calendar engines. */
internal object UiQaFixtures {
    val zone=ZoneId.of("Europe/Berlin")
    val date=LocalDate.of(2026,10,9)
    val now=date.atTime(9,3).atZone(zone).toInstant().toEpochMilli()
    val upcoming=Session(1,"qa-upcoming","PTfS · Chapter 2 · Single-Core","2026-10-09T00:00:00Z",
        listOf(Block("next","focus","Single-Core",7200)),schedule=PlannedSchedule("2026-10-09T10:00:00+02:00","2026-10-09T12:00:00+02:00",zone.id))
    fun reports(): List<Report> {
        val out=mutableListOf<Report>()
        fun add(day: LocalDate,hour: Int,minutes: Int,title: String,serial: Int) {
            val referencePopup=day==date && hour==6
            val actualMinutes=if(referencePopup) 89 else minutes
            val plannedMinutes=if(referencePopup) 120 else minutes+9
            val planned=day.atTime(hour,0).atZone(zone)
            val start=planned.plusMinutes(if(serial%2==0) 3 else -2).toInstant().toEpochMilli()
            val schedule=PlannedSchedule(planned.toInstant().toString(),planned.plusMinutes(plannedMinutes.toLong()).toInstant().toString(),zone.id)
            val plan=Session(1,"qa-$serial",title,"2026-01-01T00:00:00Z",listOf(Block("f","focus",title,(plannedMinutes*60).toLong(),schedule=schedule,category="projects",achievementId="ptfs"),Block("b","break","Break",600)),schedule=schedule,rewardStrategy=RewardStrategy())
            val timer=TimerEngine();timer.load(plan,true,ClockNow(start,0,1))
            timer.done(ClockNow(start+actualMinutes*60000L,actualMinutes*60000L,1));timer.rewardDone(ClockNow(start+actualMinutes*60000L,actualMinutes*60000L,1),skip=true);timer.done(ClockNow(start+(actualMinutes+10)*60000L,(actualMinutes+10)*60000L,1))
            out+=timer.report(ClockNow(start+(actualMinutes+10)*60000L,(actualMinutes+10)*60000L,1))!!.copy(recordedTimezone=zone.id)
        }
        for(m in 1..9) add(LocalDate.of(2026,m,15),10,60+m*9,"PTfS · Core Theory",m)
        for(d in 5..11) { add(LocalDate.of(2026,10,d),6,65+d*3,"PTfS · Phase 1 · Core Theory",d*10);add(LocalDate.of(2026,10,d),14,50+d*2,"Development · ProtoK",d*10+1) }
        add(date,18,65,"Review & Notes",999)
        return out
    }
    fun store(): AchievementStore {
        val base=AchievementEngine.seed(now-14*86400000L,zone)
        val topics=(1..126).map { IndexTopic("topic_$it","Topic $it") }
        val ledger=IndexLedger(document=IndexDocument(topics),states=topics.take(32).mapIndexed { i,t -> t.id to IndexState(true,if(i<20) now-14*86400000L else now) }.toMap())
        return IndexTracker.project(base.copy(definitions=base.definitions+
            AchievementDefinition("ptfs","PTfS · Programming Techniques for Supercomputers","index",progressEnabled=true),indexes=mapOf("ptfs" to ledger),
            days=mapOf(date.minusDays(1).toString() to AchievementDay(date.minusDays(1).toString(),zone.id,focusBlocks=8,focusMs=10800000,finalized=true,statuses=mapOf("focus_5" to "MET","focus_8" to "MET","focus_3h" to "MET")))))
    }
}
