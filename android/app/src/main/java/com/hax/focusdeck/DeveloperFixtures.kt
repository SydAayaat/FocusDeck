package com.hax.focusdeck

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Only receives/returns the preview ledger; never touches a repository or real command receipts. */
internal object DeveloperFixtures {
    fun add(store: AchievementStore, now: Long, zone: ZoneId, title: String, category: String,
        capability: String, currentStreak: Int, completed: List<Int>, current: Double, total: Double): AchievementStore {
        require(store.definitions.size<50) { "Preview limit: 50 cards" }
        require(title.isNotBlank() && title.length<=80) { "Title must be 1–80 characters" }
        require(category in AchievementEngine.categories.drop(1))
        require(capability in setOf("STREAK_ONLY","PROGRESS_ONLY","STREAK_AND_PROGRESS"))
        require(currentStreak in 0..90 && completed.size<=20 && completed.all { it in 1..90 }) { "Use streaks of 0–90 days and at most 20 completed streaks" }
        require(current.isFinite() && current in 0.0..1e6 && total.isFinite() && total>0 && total<=1e6) { "Use a nonnegative progress value and a positive total (up to 1,000,000)" }
        val id="preview_${UUID.randomUUID()}"
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val length=completed.sum()+completed.size+currentStreak
        var date=today.minusDays(length.toLong())
        val began=date.toString()
        val days=store.days.toMutableMap()
        fun day(met:Boolean) {
            val key=date.toString()
            val previous=days[key] ?: AchievementDay(key,zone.id,finalized=true)
            days[key]=previous.copy(statuses=previous.statuses+(id to if(met) "MET" else "UNMET"))
            date=date.plusDays(1)
        }
        completed.forEach { streak -> repeat(streak) {day(true)};day(false) }
        repeat(currentStreak) {day(true)}
        val streakEnabled=capability!="PROGRESS_ONLY"
        val progressEnabled=capability!="STREAK_ONLY"
        val progress=if(progressEnabled) AchievementProgress(current,total,"topics",completedAt=now.takeIf {current>=total},updatedAt=now,updateId="preview") else null
        val definition=AchievementDefinition(id,title,category.lowercase(),"daily","Developer preview · synthetic history",createdDate=began,
            streakEnabled=streakEnabled,progressEnabled=progressEnabled,progress=progress)
        val history=if(!progressEnabled) emptyList() else listOf(
            ProgressHistory("${id}_first",id,now-86_400_000,0.0,current*.4,total,null,current*.4,"topics","Developer preview"),
            ProgressHistory("${id}_second",id,now,current*.4,current,total,total,current*.6,"topics","Developer preview",progress?.completedAt))
        return store.copy(beganDate=if(store.beganDate.isBlank() || began<store.beganDate) began else store.beganDate,
            definitions=store.definitions+definition,days=days,progressHistory=store.progressHistory+history,
            cardSizes=store.cardSizes+(id to if(progressEnabled) "wide" else "compact"),progressMigration=1)
    }
    fun samples(now:Long,zone:ZoneId):AchievementStore {
        var s=AchievementStore()
        for((title,capability,current,history,value,total) in listOf(
            Sample("Record streak","STREAK_ONLY",12,listOf(3,5,8),0.0,100.0),
            Sample("Below best","STREAK_ONLY",3,listOf(7,11,4),0.0,100.0),
            Sample("Hybrid learning","STREAK_AND_PROGRESS",9,listOf(4,6),625.0,1250.0),
            Sample("Completed index","PROGRESS_ONLY",0,emptyList(),64.0,64.0),
            Sample("Fresh start","STREAK_AND_PROGRESS",0,emptyList(),0.0,100.0))) {
            s=add(s,now,zone,title,"Learning",capability,current,history,value,total)
        }
        return s
    }
    private data class Sample(val title:String,val capability:String,val current:Int,val history:List<Int>,val value:Double,val total:Double)
}
