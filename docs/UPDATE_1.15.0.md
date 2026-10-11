# FocusDeck 1.15.0 — phone plus tablet viewer

Android versionCode 20; desktop 1.15.0. One phone remains the timer authority and sole destination for all GPT session/achievement commands. Up to eight concurrent read-only Android viewers connect through the PC. The existing approved Home, achievement rings/details and history/report UI remain in use; 1.14 Skip/End behavior is retained.

| Device | Role | Pairing command |
| --- | --- | --- |
| Phone | Runs timers; receives all session/card writes | `focusdeck pairing` (existing pairing remains valid) |
| Tablet | Views cards/details, progress, streaks, history and observed session | `focusdeck pairing --viewer` |

## Upgrade and pair

Download the full source ZIP to Downloads. It contains a top-level FocusDeck folder. Extract into Projects, preserving the existing checkout/signing key/local SDK configuration and data. Use your existing JDK/SDK environment:

```bash
unzip -o "$HOME/Downloads/FocusDeck-v1.15.0.zip" -d /home/hax/Projects &&
cd /home/hax/Projects/FocusDeck &&
bash scripts/setup-desktop.sh &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
```

The phone install builds current source first. Install that same APK on the tablet without rebuilding. Run `adb devices -l` and use its tablet identifier; for Wi-Fi ADB it may be an IP:port. Replace TABLET_ADB_SERIAL below with that exact identifier:

```bash
ADB_SERIAL=TABLET_ADB_SERIAL bash scripts/install-apk.sh android/app/build/outputs/apk/debug/app-debug.apk
```

Open FocusDeck on both devices. Keep the phone's existing pairing. On the PC run `focusdeck pairing --viewer`, and privately paste its output into the tablet's top-right Pair desktop dialog. The tablet accepts the full CLI response or inner pairing object. The banner must say TABLET VIEWER · READ ONLY. `focusdeck devices` lists the phone and viewers separately. Neither ADB installation nor an app restart chooses the authority; the saved pairing role does.

Desktop setup retains existing phone token/certificate/identity and archives, adds a separate viewer token, and updates/restarts the companion. The app upgrades with install -r and the existing signing identity. Do not uninstall, clear data, or run forget-device to add a tablet. If the PC LAN address changes, update host in the appropriate private pairing JSON. New sync requires the PC to be running and both devices to reach its existing LAN/TLS port. Both Android devices and desktop should use this version.

## Read-only behavior and freshness

The tablet reuses card categories/details, custom icons, progress, history/statistics and report/timeline/copy/export UI. Layout/edit/archive/restore, timer/reward controls, history import/delete, automatic-folder and report-resend controls are hidden; repository guards also reject writes. Display/audio/immersion preferences remain local to each device. Merely opening a tablet achievement detail does not acknowledge or change phone record receipts. All GPT/CLI writes remain on the single phone, even if the tablet is the only device online.

Session time is the last phone observation; the tablet never runs an authoritative clock, completes blocks, generates reports/rewards or finalizes phone achievement days. While at least one foreground viewer watches an active/paused phone session, updates occur about every five seconds. Background viewers stop requesting the faster cadence; otherwise normal existing sync policy applies. Connection/offline banners display session observation, card-ledger observation and PC history synchronization timestamps. Card/history dates use the phone's ledger timezone. When PC/phone is offline, retained data is explicitly cached; unreceived changes cannot be inferred.

Phone snapshot, ledger and full report archive are mirrored into separate private tablet cache files. One bounded chunk at a time with ACK keeps each viewer's delivery independent of phone command ACKs. Dirty updates coalesce rather than queueing every tick. History replaces the visible cache only after the complete batch; interruption retains the previous complete history. Large first syncs can take longer. No history-age limit is added. Authority/desktop identities travel with cached data; forgetting/changing the authority clears the displayed old source, and a persisted source marker prevents it reappearing on restart.

The viewer credential authenticates only /v1/viewer; the existing phone credential authenticates only /v1/device. The same pinned TLS certificate and browser-Origin rejection apply. Viewer input accepts only presence and chunk ACKs; any command/report/state/ledger/deletion write closes that connection. Legacy phones remain accepted, but lack the full observed-session capability and show a phone-upgrade notice. Plan/report/achievement contract versions and timer arithmetic are unchanged.

## Verification actually performed

- Desktop full suite: 167 collected, 166 passed, one AF_UNIX host-restriction skip. Seven new real TLS/schema checks cover two simultaneous tablets, command routing, separate tokens/Origin rejection, attempted viewer writes, foreground interest, offline cache, chunk delivery, deletion refresh, desktop cache restart and legacy phone compatibility.
- Battery measurement-tool fixtures: seven passed; these do not establish device battery savings.
- Five new native cache regression tests are supplied: backward pairing, interruption/deletion batches, authority reset, wrong-source/writable frames, and out-of-order chunks. They are not executed.
- Source syntax inspection and Git whitespace checks are used for changed Kotlin/Python/shell files. Existing Kotlin parser limitations in DeckNetwork/DeckRepository are not compilation evidence.
- Android compilation/unit execution, APK assembly, lint and physical phone/tablet/LAN/portrait/landscape/FPS checks are unexecuted. The preceding 1.14 Gradle attempt could not resolve the offline Android Gradle plugin 8.9.2; this request does not rebuild APKs here. The install script builds on your PC.
- No target-PC service setup, device installation, user-history mutation, pairing reset or GitHub push was performed here.

After installing, check that a phone session can pause/skip/end normally while the tablet shows it, that card edits/custom icons reach the tablet, that landscape history scrolls, and that turning Wi-Fi off labels and preserves cached data. Reconnect and confirm fresh values, complete history and deletions. Use disposable data for deletion checks.

Updated GPT Project files are provided separately: replace versioned instructions/current state, paste the compact field, and keep your existing reward catalog and subject sources.

## APK follow-up — 8 October 2026

Hax explicitly requested a downloadable APK for the tablet, overriding the prior source-only build preference for this follow-up. Android main compilation and assembleDebug now passed. Five TabletMirror cache tests and six SkipEnd tests passed (11 total). The first SkipEnd run exposed a missing explicit countdown timer_mode in its v2 test fixture; the fixture and nullable parentFile call were corrected. App runtime source was unchanged. Logs are android-apk-1.15.0-results.txt and android-apk-1.15.0-tests.txt.

The standalone universal debug APK is FocusDeck-v1.15.0-debug.apk (applicationId com.hax.focusdeck, versionCode 20, versionName 1.15.0, minimum Android API 34). APK ZIP integrity, manifest/version and v2 signature verification passed. It is signed with the workspace debug key, certificate SHA-256 b3bd68b07853947eabf4d87759319b4004555d47dfa38f3b3f6138dd55643fa6. This signing key differs from prior workspace APKs and may differ from the PC key. An already installed copy needs a matching signing key for in-place updates; use the APK built on the PC with its existing key in that case. Never replace the PC phone signing key, uninstall or clear phone data to bypass a mismatch.

APK SHA-256: 3888175367974fc2b5f6340fdb7a2348e02a91ae9ce9c1b476efd486eb9918fa. Install/open it on the tablet, then paste the private output of focusdeck pairing --viewer into Pair desktop. The updated desktop companion must be running; phone pairing remains unchanged. APK assembly is not a device installation: physical phone/tablet/LAN/UI/FPS checks and lint remain unexecuted.
