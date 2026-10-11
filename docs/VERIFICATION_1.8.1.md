# Release verification · FocusDeck 1.8.1

Executed 2026-10-07 against the implementation developed from the uploaded 1.7.1 source. Android/desktop version is 1.8.1; session/report contract 2 is explicit, wire/pairing remains 1. See [STOPWATCH_MODE.md](STOPWATCH_MODE.md) for behavior, compatibility decisions and the modified-file map. The [1.7 verification record](VERIFICATION_1.7.md) and older test logs are historical evidence, not current results.

## Executed

- Desktop suite: **112 tests, 111 passed, 1 skipped**. Full output: [desktop-1.8.1-test-results.txt](desktop-1.8.1-test-results.txt). The host-restricted production AF_UNIX control-plane bind is the sole skip; other TLS/WebSocket/loopback and production daemon handler tests ran.
- **29 additional desktop checks** over the imported 83-test baseline: stopwatch plans/reports/completion rewards/milestones/selection/null metrics and validation; archive/CSV/Markdown/clipboard; full schemas; old-phone rejection and pending-v2 replay plus real TLS v2 report sync. A final zero-threshold regression preserves countdown eligibility for unknown metrics while refusing invented stopwatch metrics. Existing countdown mathematics, arbitrary reward text, deletion/tombstones, archive/history, timeline, command identity and legacy fixtures remain covered.
- The six new 1.8.1 regressions cover category totals and archive rejection, malformed reward evidence, native-compatible rounding at an inclusive reward boundary, countdown budget limits, schema limits and a real TLS connection that rejects invalid reports then accepts a valid report without reconnecting. These checks reproduced failures before the fix. The original 1.8.0 output is retained in [desktop-stopwatch-test-results.txt](desktop-stopwatch-test-results.txt).
- **Full Draft 2020-12 validation** ran for all 8 schemas, all 10 example plans and all 3 shared Android report fixtures. Invalid mode/duration/time-reward/break combinations and nullable stopwatch state/capability envelopes are checked. Production validators additionally enforce cross-block references, derived scores/counts and timing evidence.
- Schema tests used isolated official jsonschema v4.17.3 source at commit dbc398245a583cb2366795dc529ae042d10c1577 with its pyrsistent dependency because pip/network downloads were unavailable here. These tools were not vendored into the application. Runtime dependencies are unchanged; `desktop[test]` installs schema tooling for local verification, and CI installs this extra. Without it, six schema tests explicitly skip.
- Battery measurement fixtures: **7 passed**. They test measurement parsing/accounting, not physical battery savings.
- Actual CLI validate accepted all 10 example files and mixed-mode stdin JSON. The complete heredoc example in GPT_WORKFLOW.md also passed production validation. Existing start/report/copy/delete interfaces are covered by parser/handler tests; live service/phone commands remain a target-device check.
- Python compilation, shell syntax, JSON/XML parsing and Git whitespace checks passed. GPT Project Instructions are **7984 characters**.
- Read-only environment checker: desktop prerequisites pass, with logged-in user-session/KDE clipboard reminders. Full checker reports missing javac binary, Android SDK and ADB. Java runtime 17 is present.

## Native implementation and unexecuted tests

**89 Android JVM tests are supplied, including 16 stopwatch tests. None ran here.** New tests cover zero-start/no-expiry, pause/resume/DONE/DONE while paused, monotonic versus civil rollback, serialized persistence/recreation, reboot recovery, mixed weights and exact countdown scoring, completion/Focus Ratio rewards, module milestones, independent untimed session rewards, legacy countdown/overtime and shared report/CSV serialization. Existing countdown assertions only received required nullable unwraps; expected scores are unchanged.

`bash scripts/build-android.sh test` and `bash scripts/build-android.sh debug` were both attempted and exited at the missing Android SDK prerequisite check before Gradle compilation or test execution. Android lint, APK installation and Compose screenshots were not performed. Source inspection and desktop report validation do not prove native compilation or behavior.

## Remaining on Ubuntu / S24 Ultra

Follow [UPDATE_1.8.md](UPDATE_1.8.md): Android unit tests, debug build/lint, installation using the existing signing identity, portrait/landscape/large-font/dim view QA, background/recreation/reboot, phone private/visible-folder writes, live encrypted report/deletion sync and actual KDE clipboard. Run the skipped AF_UNIX control-plane test in the actual Ubuntu user session.

Pairing, history and installed app data on Hax's devices were not accessed or changed. Tests used disposable archives. No repository push, user-service setup, phone installation, real-history deletion or measured power experiment was performed. The package is source only; no compiled APK is included.

## Timing limitation retained

V2 block wall duration uses observed monotonic active+paused counters. Reboot gaps are marked partial and unobserved device-off time is not invented. Top-level session wall duration still uses civil finish-minus-start and includes result/reward waits; clock changes can affect it. Stopwatch never receives a hidden target or fake time score.
