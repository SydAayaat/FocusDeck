package com.hax.focusdeck

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable data class ConditionalReward(val type: String = "text", val amount: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val actions: List<ConditionalReward> = emptyList(), val text: String? = null) {
    fun typed(session: Boolean): List<RewardAction> {
        if (type == "text") {
            require(text != null && text.isNotBlank() && text.codePointCount(0, text.length) <= 1000)
            require(amount == null && durationSeconds == null && durationMinutes == null && actions.isEmpty())
            return listOf(RewardAction("text", text = text))
        }
        require(text == null) { "Use free text or a legacy activity, not both" }
        if (type == "combined") {
            require(amount == null && durationSeconds == null && durationMinutes == null && actions.size in 1..3 && actions.none { it.type == "combined" })
            return actions.flatMap { it.typed(session) }.also { RewardOption(it).validate(session) }
        }
        require(actions.isEmpty() && !(durationSeconds != null && durationMinutes != null))
        require(type in physicalKinds || (session && type in setOf("guitar", "coffee")))
        val reps = amount ?: 0
        val seconds = durationSeconds ?: durationMinutes?.let { require(it in 1..15); it * 60 } ?: if (type in setOf("pushups", "squats")) 60 else 0
        return listOf(RewardAction(type, reps, seconds)).also { RewardOption(it).validate(session) }
    }
}
@Serializable data class RewardTier(@SerialName("min_beat_score") val minBeatScore: Double? = null,
    @SerialName("min_session_score") val minSessionScore: Double? = null,
    @SerialName("min_focus_ratio") val minFocusRatio: Double? = null,
    @SerialName("min_pace_index") val minPaceIndex: Double? = null,
    val condition: String? = null, val reward: ConditionalReward,
    val alternatives: List<ConditionalReward> = emptyList(),
    @SerialName("max_beat_score") val maxBeatScore: Double? = null,
    @SerialName("max_session_score") val maxSessionScore: Double? = null,
    @SerialName("block_ids") val blockIds: List<String> = emptyList(),
    @SerialName("min_completed_blocks") val minCompletedBlocks: Int? = null) {
    fun validate(session: Boolean) {
        require(maxBeatScore == null || maxBeatScore.isFinite() && maxBeatScore in 0.0..111.0 && (minBeatScore == null || maxBeatScore >= minBeatScore))
        require(maxSessionScore == null || session && maxSessionScore.isFinite() && maxSessionScore in 0.0..100.0 && (minSessionScore == null || maxSessionScore >= minSessionScore))
        require(minBeatScore == null || minBeatScore.isFinite() && minBeatScore in 0.0..111.0)
        require(minSessionScore == null || session && minSessionScore.isFinite() && minSessionScore in 0.0..100.0)
        require(minFocusRatio == null || minFocusRatio.isFinite() && minFocusRatio in 0.0..100.0)
        require(minPaceIndex == null || !session && minPaceIndex.isFinite() && minPaceIndex in 0.0..120.0)
        require(condition in setOf(null, "personal_best", "completed", "module_completed", "session_completed")) { "Unsupported reward condition" }
        require(condition != "session_completed" || session) { "session_completed is a session reward condition" }
        require(minCompletedBlocks == null || session && minCompletedBlocks in 1..100) { "min_completed_blocks applies to session rewards" }
        require(if (condition == "module_completed") blockIds.size in 1..100 && blockIds.distinct().size == blockIds.size && blockIds.all { it.isNotBlank() && it.length <= 100 } else blockIds.isEmpty()) { "module_completed requires unique block_ids; other conditions cannot have block_ids" }
        reward.typed(session); require(alternatives.size <= 4); alternatives.forEach { it.typed(session) }
    }
}
@Serializable data class RewardEligibility(
    @SerialName("minimum_strong_blocks") val minimumStrongBlocks: Int = 0,
    @SerialName("minimum_on_or_above_target_blocks") val minimumOnOrAboveTargetBlocks: Int = 0,
    @SerialName("maximum_over_target_blocks") val maximumOverTargetBlocks: Int = 100,
    @SerialName("minimum_focus_ratio") val minimumFocusRatio: Double = 0.0,
    @SerialName("minimum_average_focus_ratio") val minimumAverageFocusRatio: Double = 0.0,
    @SerialName("minimum_beat_score") val minimumBeatScore: Double = 0.0,
    @SerialName("minimum_session_score") val minimumSessionScore: Double = 0.0,
    @SerialName("minimum_completed_blocks") val minimumCompletedBlocks: Int = 0,
    @SerialName("require_all_focus_completed") val requireAllFocusCompleted: Boolean = false) {
    fun validate() {
        require(minimumStrongBlocks in 0..100 && minimumOnOrAboveTargetBlocks in 0..100 && maximumOverTargetBlocks in 0..100)
        require(listOf(minimumFocusRatio, minimumAverageFocusRatio, minimumSessionScore).all { it.isFinite() && it in 0.0..100.0 })
        require(minimumBeatScore.isFinite() && minimumBeatScore in 0.0..111.0)
        require(minimumCompletedBlocks in 0..100)
    }
}
@Serializable data class ConditionalPolicy(val enabled: Boolean, val tiers: List<RewardTier> = emptyList(),
    val selection: String = "highest_satisfied", val eligibility: RewardEligibility = RewardEligibility()) {
    fun validate(session: Boolean) {
        require(tiers.size <= 20 && (!enabled || tiers.isNotEmpty()))
        require(selection in setOf("highest_satisfied", "first_satisfied")); eligibility.validate()
        require(session || eligibility == RewardEligibility())
        tiers.forEach { it.validate(session) }
    }
}
@Serializable data class ClassificationPolicy(
    @SerialName("strong_max_actual_ratio") val strongMaxActualRatio: Double = 0.90,
    @SerialName("on_target_max_actual_ratio") val onTargetMaxActualRatio: Double = 1.05,
    @SerialName("slightly_over_max_actual_ratio") val slightlyOverMaxActualRatio: Double = 1.25,
    @SerialName("strong_min_focus_ratio") val strongMinFocusRatio: Double = 80.0) {
    fun validate() {
        require(strongMaxActualRatio.isFinite() && strongMaxActualRatio in .5..1.0)
        require(onTargetMaxActualRatio.isFinite() && onTargetMaxActualRatio in 1.0..1.2)
        require(slightlyOverMaxActualRatio.isFinite() && slightlyOverMaxActualRatio in onTargetMaxActualRatio..2.0)
        require(strongMinFocusRatio.isFinite() && strongMinFocusRatio in 0.0..100.0)
    }
}
@Serializable data class SessionScoring(val coefficients: Map<String, Double> = mapOf("strong" to 1.0, "on_target" to .75, "slightly_over" to .20, "over_target" to -.40)) {
    fun validate() {
        require(coefficients.keys == setOf("strong", "on_target", "slightly_over", "over_target"))
        require(coefficients.values.all { it.isFinite() && it in -1.0..1.0 })
        val values = listOf("strong", "on_target", "slightly_over", "over_target").map { coefficients.getValue(it) }
        require(values.first() > 0 && values.zipWithNext().all { it.first >= it.second })
    }
}
@Serializable data class WeightedSession(@SerialName("raw_weighted_points") val rawWeightedPoints: Double,
    @SerialName("total_weight") val totalWeight: Double,
    @SerialName("normalized_session_score") val normalizedSessionScore: Double?,
    @SerialName("class_counts") val classCounts: Map<String, Int>,
    @SerialName("average_focus_ratio") val averageFocusRatio: Double?, val scoring: SessionScoring)
val Session.competitive: Boolean get() = protocolVersion == 2 || rewardStrategy != null || sessionRewardPolicy != null || blocks.any { it.rewardPolicy != null || it.sessionWeight != 1.0 || it.classification != null } || classification != ClassificationPolicy() || sessionScoring != SessionScoring()
val Session.strategy: RewardStrategy get() = rewardStrategy ?: RewardStrategy()

object ConditionalRewards {
    fun classify(block: BlockResult, p: Performance, policy: ClassificationPolicy): String {
        require(block.plannedMs != null) { "Stopwatch has no duration classification" }
        val ratio = if (block.plannedMs == 0L) Double.POSITIVE_INFINITY else block.activeMs.toDouble() / block.plannedMs
        return when {
            ratio <= policy.strongMaxActualRatio && (p.focusRatio ?: 0.0) >= policy.strongMinFocusRatio && !p.recalibrationNeeded -> "strong"
            ratio <= policy.onTargetMaxActualRatio -> "on_target"
            ratio <= policy.slightlyOverMaxActualRatio -> "slightly_over"
            else -> "over_target"
        }
    }
    fun weighted(results: List<BlockResult>, scoring: SessionScoring, untimed: Boolean = false): WeightedSession {
        val blocks = results.filter { it.timerMode == "countdown" && it.type == "focus" && it.outcome == "done" && it.performance?.performanceClass != null }
        val weight = blocks.sumOf { it.performance!!.sessionWeight }
        val points = blocks.sumOf { it.performance!!.sessionWeight * scoring.coefficients.getValue(it.performance!!.performanceClass!!) }
        val counts = scoring.coefficients.keys.associateWith { key -> blocks.count { it.performance!!.performanceClass == key } }
        val focus = blocks.mapNotNull { it.performance!!.focusRatio }.takeIf { it.isNotEmpty() }?.average()?.let { RewardEngine.rounded(it) }
        return WeightedSession(points, weight,
            if (weight == 0.0) if (untimed) null else 0.0 else RewardEngine.rounded((points / (weight * scoring.coefficients.getValue("strong")) * 100).coerceIn(0.0, 100.0)), counts, focus, scoring)
    }
    fun eligible(policy: ConditionalPolicy, beat: Double?, pace: Double?, focus: Double?, pb: Boolean, weighted: WeightedSession?,
        context: CompletionContext = CompletionContext()): List<IndexedValue<RewardTier>> {
        if (!policy.enabled) return emptyList()
        val e = policy.eligibility
        fun min(value: Double?, threshold: Double?) = threshold == null || value != null && value >= threshold
        fun max(value: Double?, threshold: Double?) = threshold == null || value != null && value <= threshold
        // Preserve the original countdown zero-threshold rule without fabricating stopwatch metrics.
        val tierFocus = focus ?: if (beat != null) 0.0 else null
        val tierPace = pace ?: if (beat != null) 0.0 else null
        if (weighted != null && ((weighted.classCounts["strong"] ?: 0) < e.minimumStrongBlocks ||
                (weighted.classCounts["strong"] ?: 0) + (weighted.classCounts["on_target"] ?: 0) < e.minimumOnOrAboveTargetBlocks ||
                (weighted.classCounts["over_target"] ?: 0) > e.maximumOverTargetBlocks ||
                (focus ?: 0.0) < e.minimumFocusRatio || (weighted.averageFocusRatio ?: 0.0) < e.minimumAverageFocusRatio ||
                e.minimumBeatScore > 0 && !min(beat, e.minimumBeatScore) ||
                e.minimumSessionScore > 0 && !min(weighted.normalizedSessionScore, e.minimumSessionScore) ||
                context.completedIds.size < e.minimumCompletedBlocks || e.requireAllFocusCompleted && !context.allFocusCompleted)) return emptyList()
        val satisfied = policy.tiers.withIndex().filter { (_, t) ->
            min(beat, t.minBeatScore) && max(beat, t.maxBeatScore) &&
                min(weighted?.normalizedSessionScore, t.minSessionScore) && max(weighted?.normalizedSessionScore, t.maxSessionScore) &&
                min(tierFocus, t.minFocusRatio) && min(tierPace, t.minPaceIndex) &&
                (t.minCompletedBlocks == null || context.completedIds.size >= t.minCompletedBlocks) && when (t.condition) {
                    "personal_best" -> pb
                    "completed" -> context.completed
                    "module_completed" -> context.completed && context.completedIds.containsAll(t.blockIds)
                    "session_completed" -> context.sessionCompleted
                    else -> true
                }
        }
        return if (policy.selection == "first_satisfied") satisfied else satisfied.sortedWith(compareByDescending<IndexedValue<RewardTier>> { it.value.condition == "personal_best" }
            .thenByDescending { if (weighted != null) it.value.minSessionScore ?: 0.0 else it.value.minBeatScore ?: 0.0 }
            .thenByDescending { it.value.minBeatScore ?: 0.0 }
            .thenByDescending { if (beat == null || it.value.condition in setOf("completed", "module_completed", "session_completed")) it.value.minFocusRatio ?: 0.0 else 0.0 }
            .thenByDescending { it.value.minCompletedBlocks ?: 0 }.thenByDescending { it.index })
    }
    fun choose(policy: ConditionalPolicy, beat: Double?, pace: Double?, focus: Double?, pb: Boolean,
        weighted: WeightedSession?, state: DeckState, history: List<Report>, now: ClockNow): RewardReceipt {
        if (!policy.enabled) return RewardReceipt(reason = "No reward assigned")
        val session = weighted != null
        val ordered = eligible(policy, beat, pace, focus, pb, weighted, CompletionContext.from(state))
        ordered.forEach { (i, tier) ->
            val option = RewardOption(tier.reward.typed(session), tier.alternatives.map { it.typed(session) })
            val receipt = RewardEngine.choose(RewardContract(completion = option), listOf("completion"), state, history, now,
                state.results.filter { it.type == "focus" && it.outcome == "done" }.sumOf { it.activeMs })
            if (receipt.actions.isNotEmpty()) return receipt.copy(tier = "tier_${i + 1}")
        }
        return RewardReceipt(reason = "No reward unlocked")
    }
}
