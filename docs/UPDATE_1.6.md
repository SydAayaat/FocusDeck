# Update to FocusDeck V1.6

This source release replaces quality-check completion with DONE-only execution results, configurable classes/weights and GPT-controlled conditional rewards. It adds bidirectional study-log deletion and an offline PC Markdown clipboard command. Update both phone and desktop. Existing history/pairing is preserved when installing with the same signing key; this update itself does not delete any logs.

## Install on your PC and S24 Ultra

Extract the new source over `/home/hax/Projects/FocusDeck`, keeping local.properties, signing keys and PC configuration/data. Build/test/install and refresh the desktop package:

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
bash scripts/build-android.sh test &&
bash scripts/build-android.sh debug &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh &&
bash scripts/setup-desktop.sh
sudo apt install wl-clipboard
```

The desktop tests pass in the delivery environment. Android SDK/compiler and a connected phone are unavailable here, so Android tests/build/device behavior remain to be verified on your PC. No APK is included.

## Use and copy

Read [conditional rewards](REWARDS_AND_COMPETITION.md); replace the GPT project instructions with `docs/GPT_PROJECT_INSTRUCTIONS.md`. To test:

```bash
focusdeck validate examples/conditional-smoke-test.json
focusdeck start examples/conditional-smoke-test.json
```

1. Let the first 30-second block go into red overtime. It must remain active until DONE.
2. Press DONE: measurements/class/BeatScore appear immediately, with “No reward assigned.” With the screen visible it advances after about 2.2 seconds.
3. Finish the second around its target: if a reward unlocks, check REWARD DONE and CONTINUE semantics. CONTINUE records no reward acknowledgement; both keep task outcome DONE. No exercise is needed for testing.
4. Complete the independent session result, then check Home and top-left history. Pause before DONE on another attempt and check exact intervals.
5. Inspect class/weight/weighted totals in the report and master CSVs. Copy it from the desktop:

```bash
focusdeck copy-report --session 6653c27d-907f-4504-841a-f6d31b6e46d2
```

Now Ctrl+V in the matching GPT chat. With no filter, `focusdeck copy-report` selects the most recently finished report. `--session ID` selects that ID's latest saved attempt; `--key 'SESSION_ID:STARTED_AT_MS'` selects one exact attempt. The report contains its plan ID, attempt key, UTC timestamps, timing/events, scores/policies and reward evidence. It is plain-text Markdown on the clipboard, not an attached file. Works from PC JSON even with phone/daemon offline; Wayland needs wl-copy, X11 needs xclip or xsel. Failure produces an error rather than claiming success.

## Delete automatic study logs

On the phone, open top-left history. Each session has DELETE SESSION; the panel has DELETE ALL LOGS. Confirm the displayed scope. The current active timer continues.

Equivalent desktop commands (daemon running; phone may be offline):

```bash
focusdeck delete-logs --session SESSION_ID
focusdeck delete-logs --key 'SESSION_ID:STARTED_AT_MS'
focusdeck delete-logs --all
```

`--session` removes the latest PC-saved attempt for that exact plan ID, rather than every attempt sharing it. `--key` can also target a known phone attempt not yet copied to the PC. `--all` records a cutoff for previously finalized logs, including old reports arriving later from an offline device. It does not cancel the active session. Use accurate device clocks when using the wall-time cutoff.

Deletion intent is saved before removal, merged in both directions, retried on reconnect/restart, and applied before new report replay. Tombstones prevent deleted reports from returning through retransmission/import. Exact keys distinguish attempts with the same plan ID. Small persistent deletion metadata (cutoff/attempt keys/revision) is retained to suppress old copies; the report contents are removed.

Scope: phone private archived JSON and finalized state/outbox, FocusDeck-managed JSON in its currently selected automatic phone folder, PC automatic JSON/Markdown and all four master CSV snapshots. CSVs are regenerated, removing the deleted rows. Previous automatic phone folders are pruned if selected again. Independently exported/downloaded/copied files, backups, other apps' files, pairing/settings and diagnostic service logs are outside this operation. Deleting study history also removes its PB and acknowledged reward-volume evidence.

PC offline: phone shows “Deletion saved · PC sync pending.” Phone offline: terminal says pending until reconnect. ACK confirms saved deletion intent on Android; physical/provider file cleanup follows asynchronously. If folder access/disk writes fail, history shows a notice and RETRY LOG SAVING; restore access/retry to finish removing visible copies. PC cleanup errors also need storage repaired and `focusdeck refresh-logs` or a restart/retry.

Check deletion using disposable smoke-test sessions: delete one attempt while offline, reconnect and ensure it stays gone on both devices and CSVs; resend an old report; restart both apps. Test DELETE ALL on disposable logs, then create a fresh session and confirm it is retained. Existing logs are not deleted as part of installation.

V1.5 legacy reports remain readable, but obsolete quality_confirm/quality_incomplete/reward_skip commands and phone quality/skip controls are removed. A persisted V1.5 quality screen migrates to the DONE-based result without inventing focus time. New reports retain compatible internal quality/skip fields for old readers, with DONE confirmed and skipped=false; new classes contain no failure/incomplete outcomes.
