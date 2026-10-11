# OMSS native UI · source-only continuation

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

## Executed validation

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

## Remaining visual verification

**NOT RUN:** APK assembly/packaging, installation, physical-device/emulator UI execution, screenshot comparison, touch resizing/reordering, live synchronization, frame traces or battery measurements. No service setup, credential/signing change, data reset, push or remote CI occurred.

The implementation is source-checked and compiled, not certified as a pixel match. Shadow softness/offsets on the actual GPU, font/density/insets, long titles, default mini-card row sizing, calendar legend wrapping, narrow/large-text layouts and Full/Reduced/Off/system-scale interactions still require native screenshots and touch review. Lower cards remain scrollable; user-saved dimensions/order are retained, so customized layouts can differ from the reference defaults. No device visual discrepancies have been measured in this continuation.
