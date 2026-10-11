# FOCUSDECK — FINAL UI IMPLEMENTATION MASTER SPECIFICATION
## Onyx Matte & Shadow-Stacked (OMSS) Design System
### Codex Implementation Instructions — Android / Jetpack Compose

## 0. OBJECTIVE

Implement the finalized FocusDeck mobile UI from the attached reference screenshots with the highest practical visual fidelity.

The visual design is **LOCKED**. You are NOT being asked to redesign, reinterpret, modernize, simplify, or improve it.

Your job is to reproduce the supplied interfaces using native Android UI components and reusable design-system primitives.

The application already contains substantial business logic, persistence, navigation, device synchronization, achievement tracking, session execution, and analytics functionality. Preserve all of that.

**Your primary responsibility is visual implementation, not feature development.**

### Required pages

1. Home / Overview
2. Home with Daily Timeline popup
3. Achievements
4. Analytics — Day
5. Analytics — Week
6. Analytics — Month
7. Analytics — Year

Use the most recently finalized image for each page, not earlier design iterations.

**Priority order in case of conflicts:**

1. Explicit behavioral and implementation constraints in this prompt
2. Attached final screenshots for appearance, composition, proportions, and spatial relationships
3. Existing application functionality and real stored data
4. Numerical design tokens below, treated as initial calibration targets

Do not treat illustrated values, dates, completion percentages, or chart datasets as real production data.

---

# PART I — THE CORE DESIGN LANGUAGE

## 1. DESIGN IDENTITY

The complete visual system is named:

**OMSS — Onyx Matte & Shadow-Stacked**

It combines:

- Very dark, non-reflective charcoal materials
- Physically separated overlapping visual layers
- Downward-right directional shadows
- Vibrant amber visual emphasis
- Cyan analytical data accents
- Minimal green and red status colors
- Compact, information-dense compositions
- Elevated circular gauges
- Floating progress tracks
- Precisely aligned typography
- Controlled contrast and restrained depth

The interface must look like matte, dark physical components stacked vertically above a near-black surface.

It must NOT look like a conventional flat Material Design dashboard, nor like glassmorphism, nor like reflective 3D machinery.

### Strict visual prohibitions

Do not introduce:

- Glassmorphism
- Frosted translucent cards
- Metallic reflections
- Chrome highlights
- Glossy or wet surfaces
- White edge glints
- Beveled borders
- Skeuomorphic grooves
- Raised metallic frames
- Bright outlines around every card
- Ambient glow surrounding complete panels
- Colorful decorative background gradients
- Background meshes, dots, grids, or geometric patterns
- Heavy neumorphic inner shadows
- Large radial bloom halos
- Arbitrary gradients or lens-flare effects

The overall aesthetic must feel **dry, restrained, dark, solid, premium, and physically layered**.

The screenshots may contain subtle tonal variation introduced by rendering. Reproduce this without making panels visibly glossy.

---

# PART II — EXACT COLOR SYSTEM

## 2. COLOR TOKENS

Implement a centralized color palette.

The following values are intended as close implementation targets. Calibrate them against the supplied images during visual comparison.

### 2.1 Baseline backgrounds

| Token | Hex | Purpose |
|---|---|---|
| `canvasBase` | `#090A0E` | Primary application canvas |
| `canvasDeep` | `#06070A` | Darkest background regions |
| `canvasAlternate` | `#0B0D11` | Optional slight tonal variation |
| `canvasShadow` | `#000000` | Shadow color |

The background must be almost black, with an extremely subtle charcoal undertone.

**Background texture:**

- No visible geometric pattern.
- No prominent noise.
- A barely perceptible fine-grain matte texture is acceptable if it matches the supplied screenshots.
- If texture would introduce visible grain or GPU overhead, use a solid color instead.
- A tiny edge-darkening variation is acceptable.
- Do not add warm amber or cyan ambient lighting to the canvas.

### 2.2 Elevated card surfaces

| Token | Hex | Purpose |
|---|---|---|
| `surfaceCard` | `#17191E` | Primary card material |
| `surfaceCardDark` | `#14161A` | Darker card variation |
| `surfaceCardLight` | `#1B1E23` | Slightly lighter surface |
| `surfaceNested` | `#1C1F24` | Small cards stacked inside larger cards |
| `surfaceHub` | `#111318` | Elevated circular hubs |
| `surfaceTrack` | `#30343C` | Inactive progress tracks |
| `surfaceSeparator` | `#292D35` | Very subtle internal separators |

All card backgrounds must remain opaque and matte.

The surface difference between the canvas and a card should be visible even with shadows disabled.

Do not use translucency as the main mechanism for producing surface variation.

### 2.3 Accent colors

| Token | Hex | Purpose |
|---|---|---|
| `amberPrimary` | `#FFA726` | Primary achievement and control color |
| `amberBright` | `#FFB300` | Highlighted bars, active rings |
| `amberDeep` | `#DF8D20` | Less emphasized amber segments |
| `cyanPrimary` | `#00D5E8` | Analytics, efficiency, average reference lines |
| `cyanBright` | `#00E5FF` | Bright cyan indicators |
| `greenPositive` | `#00DFA6` | Positive trends and connected states |
| `redNegative` | `#FF5252` | Negative changes and errors |

Amber is the primary brand accent.

Cyan is a secondary data accent.

Green and red indicate data states and should not be used merely for decoration.

Avoid introducing other accent colors except existing category-identification colors required by the screenshots.

### 2.4 Text colors

| Token | Hex | Purpose |
|---|---|---|
| `textPrimary` | `#F2F3F5` | Headings, important metrics |
| `textSecondary` | `#A4ADBB` | Subtitles and labels |
| `textMuted` | `#858D9D` | Secondary metadata |
| `textFaint` | `#6F7786` | Tertiary information |
| `textAmber` | `#FFA726` | Selected navigation, emphasized values |
| `textCyan` | `#00D5E8` | Analytical indicators |
| `textPositive` | `#00DFA6` | Improvements |
| `textNegative` | `#FF5252` | Regressions |

Typography must remain crisp and readable.

Never make all text pure white. The screenshots establish a clear text hierarchy through luminance.

---

# PART III — THE PHYSICAL ELEVATION SYSTEM

## 3. COORDINATE SYSTEM

Use the usual UI coordinate system:

- Positive X = right
- Positive Y = down

Every elevated object's drop shadow must primarily fall toward positive X and positive Y.

That means:

**SHADOW DIRECTION = BOTTOM-RIGHT**

Do not use centered, equally distributed blurred shadows as the main depth effect.

Do not apply symmetrical shadows to all four sides of a component.

Shadow values below are approximate Compose dp-based targets, not screenshot pixel measurements.

## 4. THE FIVE PRACTICAL RENDERING LEVELS

The design concept uses a three-tier visual stack, but its implementation should distinguish five practical elevation levels.

### LEVEL 0 — BASELINE CANVAS

Components:

- Root application background
- Unselected navigation labels
- Flat date labels
- General empty space
- Static headings positioned directly on the canvas

Properties:

- Elevation: 0
- Shadow: none
- No decorative container
- No border
- No background pattern

This level establishes the darkest visual surface.

### LEVEL 1 — PRIMARY CARD PLATES

Components:

- Achievements overview outer panel
- Individual achievement cards
- Plan Execution Score card
- Focus by Hour card
- Weekly Focus Distribution card
- Productivity Calendar card
- Productivity by Month card
- Execution card
- Time Split card
- Top Projects & Sessions card
- Executive Telemetry Insights card
- Home Daily Timeline card
- Last Session card
- Upcoming Session card
- Main statistics cards

These objects are dark, matte, solid plates physically elevated above the canvas.

**Recommended starting shadow:**

- Offset X: +4 dp
- Offset Y: +8 dp
- Blur radius: 16–20 dp
- Shadow color: black
- Opacity: approximately 45–65%

Larger cards may use up to +6 dp X and +10 dp Y, but do not exaggerate.

The shadow must create visible separation without creating a bright border or floating glass appearance.

**Surface shape:**

- Corner radius: approximately 10–14 dp
- Opaque charcoal fill
- No prominent outline
- No bevel
- No centered glow

Card thickness is suggested entirely by its occlusion and shadow.

### LEVEL 2 — NESTED MINI-CARDS

Components:

- Four small overview statistics cards within the Achievements overview panel
- Executive Telemetry Key Strengths card
- Executive Telemetry Friction Points card
- Any other explicitly nested card in a screenshot

Each mini-card sits ABOVE its parent card.

Do not make the mini-card look carved into the parent.

**Recommended shadow:**

- Offset X: +3 dp
- Offset Y: +5 dp
- Blur: 8–12 dp
- Opacity: approximately 40–55%
- Radius: approximately 8–10 dp

Material:

- `surfaceNested`
- Opaque
- Matte
- Slightly distinguishable from its parent

The most important visual requirement is that the nested rectangles resemble separate small physical objects resting on the larger panel.

### LEVEL 3 — ELEVATED COMPONENTS

Components:

- Circular progress rings
- Inner circular hubs
- Icon badges
- Horizontal progress tracks
- Filled progress bars
- Chart bars
- Floating status pills
- Primary buttons
- Timeline segments
- Small status indicators

These components must visibly rise above their supporting card.

**Recommended shadow:**

- Offset X: +2 to +3 dp
- Offset Y: +3 to +5 dp
- Blur: 5–9 dp
- Opacity: approximately 40–60%

For an icon badge, the dark circular base and icon should be composed as distinct surfaces where the reference image demonstrates that structure.

The filled bars should appear mounted over a dark track, not painted directly onto a flat card.

### LEVEL 4 — MICRO-ELEVATED CONTENT

Components:

- Prominent numerical values
- Trend arrows
- Colored status values
- Selected glyphs
- Small icons
- Important labels on elevated surfaces

The micro-elevation must be extremely subtle.

**Recommended shadow:**

- Offset X: +1 dp
- Offset Y: +1–2 dp
- Blur: 1–3 dp
- Opacity: 20–35%

This creates a tiny amount of physical contrast.

Do not apply strong shadowing to ordinary body text.

Do not put a large blurry shadow under every word.

---

# PART IV — COMPONENT CONSTRUCTION

## 5. PRIMARY CARDS

Every ordinary content card must follow the same component hierarchy:

1. Parent canvas
2. Card's bottom-right shadow
3. Opaque matte card body
4. Interior content and spacing
5. Elevated child components

Use a reusable `OmssCard` or equivalent composable.

### Appearance

- Radius: approximately 12 dp
- No border by default
- No glossy overlay
- No thick outline
- No white top edge
- Moderate padding
- Soft drop shadow toward bottom-right

### Internal spacing

Typical starting points:

- Horizontal padding: 12–16 dp
- Vertical padding: 12–16 dp
- Card-to-card gaps: 6–10 dp
- Header-to-content gaps: 6–10 dp

These are NOT instructions to reposition the UI. Use the reference screenshots to calibrate the actual proportions.

Cards should remain compact and information-dense.

Do not enlarge every component because it is elevated.

## 6. MINI-CARDS

Build a reusable `OmssMiniCard`.

Mini-cards use the same material language as primary cards but:

- Slightly smaller corner radius
- Shorter directional shadow
- Compact content padding
- No ornamental border

In the Achievements overview, four mini-cards must appear in one horizontal row at the reference viewport width.

Each mini-card contains:

- Amber icon
- Prominent value
- Muted description

Center-align these elements vertically within the mini-card.

All four must have matched dimensions and consistent spacing.

**Do not use vertical separator lines between them.**

Their separate physical surfaces provide the visual division.

## 7. CIRCULAR GAUGES

This is one of the most important components of the design system.

Do NOT draw a normal flat donut chart and stop there.

The ring must behave visually like an elevated layered assembly.

### Circular elevation stack

**Layer A — Dark circular foundation**

- Dark matte circular backing
- Slightly larger than the inner hub
- Rests on the parent card
- Small bottom-right shadow

**Layer B — Colored progress ring**

- A single colored circular stroke
- Full circle for complete states
- Partial arc for incomplete states
- Amber or cyan based on the data type
- Consistent stroke width
- Rounded ends on partial arcs where shown
- Directional shadow beneath the colored stroke

**Layer C — Central raised hub**

- Solid dark circle
- Centered above the ring
- Distinct shadow falling onto the ring underneath
- No concave inner groove
- No glass reflection

**Layer D — Icon or metric**

- Centered label, percentage, score, or icon
- Appropriate text or icon color
- Extremely subtle micro-shadow

### Recommended geometry

- Small achievement gauges: approximately 40–50 dp diameter
- Large analytical gauges: approximately 90–110 dp diameter
- Ring thickness: roughly 7–10% of outside diameter
- Inner hub occupies approximately 65–75% of total diameter

Match the final screenshots before committing exact dimensions.

### Ring appearance

- Solid, saturated amber/cyan
- No metallic gradient
- No reflective streak
- No artificial light source
- No indented or sunken ring track

A very restrained, localized softness around brightly colored rings may be used only where visible in the references. It must not become a large omnidirectional glow halo.

### Important data rule

The circular progress arc must correspond to its real value.

A progress card at 0% must not display a misleading nonzero arc merely because a mockup shows a decorative arc.

Streak achievement rings must follow the application's existing streak/progress rules.

Do not change metric definitions to match illustrative screenshot values.

## 8. HORIZONTAL PROGRESS TRACKS

Horizontal progress indicators should have a tactile raised appearance.

Construction:

1. Dark supporting track
2. Slight supporting-track shadow
3. Colored fill mounted above the track
4. Small directional shadow beneath the fill

Typical geometry:

- Track height: approximately 6–8 dp
- Rounded track corners
- Rounded fill ends where shown
- Amber for primary completion
- Cyan for efficiency or secondary progress
- Dark charcoal remaining segment

Avoid:

- White outlines
- Thick inner shadows
- Glossy highlights
- Strong multicolor gradients
- Overly tall bars

Fill lengths must represent real percentages.

## 9. ICONS AND SYMBOLS

Icons are minimal, geometric, and generally line-based.

Common symbols include:

- Target
- Trophy
- Stopwatch
- Clock
- Lightning bolt
- Bar chart
- Book
- Dumbbell
- Guitar
- Heart
- Flame
- Gear
- Calendar
- Chevron
- Mountain logo

### Styling

- Amber stroke or solid fill for primary icons
- Cyan for designated data indicators
- Muted slate for unselected controls
- Consistent stroke weight
- Rounded line caps where appropriate
- Crisp vector rendering

Do not replace established app icons with arbitrary alternatives.

Keep the existing main FocusDeck mountain logo intact.

### Icon elevation

If an icon is inside a circular badge:

- Render the badge as a raised dark hub
- Render the icon centered within it
- Shadow the hub bottom-right

If an icon appears directly over a card, use a much smaller directional shadow.

Do not create a separate badge around every icon unless the screenshot shows one.

## 10. BUTTONS AND TAGS

Two distinct button styles exist.

### Type A — Raised tactile button

Examples:

- Start Session button
- Executive status pill
- High Momentum pill
- Great Day status pill

Properties:

- Solid or dark-amber fill as appropriate
- Rounded corners
- Short bottom-right shadow
- Clear contrast
- No glossy reflections

The bright amber Start button is allowed to stand out from the surrounding matte panels.

### Type B — Flat zero-elevation control

Examples:

- Day / Week / Month / Year navigation tabs
- Certain top-header date labels
- Selected simple text controls

Properties:

- No supporting card plate
- No tactile raised effect
- No background shadow
- Text and underline communicate selection

**Do not wrap every clickable element in a visible card.**

This distinction is critical.

---

# PART V — TYPOGRAPHY

## 11. TYPOGRAPHY SYSTEM

Use the application's existing native sans-serif font family, or the closest available match to the references.

Do not introduce a decorative typeface.

Suggested typography hierarchy:

| Element | Approximate size |
|---|---|
| Major page heading | 28–34 sp |
| Home primary statement | 27–34 sp |
| Largest focus total | 36–44 sp |
| Card title | 16–20 sp |
| Main card metric | 20–28 sp |
| Medium metric | 16–20 sp |
| Standard label | 12–14 sp |
| Micro-label / chart tick | 10–12 sp |

The reference screenshots take precedence over these suggestions.

### Type weights

- Page titles: bold
- Major numerical values: bold or semibold
- Card titles: semibold
- Standard information: regular
- Supporting metadata: regular
- Navigation labels: regular except active selection

### Alignment

- Keep metric values and labels optically aligned.
- Keep headings close to their content.
- Use consistent number formatting.
- Use tabular numerals for numerical statistics and aligned chart data.
- Avoid unnecessary wrapping.
- Allow genuine long titles to use multiple lines where required.
- Do not sacrifice content readability to force a fixed single line.

### Shadow behavior

Most ordinary text should not have obvious drop shadows.

Only selected high-priority labels and indicators should receive very small micro-shadows.

The text must feel raised slightly, not embossed.

---

# PART VI — CHART RENDERING

## 12. CHART VISUAL LANGUAGE

All analytics charts must use a common native chart rendering system.

The visual rules:

- Dark matte card background
- Subtle neutral grid lines where shown
- Amber bars
- Cyan average reference lines
- High contrast but restrained chart labels
- Compact labels and axes
- No bright plot-area container
- No thick outer frame
- No unnecessary legends
- No oversized chart padding

Bar charts must appear integrated into the card.

### Bar styling

- Consistent width within each chart
- Even horizontal spacing
- Compact spacing between bars
- Small corner radii, especially at bar tops
- Solid amber color
- Avoid glossy gradient fills
- Small down-right shadow creating foreground elevation

## 13. CYAN DOTTED AVERAGE REFERENCE LINE

**This rule is mandatory on the relevant focus bar charts.**

The average reference line must:

1. Be horizontal.
2. Use saturated cyan.
3. Use a fine dotted/dashed pattern.
4. Extend across the chart plotting area.
5. Be drawn BEHIND the bars.
6. Remain visible in spaces between bars.
7. Intersect the bars visually only as a background reference.
8. Have a compact label aligned to its far-right end.

### Z-order

Render in this order:

1. Chart background
2. Grid lines
3. Average reference line
4. Bar shadows
5. Bars
6. Axis labels
7. Average value label, positioned above the line

**DO NOT draw the dotted line in front of the bars.**

The bars must occlude the dotted line where they overlap it.

### Label format

For hour-based values:

`0h 30m`

`2h 15m`

`4h 30m`

Do not add unnecessary words such as "average" or "avg" to the right-edge label.

Keep the label small, cyan, and right-aligned just above the line.

The label must not obscure the final bar or clip outside the card.

Use the application's correct existing average calculation. Do not silently change the calculation to achieve the illustrated label value.

## 14. FOCUS BY HOUR CHART

Follow the screenshots.

- Horizontal axis covers 00:00–24:00.
- Twenty-four hourly buckets.
- Compact amber bars.
- Subtle vertical reference lines.
- Thin horizontal grid lines.
- Cyan dotted average line behind the bars.
- Average value on the right, just above the line.
- Total duration near the card title on the right.

Preserve the chart's real data semantics.

Do not produce fake or interpolated buckets to make the chart resemble the sample image.

## 15. WEEKLY FOCUS DISTRIBUTION

Display seven bars:

Mon, Tue, Wed, Thu, Fri, Sat, Sun.

Properties:

- Uniform bar width
- Even day spacing
- Amber bars
- Cyan horizontal average line behind the bars
- Average value at the right edge above the line
- Current or selected day highlighted only as shown
- Compact Y-axis duration labels

Match the screenshot dimensions and card proportions.

## 16. PRODUCTIVITY CALENDAR

The calendar is a compact activity-intensity grid.

Preserve:

- Four or five visible weekly rows, depending on month layout
- Weekday headings
- Date/month control in the header
- Small square activity cells
- Rounded corners
- Dark inactive cells
- Different amber intensities
- Cyan for the designated lower-focus state
- Compact legend in the header

The activity grid must not become a heatmap with huge spacing or oversized blocks.

If applying elevation to individual cells, use extremely small directional shadows.

Do not create strong shadows that destroy the readability of the calendar grid.

## 17. PRODUCTIVITY BY MONTH

Year analytics uses a twelve-bar chart.

Months:

Jan through Dec.

Appearance:

- Twelve amber vertical bars
- Uniform width
- Even horizontal spacing
- Thin neutral horizontal grid lines
- Duration values on the Y-axis
- Total duration on the upper-right
- No additional decorative overlay

Do not replace this with a line chart or calendar.

---

# PART VII — PAGE-SPECIFIC REQUIREMENTS

## 18. HOME SCREEN

Use the finalized Home screenshot as the structural authority.

Preserve the following sections and their order:

1. Header with FocusDeck logo
2. Phone connection status
3. Date
4. Ready For Your Next Session heading
5. Today's Focus metric
6. Completed sessions / blocks statistics
7. This Week mini-card
8. All-Time Focus mini-card
9. Analytics shortcut
10. Daily Timeline
11. Last Session
12. Upcoming Session

### Home background

Use the same baseline canvas as Analytics and Achievements.

Do not introduce a special glossy background just for Home.

### This Week / All-Time Focus

Both cards use:

- Matching elevated matte card surfaces
- Identical corner radii
- Equal height
- Consistent internal alignment
- Bottom-right shadows

They should look like distinct plates resting directly on the canvas.

### Analytics shortcut

This is a special outlined navigation control.

Its thin amber outline is intentional and is an exception to the rule forbidding decorative borders on ordinary cards.

- Dark matte surface
- Amber border
- Amber chart icon
- Amber title
- Amber chevron
- Compact directional shadow

Do not turn it into a huge glowing pill or a bright gradient button.

### Daily Timeline

The Daily Timeline is a wide raised matte card.

It contains:

- Heading
- Clock icon
- Date
- Navigation chevron
- Planned timeline
- Actual timeline
- Time marker
- Compact time labels

The amber and cyan timeline tracks should appear slightly raised over the card.

The current-time marker is a small white circular indicator connected to a vertical line.

The marker must align with the correct time position.

Keep two horizontal tracks compact and aligned to the same 24-hour scale.

The tracks represent real session scheduling/execution data.

Preserve the existing popup behavior, including the in-place overlay and actual planned/actual details.

### Last Session

Preserve:

- Header
- View All control
- Elevated icon hub
- Session title
- Duration
- Completion status
- Date/time
- Chevron

Avoid oversized icon badges and excessive unused card space.

### Upcoming Session

Preserve:

- Header
- View Plan control
- Project icon hub
- Session title
- Scheduled time
- Amber Start button

The Start button should visibly sit above the card with its own short bottom-right shadow.

### Home immersive behavior

Do not introduce permanent navigation chrome into immersive mode.

If an attached screenshot contains a legacy bottom navigation bar, treat it as a layout reference only when navigation is visible outside immersive mode.

Preserve the application's actual navigation functionality.

Do not let navigation changes interfere with session execution.

---

## 19. ACHIEVEMENTS SCREEN

Use the finalized Achievements screenshot.

### Top header

- Large Achievements title
- Muted subtitle
- Horizontal category tabs
- Amber underline on active tab

The tab strip is a flat canvas-level component, not a row of raised cards.

### Overview panel

The outer overview panel is a Level-1 elevated card.

Inside it are four Level-2 mini-cards:

1. Active Streaks
2. Personal Bests
3. Daily Target
4. Progress Cards

Each contains a corresponding amber icon, large number, and muted label.

No vertical dividers between the mini-cards.

They must have equal sizes and maintain the same visual relationship as the screenshot.

### Achievement grid

Reproduce the screenshot's compact multi-column layout.

Card components:

- Dark matte plate
- Circular gauge/icon hub
- Title
- Category
- Optional progress value
- Optional horizontal progress track
- Current streak
- Best streak
- Gap

Preserve the correct distinctions between:

- STREAK_ONLY
- PROGRESS_ONLY
- STREAK_AND_PROGRESS
- INDEX achievement cards

Do not flatten all achievement types into one generic template.

### Circular icons

The achievement icon assembly uses:

- Dark circular foundation
- Amber or cyan progress arc
- Raised central dark hub
- Amber icon
- Directional shadows

Some rings are incomplete.

Some are fully complete.

All current values must come from stored data.

### Project and index cards

Maintain the larger format where shown.

Preserve:

- Full readable project names
- Current topic count
- Total topic count
- Completion percentage
- Progress track
- Streak metadata
- Category labeling

Do not truncate important long names merely to preserve a fixed height.

Let text wrap gracefully while keeping the grid visually consistent.

### Achievements interaction

Retain existing:

- Filtering
- Category navigation
- Card details
- History
- Long-press or card-management actions
- Resizing/edit-layout mode
- Index tracker functionality

The theme must apply consistently in normal and edit mode.

---

## 20. ANALYTICS — SHARED PAGE STRUCTURE

All four Analytics pages must be built from the same shared scaffold.

Do not develop four visually independent screens.

### Shared hierarchy

1. Connection/synchronization status
2. Analytics title
3. Flat top-right date/week label
4. Date navigation selector
5. Day / Week / Month / Year tabs
6. Four compact KPI cards
7. Plan Execution Score
8. Primary period-specific visualization
9. Execution and Time Split
10. Top Projects & Sessions
11. Lower period-specific card
12. Executive Telemetry Insights

### Date heading

The small top-right date or week indicator must be flat text at Level 0.

**It must not have a raised card background.**

The separate date-navigation control, containing left/right arrows and the currently selected date range, may retain its matte capsule surface as shown.

### Period navigation tabs

Day, Week, Month, Year must be flat text tabs.

No individual tab cards.

No rectangular selected-state background.

Active tab:

- Amber text
- Thin amber underline
- No elevation

Inactive tabs:

- Muted text
- No underline

The selected underline should closely match the width and position in the reference screenshots.

### KPI cards

Use four matching, compact Level-1 cards.

Each card has:

- Amber icon
- Muted metric label
- Primary numerical value
- Optional green/red trend

Avoid excessive internal whitespace.

### Plan Execution Score

Large elevated matte card.

The gauge and metric bars must occupy the same layout proportions as the reference.

Left:

- Large layered score ring
- Dark raised hub
- Score value
- Secondary trend

Right:

- Plan Completion
- Start Punctuality
- Time Efficiency

Each metric uses its corresponding icon, text, track, and percentage.

Status badge at the upper-right.

Do not redesign into vertically stacked tiles.

### Execution and Time Split

These remain separate side-by-side cards at the reference width.

Execution:

- Compact progress tracks
- Labels
- Value/percentage pairs

Time Split:

- Elevated circular gauge
- Center percentage
- Focus label
- Secondary duration label
- Navigation chevron

### Top Projects & Sessions

- Full-width elevated card
- Compact row layout
- Project/category indicator
- Project title
- Duration
- Percentage where applicable
- Horizontal amber/cyan progress indicator

Preserve the dual-color track segmentation in the provided references.

Do not collapse the rows into separate large cards.

---

## 21. ANALYTICS — DAY

Use the finalized Day screenshot.

### First chart

`Focus by Hour`

Display:

- Twenty-four hourly buckets
- Amber bars
- Cyan dotted average line behind bars
- Right-side average duration
- Total focus time

### Bottom specialized card

`Time Estimation Accuracy`

Preserve its three-part layout:

1. Accuracy
2. Duration deviation
3. Estimated versus actual

Keep the corresponding amber icons.

Red values indicate overruns or negative estimation deviations.

Cyan/green values indicate favorable results according to existing scoring rules.

This card must not be replaced with a second chart on the Day page.

---

## 22. ANALYTICS — WEEK

Use the finalized Week screenshot.

**The Week screen must reuse the Day screen's shared component dimensions and overall spacing.**

Do not resize the Plan Execution Score, KPI cards, Execution, Time Split, or Top Projects card merely because Week is selected.

Changes relative to Day:

- Select Week tab
- Use the appropriate selected weekly range
- Replace the first Focus by Hour chart with Weekly Focus Distribution
- Replace the Time Estimation Accuracy card with the weekly Focus by Hour chart

Keep all other card structures unchanged.

Weekly Focus Distribution:

- Seven amber bars
- Mon–Sun labels
- Cyan dotted average line behind bars
- Right-edge duration label

Weekly Focus by Hour:

- Twenty-four hourly buckets
- Amber bars
- Cyan dotted average line behind bars
- Total duration shown in the card header

**No additional cards or unsolicited layout changes.**

---

## 23. ANALYTICS — MONTH

The Month page reuses the Week page's structure.

Changes:

- Select Month tab
- Display the selected month
- Replace Weekly Focus Distribution with Productivity Calendar

Retain the lower Focus by Hour card.

Everything else should use the same shared visual structure and spacing.

Do not reflow the entire page unnecessarily.

---

## 24. ANALYTICS — YEAR

The Year page reuses the Month page's structure.

Changes:

- Select Year tab
- Display the selected year
- Replace Productivity Calendar with Productivity by Month

The chart contains twelve amber bars corresponding to the months.

Retain all remaining shared cards.

Do not introduce separate Year-page spacing, oversized chart panels, or a unique theme.

---

## 25. EXECUTIVE TELEMETRY INSIGHTS

This card must match the latest expanded reference, not the older compact single-paragraph version.

### Outer card

Level-1 surface:

- Full width
- Dark matte panel
- Rounded corners
- Bottom-right shadow

Header:

- Amber small circular indicator
- Executive Telemetry Insights title
- High Momentum status on the right

### Summary paragraph

A short, compact natural-language analytical summary.

Preserve:

- Inline amber highlighted values
- Neutral white supporting text
- Compact readable line spacing
- No oversized typography

The contents must come from real analytics/report data.

### Nested insight panels

Two side-by-side mini-cards:

**Key Strengths**

- Cyan section heading
- Short bullet-point insights
- Opaque dark nested surface
- Compact padding
- Subtle lower-right shadow

**Friction Points**

- Amber section heading
- Short bullet-point insights
- Matching surface dimensions
- Same shadow treatment

Do not use glossy green/amber decorative containers.

Do not add unnecessary borders.

Both must appear as physical mini-cards resting above the outer telemetry panel.

The card may grow vertically to accommodate actual insight text.

---

# PART VIII — SHADOW ENGINEERING

## 26. NATIVE JETPACK COMPOSE IMPLEMENTATION

Implement the design natively in Kotlin and Jetpack Compose.

Avoid embedding screenshot images as UI backgrounds.

Avoid WebViews for reproducing these screens.

Do not use pre-rendered screenshot fragments as controls.

Use reusable composables, Canvas drawing where appropriate, and vector assets.

### Reusable primitives

Create or refactor shared components equivalent to:

- `OmssSurface`
- `OmssCard`
- `OmssMiniCard`
- `OmssDirectionalShadow`
- `OmssCircularGauge`
- `OmssIconHub`
- `OmssProgressTrack`
- `OmssProgressBar`
- `OmssStatusPill`
- `OmssFlatTab`
- `OmssPrimaryButton`
- `OmssChartBar`
- `OmssAverageReferenceLine`
- `OmssTypography`
- `OmssColors`

The exact class/function names may be adapted to the repository's architecture.

Avoid duplicating shadows, colors, spacing, and ring geometry across individual pages.

### Directional shadow rule

Compose's ordinary elevation behavior may create diffuse shadows on multiple sides.

Do not rely blindly on default `Modifier.shadow()` if it produces a noticeably wrong shadow direction.

Use a custom reusable directional-shadow implementation when necessary.

The implementation must support:

- Configurable X offset
- Configurable Y offset
- Configurable blur radius
- Configurable opacity
- Rounded-rectangle and circular shapes
- Correct draw order
- Hardware-accelerated rendering where supported

Use an appropriate native Canvas/graphics pipeline rather than expensive software-generated shadow bitmaps.

### Clipping

Do not clip the shadow before it can extend outside its owning component.

Ensure:

- Parent cards can cast shadows onto the canvas.
- Nested cards can cast shadows over parent cards.
- Hubs can shadow their supporting rings.
- Progress fills can shadow tracks.
- Charts clip bar shapes to the appropriate plot area without unintentionally cutting off their intended small shadows.

### Avoid invalid elevation

A component's background must not appear semi-transparent merely because it has elevation.

Elevation affects its shadow, not its material opacity.

### Avoid uncontrolled glow

Never stack several large blurred shadow effects to simulate tactile depth.

Use one appropriately configured directional shadow for each physical elevation relationship.

Use only highly restrained localized emphasis where the reference itself visibly calls for it.

---

# PART IX — DIMENSIONS AND SPACING

## 27. DIMENSION CALIBRATION

The screenshots are visual authority.

Do not assume that screenshot pixels are Android dp units.

Account for:

- Real device density
- Viewport dimensions
- Display cutouts
- Status bar insets
- Text scaling
- Compose layout constraints
- Screen aspect ratio

### Calibration order

Match these dimensions in sequence:

1. Outer page margins
2. Header placement
3. Navigation alignment
4. Major card widths
5. Major card heights
6. Card-to-card spacing
7. Nested card geometry
8. Circular gauges
9. Chart plot areas
10. Typography
11. Shadows
12. Micro-spacing

Do not tune shadows before matching component positions.

### Responsive behavior

On the reference-sized device, reproduce the supplied composition.

On narrower or larger screens:

- Maintain readable content.
- Avoid overlap.
- Preserve card order.
- Adapt the layout only where required.
- Do not introduce arbitrary additional whitespace.
- Preserve scrolling.
- Preserve touch target usability.

Don't stretch icons or charts nonuniformly.

Existing user-configurable card resizing and layout persistence must continue to work.

---

# PART X — MOTION AND PERFORMANCE

## 28. ANIMATION PRINCIPLES

The screenshots define the resting visual state.

Animations must support that design without changing it.

Appropriate motion:

- Small press feedback
- Smooth ring progress interpolation
- Short bar-chart reveal
- Gentle card elevation transition
- Subtle page fade/slide
- Existing analytics drilldown animations

Avoid:

- Large bouncing transitions
- Extravagant glow effects
- Constant card pulsing
- Excessive full-screen recomposition
- Unnecessary particle effects
- Heavy real-time background blur

Respect the application's existing FULL / REDUCED / OFF motion settings.

For OFF, show correct final states without decorative motion.

### Performance

FocusDeck runs on high-refresh-rate Android devices.

Target smooth interaction on 120 Hz-capable hardware where practical.

Precompute expensive chart paths and reusable drawing resources.

Do not regenerate shadow bitmaps on every animation frame.

Use Compose caching (`drawWithCache` or suitable alternatives) where beneficial.

Avoid introducing unnecessary battery drain.

Static dashboard elements should not continuously redraw.

---

# PART XI — FUNCTIONAL AND DATA SAFETY

## 29. DO NOT CHANGE EXISTING FUNCTIONALITY

The implementation must preserve existing:

- Session engine
- Countdown and stopwatch modes
- Overtime handling
- Session completion rules
- Session history
- Data storage
- Device synchronization
- Phone/tablet roles
- Achievements history
- Achievement types
- Index tracker
- Real analytics calculations
- Date navigation
- Timeline interactions
- Popups
- Navigation
- Edit Layout
- Card resizing
- Persisted layouts
- Theme and motion settings

The visual redesign must not change the meaning of stored data.

Do not delete existing screens or features because they are not shown in the screenshots.

### Real data only

Do not hardcode mockup examples such as:

- 86/100
- 4h 12m
- 76%
- 84%
- 32/126 topics
- Specific sample project names
- Sample monthly productivity bars
- Sample insight paragraphs
- Sample dates

Use the existing application's data sources.

If the reference images contain internally inconsistent sample statistics, correct the real data binding, not the underlying visual design.

### Date behavior

Dates must use the device's local time and the application's existing date-selection behavior.

Do not hardcode the illustrated dates.

---

# PART XII — VISUAL FIDELITY AND VERIFICATION

## 30. SCREENSHOT MATCHING

The implementation should be reviewed against the supplied images at comparable viewport dimensions.

Focus on:

- Surface luminance
- Card placement
- Card proportions
- Correct shadow direction
- Shadow softness
- Nested card elevation
- Circle and icon dimensions
- Selected tab position
- Typography baseline
- Chart geometry
- Cyan dotted reference lines
- Mini-card spacing
- Overall vertical rhythm

### Visual mismatch priority

Fix in this order:

**Critical**

- Incorrect layout
- Missing cards
- Incorrect card dimensions
- Missing or incorrect layer hierarchy
- Wrong chart type
- Flat progress rings
- Bright/glossy surfaces

**High**

- Incorrect shadow direction
- Incorrect color palette
- Wrong typography scale
- Extra borders
- Incorrect chart layering
- Incorrect internal spacing

**Medium**

- Minor icon stroke variation
- Small radius differences
- Subtle shadow-opacity differences
- Micro-alignment

Do not spend excessive time polishing a 1-pixel icon difference while an entire card is missing or resized incorrectly.

---

# PART XIII — EXECUTION CONTRACT

## 31. IMPLEMENTATION PROCEDURE

First inspect the repository and identify the existing screen composables, theme system, drawing utilities, chart components, and UI architecture.

Reuse what already exists wherever possible.

### Phase A — Shared material system

Implement:

- Color tokens
- Typography tokens
- Directional shadow modifier
- Elevated card components
- Mini-card components
- Raised ring/gauge component
- Raised horizontal progress component
- Flat tab styles

### Phase B — Apply to existing screens

Apply the shared system to:

1. Home
2. Achievements
3. Analytics Day
4. Analytics Week
5. Analytics Month
6. Analytics Year

Preserve all real data connections and navigation.

### Phase C — Screenshot alignment

Use attached reference images to correct:

- Layout geometry
- Surface brightness
- Shadow direction
- Shadow opacity
- Chart position
- Circular-ring construction
- Spacing inconsistencies

Prefer adjustments to shared tokens/components rather than individual screen hacks.

### Phase D — Focused validation

Perform only the relevant checks needed to establish that the modified UI compiles and retains critical interactions.

Do not run unnecessarily expensive full-project workflows repeatedly.

Do not build or package a distributable APK unless explicitly requested.

The user can perform device installation locally.

Do not rewrite the codebase or replace working architecture.

---

# 32. NON-NEGOTIABLE FINAL REQUIREMENTS

Before reporting completion, verify the following:

- [ ] Every major card uses opaque matte charcoal.
- [ ] The global canvas is very dark, unpatterned, and non-reflective.
- [ ] All major shadows fall down and right.
- [ ] No unwanted glass, gloss, metal, bevel, or reflective effects appear.
- [ ] Nested cards visibly sit above their parent panels.
- [ ] Circular gauges have elevated hubs and distinct progress rings.
- [ ] Progress bars visually sit above their tracks.
- [ ] Small icon hubs have restrained directional shadows.
- [ ] Unselected period tabs are completely flat.
- [ ] Selected period tabs use only amber text/underline.
- [ ] Top-right period labels are flat, without card backgrounds.
- [ ] Cyan dotted average lines render BEHIND chart bars.
- [ ] Average duration labels appear at the far right, just above the line.
- [ ] Day, Week, Month, and Year retain the same shared scaffold.
- [ ] Each period displays the correct chart substitution.
- [ ] Expanded Executive Telemetry contains two nested insight cards.
- [ ] The Home, Achievements, and Analytics surfaces have consistent material appearance.
- [ ] No screenshot image is used as a substitute for functional native UI.
- [ ] Real application data drives all displayed values.
- [ ] All current functionality remains intact.
- [ ] Existing immersion, scrolling, navigation, and resizing continue to work.

---

# 33. FINAL INSTRUCTION TO CODEX

**The attached images are the design specification. This document defines the material physics and reusable implementation rules that should make those images reproducible.**

Do not introduce your own alternative interpretation of the design.

The essential quality to reproduce is this:

A near-black base canvas supports dark-charcoal matte card plates. Those card plates cast deep, soft shadows only toward the bottom-right. Smaller cards, bars, circular gauges, hubs, icon badges, and buttons sit on top of those plates as distinct physical layers. Vibrant amber and cyan convey data and interaction, while green and red convey meaningful status. Typography remains sharply defined, compact, and information-dense.

The result must look like carefully stacked, non-reflective physical UI components rather than ordinary flat rectangles or shiny glass panels.

Implement the visual system as a reusable native Android design language, preserve the existing application, and match the attached final screenshots as closely as practical.

**Do not redesign. Do not add features. Do not remove features. Do not substitute hardcoded mockup data. Deliver the final implementation and a short summary of changed files and any remaining visual discrepancies.**