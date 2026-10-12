# FOCUSDECK — APPROVED UI REFINEMENTS
## Addendum to Final OMSS Implementation Handoff

These changes are APPROVED and should be incorporated into the implementation of Home, Expanded Daily Timeline, Session Summary, and History.

Preserve all previously approved functionality and unaffected visual details.

## 1. Expanded Daily Timeline — Collapsible Event Cards

Use the approved detailed timeline event card as the EXPANDED state.

Introduce a compact COLLAPSED state displayed by default.

The collapsed state must show:

- Session or event title
- Execution status
- Planned start/end information
- Actual start/end information where available
- Active focus duration
- Start punctuality delta

Use compact OMSS material, appropriate status color, and restrained directional shadows.

Tapping a collapsed card expands that same card to reveal the full existing detailed content:

- Start, End, Elapsed, and Estimated
- Focus Time, Pause Time, Pause Count
- Start Delay Breakdown
- Time Estimation Accuracy
- Execution details

Tapping again collapses it.

Maintain stable event identity and chronological positioning. Expanding one event must not alter its timestamp or falsely shift its timeline marker.

Use collision-aware visual layout when multiple events occur close together. Allow cards to extend vertically or reflow without overlapping.

Do not delete detailed metrics for the sake of compactness.

## 2. Session Summary — Clear Metric Separation

Preserve the original Overall Focus Ratio calculation and its small circular gauge within the performance section.

Introduce a separate, compact Time Estimation Accuracy display when actual data supports it.

Do not relabel one metric as the other.

For Time Estimation Accuracy, display:

- Accuracy percentage
- Small amber progress bar
- Estimated versus actual duration where available
- Short duration-deviation caption

Keep the section visually compact.

Use distinct execution status indicators for:

- Completed
- Ended Early
- Skipped
- Not Started

Statuses must come from stored execution records.

Do not award completion credit to skipped, ended-early, or unstarted blocks.

Keep the six existing performance metrics, detailed event history, More Details, and report actions.

Do not restore the removed large decorative completion ring.

## 3. History — Reduced Visual Clutter

Preserve the approved chronological activity-feed design.

Retain activity category color coding:

- Sessions: amber
- GPT activity: cyan
- Cards: purple
- Index updates: cyan/teal
- Achievements: amber/gold
- Status and system events: muted slate

These are visual categories, not new event types.

Simplify supporting metadata to improve readability.

Avoid redundant icons, oversized badges, unnecessary nested containers, and repeated text.

Use restrained color emphasis for category identity and meaningful execution status.

Keep search, date-range selection, sorting, and filters compact.

Allow multiple activity categories to be selected simultaneously.

Preserve real historical event records and provide truthful empty states.

Do not generate missing history artificially.

## 4. Home — Context-Aware Current Session

Keep the Current Session card above Last Session.

Use three primary display states.

ACTIVE:
- Subtle red recording indicator
- Gentle pulse in FULL motion
- Current session title and timing
- Open/Resume action consistent with existing execution behavior

PAUSED:
- Amber pause indicator
- Session title
- Paused status
- Resume/Open action

IDLE:
- No active session message
- Muted supporting text
- No fake session statistics or active recording indicator

The current state must be driven by the existing session engine.

Keep the wide Analytics shortcut and adjacent Achievements trophy shortcut unchanged.

Keep Last Session and its View All → History navigation.

## 5. Shared Timing and Punctuality Convention

Use the same timestamp formatting and punctuality convention throughout:

- Home
- Analytics
- Expanded Daily Timeline
- History
- Session Summary

Define punctuality delta as:

actual start time minus planned start time

Positive deviation = late.

Negative deviation = early.

Examples:

+8m → 8 minutes late, red.

−5m → 5 minutes early, green.

Zero → on time, neutral or appropriate success color.

Display planned and actual timestamps distinctly.

Use compact duration formats consistently, with more detailed HH:MM:SS formatting where necessary.

Do not fabricate missing planned/actual timestamps or convert unknown values into zero.

Preserve valid existing time calculations and data contracts.

## 6. Continuous Navigation Motion

Establish a coordinated navigation hierarchy:

Home Compact Daily Timeline
→ Expanded Vertical Timeline
→ Expanded Event Card
→ Associated Session Summary

FULL MOTION:
- Axis rotation, elongation, and translation for the compact-to-expanded timeline transition
- Smooth event-card expansion with neighboring-card reflow
- Subtle shared-element continuity when opening the associated Session Summary
- Short fades and content reveals

REDUCED MOTION:
- No major axis rotation or zoom
- Simple fade/translation
- Shortened expansion animation

OFF MOTION:
- Immediate state changes
- No decorative transitions

Maintain navigation state, selected event identity, scroll position where appropriate, and existing Back behavior.

Do not run decorative animation continuously on static screens.

Ensure event-card expansion is efficient and does not produce large unnecessary redraws.

## 7. Implementation Scope

Apply these refinements to the existing Compose implementation.

Reuse the current OMSS rendering components, data models, timeline engine, and report functionality.

Do not create a new application.

Do not modify Executive Telemetry Insights.

Do not overwrite production data.

Do not generate mock historical records.

Retain the existing visual-test isolation.

Preserve the original approved screenshots as the styling and expanded-state references.

These refinements supplement the previous written specification and override conflicting older interaction or layout details.

The result should be more compact, easier to navigate, and visually consistent without sacrificing recorded information.