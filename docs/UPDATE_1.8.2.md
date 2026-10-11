# FocusDeck 1.8.2 — stopwatch dial

Continues the existing local source. Android and desktop are 1.8.2; Android versionCode is 12. Protocols, application ID, signing configuration, pairing and history formats are unchanged.

## Interface

Stopwatch now uses the exact same FocusScreen layout as countdown: session and task titles, responsive dial sizing, footer rows, left DONE/right PAUSE or RESUME, START when ready, history/LAN/menu header and dim desk view. The separate stopwatch page was replaced with a dial composable inside this shared layout. Countdown rendering stays unchanged.

The stopwatch dial follows [Hax's supplied reference](design/stopwatch-reference.png): dark outer track, white arc with endpoint dot and 60 inner tick marks, with longer marks every five minutes. The numeric clock remains HH:MM:SS and counts active focus time upward.

The outer arc moves **once per completed active minute**, six degrees per minute. At 15 minutes it reaches the right, at 30 the bottom, at 45 the left; at 60 it returns to the top for the next cycle. Elapsed text continues through 01:00:00 and beyond. Pause freezes the clock and marker. No continuous animation or new clock polling was added.

This is an hour scale, not a target or completion threshold. DONE remains the only completion authority. Footer progress measures DONE blocks and is labeled `% DONE`; estimated finish remains N/A whenever the current or a future block is stopwatch. The extension menu item is disabled for stopwatch, matching the engine's existing rejection of extension.

## Changed files

- `MainActivity.kt`: one shared layout; stopwatch dial branch; untimed footer and extension guard.
- `StopwatchScreen.kt`: reference dial, inner ticks, minute/hour position and count-up text. No separate page or button layout.
- `StopwatchTest.kt`: supplied regressions for minute/hour boundary and long-pause/resume behavior.
- Native/desktop release metadata, README, architecture/status/stopwatch/upgrade/verification docs and local development instructions.
- `docs/design/stopwatch-reference.png`: the supplied reference, preserved without image editing.

Timer accounting, rewards, scoring, JSON schema, reports, exports and data retention are unchanged from 1.8.1. All work remains local; no GitHub update occurred.

## Verification

Android unit tests and debug build were attempted and stopped before Gradle execution because this workspace lacks the Android SDK. **91 native JVM tests are supplied (18 in StopwatchTest), none executed here.** Source checks do not establish Compose compilation or a visual match on the phone.

Desktop and schema checks are recorded in [VERIFICATION.md](VERIFICATION.md). No phone installation or live battery measurement occurred. Follow [UPDATE_1.8.md](UPDATE_1.8.md) using the existing signing key and preserving app/PC data. On the S24 Ultra, check portrait/landscape, large font, all button states, inner ticks, quarter-hour positions, hourly wrap, long pause/resume and dim desk view.
