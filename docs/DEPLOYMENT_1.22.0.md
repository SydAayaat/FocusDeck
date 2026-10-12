# FocusDeck 1.22.0 deployed · 12 October 2026

Hax explicitly requested updating both the PC and regular phone app after Stage 2. The existing `codex/omss-native-ui` source at `36e89a7` is now deployed locally, following the completed Stage 1/2 source and isolated checks.

- **Regular Android app:** `com.hax.focusdeck`, versionName **1.22.0**, versionCode **26**, installed with `adb -s R3CX90NC7HR install -r` on USB Samsung SM-S928B. The new production-package debug APK was built from current source. Its certificate SHA-256 matches the previously installed 1.21.0 APK; signing identity is retained. Installed package metadata confirms the new version. No uninstall, clear-data, production fixture import, re-pairing or release APK/upload was performed.
- **Desktop:** existing `~/.local/share/focusdeck/venv` upgraded from 1.20.0 to **1.21.0** with `scripts/setup-control-center.sh`, including the new `qrcode` dependency. Existing managed launchers and service configuration remain. The companion service restarted successfully; only the verified Control Center GUI process was restarted to load the updated local assets/modules. Installed-module version and running GUI/service processes are verified.
- **Existing connection:** the regular phone app was opened normally. The independent Android FocusService is running, and the desktop's authenticated status reports `connected=true`, `state_is_cached=false`, with the existing completed session state. No timer, achievement, reward or session command was issued. This verifies the existing paired LAN connection after upgrade, not a new QR pairing.

## Preservation

Private owner-only backups of desktop configuration/data, the previous phone APK and phone app data were saved outside Git under `~/.local/share/focusdeck-upgrade-backups/`. Exact local backup path and hashes are in ignored `artifacts/uiqa/deployment-1.22.0/` evidence; no credentials or production records are published.

Immediately after the in-place phone install, **all ten readable private files were byte-identical**. After normal launch, **all nine protected settings/ledger/report/export/layout files remain byte-identical**; only AndroidX's `files/profileInstalled` marker changed for the new APK. Desktop configuration/certificates/tokens and all existing archived reports remain byte-identical, with no prior files missing. Four desktop runtime observation files updated during reconnect. Full private backups retain their previous values.

The supplied dataset is unchanged and the generator was not run. Developer Mode now lives in the regular installed app: Home overflow → Developer Mode settings (or Display settings) → Developer mode → Save. The synthetic preview labels its fixed simulated date and remains read-only; Use real data restores genuine views. Developer Mode was not enabled on the production app as part of deployment, so the preservation checks do not include changing the user's preference.

## Repeat an authorized update from this checkout

Close the Control Center through its tray Quit or Ctrl+Q before replacing its running GUI. Keep the current PC signing key and configuration. Run:

```bash
cd /home/hax/Projects/FocusDeck
bash scripts/setup-control-center.sh

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
# Default installer rebuilds current source and performs install -r.
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
focusdeck-control-center
```

These commands are for this same PC/phone and retained signing key. When rebuilding on a different machine, compare the new APK certificate with the installed certificate first. The completed deployment above used an explicit freshly built APK after that comparison. Do not uninstall or clear data to resolve a signature mismatch. Read [Stage 2](UPDATE_1.22.0_STAGE2.md) for the earlier focused tests and QR pairing limits.
