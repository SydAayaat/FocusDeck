# Install Android 1.21.0

Extract `FocusDeck-v1.21.0.zip`. The APK is `FocusDeck/apk/FocusDeck-v1.21.0.apk`, using application ID `com.hax.focusdeck`, versionName `1.21.0`, versionCode `25`, minimum Android 14. It is a local debug build signed with the existing PC key, verified against the installed S24 Ultra application. It contains the real application, not the `.uiqa` fixture package.

From the extracted `FocusDeck/` directory, update over ADB:

```bash
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh apk/FocusDeck-v1.21.0.apk
```

The explicit APK argument installs the included build without rebuilding. This uses `adb install -r` and retains the existing app data when signatures match. Alternatively, copy the APK to the phone and open it in the Android package installer to update the existing app. No uninstall, clear-data, pairing or desktop-service setup step is needed.

Keep the existing PC project signing key, Android SDK settings, phone archives and PC config/certificates/tokens. The ZIP excludes signing keys, local SDK paths, build caches, private QA baseline screenshots and installed-app copies. If installing on another existing device reports a signing mismatch, retain that device's data and use its original signing key rather than uninstalling.

Desktop source remains 1.20.0; this Android UI update does not require reinstalling the companion. The ZIP includes full application source, schemas, docs and locked references so it can also be rebuilt with the existing local setup.
