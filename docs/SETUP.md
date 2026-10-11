## Phone and tablet setup (1.15.0)

For the current full-source upgrade and concurrent read-only tablet pairing, follow [UPDATE_1.15.0.md](UPDATE_1.15.0.md). Phone pairing stays `focusdeck pairing`; tablet pairing is `focusdeck pairing --viewer`. Update desktop and both Android devices. Keep the existing phone identity/token/signing/data. The timer and all GPT writes remain on the phone.

# Ubuntu and S24 Ultra setup

For the audited 1.7.1 source baseline, preservation requirements and exact target-PC checks, start with [PROJECT_STATUS.md](PROJECT_STATUS.md). Before installation, run `python3 scripts/check-environment.py` from the project root. This read-only check does not alter pairing or logs.

## 1. Desktop

Prerequisites: Ubuntu 24.04, Python 3.10+, venv, OpenSSL, and a working systemd user manager. Install missing tools:

```bash
sudo apt install python3-venv openssl curl unzip openjdk-17-jdk
```

From the extracted `FocusDeck/` directory:

```bash
./scripts/setup-desktop.sh             # detect a private LAN IP
# Or specify it explicitly:
./scripts/setup-desktop.sh 192.168.1.20
```

Add the launcher directory to your current shell if needed:

```bash
export PATH="$HOME/.local/bin:$PATH"
focusdeck status
systemctl --user status focusdeck.service
```

Persist that PATH in your shell configuration if Ubuntu has not already done so. The daemon runs while the user session is alive; user-service lingering is optional and not enabled automatically. No root service is required.

Manual development install instead of systemd:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e ./desktop
focusdeck-server --host 192.168.1.20
```

Without an explicit host the daemon binds loopback. Stop the systemd service before starting a manual daemon; there is one daemon per user. `--host auto` detects and persists a private IP. A DHCP change can require an updated bind address and service restart.

## 2. Android Studio / SDK

Install Android Studio from [the official site](https://developer.android.com/studio), open `FocusDeck/android/`, and select a JDK 17 Gradle runtime. This project pins AGP 8.9.2, Gradle 8.11.1 and Kotlin 2.1.20; keep these pins for the first build. No NDK is required.

In SDK Manager install:

- Android SDK Platform 35.
- Android SDK Build-Tools 35.0.0.
- Android SDK Platform-Tools (ADB).
- Android SDK Command-line Tools (if building from terminal).

Accept SDK licenses in Android Studio, or with `sdkmanager --licenses`. Then:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
./scripts/build-android.sh test
./scripts/build-android.sh debug
```

`android/gradlew` is a documented lightweight bootstrap, rather than a bundled binary wrapper JAR: it downloads Gradle 8.11.1 and verifies its published SHA-256 over HTTPS. It needs `curl` and `unzip`. Android Studio may ask for a wrapper; choose a local Gradle 8.11.1 distribution or run `./gradlew wrapper --gradle-version 8.11.1` inside `android/` to generate the standard wrapper on your machine. The generated wrapper may replace the bootstrap. First sync/build downloads Maven artifacts. Subsequent builds can use `android/gradlew --offline assembleDebug` after all dependencies are cached.

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

## 3. Install on the S24 Ultra

For USB debugging:

```bash
adb devices
./scripts/install-apk.sh
```

For your already available wireless ADB connection:

```bash
adb devices
adb connect PHONE_IP:DEBUG_PORT      # when not already connected
ADB_SERIAL=PHONE_IP:DEBUG_PORT ./scripts/install-apk.sh
```

If wireless debugging has not been paired, Android Developer options → Wireless debugging → Pair device with pairing code supplies a separate pairing port:

```bash
adb pair PHONE_IP:PAIRING_PORT
adb connect PHONE_IP:DEBUG_PORT
```

After installation you may disconnect ADB. Normal operation uses the LAN socket, not ADB.

## 4. Firewall and pairing

If a firewall is enabled, allow TCP 8765 from your LAN subnet **only**. Example, using your actual subnet:

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8765 proto tcp
```

The setup script does not change firewall rules. Do not forward a router port. Put both devices on the same non-isolated LAN; guest Wi-Fi/AP isolation can prevent connection. Allow the private certificate/token only through trusted pairing.

Run `focusdeck pairing`. Paste its output into the phone's **Pair desktop** dialog. The dialog accepts either the inner pairing object or the complete CLI response. V1 uses JSON entry; QR scanning and mDNS are not included. KDE Connect clipboard or an offline private file transfer can move pairing JSON, but do not paste it into a shared chat or public repository.

Open the app and grant notification permission. It starts the visible user foreground service. Confirm `● LAN`, then:

```bash
focusdeck devices
focusdeck start examples/device-smoke-test.json
```

Reserve the PC's LAN IP in your router, or change only `host` in the phone's pairing JSON to a locally resolvable desktop hostname. The certificate pin makes a changing IP safe if the correct trusted desktop is reached. There is no automatic mDNS discovery in V1. To change the desktop bind IP:

```bash
systemctl --user stop focusdeck.service
focusdeck-server --init --host NEW_PRIVATE_IP
systemctl --user start focusdeck.service
focusdeck pairing
```

Re-enter pairing JSON on the phone. Updating host/port for the same desktop/token/certificate is allowed during an active offline session. Pairing a different desktop requires stopping the session first.

## 5. Release APK

Create a signing key outside the repository:

```bash
mkdir -p "$HOME/.config/focusdeck-signing"
chmod 700 "$HOME/.config/focusdeck-signing"
keytool -genkeypair -v -keystore "$HOME/.config/focusdeck-signing/release.jks" \
  -alias focusdeck -keyalg RSA -keysize 3072 -validity 10000
export FOCUSDECK_KEYSTORE="$HOME/.config/focusdeck-signing/release.jks"
export FOCUSDECK_KEY_ALIAS=focusdeck
read -rsp 'Store password: ' FOCUSDECK_STORE_PASSWORD; echo
export FOCUSDECK_STORE_PASSWORD
read -rsp 'Key password: ' FOCUSDECK_KEY_PASSWORD; echo
export FOCUSDECK_KEY_PASSWORD
./scripts/build-android.sh release
unset FOCUSDECK_STORE_PASSWORD FOCUSDECK_KEY_PASSWORD
```

With a configured key: `android/app/build/outputs/apk/release/app-release.apk`. Without it, Gradle emits `app-release-unsigned.apk`, which is not installable until signed. Never include keystores or passwords in the repo/ZIP. Keep the key securely for future updates. Switching from a debug-signed app to release signing normally requires uninstalling the debug app, removing its data; finish and sync sessions first.

## 6. ADB reverse development fallback

Stop the user service, bind the daemon to loopback, and keep its foreground terminal running:

```bash
systemctl --user stop focusdeck.service
focusdeck-server --host 127.0.0.1
```

In a second terminal:

```bash
adb reverse tcp:8765 tcp:8765
focusdeck pairing
```

Pair with host `127.0.0.1`; TLS/pinning still applies. To return to normal LAN operation, stop the manual daemon, remove reverse, set the LAN bind address again and restart the user service:

```bash
adb reverse --remove tcp:8765
focusdeck-server --init --host auto
systemctl --user start focusdeck.service
```

Update phone pairing host accordingly.


### Achievements upgrade (1.9.1)

Upgrade both source components to use `focusdeck achievements ...`. Keep existing phone app data, `.android/debug.keystore` or your original release key, PC settings/certificates/token and archives. Do not uninstall or clear data after an APK signature mismatch. Achievements initializes its independent DataStore key on first launch; pairing and study history remain in their original stores. See UPDATE_1.9.1.md for the source-only update and focused checks. This patch supplies no APK; build with the existing PC signing key through your usual scripts. The 1.9.0 APK verification is historical.
