# Stopwatch Mode · FocusDeck 1.8.2

This extends the uploaded 1.7.1 implementation. GPT selects each focus block's mode, deliverables, weights and exact reward policy in advance. FocusDeck executes that plan. There is no mode picker, recommendation, inferred target or generated reward.

## Versioned contract

New plans use session `protocol_version: 2`; their reports use `report_version: 2`. `session.schema.json` and `report.schema.json` dispatch to separate v1/v2 schemas. Wire envelopes, pairing and `/v1/device` remain version 1, preserving existing credentials and transport identity.

| Block | Mode | Duration |
| --- | --- | --- |
| v2 focus | Explicit `countdown` | Positive integer `duration_seconds`, 1–86400 |
| v2 focus | Explicit `stopwatch` | Omitted or `null`; any numeric duration is rejected |
| v2 break | Omitted or `countdown` | Existing positive countdown duration |
| Existing v1 focus/break | Mode omitted | Existing countdown contract |

V1 plans cannot carry `timer_mode` or completion-condition extensions. This avoids silently reinterpreting the v1 contract. Their reports remain v1 and ordinary noncompetitive plans keep immediate DONE progression. V2 requires UUID-form session IDs and unique block IDs; GPT must generate a fresh session UUID for every new plan. Old arbitrary identifiers/history remain readable. UUID reuse is not prohibited globally: attempt identity still includes the actual start timestamp.

Both peers advertise `supported_session_versions: [1,2]` in hello/hello_ack. An old hello without this field means `[1]`. The desktop refuses v2 send/start to an old phone; it holds a previously queued v2 command unchanged until a compatible phone reconnects. It never substitutes countdown or strips fields.

V2 countdown report targets and budgets are positive whole-second durations from 1000 to 86400000 milliseconds; an extended budget cannot be below the original target. This mirrors the existing plan and engine limits. V1 historical bounds remain unchanged. The 1.8.1 patch validates these limits consistently and rejects inconsistent category totals or malformed reward evidence; the v2 protocol and scoring rules are unchanged.

## Execution and interface

Stopwatch starts at `00:00:00`. The display counts active time upward in HH:MM:SS; PAUSE freezes active time while accumulating paused time, RESUME continues, and DONE finalizes the block from either running or paused. There is no budget, remaining time, deadline, expiry, overtime, extension or target comparison. Break/countdown expiry still enters red overtime and never completes or advances a block.

The stopwatch now shares the countdown screen layout: title spacing, dial size, footer, DONE on the left and PAUSE/RESUME on the right, portrait/landscape arrangement and dim desk view. Only its dial is different: 60 inner clock ticks (longer every five minutes), white outer arc and endpoint moving clockwise by six degrees per completed active minute. One hour returns the marker to the top for the next cycle; HH:MM:SS keeps counting upward. This visual hour scale is not a target, budget or reward condition. Its footer progress counts DONE blocks, explicitly labeled `% DONE`; estimated finish is N/A for a current or future stopwatch block. Countdown retains its existing dial, fractional progress and controls. See [the supplied reference](design/stopwatch-reference.png) and [1.8.2 changes](UPDATE_1.8.2.md). The result page shows actual active, paused and observed wall duration, Focus Ratio and the supplied reward. Stopwatch Pace/BeatScore are N/A and no fabricated score ring is shown. No normal FAILED/SKIPPED/UNFINISHED/ABANDONED task outcome was added. Existing administrative stop and historical interrupted records remain separate from DONE.

DONE leads through the existing result/reward workflow. REWARD DONE records acknowledgement; CONTINUE progresses without acknowledging the reward, leaving the task DONE. Disabled/unmatched rewards are normal results. The visible empty-reward page advances after the existing brief delay; without the UI, it can wait for the existing reward command. `auto_advance: true` starts the next block after this handling; false waits for START.

Active and paused durations use the existing monotonic clock/checkpoints, not tick counting. Stored mode and nullable budget survive serialization and process recreation. Reboot recovers paused at the last durable checkpoint, marking timeline coverage partial; unobserved device-off time is not invented as focus or pause. Result/reward waits are outside block active/paused totals.

## Completion rewards

Existing text rewards accept arbitrary exact GPT activity text. The executor selects a supplied tier/alternative; it does not parse the activity into a hard-coded catalog or rewrite it. Text rewards retain existing selection behavior; legacy typed alternatives retain their pre-existing feasibility checks.

| v2 field | Meaning |
| --- | --- |
| Tier `condition: "completed"` | Current/most recent block has DONE evidence |
| Tier `condition: "module_completed"` | All named focus `block_ids` have DONE evidence |
| Tier `block_ids` | 1–100 unique focus IDs from this plan; required only for module conditions |
| Tier `condition: "session_completed"` | Every planned block, including breaks, is DONE; session policy only |
| Tier `min_focus_ratio` | Inclusive 0–100 minimum; unknown stopwatch ratio cannot satisfy a tier minimum |
| Tier `min_completed_blocks` | Inclusive 1–100 DONE focus-block count; session policy only |
| Eligibility `minimum_completed_blocks` | Session-wide gate, 0–100, default 0 |
| Eligibility `require_all_focus_completed` | Session-wide gate, boolean, default false |
| Eligibility `minimum_focus_ratio` | Existing session-wide gate using overall measured focus time |

Stopwatch block tiers require `completed` or `module_completed`. A module reward belongs on its final topic: every referenced ID must be a focus block earlier than or equal to the awarding block. It cannot reference future, duplicate, unknown or break IDs. The optional module Focus Ratio threshold applies to the awarding block, not an inferred module aggregate. Session module policies may reference any focus ID in the session.

Stopwatch block policies reject BeatScore, Pace, session-score and personal-best tests. Stopwatch blocks also reject timed comparison/classification and legacy `reward_contract`. A plan containing stopwatch rejects legacy `session_reward` and default reward contracts; use explicit policies. Entirely untimed session policies reject positive countdown score/class/mean-focus gates and time/PB tiers. Mixed-session policies may explicitly gate countdown-only scores alongside independent completion tiers. Unsupported combinations fail validation with errors.

```json
{
  "enabled": true,
  "selection": "highest_satisfied",
  "tiers": [
    {"condition": "completed", "min_focus_ratio": 80,
     "reward": {"text": "Move one physical token into your EARNED container."}}
  ]
}
```

All specified tests are ANDed. `first_satisfied` uses array order. `highest_satisfied` preserves countdown ordering (PB, session-score/Beat threshold, Beat threshold); completion tiers then compare minimum Focus Ratio and minimum completed-block count, with later array index breaking ties. Existing countdown tiers without completion fields keep their old ordering. No eligible tier means no unlocked reward.

## Scoring boundaries

Stopwatch records completion, actual duration and Focus Ratio `100 × active / (active + paused)`, rounded to two decimals. Zero measured duration gives an unknown ratio. Planned duration, Pace, time-based BeatScore, rank and strong/on-target/over-target classification are null/N/A, never 0 or 100.

Countdown keeps the original target, extension behavior, BeatScore formula, recalibration cap, classes, weights and conditional reward ordering. Fully countdown sessions retain their existing weighted scoring.

The original countdown rule permitting an unknown Focus/Pace metric against an explicit zero minimum is preserved. This affects eligibility only; recorded metrics stay null. Stopwatch unknown metrics cannot satisfy a supplied minimum and never receive a fabricated score.

Mixed sessions show completed focus time, total paused time, overall Focus Ratio and completed focus-block count across both modes. Duration-based scoring includes only DONE countdown focus blocks; stopwatch weights are retained as plan evidence and contribute neither points nor denominator. The weighted mean Focus Ratio and class counts are countdown-only; `competition.focus_ratio` covers all measured focus active/paused time, including measured partial work in an administratively stopped report.

| `competition.score_scope` | Meaning |
| --- | --- |
| `all_focus` | All planned focus blocks are countdown |
| `countdown_only` | Session contains both modes; label aggregates countdown-only |
| `none` | No planned countdown focus; time scores N/A |

If a stopwatch-containing session has no completed eligible countdown focus yet, BeatScore and normalized score are null even when its planned scope is `countdown_only`. Zero raw points, weight and class counts describe absence of timed evidence, not stopwatch performance. Mixed/untimed whole-session personal best is disabled because a countdown-only score cannot describe the complete workload. Completion rewards do not depend on a time score unless GPT explicitly supplies an eligible countdown gate.

## Reports, history and exports

Each v2 result requires `timer_mode`, nullable `planned_ms`, nullable `budget_ms`, `active_ms`, `paused_ms`, `wall_ms`, `focus_ratio`, start/end timestamps, outcome and nullable `overtime_ms`. Stopwatch planned/budget/overtime fields are null. DONE focus results include performance/completion evidence, exact reward policy/selected receipt and acknowledgement data. Breaks retain countdown results.

Block `wall_ms` and performance `wall_ms` are the observed monotonic active+paused duration; start/end timestamps remain civil UTC data. These observed counters can differ from civil timestamp span after a clock change or reboot gap. **Top-level session `wall_ms` retains the existing civil finish-minus-start calculation** and includes result/reward waits; it is not a new monotonic session total.

V2 adds `timed_planned_ms` (known timed portion including breaks), planned countdown/stopwatch focus counts and score scope. A whole-session `planned_ms` is null when any stopwatch block exists. Completed focus time is derived from DONE focus results; total measured focus can also include interrupted work. Legacy v1 reports decode missing mode as countdown and missing new metadata as defaults. JSON retains nulls, CSV uses blank cells, and Markdown/clipboard shows N/A.

The existing unlimited private archive, visible-folder mirror, PC JSON/Markdown archive, Day/Week/Month/Year/All history, Home, tombstone deletion and replay suppression are retained. CSV appends mode/report/scope/observed-wall fields; existing columns retain their names/order. Automatic regeneration includes historical v1 data. No user history or pairing data was migrated, cleared or deleted.

Existing commands remain:

```bash
focusdeck validate examples/mixed-mode-study.json
focusdeck start examples/mixed-mode-study.json
focusdeck reports --session UUID --latest
focusdeck copy-report --session UUID
focusdeck delete-logs --session UUID
focusdeck delete-logs --all
```

Examples: [stopwatch-study.json](../examples/stopwatch-study.json) and [mixed-mode-study.json](../examples/mixed-mode-study.json). The mixed example follows three stopwatch Module 2 topics, a milestone reward, a break, countdown Topic 3.1 and stopwatch Topic 3.2. Replace fixture UUID/created_at with fresh values before a new real session. [GPT Project Instructions](GPT_PROJECT_INSTRUCTIONS.md) and [workflow](GPT_WORKFLOW.md) include the supported `focusdeck start -` heredoc route.

## Implementation map

| Area | Modified/added source |
| --- | --- |
| Contract/version/capabilities | Android build/version, desktop package version, versioned session/report schemas, wire schema, `Models.kt`, `DeckNetwork.kt`, desktop `common.py`, `server.py` |
| Timer/recovery/status | `TimerEngine.kt`, `DeckRepository.kt`, `FocusService.kt` |
| Native UI | New `StopwatchScreen.kt`, `MainActivity.kt`, `RewardScreen.kt` |
| Rewards/scoring | New `CompletionConditions.kt`, `ConditionalRewards.kt`, `Rewards.kt`; new desktop `stopwatch.py`, `conditional.py`, `rewards.py` |
| History/reports/clipboard | `StudyHistory.kt`, `TimelineAnalysis.kt`; desktop `history.py`, `timeline.py`, `reports.py` (existing clipboard route reuses Markdown) |
| Verification | New `StopwatchTest.kt`, shared mixed report, desktop stopwatch/schema tests, transport capability tests, nullable countdown assertion updates, CI test dependency |
| Planning/delivery | Project instructions, workflow/protocol/reward/history/testing docs, two examples, 1.8 upgrade guide |

Executed checks and remaining device checks are in [VERIFICATION.md](VERIFICATION.md). Use [UPDATE_1.8.md](UPDATE_1.8.md) for an upgrade that preserves pairing/history. Android tests, compilation and visual checks remain pending in this source delivery.
