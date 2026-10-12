# Synthetic contract (format: focusdeck.synthetic-fixtures.v1)

This is a **schema-neutral dev fixture**, not a production import schema. `synthetic: true` on records is a required provenance flag. Use the separate `.visualtest` sandbox or debug-only read-only adapter.

## Dates and timestamps
All ISO timestamps include Europe/Berlin's applicable UTC offset. Day-level dates use local `YYYY-MM-DD`. `fixed_clock` in manifest fixes screenshot comparisons. Sessions may be absent on idle days.

## Sessions and blocks
`session.id` is stable across files. `session.status` is `DONE`, `FINISHED_WITH_SKIPS`, `ENDED_EARLY`, or `NOT_STARTED`. `session.focus_seconds` equals the sum of all linked block `active_focus_seconds`. `done_count` counts only `DONE` blocks. `planned_focus_seconds` sums only known countdown targets; stopwatches are null.

`block.start_delta_seconds` is actual minus planned, positive=late. A `null` actual time is unknown/unstarted/skipped rather than epoch 0. `pauses[]` is ordered and falls inside `actual_start`/`actual_end`. The elapsed block interval equals focus plus pause durations. `break_before[]` tracks observed break intervals, not arbitrary waiting gaps. Quality totals are mock raw evidence, not scored achievements.

## Activity feed
`history_activity.jsonl` is reverse-chronological, stable ordered by timestamp + sequence. Names and GPT generation results are synthetic, not actual network requests. `timeline_events.jsonl` stores the same event identities chronologically; event actions are descriptive fixture names to map to the app's existing enums.

## Analytics
`daily_analytics` includes 24 `focus_by_hour_seconds` bins and three 8-hour bins. Their sums must equal daily `focus_seconds`. `evidence` is `measured`, `observed_zero`, or `unknown`; only observed_zero is genuinely zero evidence.
`period_analytics` includes ISO weeks, Gregorian months, and calendar years, partially filled at the range endpoints. The totals reconcile with daily records. Mean punctuality is the mean among observed session start deltas; snapshots are safe cache projections, not alternative scoring formulas.

## Achievements
Definitions use `STREAK_ONLY`, `PROGRESS_ONLY`, or `HYBRID`. Daily evidence is `daily_confirmed`; numerical progress uses `progress_delta`. `record_gap_days` here is current consecutive days minus best days, and can be negative. Progress may exceed its target (unclamped evidence); `progress_percent` is clamped visually at 100. This does not redefine FocusDeck's production achievement rules.

## Index
Topic IDs are stable and hierarchy is synthetic. A `topic_completed` event inserts its topic into the current complete set; `topic_reopened` removes it. Topic titles deliberately say `Synthetic practice topic`; they are *not* copied from your original 126-topic PTfS study index. Index snapshot should equal replayed event state.

## Security
No token, password, QR pairing secret, real hostname, ADB serial, device ID, user location or account data is included. Do not expose any dev-fixture export as user records or route it to real device synchronization.

## Recommended Kotlin adapter
Use lazy streaming/storage and map viewmodels with the existing `VisualTestDataProvider` / QA interfaces. Keep strong ownership and a single run-level source switch. This pack is designed for Codex to **load and map** rather than author 3 years of data from scratch.
