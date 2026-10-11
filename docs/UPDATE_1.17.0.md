# FocusDeck 1.17.0 — Achievements dashboard and icon packs

The desktop shell follows the supplied Achievements reference: the circular mountain logo, stacked sidebar branding, black panels, compact card grid, split selected-card details, colored rings, category/search tools and phone observation status. The shared shell and palette apply across all pages. The sidebar order is **Overview, Achievements, Analytics, Card Builder, Session Builder, Devices, Settings**. No top search/title bar or close/minimize controls were added; KDE/Krohnkite still controls window placement. The 1.16.1 tray lifecycle remains intact.

## Achievements and builders

**Achievements** is a read-only monitor of the phone's active cards. Search/category filters, title/progress/update sorting, grid/list view, selected-card details and a full-detail dialog show actual progress, current/best/gap values, daily objectives, recorded changes, closed streaks and milestones. **Edit in Card Builder** opens the existing editing workflow. Selecting a card in Achievements never changes a builder draft or sends a command.

The screenshot's sample numbers are not application defaults. “On track” is not inferred without a pacing policy: statuses show completed/pending objectives, active streaks, progress in progress, missing targets and initialization state. The summary uses positive observed streaks, closed personal-best segments in the observed month, actual daily-objective results and progress cards with targets. Chart bars show recorded progress deltas or daily activity rather than invented historical streak lengths. Negative progress changes are red; different units remain separate. Missing data is N/A, and source/observation/timezone labels remain visible. Tablet presence is not misrepresented as a report of the tablet's private cache freshness.

**Analytics** retains all former Study History period views, report details, timeline, scoring/reward evidence and exports. **Card Builder** retains protected built-in rules, icon/progress editing, archive/restore/delete, manual activity and explicit daily-target/abstinence actions. GPT paste still redirects to the appropriate builder; parsing and all delivery/receipt rules are unchanged.

## Collapsible sidebar

Use the arrow at the top of the sidebar to collapse or expand it. The choice is stored privately on this desktop. Collapsed mode retains accessible labeled icon buttons and the connection indicator. Narrow windows default to a small sidebar unless a preference was saved; expanding at narrow widths overlays the sidebar so the page remains usable. It adapts to tiling/resizing and respects reduced/off motion.

## Import/export an icon pack

1. In **Settings → Icon library**, choose **Import icons / ZIP pack**, then multi-select files or select a ZIP. Card Builder has the same importer. Both pages accept dropping multiple files or a ZIP.
2. Imported icons remain in a reusable desktop library. Importing does not assign icons or change phone records. In Card Builder, click the desired library icon and explicitly send the card changes.
3. In Settings, tick several icons (or Select all), then **Export selected as ZIP** and choose an export folder. The pack contains separate transparent PNG files, retaining meaningful safe names, and can be imported again. Removing library icons leaves existing card assignments and draft copies intact.

Inputs: PNG/JPEG/WebP/ICO or ZIP containing those formats. SVG is not imported; the bundled vector pack remains available. Up to 100 icons per import/export, 16 MiB per image and 64 MiB total for the native chooser. Drop batches are limited to 16 MiB total by the WebChannel request limit. Library capacity is 500 unique normalized images; content duplicates are reused. The phone still receives normalized PNGs up to 256×256/64 KiB over the existing icon channel. ZIP entries are read without extracting filesystem paths; encrypted archives/symlinks/oversized members are rejected. The batch is validated before the saved library is replaced. Unchanged library bytes are not resent on every observation poll.

Custom image icons use their alpha shape as a theme tint mask on desktop and in the updated Android renderer. Changing the local palette recolors the same saved icon without reimport, receipt changes or a database write on animation frames. Original normalized RGBA colors/alpha remain in storage and exports; no accent is baked into the PNG. Use **transparent PNG or WebP** for clean shapes. An opaque image has an opaque rectangle as its mask, so it cannot produce a cutout without a transparent background. The desktop and phone/tablet retain their own appearance preferences.

## Upgrade

Exit the existing Control Center with **Ctrl+Q** or **tray → Quit FocusDeck**, then:

```bash
unzip -o "$HOME/Downloads/FocusDeck-v1.17.0.zip" -d "$HOME/Projects" &&
cd "$HOME/Projects/FocusDeck" &&
bash scripts/setup-control-center.sh
```

Open FocusDeck Control Center from KDE's launcher. Existing pairing, certificates, archive, drafts, templates and delivery journal remain in place. No uninstall or reset is needed. The desktop UI/icon library works with Android 1.15.0, but phone/tablet **theme tinting requires updating Android from this source**. Build once and update in place using your existing PC signing key:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
# Reuse the same built APK for the tablet; use its current ADB serial:
ADB_SERIAL="TABLET_SERIAL" bash scripts/install-apk.sh android/app/build/outputs/apk/debug/app-debug.apk
```

Android/desktop version 1.17.0; Android versionCode 21 and application ID/signing configuration unchanged. Wire/pairing, icon contract 4, session/report/achievement contracts remain unchanged. GPT input syntax is unchanged; existing project sources still work. Updated source instructions identify the renamed builder pages.

## Verification

- Full desktop suite: 206 collected, 205 passed, one existing restricted-host AF_UNIX skip. Eight icon-library regressions cover batch deduplication, alpha retention, atomic failure, malicious ZIP bounds/links, safe multi-export/reimport, independent drafts/settings and unchanged-library payload suppression. Seven battery-tool fixtures passed.
- Native Qt WebEngine: 102 layout checks at 420–1672 px with expanded/collapsed sidebars and all seven pages plus extra Analytics periods. No horizontal overflow or JavaScript errors. Seventeen workflow assertions passed for original GPT/session editing plus navigation, phone-data details, read-only selection, category/search/list/full detail, saved collapse, multi-import, palette tint and unit isolation.
- JavaScript syntax, whitespace checks and desktop wheel build passed. Installed-wheel render/assets checks are recorded alongside native screenshots under `docs/verification/control-center-1.17/`.
- Android compile was attempted with the available SDK/JDK, but stopped before source compilation because Android Gradle plugin 8.9.2 is unavailable in the offline cache. No APK was built. The small native tint change has not been compiler/device verified here.
- Physical KDE/Wayland/Krohnkite/tray and live phone/tablet synchronization remain unexecuted. No target installation, data/pairing reset or GitHub push.

Approved reference: `docs/design/control-center-achievements-1.17.png`. The demo fixture is explicitly isolated; its sample records never enter real history.
