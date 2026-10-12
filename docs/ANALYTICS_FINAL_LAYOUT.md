# Final Analytics rows and immersive navigation · 1.21.0 continuation

Incremental continuation on 2026-10-11, local branch `codex/omss-native-ui`. Android remains 1.21.0 / code 25; desktop stays 1.20.0. The existing uncommitted OMSS recovery was the baseline and remains intact. The initial source-only pass was followed by explicitly requested USB verification of the isolated visualtest variant, then an explicitly authorized in-place production update. No reset, remote operation, production data seeding or global device-setting change occurred. Each verification phase is scoped below.

## Inspected baseline and references

Read `PROJECT_STATUS.md`, `OMSS_RECOVERY.md`, the existing screenshot comparison PDF, current `AnalyticsDashboard`, `DashboardGrid`/resize storage, native OMSS primitives, app/dialog navigation/insets and the isolated visualtest provider/tests. The baseline already supplies full-width Analytics, cached directional shadows, layered gauges, chart rendering and complete fixture/deployment isolation. It still had Break Time as the fourth KPI, a full-width Plan Execution Score, Execution paired with Time Split, and menu-based immersive destinations.

The latest images are preserved byte-for-byte under `docs/design/omss/analytics-final/`. `cards_finale.png` governs the new shared cards; `day.png` supplies the alternate previous composition and does not override the finalized Variance/Punctuality rows. Older period-specific references, recovery screenshots, comparison PDF and gallery remain unchanged. The existing capture script now selects the finalized Day reference; Week/Month/Year keep their own original chart references. Physical comparisons also review those screens against the finalized shared-card reference.

The pasted attachment is a design specification, not independent authorization to install apps. The initial direct repository instructions specified source-only work. Hax subsequently explicitly required building/deploying the isolated visualtest app and verifying it throughout on the USB S24 Ultra. That authorization applies to the isolated package only. Earlier physical evidence in `OMSS_RECOVERY.md` describes previous source; fresh evidence for this continuation is recorded below.

## Shared implementation

One `AnalyticsDashboard` supplies Day, Week, Month, Year and the existing All view. Defaults are:

1. Focus / Sessions / Blocks Done / Variance.
2. Plan Execution Score / Time Split, approximately 71% / 29% after the shared 8dp gap.
3. The original primary period chart: hourly / weekly / calendar / monthly.
4. Execution / Punctuality Trend, approximately equal widths after the same gap.
5. Unaffected projects, lower period content and Executive Telemetry.

`AnalyticsCompactRows.kt` supplies common KPI, measure, paired-row and compact card composables. The score description, metric icons and chevrons are removed; percentages are inline. Time Split puts tracked duration below its heading and retains original segment inspection without a chevron. Native score/segmented gauges, matte cards, tracks, palette and directional GPU shadow code are reused.

Paired rows occupy a single item in the existing `DashboardGrid`. A small opt-out from its outer frame avoids duplicate resize handles; both child cards still use `ResizableCardFrame` and retain `plan-score`, `distribution`, `execution` and the new `punctuality` IDs. Row reordering uses the shared grip and storage. Existing saved dimensions/order are not deleted or rewritten during display. Widths normalize within the row; the taller requested saved height controls row height. Small widths or font scale above 1.25 use a vertical fallback. User layouts and internal scrolling can therefore differ from fixture defaults. Pairing intentionally means Time Split moves with Plan Execution rather than remaining an independent grid slot.

New period scans, KPI comparisons and punctuality aggregation run on `Dispatchers.Default`. A changed input creates a fresh pending projection rather than reusing the previous compact projection. There are no repository, transport, timer or ledger writes from these components.

## Measurements and missing data

Focus, distinct matching attempts, DONE counts and tracked durations use existing calendar/report evidence. Focus change uses the existing performance comparison. Session/DONE changes use those same previous bounds and complete/closed comparable-coverage gate; a missing or zero denominator yields Trend N/A. Unobserved totals are N/A, while recorded zero counts stay zero. No scoring policy or calendar allocation was changed.

Variance is the arithmetic mean of the selected period's recorded start deviations. Positive means actual start was late; negative means early. Its favorable/unfavorable tint follows that meaning. Plan score/completion/punctuality/efficiency retain the existing duration-weighted formulas. Inline sample counts are evidence, not a replacement denominator; drilldown retains the full policy and coverage explanation. Production status remains factual Recorded/Partial/Provisional/N/A rather than an invented Great Day threshold.

`punctualityTrend` uses `CalendarDay.punctuality`, already attributed to actual-start dates in captured source zones. Day shows seven dates ending at the selected date; Week shows Monday–Sunday; Month groups intersecting Monday-based weeks; Year groups twelve months; All groups years. Each bucket averages its actual observations. The header averages all observations in the displayed trend window, preserving sample weights rather than averaging daily means. Day's contextual trend average may differ from the selected-day Variance KPI by design. No evidence is null/N/A; an explicitly on-time observation is measured zero.

Signed native bars use a zero baseline, positive/cyan and negative/amber, with value and period labels. Colors do not invert the established late/early data sign. Longer series have an internal horizontal plotting viewport. Accessibility actions disclose values/sample counts and open evidence. Only Full reveals bars; Reduced, Off and system scale zero render final geometry immediately. No continuous animation or frame-time persistence was added.

The existing visualtest provider supplies deterministic source-day punctuality for all four periods, positive/negative states and separate illustrative KPI/score/metric captions. Empty/zero/partial states still use measured inputs. These compile only in the existing isolated app; production ignores presentation overrides outside `LocalDashboardPreview`.

## Navigation, selector and viewport

`PrimaryNavigation` binds visible 20dp icons with explicit 48dp targets to the existing Home/Achievements/Focus/Analytics callbacks. Active selection is amber; inactive icons are muted. Focus is disabled when no session/viewer destination exists. Navigating never acknowledges rewards or issues timer commands. Analytics callbacks close its existing dialog before selecting another destination. Existing drill/detail Back handlers and nonimmersive bottom navigation remain; returning from explicitly selected Home can return to the current session.

Home reuses its header, Analytics reuses the top connection/date/options row, and the primary Achievements/detail host keeps navigation reachable above its existing approved content. Focus/reward/viewer headers use the same icons. The isolated host reuses these components; it has no real timer destination, so its Focus action stays disabled. Compiled UI tests separately cover all four enabled callback routes.

The date surface measures the exact rendered text style, requests content width bounded by the available header space, retains both arrows and the current range, and stacks at narrow/large-font widths. Physical review exposed Month/Year intrinsic-width collapse and unnecessary label wrapping; explicit measured width fixes both. A native clock drawable avoids the bundled icon fallback for Focus and Punctuality without changing achievement icon contracts. The label also opens the existing date chooser. The top-right period indicator stays flat.

The primary activity now explicitly enables edge-to-edge, matching the existing isolated host/dialog setup. A shared viewport inset function applies once per window: visible drawing insets normally, actual cutout/system bars/IME in immersion, plus mandatory bottom gesture safety. Main immersive decorative vertical padding reduces from 12dp to 4dp. Analytics' extra grid tail reduces from 24dp to 8dp; no hidden navigation strip is reserved. No arbitrary negative offset or global safe-area disable was used. Fresh isolated-app screenshots verify ordinary bottom navigation and immersive cutout/gesture clearance in the captured portrait and large-text landscape cases. Transient system-bar and keyboard states were not exhaustively exercised.

## Focused verification

- Production debug Kotlin/Compose and isolated visualtest compilation: passed.
- Production JVM: 39 passed, zero failures/errors/skips: AnalyticsCompact 6, CalendarAnalytics 8, DashboardAnalytics 15, PerformanceAnalytics 10.
- Visual fixture JVM: 5 passed, zero failures/errors/skips.
- Visualtest instrumented Kotlin compilation and physical execution: passed, 24 tests. Includes existing achievement/native tests plus six isolated Analytics/navigation/date/scroll/isolation checks.
- Debug lint: zero errors, 14 warnings, 3 informational findings.
- Capture script Python compilation, Git whitespace and reference byte/hash checks: passed.

Six new JVM regressions exercise trailing-day versus selected-day scope, signed/zero/missing values, observation weighting, month boundary groups, yearly/source-date grouping, distinct attempts, comparison coverage and zero-denominator handling. The existing calendar/performance suite retains DST, conservation, partial/live exclusion and scoring evidence.

Checks used local JDK 17 and SDK at the configured paths, with cached dependencies offline. Commands included `compileDebugKotlin`, `compileVisualtestKotlin`, `testDebugUnitTest` limited to the four listed classes, `testVisualtestUnitTest --tests com.hax.focusdeck.VisualTestDataProviderTest`, `-PvisualTestTests compileVisualtestAndroidTestKotlin` and `lintDebug`. Logs are retained locally under `artifacts/uiqa/analytics-final-source/`.

During the isolated verification phase, only visualtest/application-test APKs were assembled and installed. Production debug Kotlin compilation/lint passed at that point without production APK assembly or installation. The later authorized production deployment is recorded separately below. No emulator, frame-rate benchmark or live synchronization test was used. No desktop implementation changed, so no unrelated desktop/battery sweep was run.

## Physical verification follow-up

`adb devices -l` confirmed USB Samsung `SM-S928B`, serial `R3CX90NC7HR`. Every phone command explicitly selected that serial. The separately connected tablet was not used. The phone renders at 1440×3120, density override 560; its global density, font, orientation and animation settings were not altered. Activity orientation/font-scale extras affect only the isolated QA composition.

Only `com.hax.focusdeck.visualtest` and `com.hax.focusdeck.visualtest.test` were installed with `install -r`. Manifest checks reject unexpected package IDs/network permission. Both installed APK hashes match their built files in `installed-source-match.json`. Physical instrumentation verifies no production MainActivity/service and no INTERNET permission in this isolated app.

The first physical pass found the selector-width and clock-icon issues described above. One old achievement test attempted `performScrollTo()` before its lazy item existed; it now scrolls the tagged lazy list to that node. The final corrected suite reports **OK (24 tests)** in 37.91 seconds on this phone. Assertions cover equal-height pairs inside the viewport for all four periods, all four enabled navigation callbacks, normal bottom navigation, date labels/arrows/date-choice/next-period actions, lower-content reachability and app isolation. The isolated host has no active timer, so its actual Focus icon is disabled; the enabled Focus callback is tested separately without starting production work.

Physical captures, comparisons and preservation results are retained under `artifacts/uiqa/analytics-final-physical/`. [Open the local screenshot gallery](../artifacts/uiqa/analytics-final-physical/gallery.html). Fourteen scene/configuration captures supply 28 top/lower original screenshots; three real navigation taps and two deep landscape captures bring the total to 33. Reviewed cases: all four periods plus Home/Achievements in immersive portrait; ordinary Day with bottom navigation; Day/Year/Home/Achievements in 1.4× text landscape with Reduced motion; empty, provisional/partial with settled Full motion, and observed-zero Day. The compact rows are aligned within the full-width grid in normal-text portrait; large-text fallback remains scrollable. The exact text style is shared between selector measurement/rendering, and physical date tests assert one-line labels for all four periods. Year punctuality uses internal horizontal scrolling; it does not discard the remaining months. The actual isolated-host tap sequence Home → Achievements → Analytics → Home retained selected-state highlighting. Eight vertical swipes in large-text landscape reached the final GPT Performance Report action; a screenshot records it with bottom clearance.

Read-only before/after tar streams of the complete readable production private directory were hashed without extraction. All **nine files** remain byte-identical, including the report archive, DataStore settings/achievement ledger, exports and dashboard layouts. The production APK path and SHA-256 are unchanged. `preservation.json` records this scoped comparison. Private tar snapshots remain owner-readable only (0600) and are not included in the gallery. The production app was never launched, installed, cleared or seeded during this isolated verification phase.

Original PNGs are direct `adb exec-out screencap -p` results, including lower scrolled viewports. Comparison images preserve aspect ratio; they are not fabricated app captures.

Native text metrics, factual status text, source-day labels, missing evidence, actual sample counts and consistent Time Split totals differ from illustrative numbers. Current palette remains the existing golden/amber native palette. No pixel-identical, sustained 120Hz or battery claim is made. Production timer/reward integration is compiled and callback-tested; no real session/reward command was exercised. The subsequent production launch/navigation smoke check is recorded below. Saved user resize layouts, transient bars, keyboard and every motion/palette combination were not exhaustively touch-tested.

## Authorized production deployment · 2026-10-11

Hax explicitly requested applying the completed changes to the phone. USB `SM-S928B` / `R3CX90NC7HR` was re-detected and selected for every command. `:app:assembleDebug --offline` built current source successfully; versions remain 1.21.0 / code 25. ZIP integrity, production package/MainActivity and absence of `VisualTestDataProvider`/`UiQaActivity` in production DEX were checked. The built APK's signing certificate matches the installed certificate: SHA-256 `a722bdbed1682ffe9309c84d0ee59e530d2282997b1d0d124236afad7355be00`.

A read-only backup of the full readable private directory and previous APK was saved before installation. The existing `scripts/install-apk.sh` with explicit fresh APK and `ADB_SERIAL=R3CX90NC7HR` performed `install -r`; it returned Success. Installed APK SHA-256 matches the freshly built APK. Immediately after installation, all nine private files were byte-identical to the before snapshot.

Production MainActivity launched successfully. Actual top-icon taps navigated Home → Analytics → Achievements → Home, with correct selected highlighting. Original production screenshots record the new rows with actual stored evidence rather than mockup values. No real timer/session, achievement edit, import/delete, reward acknowledgment or pairing command was issued.

After launch/navigation, all eight protected files remain byte-identical: complete DataStore settings/timer/achievement ledger/pairing state, report archive, four CSV exports and two shared-preference files including layouts. Only `files/profileInstalled`, AndroidX ProfileVerifier's installation timestamp/cache marker, changed after launch; therefore not every installation-generated byte is unchanged. Private snapshots remain 0600 and were not extracted or restored. Evidence is retained under `artifacts/uiqa/analytics-final-production/`: build/install logs, `apk-validation.json`, `installed-source-match.json`, `preservation.json`, route results and actual production screenshots. No uninstall/clear, emulator, release build, push or remote CI occurred. Earlier 24 physical UI / 44 focused JVM checks remain applicable because no implementation source changed during deployment.

## Files changed in this continuation

All Kotlin paths below are under `android/app/src/`:

- `main/java/com/hax/focusdeck/AnalyticsDashboardScreen.kt`
- `main/java/com/hax/focusdeck/AnalyticsCompactRows.kt` (new)
- `main/res/drawable/analytics_clock.xml` (new native clock icon)
- `main/java/com/hax/focusdeck/AnalyticsWidgets.kt`
- `main/java/com/hax/focusdeck/AnalyticsHeader.kt`
- `main/java/com/hax/focusdeck/DashboardAnalytics.kt`
- `main/java/com/hax/focusdeck/DashboardPresentation.kt` (existing recovery addition extended)
- `main/java/com/hax/focusdeck/DashboardLayout.kt`
- `main/java/com/hax/focusdeck/DashboardUi.kt`
- `main/java/com/hax/focusdeck/MainActivity.kt`
- `main/java/com/hax/focusdeck/StudyHistoryScreen.kt`
- `main/java/com/hax/focusdeck/AchievementScreen.kt` (fallback Home control only)
- `uiqa/java/com/hax/focusdeck/UiQaActivity.kt`
- `uiqa/java/com/hax/focusdeck/VisualTestDataProvider.kt`
- `test/java/com/hax/focusdeck/AnalyticsCompactTest.kt` (new)
- `testVisualtest/java/com/hax/focusdeck/VisualTestDataProviderTest.kt`
- `androidTestVisualtest/java/com/hax/focusdeck/VisualIsolationUiTest.kt`
- `androidTest/java/com/hax/focusdeck/AchievementUiTest.kt` (lazy-list test selector only)

Also: `scripts/capture-locked-ui.py`, `docs/PROJECT_STATUS.md`, this report, and the two new PNG reference copies. Earlier recovery changes in the working tree remain and are separately documented in `OMSS_RECOVERY.md`.
