"""Complete report archive and deterministic CSV snapshots (milliseconds, UTC)."""
from __future__ import annotations
import csv
import datetime as dt
import io
import json
import os
from pathlib import Path
import tempfile
from .common import read_json, validate_report
from .timeline import metrics, intervals, ratio
from .rewards import REWARD_COLUMNS, SESSION_REWARD_COLUMNS, csv_values

BEHAVIOR_COLUMNS = ["timeline_coverage", "pause_count", "extension_count", "longest_active_ms", "longest_pause_ms", "average_pause_ms", "actual_to_planned_ratio", "paused_share"]

SESSION_COLUMNS = ["report_key", "session_id", "session_title", "status", "started_at_utc", "finished_at_utc",
                   "planned_ms", "active_ms", "focused_ms", "break_ms", "overtime_ms", "paused_ms", "wall_ms",
                   "done_blocks", "stopped_blocks", "timed_out_blocks", "planned_block_count", "marked_done_fraction", "recorded_timezone"] + BEHAVIOR_COLUMNS + REWARD_COLUMNS + SESSION_REWARD_COLUMNS + ["report_version", "score_scope", "timed_planned_ms", "planned_countdown_focus_blocks", "planned_stopwatch_focus_blocks", "skipped_blocks"]
BLOCK_COLUMNS = ["report_key", "session_id", "session_title", "session_status", "session_started_at_utc",
                 "session_finished_at_utc", "row_kind", "block_index", "block_id", "block_title", "block_type",
                 "started_at_utc", "finished_at_utc", "planned_ms", "budget_ms", "active_ms", "overtime_ms", "paused_ms", "outcome"] + BEHAVIOR_COLUMNS + REWARD_COLUMNS + ["report_version", "timer_mode", "block_elapsed_wall_ms", "block_focus_ratio"]
EVENT_COLUMNS = ["report_key", "session_id", "session_title", "timeline_coverage", "recorded_timezone", "seq", "kind", "at_utc", "block_index", "block_id", "block_title", "phase", "mono_ms", "boot", "active_ms", "paused_ms", "budget_ms"]
INTERVAL_COLUMNS = ["report_key", "session_id", "session_title", "timeline_coverage", "recorded_timezone", "block_index", "block_id", "block_title", "activity_kind", "started_at_utc", "finished_at_utc", "duration_ms", "start_seq", "end_seq", "boot"]


def behavior_values(report, block_index=None):
    m = metrics(report, block_index)
    subject = report if block_index is None else report["blocks"][block_index]
    return [report.get("timeline_coverage", "unavailable")] + [m[key] for key in BEHAVIOR_COLUMNS[1:6]] + [ratio(subject["active_ms"], subject["planned_ms"]), ratio(subject["paused_ms"], subject["active_ms"] + subject["paused_ms"])]


def report_key(report):
    return f"{report['session_id']}:{report['started_at_ms']}"


def load_reports(root):
    from . import deletions
    tombstones=deletions.validate(read_json(Path(root).parent / "deletions.json",deletions.empty()))
    found = {}
    for path in sorted(Path(root).glob("*.json")):
        report = validate_report(read_json(path))
        if deletions.hides(tombstones,report):continue
        key = report_key(report)
        if key in found and found[key] != report:
            raise ValueError(f"Conflicting archive records for {key}")
        found[key] = report
    return sorted(found.values(), key=lambda r: (r["started_at_ms"], r["session_id"]))


def utc(ms):
    try:
        instant = dt.datetime(1970, 1, 1, tzinfo=dt.timezone.utc) + dt.timedelta(milliseconds=ms)
        return instant.isoformat(timespec="milliseconds").replace("+00:00", "Z")
    except (OverflowError, ValueError) as exc:
        raise ValueError("Report timestamp is outside the supported UTC calendar") from exc


def overtime(report):
    return report.get("overtime_ms", sum(max(0, b["active_ms"] - b["budget_ms"]) if b.get("budget_ms") is not None else 0 for b in report["blocks"]))


def spreadsheet_text(value):
    # Quote handling is separate from spreadsheet formula interpretation.
    if isinstance(value, str) and value.lstrip().startswith(("=", "+", "-", "@")):
        return "'" + value
    return value


def csv_text(reports, kind="blocks"):
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, lineterminator="\r\n")
    writer.writerow({"sessions": SESSION_COLUMNS, "blocks": BLOCK_COLUMNS, "events": EVENT_COLUMNS, "intervals": INTERVAL_COLUMNS}[kind])
    for r in reports:
        prefix = [report_key(r), r["session_id"], r["title"], r["status"], utc(r["started_at_ms"]), utc(r["finished_at_ms"])]
        if kind == "sessions":
            row = prefix + [r["planned_ms"], r["active_ms"], r["focused_ms"], r["break_ms"], overtime(r), r["paused_ms"],
                            r["wall_ms"]] + [sum(b["outcome"] == outcome for b in r["blocks"]) for outcome in ("done", "stopped", "timed_out")]
            count = r.get("planned_block_count")
            row += [count, ratio(sum(b["outcome"] == "done" for b in r["blocks"]), count) if count else "", r.get("recorded_timezone")] + behavior_values(r) + csv_values(r.get("competition"), True) + [r.get("report_version",1), (r.get("competition") or {}).get("score_scope", "all_focus"), r.get("timed_planned_ms", r["planned_ms"]), r.get("planned_countdown_focus_blocks"), r.get("planned_stopwatch_focus_blocks"), sum(b["outcome"] == "skipped" for b in r["blocks"])]
            writer.writerow([spreadsheet_text(v) for v in row])
        elif kind == "blocks":
            if not r["blocks"]:
                writer.writerow([spreadsheet_text(v) for v in prefix + ["session_without_blocks"] + [""] * 12 + behavior_values(r) + csv_values(None) + [r.get("report_version",1), None, None, None]])
            for index, b in enumerate(r["blocks"], 1):
                row = prefix + ["block", index, b["id"], b["title"], b["type"], utc(b["started_at_ms"]), utc(b["finished_at_ms"]),
                                b["planned_ms"], b["budget_ms"], b["active_ms"], b.get("overtime_ms", max(0, b["active_ms"] - b["budget_ms"]) if b.get("budget_ms") is not None else 0),
                                b["paused_ms"], b["outcome"]] + behavior_values(r, index - 1) + csv_values(b.get("performance")) + [r.get("report_version",1), b.get("timer_mode", "countdown"), b.get("wall_ms"), b.get("focus_ratio")]
                writer.writerow([spreadsheet_text(v) for v in row])
        else:
            timeline_prefix = [report_key(r), r["session_id"], r["title"], r.get("timeline_coverage", "unavailable"), r.get("recorded_timezone")]
            for item in r.get("events", []) if kind == "events" else intervals(r):
                block = r["blocks"][item["block_index"]] if item["block_index"] < len(r["blocks"]) else {}
                if kind == "events":
                    row = timeline_prefix + [item["seq"], item["kind"], utc(item["at_ms"]), item["block_index"] + 1, block.get("id"), block.get("title"), item["phase"], item["mono_ms"], item["boot"], item["active_ms"], item["paused_ms"], item["budget_ms"]]
                else:
                    row = timeline_prefix + [item["block_index"] + 1, block.get("id"), block.get("title"), item["kind"], utc(item["started_at_ms"]), utc(item["finished_at_ms"]), item["duration_ms"], item["start_seq"], item["end_seq"], item["boot"]]
                writer.writerow([spreadsheet_text(v) for v in row])
    return stream.getvalue()


def atomic_text(path, text, bom=False):
    path = Path(path)
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".export-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8-sig" if bom else "utf-8", newline="") as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.replace(tmp, path)
        directory = os.open(path.parent, os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


def refresh_csv(reports_root, exports_root):
    reports = load_reports(reports_root)
    for kind in ("blocks", "sessions", "events", "intervals"):
        atomic_text(Path(exports_root) / f"all-{kind}.csv", csv_text(reports, kind), bom=True)
    return reports


def export_archive(reports_root, output, format="csv", kind="blocks", session_id=None):
    output = Path(output).expanduser().resolve()
    if output.parent == Path(reports_root).resolve():
        raise ValueError("Choose an export destination outside the authoritative reports directory")
    reports = load_reports(reports_root)
    if session_id:
        reports = [r for r in reports if r["session_id"] == session_id]
    content = json.dumps(reports, ensure_ascii=False, indent=2) + "\n" if format == "json" else csv_text(reports, kind)
    atomic_text(output, content, bom=format == "csv")
    return {"ok": True, "file": str(output), "sessions": len(reports), "format": format, "kind": kind if format == "csv" else "reports"}
