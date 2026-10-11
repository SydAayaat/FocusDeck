# Architecture and engineering decisions

FocusDeck 1.8 adds session/report v2 while preserving wire/pairing v1. Stopwatch uses the same authoritative monotonic engine with a nullable budget, separate Compose screen and completion reward context. Both peers negotiate supported session versions; old phones cannot receive silently downgraded v2 plans. See [STOPWATCH_MODE.md](STOPWATCH_MODE.md).

## Data ownership

The Android app is the active session authority. It holds the plan, current block, timer anchors, results, deduplication receipts and report outbox. The desktop stores the latest observed summary, a single pending control delivery and completed reports. It never pushes a cached timer snapshot onto Android after a restart.

Communication has two paths:

- Phone → desktop: encrypted, certificate-pinned WebSocket on a specific private LAN IP. HTTP Upgrade requires a 256-bit random bearer token. After authentication, the phone identifies itself and sends its current state and unsent reports.
- Desktop CLI → daemon: owner-only Unix domain socket in `$XDG_RUNTIME_DIR` (or the private data directory). No HTTP or LAN control endpoint exists. The daemon sends versioned commands to the phone and returns the durable acknowledgement.

No Internet access is needed at runtime. Gradle, Maven and pip downloads are installation-time dependencies.

## Deterministic timer

`TimerEngine.kt` is a pure state machine. `ClockNow` supplies wall time, elapsedRealtime (including sleep) and Android boot count. The remaining time is:

`budget - (saved active milliseconds + monotonic time since active anchor)`

Rendering ticks only request a new display; they do not subtract one second. Durable checkpoints advance the anchor and save accumulated active/paused time: every 10 seconds when active and visible, 30 seconds when active in background, and 60 seconds while paused. Idle/ready/finished states do not poll or periodically save. Commands and all block transitions save immediately. Rotation does not recreate the application repository, and normal process recreation reads the durable snapshot. After sleep or process recreation beyond a deadline, the same block remains active with its full recorded overtime. Expiry never advances a block.

Pause accrues paused time without advancing active time. Resume anchors a new active segment. DONE closes the current block using current active time and transitions immediately. +5 minutes increases its budget but keeps original planned time. STOP writes a partial result for a started block and a stopped session report. Expiry turns the dial red, counts excess upward and emits one alert for that deadline; it never closes a block. Pause also freezes overtime. Extending the budget can clear overtime until the new deadline. Legacy timed_out history remains readable. auto_advance now controls starting the next block after DONE only.

At a reboot, monotonic time resets. The engine detects a different boot count and recovers paused at the last checkpoint. Approximately 10 seconds of visible active time, 30 seconds of background active time, or 60 seconds of paused time before reboot may be absent when Android was scheduling normally; deep sleep can widen that recovery window. It does not invent focus during powered-off time. Wall-clock timestamps may reflect manual/NTP time changes; active and paused measurements remain based on monotonic time. The report wall span is the nonnegative difference between wall-clock start and finish.

## Persistence choice

Preferences DataStore holds the active session, command receipts and report outbox as one transactional serialized `StoredDeck` value. The command receipt, timeline event and state update are saved in the same edit before ACK. Unsent reports are retained until the desktop confirms its atomic JSON save. The recent delivered cache retains the latest 10 reports, plus all not yet archived, so detailed reports do not inflate every timer checkpoint; a separate AtomicFile JSON archive retains every finalized report without age/count pruning. Startup migrates retained DataStore reports into this archive. Previously discarded delivered reports can be imported from PC JSON.

An independent IO worker handles final-report archiving, automatic CSV snapshots and optional mirroring to a user-selected Storage Access Framework folder, outside the timer's command mutex. Private phone and PC files are atomic; user-selected provider files are best-effort copies with retry from the private archive. Import and archive work share a separate IO mutex. There is no history/CSV ticker or graph animation. Calendar summaries use final reports, the local zone and Monday-start weeks, assigning a whole session to its finish date consistently across old aggregate reports and new timelines. Session, task, event and interval CSV schemas match across devices; report keys distinguish attempts and deduplicate retransmissions. See [storage details](HISTORY_AND_LOGS.md).

New sessions retain up to 2,000 sequence-ordered transition events with UTC and monotonic timestamps, boot identity and per-block cumulative counters. Intervals derive from counter differences within a boot; extension/overtime observations merge into the surrounding continuous interval. Migration, reboot and event-limit gaps explicitly mark coverage partial. Missing pause/streak metrics remain unknown. Command replay returns the saved transaction without adding another event. No new per-tick log writes are introduced. See [timeline semantics](TIMELINE_AND_FEEDBACK.md).

The private app data includes pairing credentials. Android backups are disabled. Clearing app data/uninstalling removes pairing and private history; copies in the chosen phone folder and PC archive remain. The desktop uses private directories, 0600 files, atomic replace plus fsync, and a process lock. Report filenames are hashes of session ID and start time; incoming names never become paths. Automatic CSV logs regenerate at daemon startup and after report delivery, while explicit export/refresh commands work directly from local reports without the daemon.

## Delivery and reconnection

One mutating command can be pending. It has a UUID. The daemon persists it before transmission. Android stores the last 256 receipts atomically with state; replay returns the old receipt and does not repeat the action. When the daemon restarts, it retransmits the exact same pending UUID. The single-pending-command constraint ensures a pending receipt cannot be displaced by a stream of newer desktop commands.

Offline commands are rejected, not queued. If delivery succeeds but an ACK is lost, the CLI reports uncertainty. Read `status`, reconnect and let replay resolve it; blindly issuing another `next` would be a new command and could advance another block. Phone taps are briefly disabled while a local command is being persisted. Commands cannot replace an active, paused or prepared session.

Reports are keyed by session ID plus actual session start. JSON is saved atomically before `report_ack`; retries overwrite the same deterministic file. Markdown is regenerated from the authoritative JSON. The Android outbox sends a bounded number of reports per synchronization cycle and retries while acknowledgement is missing. A desktop crash between save and ACK therefore produces a harmless retransmission.

Connection state is independent of timer state. The reconnect loop backs off from about 1 to 30 seconds with jitter while visible, or to 120 seconds in background; bringing the UI forward wakes a background retry. Status observed on the PC is only as fresh as its last phone update and is labelled cached when offline.

## Lifecycle and battery

Opening the activity starts a visible foreground service of type `specialUse`, describing the user-started focus cockpit/LAN connection. It stays available to receive a PC schedule while the user works. Display settings include screen-awake, immersive mode, optional sound and a dim desk view. PowerPolicy schedules a 1-second visible active clock, 30-second background checks and 60-second paused checks; idle states wait for events. MainActivity marks visibility through onStart/onStop. Boot count is cached once per process rather than queried every tick. Notifications update on state revision changes instead of durable checkpoints; Android owns their chronometer. Event-driven LAN updates publish meaningful state/outbox changes immediately, with periodic refresh at 15 seconds visible-active and 60 seconds otherwise. Pending reports retry every 15 seconds. Only the desktop sends 60-second keepalive pings. Users can disconnect the service from the settings dialog; this does not cancel a session. Foreground services are not an execution guarantee: Android/One UI can still defer work, and force-stop stops network activity until reopening. Timers recover from saved deadlines when execution resumes; sounds/vibration can be delayed during deep sleep. No exact-alarm promise is made.

This is a sideloaded personal V1, targeting Android 15 / API 35 with compatibility from Android 14. Play Store publication would require separate policy review of the foreground-service declaration. No Play upload is included.

## UI and extensibility

Compose renders a thin white dial and light typography on pure #000000, without the old gradient/title or dial animation. The dim desk view has a small timer and outlined controls with an app-only brightness override and small minute-based position shifts. Window refresh preferences of 60 Hz normally and 30 Hz in dim active mode are requests; One UI selects the physical rate. It is an ordinary screen-on view, not a native Samsung AOD extension. See BATTERY.md for profiling and platform limits. Cyan is reserved for LAN connection, yellow for breaks, green for completion. A landscape layout places the dial beside controls. Countdown and stopwatch share this layout and the same footer/buttons. Stopwatch adds 60 inner clock ticks and a minute-stepped outer arc cycling each active hour; the dial is an elapsed-time scale, not a deadline. Its progress is DONE blocks and finish is N/A. Home replaces the finished-report landing screen and offers daily/week/all-time totals and a last-report link. A top-left history icon and overflow entry open calendar history; reports/timelines open on demand. There are no social features or feed.

The protocol envelope is versioned independently of the package. Pure timer logic, network transport, repository and UI are separate files. Future clients, desktop GUI or authenticated REST/MCP adapters should invoke the same control layer and preserve phone ownership. V1 deliberately does not implement multiple desktops, mDNS, smartwatch, adaptive plans or a desktop tray.

## Upstream references

- [AGP 8.9 compatibility: Gradle 8.11.1, JDK 17 and API 35](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Compose compiler plugin setup for Kotlin 2+](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [WebSocket asyncio server API](https://websockets.readthedocs.io/en/14.2/reference/asyncio/server.html)

## V1.5 competition and reward state

Reward models and RewardEngine are pure Kotlin. CommandProcessor receives the repository's complete in-memory private history snapshot, computes a proposed state/receipt transaction, and DeckRepository saves it before acknowledging. DONE on a competitive focus block closes timing and saves a pending quality result; quality declaration scores it with captured criteria/policy/contract; reward acknowledgement consumes only explicitly supplied eligible actions and advances. Reward UI never runs its own timer/network/AI loop. Quality/reward/session_reward phases have no periodic tick/checkpoint; transitions persist immediately.

Daily volume/caffeine spacing derive from deduplicated archived and current-attempt acknowledged receipts; they do not require a fragile separate counter reset at midnight. Comparisons use stable planner-supplied metadata plus exact timing targets, criteria and scoring policy; reports preserve raw values. Score/PB checks on imported reports supplement scalar/timeline validation, and desktop independently checks arithmetic before accepting a report. Pending review state persists without creating an immutable final archive record early. Stop retains that stage and offered-but-unacknowledged activities. JSON/CSV/master history and selected-folder IO remain independent of the timer mutex. Competitive event cap is 1,000 to reserve the 1 MiB wire budget for structured performance evidence.
