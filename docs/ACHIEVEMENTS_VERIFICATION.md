# FocusDeck 1.9.0 — delivery and verification

Implemented locally from the uploaded **FocusDeck-v1.8.2.zip**, inspected against actual source, documentation, tests and Git status. The upload had no Git metadata; it was imported into local branch `codex/achievements-v1.9` as a baseline, without configuring/contacting a remote. No GitHub push/CI, PC service setup or phone installation was performed.

## Delivered behavior

- Normal trophy navigation; horizontally scrolling All/Discipline/Health/Learning/Focus/Projects/College tabs; stable live overview; responsive visual tiles.
- Current/Best/signed Record gap/Average with explicit no-history results; chronological selectable chart bars, dotted Best/Average references, metadata marker, Today card and inline detailed history.
- Static green record ring with one durable 1.1-second halo per record streak; no looping animation, social sharing or invented records.
- Explicit Porn Free timestamp initialization/breaks with exact completed intervals; German/Gym/Cardio/Guitar events; dynamic project/college upsert/archive and scheduled-day neutrality; Custom Compute supplied as the initial project.
- Automatic 5/8 DONE-focus-block and 3-active-hour achievements across both modes, including live progress, pause/reboot exclusion, midnight/DST allocation and per-attempt replay protection.
- Separate actual activity and complete GPT target-revision history; latest timestamp/id wins; targets recompute until local midnight; permanent MET/UNMET/NO_TARGET; neutral NO_TARGET/unscheduled days.
- Separate durable DataStore achievement key, atomic command persistence, original timer/reward/history/pairing stores retained; independent cached PC ledger over capability-gated pinned TLS snapshots with ordered chunk ACKs.
- `focusdeck achievements validate/apply/list/history`, explicit achievement contract 1 schema, five examples and updated GPT instructions. Countdown/stopwatch math, session/report versions 1/2, original CLI and wire/pairing 1 remain intact.

## Executed checks (2026-10-07)

| Check | Result |
| --- | --- |
| Native JVM suite | **125 passed, zero failed/errors/skipped**; 91 prior regressions + 34 achievement tests |
| Desktop production/CLI/TLS/schema suite | **134 collected: 133 passed, one skipped** |
| Sole desktop skip | Production AF_UNIX control-plane bind is restricted on this host; TLS/WebSocket tests executed |
| Battery measurement tooling | **7 fixtures passed**; no physical battery savings measured |
| Android clean build | `clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **BUILD SUCCESSFUL**, all 77 tasks executed |
| Instrumented UI suite | **4 tests compiled into a test APK; not executed** (no device/emulator) |
| Android lint | **0 errors, 5 warnings**; existing SAM-name heuristic, custom X509 trust-manager warning (the transport explicitly validates the pairing pin and certificate validity), manifest backup declaration and two URI KTX style suggestions |
| APK verification | v2 signature verifies, 2048-bit RSA; 16 KiB ZIP alignment passes |
| Package metadata | `com.hax.focusdeck`, versionCode **13**, versionName **1.9.0**, min SDK **34**, target/compile **35** |
| Static/source checks | Python compilation, shell syntax, JSON Schema/example validation and Git whitespace checks passed |
| GPT Project Instructions | **7,906 characters**, with separate detailed achievement instructions |

The desktop suite includes actual CLI stdin/query execution, production daemon handlers and real temporary TLS/WebSocket command/ACK/snapshot/replay tests. Native tests cover new/existing histories, negative/zero/positive records, completed-only average, exact timestamp breaks, persistence round trips, target replacement/ties/closure, dynamic categories, neutral schedules, auto thresholds before DONE, mixed timers/breaks/stop, pause/reboot and DST/midnight allocation, plus Unicode chunk boundaries. Four UI tests exercise actual Compose pages with deterministic ledger data; their compilation is not phone interaction evidence.

Raw results: `desktop-1.9.0-test-results.txt`, `android-1.9.0-test-results.txt`, `battery-1.9.0-test-results.txt`. Prior 1.8 verification is historical and does not supersede these results.

## APK and signing

Delivered filename: **FocusDeck-v1.9.0-debug.apk**. Built from `android/app/build/outputs/apk/debug/app-debug.apk`.

SHA-256:
`ffa0f792f43c6104ad3ad8558510d20267b646e87cdab175096e352bdfe87bb0`

Debug signing certificate SHA-256:
`69bf5a71ff456e0a2dced41bf85e0b377d40cb263f3911da731871c071a78ca3`

This workspace's regenerated debug signer **differs from the earlier delivered 1.8.2 APK signer**. The installed phone's signer was not accessed. The delivered APK is suitable for a matching-key installation or a disposable fresh test device. For an existing installation signed with another key, build the source using its original PC debug/release key. **Never uninstall or clear app data to bypass an APK signature mismatch.** No keystore, token or private certificate is included in the source ZIP.

## Changed-file map

| Area | Files |
| --- | --- |
| Models/engine | `Achievements.kt`: definitions, events, actual days, target revisions, segments, records, focus cursors, command validation/evaluation |
| Persistence/capture | `DeckRepository.kt`: separate DataStore key, atomic ACK persistence, checkpoint/day closure and record acknowledgments |
| Native transport | `DeckNetwork.kt`: independent achievement capability, commands and Unicode-safe bounded in-flight snapshot chunks/ACKs |
| UI/navigation | `AchievementScreen.kt`, `MainActivity.kt`, `ic_trophy.xml`; approved references in `docs/design/` |
| Desktop | `achievements.py`, `cli.py`, `server.py`; cached history queries, validation and atomic snapshot assembly |
| Contracts/examples | `achievement-command.schema.json`, `wire.schema.json`, five `examples/achievements/*.json` |
| Tests | `AchievementEngineTest.kt`, `AchievementUiTest.kt`, `test_achievements.py`, `test_focusdeck.py`, `test_schemas.py` |
| Build/version | Android Gradle app config and UI test dependencies, desktop version files, `.gitignore`, package-release exclusions |
| Documentation | `ACHIEVEMENTS.md`, this verification, GPT achievement instructions, existing Project Instructions, README/status/workflow/setup/verification/AGENTS and results |

No Room tables were added: this project uses DataStore. The new `achievements_v1` key stores all new entities independently of existing app data. Definitions and completed segments are never age-pruned. Report deletion retains the independent achievement activity ledger and its replay cursors; it never reimports deleted reports. Target revision/record-count/archive/no-benchmark policies are documented in ACHIEVEMENTS.md.

## Remaining device checks

On the S24 Ultra, using the existing signing identity: inspect portrait/landscape/large fonts and long names; scroll filters and history; select bars; verify signed colors and the one-time halo; pause/resume/DONE across both modes; background/reopen/reboot; local midnight and timezone transitions; offline manual-event retention and automatic focus tracking; pinned LAN snapshot/command reconnection; private/visible-folder archive writes and existing deletion synchronization. Actual KDE clipboard and the AF_UNIX test require the target Ubuntu user session. Pairing/history on the user's devices were not touched.

To run UI tests, use a disposable emulator or test installation with `bash android/gradlew connectedDebugAndroidTest`; do not use the production phone as disposable test storage. Upgrade source while retaining its local signing files, Git directory, PC config/token/certificate and all archives. Build/install through the existing scripts only when the installed signing identity matches.
