# Upgrade to 1.1: overtime + correlated terminal plans

Finish and sync any current session. Extract the new ZIP over your existing FocusDeck source directory, preserving SDK files and build caches. The APK retains the same package and existing debug signing key when built on the same computer; adb install -r preserves pairing/history.

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
bash scripts/build-android.sh test
bash scripts/build-android.sh debug
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
bash scripts/setup-desktop.sh
```

The setup script upgrades the private Python installation and restarts the user service. Existing token/certificate/device pairing and reports are retained. Reopen the phone app after install. If you deliberately use a particular private bind IP instead of auto detection, pass that address to setup-desktop.sh.

Let the first smoke-test block run for 45 seconds: it should be red +00:15 and still show the first task. Press DONE to move to the break. Let the break expire too: it stays on the break until DONE. Finish the final block with DONE, then check the report for session ID and overtime.

See GPT_WORKFLOW.md for the paste-ready heredoc format and session-specific report command. Protocol version remains 1; added overtime fields are optional so old reports still read. auto_advance now refers solely to whether the next block starts after DONE.
