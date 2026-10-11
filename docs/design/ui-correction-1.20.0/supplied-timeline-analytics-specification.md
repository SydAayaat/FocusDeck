# FocusDeck — Daily Timeline, Analytics & UI Refinements
**Implementation Specification v2.0 — Final**

## OBJECTIVE

Implement the finalized FocusDeck Horizontal Daily Timeline and expanded Analytics experience on Android and desktop, together with the approved achievement-card reordering and scrollbar refinements.

Preserve FocusDeck's existing architecture, session execution, achievements, Index Tracker, reward logic, synchronization, and offline-first behavior.

**This is an incremental implementation, not an application rewrite.**

GPT remains responsible for planning schedules. FocusDeck displays planned activities, records actual execution, calculates factual statistics, and provides interactive visualizations.

Do not introduce AI scheduling, automatic rescheduling, or autonomous productivity decisions.

---

## 1. SOURCE AUTHORITY & VISUAL REFERENCES

Use the existing repository as the authority for actual behavior, storage schemas, navigation, device responsibilities, and supported data.

Use the six attached finalized screenshots as the UI reference set:

| Reference | Screen |
|---|---|
| `FocusDeck Daily Timeline Popup(1).png` | Android Home |
| `Space-Themed Analytics Dashboard(1).png` | Android Analytics — aggregate |
| `FocusDeck Analytics Dashboard(2).png` | Desktop Analytics — Day |
| `FocusDeck Neon Analytics Dashboard(1).png` | Desktop Analytics — aggregate |
| `ChatGPT Image Oct 9, 2026, 09_31_03 AM.png` | Desktop Achievements |
| `ChatGPT Image Oct 9, 2026, 09_30_50 AM.png` | Android Achievements |

For Desktop Overview, follow the previously approved compact redesign and requirements in Section 2.

**Visual-reference rules:**

- Match the dark theme, amber/gold accents, cyan chart lines, rounded panels, spacing, and interaction states.
- Screenshots are visual references, not sources of data or additional functionality.
- All displayed measurements must originate from real stored records.
- Do not hardcode illustrated dates, durations, scores, punctuality, achievements, or session statuses.
- Preserve actual navigation and button placement unless a change is explicitly requested.
- Do not copy mockup-only Android navigation, Card Builder controls, or other desktop-only features into the phone.
- Written requirements override conflicting details in screenshots.

Keep the existing FocusDeck logo unchanged.

---

## 2. DESKTOP OVERVIEW — COMPACT DAILY TIMELINE

### Header and layout

Remove:

- The large "Focus with purpose" headline.
- The descriptive/motivational subtitle.
- The motivational quote in the top-right header.

Retain the existing planet artwork and phone connection/offline information strip.

Move the remaining content upward to use the available space.

Preserve the current KPI summary row.

### Daily Timeline component

Insert a compact full-width horizontal timeline directly underneath the KPI row.

Display:

- Full local calendar day, 00:00–24:00.
- Two aligned tracks representing planned and actual execution.
- Time markers at 00:00, 06:00, 12:00, 18:00, 24:00.
- Activity segments positioned using actual timestamps.
- Thin current-time indicator where applicable.
- Category-specific colors consistent with FocusDeck.

Keep the timeline vertically compact.

Do **not** display:

- A large Daily Timeline title.
- Timeline description or decorative logo.
- Permanent PLANNED/ACTUAL track labels on the desktop Home card.
- A verbose legend.
- Previous/next-day controls or a date selector.
- A large "View full day" button.

Provide only a small two-diagonal-arrows expand icon at the top-right.

The expand action opens the detailed Analytics Day view.

### Segment interaction

On desktop hover:

1. Highlight the segment with a subtle glow.
2. Show a compact anchored popup next to the selected segment.
3. Include task title, planned interval, actual interval, and punctuality where known.
4. Allow click or keyboard selection for accessibility.

The popup must not change layout dimensions or push nearby cards.

Missing timing fields must be represented explicitly.

### Other Overview changes

- Replace the bottom-left Your Devices summary card with a Previous Sessions card.
- Preserve previous-session navigation and real report data.
- Move the This Week chart above Paste from GPT in the right column.
- Move Paste from GPT below This Week.
- Preserve Current Session and Session Ended functions.

Do not remove the Devices page or device-management functionality.

---

## 3. ANDROID HOME — DAILY TIMELINE

Preserve the current Android Home screen, its existing hero area, focus summary, controls, and navigation positions.

Do not redesign the whole screen or add desktop-only controls.

Rename the existing **Study History** entry to **Analytics**, retaining its original navigation location and behavior.

Replace only the Study History icon with a minimalist amber vertical-bar Analytics icon matching the desktop sidebar.

### Timeline design

Add the compact 00:00–24:00 two-track timeline in the approved position.

Unlike Desktop Home, the mobile layout may retain compact PLAN and ACTUAL labels as shown in the finalized Android reference.

Keep the time axis readable with the five standard ticks.

Tapping a segment highlights it and opens a small floating popup.

### Popup placement — important

The popup must:

- Appear **below the selected timeline segment**.
- Be anchored visually to that segment using a small pointer/connector.
- Float above surrounding UI content.
- Overlap the Last Session card when necessary.
- Reserve **no additional permanent vertical space**.
- Never push the Last Session card downward.
- Dismiss on outside tap, Back, or selecting another segment.
- Reposition within the viewport when necessary to avoid clipping.

Keep the timeline card at its normal compact height while the popup is open or closed.

Use a suitable overlay/popup layer rather than inserting the popup into ordinary vertical layout flow.

### Popup content

Show only concise metadata:

- Task title.
- Planned start and end.
- Actual start and end, when recorded.
- Punctuality.
- Optional short execution status.

Examples:

**Early:** `+8m` in green.

**Late:** `-8m` in red.

**On time:** `0m` in neutral color.

Use an amber-bordered dark popup with subtle glow. Preserve the existing category/segment colors.

Do not navigate to a new page when inspecting Home segments.

---

## 4. ANALYTICS — TWO RESPONSIVE TEMPLATES

Implement a shared Analytics experience across Android and desktop using two distinct presentation templates.

### Template A — Day

Used when a specific day is selected.

- Full 00:00–24:00 horizontal Daily Timeline.
- Planned and actual tracks.
- Detailed activity inspection.
- Session details and punctuality.
- Day-specific recorded statistics.
- Existing session history/report access.

**Do not display an additional daily vertical-bar overview above the main timeline.**

### Template B — Aggregate

Used for Week, Month, Year, and All views.

- Compact vertical-bar visualizations.
- Line-plus-bar combined charts.
- Average and best indicators.
- Punctuality Trend.
- Period summary.
- Cumulative focus distribution.
- Consistency and completion charts.

Do not display the Daily Timeline or Session Detail panel in aggregate views.

Those belong to Day.

### Navigation

Preserve direct access through the existing:

`Day | Week | Month | Year | All`

Users must be able to switch directly to any scope without stepping through every level.

Use the following drill-down structure:

- **All:** yearly bars across available history.
- **Year:** 12 monthly bars for the selected year.
- **Month:** calendar-week groups intersecting the selected month.
- **Week:** seven daily bars.
- **Day:** horizontal 24-hour timeline.
- **Activity:** details inside the Day view.

Calendar-week intersections must be explicitly defined and must not double-count activity.

Keep date navigation and date selection.

Remove the obsolete optional six-hour zoom mode. The daily overview always shows the full day.

---

## 5. ANIMATED ANALYTICS DRILL-DOWN

Implement connected transitions **inside the expanded Analytics experience only**.

The compact Home timeline must not perform full chart morphing.

### Animation behavior

When selecting a chart bar:

1. The selected bar receives a subtle glow.
2. The previous chart transitions toward the selected period.
3. Bars transform/reflow into the next granularity.
4. The new chart appears as a visually connected continuation.

Examples:

- Selecting 2026 in All transforms to the 12 months of 2026.
- Selecting October transforms to its constituent weeks.
- Selecting a week transforms to seven days.
- Selecting a day transforms the vertical chart into the horizontal 24-hour timeline.
- Selecting an activity reveals its detail panel.

Back navigation reverses the transition and restores the prior period and selection.

Maintain a drill-down navigation state so the user can reliably return to the previous chart.

### Motion timing targets

| Transition | Target |
|---|---|
| Year → Month | 450 ms |
| Month → Week | 400 ms |
| Week → Days | 350 ms |
| Day bar → Timeline | 500 ms |
| Activity popup reveal | 220 ms |
| Popup dismissal | 180 ms |

These timings are guidance rather than mandatory delays.

Use smooth easing, restrained amber glow, connected motion, and animated repositioning.

Animations must be interruptible and must not block interaction.

Respect the existing motion preferences:

- FULL — complete transitions.
- REDUCED — shortened/faded transitions.
- OFF — immediate state changes.

Do not install a large animation library if the existing UI framework supports the required behavior.

---

## 6. ANALYTICS KPI METRICS

Display the following metrics where appropriate for the selected period:

1. Focus time.
2. Sessions.
3. Blocks marked DONE.
4. Break time.
5. Planned or total tracked time, as appropriate to the template.
6. Focus rate / supported completion metric.
7. Punctuality.

Do not invent a score or infer a completion percentage from unsupported data.

Preserve the existing meaning of measured metrics. A newly introduced tracked-time ratio must be distinguished from any existing completion-rate metric.

All KPI cards must use the same visual language as the supplied references.

### Punctuality calculation

Define:

`start_difference = planned_start - actual_start`

Use absolute timestamps to calculate the difference.

Display convention:

- Early start: positive value, green.
- Late start: negative value, red.
- Exactly on time: zero, neutral.

Examples:

| Planned | Actual | Display |
|---|---|---|
| 10:00 | 09:52 | `+8m` green |
| 10:00 | 10:08 | `-8m` red |
| 10:00 | 10:00 | `0m` neutral |
| 10:00 | 10:50 | `-50m` red |

For aggregate periods, calculate the **signed arithmetic mean** of eligible start differences.

Include only activities with both valid planned and actual start timestamps.

Do not fabricate punctuality for duration-only plans, historical sessions with missing timestamps, or unscheduled activities.

A missing comparison shows `N/A`.

Expose the number of eligible comparisons in details/tooltips where useful, so partial coverage is clear.

For session-list punctuality, compare the planned session start against the actual recorded session start. Do not substitute the first block's start unless existing canonical records explicitly establish that equivalence.

Keep session punctuality distinct from individual block punctuality.

### Punctuality Trend

Include the approved Punctuality Trend card.

- Zero-centered chart.
- Green positive/early values.
- Red negative/late values.
- Daily or period-appropriate averages.
- Subtle cyan connecting line.
- Hover/tap for exact values.
- Clear missing-data treatment.

Do not infer that a session was completed simply because punctuality data exists.

---

## 7. AGGREGATE ANALYTICS — FINAL CHART DESIGN

### First chart row

Use three compact, balanced cards on desktop:

1. **Focus Time per Day** (or equivalent period-granularity title).
2. **Punctuality Trend**.
3. **Average Focus by Hour**.

The Focus Time card must no longer stretch across the entire page.

Aim for approximately one-third of the main chart-row width per card, adapting responsively.

On Android, reorganize these cards for the existing mobile screen width without making text or chart labels illegible.

### Line-plus-bar styling

Use the approved chart style:

- Solid warm-golden vertical bars.
- Smooth glowing cyan line.
- The line passes through the exact top-center value of every bar.
- The line and bars represent the **same data series**.
- No independent smoothed values or visually separated peaks.
- Fine dotted cyan grid.
- Subtle selection glow.
- Compact bars with comfortable spacing.

Use interpolation that preserves the true data points. Missing values must not be filled with invented measurements.

### Selection behavior

Do not permanently display time labels above every bar.

On hover (desktop) or tap (phone):

- Highlight the selected bar.
- Show a small anchored popup with the period/date and precise value.
- Optionally show a thin vertical guide.
- Do not surround the bar with a large rectangular selection box.

### Average and best lines

For Focus Time charts, display:

- Dotted cyan average reference.
- Dotted amber best reference.

Both must be calculated from the appropriate underlying data and selected scope.

For month/year views, adapt the categories, axes and chart titles to the chosen period while preserving the chart design.

For dense periods, use responsive labeling, appropriate aggregation, or horizontal chart scrolling without distorting the underlying values.

### Average Focus by Hour

Represent the average recorded focus time in each local clock-hour bucket across the selected period.

Use canonical focus intervals.

Split activity correctly across hour boundaries, calendar days, and DST transitions.

Define the averaging denominator consistently and distinguish missing/incomplete recording days from genuine zero-activity days.

This chart is an hourly distribution, not a separate productivity score.

---

## 8. AGGREGATE ANALYTICS — ADDITIONAL CARDS

Keep the approved supporting cards below the three primary charts.

### Period Summary

Display grounded values such as:

- Total focused duration.
- Total tracked duration.
- Focus rate.
- Completed block count.
- Comparison against the equivalent previous period, when possible.

Do not show fabricated percentage changes if the comparison period lacks valid data.

### Focus vs Tracked Time

Use the approved donut/circle graph.

Show:

- Cumulative focused time.
- Cumulative total tracked time.
- Explicitly recorded non-focus time.
- Percentage distribution.

Only classify intervals supported by actual records.

Unrecorded time is not automatically break, pause, distraction, or rest.

The donut components must agree with the canonical tracked-time total.

### Weekday Consistency

Display recorded focus time and supported consistency/completion measurements across weekdays.

### Top Sessions / Projects

Rank available sessions or projects by recorded focus duration.

Use existing session/project associations rather than introducing new independent records.

### Completion Trend

Display the number of recorded DONE blocks over the selected period, using the same polished chart treatment.

Keep the existing session-history navigation accessible.

---

## 9. DAY ANALYTICS — FINAL LAYOUT

Use the finalized Desktop Day reference.

### Primary layout

- Existing scope tabs and date navigation.
- KPI row including punctuality.
- Large horizontal Daily Timeline.
- Session Detail panel.
- Sessions for This Day.
- Focus vs Breaks donut.
- Focus by Hour chart.

Keep the layout balanced.

### Daily Timeline

Display two synchronized tracks for PLANNED and ACTUAL.

Avoid unnecessary legends explaining every segment or color.

Track identity should remain accessible through concise labels or accessible descriptions, even when verbose legends are removed.

Provide activity selection and a clean detail reveal.

The selected activity shows:

- Task title.
- Associated Index topic/achievement, if any.
- Planned start and end.
- Actual start and end.
- Planned duration.
- Actual elapsed duration.
- Active focus duration, where different.
- Pause and overtime measurements, where recorded.
- Start punctuality.
- Recorded execution status.

When a value is unavailable, show an explicit missing-data state.

Do not show a false zero.

### Sessions for This Day

Each session row should include:

- Session title.
- Recorded interval and focus time.
- Existing report/session status.
- Compact punctuality indicator.

Punctuality examples:

- `+3m` green.
- `-47m` red.
- `N/A` neutral.

Retain navigation to actual saved reports and detailed history.

### Detail panel

Use the finalized side-panel style on desktop and a compact inline/bottom detail panel on Android.

Do not create a separate page merely to inspect an activity.

---

## 10. PLANNED SCHEDULING METADATA

Extend the existing GPT session protocol with optional absolute scheduling information.

Inspect existing schema/protocol files first.

Reuse established session and block identifiers.

For each scheduled activity, support:

- Existing session/block ID.
- Activity title.
- Category.
- Optional achievement or Index topic reference.
- Planned start timestamp.
- Planned end timestamp.
- Associated timezone/offset information.

Support a timezone identifier where necessary for stable local-day interpretation.

**Backward compatibility is mandatory.**

- Duration-only plans remain fully valid.
- Absolute scheduled times must never be guessed from durations.
- Untimed activities appear as Unscheduled.
- Existing GPT imports continue working.
- Timed planning fields remain optional.
- Historical planned schedules are persisted as snapshots.
- Explicit plan replacement/revision must not silently rewrite prior history.

Do not create a second independently editable scheduling system.

---

## 11. ACTUAL EXECUTION & HISTORY

Use existing canonical session reports, timing events, and recorded intervals.

Represent:

- Focus intervals.
- Break intervals.
- Pauses/resumes.
- Overtime.
- DONE timestamps.
- Recorded block titles and categories.

Preserve the original meaning of session completion.

Do not add FAILED, SKIPPED, ABANDONED, or other outcome states to the normal DONE-only workflow.

Paused time must not count as active focus.

Overtime must be derived from genuine recorded timing.

Do not mark unrecorded periods as inactivity, distraction, or rest.

Legacy or incomplete records must be labeled accurately, not reconstructed using guesses.

Reconnection, replay, restarts and repeated synchronization must not duplicate activity intervals or counts.

Retain existing deletion/tombstone behavior and historical report filtering.

---

## 12. CALENDAR & TIMEZONE CORRECTNESS

The daily timeline represents a complete local calendar date from midnight to the following midnight.

Preserve source timestamps as absolute instants.

Also preserve the timezone context required to interpret historical schedules.

Important cases:

- Sessions spanning midnight.
- Focus blocks crossing multiple calendar dates.
- DST spring transitions (23 elapsed-hour local days).
- DST autumn transitions (25 elapsed-hour local days).
- Repeated local clock hours.
- Timezone changes after data was recorded.

Visually split intervals across the relevant calendar dates without duplicating the underlying records.

Allocate durations to the correct day and hourly buckets.

Retain a consistent, explicit date-grouping policy so historical values do not change silently.

The 00:00–24:00 visual representation must handle DST discontinuities rather than assuming all dates have exactly 86,400 elapsed seconds.

Use existing date/time utilities where practical.

---

## 13. ACHIEVEMENTS — DRAG-AND-DROP REORDERING

Implement or refine persistent achievement-card rearrangement on both desktop and Android.

Use the finalized Achievements references.

### Desktop

Provide a Custom Order / Reorder mode within the existing Achievements page.

Preserve:

- Search and category filters.
- Achievement grid.
- Selected achievement detail panel.
- Existing card actions and sorting controls.

### Android

Reuse the existing Edit Layout entry and grid where available.

Do not redesign Android navigation or introduce unrelated controls.

### Drag behavior

When dragging begins:

- Card lifts slightly.
- Subtle amber glow/shadow appears.
- Card follows the pointer/finger.
- Neighboring cards smoothly shift into their new positions.
- Drop target updates naturally.
- Card settles into its new position on release.

Avoid exaggerated rotation or distracting animation.

### Persistence

- Save custom ordering durably.
- Synchronize between phone and desktop using the existing device authority and synchronization protocol.
- Respect existing ownership rules; the desktop must not become an unauthorized second source of truth.
- Preserve order across app restarts and updates.
- Filtering must not destroy the underlying custom order.
- Other sorting options must not overwrite the saved custom order.
- Handle deleted, newly created, and archived achievements safely.

Avoid replacing an existing Android reorder implementation if only animation/synchronization refinement is needed.

Honor Reduced/Off motion settings and provide a non-drag accessible ordering mechanism where appropriate.

---

## 14. DESKTOP SCROLLBAR REDESIGN

Replace generic bright desktop scrollbars with themed FocusDeck scrollbars.

Style:

- Thin dark track.
- Subtle muted thumb by default.
- Amber accent on hover/drag.
- Rounded geometry.
- Smooth but restrained transitions.
- Consistent appearance across page scrolling and internal scrollable panels.

Do not compromise usability, keyboard scrolling, or accessibility.

Avoid introducing a dependency solely for scrollbar styling.

---

## 15. PERFORMANCE, ARCHITECTURE & RESPONSIVENESS

Preserve the existing responsibilities:

- Phone remains authoritative for timer execution and recorded activity.
- Desktop prepares plans and visualizes synchronized data.
- Tablet/read-only viewers retain their existing restrictions.
- Phone works offline.
- No cloud account or new backend is required.

Reuse Kotlin/Jetpack Compose components and the existing desktop stack.

Minimize schema and protocol changes.

Do not rebuild the desktop architecture, replace the synchronization layer, or migrate unrelated data models.

Aggregate charts from canonical stored records using reusable calculation logic.

Avoid redundant database queries, unnecessary redraws and continuous background polling.

Update the current-time indicator only while relevant UI is visible.

Preserve smooth scrolling and touch interactions.

Ensure layouts respond appropriately to different Android screen sizes and desktop window dimensions.

---

## 16. SCOPE EXCLUSIONS

Do not implement:

- GPT-based automatic scheduling.
- Automatic task rescheduling.
- Distraction monitoring.
- Calendar integrations.
- Anki or spaced-repetition features.
- GPT Weekly Coach.
- New achievement or reward mechanisms.
- New daily performance scoring out of 100.
- Unrequested Android navigation changes.
- Unrelated visual redesigns.

These may be developed separately.

---

## 17. IMPLEMENTATION EFFICIENCY

The goal is reliable implementation with minimal wasted execution time.

### Repository inspection

Inspect only the files and interfaces directly relevant to:

- Desktop Overview and Analytics.
- Android Home and Study History/Analytics.
- Session/report storage.
- GPT import protocol.
- Time-event handling.
- Achievement ordering.
- Motion preferences.
- Existing theme and scrollbars.

Use targeted searches and file inspection.

Do not perform repeated repository-wide audits when existing implementation patterns are clear.

### Changes

- Prefer localized, incremental modifications.
- Reuse existing components.
- Do not refactor unrelated features.
- Avoid speculative fallback systems.
- Do not add dependencies unless essential.
- Do not rewrite previously functioning functionality merely to match mockup details.

### Validation

Perform focused checks for:

1. Scheduling metadata compatibility.
2. Correct planned/actual timeline rendering.
3. Punctuality signs, signed averages, and missing data.
4. Midnight/DST attribution.
5. Interval aggregation without double counting.
6. Chart selection/drill-down behavior.
7. Achievement ordering persistence.
8. Existing synchronization/replay/deletion compatibility.

Use a small set of targeted tests or compile checks where feasible.

Do not repeatedly run the full test suite.

**Do not build an APK.**

**Do not perform physical-device verification.**

I will build and test the Android APK locally.

Avoid unnecessary packaging, broad build matrices, or lengthy visual verification loops.

---

## 18. ACCEPTANCE CRITERIA

The implementation is complete when:

1. Desktop Overview shows the compact Daily Timeline and approved panel rearrangement.
2. Android Home shows the compact timeline without changing established navigation.
3. Home activity popups display planned/actual times and punctuality.
4. Android popup overlays the Last Session card without reserving layout space.
5. Day Analytics displays the 24-hour planned/actual timeline.
6. Aggregate Analytics uses the finalized compact line-plus-bar charts and supporting cards.
7. Chart lines touch the exact tops of their associated bars.
8. Average/best reference lines are grounded in stored data.
9. Tooltips appear on selection rather than permanently above every bar.
10. Day, Week, Month, Year, and All navigation works directly and through animated drill-down.
11. Punctuality uses green positive values for early starts and red negative values for late starts.
12. Session rows display their supported punctuality.
13. Focus time, tracked time, break time, and derived ratios remain mathematically consistent.
14. Legacy duration-only GPT plans still work without fabricated scheduling times.
15. DST and cross-midnight history are handled without duplicate time/counting.
16. Desktop and Android achievement grids support animated, persistent reordering.
17. Themed desktop scrollbars replace the generic bright style.
18. Existing DONE workflow, rewards, Index Tracker, session reports, device ownership, synchronization, and offline recording remain functional.

---

## 19. FINAL DELIVERABLE

Provide:

- Implemented changes in the existing FocusDeck repository.
- Necessary backward-compatible protocol/schema updates.
- Updated desktop and Android UI components.
- Canonical Analytics calculation/aggregation logic.
- Focused validation results.
- Concise summary of changed files.
- Any remaining limitations or manual verification items.
- The local APK build command, without executing the build.

Do not claim unperformed checks passed.

Do not create releases, publish artifacts, or push changes to a remote repository unless separately authorized.

**Implement the feature according to this specification and the supplied final UI references.**