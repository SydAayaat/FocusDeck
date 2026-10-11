# Updating to FocusDeck 1.3.0

V1.3 adds calendar study summaries, a lasting private phone archive, automatic CSV logs on both devices, and an optional visible phone folder containing full JSON records and both master CSVs. You choose that phone folder once; subsequent saving is automatic. See [history and storage details](HISTORY_AND_LOGS.md).

Extract this ZIP over `/home/hax/Projects/FocusDeck`, retaining `android/local.properties` and your existing signing key. Do not uninstall the phone app or clear its data. Finish or stop the current session before updating, then paste:

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

bash scripts/build-android.sh test &&
bash scripts/build-android.sh debug &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh &&
bash scripts/setup-desktop.sh
```

The scripts use the project's pinned build tooling. Install uses `adb -r` against the specified phone and preserves app data when the same debug signing key is used. Desktop setup preserves existing reports and credentials, updates the Python package and restarts its user service. Pairing should remain intact.

On the phone, tap the **history icon at the top left**, then **CHOOSE AUTOMATIC LOG FOLDER**. The overflow menu's **Study history & logs** opens the same screen. Create/select **Documents/FocusDeck** in local phone storage and allow access. This creates visible copies of existing phone history and enables automatic updates for new completed/stopped sessions. No manual CSV export is required.

The PC master logs are automatically saved in:

```text
/home/hax/.local/share/focusdeck/exports/all-sessions.csv
/home/hax/.local/share/focusdeck/exports/all-blocks.csv
```

For older PC history that the phone's former 100-report cache already discarded, export a full JSON array, transfer it to the phone, and select **IMPORT JSON**:

```bash
focusdeck export --format json --output "$HOME/Downloads/focusdeck-history.json"
```

## Short device check

1. Tap the top-left history icon while idle and during a timer. Check Day/Week/Month/Year/All, date arrows, totals and an existing session's copied report. Confirm current history is present and CLOSE returns to the timer.
2. Choose the automatic phone folder. After saving settles, inspect it in Files: hashed JSON records, `all-sessions.csv` and `all-blocks.csv` should exist.
3. Start the device smoke session. Let the first block reach overtime, then tap DONE. Finish the session normally and verify the phone files and PC CSVs update without EXPORT.
4. Press SEND REPORT twice. Each CSV should still contain one row per attempt/task, not duplicate rows.
5. Turn Wi-Fi off, complete another session, and check the phone folder updates. Reconnect and check the PC gets that session too.
6. Import the PC JSON once and again. Only missing sessions should be added. Restart the phone app and verify history remains.
7. Optionally revoke the folder grant or choose an unavailable provider. Private history must remain visible, and the app should show a log-saving error. Reselect a writable local folder and retry.

Calendar summaries assign each whole session to its local finish date. A live session appears after completion or STOP; intermediate state remains checkpointed. Keep the JSON archive as well as CSV for restoration.

This is a source update. Desktop checks passed in the delivery environment; Android compilation and the storage-picker/device checks remain to run on your PC/S24 Ultra because this host has no Android SDK or connected phone. No prebuilt APK is included.
