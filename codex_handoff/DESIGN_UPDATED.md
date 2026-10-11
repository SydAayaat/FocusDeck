---
version: alpha
name: FocusDeck — Matte Graphite Mobile (Approved Multi-Page UI)
description: >-
  A compact matte-graphite design system for FocusDeck's Android, tablet and desktop
  Home, Achievements and Day/Week/Month/Year Analytics screens, incorporating the
  latest approved cards, data semantics, and minimal two-track daily timeline.
colors:
  app-background: "#111214"
  background-deep: "#0D0E10"
  card-surface: "#1C1D20"
  surface-raised: "#242529"
  surface-recessed: "#141518"
  navigation-surface: "#141518"
  track-unfilled: "#30343B"
  divider-subtle: "#292B2F"
  text-primary: "#F5F5F6"
  text-secondary: "#A4AAB5"
  text-tertiary: "#757E8E"
  amber-primary: "#F7A915"
  amber-highlight: "#FFC12B"
  cyan-secondary: "#18C8E5"
  teal-success: "#17C99C"
  red-negative: "#F04453"
  idle-neutral: "#737C8B"
  shadow-color: "#000000"
  popup-background: "#242529"
  popup-text: "#F5F5F6"
  achievement-ring-inactive: "#343B43"
  achievement-ring-glow: "rgba(247,169,21,0.22)"
typography:
  page-title:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: -0.025em
  card-title:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 700
    lineHeight: 1.3
  body:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.45
  metric:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.2
  micro-label:
    fontFamily: "JetBrains Mono"
    fontSize: 11px
    fontWeight: 500
    lineHeight: 1.3
  chart-label:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: 400
    lineHeight: 1.3
rounded:
  xs: 6px
  sm: 9px
  md: 12px
  lg: 16px
  xl: 20px
  pill: 999px
spacing:
  xxs: 2px
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  xxl: 24px
components:
  app-canvas:
    background: "{colors.app-background}"
  card:
    background: "{colors.card-surface}"
    radius: "{rounded.lg}"
  raised-control:
    background: "{colors.surface-raised}"
    radius: "{rounded.md}"
  inset-panel:
    background: "{colors.surface-recessed}"
    radius: "{rounded.md}"
  chart-track:
    background: "{colors.track-unfilled}"
  primary-data:
    color: "{colors.amber-primary}"
  secondary-data:
    color: "{colors.cyan-secondary}"
---

# FocusDeck — DESIGN.md

## Overview

**Product:** FocusDeck, a focus-session execution, achievement, and personal analytics app. Android/mobile is the priority; the style must also scale to tablet and desktop without changing its identity.

**Authoritative visual references:** the finalized mobile Analytics **Day and Week**, the subsequent **Month and Year** variants, the normal-view **Achievements** screen, and the **Home/Overview** screen with its *two ultra-thin daily timeline tracks and floating task popup*. The Week screen remains the *base visual language* for Analytics, **not** a mandate to copy its cards to other periods. Preserve each screen's most recently approved distinct content and layout.

**Visual idea:** sophisticated dark tactile instrumentation — graphite, soft physical depth, crisp high-contrast typography, and disciplined amber/cyan accents. No planet/eclipse hero image on Home. Avoid glassmorphism, glossy gradients, light-streak borders, neon chart bloom, or giant empty cards. Restrained **achievement-icon/ring highlights** and the explicit golden Home Analytics shortcut are *local exceptions* to the otherwise matte non-glowing card system.

**Priority order when instructions conflict:** (1) latest explicit user correction for the specific screen/control, (2) latest approved image for that screen, (3) previously approved functional/data semantics, (4) this shared design system, (5) generator inference. Never copy demo values as actual user data, and never redesign other cards because one card was changed.

## Colors

The YAML colors are semantic defaults, not arbitrary decoration. The original screenshot's charcoal canvas and common card surface were sampled at approximately `#111214` and `#1C1D20` respectively. Remaining values are intentionally proposed harmonizing tokens and may be fine-tuned from the source image.

| Role | Token | Visual intent |
| --- | --- | --- |
| Whole app | `app-background` | Matte near-black charcoal, not pure blue-black |
| Main card | `card-surface` | Slightly lighter graphite; opaque and unreflective |
| Raised selected control | `surface-raised` | Gray-on-graphite state, not a luminous outline |
| Recessed inset | `surface-recessed` | Physically sunken, e.g., telemetry sub-panels |
| Text | `text-primary`, `text-secondary`, `text-tertiary` | Clear hierarchy without washed-out gray labels |
| Focus, productive progress | `amber-primary` | Solid amber/gold; no halo |
| Secondary time/progress series | `cyan-secondary` | Cool cyan, used deliberately rather than universally |
| Good performance | `teal-success` | Success change or positive status |
| Late or unfavorable variance | `red-negative` | Loss/late states |
| Idle/remaining | `idle-neutral`, `track-unfilled` | Muted grays distinct from colored active data |
| Unstarted achievement ring | `achievement-ring-inactive` | Thin unlit dark graphite track at 0% |
| Earned achievement ring highlight | `achievement-ring-glow` | Subtle local amber halo on selected achievement icons/rings only |

Keep the palette consistent across cards. Colored data objects can be vivid enough to read, but general **card surfaces, shadows and borders remain neutral dark**. Don't put colored ambient light behind charts/cards. A low-intensity warm halo may be applied **only** around achievement icon rings that are in a highlighted/earned state; zero-progress rings must remain unlit. Do not recolor existing data categories randomly between views.

Optional exploration of other accent palettes is permitted *only when explicitly asked*. The default approved scheme remains amber + cyan + teal + muted red over graphite.

## Typography

Use **Inter** or a comparable clean system sans-serif for headings, navigation, descriptions, and large metrics. Use **JetBrains Mono** sparingly for timing, small numeric metadata, date ranges, or technical data where aligned digits improve scanning. On Android, fallback to native system font / system monospace if needed.

- Screen title bold, prominent, not oversized.
- Card headers bold and easy to scan; body copy medium contrast.
- Metric tiles: tiny uppercase label with a strong numeric value and subtle change indicator.
- Use tabular numerals for durations, percentages, and chart axes.
- Avoid lettering that is so small it only looks readable in enlarged mockups. Generally keep secondary labels around 11–12sp minimum and main body around 12–14sp.
- Preserve established feature names: “Plan Execution Score,” “Weekly Focus Distribution,” “Productivity Calendar,” “Productivity by Month,” “Execution,” “Time Split,” “Top Projects & Sessions,” “Focus by Hour,” “Time Estimation Accuracy,” and “Executive Telemetry Insights.”

## Layout

### Mobile target

- Primary reference: **Samsung Galaxy S24 Ultra portrait**, implemented responsively at roughly **384–412 logical dp** width, respecting safe areas, status/nav bars, system font scaling, and landscape behavior.
- Treat design-token `px` values as *logical layout units* for conceptual mockups; translate appropriately to Android dp/sp in implementation. Do not render a 1440-physical-pixel-wide UI as if it were a 1440-dp layout.
- Do **not** use a tablet-width, two-column overall dashboard shrunk to fit the phone.
- Mobile outer gutters approximately 14–18dp. Vertical card gap typically 10–14dp. Internal card padding approximately 12–16dp, using 8–10dp for dense sub-rows.
- **Cards use individual content-based heights.** Do not match heights across cards or increase heights for symmetry. `align-items: start` for side-by-side content; never `stretch` unless explicitly required by content.
- Main content scrolls vertically behind a visually discrete, bottom-anchored navigation bar. Do not let the bottom navigation obstruct content; include bottom scroll inset.
- Four KPI tiles can remain in one compact row if typography is legible at actual phone width. Wrap or reduce nonessential microcopy rather than making the tiles giant. The screen may adapt to two columns at larger text sizes.
- Only the small **Execution** and **Time Split** cards are deliberately side-by-side at phone width; other detailed analytics cards span the available width. This does not mandate identical heights for the two cards.
- Do not reserve blank space for tooltips/popups. Tooltips float over neighboring UI and are positioned to stay within the screen bounds.
- Desktop/tablet may use an adaptive grid and movable/resizable cards, preserving each card's content proportions and this styling. The separate **Edit Layout** state must also be available for supported cards on mobile. It is not visible in the normal Achievements viewing state.

### Exact approved Weekly Analytics order (top → bottom)

1. Compact status row: green synced dot, “Synced · 100%”; Week 41 chip and overflow menu.
2. “Analytics” heading; adjacent date navigator showing the selected week.
3. Day / **Week selected** / Month / Year segmented navigation.
4. Four small metric cards: Focus, Sessions, Done Rate, Variance.
5. **Plan Execution Score** (full-width, compact).
6. **Weekly Focus Distribution** (full-width, seven daily bars).
7. One row with **Execution** (left) and compact circular **Time Split** (right).
8. **Top Projects & Sessions** (full-width).
9. **Focus by Hour** (full-width, amber bars only).
10. **Executive Telemetry Insights** (full-width, **final/bottom content card**, restoring original detailed layout).
11. Fixed bottom navigation: Home, Focus, GPT, Analytics (active amber).

This is the approved **Week** arrangement, not the universal order. Do not silently insert, remove, move, duplicate or consolidate its core cards. Punctuality Trend and GPT Performance Report are reusable optional analytics cards, but should not interrupt this exact page without explicit instruction.

### Period navigation: same frame, different data

- All Analytics period screens share the same graphite canvas, top status row, **Analytics title with the date navigator adjacent to its right**, segmented Day/Week/Month/Year control, compact KPI tiles, card radii, gutters, and bottom-navigation styling. The **Day date navigator must not become a separately centered row below the tabs**. Use the Week header positions as the alignment reference.
- In the segmented control, selection should use the **same slightly raised neutral gray treatment as the approved Week tab** when showing Week, Month, or Year; no new colored outline, gradient, or special Month/Year styling. Reuse common geometry, padding, text sizing, and corner radii. Keep Day in the same control family rather than inventing an unrelated tab widget.
- Update the date navigator and status chip to reflect the *selected period*: a calendar date for Day, a week range for Week, a month/year for Month, and the selected year for Year. Do not mix a YEAR 2026 chip with a Week 41 range as though both select the same period. The reference screenshots sometimes contain mismatched fixture dates; fix the logic, not the visual styling.
- KPI labels and metric values can vary legitimately by period. Do not force the Week metric labels or its hardcoded sample numbers onto Day/Month/Year.
- Analytics screenshots show a four-destination Home / Focus / GPT / Analytics bottom bar; Home/Achievements mockups show a three-destination Home / Achievements / Analytics bar. **This is an unresolved mockup navigation-set inconsistency, not permission to remove real app destinations.** Preserve the actual app's implemented navigation set while unifying the surface, icons, active amber state, and bottom positioning.

### Day Analytics — approved structure

1. Compact status row and header: **Analytics** left, selected date navigator alongside it right; Day selected within the shared segmented control.
2. Four small day metrics: **Focus, Sessions, Blocks Done, Break Time** (demo only: `4h 12m`, `18`, `42`, `42m`).
3. **Plan Execution Score** compact full width: same ring plus three submetrics and popup behavior as Week.
4. **Focus by Hour** full width: 24 narrow amber bars, 00–23 bins; for a single day the Y axis uses **minutes** (such as 0m/15m/30m/45m/60m).
5. **Execution** and **Time Split** side by side, both content-fit.
6. **Top Projects & Sessions**, with project duration and amber previously-completed + cyan **done today** segmented progress bars.
7. **Time Estimation Accuracy** full-width detailed card. Preserve the accuracy summary, duration deviation, estimated vs actual comparison, and **By project type** progress rows. Overestimation/underestimation colors follow actual favorable/unfavorable semantics. Never reduce this detailed Day card to a badge.
8. **Executive Telemetry Insights**: the approved **shorter Day variant**, including its summary and momentum badge, not necessarily the larger two-inset Week layout.
9. Fixed bottom navigation using the shared material.

**Day chart layout rule:** there is no Weekly Focus Distribution/Calendar/Monthly chart inserted above Focus by Hour. Preserve the Day-specific order even if doing so means different card positions from Week.

### Month Analytics — minimally changed from Week

- **Month** is selected using precisely the same neutral raised selected-tab design as the Week reference. Top date navigator selects the displayed month (e.g. `Oct 2026`).
- Use the **identical Week card order, typography, surfaces and sizing rules**, with only one core-card substitution: replace **Weekly Focus Distribution** in slot 6 with the compact full-width **Productivity Calendar**. No additional UI redesign.
- Calendar uses a labeled, compact **month heatmap** with weekday columns Mon–Sun, correct calendar dates/week rows, dark empty cells, **amber for higher-focus days**, and **cyan for lower-focus days** as established by its legend. Use consistent thresholds based on actual data and clearly distinguish no-data days from real zero-focus days; preserve compact square cells and gentle rounding.
- Date cells are tappable, opening an anchored date/focus-duration popup without resizing cards. Weeks fit within the selected month (typically 4–6 rows); scrolling or an adapted dense layout is acceptable if necessary for font scaling.
- Keep Execution, Time Split, **the latest Top Projects & Sessions segmented-bar design**, Focus by Hour and the detailed Executive Telemetry Insights in the same relative order. Use period-correct metrics rather than recycling the Week numbers.

### Year Analytics — minimally changed from Month

- **Year** is selected with exactly the same neutral raised tab styling as Week and Month. Navigator is year-specific (e.g. `2026`).
- Keep the Month page unchanged **except** replace the Productivity Calendar in its exact position with **Productivity by Month**, a compact full-width vertical amber bar chart with **12 bars (Jan–Dec)**. No other card/layout changes are authorized by this variant.
- **X axis = months; Y axis = total focus HOURS**, clearly labeled with `h` ticks (for example 0h, 15h, 30h, 45h, 60h depending on actual maximum). Never use minutes or percentages on the Year chart's Y axis.
- Match the Weekly Focus Distribution/Focus by Hour bar style: solid slender amber bars, comfortable tight spacing, faint graphite gridlines, **no decorative bloom**. Bar tap opens a non-reflowing popup with month and actual focus hours.
- If a cumulative total is displayed at chart top right, derive it from the same twelve bar values. Future months should show missing/unelapsed states rather than fabricated focus hours.

### Home / Overview — finalized mobile design

**This is the current approved Home visual, replacing the older planet/eclipsed-hero mockup.**

1. Full matte-black/graphite background, top system-safe status area, small FocusDeck **mountain-in-circle app logo**, FocusDeck wordmark, connected-phone status and overflow menu. **Remove the decorative planet/orbit/eclipse art entirely.** Do not modify the actual mountain logo.
2. Concise date and large two-line motivational headline (reference: “READY FOR YOUR NEXT SESSION”). Below: **Today's Focus** large number, completed sessions and blocks summary.
3. Compact **This Week** and **All-time Focus** metrics side by side, optionally a comparison percentage based on real data.
4. Wide, thin-height **Analytics shortcut** with its distinctive amber outlined button treatment. The amber outline is an approved action-specific accent, not a general glowing-card border style.
5. **Daily Timeline** — extremely compact card; header left, chosen day right, then two **separate ultra-thin horizontal tracks stacked with only a small vertical gap**. Top = PLANNED; bottom = ACTUAL. **Remove the visible “PLAN” and “ACTUAL” text labels**, but do not remove either track. The two tracks share the same 00:00–24:00 horizontal time scale and occupy nearly the entire inner card width.
6. **Last Session** detail card with its existing focus title, duration, execution status, timestamp and navigation affordance.
7. **Upcoming Session** card with title, planned time and Start control, as depicted in the latest approved Home mockup; show actual available session data or a purposeful empty state.
8. Bottom navigation, styled to match the other screen surfaces and respecting safe-area insets.

#### Daily Timeline: exact compact interaction rules

- Thin rounded tracks are **lines**, not tall stacked event tiles or full-height Gantt bars. The line thickness should remain readable/tappable but visually minimal, approximately **4–6dp visible stroke** each, with **4–8dp vertical gap**. Expand hit targets invisibly to at least accessible touch dimensions. The *entire card* should be compressed around header, thin tracks and sparse ticks, without artificial top/bottom blank space.
- Both tracks share fixed labeled ticks `00:00`, `06:00`, `12:00`, `18:00`, `24:00`; thinner intermediate tick marks are allowed. Plan segments use amber/light neutral distinctions; Actual segments use cyan and other approved status colors as appropriate. A fine vertical current/selected-time marker may cross both tracks.
- Segment length and start position encode actual timestamps on the **same 24h scale**; no automatic rescheduling or misleading normalization. The current-time indicator must not be fabricated in screenshots built from real data.
- On tap/hover of either track, a **small floating dark graphite tooltip** appears **overlaid**, anchored to the chosen interval. Demonstration content: task or index topic title (e.g. `PTfS — Phase 1 — Core Theory`), planned start–end, actual start–end, and a **start punctuality delta**. Small amber outline/soft shadow, muted secondary text, and a teal favorable or red unfavorable delta are permitted. Popup *does not reserve space* or increase card height and should ideally float above the lines, repositioning as needed to avoid the viewport edge.
- Punctuality math must be coherent: **actual start 06:03 against planned 06:00 = 3 minutes LATE** (red), *not* `+3m early`. **Actual start 05:57 = 3 minutes EARLY** (teal). Use the app's established favorable-positive/late-negative sign convention in UI. Don't trust contradictory decorative numbers in generated references.
- Card tap to expand into the full day timeline remains available. In-place Home interaction is popup/highlight only: **do not trigger Year→Month→Week→Day morph transitions inside the Home card**. Expanded day view can contain its richer two-track drilldown and breaks.
- Minimize overall timeline card height, but preserve readability of date, hour labels and popup, and allow the popup to overlap content outside the card's nominal bounds rather than clipping it.

### Achievements — normal mode + progress examples

- Use the same graphite shadows, typography, amber visual language and compact cards as Analytics. Header **Achievements** with subtitle “Track your streaks, goals, and progress.” Category tabs shown in reference: **All, Discipline, Health, Learning, Focus, Projects**. Keep the understated active-tab amber underline and preserve existing app-specific category/filter behavior.
- The compact four-stat **Overview** card keeps Active streaks, Personal bests, Daily target, and Progress cards, with real values in production.
- Normal browse mode has an adaptive compact three-column achievement grid where the portrait viewport permits, with larger/wider cards for long project titles and index progress. Preserve cards such as Porn Free, Daily Objectives Complete, Gym, Cardio, German, Guitar, 5+ Focus Blocks, 8+ Focus Blocks, 3+ Hours Active Focus, Custom Compute, and PTfS index; titles in mockups are demonstration examples rather than mandatory fixture data.
- **Normal viewing mode shows NO Edit Layout toggle, NO top Done button, NO drag handles/dashed drop target, and NO fixed bottom “Reset order / Drag to reorder / Done” toolbar.** Those are permitted only in the distinct Edit Layout mode (accessible through the existing app's overflow/menu interaction, not a compulsory permanent toggle).
- **Achievement ring visual states differ from analytical donut charts**. Active/earned streak badge icon rings can have a tightly restrained amber glow, as shown by the approved mockup. **German at 0 of 1,250 words** shows a thinner **dark gray, completely unlit 0% outer ring** around its amber book icon: no filled amber arc, bloom or fake achievement illumination. Its horizontal progress bar is also empty and labeled `0 / 1,250` and `0%`.
- The **PTfS — Programming Techniques for Supercomputers** index card demonstrates about **25% completion** in the design reference: `32 / 126 topics` (~25%, rounded). Its circular ring must show **exactly the same proportional quarter-arc completion** as the horizontal track, with the rest of the circumference remaining dark/inactive. Do not render a full glowing ring with a separate `25%` text; the shape must encode progress. In real UI, compute the ring and track from actual completed/total topic counts, rather than hardcoding 32.
- For all progress rings: start at a consistent top origin, draw real proportional arc, use consistent compact stroke weights and a muted remaining track. **At zero**: only the inactive narrow outline. **At intermediate progress**: colored completed arc, no fake halo on the uncompleted portion. At completed milestones, a restrained highlight/one-shot illumination can be used per motion preference. Detailed achievement rings may have subtle sparkles; overview list rings should be static.
- Preserve **Current, Best, Gap** metrics (including `N/A` when undefined), optional hybrid streak + numeric progress, compact chart/progress rows, and project/index summary elements. Reopening a completed Index topic reduces its current count while retaining historical evidence. Don't infer that category/icon glow means a streak is nonzero; numeric fields are authoritative.
- Resizing/reordering remain supported in **Edit Layout mode**, with drag handles, snapping, minimum readable card widths/heights, persistence per device, and live content reflow. This must not leak into the normal-view screenshot.

## Elevation & Depth

**Critical principle: shadows establish general card depth; do not use illuminated card edges.** Achievement rings and specific highlighted controls are narrow, explicit exceptions.

- The main canvas is non-reflective matte graphite. Cards should look softly *placed above* the canvas through dark drop shadows.
- Main card suggested shadow for implementation: `0 6px 14px rgba(0,0,0,0.34), 0 2px 5px rgba(0,0,0,0.22)`; adapt to native elevation/shadows as needed.
- More elevated floating popups may use `0 10px 24px rgba(0,0,0,0.48)`.
- Sub-panels/inset tracks may use restrained **dark internal shadow**, without light on the top or left edges.
- Avoid outlines. If needed for separation, a neutral low-contrast divider/border no brighter than `#292B2F` and reduced opacity may be used. It must not read as rim-lighting.
- No bright top-edge reflections, no left-edge light streaks, no specular sheen, no luminous corner gradients, no colored ambient shadows and **no glowing card border**. A restrained halo around certain **achievement icon rings** and the Home shortcut's amber action outline are explicitly allowed; neither is a default general-card effect.
- **Chart bars:** flat/solid amber fills with an extremely soft, dark, short contact shadow similar in character to card elevation. NO orange bloom below bars, NO neon glow, NO floating pool-of-light at the bar baseline.
- **Analytics rings and progress bars:** opaque solid colors without luminous halos. **Achievement rings:** use subtle earned/active highlight *only when state justifies it*; **0% progress is a narrow unlit neutral ring**. Soft antialiasing and rounded caps are fine.
- Texture, if any, is almost imperceptible fine matte grain. It must never reduce legibility or look noisy.

## Shapes

- Card corner radius ~16dp; outer group containers 16–20dp; small buttons 9–12dp; segmented controls 9–12dp; tiny chart bars softly rounded.
- Controls have physical, restrained depth. Analytics period tabs differentiate through the same slightly lighter matte raised fill and crisp text in all modes, **not** unique brightly illuminated Month/Year borders. The small Achievements category underline and Home action outline are separate component patterns.
- Metric rings use geometric circles with consistent stroke widths and small clean breaks for segments. Achievement progress uses thinner proportional arcs on compact cards and truly inactive rings at 0%.
- Rows align to consistent baselines, with unobtrusive dividers. Avoid arbitrary icons unless they clarify a metric.

## Components

### Header and primary navigation

- Small status dot is teal/green; text “Synced · 100%” in muted light gray. Week chip sits at top right with minimal overflow glyph.
- Large “Analytics” title and **period-correct date navigator sit in the same header band**, regardless of Day/Week/Month/Year.
- Day, Week, Month, Year segmented control: the same restrained matte raised selected state when switching periods. Do not use a new color or button style for Month or Year.
- Bottom navigation is dark and persistent, with an understated separator and amber active Analytics icon/label.

### Summary KPI tiles

Compact four-item row: **FOCUS** `22h 41m` `↑18%` (amber), **SESSIONS** `18` `↑6%` (teal), **DONE RATE** `78%` `↑8%` (teal), **VARIANCE** `−8m` `avg delta` (red). These are demonstration values from the reference, not hardcoded application state. Do not make all tile widths or heights larger merely to fit redundant decoration.

### Plan Execution Score — full-width compact card

- Heading “Plan Execution Score”, small subheading about how well the plan was followed; small success badge e.g. “Great day!” on right (context-specific, not hardcoded).
- Left: prominent but compact score ring with **86 / 100** and **↑+6** change in the center. Use amber score arc, recessed neutral remainder, **no bright halo around the arc**.
- Right: three stacked concise rows, each with label + detail + bar: **Plan Completion** `5/6` `83%`, **Start Punctuality** `5/6` `83%`, **Time Efficiency** `1h 12m saved` `92%` (cyan). These are separate metrics; do not equate the overall score to the simple average unless specified by actual app scoring.
- Keep rows legible at phone width and the entire card shorter than a hero panel. Data/details may open from row taps without expanding the whole layout.
- Use a score derived from actual execution measurements in a real build; demo screenshots may use the listed reference values.

### Weekly Focus Distribution — full-width compact chart

- Exactly seven **amber** vertical bars, Mon through Sun, representing actual daily focus durations.
- Minimal Y axis tick labels. A thin dashed **cyan** weekly benchmark/average line when applicable. Keep bars solid and modestly narrow with close, comfortable spacing.
- Flat chart background. NO bright foot shadows / glow beneath bars. A subtle dark contact shadow is acceptable.
- Tap a daily bar to show an overlay with date, duration and relevant summary; overlay must not resize the chart/card.

### Execution — compact half-width card

- Heading “Execution” and score chip such as `84/100`.
- Three concise horizontal measures: Blocks `39/50 (78%)` in amber, Punctual `5/7 (71%)` in amber, Efficiency `22.7h (88%)` in cyan.
- Labels and figures remain on one row when possible. Dense thin progress tracks with rounded ends; no glowing track or borders.
- Height determined by its own content. Do not force it to match Time Split's height.

### Time Split — **ring ONLY** compact half-width card

This specific card was intentionally redesigned from a horizontal split bar + permanently visible side legend. **Never revert to the old layout.**

- Title “Time Split” at upper left; tiny `29h 40m` tracked total at upper right where it fits.
- Underneath: **one centrally positioned donut ring**, with proportional **Focus amber**, **Breaks cyan**, **Idle neutral gray** segments. The ring occupies the card; NO legend, NO list, NO permanent detail text beside it.
- Center display: **`76%` + `Focus`** as a concise summary of the selected period's focus share. Avoid labeling this as “Efficiency” unless a separately defined efficiency measure is actually being calculated.
- Keep the ring legible at approximately half the phone content width, without making the overall row oversized.
- **On tap of a ring segment:** display a small anchored floating popup/tooltip OVER existing cards/UI (high z-index), showing the tapped category, duration and share. Reference data: Focus `22h 41m` ~`76%`; Breaks `6h 12m` ~`21%`; Idle `0h 47m` ~`3%` (total 29h 40m). Popup must NOT take permanent space in card, expand card height, shift other cards, or appear as a persistent sidebar.
- Popup dismisses on outside tap, another segment tap, or back/escape; positions adapt to viewport edges. Provide an accessible click target/keyboard equivalent and screen-reader label for each segment.
- Use real time splits and calculate percentages consistently; if the user has no data, show a meaningful empty state rather than a fabricated ring.

### Top Projects & Sessions — full-width

- Header “Top Projects & Sessions” and small active item count.
- Each row: tiny categorical dot, truncated project/session name, tracked duration, **compact segmented progress bar at the far right**, plus the small chevron/drilldown indicator in the updated reference when space permits. Rows remain compact.
- Progress-bar semantics: **amber = previously completed progress**, **cyan = additional completion during the selected Day/Week/Month/Year range**, **dark gray remainder = incomplete**. Example: total completion 78% with 32 *percentage points* during current week -> amber 46%, cyan 32%, unfilled 22%. These percentages are relative to the **whole project target**, not the completed portion. Specifically in Day, the cyan end segment means **done today**. This is not a second unrelated target bar.
- Do NOT use weekly share-of-time percentages (the old screenshot's 27% / 22% / etc.) as if they were project completion. If project total targets do not exist, show an explicit unavailable/provisional progress state rather than invent completion.
- Tap a bar to show an anchored overlay with overall completion, earlier contribution, selected-period contribution, remaining, and tracked time; again, **overlay never changes card height**.
- Maintain very tight row spacing, legible names, subtle dividing rules, and no fixed height to match other cards.

### Focus by Hour — immediately above Executive Telemetry Insights

- Card title “Focus by Hour”; small total focused duration on the opposite side where useful.
- **One and only one data series: focus duration per hour.** Use simple solid amber vertical bars over 24 hourly bins (00 through 23); show sparse, readable hour-axis labels rather than cluttering the x axis. For **Day**, vertical ticks are **minutes per hour**; for multiday aggregates, use correct duration units and scale. Bars must be consistent with the period total if shown.
- No cyan break bars, no lines, no line+bar overlay, no gradient glow, no secondary legend, no fancy chart decoration.
- Dark faint horizontal gridlines are acceptable. Bars should appear visually grounded by the same subtle dark shadow treatment used on other cards.
- Tap a bar for a floating overlay with the hour and focus duration; no reserved popup space.
- Full-width mobile card, moderately short, and **placed directly before Executive Telemetry Insights**.

### Executive Telemetry Insights — **last card, original detailed version**

- Keep the complete original layout from the user's prior Stitch screenshot. **Do NOT collapse it to a brief text-only summary or replace it with a different “GPT” card**.
- Header with small amber dot, “Executive Telemetry Insights”; upper-right muted teal “High Momentum” indicator.
- Compact explanatory paragraph with highlighted performance figures such as +18%, execution score, and productive-hours window.
- Below it, two small dark recessed sub-panels **side by side**: “KEY STRENGTHS” (teal text) and “FRICTION POINTS” (amber text), each with concise bullet observations.
- Use natural content-based height. This is the **bottommost Analytics content card** above the persistent nav bar in the currently approved Week design.
- Text and metrics in this panel must derive from available performance records in a real build; reference wording is sample copy.

### Optional analytics modules — NOT automatically added to the core weekly composition

- **Punctuality Trend:** separate, available full-width card with Mon–Sun positive/negative start-time delta bars and meaningful zero baseline; positive/early teal-cyan and negative/late muted red; units minutes and concise per-day tooltips. Display only when explicitly enabled/placed in this page.
- **GPT Performance Report:** full-width/vertical module with scores such as Plan Quality, Time Estimation, Task Distribution, Adaptation. This is a different card from Executive Telemetry Insights. Do not silently replace one with the other.
- **Day, Month and Year are now specified above**, not unspecified optional layouts. An **All** period or expanded day timeline, if already present in app navigation, keeps its previously approved behavior, dimensions and drilldown instead of being fabricated from this design file. Do not copy the exact Week card set onto Day.

### Cross-app application

Apply the same color and surface tokens to **Home/Overview** and **Achievements** as explicitly detailed above, and to Focus/Timer, Stopwatch, achievement detail, INDEX/Index Tracker, GPT reports, Settings, Session Builder/Card Builder, dialogs, menus, and desktop Control Center as they are designed. Keep their established functional structure; a design system is not a mandate to invent or delete controls. Phone executes timers; tablet read-only viewers and desktop dashboards may adapt responsive layout without changing underlying data semantics.

### Accessibility and behavior

- Maintain sufficient contrast for numbers, descriptions, and small metadata against charcoal. Accent-only status must also have a label/shape.
- Interactive ring segments/progress charts need larger touch hit targets than their visible stroke and accessible semantic names.
- Popup and menus do not reserve layout height. Support tapping outside, back/escape, and scrolling without clipping.
- Respect device reduced-motion preferences (FULL / REDUCED / OFF). Small 150–250ms transitions are fine. One-shot achievement completion illumination and subtle detail-ring sparkles may be enabled in FULL, reduced or suppressed otherwise. No endless sweeping general-card glow. Hierarchical Analytics Year→Month→Week→Day morphs belong in the expanded drilldown, **not on the Home timeline**.
- Responsive text may wrap or ellipsize; never force overflow off the right edge of an S24 Ultra viewport.
- The actual app uses real stored data; the dated values, project names and test completion counts in screenshots are **illustrative design fixtures**, not live stats or a promise of future values. Check arithmetic coherence across progress fraction, percentage, ring arc and bar fill.

## Do's and Don'ts

**DO:**
- Preserve the approved matte surface and readable compact mobile density.
- Use shadow-cast elevation under cards and subdued inset depth for controls.
- Allow different cards to have their own independent natural heights.
- Keep amber for focus/pre-period project progress, cyan for secondary/selected-period project progress, teal for improvement/early starts, red for lateness, gray for inactive.
- Preserve the **Home two thin timeline tracks**, anchored floating popup, and no hero illustration.
- Keep the Week/Month/Year comparison views visually identical except for period selection and their single period-specific distribution/calendar/monthly-bars card.
- Distinguish **unlit zero progress** from **earned progress** in achievement rings.
- Preserve each chart's intended semantics and interactivity.
- Place Focus by Hour **above** Executive Telemetry Insights; keep telemetry content with its two inset sections.
- Check the design at actual phone viewport width, not only zoomed mockup width.

**DON'T:**
- Add glowing **card** edges, light streaks, shiny/glassy panels, bright gradient borders, or luminous chart shadows. Subtle achievement-ring highlights are a purposeful exception, not permission for general glow.
- Stretch cards so they match heights, introduce large empty gutters, or overgrow the page vertically.
- Replace the Time Split ring with a horizontal bar or permanent right-side legend.
- Add break bars or a line series to the Focus by Hour chart.
- Change the meaning of completion vs. current-period contribution in project progress bars.
- Hardcode reference example values into functional UI.
- Shorten Executive Telemetry Insights into a single line or move it above Focus by Hour.
- Redesign the entire project when only a specific card is being refined.
- Collapse Home's two parallel Planned/Actual lines to one line, add visible PLAN/ACTUAL captions, or turn thin tracks into tall boxes.
- Leave any edit-mode controls visible on the approved normal Achievements page.
- Show a 0%-progress German badge as a glowing full amber ring, or show PTfS at 25% using a full-circle progress ring.
- Copy Week numbers and week-range navigation into Month/Year fixtures; the Year graph Y axis must be **hours**.

**New Stitch / design implementation starting instruction:** Treat this document as the shared design contract for **Home, Achievements and Analytics Day/Week/Month/Year**. Match each page's **latest approved screenshot**, reuse common tokens and the Week-header alignment across all period pages, and implement exact period-specific card substitutions without modifying unrelated cards. On Home keep two minimum-height planned/actual timeline tracks with the task popup; on Achievements keep the normal-mode grid and proportional progress-ring examples. Use real data in functional builds; fixture numbers only communicate the visual state. Prefer accuracy of compact layouts and data semantics over decorative symmetry.

## Revision notes — 11 Oct 2026

Updated this earlier Week-centric document to reflect the subsequently approved images. Principal additions: Day header alignment and detailed Time Estimation Accuracy card; Month Calendar; Year 12-month hours chart; latest amber/cyan project-completion bars; Home without planet image and with two ultra-thin aligned timeline tracks plus floating task popup; normal-mode Achievements and its 0% German / ~25% PTfS ring states; reconciliation of achievement-only glow with the matte graphite system. Kept the original Week card order and its detailed Executive Telemetry Insights layout.
