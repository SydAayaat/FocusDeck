# Current development status · 1.22.0 Stage 2

The existing three-year Developer Mode integration is preserved. Stage 2 restores shared Home settings/pairing/device controls, removes the four-icon top toolbar, fixes Back-to-Home/double-Back exit and saves meaningful navigation state across rotation. The approved shortcut goes on the existing Analytics date control; Time Estimation shortcuts are removed. Local one-use QR invitations extend certificate-pinned pairing with confirmation while keeping independent manual JSON fallback. Android remains 1.22.0/code 26; desktop is 1.21.0. Stage 1 was pushed on the authorized development branch. Only the isolated visualtest app is deployed on the USB S24 Ultra; production installation/data/pairing remain untouched. See [Stage 2 verification and limits](UPDATE_1.22.0_STAGE2.md) and [pairing model](PAIRING_INVITATIONS.md). Earlier entries describe their own work.

# Current development status · 1.22.0 supplied Developer Mode dataset

The supplied deterministic three-year fixture archive is validated and integrated through a read-only asset adapter into the existing native Developer Mode and isolated `.visualtest` presentation. Android source is 1.22.0/code 26, desktop remains 1.20.0, and the existing local `codex/omss-native-ui` 1.21.0 work is preserved. Home, all Analytics periods, Achievements, Index, paged History, lazy Timeline and Summary use the supplied data with a fixed clearly labeled simulated date. No synthetic data reaches production storage, transport, rewards or genuine exports. Focused JVM/physical verification, production-data preservation and exact limits are in [UPDATE_1.22.0.md](UPDATE_1.22.0.md). The production phone install remains unchanged; only the isolated visualtest app is deployed. No dataset regeneration, production installation/reset, emulator, GitHub changes or release APK. Prior entries describe their own work.

# Current development status · OMSS Home, Timeline, History and compact Summary

Continued the existing local `codex/omss-native-ui` work with paired Home shortcuts and an engine-driven Current Session, a source-zone 24-hour lazy timeline with collapsed/expanded event cards, a real-record History feed with multi-category/search/date/sort controls, and a compact Session Summary retaining the small Focus Ratio gauge, six metrics, all events and report actions. Original scoring/calibration, timer, ledger, transport, Executive Telemetry Insights and versions are unchanged. Hax explicitly authorized the existing isolated visualtest build on physical USB Samsung SM-S928B `R3CX90NC7HR`. Production/debug compilation, 33 focused JVM + five fixture tests, 21 physical UI tests and zero-error lint passed (23 warnings). Raw portrait/large-font landscape and all-motion navigation captures are local. Production APK and all ten readable private files remained byte-identical. See [OMSS_HANDOFF.md](OMSS_HANDOFF.md) for source, physical evidence, timing definitions, missing historical-log limitations, bounded performance results and repeat instructions. No production install/launch, reset, emulator, GitHub changes, release or APK upload. Prior entries describe earlier work.

# Current development status · finalized Analytics installed on USB S24 Ultra

After Hax explicitly requested the production update, current Android 1.21.0/code 25 was built locally and installed in place on USB Samsung SM-S928B `R3CX90NC7HR` with the existing signer and `install -r`. Installed APK SHA-256 matches the build; production DEX excludes isolated QA fixtures. Actual Home → Analytics → Achievements → Home navigation passed. All nine private files were byte-identical immediately after install; all eight protected settings/ledger/report/export/layout files remain identical after launch, with only AndroidX's installation cache marker changing. The shared final Analytics rows, compact selector, native clock icons and immersive navigation are now installed. Existing 24 physical UI / 44 focused JVM tests and zero-error lint apply; no implementation changed during deployment. See [ANALYTICS_FINAL_LAYOUT.md](ANALYTICS_FINAL_LAYOUT.md) for signing, backup and verification evidence. Versions/local branch remain unchanged. No reset, emulator, GitHub push, remote CI or release build. Entries below describe earlier phases.

# Current development status · finalized Analytics rows verified on physical S24 Ultra

The existing Android 1.21.0 OMSS recovery now has shared Focus/Sessions/Blocks Done/Variance KPIs, paired Plan Execution Score/Time Split and Execution/Punctuality Trend rows across Day/Week/Month/Year, a measured compact date selector and direct immersive primary-navigation icons. Shared resize storage, native materials, chart drilldown and the isolated visualtest app remain. Versions and local branch `codex/omss-native-ui` are unchanged. After explicit physical-testing authorization, only the isolated visualtest and test packages were built/deployed to USB Samsung SM-S928B `R3CX90NC7HR`; 24 physical UI tests passed. Fourteen scene/configurations and real navigation/deep-scroll checks supply 33 original phone screenshots, including ordinary navigation, empty/zero/partial states and large-text landscape. Physical review corrected date-selector collapse/wrapping and clock fallback. Production APK and all nine readable private files remain byte-identical; production was not launched or installed. Production Kotlin/lint passed; 39 production JVM plus five fixture tests passed; lint zero errors/14 warnings. See [ANALYTICS_FINAL_LAYOUT.md](ANALYTICS_FINAL_LAYOUT.md) and the [local physical gallery](../artifacts/uiqa/analytics-final-physical/gallery.html). No emulator, push, remote CI or release. Prior recovery evidence below applies to its earlier source.

# Current development status · OMSS recovery and isolated visualtest

The existing native OMSS implementation has been corrected through shared geometry, full-width Analytics, compact calendar/achievement layouts and immersion-aware navigation. The independent `com.hax.focusdeck.visualtest` debug app provides deterministic in-memory fixtures with no network permission or production repository. All seven scenes were captured/reviewed on USB S24 Ultra `R3CX90NC7HR`; additional empty/completed/large-font landscape scenes were checked. Production debug was updated with the matching signer; all eight protected data files remain byte-identical, and the installed APK matches the built source. A supplied-clock period-closure calculation bug was fixed with regression evidence. Focused production JVM tests: 101 passed; fixture tests: four passed; physical native UI tests: 14 passed; lint: zero errors/15 warnings. A limited 465-frame transition sample reported 2.8% jank; no sustained 120 Hz claim. Android 1.21.0/code 25 and desktop 1.20.0 stay unchanged on local branch `codex/omss-native-ui`. See [OMSS_RECOVERY.md](OMSS_RECOVERY.md) for source changes, gallery, preservation evidence and explicit visual/performance/verification limits. No release build, reset, push or remote CI. Entries below describe earlier deliveries.

# Prior development status · OMSS native UI on physical S24 Ultra

The finalized matte Home, popup, Achievements and Day/Week/Month/Year Analytics UI has been built and installed locally on USB Samsung SM-S928B `R3CX90NC7HR` using the matching existing signer and `install -r`. Three grouped physical screenshot passes corrected compact spacing, summary/KPI row sizing, filter/header wrapping and saved-height plate rendering. All seven requested primary-app screens and lower sections were visually inspected; 11 isolated native UI tests, 38 focused JVM tests and lint passed (0 errors, 15 warnings). Protected report/ledger/timer/pairing/layout state matched the pre-install snapshot; Hax confirmed changing the palette. Android 1.21.0/code 25 remains installed; desktop stays 1.20.0. Work is local on `codex/omss-native-ui`. See [OMSS_UI.md](OMSS_UI.md) for captures, comparison alignment, remaining calendar/density differences and unexecuted checks. No release APK, upload, primary data reset, push or remote CI. Earlier source-only entries below describe the delivery before physical deployment was authorized.

# Current development status · OMSS native UI (source only)

The latest six dashboard references and pasted OMSS specification now drive the native Home, timeline popup, Achievements and shared Day/Week/Month/Year Analytics materials and composition. Reusable opaque matte cards, directional cached GPU shadows, raised hubs/tracks, flat tabs and cyan references behind bars are implemented on local branch `codex/omss-native-ui`. Versions remain Android 1.21.0 / desktop 1.20.0. Main/instrumented Kotlin compilation, 38 focused JVM tests and lint passed (0 errors, 15 warnings). See [OMSS_UI.md](OMSS_UI.md) for changed files, evidence and remaining visual limits. No APK assembly, device execution, installation, reset, push or remote CI was performed. Previous entries describe earlier deliveries.

# Phase 2 Analytics (source only)

Android and desktop Analytics now share factual Plan Execution Score, estimation/calibration, consistency and historical comparison definitions. Day, Week and shared Month/Year/All templates retain local shared-grid editing; report copy rechecks canonical history and deletion state. Versions remain Android 1.21.0 / desktop 1.20.0. Work is on `codex/phase-2-analytics`. See [PHASE_2_ANALYTICS.md](PHASE_2_ANALYTICS.md) for policy, changed files, executed checks and limitations. The Phase 2 delivery contains complete source and no APK; no assembly, installation, service setup, data reset, push or remote CI was performed.

The following entries describe earlier development and releases.

# Current development status · Android 1.21.0 / desktop 1.20.0

The locked native Android Home, timeline popup, Day/Week/Month/Year Analytics and Achievements UI is implemented in Compose through milestones M0–M8. Android versionCode is 25 (installed 1.20.1 uses 24). Desktop functionality/version stays 1.20.0. See UPDATE_1.21.0.md and INSTALL_1.21.0.md. The installable local ZIP includes source and the production-package debug APK signed with the existing PC key; its certificate was checked against the installed S24 Ultra app. No installation, data reset, remote push or merge was performed for packaging.

The following entries describe historical releases.

# Current development status · FocusDeck 1.20.0

Timeline, Analytics and reusable desktop/Android card resizing are implemented in source on `codex/timeline-analytics-resizing`. Read UPDATE_1.20.0.md for focused validation and unexecuted Android/device checks; read ANALYTICS_AND_LAYOUTS.md for calendar attribution, scheduling and persistent layout contracts. Android source is versionCode 23. No APK assembly, installation, data reset, pairing change or remote publication was performed.

The following entries describe historical releases.

# Current development status · FocusDeck 1.19.0

Index Tracker reconstructed from 1.18.0. See UPDATE_1.19.0.md for focused verification and remaining device checks. Existing cards/data remain untouched.

# Current development status · desktop 1.18.0 / Android 1.17.0

Complete indented desktop GPT input, schema-derived session/reward/update form editors, all nine achievement operations, ordered update drafts, replay-safe receipts, explicit Send & start, visible/copyable/savable text/JSON session output on Overview/Analytics, exact historical selection, GUI archive deletion/export/rebuild and device service/pairing recovery are implemented. Full desktop: 225 collected, 224 passed, one existing AF_UNIX host skip; nineteen new contract/workflow regressions; battery fixtures seven passed. Actual native Qt WebEngine: 102 responsive layouts and 28 workflow assertions passed, zero overflow/JavaScript errors. Wheel build/install/assets and native preview verified. Read UPDATE_1.18.0.md and GPT_CONTROL_CENTER_INPUT.md. Android is unchanged; no APK needed. Physical KDE/Wayland/tray/chooser/clipboard and live phone/tablet synchronization remain unexecuted. No target installation, reset, pairing change or remote push.

The following entries describe historical releases.

# Current development status · desktop/Android 1.17.0

The approved Achievements dashboard shell, seven-page navigation, mountain logo, saved collapsible sidebar, separate read-only Achievements/detail monitor, renamed Analytics/Card Builder and reusable multi-icon import/export library are implemented. Custom image alpha masks follow the desktop palette; the Android image renderer uses the ring's theme tint with unchanged icon contracts. Desktop: 206 collected, 205 passed, one existing AF_UNIX host skip; eight new icon-library regressions; battery fixtures seven passed. Native Qt WebEngine: 102 layouts and seventeen workflow assertions passed, with no overflow/JavaScript errors. Source/wheel verification and install instructions are in UPDATE_1.17.0.md. Android compilation stops before source compilation at missing offline Android Gradle plugin 8.9.2; no APK build. Physical KDE/Wayland/tray/phone/tablet checks remain unexecuted. No target installation, reset or remote push.

The following entries describe historical releases.

# Current development status · desktop 1.16.1 / Android 1.15.0

The Control Center now hides to its system tray on window close, restores from tray or launcher, and exits via tray Quit/Ctrl+Q. Single-instance activation preserves the existing window and drafts; no-tray fallback exits normally. Native offscreen lifecycle checks passed (11). Full desktop and packaging results are recorded in UPDATE_1.16.1.md. Physical KDE/Wayland/StatusNotifier behavior remains unexecuted; no Android build, target install, pairing reset or GitHub push.

The following entries describe historical releases.

# Current development status · desktop 1.16.0 / Android 1.15.0

The KDE/Wayland Control Center is implemented with six responsive screens, local GPT text/JSON draft detection, card/icon/progress editors, session templates/controls, history/exports, device pairing and private delivery replay tracking. The existing phone/tablet app is unchanged. Desktop verification: 187 collected, 186 passed, one existing AF_UNIX host skip; seven battery-tool fixtures passed. Native Qt WebEngine offscreen rendering and 54 layout checks at 420–1672 px passed, with no JavaScript errors or horizontal overflow; paste redirects, preview editing, unique duplicate IDs and template saving passed through actual WebChannel. Wheel/assets, syntax and packaging checks are recorded in UPDATE_1.16.0.md. Target KDE/Krohnkite/Wayland/phone synchronization and physical frame-rate checks remain unexecuted. No target installation, signing/data/pairing reset, APK build or GitHub push.

The following entries describe historical releases.

# Current development status · 1.15.0

Phone authority plus read-only tablet viewers is implemented in source. Separate viewer pairing/token and role isolation preserve all GPT writes on the single phone. The tablet reuses achievement/history UI, observes the session, and caches complete snapshots with freshness/offline indicators. Android/desktop 1.15.0, Android versionCode 20. Desktop full suite: 166 passed, one AF_UNIX host skip; seven battery-tool fixtures passed. APK follow-up: Android main compilation and assembleDebug passed; five TabletMirror and six SkipEnd native regressions passed. The SkipEnd v2 fixture was corrected. APK signature/manifest/integrity checks passed. Lint and physical two-device UI/LAN/FPS checks remain unexecuted. The APK uses a workspace debug key; existing installs need a matching signer. No installation, pairing/data reset or GitHub push. See [UPDATE_1.15.0.md](UPDATE_1.15.0.md) for setup and limits.

The following entries describe historical releases.

# Current development status · 1.14.0

Skip block, End block and End session are implemented in the clock-page top-right menu; +5 minutes is removed. Monotonic time, distinct skipped/stopped outcomes, partial reporting and DONE-only completion/reward rules are retained. Android/desktop source version 1.14.0, Android versionCode 19. Focused desktop checks: 43 passed; no skips. Kotlin syntax inspection passed for the changed UI/model/timer/history/reward files and new regression tests; the parser reports pre-existing limitations in CommandProcessor/DeckRepository and is not compiler evidence. Android focused unit-test/compile attempt stopped before source compilation: offline Android Gradle plugin 8.9.2 is unavailable. Six new native regressions are supplied but not executed. No APK assembly, phone installation/device checks or GitHub push. See [UPDATE_1.14.0.md](UPDATE_1.14.0.md).

The following entries describe historical releases.

# Current development status · 1.13.0

The supplied Home, Study history and Session Complete reference layouts, global immersive mode (including app dialogs), centered icon-only achievement rings and shared animation table are implemented in source. Android/desktop package 1.13.0, versionCode 18. Phone screenshots revealed a clipped flat Home disc; follow-ups replace it with fitted textured artwork and match the Session Complete close-up with shaded moons, tilted orbits and atmospheric light around the native check ring. The achievement detail lower section follows the newly supplied crop; the approved upper ring/header/progress panel remains unchanged. The PC-reported scope/import compile errors are corrected. Source syntax/whitespace and mocked install-script checks pass; Android compilation, APK builds, full tests, lint and device execution were deliberately not run for this request. See [UPDATE_1.13.0.md](UPDATE_1.13.0.md). Actual pixel matching and frame-rate behavior are unverified. Source-only package; no phone installation, reset, history mutation or GitHub push.

The following entries describe historical releases.

# Current development status · 1.12.0

Static achievement halos, finite detail sparkles, removal of duplicate title icons, scrollable Overview in landscape, four clock/three feedback sounds with bounded custom audio imports, and isolated temporary developer preview are implemented. Android/desktop 1.12.0, versionCode 17. Native160 pass; desktop154 pass/one host skip; battery fixtures7 pass; debug APK, seven instrumented UI tests compiled, lint zero errors/seven warnings. See [UPDATE_1.12.0.md](UPDATE_1.12.0.md) for exact controls, test records, signer mismatch and pending device checks. No phone installation, history clearing, real sample-data injection or GitHub push.

The following entries describe historical releases.

# Current development status · 1.11.0

Global palettes, bounded contract-4 card icons, thicker halo rings and finite opening/press motion are implemented. Navigation and fullscreen Study history request up to 120 Hz separately from timer 30/60/120 preferences. Main-thread history/snapshot/statistics/serialization work and per-frame grid/layout updates were reduced. Android/desktop 1.11.0, versionCode 16. Native155 pass; desktop154 pass/one host skip; battery fixtures7 pass; debug APK, instrumented-test Kotlin compilation and lint pass. See [UPDATE_1.11.0.md](UPDATE_1.11.0.md) for the exact warning count, signer difference, commands and pending physical-device checks. No APK installation, history clearing or remote push was performed.

The following status describes historical releases.

# Current development status · 1.10.0

The current release adds cumulative progress and motion to the existing 1.9.1 achievement infrastructure. Android/desktop 1.10.0, versionCode 15. See [UPDATE_1.10.0.md](UPDATE_1.10.0.md) for exact implementation, schema/persistence changes, current verification and device checks. Current results: 148 native tests passed; 147 desktop tests passed and one AF_UNIX host skip; seven battery-tool fixture tests passed. Debug APK and instrumented-test Kotlin compilation succeeded. Instrumented UI tests were not executed; physical visual/LAN/upgrade/frame-rate verification remains pending. Source remains on a local development branch without a remote push.

The material below describes older releases and does not override 1.10 behavior or results.

# Historical development status · 1.9.1

## Current 1.9.1 source update

Achievement card management now supports GPT-created manual cards in all categories, swipe navigation, saved ordering/size presets, archive/restore and confirmed permanent deletion with replay protection. Achievement command contract 2 is explicit; contract 1 remains supported. Storage/snapshot format 1 gains backward-default layout/deletion fields; session/report 1/2 and wire/pairing 1 are unchanged. Android/desktop **1.9.1**, versionCode **14**. Work remains local with no remote configured/contacted.

Executed focused checks: **40 native achievement JVM tests passed**, including main Kotlin/Compose compilation; **34 desktop achievement/schema/selected real TLS tests passed**. See [UPDATE_1.9.1.md](UPDATE_1.9.1.md) for commands, changes and limits. At the user's request, no APK assembly, lint, full-suite rerun, installation or service setup was performed for this patch. Swipe/drag/resize and upgrade/sync need actual phone verification. The following release checks apply only to their historical versions.

## Historical 1.9.0 changes and checks

The uploaded 1.8.2 source was restored into a new local Git development branch, with no remote configured/contacted. Achievements is now integrated with the existing DataStore, timer measurements, pinned WebSocket and CLI. See [ACHIEVEMENTS.md](ACHIEVEMENTS.md) for entities, contracts, UI and compatibility decisions, and [ACHIEVEMENTS_VERIFICATION.md](ACHIEVEMENTS_VERIFICATION.md) for current executed results. The 1.8 audit below is historical provenance, not the current build status. Current checks: **125 native JVM passes; 133 desktop passes, one AF_UNIX host skip; seven battery fixture passes; debug and UI-test APK builds passed; lint zero errors/five warnings**. Four instrumented UI tests are compiled but unexecuted. Phone installation and visual/LAN/storage/clipboard checks remain pending. Android/desktop 1.9.0, versionCode 13. The delivered debug signer differs from the earlier 1.8.2 APK; use the existing PC signing key for an in-place upgrade.

## Historical 1.8 changes and checks

GPT-controlled Stopwatch Mode now spans native timer/recovery/UI/rewards, desktop validation/transport/report/clipboard/history, versioned schemas, Project Instructions and examples. Fully timed mathematics and v1 workflows remain compatible. Wire/pairing stays v1; phone capabilities gate v2 plans. Android versionCode is 12, application ID unchanged; desktop is 1.8.2.

The local 1.8.1 continuation rejects inconsistent report category totals and malformed reward evidence, uses native-compatible block Focus Ratio rounding, and enforces existing countdown target/budget bounds consistently in native/desktop/schema validators. Details: [UPDATE_1.8.1.md](UPDATE_1.8.1.md).

The local 1.8.2 UI continuation shares the countdown layout with stopwatch, changing only the dial to the supplied clock-tick reference with a minute-stepped hourly arc. Untimed progress shows DONE blocks and finish remains N/A; no timer/reward/report semantics changed. Two new native regressions cover minute/hour boundaries and pause behavior. See [UPDATE_1.8.2.md](UPDATE_1.8.2.md).

Current validation: **111 desktop passes, 1 host-restricted skip**, full schema validation included; **7 battery-tool fixture passes**. **91 native tests supplied, not executed**; Android test/debug attempts stop at missing SDK. See [VERIFICATION.md](VERIFICATION.md) and [STOPWATCH_MODE.md](STOPWATCH_MODE.md) for the full test record, implementation map and compatibility choices. The original audit below is retained as provenance. Source remains on a local development branch, with no remote push.

Audited on 2026-10-07 from the uploaded `FocusDeck-v1.7.1 (1).zip`. Actual files are authoritative. This document separates source implementation from executed tests and device verification.

## Imported 1.7.1 provenance

- Uploaded ZIP SHA-256: `c2f64d55e6b2af89d96630d1c3e720eab9b36f4540f8a5c1c58c382eb678f137`.
- Android: `versionName = "1.7.1"`, `versionCode = 9`, application ID `com.hax.focusdeck`.
- Desktop package: 1.7.0, intentionally unchanged by the Android reward-page release.
- ZIP contains source, schemas, docs and tests, but no `.git` history.
- GitHub `origin`: `https://github.com/SydAayaat/FocusDeck.git`. At setup, its `main` was `d480385e2c7014a9ab6add015ed42ef78059b018` (starter README only).
- Imported the uploaded source without rewriting it into local commit `5415f7b` on `codex/setup-v1.7.1`. Setup changes follow as a separate commit. No push was performed.
- Work is in the agent checkout. Hax's `/home/hax/Projects/FocusDeck` and attached phone are not accessible here; they have not been installed or modified.

## Baseline source audit (before Stopwatch Mode)

| Requirement | Source evidence | Validation status |
| --- | --- | --- |
| Expiry enters overtime; DONE advances | `TimerEngine.tick`, `done`, `MainActivity` | Source inspected; 73 supplied Android JVM tests have not run here |
| DONE-only normal completion | `TimerEngine.finishBlock`, `RewardScreen`, `CommandProcessor` | No failed/unfinished/abandoned normal completion controls found; explicit stop and legacy report fields remain |
| GPT policies, conditions, weights and independent session rewards | `ConditionalRewards.kt`, `Rewards.kt`, `desktop/conditional.py`, `desktop/rewards.py` | Desktop conditional/report tests pass; native execution pending |
| Disabled/unmatched reward is a normal result | Conditional evaluator and result screen | Desktop policy tests pass; phone flow pending |
| Arbitrary reward text | Conditional text reward encoding, receipt/report/CSV paths | Desktop text reward tests pass; actual Compose wording pending |
| Session identity in plans/commands/reports | Kotlin session/report models, CLI/server, GPT instructions | Identity-preservation tests pass; UUID enforcement/uniqueness gap below |
| Detailed monotonic timeline | `DeckRepository.readClock`, `TimerEngine`, both timeline analyzers/CSV writers | Desktop timeline tests pass; native timeline/device checks pending |
| Full automatic JSON/CSV history; private + visible phone storage | `ReportArchive`, `DeckRepository`, desktop history/server | Desktop archive tests pass; phone provider writes pending. Small DataStore delivery cache does not cap the separate archive |
| Day/Week/Month/Year/All and top-left history | `StudyHistory`, `StudyHistoryScreen`, `MainActivity` | Source inspected; phone visual verification pending |
| Idle Home | `HomeScreen`, `MainActivity` | Source inspected; phone completion/reopen flow pending |
| Single/all deletion sync and replay suppression | Phone deletion/archive/network, desktop tombstones/server | Desktop temporary-data transport/deletion tests pass; live phone-folder deletion pending |
| PC Markdown clipboard | `desktop/clipboard.py`, CLI `copy-report` | Selection/content/process tests pass; KDE clipboard processes are mocked in tests |
| OLED/battery behavior and dim desk view | Pure-black UI, static canvases, `PowerPolicy` | Source inspected; 7 battery measurement-tool fixtures pass. No physical savings measured |
| 1.7.1 reward UI | `RewardScreen`: outlined cards, inline metrics, white static score ring/buttons | Source inspected; Android compilation and portrait/landscape/font-size QA pending |

## Baseline setup checks (before Stopwatch Mode)

- `bash scripts/test-desktop.sh`: 83 tests collected, **82 passed, 1 skipped**. The skipped `test_unix_control_plane` cannot bind an AF_UNIX socket in this host. Other TLS/WebSocket/loopback transport tests executed.
- `python3 scripts/test-battery-profile.py`: **7 passed**; fixtures test measurement tooling, not battery life.
- All **8 example plans** accepted by the production validator and actual CLI `validate` command.
- Actual `validate -` stdin command passed. Requested `start`, `reports`, `copy-report`, `delete-logs` parsers expose their documented arguments. Start payload/session identity, report filtering, clipboard and deletion behavior are covered by the existing tests; this does not prove the real user service/phone command path.
- Both Android-shared report fixtures accepted by the production desktop report validator.
- Python compilation, shell syntax and Git whitespace checks passed. All four protocol JSON files parse; full JSON Schema engine validation was not available.
- `bash scripts/build-android.sh test` and `debug` were attempted: both exited at the missing SDK prerequisite check, before compilation/tests.
- Source contains **73 Android `@Test` annotations**. Counting tests is not execution.
- `python3 scripts/check-environment.py --desktop`: passed prerequisite checks, with user-session/Wayland clipboard reminders. Full checker correctly reported missing `javac`, Android SDK and ADB. Java runtime 17 is available here; the Java compiler is not.

Historical verification and test-result text files are retained as prior delivery records. The baseline numbers here describe setup; current executed results are in VERIFICATION.md.

## Setup changes

- Added root `AGENTS.md` with architecture, preservation requirements, target-device context and pending Stopwatch Mode instructions.
- Added read-only `scripts/check-environment.py`. It does not generate credentials, install anything, initialize the service, contact the phone or delete data.
- Fixed `scripts/package-release.sh` to derive its filename from Android `versionName`; the old hard-coded 1.7.0 name was stale.
- Updated README to identify Android 1.7.1 and link this baseline.
- App runtime, timer, reward arithmetic, schemas, pairing and history formats are unchanged during setup.

## Remaining gaps and compatibility

1. Stopwatch and Achievements compile and their native JVM suites pass in 1.9.0; physical phone UI checks remain pending. Session/report contract 2 and achievement contract 1 are independent.
2. V2 UUID form is now enforced. Freshness remains GPT's responsibility; archive attempt identity allows repeated IDs with distinct start timestamps. V1 arbitrary IDs/history remain readable.
3. Top-level session wall time remains civil-clock based. V2 block wall is observed monotonic active+paused; reboot gaps remain partial.
4. Native unit tests/debug/lint passed here. Run live phone/storage/clipboard/deletion checks using the installed signing identity. No target-PC/phone setup or installation was performed here.

## Target PC commands

Upgrade source in `/home/hax/Projects/FocusDeck` while retaining its `.git`, `android/local.properties`, signing/debug keystores and all existing PC/app data. Installing requires the same app signing key as the installed APK. If a signature mismatch occurs, resolve the signing key; do not uninstall or clear data to bypass it.

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

python3 scripts/check-environment.py
bash scripts/test-desktop.sh
python3 scripts/test-battery-profile.py

bash scripts/build-android.sh test &&
bash scripts/build-android.sh debug &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh &&
bash scripts/setup-desktop.sh

bash android/gradlew lintDebug
adb -s R3CX90NC7HR shell dumpsys package com.hax.focusdeck | rg 'versionCode|versionName'
systemctl --user status focusdeck.service
focusdeck status
```

Run desktop setup as Hax in the logged-in user session, not as root. The existing setup reuses PC settings, token/certificate files and archive directories. `adb install -r` preserves app data when the package/signing identity matches. Live smoke-test commands must use fresh UUIDs and disposable sessions. Verify pairing privately; do not paste pairing tokens into source or reports.

The following command interfaces exist and are covered at parser/handler/test level; execute them against disposable sessions on the actual installation to close end-to-end verification:

```bash
focusdeck start - <<'FOCUSDECK_PLAN'
...valid session JSON with a fresh session_id UUID...
FOCUSDECK_PLAN

focusdeck reports --session UUID --latest
focusdeck copy-report --session UUID
focusdeck delete-logs --session UUID
# --all removes existing real logs: use only in a disposable test archive.
focusdeck delete-logs --all
```

The placeholder JSON above is illustrative, not a runnable smoke plan. Use `examples/text-reward-smoke-test.json` as a base, assigning a fresh UUID and current `created_at` before starting it.
