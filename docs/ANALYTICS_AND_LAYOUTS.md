# Analytics and dashboard layouts — current source

Overview, Achievements and Analytics share a resizable grid within each UI framework. Desktop uses bundled `layout.js`; Android uses `DashboardLayout.kt`. Existing timer, transport, archive, reward and Index authority remain unchanged.

## Edit Layout

Use **Edit Layout** on each page. Edge handles change width or height; the corner changes both. A separate grip moves cards. Desktop handles also support arrow keys; move buttons and native accessibility actions provide alternatives to dragging. Only handles claim drag gestures. Outside Edit Layout, handles disappear and existing body actions and chart selection remain available.

Dimensions preview during the drag and save after release. Cancel restores the previous dimensions. Grid widths snap to normalized spans; heights snap to 24 pixels on desktop and 24 dp on Android. Saved heights are bounded to 120–2400. Narrow windows apply sensible minimum widths; phone grids allow half-width cards where the available width is at least 372 dp, otherwise full width. Tablet grids allow quarter-width cards. Custom dimensions provide internal scrolling rather than removing content. Charts redraw within their available dimensions; dense charts retain a horizontally scrollable plotting area and readable labels.

Eligible cards include Overview statistics, timeline, current/previous sessions, weekly panels and Index progress; Achievement tiles, summary, catalog and detail panels; Analytics statistics, timelines, charts, period summaries and report/history panels. Page navigation, date controls, connection notices and action-only controls are not separately resizable.

**Reset Layout** clears dimensions and local panel order for the current template. Achievement tile order is independently phone-owned: **Reset order** uses a synchronized receipt and restores default achievement order. Resetting dimensions does not silently reset the phone ledger.

## Persistence and motion

Desktop saves an atomic `layouts.json` in the existing private Control Center data directory. Templates are `overview`, `achievements`, `analytics-day`, `analytics-week` and `analytics-aggregate`, separately keyed by `@wide` or `@compact` at the 800-pixel breakpoint. Day and Week have independent templates; Month/Year/All share the aggregate template. When no Week layout exists, existing aggregate dimensions/order seed it once, independently for the same form factor. Resetting Week does not reset Month/Year/All. Writes are serialized so rapid gestures cannot save an older snapshot last.

Android retains versioned JSON in private `dashboard_layouts_v1` preferences. Keys include the page/template, phone or tablet form factor, portrait or landscape orientation and layout version. App-lifetime IO writes continue across page changes; an in-memory snapshot also prevents rapid switches from loading an older write. Developer preview uses ephemeral layout state. Existing package identity and update installation preserve these preferences; clearing application data does not.

Stable card IDs, normalized spans and separate desktop/native storage avoid forcing identical pixel sizes across devices. Reconnection does not overwrite local dimensions. Achievement order uses negotiated achievement command 6 `layout_order`, an expected store revision and an exact receipt ID. Dimensions never enter that command. An old phone retains existing contracts and cannot accept the new order command. An offline desktop can edit local dimensions; synchronized order requires the phone.

Full motion animates grid placement and connected chart drill transitions. Reduced motion uses shorter transitions/fades; Off applies immediate changes. Android also respects disabled system animation scale. Layout persistence runs after gestures, never on animation frames.

## Optional scheduling metadata

Existing duration-only sessions remain valid. Session and block `schedule` fields are optional, using explicit ISO timestamps with seconds and an offset, plus an optional IANA timezone. Optional block `category`, `achievement_id` and `index_topic_id` preserve explicit associations. Example:

```json
"schedule": {
  "planned_start": "2026-10-09T10:00:00+02:00",
  "planned_end": "2026-10-09T10:25:00+02:00",
  "timezone": "Europe/Berlin"
}
```

An end time requires a start time and cannot precede it. The schedule is descriptive; it does not start the timer, change a countdown budget, create DONE credit or infer an endpoint from a duration. The phone stores an immutable `schedule_snapshot` in each report. Punctuality is recorded actual start minus planned start: late is positive, early negative, on time zero. Optional `planned_ms` and `timer_mode` in snapshot activities capture the original active-duration estimate and mode; legacy snapshots without them remain valid and unknown estimates remain unavailable. Missing comparisons are N/A; session and activity punctuality are separate series.

`schedule_version: 1` gates immediate and pending delivery to capable phones. Old phone delivery is blocked with an explanation rather than silently removing metadata. Old read-only viewers receive a compatibility projection without the new optional metadata/contract-6 receipts; the authoritative archive remains intact.

## Calendar evidence

Calendar allocation uses the recorded source timezone, then explicit schedule timezone, then UTC for legacy evidence. Changing the viewer's timezone does not reassign stored days. A day with multiple source zones has distinct aligned axes. Midnight and hour splits use real elapsed UTC intervals, so DST days have 23 or 25 hours; repeated hours contribute to the same clock-hour bucket. Integer allocation conserves measured component totals.

Detailed event evidence locates active, break and paused intervals. Report counters remain the authority for totals. Missing events or contradictory wall-clock jumps retain residual totals as explicitly unlocated evidence on the recorded finish day; they do not invent clock-hour segments. An active checkpoint is a temporary observation with an open actual endpoint, not a new archived report. Archive/checkpoint duplicates share the existing attempt identity and do not double count.

Hour averages include only days with fully located evidence and complete reports. Active checkpoints, partial coverage and unlocated residuals are excluded from that denominator. Absent dates and planned-only dates are unavailable measurements, not fabricated zeroes. Actual zeroes on observed eligible days remain zeroes. Period rankings use focus allocated inside the selected calendar period, rather than an entire cross-midnight session total. DONE remains actual completion evidence only.

Analytics supports All → Year → Month → Monday-based week intersection → seven-day Week → Day and reverse selection. Day shows planned/actual timeline and activity metadata; aggregate charts show grounded focus, punctuality, hourly, weekday, DONE and period summaries. Popups follow selected geometry and dismiss on scrolling. Existing full-report copy/export/import/delete actions remain available; exporting a selected period retains complete matching reports rather than rewriting archived attempts into partial reports.

See [PHASE_2_ANALYTICS.md](PHASE_2_ANALYTICS.md) for current performance definitions, validation and limits; `UPDATE_1.20.0.md` describes that historical release.
