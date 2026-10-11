# FocusDeck 1.14.0 — skip and end controls

Android versionCode 19; desktop 1.14.0. This user-authorized change supersedes the older no-skip requirement. Approved Home, achievement header/ring/progress and lower detail layouts are preserved.

| Clock-page three-dot option | Color | Behavior |
| --- | --- | --- |
| Skip block | Amber #FFC857 | Save skipped outcome and measured time; advance without DONE credit or reward. |
| End block | Red #FF6B6B | Confirm, save Ended early/stopped outcome and measured time; advance without DONE credit or reward. |
| End session | Red #FF6B6B | Confirm and close the attempt; save completed blocks/current partial effort in the report. |

Options appear only on stopwatch/countdown pages, including paused clocks. Block actions are disabled while waiting for Start; whole-session End remains available there. They are absent from Home, achievements and reward pages. +5 minutes is removed from the menu. Historical extensions/older CLI extend remain compatible; no new GPT plan fields or CLI block controls are introduced.

Advancing respects auto_advance: otherwise wait for Start. Skip has immediate effect; ends require confirmation. Pending commands disable actions. Menu/confirmation actions carry the observed state revision; repository checks it under the command mutex to prevent acting on a different session/block. Existing command receipts retain replay protection and state/receipt persistence before successful acknowledgment.

DONE alone grants completion. Skipped/ended blocks bypass performance/reward generation and completion sounds. Existing completed block rewards remain recorded. Actual monotonic active/paused/overtime measurements remain; current active focus counts toward history and automatic focus-time tracking. DONE-count achievements, manual objectives, progress and module completion cannot be inferred from skipped/ended tasks. Whole-session rewards/PBs require every planned block DONE, including breaks. Scoring retains eligible DONE countdown blocks and adds no planned-weight penalty.

A fully traversed attempt containing skip/end uses wire status stopped, not complete. Phone labels Finished with skips if skips alone prevented completion, otherwise Ended early. Block evidence distinguishes skipped from stopped; timeline adds block_skipped, closes its interval correctly, and preserves block_stopped/session_stopped. Not-started future blocks remain absent from actual results and are counted separately in the report header. Session CSV appends skipped_blocks while keeping existing columns in order. JSON, CSV, clipboard and archives preserve evidence.

Update the desktop companion from this source: old desktop validators reject skipped/block_skipped. Reports/sessions remain contract 1/2 with extended report enums; pairing/wire/achievement contracts and stored identity are unchanged. End session is final for that attempt; this release does not implement Save & exit/resume. Start a new approved session with a fresh UUID when starting again.

## Verification actually performed

- 43 focused desktop checks passed, no skips: skip/end regressions plus stopwatch, timeline and schemas. Covers skipped/ended evidence, reward rejection, exact intervals, schema validation, archive round-trip, CSV and existing legacy/mixed reports.
- Changed UI/model/timer/reward/history Kotlin and six new native regression cases passed source syntax parsing. CommandProcessor/DeckRepository retain parser limitations also present in the baseline; syntax parsing is not compilation.
- Android focused test/compile attempt stopped before Kotlin compilation: offline Android Gradle plugin com.android.application 8.9.2 unavailable. New native tests were not executed. No APK assembly or device/UI/FPS/LAN/upgrade execution.
- Git whitespace checks passed. Package/source checks are recorded during delivery; not native execution.
- No phone installation, data/signing/pairing reset, real history mutation or GitHub push.

## Install from the source update

Download the update ZIP to Downloads first. Run in the existing checkout, retaining its signing key, .git, local SDK configuration and data:

```bash
cd /home/hax/Projects/FocusDeck &&
unzip -o "$HOME/Downloads/FocusDeck-v1.14.0-skip-end-update.zip" -d . &&
bash scripts/setup-desktop.sh &&
ADB_SERIAL=R3CX90NC7HR bash scripts/install-apk.sh
```

Use the existing JDK/SDK environment. Desktop setup retains the existing configuration/archive and restarts the companion with new report validation. The default install script builds source on the PC, then adb install -r; it stops on build failure. Do not uninstall/clear data for an upgrade or signing mismatch.

GPT Project files are updated to 1.14.0. Replace the full Project Instructions source with its new version and paste the updated compact field; keep the existing reward catalog and subject sources.
