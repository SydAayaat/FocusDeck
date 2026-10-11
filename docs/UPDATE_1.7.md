# Update to FocusDeck1.7

GPT may now supply any short reward description and score ranges. No required catalog. Update BOTH desktop and phone before using text rewards; legacy plans/reports remain readable.

Extract FocusDeck-v1.7.0.zip over /home/hax/Projects/FocusDeck, preserving local.properties and signing keys. Pairing/history are retained.

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

Replace GPT Project instructions with docs/GPT_PROJECT_INSTRUCTIONS.md.

```bash
focusdeck validate examples/text-reward-smoke-test.json
focusdeck start examples/text-reward-smoke-test.json
```

1. Let block1 exceed30s: still active until DONE, no reward.
2. Finish block2 around30s: Beat95–104.99 displays token text;105–111 displays pull-ups/token text. Other scores unlock none. Press CONTINUE without exercising during testing.
3. After completion/sync: `focusdeck copy-report --session b3f7ac79-8da4-44d0-b6a3-a4cd87920443`.
4. Verify exact wording, tier, ranges and acknowledgement in clipboard report and CSVs.

No APK is included: Android cannot be compiled in this delivery environment. Run tests/build on your PC and verify display, continuation and sync on the S24 Ultra before long sessions.

## Reward-page visual update

The completion page now follows the supplied monochrome reference: outlined planned/active/paused/wall metrics, performance/pace/focus panel, static BeatScore gauge, reward and next-task cards, filled REWARD DONE and outlined CONTINUE buttons. History and LAN controls remain in the existing top bar. Reward wording stays exactly as supplied by GPT; the coin illustration is decorative and does not restrict reward types. Session rewards keep their weighted score, class counts and benchmark information. No-reward results keep the existing brief automatic continuation.

The gauge uses the existing 0–111 BeatScore scale and draws once per state change, with no animation loop. Long titles and rewards wrap; the page scrolls for smaller screens and larger text sizes. Device visual verification is still required.
