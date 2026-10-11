package com.hax.focusdeck

/** Pure deterministic state machine; no Android/UI/ticker dependency. */
class TimerEngine(var state: DeckState = DeckState(), private val history: List<Report> = emptyList()) {
    private fun event(kind: String, now: ClockNow) {
        if (state.events.size >= (if (state.session?.competitive == true) 1_000 else 2_000)) {
            state = state.copy(timelineComplete = false)
            return
        }
        state = state.copy(events = state.events + TimelineEvent(state.events.size + 1, kind,
            now.wall, now.mono, now.boot, state.index, state.phase,
            currentActive(now), currentPaused(now), state.budgetMs))
    }
    private fun delta(now: ClockNow): Long = (now.mono - state.anchorMono).coerceAtLeast(0)
    fun currentActive(now: ClockNow): Long = state.activeMs +
        if (state.phase == "active" && now.boot == state.boot) delta(now) else 0
    fun currentPaused(now: ClockNow): Long = state.pausedMs +
        if (state.phase == "paused" && now.boot == state.boot) delta(now) else 0
    fun remaining(now: ClockNow): Long? = state.budgetMs?.let { (it - currentActive(now)).coerceAtLeast(0) }
    fun overtime(now: ClockNow): Long = state.budgetMs?.let { (currentActive(now) - it).coerceAtLeast(0) } ?: 0
    fun isOvertime(now: ClockNow): Boolean = state.phase in listOf("active", "paused") &&
        state.budgetMs?.let { currentActive(now) >= it } == true

    fun checkpoint(now: ClockNow) {
        if (state.phase !in listOf("active", "paused")) return
        if (state.boot != now.boot) {
            // elapsedRealtime resets at reboot. Never invent focus while the device was off.
            state = state.copy(phase = "paused", anchorMono = now.mono, anchorWall = now.wall,
                boot = now.boot, notice = "Device restarted. Timer recovered paused; resume when ready.",
                revision = state.revision + 1, timelineComplete = false)
            event("recovered_after_reboot", now)
            return
        }
        val active = currentActive(now)
        val paused = currentPaused(now)
        state = state.copy(activeMs = active, pausedMs = paused, anchorMono = now.mono, anchorWall = now.wall)
    }

    fun load(session: Session, start: Boolean, now: ClockNow) {
        session.validate()
        require(state.phase !in listOf("active", "paused", "ready", "between", "quality", "reward", "session_reward")) { "A session already exists; stop it before replacing" }
        state = DeckState(session = session, phase = "ready", budgetMs = session.blocks[0].plannedMs,
            anchorMono = now.mono, anchorWall = now.wall, boot = now.boot, revision = state.revision + 1,
            timelineComplete = true, recordedTimezone = java.time.ZoneId.systemDefault().id)
        if (start) resume(now)
    }

    fun resume(now: ClockNow) {
        require(state.phase in listOf("paused", "ready", "between")) { "Nothing to resume" }
        val starting = state.phase in listOf("ready", "between")
        if (state.boot != now.boot) state = state.copy(timelineComplete = false)
        checkpoint(now)
        state = state.copy(phase = "active", anchorMono = now.mono, anchorWall = now.wall, boot = now.boot,
            startedAtMs = if (state.startedAtMs == 0L) now.wall else state.startedAtMs,
            blockStartedAtMs = if (state.blockStartedAtMs == 0L) now.wall else state.blockStartedAtMs,
            notice = "", revision = state.revision + 1)
        event(if (starting) "block_started" else "resumed", now)
    }

    fun pause(now: ClockNow) {
        tick(now)
        require(state.phase == "active") { "No active block to pause" }
        checkpoint(now)
        state = state.copy(phase = "paused", revision = state.revision + 1)
        event("paused", now)
    }

    fun extend(now: ClockNow) {
        tick(now)
        require(state.phase in listOf("active", "paused")) { "No current block to extend" }
        require(state.budgetMs != null) { "Stopwatch has no deadline to extend" }
        require(state.budgetMs!! <= 86400000L - 300000L) { "Block extension limit reached" }
        state = state.copy(budgetMs = state.budgetMs!! + 300000, revision = state.revision + 1)
        event("extended", now)
        tick(now)
    }

    private fun finishBlock(now: ClockNow, outcome: String) {
        checkpoint(now)
        event(when (outcome) { "skipped" -> "block_skipped"; "stopped" -> "block_stopped"; else -> "block_done" }, now)
        val session = requireNotNull(state.session)
        val block = session.blocks[state.index]
        val result = BlockResult(block.id, block.type, block.title, block.plannedMs,
            state.budgetMs, state.activeMs, state.pausedMs, state.blockStartedAtMs, now.wall, outcome, timerMode = block.effectiveMode, wallMs = state.activeMs + state.pausedMs,
            focusRatio = RewardEngine.focus(state.activeMs, state.pausedMs))
        val results = state.results + result
        if (outcome == "done" && block.type == "focus" && session.competitive) {
            state = state.copy(results = results)
            val performance = RewardEngine.evaluate(result, block, true, state, history, now)
            state = state.copy(phase = "reward", results = results.dropLast(1) + result.copy(performance = performance), revision = state.revision + 1)
            event("reward_offered", now)
            return
        }
        state = state.copy(results = results)
        advance(now)
    }

    private fun advance(now: ClockNow) {
        val session = requireNotNull(state.session)
        val results = state.results
        val next = state.index + 1
        if (next == session.blocks.size && session.competitive) {
            state = state.copy(competition = RewardEngine.session(state, history, now))
            if (results.all { it.outcome == "done" } && (session.sessionReward != null || session.sessionRewardPolicy != null)) {
                state = state.copy(phase = "session_reward", revision = state.revision + 1)
                event("session_reward_offered", now)
                return
            }
        }
        state = if (next == session.blocks.size) {
            state.copy(phase = if (results.all { it.outcome == "done" }) "complete" else "stopped",
                results = results, finishedAtMs = now.wall, revision = state.revision + 1)
        } else {
            state.copy(phase = if (session.autoAdvance) "active" else "between", index = next,
                budgetMs = session.blocks[next].plannedMs, activeMs = 0, pausedMs = 0,
                anchorMono = now.mono, anchorWall = now.wall, boot = now.boot,
                blockStartedAtMs = if (session.autoAdvance) now.wall else 0, results = results,
                overtimeNotified = false,
                revision = state.revision + 1)
        }
        event(when (state.phase) {
            "complete" -> "session_complete"
            "stopped" -> "session_stopped"
            "active" -> "block_started"
            else -> "waiting_for_start"
        }, now)
    }

    fun done(now: ClockNow) {
        tick(now)
        require(state.phase in listOf("active", "paused")) { "No current task to finish" }
        finishBlock(now, "done")
    }

    /** Close without completion credit or a reward; retain measured effort and advance. */
    fun skipBlock(now: ClockNow) = closeBlock(now, "skipped")
    fun endBlock(now: ClockNow) = closeBlock(now, "stopped")

    private fun closeBlock(now: ClockNow, outcome: String) {
        tick(now)
        require(state.phase in listOf("active", "paused")) { "Start the current block before skipping or ending it" }
        finishBlock(now, outcome)
    }

    fun quality(now: ClockNow, confirmed: Boolean) {
        require(confirmed) { "DONE is the completion authority" }
        require(state.phase == "quality") { "No completion quality check pending" }
        val plan = requireNotNull(state.session).blocks[state.index]
        val result = state.results.last()
        val performance = RewardEngine.evaluate(result, plan, confirmed, state, history, now)
        event("quality_confirmed", now)
        state = state.copy(phase = "reward", results = state.results.dropLast(1) + result.copy(performance = performance), revision = state.revision + 1)
        event("reward_offered", now)
    }

    fun rewardDone(now: ClockNow, skip: Boolean = false) {
        tick(now)
        require(state.phase in listOf("reward", "session_reward")) { "No reward awaiting acknowledgement" }
        if (state.phase == "session_reward") {
            val c = requireNotNull(state.competition)
            // Recheck eligibility at acknowledgement, including a midnight or cutoff crossing.
            val fresh = RewardEngine.checkReceipt(c.reward, state, history, now, state.results.filter { it.type == "focus" && it.performance?.quality == "confirmed" }.sumOf { it.activeMs })
            require(skip || c.reward.actions.isEmpty() || fresh.actions == c.reward.actions) { "Reward eligibility changed. Press CONTINUE to proceed." }
            state = state.copy(competition = c.copy(reward = RewardEngine.acknowledge(c.reward.copy(dailyRepsBefore = fresh.dailyRepsBefore, dailyPhysicalSecondsBefore = fresh.dailyPhysicalSecondsBefore), now, state.recordedTimezone, skip)))
            event(if (skip) "session_reward_continued" else "session_reward_acknowledged", now)
            state = state.copy(phase = "complete", finishedAtMs = now.wall, revision = state.revision + 1)
            event("session_complete", now)
        } else {
            val result = state.results.last(); val p = requireNotNull(result.performance)
            val fresh = RewardEngine.checkReceipt(p.reward, state, history, now)
            require(skip || p.reward.actions.isEmpty() || fresh.actions == p.reward.actions) { "Reward eligibility changed. Press CONTINUE to proceed." }
            state = state.copy(results = state.results.dropLast(1) + result.copy(performance = p.copy(reward = RewardEngine.acknowledge(p.reward.copy(dailyRepsBefore = fresh.dailyRepsBefore, dailyPhysicalSecondsBefore = fresh.dailyPhysicalSecondsBefore), now, state.recordedTimezone, skip))))
            event(if (skip) "reward_continued" else "reward_acknowledged", now)
            advance(now)
        }
    }

    fun stop(now: ClockNow) {
        tick(now)
        require(state.phase in listOf("active", "paused", "ready", "between", "quality", "reward", "session_reward")) { "No session to stop" }
        if (state.blockStartedAtMs != 0L && state.phase in listOf("active", "paused")) {
            checkpoint(now)
            event("block_stopped", now)
            val block = requireNotNull(state.session).blocks[state.index]
            state = state.copy(results = state.results + BlockResult(block.id, block.type, block.title,
                block.plannedMs, state.budgetMs, state.activeMs, state.pausedMs,
                state.blockStartedAtMs, now.wall, "stopped", timerMode = block.effectiveMode, wallMs = state.activeMs + state.pausedMs,
                focusRatio = RewardEngine.focus(state.activeMs, state.pausedMs)))
        }
        if (state.session?.competitive == true && state.competition == null) state = state.copy(competition = RewardEngine.session(state, history, now).copy(reward = RewardReceipt(reason = "Session stopped")))
        state = state.copy(phase = "stopped", finishedAtMs = now.wall, revision = state.revision + 1)
        event("session_stopped", now)
    }

    fun tick(now: ClockNow) {
        if (state.phase == "quality") {
            if (state.boot != now.boot) {
                state = state.copy(boot = now.boot, anchorMono = now.mono, anchorWall = now.wall, timelineComplete = false)
                event("review_recovered_after_reboot", now)
            }
            quality(now, true)
            return
        }
        if (state.boot != now.boot && state.phase in listOf("quality", "reward", "session_reward")) {
            state = state.copy(boot = now.boot, anchorMono = now.mono, anchorWall = now.wall, timelineComplete = false, revision = state.revision + 1)
            event("review_recovered_after_reboot", now)
        }
        if (state.boot != now.boot && state.phase == "between") {
            state = state.copy(boot = now.boot, anchorMono = now.mono, anchorWall = now.wall,
                timelineComplete = false, revision = state.revision + 1)
            event("waiting_for_start", now)
        }
        if (state.boot != now.boot) checkpoint(now)
        // Expiry changes presentation only. DONE is the sole block-completion action.
        val exceeded = isOvertime(now)
        if (state.phase in listOf("active", "paused") && state.overtimeNotified != exceeded) {
            state = state.copy(overtimeNotified = exceeded, revision = state.revision + 1)
            if (exceeded) event("overtime_observed", now)
        }
    }

    fun report(now: ClockNow): Report? {
        if (state.phase !in listOf("complete", "stopped")) return null
        val session = state.session ?: return null
        val start = if (state.startedAtMs == 0L) state.finishedAtMs else state.startedAtMs
        return Report(session.sessionId, session.title, state.phase, if (session.blocks.any { it.stopwatch }) null else session.blocks.sumOf { it.plannedMs ?: 0 },
            state.results.sumOf { it.activeMs }, state.results.filter { it.type == "focus" }.sumOf { it.activeMs },
            state.results.filter { it.type == "break" }.sumOf { it.activeMs }, state.results.sumOf { it.pausedMs },
            (state.finishedAtMs - start).coerceAtLeast(0), start, state.finishedAtMs, state.results,
            timelineCoverage = if (state.timelineComplete) "complete" else if (state.events.isEmpty()) "unavailable" else "partial",
            events = state.events, plannedBlockCount = session.blocks.size, recordedTimezone = state.recordedTimezone, competition = state.competition, reportVersion = session.protocolVersion,
            timedPlannedMs = session.blocks.sumOf { it.plannedMs ?: 0 },
            plannedCountdownFocusBlocks = session.blocks.count { it.type == "focus" && !it.stopwatch },
            plannedStopwatchFocusBlocks = session.blocks.count { it.stopwatch }, scheduleSnapshot = session.scheduleSnapshot())
    }
}
