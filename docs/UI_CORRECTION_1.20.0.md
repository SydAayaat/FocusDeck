# FocusDeck 1.20.0 — UI source correction

This is a source correction of the supplied 1.20.0 archive, not a new release. Version identifiers, application ID, artwork, signing configuration, transport and authoritative phone records remain unchanged.

## Reference decisions and concrete source mismatches

The six supplied images and the supplied Daily Timeline/Analytics specification govern composition. Mockup measurements are not application data. Existing phone navigation/button positions override mockup navigation. The Universal Dynamic Card Resizing supplement remains applicable to ordinary content panels and KPI metrics.

| Source mismatch | Correction |
| --- | --- |
| Android converted twelve-unit spans to a coarse one/two/four-column grid; fractional spans lost their intended proportions. | Use twelve actual grid tracks, responsive minimum widths and one-track resize snapping. Small screens retain usable card widths; compact achievements support two or three columns where space permits. |
| Seven separate Analytics KPI boxes wrapped unevenly and occupied excessive vertical space. | One visual summary panel with four metrics followed by three on Android; a horizontal desktop strip where space permits. Individual metrics retain independent resize handles. |
| Android aggregate charts were almost entirely full-width and required fixed-width internal scrolling, including 24-hour charts. | Full-width primary focus chart on phones; paired hourly/punctuality, summary/distribution, weekday/top panels; full-width completion/history. Wide native layouts retain three primary chart columns. Normal seven/24-point charts fit their card; very dense series can scroll. |
| Achievement overview exposed three metrics and compact tiles began with the title rather than the ring. | Four Overview metrics, ring-first compact tiles, topic/progress count and full-width bar, value-first current/best/gap facts. Wide/full tile alternatives remain. |
| Desktop made each achievement summary metric a separate bordered panel, changed three-column detail facts to two, and stacked chart/milestone panels. | One summary strip; three facts per row where readable; paired chart/milestone panels. Catalog/detail defaults are seven/five grid tracks, stacked at compact widths. |
| Desktop mixed fixed sections with cards carrying CSS order 1000, potentially placing detail sections before the hero/progress panel. | Preserve the DOM slots of fixed headings/sections and order cards within their eligible slots. The summary has its own grid wrapper. |
| Width-only resize captured a measured height and unintentionally froze vertical content. | Optional local `heightSized` flag distinguishes width-only changes from intentional height changes. Existing saved sizes without this field remain explicit heights. |
| Expanded timeline lanes were short and lacked useful native segment labels. | Taller expanded lanes and recorded activity names where space allows; desktop shows recorded segment timing. Compact Home tracks remain compact and popups remain overlays. |
| Analytics date actions occupied two extra native header rows. | Put date selection/current-period actions on one row without moving the close button or scope navigation. |

The mobile Home hero, eclipse asset and navigation are untouched. Its existing shared timeline gets the approved compact heading/subtitle treatment. Android achievement detail hero/ring/description/progress composition is untouched. All real archive, Index, streak, reward, schedule and timer calculations remain their existing implementations.

## Saved layouts

No saved layout is silently deleted, no preferences namespace is changed and no remote achievement ordering is reset. Existing custom sizes can therefore continue to override these corrected defaults. To see the approved defaults, use **Edit Layout → Reset Layout** for the affected page/template and form factor. Day and aggregate Analytics remain separate templates. Reset Layout changes local presentation; Reset Order is a separate phone-synchronized action.

The original individual KPI identifiers are reused inside summary strips. New width-only changes save `heightSized: false`; vertical/corner resizing saves `true`. The local desktop validator accepts the optional boolean and rejects malformed values. Chart/metric content can wrap/reflow or scroll instead of being discarded.

## Changed files

- Android: `DashboardLayout.kt`, `AnalyticsUi.kt`, `AchievementCards.kt`, `AchievementScreen.kt`, `StudyHistoryScreen.kt`.
- Desktop: `control_ui/layout.js`, `analytics.js`, `analytics.css`, `app.js`, `layouts.py`.
- Focused checks: `scripts/check-ui-layout.js`, `desktop/tests/test_calendar_analytics.py`.
- Carry-forward test fix: `AchievementProgressTest.kt` now checks `supported_schedule_version`, matching actual hello output. The existing Index category guard and contracts 1–6 assertion are retained.
- Documentation/references: this file and `docs/design/ui-correction-1.20.0/`.

## Executed verification

- `PYTHONPATH=desktop/src python3 -m unittest discover -s desktop/tests -p test_calendar_analytics.py -q`: **14 tests passed**. Includes calendar/DST/punctuality allocation and durable layout validation; new sizing flag has a targeted validation regression.
- `node scripts/check-ui-layout.js`: **16 checks passed**. Runs actual JavaScript layout/chart functions and resize handler paths in an isolated lightweight harness. Covers default responsive spans, three-column facts/cards, seven-metric strip widths, fixed section ordering, legacy heights, width-only/vertical/cancel behavior, 24-hour chart sizing and missing-versus-zero evidence.
- `node --check` on changed `app.js`, `analytics.js`, `layout.js`: passed.
- Edited Kotlin delimiters were checked for balance. **This is not Kotlin compilation.**

No APK, Android compile/unit-test/lint run, physical device test, Qt render or pixel-level comparison was performed. A headless Chromium attempt could not obtain a valid executable; the JavaScript harness has no renderer and is not proof of final pixel matching. No broad test suite was repeated. The attached design images were inspected; no separate current installed phone screenshots were available in this request, so the listed problems are concrete source/reference mismatches, not measured screenshots of the user's installed build.

## Local build/install

Quit the old Control Center via its tray menu before opening the updated desktop process (closing its window leaves it running).

```bash
unzip -o "$HOME/Downloads/FocusDeck-v1.20.0-ui-corrected-source.zip" -d "$HOME/Projects"
cd "$HOME/Projects/FocusDeck"
bash scripts/setup-control-center.sh

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
```

The install script builds the current debug source and uses `adb install -r`. Use your actual selected ADB serial if it differs. It preserves app data provided the existing signing key remains available on your PC. Do not uninstall, clear data, forget devices or regenerate credentials as part of this update.

If building without installing, run `bash scripts/build-android.sh debug`. Focused checks above can be rerun independently; there is no need to chain the entire Android test suite in front of desktop installation.

## Still requires local verification

1. Android compilation and appearance on phone/tablet, especially larger font settings and landscape.
2. Native touch resize versus scrolling, dedicated reorder handles, and saved sizes after restart/rotation.
3. Desktop Qt/KDE window tiling: default and custom dimensions, changing catalog width, metric-strip reflow, chart hover anchoring and internal scrolling.
4. Reset Layout on each template exposes corrected defaults while custom layouts and phone-owned order otherwise remain preserved.
5. FULL/REDUCED/OFF motion on actual devices and real synchronized session/Index reports.

Do not regard passing non-rendered checks as visual acceptance. If a remaining mismatch is visible after resetting the affected layout, compare an installed screenshot with the bundled reference before changing the approved header/artwork/navigation.
