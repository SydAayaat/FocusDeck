package com.hax.focusdeck

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

@Serializable
data class AchievementDefinition(val id: String, val title: String, val category: String,
    val kind: String = "scheduled", val description: String = "", @SerialName("daily_participation") val dailyParticipation: Boolean = false,
    val archived: Boolean = false, @SerialName("created_date") val createdDate: String = "", val metadata: Map<String, String> = emptyMap(),
    @SerialName("streak_enabled") val streakEnabled: Boolean = true,
    @SerialName("progress_enabled") val progressEnabled: Boolean = false,
    val progress: AchievementProgress? = null)
@Serializable
data class AchievementProgress(@SerialName("current_value") val currentValue: Double = 0.0,
    @SerialName("target_value") val targetValue: Double? = null, val unit: String = "units",
    @SerialName("completed_at") val completedAt: Long? = null,
    val updatedAt: Long = 0, val updateId: String = "") {
    val percent: Double? get() = targetValue?.takeIf { it > 0 && it.isFinite() }?.let { (currentValue / it * 100).coerceIn(0.0, 100.0) }
    val complete: Boolean get() = targetValue?.let { it > 0 && currentValue >= it } == true
}
@Serializable
data class ProgressHistory(val id: String, val achievementId: String, val timestamp: Long,
    val previousValue: Double, val newValue: Double, val targetValueAtThatTime: Double?,
    val previousTarget: Double?, val delta: Double, val unit: String, val source: String,
    val completedAt: Long? = null)
@Serializable
data class AchievementObjective(val kind: String, val minimum: Long = 1, @SerialName("achievement_id") val achievementId: String? = null,
    val text: String = "")
@Serializable
data class AchievementCommand(val achievement_version: Int = 1, val operation: String,
    val id: String, val timestamp: String, val source: String = "GPT", val definition: AchievementDefinition? = null,
    val achievement_id: String? = null, val event: String? = null, val date: String? = null,
    val objectives: List<AchievementObjective>? = null, val amount: Long = 1, val text: String = "", val confirm: Boolean = false,
    val current_value: Double? = null, val target_value: Double? = null, val unit: String? = null, val icon: CardIcon? = null, val document: IndexDocument? = null, val configuration: String? = null,
    val expected_index_revision: Long? = null, val expected_store_revision: Long? = null,
    val topic_id: String? = null, val completed: Boolean? = null, val note: String? = null, val evidence: String? = null, val order: List<String>? = null)
@Serializable
data class AchievementEvent(val id: String, val achievementId: String, val event: String,
    val atMs: Long, val date: String, val source: String, val amount: Long = 1, val text: String = "")
@Serializable
data class DailyTarget(val id: String, val date: String, val atMs: Long, val source: String,
    val objectives: List<AchievementObjective>)
@Serializable
data class AchievementDay(val date: String, val zone: String, val focusBlocks: Long = 0,
    val focusMs: Long = 0, val activities: Map<String, Long> = emptyMap(),
    val finalized: Boolean = false, val statuses: Map<String, String> = emptyMap(), val targetId: String? = null)
@Serializable
data class AchievementSegment(val achievementId: String, val start: String, val end: String,
    val value: Double, val personalRecord: Boolean, val startMs: Long? = null, val endMs: Long? = null)
@Serializable
data class FocusCursor(val activeMs: Long = 0, val atMs: Long, val done: Boolean = false)
@Serializable
data class AchievementStore(val achievement_version: Int = 1, val beganDate: String = "",
    val definitions: List<AchievementDefinition> = emptyList(), val events: List<AchievementEvent> = emptyList(),
    val targets: List<DailyTarget> = emptyList(), val days: Map<String, AchievementDay> = emptyMap(),
    val segments: List<AchievementSegment> = emptyList(), val focusCursors: Map<String, FocusCursor> = emptyMap(),
    val commands: Map<String, AchievementCommand> = emptyMap(), val seenRecords: Set<String> = emptySet(),
    val revision: Long = 0, val cardOrder: List<String> = emptyList(), val cardSizes: Map<String, String> = emptyMap(),
    val deletedIds: Set<String> = emptySet(), val retiredCommands: Set<String> = emptySet(),
    val progressHistory: List<ProgressHistory> = emptyList(), val progressMigration: Int = 0,
    val cardIcons: Map<String, CardIconAssignment> = emptyMap(), val indexes: Map<String, IndexLedger> = emptyMap())
data class AchievementStats(val definition: AchievementDefinition, val current: Double,
    val previousBest: Double?, val best: Double?, val average: Double?, val completed: List<AchievementSegment>,
    val currentStart: String?, val initialized: Boolean = true) {
    val gap: Double? get() = previousBest?.let { current - it }
    val newRecord: Boolean get() = gap?.let { it > 0 } == true
    val recordKey: String get() = "${definition.id}:$currentStart"
}
data class ObjectiveProgress(val objective: AchievementObjective, val actual: Long) {
    val met: Boolean get() = actual >= objective.minimum
}

/** Pure achievement ledger. Timer measurements enter here; this engine never controls a timer. */
object AchievementEngine {
    val builtInIds = setOf("porn_free", "daily_objectives", "gym", "cardio", "german", "guitar", "focus_5", "focus_8", "focus_3h")
    val categories = listOf("All", "Discipline", "Health", "Learning", "Focus", "Projects", "College", "Index")
    fun date(atMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().toString()
    fun seed(now: Long, zone: ZoneId): AchievementStore {
        val day = date(now, zone)
        fun d(id: String, title: String, cat: String, kind: String, desc: String) =
            AchievementDefinition(id, title, cat, kind, desc, createdDate = day)
        return AchievementStore(beganDate = day, progressMigration = 1, definitions = listOf(
            d("porn_free", "Porn Free", "discipline", "abstinence", "Time since supplied starting timestamp"),
            d("daily_objectives", "Daily Objectives Complete", "discipline", "contract", "Final daily GPT contract"),
            d("gym", "Gym", "health", "daily", "Completed training days"),
            d("cardio", "Cardio", "health", "daily", "Completed cardio days"),
            d("german", "German", "learning", "daily", "Build vocabulary and maintain daily consistency").copy(
                progressEnabled = true, progress = AchievementProgress(targetValue = 1250.0, unit = "words"), metadata = mapOf("progress_label" to "VOCABULARY PROGRESS")),
            d("guitar", "Guitar", "learning", "daily", "Completed practice days"),
            d("focus_5", "5+ Focus Blocks", "focus", "focus_blocks", "Five DONE focus blocks per day"),
            d("focus_8", "8+ Focus Blocks", "focus", "focus_blocks", "Eight DONE focus blocks per day"),
            d("focus_3h", "3+ Hours Active Focus", "focus", "focus_time", "Three hours of measured active focus"),
            d("custom_compute", "Custom Compute", "projects", "scheduled", "Progress through your master index while maintaining project consistency").copy(
                progressEnabled = true, progress = AchievementProgress(unit = "index topics"))))
    }
    /** Add defaults once without recreating archived/deleted cards or modifying existing measurements. */
    fun migrateProgress(store: AchievementStore): AchievementStore {
        if (store.progressMigration >= 1) return store
        return store.copy(progressMigration = 1, revision = store.revision + 1, definitions = store.definitions.map {
            if (it.progressEnabled || it.progress != null) it
            else when (it.id) {
                "german" -> it.copy(progressEnabled = true, progress = AchievementProgress(targetValue = 1250.0, unit = "words"), metadata = it.metadata + ("progress_label" to "VOCABULARY PROGRESS"))
                "custom_compute" -> it.copy(progressEnabled = true, progress = AchievementProgress(unit = "index topics"))
                else -> it
            }
        })
    }
    private fun validateProgress(p: AchievementProgress) {
        require(p.currentValue.isFinite() && p.currentValue in 0.0..1e12) { "current_value must be finite and 0..1e12" }
        require(p.targetValue == null || p.targetValue.isFinite() && p.targetValue > 0 && p.targetValue <= 1e12) { "target_value must be finite and > 0..1e12" }
        require(p.unit.isNotBlank() && p.unit.length <= 100) { "unit must be 1..100 characters" }
    }
    private fun setProgress(store: AchievementStore, id: String, requested: AchievementProgress, command: AchievementCommand, at: Long): AchievementStore {
        validateProgress(requested)
        val def = store.definitions.first { it.id == id }
        val old = def.progress ?: AchievementProgress()
        // Authoritative SETs are ordered by timestamp/id; stale delivery cannot roll back a newer total.
        if (at < old.updatedAt || at == old.updatedAt && command.id <= old.updateId && command.operation != "progress_increment") return store
        val changed = old.currentValue != requested.currentValue || old.targetValue != requested.targetValue || old.unit != requested.unit
        val completion = if (requested.complete) {
            if (old.complete && old.targetValue == requested.targetValue) old.completedAt ?: at else at
        } else null
        val next = requested.copy(completedAt = completion, updatedAt = at, updateId = command.id)
        return store.copy(definitions = store.definitions.map { if (it.id == id) it.copy(progressEnabled = true, progress = next) else it },
            progressHistory = if (!changed) store.progressHistory else store.progressHistory + ProgressHistory(
                command.id, id, at, old.currentValue, next.currentValue, next.targetValue, old.targetValue,
                next.currentValue - old.currentValue, next.unit, command.source, completion))
    }
    fun progressValue(value: Double): String = if (value % 1 == 0.0) value.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, value)
    fun filtered(store: AchievementStore, category: String): List<AchievementDefinition> = store.definitions.filter {
        !it.archived && (category == "All" || it.category.equals(category, ignoreCase = true)) }
        .sortedBy { (store.cardOrder + store.definitions.map { d -> d.id }).distinct().indexOf(it.id) }

    fun moveCard(store: AchievementStore, id: String, before: String): AchievementStore {
        require(store.definitions.any { it.id == id } && store.definitions.any { it.id == before }) { "Unknown card" }
        val order = (store.cardOrder + store.definitions.map { it.id }).distinct().filter { key -> store.definitions.any { it.id == key } }.toMutableList()
        if (id == before) return store
        val from = order.indexOf(id); val to = order.indexOf(before)
        order.removeAt(from); order.add(to.coerceAtMost(order.size), id)
        return store.copy(cardOrder = order, revision = store.revision + 1)
    }
    fun resizeCard(store: AchievementStore, id: String, size: String): AchievementStore {
        require(store.definitions.any { it.id == id } && size in setOf("compact", "wide", "full")) { "Invalid card/size" }
        return store.copy(cardSizes = store.cardSizes + (id to size), revision = store.revision + 1)
    }
    fun latest(store: AchievementStore, day: String): DailyTarget? = store.targets.filter { it.date == day }
        .maxWithOrNull(compareBy<DailyTarget> { it.atMs }.thenBy { it.id })
    fun progress(store: AchievementStore, day: String): List<ObjectiveProgress> {
        val actual = store.days[day] ?: AchievementDay(day, "")
        return latest(store, day)?.objectives.orEmpty().map { o -> ObjectiveProgress(o, when (o.kind) {
            "focus_blocks" -> actual.focusBlocks; "focus_time" -> actual.focusMs
            else -> actual.activities[o.achievementId] ?: 0 }) }
    }
    fun status(store: AchievementStore, def: AchievementDefinition, day: String): String {
        val actual = store.days[day] ?: AchievementDay(day, "")
        if (actual.finalized) return actual.statuses[def.id] ?: "NO_TARGET"
        if (!def.streakEnabled || day < def.createdDate || def.archived) return "NO_TARGET"
        val count = actual.activities[def.id] ?: 0
        val met = when (def.kind) {
            "contract" -> { val p = progress(store, day); if (p.isEmpty()) return "NO_TARGET"; p.all { it.met } }
            "focus_blocks" -> actual.focusBlocks >= if (def.id == "focus_8") 8 else 5
            "focus_time" -> actual.focusMs >= 10_800_000
            "scheduled" -> {
                val scheduled = latest(store, day)?.objectives.orEmpty().filter { it.achievementId == def.id }
                if (scheduled.isEmpty() && !def.dailyParticipation && count == 0L) return "NO_TARGET"
                count >= (scheduled.firstOrNull()?.minimum ?: 1)
            }
            "abstinence" -> return "NO_TARGET"
            else -> count > 0
        }
        return if (met) "MET" else "PENDING"
    }
    fun stats(store: AchievementStore, def: AchievementDefinition, now: Long, zone: ZoneId): AchievementStats {
        if (!def.streakEnabled) return AchievementStats(def, 0.0, null, null, null, emptyList(), null)
        if (def.kind == "abstinence") {
            val points = store.events.filter { it.achievementId == def.id }.sortedWith(compareBy<AchievementEvent> { it.atMs }.thenBy { it.id })
            var best = 0.0
            val segments = points.zipWithNext().map { (a,b) ->
                val value = (b.atMs - a.atMs).toDouble() / 86_400_000
                AchievementSegment(def.id, a.date, b.date, value, value > best, a.atMs, b.atMs).also { best = max(best, value) }
            }
            val start = points.lastOrNull()
            val current = start?.let { (now - it.atMs).coerceAtLeast(0).toDouble()/86_400_000 } ?: 0.0
            val previous = segments.maxOfOrNull { it.value }
            return AchievementStats(def, current, previous, if (start == null) null else max(previous ?: 0.0, current),
                segments.takeIf { it.isNotEmpty() }?.map { it.value }?.average(), segments,
                start?.atMs?.toString(), start != null)
        }
        var start: String? = null; var end = ""; var current = 0.0; var best = 0.0
        val segments = mutableListOf<AchievementSegment>()
        val today = date(now, zone)
        val dates = (store.days.keys + today).filter { it >= def.createdDate && it <= today }.sorted()
        for (day in dates) {
            when (status(store, def, day)) {
                "MET" -> { if (start == null) start = day; end = day; current += 1 }
                "UNMET" -> {
                    if (current > 0) {
                        segments += AchievementSegment(def.id, start!!, end, current, current > best)
                        best = max(best, current)
                    }
                    start = null; current = 0.0
                }
                // Today's PENDING and unscheduled days do not close a streak before midnight.
            }
        }
        return AchievementStats(def, current, segments.maxOfOrNull { it.value }, max(best, current),
            segments.takeIf { it.isNotEmpty() }?.map { it.value }?.average(), segments, start)
    }
    fun finalize(store: AchievementStore, now: Long, zone: ZoneId): AchievementStore {
        if (store.beganDate.isBlank()) return store // Repository has not loaded yet.
        var next = store
        val today = date(now, zone)
        var day = LocalDate.parse(store.days.keys.filter { store.days[it]?.finalized == true }.maxOrNull() ?: store.beganDate)
        while (day.toString() < today) {
            val key = day.toString()
            val actual = next.days[key] ?: AchievementDay(key, zone.id)
            if (!actual.finalized) {
                val statuses = next.definitions.associate { def -> def.id to when (val s = status(next, def, key)) {
                    "PENDING" -> "UNMET"; else -> s } }
                next = next.copy(days = next.days + (key to actual.copy(finalized = true, statuses = statuses,
                    targetId = latest(next, key)?.id)), revision = next.revision + 1)
            }
            day = day.plusDays(1)
        }
        if (next !== store) next = next.copy(segments = next.definitions.flatMap { if (!it.streakEnabled && it.id in next.indexes) next.indexes.getValue(it.id).legacySegments else stats(next, it, now, zone).completed })
        return next
    }
    private fun mutableDay(store: AchievementStore, day: String, zone: ZoneId): AchievementDay {
        val actual = store.days[day] ?: AchievementDay(day, zone.id)
        require(!actual.finalized) { "Day $day is finalized; historical activity/targets cannot be changed" }
        return actual
    }
    fun apply(store: AchievementStore, command: AchievementCommand, now: Long, zone: ZoneId): AchievementStore {
        require(command.achievement_version in 1..6) { "Unsupported achievement contract" }
        require(!command.confirm || command.achievement_version >= 2 && command.operation in setOf("delete", "index_migrate", "index_reconcile")) { "confirm is only supported for contract 2 deletion" }
        require(command.id.matches(Regex("[A-Za-z0-9_.:-]{1,100}"))) { "Invalid achievement command id" }
        if (command.id in store.retiredCommands) return store // Redacted receipt: never resurrect deleted data.
        store.commands[command.id]?.let { require(it == command) { "Command id already used with different content" }; return store }
        require(command.source.isNotBlank() && command.source.length <= 100 && command.text.length <= 2000) { "Invalid source/text" }
        val at = Instant.parse(command.timestamp).toEpochMilli()
        require(at in 0..now) { "Achievement timestamps must not be in the future" }
        require(command.achievement_version >= 3 || command.current_value == null && command.target_value == null && command.unit == null) { "Progress requires contract 3" }
        require(command.operation in setOf("progress_set", "progress_increment") || command.current_value == null && command.target_value == null && command.unit == null) { "Unsupported progress fields" }
        require(command.icon == null || command.achievement_version >= 4 && command.operation == "icon_set") { "Icon requires contract 4 icon_set" }
        if (command.operation == "layout_order") {
            require(command.achievement_version == 6 && command.expected_store_revision == store.revision) { "Achievement order changed; refresh before rearranging" }
            require(command.definition == null && command.achievement_id == null && command.event == null && command.date == null && command.objectives == null && command.document == null && command.configuration == null && command.expected_index_revision == null && command.topic_id == null && command.completed == null && command.note == null && command.evidence == null && !command.confirm && command.text.isEmpty() && command.amount == 1L) { "Unsupported order fields" }
            val order = requireNotNull(command.order)
            require(order.size <= 1000 && order.distinct().size == order.size && order.all { id -> store.definitions.any { it.id == id } }) { "Invalid achievement order" }
            return store.copy(cardOrder = order, commands = store.commands + (command.id to command), revision = store.revision + 1)
        }
        require(command.achievement_version != 6) { "Contract 6 supports layout_order only" }
        require(command.order == null) { "Order requires layout_order" }
        if (command.operation.startsWith("index_")) {
            val result = IndexTracker.apply(store, command, at, zone)
            return result.copy(commands = result.commands + (command.id to command), revision = result.revision + 1)
        }
        require(command.document == null && command.configuration == null && command.expected_index_revision == null && command.expected_store_revision == null && command.topic_id == null && command.completed == null && command.note == null && command.evidence == null) { "Index fields require Index operation" }
        var next = finalize(store, now, zone)
        val today = date(now, zone)
        when (command.operation) {
            "icon_set" -> {
                require(command.achievement_version >= 4) { "Icons require achievement contract 4" }
                require(command.definition == null && command.event == null && command.date == null && command.objectives == null && command.text.isEmpty() && command.amount == 1L && !command.confirm) { "Unsupported icon fields" }
                val id = requireNotNull(command.achievement_id)
                require(next.definitions.any { it.id == id }) { "Unknown achievement" }
                val icon = requireNotNull(command.icon) { "icon required" }
                CardIconPolicy.validate(icon)
                val previous = next.cardIcons[id]
                // Default is an ordered reset receipt, so older icon SETs cannot undo it.
                if (previous == null || at > previous.atMs || at == previous.atMs && command.id > previous.commandId)
                    next = next.copy(cardIcons = next.cardIcons + (id to CardIconAssignment(icon, at, command.id)))
            }
            "upsert" -> {
                require(command.event == null && command.date == null && command.objectives == null && command.achievement_id == null) { "Unsupported upsert fields" }
                val d = requireNotNull(command.definition) { "definition required" }
                require(d.id.matches(Regex("[a-z][a-z0-9_]{0,99}")) && d.title.isNotBlank() && d.title.length <= 200) { "Invalid definition id/title" }
                if (command.achievement_version == 1) require(d.category in setOf("projects", "college") && d.kind == "scheduled") { "Only scheduled projects/college definitions can be created or updated in contract 1" }
                else require(d.category in categories.drop(1).map { it.lowercase() } && d.kind in setOf("daily", "scheduled", "abstinence")) { "Custom cards require a valid category and daily, scheduled or abstinence kind" }
                require(d.id !in next.deletedIds) { "Achievement id was permanently deleted; create a new id" }
                val old = next.definitions.find { it.id == d.id }
                require(d.id !in builtInIds) { "Use restore for built-in cards; their measurement rules cannot be replaced" }
                require(old == null || old.category == d.category && old.kind == d.kind) { "Cannot change achievement category/kind; use a new id" }
                require(d.description.length <= 1000 && d.metadata.size <= 30 && d.metadata.all { it.key.length <= 100 && it.value.length <= 2000 }) { "Definition metadata too large" }
                require(command.achievement_version >= 3 || d.streakEnabled && !d.progressEnabled && d.progress == null) { "Capabilities require contract 3" }
                require(d.streakEnabled || d.progressEnabled) { "At least one capability is required" }
                require(!d.progressEnabled || d.kind != "abstinence") { "Abstinence progress is unsupported" }
                d.progress?.let(::validateProgress)
                require(d.progressEnabled || d.progress == null) { "progress requires progress_enabled" }
                require(d.category != "index" || old != null && d.id in next.indexes && d.streakEnabled == old.streakEnabled && d.progressEnabled && d.progress == null) { "Use Index create/migration; numeric Index edits are forbidden" }
                val replacement = d.copy(createdDate = old?.createdDate ?: today, progress = old?.progress,
                    streakEnabled = if (command.achievement_version < 3) old?.streakEnabled ?: true else d.streakEnabled,
                    progressEnabled = if (command.achievement_version < 3) old?.progressEnabled ?: false else d.progressEnabled)
                next = next.copy(definitions = if (old == null) next.definitions + replacement else next.definitions.map { if (it.id == d.id) replacement else it })
                if (d.progressEnabled && d.progress != null) next = setProgress(next, d.id, d.progress, command, at)
            }
            "progress_set", "progress_increment" -> {
                require(command.achievement_version >= 3) { "Progress requires achievement contract 3" }
                require(command.definition == null && command.event == null && command.date == null && command.objectives == null && !command.confirm && command.text.isEmpty()) { "Unsupported progress fields" }
                val d = next.definitions.find { it.id == command.achievement_id && !it.archived } ?: error("Unknown or archived achievement")
                require(d.kind != "abstinence" && d.id !in next.indexes) { "Index counts come only from topic evidence" }
                val old = d.progress ?: AchievementProgress()
                if (command.operation == "progress_increment") {
                    require(d.progressEnabled && command.current_value == null && command.target_value == null && command.unit == null && command.amount in 1..100000) { "Increment requires enabled progress and amount 1..100000" }
                    require(at >= old.updatedAt) { "Increment is older than latest progress; use an authoritative SET" }
                    // Each unique increment is applied once, including equal timestamp increments.
                    next = setProgress(next, d.id, old.copy(currentValue = old.currentValue + command.amount), command,
                        at.coerceAtLeast(old.updatedAt))
                } else {
                    require(command.current_value != null || command.target_value != null || command.unit != null) { "SET needs current_value, target_value or unit" }
                    next = setProgress(next, d.id, old.copy(currentValue = command.current_value ?: old.currentValue,
                        targetValue = command.target_value ?: old.targetValue, unit = command.unit ?: old.unit), command, at)
                }
            }
            "archive", "restore", "delete" -> {
                require(command.definition == null && command.event == null && command.date == null && command.objectives == null) { "Unsupported archive fields" }
                val d = next.definitions.find { it.id == command.achievement_id } ?: error("Unknown achievement")
                if (command.achievement_version == 1) require(command.operation == "archive" && d.kind == "scheduled") { "This action requires achievement contract 2" }
                if (command.operation != "restore") {
                    val referenced = next.targets.map { it.date }.distinct().filter { it >= today }.any { day ->
                        latest(next, day)?.objectives.orEmpty().any { it.achievementId == d.id } }
                    require(!referenced) { "Remove ${d.title} from current/future daily targets before ${command.operation}" }
                }
                if (command.operation in setOf("delete", "index_migrate", "index_reconcile")) {
                    require(command.confirm) { "Permanent deletion requires confirm:true" }
                    val retired = next.commands.filterValues { it.achievement_id == d.id || it.definition?.id == d.id }.keys
                    next = next.copy(definitions = next.definitions.filterNot { it.id == d.id },
                        progressHistory = next.progressHistory.filterNot { it.achievementId == d.id },
                        events = next.events.filterNot { it.achievementId == d.id }, segments = next.segments.filterNot { it.achievementId == d.id },
                        days = next.days.mapValues { (_, day) -> day.copy(activities = day.activities - d.id, statuses = day.statuses - d.id) },
                        commands = next.commands - retired, retiredCommands = next.retiredCommands + retired,
                        indexes = next.indexes - d.id, deletedIds = next.deletedIds + d.id, cardOrder = next.cardOrder - d.id, cardSizes = next.cardSizes - d.id, cardIcons = next.cardIcons - d.id,
                        seenRecords = next.seenRecords.filterNot { it.startsWith("${d.id}:") }.toSet())
                } else next = next.copy(definitions = next.definitions.map { if (it.id == d.id) it.copy(archived = command.operation == "archive") else it })
            }
            "daily_target" -> {
                require(command.definition == null && command.event == null && command.achievement_id == null) { "Unsupported target fields" }
                val day = LocalDate.parse(requireNotNull(command.date)).toString()
                require(day >= today && LocalDate.parse(day) <= LocalDate.parse(today).plusDays(366)) { "Targets must be for today or the next 366 days" }
                mutableDay(next, day, zone)
                val objectives = requireNotNull(command.objectives)
                require(objectives.size in 1..100) { "Target needs 1..100 objectives" }
                require(objectives.map { it.kind to it.achievementId }.distinct().size == objectives.size) { "Duplicate target objective" }
                objectives.forEach { o ->
                    require(o.kind in setOf("focus_blocks", "focus_time", "achievement") && o.minimum in 1..604800000 && o.text.length <= 2000) { "Invalid objective kind/minimum/text" }
                    if (o.kind == "achievement") require(next.definitions.any { it.id == o.achievementId && !it.archived && it.streakEnabled && it.kind in setOf("daily", "scheduled") }) { "Unknown or unsupported objective achievement" }
                    else require(o.achievementId == null) { "Focus objectives cannot reference achievementId" }
                }
                next = next.copy(targets = next.targets + DailyTarget(command.id, day, at, command.source, objectives))
            }
            "event" -> {
                require(command.definition == null && command.date == null && command.objectives == null) { "Unsupported event fields" }
                val d = next.definitions.find { it.id == command.achievement_id && !it.archived } ?: error("Unknown or archived achievement")
                val event = requireNotNull(command.event)
                val day = date(at, zone)
                require(command.amount in 1..100000) { "Event amount must be 1..100000" }
                if (d.kind == "abstinence") {
                    val last = next.events.lastOrNull { it.achievementId == d.id }
                    require(event == if (last == null) "initialize" else "streak_break") { "Initialize abstinence once, then use streak_break" }
                    require(last == null || at > last.atMs) { "Streak breaks must be chronological and distinct" }
                } else {
                    require(d.kind in setOf("daily", "scheduled") && event in setOf("completed", "objective_completed")) { "This achievement is automatically measured; unsupported event" }
                    require(day == today) { "Only today's activity can be recorded; closed days are permanent" }
                    val actual = mutableDay(next, day, zone)
                    next = next.copy(days = next.days + (day to actual.copy(activities = actual.activities + (d.id to ((actual.activities[d.id] ?: 0) + command.amount)))))
                }
                next = next.copy(events = next.events + AchievementEvent(command.id, d.id, event, at, day, command.source, command.amount, command.text))
                next.indexes[d.id]?.let { l ->
                    require(d.streakEnabled) { "Progress-only Index has no daily activity capability" }
                    if (store.events.none { it.achievementId == d.id && it.date == day }) next = next.copy(indexes = next.indexes + (d.id to l.copy(milestones = l.milestones + IndexMilestone(command.id, at, "Verified daily study activity ($day)"))))
                }
            }
            else -> throw IllegalArgumentException("Unknown achievement operation")
        }
        next = next.copy(commands = next.commands + (command.id to command), revision = next.revision + 1)
        return IndexTracker.project(next.copy(segments = next.definitions.flatMap { if (!it.streakEnabled && it.id in next.indexes) next.indexes.getValue(it.id).legacySegments else stats(next, it, now, zone).completed }))
    }
    /** Capture cumulative monotonic focus once per block attempt, independently of report sync/deletion. */
    fun capture(store: AchievementStore, state: DeckState, now: ClockNow, zone: ZoneId): AchievementStore {
        val session = state.session ?: return store
        if (state.startedAtMs == 0L) return store
        var next = store
        fun record(index: Int, active: Long, at: Long, done: Boolean, start: Long) {
            if (session.blocks[index].type != "focus") return
            val key = "${session.sessionId}:${state.startedAtMs}:$index"
            val old = next.focusCursors[key] ?: FocusCursor(atMs = start)
            val delta = (active - old.activeMs).coerceAtLeast(0)
            // Duration is monotonic. Civil boundaries allocate that measured duration; no off/reboot gap is counted.
            var end = at.coerceAtLeast(old.atMs)
            var remaining = delta
            while (remaining > 0) {
                val day = date((end - 1).coerceAtLeast(0), zone)
                val boundary = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
                val portion = minOf(remaining, (end - max(boundary, old.atMs)).coerceAtLeast(1))
                val actual = next.days[day] ?: AchievementDay(day, zone.id)
                // A civil-clock correction must not rewrite a closed contract.
                if (!actual.finalized && day >= store.beganDate) next = next.copy(days = next.days + (day to actual.copy(focusMs = actual.focusMs + portion)))
                remaining -= portion; end -= portion
            }
            if (done && !old.done) {
                val day = date(at, zone)
                val actual = next.days[day] ?: AchievementDay(day, zone.id)
                if (!actual.finalized && day >= store.beganDate) next = next.copy(days = next.days + (day to actual.copy(focusBlocks = actual.focusBlocks + 1)))
            }
            if (delta > 0 || done != old.done || key !in next.focusCursors) next = next.copy(
                focusCursors = next.focusCursors + (key to FocusCursor(max(old.activeMs, active), at, old.done || done)), revision = next.revision + 1)
        }
        state.results.forEachIndexed { index, r -> record(index, r.activeMs, r.finishedAtMs, r.outcome == "done", r.startedAtMs) }
        if (state.phase in setOf("active", "paused") && state.index >= state.results.size) record(state.index,
            TimerEngine(state).currentActive(now), now.wall, false, state.blockStartedAtMs)
        return next
    }
    fun value(value: Double?, abstinence: Boolean, signed: Boolean = false): String {
        if (value == null) return "N/A"
        val sign = if (!signed) "" else if (value > 0) "+" else if (value < 0) "−" else ""
        val abs = kotlin.math.abs(value)
        if (!abstinence) return sign + (if (abs % 1 == 0.0) abs.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, abs)) + " days"
        val minutes = kotlin.math.round(abs * 86_400_000).toLong() / 60_000
        return sign + if (minutes >= 1440) "${minutes/1440}d ${minutes%1440/60}h ${minutes%60}m" else "${minutes/60}h ${minutes%60}m"
    }

    fun snapshotChunks(raw: String): List<String> {
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < raw.length) {
            var end = minOf(offset + 50_000, raw.length)
            if (end < raw.length && raw[end - 1].isHighSurrogate()) end--
            chunks += raw.substring(offset, end)
            offset = end
        }
        return chunks
    }
}
