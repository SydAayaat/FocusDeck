"""Behavioral timing evidence: event order and monotonic interval accounting."""
KINDS = {"block_started", "paused", "resumed", "extended", "block_done", "block_stopped", "block_skipped",
         "review_started", "quality_confirmed", "quality_incomplete", "reward_offered", "reward_acknowledged", "reward_skipped", "reward_continued", "session_reward_offered", "session_reward_acknowledged", "session_reward_skipped", "session_reward_continued", "review_recovered_after_reboot",
         "waiting_for_start", "session_complete", "session_stopped", "overtime_observed", "recovered_after_reboot"}
CLOSED = {"block_done", "block_stopped", "block_skipped", "session_complete", "session_stopped", "reward_acknowledged", "reward_skipped", "reward_continued", "session_reward_acknowledged", "session_reward_skipped", "session_reward_continued"}
EVENT_FIELDS = {"seq", "kind", "at_ms", "mono_ms", "boot", "block_index", "phase", "active_ms", "paused_ms", "budget_ms"}


def intervals(report):
    output = []
    events = report.get("events", [])
    for start, end in zip(events, events[1:]):
        if start["kind"] in CLOSED or start["boot"] != end["boot"] or start["block_index"] != end["block_index"]:
            continue
        kind = {"active": "active", "paused": "paused", "between": "waiting", "quality": "quality_review", "reward": "reward", "session_reward": "reward"}.get(start["phase"])
        if kind is None:
            continue
        counter = {"active": "active_ms", "paused": "paused_ms", "waiting": "mono_ms", "quality_review": "mono_ms", "reward": "mono_ms"}[kind]
        duration = end[counter] - start[counter]
        if duration < 0:
            continue
        interval = dict(kind=kind, block_index=start["block_index"], started_at_ms=start["at_ms"],
                        finished_at_ms=end["at_ms"], duration_ms=duration, start_seq=start["seq"], end_seq=end["seq"], boot=start["boot"])
        previous = output[-1] if output else None
        if previous and previous["kind"] == kind and previous["block_index"] == start["block_index"] and previous["boot"] == start["boot"] and previous["end_seq"] == start["seq"]:
            previous.update(finished_at_ms=end["at_ms"], duration_ms=previous["duration_ms"] + duration, end_seq=end["seq"])
        else:
            output.append(interval)
    return output


def metrics(report, block_index=None):
    if report.get("timeline_coverage", "unavailable") != "complete":
        return {key: None for key in ("pause_count", "extension_count", "longest_active_ms", "longest_pause_ms", "average_pause_ms")}
    events = [e for e in report.get("events", []) if block_index is None or e["block_index"] == block_index]
    spans = [i for i in intervals(report) if block_index is None or i["block_index"] == block_index]
    pauses = [i["duration_ms"] for i in spans if i["kind"] == "paused"]
    return dict(pause_count=sum(e["kind"] == "paused" for e in events), extension_count=sum(e["kind"] == "extended" for e in events),
                longest_active_ms=max((i["duration_ms"] for i in spans if i["kind"] == "active"), default=0),
                longest_pause_ms=max(pauses, default=0), average_pause_ms=sum(pauses) // len(pauses) if pauses else 0)


def ratio(numerator, denominator):
    return f"{numerator / denominator:.4f}" if denominator else ""


def validate_timeline(report):
    coverage = report.get("timeline_coverage", "unavailable")
    events = report.get("events", [])
    if not isinstance(coverage, str) or coverage not in {"complete", "partial", "unavailable"} or not isinstance(events, list) or len(events) > 2000:
        raise ValueError("Invalid timeline coverage or event count")
    if coverage == "unavailable" and events:
        raise ValueError("Unavailable timeline cannot contain events")
    count = report.get("planned_block_count")
    if count is not None and (type(count) is not int or not len(report["blocks"]) <= count <= 100):
        raise ValueError("Invalid planned block count")
    zone = report.get("recorded_timezone")
    if zone is not None and (not isinstance(zone, str) or not zone.strip() or len(zone) > 100):
        raise ValueError("Invalid recorded time zone")
    for index, e in enumerate(events, 1):
        if not isinstance(e, dict) or set(e) != EVENT_FIELDS:
            raise ValueError("Invalid timeline event fields")
        ranges = dict(seq=(index, index), at_ms=(0, 253402300799999), mono_ms=(0, 10**15), boot=(-1, 2**31-1),
                      block_index=(0, 99), active_ms=(0, 10**12), paused_ms=(0, 10**12), budget_ms=(0, 10**12))
        for field, (lower, upper) in ranges.items():
            if field == "budget_ms" and e[field] is None and report.get("report_version",1)==2: continue
            if type(e[field]) is not int or not lower <= e[field] <= upper:
                raise ValueError(f"Invalid event {field}")
        if not isinstance(e["kind"], str) or not isinstance(e["phase"], str) or e["kind"] not in KINDS or e["phase"] not in {"active", "paused", "between", "complete", "stopped", "quality", "reward", "session_reward"}:
            raise ValueError("Invalid event kind/phase")
        expected_phase = {"block_started": "active", "resumed": "active", "paused": "paused", "waiting_for_start": "between",
                          "review_started": "quality", "quality_confirmed": "quality", "quality_incomplete": "quality",
                          "reward_offered": "reward", "reward_acknowledged": "reward", "reward_skipped": "reward", "reward_continued": "reward",
                          "session_reward_offered": "session_reward", "session_reward_acknowledged": "session_reward", "session_reward_skipped": "session_reward", "session_reward_continued": "session_reward",
                          "session_complete": "complete", "session_stopped": "stopped", "recovered_after_reboot": "paused"}.get(e["kind"])
        if expected_phase and e["phase"] != expected_phase:
            raise ValueError("Event kind does not match phase")
        if count is not None and e["block_index"] >= count:
            raise ValueError("Event outside planned blocks")
    for start, end in zip(events, events[1:]):
        if start["boot"] == end["boot"] and end["mono_ms"] < start["mono_ms"]:
            raise ValueError("Event monotonic order is invalid")
    if coverage == "complete":
        if len({e["boot"] for e in events}) > 1 or any(e["kind"] == "recovered_after_reboot" for e in events):
            raise ValueError("A rebooted timeline must be marked partial")
        if report["status"] == "complete" and count is not None and count != len(report["blocks"]):
            raise ValueError("Completed report does not include all planned blocks")
        spans = intervals(report)
        for index, block in enumerate(report["blocks"]):
            starts = [e for e in events if e["block_index"] == index and e["kind"] == "block_started" and e["active_ms"] == 0 and e["paused_ms"] == 0 and e["at_ms"] == block["started_at_ms"]]
            expected = {"done": "block_done", "skipped": "block_skipped"}.get(block["outcome"], "block_stopped")
            finishes = [e for e in events if e["block_index"] == index and e["kind"] == expected and e["active_ms"] == block["active_ms"] and e["paused_ms"] == block["paused_ms"] and e["budget_ms"] == block["budget_ms"] and e["at_ms"] == block["finished_at_ms"]]
            if len(starts) != 1 or len(finishes) != 1:
                raise ValueError("Missing or conflicting block boundary events")
            for kind, counter in (("active", "active_ms"), ("paused", "paused_ms")):
                if sum(i["duration_ms"] for i in spans if i["block_index"] == index and i["kind"] == kind) != block[counter]:
                    raise ValueError("Timeline intervals do not match block totals")
        finish_kind = "session_complete" if report["status"] == "complete" else "session_stopped"
        if not events or events[-1]["kind"] != finish_kind or events[-1]["at_ms"] != report["finished_at_ms"]:
            raise ValueError("Missing session finish event")
