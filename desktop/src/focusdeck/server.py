"""Encrypted LAN data plane and owner-only Unix control plane."""
from __future__ import annotations
import argparse
import asyncio
import json
import os
import signal
import ssl
import uuid
from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed
from .common import (VERSION, MAX_BYTES, atomic_json, config_dir, data_dir, load_config,
                     pairing_info, read_json, socket_path, text_field, validate_plan, validate_report)
from .reports import save_report
from .history import refresh_csv
from . import deletions
from .achievements import validate_command as validate_achievement, SnapshotReceiver

def has_schedule_metadata(plan):
    return bool(plan.get('schedule') or any(b.get('schedule') or any(b.get(k) for k in ('category','achievement_id','index_topic_id')) for b in plan.get('blocks',[])))


class DeckServer:
    def __init__(self, cfg, root=None):
        self.cfg = cfg
        self.root = root or data_dir()
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.saved = read_json(self.root / "daemon.json", {"device_id": None, "state": None, "pending": None})
        self.phone = None
        self.device = None
        from .pairing_invitations import PairingInvitations
        self.invitations = PairingInvitations()
        self.waiters = {}
        self.lock = asyncio.Lock()
        self.archive_lock = asyncio.Lock()
        self.deletions = deletions.validate(read_json(self.root / "deletions.json", deletions.empty()))
        from .viewers import ViewerHub
        self.viewers = ViewerHub(self)

    def persist(self):
        atomic_json(self.root / "daemon.json", self.saved)

    def envelope(self, kind, **payload):
        return {"protocol_version": VERSION, "type": kind, **payload}

    async def push_deletions(self, ws):
        if self.deletions["revision"]:
            await ws.send(json.dumps(self.envelope("history_delete", sync_id=self.deletions["revision"], deletions=self.deletions)))

    async def merge_deletions(self, incoming):
        self.deletions = await asyncio.to_thread(deletions.persist_merge, self.root, self.deletions, incoming)
        removed = await asyncio.to_thread(deletions.apply, self.root, self.deletions)
        state = self.saved.get("state")
        if state and state.get("phase") in {"complete", "stopped"} and (self.deletions["before_ms"] or any(k.startswith(str(state.get("session_id")) + ":") for k in self.deletions["report_keys"])):
            self.saved["state"] = None
            self.persist()
        self.viewers.notify("reports")
        return removed

    def authenticate(self, connection, request):
        import hmac
        if request.headers.get("Origin") is not None:
            return connection.respond(403, "Browser origins are not supported\n")
        if request.path == "/v1/pair": return None
        supplied = request.headers.get("Authorization", "")
        token = self.cfg.get("viewer_token") if request.path == "/v1/viewer" else self.cfg.get("token") if request.path == "/v1/device" else None
        if not token or not hmac.compare_digest(supplied, "Bearer " + token):
            return connection.respond(401, "Authentication required\n")
        if request.headers.get("Origin") is not None:
            return connection.respond(403, "Browser origins are not supported\n")

    async def invitation_handler(self, ws):
        from .pairing_invitations import InvitationError
        try:
            raw = await asyncio.wait_for(ws.recv(), 10)
            if not isinstance(raw,str) or len(raw)>4096: raise ValueError("Invalid pairing request")
            request = json.loads(raw)
            async with self.lock:
                pairing = self.invitations.redeem(request, self.cfg, self.saved['device_id'])
            await ws.send(json.dumps(dict(type='pairing_grant',protocol_version=1,invitation_version=1,pairing=pairing)))
        except InvitationError as error:
            await ws.send(json.dumps(dict(type='pairing_error',error=str(error))))
        except (ValueError, TypeError, asyncio.TimeoutError):
            # Errors are fixed messages: never echo raw requests, credentials or parser diagnostics.
            await ws.send(json.dumps(dict(type='pairing_error',error='Invalid, expired, used or incompatible invitation; create a new invitation')))
        except ConnectionClosed:
            pass
        finally:
            await ws.close()

    async def device_handler(self, ws):
        if ws.request.path == "/v1/pair": return await self.invitation_handler(ws)
        if ws.request.path == "/v1/viewer":
            return await self.viewers.handle(ws)
        assigned = False
        snapshots = SnapshotReceiver(self.root)
        try:
            hello = json.loads(await asyncio.wait_for(ws.recv(), 10))
            if not isinstance(hello, dict) or hello.get("protocol_version") != VERSION or hello.get("type") != "hello":
                raise ValueError("Expected v1 hello")
            if hello.get("role", "phone") != "phone": raise ValueError("Phone endpoint requires phone role")
            viewer_version = hello.get("supported_viewer_version")
            if viewer_version is not None and (type(viewer_version) is not int or viewer_version != 1): raise ValueError("Unsupported viewer version")
            device_id = text_field(hello.get("device_id"), "device_id", 100)
            text_field(hello.get("name"), "name", 100)
            schedule_version=hello.get("supported_schedule_version",0)
            if type(schedule_version) is not int or schedule_version not in (0,1):raise ValueError("Invalid schedule capability")
            versions = hello.get("supported_session_versions", [1])
            achievement_versions = hello.get("supported_achievement_versions", [])
            if not isinstance(achievement_versions,list) or len(achievement_versions)>6 or any(type(v) is not int or v not in (1,2,3,4,5,6) for v in achievement_versions) or len(set(achievement_versions)) != len(achievement_versions):
                raise ValueError("Invalid supported_achievement_versions")
            if not isinstance(versions, list) or not versions or len(versions)>2 or any(type(v) is not int or v not in (1,2) for v in versions) or len(set(versions))!=len(versions):
                raise ValueError("Invalid supported_session_versions")
            async with self.lock:
                known = self.saved["device_id"] or self.invitations.reserved_id()
                if known and known != device_id:
                    raise ValueError("A different phone is paired; use focusdeck forget-device locally")
                if self.phone is not None:
                    raise ValueError("Phone already connected; wait for the previous connection to close")
                self.saved["device_id"] = device_id
                self.persist()
                self.phone = ws
                self.device = {"device_id": device_id, "name": hello["name"], "supported_session_versions": versions, "supported_achievement_versions":achievement_versions, "supported_viewer_version":viewer_version, "supported_schedule_version":hello.get("supported_schedule_version",0)}
                assigned = True
            capabilities = {"supported_achievement_versions":[1,2,3,4,5,6]} if achievement_versions else {}
            await ws.send(json.dumps(self.envelope("hello_ack", desktop_id=self.cfg["desktop_id"], supported_session_versions=[1,2], supported_viewer_version=1, supported_schedule_version=1, **capabilities)))
            await self.push_deletions(ws)
            self.viewers.notify("state", "ledger", "reports")
            await self.viewers.interest()
            # Never push stale desktop state to Android. It is the timer authority.
            if self.saved["pending"]:
                pending = self.saved["pending"]
                if pending.get("action") == "achievements" and pending["achievement"]["achievement_version"] not in achievement_versions:
                    await ws.send(json.dumps(self.envelope("error", message="Pending achievement contract requires a compatible phone upgrade")))
                elif pending.get("session", {}).get("protocol_version",1) not in versions:
                    await ws.send(json.dumps(self.envelope("error", message="Pending v2 plan requires FocusDeck 1.8+ on the phone")))
                elif has_schedule_metadata(pending.get("session",{})) and schedule_version!=1:
                    await ws.send(json.dumps(self.envelope("error",message="Pending schedule metadata requires a compatible phone upgrade")))
                else:
                    await ws.send(json.dumps(pending))
            async for raw in ws:
                try:
                    msg = json.loads(raw)
                    if not isinstance(msg, dict) or msg.get("protocol_version") != VERSION:
                        raise ValueError("Unsupported protocol")
                    kind = msg.get("type")
                    if kind == "achievement_snapshot":
                        if 1 not in achievement_versions:raise ValueError("Achievement capability not negotiated")
                        snapshot = await asyncio.to_thread(snapshots.receive,msg)
                        if snapshot is not None: await self.viewers.ledger_received(device_id)
                        await ws.send(json.dumps(self.envelope("achievement_snapshot_ack",snapshot_id=msg["snapshot_id"],index=msg["index"])))
                    elif kind == "viewer_state":
                        if viewer_version != 1: raise ValueError("Viewer capability not negotiated")
                        await self.viewers.state_received(device_id, msg.get("data"))
                    elif kind == "state":
                        state = msg.get("state")
                        if not isinstance(state, dict):
                            raise ValueError("Invalid state")
                        self.saved["state"] = state
                        self.persist()
                    elif kind == "ack":
                        cid = text_field(msg.get("command_id"), "command_id", 100)
                        pending = self.saved.get("pending")
                        if pending and pending["command_id"] == cid:
                            self.saved["pending"] = None
                            self.persist()
                        waiter = self.waiters.get(cid)
                        if waiter and not waiter.done():
                            waiter.set_result(msg)
                    elif kind == "history_delete":
                        sync_id = text_field(msg.get("sync_id"), "sync_id", 100)
                        incoming = deletions.validate(msg.get("deletions"))
                        async with self.archive_lock:
                            await self.merge_deletions(incoming)
                            await ws.send(json.dumps(self.envelope("history_delete_ack", sync_id=sync_id)))
                            await self.push_deletions(ws)
                    elif kind == "history_delete_ack":
                        text_field(msg.get("sync_id"), "sync_id", 100)
                    elif kind == "report":
                        report = validate_report(msg.get("report"))
                        key = f"{report['session_id']}:{report['started_at_ms']}"
                        if msg.get("report_key") != key:
                            raise ValueError("Report key differs from session attempt")
                        async with self.archive_lock:
                            name = None
                            if not deletions.hides(self.deletions, report):
                                name = await asyncio.to_thread(save_report, self.root / "reports", report)
                                self.viewers.notify("reports")
                                try:
                                    await asyncio.to_thread(refresh_csv, self.root / "reports", self.root / "exports")
                                except (OSError, ValueError) as exc:
                                    await ws.send(json.dumps(self.envelope("error", message=f"Report saved, but PC CSV logs could not update: {exc}")))
                            await ws.send(json.dumps(self.envelope("report_ack", report_key=key, file_id=name)))
                    else:
                        raise ValueError("Unknown message type")
                except (ValueError, TypeError, KeyError, OSError) as exc:
                    await ws.send(json.dumps(self.envelope("error", message=str(exc))))
        except (ValueError, TypeError, KeyError, asyncio.TimeoutError) as exc:
            await ws.close(code=1008, reason=str(exc)[:120])
        except ConnectionClosed:
            pass  # Expected network loss: the phone remains the timer authority.
        finally:
            snapshots.close()
            if assigned and self.phone is ws:
                self.phone = None
                self.device = None
                self.viewers.notify("state")

    async def control(self, request):
        action = request.get("action")
        if action == "status":
            return {"ok": True, "connected": self.phone is not None, "state": self.saved["state"],
                    "pending_command": self.saved["pending"], "state_is_cached": self.phone is None}
        if action == "devices":
            return {"ok": True, "devices": [dict(self.device, role="phone")] if self.device else [], "paired_id": self.saved["device_id"],
                    "viewers": [{"device_id": key, "name": c["name"], "role": "viewer"} for key,c in self.viewers.clients.items()]}
        if action == "pair-invite":
            async with self.lock:
                invitation = self.invitations.create(self.cfg, bool(request.get('viewer', False)))
            return dict(ok=True, invitation=invitation)
        if action == "revoke-invite":
            async with self.lock: self.invitations.revoke(request.get('invitation_id'))
            return dict(ok=True)
        if action == "pairing":
            return {"ok": True, "pairing": pairing_info(self.cfg, bool(request.get("viewer", False)))}
        if action == "forget-device":
            if self.phone:
                return {"ok": False, "error": "Disconnect the paired phone first"}
            self.saved["device_id"] = None
            self.saved["pending"] = None
            self.persist()
            self.viewers.notify("state", "ledger", "reports")
            return {"ok": True}
        if action == "reports":
            files = sorted((self.root / "reports").glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
            selected = []
            for path in files:
                report = read_json(path)
                if deletions.hides(self.deletions, report):
                    continue
                if request.get("session_id") and report["session_id"] != request["session_id"]:
                    continue
                selected.append({"file": str(path), "summary": str(path.with_suffix(".md")), "session_id": report["session_id"]})
            return {"ok": True, "reports": selected}
        if action == "delete-logs":
            if sum(bool(request.get(k)) for k in ("all", "report_key", "session_id")) != 1:
                raise ValueError("Specify exactly one of --all, --key, or --session")
            async with self.archive_lock:
                if request.get("all"):
                    from .history import load_reports
                    import time
                    records = []
                    for path in (self.root / "reports").glob("*.json"):
                        try:
                            r = read_json(path)
                            if type(r.get("finished_at_ms")) is int and 0 <= r["finished_at_ms"] <= 253402300799999:records.append(r)
                        except (ValueError, TypeError, AttributeError):pass
                    incoming = dict(before_ms=max(int(time.time() * 1000), max((r["finished_at_ms"] for r in records), default=0)), report_keys=[], revision="")
                else:
                    key = request.get("report_key")
                    if key is None:
                        from .history import load_reports
                        records = [r for r in load_reports(self.root / "reports") if r["session_id"] == request["session_id"] and not deletions.hides(self.deletions, r)]
                        if not records:raise ValueError("No saved session matches this ID; use --key for a known phone attempt")
                        record = max(records, key=lambda r:(r["finished_at_ms"],r["started_at_ms"]))
                        key = f"{record['session_id']}:{record['started_at_ms']}"
                    incoming = dict(before_ms=0, report_keys=[key], revision="")
                removed = await self.merge_deletions(incoming)
                if self.phone:
                    try:await self.push_deletions(self.phone)
                    except ConnectionClosed:pass
                return {"ok": True, "deleted_pc_reports": removed, "deletion_revision": self.deletions["revision"], "phone_sync": "sent; applied after acknowledgement" if self.phone else "pending until reconnect"}
        if action not in {"send", "start", "stop", "pause", "resume", "next", "skip_block", "end_block", "extend", "reward_done", "reward_continue", "achievements"}:
            raise ValueError("Unknown control action")
        if action in {"send", "start"}:
            validate_plan(request.get("session"))
        if action == "achievements":validate_achievement(request.get("achievement"))
        async with self.lock:
            if self.phone is None:
                return {"ok": False, "error": "Phone is offline. Commands are not queued; its timer continues locally."}
            if "expected_session_id" in request:
                state = self.saved.get("state") or {}
                if state.get("session_id") != request["expected_session_id"] or state.get("block_index") != request.get("expected_block_index"):
                    return {"ok": False, "error": "The observed session/block changed; refresh before acting"}
            if action == "achievements" and request["achievement"]["achievement_version"] not in (self.device or {}).get("supported_achievement_versions",[]):
                return {"ok":False,"error":f"The connected phone does not support achievement contract {request['achievement']['achievement_version']}; upgrade it before sending this command"}
            if action in {"send", "start"} and request["session"]["protocol_version"] not in (self.device or {}).get("supported_session_versions", [1]):
                return {"ok": False, "error": "Session protocol 2 requires FocusDeck 1.8+ on the phone; upgrade before sending"}
            if action in {"send", "start"} and has_schedule_metadata(request['session']) and (self.device or {}).get('supported_schedule_version') != 1:
                return {"ok":False,"error":"Scheduled metadata requires a compatible phone upgrade; duration-only plans remain supported"}
            if self.saved["pending"]:
                return {"ok": False, "error": "Previous command awaits acknowledgement; reconnect the phone before retrying"}
            cid = str(uuid.uuid4())
            command = self.envelope("command", command_id=cid, action=action)
            if action in {"send", "start"}:
                command["session"] = request["session"]
            if action == "achievements":command["achievement"] = request["achievement"]
            self.saved["pending"] = command
            self.persist()
            waiter = asyncio.get_running_loop().create_future()
            self.waiters[cid] = waiter
            try:
                await self.phone.send(json.dumps(command))
            except Exception:
                self.waiters.pop(cid, None)
                return {"ok": False, "error": "Connection lost; command remains pending for safe replay", "command_id": cid}
        try:
            ack = await asyncio.wait_for(asyncio.shield(waiter), 10)
            return {"ok": bool(ack.get("ok")), "command_id": cid, "error": ack.get("error")}
        except asyncio.TimeoutError:
            return {"ok": False, "error": "Acknowledgement timed out. Delivery is uncertain; use status before retrying.", "command_id": cid}
        finally:
            self.waiters.pop(cid, None)

    async def unix_handler(self, reader, writer):
        try:
            raw = await asyncio.wait_for(reader.readline(), 5)
            if len(raw) > MAX_BYTES:
                raise ValueError("Request too large")
            request = json.loads(raw)
            if not isinstance(request, dict):
                raise ValueError("Request must be an object")
            result = await self.control(request)
        except (ValueError, TypeError, KeyError, asyncio.TimeoutError) as exc:
            result = {"ok": False, "error": str(exc)}
        except Exception:
            result = {"ok": False, "error": "Internal error; inspect daemon logs"}
        try:
            writer.write((json.dumps(result, ensure_ascii=False) + "\n").encode())
            await writer.drain()
        finally:
            writer.close()
            await writer.wait_closed()

    async def run(self):
        # Lock before touching the socket; prevents a second daemon unlinking the live socket.
        import fcntl
        lock_path = self.root / "daemon.lock"
        with lock_path.open("a") as lockfile:
            try:
                fcntl.flock(lockfile, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise ValueError("Another FocusDeck daemon is already running") from None
            path = socket_path()
            # Populate logs for reports received before V1.3, even without a phone reconnect.
            try:
                await asyncio.to_thread(deletions.apply, self.root, self.deletions)
            except (OSError, ValueError) as exc:
                print(f"Automatic CSV logs could not update: {exc}. Existing reports are retained; run focusdeck refresh-logs after repairing storage.", flush=True)
            path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            path.unlink(missing_ok=True)
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.minimum_version = ssl.TLSVersion.TLSv1_2
            tls.load_cert_chain(config_dir() / "cert.pem", config_dir() / "key.pem")
            stop = asyncio.Event()
            loop = asyncio.get_running_loop()
            for sig in (signal.SIGINT, signal.SIGTERM):
                loop.add_signal_handler(sig, stop.set)
            unix = await asyncio.start_unix_server(self.unix_handler, str(path), limit=MAX_BYTES + 1)
            path.chmod(0o600)
            try:
                async with unix, serve(self.device_handler, self.cfg["host"], self.cfg["port"], ssl=tls,
                                      process_request=self.authenticate, max_size=MAX_BYTES,
                                      ping_interval=60, ping_timeout=30, compression=None):
                    print(f"FocusDeck listening on {self.cfg['host']}:{self.cfg['port']} (TLS).", flush=True)
                    print("Run focusdeck pairing locally to see pairing credentials.", flush=True)
                    await stop.wait()
            finally:
                path.unlink(missing_ok=True)

def main():
    parser = argparse.ArgumentParser(description="FocusDeck encrypted LAN daemon")
    parser.add_argument("--host", help="Specific private IP, loopback, or auto; persists to config")
    parser.add_argument("--port", type=int)
    parser.add_argument("--init", action="store_true", help="Create/update configuration and print pairing JSON")
    args = parser.parse_args()
    os.umask(0o077)
    try:
        cfg = load_config(args.host, args.port)
        if args.init:
            print(json.dumps(pairing_info(cfg), indent=2))
            return
        asyncio.run(DeckServer(cfg).run())
    except (OSError, ValueError, RuntimeError) as exc:
        parser.exit(1, f"FocusDeck: {exc}\n")

if __name__ == "__main__":
    main()
