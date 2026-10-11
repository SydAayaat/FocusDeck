# OMSS native UI · implementation and physical verification

Implemented locally on `codex/omss-native-ui` on 2026-10-11. Versions remain Android 1.21.0 / desktop 1.20.0. This extracted directory had no `.git`; the imported source was preserved in a local baseline commit before editing. No remote is configured or contacted.

The six latest user PNGs and the complete pasted specification are retained untouched in [design/omss](design/omss/MASTER_SPECIFICATION.md), with SHA-256 checksums. They supersede older references for these dashboard surfaces. The existing popup behavior remains authoritative because this request supplied no separate new popup image.

## Changed files and appearance

| Files | Result |
| --- | --- |
| `OmssUi.kt`, `AppTheme.kt`, `DashboardUi.kt` | Central opaque charcoal/near-black tokens, saved palette support, tabular metrics with restrained micro-shadows, cached directional GPU shadows, matte cards/mini-cards, raised circular foundations/hubs/strokes, raised tracks/fills, status pills, flat tabs and primary buttons |
| `HomeScreen.kt`, `CompactTimeline.kt` | Shared materials on Home, matching weekly/lifetime cards, outlined Analytics shortcut, raised session badges and Start button; two existing source-zone/DST-aligned tracks retain selection and the anchored popup, with directional track shadows and the white current-time dot |
| `AchievementScreen.kt`, `DashboardLayout.kt` | Four distinct nested overview cards with matched default row heights; compact ring/title/category composition, long title wrapping, truthful zero/partial/earned arcs, existing long-press options and shared edit handles; 8 dp grid spacing with matching span/drag geometry |
| `AnalyticsDashboardScreen.kt`, `AnalyticsHeader.kt` | One common scaffold/KPI set for all periods; flat text/underline period tabs and flat top-right labels; identical shared score/execution/split/project structures; Day hourly then estimation, Week seven-day distribution then hourly, Month calendar then hourly, Year twelve-month bars then hourly |
| `AnalyticsWidgets.kt`, `MonthCalendar.kt` | Cached chart geometry and grouped bar-shadow display lists; horizontal cyan dashed references behind opaque amber bars, right-side hour/minute labels with reserved space; raised time-split gauge; evidence-based expanded telemetry with two equal-height nested Strengths/Friction panels; compact month control, metric selection and intensity cells |
| `DashboardAnalytics.kt`, `DashboardAnalyticsTest.kt`, `LockedDashboardUiTest.kt` | Presentation-only reference projections plus three JVM regressions for eligible averages/zero/missing evidence and label formatting; two isolated render assertions for cyan/bar occlusion and down-right shadows |

No screenshot is rendered as a UI/control. All figures, progress fractions and report summaries come from the existing dataset, ledger, archive or performance evidence. The material uses the selected saved accent; the existing default Gold palette matches amber/cyan. Completion colors still communicate actual earned completion. Custom icons retain palette tint/grayscale behavior.

The previous per-hour eligible-day comparisons remain available in chart popups. The horizontal hourly reference is the mean of those 24 complete-day hourly means; weekly reference is mean focus of complete located observed days. Partial, live and unlocated days are excluded. Observed eligible zero participates; missing dates do not. The original score, timing, reward and archive calculations are unchanged.

Existing detail/inline histories, Index workflows, filtering, period drilling/back, All history, report copy/export/delete and local resizing/order remain accessible. Punctuality charts remain in Expanded timeline & charts; project calibration, daily timeline/session evidence and factual comparison/consistency details remain in their existing dashboard sections behind disclosures. Exact report copy still rechecks canonical history/tombstones. The approved achievement detail hero, timer, transport, receipts, authority and storage contracts were not rewritten.

## Initial source validation

Using the available JDK 17, Android SDK and cached Gradle dependencies:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
ANDROID_HOME=/home/hax/Android/Sdk \
bash android/gradlew --offline \
  compileDebugKotlin compileDebugAndroidTestKotlin \
  testDebugUnitTest \
  --tests com.hax.focusdeck.DashboardAnalyticsTest \
  --tests com.hax.focusdeck.CalendarAnalyticsTest \
  --tests com.hax.focusdeck.PerformanceAnalyticsTest \
  --tests com.hax.focusdeck.CardAppearanceTest \
  lintDebug --console=plain
```

- Main Compose/Kotlin compilation passed.
- Instrumented test Kotlin compilation passed; device tests were **not executed**.
- **38 focused JVM tests passed**, zero failures/errors (13 dashboard, 8 calendar, 10 performance, 7 appearance).
- Lint passed: **0 errors, 15 warnings, 3 informational findings**.
- `git diff --check` passed. Saved reference checksums were verified.
- Desktop and protocol sources are unchanged; desktop/battery/schema suites were not repeated for this native visual continuation.

## Initial source-only delivery limits

At the initial source-only delivery, these checks were **NOT RUN:** APK assembly/packaging, installation, physical-device/emulator UI execution, screenshot comparison, touch resizing/reordering, live synchronization, frame traces or battery measurements. No service setup, credential/signing change, data reset, push or remote CI occurred.

The implementation is source-checked and compiled, not certified as a pixel match. Shadow softness/offsets on the actual GPU, font/density/insets, long titles, default mini-card row sizing, calendar legend wrapping, narrow/large-text layouts and Full/Reduced/Off/system-scale interactions still require native screenshots and touch review. Lower cards remain scrollable; user-saved dimensions/order are retained, so customized layouts can differ from the reference defaults. The physical follow-up below supersedes these initial limitations.


## Physical S24 Ultra follow-up · 2026-10-11

The user explicitly authorized local debug build/install and physical screenshots, overriding the earlier source-only restriction. `adb devices -l` showed USB Samsung SM-S928B (`R3CX90NC7HR`) and a separate network SM-X710 tablet. Every phone command selected the USB serial; the tablet was not deployed to.

`bash scripts/build-android.sh debug` and `ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh` succeeded with JDK 17 and the existing SDK. Three grouped installation/capture passes used `install -r`; the final production-package debug APK remains installed as `com.hax.focusdeck`, version 1.21.0/code 25. Installed and new signer SHA-256 matched `a722bdbed1682ffe9309c84d0ee59e530d2282997b1d0d124236afad7355be00`. Final APK SHA-256: `2d453d1d1285a9afcecff2cf71e149a92626ef861d9df6844f5eb99ffada44c5`. No release/distribution APK or remote upload was created.

Physical feedback corrected compact card padding/type, chart heights, equal-height KPI cards, fractional-width rounding in the four-card overview, achievement filter wrapping, the Analytics header breakpoint, and rounded future-calendar cells. Saved custom heights now retain their matte plate across the reserved area. The phone's existing custom score height and layout/order were preserved. Six reference categories fit at normal phone text size; additional College/Index categories remain horizontally reachable. Large/narrow text layouts keep their adaptive path.

### Actual installed app captures

All seven requested views were opened with ADB navigation and captured using `adb -s R3CX90NC7HR exec-out screencap -p`. Foreground/window state and captured UI were checked; tabs, swipes and popup dismissal were exercised. Home popup inspection selected existing archived activity only. No timer, achievement or schedule command was used. Lower Analytics and achievement viewports were captured separately, never stitched into a purported phone screenshot.

| View | Primary physical evidence (ignored local files) |
| --- | --- |
| Home | `artifacts/uiqa/omss-device/home-final.png` |
| Daily Timeline popup | `artifacts/uiqa/omss-device/home-popup-final.png` |
| Achievements | `artifacts/uiqa/omss-device/achievements-final.png`, `achievements-lower-final.png` |
| Analytics Day | `artifacts/uiqa/omss-device/analytics-day-final.png`, `analytics-day-lower-final.png` |
| Analytics Week | `artifacts/uiqa/omss-device/analytics-week-final.png`, `analytics-week-lower-final.png` |
| Analytics Month | `artifacts/uiqa/omss-device/analytics-month-final.png`, `analytics-month-lower-final.png` |
| Analytics Year | `artifacts/uiqa/omss-device/analytics-year-final.png`, `analytics-year-lower-final.png` |

Matching-width comparisons are in `artifacts/uiqa/omss-device/comparisons/index.html`, with explicit crop/scale metadata in `alignment.json`. The phone is 1440×3120 at its existing 560 dpi override (physical 600 dpi); the supplied PNGs are 941×1672. Comparison removes native status/system-navigation insets and illustrative reference status bars, preserves aspect ratio and normalizes width. It never stretches height or center-crops away the header. No raw image-difference score is treated as a visual pass because the real data differs.

### Preservation and executed checks

A private, read-only `run-as` backup was taken before installation. After navigation, report file count/bytes, the complete achievement ledger, complete timer state, pairing, device identity and saved layouts matched exactly. The palette changed purple → gold during capture; Hax explicitly confirmed making that change. No verification command changed preferences. The timer stayed stopped; no new session/report/activity was created. Private backups, snapshots, screenshots and tokens were not committed or uploaded.

Final main/debug build, isolated instrumentation APK compilation, all **38 focused JVM tests**, and lint passed (**0 errors, 15 warnings, 3 informational findings**). All **11 native instrumented UI tests ran and passed on this S24 Ultra in the isolated `com.hax.focusdeck.uiqa` package**, covering timeline popup/dismissal, navigation/period drill, card/detail reachability, large-font adaptation, exact report selection, ring fractions, cyan references behind opaque bars and bottom-right GPU shadow contrast. An initial period test assumed a single hourly chart; the compact page exposes two distinct charts. Its assertion was scoped to the distribution card and the complete suite passed on rerun. Supplemental Day/Week/Year/Achievements fixture captures use the separate QA package only; they are not user history or primary-app evidence. Each QA activity was restarted separately before capture. Both disposable QA packages were removed after review, and the primary app was returned to Home. The installed primary APK bytes were checked against the final local debug APK and matched.

### Remaining visual differences and unexecuted checks

The result was visually compared on the physical phone and improves the requested matte hierarchy; it is not an exact pixel match. Native status/nav insets, real long titles, zero/N/A states, an existing Focus navigation item for the retained stopped session, and saved custom heights differ from the illustrative page. Analytics chart/calendar cards and some section gaps remain taller than the reference; lower content scrolls. The existing semantic calendar uses seven real date columns, five October rows and explicit missing/partial/future markers rather than copying the reference's decorative dense heatmap. Existing icon assignments and mountain logo are retained. Main cards are uniformly opaque/dry, with restrained down-right shadows and no glossy/beveled overlays.

No live synchronization transaction, timer/reward scenario, physical resize/reorder gesture, Full/Reduced/system-scale setting change, landscape device setting, FPS trace or battery measurement was performed. The isolated large-text and OFF-motion checks are execution evidence for those QA compositions, not a claim about every physical setting. Desktop/protocol suites were not repeated for this native-only change. No uninstall/clear of the primary app, pairing reset, signing change, service setup, GitHub push or remote CI occurred.
