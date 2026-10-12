## Android 1.22.0 — Read-only three-year Developer Mode

Enable Developer Mode in Display settings to preview the supplied deterministic dataset across Home, Analytics, Achievements, Index, History, Timeline and Summary. Every preview uses a labeled simulated clock; **Use real data** restores the existing repository views. Synthetic records cannot enter production history, sync, rewards or genuine exports. The generator is retained as an optional utility and was not run. The existing isolated visualtest app is verified on the USB S24 Ultra; production installation remains unchanged. See [integration and verification](docs/UPDATE_1.22.0.md). Desktop remains 1.20.0.

## Android 1.21.0 — Locked native dashboard UI

Native Kotlin/Compose Home, two-track timeline and anchored popup, Day/Week/Month/Year Analytics and compact Achievements now follow the supplied locked references with real stored evidence. Existing timer/stopwatch, rewards, archives, synchronization, navigation and layout editing remain in place. Desktop stays at 1.20.0. See [update and verification](docs/UPDATE_1.21.0.md) and [install the included APK](docs/INSTALL_1.21.0.md).

## 1.20.0 — Timeline, Analytics and resizable dashboards

Overview, Achievements and Analytics share Edit Layout, edge/corner resizing, independent reorder grips and Reset Layout on desktop and Android. Layout dimensions stay local to each form factor; achievement order stays phone-owned and synchronized. Analytics adds optional schedule snapshots, source-zone daily timelines, punctuality, scope drill-down and grounded line-plus-bar charts. Existing session, reward, Index, history and device workflows remain in place. See [changes, verification and local build command](docs/UPDATE_1.20.0.md) and [scheduling metadata](docs/ANALYTICS_AND_LAYOUTS.md). This is a source delivery; no APK was built.

## Desktop 1.18.0 — complete GPT input and session output

Readable GPT text now covers conditional/session rewards, criteria, weights, daily objectives and every achievement operation. Edit all conditions in forms, send reviewed drafts to the phone, and copy/save visible complete session output from Overview or Analytics without terminal commands or typing IDs. Includes GUI filtered/full JSON/CSV export, deletion/rebuild and device recovery. See [upgrade and verification](docs/UPDATE_1.18.0.md) and [complete paste format](docs/GPT_CONTROL_CENTER_INPUT.md). Android remains 1.17.0; no new APK or re-pairing is required.

## 1.17.0 — Achievements dashboard and icon packs

Seven desktop pages: Overview, Achievements, Analytics, Card Builder, Session Builder, Devices and Settings. The approved dashboard guides the new mountain logo/shared shell, collapsible sidebar, read-only achievement grid/details and charts. Import multiple images or a ZIP pack into the reusable icon library; assign them in Card Builder or export a selection together. Transparent image icons follow the current theme on desktop and the updated phone/tablet renderer. Tray/background behavior remains available. See [upgrade and verification](docs/UPDATE_1.17.0.md); install the desktop with `bash scripts/setup-control-center.sh`. Existing pairing/history/drafts remain in place. Android source must be updated for phone/tablet tinting; no APK is included.

## Desktop 1.16.1 — FocusDeck Control Center

A responsive Qt desktop control panel for Ubuntu 26.04 LTS KDE Plasma/Wayland and Krohnkite tiling: GPT paste-to-draft, card/icon editing, session templates, phone controls, study history, device pairing and desktop palettes. No custom title bar or independent desktop timer. Phone/tablet Android remains 1.15.0. Install with `bash scripts/setup-control-center.sh`; open FocusDeck Control Center from KDE’s launcher. Closing hides the panel to its system tray icon; clicking the icon or launching again restores it. Use **Quit FocusDeck** in the tray menu or **Ctrl+Q** to exit the panel. See [tray update](docs/UPDATE_1.16.1.md). No new APK or re-pairing is required. See [UPDATE_1.16.0.md](docs/UPDATE_1.16.0.md) and [GPT paste format](docs/GPT_CONTROL_CENTER_INPUT.md).

## 1.15.0 — phone plus tablet viewer

Keep the phone as timer authority and GPT command destination; connect a read-only tablet with `focusdeck pairing --viewer`. Cards/details, progress/streaks, history and observed session status synchronize through the PC. Offline snapshots show freshness timestamps. Update desktop and both devices; existing phone pairing remains valid. See [UPDATE_1.15.0.md](docs/UPDATE_1.15.0.md) for full-source install and verification limits.

## 1.14.0 — skip and end controls

Timer/stopwatch three-dot menu: amber Skip block, red End block and End session. +5 minutes is removed. Measured time is retained; skipped/ended blocks receive no DONE credit or reward. End session saves a final partial report. Update the desktop companion for skipped reports. See [UPDATE_1.14.0.md](docs/UPDATE_1.14.0.md).

# FocusDeck 1.13.0

Android and desktop are 1.13.0, developed from the uploaded 1.7.1 source. [Stopwatch Mode](docs/STOPWATCH_MODE.md) introduces explicitly versioned v2 plans/reports while preserving v1 countdown plans, history and pairing. See the [source audit](docs/PROJECT_STATUS.md). Run `python3 scripts/check-environment.py` before installation.

Native Android focus cockpit for Hax's S24 Ultra, with an Ubuntu LAN companion. The UI follows the supplied minimal dial reference: light timer text, a restrained ring, DONE and PAUSE, and a session report that returns to the PC for GPT feedback.

**Current update:** [1.13.0](docs/UPDATE_1.13.0.md) implements the three supplied Home/history/report layouts, global immersive mode, icon-only achievement ring centers and shared motion. This delivery is source-only; the default install script builds on your PC, then installs in place.

**Previous update:** [1.12.0](docs/UPDATE_1.12.0.md) adds static achievement halos, detail sparkles, a scrolling Overview in landscape, built-in/custom sounds, and an isolated developer sandbox.

**Previous update:** [1.11.0](docs/UPDATE_1.11.0.md) adds global palettes, GPT/CLI card icons, thicker halo rings, navigation refresh hints and UI-thread performance work. See the update for device verification limits.

**Previous delivery status:** [1.10.0](docs/UPDATE_1.10.0.md) adds persistent cumulative progress, configurable targets, progress-only/hybrid achievements, native cyan/green rings, same-page progress and streak history, synchronized category pager, animated grid layout and saved Full/Reduced/Off motion. Progress commands require achievement contract 3; commands 1/2, wire/pairing 1, timer/reward contracts and existing card management remain compatible. See the update report for actual build/test results and pending physical-device checks.

The 1.8.2 stopwatch uses the countdown layout and outlined controls, with inner clock ticks and a white arc advancing once per active minute (one rotation per hour). Its session progress counts DONE blocks and estimated finish stays N/A. See [UI changes](docs/UPDATE_1.8.2.md).

The 1.8.1 patch hardens report totals, malformed reward evidence, native-compatible Focus Ratio rounding and countdown budgets. See [patch notes](docs/UPDATE_1.8.1.md).

For an existing installation, preserve its signing key/app data and follow [the upgrade guide](docs/UPDATE_1.8.md). See [rewards and competition](docs/REWARDS_AND_COMPETITION.md), [GPT project instructions](docs/GPT_PROJECT_INSTRUCTIONS.md), [detailed timelines and feedback](docs/TIMELINE_AND_FEEDBACK.md), [automatic logs and storage locations](docs/HISTORY_AND_LOGS.md), and [battery measurement commands and AOD limits](docs/BATTERY.md).

## First command

Extract the ZIP, enter `FocusDeck/`, and run:

```bash
./scripts/setup-desktop.sh
```

The script installs the desktop package into a private Python virtual environment, generates a private token and TLS certificate, detects a private LAN IP and installs a systemd user service. It prints the pairing JSON. It needs Python 3, venv support, OpenSSL, Internet access for installation dependencies, and an Ubuntu user systemd session. Runtime operation is entirely local.

If prerequisites are missing:

```bash
sudo apt install python3-venv openssl curl unzip openjdk-17-jdk
```

Build and install the Android app in one command with the Android SDK as described in [setup](docs/SETUP.md):

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
```

Open FocusDeck on the phone, grant notification permission, select **Pair desktop**, and paste the output from `focusdeck pairing`. Use a trusted private transfer; pairing JSON contains a secret. Allow only your LAN subnet through an enabled firewall. The phone connects to the desktop using `wss://PC-IP:8765/v1/device` and the pinned certificate.

```bash
focusdeck status
focusdeck start examples/device-smoke-test.json
```

## Daily workflow

```bash
focusdeck validate plan.json
focusdeck start plan.json
focusdeck pause
focusdeck resume
focusdeck next           # same meaning as phone DONE
focusdeck extend         # +5 minutes on a countdown block; stopwatch rejects extension
focusdeck copy-report        # full Markdown report on clipboard; then Ctrl+V in GPT
focusdeck reports --latest   # print Markdown in terminal
focusdeck reports --latest --json
```

`focusdeck send plan.json` prepares a session without starting it. The phone displays START; `focusdeck resume` also starts it. `focusdeck quick --title "ROB Index" --task "Topics 1.1–1.2" --minutes 35` creates and starts one block. `focusdeck devices` lists the connected phone. A running/ready session must be stopped before a new plan can replace it.

The phone owns the timer. Wi-Fi loss does not pause or reset it. DONE records completion and actual time. Conditional plans display metrics and any reward before progressing; ordinary old plans advance directly. At expiry the same block stays active with a **red +MM:SS overtime dial** and “running in excess”. Focus and break blocks advance only after DONE. Pause freezes countdown or overtime. Reports preserve the plan session_id and include actual and overtime durations. Reports sync automatically; SEND REPORT requests a resend, and COPY SUMMARY copies a report on the phone. On the PC they are stored under `~/.local/share/focusdeck/reports/` in JSON and Markdown.

## Conditional rewards and log commands

Each focus block can set reward_policy enabled/disabled, arbitrary reward text and session_weight. Example: {"text":"5 pull-ups + move one token."}; no activity catalog required. Countdown DONE calculates existing classes, Pace, Focus Ratio and BeatScore. Stopwatch DONE records actual time/Focus Ratio with time scores N/A. Module conditions name explicit focus block IDs; independent session policies can use session completion, DONE counts and overall Focus Ratio. Mixed weighted scores use countdown blocks only. See [exact v2 conditions](docs/STOPWATCH_MODE.md) and [countdown arithmetic](docs/REWARDS_AND_COMPETITION.md).

Use `focusdeck copy-report --session SESSION_ID` for a paste-ready full report (KDE Wayland: install `wl-clipboard`). `focusdeck delete-logs --session SESSION_ID` deletes the latest attempt; `--key` selects an exact attempt and `--all` removes previously finalized automatic logs. Deletion records sync in both directions and suppress later replay/import; independently exported copies are retained. The daemon must run for PC deletion, but the phone may be offline. See [update and deletion scope](docs/UPDATE_1.6.md).

## OLED display and power

The normal timer is pure black with no dial shading or top-left logo. The dial updates once per second without spring animation; DONE uses an outline. **Display settings → Dim desk view** offers a small timer without the ring or footer, with low app-only brightness. Keep screen-awake enabled for a desk timer, or disable it and lock the phone for lower display use. Dim desk view uses the normal screen; native Samsung AOD may show the notification icon but a live custom dial is not supported by this release.

Background timer checks, durable writes, routine state messages, notification updates and duplicate keepalives are reduced. Commands still apply immediately and elapsed-time accounting preserves overtime/DONE behavior. Background status can be up to 60 seconds old, and sleep can delay alerts/LAN access. Measure on the phone before drawing battery-life conclusions.

## Study history and saved logs

When no session is prepared/running, Home shows today's focus, week/all-time totals and a small last-session link. A previous report opens only when selected; completing a session returns Home. Reports in history have VIEW TIMELINE, COPY FULL REPORT and DELETE SESSION actions. DELETE ALL LOGS removes previous automatic logs on both devices when synced.

The **history icon at the top left** opens Day/Week/Month/Year/All summaries, date navigation, focus/break composition, focus bars, session/task counts, overtime and copied full reports. **⋮ → Study history & logs** also opens the same screen. Whole sessions are grouped by their local finish date; weeks start Monday. Active sessions appear after finishing or stopping.

Full final reports and `all-sessions.csv`, `all-blocks.csv`, `all-events.csv`, `all-intervals.csv` save automatically inside the phone app, with no age/count limit on the separate report archive. Choose **CHOOSE AUTOMATIC LOG FOLDER** once to keep visible copies in a local folder such as **Documents/FocusDeck**. Future completed/stopped sessions update those files automatically; EXPORT is optional. The PC maintains all four CSVs under `~/.local/share/focusdeck/exports/` alongside its existing JSON/Markdown archive in `reports/`. Phone saving continues offline; PC copies arrive on reconnection.

New sessions record PAUSE/RESUME, DONE/next-task starts, STOP, extensions, waiting and observed overtime, with UTC timestamps and monotonic counters. Reports and CSVs include exact pause/active intervals, pause counts, longest uninterrupted stretches and actual/planned ratios. Older sessions have unavailable timelines; upgrade/reboot/event-limit gaps are marked partial, with missing behavior metrics left blank. Detailed logs describe timer use; accuracy/recall still need your evidence. See [TIMELINE_AND_FEEDBACK.md](docs/TIMELINE_AND_FEEDBACK.md).

Older reports already discarded from the phone's former 100-report cache can be restored from PC JSON. `focusdeck export --format json --output "$HOME/Downloads/focusdeck-history.json"` reads the full PC archive offline; transfer it to the phone and use **IMPORT JSON**. `focusdeck refresh-logs` rebuilds the PC's master CSVs. Full storage, retention, restore and export instructions are in [HISTORY_AND_LOGS.md](docs/HISTORY_AND_LOGS.md).

## Repository

| Directory | Contents |
| --- | --- |
| `android/` | Kotlin/Compose app, DataStore + report archive, history charts, automatic CSV/folder saving, monotonic timer, pinned TLS client, JVM tests |
| `desktop/` | Python asyncio daemon, local CLI, automatic CSV logs, offline export, transport/history/validation tests, systemd unit |
| `protocol/` | Session, pairing, report and wire JSON schemas |
| `examples/` | Single block, four-hour study, rapid smoke test, manual advance |
| `scripts/` | Desktop setup, Android build/install, tests, ZIP packaging |
| `docs/` | Setup, protocol, architecture, GPT workflow, troubleshooting and verification |

## Verification commands

```bash
./scripts/test-desktop.sh
python3 scripts/test-battery-profile.py
./scripts/build-android.sh test
./scripts/build-android.sh debug
```

Current executed results are in [verification](docs/VERIFICATION.md), including full Draft 2020-12 schema validation and a host-restricted Unix-socket skip. The supplied Android JVM tests have not run here. Battery fixtures validate measurement logic, not physical savings. Follow [testing](docs/TESTING.md) and the [stopwatch upgrade checks](docs/UPDATE_1.8.md).

V1 supports one trusted desktop and one paired phone. mDNS is not implemented; use a DHCP reservation or a locally resolvable hostname. Settings are private local files. There is no cloud, router port forwarding, telemetry, desktop GUI, WebView, or claim to measure actual attention. The foreground service keeps a visible user-started cockpit connection; timers retain their current block and derive overtime from monotonic time when Android delays execution. A reboot recovers paused rather than counting device-off time as focus.

MIT license. This release is source-delivered; see the precise validation limits above.
