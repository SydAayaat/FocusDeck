package com.hax.focusdeck

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

val WireJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable
data class Block(
    val id: String, val type: String, val title: String,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
    @SerialName("quality_criteria") val qualityCriteria: List<String> = emptyList(),
    val comparison: TaskComparison? = null,
    @SerialName("reward_contract") val rewardContract: RewardContract? = null,
    @SerialName("reward_policy") val rewardPolicy: ConditionalPolicy? = null,
    @SerialName("session_weight") val sessionWeight: Double = 1.0,
    val classification: ClassificationPolicy? = null,
    @SerialName("timer_mode") val timerMode: String? = null,
    val schedule: PlannedSchedule? = null, val category: String? = null,
    @SerialName("achievement_id") val achievementId: String? = null,
    @SerialName("index_topic_id") val indexTopicId: String? = null
) {
    val stopwatch: Boolean get() = timerMode == "stopwatch"
    val effectiveMode: String get() = timerMode ?: "countdown"
    val plannedMs: Long? get() = durationSeconds?.times(1000)
}

@Serializable
data class Session(
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("session_id") val sessionId: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    val blocks: List<Block>,
    @SerialName("auto_advance") val autoAdvance: Boolean = true,
    @SerialName("reward_strategy") val rewardStrategy: RewardStrategy? = null,
    @SerialName("session_reward") val sessionReward: RewardContract? = null,
    @SerialName("comparison_key") val comparisonKey: String? = null,
    @SerialName("session_reward_policy") val sessionRewardPolicy: ConditionalPolicy? = null,
    val classification: ClassificationPolicy = ClassificationPolicy(),
    @SerialName("session_scoring") val sessionScoring: SessionScoring = SessionScoring(),
    val schedule: PlannedSchedule? = null
) {
    fun validate() {
        require(protocolVersion in 1..2) { "Unsupported session protocol; expected 1 or 2" }
        if (protocolVersion == 2) require(sessionId.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))) { "v2 session_id must be a fresh UUID" }
        require(sessionId.isNotBlank() && sessionId.length <= 100) { "Invalid session ID" }
        require(title.isNotBlank() && title.length <= 200) { "Invalid session title" }
        require(createdAt.length <= 60) { "Invalid timestamp" }
        Instant.parse(createdAt) // Incoming CLI plans are ISO-8601 instants with timezone.
        schedule?.validate()
        require(blocks.size in 1..100) { "Session needs 1..100 blocks" }
        require(blocks.map { it.id }.distinct().size == blocks.size) { "Duplicate block IDs" }
        blocks.forEach {
            it.schedule?.validate()
            listOf(it.category,it.achievementId,it.indexTopicId).filterNotNull().forEach { text -> require(text.isNotBlank() && text.length <= 200) }
            require(it.id.isNotBlank() && it.id.length <= 100) { "Invalid block ID" }
            require(it.title.isNotBlank() && it.title.length <= 200) { "Invalid block title" }
            require(it.type in listOf("focus", "break")) { "Invalid block type" }
            require(it.timerMode == null || it.timerMode in setOf("countdown", "stopwatch")) { "timer_mode must be countdown or stopwatch" }
            require(protocolVersion != 2 || it.type != "focus" || it.timerMode != null) { "v2 focus blocks require explicit timer_mode" }
            require(protocolVersion != 1 || it.timerMode == null) { "timer_mode requires session protocol 2" }
            require(!it.stopwatch || protocolVersion == 2 && it.type == "focus") { "Stopwatch requires session protocol 2 and a focus block" }
            if (it.stopwatch) require(it.durationSeconds == null) { "Stopwatch must not have duration_seconds" }
            else require(it.durationSeconds != null && it.durationSeconds in 1..86400) { "Countdown duration_seconds must be 1..86400" }
        }
        require(blocks.sumOf { it.durationSeconds ?: 0 } <= 604800) { "Timed portion longer than seven days" }
        validateRewards()
        validateCompletionPolicies()
        if (competitive) require(WireJson.encodeToString(Session.serializer(), this).toByteArray(Charsets.UTF_8).size <= 131072) { "Reward plan exceeds 128 KiB" }
    }
}

@Serializable
data class Pairing(
    @SerialName("protocol_version") val protocolVersion: Int,
    val host: String, val port: Int, val token: String,
    @SerialName("certificate_sha256") val certificateSha256: String,
    @SerialName("desktop_id") val desktopId: String,
    val role: String = "phone"
) {
    val viewer: Boolean get() = role == "viewer"
    fun validate() {
        require(role in setOf("phone", "viewer")) { "Invalid device role" }
        require(protocolVersion == 1 && port in 1024..65535) { "Invalid pairing version or port" }
        require(host.matches(Regex("[a-zA-Z0-9.:-]{1,253}"))) { "Enter an IP address or hostname" }
        require(token.length in 32..200) { "Invalid pairing token" }
        require(certificateSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid certificate fingerprint" }
        require(desktopId.isNotBlank() && desktopId.length <= 100) { "Invalid desktop ID" }
    }
}

@Serializable
data class ClockNow(val wall: Long, val mono: Long, val boot: Int)

@Serializable
data class BlockResult(
    val id: String, val type: String, val title: String,
    @SerialName("planned_ms") val plannedMs: Long?,
    @SerialName("budget_ms") val budgetMs: Long?,
    @SerialName("active_ms") val activeMs: Long,
    @SerialName("paused_ms") val pausedMs: Long,
    @SerialName("started_at_ms") val startedAtMs: Long,
    @SerialName("finished_at_ms") val finishedAtMs: Long,
    val outcome: String,
    @SerialName("overtime_ms") val overtimeMs: Long? = budgetMs?.let { (activeMs - it).coerceAtLeast(0) },
    val performance: Performance? = null,
    @SerialName("timer_mode") val timerMode: String = "countdown",
    @SerialName("wall_ms") val wallMs: Long? = null,
    @SerialName("focus_ratio") val focusRatio: Double? = null
)

@Serializable
data class TimelineEvent(
    val seq: Int, val kind: String,
    @SerialName("at_ms") val atMs: Long,
    @SerialName("mono_ms") val monoMs: Long,
    val boot: Int,
    @SerialName("block_index") val blockIndex: Int,
    val phase: String,
    @SerialName("active_ms") val activeMs: Long,
    @SerialName("paused_ms") val pausedMs: Long,
    @SerialName("budget_ms") val budgetMs: Long?
)

@Serializable
data class Report(
    @SerialName("session_id") val sessionId: String,
    val title: String, val status: String,
    @SerialName("planned_ms") val plannedMs: Long?,
    @SerialName("active_ms") val activeMs: Long,
    @SerialName("focused_ms") val focusedMs: Long,
    @SerialName("break_ms") val breakMs: Long,
    @SerialName("paused_ms") val pausedMs: Long,
    @SerialName("wall_ms") val wallMs: Long,
    @SerialName("started_at_ms") val startedAtMs: Long,
    @SerialName("finished_at_ms") val finishedAtMs: Long,
    val blocks: List<BlockResult>,
    @SerialName("overtime_ms") val overtimeMs: Long = blocks.sumOf { it.overtimeMs ?: 0 },
    @SerialName("timeline_coverage") val timelineCoverage: String = "unavailable",
    val events: List<TimelineEvent> = emptyList(),
    @SerialName("planned_block_count") val plannedBlockCount: Int? = null,
    @SerialName("recorded_timezone") val recordedTimezone: String? = null,
    val competition: SessionCompetition? = null,
    @SerialName("report_version") val reportVersion: Int = 1,
    @SerialName("timed_planned_ms") val timedPlannedMs: Long? = null,
    @SerialName("planned_countdown_focus_blocks") val plannedCountdownFocusBlocks: Int? = null,
    @SerialName("planned_stopwatch_focus_blocks") val plannedStopwatchFocusBlocks: Int? = null,
    @SerialName("schedule_snapshot") val scheduleSnapshot: ScheduleSnapshot? = null
) {
    val completedFocusedMs: Long get() = blocks.filter { it.type == "focus" && it.outcome == "done" }.sumOf { it.activeMs }
    val statusLabel: String get() = when {
        status == "complete" -> "Complete"
        plannedBlockCount != null && blocks.size == plannedBlockCount && blocks.any { it.outcome == "skipped" } && blocks.none { it.outcome == "stopped" } -> "Finished with skips"
        else -> "Ended early"
    }
    val outcomeSummary: String get() = buildList {
        add("${blocks.count { it.outcome == "done" }} done")
        val skipped = blocks.count { it.outcome == "skipped" }
        val ended = blocks.count { it.outcome == "stopped" }
        val timedOut = blocks.count { it.outcome == "timed_out" }
        val notStarted = plannedBlockCount?.let { (it - blocks.size).coerceAtLeast(0) } ?: 0
        if (skipped > 0) add("$skipped skipped")
        if (ended > 0) add("$ended ended early")
        if (timedOut > 0) add("$timedOut timed out")
        if (notStarted > 0) add("$notStarted not started")
    }.joinToString(" · ")
    val key: String get() = "$sessionId:$startedAtMs"
    fun summary(): String = buildString {
        appendLine("FocusDeck — $title ($status)")
        appendLine("$statusLabel | $outcomeSummary")
        appendLine("Session ID: $sessionId")
        appendLine("Report key: $key | Started: ${Instant.ofEpochMilli(startedAtMs)}")
        appendLine("Report contract: v$reportVersion | known timed planned portion: ${humanTime(timedPlannedMs ?: plannedMs)}")
        appendLine("Overtime: ${humanTime(overtimeMs)}")
        appendLine("Planned: ${humanTime(plannedMs)} | Actual active: ${humanTime(activeMs)}")
        appendLine("Completed focus time: ${humanTime(completedFocusedMs)} | Overall Focus Ratio: ${competition?.focusRatio ?: "N/A"}")
        appendLine("Focused: ${humanTime(focusedMs)} | Breaks: ${humanTime(breakMs)} | Paused: ${humanTime(pausedMs)}")
        appendLine("Wall time: ${humanTime(wallMs)} | Finished: ${Instant.ofEpochMilli(finishedAtMs)}")
        blocks.forEach { appendLine("${it.id} · ${it.title} (${it.timerMode}): planned ${humanTime(it.plannedMs)}, actual ${humanTime(it.activeMs)}, overtime ${humanTime(it.overtimeMs)}, ${it.outcome}") }
        append(RewardEngine.summary(this@Report))
        append(TimelineAnalysis.summary(this@Report))
        appendLine("Timer activity is not proof of attention or learning quality. Any legacy timed_out results need confirmation.")
        appendLine("GPT: match this Session ID to the original plan, compare planned/actual/overtime timings, adjust countdown targets only; assess stopwatch deliverables/completion/Focus Ratio without inventing time targets; ask for recall/task-quality evidence.")
    }
}

fun humanTime(ms: Long?): String {
    if (ms == null) return "N/A"
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}m" else "${s / 60}m ${s % 60}s"
}

@Serializable
data class CommandAck(val ok: Boolean, val error: String? = null)

@Serializable
data class DeckState(
    val session: Session? = null,
    val phase: String = "idle",
    val index: Int = 0,
    val budgetMs: Long? = 0,
    val activeMs: Long = 0,
    val pausedMs: Long = 0,
    val anchorMono: Long = 0,
    val anchorWall: Long = 0,
    val boot: Int = -1,
    val startedAtMs: Long = 0,
    val blockStartedAtMs: Long = 0,
    val finishedAtMs: Long = 0,
    val results: List<BlockResult> = emptyList(),
    val notice: String = "",
    val overtimeNotified: Boolean = false,
    val revision: Long = 0,
    val events: List<TimelineEvent> = emptyList(),
    val timelineComplete: Boolean = false,
    val recordedTimezone: String? = null,
    val competition: SessionCompetition? = null
)

@Serializable
data class StoredDeck(
    val deviceId: String,
    val pairing: Pairing? = null,
    val state: DeckState = DeckState(),
    val acknowledgements: Map<String, CommandAck> = emptyMap(),
    val reports: List<Report> = emptyList(),
    val delivered: Set<String> = emptySet(),
    val keepAwake: Boolean = true,
    val immersive: Boolean = false,
    val sound: Boolean = false,
    val dimDesk: Boolean = false,
    val motion: String = "FULL",
    val theme: String = "gold",
    val timerRefreshRate: Int = 60,
    val audio: SoundPreferences = SoundPreferences(),
    val developerMode: Boolean = false,
    val historyTreeUri: String? = null,
    val deletions: HistoryDeletion = HistoryDeletion(),
    val deletionDelivered: String = ""
)
