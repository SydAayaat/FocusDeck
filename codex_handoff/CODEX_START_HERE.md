# Codex autonomous implementation handoff — FocusDeck locked mobile UI

**Mission:** Implement the seven visually locked screenshot references as *native, functional Jetpack Compose UI* in the user's **existing full local FocusDeck application**, while retaining all current behavior, storage, navigation, synchronization, timers, tracking and reporting. Do not build a separate demonstration app, static image-based screen, Figma pipeline, or web UI. Don't stop at planning: perform the implementation, verification, and self-correction in carefully bounded milestones.

## 0. Inputs and authoritative precedence

This handoff directory contains:

- `references/home.jpeg` — Home, normal state
- `references/home_!.jpeg` — **same Home screen**, Daily Timeline popup open
- `references/achivements.jpeg` — Achievements, normal/non-edit state (original filename intentionally preserved)
- `references/day.jpeg` — Analytics Day
- `references/weekl.jpeg` — Analytics Week (original filename intentionally preserved)
- `references/month.jpeg` — Analytics Month
- `references/year.jpeg` — Analytics Year
- `DESIGN_UPDATED.md` — semantic component rules, chart semantics, data/state behavior and visual design principles
- `REFERENCE_SHA256.json` — locked originals' checksums
- `tools/compare_ui.py` — optional repeatable screenshot QA utility

**Precedence**: (1) preserving real working behavior and safety/integrity of stored data, (2) the latest locked reference screenshot for the *appearance and arrangement of that particular screen/state*, (3) DESIGN_UPDATED.md for intended behavior, actual chart semantics, data source, interactions and responsive adaptation, (4) existing application conventions, (5) your implementation judgment. If some screenshot contains inconsistent placeholder data, dates, navigation destinations, or a timing-sign typo, do not encode incorrect demo facts as real behavior. Preserve the *visual composition*, not incorrect fixture values. Mark unavoidable discrepancy in QA report.

**Absolute prohibition:** No redesign, rearranging cards, replacing icons with random artwork, changing layouts to a generic Material template, using screenshots as image backgrounds or screenshot overlays, or silently removing any working controls. Rebuild widgets from native editable/drawable Compose UI. One-shot generated screenshots are only references. Do not change the originals.

## 1. Repository selection and safety

1. Start in the **full, actual local codebase**. The previously used path is `/home/hax/Projects/FocusDeck`; locate the correct active project if this moved. There has also been a separate `FocusDeck-ui-rebuild/FocusDeck` copy. **Do not assume a GitHub clone is complete**: as of handoff, `SydAayaat/FocusDeck` `main` has only README.md and is not the full application. Do not replace a real local checkout with that remote snapshot.
2. Discover the project root by Android Gradle files, Compose sources, desktop/control-center code, scripts and history; verify `git status`, current branch, working directory and the app's real entry points. Record baseline commit/status and modified files. Never use `git reset --hard`, destructive clean, overwrite local modifications, rewrite migrations, delete stored state or stage secrets.
3. If safe, make a new non-destructive working branch like `ui/locked-design-2026-10-11` (or worktree/checkpoint preserving any working tree changes). If uncommitted changes make this unsafe, protect them and report one concrete blocker rather than discarding them.
4. Read only relevant files first; avoid wide repo rewriting. Inventory existing Compose UI theme, Home, Achievements, Analytics, navigation, timer, domain models, repositories, device bridge, database and tests. Read `DESIGN_UPDATED.md` and the seven screenshots before changing code.
5. Capture **baseline screenshot(s)** from the actual running app if possible, and record any pre-existing test failures as baseline. Do not claim existing failures were introduced by the new UI. Previous sessions reported `AchievementProgressTest` and `AchievementEngineTest` failures; confirm present state rather than assuming.
6. Provide a short scope map of what will change vs must stay untouched, then **proceed autonomously**; don't ask permission between routine milestones.

## 2. Feature-preservation contract (must remain operational)

- Existing **session schema, session_id, per-block timer_mode (`countdown` / `stopwatch`), plan imports, GPT-generated reward text/tier policy, score/weight calculations, reports, CSV exports and histories** remain intact. No new fake reward tokens or simplified data contract.
- Phone remains timer/stopwatch executor. Countdown reaches zero, turns red, keeps counting overtime, and advances only with FINISH/DONE; do not add auto-advance. Stopwatch behavior, dial rules and per-block controls must follow actual existing implementation. Preserve End Block, Skip Block and End Session through existing menus when present.
- Existing phone-PC sync/device pairing, tablet read-only viewer and desktop Control Center behavior remain intact. Don't modify sync protocol merely to re-skin cards.
- Achievements data types **STREAK_ONLY, PROGRESS_ONLY, STREAK_AND_PROGRESS**, category filters, persisted streak/best/gap, history, INDEX topics, reopen, stable topic identifiers, daily confirmations and developer/example mode stay functional. The normal screenshot omits editing controls, but **Edit Layout**/reorder/resize functionality must remain available through the existing supported entry point; don't delete it.
- Day/Week/Month/Year Analytics, detailed Day time-estimation accuracy, breakdown charts, period navigation, drilldowns and popups remain functional. Do not replace chart values with image-fixture numbers.
- Preserve actual navigation destinations even though some reference images differ between 3-item and 4-item nav examples. Follow the current app's real navigation graph and use the locked visual materials; no destination deletion.
- Preserve persisted local data; no destructive migrations/clears during testing. Add test-only deterministic fixtures via a debug/test seam if needed; never hardcode screenshot sample content into production default state.

## 3. Visual implementation details — do not improvise alternatives

### Shared theme and composition
Use matching near-black/graphite backgrounds, dark raised card surfaces, dark cast shadows and tightly controlled amber/gold, cyan, teal, muted red and pale-gray accents. Avoid glass, bright card borders, blur-bloom, generic Material 3 redesign, extra empty margins and neon rims. Respect source screen proportions at Samsung S24 Ultra portrait logical width (approx. 384–412dp) with native system safe areas and font scaling. Text, progress, rings, charts and icons are true Compose components (`Canvas` where helpful) with accessibility semantics; not flattened screenshots.

### Home (two images, one screen and one overlay state)
Preserve header, FocusDeck mark, hero typography, today's focus, week/all-time metrics, Analytics shortcut, Last Session, Upcoming Session, and navigation. Daily Timeline is compact, with **TWO thin horizontal tracks stacked close together**: planned top, actual below; no "PLAN" or "ACTUAL" label clutter, no fat bars. Time labels 00/06/12/18/24 (depending available width); colored segments positioned by true local-time start/end. Tapping a segment anchors a small floating task-detail popup overlapping other content (not pushing cards down). Popup includes task title, planned/actual timestamps, meaningful duration and delta. Actual 06:03 vs planned 06:00 is **3 minutes late** (never present it as early). Outside tap/back closes. `home_!.jpeg` is the open overlay **state**, not another screen.

### Analytics Day
Preserve header date navigator aligned beside title, Day-selected control style, four small KPI cards, Plan Execution Score, **Focus by Hour in minutes**, Execution and Time Split side by side, Top Projects & Sessions with period contribution segments, complete Time Estimation Accuracy breakdown, shorter Executive Telemetry Insights. Correct sample inconsistencies through functional data logic; don't change chart dimensions / order arbitrarily.

### Analytics Week
Reference `weekl.jpeg`. Seven narrow close amber daily bars with hours on Y-axis and optional cyan average line, compact KPI/plan execution, Execution and ring-only Time Split, segmented Top Projects & Sessions, focus hourly bars, detailed Telemetry Insights with Strengths/Friction panels. Selected Week tab is neutral raised graphite, not necessarily the Day border treatment.

### Analytics Month
Same shared analytics shell/card patterns as Week; replace weekly focus distribution with **Productivity Calendar**. Month button selected in the same neutral raised style as Week; heatmap remains compact with weekday columns and meaningful low/high focus colors; correct real calendar structure (don't replicate mistaken artificial fixture day placements). Retain segmented project bars and other cards.

### Analytics Year
Same pattern as Month; replace Calendar with **12-month vertical Productivity by Month bar chart**; X-axis Jan–Dec, **Y-axis HOURS**. No calendar in Year. Actual data aggregation in selected year, not pasted samples.

### Achievements
Three-column compact portrait achievement grid and Overview summary as screenshot allows at normal text size; adapt gracefully to other widths. This reference is NORMAL mode: no edit toggle, handle overlay, Done button or reorder footer shown. Preserve the capability of Edit Layout via existing access outside this state. Achievement ring behavior: unearned/0% German card has thin unlit neutral ring and empty progress (0/1250 words), not a glowing full orange ring. PTfS sample displays 32/126 topics (~25%) with matching one-quarter ring arc AND matching horizontal bar. Real app uses stored value. Earned streak icon rings can have restrained local glow; no full ring at arbitrary 0%. Ring animations respect FULL/REDUCED/OFF modes.

### Top Projects & Sessions (all analytics periods)
Each project row has category dot, title, duration and compact right-end horizontal track. **Amber** = total completion achieved **before** selected range, **cyan** = additional completion *within* current Day/Week/Month/Year range, dark remainder = incomplete. Do not mislabel session time share percentages as completion percentages. If target cannot be computed from stored data, display a truthful unavailable state; never invent achievements or project targets.

### Timer (outside seven locked screenshots)
**Do not rebuild or remove the existing Timer during these seven screens.** It has a separately generated theme reference, but this handoff's seven locked screenshots do not authorize a Timer overhaul. Keep it functional; put optional theming in a separate follow-up unless essential for shared theme consistency.

## 4. Autonomous staging and reusability

Implement in **bounded logical milestones**, committing each after appropriate checks if git state allows:

- **M0 — baseline & safety:** correct local project, existing behavior map, baseline screenshots/tests, reusable theme plan.
- **M1 — shared tokens/components and Home normal** (visual pass). No behavior rewrites.
- **M2 — Home timeline second track, floating popup and tap/close behavior** (visual + interaction pass).
- **M3 — Analytics common shell, Day-specific cards and detailed breakdown**.
- **M4 — Week variant and seven-day chart**.
- **M5 — Month calendar variant**.
- **M6 — Year 12-month chart**.
- **M7 — Achievements grid and data-bound progress/streak rings**.
- **M8 — regression, accessibility/responsiveness, final screenshot gallery and concise implementation report**.

Extract reusable primitives where genuinely shared: `FocusDeckTheme`, `FDCard`, KPI tile, ring, segmented progress, bar chart, timeline track, popup, navigation item, month heatmap, achievement compact card. Reuse existing shared UI if available rather than creating duplicates. Preserve user data sources and `ViewModel` contracts. Avoid adding libraries/rewriting architecture unless unavoidable; document any dependency changes.

**Do not implement everything as a single mega-diff.** Build/test/checkpoint once per meaningful milestone. If a milestone cannot meet its visual or regression gate after a few targeted attempts, report exactly what failed and stop that milestone; do not silently continue into more screens with a broken theme.

## 5. Automated visual verification (must actually run when environment permits)

1. Detect connected Android device/emulator with `adb devices -l`. Preferred available physical device: Samsung S24 Ultra, known earlier serial `R3CX90NC7HR` (confirm, don't assume). Detect `applicationId`, launch activity, orientation and correct app state from code; use approved build/install scripts where possible.
2. For each milestone's screen/state, set deterministic **debug-only fixture state** and selected date/period if needed, or use real data while explicitly reporting mismatch in values. Never erase real stored user data or mutate production history merely to create images. Automated navigation via Compose UI test tags / UI Automator / safe explicit-intent debug deep link preferred over brittle tapping pixel coordinates.
3. Capture actual device UI with `adb -s <serial> exec-out screencap -p > artifacts/uiqa/<screen>.png` after waiting for layout/animation settle. Keep captures for: Home, Home-popup, Day, Week, Month, Year, Achievements.
4. Compare against the exact file in `references/` using `python3 .../tools/compare_ui.py --reference ... --actual ... --out ...`. Inspect `overlay.png`, `difference.png`, and `report.json` for structural issues. Align/crop system bars, white screenshot gutters, dynamic times and device resolution as necessary. **Pixel similarity is diagnostic, not proof of correctness**; original references are generated compositions and may differ in dynamic metric content or system chrome.
5. Automatically assess at least: card position/order; header/date navigator alignment; x/y margins; two thin timeline track heights; correct popup overlap; top-project amber/cyan segments; ring partial arc; font fit/no clipping; persistent nav position; chart axis units; no horizontal overflow. Require screenshot-review reasoning (visual/vision if available), not only average pixel error. Apply up to **two targeted fix passes per milestone** with before/after evidence and no arbitrary redesign.
6. If no device/emulator, lack of UI test data, or build tools block real rendering, mark `visual QA: NOT RUN`, give cause and exact next command. Don't claim visual match, don't use a saved Figma raster as the actual app capture, don't manufacture results. Add screenshot-test hooks so local QA becomes a one-command action once ADB is available.

Suggested capture when available (adapt `applicationId`, scripts and build task):

```bash
adb devices -l
# Build only the needed local debug variant for screenshots, NOT a distributed release APK.
# Use project-approved scripts first; otherwise app:assembleDebug as applicable.
adb -s R3CX90NC7HR exec-out screencap -p > artifacts/uiqa/home.png
python3 codex_handoff/tools/compare_ui.py \
  --reference codex_handoff/references/home.jpeg \
  --actual artifacts/uiqa/home.png \
  --out artifacts/uiqa/home_compare
```

Do not fake an end-to-end capture with the screenshot itself. Screenshot references may have different outer borders, cropping, sizes and mock numeric fixtures; note them explicitly.

## 6. Functional testing strategy — fast but meaningful

- Before UI changes: record baseline targeted tests, compilation state, failure signatures and uncommitted status.
- After each milestone: compile changed Compose sources; run focused tests on changed ViewModels, domain rules and UI tests as appropriate, not full test suite on every tiny pixel adjustment.
- Run a final local debug build and focused complete regression set covering session data, scores, rewards, local storage, sync/pairing, analytics and achievements. Record pre-existing failures separately from newly caused failures. **Never change or delete tests merely to make them pass.**
- Test two important UI states: no data, and partial progress; 0% German and 25%-ish PTfS debug fixtures where safe. Test rotation/smaller device widths, back navigation, selected tab state, outside-popup tap, reduced motion and accessibility content descriptions.
- Do not build/export/distribute unnecessary final APK artifacts to the chat. Local incremental debug builds are authorized when needed for actual screenshot comparison. Don't repeatedly full-clean or download dependencies without cause.

## 7. Status output, acceptance and stopping rules

At the end of each stage, provide **one compact** status block: files changed, UI behavior implemented, tests executed, screenshot captured path(s), visual comparison notes and unresolved issues. Avoid dumping huge Gradle logs. Keep a `artifacts/uiqa/STATUS.md` report with stages and actual evidence. Commit only passing milestones to your work branch when safe; do not push remote or merge main without an explicit separate instruction.

Completion is accepted only when:

- [ ] All seven screenshots are represented as **six real app screens/state groups**: Home, Home Popup, Day, Week, Month, Year, Achievements.
- [ ] UI is data-bound native Compose, not screenshot layers or hardcoded sample UI.
- [ ] Home compact two-track timeline popup works and contains correct delta semantics.
- [ ] Period selectors and project current-period progress are correct.
- [ ] Achievement rings correctly render 0%, partial and full progress, without fake glow.
- [ ] No established navigation, modes, data exports, sync, rewards, index history, resizing/edit mode or timer functionality is lost.
- [ ] Automated screenshot evidence has been generated **or honestly reported as blocked**; major visible differences have been iteratively corrected.
- [ ] Regression checks have run or blocking conditions are clearly enumerated.

**Start now with M0 and continue through the milestones independently, pausing only on an unavoidable access/safety blocker. The output is an implementation, not another redesign proposal.**
