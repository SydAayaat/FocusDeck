# FocusDeck development

## Calendar Analytics and layouts (1.20.0)

Read `docs/UPDATE_1.20.0.md` and `docs/ANALYTICS_AND_LAYOUTS.md`. Use the shared desktop/native grid rather than per-card gesture implementations. Keep dimensions local to page/template/form factor and achievement order phone-owned through contract-6 exact receipts/revision guards. Optional scheduling metadata is descriptive, captured immutably, and capability-gated; never silently strip a plan for an old phone. Preserve source-zone/DST allocation, measured totals, unknown versus zero, partial/live hour-average exclusion and read-only preview/viewer guards. Full/Reduced/Off/system scale apply to resize/reflow/drill motion. This requested delivery is source-only: no APK assembly, physical-device claims, push or release. Run necessary focused checks and explicitly record unavailable Android compilation/touch verification.

Continue the existing implementation. Inspect actual source and tests before changing behavior. Start with `docs/PROJECT_STATUS.md`; historical release documents describe their own versions and may contain superseded workflows.

## Architecture

- `android/app/src/main/java/com/hax/focusdeck/`: Kotlin/Compose UI, authoritative timer, private archive and visible-folder mirror, LAN transport.
- `desktop/src/focusdeck/`: Python CLI, TLS/WebSocket companion, archive, deletion synchronization and reports.
- `protocol/`: JSON contracts. `examples/`: compatibility and smoke plans.
- GPT plans. FocusDeck executes the supplied policy and reports measurements.

## Behavior to preserve

- Countdown expiry only enters red overtime. DONE completes the current block; expiry never advances it.
- Only DONE grants block completion. Hax explicitly added Skip block, End block and End session in 1.14.0, overriding the earlier no-skip rule. These close without completion credit; retain measured focus time and detailed evidence. Continuing without acknowledging a reward remains separate.
- GPT controls policies, thresholds, conditions, arbitrary reward text, weights and independent session rewards. Disabled/unmatched rewards are normal results.
- Carry the original session identifier through commands and reports. V2 requires UUID form; GPT must choose fresh IDs. V1 arbitrary IDs and historical attempts remain compatible.
- Use monotonic duration accounting and detailed start/pause/resume/transition/extension/completion events.
- Retain full phone/PC JSON and CSV archives. Keep the visible phone-folder option and private archive, Home, top-left history and Day/Week/Month/Year/All views.
- Synchronize deletion tombstones and suppress replay of deleted reports. Exercise deletion only against disposable test data.
- Preserve Markdown clipboard reports, pure-black OLED UI, static graphics, reduced polling/writes and optional dim desk view. Dim desk view is not Samsung AOD.
- Preserve Android application ID, installed signing identity, phone app data, PC config/certificates/tokens, archives and deletion state during upgrades. Never uninstall or clear data as a routine upgrade step.

## Stopwatch Mode

Implemented in 1.8.0 with session/report contract 2. Read `docs/STOPWATCH_MODE.md`. GPT explicitly supplies each v2 focus mode. Stopwatch has no duration/budget/target/overtime; only DONE completes it. Time scores/classes are null; completion/Focus Ratio rewards use explicit supplied conditions. Mixed scores exclude stopwatch weights and carry countdown-only scope. The 1.8.2 UI shares the countdown layout and controls, with 60 inner dial ticks and a minute-stepped hourly outer arc. This scale is not a time target; stopwatch footer progress is DONE blocks and estimated finish is N/A. Preserve v1 countdown math and workflow. Wire/pairing stay v1; capabilities gate v2 delivery to old phones.

## Validation and environment

Run `bash scripts/test-desktop.sh` and `python3 scripts/test-battery-profile.py` for desktop changes. Run Android unit tests, debug build and lint when the JDK/SDK are available; inspect Compose on a real device/emulator for visual changes. Record skips and unavailable checks explicitly.

Target PC: `/home/hax/Projects/FocusDeck`, Ubuntu 26.04 LTS KDE Plasma/Wayland with Krohnkite tiling. Target phone: Samsung Galaxy S24 Ultra, ADB serial `R3CX90NC7HR`. JDK: `/usr/lib/jvm/java-17-openjdk-amd64`; SDK: `$HOME/Android/Sdk`. These target paths are not proof they exist in the agent workspace.

Keep source work on a development branch. Push, APK installation and service setup must only be reported as complete when actually performed. Local tests do not prove live phone synchronization, Android compilation or battery savings.

Hax's current direction is to work locally and leave GitHub alone. Do not push or trigger remote CI unless explicitly requested again.

## Achievements (1.9.1)

Read docs/ACHIEVEMENTS.md. Achievement command contracts 1/2 on existing wire 1; capability-gated, with backward-compatible storage/snapshot 1. Read docs/UPDATE_1.9.1.md for card layout, removal and GPT definition rules. Preserve deleted IDs/retired command receipts, immutable built-in measurement definitions and removal guards for current/future targets. Preserve independent DataStore ledger, command IDs, cumulative focus cursors and permanent closed-day statuses. Targets are latest-write-wins by timestamp/id and remain mutable until local midnight; actual activity is never replaced. NO_TARGET and unscheduled project/college days are neutral. Porn Free requires explicit timestamp initialization. Record gap compares current against best completed history, average excludes current. Do not infer subject names, backfill manual achievements from topic titles, change timer/reward math, or resurrect deleted reports. Run native JVM/desktop/schema tests and compile instrumented UI tests; do not claim device execution from compilation.


## Progress and motion (1.10.0)

Achievement command contract 3 adds cumulative progress with independent streak/progress capability flags. Preserve actual totals, historical targets, completion receipts, migration marker and stale-SET ordering. Unique increments are deduplicated by command id; never drop an equal-timestamp increment because of lexical id order. Cumulative updates never imply daily completion. Defaults are German 1250 words and Custom Compute unset total. Motion FULL is the default; REDUCED shortens transitions and suppresses pulse, OFF uses immediate changes. Android animator scale zero disables motion. Do not add continuous decorative loops or database writes on animation frames. Read docs/UPDATE_1.10.0.md for verification and actual limitations. User direction now prioritizes smooth event-driven animation over the earlier static-graphics preference.


## Palettes, icons and navigation (1.11.0)
Read docs/UPDATE_1.11.0.md. Preserve contract-4 icon SET ordering/default reset receipts and capability negotiation. PNG imports are bounded and decoded off the main thread before durable ACK; archive retains icons, delete removes assignments and retires receipts. Built-in icon appearance is independent of protected measurement rules. Navigation requests up to 120 Hz separately from the timer's saved refresh setting; never claim sustained FPS without a device trace. Keep history/statistics/serialization off the main thread and long histories lazy; do not reintroduce observable grid-position writes or per-frame layout changes. Native rings use finite bloom and static halo, respecting Full/Reduced/Off/system scale. Global palettes retain black backgrounds; grayscale also desaturates custom icons.

## Glow, audio and developer preview (1.12.0)
Overview is a full-span item in each category grid, so landscape never hides the only scrolling viewport behind fixed overview content. Keep title icons removed; ring icons remain. Detail sparkles are finite and Full-only; never add continuous decorative loops. Sound samples are original generated PCM, disabled by default except the legacy boundary toggle, and short custom clips are copied privately and decoded before saving selection. Audio must never mutate timer/reward/history logic or write on ticks. Developer preview is an ephemeral ledger with no repository/network mutation; sample data must never enter real history or GPT evidence. Read UPDATE_1.12.0.md.

## Approved reference pages and motion (1.13.0)
Read docs/UPDATE_1.13.0.md. Follow the three approved Home/history/report images under docs/design. Preserve real metrics and all original archive/export/import/delete/reward detail actions; never copy mockup numbers or wrong Pace units. Immersion is application-wide, including dialog windows. Achievement ring centers are icon-only; totals/percentages remain in the progress panel. Shared motion respects Full/Reduced/Off/system scale. Hax explicitly authorizes an extremely slow Home eclipse orbit, overriding the earlier continuous-loop prohibition for that element only; pause it when obscured/backgrounded and never write on animation frames. This request is source-only: do not run unnecessary suites or build APKs. Record compile/device checks as unexecuted unless actually performed. Default install-apk.sh builds current source on Hax's PC before install-r; explicit APK arguments bypass build. Preserve the PC's existing signing key/data and leave GitHub alone.

## Home artwork correction
Home's eclipse is a fitted density-independent bitmap (`drawable-nodpi/home_eclipse_art.png`) with textured directional crescent, tilted orbit and moon. Do not replace it with a generic Canvas circle. Keep complete curved limbs/moon visible; capture BoxWithConstraints values before nested layout scopes. Grayscale luminance maps to the saved global accent and near-black pixels clamp to OLED black. Bitmap decode is off-thread/cached; ≤1.5 dp two-minute drift is Full-only and stops while obscured/backgrounded. Preserve the drawIntoCanvas import used by history charts. See docs/design/ECLIPSE_ARTWORK.md and the 1.13 correction notes.

## Session Complete artwork correction
Report's `drawable-nodpi/session_orbit_art.png` contains only static atmospheric light, tilted orbital arcs and two shaded moons. Keep the ring/check and completion/stopped state native, with the existing finite illumination and real DONE/timeout totals. Share off-thread cached decoding and global palette luminance tint with Home. Do not bake UI text or a success checkmark into the asset, replace shaded moons with dots, or add a continuous report orbit loop. See docs/design/SESSION_ORBIT_ARTWORK.md.

## Achievement detail cropped lower-section reference
Hax explicitly approved the existing upper detail navigation, hero ring/icon/glow, title/description and progress panel. Preserve them; the regenerated full image is not authorization to replace that upper section. Only the crop starting at Current/Best/Record gap/Average guides this correction. `AchievementDetailSections.kt` owns the lower statistics, policy, adaptive lazy chart, selected evidence, Today rows and inline histories. Render according to independent streak/progress flags. All numbers, units, dates, contracts and focus totals must come from actual stored data; missing evidence is N/A and unit changes stay separate. Detailed history remains inline as previously requested. See docs/design/ACHIEVEMENT_DETAIL_REFERENCE.md.

## Skip and end controls (1.14.0)
Read docs/UPDATE_1.14.0.md. Clock-page three-dot menu only: amber Skip block, red End block and End session; no +5 minutes item. Block controls require active/paused clocks; whole-session End is also available while waiting to Start. Confirm ends and guard UI commands against a changed state revision under the repository mutex. Skip/end retain monotonic effort, bypass block rewards, never imply DONE or cumulative/manual achievement activity; automatic focus-time capture remains valid. Skipped/ended attempts cannot receive a whole-session reward/PB. Terminal partial reports keep wire status stopped and per-block skipped/stopped outcomes; never mark them complete. Preserve old reports/extensions and unrelated approved UI. Update the desktop companion for new skipped/block_skipped report evidence. No save-and-resume workflow is introduced by this request.

## Tablet viewer (1.15.0)
Read docs/UPDATE_1.15.0.md. Phone alone is timer/achievement authority and GPT command destination. Viewers authenticate with separate token/endpoint and send only presence/ACKs. Preserve legacy phone pairing. Never import mirrored state/ledger/reports into the local timer/archive or finalize/reward/acknowledge phone work from a viewer. Read-only guards must cover UI and repository; local appearance/export preferences are allowed. Keep identity-scoped atomic private caches and complete history batches, observation/PC synchronization timestamps and phone timezone. An offline cache is not live data. Background viewers disable fast polling; no per-frame writes or independent tablet timer. Keep signing/data/history and approved UI. No GitHub changes or unnecessary APK builds.

## Control Center (desktop 1.16.0; Android stays 1.15.0)
Read docs/UPDATE_1.16.0.md and docs/GPT_CONTROL_CENTER_INPUT.md. Qt WebEngine/WebChannel uses bundled local assets and the existing owner-only Unix control plane; no HTTP/LAN UI server. Target is Ubuntu 26.04 LTS KDE Plasma/Wayland with Krohnkite tiling. Default window is frameless/maximized rather than exclusive fullscreen; no custom close/minimize buttons, no fixed desktop resolution. Preserve phone authority, separate private drafts/settings/delivery journal and scoped snapshot identity. Definition edits exclude existing progress unless explicitly requested. Built-in rules are protected. GPT paste is a deterministic documented text/JSON parser, not an undisclosed cloud AI. Draft creation redirects but never auto-sends/starts. Keep full policies and receipt IDs for uncertain retries; don't re-send completed batch steps. Do not install/re-pair/clear user data or build an APK for a desktop-only release.

## Desktop tray lifecycle (1.16.1)
Read docs/UPDATE_1.16.1.md. Closing hides the Control Center to Qt's system tray; explicit tray Quit/Ctrl+Q exits only the GUI. Secondary launchers activate the existing owner through a private atomic mailbox while retaining the flock. Preserve the same window/drafts, compositor-managed geometry and background visibility polling behavior. Without a usable system tray, closing exits instead of leaving an unreachable hidden process. Demo has no tray. Phone/service/data and Android remain unchanged.

## Achievements dashboard and packs (1.17.0)
Read docs/UPDATE_1.17.0.md. Follow docs/design/control-center-achievements-1.17.png for the shared seven-page shell/logo and monitor layout. Preserve separate read-only Achievements and Card Builder; Analytics retains history/report evidence. Do not infer pace/on-track policies, copy fixture counts, mix progress units or claim tablet-private cache freshness. Retain stored snapshot time/zone and missing evidence as N/A. Icons are alpha-tinted at render time on desktop/Android with unchanged contract 4; never bake theme colors or change receipts on palette changes. Multi-import/export uses the private reusable library, bounded decoding/ZIP reads without filesystem extraction, atomic validation and content deduplication. Unchanged library payloads are suppressed on observations. Keep tray behavior, optional desktop/sidebar preferences, phone authority, data and pairing. No APK assembly is needed for desktop UI verification; Android compile status must be reported honestly.

## Complete desktop workflow (1.18.0)
Read docs/UPDATE_1.18.0.md and docs/GPT_CONTROL_CENTER_INPUT.md. CARD/SESSION/UPDATE/BATCH indented text compiles to existing contracts without policy loss; strict bounded safe YAML retains timestamps as strings and rejects aliases/tags/duplicates. Keep canonical command receipts, including upserts, and exact ordered-batch retry steps. Schema-derived forms must preserve optional/unknown supported fields and validate whole drafts before saving; never simplify a policy by dropping tests. Full report text/JSON copies use exact archived attempts, honor tombstones on each click and save atomically outside private data. Overview final reports wait for actual sync; Analytics selection must not silently switch attempts. GUI covers daily CLI workflows; +5-minute control stays removed. Service restart uses only the fixed installed owner service, never shell interpolation. Desktop is 1.18.0; Android remains 1.17.0. No APK, install/reset or re-pairing for this desktop-only change.

## Index Tracker (1.19.0)
Read docs/INDEX_TRACKER.md and docs/GPT_INDEX_INSTRUCTIONS.md. Phone ledger owns named topic counts. No independent numeric counter edits, inferred study days or fabricated topics/history. Preserve explicit migration backups, stable IDs, removed item evidence, replay receipts, category/kind guards and read-only viewers. New writes use negotiated achievement contract 5; retain contracts 1–4. Hax requests only necessary focused checks for this reconstruction.
