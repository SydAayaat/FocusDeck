# Troubleshooting

| Symptom | Action |
| --- | --- |
| `focusdeck: command not found` | Add `~/.local/bin` to PATH, or run `~/.local/bin/focusdeck` |
| CLI cannot reach daemon | `systemctl --user status focusdeck.service`; `journalctl --user -u focusdeck.service -n 50` |
| Service bind fails after DHCP change | Stop service, `focusdeck-server --init --host auto`, restart; update phone pairing host |
| CLI status succeeds but phone is offline | Check PC IP, TCP 8765 firewall allowance, same LAN, and guest-network isolation |
| Certificate changed | Confirm which desktop you are reaching; run `focusdeck pairing` locally and re-pair with the new fingerprint; never disable certificate checking |
| Pairing token rejected | Re-enter pairing JSON from the daemon; do not paste unrelated/stale output |
| A different phone is paired | Disconnect old phone, run `focusdeck forget-device`, then connect the new phone. Token must still match |
| Lost/reset token | Stop service; edit the private settings token to a fresh random value, restart and re-pair. A local command can generate one: `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'` |
| Pending command / uncertain delivery | Read status. Reconnect phone and allow the same command UUID to replay. Do not issue a second DONE just because the first timed out |
| New plan rejected | STOP current/prepared session first; reports preserve started work |
| Timer turns red and keeps running | Intended V1.1 behavior: budget expired; press DONE when the task is finished |
| Phone offline while screen is off | Reopen app; review One UI battery settings for FocusDeck and keep the visible foreground notification. Timing recovers from monotonic deadlines |
| Sound late during device sleep | Expected execution limitation; timer timestamps catch up, but the app does not promise an exact wake alarm |
| Report not on PC | Open/reconnect phone, verify LAN, check `focusdeck reports`; the report stays locally pending until saved and acknowledged |
| Automatic phone folder did not update | Open Study history & logs, read its saving error, verify free space, reselect a writable local folder and retry. Private reports remain independent of the visible copy |
| PC CSV missing or stale | Run `focusdeck refresh-logs`; inspect the error and restore any damaged report JSON. CSV regeneration preserves the prior file when validation fails |
| Older sessions missing on phone | Import full PC JSON using the steps in HISTORY_AND_LOGS.md; pre-V1.3 phone delivery history was capped at 100 |
| Local storage failure on phone | Do not clear app data if unsent work matters. Check available storage, restart app, inspect `adb logcat` for FocusDeck/DataStore. Do not uninstall before saving reports |
| Gradle download blocked | Build on a network that can access services.gradle.org, Google Maven and Maven Central, or use populated local caches |
| SDK location missing | Set ANDROID_HOME or open android/ in Studio so local.properties is created |
| Gradle requires wrong JDK | Select JDK 17 for this project's pinned AGP/Gradle versions |
| APK won't update debug install | Different signing keys require uninstall/reinstall; finish/sync local work before removing app data |
| adb finds multiple devices | Set ADB_SERIAL for install script |
| `--user` service unavailable | Run daemon manually in your Ubuntu session; do not use root to run the companion |

Desktop config: `~/.config/focusdeck/settings.json`, `cert.pem`, `key.pem`. Data: `~/.local/share/focusdeck/daemon.json`, `reports/` and `exports/`. Runtime socket: `$XDG_RUNTIME_DIR/focusdeck.sock`. Overrides `FOCUSDECK_CONFIG` and `FOCUSDECK_DATA` are available for isolated development/tests; their directories must be private and writable. See [all phone/PC storage locations](HISTORY_AND_LOGS.md).

The app does not auto-launch after reboot or force-stop. Reopen it; reboot recovery pauses the timer. A phone app data reset also creates a new device UUID, requiring `forget-device` on the disconnected desktop.
