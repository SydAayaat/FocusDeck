# Detailed timelines and useful feedback

FocusDeck 1.8 records both modes with the same monotonic start/pause/resume/DONE events. Stopwatch event budget is null and has no extension/overtime. V2 block wall is observed active+paused; session wall retains civil accounting. Time-target comparisons apply only to countdown. See [STOPWATCH_MODE.md](STOPWATCH_MODE.md).

V1.4 records timestamped timer transitions for new sessions. Your default landing screen is now Home whenever no prepared/running session exists: today's focus, this week's total, all-time focus, history access and a small last-session link. A finished report opens when you select VIEW REPORT or VIEW TIMELINE in history. Prepared, running, paused and between-task sessions still show their timer. Opening history or a report does not itself pause a running session.

## What the detailed record contains

Full JSON reports retain the original plan session ID and block IDs, with optional additional fields:

- `planned_block_count`: how many focus/break blocks the original plan contained.
- `recorded_timezone`: the phone's time zone when the plan was loaded. Actual timestamps remain UTC; the report preview uses the phone's current local zone.
- `timeline_coverage`: complete, partial or unavailable.
- `events`: a sequence-ordered list containing kind, UTC epoch milliseconds, elapsedRealtime milliseconds, boot count, zero-based planned block index, phase, cumulative active/paused milliseconds and effective budget.

Events include block_started, paused, resumed, extended, block_done, block_stopped, waiting_for_start, session_complete, session_stopped, overtime_observed and recovered_after_reboot. DONE and the next automatic block start share a timestamp but have separate sequence numbers. DONE/STOP while paused closes that pause without inventing a resume. With manual advance, waiting for START is logged separately from active and paused time.

Event counters describe the block identified by that event. They reset at a new block. JSON block indexes are zero-based; readable reports and CSV block indexes are one-based. Waiting before an unstarted task can have a block index but no matching completed block row/ID in the final report.

## Pause intervals and timing accuracy

The readable report and interval CSV turn those events into rows such as:

| Task | Activity | From | To | Duration |
| --- | --- | --- | --- | --- |
| b1 | active | 20:00:00 | 20:12:00 | 12 minutes |
| b1 | paused | 20:12:00 | 20:16:30 | 4 minutes 30 seconds |
| b1 | active | 20:16:30 | 20:35:00 | 18 minutes 30 seconds |
| b2 | active | 20:35:00 | … | … |

This is an illustrative example, not a measured session. UTC timestamps in actual files include their date and millisecond precision. Duration comes from monotonic active/paused counters; waiting uses elapsedRealtime within the same boot. Wall-clock changes can make the displayed end timestamp earlier than the start while the measured duration remains correct. Event sequence then determines order. The timestamps describe when the phone applies an action, not when a PC command was typed.

An extension or observed-overtime event does not split an otherwise uninterrupted active interval. Overtime observation is the time the app notices the deadline, which can be later during sleep; it is not presented as an exact scheduled alert timestamp. Timer measurement still derives from elapsed time rather than UI ticks.

## Automatically saved CSVs

Both the private phone exports folder, your selected visible phone folder and the PC exports folder now contain:

| File | Contents |
| --- | --- |
| `all-sessions.csv` | Session totals, outcome counts, original planned block count and behavior metrics |
| `all-blocks.csv` | Individual task/break totals and behavior metrics |
| `all-events.csv` | Every recorded transition with its timestamp, sequence and counters |
| `all-intervals.csv` | Active, paused and waiting intervals with exact duration, endpoints and block mapping |

These files refresh automatically when a session finishes/stops, history imports, a folder is selected or saving is retried. During an active session, events are saved immediately with private timer state and command receipts. Final public JSON/CSV copies and PC synchronization occur after completion/STOP. They are not continuously streamed live journals. Existing folder grants remain usable after the update.

Phone COPY FULL REPORT and PC `focusdeck reports --session ID --latest` include intervals and the entire event sequence. The on-phone report preview shows up to 40 intervals; copied reports and CSVs include all recorded entries. Separate optional PC exports work offline:

```bash
focusdeck export --kind events --output "$HOME/Downloads/focusdeck-events.csv"
focusdeck export --kind intervals --output "$HOME/Downloads/focusdeck-intervals.csv"
```

Session/task CSVs retain their existing columns and add behavior columns at the end. Millisecond durations and UTC timestamps remain the same units. All four phone/PC CSV column schemas match. No wire protocol version change is needed; the new report fields are optional for older records.

## Automatic behavior metrics

For complete timelines, each session and recorded block includes pause count, extension count, longest uninterrupted active timer stretch, longest pause and average pause. The average is integer milliseconds. Sessions combine focus and break blocks; use `block_type` to compare focus tasks separately. A pause count is the number of explicit PAUSE actions, including a pause ended by DONE/STOP. The active stretch measures timer activity and can include time when you were away from the desk.

Actual-to-planned ratio and paused share also use existing aggregate totals, so they remain available for older sessions. Actual-to-planned is `active_ms / planned_ms`; paused share is `paused_ms / (active_ms + paused_ms)`. A zero denominator yields a blank. `marked_done_fraction` is DONE blocks divided by the original planned block count, including breaks; it remains blank when an old record lacks that count. DONE records your action and is not an assessment of task quality.

These metrics help GPT identify task estimates that repeatedly run long, concentrated versus interrupted timer patterns, and which tasks attract pauses. Pauses can reflect deliberate rest, external interruptions or a difficult problem; their reason cannot be inferred from the timestamp alone. Finishing faster can reflect fluency, easier material, a generous estimate or incomplete work.

## Additional evidence worth giving GPT

These are recommendations for information to provide with your report; V1.4 does not collect them automatically:

| Evidence | Useful comparison |
| --- | --- |
| Clear task goal and what you actually produced | Time against completed work, not just DONE taps |
| Questions answered correctly / questions attempted | Accuracy and recurring error types |
| Recall without notes immediately and on a later day | What remains available after the session |
| Confidence before checking an answer | Confidence against actual correctness |
| Task difficulty and method used: reading, solving, retrieval, speaking | Compare similar tasks and practice methods |
| Pause reason: planned rest, interruption, stuck, fatigue | Explain pauses without treating all pauses as distraction |

My recommended next addition is a small optional completion check for goal achieved, recall/correctness evidence and one note. These should be attached to the session/block ID. Keep missing answers explicitly unknown; do not produce an overall learning score from timing alone.

The value of delayed recall is supported by [Roediger and Karpicke's primary study on test-enhanced learning](https://www.psychologicalscience.org/journals/psychological-science/j.1467-9280.2006.01693.x/). In that study, repeated testing improved delayed retention while repeated study increased confidence; confidence and durable recall can therefore point in different directions. Applying this as an optional log field is a design recommendation, not a finding that this timer measures learning.

## Gaps and storage limits

Older finalized reports remain unavailable for detailed timelines: their individual pauses were never recorded. A session already active when updating starts a partial timeline. Reboots mark the timeline partial; no interval is synthesized across boot counters. The existing timer recovery rules still apply and save bounded checkpointed active/paused totals. Process recreation within the same boot preserves events and timer anchors. Replayed command UUIDs do not add events twice.

At 2,000 events in one session, the timer continues and aggregate totals remain available, but further events are not stored and coverage becomes partial. This keeps normal reports within the existing 1 MiB message limit. For partial/unavailable coverage, pause counts and uninterrupted/longest/average metrics are blank rather than presented as full-session measurements; any known intervals remain in the timeline. This is a per-session event limit, not a limit on years or number of archived sessions.

The transaction's delivered-report cache is reduced from 100 to 10 to keep detailed archived reports out of routine timer checkpoint writes. Outstanding and not-yet-archived reports remain retained. The separate phone and PC archives keep all sessions. Timeline entries are added only at transitions/observations, with no new per-second disk writes or history animation. Actual battery effects still need measurement on the S24 Ultra.
