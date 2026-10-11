#!/usr/bin/env python3
"""Verify measurement units and rejection of misleading discharge rates."""
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from contextlib import redirect_stdout

spec = importlib.util.spec_from_file_location("battery_profile", Path(__file__).with_name("battery-profile.py"))
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)


def endpoint(level, charge, elapsed):
    raw = f"""Current Battery Service state:
  AC powered: false
  USB powered: false
  Wireless powered: false
  Charge counter: {charge}
  status: 3
  level: {level}
  scale: 100
  temperature: 300
"""
    return {"battery": profile.parse_battery(raw), "uptime_seconds": 5000 + elapsed,
            "timestamp": 1_000_000 + elapsed, "device_serial": "test-phone",
            "version": "1.2.0", "model": "test", "build": "test-build", "utc": "test"}


class MeasurementTests(unittest.TestCase):
    def test_real_discharge_units_and_elapsed_time(self):
        result = profile.summarize(endpoint(80, 4_000_000, 0), endpoint(76, 3_800_000, 3600))
        self.assertTrue(result["valid_endpoint_checks"])
        self.assertAlmostEqual(4, result["percentage_points_per_hour"])
        self.assertAlmostEqual(200, result["mah_per_hour"])

    def test_usb_charging_and_simulation_withhold_rates(self):
        for kind in ("plugged", "simulated"):
            start = endpoint(80, 4_000_000, 0)
            start["battery"][kind] = True
            result = profile.summarize(start, endpoint(76, 3_800_000, 3600))
            self.assertFalse(result["valid_endpoint_checks"])
            self.assertIsNone(result["mah_per_hour"])

    def test_short_run_or_reboot_withholds_rates(self):
        start = endpoint(80, 4_000_000, 0)
        short = endpoint(79, 3_950_000, 30)
        self.assertIsNone(profile.summarize(start, short)["percentage_points_per_hour"])
        end = endpoint(76, 3_800_000, 3600)
        end["uptime_seconds"] = 10
        self.assertFalse(profile.summarize(start, end)["valid_endpoint_checks"])

    def test_unsupported_counter_does_not_invent_mah(self):
        start, end = endpoint(80, 0, 0), endpoint(76, 0, 3600)
        result = profile.summarize(start, end)
        self.assertEqual(4, result["percentage_points_per_hour"])
        self.assertIsNone(result["mah_per_hour"])

    def test_unknown_charging_state_withholds_rate(self):
        start = endpoint(80, 4_000_000, 0)
        start["battery"] = profile.parse_battery("level: 80\nscale: 100\nstatus: 3")
        self.assertIsNone(profile.summarize(start, endpoint(76, 3_800_000, 3600))["mah_per_hour"])

    def test_wrong_device_or_version_withholds_rate(self):
        for key in ("device_serial", "version", "build"):
            start, end = endpoint(80, 4_000_000, 0), endpoint(76, 3_800_000, 3600)
            end[key] = "changed"
            self.assertFalse(profile.summarize(start, end)["valid_endpoint_checks"])

    def test_cli_endpoint_capture_and_comparison_with_fake_adb(self):
        class FakeAdb:
            finished = False
            def run(self, *args):
                if args == ("get-state",):
                    return "device\n"
                if args == ("shell", "cat", "/proc/uptime"):
                    return "8600 0\n" if self.finished else "5000 0\n"
                if args == ("shell", "getprop"):
                    return "[ro.serialno]: [test-phone]\n[ro.product.model]: [test]\n[ro.build.fingerprint]: [test-build]\n"
                if args == ("shell", "dumpsys", "package", profile.PACKAGE):
                    return "versionName=1.2.0\n"
                if args == ("shell", "dumpsys", "battery"):
                    level, charge = (76, 3_800_000) if self.finished else (80, 4_000_000)
                    return f"AC powered: false\nUSB powered: false\nWireless powered: false\nstatus: 3\nlevel: {level}\nscale: 100\nCharge counter: {charge}\n"
                return "diagnostic fixture\n"
        fake = FakeAdb()
        with tempfile.TemporaryDirectory() as temporary, patch.object(profile, "Adb", return_value=fake), \
                patch.object(profile.time, "time", side_effect=[1_000_000, 1_003_600]), redirect_stdout(io.StringIO()):
            root = Path(temporary) / "run"
            with patch.object(sys, "argv", ["battery-profile.py", "start", str(root), "--label", "test", "--mode", "dim"]):
                profile.main()
            fake.finished = True
            with patch.object(sys, "argv", ["battery-profile.py", "finish", str(root)]):
                profile.main()
            summary = json.loads((root / "summary.json").read_text())
            self.assertEqual(200, summary["measurement"]["mah_per_hour"])
            self.assertTrue((root / "finish/display.txt").exists())
            self.assertIn("whole-phone", (root / "summary.md").read_text().lower())
            with patch.object(sys, "argv", ["battery-profile.py", "compare", str(root)]):
                profile.main()


if __name__ == "__main__":
    unittest.main(verbosity=2)
