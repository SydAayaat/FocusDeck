from __future__ import annotations
import datetime as dt
import hashlib
from pathlib import Path
from .common import atomic_json, validate_report
from .timeline import intervals, metrics, ratio
from .rewards import summary as reward_summary

def utc(ms):
    return (dt.datetime(1970, 1, 1, tzinfo=dt.timezone.utc) + dt.timedelta(milliseconds=ms)).isoformat(timespec="milliseconds").replace("+00:00", "Z")

def duration(ms):
    if ms is None: return "N/A"
    seconds = ms // 1000
    return f"{seconds // 3600}h {(seconds % 3600) // 60:02d}m {seconds % 60:02d}s"

def markdown(report):
    finished = utc(report["finished_at_ms"])
    lines = [f"# FocusDeck session report: {report['title']}", "",
             f"Session ID: {report['session_id']}", f"Report key: {report['session_id']}:{report['started_at_ms']}", f"Started (UTC): {utc(report['started_at_ms'])}", f"Status: {report['status']}",
             f"Finished (UTC): {finished}", "",
             f"Planned: {duration(report['planned_ms'])}",
             f"Actual active time: {duration(report['active_ms'])}",
             f"Focused timer time: {duration(report['focused_ms'])}",
             f"Break timer time: {duration(report['break_ms'])}",
             f"Paused: {duration(report['paused_ms'])}",
             f"Overtime beyond block budgets: {duration(report.get('overtime_ms', sum(max(0, b['active_ms'] - b['budget_ms']) if b.get('budget_ms') is not None else 0 for b in report['blocks'])))}",
             f"Wall time: {duration(report['wall_ms'])}", "",
             "| Block ID | Block | Type | Timer mode | Planned | Budget incl. extensions | Actual active | Overtime | Paused | Outcome |",
             "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for b in report["blocks"]:
        title = b["title"].replace("|", "/").replace("\n", " ")
        bid = b['id'].replace('|', '/').replace('\n', ' ')
        overtime = b.get('overtime_ms', max(0, b['active_ms'] - b['budget_ms']) if b.get('budget_ms') is not None else 0)
        lines.append(f"| {bid} | {title} | {b['type']} | {b.get('timer_mode','countdown')} | {duration(b['planned_ms'])} | {duration(b['budget_ms'])} | {duration(b['active_ms'])} | {duration(overtime)} | {duration(b['paused_ms'])} | {b['outcome']} |")
    if report.get('report_version',1)==2:
        lines += ['', f"Report contract: v2; known timed planned portion: {duration(report['timed_planned_ms'])}",
                  f"Completed focus time: {duration(sum(b['active_ms'] for b in report['blocks'] if b['type']=='focus' and b['outcome']=='done'))}",
                  f"Completed focus blocks: {(report.get('competition') or {}).get('completed_focus_blocks',0)}",
                  f"Overall Focus Ratio: {(report.get('competition') or {}).get('focus_ratio')}%",
                  'Stopwatch blocks have no planned duration, deadline, Pace, time-based BeatScore or overtime.']
    if any(b["outcome"] in ("skipped", "stopped") for b in report["blocks"]):
        skipped = sum(b["outcome"] == "skipped" for b in report["blocks"])
        ended = sum(b["outcome"] == "stopped" for b in report["blocks"])
        remaining = max(0, (report.get("planned_block_count") or len(report["blocks"])) - len(report["blocks"]))
        label = "Finished with skips" if skipped and not ended and not remaining else "Ended early"
        lines += ["", f"Outcome: {label}; {skipped} skipped, {ended} ended early, {remaining} not started.", "Skipped/ended blocks retain measured focus time without DONE credit or block rewards."]
    lines.append(reward_summary(report))
    coverage = report.get("timeline_coverage", "unavailable")
    lines += ["", f"Timeline coverage: {coverage}", f"Recorded time zone: {report.get('recorded_timezone') or 'unknown'}",
              f"Actual/planned active ratio: {ratio(report['active_ms'], report['planned_ms'])}",
              f"Paused share of active + paused: {ratio(report['paused_ms'], report['active_ms'] + report['paused_ms'])}"]
    m = metrics(report)
    if m["pause_count"] is not None:
        lines += [f"Pause count: {m['pause_count']}", f"Extensions: {m['extension_count']}",
                  f"Longest uninterrupted timer stretch: {duration(m['longest_active_ms'])}",
                  f"Longest pause: {duration(m['longest_pause_ms'])}", f"Average pause: {duration(m['average_pause_ms'])}", "",
                  "| Block | Pauses | Extensions | Longest active [ms] | Longest pause [ms] | Average pause [ms] |",
                  "| --- | --- | --- | --- | --- | --- |"]
        for index, b in enumerate(report["blocks"]):
            bm = metrics(report, index)
            lines.append(f"| {b['id'].replace('|', '/').replace(chr(10), ' ')} | {bm['pause_count']} | {bm['extension_count']} | {bm['longest_active_ms']} | {bm['longest_pause_ms']} | {bm['average_pause_ms']} |")
    if report.get("events"):
        lines += ["", "Activity intervals (UTC; durations from monotonic counters):", "",
                  "| Block | Activity | From (UTC) | To (UTC) | Duration [ms] |",
                  "| --- | --- | --- | --- | --- |"]
        for span in intervals(report):
            lines.append(f"| {span['block_index'] + 1} | {span['kind']} | {utc(span['started_at_ms'])} | {utc(span['finished_at_ms'])} | {span['duration_ms']} |")
        lines += ["", "Event sequence (UTC; use sequence order if the wall clock changes):", "",
                  "| Seq | At (UTC) | Block | Event | Active [ms] | Paused [ms] | Budget [ms] |",
                  "| --- | --- | --- | --- | --- | --- | --- |"]
        for e in report["events"]:
            lines.append(f"| {e['seq']} | {utc(e['at_ms'])} | {e['block_index'] + 1} | {e['kind']} | {e['active_ms']} | {e['paused_ms']} | {e['budget_ms']} |")
        if coverage != "complete":
            lines.append("Partial timeline: migration, reboot or the event limit left a gap. Missing intervals are not invented; full behavior metrics are unavailable.")
    else:
        lines.append("No individual pause/resume timestamps were recorded for this session.")
    lines += ["", "Timer time describes recorded session activity; it does not establish learning quality or attention.",
              "V1.1 blocks run in overtime until DONE. Legacy timed_out blocks are not confirmed completed tasks.", "",
              "GPT feedback request: Match the Session ID to the original plan. Compare planned/actual/overtime and adjust countdown targets only. For stopwatch blocks assess DONE deliverables, actual duration and Focus Ratio without inventing time targets. Ask me for recall or task-quality evidence before judging learning performance.", ""]
    return "\n".join(lines)

def save_report(root: Path, report):
    validate_report(report)
    # Hash untrusted identifiers: no path traversal, and deterministic retransmission.
    name = hashlib.sha256((report["session_id"] + ":" + str(report["started_at_ms"])).encode()).hexdigest()[:24]
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    atomic_json(root / f"{name}.json", report)
    text_path = root / f"{name}.md"
    # The JSON is the authoritative atomic record; Markdown is regenerated on each delivery.
    text_path.write_text(markdown(report))
    text_path.chmod(0o600)
    return name
