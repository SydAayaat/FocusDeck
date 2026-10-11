# FocusDeck 1.11.0 — palettes, icons and fluid navigation

Android versionCode 16; application ID remains `com.hax.focusdeck`. Upgrade phone and desktop together for icon command contract 4. Session/report versions 1/2, pairing/wire 1, and achievement commands 1/2/3 stay compatible. No report, timer, scoring, progress or streak measurement rules change.

## What changed

- Eight saved global palettes: Cyan, Monochrome/grayscale, Gold/yellow, Ocean blue, Amethyst, Emerald, Rose, Amber/orange. Choose **Display settings → Global palette → Save**. Palette applies to achievements, dialogs, history, Home, reward controls and timer accents; monochrome also desaturates imported image icons. OLED page backgrounds remain black; numeric text remains readable white. Progress/record/error labels retain their meanings independently of color.
- 37 bundled Lucide vector icons with original source SVGs and complete license notices included in both source and APK. Defaults now include book-open (German), laptop (Custom Compute), ban (Porn Free), trophy (Daily Objectives), dumbbell, heart, guitar and timer. A custom card can choose any bundled name.
- Unified thick rings for streak-only/progress/hybrid cards, larger compact rings, clearer titles, header icons, softly outlined surfaces and overview icons. Rings have a short opening sweep/bloom and completion/record pulse, then settle to static halos. Static streak rings are decorative, not an invented completion percentage. Existing navigation, category filters, long-press/drag/card sizes and inline histories remain.
- Full/Reduced/Off controls still apply; system animator scale zero is observed live. Full has finite entry, press, tab, reorder and ring animation. Reduced removes opening bloom and uses shorter transitions; Off changes immediately. No continuous decorative loop or per-frame database writes.
- Navigation requests the highest supported panel rate **up to 120 Hz**, rather than forcing 60 Hz. Timer/counter has its own saved 30/60/120 Hz preference (default 60); dim timer requests 30. Opening Achievements, Study history, settings or report view during a running timer uses the navigation rate. These are Android window hints, not evidence of actual frame rate.
- History aggregation, achievement preview/statistics, command application, achievement snapshot generation and JSON serialization move off the main thread. Detail/history lists and chart series use lazy containers. Chart/ring animation runs in draw phases with fixed layout, and grid position bookkeeping no longer writes observable state during movement. Icon decoding is asynchronous with a bounded bitmap cache.

## Icon commands

Run desktop setup after merging this release; Pillow is now a desktop dependency used to normalize images. The paired LAN connection transfers bytes to the phone. ADB is used for app installation only; icon transfer does not require USB or ADB access to private app files.

```bash
focusdeck achievements icons
focusdeck achievements icon german --name book-open
focusdeck achievements icon custom_compute --name laptop
focusdeck achievements icon german --file /home/hax/Downloads/german-icon.png
focusdeck achievements icon german --clear
```

Local image import accepts PNG, JPEG and WebP up to 16 MiB/16 megapixels, retains alpha, scales to fit 256×256, and creates a PNG up to 64 KiB. The phone validates bounds and decodes actual PNG bytes before writing/ACK. Images keep their original colors in colored themes; monochrome renders them in grayscale. Bundled vector icons follow the chosen theme. SVGs use the bundled icon names; arbitrary SVG transport is not implemented.

For an exact, repeatable GPT command, save then apply:

```bash
focusdeck achievements icon german --file /home/hax/Downloads/german-icon.png --output /home/hax/Downloads/german-icon-command.json
focusdeck achievements validate /home/hax/Downloads/german-icon-command.json
focusdeck achievements apply /home/hax/Downloads/german-icon-command.json
```

If an ACK is uncertain, apply that **same JSON file** again. An immediate-send shortcut also prints/saves the exact command in the desktop data directory's `icon-commands` folder. Never generate a fresh command for a delivery retry. `--command-id` and `--timestamp` are available, but both and image bytes must match the original receipt; reapplying the saved JSON is simpler.

### JSON contract 4

A complete command contains achievement_version 4, operation icon_set, stable command id, real timezone-aware timestamp at/before now, source, achievement_id and icon. Supported icon objects are:

```json
{"kind":"builtin","name":"laptop"}
```

```json
{"kind":"image","data_base64":"<bounded PNG bytes encoded as base64>"}
```

```json
{"kind":"default"}
```

The base64 object is structural notation; the importer creates real bytes. No PC file path or URL is treated as a phone icon. GPT may change built-in **appearance** without replacing its immutable measurement rules. Icons are per-card, persisted independently of totals; archive/restore retains them. Delete removes the icon assignment and retires receipts, so old commands cannot resurrect it. Latest timestamp wins, command ID breaks ties, and a default reset keeps its ordering receipt. Stale SETs are receipted without overriding newer appearance. Existing IDs with different contents are rejected. New offline writes still reject; negotiated contract-4 capability is required for send/replay. Icon commands do not create daily events, progress or rewards.

## In-place upgrade

Merge the source into the existing PC checkout; retain `.git`, `android/local.properties`, signing/debug keystore and all desktop/app data. Do not replace the project folder wholesale or uninstall the phone app. Build with the signing identity already installed on your phone, then use `adb install -r`.

```bash
cd /home/hax/Projects/FocusDeck
bash scripts/setup-desktop.sh
bash scripts/build-android.sh test
bash scripts/build-android.sh debug
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
focusdeck status
```

The supplied debug APK is installable only when its signer matches the installed app. If Android reports a signer mismatch, rebuild using your existing PC key; do not uninstall/clear data to bypass it.

## Device verification

A source build and desktop tests do not prove S24 Ultra visual fidelity, sustained 120 FPS or live icon synchronization. Run on the actual phone with adaptive motion smoothness enabled. Open all palette choices, check compact/wide/full and large-font layouts, swipe/scroll both history views and inspect ring bloom at entry. Verify Full/Reduced/Off and system animation scale zero. Test bundled/image/default icon changes and uncertain-delivery replay with a disposable custom card; check existing history remains after app restart/in-place upgrade.

For frame profiling, reset before navigating, scroll/swipe/open details on the phone, then capture:

```bash
adb -s R3CX90NC7HR shell dumpsys gfxinfo com.hax.focusdeck reset
adb -s R3CX90NC7HR shell dumpsys gfxinfo com.hax.focusdeck framestats > focusdeck-navigation-frames.txt
adb -s R3CX90NC7HR shell dumpsys display > focusdeck-display.txt
```

A 120 Hz panel has an 8.33 ms frame interval. Inspect Android's jank counters/frame timings and active display mode; do not infer sustained FPS from the requested window hint. Repeat with timer 30/60 and navigation opened while the timer is active. No physical device/emulator was available here.

## Sources

- Android Compose performance phases: https://developer.android.com/develop/ui/compose/performance/phases
- Android adaptive refresh rate: https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate
- Lucide license: https://lucide.dev/license
- Bundled source version: https://github.com/lucide-icons/lucide/tree/0.468.0/icons


## Executed verification (1.11.0)

- Native JVM: **155 tests passed**, zero failures/errors (includes seven new appearance/ordering/deletion/migration/refresh/grayscale regressions).
- Desktop: **155 collected; 154 passed; one host-restricted AF_UNIX control-plane skip**. Includes local PNG/JPEG import, bounded bytes/alpha retention, malformed icon rejection, replayable CLI file output, versioned schemas and real TLS capability-gated exact payload delivery.
- Battery-tool fixtures: **7 passed**; these validate tooling, not phone energy use.
- `testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin lintDebug`: **BUILD SUCCESSFUL**. Six existing instrumented UI tests compile but were not executed.
- Lint: **0 errors, 0 fatal issues, 7 warnings**.
- Production CLI `achievements validate` and full versioned JSON Schema accepted the bundled contract-4 example. Python compilation, shell syntax and Git whitespace checks passed.
- APK signature verification passed. **The new debug signer differs from the previously delivered 1.10.0 APK** after the toolchain reset; build with the existing PC signing key for an in-place upgrade. No phone app was installed/uninstalled and no app/PC history was cleared.
- No emulator/physical phone was available. Actual visual fidelity, sustained frame rate, battery use, in-place phone upgrade and live phone icon decoding/LAN application remain device checks.

APK SHA-256: `d72da29da4ce8c62974db5243d91b9264eec08e9537d2c9f54a7d9fb1ed8431f`.
