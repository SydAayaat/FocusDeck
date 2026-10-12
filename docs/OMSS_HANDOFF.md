# OMSS Home, Timeline, History and Session Summary · 12 October 2026

Continues the existing local `codex/omss-native-ui` implementation and uncommitted work. Android remains 1.21.0/code 25; desktop remains unchanged. The supplied [handoff](design/omss/handoff/implementation-handoff.md), [refinements](design/omss/handoff/approved-refinements.md) and five original PNGs are retained under `docs/design/omss/handoff/`. Written refinements override illustrative screenshot numbers and obsolete interactions. Hax's current explicit physical-device request authorizes isolated debug APK assembly/deployment, superseding older source-only restrictions for that variant only.

## Delivered behavior and source

| Component | Files | Result |
| --- | --- | --- |
| Home | `HomeScreen.kt`, `DashboardUi.kt`, `MainActivity.kt` | Date/connection/menu header replaces Home's four upper navigation icons. Wide Analytics and adjacent trophy shortcuts; default Current Session above Last Session; View All opens the dedicated History feed. Active recording pulse only in FULL while visible/resumed; paused amber indicator and truthful idle state. Open follows existing engine navigation. Existing `upcoming-session` layout ID retains local dimensions and custom ordering. Measured focus includes skipped work, excludes active breaks and uses monotonic accounting. |
| Timing/data presentation | `ActivityPresentation.kt` | Shared source-zone clock formatting, immutable record-based status and duration comparisons. New UI accuracy is `100 × min(estimated, actual) / max(estimated, actual)`; zero/zero is 100%, one zero is 0%, unknown/negative/provisional data is N/A. Existing Analytics calibration, Pace, BeatScore, rewards and scoring contracts are untouched. |
| Expanded day | `ExpandedTimelineScreen.kt`, `CompactTimeline.kt` | Lazy 30-minute bands across the source day's real 23/24/25 elapsed hours. Repeated DST hours include UTC offset labels. Actual start anchors take precedence over descriptive plans. Stable event identities, collapsed cards by default, measured expansion/reflow, original timestamps, separate unlocated/continued evidence, exact associated archived-attempt navigation. |
| Event details | `ExpandedTimelineScreen.kt`, `ActivityUi.kt` | Start/end/elapsed/estimate, focus/pause/count, signed start/end deviation, compact delay segments with accessible segment values, finalized accuracy and recorded execution/quality evidence. Delay attribution requires adjacent reliable work in the same attempt; missing/overlapping/independent schedules stay N/A. Completed, live, paused, skipped, stopped, break and planned states remain distinct. |
| History | `ActivityHistoryScreen.kt`, `ActivityPresentation.kt` | Lazy chronological feed from actual reports, timeline sequences, retained card-command receipts, achievement/progress records and Index events. Multiple category selections, search, inclusive source-date range picker and both sort orders. Equal timestamps use recorded event sequence, with the final session row first. No reconstructed GPT generation or invented historical outcomes. Archive Actions opens existing guarded export/import/folder/deletion functionality. |
| Summary | `ReportScreen.kt`, `StudyHistoryScreen.kt`, `MainActivity.kt` | Compact status beside title replaces the decorative orbit. Six original metrics and small Overall Focus Ratio gauge remain. Separate finalized whole-session duration accuracy; partial, mixed/unknown and live plans stay N/A. Distinct DONE/skip/stopped/unstarted evidence; fully traversed skips keep the existing Finished with skips label. All recorded events, Show More, More Details, measured footer, Save/Copy and supported Send actions remain. Export and copy recheck canonical availability; serialization/report generation run off the UI thread. |
| Materials/motion | `ActivityUi.kt`, `ReferenceUi.kt`, `CompactTimeline.kt` | Reused opaque OMSS cards/tracks/gauges and finite motion. Shadows follow icon alpha directly, without added square icon containers. Native Search/Sort/Play/Skip/Stop glyphs. FULL track endpoints rotate/elongate/translate from captured compact geometry; REDUCED uses a short fade; OFF is immediate. Card-origin entrance and retained overlay state connect summaries to their event and preserve Back/scroll/expansion. No completed-page decorative loop. |
| Isolated QA | `VisualTestDataProvider.kt`, `UiQaActivity.kt`, `scripts/capture-locked-ui.py`, `scripts/build-android.sh` | Reused `.visualtest`, adding History/Timeline/Summary and active/paused/mixed fixtures. No new QA framework, production repository, network permission or service. Build script now accepts `visualtest`. Raw screenshot metadata records real resolution/density, orientation, font scale, scene, fixture and motion. |

Executive Telemetry Insights, finalized Analytics layouts, achievement definitions/receipts/order, timer execution, transport, archive formats, tombstones and pairing are unchanged by this delivery. Existing shared dashboard resizing is retained; event cards add ordinary disclosure, not independent drag/resize gestures.

## Validation and physical evidence

USB ADB identified **Samsung SM-S928B / R3CX90NC7HR**, status **device**. The connected tablet was not used. All installs selected that serial and only `com.hax.focusdeck.visualtest` / `.visualtest.test` packages. APK badging and physical manifest tests confirm isolated identity, plain Application, removed production launcher/services and no INTERNET permission.

Measured display: physical 1440×3120 pixels; physical density 600, existing override 560 (3.5 pixels/dp, approximately 411×891 dp before insets). Landscape captures are 3120×1440. Test font scale 1.3 is composition-local; global phone display/font settings were not changed. Screenshots were obtained with `adb -s R3CX90NC7HR exec-out screencap -p`. Raw captures were reviewed against the supplied originals; comparison artifacts normalize width and keep raw originals, without pretending mockup numbers or generated-page aspect ratios are real device data.

- Production/debug Kotlin, isolated app and instrumented-test compilation succeeded. Only the isolated debug APKs were assembled/installed; no production or release APK was assembled for this task.
- **33 focused production JVM tests passed**: ActivityPresentation 9, CalendarAnalytics 8, PerformanceAnalytics 10, SkipEnd 6. **Five visual fixture JVM tests passed.** Regression evidence includes unknown versus zero, finalized versus live accuracy, independent original calibration/score math, DONE-only credit, retained skipped effort, break exclusion, immutable outcomes, real receipt redaction, multi-filter/source-date sorting, equal-time sequence, actual anchors and DST band counts.
- **21 physical instrumented UI tests passed** across OmssHandoffUiTest, VisualIsolationUiTest and LockedDashboardUiTest. These cover isolation, Home/History/timeline/summary routing, report identity and actions, true empty filtering, event expansion under all three motion modes, existing shared Analytics rows/navigation, Home empty state and exact archived copy/deletion handling against disposable in-memory UI data. Two viewport assumptions in the test harness were corrected: explicitly scroll summary accuracy into view, and bring the shared Month/Year plate into composition after retaining aggregate scroll position.
- Lint: **zero errors, 23 warnings**. The final date-flow initialization lint errors were fixed. Warnings include existing dependency/configuration/style notices and the intentionally retained, now unused `session_orbit_art` resource. The production artwork was not deleted.
- Physical captures include Home idle/active/paused, completed and mixed/stopped summaries with lower report controls, collapsed/expanded timelines, History/empty filters, unchanged Achievements, and large-font landscape Summary/History/Timeline with lower viewports. ADB touch navigation directly exercised compact → expanded → summary → Back and History scrolling under FULL/REDUCED/OFF.
- Local evidence and gallery: `artifacts/uiqa/omss-handoff/`. Raw screenshots/logs/checksum baselines remain local and are ignored by Git.

The production APK and **all ten readable private files** matched their pre-test checksums byte for byte. Production was not installed, launched, uninstalled, cleared, re-paired or populated with fixtures. No emulator, GitHub push, remote CI, release build, service setup or APK upload was performed.

## Bounded final motion sample

The first aggregate section of each physical `dumpsys gfxinfo … framestats` log is used below; per-window sections are not double counted. Each mode sampled one warm compact-timeline/event-expansion/summary/back/history/scroll sequence. These are observed frame durations and deadline misses, including inspection/capture overhead, not a performance acceptance threshold or controlled comparison between preferences.

| Motion | Frames | Missed deadlines | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| FULL | 425 | 9.41% | 12 ms | 18 ms | 29 ms | 121 ms |
| REDUCED | 341 | 12.02% | 12 ms | 20 ms | 32 ms | 150 ms |
| OFF | 320 | 10.00% | 12 ms | 18 ms | 32 ms | 150 ms |

The final FULL Session Summary recorded zero additional frames across two successive two-second idle windows after entry settled. This supports removal of its continuous decorative workload within that observation window. Navigation still has tail frames and measured deadline misses; no sustained-FPS or battery-saving claim is made. Raw logs and `summary-idle.json` remain local.

## Limits and remaining checks

Historical GPT session generation/import outcomes and device/synchronization changes have no durable comprehensive activity log in the current model. Their category remains empty where evidence is absent. A card upsert receipt is explicitly a processed command, not proof that a card was newly generated or changed; its exact retained payload is available. Removed IDs/retired receipts are omitted. Progress/achievement entries without stored source-zone metadata use the phone display zone; report and Index source zones are retained. No historical data was backfilled.

Whole-session duration accuracy is conservative: it requires a complete known countdown plan with all blocks DONE; partial or mixed stopwatch sessions retain N/A rather than comparing their partial active time to a full estimate. Existing countdown-only competition scope is still displayed independently. Event quality shows the persisted criteria count and recorded quality state, not an invented per-criterion pass count.

Dense 30-minute intervals grow vertically to fit their evidence, so geometric distance is intentionally adaptive; labels and event timestamps remain exact. Continued or unlocated activity is disclosed separately rather than given an invented start in the selected day. Narrow delay segments expose their values through accessibility. Summary continuity is a brief origin-based entrance with preserved underlying event/scroll state; it is not a cross-window Compose SharedTransitionLayout.

The screenshot references are generated full pages with different dimensions and illustrative data. This is a native implementation with real scrollable content, not a pixel-identical screenshot replacement. Bounded `gfxinfo` samples include accessibility inspection, window creation and screencapture overhead; they are not a sustained 120 Hz benchmark. System animator scale zero is honored by the existing provider but was not changed/exercised on the phone. Physical LAN/tablet synchronization, production timer interaction, destructive production tests and real SAF provider save/copy delivery were not exercised. Report buttons/callbacks and read-only guards were checked; no real production record was deleted or exported by the tests. Broad desktop/full Android suites were intentionally not rerun for this Android UI change.

## Reproduce locally

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
adb devices -l # Select the currently connected physical S24 Ultra in device status.
bash scripts/build-android.sh visualtest
adb -s R3CX90NC7HR install -r android/app/build/outputs/apk/visualtest/app-visualtest.apk
python3 scripts/capture-locked-ui.py --serial R3CX90NC7HR --no-build \
  --screens home history timeline summary --lower --out artifacts/uiqa/omss-review
```

Use the verified current serial if it changes. `--fixture ACTIVE`, `PAUSED`, `MIXED`, `EMPTY` and `--motion FULL|REDUCED|OFF` reuse the existing fixture boundary. Do not use the production install helper or reset commands for visual QA.
