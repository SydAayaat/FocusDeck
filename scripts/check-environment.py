#!/usr/bin/env python3
"""Read-only prerequisite checks; never initialize pairing, install or delete data."""
from __future__ import annotations

import argparse
import importlib.util
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--desktop", action="store_true", help="Check only desktop prerequisites")
    args = parser.parse_args()
    failures = 0

    def check(ok: bool, label: str, hint: str = "") -> None:
        nonlocal failures
        print(f"{'OK' if ok else 'MISSING'}: {label}" + (f" — {hint}" if not ok and hint else ""))
        failures += not ok

    def warn(label: str) -> None:
        print(f"CHECK ON PC: {label}")

    check(sys.version_info >= (3, 10), "Python >= 3.10")
    check(importlib.util.find_spec("venv") is not None, "Python venv", "Install python3-venv")
    check(shutil.which("openssl") is not None, "OpenSSL", "Install openssl")
    # setup-desktop.sh installs the runtime package in its own venv.
    if importlib.util.find_spec("websockets") is None:
        warn("websockets is absent in this Python; setup-desktop.sh installs it for the service")
    else:
        import websockets
        check(14 <= int(websockets.__version__.split('.')[0]) < 17, "websockets >= 14, < 17")
    if not os.environ.get("XDG_RUNTIME_DIR"):
        warn("Run desktop setup in Hax's logged-in user session with XDG_RUNTIME_DIR")
    if not shutil.which("systemctl"):
        warn("systemctl --user is required to install the desktop service")
    if not shutil.which("wl-copy"):
        warn("KDE Wayland clipboard needs wl-copy (Ubuntu package wl-clipboard)")

    if not args.desktop:
        for tool in ("curl", "unzip"):
            check(shutil.which(tool) is not None, tool, f"Install {tool}")
        java_home = os.environ.get("JAVA_HOME")
        for tool in ("java", "javac"):
            path = str(Path(java_home) / "bin" / tool) if java_home else shutil.which(tool)
            if not path or not os.access(path, os.X_OK):
                check(False, f"JDK {tool}", "Export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64")
                continue
            try:
                result = subprocess.run([path, "-version"], capture_output=True, text=True, timeout=5)
                version = re.search(r'(?:version\s+"|javac\s+)(\d+)', result.stdout + result.stderr)
                check(result.returncode == 0 and version is not None and int(version[1]) >= 17,
                      f"{tool} >= 17", "Use the documented Java 17 JDK")
            except (OSError, subprocess.TimeoutExpired):
                check(False, f"JDK {tool}", "Cannot run version check")
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            # Match the build script's local.properties fallback; support common escaped paths.
            props = Path(__file__).resolve().parents[1] / "android/local.properties"
            if props.is_file():
                match = re.search(r'^\s*sdk\.dir\s*=\s*(.+?)\s*$', props.read_text(), re.MULTILINE)
                if match:
                    sdk = re.sub(r'\\(.)', r'\1', match[1])
        check(bool(sdk) and Path(sdk).is_dir(), "Android SDK", 'Export ANDROID_HOME="$HOME/Android/Sdk"')
        if sdk and Path(sdk).is_dir():
            root = Path(sdk)
            check((root / "platforms/android-35/android.jar").is_file(), "Android API 35 platform")
            check((root / "build-tools/35.0.0").is_dir(), "Android build-tools 35.0.0")
        # install-apk.sh needs adb on PATH even when the SDK contains it.
        adb = shutil.which("adb")
        check(adb is not None, "ADB on PATH", 'Add "$ANDROID_HOME/platform-tools" to PATH')
        if adb:
            warn("Confirm adb devices -l lists R3CX90NC7HR as device before installing")
        warn("First Gradle build requires network access or populated Gradle/Maven caches")

    print(f"\n{failures} missing prerequisite(s). No configuration or history changed.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
