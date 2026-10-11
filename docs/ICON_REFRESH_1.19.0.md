# 1.19.0 icon refresh

Supplied transparent PNG copied unchanged to desktop and Android source. KDE launcher SVG embeds that same PNG. Android launcher scales the image within its existing inset. Corrected stale desktop internal version string to match package metadata (1.19.0).

Verification: PNG byte equality, launcher XML/SVG parsing, package/internal version agreement, installer shell syntax. No Android build, full test suite, installation, or physical KDE/device checks performed for this asset-only update.

Quit the running desktop GUI through tray Quit before installing with `bash scripts/setup-control-center.sh`. Pairing and histories are retained. Android icon requires a future local APK build.

Control Center sidebar uses the supplied PNG alpha as a CSS mask and the existing saved --accent palette. Palette changes recolor it immediately without modifying image files, receipts or other settings. Android and KDE launchers retain the supplied white artwork. Uploaded sidebar screenshot identifies the placement; it is not used as an app icon.

Additional check: alpha transparency and CSS selector/palette wiring validated; physical KDE icon cache and launcher/Android appearance still require device verification.
