#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$ROOT" "$@" <<'PY'
import argparse, os, pathlib, re, sys, zipfile
root = pathlib.Path(sys.argv[1])
p = argparse.ArgumentParser(description="Package source, optionally with an already-built Android APK.")
p.add_argument("--apk", type=pathlib.Path)
p.add_argument("--output", type=pathlib.Path, help="Optional destination for a source-only continuation package")
args = p.parse_args(sys.argv[2:])
desktop = re.search(r'^\s*version\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"\s*$', (root / "desktop/pyproject.toml").read_text(), re.MULTILINE)
android = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', (root / "android/app/build.gradle.kts").read_text())
if desktop is None or android is None:
    raise SystemExit("Cannot determine Android and desktop versions")
version = max(desktop.group(1), android.group(1), key=lambda s: tuple(map(int, s.split("."))))
if args.apk is not None and not args.apk.is_file():
    raise SystemExit("APK not found: " + str(args.apk))
output = args.output.resolve() if args.output else root.parent / f"FocusDeck-v{version}.zip"
temporary = output.with_suffix(".zip.tmp")
with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob("*")):
        if path.is_symlink() or not path.is_file() or any(part in {".git", ".gradle", ".kotlin", "build", "__pycache__", ".pytest_cache", ".venv", "dist", "battery-runs", "artifacts"} or part.endswith(".egg-info") for part in path.relative_to(root).parts):
            continue
        if path.name in {"local.properties", ".env"} or path.name.startswith(".env.") or path.suffix in {".jks", ".keystore", ".pyc", ".apk"}:
            continue
        name = "FocusDeck/" + str(path.relative_to(root))
        info = zipfile.ZipInfo.from_file(path, name)
        if path.suffix == ".sh" or path.name == "gradlew":
            info.external_attr = (0o100755 << 16)
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, path.read_bytes())
    if args.apk is not None:
        archive.write(args.apk, f"FocusDeck/apk/FocusDeck-v{android.group(1)}.apk")
os.replace(temporary, output)
print(output)
PY
