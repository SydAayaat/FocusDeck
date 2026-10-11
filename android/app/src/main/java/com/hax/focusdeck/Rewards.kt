package com.hax.focusdeck

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime
import java.util.Locale
import kotlin.math.min
import kotlin.math.round

/** Explicit task identity supplied by the planner; titles alone never establish comparability. */
@Serializable data class TaskComparison(val category: String, val complexity: String, val method: String,
    @SerialName("work_units") val workUnits: Int, @SerialName("target_revision") val targetRevision: String) {
    fun validate() { require(listOf(category, complexity, method, targetRevision).all { it.isNotBlank() && it.length <= 100 }); require(workUnits in 1..10000) }
}
@Serializable data class RewardAction(val kind: String, val reps: Int = 0, val seconds: Int = 0, val text: String? = null) {
    fun validate(session: Boolean) {
        if (kind == "text") {
            require(text != null && text.isNotBlank() && text.codePointCount(0, text.length) <= 1000 && reps == 0 && seconds == 0)
            return
        }
        require(text == null)
        require(kind in physicalKinds || (session && kind in setOf("guitar", "coffee"))) { "Unsupported reward kind" }
        when (kind) {
            "pushups", "squats" -> require(reps in 1..20 && seconds in 30..120) { "Repetition rewards need 1..20 reps and 30..120 seconds" }
            "guitar" -> require(reps == 0 && seconds in 600..900)
            "coffee" -> require(reps == 0 && seconds == 0)
            else -> require(reps == 0 && seconds in 30..120)
        }
    }
    fun label(): String = when (kind) { "text" -> requireNotNull(text); "pushups", "squats" -> "$reps $kind"; "coffee" -> "coffee"; else -> "$seconds sec $kind" }
}
val physicalKinds = setOf("pushups", "squats", "plank", "mobility", "walk", "stretching")
@Serializable data class RewardOption(val actions: List<RewardAction>, val alternatives: List<List<RewardAction>> = emptyList()) {
    fun validate(session: Boolean) {
        require(alternatives.size <= 4)
        (listOf(actions) + alternatives).forEach { group ->
            require(group.size in 1..3 && group.map { it.kind }.distinct().size == group.size)
            require(group.none { it.kind == "text" } || group.size == 1) { "Text rewards use one complete description" }
            group.forEach { it.validate(session) }
            require(group.filter { it.kind in physicalKinds }.sumOf { it.seconds } <= 120) { "Physical reset exceeds two minutes" }
        }
    }
}
@Serializable data class RewardContract(val completion: RewardOption? = null, val target: RewardOption? = null,
    val strong: RewardOption? = null, @SerialName("personal_best") val personalBest: RewardOption? = null,
    val exceptional: RewardOption? = null) {
    fun validate(session: Boolean) {
        require(listOfNotNull(completion, target, strong, personalBest, exceptional).isNotEmpty())
        require(if (session) target == null && personalBest == null else exceptional == null)
        listOfNotNull(completion, target, strong, personalBest, exceptional).forEach { it.validate(session) }
    }
    fun option(tier: String) = when (tier) { "completion" -> completion; "target" -> target; "strong" -> strong; "personal_best" -> personalBest; "exceptional" -> exceptional; else -> null }
}
@Serializable data class ScorePolicy(
    @SerialName("rank_thresholds") val rankThresholds: List<Double> = listOf(85.0, 95.0, 105.0, 110.0),
    @SerialName("target_pace") val targetPace: Double = 95.0,
    @SerialName("strong_pace") val strongPace: Double = 110.0,
    @SerialName("target_focus") val targetFocus: Double = 80.0,
    @SerialName("strong_focus") val strongFocus: Double = 90.0,
    @SerialName("recalibration_pace") val recalibrationPace: Double = 150.0) {
    fun validate() {
        require(rankThresholds.size == 4 && rankThresholds.all { it.isFinite() && it in 1.0..111.0 } && rankThresholds.zipWithNext().all { it.first < it.second })
        require(targetPace.isFinite() && strongPace.isFinite() && targetPace in 80.0..100.0 && strongPace in 100.0..120.0 && strongPace >= targetPace)
        require(targetFocus.isFinite() && strongFocus.isFinite() && targetFocus in 50.0..100.0 && strongFocus in targetFocus..100.0)
        require(recalibrationPace.isFinite() && recalibrationPace in 125.0..200.0)
    }
    fun rank(score: Double): String = listOf("C", "B", "A", "S", "S+")[rankThresholds.count { score >= it }]
}
@Serializable data class CoffeePolicy(val enabled: Boolean = false,
    @SerialName("minimum_productive_session_seconds") val minimumProductiveSessionSeconds: Long = 10800,
    @SerialName("minimum_hours_between_coffee_rewards") val minimumHoursBetweenCoffeeRewards: Int = 3,
    @SerialName("latest_allowed_local_time") val latestAllowedLocalTime: String = "16:00") {
    fun validate() {
        require(minimumProductiveSessionSeconds in 10800..86400 && minimumHoursBetweenCoffeeRewards in 1..24)
        require(latestAllowedLocalTime.matches(Regex("\\d{2}:\\d{2}"))); LocalTime.parse(latestAllowedLocalTime)
    }
}
@Serializable data class RewardStrategy(val style: String = "physical_competitive",
    @SerialName("allowed_rewards") val allowedRewards: List<String> = physicalKinds.toList() + listOf("guitar", "coffee"),
    @SerialName("forbidden_rewards") val forbiddenRewards: List<String> = listOf("youtube", "social_media", "chess", "pornography", "endless_browsing"),
    @SerialName("default_contract") val defaultContract: RewardContract? = null,
    val scoring: ScorePolicy = ScorePolicy(),
    @SerialName("daily_rep_limits") val dailyRepLimits: Map<String, Int> = mapOf("pushups" to 40, "squats" to 60),
    @SerialName("daily_physical_seconds") val dailyPhysicalSeconds: Int = 600,
    @SerialName("max_consecutive_same_activity") val maxConsecutiveSameActivity: Int = 2,
    @SerialName("coffee_reward") val coffeeReward: CoffeePolicy = CoffeePolicy()) {
    fun validate() {
        require(style == "physical_competitive")
        require(allowedRewards.isNotEmpty() && allowedRewards.size <= 8 && allowedRewards.distinct().size == allowedRewards.size && allowedRewards.all { it in physicalKinds || it in setOf("guitar", "coffee") })
        require(forbiddenRewards.size <= 20 && forbiddenRewards.all { it.isNotBlank() && it.length <= 100 })
        require(dailyRepLimits.keys == setOf("pushups", "squats") && dailyRepLimits.values.all { it in 0..100 })
        require(dailyPhysicalSeconds in 0..1200 && maxConsecutiveSameActivity in 1..3)
        scoring.validate(); coffeeReward.validate(); defaultContract?.validate(false)
    }
}
fun Session.validateRewards() {
    rewardStrategy?.validate(); sessionReward?.validate(true)
    sessionRewardPolicy?.validate(true); classification.validate(); sessionScoring.validate()
    require(sessionReward == null || rewardStrategy != null) { "session_reward requires reward_strategy" }
    require(comparisonKey == null || (comparisonKey.isNotBlank() && comparisonKey.length <= 100))
    blocks.forEach { block ->
        require(block.qualityCriteria.size <= 10 && block.qualityCriteria.all { it.isNotBlank() && it.length <= 200 })
        block.comparison?.validate(); block.rewardContract?.validate(false)
        block.rewardPolicy?.validate(false); block.classification?.validate()
        require(block.sessionWeight.isFinite() && block.sessionWeight in 0.01..10.0)
        require(block.rewardPolicy == null || block.rewardContract == null) { "Use reward_policy or legacy reward_contract, not both" }
        require(block.type == "focus" || (block.rewardContract == null && block.comparison == null && block.qualityCriteria.isEmpty() && block.rewardPolicy == null && block.classification == null && block.sessionWeight == 1.0))
        require(block.rewardContract == null || rewardStrategy != null) { "Reward contracts require reward_strategy" }
    }
    require(sessionRewardPolicy == null || sessionReward == null) { "Use session_reward_policy or legacy session_reward, not both" }
    val strategy = this.strategy
    (blocks.mapNotNull { it.rewardPolicy } + listOfNotNull(sessionRewardPolicy)).forEach { policy -> policy.tiers.forEach { tier ->
        (listOf(tier.reward) + tier.alternatives).flatMap { it.typed(policy === sessionRewardPolicy) }.forEach { require(it.kind == "text" || (it.kind in strategy.allowedRewards && it.kind !in strategy.forbiddenRewards)) }
    } }
    val contracts = listOfNotNull(strategy.defaultContract, sessionReward) + blocks.mapNotNull { it.rewardContract }
    contracts.forEach { contract -> listOfNotNull(contract.completion, contract.target, contract.strong, contract.personalBest, contract.exceptional).forEach { option ->
        (listOf(option.actions) + option.alternatives).flatten().forEach { require(it.kind in strategy.allowedRewards && it.kind !in strategy.forbiddenRewards) { "Reward not permitted by strategy" } }
    } }
}
@Serializable data class Benchmark(@SerialName("report_key") val reportKey: String, @SerialName("block_id") val blockId: String? = null,
    val score: Double, @SerialName("active_ms") val activeMs: Long)
@Serializable data class RewardReceipt(val tier: String = "none", val actions: List<RewardAction> = emptyList(),
    val reason: String = "No eligible configured reward", val acknowledged: Boolean = false, val skipped: Boolean = false,
    @SerialName("acknowledged_at_ms") val acknowledgedAtMs: Long? = null,
    @SerialName("local_day") val localDay: String? = null,
    @SerialName("daily_reps_before") val dailyRepsBefore: Map<String, Int> = emptyMap(),
    @SerialName("daily_physical_seconds_before") val dailyPhysicalSecondsBefore: Int = 0) {
    fun label() = actions.joinToString(" + ") { it.label() }.ifEmpty { "No reward assigned" }
}
@Serializable data class Performance(val quality: String = "pending",
    @SerialName("quality_criteria") val qualityCriteria: List<String> = emptyList(), val comparison: TaskComparison? = null,
    @SerialName("wall_ms") val wallMs: Long = 0,
    @SerialName("pace_index") val paceIndex: Double? = null,
    @SerialName("focus_ratio") val focusRatio: Double? = null,
    @SerialName("beat_score") val beatScore: Double? = 0.0, val rank: String? = "C",
    @SerialName("recalibration_needed") val recalibrationNeeded: Boolean = false,
    @SerialName("previous_benchmark") val previousBenchmark: Benchmark? = null,
    @SerialName("new_personal_best") val newPersonalBest: Boolean = false,
    @SerialName("pb_delta") val pbDelta: Double? = null,
    val scoring: ScorePolicy = ScorePolicy(), val reward: RewardReceipt = RewardReceipt(),
    @SerialName("reward_contract") val rewardContract: RewardContract? = null,
    @SerialName("reward_policy") val rewardPolicy: ConditionalPolicy? = null,
    @SerialName("performance_class") val performanceClass: String? = null,
    @SerialName("session_weight") val sessionWeight: Double = 1.0,
    val classification: ClassificationPolicy? = null)
@Serializable data class SessionCompetition(@SerialName("comparison_key") val comparisonKey: String? = null,
    @SerialName("planned_focus_blocks") val plannedFocusBlocks: Int,
    @SerialName("completed_focus_blocks") val completedFocusBlocks: Int,
    @SerialName("on_or_ahead_count") val onOrAheadCount: Int,
    @SerialName("completion_fraction") val completionFraction: Double,
    @SerialName("average_pace") val averagePace: Double?,
    @SerialName("focus_ratio") val focusRatio: Double?,
    @SerialName("beat_score") val beatScore: Double?, val rank: String?,
    @SerialName("previous_benchmark") val previousBenchmark: Benchmark? = null,
    @SerialName("new_personal_best") val newPersonalBest: Boolean = false,
    @SerialName("pb_delta") val pbDelta: Double? = null,
    @SerialName("reward_strategy") val rewardStrategy: RewardStrategy,
    @SerialName("session_reward") val sessionReward: RewardContract? = null,
    val reward: RewardReceipt = RewardReceipt(),
    val weighted: WeightedSession? = null,
    @SerialName("session_reward_policy") val sessionRewardPolicy: ConditionalPolicy? = null,
    @SerialName("score_scope") val scoreScope: String = "all_focus")

/** No random selection, network or AI. All fallbacks must be supplied in the contract. */
object RewardEngine {
    fun rounded(value: Double) = round(value * 100.0) / 100.0
    fun pace(block: BlockResult): Double? = if (block.plannedMs != null && block.activeMs > 0) rounded(block.plannedMs.toDouble() / block.activeMs * 100) else null
    fun focus(active: Long, paused: Long): Double? = if (active + paused > 0) rounded(active.toDouble() / (active + paused) * 100) else null
    fun score(pace: Double?, focus: Double?, confirmed: Boolean, policy: ScorePolicy): Double {
        if (!confirmed || pace == null || focus == null) return 0.0
        val score = .55 * min(pace, 120.0) + .25 * focus + 20.0
        return rounded(if (pace > policy.recalibrationPace) min(score, 100.0) else score)
    }
    private fun receipts(state: DeckState, history: List<Report>): List<RewardReceipt> {
        val key = "${state.session?.sessionId}:${state.startedAtMs}"
        return history.distinctBy { it.key }.filter { it.key != key }.flatMap { it.blocks.mapNotNull { b -> b.performance?.reward } + listOfNotNull(it.competition?.reward) } +
            state.results.mapNotNull { it.performance?.reward } + listOfNotNull(state.competition?.reward)
    }
    fun choose(contract: RewardContract?, tiers: List<String>, state: DeckState, history: List<Report>, now: ClockNow, productiveMs: Long = 0): RewardReceipt {
        val strategy = requireNotNull(state.session).strategy
        val zone = ZoneId.of(state.recordedTimezone ?: ZoneId.systemDefault().id)
        val local = Instant.ofEpochMilli(now.wall).atZone(zone)
        val ledger = receipts(state, history).filter { it.acknowledged && !it.skipped && it.acknowledgedAtMs != null }.sortedBy { it.acknowledgedAtMs }
        val today = ledger.filter { Instant.ofEpochMilli(it.acknowledgedAtMs!!).atZone(zone).toLocalDate() == local.toLocalDate() }
        val reps = mapOf("pushups" to today.sumOf { r -> r.actions.filter { it.kind == "pushups" }.sumOf { it.reps } },
            "squats" to today.sumOf { r -> r.actions.filter { it.kind == "squats" }.sumOf { it.reps } })
        val seconds = today.sumOf { r -> r.actions.filter { it.kind in physicalKinds }.sumOf { it.seconds } }
        val lastCoffee = ledger.lastOrNull { r -> r.actions.any { it.kind == "coffee" } }?.acknowledgedAtMs
        fun eligible(actions: List<RewardAction>): Boolean = actions.all { a ->
            a.kind == "text" || (a.kind in strategy.allowedRewards && a.kind !in strategy.forbiddenRewards &&
                (a.kind !in reps || reps.getValue(a.kind) + a.reps <= strategy.dailyRepLimits.getValue(a.kind)) &&
                (a.kind !in physicalKinds || today.takeLast(strategy.maxConsecutiveSameActivity).let { tail -> tail.size < strategy.maxConsecutiveSameActivity || tail.any { r -> r.actions.none { it.kind == a.kind } } }) &&
                (a.kind != "coffee" || (strategy.coffeeReward.enabled && productiveMs >= strategy.coffeeReward.minimumProductiveSessionSeconds * 1000 &&
                    local.toLocalTime() < LocalTime.parse(strategy.coffeeReward.latestAllowedLocalTime) &&
                    (lastCoffee == null || now.wall - lastCoffee >= strategy.coffeeReward.minimumHoursBetweenCoffeeRewards * 3600000L))))
        } && (actions.none { it.kind in physicalKinds } || seconds + actions.filter { it.kind in physicalKinds }.sumOf { it.seconds } <= strategy.dailyPhysicalSeconds)
        tiers.forEach { tier -> contract?.option(tier)?.let { option ->
            (listOf(option.actions) + option.alternatives).firstOrNull { eligible(it) }?.let { actions ->
                return RewardReceipt(tier, actions, "Configured reward eligible", dailyRepsBefore = reps, dailyPhysicalSecondsBefore = seconds)
            }
        } }
        return RewardReceipt(reason = if (contract == null) "No reward contract" else "Configured rewards unavailable under daily volume, variety or coffee limits",
            dailyRepsBefore = reps, dailyPhysicalSecondsBefore = seconds)
    }
    fun checkReceipt(receipt: RewardReceipt, state: DeckState, history: List<Report>, now: ClockNow, productiveMs: Long = 0): RewardReceipt =
        choose(if (receipt.actions.isEmpty()) null else RewardContract(completion = RewardOption(receipt.actions)), listOf("completion"), state, history, now, productiveMs)
    fun evaluate(block: BlockResult, plan: Block, confirmed: Boolean, state: DeckState, history: List<Report>, now: ClockNow): Performance {
        val policy = requireNotNull(state.session).strategy.scoring
        if (plan.stopwatch) {
            val focus = focus(block.activeMs, block.pausedMs)
            val reward = plan.rewardPolicy?.let { ConditionalRewards.choose(it, null, null, focus, false, null, state, history, now) }
                ?: RewardReceipt(reason = "No reward assigned")
            return Performance(quality = "confirmed", qualityCriteria = plan.qualityCriteria, wallMs = block.wallMs ?: block.activeMs + block.pausedMs,
                focusRatio = focus, beatScore = null, rank = null, scoring = policy, reward = reward,
                rewardPolicy = plan.rewardPolicy, sessionWeight = plan.sessionWeight)
        }
        val pace = pace(block); val focus = focus(block.activeMs, block.pausedMs)
        val recalibrate = pace == null || pace > policy.recalibrationPace
        val beat = score(pace, focus, confirmed, policy)
        val candidates = history.filter { it.timelineCoverage == "complete" }.flatMap { r -> r.blocks.map { r.key to it } } +
            state.results.dropLast(1).map { "${state.session?.sessionId}:${state.startedAtMs}" to it }
        val prior = if (plan.comparison == null) null else candidates.filter { (_, b) ->
            b.type == "focus" && b.outcome == "done" && b.plannedMs == block.plannedMs && b.performance?.let { it.quality == "confirmed" && !it.recalibrationNeeded && it.comparison == plan.comparison && it.qualityCriteria == plan.qualityCriteria && it.scoring == policy } == true
        }.maxByOrNull { it.second.performance!!.beatScore!! }?.let { (key, b) -> Benchmark(key, b.id, b.performance!!.beatScore!!, b.activeMs) }
        val pb = confirmed && !recalibrate && state.timelineComplete && prior != null && beat > prior.score
        val tier = when {
            !confirmed -> "none"
            recalibrate || !state.timelineComplete -> "completion"
            pb -> "personal_best"
            (pace ?: 0.0) >= policy.strongPace && (focus ?: 0.0) >= policy.strongFocus -> "strong"
            (pace ?: 0.0) >= policy.targetPace && (focus ?: 0.0) >= policy.targetFocus -> "target"
            else -> "completion"
        }
        val tiers = when (tier) { "personal_best" -> listOf(tier, "strong", "target", "completion"); "strong" -> listOf(tier, "target", "completion"); "target" -> listOf(tier, "completion"); "completion" -> listOf(tier); else -> emptyList() }
        val legacy = Performance(if (confirmed) "confirmed" else "incomplete", plan.qualityCriteria, plan.comparison,
            if (state.session!!.protocolVersion == 2) block.wallMs ?: block.activeMs + block.pausedMs else (block.finishedAtMs - block.startedAtMs).coerceAtLeast(0), pace, focus, beat, policy.rank(beat), recalibrate, prior, pb,
            prior?.let { rounded(beat - it.score) }, policy,
            if (confirmed) choose(plan.rewardContract ?: state.session?.rewardStrategy?.defaultContract, tiers, state, history, now) else RewardReceipt(reason = "Completion quality not confirmed"), plan.rewardContract ?: state.session?.strategy?.defaultContract)
        val classification = plan.classification ?: requireNotNull(state.session).classification
        val result = legacy.copy(performanceClass = ConditionalRewards.classify(block, legacy, classification), sessionWeight = plan.sessionWeight,
            classification = classification, rewardPolicy = plan.rewardPolicy)
        return if (plan.rewardPolicy == null) result else result.copy(reward = ConditionalRewards.choose(plan.rewardPolicy, result.beatScore,
            if (result.recalibrationNeeded) null else result.paceIndex, result.focusRatio, result.newPersonalBest, null, state, history, now))
    }
    fun session(state: DeckState, history: List<Report>, now: ClockNow): SessionCompetition {
        val session = requireNotNull(state.session); val strategy = session.strategy
        val planned = session.blocks.count { it.type == "focus" }
        val focusBlocks = state.results.filter { it.type == "focus" }
        val completed = focusBlocks.count { it.performance?.quality == "confirmed" }
        val onTarget = focusBlocks.count { it.performance?.performanceClass in setOf("strong", "on_target") }
        val averagePace = focusBlocks.mapNotNull { it.performance?.paceIndex }.takeIf { it.isNotEmpty() }?.average()?.let { rounded(it) }
        // DONE blocks alone contribute, with GPT weights. No incomplete penalty.
        val scored = focusBlocks.filter { it.timerMode == "countdown" && it.outcome == "done" && it.performance != null }
        val totalWeight = scored.sumOf { it.performance!!.sessionWeight }
        val untimed = session.blocks.any { it.stopwatch }
        val scope = if (!untimed) "all_focus" else if (session.blocks.any { it.type == "focus" && !it.stopwatch }) "countdown_only" else "none"
        val beat = if (totalWeight == 0.0) if (untimed) null else 0.0 else rounded(scored.sumOf { it.performance!!.beatScore!! * it.performance!!.sessionWeight } / totalWeight)
        val focus = focus(focusBlocks.sumOf { it.activeMs }, focusBlocks.sumOf { it.pausedMs })
        val signatures = focusBlocks.map { Triple(it.plannedMs, it.performance?.comparison, it.performance?.qualityCriteria) }
        val fullyDone = state.results.size == session.blocks.size && state.results.all { it.outcome == "done" }
        val prior = if (!fullyDone || untimed || session.comparisonKey == null || signatures.any { it.second == null } || completed != planned || planned == 0) null else history.filter { r ->
            r.status == "complete" && r.timelineCoverage == "complete" && r.competition?.let { c ->
                c.comparisonKey == session.comparisonKey && c.rewardStrategy.scoring == strategy.scoring && c.completedFocusBlocks == c.plannedFocusBlocks && c.weighted?.scoring == session.sessionScoring &&
                    r.blocks.filter { it.type == "focus" }.map { it.performance?.sessionWeight } == focusBlocks.map { it.performance?.sessionWeight } &&
                    r.blocks.filter { it.type == "focus" }.map { Triple(it.plannedMs, it.performance?.comparison, it.performance?.qualityCriteria) } == signatures &&
                    r.blocks.filter { it.type == "focus" }.all { it.performance?.recalibrationNeeded == false }
            } == true
        }.maxByOrNull { it.competition!!.beatScore!! }?.let { Benchmark(it.key, score = it.competition!!.beatScore!!, activeMs = it.focusedMs) }
        val eligible = completed == planned && planned > 0 && fullyDone
        val normal = state.timelineComplete && focusBlocks.all { it.performance?.recalibrationNeeded == false }
        val tiers = when { !eligible -> emptyList(); normal && beat != null && beat >= strategy.scoring.rankThresholds[3] -> listOf("exceptional", "strong", "completion"); normal && beat != null && beat >= strategy.scoring.rankThresholds[2] -> listOf("strong", "completion"); else -> listOf("completion") }
        val weighted = ConditionalRewards.weighted(state.results, session.sessionScoring, untimed)
        val legacy = SessionCompetition(session.comparisonKey, planned, completed, onTarget, if (planned == 0) 0.0 else rounded(completed.toDouble() / planned), averagePace, focus,
            beat, beat?.let { strategy.scoring.rank(it) }, prior, eligible && normal && prior != null && beat != null && beat > prior.score, prior?.let { rounded(beat!! - it.score) }, strategy, session.sessionReward,
            choose(session.sessionReward, tiers, state, history, now, focusBlocks.filter { it.performance?.quality == "confirmed" }.sumOf { it.activeMs }))
        val reward = session.sessionRewardPolicy?.let { policy ->
            if (!fullyDone) RewardReceipt(reason = "Session ended with skipped or incomplete blocks") else ConditionalRewards.choose(policy, beat, averagePace, focus,
                legacy.newPersonalBest, weighted, state, history, now)
        } ?: legacy.reward
        return legacy.copy(weighted = weighted, sessionRewardPolicy = session.sessionRewardPolicy, reward = reward, scoreScope = scope)
    }
    fun acknowledge(receipt: RewardReceipt, now: ClockNow, zone: String?, skip: Boolean): RewardReceipt = receipt.copy(
        acknowledged = !skip && receipt.actions.isNotEmpty(), skipped = false,
        acknowledgedAtMs = now.wall, localDay = Instant.ofEpochMilli(now.wall).atZone(ZoneId.of(zone ?: ZoneId.systemDefault().id)).toLocalDate().toString())
    fun summary(report: Report): String = buildString {
        report.blocks.forEach { b -> b.performance?.let { p ->
            appendLine("${b.id}: DONE declaration=${p.quality}; class=${p.performanceClass ?: "N/A"}; weight=${p.sessionWeight}; wall=${p.wallMs}ms; pace=${p.paceIndex ?: "N/A"}; focus=${p.focusRatio ?: "N/A"}%; BeatScore=${p.beatScore ?: "N/A"} ${p.rank.orEmpty()}; recalibration=${p.recalibrationNeeded}")
            if (b.timerMode != "stopwatch") appendLine("Comparison: ${p.comparison}; benchmark=${p.previousBenchmark ?: "NO BENCHMARK YET"}; PB=${p.newPersonalBest}; delta=${p.pbDelta}")
            appendLine("Reward: ${p.reward.tier} · ${p.reward.label()} · acknowledged=${p.reward.acknowledged}, at=${p.reward.acknowledgedAtMs}, local day=${p.reward.localDay}; ${p.reward.reason}")
            appendLine("Performance JSON: ${WireJson.encodeToString(p)}")
        } }
        report.competition?.let { c ->
            appendLine("${if (c.scoreScope == "countdown_only") "Countdown-only scoring; overall completion" else if (c.scoreScope == "none") "Untimed completion (scores N/A)" else "Competition"}: ${c.completedFocusBlocks}/${c.plannedFocusBlocks} DONE focus blocks; ${c.onOrAheadCount} on/ahead; average pace=${c.averagePace}; focus=${c.focusRatio}%; BeatScore=${c.beatScore ?: "N/A"} ${c.rank.orEmpty()}")
            if (c.scoreScope == "all_focus") appendLine("Session benchmark=${c.previousBenchmark ?: "NO BENCHMARK YET"}; PB=${c.newPersonalBest}; delta=${c.pbDelta}")
            appendLine("Session reward: ${c.reward.label()} · acknowledged=${c.reward.acknowledged}, at=${c.reward.acknowledgedAtMs}, day=${c.reward.localDay}")
            appendLine("Weighted result: ${c.weighted}")
            appendLine("Session competition JSON: ${WireJson.encodeToString(c)}")
            appendLine("Acknowledgement is self-reported reward activity, not verified exercise or caffeine intake.")
        }
    }
}

/** Imported history may inform PBs, so scores are checked against the raw timer data. */
fun Report.validateRewards() {
    fun reliablePrefix(blockId: String? = null): Boolean {
        val index = if (blockId == null) blocks.lastIndex else blocks.indexOfFirst { it.id == blockId }
        val end = events.indexOfLast { it.blockIndex == index && it.kind == "block_done" }
        if (end < 0) return false
        val prefix = events.take(end + 1)
        return prefix.map { it.boot }.distinct().size == 1 && prefix.none { it.kind in setOf("recovered_after_reboot", "review_recovered_after_reboot") }
    }
    fun close(value: Double?, expected: Double?) {
        require(if (expected == null) value == null else value != null && value.isFinite() && kotlin.math.abs(value - expected) <= .011) { "Reward metric differs from timer evidence" }
    }
    fun receipt(r: RewardReceipt, session: Boolean, contract: RewardContract?, policy: ConditionalPolicy? = null) {
        require(r.tier in setOf("none", "completion", "target", "strong", "personal_best", "exceptional") || r.tier.matches(Regex("tier_[1-9][0-9]?")))
        if (r.actions.isNotEmpty()) {
            RewardOption(r.actions).validate(session)
            val option = contract?.option(r.tier)
            val tier = r.tier.removePrefix("tier_").toIntOrNull()?.let { policy?.tiers?.getOrNull(it - 1) }
            val supplied = if (policy != null) policy.enabled && tier != null && r.actions in (listOf(tier.reward) + tier.alternatives).map { it.typed(session) }
                else option != null && r.actions in listOf(option.actions) + option.alternatives
            require(supplied) { "Earned reward was not supplied by planner" }
        } else require(r.tier == "none")
        require(!r.acknowledged || (!r.skipped && r.actions.isNotEmpty()))
        require(r.reason.isNotBlank() && r.reason.length <= 250)
        require(r.dailyRepsBefore.keys.all { it in setOf("pushups", "squats") } && r.dailyRepsBefore.values.all { it in 0..1_000_000_000 })
        require(r.dailyPhysicalSecondsBefore in 0..1_000_000_000)
        if (r.acknowledgedAtMs != null) {
            require(r.acknowledgedAtMs in 0..253_402_300_799_999L && r.localDay != null && r.localDay.length == 10)
            java.time.LocalDate.parse(r.localDay)
        } else require(r.localDay == null && !r.acknowledged && !r.skipped)
    }
    fun benchmark(b: Benchmark?, score: Double, delta: Double?, pb: Boolean, qualified: Boolean) {
        if (b != null) {
            require(b.reportKey.isNotBlank() && b.reportKey.length <= 250 && b.score.isFinite() && b.score in 0.0..111.0 && b.activeMs in 1..1_000_000_000_000L)
            require(b.blockId == null || (b.blockId.isNotBlank() && b.blockId.length <= 100))
        }
        close(delta, b?.let { RewardEngine.rounded(score - it.score) })
        require(!pb || qualified && b != null && score > b.score)
    }
    blocks.forEachIndexed { index, b -> b.performance?.let { p ->
        if (b.timerMode == "stopwatch") {
            p.scoring.validate()
            require(reportVersion == 2 && b.type == "focus" && b.outcome == "done" && p.quality == "confirmed") { "Invalid stopwatch completion evidence" }
            require(p.paceIndex == null && p.beatScore == null && p.rank == null && p.performanceClass == null && p.classification == null && p.comparison == null && p.rewardContract == null && !p.recalibrationNeeded && p.previousBenchmark == null && !p.newPersonalBest && p.pbDelta == null) { "Stopwatch cannot contain timed scores/benchmarks" }
            require(p.wallMs == b.activeMs + b.pausedMs && p.qualityCriteria.size <= 10 && p.qualityCriteria.all { it.isNotBlank() && it.length <= 200 })
            close(p.focusRatio, RewardEngine.focus(b.activeMs, b.pausedMs))
            p.rewardPolicy?.let { policy ->
                policy.validate(false)
                require(policy.tiers.all { it.condition in setOf("completed", "module_completed") && it.minBeatScore == null && it.maxBeatScore == null && it.minPaceIndex == null && it.minSessionScore == null && it.maxSessionScore == null }) { "Invalid stopwatch reward condition" }
            }
            require(p.sessionWeight.isFinite() && p.sessionWeight in 0.01..10.0)
            require(p.rewardPolicy?.tiers?.all { tier -> tier.blockIds.all { id -> blocks.take(index + 1).any { it.id == id && it.type == "focus" } } } != false) { "Invalid milestone references" }
            receipt(p.reward, false, null, p.rewardPolicy)
            if (p.reward.actions.isNotEmpty()) require(ConditionalRewards.eligible(requireNotNull(p.rewardPolicy), null, null, p.focusRatio, false, null, CompletionContext.from(this, index)).any { "tier_${it.index + 1}" == p.reward.tier }) { "Stopwatch reward condition not met" }
            return@forEachIndexed
        }
        require(p.quality in setOf("pending", "confirmed", "incomplete") && b.type == "focus" && b.outcome == "done")
        require(p.quality != "pending" || status == "stopped")
        require(p.qualityCriteria.size <= 10 && p.qualityCriteria.all { it.isNotBlank() && it.length <= 200 })
        p.comparison?.validate(); p.scoring.validate(); p.rewardContract?.validate(false)
        require(p.wallMs == if (reportVersion == 2) b.activeMs + b.pausedMs else (b.finishedAtMs - b.startedAtMs).coerceAtLeast(0))
        val pending = p.quality == "pending"
        val pace = RewardEngine.pace(b); val focus = RewardEngine.focus(b.activeMs, b.pausedMs)
        close(p.paceIndex, if (pending) null else pace); close(p.focusRatio, if (pending) null else focus)
        val score = RewardEngine.score(pace, focus, p.quality == "confirmed", p.scoring)
        close(p.beatScore, score); require(p.rank == p.scoring.rank(score))
        require(pending || p.recalibrationNeeded == (pace == null || pace > p.scoring.recalibrationPace))
        require(p.previousBenchmark == null || p.comparison != null)
        benchmark(p.previousBenchmark, score, p.pbDelta, p.newPersonalBest, p.quality == "confirmed" && !p.recalibrationNeeded && (timelineCoverage == "complete" || reliablePrefix(b.id)))
        receipt(p.reward, false, p.rewardContract, p.rewardPolicy)
        p.rewardPolicy?.let { policy ->
            policy.validate(false)
            if (reportVersion == 1) require(policy.tiers.all { it.condition in setOf(null, "personal_best") && it.blockIds.isEmpty() && it.minCompletedBlocks == null }) { "Completion conditions require report version 2" }
            require(policy.tiers.all { tier -> tier.blockIds.all { id -> blocks.take(index + 1).any { it.id == id && it.type == "focus" } } }) { "Invalid milestone references" }
            if (p.reward.actions.isNotEmpty()) require(ConditionalRewards.eligible(policy, p.beatScore, if (p.recalibrationNeeded) null else p.paceIndex, p.focusRatio, p.newPersonalBest, null, CompletionContext.from(this, index)).any { "tier_${it.index + 1}" == p.reward.tier }) { "Reward threshold not satisfied" }
        }
        require(p.sessionWeight.isFinite() && p.sessionWeight in 0.01..10.0)
        if (p.performanceClass != null) {
            val policy = requireNotNull(p.classification); policy.validate()
            require(p.quality == "confirmed" && p.performanceClass == ConditionalRewards.classify(b, p, policy))
        }
        require(p.quality == "confirmed" || p.reward.actions.isEmpty())
    } }
    val c = competition
    require(c != null || blocks.none { it.performance != null })
    c?.let {
        c.rewardStrategy.validate(); c.sessionReward?.validate(true)
        (blocks.mapNotNull { it.performance?.rewardContract } + listOfNotNull(c.sessionReward)).forEach { contract ->
            listOfNotNull(contract.completion, contract.target, contract.strong, contract.personalBest, contract.exceptional).forEach { option ->
                (listOf(option.actions) + option.alternatives).flatten().forEach { action -> require(action.kind in c.rewardStrategy.allowedRewards && action.kind !in c.rewardStrategy.forbiddenRewards) }
            }
        }
        require(c.comparisonKey == null || (c.comparisonKey.isNotBlank() && c.comparisonKey.length <= 100))
        val focused = blocks.filter { b -> b.type == "focus" }
        require(c.plannedFocusBlocks in focused.size..100)
        val completed = focused.count { b -> b.performance?.quality == "confirmed" }
        require(c.completedFocusBlocks == completed && c.onOrAheadCount == focused.count { b -> if (c.weighted != null) b.performance?.performanceClass in setOf("strong", "on_target") else b.performance?.quality == "confirmed" && b.plannedMs != null && b.activeMs <= b.plannedMs })
        close(c.completionFraction, if (c.plannedFocusBlocks == 0) 0.0 else RewardEngine.rounded(completed.toDouble() / c.plannedFocusBlocks))
        close(c.averagePace, focused.mapNotNull { b -> b.performance?.paceIndex }.takeIf { v -> v.isNotEmpty() }?.average()?.let { v -> RewardEngine.rounded(v) })
        close(c.focusRatio, RewardEngine.focus(focused.sumOf { b -> b.activeMs }, focused.sumOf { b -> b.pausedMs }))
        val score = if (c.weighted == null) { if (c.plannedFocusBlocks == 0) 0.0 else RewardEngine.rounded(focused.sumOf { b -> b.performance?.beatScore ?: 0.0 } / c.plannedFocusBlocks) }
            else { val scored = focused.filter { b -> b.timerMode == "countdown" && b.outcome == "done" && b.performance != null }; val weight = scored.sumOf { b -> b.performance!!.sessionWeight }
                if (weight == 0.0) if ((plannedStopwatchFocusBlocks ?: 0) > 0) null else 0.0 else RewardEngine.rounded(scored.sumOf { b -> b.performance!!.beatScore!! * b.performance!!.sessionWeight } / weight) }
        close(c.beatScore, score); require(c.rank == score?.let { c.rewardStrategy.scoring.rank(it) })
        val scope = if ((plannedStopwatchFocusBlocks ?: 0) == 0) "all_focus" else if ((plannedCountdownFocusBlocks ?: 0) > 0) "countdown_only" else "none"
        require(c.scoreScope == scope) { "Invalid score scope" }
        require(c.previousBenchmark == null || c.comparisonKey != null)
        if (score == null || c.scoreScope != "all_focus") require(c.previousBenchmark == null && !c.newPersonalBest && c.pbDelta == null) { "Untimed/mixed session cannot have a whole-session PB" }
        else benchmark(c.previousBenchmark, score, c.pbDelta, c.newPersonalBest, c.plannedFocusBlocks > 0 && completed == c.plannedFocusBlocks && (timelineCoverage == "complete" || reliablePrefix()))
        receipt(c.reward, true, c.sessionReward, c.sessionRewardPolicy)
        c.sessionRewardPolicy?.let { policy ->
            policy.validate(true)
            if (reportVersion == 2 && status == "complete") require(policy.tiers.all { tier -> tier.blockIds.all { id -> blocks.any { it.id == id && it.type == "focus" } } }) { "Session milestone references an unknown/non-focus block" }
            if (reportVersion == 1) require(policy.tiers.all { it.condition in setOf(null, "personal_best") && it.blockIds.isEmpty() && it.minCompletedBlocks == null } && policy.eligibility.minimumCompletedBlocks == 0 && !policy.eligibility.requireAllFocusCompleted) { "Completion conditions require report version 2" }
            if (c.scoreScope == "none") require(policy.tiers.all { it.minBeatScore == null && it.maxBeatScore == null && it.minSessionScore == null && it.maxSessionScore == null && it.condition != "personal_best" } && policy.eligibility.minimumBeatScore == 0.0 && policy.eligibility.minimumSessionScore == 0.0 && policy.eligibility.minimumStrongBlocks == 0 && policy.eligibility.minimumOnOrAboveTargetBlocks == 0 && policy.eligibility.maximumOverTargetBlocks == 100 && policy.eligibility.minimumAverageFocusRatio == 0.0) { "Untimed session cannot require countdown scoring" }
            require(c.weighted != null) { "Conditional session reward requires weighted metrics" }
            if (c.reward.actions.isNotEmpty()) require(ConditionalRewards.eligible(policy, c.beatScore, c.averagePace, c.focusRatio, c.newPersonalBest, c.weighted, CompletionContext.from(this)).any { "tier_${it.index + 1}" == c.reward.tier }) { "Session reward eligibility not satisfied" }
        }
        (blocks.mapNotNull { b -> b.performance?.rewardPolicy?.let { it to false } } + listOfNotNull(c.sessionRewardPolicy?.let { it to true })).forEach { (policy, session) ->
            policy.tiers.forEach { tier -> (listOf(tier.reward) + tier.alternatives).flatMap { it.typed(session) }.forEach { a -> require(a.kind == "text" || (a.kind in c.rewardStrategy.allowedRewards && a.kind !in c.rewardStrategy.forbiddenRewards)) } }
        }
        c.weighted?.let { w -> require(blocks.filter { b -> b.timerMode == "countdown" && b.type == "focus" && b.outcome == "done" }.all { b -> b.performance?.performanceClass != null }); w.scoring.validate(); require(w == ConditionalRewards.weighted(blocks, w.scoring, (plannedStopwatchFocusBlocks ?: 0) > 0)) { "Weighted score differs from block evidence" } }
        if (blocks.any { it.outcome == "skipped" }) require(c.reward.actions.isEmpty() && !c.newPersonalBest && c.previousBenchmark == null && c.pbDelta == null) { "Skipped session cannot earn a session reward or whole-session PB" }
        require(c.reward.actions.isEmpty() || c.plannedFocusBlocks > 0 && completed == c.plannedFocusBlocks)
    }
}
