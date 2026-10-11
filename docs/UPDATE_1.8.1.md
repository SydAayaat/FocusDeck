# FocusDeck 1.8.1 local continuation

This patch continues the existing 1.8.0 Stopwatch implementation derived from the uploaded 1.7.1 source. Android/desktop versions are 1.8.1; Android versionCode is 11. Session/report contract 2 and wire/pairing contract 1 are unchanged. All work stayed in the local checkout; no remote update occurred.

## Fixed

- Desktop validation now requires focus and break totals to match their actual block categories. Contradictory reports cannot enter the archive or inflate history/exports. The native validator already checked these totals.
- Malformed v2 competition, performance and reward-policy data produces clear validation errors. A TLS regression verifies that three invalid reports are rejected without entering the archive or closing the connection, then a valid report is acknowledged and saved on that same connection.
- Block Focus Ratio uses the existing native-compatible rounding helper rather than Python's different rounding behavior. At the tested boundary, 107 ms active plus 3893 ms paused is 2.68%; the supplied inclusive 2.68 reward threshold remains eligible. A fabricated 2.67 block metric is rejected.
- Native, desktop and schema validators consistently enforce the existing v2 countdown limits: positive whole seconds, up to 86400 seconds. Runtime validation also requires budget >= original target. Stopwatch remains untimed; no duration or time score is introduced.
- Native structural checks run before derived timeline/reward evaluation to surface clearer invalid-report errors.

## Compatibility and modified files

Countdown mathematics and reward selection are unchanged. V1 reports keep their old duration bounds; valid existing history remains readable. Inconsistent category totals are rejected for both versions. No migration, archive deletion, pairing reset or app identity change is needed. Install over the existing app using the same signing key and retain PC configuration/history, as described in [UPDATE_1.8.md](UPDATE_1.8.md).

| Area | Files |
| --- | --- |
| Desktop validation | `desktop/src/focusdeck/common.py`, `stopwatch.py` |
| Native report validation | `android/app/src/main/java/com/hax/focusdeck/StudyHistory.kt` |
| Schema | `protocol/report-v2.schema.json` |
| Regressions | `desktop/tests/test_stopwatch.py`, `test_focusdeck.py`, `test_schemas.py`; Android `StopwatchTest.kt` |
| Versions | `android/app/build.gradle.kts`, `desktop/pyproject.toml`, `desktop/src/focusdeck/__init__.py` |
| Instructions and release record | `AGENTS.md`, `README.md`, `docs/PROJECT_STATUS.md`, `STOPWATCH_MODE.md`, `UPDATE_1.8.md`, `VERIFICATION.md`, this document and the new desktop test log |

The planning instructions and stopwatch/mixed examples from 1.8.0 still apply without changes. The full Stopwatch implementation file map is in [STOPWATCH_MODE.md](STOPWATCH_MODE.md).

## Checks

112 desktop tests collected: **111 passed, one host-restricted AF_UNIX skip**. All six schema checks ran, covering eight schemas, ten example plans and three shared report fixtures. Seven battery-tool fixture tests passed; this does not measure battery savings. See [VERIFICATION.md](VERIFICATION.md) and [desktop-1.8.1-test-results.txt](desktop-1.8.1-test-results.txt).

89 Android JVM tests are supplied, including 16 stopwatch tests, but remain unexecuted here. Both native build/test commands stop at the missing SDK prerequisite. APK/lint, phone UI, recovery on the device, visible-folder writes, live sync and KDE clipboard must still be checked on the target Ubuntu/S24 Ultra setup. The ZIP contains source, not an APK.
