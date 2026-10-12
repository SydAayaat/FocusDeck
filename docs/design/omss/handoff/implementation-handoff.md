# FocusDeck — Complete UI Implementation Handoff
## OMSS Theme · Home · Session Summary · Daily Timeline · History · Motion

### OBJECTIVE

Implement the finalized FocusDeck Android UI designs using the attached screenshots as authoritative visual references.

This task covers:

1. Home navigation and layout redesign.
2. Current Session and Last Session cards.
3. Compact Daily Timeline expansion animation.
4. Expanded vertical 24-hour Daily Timeline.
5. Reusable timeline event detail cards.
6. Session History and Activity Log.
7. Session Summary page for completed, ended-early, and other terminated sessions.
8. Shared OMSS visual materials, shadows, elevation, and motion behavior.

**Executive Telemetry Insights is explicitly deferred. Do not modify or implement it.**

These screens have been designed and approved in a separate conversation. However, do not assume the mockups are already implemented in the repository.

Inspect the current code first, preserve working functionality, and implement only what is missing or requires refinement.

The existing repository is the source of truth for functional behavior and data contracts.

The attached screenshots are the visual authority.

Written requirements below override conflicting details in older screenshots.

---

# 1. Global Design System — OMSS

**OMSS = Onyx Matte & Shadow-Stacked.**

Use this design language consistently across every affected screen.

### Background

- Near-black background, approximately `#090A0E`.
- Matte charcoal cards, approximately `#17191E`.
- Slightly lighter nested surfaces where elevation requires them.
- No glassmorphism.
- No glossy reflections.
- No decorative background textures.
- No unnecessary gradients.
- No thick illuminated borders.

### Elevation and shadows

The visual hierarchy is created through physical-looking layering.

Background → Main card → Elevated chart/progress elements → Icons and indicators.

Higher elements cast soft shadows toward the bottom-right.

Use subtle shadow blur and offset to suggest depth.

Do not apply equally strong shadows to every element.

**Critical rule: icons must cast shadows directly.**

Do not wrap ordinary icons in square blocks merely to create elevation.

This applies to:
- Clock and timer icons.
- Play/pause icons.
- Trophy icons.
- Analytics symbols.
- Completion indicators.
- Progress and status icons.

Only use icon backgrounds when explicitly required by an approved component.

### Accent colors

- Amber `#FFA726`: primary theme accent.
- Golden yellow: progress and highlighted values.
- Cyan: informational/progress indicators.
- Teal-green: success and completion.
- Red: lateness, overruns, failures, and negative states.
- Near-white: primary text.
- Muted blue-gray: secondary information.

Use restrained localized glow on approved progress rings and status indicators, not on card surfaces.

### Typography and spacing

- Clear, bold headings.
- Compact metric labels.
- Large, readable numeric values.
- Tabular numerals for times and durations.
- Thin separators between related information.
- Consistent padding.
- Avoid oversized blank spaces.

Use native Jetpack Compose components wherever possible.

Do not replace functional UI with static screenshot backgrounds.

---

# 2. Home Page Redesign

Remove the four original top-left navigation icons:

- Home.
- Achievements.
- Focus/Timer.
- Analytics.

The unused Focus/Timer navigation destination should no longer appear.

**Do not remove the actual session timer or stopwatch functionality.**

### New navigation

Below the Home metrics, display:

- One wide Analytics button.
- One compact trophy-shaped Achievements button immediately beside it.

The Analytics button occupies most of the available width.

Both controls should have matching heights.

Use the same matte OMSS material.

### Header

Preserve the Connected/Synced indicator, current date, and three-dot menu where applicable.

Arrange them with visually balanced spacing.

Do not force awkward alignment.

Move the page content upward to reclaim the space previously occupied by the removed navigation icons.

### Session cards

Swap the positions of the original Last Session and Upcoming Session cards.

Rename:

`Upcoming Session` → `Current Session`

**Current Session:**
- Displays the active session, if one exists.
- Tapping opens that session.
- Uses a small red recording indicator that pulses gently while the session is active.
- Displays `No active session` when idle.
- Must reflect actual application state.

**Last Session:**
- Displays the most recently recorded session.
- Tapping opens its summary.
- `View all` opens the History page.

Preserve the existing Daily Timeline and other Home metrics.

---

# 3. Compact Daily Timeline and Expansion Animation

The Home Daily Timeline remains horizontal, representing 00:00–24:00.

It retains planned and actual tracks.

Tapping the date and arrow opens the Expanded Daily Timeline.

### Required transition

The horizontal timeline should visually transform into the vertical timeline.

Sequence:

1. The horizontal axis begins rotating.
2. The axis elongates and scales.
3. The camera-like transition zooms into the timeline.
4. The compact card fades out.
5. The timeline settles into a vertical axis on the left side.
6. Event cards appear beside their chronological positions.

Coordinate rotation, scale, translation, and opacity.

The transition should feel continuous rather than like an unrelated page opening.

Use motion preferences and avoid expensive animations.

Do not introduce this animation into unrelated Home components.

---

# 4. Expanded Daily Timeline

Create a vertically scrollable 24-hour timeline.

### Layout

- Time axis on the left.
- Time labels every 30 minutes.
- Chronological events placed on the right.
- Colored markers connecting events to their timestamps.
- Each event anchored to its actual start time.
- Support multiple events within the same day.

Example:

08:00 — Event A

10:00 — Event B

12:00 — Event C

The vertical axis must remain temporally accurate.

### Event layout

Event detail cards may be taller than the time interval between consecutive events.

Use collision-aware layout, expandable cards, or compact previews to prevent overlap.

Do not falsify timestamps to make the layout fit.

Use real persisted session/block records.

Support completed, active, paused, skipped, ended-early, and break events.

---

# 5. Reusable Expanded Timeline Event Card

Use the approved final event-card screenshot.

This component is repeated along the vertical timeline.

Remove standalone page navigation from the event card:
- No back arrow.
- No three-dot page menu.

### Header

Display:
- Project/session context.
- Event title.
- Event type.
- Timer mode.
- Execution status.

### Timing metrics

Display four primary values:

| Metric | Example |
|---|---|
| Start | 02:47 (+00:47) |
| End | 04:16 (+00:46) |
| Elapsed | 01:29 |
| Estimated | 01:20 (+00:09) |

Use signed `HH:MM` deviations.

Positive start/end delays indicate lateness.

Negative values indicate earliness.

Estimated duration deviation should represent actual duration minus planned duration.

### Focus statistics

Display:
- Focus Time.
- Pause Time.
- Pause Count.

### Start Delay Breakdown

Use the approved compact segmented bar.

Display on one compact horizontal section:

**Start Delay Breakdown — Segmented Bar — Total Delay**

Example:
- Amber inherited segment: `+00:40`.
- Red additional segment: `+00:07`.
- Total: `+00:47`.

Values appear inside the bar segments.

Do not add separate inherited/additional rows below the bar.

For narrow segments, provide a tooltip or accessible detail disclosure.

### Calculation

Total start delay = actual start − planned start.

Inherited delay represents lateness attributable to preceding work.

Additional delay represents the remaining delay introduced before starting the current block.

Handle:
- Scheduled gaps.
- Overlapping blocks.
- Early starts.
- Independent sessions.
- Missing timestamps.

Do not invent attribution when the available records are insufficient.

### Time Estimation Accuracy

Display:
- Accuracy percentage.
- Amber progress bar.
- Short explanatory caption.

Example:

89.9%

9 minutes longer than estimated.

Use a consistent formula, with the previously approved illustrative calculation:

`100 × min(estimated_duration, actual_duration) / max(estimated_duration, actual_duration)`

Handle zero and missing durations safely.

Do not treat provisional live values as finalized scores.

### Execution Details

Display:
- Status.
- Overtime, when applicable.
- Quality criteria count.

Example:

Completed · Ended early

Overtime: -00:09

Quality: 4/5 criteria met.

Use the actual execution semantics. Countdown overtime and early finish relative to a schedule are different concepts.

### Explicit removals

Do not include:
- Session & Block details section.
- Quality Criteria checklist section.

The final approved event card omits both.

### Styling

Use matte charcoal surfaces, standalone shadowed icons, subtle directional shadows, and elevated progress bars.

Do not introduce square icon backgrounds.

---

# 6. History — Sessions & Activity Log

Implement the finalized History design.

### Navigation

Accessible through Home → Last Session → View all.

### Main layout

- History heading.
- Subtitle.
- Search.
- Date picker.
- Filter controls.
- Horizontal category selector.
- Chronological vertical activity feed.

Newest entries appear first by default.

### Date separators

Group records by local date.

Use centered separators such as:

TODAY, 11 OCT 2026

YESTERDAY, 10 OCT 2026

Older records appear as the user scrolls downward.

### Activity filters

Support simultaneous selection of:

1. Sessions.
2. GPT Session Generation.
3. GPT Card Generation.
4. Index Progress.
5. Achievement Changes.
6. Block Events.
7. Status Updates.

Filters should support multiple active categories.

Also provide date-range filtering, text search, and chronological sorting.

### Activity cards

Use compact OMSS cards with:
- Event icon.
- Title.
- Timestamp.
- Short description.
- Relevant metrics.
- Status.
- Detail-navigation chevron.

Event categories should be visually distinguishable without overwhelming the interface.

### Session events

Display actual session title, duration, completion state, block counts, and relevant performance metrics.

Tapping opens the Session Summary.

### GPT Session Generation

Show generation timestamp, session ID, generated block count, planned duration, and import result.

Show original structured payload only when actually persisted and accessible.

### GPT Card Generation

Show card name, type, category, generation timestamp, and operation result.

### Index Progress

Show topic completions/reopens, topic count, and net progress changes.

### Achievement Changes

Show achievement name, streak/progress change, and updated state.

### Block Events

Show started, paused, resumed, completed, skipped, ended, and overtime-related events when recorded.

### Status Updates

Show meaningful session, objective, device, or synchronization changes that were actually logged.

### Persistence

Use existing durable storage.

Do not invent past GPT interactions or reconstruct missing payloads from summaries.

Preserve event identity and relationships.

Historical categories without available records should show an appropriate empty state.

---

# 7. Session Summary Page — Finalized Redesign

This is the newly approved screen.

Use the final screenshot showing the **Session Complete page without the large central completion ring**.

The original implementation contains substantial reporting functionality that must remain available.

### Main design objective

Make the Session Summary cleaner, more compact, and visually consistent with OMSS.

**Remove the large decorative completion ring with the central checkmark.**

Do not replace it with another large hero animation.

Instead, display a compact status badge near the session title.

### Header

Display:
- Close button.
- Session/project title.
- Session type and timer mode, when available.
- Session date.
- Start and end timestamps.
- Final session status.

Example:

PTfS · Phase 1

Core Theory Rapid Pass

Study · Countdown

12 Oct 2026 · 00:12 → 00:12

Session Complete

### Status badge

Use a compact teal-green checkmark and status label for successful completion.

Use an appropriate amber/red status for early termination or failure states.

The badge must reflect the real session result.

Display the actual summary counts:

- Completed blocks.
- Skipped blocks.
- Ended-early blocks.
- Not-started blocks.

Do not hardcode success counts.

### Main performance card

Preserve the existing metrics:

| Metric | Description |
|---|---|
| Planned | Planned session duration, if available |
| Active Focus | Total active focus time |
| Paused | Total paused duration |
| Wall Time | Actual elapsed wall-clock duration |
| Pace | Existing FocusDeck pace metric |
| BeatScore | Existing FocusDeck BeatScore metric |

Use two compact rows of three metrics.

Maintain the existing calculations.

Do not invent new formulas for Pace or BeatScore.

### Focus ratio

Preserve the existing Overall Focus Ratio metric and its circular progress indicator inside the performance card.

**Important distinction:**

Remove only the large decorative completion ring at the top of the page.

The smaller analytical Focus Ratio ring may remain inside the performance card.

Preserve the existing score explanations, but improve typography and spacing.

Do not silently relabel Focus Ratio as Time Estimation Accuracy. These are different metrics.

The previous generated mockup included that label substitution; treat it as a visual inconsistency rather than a functional requirement.

### Session Blocks

Display the blocks in execution order.

Each compact card shows:
- Block title.
- Recorded completion state.
- Duration.
- Relevant status text.
- Chevron to open block details.

Completed blocks use the appropriate success icon.

Skipped, ended-early, and unfinished blocks must have distinct status indicators.

Do not award completion credit to skipped or ended-early blocks.

The screenshot includes test blocks with names such as “Verify Skip block.” These are test titles, not evidence of actual skip status.

Always use persisted block status.

### Detailed Timeline

Preserve the expandable event timeline.

Display:
- Total event count.
- Recorded session start/stop range.
- Chronological event list.
- Timestamps.
- Event descriptions.
- Relevant durations.
- Colored event markers.

Support events such as:
- Block started.
- Block completed.
- Reward offered.
- Reward acknowledged.
- Pause/resume.
- Skip.
- Manual block end.
- Session end.

Keep the existing Show More interaction.

Do not discard events to simplify the design.

### Footer statistics

Preserve:
- Total pauses.
- Extensions.
- Longest uninterrupted focus period.
- Timezone.
- Timeline coverage/completeness indicator.

These can be presented in compact secondary text.

### More Details

Preserve the existing expandable More Details section and its functionality.

It may remain collapsed by default.

### Report actions

Preserve:
- Save Report.
- Copy Full Report.
- Existing report status feedback.
- Send Report, if currently supported.

Use compact OMSS-styled action buttons.

Do not remove report export functionality.

### Layout

The final summary should feel compact and information-dense.

Avoid:
- Giant hero animations.
- Oversized empty spaces.
- Repeated status text.
- Excessive nested cards.
- Unnecessary decorative borders.

Use directional shadows on elevated icons and indicators.

Keep the original reporting data intact.

---

# 8. Session Summary State Handling

The same layout must adapt to different outcomes.

### Completed

- Compact green/teal success indicator.
- Accurate completed-block count.
- Completed-session reporting.

### Ended Early

- Appropriate warning/termination indicator.
- Accurate ended-early and unfinished counts.
- Preserve the actual focus and wall-time measurements.
- No false completion credit.

### Skipped or Partially Completed

- Display actual execution outcomes.
- Distinguish skipped, incomplete, and not-started states.

Do not infer state from the session title.

Do not assume a high BeatScore means all blocks were completed.

The reporting screen must use the existing execution and scoring contracts.

---

# 9. Motion Specifications

Preserve the FocusDeck motion preferences:

- FULL.
- REDUCED.
- OFF.

### FULL

Use:
- Smooth horizontal-to-vertical timeline transformation.
- Gentle page fade and translation.
- Short progress-bar reveals.
- Subtle icon elevation transitions.
- Smooth numeric updates.
- Gentle Current Session recording pulse.
- Smooth expansion/collapse for detailed timelines.

### REDUCED

Use simplified fades and minimal transforms.

Avoid large rotations and zooms.

### OFF

Use immediate state transitions without decorative motion.

### Session Summary

Since the large completion ring has been removed, do not retain its orbiting animation as an invisible or off-screen workload.

A brief status-indicator entrance animation is acceptable in FULL mode.

Do not create continuous decorative animation on a completed-session page.

### Performance

Prioritize responsiveness.

Avoid unnecessary recomposition, repeated animations, or continuous high-frequency updates on static screens.

Maintain efficient scrolling for long History and Daily Timeline lists.

---

# 10. Implementation Constraints

1. Work within the existing FocusDeck repository.
2. Inspect current Compose screens and navigation before editing.
3. Reuse existing models, services, and storage.
4. Preserve session execution and synchronization behavior.
5. Do not modify unrelated pages.
6. Do not overwrite historical records.
7. Do not hardcode mockup statistics.
8. Do not silently replace existing scoring formulas.
9. Do not remove existing export/report capabilities.
10. Keep modifications modular and easy to review.

### Validation

Use focused checks for changed code.

Prioritize:
- Compilation correctness.
- Navigation.
- Session state mapping.
- Timeline event ordering.
- History filtering.
- Report preservation.
- Motion preferences.

Avoid unnecessary full-project validation loops.

Do not build or attach an APK unless explicitly requested.

The user can build and install the APK locally.

---

# 11. Explicitly Deferred

**Executive Telemetry Insights**

Leave the existing implementation unchanged.

Do not implement the experimental redesign or add new analytics insights.

This will be addressed in a separate task.

---

# 12. Screenshot Authority

Use the attached finalized screenshots for:

- Home layout and navigation.
- OMSS Achievements theme.
- Analytics visual styling.
- Expanded Daily Timeline.
- Event detail cards.
- History activity feed.
- Completed Session Summary.
- Ended-Early Session Summary.

Use the latest approved versions.

Older screenshots may contain obsolete components.

If an image conflicts with the latest written requirements, follow this document.

All example names, dates, durations, scores, and statuses in mockups are illustrative.

---

# 13. Final Deliverables

Provide:

1. Concise implementation summary.
2. Changed files/components.
3. Features preserved from the existing implementation.
4. Focused test results.
5. Any unsupported historical data or logging limitations.
6. Known issues, if any.
7. Local build/install instructions.

**Goal:** Implement the approved FocusDeck UI consistently, preserving all functional contracts, while keeping the code changes focused and efficient.

Do not redesign finalized components or expand scope unnecessarily.