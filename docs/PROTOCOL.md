# FocusDeck wire v1 · session/report contracts v1 and v2

FocusDeck 1.8.0 adds session `protocol_version:2` and report `report_version:2` for GPT-controlled Stopwatch Mode and completion conditions. Wire envelopes/pairing remain v1. See [STOPWATCH_MODE.md](STOPWATCH_MODE.md) for exact v2 conditions, scoring scope and recovery/report semantics. The historical v1 details below apply to countdown; obsolete quality commands are not accepted.

V1.5 adds optional reward_strategy/session_reward/comparison fields to plans and performance/competition evidence to final reports. New quality/reward phases and commands are listed in the JSON schemas and [reward rules](REWARDS_AND_COMPETITION.md). Both peers must be updated to V1.5 for competitive plans. Protocol envelope version remains 1.

V1.4 final reports optionally include timeline_coverage, planned_block_count, recorded_timezone and sequence-ordered events. These extend the report schema without changing the envelope version. Old reports decode with unavailable coverage and no events. Detailed event/interval semantics and gap handling are documented in [TIMELINE_AND_FEEDBACK.md](TIMELINE_AND_FEEDBACK.md).

## Plans

See `protocol/session.schema.json`. `focusdeck validate plan.json` additionally checks semantic constraints. Kotlin validates incoming plans independently.

| Field | Meaning |
| --- | --- |
| `protocol_version` | Integer 1 (legacy countdown) or 2 (explicit focus modes/completion conditions) |
| `session_id` | At most 100 characters; v2 requires UUID form; choose a fresh UUID per new plan |
| `title` | Nonempty display title, at most 200 characters |
| `created_at` | ISO-8601 timestamp with timezone; metadata only, not a scheduled start time |
| `auto_advance` | Defaults true: next block starts after DONE/result/reward handling; false waits for START. Expiry never advances |
| `blocks` | Ordered array of 1–100 blocks |
| `blocks[].id` | Unique nonempty ID, at most 100 characters |
| `blocks[].type` | `focus` or `break` |
| `blocks[].title` | Nonempty title, at most 200 characters |
| `blocks[].timer_mode` | V2 focus requires countdown or stopwatch; breaks omit it or use countdown; absent in v1 |
| `blocks[].duration_seconds` | Countdown integer 1–86400; stopwatch omitted/null; total known timed portion at most 604800 seconds |

Unknown session/block fields and duplicate block IDs are rejected. Strings must not be whitespace-only. Countdown durations are relative and start on START. Stopwatch has no duration or predicted finish; a remaining stopwatch also makes the countdown screen's session ETA unknown. Time-of-day scheduling is not implemented. Extended countdown budget is separate from original target. Stopwatch shows no deadline progress. Load wire.schema.json with its local file URI as the base so sibling schema references resolve offline.

## Envelopes

All network messages are JSON objects with `protocol_version: 1` and a `type`. Maximum received message size is 1 MiB. Standard WebSocket ping/pong detects a lost transport. TLS 1.2+ and certificate pinning protect the bearer token and session content.

Connect to `/v1/device` with `Authorization: Bearer <pairing token>`. Browser Origin headers are rejected. After Upgrade:

```json
{"protocol_version":1,"type":"hello","device_id":"persistent-phone-uuid","name":"Android FocusDeck","supported_session_versions":[1,2]}
```

Server responds:

```json
{"protocol_version":1,"type":"hello_ack","desktop_id":"paired-desktop-id","supported_session_versions":[1,2]}
```

Missing capabilities mean session v1 support. V2 plans are refused/held for an old phone without changing their payload. The first authenticated device becomes the sole paired phone; a different UUID is rejected until local forget-device. Transfer the pairing token privately.

Commands:

```json
{"protocol_version":1,"type":"command","command_id":"uuid","action":"pause"}
```

`send` and `start` require `session`, containing a validated plan. Actions are `send`, `start`, `pause`, `resume`, `next`, `stop`, `extend`, `reward_done`, `reward_continue`. `next` means DONE. `extend` adds five minutes to countdown and rejects stopwatch. Commands are persisted and deduplicated as described in architecture.

Phone ACK after durable commit:

```json
{"protocol_version":1,"type":"ack","command_id":"uuid","ok":true}
```

An unsuccessful ACK has `ok:false` and `error`. Invalid state transitions leave the intended action unapplied; an elapsed deadline only enters overtime, so DONE remains valid after expiry.

Phone status (sent immediately for commands, meaningful state changes and outbox changes; refreshed every 15 seconds while active/visible or 60 seconds otherwise in V1.2):

```json
{"protocol_version":1,"type":"state","state":{"phase":"active","session_id":"study-1","title":"Index","block_index":0,"block_count":3,"remaining_ms":120000,"revision":2,"observed_at_ms":1791310000000,"completed_blocks":0}}
```

`block_index` is zero-based. `completed_blocks` counts closed blocks, including timed_out; it is not the count of confirmed done tasks. Desktop status is an observation, not timer authority.

Reports:

```json
{"protocol_version":1,"type":"report","report_key":"study-1:1791310000000","report":{"session_id":"study-1","title":"Index","status":"complete","planned_ms":60000,"active_ms":45000,"focused_ms":45000,"break_ms":0,"paused_ms":0,"wall_ms":45000,"started_at_ms":1791310000000,"finished_at_ms":1791310045000,"blocks":[]}}
```

The example elides block results for readability. Each result includes `id`, `type`, `title`, `planned_ms`, `budget_ms`, `active_ms`, `paused_ms`, `started_at_ms`, `finished_at_ms`, `outcome`. New outcomes are `done` or `stopped`; `timed_out` remains accepted for legacy reports. V1.1 emits overtime_ms on blocks and reports: max(0, active time minus extended budget), summed over all blocks. Status adds overtime_ms and is_overtime, with public phase overtime when actively in excess. Full schemas define required fields. `actual active` = focused + break timer time, excluding pause. `wall_ms` includes waits and pauses; device-clock changes may affect it. Stopped reports contain only blocks actually started/closed, while `planned_ms` covers the full original session.

After saving, server responds:

```json
{"protocol_version":1,"type":"report_ack","report_key":"study-1:1791310000000","file_id":"deterministic-file-hash"}
```

Server errors: `{"protocol_version":1,"type":"error","message":"..."}`. Credentials are never sent as messages or URL query parameters.

## Timer states

V2 state adds timer_mode and elapsed_active_ms/elapsed_paused_ms. Stopwatch remaining_ms/overtime_ms are null and is_overtime false; public phase stays active/paused. V2 focus DONE enters reward before the existing next-block/session transitions; there is no extra completion-quality gate.

| Phase | Accepted actions and result |
| --- | --- |
| `idle` | send → ready; start → active |
| `ready` | resume/phone START → active; stop → stopped |
| `active` | pause → paused; next → next active/between/complete; deadline → same block in red overtime; extend keeps active; stop → stopped |
| `paused` | resume → active; next → next active/between/complete; extend keeps paused; stop → stopped |
| `between` | resume/START → active; stop → stopped |
| `complete`, `stopped` | send/start creates a new session |

Focus versus break is the current block type, not a different clock engine. Connected/connecting/offline is a separate transport state. A reboot converts active/paused to paused recovery. UI storage/network errors are separate messages and do not reset a session.

## CLI exit codes

| Code | Meaning |
| --- | --- |
| 0 | Success |
| 1 | Daemon setup/startup failure |
| 2 | Invalid command line or plan/input |
| 3 | Offline device, rejected transition, pending/uncertain delivery or no reports |
| 4 | Cannot reach local daemon/socket/file, or local transport timeout |

Mutation success means the phone committed the command. A timeout cannot prove it was not executed. Always inspect `focusdeck status` before retrying a potentially delivered DONE/next command.

## V1.6 conditional rewards

A focus block reward_policy (including enabled=false), custom weights/classes or a session_reward_policy enables execution results; the legacy reward_strategy also remains supported. DONE/next freezes active/paused time, immediately calculates Performance and enters reward. No quality_confirm/quality_incomplete/reward_skip command is supported. reward_done acknowledges an offered activity, while reward_continue advances without acknowledgement; both leave the task DONE. A no-reward result also supports reward_done, which the visible UI issues after about 2.2 seconds. auto_advance=false still waits for START. The final independent session_reward phase may precede complete. Administrative stop remains a desktop interruption, with historical stopped records readable.

Performance adds reward_policy, performance_class, session_weight and classification. Competition adds weighted (raw points, weight, normalized score, class counts, mean Focus Ratio, coefficients) and session_reward_policy. BeatScore and normalized class score are distinct. Policies/eligibility, timer arithmetic and reward source permissions are checked at runtime, in addition to schema structure. New result/reward event intervals exclude active/paused time; legacy quality events are preserved on read.

## Study-log deletion synchronization

history_delete carries protocol_version=1, sync_id and deletions={before_ms,report_keys,revision}. before_ms hides finalized reports at or before the delete-all cutoff; exact report_keys target session-ID:start-timestamp attempts. Sets/cutoffs merge monotonically; changed content gets a new local revision. Metadata is capped at 3,000 exact keys and 768 KiB encoded.

history_delete_ack echoes sync_id after durable deletion intent: PC also completes local cleanup before ACK, whereas phone file/provider cleanup is asynchronous and retryable. Both devices suppress deleted outbox/import/report replay. The desktop sends its deletion state immediately after hello_ack, before replaying pending timer commands. Android sends pending deletion state ahead of its report outbox. Deletions are separate from timer-command delivery receipts, and can be recorded while the peer is offline. Report envelope keys must match payload attempt identity. A suppressed old report receives report_ack with file_id=null and is not saved.

The local daemon delete-logs control accepts exactly one of all=true, session_id (latest locally saved attempt), or report_key (exact attempt, even if not yet archived on PC). Desktop copy-report is offline and reads validated JSON into Markdown, then uses a graphical clipboard program without a shell. See UPDATE_1.6.md for scope/commands.


## V1.7 GPT reward text
Conditional reward: {"text":"5 pull-ups + move one token."}; type:"text" optional. Nonblank1–1000 Unicode code points; no typed amount/duration fields. max_beat_score and session-only max_session_score are inclusive optional upper bounds, checked against minimums. All conditions must pass. Text bypasses typed activity permissions/volume/caffeine parsing; GPT owns those planning choices. Legacy typed forms remain compatible. Receipts contain kind:"text", exact text, reps:0, seconds:0. CSV/Markdown preserve text, policy and acknowledgement; altered receipt text is rejected.


## FocusDeck 1.10 cumulative progress and motion

Current achievement command contracts are 1/2/3. Contract 3 adds `progress_set`, `progress_increment`, capability flags and initial progress in definitions. Commands 1/2 remain accepted; progress is capability-gated. Read [the 1.10 update](UPDATE_1.10.0.md) and [current GPT achievement rules](GPT_ACHIEVEMENTS_INSTRUCTIONS.md) before generating progress commands. Targets are supplied by project configuration; never assume Custom Compute has 100 topics. Progress does not replace the daily/streak model. All archive/restore/delete, replay, layout and closed-day safeguards remain.
