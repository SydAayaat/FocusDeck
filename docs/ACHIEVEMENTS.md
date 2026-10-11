# Achievements · FocusDeck 1.9.1

The trophy in the existing top-left navigation opens a normal page. HOME/FOCUS returns to the executor; timers continue locally while viewing achievements. The original history button, reward workflow, session/report contracts, pairing, application ID and report deletion tombstones remain intact. Session/report versions remain 1/2; achievements has explicit command **contracts 1/2**, negotiated over the existing wire/pairing protocol 1. No GitHub operations are needed.

## Page and measurement rules

The approved visual references drive the black background, cyan category underline, outlined overview cards, two-column visual tiles where space permits, large icon rings, signed record gaps, detail chart, Today card and inline detailed history. At narrow widths tiles become one column. Long names and objectives wrap. Categories are All, Discipline, Health, Learning, Focus, Projects and College. Tap tabs or swipe left across the cards to advance, right to return; the selected tab scrolls into view. Filtering affects tiles only. Long-press a card for its menu. EDIT LAYOUT enables hold-and-drag ordering; Move earlier/later also works across long lists. Compact pairs cards on wider screens; Wide spans the row; Full-width adds larger content. Order is global across filters; per-card sizes/order persist and accompany PC snapshots. Swipe is disabled in Edit Layout. Drag works between visible cards; use the menu to reach off-screen positions. ARCHIVED opens a restore list.

Built-ins: Porn Free, Daily Objectives Complete, Gym, Cardio, German, Guitar, 5+ Focus Blocks, 8+ Focus Blocks, 3+ Hours Active Focus, and the supplied Custom Compute project. College starts empty: example plan topic labels were not treated as a confirmed subject configuration. Project and college names come from definitions; no YouTube/screen-usage feature or permissions were added.

Current counts successful finalized days plus a currently MET day. Today's PENDING does not close yesterday's streak before midnight. Best is max(current, best completed segment). Record gap is **current − best completed segment**, which preserves the previous benchmark during a new record. Below/equal/above uses an explicit negative/zero/positive value and red/neutral/green styling. No completed segment means gap/average N/A; an uninitialized abstinence streak also has current/best N/A. Average includes **completed segments only**. Chart bars are completed streaks in chronological order; selecting a bar highlights it, moves the marker and reveals metadata immediately below. Detailed history is on the same scrollable page. Focus totals in segment metadata are whole-day context; events carry the supplied project/subject tasks and completion counts, without inventing focus-block attribution.

The overview Records count is **the number of visible achievements currently above their previous completed personal best**. Active streaks and denominators derive from the visible definition library, independent of the selected filter. Daily target percentage is met objective count / supplied objective count, not partial weighted progress. No target is N/A.

A new record ring becomes static green with a restrained glow. Its 1.1-second halo runs once per achievement/streak when first seen, with a durable acknowledgment shared by tiles and detail. Navigating back, reopening, or increasing the same record does not continuously animate. No confetti, XP, social sharing or fabricated records.

## Durable state and day closure

There is no Room database in FocusDeck. The existing Preferences DataStore gains an `achievements_v1` JSON key, independent of `deck_v1`. Both keys update atomically before command ACKs. Storage/snapshot version 1 remains readable: added `cardOrder`, `cardSizes`, `deletedIds` and `retiredCommands` have empty defaults for old stores. Old deck, preferences, device identity, pairing and archives are decoded unchanged. No invented migration/backfill: the achievement calendar begins on first launch of this version. Existing reports are not interpreted as manual German/Gym/project events.

Stored entities are definitions/metadata, raw received events, command-ID receipts, all target revisions, actual daily activity, permanent finalized daily statuses/target IDs, completed streak segments, focus cursors and viewed-record acknowledgments. Nothing is age-pruned. Archiving any card through contract 2 hides its tile and makes new days neutral while preserving all its history. Restore unhides it; custom re-upsert can also unarchive. Built-in definitions cannot be replaced. Permanent deletion removes the definition, its events/segments/daily per-card evidence/layout/record acknowledgments and its per-card command bodies. Deleted IDs and retired command IDs prevent replay resurrection; a new card needs a new ID. Shared closed-day target revisions/contracts and global measured focus totals remain audit history and can reference a deleted ID. Study session reports are independent. Archive/delete is rejected when the latest current/future target references the card; replace those targets first. Definition updates affect open/future days; closed statuses retain their original interpretation.

GPT targets and actual activity are separate. The latest target is selected by `(timestamp instant, command id lexicographically)` regardless of receipt order; all revisions retain timestamp/source/objectives. A harder replacement can change MET to PENDING; an easier one can change PENDING to MET. No event or focus activity disappears. At local midnight the final revision becomes permanent MET, UNMET or NO_TARGET. NO_TARGET days neither extend nor break Daily Objectives. Projects/college count successful **scheduled** days; unscheduled days are neutral. An unsolicited completed objective also establishes a successful work day. `daily_participation:true` explicitly makes every day required.

The repository schedules the next local midnight while idle/background, rather than continuous idle polling. The foreground clock refreshes at most once per minute while idle; active timer scheduling/checkpoints remain as before. Offline restart catches up missed dates before rendering/accepting new commands. During active focus, measurements are captured before closing the preceding day. DST boundaries use `LocalDate.atStartOfDay(ZoneId.systemDefault())` rather than fixed 24-hour days. A timezone change affects open-day assignment and display; finalized date/zone/status remain immutable. Abstinence timestamps are instants, with exact millisecond intervals and local display.

Closed-day target/activity edits are rejected explicitly; this contract does not retroactively reopen finalized days. Targets may be today or up to 366 days ahead; their write timestamp cannot be in the future. Ordinary completed events must be for today. Abstinence initialization may provide a known past start; subsequent breaks must be distinct chronological instants at or before now. Missing time, completion or history is never guessed.

## Automatic focus evidence

DONE focus blocks increment the local **completion day's** count, across countdown and stopwatch sessions; breaks and stopped blocks never increment it. Measured active focus time counts during execution, even before DONE, and excludes pauses/breaks/reboot gaps. The thresholds are the requested 5/8 DONE blocks and 3 active hours. Rewards, BeatScore, completion authority and timer selection do not depend on achievements.

Per-attempt/block cumulative cursors provide permanent replay protection; timer checkpoints/command transitions add only newly observed monotonic active duration. New intervals are split at local midnight. Civil timestamps assign already-measured duration to dates; they never create duration. After a civil-clock correction, closed contracts are not rewritten; ambiguous intervals can have incomplete date allocation. Reboot recovery follows the existing paused checkpoint behavior and never credits powered-off time. Live UI previews between durable checkpoints are read-only. The existing checkpoint interval still bounds abrupt-power-loss recovery precision.

Study log deletion retains this independent activity ledger: deleting reports must not erase earned activity or cause it to be reimported. Achievement sync does not import or resurrect deleted reports. It never modifies report archives/tombstones.

## CLI and wire

```bash
focusdeck achievements validate command.json
focusdeck achievements apply - <<'FOCUSDECK_ACHIEVEMENT'
{"achievement_version":1,"operation":"event","id":"unique-german-event-id","timestamp":"2026-10-07T14:00:00+02:00","source":"GPT","achievement_id":"german","event":"completed"}
FOCUSDECK_ACHIEVEMENT
focusdeck achievements list
focusdeck achievements history german
```

Use a fresh stable `id` for each intended write; reuse that id/content to safely retry uncertain delivery. Same id with different content is rejected permanently. The timestamp examples are illustrative: use the real event time and the phone's current local date. `apply` requires the companion daemon and connected compatible phone; it follows the existing one-command pending/ACK policy. Offline new commands are rejected, not silently queued. Pending uncertain deliveries replay after reconnect. Android remains authoritative and existing events/automatic activity continue offline.

`list` and `history` read the automatic PC `achievements.json` cache without a running daemon/phone and identify its `as_of_ms`, local zone and cached status. Queries do not pretend the snapshot is live. History includes completed segments, events, day evidence and target revisions. These unbounded files bypass the small control-wire size limit intentionally.

Contract: `protocol/achievement-command.schema.json`. Commands require `achievement_version` 1 or 2, operation, id, timezone-qualified timestamp; optional source defaults GPT. The original contract-1 operations (contract-2 additions below):

| Operation | Payload |
| --- | --- |
| `upsert` | `definition:{id,title,category:projects\|college,kind:scheduled,description?,daily_participation?,archived?,metadata?}` |
| `archive` | `achievement_id` of a dynamic definition |
| `event` | `achievement_id`, `event:initialize\|streak_break\|completed\|objective_completed`, optional positive `amount`, `text` |
| `daily_target` | `date:YYYY-MM-DD`, nonempty `objectives` array |

Objective: `{kind:focus_blocks|focus_time|achievement,minimum:positive integer,text?:exact task label,achievement_id?:definition id}`. Focus time minimum uses **milliseconds**. Achievement references require a manually completed daily/scheduled definition; abstinence/automatic/contract references and duplicate objective keys are rejected. For a project with several deliverables, use a minimum count and record distinct objective events, or one completion event for the supplied combined deliverable. Focus goals must omit achievement_id. Unsupported combinations/unknown fields are rejected by CLI/schema and the phone checks its actual definition/day state.

Phone/desktop 1.9.1 hello advertises `supported_achievement_versions:[1,2]`; each command requires its own advertised version, while snapshots stay format 1. An older contract-1 phone is rejected before sending or replaying a contract-2 write. Wire/pairing stays 1; commands use existing `type:command,action:achievements,achievement:{...}` and existing ACK. A phone/desktop without the capability receives no achievement commands/snapshots. Session v2 gating stays independent.

Snapshots use `type:achievement_snapshot,achievement_version:1,snapshot_id,index,count,data` with ordered Unicode-safe ≤50,000-character chunks. This preserves the existing 1 MiB message bound without limiting retained history. Each chunk awaits `achievement_snapshot_ack` (snapshot_id/index), keeping transport memory bounded; the final ACK follows durable cache replacement. The PC streams chunks to a temporary file, validates the final payload and atomically replaces its cache only when complete. Disconnect/malformed/out-of-order transfers retain the previous snapshot. Snapshots contain ledger, phone-calculated summaries, local zone and observation time. They reuse the pinned TLS WebSocket; no cloud/new transport. Checkpoints alone do not trigger immediate network sends; snapshots piggyback the existing periodic sender.

Executable command examples live under `examples/achievements/`. Replace dates/timestamps/IDs before applying, and create a subject before referencing it. The Robotics 1 creation example is an explicit request payload, not an automatically seeded college subject.

## Contract 2 card commands

Contract 1 remains restricted to scheduled project/college definitions. The dispatcher `achievement-command.schema.json` resolves the explicit v1/v2 schemas. Contract 2 extends `upsert` to `discipline`, `health`, `learning`, `focus`, `projects`, `college` with manual `daily`, `scheduled` or `abstinence` kinds. Categories do not imply measurement: a custom Focus card is manual. No new automatic focus rules or arbitrary executable logic are inferred. Daily cards need explicit completion each day; scheduled cards use scheduled-day neutrality; abstinence cards need explicit initialization and chronological breaks. Existing definition category/kind is immutable: use a new ID to change them.

```bash
focusdeck achievements archive german
focusdeck achievements restore german
focusdeck achievements delete german --yes
```

The shortcuts generate fresh command IDs and timestamps. For retryable GPT writes, use complete contract-2 JSON with a stable ID via `apply`; permanent `delete` requires `confirm:true`. Obtain the user's explicit intent before preparing a permanent deletion. Phone long-press offers Archive and Delete permanently with a confirmation dialog; ARCHIVED restores. Successful phone mutations persist before ACK and update the authoritative PC snapshot over the existing connection. Offline PC list/history may show an older timestamp until sync completes. Phone changes work offline, CLI writes require a connected compatible phone; no offline write queue is introduced.

## Validation and device work

See [UPDATE_1.9.1.md](UPDATE_1.9.1.md) for current focused results and the modified-file map. Existing Compose instrumentation tests cover the previous achievement page; new touch gestures have not run on a device. Verify S24 Ultra swipe/drag/menus/sizes in portrait, landscape and large font mode, reopen persistence, and pinned LAN snapshot/removal synchronization. Build/install with your original signing key; retain pairing, app data and PC archives.


## FocusDeck 1.10 cumulative progress and motion

Current achievement command contracts are 1/2/3. Contract 3 adds `progress_set`, `progress_increment`, capability flags and initial progress in definitions. Commands 1/2 remain accepted; progress is capability-gated. Read [the 1.10 update](UPDATE_1.10.0.md) and [current GPT achievement rules](GPT_ACHIEVEMENTS_INSTRUCTIONS.md) before generating progress commands. Targets are supplied by project configuration; never assume Custom Compute has 100 topics. Progress does not replace the daily/streak model. All archive/restore/delete, replay, layout and closed-day safeguards remain.
