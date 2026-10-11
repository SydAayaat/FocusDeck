import asyncio
import copy
import csv
import io
import json
import os
from pathlib import Path
import ssl
import tempfile
import subprocess
import sys
import unittest
from unittest.mock import patch
from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import InvalidStatus, ConnectionClosed
from focusdeck.common import validate_plan, validate_report, load_config, atomic_json, check_host
from focusdeck.server import DeckServer
from focusdeck.reports import save_report, markdown

def plan():
    return {"protocol_version": 1, "session_id": "test-session", "title": "Index",
            "created_at": "2026-10-06T19:00:00Z", "auto_advance": True,
            "blocks": [{"id": "b1", "type": "focus", "title": "Topics 1.1–1.2", "duration_seconds": 60},
                       {"id": "b2", "type": "break", "title": "Break", "duration_seconds": 10}]}

def report():
    return {"session_id": "../untrusted", "title": "Index", "status": "complete", "planned_ms": 70000,
            "active_ms": 50000, "focused_ms": 40000, "break_ms": 10000, "paused_ms": 5000,
            "wall_ms": 55000, "started_at_ms": 100000, "finished_at_ms": 155000,
            "blocks": [{"id": "b1", "type": "focus", "title": "Topic 1", "planned_ms": 60000,
                        "budget_ms": 60000, "active_ms": 40000, "paused_ms": 5000,
                        "started_at_ms": 100000, "finished_at_ms": 145000, "outcome": "done"},
                       {"id": "b2", "type": "break", "title": "Break", "planned_ms": 10000,
                        "budget_ms": 10000, "active_ms": 10000, "paused_ms": 0,
                        "started_at_ms": 145000, "finished_at_ms": 155000, "outcome": "timed_out"}]}

class ValidationTests(unittest.TestCase):
    def test_valid_focus_and_break(self):
        self.assertEqual(validate_plan(plan())["blocks"][1]["type"], "break")

    def test_valid_single_focus(self):
        p = plan(); p["blocks"] = p["blocks"][:1]
        validate_plan(p)

    def test_cli_validation_and_exit_codes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "plan.json"
            atomic_json(path, plan())
            command = [sys.executable, "-m", "focusdeck.cli", "validate", str(path)]
            valid = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(valid.returncode, 0, valid.stderr)
            path.write_text('{bad json')
            invalid = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(invalid.returncode, 2)
            self.assertIn("Invalid input", invalid.stderr)

    def test_stdin_plan_is_ready_for_terminal_paste(self):
        command = [sys.executable, "-m", "focusdeck.cli", "validate", "-"]
        valid = subprocess.run(command, input=json.dumps(plan()), capture_output=True, text=True)
        self.assertEqual(valid.returncode, 0, valid.stderr)
        invalid = subprocess.run(command, input="{bad}", capture_output=True, text=True)
        self.assertEqual(invalid.returncode, 2)

    def test_cli_start_returns_original_session_id(self):
        from focusdeck.cli import main
        import io
        from contextlib import redirect_stdout
        async def fake_request(payload):
            self.assertEqual(payload["session"]["session_id"], "test-session")
            return {"ok": True, "command_id": "transport-command-id"}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "plan.json"; atomic_json(path, plan())
            output = io.StringIO()
            with patch.object(sys, "argv", ["focusdeck", "start", str(path)]), patch("focusdeck.cli.request_daemon", fake_request), redirect_stdout(output):
                main()
            self.assertEqual(json.loads(output.getvalue())["session_id"], "test-session")

    def test_overtime_report_preserves_plan_id_and_actual_time(self):
        value = report()
        value["session_id"] = "unique-plan-id"
        value["blocks"][0]["active_ms"] = 75000
        value["blocks"][0]["overtime_ms"] = 15000
        value["active_ms"] = 85000; value["focused_ms"] = 75000; value["overtime_ms"] = 15000
        validate_report(value)
        summary = markdown(value)
        self.assertIn("unique-plan-id", summary); self.assertIn("0h 00m 15s", summary)
        value["overtime_ms"] = 999
        with self.assertRaises(ValueError): validate_report(value)

    def test_reject_inconsistent_report(self):
        value = report(); value["active_ms"] += 1
        with self.assertRaises(ValueError): validate_report(value)
        value = report(); value["blocks"][0]["outcome"] = "fictional"
        with self.assertRaises(ValueError): validate_report(value)

    def test_reject_malformed_cases(self):
        mutations = [lambda p: p.update(protocol_version=2), lambda p: p.update(blocks=[]),
                     lambda p: p.update(created_at="2026-10-06T12:00:00"),
                     lambda p: p.update(auto_advance="yes"), lambda p: p.update(unknown=1),
                     lambda p: p["blocks"][0].update(duration_seconds=True),
                     lambda p: p["blocks"][0].update(duration_seconds=-1),
                     lambda p: p["blocks"][0].update(duration_seconds=999999),
                     lambda p: p["blocks"][0].update(type="other"),
                     lambda p: p["blocks"][1].update(id="b1"),
                     lambda p: p["blocks"][0].update(extra="bad"), lambda p: p.update(title="")]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                p = plan(); mutation(p)
                with self.assertRaises(ValueError): validate_plan(p)

    def test_no_public_or_wildcard_bind(self):
        for ip in ("0.0.0.0", "::", "8.8.8.8"):
            with self.assertRaises(ValueError): check_host(ip)
        self.assertEqual(check_host("192.168.1.4"), "192.168.1.4")

    def test_report_is_idempotent_and_path_safe(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "reports"
            first = save_report(root, report()); second = save_report(root, report())
            self.assertEqual(first, second)
            self.assertEqual(len(list(root.glob("*.json"))), 1)
            self.assertTrue((root / f"{first}.md").exists())
            self.assertIn("timed_out", markdown(report()))

    def test_atomic_storage_private(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "state.json"
            atomic_json(path, {"revision": 1}); atomic_json(path, {"revision": 2})
            self.assertEqual(json.loads(path.read_text())["revision"], 2)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

class TransportTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        asyncio.get_running_loop().set_debug(False)
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.env = patch.dict(os.environ, {"FOCUSDECK_CONFIG": str(self.root / "config")})
        self.env.start()
        self.cfg = load_config("127.0.0.1", 8765)
        self.deck = DeckServer(self.cfg, self.root / "data")
        self.tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        self.tls.load_cert_chain(self.root / "config/cert.pem", self.root / "config/key.pem")
        self.client_tls = ssl.create_default_context(cafile=str(self.root / "config/cert.pem"))
        self.client_tls.check_hostname = False
        await self.start_listener()

    async def start_listener(self):
        self.ws_server = await serve(self.deck.device_handler, "127.0.0.1", 0,
                                     ssl=self.tls, process_request=self.deck.authenticate, max_size=1048576)
        self.port = self.ws_server.sockets[0].getsockname()[1]

    async def asyncTearDown(self):
        self.ws_server.close(); await self.ws_server.wait_closed()
        self.env.stop(); self.temp.cleanup()

    async def phone(self, device="phone-1", versions=None, achievement_versions=None, schedule_version=None):
        ws = await connect(f"wss://127.0.0.1:{self.port}/v1/device", ssl=self.client_tls,
                           additional_headers={"Authorization": "Bearer " + self.cfg["token"]})
        hello_payload={"protocol_version": 1, "type": "hello", "device_id": device, "name": "Test phone"}
        if versions is not None:hello_payload['supported_session_versions']=versions
        if achievement_versions is not None:hello_payload['supported_achievement_versions']=achievement_versions
        if schedule_version is not None:hello_payload['supported_schedule_version']=schedule_version
        await ws.send(json.dumps(hello_payload))
        hello = json.loads(await ws.recv())
        self.assertEqual(hello["type"], "hello_ack")
        return ws

    async def ack(self, ws, command, ok=True):
        await ws.send(json.dumps({"protocol_version": 1, "type": "ack", "command_id": command["command_id"], "ok": ok}))

    async def test_achievement_command_ack_snapshot_and_offline_cache_over_existing_tls(self):
        from test_achievements import command, AchievementSnapshotTests
        from focusdeck.achievements import query
        async with await self.phone(versions=[1,2],achievement_versions=[1]) as ws:
            value=command(achievement_id='german',event='completed')
            task=asyncio.create_task(self.deck.control(dict(action='achievements',achievement=value)))
            sent=json.loads(await ws.recv());self.assertEqual(sent['achievement'],value)
            await self.ack(ws,sent);self.assertTrue((await task)['ok'])
            snapshot=json.dumps(AchievementSnapshotTests().snapshot())
            cut=len(snapshot)//2
            for index,data in enumerate((snapshot[:cut],snapshot[cut:])):
                await ws.send(json.dumps(dict(protocol_version=1,type='achievement_snapshot',achievement_version=1,snapshot_id='snapshot-1',index=index,count=2,data=data)))
                ack=json.loads(await ws.recv());self.assertEqual('achievement_snapshot_ack',ack['type']);self.assertEqual(index,ack['index'])
            # Round-trip a report as an ordered barrier after snapshot persistence.
            r=report();await ws.send(json.dumps(dict(protocol_version=1,type='report',report_key='../untrusted:100000',report=r)))
            self.assertEqual(json.loads(await ws.recv())['type'],'report_ack')
            self.assertIsNone(self.deck.saved['pending'])
        self.assertEqual(2,query(self.deck.root)['summary'][0]['current'])

    async def test_achievement_pending_replay_is_gated_then_preserves_exact_payload(self):
        from test_achievements import command
        pending=self.deck.envelope('command',command_id='pending-achievement',action='achievements',achievement=command(achievement_id='german',event='completed'))
        self.deck.saved['pending']=pending;self.deck.persist()
        async with await self.phone(versions=[1,2]) as ws:
            self.assertEqual(json.loads(await ws.recv())['type'],'error')
            self.assertEqual(pending,self.deck.saved['pending'])
        await asyncio.sleep(.05)
        async with await self.phone(versions=[1,2],achievement_versions=[1]) as ws:
            self.assertEqual(pending,json.loads(await ws.recv()));await self.ack(ws,pending)

    async def test_achievement_v2_pending_delete_is_gated_until_phone_upgrade(self):
        from test_achievements import command
        value=command('delete',achievement_id='german',confirm=True);value['achievement_version']=2
        pending=self.deck.envelope('command',command_id='pending-delete',action='achievements',achievement=value)
        self.deck.saved['pending']=pending;self.deck.persist()
        async with await self.phone(versions=[1,2],achievement_versions=[1]) as ws:
            self.assertEqual('error',json.loads(await ws.recv())['type'])
            self.assertEqual(pending,self.deck.saved['pending'])
        await asyncio.sleep(.05)
        async with await self.phone(versions=[1,2],achievement_versions=[1,2]) as ws:
            self.assertEqual(pending,json.loads(await ws.recv()));await self.ack(ws,pending)
            # Barrier ensures the ACK was persisted before examining the pending slot.
            r=report();await ws.send(json.dumps(dict(protocol_version=1,type='report',report_key=f"{r['session_id']}:{r['started_at_ms']}",report=r)))
            self.assertEqual('report_ack',json.loads(await ws.recv())['type'])
            self.assertIsNone(self.deck.saved['pending'])

    async def test_icon_contract_four_pending_replay_requires_capable_phone(self):
        from test_icons import command
        icon = command(dict(kind="builtin",name="laptop"))
        pending=self.deck.envelope('command',command_id='pending-icon',action='achievements',achievement=icon)
        self.deck.saved['pending']=pending;self.deck.persist()
        async with await self.phone(versions=[1,2],achievement_versions=[1,2,3]) as ws:
            self.assertEqual('error',json.loads(await ws.recv())['type'])
            self.assertEqual(pending,self.deck.saved['pending'])
        await asyncio.sleep(.05)
        async with await self.phone(versions=[1,2],achievement_versions=[1,2,3,4]) as ws:
            self.assertEqual(pending,json.loads(await ws.recv()));await self.ack(ws,pending)
            r=report();await ws.send(json.dumps(dict(protocol_version=1,type='report',report_key=f"{r['session_id']}:{r['started_at_ms']}",report=r)))
            self.assertEqual('report_ack',json.loads(await ws.recv())['type']);self.assertIsNone(self.deck.saved['pending'])

    async def test_progress_contract_three_gating_delivery_and_history_over_tls(self):
        from test_progress import command
        from test_achievements import AchievementSnapshotTests
        from focusdeck.achievements import query
        value=command(current_value=200,target_value=1250,unit='words')
        async with await self.phone(versions=[1,2],achievement_versions=[1,2]) as ws:
            result=await self.deck.control(dict(action='achievements',achievement=value))
            self.assertFalse(result['ok']);self.assertIsNone(self.deck.saved['pending'])
        await asyncio.sleep(.05)
        async with await self.phone(versions=[1,2],achievement_versions=[1,2,3]) as ws:
            task=asyncio.create_task(self.deck.control(dict(action='achievements',achievement=value)))
            sent=json.loads(await ws.recv());self.assertEqual(value,sent['achievement'])
            await self.ack(ws,sent);self.assertTrue((await task)['ok'])
            snapshot=AchievementSnapshotTests().snapshot()
            snapshot['summary'][0]['progress_percent']=16
            snapshot['ledger']['progressHistory']=[dict(id='progress-1',achievementId='german',newValue=200,targetValueAtThatTime=1250)]
            await ws.send(json.dumps(dict(protocol_version=1,type='achievement_snapshot',achievement_version=1,snapshot_id='progress-snapshot',index=0,count=1,data=json.dumps(snapshot))))
            self.assertEqual('achievement_snapshot_ack',json.loads(await ws.recv())['type'])
        history=query(self.deck.root,'german');self.assertEqual(16,history['summary'][0]['progress_percent']);self.assertEqual(200,history['progress_history'][0]['newValue'])

    async def test_v2_plan_requires_phone_capability_without_changing_the_plan(self):
        from test_stopwatch import plan as stopwatch_plan
        value=stopwatch_plan()
        async with await self.phone() as ws:
            result=await self.deck.control({"action":"start","session":value})
            self.assertFalse(result['ok']);self.assertIn('1.8+',result['error'])
            self.assertIsNone(self.deck.saved['pending'])
            self.assertNotIn('duration_seconds',value['blocks'][0])

    async def test_v2_start_and_report_sync_preserve_null_metrics_and_csv_modes(self):
        from test_stopwatch import plan as stopwatch_plan, report as mixed_report
        async with await self.phone(versions=[1,2]) as ws:
            value=stopwatch_plan()
            task=asyncio.create_task(self.deck.control({"action":"start","session":value}))
            command=json.loads(await ws.recv());self.assertEqual(command['session'],value)
            await self.ack(ws,command);self.assertTrue((await task)['ok'])
            r=mixed_report();key=f"{r['session_id']}:{r['started_at_ms']}"
            await ws.send(json.dumps(dict(protocol_version=1,type='report',report_key=key,report=r)))
            self.assertEqual(json.loads(await ws.recv())['type'],'report_ack')
            selected=await self.deck.control(dict(action='reports',session_id=r['session_id']))
            saved=json.loads(Path(selected['reports'][0]['file']).read_text())
            self.assertIsNone(saved['blocks'][0]['performance']['beat_score'])
            self.assertIn('Countdown-only scoring',Path(selected['reports'][0]['summary']).read_text())
            self.assertIn('stopwatch',(self.deck.root/'exports/all-blocks.csv').read_text())

    async def test_pending_v2_replay_is_held_for_old_phone_and_sent_unchanged_after_upgrade(self):
        from test_stopwatch import plan as stopwatch_plan
        pending=self.deck.envelope('command',command_id='pending-v2',action='start',session=stopwatch_plan())
        self.deck.saved['pending']=pending;self.deck.persist()
        async with await self.phone() as ws:
            message=json.loads(await ws.recv());self.assertEqual(message['type'],'error')
            self.assertEqual(self.deck.saved['pending'],pending)
        await asyncio.sleep(.05)
        async with await self.phone(versions=[1,2]) as ws:
            self.assertEqual(json.loads(await ws.recv()),pending)
            await self.ack(ws,pending)
            await ws.send(json.dumps(dict(protocol_version=1,type='state',state={'phase':'ready'})))
            await asyncio.sleep(.05)
            self.assertIsNone(self.deck.saved['pending'])

    async def test_optional_schedule_delivery_and_pending_replay_require_capability(self):
        value=plan();value['blocks'][0]['schedule']={'planned_start':'2026-10-09T10:00:00Z','planned_end':'2026-10-09T10:01:00Z','timezone':'UTC'}
        original=copy.deepcopy(value)
        async with await self.phone() as ws:
            result=await self.deck.control(dict(action='send',session=value));self.assertFalse(result['ok']);self.assertIsNone(self.deck.saved['pending']);self.assertEqual(value,original)
        await asyncio.sleep(.05)
        async with await self.phone(schedule_version=1) as ws:
            task=asyncio.create_task(self.deck.control(dict(action='send',session=value)));command=json.loads(await ws.recv());self.assertEqual(command['session'],original);await self.ack(ws,command);self.assertTrue((await task)['ok'])
        await asyncio.sleep(.05)
        pending=self.deck.envelope('command',command_id='schedule-replay',action='send',session=value);self.deck.saved['pending']=pending;self.deck.persist()
        async with await self.phone() as ws:
            self.assertEqual(json.loads(await ws.recv())['type'],'error');self.assertEqual(self.deck.saved['pending'],pending)
        await asyncio.sleep(.05)
        async with await self.phone(schedule_version=1) as ws:
            self.assertEqual(json.loads(await ws.recv()),pending);await self.ack(ws,pending)

    async def test_layout_order_receipt_is_delivered_exactly_on_existing_transport(self):
        value=dict(achievement_version=6,operation='layout_order',id='order-1',timestamp='2026-10-09T10:00:00Z',source='PC',order=['german','gym'],expected_store_revision=12)
        async with await self.phone(achievement_versions=[1,2,3,4,5,6]) as ws:
            task=asyncio.create_task(self.deck.control(dict(action='achievements',achievement=value)));command=json.loads(await ws.recv());self.assertEqual(command['achievement'],value);await self.ack(ws,command);self.assertTrue((await task)['ok'])

    async def test_invalid_token(self):
        with self.assertRaises(InvalidStatus) as error:
            await connect(f"wss://127.0.0.1:{self.port}/v1/device", ssl=self.client_tls,
                          additional_headers={"Authorization": "Bearer wrong"})
        self.assertEqual(error.exception.response.status_code, 401)

    async def test_browser_origin_rejected(self):
        with self.assertRaises(InvalidStatus):
            await connect(f"wss://127.0.0.1:{self.port}/v1/device", ssl=self.client_tls, origin="https://example.org",
                          additional_headers={"Authorization": "Bearer " + self.cfg["token"]})

    async def test_untrusted_certificate_rejected(self):
        loop = asyncio.get_running_loop()
        previous = loop.get_exception_handler()
        def handler(loop, context):
            if isinstance(context.get("exception"), ConnectionResetError):
                return  # Expected server-side consequence of client rejecting TLS.
            if previous: previous(loop, context)
            else: loop.default_exception_handler(context)
        loop.set_exception_handler(handler)
        try:
            with self.assertRaises(ssl.SSLCertVerificationError):
                await connect(f"wss://127.0.0.1:{self.port}/v1/device", ssl=ssl.create_default_context())
            await asyncio.sleep(.05)
        finally:
            loop.set_exception_handler(previous)

    async def test_full_command_and_state_roundtrip(self):
        async with await self.phone() as ws:
            for action in ("send", "start", "pause", "resume", "next", "extend", "reward_done", "reward_continue", "stop"):
                task = asyncio.create_task(self.deck.control({"action": action, "session": plan()}))
                command = json.loads(await ws.recv())
                self.assertEqual(command["action"], action)
                await self.ack(ws, command)
                self.assertTrue((await task)["ok"])
            await ws.send(json.dumps({"protocol_version": 1, "type": "state", "state": {"phase": "paused", "remaining_ms": 5000}}))
            await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
            self.assertEqual(json.loads(await ws.recv())["type"], "report_ack")
            self.assertEqual((await self.deck.control({"action": "status"}))["state"]["phase"], "paused")
            self.assertEqual(len((await self.deck.control({"action": "reports"}))["reports"]), 1)
            matched = await self.deck.control({"action": "reports", "session_id": "../untrusted"})
            self.assertEqual(len(matched["reports"]), 1)
            wrong = await self.deck.control({"action": "reports", "session_id": "different-plan"})
            self.assertEqual(wrong["reports"], [])

    async def test_reward_report_delivery_persists_competition_and_csv(self):
        from test_rewards import competitive_report
        import csv
        r = competitive_report()
        async with await self.phone() as ws:
            for _ in range(2):
                await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "competitive:1000000", "report": r}))
                self.assertEqual(json.loads(await ws.recv())["type"], "report_ack")
            saved = list((self.root / "data/reports").glob("*.json"))
            self.assertEqual(len(saved), 1)
            self.assertEqual(json.loads(saved[0].read_text())["competition"]["beat_score"], 104.8)
            with (self.root / "data/exports/all-blocks.csv").open(encoding="utf-8-sig", newline="") as f:
                rows = list(csv.DictReader(f))
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["reward_tier"], "strong")
            self.assertEqual(rows[0]["reward_acknowledged"], "True")

    async def test_disconnect_and_resync(self):
        ws = await self.phone()
        await ws.send(json.dumps({"protocol_version": 1, "type": "state", "state": {"phase": "active", "remaining_ms": 42000}}))
        await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
        await ws.recv()
        await ws.close()
        await asyncio.sleep(.05)
        result = await self.deck.control({"action": "next"})
        self.assertFalse(result["ok"])
        self.assertTrue((await self.deck.control({"action": "status"}))["state_is_cached"])
        async with await self.phone() as ws:
            await ws.send(json.dumps({"protocol_version": 1, "type": "state", "state": {"phase": "complete"}}))
            await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
            await ws.recv()
            self.assertEqual(self.deck.saved["state"]["phase"], "complete")
            self.assertEqual(len(list((self.root / "data/reports").glob("*.json"))), 1)

    async def test_report_delivery_updates_automatic_csv_without_duplicate_rows(self):
        async with await self.phone() as ws:
            for _ in range(2):
                await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
                self.assertEqual(json.loads(await ws.recv())["type"], "report_ack")
            exports = self.root / "data/exports"
            sessions = list(csv.DictReader(io.StringIO((exports / "all-sessions.csv").read_text(encoding="utf-8-sig"))))
            blocks = list(csv.DictReader(io.StringIO((exports / "all-blocks.csv").read_text(encoding="utf-8-sig"))))
            self.assertEqual(len(sessions), 1)
            self.assertEqual(sessions[0]["session_id"], report()["session_id"])
            self.assertEqual(sessions[0]["focused_ms"], "40000")
            self.assertEqual(len(blocks), 2)
            self.assertEqual([r["outcome"] for r in blocks], ["done", "timed_out"])

    async def test_csv_failure_retains_report_and_acknowledges_safe_json(self):
        reports = self.root / "data/reports"
        reports.mkdir()
        (reports / "damaged.json").write_text("{broken")
        async with await self.phone() as ws:
            await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
            error = json.loads(await ws.recv())
            self.assertEqual(error["type"], "error")
            self.assertIn("Report saved", error["message"])
            self.assertEqual(json.loads(await ws.recv())["type"], "report_ack")
            archived = [p for p in reports.glob("*.json") if p.name != "damaged.json"]
            self.assertEqual(len(archived), 1)
            self.assertEqual(json.loads(archived[0].read_text()), report())

    async def test_detailed_timeline_sync_updates_event_and_interval_logs(self):
        from test_timeline import detailed_report
        r = detailed_report()
        async with await self.phone() as ws:
            for _ in range(2):
                await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "detailed:1000000", "report": r}))
                self.assertEqual(json.loads(await ws.recv())["type"], "report_ack")
            exports = self.root / "data/exports"
            events = list(csv.DictReader(io.StringIO((exports / "all-events.csv").read_text(encoding="utf-8-sig"))))
            spans = list(csv.DictReader(io.StringIO((exports / "all-intervals.csv").read_text(encoding="utf-8-sig"))))
            self.assertEqual(len(events), 9)
            self.assertEqual([i["duration_ms"] for i in spans if i["activity_kind"] == "paused"], ["10000", "5000"])

    async def test_restart_replays_exact_pending_command(self):
        # Persist a delivery whose acknowledgement was lost, then recreate the daemon.
        command = self.deck.envelope("command", command_id="stable-id", action="next")
        self.deck.saved["pending"] = command; self.deck.persist()
        self.ws_server.close(); await self.ws_server.wait_closed()
        self.deck = DeckServer(self.cfg, self.root / "data")
        await self.start_listener()
        async with await self.phone() as ws:
            self.assertEqual(json.loads(await ws.recv()), command)
            await self.ack(ws, command)
            # A subsequent report serves as an ordering barrier for the ack.
            await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
            await ws.recv()
            self.assertIsNone(self.deck.saved["pending"])

    async def test_second_phone_is_rejected(self):
        ws = await self.phone(); await ws.close(); await asyncio.sleep(.05)
        ws2 = await connect(f"wss://127.0.0.1:{self.port}/v1/device", ssl=self.client_tls,
                            additional_headers={"Authorization": "Bearer " + self.cfg["token"]})
        await ws2.send(json.dumps({"protocol_version": 1, "type": "hello", "device_id": "different", "name": "Other"}))
        with self.assertRaises(ConnectionClosed): await ws2.recv()
        await ws2.close()

    async def test_malformed_json_does_not_end_connection(self):
        async with await self.phone() as ws:
            await ws.send("{bad json")
            self.assertEqual(json.loads(await ws.recv())["type"], "error")
            await ws.send(json.dumps({"protocol_version": 1, "type": "report", "report_key": "../untrusted:100000", "report": report()}))
            self.assertEqual(json.loads(await ws.recv())["type"], "report_ack")

    async def test_negative_ack_is_returned(self):
        async with await self.phone() as ws:
            task = asyncio.create_task(self.deck.control({"action": "next"}))
            command = json.loads(await ws.recv()); await self.ack(ws, command, False)
            self.assertFalse((await task)["ok"])

    async def test_unix_control_plane(self):
        sock = self.root / "control.sock"
        try:
            server = await asyncio.start_unix_server(self.deck.unix_handler, str(sock))
        except PermissionError:
            self.skipTest("Execution environment prohibits AF_UNIX sockets; verify on Ubuntu")
        async with server:
            reader, writer = await asyncio.open_unix_connection(str(sock))
            writer.write(b'{"action":"status"}\n'); await writer.drain()
            result = json.loads(await reader.readline())
            self.assertTrue(result["ok"]); self.assertFalse(result["connected"])
            writer.close(); await writer.wait_closed()

    async def test_control_framing_over_loopback_transport(self):
        # Tests the same production request handler when AF_UNIX is unavailable here.
        server = await asyncio.start_server(self.deck.unix_handler, "127.0.0.1", 0)
        async with server:
            port = server.sockets[0].getsockname()[1]
            for message in (b'{"action":"status"}\n', b'{bad}\n'):
                reader, writer = await asyncio.open_connection("127.0.0.1", port)
                writer.write(message); await writer.drain()
                result = json.loads(await reader.readline())
                self.assertEqual(result["ok"], message.startswith(b'{"action"'))
                writer.close(); await writer.wait_closed()


    async def test_phone_deletion_prunes_pc_and_replay_cannot_resurrect(self):
        from focusdeck import deletions
        r=report();key=f"{r['session_id']}:{r['started_at_ms']}"
        async with await self.phone() as ws:
            await ws.send(json.dumps(self.deck.envelope("report",report_key=key,report=r)))
            self.assertEqual(json.loads(await ws.recv())["type"],"report_ack")
            incoming=dict(before_ms=0,report_keys=[key],revision="phone-delete")
            await ws.send(json.dumps(self.deck.envelope("history_delete",sync_id="phone-delete",deletions=incoming)))
            self.assertEqual(json.loads(await ws.recv()),self.deck.envelope("history_delete_ack",sync_id="phone-delete"))
            state=json.loads(await ws.recv());self.assertEqual(state["type"],"history_delete")
            self.assertEqual(state["deletions"]["report_keys"],[key])
            self.assertFalse(list((self.root/"data/reports").glob("*.json")))
            self.assertFalse(list((self.root/"data/reports").glob("*.md")))
            self.assertEqual(len(list(csv.DictReader(io.StringIO((self.root/"data/exports/all-sessions.csv").read_text(encoding="utf-8-sig"))))),0)
            await ws.send(json.dumps(self.deck.envelope("report",report_key=key,report=r)))
            self.assertEqual(json.loads(await ws.recv())["type"],"report_ack")
            self.assertEqual((await self.deck.control({"action":"reports"}))["reports"],[])
            await ws.send(json.dumps(self.deck.envelope("history_delete",sync_id="phone-delete",deletions=incoming)))
            await ws.recv();await ws.recv()
            self.assertTrue(deletions.hides(self.deck.deletions,r))

    async def test_pc_offline_deletion_survives_restart_and_syncs_before_commands(self):
        r=report();save_report(self.root/"data/reports",r)
        result=await self.deck.control({"action":"delete-logs","session_id":r["session_id"]})
        self.assertTrue(result["ok"]);self.assertEqual(result["phone_sync"],"pending until reconnect")
        self.ws_server.close();await self.ws_server.wait_closed()
        self.deck=DeckServer(self.cfg,self.root/"data")
        self.deck.saved["pending"]=self.deck.envelope("command",command_id="old-command",action="next");self.deck.persist()
        await self.start_listener()
        async with await self.phone() as ws:
            first=json.loads(await ws.recv());second=json.loads(await ws.recv())
            self.assertEqual(first["type"],"history_delete");self.assertEqual(second["type"],"command")
            self.assertEqual(first["deletions"]["report_keys"],[f"{r['session_id']}:{r['started_at_ms']}"])

    async def test_pc_delete_all_syncs_and_future_report_is_retained(self):
        r=report();save_report(self.root/"data/reports",r)
        async with await self.phone() as ws:
            result=await self.deck.control({"action":"delete-logs","all":True})
            self.assertTrue(result["ok"]);state=json.loads(await ws.recv())
            self.assertEqual(state["type"],"history_delete");self.assertGreater(state["deletions"]["before_ms"],r["finished_at_ms"])
            future=copy.deepcopy(r);offset=state["deletions"]["before_ms"]
            future["started_at_ms"]+=offset;future["finished_at_ms"]+=offset
            for b in future["blocks"]:b["started_at_ms"]+=offset;b["finished_at_ms"]+=offset
            key=f"{future['session_id']}:{future['started_at_ms']}"
            await ws.send(json.dumps(self.deck.envelope("report",report_key=key,report=future)))
            self.assertEqual(json.loads(await ws.recv())["type"],"report_ack")
            self.assertEqual(len((await self.deck.control({"action":"reports"}))["reports"]),1)

    async def test_simultaneous_device_deletions_merge_instead_of_overwrite(self):
        r=report();save_report(self.root/"data/reports",r)
        key=f"{r['session_id']}:{r['started_at_ms']}"
        await self.deck.control({"action":"delete-logs","report_key":key})
        async with await self.phone() as ws:
            await ws.recv()
            incoming=dict(before_ms=0,report_keys=["other:123"],revision="concurrent-phone")
            await ws.send(json.dumps(self.deck.envelope("history_delete",sync_id="concurrent-phone",deletions=incoming)))
            ack=json.loads(await ws.recv());merged=json.loads(await ws.recv())
            self.assertEqual(ack["sync_id"],"concurrent-phone")
            self.assertEqual(set(merged["deletions"]["report_keys"]),{key,"other:123"})

    async def test_wrong_report_key_rejected_and_deletion_is_separate_from_controls(self):
        async with await self.phone() as ws:
            await ws.send(json.dumps(self.deck.envelope("report",report_key="wrong",report=report())))
            self.assertIn("Report key",json.loads(await ws.recv())["message"])
            for old in ("quality_confirm","quality_incomplete","reward_skip"):
                with self.assertRaises(ValueError):await self.deck.control({"action":old})
            with self.assertRaises(ValueError):await self.deck.control({"action":"delete-logs","all":True,"report_key":"a:123"})
            self.assertIsNone(self.deck.saved["pending"])

    async def test_malformed_stopwatch_report_keeps_connection_and_does_not_enter_archive(self):
        raw = json.loads((Path(__file__).resolve().parents[2] / 'android/app/src/test/resources/mixed-mode-report.json').read_text())
        key = f"{raw['session_id']}:{raw['started_at_ms']}"
        async with await self.phone(versions=[1,2]) as ws:
            for value in (7, 'bad', {'enabled':True,'tiers':[None]}):
                bad = copy.deepcopy(raw)
                if value == 7: bad['blocks'][0]['performance'] = value
                else: bad['blocks'][0]['performance']['reward_policy'] = value
                await ws.send(json.dumps(self.deck.envelope('report', report_key=key, report=bad)))
                response = json.loads(await asyncio.wait_for(ws.recv(), 2))
                self.assertEqual(response['type'], 'error')
                self.assertFalse(list((self.root/'data/reports').glob('*.json')))
            await ws.send(json.dumps(self.deck.envelope('report', report_key=key, report=raw)))
            response = json.loads(await asyncio.wait_for(ws.recv(), 2))
            self.assertEqual(response['type'], 'report_ack')
            saved = list((self.root/'data/reports').glob('*.json'))
            self.assertEqual(len(saved), 1)
            self.assertEqual(json.loads(saved[0].read_text()), raw)

if __name__ == "__main__": unittest.main()
