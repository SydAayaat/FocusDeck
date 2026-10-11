# Study history and automatic logs

FocusDeck 1.8 retains this storage/deletion design. V2 JSON includes explicit timer modes and null stopwatch targets/scores; CSV appends report/mode/scope/wall/Focus Ratio metadata, and Markdown/clipboard uses N/A. Whole planned duration is unknown when stopwatch exists. See [STOPWATCH_MODE.md](STOPWATCH_MODE.md).

V1.4 keeps finalized study records on the phone and PC and automatically maintains four CSV logs on each device. Export remains available for a separate copy or selected period. You do not need to press EXPORT for the automatic logs to update. See [detailed timelines and metrics](TIMELINE_AND_FEEDBACK.md).

## Phone setup

Tap the **history icon at the top left** (or **⋮ → Study history & logs**), then **CHOOSE AUTOMATIC LOG FOLDER**. In Android's folder picker, create/select a dedicated local folder such as **Documents/FocusDeck**, then allow access to that folder. Select a folder on this phone if you want a local phone copy. A cloud provider in the picker would store the visible copy with that provider instead.

The app remembers the folder grant and writes:

- One full JSON record per finalized session attempt, with a deterministic hashed filename.
- `all-sessions.csv`: one row per attempt, with session totals and outcome counts.
- `all-blocks.csv`: one row per recorded task/break, with planned, extended budget, actual, overtime and paused milliseconds and its outcome.
- `all-events.csv`: timestamped pause/resume/task transitions and timer observations.
- `all-intervals.csv`: individual active, paused and waiting intervals with duration and endpoints.

Existing phone history is copied when the folder is selected. Subsequent completed or stopped sessions update the files automatically. A private archive is kept even if you do not select a folder, or the selected folder becomes unavailable. Android's folder grant is sufficient; this feature does not request unrestricted access to the device.

Private phone locations, normally hidden from ordinary file managers:

| Location | Contents |
| --- | --- |
| `/data/user/0/com.hax.focusdeck/files/datastore/focusdeck.preferences_pb` | Active timer, checkpointed results, pairing, command receipts and recent/outstanding report cache |
| `/data/user/0/com.hax.focusdeck/files/reports/` | Full JSON session archive, with no age/count pruning |
| `/data/user/0/com.hax.focusdeck/files/exports/all-sessions.csv` | Automatically maintained session CSV |
| `/data/user/0/com.hax.focusdeck/files/exports/all-blocks.csv` | Automatically maintained task/break CSV |
| `/data/user/0/com.hax.focusdeck/files/exports/all-events.csv` | Automatically maintained transition CSV |
| `/data/user/0/com.hax.focusdeck/files/exports/all-intervals.csv` | Automatically maintained activity interval CSV |

The chosen visible folder is separate from those private locations. It contains study reports and CSVs, not pairing credentials. The Android folder picker supplies its actual location; it is not hardcoded to a particular shared-storage path.

## PC locations

Default locations for Hax's Ubuntu account:

| Location | Contents |
| --- | --- |
| `/home/hax/.local/share/focusdeck/reports/` | Full JSON records and readable Markdown reports |
| `/home/hax/.local/share/focusdeck/exports/all-sessions.csv` | Automatically maintained session CSV |
| `/home/hax/.local/share/focusdeck/exports/all-blocks.csv` | Automatically maintained task/break CSV |
| `/home/hax/.local/share/focusdeck/exports/all-events.csv` | Automatically maintained transition CSV |
| `/home/hax/.local/share/focusdeck/exports/all-intervals.csv` | Automatically maintained activity interval CSV |
| `/home/hax/.local/share/focusdeck/daemon.json` | Latest observed phone state and pending command |
| `/home/hax/.config/focusdeck/` | Configuration, pairing secret and TLS credentials |

`FOCUSDECK_DATA` can override the PC data directory. PC CSVs are rebuilt at daemon startup and after report delivery, so older PC reports are included automatically after upgrading. No report age/count limit is imposed. Retransmitting a report does not add duplicate rows.

Diagnostic service messages are separate from study history:

```bash
journalctl --user -u focusdeck.service
```

## When saving happens

During a running session, the phone saves timer checkpoints and the timestamped transition timeline to private DataStore. A completed or stopped session creates its final JSON report, including those events, and enters all four automatic CSV logs. Timeline entries are made at meaningful transitions, not every timer tick. Future unstarted tasks are not recorded as completed task rows. A stopped session with no started blocks still gets a session row and a `session_without_blocks` placeholder in the task CSV.

Final reports are first saved transactionally with phone state. Archive/CSV/folder work runs on an IO worker outside the timer's command lock. Private archive files use Android AtomicFile. Recent delivered reports are eligible to leave the bounded DataStore cache only after their separate archive file is saved. The archive itself has no 100-session cap. Files are not rewritten every second.

If Wi-Fi or the PC is unavailable, phone saving continues. Reports queue locally and the PC saves its copy on reconnection. The PC acknowledges an atomic JSON save; a CSV failure is reported separately because CSV can be regenerated from JSON. It does not erase a safe report.

Private JSON/CSV files and PC CSV files are replaced atomically. Files in the user-selected Android folder use the selected storage provider, which may not offer atomic replacement. An interrupted visible copy can be incomplete; reopening the app or **RETRY LOG SAVING** retries it from private records, and JSON copies are checked before skipping them. If a folder grant is revoked or storage is full, the history screen shows an error. Reselect an accessible folder and retry. Do not delete the private archive to fix a shared-folder problem.

Storage grows with history. Clearing app data or uninstalling removes private records and pairing. PC records and copies in your chosen phone folder remain independently stored. After reinstalling, grant folder access again and import full JSON records to restore history. CSV alone is a readable analysis log; full JSON is the supported restore format. No cloud backup is included.

## Period summaries

The black history screen provides Day, Week, Month, Year and All views; date navigation; all-time and period focus; session and DONE counts; overtime, pauses and planned time; a focus/break ring; focus bars; and copyable full reports.

Periods use the phone's current time zone. Weeks start on Monday. Each finalized session is attributed entirely to its finish date, with day charts using its finish hour. Period grouping uses this consistent rule for both old reports without intervals and new detailed reports; it does not split activity across midnight. This rule is shown on the screen. All-time totals include every retained report; the All chart displays the latest 12 years with records. Completed and stopped sessions are included; live sessions appear after finalization. Focus excludes breaks and pauses, and includes focus overtime. Legacy `timed_out` blocks are retained and are not counted as DONE.

Recorded timer activity does not establish attention or learning quality.

## Restore older PC sessions to phone history

Before V1.3, the phone retained only the latest 100 delivered sessions plus outstanding reports. Updating migrates everything still present on the phone. Older sessions cannot be recovered from a cache that already discarded them; the PC archive can supply them:

```bash
focusdeck export --format json --output "$HOME/Downloads/focusdeck-history.json"
```

Transfer that JSON file to the phone and use **Study history & logs → IMPORT JSON**. The importer accepts the exported array or a single full report. It validates the totals, deduplicates identical attempts, and rejects conflicting records. Imports are limited to 20 MiB and 10,000 sessions per file; this is an import batch limit, not a history retention limit. Importing refreshes the phone archive, summaries and automatic CSVs. Imported history is not sent back through the phone's desktop report outbox.

## Additional exports and recovery commands

These commands read local PC reports and work with the daemon/phone offline:

```bash
# Rebuild all four automatic master CSV files, including old reports.
focusdeck refresh-logs

# Optional separate CSV copies.
focusdeck export --output "$HOME/Downloads/focusdeck-tasks.csv"
focusdeck export --kind sessions --output "$HOME/Downloads/focusdeck-sessions.csv"
focusdeck export --session YOUR_SESSION_ID --output "$HOME/Downloads/focusdeck-one-session.csv"
focusdeck export --kind events --output "$HOME/Downloads/focusdeck-events.csv"
focusdeck export --kind intervals --output "$HOME/Downloads/focusdeck-intervals.csv"
```

CSV encoding is UTF-8 with a BOM, standard escaped cells and CRLF rows. Durations are exact integer milliseconds; timestamps are UTC ISO-8601 with millisecond precision. Session IDs and the `session_id:started_at_ms` report key preserve the link to the original plan and distinguish repeated attempts. Formula-looking text is prefixed with an apostrophe for spreadsheet use. The phone and PC use the same column schema; their CSV bytes/quoting need not be identical. Master CSVs are regenerated snapshots of all valid local final reports, not duplicate-prone append-only files.

If a PC archive JSON is damaged, CSV regeneration stops and preserves the prior CSV rather than silently emitting an incomplete history. Restore that JSON from a known good phone/backup copy, then run `focusdeck refresh-logs`. Keep JSON copies alongside CSV if you want restorable long-term records.

V1.5 adds performance/reward fields to block and session CSVs, including explicit quality, capped BeatScore/rank, raw pace/focus, comparison/criteria/contract/policy JSON, benchmark/PB delta, reward tier/actions, acknowledgement/skip timestamps/local day and acknowledged daily-volume snapshots. Event/interval CSVs also log quality_review and reward gaps. Pending gates are saved in the phone checkpoint store; finalized reports regenerate the CSVs. Paths, optional folder permission and PC archival behavior remain unchanged. See REWARDS_AND_COMPETITION.md.

## V1.6 deletion and clipboard

Use the phone history panel's DELETE SESSION / DELETE ALL LOGS or PC delete-logs --session / --key / --all. Saved deletion records merge and suppress report replay/import, while automatic JSON/Markdown and all four CSVs are removed/regenerated on both devices when synced. Independent exports are retained. copy-report places a full Markdown report on the PC clipboard for Ctrl+V into GPT. See UPDATE_1.6.md for exact commands, attempt selection, offline behavior and file-removal scope.
