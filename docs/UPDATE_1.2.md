# Updating to FocusDeck 1.2.0

V1.1 was physically validated: the block stayed in overtime, DONE advanced it, and the report preserved the original plan ID. V1.2 adds pure-black OLED rendering, removes the top-left title, reduces ticking/disk/network/notification work, and adds an optional dim desk view. Native Samsung AOD rendering is not included; see [battery measurements and AOD limits](BATTERY.md).

For a genuine before/after measurement, copy `scripts/battery-profile.py` from this release and run the V1.1 baseline **before updating the installed app**. It can measure either version.

Extract the source ZIP over your existing project directory. Preserve your SDK `local.properties`, debug keystore and desktop pairing configuration. Do not uninstall the app or clear its data. Then:

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

Installing with `-r` on the same computer/debug signing key keeps pairing, history and the active session. Desktop setup updates the existing package and restarts the daemon while preserving its credentials and reports. No new pairing should be needed. Update the desktop too so that its old 20-second keepalive is replaced by the 60-second keepalive.

On the phone, open **⋮ → Display settings**. Normal view is already pure black. Enable **Dim desk view** and optionally **Immersive display** for the minimal desk view. The dim preset uses 0.08 normalized app brightness, requests a 30 Hz window preference and leaves the actual panel refresh choice to One UI. Its small timer shifts a few pixels each minute. DONE/PAUSE retain the V1.1 behavior.

## Device checks before a long run

- Start a 30-second focus block. Confirm black background/dial interior and no top-left title. Expiry must still become red overtime and stay on the same block until DONE.
- Toggle dim view, pause/resume, extend from overtime and complete both focus/break blocks. Confirm the report still has the original session ID and correct actual/overtime time.
- Rotate the phone and reopen the app after backgrounding. The timer should catch up from elapsed time immediately.
- Leave a paused/finished session for a minute and confirm it does not appear as an actively changing notification. Notification chronometer rendering is owned by Android; AOD may show only its icon.
- Lock the phone for several minutes, wake it, and inspect the elapsed timer and desktop connection. Sleep can delay alerts and LAN messages. Closing/disconnecting the service does not cancel the timer.
- Disable dim view or finish a session and confirm brightness returns to the phone's normal setting.

This source update must be compiled and tested on your PC/phone. The delivery host has no Android SDK or connected S24 Ultra; there are no measured battery savings or APK included.
