# Desktop Control Center 1.16.0

Android remains 1.15.0 (versionCode 20). This release adds the local desktop GUI and accepts the already implemented phone Skip/End commands in the desktop companion. It does not require a new APK, re-pairing, app uninstall, signing-key changes or phone data clearing.

## Install on Hax’s Ubuntu 26.04 LTS KDE Plasma/Wayland desktop

The ZIP contains the complete FocusDeck source in a top-level `FocusDeck` folder. Extract over the existing checkout while preserving its `.git`, local SDK properties, keystores and existing application/configuration/archive directories:

```bash
unzip -o "$HOME/Downloads/FocusDeck-v1.16.0.zip" -d "$HOME/Projects" &&
cd "$HOME/Projects/FocusDeck" &&
bash scripts/setup-control-center.sh
```

Open **FocusDeck Control Center** from KDE’s application launcher. The installer reuses the existing FocusDeck virtual environment, installs the optional Qt GUI dependencies, creates a KDE launcher, and restarts an already-running companion so its new desktop commands are loaded. It does not initialize a new companion, change pairing tokens/certificates, or build/install Android. If this is a fresh PC rather than Hax’s existing working installation, first run the existing `scripts/setup-desktop.sh` once to initialize the companion.

The window starts maximized without a custom title bar or minimize/close controls. It is a normal frameless Qt window, not exclusive fullscreen, so Krohnkite/KWin controls placement and resizing. The layout adapts from 420 px through desktop widths; the sidebar becomes icon-only when narrow and editor columns stack. Use normal compositor shortcuts to move/resize/close it; Ctrl+Q also closes the panel. Optional `focusdeck-control-center --windowed --decorated` enables a conventional frame. Native Wayland is preferred when WAYLAND_DISPLAY is available; explicit QT_QPA_PLATFORM settings are respected.

## Implemented workflows

- Six approved screens: Overview, Achievement Cards, Session Builder, Study History, Devices, Settings. Bundled eclipse artwork, black/charcoal panels, gold accents, bundled Lucide vectors, static icon-ring glow, responsive grids, reduced/off motion and alternate palettes.
- A GPT paste box in Overview/Cards/Session Builder. A documented `FOCUSDECK CARD`/`FOCUSDECK SESSION` text block or existing valid JSON creates a local editable draft and redirects to its editor. No API key, arbitrary-prose AI interpretation, automatic send or automatic start. See GPT_CONTROL_CENTER_INPUT.md.
- New streak/progress/hybrid cards; custom definition editing; protected built-in measurement rules; category/kind guards; explicit initial/updated progress; private local drafts; archive/restore/confirmed delete; manual activity recording; explicit abstinence start/break timestamps; today’s manual activity objective preserving other scheduled objectives.
- Drag/drop or native file chooser for PNG/JPEG/WebP/ICO icons, converted off-thread to a bounded PNG for the existing LAN protocol. Bundled icons and default reset work without ADB. SVG files are not custom import inputs; the bundled vector set remains available. Upsert→icon batches are sequential and individually acknowledged.
- Session block forms, timed focus/break and untimed stopwatch, reorder by arrows or drag, duplicate with a fresh block ID, remove, instructions, simple completed/Focus Ratio text rewards, advanced policy inspection/editing, and reusable templates. Imported comparison/weight/reward/session fields remain intact; invalid edits are rejected rather than silently downgraded.
- Send prepares the plan on the phone. Start prepared session sends Resume rather than loading the plan again. Current-session observation and Pause/Resume/DONE, amber Skip, red End block/session, reward acknowledgment/continue. End actions confirm intent. No +5 minutes GUI action. Session/block guards reject changed observed state at the desktop boundary; phone timer/policy validation remains authoritative.
- Day/Week/Month/Year/All saved history, actual focus/break charts, report/block outcomes, timeline and full Markdown clipboard report. Complete JSON and session/block/event/interval CSV exports use the existing archive/tombstone handling.
- Connected and saved offline device entries, explicit phone/viewer roles, observed last-seen time, local naming and removal of offline saved entries (not pairing reset), and separately requested phone/tablet pairing credentials with native clipboard support. Signal strength is not fabricated. Devices reconnect from their mobile app; a desktop button does not pretend to initiate that connection.
- Desktop palette/motion/density/artwork/default-duration preferences. These do not change phone preferences. System startup/tray, notifications/sounds, GPU switches and cloud/telemetry toggles from illustrative settings are not represented as working features. All exposed settings have an implementation.

## Data and authority

The GUI communicates through the existing owner-only Unix control socket. No web server or LAN listener is added. Qt WebChannel connects bundled local HTML/JS to a restricted native bridge; external requests/navigation are blocked. Normal operation has no demo data. `--demo` is explicitly labeled and disables phone writes/pairing/export. Private drafts/settings/templates/device names/delivery journal live under the existing data directory’s `control-center` subfolder. They never enter phone history or achievement measurements. A file lock prevents simultaneous GUI instances from racing these files.

The phone alone runs timers and owns activity. The UI shows phone-observed time with its timestamp, never an independently advancing desktop timer. Achievements/full-session caches are scoped to the paired authority. Existing configuration edits omit measured progress unless an explicit update is selected. Card configuration hashes reject stale editing. The delivery journal saves exact operations before transport, skips acknowledged batch steps, preserves receipt IDs for uncertain retries and reconciles session delivery by its original execution ID. New template deliveries get fresh session IDs.

Physical PC/phone/LAN/Krohnkite verification remains needed. Remote actions compare the latest desktop-observed session/block; this is not a distributed lock against simultaneous phone actions. Do not claim measured FPS or exact physical pixel matching from headless checks.

## Executed verification

- Desktop full suite: 187 collected, 186 passed, one existing AF_UNIX host-permission skip. Includes 20 new parser/data/replay/real-TLS regressions.
- Battery measurement-tool fixtures: seven passed; no battery-life claim.
- Native PySide6 6.12.0/Qt WebEngine loaded and rendered the application offscreen. All six screens at 1672/1440/1050/800/600/420 px: 36 base screen layouts plus 18 Month/Year/All history variants (54 checks), no horizontal document overflow or JavaScript errors. Actual WebChannel paste→card/session redirects, live card preview editing, unique duplicate block IDs and template saving passed in isolated demo mode.
- Python compilation, JavaScript syntax, shell syntax and Git whitespace checks passed. Wheel creation succeeded; asset/entry-point checks are recorded with packaging.
- Headless host needed privately extracted libXtst/libxkbfile and test-only offscreen/no-sandbox flags. These are not production application defaults or shipped workarounds. Runtime graphics/DBus restrictions were logged.
- No Android changes/APK build, target installation, service/pairing/data mutation, GitHub push or physical Wayland/KWin execution was performed here.
