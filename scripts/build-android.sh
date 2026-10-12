#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-debug}"
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" && ! -f "$ROOT/android/local.properties" ]]; then
  echo "Set ANDROID_HOME to your Android SDK, or open android/ in Android Studio first." >&2; exit 1
fi
case "$mode" in
  debug) task=assembleDebug ;;
  visualtest) task=assembleVisualtest ;;
  release) task=assembleRelease ;;
  test) task=testDebugUnitTest ;;
  *) echo "Usage: $0 [debug|visualtest|release|test]" >&2; exit 2 ;;
esac
cd "$ROOT/android"
exec "$ROOT/android/gradlew" "$task"
