# FocusDeck desktop input and report handoff · 1.18.0

The desktop accepts readable indented text for **every existing session and achievement contract field**, including conditional rewards, independent session rewards, classification/scoring, comparisons, quality criteria, weights, daily objectives, progress updates and icon assignments. Full JSON remains compatible. Nothing is sent or started by a paste: it opens a local draft for review. No cloud AI or terminal is involved in parsing.

Use one fenced text block with one of four headings: `FOCUSDECK CARD`, `FOCUSDECK SESSION`, `FOCUSDECK UPDATE`, `FOCUSDECK BATCH`. Fields below the heading use YAML indentation (spaces). Use lowercase contract names; the original capitalized Card labels and simple timer-line session format remain compatible. Quote text containing `: ` or starting with YAML punctuation. No YAML tags, anchors, aliases, merge keys, duplicate keys or multiple documents. Input is bounded at 1 MiB; existing stricter contract bounds still apply. `true`/`false` are booleans, dates and timestamps remain text. Quote numeric titles and metadata values.

## Instructions to add to the GPT project

> For FocusDeck Control Center, produce one fenced text block headed FOCUSDECK CARD, SESSION, UPDATE or BATCH. Prefer the readable indented examples below over terminal commands or JSON. Use the current session-v2 and achievement-command-v4 field names and constraints for all advanced fields. Carry every approved reward, threshold, condition, alternative, eligibility gate, comparison, quality criterion and weight unchanged. Do not invent completed work, progress, history, streaks or observations. Use explicit disabled reward policies when no reward is approved. Pasting only prepares a local draft. The user reviews it and clicks Send/Apply; the phone alone executes. For feedback, ask the user to open the completed session or select it in Analytics, then click Copy for GPT or View output. Do not ask them to type session IDs or run terminal commands. Match the report's embedded session ID and report key internally before analysis. Preserve the existing reward library and planning rules.

## Cards

```text
FOCUSDECK CARD
title: Research papers
type: hybrid
category: projects
kind: scheduled
description: Read and review the research papers.
daily_participation: false
current: 0
target: 20
unit: papers
```

Type is streak, progress or hybrid. Categories: discipline, health, learning, focus, projects, college. Kind: daily, scheduled or abstinence (abstinence has no progress). Optional `id` selects an existing custom card; omission creates a fresh card ID. All definition fields are supported: description, daily_participation, archived, created_date, metadata, independent streak_enabled/progress_enabled and full progress object. Use `type` or explicit capability flags; use shortcut current/target/unit or full progress fields without duplicating keys. Metadata values must be strings.

Existing Card Builder edits protect category/kind and built-in rules. Title/icon edits do not overwrite measured progress; recording a SET is explicit. For an exact existing command, use UPDATE with `operation: upsert` and `definition:`: unlike a definition draft, this retains its supplied receipt ID. Phone validation still controls built-in rules, deleted IDs and stale updates.

## Sessions with complete reward policies

```text
FOCUSDECK SESSION
title: German practice
auto_advance: true
blocks:
  - id: vocabulary
    focus: 25m
    title: Recall the approved vocabulary list
    session_weight: 2
    quality_criteria:
      - Recall the words without looking at notes.
    reward_policy:
      enabled: true
      selection: highest_satisfied
      tiers:
        - condition: completed
          min_focus_ratio: 85
          reward:
            text: Take a short walk.
          alternatives:
            - text: Stretch gently.
        - condition: completed
          min_focus_ratio: 95
          min_beat_score: 95
          reward:
            text: Choose the approved higher-tier reward.
  - break: 5m
    title: Stretch and get water
  - id: review
    stopwatch: true
    title: Review the difficult phrases
    reward_policy:
      enabled: false
session_reward_policy:
  enabled: true
  eligibility:
    minimum_completed_blocks: 2
    require_all_focus_completed: true
    minimum_focus_ratio: 85
  tiers:
    - condition: session_completed
      reward:
        text: Enjoy the approved session reward.
```

Examples illustrate syntax, not evidence, approved reward choices or mandatory thresholds. GPT must use the actual approved reward library/plan. Session ID and creation time can be omitted for new drafts; the app supplies them. Every new template launch receives a fresh execution UUID/time, while retrying its journal entry retains the original. Explicit supplied block IDs are retained; missing IDs become b1, b2, etc. Specify explicit IDs for milestone references.

Timer shortcuts accept whole-number m/min/minutes or s/sec/seconds. `stopwatch: true` has no duration. Alternatively use full `type`, `timer_mode`, `duration_seconds` fields; do not combine shortcuts with full timer fields. All canonical session fields are supported: protocol_version, session_id, created_at, title, auto_advance, blocks, reward_strategy, comparison_key, classification, session_scoring, session_reward_policy or legacy session_reward. All block fields are supported: id, type, title, timer_mode, duration_seconds, quality_criteria, comparison, session_weight, classification, reward_policy or legacy reward_contract. Old v1 plans/contracts remain compatible; new GPT plans should use v2 text policies.

Every conditional policy field is accepted, including disabled policies, highest_satisfied/first_satisfied selection, all score bounds, Focus Ratio, Pace, personal_best/completed/module_completed/session_completed conditions, block_ids, min_completed_blocks, reward text, legacy typed actions, alternatives and all session eligibility gates. See the schemas/reference for valid combinations and limits. The app never converts an imported policy to a simpler policy. Stopwatch restrictions, milestone reference checks, time-score scope and legacy strategy gates remain enforced.

The original shorthand remains valid for simple reward-disabled plans:

```text
FOCUSDECK SESSION
Title: Calculus practice
Focus 25m | Review lecture notes
Break 5m | Stretch
Stopwatch | Review mistakes
```

Use the indented format when adding policies. Session Builder’s **Edit conditions, rewards & scoring** and each block’s **Edit all conditions & rewards** provide form fields, optional field selection, tier/alternative arrays and ordering. Full-contract validation happens before saving edits. Send prepares the phone; Start prepared starts that plan; Send & start explicitly submits and starts a fresh plan.

## Progress and actual events

```text
FOCUSDECK UPDATE
operation: progress_increment
card: german
amount: 20
```

```text
FOCUSDECK UPDATE
operation: progress_set
card: german
current: 240
target: 1250
unit: words
```

```text
FOCUSDECK UPDATE
operation: event
card: guitar
event: completed
amount: 1
text: Actual practice completed.
timestamp: 2026-10-08T12:30:00+02:00
```

`card` aliases achievement_id; current/target alias current_value/target_value on updates. Increment amounts must remain positive integers in the existing contract. Events support completed, objective_completed, initialize, streak_break; supply the actual timezone-aware timestamp for historical activity/abstinence initialization/breaks. Progress never implies daily completion.

## Daily objectives and other operations

```text
FOCUSDECK UPDATE
operation: daily_target
date: 2026-10-08
objectives:
  - kind: focus_blocks
    minimum: 4
    text: Four confirmed focus blocks.
  - kind: focus_time
    minimum: 3600000
    text: One hour of actual focus time.
  - kind: achievement
    achievement_id: guitar
    minimum: 1
    text: Complete the approved practice.
```

A target **replaces the complete objectives for that date**. Include unrelated existing objectives unless their removal was explicitly requested. focus_time minimum uses milliseconds (3600000 = one hour). Target changes do not manufacture activity or reopen closed days. UI-created daily-target drafts begin with the latest received objectives for today; review cache freshness before changing them.

```text
FOCUSDECK BATCH
- operation: icon_set
  card: german
  icon:
    kind: builtin
    name: book-open
- operation: archive
  card: old_project
```

Supported operations: upsert, daily_target, event, progress_set, progress_increment, icon_set, archive, restore, delete. DELETE requires `confirm: true` plus GUI confirmation. Icon kinds: default, builtin/name, image/data_base64. Use Card Builder’s import/library picker for local image files and packs; images do not need to be pasted as base64.

The app supplies omitted achievement_version (4), receipt id, timestamp and source for new updates. Supplied complete envelope fields are kept intact. A BATCH is an ordered YAML list of 1–100 achievement updates with unique receipt IDs; it is not a transaction. The delivery journal saves exact payloads before sending and retains acknowledged steps/receipts for manual uncertain retries. Never regenerate an update after an uncertain ACK: review/retry its saved delivery. Changing an already-used receipt into a different payload is rejected.

Card Builder → **Prepare update** covers every operation with a card selector and editable named fields. **Add measured progress** opens the selected card’s increment draft. The dedicated card forms remain available for creation, icon uploads, metadata, SET, activity, abstinence, archive/restore/delete and today objectives.

## Session output without IDs or terminal

After a final report reaches the desktop, Overview displays Session completed/ended with **View output**, **Copy for GPT**, **Save .txt**. During a new active session, the earlier result remains accessible through Recent sessions/Analytics. No report is invented while synchronization is pending.

Analytics → select a session → the same three actions. Selecting an older session from Overview switches to All history when needed instead of silently displaying a different report. Each attempt is selected by its internal exact report key; no manual identifier entry is needed.

View output opens the complete readable report in a selectable text panel. Copy output uses the desktop clipboard. Toggle **Full JSON** to inspect/copy/save the exact archived object. Save .txt/.json opens the native file chooser and writes an atomic UTF-8 copy outside private app data. Archived reports work offline. Text preserves the existing measured-time, skipped/ended/DONE, scoring, reward, timeline and feedback evidence. Cache timestamps and missing metrics stay honest.

Analytics also offers selected-session JSON/CSV export (all stored attempts with that same session ID), timeline and confirmed single/all report deletion with existing phone tombstones. Settings exports the whole archive as JSON and sessions/blocks/events/intervals CSV and rebuilds derived logs. Deleted reports cannot be copied/saved from a stale open panel.

## Desktop workflow coverage

| Prior CLI workflow | Desktop path |
| --- | --- |
| status / devices | Overview / Devices with live/cached labels |
| send / start / quick / validate | Session Builder, Send, Send & start, Start prepared; full validation before sending |
| pause / resume / next / skip_block / end_block / stop / reward_done / reward_continue | Current session controls; phone remains authority |
| achievement list / history | Achievements with full card details |
| achievement apply / validate / all nine operations | Paste UPDATE/BATCH; Card Builder Prepare update and validated form editor |
| icon list / local image / clear / saved replay | Card Builder bundled icons, imports/library/reset and delivery journal |
| reports / copy-report / per-attempt JSON | Overview / Analytics output viewer, clipboard and native text/JSON save |
| export / filtered export / refresh-logs | Settings full JSON/CSV; Analytics selected-session export; rebuild automatic CSV logs |
| delete-logs | Analytics confirmed single/all synchronized deletion |
| phone / viewer pairing / forget-device | Devices pairing copy and confirmed disconnected-phone recovery |
| start/restart installed companion | Devices Restart companion; unavailable-service banner Start companion |

The old +5-minute extension button stays removed per the earlier request. Historical extension evidence/CLI compatibility remain unchanged. Installation/uninstallation, signing/development builds and OS configuration are administrative tasks outside the daily planner interface. No terminal is required for the workflows in this table once the desktop is installed.


## Index Tracker (1.19.0)
Read GPT_INDEX_INSTRUCTIONS.md as the binding Index extension and INDEX_TRACKER.md for imports/migration. Contract 5 adds exact named-topic commands. Existing session/reward workflows and contracts 1–4 remain valid. No fabricated topics, completions or daily study evidence. Topic progress and explicitly confirmed study streaks stay independent. Use actual imported unique leaves, not assumed 126. Preserve existing IDs and numeric histories as labelled legacy evidence.

## Optional absolute schedules (1.20.0)

Session and block `schedule` may contain `planned_start`, optional `planned_end`, and optional IANA `timezone`. All timestamps include seconds and `Z` or an explicit offset. End must follow start. Block `category`, `achievement_id` and `index_topic_id` are optional associations with existing records. Missing absolute times remain Unscheduled; do not infer them from durations. Scheduling does not control execution or rewards. The phone preserves the submitted schedule in each finalized report. See ANALYTICS_AND_LAYOUTS.md.

```text
FOCUSDECK SESSION
title: Vocabulary review
schedule:
  planned_start: "2026-10-09T10:00:00+02:00"
  timezone: Europe/Berlin
blocks:
  - focus: 25m
    title: Review vocabulary
    category: learning
    achievement_id: german
    schedule:
      planned_start: "2026-10-09T10:00:00+02:00"
      planned_end: "2026-10-09T10:25:00+02:00"
      timezone: Europe/Berlin
    reward_policy:
      enabled: false
```

This is a syntax example, not an approved or automatically executed plan. Always supply the user's approved dates. Existing duration-only drafts remain valid.
