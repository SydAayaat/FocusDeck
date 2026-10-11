# FocusDeck 1.7.1 — reward-page layout

The Android completion page follows the supplied monochrome reference:

- Pure black background and outlined timing, performance, reward and next-task cards.
- Planned, active, paused and wall time; ahead/behind delta; inline Pace and Focus metrics.
- Static white BeatScore ring with an open bottom and top seam, plus score, rank and benchmark status.
- Exact GPT reward text, filled REWARD DONE button and outlined CONTINUE button.
- Existing top-left history access, LAN status and menu.

Long text wraps and the page scrolls. Metrics wrap at larger system text sizes. No animations, new timer writes, scoring changes or reward-action changes are introduced.

This is an Android UI update. The desktop component stays at 1.7.0 and is compatible. Extract the source ZIP over your existing project, retaining local SDK settings and signing keys, then build and install:

```bash
cd /home/hax/Projects/FocusDeck
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
bash scripts/build-android.sh test &&
bash scripts/build-android.sh debug &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
```

Use `examples/text-reward-smoke-test.json` to check rewarded and unrewarded blocks, exact reward wording, NEXT titles and continuation. Check portrait/landscape and larger system font sizes on the S24 Ultra. Pairing and existing logs use the same app identity.

Validation: source diff checks and the existing desktop tests were run. Android compilation and on-device visual verification could not be run because this environment has no Android SDK or connected phone. No APK is included.
