package com.hax.focusdeck

/** Conditions use DONE evidence, never an inferred time target. */
data class CompletionContext(val completed: Boolean = false, val completedIds: Set<String> = emptySet(),
    val allFocusCompleted: Boolean = false, val sessionCompleted: Boolean = false) {
    companion object {
        fun from(state: DeckState): CompletionContext {
            val session = requireNotNull(state.session)
            val ids = state.results.filter { it.type == "focus" && it.outcome == "done" }.map { it.id }.toSet()
            return CompletionContext(state.results.lastOrNull()?.outcome == "done", ids,
                session.blocks.filter { it.type == "focus" }.all { it.id in ids },
                state.results.size == session.blocks.size && state.results.all { it.outcome == "done" })
        }
        fun from(report: Report, index: Int? = null): CompletionContext {
            val results = if (index == null) report.blocks else report.blocks.take(index + 1)
            val ids = results.filter { it.type == "focus" && it.outcome == "done" }.map { it.id }.toSet()
            val planned = report.competition?.plannedFocusBlocks
            return CompletionContext(results.lastOrNull()?.outcome == "done", ids,
                planned != null && ids.size == planned,
                report.plannedBlockCount == report.blocks.size && report.blocks.all { it.outcome == "done" })
        }
    }
}

fun Session.validateCompletionPolicies() {
    val ids = blocks.filter { it.type == "focus" }.map { it.id }.toSet()
    fun validate(policy: ConditionalPolicy?, block: Block? = null) {
        policy ?: return
        policy.tiers.forEach { tier ->
            if (protocolVersion == 1) require(tier.condition in setOf(null, "personal_best") && tier.minCompletedBlocks == null && tier.blockIds.isEmpty()) { "Completion conditions require session protocol 2" }
            require(tier.blockIds.all { it in ids }) { "module_completed references an unknown/non-focus block ID" }
            if (block != null) {
                val prefix = blocks.take(blocks.indexOf(block) + 1).map { it.id }.toSet()
                require(tier.blockIds.all { it in prefix }) { "Milestone cannot reference future blocks" }
            }
            if (block?.stopwatch == true) {
                require(tier.condition in setOf("completed", "module_completed")) { "Stopwatch reward requires completed or module_completed" }
                require(tier.minBeatScore == null && tier.maxBeatScore == null && tier.minPaceIndex == null && tier.minSessionScore == null && tier.maxSessionScore == null) { "Stopwatch reward cannot use time-based scores" }
            }
        }
        if (protocolVersion == 1) require(policy.eligibility.minimumCompletedBlocks == 0 && !policy.eligibility.requireAllFocusCompleted) { "Completion eligibility requires protocol 2" }
        if (protocolVersion == 2 && block == null && blocks.none { it.type == "focus" && !it.stopwatch }) {
            require(policy.eligibility.minimumBeatScore == 0.0 && policy.eligibility.minimumSessionScore == 0.0 && policy.eligibility.minimumStrongBlocks == 0 && policy.eligibility.minimumOnOrAboveTargetBlocks == 0 && policy.eligibility.maximumOverTargetBlocks == 100 && policy.eligibility.minimumAverageFocusRatio == 0.0) { "Untimed session cannot require countdown scoring" }
            require(policy.tiers.all { it.minBeatScore == null && it.maxBeatScore == null && it.minSessionScore == null && it.maxSessionScore == null && it.condition != "personal_best" }) { "Untimed session reward cannot require countdown scoring" }
        }
    }
    blocks.forEach { block ->
        if (block.stopwatch) require(block.classification == null && block.comparison == null && block.rewardContract == null) { "Stopwatch supports completion reward_policy, not timed classification/comparison/legacy contracts" }
        validate(block.rewardPolicy, block)
    }
    validate(sessionRewardPolicy)
    if (blocks.any { it.stopwatch }) require(sessionReward == null && rewardStrategy?.defaultContract == null) { "Stopwatch sessions require explicit reward_policy; legacy default/session contracts are unsupported" }
}
