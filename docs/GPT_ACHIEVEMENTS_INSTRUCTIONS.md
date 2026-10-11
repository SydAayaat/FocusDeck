# FocusDeck GPT Project Instructions — Achievement contracts 1/2/3/4

Use with the session instructions. FocusDeck 1.12.0 has offline, phone-authoritative achievements over its existing pinned LAN connection. GPT supplies explicit manual evidence, known abstinence timestamps, custom definitions and daily contracts. Never fabricate activity, history, starting dates, project or subject names. Achievements never alter timers, rewards, BeatScore or DONE authority.

Built-in measurement definitions: `porn_free`, `daily_objectives`, `gym`, `cardio`, `german`, `guitar`, `focus_5`, `focus_8`, `focus_3h`. These can be archived/restored/deleted with contract 2, but cannot be replaced by upsert. `custom_compute` is an initially seeded custom scheduled project. German is simply German. Automatic focus achievements need no GPT events: active focus excludes breaks/pauses/reboot gaps; DONE counts span countdown and stopwatch. Custom cards do not add automatic trackers or permissions.

Query `focusdeck achievements list` / `focusdeck achievements history ID` when history matters. These work offline using a **cached** snapshot with observation timestamp/timezone. Current − best completed segment defines record gap; completed segments alone define average; missing history is N/A. Do not invent benchmarks from sample UI values.

Emit COMPLETE JSON via `focusdeck achievements apply - <<'FOCUSDECK_ACHIEVEMENT'` … `FOCUSDECK_ACHIEVEMENT`, or a file. Validate via `focusdeck achievements validate FILE`. Required: `achievement_version` 1, 2, 3 or 4, operation, unique stable command id, timezone-aware timestamp at/before now. Source defaults GPT. Reuse identical id/content only to retry uncertain delivery; changed content with an existing receipt is rejected. Deleted per-card receipts are retired and ignored on replay. Contract-2 writes need a connected 1.9.1+ phone; contract-1 writes need 1.9+. Pending commands replay only to compatible phones. Do not claim delivery without a success ACK. New offline writes are rejected, not queued.

Operations:

- `event`: achievement_id, event `completed`/`objective_completed`, optional positive amount and exact text. Ordinary activity must be today in the phone's local zone. Closed days are immutable; obtain missing timing rather than silently backdating.
- Abstinence cards use `event:initialize` once with a known start; then `streak_break` at distinct chronological instants. Each break closes the old interval and starts another. Never guess a starting date. This also applies to Porn Free.
- Contract-2 `upsert`: definition `{id,title,category,kind,description?,daily_participation?,archived?,metadata?:{string:string}}`. Categories: `discipline`, `health`, `learning`, `focus`, `projects`, `college`. Kinds: `daily`, `scheduled` (default), `abstinence`. Daily requires explicit completed-day evidence; scheduled missing days are neutral unless targeted or daily_participation is true; abstinence uses exact timestamp intervals. Category is presentation, not an automatic measurement rule. Custom Focus cards are manual. Use supplied real names, create before referencing, and never derive achievements from arbitrary timer topic titles. Existing category/kind cannot change; use a fresh card ID instead. Contract 1 preserves its scheduled-project/college creation rules.
- `daily_target`: date YYYY-MM-DD and 1–100 objectives `{kind:"focus_blocks"|"focus_time"|"achievement",minimum:positive integer,text?:exact objective,achievement_id?:id}`. Focus time uses milliseconds. Achievement references require a visible manual daily/scheduled card. No duplicate keys, abstinence/automatic/contract references, or achievement_id on focus objectives.
- Contract-2 `archive` + achievement_id hides any card and retains its history. `restore` unhides an archived card. Custom upsert can also unarchive; built-in rules cannot be replaced. Prefer archive when the user wants to stop displaying a card while retaining history.
- Contract-2 `delete` + achievement_id + `confirm:true` permanently removes that card's evidence/layout and retires its ID. Only prepare this after explicit deletion intent. Latest current/future targets must first be replaced without referencing the card; otherwise removal is rejected. Old command replay cannot restore it, and a new card needs a new ID. Study reports, shared closed daily contracts/revisions and global focus totals remain; closed audit references can still mention the deleted ID. Do not claim this deletes every reference to a name.

Terminal shortcuts: `focusdeck achievements archive ID`, `focusdeck achievements restore ID`, `focusdeck achievements delete ID --yes`. Shortcuts generate fresh IDs/timestamps. Stable-ID JSON is preferable when retrying uncertain delivery. On the phone, long-press opens sizes/order/archive/delete; Edit Layout enables drag; ARCHIVED offers restore. These actions save locally, and subsequent authoritative snapshots update the PC cache.

Latest target timestamp wins; ties use lexicographically greatest command id. All revisions persist and actual work remains separate. A replacement may change today's MET/PENDING until local midnight; the final revision then closes permanently as MET/UNMET/NO_TARGET. NO_TARGET is neutral. Targets can be today through the next 366 days. For multiple deliverables, specify minimum count and distinct events, or one event for the complete combined deliverable.

Example creation (replace timestamp/id with real values):

```json
{"achievement_version":2,"operation":"upsert","id":"create-mobility-20261007","timestamp":"2026-10-07T12:00:00Z","source":"GPT","definition":{"id":"mobility","title":"Mobility","category":"health","kind":"daily","description":"Complete the agreed mobility routine"}}
```

Full rules: `docs/ACHIEVEMENTS.md`, `docs/UPDATE_1.9.1.md`, explicit v1/v2/v3 command schemas and `examples/achievements/`. Wire/pairing stays 1; session/report stays 1/2; storage/snapshot stays 1 with backward-default layout/tombstone fields. Deleting study reports does not erase or reimport achievement activity. Never delete logs as routine setup.


## Cumulative progress (contract 3)

`streak_enabled` defaults true; `progress_enabled` defaults false. For custom definitions, specify both explicitly for streak-only, progress-only or hybrid. A definition must enable at least one. Optional initial `progress` contains `current_value`, positive `target_value`, and `unit`. Definition updates retain history. To adjust totals after creation, use the dedicated SET operation. Existing built-in measurement rules stay immutable: update German's cumulative data using `progress_set`, not `upsert`.

Prefer authoritative totals:

```json
{"achievement_version":3,"operation":"progress_set","id":"german-total-unique","timestamp":"2026-10-07T12:00:00Z","source":"GPT","achievement_id":"german","current_value":200,"target_value":1250,"unit":"words"}
```

SET can supply any nonempty combination of current_value, target_value, unit. Unspecified fields stay unchanged. Totals are ordered by timestamp then id; stale SETs are acknowledged/receipted without changing newer data. A repeated semantic total adds no redundant history. A positive integer `progress_increment` uses `amount` (1..100000), requires enabled progress, rejects timestamps older than the latest total, and is deduplicated by the existing stable command id. Equal-timestamp unique increments all apply once. Never issue a new id merely to retry an increment.

German defaults to 0/1250 words; no learned words are fabricated. Custom Compute defaults to 0 with an unset target in index topics: obtain the actual master-index count from the project and explicitly supply it. The 20/100 fixture is only an example. Any project or college card can use any valid total without rebuilding the APK. Do not fabricate course or project names.

Current values must be finite, nonnegative and <=1e12; targets must be finite, positive and <=1e12. Unit is 1..100 nonblank characters. Visual percentages clamp to 0..100; the actual total is retained. Completing a goal preserves the card and timestamps completion. An explicitly enlarged target can reopen it; earlier completion and target history remain.

Cumulative progress is independent of daily activity: increasing a total does not invent a completed study day. Send a separate existing `event` only when the user confirms the daily activity. The latest supplied daily contract remains independently evaluated at midnight.

Use `focusdeck achievements list` for cached progress summaries and `history ID` for the cached progress_history plus existing events, streaks, days and targets. The cache includes its observation clock. A successful command ACK proves ledger application; a new snapshot updates the PC cache afterward. Contract-3 writes are capability-gated and cannot be sent to an older phone. Upgrade phone and desktop together.

Examples: `examples/achievements/german-progress-v3.json`, `custom_compute-progress-v3.json`, `progress-only-v3.json`. Replace fixture IDs/timestamps with current values before real use. Wire/pairing remains 1; command contract 3 is independent of session/report 1/2. Storage/snapshot 1 gains backward-default capability, progress, history and migration fields.
## 11. Card icons and global appearance (FocusDeck 1.11.0)

The phone chooses a saved global palette (cyan, monochrome, gold, blue, purple, green, rose, orange) in Display settings. Timer refresh 30/60/120 is separate from navigation's up-to-120 Hz hint. Do not promise measured FPS or pixel-identical rendering without actual device verification. Full motion uses short ring opening glow/sweep and record/completion pulse followed by a static halo; Reduced/Off and Android animation scale apply. GPT commands do not change those settings.

For an explicitly requested icon, use `focusdeck achievements icons` to list bundled names and `focusdeck achievements icon ACHIEVEMENT_ID --name NAME` to send one. Real names include book-open, laptop, ban, trophy, heart, guitar, dumbbell, timer, cpu, circuit-board and graduation-cap. Do not invent a name. All built-in/custom cards may change appearance without changing measurements.

For a PC image, use `focusdeck achievements icon ACHIEVEMENT_ID --file /actual/path.png --output /actual/icon-command.json`, then validate/apply the saved file. PNG/JPEG/WebP importer normalizes to PNG <=256×256 and <=64 KiB. Obtain the actual file path if missing; do not claim to have transferred a file that is inaccessible. LAN carries bytes; ADB is not necessary for icons. Arbitrary SVG URLs/files are not accepted as command image data; bundled vectors use --name. `--clear` restores the default icon.

A JSON icon command has `achievement_version:4`, `operation:"icon_set"`, id/timestamp/source, achievement_id, and exactly one icon object: `{"kind":"builtin","name":"laptop"}`, `{"kind":"image","data_base64":"<actual PNG base64>"}` or `{"kind":"default"}`. The placeholder is explanatory; never emit it in an actual command. Generate base64 from verified real image bytes rather than writing a fictitious payload. Default/builtin objects cannot carry image data; unknown names/types/oversized images reject.

Save the complete command and repeat its identical ID/timestamp/bytes for uncertain-delivery retry. Immediate CLI sends also retain/print an exact retry JSON file. Latest timestamp then lexical ID orders icon SETs; stale commands cannot undo a newer default reset. Archive/restore retains icons, deletion removes them and retires their receipts. Icon changes never create activity/progress, change builtin rules or fabricate learning evidence. Older peers cannot accept/replay contract 4; do not downgrade or claim delivery without ACK.

Developer preview (1.12.0) is a separate temporary synthetic ledger. Never report its sample progress/streak/history as actual work or reward evidence. Sounds remain phone settings; command contracts stay 1–4.
