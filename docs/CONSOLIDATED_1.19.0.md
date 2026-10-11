# FocusDeck 1.19.0 consolidated source — 9 October 2026

Base: full 1.19.0 Index Tracker source. Includes the subsequent supplied mountain-ring logo in Android launcher source, desktop sidebar/tray, and KDE launcher; sidebar logo follows the saved palette. Internal desktop version matches package metadata. KDE installer uses the absolute focusdeck-mountain.png path, incorporating the PNG fix the user confirmed working, and refreshes kbuildsycoca6 when available. Updated GPT project instructions and Index workflow documents are included under docs/.

No new feature from another chat is included. No APK build, installation, live device checks or full test rerun performed for consolidation. Previous two Android legacy-test failures reported by the user remain unresolved; consolidation does not claim they pass. Shell syntax, launcher-generation Python syntax, archive integrity and included-patch verification run for this package.

For desktop: quit the running GUI through tray Quit, then bash scripts/setup-control-center.sh.
For Android: set JAVA_HOME and ANDROID_HOME on your PC and use the existing install-apk.sh with your usual serial/signing key. Do not uninstall or clear app data.

Carry this complete ZIP into the next implementation chat as the source baseline; do not start from the old unpatched 1.19.0 ZIP.
