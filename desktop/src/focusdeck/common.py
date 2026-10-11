"""Bounded protocol validation, private durable files, and pairing configuration."""
from __future__ import annotations
import datetime as dt
import hashlib
import ipaddress
import json
import os
import re
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile

VERSION = 1
MAX_BYTES = 1024 * 1024

def config_dir() -> Path:
    return Path(os.environ.get("FOCUSDECK_CONFIG", str(Path.home() / ".config/focusdeck")))

def data_dir() -> Path:
    return Path(os.environ.get("FOCUSDECK_DATA", str(Path.home() / ".local/share/focusdeck")))

def socket_path() -> Path:
    base = Path(os.environ.get("XDG_RUNTIME_DIR", str(data_dir())))
    return base / "focusdeck.sock"

def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()

def atomic_json(path: Path, value) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".write-")
    try:
        with os.fdopen(fd, "w") as f:
            json.dump(value, f, ensure_ascii=False, indent=2, allow_nan=False)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
        directory = os.open(path.parent, os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)

def read_json(path: Path, default=None):
    if not path.exists():
        return default
    if path.stat().st_size > MAX_BYTES * 8:
        raise ValueError(f"File too large: {path}")
    return json.loads(path.read_text())

def text_field(value, name, limit=200):
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise ValueError(f"{name} must be a nonempty string of at most {limit} characters")
    return value

def integer(value, name, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"{name} must be an integer in {minimum}..{maximum}")
    return value

def validate_plan(plan):
    if not isinstance(plan, dict):
        raise ValueError("Session must be an object")
    allowed = {"protocol_version", "session_id", "title", "created_at", "blocks", "auto_advance", "reward_strategy", "session_reward", "comparison_key", "session_reward_policy", "classification", "session_scoring", "schedule"}
    if set(plan) - allowed:
        raise ValueError("Unknown session fields: " + ", ".join(sorted(set(plan) - allowed)))
    if plan.get("protocol_version") not in (1, 2) or type(plan.get("protocol_version")) is not int:
        raise ValueError("protocol_version must be 1 or 2")
    text_field(plan.get("session_id"), "session_id", 100)
    if plan["protocol_version"] == 2:
        import uuid
        try:
            if str(uuid.UUID(plan["session_id"])) != plan["session_id"].lower(): raise ValueError()
        except ValueError: raise ValueError("v2 session_id must be a fresh canonical UUID") from None
    text_field(plan.get("title"), "title")
    stamp = text_field(plan.get("created_at"), "created_at", 60)
    try:
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})", stamp):
            raise ValueError()
        parsed = dt.datetime.fromisoformat(stamp.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError()
    except ValueError:
        raise ValueError("created_at must be an ISO-8601 timestamp with timezone") from None
    if "auto_advance" in plan and type(plan["auto_advance"]) is not bool:
        raise ValueError("auto_advance must be boolean")
    from .analytics import validate_schedule
    validate_schedule(plan.get("schedule"))
    blocks = plan.get("blocks")
    if not isinstance(blocks, list) or not 1 <= len(blocks) <= 100:
        raise ValueError("blocks must contain 1..100 blocks")
    seen = set()
    total = 0
    for block in blocks:
        if not isinstance(block, dict) or not {"id", "type", "title"} <= set(block) or set(block) - {"id", "type", "title", "duration_seconds", "quality_criteria", "comparison", "reward_contract", "reward_policy", "session_weight", "classification", "timer_mode", "schedule", "category", "achievement_id", "index_topic_id"}:
            raise ValueError("Each block requires exactly id, type, title, duration_seconds")
        bid = text_field(block.get("id"), "block.id", 100)
        if bid in seen:
            raise ValueError("Duplicate block id: " + bid)
        seen.add(bid)
        if block.get("type") not in ("focus", "break"):
            raise ValueError("block.type must be focus or break")
        text_field(block.get("title"), "block.title")
        validate_schedule(block.get("schedule"))
        for metadata in ("category", "achievement_id", "index_topic_id"):
            if block.get(metadata) is not None: text_field(block[metadata], metadata)
        mode = block.get("timer_mode", "countdown")
        if plan["protocol_version"] == 1 and "timer_mode" in block:
            raise ValueError("timer_mode requires session protocol_version 2")
        if plan["protocol_version"] == 2 and block["type"] == "focus" and "timer_mode" not in block:
            raise ValueError("v2 focus blocks require explicit timer_mode")
        if mode not in ("countdown", "stopwatch"):
            raise ValueError("timer_mode must be countdown or stopwatch")
        if mode == "stopwatch":
            if block["type"] != "focus": raise ValueError("Break blocks retain countdown behavior")
            if block.get("duration_seconds") is not None: raise ValueError("Stopwatch must not have duration_seconds")
        else:
            total += integer(block.get("duration_seconds"), "countdown duration_seconds", 1, 86400)
    if total > 604800:
        raise ValueError("Session may not exceed seven days")
    from .rewards import validate_plan_rewards
    validate_plan_rewards(plan)
    from .stopwatch import validate_completion_plan
    validate_completion_plan(plan)
    return plan

def validate_report(report):
    if not isinstance(report, dict):
        raise ValueError("Report must be an object")
    version = report.get("report_version", 1)
    integer(version, "report_version", 1, 2)
    if version == 1 or report.get("planned_ms") is not None:
        integer(report.get("planned_ms"), "planned_ms", 0, 10**12)
    for key in ("session_id", "title", "status"):
        text_field(report.get(key), key)
    if report["status"] not in ("complete", "stopped"):
        raise ValueError("Invalid report status")
    for key in ("active_ms", "focused_ms", "break_ms", "paused_ms", "wall_ms"):
        integer(report.get(key), key, 0, 10**12)
    integer(report.get("started_at_ms"), "started_at_ms", 0, 10**15)
    integer(report.get("finished_at_ms"), "finished_at_ms", 0, 253402300799999)
    if not isinstance(report.get("blocks"), list) or len(report["blocks"]) > 100:
        raise ValueError("Invalid report blocks")
    for block in report["blocks"]:
        if not isinstance(block, dict):
            raise ValueError("Invalid report block")
        for key in ("id", "title", "type", "outcome"):
            text_field(block.get(key), key)
        for key in ("active_ms", "paused_ms", "started_at_ms", "finished_at_ms"):
            integer(block.get(key), key, 0, 10**15)
        mode = block.get("timer_mode", "countdown")
        if mode not in ("countdown", "stopwatch"): raise ValueError("Invalid report timer_mode")
        if mode == "stopwatch":
            if version != 2 or block["type"] != "focus" or any(block.get(k) is not None for k in ("planned_ms", "budget_ms", "overtime_ms")):
                raise ValueError("Stopwatch report cannot have a planned duration, budget or overtime")
        else:
            for key in ("planned_ms", "budget_ms"):
                integer(block.get(key), f"v2 countdown.{key}" if version == 2 else key,
                        1000 if version == 2 else 0, 86400000 if version == 2 else 10**12)
            if version == 2 and (block['planned_ms'] % 1000 or block['budget_ms'] % 1000 or block['budget_ms'] < block['planned_ms']):
                raise ValueError("Invalid v2 countdown budget: whole seconds required and budget cannot be below original target")
        if block["type"] not in ("focus", "break") or block["outcome"] not in ("done", "timed_out", "stopped", "skipped"):
            raise ValueError("Invalid block type/outcome")
    if report["status"] == "complete" and any(b["outcome"] == "skipped" for b in report["blocks"]):
        raise ValueError("Skipped blocks cannot claim a fully completed session")
    if report["active_ms"] != report["focused_ms"] + report["break_ms"]:
        raise ValueError("Active time must equal focused plus break time")
    if report["active_ms"] != sum(b["active_ms"] for b in report["blocks"]):
        raise ValueError("Report block times must equal active time")
    if report["focused_ms"] != sum(b["active_ms"] for b in report["blocks"] if b["type"] == "focus") or report["break_ms"] != sum(b["active_ms"] for b in report["blocks"] if b["type"] == "break"):
        raise ValueError("Report categories do not match focus/break block totals")
    if report["paused_ms"] != sum(b["paused_ms"] for b in report["blocks"]):
        raise ValueError("Report block pauses must equal paused time")
    total_overtime = 0
    for block in report["blocks"]:
        excess = max(0, block["active_ms"] - block["budget_ms"]) if block.get("timer_mode", "countdown") == "countdown" else 0
        total_overtime += excess
        if block.get("timer_mode", "countdown") == "countdown" and "overtime_ms" in block and integer(block["overtime_ms"], "block.overtime_ms", 0, 10**12) != excess:
            raise ValueError("Block overtime does not match active time minus budget")
    if "overtime_ms" in report and integer(report["overtime_ms"], "overtime_ms", 0, 10**12) != total_overtime:
        raise ValueError("Total overtime does not match block overtime")
    if version == 2:
        from .stopwatch import validate_v2_report
        validate_v2_report(report)
    from .timeline import validate_timeline
    validate_timeline(report)
    from .analytics import validate_snapshot
    validate_snapshot(report.get("schedule_snapshot"))
    from .rewards import validate_report_rewards
    validate_report_rewards(report)
    from .conditional import validate_report as validate_conditional
    validate_conditional(report)
    return report

def lan_ip() -> str:
    # Route selection only; no packet or request is sent to the external address.
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            s.connect(("192.0.2.1", 9))
            return s.getsockname()[0]
        except OSError:
            return "127.0.0.1"

def check_host(host: str) -> str:
    ip = ipaddress.ip_address(host)
    if not (ip.is_private or ip.is_loopback) or ip.is_unspecified:
        raise ValueError("Bind to a specific private LAN IP or loopback, not a public IP or wildcard")
    return host

def load_config(host: str | None = None, port: int | None = None):
    root = config_dir()
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    cfg = read_json(root / "settings.json", {})
    cfg.setdefault("host", "127.0.0.1")
    cfg.setdefault("port", 8765)
    cfg.setdefault("token", secrets.token_urlsafe(32))
    cfg.setdefault("viewer_token", secrets.token_urlsafe(32))
    cfg.setdefault("desktop_id", secrets.token_hex(16))
    if host is not None:
        cfg["host"] = check_host(lan_ip() if host == "auto" else host)
    if port is not None:
        cfg["port"] = integer(port, "port", 1024, 65535)
    check_host(cfg["host"])
    cert, key = root / "cert.pem", root / "key.pem"
    if not cert.exists() or not key.exists():
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                        "-keyout", str(key), "-out", str(cert), "-days", "3650",
                        "-subj", "/CN=FocusDeck", "-addext", "subjectAltName=DNS:focusdeck.local"],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        key.chmod(0o600)
        cert.chmod(0o600)
    import ssl
    der = ssl.PEM_cert_to_DER_cert(cert.read_text())
    cfg["certificate_sha256"] = hashlib.sha256(der).hexdigest()
    atomic_json(root / "settings.json", cfg)
    return cfg

def pairing_info(cfg, viewer=False):
    return {"protocol_version": VERSION, "host": cfg["host"], "port": cfg["port"],
            "token": cfg["viewer_token"] if viewer else cfg["token"], "certificate_sha256": cfg["certificate_sha256"],
            "desktop_id": cfg["desktop_id"], **({"role": "viewer"} if viewer else {})}
