# Battery optimization and measurements — V1.2

The display changes and reduced background work are implemented. Savings have **not** been measured on the S24 Ultra yet. A pure-black OLED background reduces the number of emitting pixels; the active panel, lit text, CPU and Wi-Fi still consume power. Brightness and total screen-on time can dominate the result. No battery-life percentage or runtime is promised.

## Changes

| Area | V1.1 | V1.2 |
| --- | --- | --- |
| Main background / dial interior | #121212 with a shaded gradient | #000000, no gradient |
| Top-left title | FOCUSDECK | Removed |
| DONE button | Filled | Outlined |
| Timer display | Four updates/second and spring animation | One update/second, no dial animation |
| Repository when active in background | Four ticks/second | One check per 30 seconds |
| Paused clock | Four ticks/second | One check per minute |
| Idle/ready/complete clock | Polling | Waits for events |
| Durable checkpoints | Every 10 seconds, including idle | Active: 10 seconds visible / 30 seconds background; paused: 60 seconds; none while idle |
| Notification writes | Every checkpoint | State revisions only, with a system notification chronometer |
| Routine LAN state | Every 2 seconds | 15 seconds visible and active; 60 seconds otherwise |
| Commands / pending report changes | Command response and next periodic update | Immediate state event; unsent report retries every 15 seconds |
| Keepalive | Phone and desktop both ping every 20 seconds | Desktop alone pings every 60 seconds |
| Reconnect cap | 30 seconds | 30 seconds visible / 120 seconds background; bringing app forward wakes a background retry |

Timer values come from `elapsedRealtime`, not tick counts. DONE, PAUSE, RESUME, extension and report times use the current clock. Expiry stays red and counts overtime until DONE. Commands and transitions remain durably saved before ACK.

The lower checkpoint frequency trades less disk work for a larger **reboot-only** recovery window: up to about 30 seconds of active time or 60 seconds of paused time before a sudden reboot may be absent, assuming Android was scheduling the app normally. Deep sleep can lengthen the interval. Same-boot process restoration can derive the full elapsed duration from the saved monotonic anchor. Alerts may be delayed by up to the background check interval and further by Android sleep; no exact alarm or CPU wake lock is added.

## Display settings

In **⋮ → Display settings**, enable **Dim desk view** for a small timer and task title on pure black, without the ring, footer or progress bar. DONE and PAUSE/RESUME remain visible. Timer content shifts a few pixels every minute while displayed, to spread static pixel exposure; this is not a guarantee against OLED burn-in. This mode sets **app-window brightness to 0.08 on Android's normalized brightness scale**; it is not a percentage of physical light output. Leaving the active timer or disabling the option returns brightness to the phone's own setting. It does not change global brightness settings or request special permissions.

The app asks Android for a 60 Hz window preference in normal view and 30 Hz in dim active view. Android/One UI chooses the actual panel rate, which can differ. This does **not** force Samsung's hardware AOD mode or guarantee 1 Hz operation. Interactions and system UI can alter the actual rate.

For lowest display power, disable **Keep screen awake**, then lock the phone. The timer continues by elapsed time and the service maintains its notification. If no desktop interaction is needed, **DISCONNECT DESKTOP** stops the network service without cancelling the session. Reopening the app automatically reconnects as before. Sleeping Android can delay LAN commands/report delivery; wake the phone before a PC command if it is offline.

## Native Samsung AOD

Samsung documents its own clock, wallpaper and notification options. We have not found a supported public smartphone SDK that lets a regular Compose app render an arbitrary live dial inside that system AOD. No native AOD extension is included or claimed. The notification can appear on the lock screen and its icon may be shown on Samsung AOD depending on One UI settings. Its system chronometer is for the notification interface; a live numeric timer on Samsung AOD is **not guaranteed**. Dim desk view is an ordinary, lower-brightness **screen-on** view.

On your phone, check **Settings → Lock screen and AOD → Always On Display**. Where available, disable the AOD wallpaper and use the native clock/notification display for the screen-off comparison. Menus vary with the installed One UI version.

## Capture a useful comparison

The script captures two endpoints, saves raw system diagnostics and computes a whole-phone discharge summary. It does not poll during the run, install anything, alter brightness, toggle Wi-Fi, simulate unplugging or reset battery readings. `--mode` is a label; select that view on the phone yourself. The optional **`--reset-stats` erases system-wide Batterystats history**, not just FocusDeck history; default runs do not reset it.

1. Keep your existing V1.1 installed for a baseline first. Copy the new `scripts/battery-profile.py` onto the PC; it works with V1.1 too.
2. Use **wireless ADB and physically unplug USB/charging** before both endpoints and throughout the test. USB-connected tests cannot establish actual discharge. The PC-to-phone ADB transport is separate from FocusDeck's phone-to-PC LAN connection. You previously listed `192.168.1.143:5555`; confirm it still connects with `adb devices` or use the current Wireless debugging address. Keep the same ADB arrangement in every run.
3. Disable adaptive brightness for comparisons, choose a fixed brightness for normal view, and match workload, Wi-Fi, signal, phone temperature, notification activity and OS power settings. Start around a similar charge level, away from very low/full charge. Set the mode before taking the start snapshot. Close menus and let the phone cool before the run. The dim view intentionally changes brightness/layout; comparing it to normal view measures that complete preset, not just the black-background change.
4. Run one long focus block (for example `focusdeck quick --minutes 120 --title "Battery test" --task "Matched study workload"`). Overtime does not advance it. Keep the same DONE/pause behavior across comparisons. If a previous session remains active, finish or stop it first.
5. Capture start, work for **60–120 minutes**, and capture finish before opening other apps or plugging in. The command returns immediately; there is no waiting script to leave running.

```bash
cd /home/hax/Projects/FocusDeck
export ADB_SERIAL=192.168.1.143:5555

python3 scripts/battery-profile.py start battery-runs/v11-normal \
  --label "V1.1 baseline" --mode normal

# After 60–120 minutes, still physically unplugged:
python3 scripts/battery-profile.py finish battery-runs/v11-normal
```

Install V1.2, choose normal view at the same brightness, and repeat with a **new directory**:

```bash
python3 scripts/battery-profile.py start battery-runs/v12-normal \
  --label "V1.2 normal" --mode normal
# After the matching interval:
python3 scripts/battery-profile.py finish battery-runs/v12-normal
```

Then select **Dim desk view** and repeat:

```bash
python3 scripts/battery-profile.py start battery-runs/v12-dim \
  --label "V1.2 dim desk" --mode dim
# After the matching interval:
python3 scripts/battery-profile.py finish battery-runs/v12-dim

python3 scripts/battery-profile.py compare \
  battery-runs/v11-normal battery-runs/v12-normal battery-runs/v12-dim
```

For a screen-off/AOD run, lock the phone before both samples and label it `--mode screen-off`. ADB can still read the snapshots; avoid unlocking the screen to take them. Record your AOD settings alongside the run. For a clearer comparison, repeat each case twice or more, preferably alternating the test order. Use identical build types, since debug-vs-release is another variable.

Each directory contains `summary.md`, `summary.json`, start/finish battery readings, full and app-filtered Batterystats, gfxinfo, CPU, display/power dumps, version and device metadata. Measured battery percentage is coarse. Charge counter is treated as microampere-hours according to Android's standard interface and is only used when plausible; Samsung may omit it or report noisy values. Rates are withheld if endpoints are charging/simulated, the interval is too short, device/version changes, or a reboot/clock change is detected. Endpoint checks cannot detect charging in the middle of the test; keep it unplugged throughout and review Batterystats. Zero percentage change is below that coarse gauge's resolution, not zero power.

All computed discharge is **whole-phone**, including screen, radios, Android and other apps. App-filtered Batterystats provides activity/estimated attribution, not a laboratory measurement of app watts. The full dumps are cumulative unless deliberately reset; compare endpoint differences and inspect the interval. A wireless ADB connection itself can influence sleep/network use; use the same arrangement for all comparisons and later confirm natural-use battery drain without ADB.

Paste the three summaries into this chat for analysis. Keep full diagnostics private unless needed: system dumps may contain unrelated device/app information. `battery-runs/` is excluded from release ZIP packaging.

## Further profiling

Android now recommends system tracing, Macrobenchmark power metrics or Power Profiler where supported; Battery Historian is no longer actively maintained. The raw Batterystats files remain useful for checking activity, screen time, network and charging. S24 Ultra power-rail profiling availability depends on the installed firmware/tooling; it is not promised here.

References:

- [Android battery diagnostics and endpoint workflow](https://developer.android.com/topic/performance/power/setup-battery-historian)
- [Doze restrictions](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Window brightness and refresh preferences](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Android battery counters and units](https://developer.android.com/reference/android/os/BatteryManager)
- [Samsung AOD options](https://www.samsung.com/us/support/answer/ANS10001976/)
