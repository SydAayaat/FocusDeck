# Updating to FocusDeck 1.4.0

This update adds a proper home screen and detailed pause/resume/task-transition timelines. The top-left history icon remains. Reports open on demand from Home or history, while an unfinished session opens its timer. Automatic phone/PC logging now maintains four CSVs: sessions, blocks, events and intervals. See [timeline and feedback details](TIMELINE_AND_FEEDBACK.md).

Extract the source ZIP over `/home/hax/Projects/FocusDeck`, preserving `android/local.properties` and your signing key. Finish/stop the current session before upgrading if you want the next session to have a complete timeline. Updating an unfinished session preserves its timer but can only log events from the upgrade onward, marked partial. Use install-over-existing rather than uninstalling.

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

The desktop must be updated too for its timeline display and event/interval CSV generation. Existing pairing, archives and folder grants are retained. If you have not chosen a visible phone folder yet, use the top-left history icon → CHOOSE AUTOMATIC LOG FOLDER and select a local folder such as Documents/FocusDeck.

## Short check

1. Open after a completed session. Confirm Home shows today's/week/all-time focus and a compact last-session link. VIEW REPORT opens it; CLOSE returns Home.
2. Start a new two-block session. PAUSE for a few seconds, RESUME, pause again and tap DONE while paused. The next block starts only after DONE; overtime still never advances automatically.
3. Complete the session. Home should return. Open VIEW REPORT or history → VIEW TIMELINE. Check both pause intervals, task transition timestamps and pause count.
4. Copy the full report, then retrieve `focusdeck reports --session YOUR_ID --latest`. Check matching session/block IDs and timeline data on both copies.
5. Inspect the selected phone folder and `~/.local/share/focusdeck/exports/` on PC for all-events.csv and all-intervals.csv alongside the existing logs. Confirm repeat SEND REPORT does not duplicate event rows.
6. Try manual advance: the period after DONE and before START should be waiting, with no extra focus or pause time.
7. Background/reopen during a pause. Its interval should remain intact. A reboot should recover paused and mark timeline coverage partial, with no invented across-reboot interval.
8. Open an older report. It should show unavailable timeline coverage and retain its original aggregate totals. Missing pause counts remain unknown.

This is a source release. The delivery host has no Android SDK/connected phone, so its Android build, 37 JVM tests and these physical checks remain pending. Desktop tests and source/resource checks were executed; see VERIFICATION.md. No APK or measured battery change is included.
