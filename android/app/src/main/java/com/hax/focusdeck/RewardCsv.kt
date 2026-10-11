package com.hax.focusdeck

import kotlinx.serialization.encodeToString
import java.time.Instant

object RewardCsv {
    val columns = listOf("quality", "block_wall_ms", "pace_index", "focus_ratio", "beat_score", "rank", "recalibration_needed",
        "comparison_json", "quality_criteria_json", "scoring_policy_json", "reward_contract_json", "reward_tier", "reward_actions_json", "reward_label",
        "reward_acknowledged", "reward_skipped", "reward_acknowledged_at_utc", "reward_local_day", "daily_reps_before_json", "daily_physical_seconds_before",
        "previous_benchmark_json", "new_personal_best", "pb_delta", "performance_class", "session_weight", "classification_json", "reward_policy_json")
    val sessionColumns = listOf("planned_focus_blocks", "completed_focus_blocks", "on_or_ahead_count", "completion_fraction", "average_pace", "session_comparison_key", "reward_strategy_json", "raw_weighted_points", "total_weight", "normalized_session_score", "strong_blocks", "on_target_blocks", "slightly_over_blocks", "over_target_blocks", "average_focus_ratio", "session_scoring_json", "session_reward_policy_json")
    private fun receipt(r: RewardReceipt): List<Any?> = listOf(r.tier, WireJson.encodeToString(r.actions), r.label(), r.acknowledged, r.skipped,
        r.acknowledgedAtMs?.let { Instant.ofEpochMilli(it).toString() }, r.localDay, WireJson.encodeToString(r.dailyRepsBefore), r.dailyPhysicalSecondsBefore)
    fun block(p: Performance?): List<Any?> = if (p == null) List(columns.size) { null } else
        listOf(p.quality, p.wallMs, p.paceIndex, p.focusRatio, p.beatScore, p.rank, p.recalibrationNeeded,
            p.comparison?.let { WireJson.encodeToString(it) }, WireJson.encodeToString(p.qualityCriteria), WireJson.encodeToString(p.scoring),
            p.rewardContract?.let { WireJson.encodeToString(it) }) + receipt(p.reward) + listOf(p.previousBenchmark?.let { WireJson.encodeToString(it) }, p.newPersonalBest, p.pbDelta, p.performanceClass, p.sessionWeight, p.classification?.let { WireJson.encodeToString(it) }, p.rewardPolicy?.let { WireJson.encodeToString(it) })
    fun session(c: SessionCompetition?): List<Any?> = if (c == null) List(columns.size + sessionColumns.size) { null } else
        listOf("session", null, c.averagePace, c.focusRatio, c.beatScore, c.rank, null, null, null, WireJson.encodeToString(c.rewardStrategy.scoring),
            c.sessionReward?.let { WireJson.encodeToString(it) }) + receipt(c.reward) + listOf(c.previousBenchmark?.let { WireJson.encodeToString(it) }, c.newPersonalBest, c.pbDelta, null, null, null, null,
            c.plannedFocusBlocks, c.completedFocusBlocks, c.onOrAheadCount, c.completionFraction, c.averagePace, c.comparisonKey, WireJson.encodeToString(c.rewardStrategy), c.weighted?.rawWeightedPoints, c.weighted?.totalWeight, c.weighted?.normalizedSessionScore,
            c.weighted?.classCounts?.get("strong"), c.weighted?.classCounts?.get("on_target"), c.weighted?.classCounts?.get("slightly_over"), c.weighted?.classCounts?.get("over_target"), c.weighted?.averageFocusRatio,
            c.weighted?.scoring?.let { WireJson.encodeToString(it) }, c.sessionRewardPolicy?.let { WireJson.encodeToString(it) })
}
