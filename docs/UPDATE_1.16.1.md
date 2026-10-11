# Desktop 1.16.1 — close to system tray

Closing the Control Center window (including the compositor's close shortcut) hides it while the same process and interface remain alive. The gold FocusDeck icon stays in KDE's system tray. Click it, choose **Open FocusDeck**, or launch the app again to restore the existing window. The tray menu also has **Hide window** and **Quit FocusDeck**. **Ctrl+Q** explicitly quits the GUI. Quitting the GUI does not stop the companion service or phone timer.

The same window retains its current page, unsent draft and compositor-managed size/maximized state across hiding. Hidden WebEngine content uses the existing visibility-based polling pause. The GUI is single-instance per FocusDeck data directory: subsequent launches send an owner-private atomic activation request to the lock holder, without introducing a network listener. Startup requests are retained until the running GUI handles them. No auto-start-at-login behavior is introduced.

Qt's system tray supports Linux StatusNotifierItem hosts such as KDE Plasma. If no usable tray exists when the window closes, the GUI exits normally so there is no unreachable hidden app. Demo mode has no tray and closes normally. Wayland decides whether an activation request may take keyboard focus; reopening still makes the window visible. No custom title-bar controls were added.

## Update

First exit the currently running Control Center with **Ctrl+Q** (or **Quit FocusDeck** if it already has a tray). Then:

```bash
unzip -o "$HOME/Downloads/FocusDeck-v1.16.1.zip" -d "$HOME/Projects" &&
cd "$HOME/Projects/FocusDeck" &&
bash scripts/setup-control-center.sh
```

Open **FocusDeck Control Center** from KDE's launcher. Android stays at 1.15.0; no APK, re-pairing, clearing data or replacement signing key is needed. GPT card/session input formats are unchanged; keep the 1.16.0 GPT project pack.

## Verification

- Eleven new regressions passed: real Qt widget close/hide/restore, maximized/minimized states, tray actions, explicit quit, no-tray/demo fallback, single-instance lock handover, startup activation, owner-private request permissions, stale/corrupt/symlink request handling. Tray-host availability is simulated for offscreen widget tests; this is not a physical tray test.
- Full desktop suite: 198 collected, 197 passed, one existing restricted-host AF_UNIX skip. Seven battery-tool fixture tests passed.
- JavaScript syntax, Git whitespace checks and Python wheel build/install passed. The installed 1.16.1 wheel rendered the bundled Control Center through native offscreen Qt WebEngine and exited through the capture timer.
- Records are in `docs/verification/control-center-tray/`. The offscreen Chromium no-sandbox/disable-gpu flags are test-only and are not production defaults.
- Physical KDE/Wayland/Krohnkite system tray, task-switcher visibility and live phone/tablet synchronization remain unexecuted here. No Android build, target installation, data/pairing reset or remote GitHub operation was performed.

Implementation: `gui_instance.py`, `tray_window.py` and `desktop_app.py`. Qt reference: <https://doc.qt.io/qtforpython-6/PySide6/QtWidgets/QSystemTrayIcon.html>.
