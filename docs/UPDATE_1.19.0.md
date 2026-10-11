# FocusDeck 1.19.0 — Index Achievement System

Reconstructed from the saved 1.18.0 source after the interrupted workspace lost the first implementation. The old run's test counts are not reused as evidence for this reconstructed code.

Implemented: first-class Index category; reusable Hybrid/Progress-only topic ledger; exact numbered Markdown/text/JSON import; hierarchy validation, stable identity, unique leaf counts; explicit migration and guarded revision reconciliation; preserved original/numeric/streak evidence; replay-safe completion/reopen/re-completion with notes; derived percentage; independent daily activity; real gross/net daily topic bars, real milestones, selected evidence, inline history and lazy/paged large-index UI. Desktop Builder, Achievements, Overview/Analytics exports and read-only tablet synchronization share one implementation. Supplied logo is used for Android and desktop/tray/launcher assets. The approved upper Android detail layout and existing desktop shell are preserved.

Changed files: new Android IndexTracker.kt, IndexDetail.kt and IndexTrackerTest.kt; Achievement engine/store/command, detail UI and network capabilities; new desktop index_tracker.py, index-tracker.js and focused import/synchronization tests; desktop validators, GPT parser, Control Center, native choosers, UI/form schemas, server/viewer compatibility; command schema 5 and wire capabilities; Android/desktop versions/icons; Index and GPT documentation. Existing contracts 1–4 remain supported.

Executed focused verification:
- 11 Android Index JVM tests passed, zero failures/skips.
- 18 desktop Index import/validation/evidence and builder tests passed.
- One real TLS integration scenario passed: contract-5 desktop-to-phone delivery and exact command receipt, complete Index evidence to updated tablet, read-only write rejection (1008), old-tablet compatibility without modifying the source snapshot.
- Android main compilation and debug APK assembly succeeded. APK manifest confirms com.hax.focusdeck, versionCode 22, versionName 1.19.0; APK signature verified.
- Client rendering smoke verified real net corrections, 1,200-leaf counts, 100-row paging and Progress-only independence.
- Desktop Python compilation and changed JavaScript syntax checks passed. Desktop wheel built.

Full existing suites, lint, instrumented UI/device execution and exhaustive Qt layout checks were deliberately not repeated at the user's request. Previous release results are historical, not proof of this rebuilt source. Pending physical checks: in-place upgrade using the current signing key, phone/tablet LAN reconnect and live evidence updates, KDE/Wayland/tray/icon/native chooser/clipboard behavior, responsive detail layout, large-index scrolling and Full/Reduced/Off motion. No measured 120 FPS claim.

The APK uses workspace debug certificate SHA-256 ca3a971fd2b0c31005798c6543b24f6e47b478e1d396899dad01a57332a3ec53. It differs from the older workspace APK; build on your PC with its existing key for an in-place upgrade if needed. Never uninstall or clear data as an upgrade workaround.

No actual Master Index was provided for PTfS or Custom Compute, so canonical counts remain unverified and existing real cards are untouched. No target installation, automatic card migration, history deletion/reset, credential change, re-pairing or GitHub push occurred. See INDEX_TRACKER.md for supported formats/bounds and the GUI migration/reconciliation workflow.
