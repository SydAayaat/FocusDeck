import copy
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest

from focusdeck.common import validate_report, MAX_BYTES
from focusdeck.history import csv_text, refresh_csv
from focusdeck.reports import markdown, save_report
from focusdeck.timeline import intervals, metrics


def detailed_report():
    def event(seq, kind, ms, index, phase, active, paused, budget):
        return dict(seq=seq, kind=kind, at_ms=1_000_000 + ms, mono_ms=ms, boot=1,
                    block_index=index, phase=phase, active_ms=active, paused_ms=paused, budget_ms=budget)
    return dict(session_id="detailed", title="Study", status="complete", planned_ms=70_000, active_ms=40_000,
                focused_ms=30_000, break_ms=10_000, paused_ms=15_000, wall_ms=55_000, started_at_ms=1_000_000,
                finished_at_ms=1_055_000, timeline_coverage="complete", recorded_timezone="Europe/Berlin", planned_block_count=2,
                blocks=[dict(id="a", type="focus", title="First", planned_ms=60_000, budget_ms=60_000, active_ms=30_000, paused_ms=15_000,
                             started_at_ms=1_000_000, finished_at_ms=1_045_000, outcome="done"),
                        dict(id="b", type="break", title="Second", planned_ms=10_000, budget_ms=10_000, active_ms=10_000, paused_ms=0,
                             started_at_ms=1_045_000, finished_at_ms=1_055_000, outcome="done")],
                events=[event(1, "block_started", 0, 0, "active", 0, 0, 60_000),
                        event(2, "paused", 10_000, 0, "paused", 10_000, 0, 60_000),
                        event(3, "resumed", 20_000, 0, "active", 10_000, 10_000, 60_000),
                        event(4, "paused", 30_000, 0, "paused", 20_000, 10_000, 60_000),
                        event(5, "resumed", 35_000, 0, "active", 20_000, 15_000, 60_000),
                        event(6, "block_done", 45_000, 0, "active", 30_000, 15_000, 60_000),
                        event(7, "block_started", 45_000, 1, "active", 0, 0, 10_000),
                        event(8, "block_done", 55_000, 1, "active", 10_000, 0, 10_000),
                        event(9, "session_complete", 55_000, 1, "complete", 10_000, 0, 10_000)])


class TimelineTests(unittest.TestCase):
    def test_exact_pause_intervals_and_metrics_per_task(self):
        r = detailed_report(); validate_report(r)
        paused = [i for i in intervals(r) if i["kind"] == "paused"]
        self.assertEqual([i["duration_ms"] for i in paused], [10_000, 5_000])
        self.assertEqual((paused[0]["started_at_ms"], paused[0]["finished_at_ms"]), (1_010_000, 1_020_000))
        self.assertEqual(metrics(r, 0), dict(pause_count=2, extension_count=0, longest_active_ms=10_000, longest_pause_ms=10_000, average_pause_ms=7_500))
        self.assertEqual(metrics(r, 1)["pause_count"], 0)

    def test_extension_event_does_not_split_uninterrupted_active_interval(self):
        r = detailed_report()
        e = copy.deepcopy(r["events"][0]); e.update(kind="extended", at_ms=1_005_000, mono_ms=5_000, active_ms=5_000, budget_ms=360_000)
        r["events"].insert(1, e)
        for i, e in enumerate(r["events"], 1):
            e["seq"] = i
            if e["block_index"] == 0 and i > 1: e["budget_ms"] = 360_000
        r["blocks"][0]["budget_ms"] = 360_000
        validate_report(r)
        self.assertEqual(metrics(r, 0)["longest_active_ms"], 10_000)
        self.assertEqual(metrics(r, 0)["extension_count"], 1)

    def test_clock_rollback_uses_counters_instead_of_wall_span(self):
        r = detailed_report()
        for e in r["events"][2:]: e["at_ms"] -= 100_000
        r["finished_at_ms"] -= 100_000
        r["blocks"][0]["finished_at_ms"] -= 100_000
        r["blocks"][1]["started_at_ms"] -= 100_000; r["blocks"][1]["finished_at_ms"] -= 100_000
        r["wall_ms"] = 0
        # Monotonic active/paused data remains unchanged despite reversed wall timestamps.
        validate_report(r)
        pause = next(i for i in intervals(r) if i["kind"] == "paused")
        self.assertLess(pause["finished_at_ms"], pause["started_at_ms"])
        self.assertEqual(pause["duration_ms"], 10_000)

    def test_partial_timeline_never_claims_complete_behavior_metrics(self):
        r = detailed_report(); r["timeline_coverage"] = "partial"; r["events"] = r["events"][1:]
        for i, e in enumerate(r["events"], 1): e["seq"] = i
        validate_report(r)
        self.assertIsNone(metrics(r)["pause_count"])
        row = next(csv.DictReader(io.StringIO(csv_text([r], "sessions"))))
        self.assertEqual(row["timeline_coverage"], "partial"); self.assertEqual(row["pause_count"], "")

    def test_reboot_does_not_create_interval_across_boots(self):
        r = detailed_report(); r["timeline_coverage"] = "partial"
        for e in r["events"][2:]: e["boot"] = 2; e["mono_ms"] -= 20_000
        validate_report(r)
        self.assertEqual([i["duration_ms"] for i in intervals(r) if i["kind"] == "paused"], [5_000])

    def test_csv_and_markdown_preserve_timeline_and_unknown_legacy_metrics(self):
        r = detailed_report()
        events = list(csv.DictReader(io.StringIO(csv_text([r], "events"))))
        self.assertEqual(len(events), 9); self.assertEqual(events[1]["kind"], "paused")
        self.assertEqual(events[1]["block_id"], "a")
        spans = list(csv.DictReader(io.StringIO(csv_text([r], "intervals"))))
        self.assertEqual(sum(int(i["duration_ms"]) for i in spans), 55_000)
        self.assertIn("Pause count: 2", markdown(r)); self.assertIn("1970-01-01T00:16:50.000Z", markdown(r))
        legacy = {k: v for k, v in r.items() if k not in {"events", "timeline_coverage", "planned_block_count", "recorded_timezone"}}
        validate_report(legacy)
        row = next(csv.DictReader(io.StringIO(csv_text([legacy], "sessions"))))
        self.assertEqual(row["pause_count"], ""); self.assertEqual(row["timeline_coverage"], "unavailable")
        self.assertEqual(list(csv.DictReader(io.StringIO(csv_text([legacy], "events")))), [])

    def test_invalid_sequence_phase_and_totals_are_rejected(self):
        for change in (lambda r: r["events"][1].update(seq=10),
                       lambda r: r["events"][1].update(phase="active"),
                       lambda r: r["events"][5].update(active_ms=1),
                       lambda r: r.update(planned_block_count=1),
                       lambda r: r.update(timeline_coverage=[])):
            r = detailed_report(); change(r)
            with self.assertRaises(ValueError): validate_report(r)

    def test_all_four_master_logs_are_written_automatically(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); save_report(root / "reports", detailed_report())
            refresh_csv(root / "reports", root / "exports")
            self.assertEqual({p.name for p in (root / "exports").glob("*.csv")}, {"all-sessions.csv", "all-blocks.csv", "all-events.csv", "all-intervals.csv"})
            self.assertEqual(len(list(csv.DictReader(io.StringIO((root / "exports/all-events.csv").read_text(encoding="utf-8-sig"))))), 9)

    def test_bounded_phone_report_stays_within_wire_limit(self):
        r = detailed_report(); r["timeline_coverage"] = "partial"
        seed = r["events"][0]
        r["events"] = [dict(seed, seq=i + 1, mono_ms=10**15 - 2_000 + i,
                            at_ms=253402300799999, active_ms=10**12, paused_ms=10**12, budget_ms=10**12) for i in range(2_000)]
        r["title"] = "🧠" * 200
        r["blocks"] = [dict(r["blocks"][0], id="🧠" * 94 + f"{i:06d}", title="🧠" * 200) for i in range(100)]
        r.update(planned_block_count=100, planned_ms=6_000_000, active_ms=3_000_000,
                 focused_ms=3_000_000, break_ms=0, paused_ms=1_500_000)
        payload = dict(protocol_version=1, type="report", report_key="x" * 120, report=r)
        validate_report(r)
        self.assertLess(len(json.dumps(payload, ensure_ascii=False).encode()), MAX_BYTES)
