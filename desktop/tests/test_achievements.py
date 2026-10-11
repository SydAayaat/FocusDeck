import asyncio
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from focusdeck.achievements import validate_command, SnapshotReceiver, query
from focusdeck.server import DeckServer
from focusdeck.common import atomic_json

def command(operation="event", **fields):
    return dict(achievement_version=1,operation=operation,id="event-1",timestamp="2026-10-07T12:00:00+02:00",**fields)

class AchievementValidationTests(unittest.TestCase):
    def test_german_completion(self):
        c=command(achievement_id="german",event="completed");self.assertEqual(validate_command(c),c)

    def test_abstinence_initialize_and_break(self):
        for event in ("initialize","streak_break"):validate_command(command(achievement_id="porn_free",event=event))

    def test_dynamic_projects_and_college(self):
        for cat in ("projects","college"):
            validate_command(command("upsert",definition=dict(id="custom_subject",title="User supplied name",category=cat,daily_participation=True,metadata={"module":"2"})))

    def test_daily_target_variable_objectives(self):
        validate_command(command("daily_target",date="2026-10-07",objectives=[dict(kind="focus_blocks",minimum=5),dict(kind="focus_time",minimum=10800000),dict(kind="achievement",achievement_id="german")]))

    def test_archive(self):validate_command(command("archive",achievement_id="custom_compute"))

    def test_contract_two_custom_cards_across_categories_and_manual_kinds(self):
        for category in ('discipline','health','learning','focus','projects','college'):
            for kind in ('daily','scheduled','abstinence'):
                c=command('upsert',definition=dict(id='custom_card',title='Exact GPT name',category=category,kind=kind))
                c['achievement_version']=2
                self.assertEqual(c,validate_command(c))
        for kind in ('focus_time','focus_blocks','contract'):
            c=command('upsert',definition=dict(id='custom_card',title='No invented automatic rule',category='focus',kind=kind));c['achievement_version']=2
            with self.assertRaises(ValueError):validate_command(c)

    def test_contract_two_management_requires_explicit_delete_confirmation(self):
        for operation in ('archive','restore','delete'):
            c=command(operation,achievement_id='german');c['achievement_version']=2
            if operation=='delete':c['confirm']=True
            self.assertEqual(c,validate_command(c))
        for confirmation in (None,False,1,'true'):
            c=command('delete',achievement_id='german');c['achievement_version']=2;c['confirm']=confirmation
            with self.subTest(confirmation=confirmation),self.assertRaises(ValueError):validate_command(c)
        for operation in ('restore','delete'):
            with self.assertRaises(ValueError):validate_command(command(operation,achievement_id='german'))

    def test_cli_management_generates_confirmed_contract_two_commands(self):
        from focusdeck.cli import main
        sent=[]
        async def daemon(payload):sent.append(payload);return {'ok':True}
        for operation in ('archive','restore','delete'):
            args=['focusdeck','achievements',operation,'german']+(['--yes'] if operation=='delete' else [])
            with patch.object(sys,'argv',args),patch('focusdeck.cli.request_daemon',daemon),patch('builtins.print'):
                self.assertEqual(0,main())
            value=sent[-1]['achievement'];validate_command(value)
            self.assertEqual(2,value['achievement_version']);self.assertEqual(operation,value['operation'])
        self.assertTrue(sent[-1]['achievement']['confirm'])
        self.assertEqual(3,len({v['achievement']['id'] for v in sent}))
        with patch.object(sys,'argv',['focusdeck','achievements','delete','german']),patch('focusdeck.cli.request_daemon',daemon),patch('builtins.print'):
            with self.assertRaises(SystemExit) as exit_result:main()
            self.assertEqual(2,exit_result.exception.code)
        self.assertEqual(3,len(sent))

    def test_bad_timestamps_versions_types_and_combinations(self):
        original=command(achievement_id="german",event="completed")
        for edit in ({"timestamp":"2026-10-07T12:00:00"},{"timestamp":"bad"},{"achievement_version":True},{"event":"failed"},{"amount":True},{"amount":0},{"objectives":[]},{"definition":{}},{"id":"../bad"}):
            c=copy.deepcopy(original);c.update(edit)
            with self.subTest(edit=edit),self.assertRaises(ValueError):validate_command(c)

    def test_bad_target_objectives(self):
        for objectives in ([],[dict(kind="target_time",minimum=10)],[dict(kind="achievement")],[dict(kind="focus_blocks",achievement_id="german")],[dict(kind="focus_blocks"),dict(kind="focus_blocks",minimum=8)]):
            with self.subTest(objectives=objectives),self.assertRaises(ValueError):validate_command(command("daily_target",date="2026-10-07",objectives=objectives))

    def test_builtins_cannot_be_defined_as_dynamic(self):
        with self.assertRaises(ValueError):validate_command(command("upsert",definition=dict(id="focus_5",title="Fake",category="focus")))

    def test_actual_cli_stdin_validation_and_rejection(self):
        cmd=[sys.executable,"-m","focusdeck.cli","achievements","validate","-"]
        result=subprocess.run(cmd,input=json.dumps(command(achievement_id="german",event="completed")),text=True,capture_output=True)
        self.assertEqual(0,result.returncode,result.stderr)
        result=subprocess.run(cmd,input='{}',text=True,capture_output=True);self.assertEqual(2,result.returncode)

class AchievementSnapshotTests(unittest.TestCase):
    def snapshot(self):
        return dict(achievement_version=1,as_of_ms=1000,zone="Europe/Berlin",summary=[dict(id="german",current=2,record_gap=None)],ledger=dict(definitions=[dict(id="german")],events=[dict(achievementId="german")],segments=[dict(achievementId="german",value=3)],days={},targets=[]))

    def chunk(self,index,data,count=2):return dict(achievement_version=1,snapshot_id="snapshot-1",index=index,count=count,data=data)

    def test_complete_chunks_persist_atomically_and_query_history_offline(self):
        with tempfile.TemporaryDirectory() as root:
            receiver=SnapshotReceiver(root);text=json.dumps(self.snapshot());mid=len(text)//2
            self.assertIsNone(receiver.receive(self.chunk(0,text[:mid])))
            self.assertFalse((Path(root)/"achievements.json").exists())
            self.assertEqual(self.snapshot(),receiver.receive(self.chunk(1,text[mid:])))
            history=query(root,"german");self.assertEqual(1,len(history["segments"]));self.assertTrue(history["cached"])
            self.assertEqual(2,query(root)["summary"][0]["current"])

    def test_disconnect_and_out_of_order_preserve_previous_cache(self):
        with tempfile.TemporaryDirectory() as root:
            atomic_json(Path(root)/"achievements.json",self.snapshot());receiver=SnapshotReceiver(root)
            receiver.receive(self.chunk(0,'{"achievement_version":'));receiver.close()
            self.assertEqual(2,query(root)["summary"][0]["current"])
            with self.assertRaises(ValueError):receiver.receive(self.chunk(1,'1}'))
            self.assertEqual(2,query(root)["summary"][0]["current"])

    def test_invalid_final_payload_does_not_replace_cache(self):
        with tempfile.TemporaryDirectory() as root:
            atomic_json(Path(root)/"achievements.json",self.snapshot());receiver=SnapshotReceiver(root)
            with self.assertRaises(ValueError):receiver.receive(self.chunk(0,'{}',1))
            self.assertEqual(2,query(root)["summary"][0]["current"])

    def test_empty_cache_is_honest(self):
        with tempfile.TemporaryDirectory() as root:self.assertEqual([],query(root)["summary"])

    def test_actual_cli_queries_with_daemon_offline(self):
        with tempfile.TemporaryDirectory() as root:
            atomic_json(Path(root)/"achievements.json",self.snapshot())
            env=dict(os.environ,FOCUSDECK_DATA=root)
            result=subprocess.run([sys.executable,"-m","focusdeck.cli","achievements","history","german"],capture_output=True,text=True,env=env)
            self.assertEqual(0,result.returncode,result.stderr);self.assertTrue(json.loads(result.stdout)["cached"])

class AchievementControlTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp=tempfile.TemporaryDirectory();self.server=DeckServer(dict(token="x"*40,desktop_id="test"),Path(self.temp.name))
    async def asyncTearDown(self):self.temp.cleanup()

    async def test_old_phone_rejected_without_pending_command(self):
        self.server.phone=object();self.server.device={"supported_session_versions":[1,2]}
        result=await self.server.control(dict(action="achievements",achievement=command(achievement_id="german",event="completed")))
        self.assertFalse(result["ok"]);self.assertIn("1.9",result["error"]);self.assertIsNone(self.server.saved["pending"])

    async def test_offline_commands_follow_existing_no_queue_policy(self):
        result=await self.server.control(dict(action="achievements",achievement=command(achievement_id="german",event="completed")))
        self.assertFalse(result["ok"]);self.assertIsNone(self.server.saved["pending"])

    async def test_contract_two_is_rejected_by_contract_one_phone(self):
        self.server.phone=object();self.server.device={'supported_achievement_versions':[1]}
        value=command('restore',achievement_id='german');value['achievement_version']=2
        result=await self.server.control(dict(action='achievements',achievement=value))
        self.assertFalse(result['ok']);self.assertIsNone(self.server.saved['pending'])

    async def test_existing_pending_ack_replay_preserves_event_id(self):
        server=self.server
        class Phone:
            async def send(self,raw):
                value=json.loads(raw);self.value=value
                self.persisted=json.loads((server.root/"daemon.json").read_text())["pending"]
                server.waiters[value["command_id"]].set_result(dict(ok=True))
        phone=Phone();server.phone=phone;server.device={"supported_achievement_versions":[1]}
        event=command(achievement_id="german",event="completed")
        result=await server.control(dict(action="achievements",achievement=event))
        self.assertTrue(result["ok"]);self.assertEqual(event,phone.value["achievement"]);self.assertEqual(phone.persisted,phone.value)

    async def test_log_deletion_never_erases_or_reimports_achievement_ledger(self):
        atomic_json(self.server.root/"achievements.json",AchievementSnapshotTests().snapshot())
        await self.server.control(dict(action="delete-logs",all=True))
        self.assertEqual(2,query(self.server.root)["summary"][0]["current"])
