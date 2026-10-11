package com.hax.focusdeck

data class CommandTransaction(val state: DeckState, val receipts: Map<String, CommandAck>, val ack: CommandAck)

/** Returns a proposed transaction. The caller must persist state and receipts before ACK. */
object CommandProcessor {
    fun apply(state: DeckState, receipts: Map<String, CommandAck>, id: String, action: String,
        session: Session?, now: ClockNow, history: List<Report> = emptyList()): CommandTransaction {
        require(id.isNotBlank() && id.length <= 100) { "Invalid command ID" }
        receipts[id]?.let { return CommandTransaction(state, receipts, it) }
        val timer = TimerEngine(state, history)
        val ack = try {
            when (action) {
                "send" -> timer.load(requireNotNull(session), false, now)
                "start" -> timer.load(requireNotNull(session), true, now)
                "pause" -> timer.pause(now)
                "resume" -> timer.resume(now)
                "next" -> timer.done(now)
                "stop" -> timer.stop(now)
                "skip_block" -> timer.skipBlock(now)
                "end_block" -> timer.endBlock(now)
                "extend" -> timer.extend(now)
                "reward_done" -> timer.rewardDone(now)
                "reward_continue" -> timer.rewardDone(now, true)
                else -> throw IllegalArgumentException("Unknown command")
            }
            CommandAck(true)
        } catch (e: IllegalArgumentException) { CommandAck(false, e.message ?: "Invalid command") }
        catch (e: IllegalStateException) { CommandAck(false, e.message ?: "Invalid state") }
        timer.checkpoint(now)
        val next = (receipts + (id to ack)).entries.toList().takeLast(256).associate { it.toPair() }
        return CommandTransaction(timer.state, next, ack)
    }
}
