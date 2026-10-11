# Release verification · FocusDeck1.7

Date:2026-10-07. Source release; no APK. Android SDK/Gradle/Kotlin compiler and connected phone are unavailable here. Rebuild both components and smoke-test on Hax's PC/S24 Ultra.

## Executed

- Desktop suite:83 tests;82 passed,1 skipped (host prohibits the production AF_UNIX socket). Output: desktop-test-results.txt.
- Six new regression checks cover arbitrary GPT text without an activity catalog, inclusive block/session range boundaries, disabled/unmatched/PB tiers, invalid/reversed ranges and mixed metadata, exact receipt-text comparison, CSV/Markdown/clipboard archive preservation.
- Existing authentication/TLS/reconnect/report-sync/history/deletion/clipboard/timeline/legacy reward tests pass.
- Production validators accept every example plan, legacy shared native report fixtures and the text example. Worked report selection/arithmetic checks give session score73.75 and BeatScore100.57.
- Python syntax, JSON/XML parsing, schema reference/field consistency and matching text/range definitions in session/report schemas checked. Shell scripts pass bash syntax checks. GPT instructions are7951 characters.
- Native reward engine/models and changed JVM test delimiters checked and source inspected. This is not Android compilation.
- No full JSON Schema engine available: schema checks are structural plus production runtime validation.
- No real logs deleted; archive tests use temporary files. No measured power/battery claims.

## Pending on target devices

- Android compilation/lint and all73 supplied JVM checks (4 new native text/range/report tests). Not executed here.
- Exact reward wording/line breaks in Compose, result acknowledgements/continuation, background/rotation, phone storage and live encrypted phone-PC synchronization.
- Actual clipboard on KDE Wayland and installation/service restart.
- See UPDATE_1.7.md for build/install and disposable text-reward smoke test.
