# FocusDeck 1.9.1 — achievement card management

This source-only patch continues the existing local 1.9.0 implementation. No GitHub operation, APK assembly, installation or PC service setup was performed. Android/desktop versions are 1.9.1; Android versionCode is 14. The application ID and existing signing setup are retained.

## Delivered controls

- Swipe **left** across the cards to advance All → Discipline → Health → Learning → Focus → Projects → College; right returns. Tabs remain tappable and the selected tab scrolls into view. Ends do not wrap.
- Long-press a card for layout/size/archive/delete options. **EDIT LAYOUT** enables hold-and-drag ordering; tap a card for its menu. Swipe is disabled while editing. Drop onto another visible card to move to its position. Use **Move earlier/later** for positions beyond the screen. Order is global across category filters.
- **Compact**, **Wide**, **Full-width** presets save independently per card. Compact uses paired tiles where space permits; Wide spans a row; Full-width also enlarges its ring/text. Narrow screens remain one column. No freeform resize handles are introduced.
- **Archive** keeps history and hides the card; **ARCHIVED → RESTORE** brings it back. **Delete permanently** requires the phone confirmation dialog and removes the card's own achievement evidence/layout. Study session logs and shared closed daily contracts/global focus totals remain. Current/future target references block removal until GPT replaces those targets.
- GPT can create custom manual cards in every category through command contract 2; the existing phone UI renders them without another rebuild. GPT supplies complete JSON for the CLI to execute; no cloud model connection or new tracking permission is added.

## CLI/GPT

```bash
focusdeck achievements archive german
focusdeck achievements restore german
focusdeck achievements delete german --yes

focusdeck achievements validate examples/achievements/create-health-card-v2.json
# Update its ID and timestamp before applying to the connected phone.
focusdeck achievements apply command.json
```

Existing apply/validate/list/history commands remain. Contract 1 retains scheduled project/college creation. Contract 2 extends upsert to all six categories with manual daily/scheduled/abstinence kinds and adds restore/confirmed deletion. Built-in measurement definitions cannot be replaced. Automatic focus rules, stopwatch/countdown math, rewards and session reports are unchanged.

The dispatcher references explicit `achievement-command-v1.schema.json` and `achievement-command-v2.schema.json`; wire 1 advertises achievement capabilities [1,2] and rejects contract-2 writes/replays to old phones. Session/report 1/2 and pairing 1 remain. Storage/snapshot 1 adds backward-default order/size/deleted-ID/retired-command fields. Deleted card IDs cannot be reused; redacted old per-card command IDs remain retired, preventing replay resurrection. Closed target revisions are retained for audit, so old references may remain.

Examples include health/learning/discipline creation and archive/restore/delete JSON. Use genuine current timestamps and fresh stable command IDs; definitions must exist before events/removal. GPT details are in `GPT_ACHIEVEMENTS_INSTRUCTIONS.md`; the general project instructions remain below 8,000 characters.

## Focused executed checks — 2026-10-07

| Check | Result |
| --- | --- |
| Native achievement engine JVM tests | **40 passed**, zero failures/errors/skips, including six new contract/layout/deletion regressions |
| Android main Kotlin/Compose and unit-test compilation | Passed as prerequisites of the focused JVM task; no APK assembled |
| Desktop achievement/schema/selected real TLS tests | **34 passed**, zero failures/skips; includes CLI confirmation, all-category/kind validation, schema dispatch, old-phone gating and pending delete replay after upgrade |
| Static checks | Python compilation and Git whitespace passed |
| Full suites / APK / lint / instrumentation | Not rerun for this source-only patch, per the user's fast-update preference |
| S24 Ultra / Ubuntu installation and live gestures/sync | Not executed here |

Native command: `bash android/gradlew testDebugUnitTest --tests com.hax.focusdeck.AchievementEngineTest --no-daemon` with the cached Java 17/SDK toolchain. Desktop command:

```bash
PYTHONPATH=desktop/src:desktop/tests python3 -m unittest \
  test_achievements test_schemas \
  test_focusdeck.TransportTests.test_achievement_command_ack_snapshot_and_offline_cache_over_existing_tls \
  test_focusdeck.TransportTests.test_achievement_pending_replay_is_gated_then_preserves_exact_payload \
  test_focusdeck.TransportTests.test_achievement_v2_pending_delete_is_gated_until_phone_upgrade -v
```

Desktop test dependencies were already available; Draft 2020-12 schema checks ran, rather than skipping. Raw focused results are `android-1.9.1-focused-results.txt` and `desktop-1.9.1-focused-results.txt`. Previous full 1.9.0 verification describes that historical build only.

## Modified-file map

| Area | Files |
| --- | --- |
| Card controls/UI | `AchievementCards.kt` (new), `AchievementScreen.kt` |
| Engine/persistence | `Achievements.kt`, `DeckRepository.kt` |
| Capability/command transport | `DeckNetwork.kt`, desktop `server.py` |
| CLI/validation | desktop `cli.py`, `achievements.py` |
| Contracts | Achievement dispatcher, explicit v1/v2 schemas, `wire.schema.json` |
| Tests | `AchievementEngineTest.kt`, `test_achievements.py`, `test_schemas.py`, selected transport tests in `test_focusdeck.py` |
| Examples | Six new contract-2 JSON files in `examples/achievements/` |
| Versions/docs | Android app Gradle config, desktop package versions, README, AGENTS, achievement/project instructions, workflow/setup/status/verification and this record |

## Your existing upgrade workflow

Overlay the ZIP's `FocusDeck/` source into `/home/hax/Projects/FocusDeck`, retaining its Git directory, local SDK configuration, signing/debug keys, PC credentials/config and archives. Build in place with your existing signing identity; do not uninstall or clear app data.

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

After upgrading, check swipe direction, dragging, all three sizes, long names/font scaling, archived restoration and persistence after reopen. Exercise permanent deletion only on a disposable custom card and verify its PC snapshot updates; also verify existing pairing/history remains. This patch compiled the UI but did not validate physical touch behavior on the S24 Ultra.
