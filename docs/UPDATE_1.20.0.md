# FocusDeck 1.20.0 — source update

This update extends the uploaded 1.19.0 source with daily timelines, calendar Analytics, optional scheduling evidence, synchronized achievement order and universal dashboard resizing. Desktop and Android version names are 1.20.0; Android version code is 23. No APK was assembled, installed or tested on a physical device. No GitHub push or release was made.

## Changed source

| Area | Main files and resulting behavior |
| --- | --- |
| Desktop layout | `control_ui/layout.js`, `layouts.py`, `control_center.py`: shared edge/corner resizing, separate grips, grid reflow, bounded persisted dimensions/order, template reset and queued saves |
| Native layout | `DashboardLayout.kt`, `HomeScreen.kt`, `AchievementScreen.kt`, `AchievementCards.kt`, `AchievementDetailSections.kt`, `StudyHistoryScreen.kt`: touch handles, responsive grid/panels, local form-factor templates, internal scrolling and accessibility actions |
| Calendar evidence | `analytics.py`, `CalendarAnalytics.kt`, `Models.kt`, `TimerEngine.kt`, `TimelineAnalysis.kt`: immutable optional schedule snapshot, signed punctuality, source-zone allocation, DST handling, partial/live coverage and duplicate suppression |
| Analytics UI | `analytics.js`, `analytics.css`, `AnalyticsUi.kt`, `app.js`, `index.html`, `index-tracker.js`, `monitor.js`, `MainActivity.kt`: Day/aggregate templates, timeline metadata, line-plus-bar charts, connected drill/back transitions, scoped rankings and existing report actions |
| Compatibility/order | `achievements.py`, `Achievements.kt`, `DeckRepository.kt`, `server.py`, `viewers.py`, `DeckNetwork.kt`, `protocol/`: contract-6 receipt/revision order writes, optional schedule capability, old-device delivery guards and viewer projections |
| Isolated fixtures/docs | `control_demo.py`, `DeveloperPreview.kt`, `verify-dashboard-ui.py`, calendar tests, schemas/examples, GPT documentation and version declarations |

The phone remains timer/ledger authority. Countdown expiry, DONE, stopwatch accounting, rewards, Index receipts, deletion tombstones, pairing identities, archives, artwork and navigation positions retain their existing roles. Preview fixtures never enter the real ledger/archive. The achievement hero adapts to a narrow resized width while keeping its ring, title and description.

## Executed focused verification

- **45 Python tests passed** in the final targeted run: calendar allocation/punctuality/coverage, persisted layouts/reset, schemas and parser/control workflows, real local TLS schedule capability and pending-replay gating, exact order receipt delivery, and old/new viewer compatibility. Earlier focused baseline runs passed 51 timeline/history/control/index tests and 29 schema/workflow checks; broad suites were not repeatedly rerun.
- **55 isolated Qt WebEngine UI assertions passed**, with zero JavaScript errors. Eleven desktop/narrow layouts covered Overview, Achievements and Analytics Day/Week/Month/Year/All. Checks include handle coverage, horizontal/vertical/corner dragging, actual Qt mouse capture and live update, release save, cancel, reset, dimension retention, independent panel/achievement order, queued saves, filtering/detail dialogs, forward/back drill and all three motion settings. Sibling overlap and page overflow checks passed at 1440 and 420 pixels. All writes used in-memory DemoCenter fixtures; no phone connection was attempted.
- Seven existing battery-profile fixture checks passed. Changed Python/JavaScript sources, schema JSON and whitespace were checked. The fourteen changed/new native UI/model files and new native test file passed Kotlin syntax-tree parsing. Syntax parsing is not Android compilation or proof of Compose API/type correctness.
- Eight native `CalendarAnalyticsTest` cases were added for midnight/replay, DST, snapshots, partial/legacy evidence, source timezone, invalid schedules and order receipt/revision independence. **They were not executed.** `bash scripts/build-android.sh test` stopped at the missing Android SDK check before Gradle compilation. This environment also lacks a complete Android JDK toolchain.

Two existing desktop test assumptions were corrected: generated `contracts.js` now parses the leading JSON while preserving its existing JavaScript suffix, and the unsupported contract fixture now uses version 7 because version 5 was already supported in the baseline.

Repeat the focused desktop checks from the extracted repository root:

```bash
PYTHONPATH=desktop/src:desktop/tests python3 -m unittest \
  test_calendar_analytics test_schemas.SchemaTests test_control_workflows \
  test_focusdeck.TransportTests.test_optional_schedule_delivery_and_pending_replay_require_capability \
  test_focusdeck.TransportTests.test_layout_order_receipt_is_delivered_exactly_on_existing_transport \
  test_viewers.ViewerTests.test_old_viewers_receive_compatible_projection_without_mutating_source

# Requires the normal desktop GUI dependencies and a working display (or offscreen Qt).
PYTHONPATH=desktop/src python3 scripts/verify-dashboard-ui.py /tmp/focusdeck-dashboard-qa
```

## Local Android build and remaining checks

### Follow-up from the user's local JVM test run

The user compiled and ran 190 native tests; 188 passed and two stale assertions failed. The manual-category test included the specialized Index category introduced in 1.19.0, although Index intentionally requires its dedicated contract-5 create/migration workflow. It now tests the ordinary categories and explicitly asserts that a contract-2 manual Index upsert is rejected. The hello test still expected achievement contracts 1–4; it now expects 1–6 and asserts the independent schedule capability. Production validation was retained. The corrected native tests have not been rerun in this SDK-less environment; rerun them locally before installation.

On your PC, use the existing Android SDK/JDK and signing setup. From the extracted repository root:

```bash
export ANDROID_HOME="$HOME/Android/Sdk" # Adjust if your SDK is elsewhere.
bash scripts/build-android.sh test
bash scripts/build-android.sh debug
```

The debug APK is produced at `android/app/build/outputs/apk/debug/app-debug.apk`. Keep the installed signing identity and use an update installation; do not uninstall, clear app data or replace PC certificates/tokens as part of this change.

Still requiring local verification: Android compilation/unit tests/lint; phone/tablet touch resizing versus scrolling, grip reordering, small displays, font scaling, portrait/landscape and system navigation insets; chart popup anchoring and redraw during resize; Full/Reduced/Off plus system animator scale; restart/update persistence; phone–PC–tablet LAN order synchronization and stale/replayed receipts; old-device negotiated compatibility; and existing card/report/export/delete actions with disposable data. Desktop Qt checks ran offscreen; KDE/Wayland tiling/tray behavior and touch hardware were not exercised.

Section 20 has been implemented in source, but its device-dependent acceptance criteria remain unverified until these native and physical checks pass. See `ANALYTICS_AND_LAYOUTS.md` for evidence policy, storage keys and reset semantics.
