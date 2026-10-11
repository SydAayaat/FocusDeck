# Android FocusDeck 1.21.0 — locked native UI

Android versionName 1.21.0, versionCode 25. Desktop remains 1.20.0. The local `ui/locked-design-2026-10-11` branch contains separate M0–M8 checkpoints and the packaging follow-up. No remote push or merge.

Implemented native data-bound Compose Home/compact daily timeline/anchored task popup, Analytics common shell and Day/Week/Month/Year charts, Monday-first calendar, twelve-month hours chart, three-column Achievements and truthful zero/partial/earned rings. Existing timer engine, stopwatch, policies, reward calculations, repositories, protocol, archive, Index receipt and phone-authority behavior were not rewritten. Existing editing, All/expanded analytics, history/export and real navigation remain accessible.

Validation before packaging: 200 JVM tests and nine new physical S24 Ultra Compose UI tests passed; debug assembly, instrumented compilation and lint passed (0 errors, 23 warnings). Seven actual native screenshots were captured and automatically compared against the unchanged locked originals; rotation, large text and motion states were inspected. Generated full-page reference density differs from readable native fonts/safe areas, so lower cards scroll. Stored dates/values, real icons and navigation replace inconsistent fixture facts. No literal pixel-match claim.

Existing AchievementUiTest is 6/7 passing; `tile_opens_detail_and_chart_selection_and_history_work` fails on an obsolete lazy `Detailed history` label assertion, reproduced on baseline source (baseline 2/7 passing). Original test retained; new physical test verifies actual detail progress and inline histories by scrolling the real lazy list. Current evidence is recorded locally under artifacts/uiqa; private images/logs are excluded from the distributable source ZIP.

The installable ZIP adds the production `com.hax.focusdeck` debug APK. Its signing certificate SHA-256 matches the app installed on R3CX90NC7HR:

`a722bdbed1682ffe9309c84d0ee59e530d2282997b1d0d124236afad7355be00`

The packaged APK version, signature and ZIP integrity are checked locally. No APK installation is performed by packaging. Instructions: INSTALL_1.21.0.md. No device data reset, re-pairing, live synchronization, timer smoke session, sustained FPS or battery savings claim. No release signing key or private configuration is distributed.

Repackage with the existing key and SDK:

```bash
ANDROID_HOME=/home/hax/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/build-android.sh debug
bash scripts/package-release.sh --apk android/app/build/outputs/apk/debug/app-debug.apk
```

The packaging script uses the newer Android/desktop version, skips private artifacts and local caches/keys, preserves executable scripts and atomically writes the ZIP. Without `--apk` it produces a source-only archive.
