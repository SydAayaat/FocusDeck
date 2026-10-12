# FocusDeck Developer Mode — 3-Year Synthetic Fixture Pack

**Fixture format:** `focusdeck.synthetic-fixtures.v1`  
**Target UI release:** Android 1.22.0 (planned)  
**Seed:** `122026` (deterministic)  
**Fixed local test clock:** `2026-10-12T23:59:30+02:00` (Europe/Berlin)  
**Range:** `2023-10-12` to `2026-10-12` inclusive (**1097 dates**)

## WHAT IS IN THE PACKAGE

- `manifest.json`: file hashes, counts, clock, safety contract and schema marker.
- `projects.json`: 9 synthetic Projects/College/Learning entities.
- `sessions.jsonl`: 1321 synthetic sessions (planned/actual timing, stopwatch/countdown, states and totals).
- `blocks.jsonl`: 2832 timed focus blocks, pauses, break intervals, estimation and quality evidence.
- `timeline_events.jsonl`: 15206 timestamped events for timeline/details.
- `history_activity.jsonl`: chronological activity feed from synthetic session, GPT, Index, achievement and block events.
- `daily_analytics.jsonl`: 1097 daily summaries with exact 24 hourly bins, three 8-hour bins, status and counts.
- `period_analytics.jsonl`: 199 weekly/monthly/yearly summaries derived from daily rows.
- `achievement_definitions.json`, `achievement_events.jsonl`, `achievement_snapshots.json`: nine achievement definitions with evidence and computed progression.
- `index_definitions.json`, `index_topics.jsonl`, `index_events.jsonl`, `index_snapshots.json`: two SYNTHETIC sample indexes (126 and 64 topics) with replayable topic changes.
- `developer_scenarios.json`: separate active, paused, idle, early, empty, observed-zero and long-text UI preview presets.
- `schema_notes.md`: practical field contracts and adapter guidelines.
- `validate_fixtures.py`: offline archive validator.
- `generate_fixtures.py`: optional offline deterministic re-generator (pass a numeric seed).

## INSTALLATION / LOADING

**NOT a production backup and NOT a native FocusDeck import file.** The exact Kotlin/SQLite schema is unavailable in this chat.

Codex should:

1. Keep the existing `com.hax.focusdeck.visualtest` isolation and developer feature boundaries.
2. Copy this fixture pack into a development-only assets folder (or unpack it into a debug-only sandbox).
3. Validate `manifest.json` + SHA256 checksums before loading.
4. Parse JSONL **streamingly**; do not hold the entire 3-year activity feed in Compose state.
5. Map the synthetic records through an adapter to the existing repository/viewmodel interfaces, without changing production schema or scoring logic.
6. Build analytics from the supplied source records or use the provided validated aggregates as fast display caches.
7. Use immutable, offline fixtures: NO production writes, pairing, transport, cloud sync, archive restore, reward redemptions or external device actions.
8. Allow separate `REFERENCE_3Y`, `EMPTY`, `ZERO`, `ACTIVE`, `PAUSED`, `LONG_TEXT` states without mutation.
9. In Developer Mode OFF, restore the genuine production data source without changing any stored record.
10. Compare the fixture's aggregate/count invariants to the adapter's computed results in tests.

**Don't just write these JSONL files into the app's actual SQL tables.** Their schema is intentionally portable; the adapter must translate records into production view models.

## KEY SEMANTIC RULES

- `start_delta_seconds = actual_start - planned_start`: **positive = late (red)**, **negative = early (green)**.
- `active_focus_seconds` is measured focus. Paused seconds and explicit break seconds are distinct.
- `status=DONE` only when every focus block is DONE. Mixed/skipped outcomes do not receive full completion credit.
- `unknown` days show **N/A**, while `observed_zero` days show 0 activity.
- Stopwatch focus blocks have `planned_duration_seconds=null`, not an invented countdown.
- Planned and actual timestamps retain timezone offsets; the display zone is Europe/Berlin.
- Achievements derive streaks from daily confirmations and progress from event deltas.
- Index topic completions and reopens are replayable events, not raw counters.
- Scoring, BeatScore, efficiency, estimation accuracy and reward policies remain owned by the existing FocusDeck implementation.
- Synthetic activity is strictly fiction and must be visually distinguished in developer tooling.

## VISUAL COVERAGE

This pack exercises Home, Analytics Day/Week/Month/Year, focus-by-hour charts, productivity calendars, punctuality trends, Plan Execution, Time Split, category project charts, History, Timeline, Session Summary, Achievements, indexes, and empty/long-name edge cases. It also includes source event records for GPT generation feed entries but **does not recreate actual GPT payloads**.

## EXAMPLE STREAMING READER

```python
import json
with open('sessions.jsonl', encoding='utf-8') as f:
    for line in f:
        session = json.loads(line)
        # Route to a debug-only Kotlin fixture adapter; NEVER production import.
```

## RELATIONSHIPS

- `session.id` -> `blocks.session_id` and `timeline_events.session_id`.
- `session.project_id` -> `projects.id`.
- `achievement_events.achievement_id` -> `achievement_definitions.id`.
- `index_events.topic_id` -> `index_topics.id`.
- Period summary rows derive from daily rows, which reconcile to session focus totals.

Use the included manifest and validation script before each integration.

## OPTIONAL REGENERATION

```bash
python3 generate_fixtures.py 122027
python3 validate_fixtures.py
```

Run only in a disposable fixture directory. It overwrites the synthetic files in that directory, never the FocusDeck production data.
