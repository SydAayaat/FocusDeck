#!/usr/bin/env python3
"""Endpoint-only ADB measurements: no polling, charging simulation, or phone changes."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

PACKAGE = "com.hax.focusdeck"


def parse_battery(raw):
    values = {}
    for line in raw.splitlines():
        if ":" in line:
            key, value = line.strip().split(":", 1)
            values[key.lower()] = value.strip()
    def number(key):
        try:
            return int(values[key])
        except (KeyError, ValueError):
            return None
    powered = [values.get(f"{kind} powered") for kind in ("ac", "usb", "wireless")]
    return {
        "level": number("level"), "scale": number("scale"),
        "charge_uah": number("charge counter"), "temperature_tenths_c": number("temperature"),
        "plugged": None if any(x not in ("true", "false") for x in powered)
        else ("true" in powered or values.get("dock powered") == "true"),
        "status": number("status"), "simulated": "UPDATES STOPPED" in raw.upper(),
    }


class Adb:
    def __init__(self, serial):
        self.command = ["adb"] + (["-s", serial] if serial else [])

    def run(self, *args):
        result = subprocess.run(self.command + list(args), capture_output=True, text=True, timeout=45)
        if result.returncode:
            raise ValueError(result.stderr.strip() or result.stdout.strip() or "ADB command failed")
        return result.stdout


def snapshot(adb, root, name):
    folder = root / name
    folder.mkdir(exist_ok=True)
    # Sample the battery first; collect diagnostic dumps afterwards, never during the run.
    uptime = float(adb.run("shell", "cat", "/proc/uptime").split()[0])
    battery_raw = adb.run("shell", "dumpsys", "battery")
    stamp = time.time()
    (folder / "battery.txt").write_text(battery_raw)
    properties = adb.run("shell", "getprop")
    (folder / "properties.txt").write_text(properties)
    props = dict(re.findall(r"^\[([^]]+)\]: \[(.*)\]$", properties, re.M))
    package = adb.run("shell", "dumpsys", "package", PACKAGE)
    (folder / "package.txt").write_text(package)
    if "versionName=" not in package:
        raise ValueError("FocusDeck is not installed on the selected phone")
    diagnostics = {
        "batterystats.txt": ("dumpsys", "batterystats"),
        "app-batterystats.txt": ("dumpsys", "batterystats", PACKAGE),
        "gfxinfo.txt": ("dumpsys", "gfxinfo", PACKAGE),
        "cpuinfo.txt": ("dumpsys", "cpuinfo"),
        "power.txt": ("dumpsys", "power"),
        "display.txt": ("dumpsys", "display"),
        "brightness.txt": ("settings", "get", "system", "screen_brightness"),
        "brightness-mode.txt": ("settings", "get", "system", "screen_brightness_mode"),
    }
    for filename, args in diagnostics.items():
        try:
            raw = adb.run("shell", *args)
        except (ValueError, subprocess.TimeoutExpired) as error:
            raw = f"Unavailable: {error}\n"
        (folder / filename).write_text(raw)
    version = re.search(r"versionName=(\S+)", package)
    result = {
        "utc": datetime.fromtimestamp(stamp, timezone.utc).isoformat(),
        "timestamp": stamp, "uptime_seconds": uptime, "battery": parse_battery(battery_raw),
        "device_serial": props.get("ro.serialno"), "model": props.get("ro.product.model"),
        "build": props.get("ro.build.fingerprint"), "version": version.group(1),
    }
    (folder / "snapshot.json").write_text(json.dumps(result, indent=2) + "\n")
    return result


def summarize(start, end):
    first, last = start["battery"], end["battery"]
    seconds = end["uptime_seconds"] - start["uptime_seconds"]
    issues = []
    if not start.get("device_serial") or start["device_serial"] != end.get("device_serial"):
        issues.append("Phone identity could not be matched.")
    if start.get("build") != end.get("build") or start.get("version") != end.get("version"):
        issues.append("The OS or app version changed during the measurement.")
    wall_seconds = end["timestamp"] - start["timestamp"]
    if seconds <= 0 or abs(seconds - wall_seconds) > 30:
        issues.append("A reboot or clock change interrupted the interval.")
    for battery in (first, last):
        if battery["simulated"]:
            issues.append("Battery readings were simulated; run adb shell dumpsys battery reset before a new test.")
        if battery["plugged"] is not False or battery["status"] != 3:
            issues.append("An endpoint was charging, plugged in, or not confirmed discharging. Use wireless ADB with USB unplugged.")
    if seconds < 1800:
        issues.append("The run is shorter than 30 minutes; repeat for 60–120 minutes for a useful battery comparison.")
    hours = seconds / 3600 if seconds > 0 else None
    loss = None
    if all(b["level"] is not None and b["scale"] is not None and b["scale"] > 0 for b in (first, last)):
        loss = first["level"] / first["scale"] * 100 - last["level"] / last["scale"] * 100
        if loss < 0:
            issues.append("Battery level increased during the run.")
    else:
        issues.append("Battery level or scale is unavailable.")
    charge_loss = None
    if all(b["charge_uah"] is not None and 0 < b["charge_uah"] < 100_000_000 for b in (first, last)):
        charge_loss = (first["charge_uah"] - last["charge_uah"]) / 1000
        if charge_loss < 0:
            issues.append("The charge counter increased; this is not a comparable discharge run.")
            charge_loss = None
    # Endpoint values alone cannot detect a charger connected midway through a test.
    valid = not issues
    return {
        "duration_minutes": seconds / 60, "level_loss_percentage_points": loss,
        "charge_loss_mah": charge_loss,
        "percentage_points_per_hour": loss / hours if valid and loss is not None else None,
        "mah_per_hour": charge_loss / hours if valid and charge_loss is not None else None,
        "valid_endpoint_checks": valid, "issues": list(dict.fromkeys(issues)),
    }


def fmt(value, unit=""):
    return "unavailable" if value is None else f"{value:.2f}{unit}"


def markdown(start, end, summary, label, mode):
    lines = [f"# FocusDeck battery run: {label}", "",
             f"Mode label: {mode} · Phone: {start['model']} · App: {start['version']}",
             f"Start: {start['utc']} · End: {end['utc']}", "",
             f"Duration: {fmt(summary['duration_minutes'], ' minutes')}",
             f"Whole-phone battery decrease: {fmt(summary['level_loss_percentage_points'], ' percentage points')}",
             f"Whole-phone charge decrease: {fmt(summary['charge_loss_mah'], ' mAh')}",
             f"Discharge rate: {fmt(summary['percentage_points_per_hour'], ' percentage points/hour')}",
             f"Charge rate: {fmt(summary['mah_per_hour'], ' mAh/hour')}", ""]
    if summary["issues"]:
        lines += ["Rates withheld:", ""] + [f"- {issue}" for issue in summary["issues"]] + [""]
    lines += ["These are whole-phone measurements, not isolated FocusDeck power or watts. "
              "The level is coarse and the charge counter may be unsupported or noisy. "
              "Endpoint checks do not prove that charging stayed off throughout the run. "
              "Keep the phone unplugged for the entire interval.", "",
              "Compare equal workloads, brightness, Wi-Fi/ADB connection, temperature and OS settings. "
              "Repeat runs and inspect batterystats for screen time, CPU, network, charging and other apps. "
              "gfxinfo totals are cumulative; compare start/end, not a single dump. "
              "Raw display/power dumps help check the actual refresh and brightness policy.", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    for action in ("start", "finish"):
        item = sub.add_parser(action)
        item.add_argument("directory", type=Path)
        item.add_argument("--serial", default=os.environ.get("ADB_SERIAL"))
        if action == "start":
            item.add_argument("--label", required=True)
            item.add_argument("--mode", choices=("normal", "dim", "screen-off", "baseline"), required=True)
            item.add_argument("--reset-stats", action="store_true", help="Erase system-wide Batterystats history before this run")
    comparison = sub.add_parser("compare")
    comparison.add_argument("directories", type=Path, nargs="+")
    args = parser.parse_args()
    try:
        if args.action == "compare":
            print("| Run | Mode | App | Minutes | Battery pp/hour | mAh/hour | Endpoint checks |")
            print("| --- | --- | --- | ---: | ---: | ---: | --- |")
            for directory in args.directories:
                data = json.loads((directory / "summary.json").read_text())
                s = data["measurement"]
                label = data["label"].replace("|", "/").replace("\n", " ")
                print(f"| {label} | {data['mode']} | {data['version']} | {fmt(s['duration_minutes'])} | "
                      f"{fmt(s['percentage_points_per_hour'])} | {fmt(s['mah_per_hour'])} | "
                      f"{'passed' if s['valid_endpoint_checks'] else 'invalid'} |")
            print("\nWhole-phone discharge; compare matched runs. Mode labels do not change app settings.")
            return
        adb = Adb(args.serial)
        adb.run("get-state")
        root = args.directory.expanduser().resolve()
        if args.action == "start":
            root.mkdir(parents=True, exist_ok=False)
            if args.reset_stats:
                adb.run("shell", "dumpsys", "batterystats", "--reset")
            snapshot(adb, root, "start")
            (root / "run.json").write_text(json.dumps({"label": args.label, "mode": args.mode,
                "reset_stats": args.reset_stats}, indent=2) + "\n")
            print(f"Started: {root}\nRun the selected mode unplugged for 60–120 minutes, then use finish. No polling is running.")
        else:
            if (root / "finish/snapshot.json").exists():
                raise ValueError("This run already has a finish snapshot; use a new directory for another run")
            meta = json.loads((root / "run.json").read_text())
            start = json.loads((root / "start/snapshot.json").read_text())
            end = snapshot(adb, root, "finish")
            summary = summarize(start, end)
            data = {**meta, "version": start["version"], "measurement": summary}
            (root / "summary.json").write_text(json.dumps(data, indent=2) + "\n")
            report = markdown(start, end, summary, meta["label"], meta["mode"])
            (root / "summary.md").write_text(report)
            print(report)
            print(f"Saved: {root / 'summary.md'}")
    except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        print(f"Battery measurement failed: {error}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
