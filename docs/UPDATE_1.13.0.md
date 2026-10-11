# FocusDeck 1.13.0 — reference pages, immersion and shared motion

Source-only update. Android/desktop package version 1.13.0; Android versionCode 18, unchanged application ID. Local branch `codex/reference-pages-motion`; no GitHub push or phone installation.

## Implemented pages

- **Home:** uppercase date, two-line white/accent heading, live daily total and DONE count, textured eclipse artwork with illuminated rim/orbit/moon, week/all-time totals separated by a vertical rule, outlined Study History pill, connection indicator, outlined last-session report card. Summary aggregation runs on a background dispatcher.
- **Study history:** underlined Day/Week/Month/Year/All tabs, date navigation, five outlined metric cards, focus/break halo donut, paused/planned information, gradient focus bars, y-axis, peak label, dashed Best/Average lines, icon pill controls and outlined recent-report cards with three inline actions. Empty periods show zero measured focus and a neutral ring. Best is the largest bucket; Average is the mean of buckets with recorded positive focus, not the entire calendar period. All original CSV selections, JSON import, automatic folder saving, deletion/sync notices and per-session actions remain.
- **Session report:** close/title/date header, completion halo and static two-moon orbit, gold/accent summary card with six metrics and overall Focus Ratio, expandable block rows, collapsible event timeline, Save Report and Copy Full Report pills. The first five actual stored events appear initially; Show More displays every remaining event lazily. Save Report exports the complete original JSON through Android's file picker and can be imported in Study history. Send Report and desktop-delivery status remain available. Additional ID, break, completed-focus, overtime, score-weight/classification and reward information is available in More Details; each expanded block retains performance/reward evidence.

The three files under `docs/design/approved-*-gold.png` and `approved-home-eclipse.png` are Hax's supplied concept images, not screenshots of this implementation. Layout, surfaces, typography hierarchy, rings, glyphs and controls follow those references; native rendering is responsive and respects Android text scaling. Exact pixel matching still requires checking the installed app. Example totals, inconsistent sample timings, and incorrect Pace units in a mockup are never copied into real reports. Pace is the recorded numeric index; absent/untimed metrics remain N/A.

## Global display and appearance

Immersive display now hides system bars on Home, focus/reward, achievement overview/detail, History, report, developer preview and Material alerts (settings, sounds, pairing and confirmations). Transient swipe access remains available. Android's external file/audio picker owns its own system UI. Keep-awake still applies only to active/paused sessions. Fullscreen dialogs use edge-to-edge sizing and safe drawing insets.

Gold is the default for new installations/missing theme preferences. Existing saved palette choices remain: select **⋮ → Display settings → Gold / yellow** to use the pictured colors on an existing installation. Other global palettes remain supported, including monochrome. Navigation and all app dialog windows request up to 120 Hz, independently of timer refresh settings; this is not evidence of measured 120 FPS.

Achievement overview/detail rings now contain only the centered icon. Numeric progress remains in the progress panel and accessibility description; the measured arc still reflects the stored total/target. Duplicate icons before card titles stay removed. Static outer glow and the finite 2.4-second detail sparkles are retained.

## Shared motion

| Element | Source behavior |
| --- | --- |
| Achievement rings | Static outer halo; no opening arc sweep on overview |
| Achievement detail | Opening glow and subtle 2.4-second sparkles, then static |
| Session completed | One illumination fade per report entry, then static |
| Study history ring | Smooth 480 ms transitions when measured composition changes |
| Bar charts | 280 ms upward reveal when bucket data changes |
| Cards | Gentle 0.985 press scaling and 280 ms expansion; existing achievement sizing/reorder remains |
| Page navigation | Shared 280 ms fade/slide with stable page keys; dialog entry fade/slide |
| Home eclipse | Extremely slow two-minute artwork drift, draw-only low-frequency sampling |

Full/Reduced/Off and Android animator-scale zero apply throughout. Reduced uses short 120 ms transitions without sparkles or ambient orbit; Off switches immediately. The eclipse stops while backgrounded, while another page/modal/menu obscures Home, and with Reduced/Off. It performs no storage/network writes. This is the only newly authorized continuous decorative motion; other glows settle and remain static.

## Installation on Hax's PC

Overlay the source onto the existing checkout while retaining local signing keys, `android/local.properties`, `.git`, desktop configuration and all app data. With the existing JDK/SDK/adb environment, use exactly:

```bash
cd /home/hax/Projects/FocusDeck
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
```

The script's default **now builds the current source on the PC before `adb install -r`**. It cannot accidentally install an old default-path APK after a build failure. Supplying an explicit APK argument installs that file directly without rebuilding. No uninstall/reset is performed. No APK is supplied in this source-only release.

## Checks actually performed for this update

- Changed Kotlin files: tree-sitter syntax parsing passed. This is a source syntax check, not Android/Kotlin compilation.
- Shell syntax: install/build/package scripts passed `bash -n`.
- Install script smoke with a temporary stub builder and mocked adb: default command builds then installs with `R3CX90NC7HR`; explicit APK skips build; failed build does not install a stale APK. No real phone was contacted.
- Git whitespace checks passed. ZIP entries and executable permissions checked; source includes all three reference images and excludes build outputs/local keys.
- **Not run, at Hax's request:** APK assembly, full unit/instrumented suites, lint, emulator/device visual or FPS checks. Android compilation is also unexecuted. Prior 1.12 test counts/build results remain historical and do not validate 1.13.

Timer, DONE/overtime, rewards, scoring, ledger rules, pairing and wire contracts are unchanged. Session completion still returns Home; open Last Session or a history card to view the report. No real history, preferences on the phone, signing identity, service configuration or remote repository was altered by this workspace change.

## Compilation correction after the PC build report

Hax's actual PC build caught two source errors that the syntax parser could not detect: the Home heading read BoxWithConstraints.maxWidth from inside nested Column scopes, and the chart lacked the drawscope.drawIntoCanvas import. The heading size is now captured directly in the BoxWithConstraints scope; the missing import is present. Those fixes address the posted compiler diagnostics. Android compilation has not been rerun in this workspace. No APK was built.

## Home eclipse correction after the phone screenshots

The original Home Canvas had a radius determined by width inside a short text-height box: the planet was clipped flat above/below and the moon could sit outside the viewport. The radial gradient also made a uniformly shaded disc rather than the approved dimensional eclipse.

Home now uses `drawable-nodpi/home_eclipse_art.png`, a 1536×1024 cinematic textured planet, directional crescent, tilted orbital ellipse and shaded upper-right moon. Artwork is fit completely behind the combined date/heading/focus section, with a responsive minimum height and extra motion inset; the text-height clipping modifier is removed. A luminance-to-accent color matrix preserves OLED black and follows all eight saved palettes, including Gold and monochrome. Decode occurs off the UI thread and the bitmap is retained between Home visits.

The slow Home animation is now a ≤1.5 dp drift with a two-minute cycle, rather than rotating a moon offscreen. It changes only the artwork layer, stops when obscured/backgrounded, and stays static in Reduced/Off. No timer, ledger or history changes. The earlier two compilation corrections are included in the full ZIP and incremental patch.

Checks: inspected the actual phone screenshots and the original approved Home image; inspected the real opaque RGB artwork and its dimensions; source syntax, image-fit/motion bounds at six portrait/wide/large-text viewport sizes, and ZIP content checks passed. No APK build, broad test suite, Android compilation rerun or device execution was performed for this correction. Actual installation and pixel matching still require checking on the phone.

## Session Complete orbit correction

Hax's additional close-up reference is retained as `docs/design/approved-session-orbit-detail.png`. The report hero now uses `drawable-nodpi/session_orbit_art.png`: two softly shaded moons, faint diagonally tilted elliptical arcs and restrained atmospheric light on OLED black. It replaces the flat Canvas oval and tiny gradient dots. The hero is 260 × 174 dp, with a centered native 128 dp HaloRing; the luminous ring diameter is about 113 dp including stroke. The checkmark, stopped-session state, report title and DONE/timeout totals remain native and reflect the actual report. No example numbers or lettering are embedded in the art.

Home and report artwork share an off-thread bitmap decoder/cache and luminance-to-accent filter. All saved palettes apply to both. Home's existing fit/inset and Full-only ambient drift are preserved; the report moons/orbits remain static. Completed reports retain the existing one-time 900 ms ring illumination in Full mode, then settle to the static glow. Reduced/Off and Android animator scale zero retain their existing behavior.

Focused checks: inspected the generated background against the cropped reference; changed Kotlin syntax parsing and Git whitespace checks passed; artwork fit/center and native ring bounds checked; packaged source/patch assets checked byte-for-byte. These are source checks, not Android compilation or a phone screenshot. No APK build, broad tests, Android compilation rerun or device execution was performed. The combined orbit patch includes the earlier Home artwork and compiler corrections.

## Achievement detail lower-section correction

Hax supplied a regenerated hybrid detail reference, then explicitly limited this change to the cropped area beginning at Current / Best / Record gap / Average. The existing upper navigation, hero ring, icon, glow/sparkle animation, title, description and cumulative progress panel are unchanged byte-for-byte. Both references are retained under `docs/design/approved-achievement-detail-*.png`.

The lower section now follows the crop: separated four-column statistics (two-column fallback at narrow widths/large text), outlined policy information with an info glyph, compact chronological gradient bars, dashed Best/Average labels inside the plot, white selected-bar outline and aligned triangle marker, a two-column selected-streak summary, Today/date/introduction and an outlined list of expandable icon/value rows. History bars fit the available width up to 32 entries; longer history scrolls horizontally and remains lazy. The latest completed streak is selected on entry. Bar reveals, press/expand and selection transitions respect Full/Reduced/Off/system scale. Detailed history stays inline, following the earlier written requirement; its outlined heading is followed by lazy history entries with retained expanded evidence. Progress history retains each stored update and historical target.

Hybrid cards show both systems; streak-only cards omit cumulative metrics/history; progress-only cards omit the four streak statistics, streak chart/summary and daily streak completion. Abstinence keeps elapsed-time statistics, hourly chart scale for short histories and exact start/end timestamps, without invented daily objectives. All use the same lower-section surfaces, theme colors and spacing. No stored values or measurement definitions were changed.

Progress in a selected streak is the net recorded delta grouped by stored unit; unit changes are not added together. Missing updates show N/A. Daily average divides by counted streak days. Day-contract evidence uses real finalized statuses, distinguishes all-calendar-day coverage from target-day-only coverage and shows N/A when there is no target evidence. The explanatory policy matches each actual measurement kind; mockup example numbers, dates and unsupported objectives are not seeded. Focus rows are measured global day totals, not project-attributed focus. Read-only history aggregation runs off the UI thread.

Focused verification: changed source parses as Kotlin syntax; Git whitespace and package integrity/content checks passed; the complete overview/header/ring/progress-panel implementation was compared byte-for-byte with the preceding source; chart scaling/fit and capability branches were inspected. The existing selected-streak instrumented assertion was updated to its retained accessibility description. No new test suite, APK build, Android compilation or phone/emulator execution was performed. Actual visual/touch/font-scale verification remains pending on the phone. The cumulative detail patch also includes both artwork corrections and the earlier compiler fixes.
