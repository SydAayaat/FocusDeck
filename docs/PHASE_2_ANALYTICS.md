# Phase 2 Analytics · source delivery

Android 1.21.0 / desktop 1.20.0; development branch `codex/phase-2-analytics`. The original local 1.21.0 ZIP was checked against the starting source. This continuation changes Analytics and additive report evidence; timer/reward policies, authority, pairing and user archives are preserved.

## Behavior and calculation

Day, Week and shared Month/Year/All templates use the existing local grids. Desktop Day places the planned/actual timeline beside Session Detail and its factual report. Native Day expands the timeline/details in the existing estimation panel. All uses the aggregate dashboard. Existing archive actions and expanded charts remain available.

Plan Execution Score replaces the earlier mislabeled mean BeatScore/focus-share display. BeatScore remains in canonical session reports. Score is `100 × C × (0.60 + 0.25P + 0.15E) / available weights`: C is duration-weighted DONE / eligible planned focus; P is duration-weighted `max(0, 1 − max(0, actualStart − plannedStart)/15 minutes)`; E is duration-weighted `max(0, 1 − abs(actualActive − plannedActive)/plannedActive)` for closed DONE focus. Missing P/E weights are omitted and the remainder normalized, with coverage disclosed. Stopwatch, unknown/zero planned durations and future scheduled starts are excluded. Current/source-day or live records are provisional. Planned intervals are split across source-zone midnights using real elapsed time; original per-block estimation remains intact. Scores aggregate underlying weights. No-plan scores are N/A.

Start deviation is positive late, negative early. Estimation excludes pauses; shorter actual work means the plan overestimated duration. Aggregate absolute errors cannot cancel. Original planned active duration/mode are captured additively in schedule snapshots; legacy incomplete plans explicitly disclose missing coverage. Older snapshots remain readable. Companions with strict old report schemas must be updated together to read the new optional fields.

Hour bars expose located focus/break/pause evidence, contributors and eligible averages; partial/live/unlocated days are excluded from the average denominator. Overtime is a marked subset of active focus, cyan pauses are non-focus. Top Sessions/Projects has persistent ascending/descending Time and Total sorting. Calendar offers Score/Hours/Completion and distinct missing/partial/zero/future states. Month buckets are days, Year months, All years; hierarchical drilldown remains available.

Consistency uses complete valid planned days and an 80/100 threshold, with mean and population standard deviation. Calendar comparisons disclose boundaries and absolute differences; percentages require closed periods with equal complete-recorded-day coverage proportions and compatible components. Missing/zero denominators yield N/A. All has a lifetime trend without a fictitious previous period. Deterministic factual reports export the measurements, coverage, contributions, comparisons and project/category calibration; every copy recalculates from current canonical history and tombstones. No API call or parallel database is introduced.

## Executed checks

- 124 focused desktop tests passed: performance/calendar, schema, Control Center/workflows, timeline, viewers, index synchronization, history and skip/end. Shared Python/Kotlin golden fixture covers six score cases.
- 28 Android JVM tests passed (Performance 10, Calendar 8, Dashboard 10). Offline `compileDebugKotlin`, `compileDebugAndroidTestKotlin` and `lintDebug` succeeded; lint: 0 errors, 15 warnings. No APK assembly.
- Native offscreen Qt/WebChannel: 44 performance interaction/responsive checks and 55 shared-grid checks passed; no JavaScript errors, tested horizontal overflow or card overlap. Widths 1440/960/420 and Full/Reduced/Off covered. Screenshots are disposable fixture evidence, not real history.
- Battery-profile fixtures: 7 passed. JavaScript syntax and `git diff --check` passed.
- Required full desktop script was attempted: 287 tests, 1 failure, 4 dependency errors, 10 skips in the existing Qt virtual environment (missing optional jsonschema). Schema tests passed in the dependency-complete focused run. The unrelated old-phone achievement test expects “1.9” in an error that now names contract 1; this exact failure was reproduced from the untouched baseline. It was not suppressed or changed.

Useful reruns after installing the project's existing dependencies: `bash scripts/test-desktop.sh`, `python3 scripts/test-battery-profile.py`, and `PYTHONPATH=desktop/src python3 scripts/verify-performance-ui.py /tmp/focusdeck-performance-ui` in a Qt-capable environment. Native focused checks use the existing Gradle tasks above, with the three analytics test classes.

## Verification limits

The attachment available for this request contains text; the six new reference images were not present. Existing approved repository references guided the layouts, so matching those six new images cannot be confirmed. Native compilation is verified; Android device/emulator rendering, touch/accessibility, real KDE clipboard/tray and live phone/tablet synchronization are unexecuted. No APK, installation, service setup, data reset, remote push or CI. Existing identity/signing/data remain untouched.

## Changed files

- `android/app/src/main/java/com/hax/focusdeck/AnalyticsDashboardScreen.kt`
- `android/app/src/main/java/com/hax/focusdeck/AnalyticsHeader.kt`
- `android/app/src/main/java/com/hax/focusdeck/AnalyticsUi.kt`
- `android/app/src/main/java/com/hax/focusdeck/AnalyticsWidgets.kt`
- `android/app/src/main/java/com/hax/focusdeck/CalendarAnalytics.kt`
- `android/app/src/main/java/com/hax/focusdeck/CompactTimeline.kt`
- `android/app/src/main/java/com/hax/focusdeck/DashboardAnalytics.kt`
- `android/app/src/main/java/com/hax/focusdeck/DashboardLayout.kt`
- `android/app/src/main/java/com/hax/focusdeck/MonthCalendar.kt`
- `android/app/src/main/java/com/hax/focusdeck/PerformanceAnalytics.kt`
- `android/app/src/main/java/com/hax/focusdeck/PerformanceDetails.kt`
- `android/app/src/main/java/com/hax/focusdeck/StudyHistoryScreen.kt`
- `android/app/src/main/java/com/hax/focusdeck/TimelineAnalysis.kt`
- `android/app/src/test/java/com/hax/focusdeck/CalendarAnalyticsTest.kt`
- `android/app/src/test/java/com/hax/focusdeck/DashboardAnalyticsTest.kt`
- `android/app/src/test/java/com/hax/focusdeck/PerformanceAnalyticsTest.kt`
- `desktop/src/focusdeck/analytics.py`
- `desktop/src/focusdeck/control_center.py`
- `desktop/src/focusdeck/control_demo.py`
- `desktop/src/focusdeck/control_ui/analytics.css`
- `desktop/src/focusdeck/control_ui/analytics.js`
- `desktop/src/focusdeck/control_ui/layout.js`
- `desktop/src/focusdeck/performance.py`
- `desktop/tests/test_calendar_analytics.py`
- `desktop/tests/test_performance_analytics.py`
- `docs/ANALYTICS_AND_LAYOUTS.md`
- `docs/PHASE_2_ANALYTICS.md`
- `docs/PROJECT_STATUS.md`
- `protocol/fixtures/performance-score.json`
- `protocol/report-v1.schema.json`
- `protocol/report-v2.schema.json`
- `scripts/package-release.sh`
- `scripts/verify-performance-ui.py`
