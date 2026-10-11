from __future__ import annotations
import argparse
import asyncio
import json
from pathlib import Path
import sys
import uuid
from .common import MAX_BYTES, data_dir, read_json, socket_path, utc_now, validate_plan
from .history import export_archive, refresh_csv

def read_plan(source):
    if str(source) == "-":
        raw = sys.stdin.buffer.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise ValueError("Plan exceeds 1 MiB")
        return validate_plan(json.loads(raw))
    return validate_plan(read_json(Path(source)))

async def request_daemon(payload):
    reader, writer = await asyncio.wait_for(asyncio.open_unix_connection(str(socket_path()), limit=MAX_BYTES + 1), 3)
    try:
        writer.write((json.dumps(payload, ensure_ascii=False) + "\n").encode())
        await writer.drain()
        result = await asyncio.wait_for(reader.readline(), 15)
        return json.loads(result)
    finally:
        writer.close()
        await writer.wait_closed()

def main():
    parser = argparse.ArgumentParser(description="Send local schedules to your FocusDeck phone")
    sub = parser.add_subparsers(dest="action", required=True)
    for action in ("status", "devices", "stop", "pause", "resume", "next", "skip_block", "end_block", "extend", "reward_done", "reward_continue", "forget-device"):
        sub.add_parser(action)
    sub.add_parser("pairing").add_argument("--viewer", action="store_true", help="Read-only tablet credential; never replaces the timer phone")
    for action in ("send", "start", "validate"):
        p = sub.add_parser(action)
        p.add_argument("file", help="Session JSON file, or - to read JSON from stdin")
    p = sub.add_parser("achievements", help="Query the cached phone ledger offline, or apply an achievement command")
    achievement_sub = p.add_subparsers(dest="achievement_action",required=True)
    achievement_sub.add_parser("list",help="Phone-derived summaries with cache timestamp")
    p = achievement_sub.add_parser("history");p.add_argument("achievement_id")
    for name in ("archive","restore","delete"):
        p = achievement_sub.add_parser(name);p.add_argument("achievement_id")
        if name == "delete":p.add_argument("--yes",action="store_true",help="Permanently remove card/history; requires explicit confirmation")
    p = achievement_sub.add_parser("icons", help="List the bundled Lucide icon names; no phone needed")
    p = achievement_sub.add_parser("icon", help="Send a bundled icon or local image over the paired LAN connection")
    p.add_argument("achievement_id")
    choice = p.add_mutually_exclusive_group(required=True)
    choice.add_argument("--name", help="Bundled icon name, e.g. laptop")
    choice.add_argument("--file", type=Path, help="Local PNG/JPEG/WebP image, normalized to a bounded PNG")
    choice.add_argument("--clear", action="store_true", help="Restore this card's default icon")
    p.add_argument("--command-id", help="Reuse the original command ID for uncertain-delivery retry")
    p.add_argument("--timestamp", help="Reuse the original ISO timestamp for uncertain-delivery retry")
    p.add_argument("--output", type=Path, help="Save a complete replay-safe JSON command instead of sending")
    for name in ("apply","validate"):
        p = achievement_sub.add_parser(name);p.add_argument("file",help="Achievement command JSON file, or - for stdin")
    p = sub.add_parser("quick")
    p.add_argument("--title", required=True)
    p.add_argument("--task", required=True)
    p.add_argument("--minutes", type=int, required=True)
    p = sub.add_parser("reports")
    p.add_argument("--latest", action="store_true", help="Print latest Markdown summary ready for GPT")
    p.add_argument("--json", action="store_true", help="With --latest, print full JSON report")
    p.add_argument("--session", help="Only reports matching this exact session ID")
    p = sub.add_parser("export", help="Save all archived logs locally; works with the daemon/phone offline")
    p.add_argument("--output", required=True, type=Path)
    p.add_argument("--format", choices=("csv", "json"), default="csv")
    p.add_argument("--kind", choices=("blocks", "sessions", "events", "intervals"), default="blocks")
    p.add_argument("--session", help="Optional exact session ID filter")
    p = sub.add_parser("copy-report", help="Copy a complete GPT-friendly Markdown session report to the desktop clipboard; works offline")
    p.add_argument("--session", help="Exact session ID; selects its latest attempt")
    p.add_argument("--key", help="Exact report key, SESSION_ID:STARTED_AT_MS")
    p = sub.add_parser("delete-logs", help="Delete automatic study logs on PC and phone when synced; daemon must be running")
    choice = p.add_mutually_exclusive_group(required=True)
    choice.add_argument("--all", action="store_true", help="All previously finalized logs; active sessions continue")
    choice.add_argument("--session", help="Latest saved attempt for this exact session ID")
    choice.add_argument("--key", help="Exact report key, SESSION_ID:STARTED_AT_MS")
    sub.add_parser("refresh-logs", help="Rebuild automatic CSV logs from every existing report; works offline")
    args = parser.parse_args()
    try:
        if args.action == "achievements":
            from .achievements import validate_command, query
            if args.achievement_action == "icons":
                from .icons import ICON_NAMES
                print("\n".join(sorted(ICON_NAMES)));return 0
            if args.achievement_action in {"list","history"}:
                print(json.dumps(query(data_dir(),getattr(args,"achievement_id",None)),ensure_ascii=False,indent=2));return 0
            if args.achievement_action == "icon":
                from .icons import import_image
                icon = {"kind":"default"} if args.clear else {"kind":"builtin","name":args.name} if args.name else import_image(args.file)
                command = validate_command(dict(achievement_version=4,operation="icon_set",id=args.command_id or str(uuid.uuid4()),
                    timestamp=args.timestamp or utc_now(),source="CLI",achievement_id=args.achievement_id,icon=icon))
                if args.output:
                    from .common import atomic_json
                    atomic_json(args.output,command);print(f"Saved icon command to {args.output}. Apply this same file for retries.");return 0
                # Keep a durable exact command for retry even if delivery/ACK is interrupted.
                from .common import atomic_json
                saved = data_dir()/"icon-commands"/(command["id"]+".json")
                if saved.exists() and read_json(saved) != command:raise ValueError("Command ID already saved with different content; apply the existing JSON for retries")
                atomic_json(saved,command)
                print(f"Retry command saved: {saved}",file=sys.stderr)
            elif args.achievement_action in {"archive","restore","delete"}:
                if args.achievement_action == "delete" and not args.yes:raise ValueError("Permanent deletion requires --yes; use archive to retain history")
                command = dict(achievement_version=2,operation=args.achievement_action,id=str(uuid.uuid4()),timestamp=utc_now(),source="CLI",achievement_id=args.achievement_id)
                if args.achievement_action == "delete":command["confirm"] = True
                command = validate_command(command)
            else:
                raw = sys.stdin.buffer.read(MAX_BYTES+1) if args.file == "-" else Path(args.file).read_bytes()
                if len(raw)>MAX_BYTES:raise ValueError("Achievement command exceeds 1 MiB")
                command = validate_command(json.loads(raw))
            if args.achievement_action == "validate":print(f"Valid achievement contract {command['achievement_version']} command");return 0
            result = asyncio.run(request_daemon({"action":"achievements","achievement":command}))
            print(json.dumps(result,ensure_ascii=False,indent=2));return 0 if result.get("ok") else 3
        if args.action == "copy-report":
            from .clipboard import copy_report
            report = copy_report(data_dir(), args.session, args.key)
            print(f"Copied Markdown report for {report['session_id']}. Press Ctrl+V in GPT chat.")
            return
        if args.action == "export":
            print(json.dumps(export_archive(data_dir() / "reports", args.output, args.format, args.kind, args.session), indent=2))
            return
        if args.action == "refresh-logs":
            from . import deletions
            deletions.apply(data_dir(), deletions.validate(read_json(data_dir() / "deletions.json", deletions.empty())))
            from .history import load_reports
            reports = load_reports(data_dir() / "reports")
            print(json.dumps({"ok": True, "sessions": len(reports), "directory": str(data_dir() / "exports")}, indent=2))
            return
        payload = {"action": args.action}
        if args.action == "pairing": payload["viewer"] = args.viewer
        if args.action == "delete-logs":
            payload.update(all=args.all, session_id=args.session, report_key=args.key)
        elif args.action in ("send", "start", "validate"):
            payload["session"] = read_plan(args.file)
        elif args.action == "reports" and args.session:
            payload["session_id"] = args.session
        elif args.action == "quick":
            payload = {"action": "start", "session": validate_plan({
                "protocol_version": 1, "session_id": str(uuid.uuid4()), "title": args.title,
                "created_at": utc_now(), "auto_advance": True,
                "blocks": [{"id": "b1", "type": "focus", "title": args.task, "duration_seconds": args.minutes * 60}]})}
        if args.action == "validate":
            print(f"Valid FocusDeck v{payload['session']['protocol_version']} session")
            return
        result = asyncio.run(request_daemon(payload))
        if "session" in payload:
            result["session_id"] = payload["session"]["session_id"]
        if not result.get("ok"):
            message = "FocusDeck: " + result.get("error", "Command failed")
            if "session_id" in result: message += " (session_id=" + result["session_id"] + ")"
            print(message, file=sys.stderr)
            sys.exit(3)
        if args.action == "reports" and args.latest:
            if not result["reports"]:
                print("FocusDeck: No reports received yet", file=sys.stderr)
                sys.exit(3)
            first = result["reports"][0]
            print(Path(first["file"] if args.json else first["summary"]).read_text(), end="")
        else:
            print(json.dumps(result, indent=2, ensure_ascii=False))
    except (ValueError, TypeError, KeyError) as exc:
        print(f"FocusDeck: Invalid input: {exc}", file=sys.stderr)
        sys.exit(2)
    except (OSError, asyncio.TimeoutError) as exc:
        print(f"FocusDeck: Cannot reach daemon/file: {exc}. Start focusdeck-server or the user service.", file=sys.stderr)
        sys.exit(4)

if __name__ == "__main__":
    main()
