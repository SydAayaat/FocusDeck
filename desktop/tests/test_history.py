import csv
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from focusdeck.history import BLOCK_COLUMNS, SESSION_COLUMNS, csv_text, refresh_csv, export_archive
from focusdeck.reports import save_report
from test_focusdeck import report


class HistoryTests(unittest.TestCase):
    def test_csv_has_exact_task_times_and_session_id(self):
        r = report()
        r["title"] = 'A, "study"\nTopic'
        r["blocks"][0]["title"] = "=2+2"
        rows = list(csv.DictReader(io.StringIO(csv_text([r]))))
        self.assertEqual(list(rows[0]), BLOCK_COLUMNS)
        self.assertEqual(rows[0]["session_title"], r["title"])
        self.assertEqual(rows[0]["block_title"], "'=2+2")
        self.assertEqual(rows[0]["active_ms"], "40000")
        self.assertEqual(rows[0]["session_id"], r["session_id"])
        self.assertTrue(rows[0]["finished_at_utc"].endswith("Z"))

    def test_regeneration_deduplicates_retransmission_and_preserves_attempts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            save_report(root / "reports", report())
            save_report(root / "reports", report())
            second = report(); second["started_at_ms"] += 1
            save_report(root / "reports", second)
            records = refresh_csv(root / "reports", root / "exports")
            self.assertEqual(len(records), 2)
            rows = list(csv.DictReader(io.StringIO((root / "exports/all-sessions.csv").read_text(encoding="utf-8-sig"))))
            self.assertEqual(list(rows[0]), SESSION_COLUMNS)
            self.assertEqual(len(rows), 2)
            self.assertNotEqual(rows[0]["report_key"], rows[1]["report_key"])
            self.assertEqual((root / "exports/all-sessions.csv").stat().st_mode & 0o777, 0o600)

    def test_legacy_report_overtime_is_derived(self):
        r = report(); r["blocks"][0]["budget_ms"] = 30000
        rows = list(csv.DictReader(io.StringIO(csv_text([r], "sessions"))))
        self.assertEqual(rows[0]["overtime_ms"], "10000")

    def test_empty_stopped_session_is_not_lost_from_csv(self):
        r = report()
        r.update(status="stopped", blocks=[], active_ms=0, focused_ms=0, break_ms=0, paused_ms=0)
        rows = list(csv.DictReader(io.StringIO(csv_text([r]))))
        self.assertEqual(rows[0]["row_kind"], "session_without_blocks")
        self.assertEqual(rows[0]["session_status"], "stopped")
        self.assertEqual(set(rows[0]), set(BLOCK_COLUMNS))

    def test_offline_cli_exports_all_existing_reports_as_csv_and_json(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            save_report(root / "reports", report())
            env = dict(os.environ, FOCUSDECK_DATA=str(root))
            command = [sys.executable, "-m", "focusdeck.cli"]
            refresh = subprocess.run(command + ["refresh-logs"], env=env, capture_output=True, text=True)
            self.assertEqual(refresh.returncode, 0, refresh.stderr)
            output = root / "copy.json"
            export = subprocess.run(command + ["export", "--format", "json", "--output", str(output)], env=env, capture_output=True, text=True)
            self.assertEqual(export.returncode, 0, export.stderr)
            self.assertEqual(json.loads(output.read_text()), [report()])
            export_archive(root / "reports", root / "copy.csv", session_id="unmatched")
            self.assertEqual(len(list(csv.DictReader(io.StringIO((root / "copy.csv").read_text(encoding="utf-8-sig"))))), 0)

    def test_corrupt_archive_does_not_replace_good_csv_with_partial_history(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            save_report(root / "reports", report())
            refresh_csv(root / "reports", root / "exports")
            before = (root / "exports/all-blocks.csv").read_bytes()
            (root / "reports/broken.json").write_text("{broken")
            with self.assertRaises(ValueError): refresh_csv(root / "reports", root / "exports")
            self.assertEqual((root / "exports/all-blocks.csv").read_bytes(), before)

    def test_export_does_not_overwrite_authoritative_report_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "reports"
            with self.assertRaises(ValueError): export_archive(root, root / "history.json", "json")
