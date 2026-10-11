#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
apk="${1:-$ROOT/android/app/build/outputs/apk/debug/app-debug.apk}"
command -v adb >/dev/null || { echo "Install Android platform-tools (adb) first." >&2; exit 1; }
# No explicit APK: build the current source on the user's PC, then update in place.
# An explicit APK path still installs directly, without rebuilding.
if [[ $# -eq 0 ]]; then bash "$ROOT/scripts/build-android.sh" debug; fi
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }
# ADB_SERIAL lets you choose the paired wireless device when more than one is connected.
if [[ -n "${ADB_SERIAL:-}" ]]; then exec adb -s "$ADB_SERIAL" install -r "$apk"; fi
exec adb install -r "$apk"
