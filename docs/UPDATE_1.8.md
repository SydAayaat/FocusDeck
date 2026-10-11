# Upgrade to FocusDeck 1.8.2

Android and desktop are both 1.8.2. Stopwatch/completion plans use session/report contract 2; existing plans/reports and wire/pairing contract 1 remain compatible. Read [STOPWATCH_MODE.md](STOPWATCH_MODE.md) for the exact contract and limitations.

The 1.8.2 stopwatch UI follows the supplied dial reference within the existing countdown layout. Its outer arc advances once per active minute and cycles hourly; it does not create a time target. See [UI changes](UPDATE_1.8.2.md).

The 1.8.1 patch strengthens report validation without changing session/report contract 2, countdown scoring or installed identity. See [patch notes](UPDATE_1.8.1.md). Work remains local; no remote CI or repository update is required.

Overlay the delivered source into `/home/hax/Projects/FocusDeck`, preserving its Git history, `android/local.properties`, existing signing/debug keystores and local configuration. Retain `~/.config/focusdeck`, `~/.local/share/focusdeck`, the phone's app data and selected log folder. The application ID remains `com.hax.focusdeck`; install with the same signing identity using the existing `adb install -r` script. If signing differs, resolve the key rather than uninstalling or clearing app data.

Run as Hax in the logged-in Ubuntu user session:

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

python3 scripts/check-environment.py
python3 -m venv /tmp/focusdeck-1.8-checks
/tmp/focusdeck-1.8-checks/bin/pip install './desktop[test]'
PATH="/tmp/focusdeck-1.8-checks/bin:$PATH" bash scripts/test-desktop.sh
python3 scripts/test-battery-profile.py

bash scripts/build-android.sh test &&
bash scripts/build-android.sh debug &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh &&
bash scripts/setup-desktop.sh

bash android/gradlew lintDebug
adb -s R3CX90NC7HR shell dumpsys package com.hax.focusdeck | rg 'versionCode|versionName'
systemctl --user status focusdeck.service
focusdeck status
```

Desktop setup reuses existing settings/certificates/tokens and archives. V2 plans are refused until the phone advertises v2 support. Existing v1 plans continue to work; none is converted automatically. Do not paste pairing secrets into logs or source.

Upgrade both components before using v2. Compatibility means 1.8 can read older plans/reports; 1.7 cannot read newly saved v2 state/reports. Avoid downgrading the phone after executing stopwatch plans.

## Device checks still required

Use copies of the supplied examples with fresh UUID/created_at and disposable sessions. Reward wording and thresholds should remain exactly as supplied.

1. Confirm previous history, visible folder access, pairing and Home remain available after upgrade.
2. Start a stopwatch block: `00:00:00`, STOPWATCH, upward elapsed focus; 60 inner ticks, minute-stepped clockwise hourly arc, completed-block progress labeled `% DONE`, N/A finish, and no deadline/overtime/extension. At 15 active minutes the marker is right, 30 bottom, 45 left; after 60 it returns to the top while elapsed text continues. Pause for a known interval, resume, DONE while paused on a second run. Check active, paused, observed wall, timestamps and N/A time scores.
3. Recreate/background the app and reconnect Wi-Fi; stopwatch catches up without counting rendered ticks. Reboot: recover paused at the last checkpoint with partial coverage. Check portrait, landscape, larger fonts and dim desk view on the S24 Ultra.
4. Run mixed-mode plan: module reward only after all named topics are DONE; unchanged red countdown overtime; next block mode remains exactly supplied; reward handling preserves auto/manual progression.
5. Confirm session rewards use DONE count/all-focus/session completion and overall Focus Ratio. Change a threshold in a new plan to produce an ordinary unmatched reward result.
6. Inspect `focusdeck reports --session UUID --latest --json`, history, phone/private/visible JSON and all four CSVs. Stopwatch planned/score cells stay null/blank; mixed scores are labeled countdown-only. Run `focusdeck copy-report --session UUID` and paste into GPT on KDE Wayland.
7. Delete only disposable reports using single-session and all-log commands in a disposable archive setup; verify tombstones prevent their return. Existing automated tests use temporary data; implementation did not delete your logs.

The source delivery includes no compiled APK or phone screenshots. See [verification](VERIFICATION.md) for executed checks versus pending work. Dim desk view remains an app display mode, not native Samsung AOD.
