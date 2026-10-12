# OMSS UI recovery and visual-data isolation

Verified locally on 2026-10-11, branch `codex/omss-native-ui`. Android remains 1.21.0 / versionCode 25; desktop remains 1.20.0. This continues the existing Compose implementation. The latest pasted request explicitly authorized debug assembly, nondestructive deployment and physical USB verification; those actions supersede the earlier source-only instructions for this task. No release assembly, push, remote CI, service setup, tablet modification, reset or destructive reinstall occurred.

## Diagnosis and architecture

The discrepancies came from layout and measurement choices rather than limitations of Compose: approximately 40 dp of parent Analytics gutters, Material's minimum interactive-layout reservation inside compact rows, undersized gauge radius, a reserved chart-label strip, and navigation reserved even during immersion. Screenshot pixels also cannot be used as dp: the physical phone captures are 1440 × 3120 at an overridden density of 560 dpi.

The existing native OMSS foundation remains in `OmssUi.kt`: opaque charcoal plates and nested surfaces; cached native paths and GPU RenderNode/RenderEffect directional shadows; separate gauge foundation, colored arc and raised hub; raised tracks, pills, icon hubs and primary controls; flat tabs; restrained numeric shadows. There is no framework migration, glossy surface, shadow bitmap regeneration or new continuous decorative loop. Shadows retain the established bottom-right hierarchy (card 4/8/16 dp, nested 3/5/10 dp, component 2/4/7 dp).

This recovery extends that foundation with a native timeline callout foot, motion-aware circular value transitions and finite chart reveals. Full uses the reveal; Reduced suppresses the chart reveal and shortens other shared transitions; Off/system scale zero uses immediate values. Existing shared drill, press and resize/reflow motion remains. Gauge value changes rebuild small arc geometry during finite transitions; no sustained frame-rate or allocation-free claim is made.

Day/Week/Month/Year continue through one `AnalyticsDashboard` and the existing shared `DashboardGrid` in `DashboardLayout.kt`. The 2 dp outer gutter and internal card padding are distinct. Card resizing, template/form-factor dimensions, phone-owned achievement ordering, receipt guards and saved custom heights remain intact. Compact Material controls no longer reserve empty 48 dp layout rows; Compose's minimum touch hit testing and accessibility actions remain.

Shared `OmssGeometry` supplies proportional score gauges and 76/64 dp chart bodies. The native hourly plot now uses the right edge formerly reserved for labels. Drawing order is grid, cyan dashed average, bar shadows, opaque amber bars, then labels. The average label is at the extreme right above the line, with two-digit minutes and no extra "avg" text. Unknown bars remain different from measured zero; actual averages still exclude partial/live coverage.

Achievement cards use 48 dp gauge containers with a .42 centerline radius rather than 44 dp/.36. This increases the visible centerline diameter by approximately 27%; it does not fabricate progress. Minimum content height aligns bottom statistics, long titles wrap, totals retain units, and Overview stays four raised mini-cards. The approved upper achievement-detail composition is unchanged.

The compact Monday-first Productivity Calendar groups seven weekday columns and three measured eight-hour bins per date. It preserves real leading/trailing dates, source-zone hour allocation, zero/unknown/partial/future distinctions and date/week drill actions. Score/completion selection remains available as one cell per date. Controls and legend use compact native geometry; no new timer target or inferred study activity is introduced.

Immersion removes the persistent dashboard navigation strip. Home's options menu retains Home/Achievements/Focus destinations, Analytics options include Home/Achievements and its existing tools, and Achievements has an accessible Home action. Normal mode retains navigation, including the existing saved nonimmersive preference on the primary phone. Bottom system-gesture safety is applied alongside safe drawing insets.

## Isolated visual data

`visualtest` is a debug-derived Gradle build with application ID `com.hax.focusdeck.visualtest`, its own sandbox, a plain `android.app.Application` and a fixture launcher. Its merged manifest removes the production activity/service and INTERNET/network/notification/foreground-service permissions. The fixture host instantiates no repository, transport, DataStore or production context and keeps all scene/layout state in memory. Dangerous commands are no-ops; achievements are read-only. No persistent production mock switch exists.

`VisualTestDataProvider` resides only under `src/uiqa`, shared by the legacy isolated `uiqa` and new `visualtest` source sets. It supplies fixed date/time/zone, native timer-generated reports, named Index items, progress/streak definitions and clearly designated illustrative presentation projections. Production composables accept these projections only under the existing ephemeral `LocalDashboardPreview`; ordinary production callers supply none. The production debug APK's DEX was inspected and contains no fixture provider or launcher classes. Release source-set isolation was inspected; a release APK was not assembled.

Fixtures cover REFERENCE, EMPTY, PARTIAL, COMPLETED, INCOMPLETE, LONG_TEXT, POSITIVE, NEGATIVE, LARGE and ZERO. Empty is unknown; ZERO has explicitly observed zero-duration days. PARTIAL conserves located plus unlocated focus and is excluded from hourly references. Named Index states produce 32/126 topics. Completed progress is 1250/1250, with progress-only rendering. The inconsistent reference's filled ring beside 0/1250 is represented by actual 720/1250 in the reference fixture.

Illustrative 86/100, 18 sessions, 42 DONE, reference distributions and telemetry copy are explicit display projections, separate from calculation inputs. Production formulas never consume those display numbers. Timelines, calendar activity, unknown evidence and genuine progress continue using valid records. Some reference contradictions are deliberately retained only as designated illustration text; measured totals and actual dates take precedence elsewhere.

## Data correctness and preservation

One real bug was found: `PerformanceAnalytics.report(now)` used the supplied observation clock for record generation but the system wall clock for `period_closed`. Historical/fixed-clock observations could therefore claim a period was closed while their own evidence was still current. The same `now` now reaches current/previous summaries and default record generation. A focused regression verifies closure; no scoring, timer, reward, storage or wire formula was rewritten.

Home's absent daily/weekly evidence now displays N/A instead of inventing zero; explicitly observed zero remains zero. New calendar-bin regression checks preserve measured totals and unknown versus zero. Existing focused tests validate DST/source-zone allocation, midnight conservation, partial coverage, execution/estimation/calibration, comparisons, streak/progress receipts and named Index projections.

The primary debug APK was updated with the existing `scripts/install-apk.sh` and explicit APK argument / USB serial. `install -r` succeeded with the matching installed signing certificate SHA-256 `a722bdbed1682ffe9309c84d0ee59e530d2282997b1d0d124236afad7355be00`. The final installed APK SHA-256 equals the locally built debug APK.

A private read-only pre-update backup and byte/SHA-256 snapshots remain in the ignored local artifacts folder. All eight protected files are identical after installation, primary Home launch and isolated QA: the private report, complete DataStore preferences (timer/ledger/pairing/config), four CSV exports and two shared-preference files including layouts. The ninth file, `files/profileInstalled`, changed only as AndroidX ProfileVerifier's installation timestamp/cache marker; its format was verified against the dependency bytecode. Thus all protected data is unchanged, but not every installation-generated byte is unchanged. No real session, achievement write, delete/import, seed, pairing command or reset was performed.

The one stored primary report was read from the private backup and passed the existing production desktop report validator (contract 2, three blocks). Production Home was separately captured with genuine values. The backup/report contents are intentionally excluded from the review gallery.

## Physical screenshots and remaining differences

The phone was explicitly selected as USB `R3CX90NC7HR`, model SM-S928B. The connected tablet was not used. Prototype score-card and achievement-card captures were reviewed before the broader changes. Batched physical comparisons then covered all seven reference scenes: Home, Home timeline popup, Achievements and Analytics Day/Week/Month/Year, plus independent scrolled viewports. Final icon/callout captures refresh those affected scenes. Additional actual captures cover empty Analytics, completed progress, and long text in landscape at composition-only 1.4 font scale with Reduced motion. No global phone font/orientation/animation setting was changed.

The six latest standalone PNG references match their tracked copies under `docs/design/omss`; the popup uses the separate approved `home_!.jpeg`. Supplied comparison panels were read as LEFT reference / RIGHT previous actual. References were inspected by content. Full comparisons normalize width while preserving aspect ratio and all content; separately aligned comparisons record their chrome masks. No stretched/cropped comparison is treated as proof of exact fidelity.

Measured opaque Analytics plate spans x=8 through x=1431 of the 1440-pixel display, or 98.89% width. A physical native layout assertion independently checks greater than 98% width. This intentionally overrides decorative reference side margins. Larger visible gauges, reduced empty row reservations, extended plotting width, compact calendar rows and aligned achievement statistics correct the principal geometry defects. Materials remain opaque, with directional shadows and a raised hub/arc/track hierarchy.

This is a close native adaptation, not a pixel-identical certification. Android text metrics, display density, cutout/gesture insets and full-width requirements produce different vertical proportions; lower sections scroll. Existing custom production sizes/order are preserved and can differ from fixture defaults. October 2026 has five real calendar rows; October 9 is Friday rather than the reference's Thursday. Unknown/zero rings stay unlit. The popup correctly labels 06:03 against planned 06:00 as three minutes late. Time Split uses a consistent 5h31 fixture tracked total instead of the inconsistent 29h48 mockup total. Production metric copy remains factual, and some decorative trend/status text and reference icon choices differ. The gallery exposes these differences without hiding them in a pixel score.

Local evidence: [review gallery](../artifacts/uiqa/omss-recovery/gallery.html), [geometry](../artifacts/uiqa/omss-recovery/geometry.json), [preservation](../artifacts/uiqa/omss-recovery/preservation.json), [final source match](../artifacts/uiqa/omss-recovery/installed-source-match.json). These are ignored local review artifacts, not publicly hosted files.

## Validation and rendering sample

| Check | Executed result |
| --- | --- |
| Production debug Kotlin/Compose compilation and assembly | Passed; primary updated and launched |
| Visualtest compilation/assembly and Android test APK | Passed; isolated app installed |
| Focused production JVM tests | 101 passed, zero failures/errors/skips: AchievementEngine 40, AchievementProgress 17, CalendarAnalytics 8, DashboardAnalytics 15, IndexTracker 11, PerformanceAnalytics 10 |
| Visual fixture JVM tests | Four passed, including deterministic clock, independent state, icon validity, conservation and projection isolation |
| Physical instrumented Compose tests | 14 passed: 11 existing locked-dashboard tests plus three variant/isolation/geometry/navigation/text tests |
| Debug lint | Passed: zero errors, 15 warnings, three informational findings |
| Capture script syntax and Git whitespace | Passed |
| Real stored report validation | One of one stored reports passed the production validator |

Commands used include `testDebugUnitTest` with the six focused classes, `testVisualtestUnitTest --tests com.hax.focusdeck.VisualTestDataProviderTest`, `assembleDebug`, `assembleVisualtest`, `-PvisualTestTests assembleVisualtestAndroidTest`, `lintDebug`, and actual `am instrument` on the isolated package. The final fixture conservation adjustment was rechecked with its four JVM tests and rebuilt/reinstalled; reference layout did not change. Fourteen physical UI tests were executed on the shared final rendering implementation before that fixture-only adjustment.

One bounded Full-motion sample switched native period tabs 12 times after warmup/reset in the isolated fixture, with phone animator scale 1. `dumpsys gfxinfo` recorded 465 rendered frames, 13 janky (2.80%), p50 8 ms, p90 12 ms, p95 38 ms and p99 117 ms. Raw frame data and [sample metadata](../artifacts/uiqa/omss-recovery/rendering-sample.json) are retained. This includes transition/layout work and Android's reported percentiles; it is not a sustained FPS, power or production-traffic benchmark. The tail indicates remaining transition jank. Requesting up to 120 Hz does not establish stable 8.33 ms delivery.

No release compilation/assembly, full desktop/battery rerun, end-to-end live phone/tablet synchronization, real session/reward scenario, exhaustive system-setting matrix, physical drag/resize acceptance trace or sustained frame/battery benchmark was performed in this recovery. Desktop source was unchanged; its validator was used read-only. Native Android compilation and touch/UI execution were available and actually performed, so they are not reported as unavailable.

## Changed source files

Modified Android build/host files: `android/app/build.gradle.kts`, `src/uiqa/AndroidManifest.xml`, `UiQaActivity.kt`, `UiQaFixtures.kt`.

Modified native presentation: `OmssUi.kt`, `DashboardUi.kt`, `HomeScreen.kt`, `CompactTimeline.kt`, `AchievementScreen.kt`, `AnalyticsDashboardScreen.kt`, `AnalyticsHeader.kt`, `AnalyticsWidgets.kt`, `MonthCalendar.kt`, `MainActivity.kt`, `StudyHistoryScreen.kt`.

Modified calculation/test evidence: `DashboardAnalytics.kt`, `PerformanceAnalytics.kt`, `src/test/.../DashboardAnalyticsTest.kt`, `src/androidTest/.../LockedDashboardUiTest.kt`. Production timer/repository/transport/ledger sources and contracts are unchanged.

Added: `src/main/.../DashboardPresentation.kt`, `src/uiqa/.../VisualTestDataProvider.kt`, `src/testVisualtest/.../VisualTestDataProviderTest.kt`, `src/androidTestVisualtest/.../VisualIsolationUiTest.kt`, this report. Updated `scripts/capture-locked-ui.py` and `docs/PROJECT_STATUS.md`. Paths abbreviated above are under `android/app/src/.../java/com/hax/focusdeck/`.

The capture script refuses an unexpected phone/package or INTERNET-enabled visual APK before installation. It supports scene, fixture, motion, immersive setting, composition font scale, orientation and separate lower captures. For example:

```sh
PATH=/home/hax/Android/Sdk/platform-tools:$PATH python3 scripts/capture-locked-ui.py \
  --serial R3CX90NC7HR --screens home home-popup achievements day week month year \
  --lower --out artifacts/uiqa/review
```

This builds/installs only the isolated visualtest variant. `--no-build` reuses the installed isolated build. User-supplied untracked `ui-references/` was preserved.
