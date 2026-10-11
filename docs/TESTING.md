# Verification checklist

Current 1.8 results are in [VERIFICATION.md](VERIFICATION.md); version-specific counts below are historical. Add the stopwatch/mixed-mode physical checks in [UPDATE_1.8.md](UPDATE_1.8.md). Install `./desktop[test]` into a test venv so optional full JSON Schema checks execute; CI installs this extra.

Automated desktop tests exercise actual TLS WebSocket sockets and the production daemon message handlers. JVM tests target the pure Kotlin timer. Physical checks are required for Compose, One UI service lifecycle and the complete installation path.

## Automated

```bash
./scripts/test-desktop.sh
python3 scripts/test-battery-profile.py
./scripts/build-android.sh test
./scripts/build-android.sh debug
```

The desktop test suite has no pytest dependency. It requires the runtime websockets package and OpenSSL. `test_unix_control_plane` skips only if the host forbids AF_UNIX; that skip is a remaining verification requirement, not a passed socket test. A separate loopback transport test covers framing and errors through the production control handler.

## Physical S24 Ultra / Ubuntu checks

| Test | Procedure | Expected |
| --- | --- | --- |
| Installation and pairing | Run setup, build/install APK, paste pairing | User service starts, LAN indicator connected, `devices` lists one phone |
| Single block | Start quick-focus example | Large timer, task, block 1/1, predicted finish |
| Multi-block / focus + break | Start smoke-test example | DONE drives ordered transitions; yellow break; final report after final DONE |
| DONE early | Tap DONE at about 10 seconds | Records about 10 seconds actual, keeps 30 seconds planned, next starts immediately |
| Pause/resume | Pause for 10 seconds, resume | Remaining unchanged during pause; report includes paused time |
| Desktop controls | pause/resume/next/extend/stop via CLI | Same state changes as phone, useful status and exit codes |
| Rotation | Rotate at least twice while active | No reset; landscape layout usable |
| Background / foreground | Leave app for 20 seconds, return | Remaining accounts for elapsed time, not rendered frames |
| Screen sleep | Sleep longer than several smoke blocks | Same block in overtime; no following tasks start without DONE; expiry alert may be delayed |
| App recreation | Background app, kill cached process where feasible, reopen | Durable timer resumes from same monotonic deadline; no new session |
| Force-stop | Force-stop via app settings, reopen later | No connection during force-stop; saved timer catches up on reopen |
| Reboot | Reboot during a focus block, reopen | Recovery is paused at latest checkpoint with explanatory notice |
| Wi-Fi loss | Turn Wi-Fi off during focus | Local timer/DONE/pause work, offline label, reports retained |
| Wi-Fi return | Re-enable same Wi-Fi | Reconnect/backoff resolves, phone state wins, report saved once |
| Daemon restart | `systemctl --user restart focusdeck.service` during focus | Phone timer continues; current state replaces desktop cache |
| Lost ACK / duplicate command | Interrupt daemon after delivery, reconnect | Same pending UUID replay does not perform a second action |
| Report resend | Press SEND REPORT twice | Same deterministic desktop JSON file; no duplicate history record |
| Malformed JSON | Validate JSON with missing fields/duplicates/negative duration | CLI exits 2; no plan sent |
| Wrong token | Modify phone pairing token then connect | Authentication rejected; active timer still works |
| Wrong certificate pin | Change phone pin then connect | TLS rejected; no token/session accepted by impostor |
| PC IP change | Restart daemon on new private IP; edit pairing host | Same pin/token accepted and active timer retained |
| Second phone | Connect another device UUID | Rejected until explicit local forget-device |
| Timer accuracy | Compare remaining after a 60-second reference interval | Expected subsecond display rounding plus normal scheduling jitter, no accumulated per-tick drift |
| Manual advance | Run manual-advance example | Expiry counts overtime; DONE then waits on START; waiting does not count as next focus |
| Report clipboard/GPT | Copy summary and retrieve `reports --latest` | Plan ID and planned/actual/overtime/focused/break/paused time preserved |

Exact foreground/background alert delivery and One UI battery behavior cannot be established with the JVM tests. Run a realistic two-hour session after the smoke tests before using it for important study sessions.

## V1.2 power and display checks

Follow [UPDATE_1.2.md](UPDATE_1.2.md) for the new display/lifecycle smoke checks and [BATTERY.md](BATTERY.md) for baseline, normal, dim and screen-off comparisons. The battery script's synthetic fixtures validate parsing, units, charging/simulation detection, minimum duration, reboot/version/device checks, missing counters and the complete start/finish/compare file workflow. They do not establish savings on a phone. Four new Kotlin checks cover idle scheduling, slower background/paused work, report cadence, and exact overtime/DONE accounting after sparse ticks. The Android suite now contains 21 tests.

## V1.3 history and automatic storage checks

Follow [UPDATE_1.3.md](UPDATE_1.3.md) to check the visible phone folder, automatic JSON/CSV writes, offline phone retention and later PC synchronization, duplicate resends, restart persistence and imports. Eight added Kotlin checks cover Monday/year boundaries, leap-month navigation, local finish dates, DST, attempts/deduplication, 151-session history, CSV escaping/precision and import-total validation. The total Android suite is 29 tests, pending execution here. Desktop history checks run in the normal test suite: 31 total, 30 passed and one Unix-socket skip on this host.

## V1.4 home and detailed timeline checks

Follow [UPDATE_1.4.md](UPDATE_1.4.md) for Home after completion/reopening, on-demand reports, repeated pause/resume, DONE while paused, next-task timestamps, manual waiting, detailed report copies and all four automatic CSVs. Eight more Kotlin tests bring the Android suite to 37, pending compilation/execution here. The desktop suite now has 41 tests, 40 passed and one host-restricted Unix-socket skip. Detailed TLS delivery, interval/metric accounting, clock rollback, reboot/legacy/partial handling and the message limit are exercised.

## V1.5 rewards

The pure Kotlin reward suite adds 20 checks, including strict history comparability, quality/score gate, capped speed/recalibration, optional reward/manual progression, daily volume/variety/day boundaries, session aggregate/reward/PB, caffeine duration/cutoff/spacing, recovery and command replay. The shared competitive-report.json resource is decoded/validated by Android and Python tests to check arithmetic/schema/CSV compatibility. These Android tests have not executed in the delivery host. Follow UPDATE_1.5.md on the S24 Ultra before relying on a long session; desktop's score/report validation alone does not execute Android reward selection.

## V1.6 conditional rewards and deletion

V1.6 supersedes the V1.5 quality/skip workflow above. Follow UPDATE_1.6.md for the current phone flow and disposable-log deletion checks. The desktop suite has 77 tests (76 passed, one host-restricted Unix socket skip). The Android suite has 69 provided JVM checks, pending execution: revised DONE/reward tests plus 12 conditional policy, class/weight, session eligibility, report validation and deletion-identity/cutoff checks. Clipboard helpers are tested with mocked desktop processes; live KDE clipboard and Android provider deletion must be checked on Hax's devices. No user history was deleted during implementation.
