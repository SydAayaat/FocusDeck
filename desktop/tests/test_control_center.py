"""Control-panel parsing, preservation, identity isolation and uncertain delivery regressions."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from focusdeck.common import atomic_json, validate_plan
from focusdeck.control_center import ControlCenter, parse_draft, DEFAULTS, definition_hash
from focusdeck.server import DeckServer

ROOT=Path(__file__).resolve().parents[2]
CARD='FOCUSDECK CARD\nTitle: German practice\nType: hybrid\nCategory: learning\nUnit: words\nTarget: 1250\nCurrent: 200'
SESSION='FOCUSDECK SESSION\nTitle: Calculus\nFocus 25m | Review\nBreak 5m | Stretch\nStopwatch | Problems'

class ParserTests(unittest.TestCase):
    def test_card_and_modes(self):
        c=parse_draft(CARD); self.assertEqual(c['kind'],'card');self.assertEqual(c['draft']['progress']['target_value'],1250)
        s=parse_draft(SESSION)['draft'];self.assertEqual(s['blocks'][2]['timer_mode'],'stopwatch');self.assertNotIn('duration_seconds',s['blocks'][2]);validate_plan(s)
    def test_no_prose_guessing_or_ambiguous_fields(self):
        for text in ['Make me a German card',CARD+'\nTitle: Duplicate',CARD.replace('1250','nan'),SESSION+'\nSomething else',CARD.replace('hybrid','maybe')]:
            with self.subTest(text=text),self.assertRaises(ValueError):parse_draft(text)
    def test_advanced_policy_and_original_ids_survive_json_paste(self):
        plan=json.loads((ROOT/'examples/mixed-mode-study.json').read_text()); result=parse_draft('```json\n'+json.dumps(plan)+'\n```')['draft'];self.assertEqual(result,plan)
    def test_progress_command_receipt_preserved(self):
        cmd=json.loads((ROOT/'examples/achievements/german-progress-v3.json').read_text());self.assertEqual(parse_draft(json.dumps(cmd))['draft'],cmd)

class PanelTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.calls=[];self.connected=True;self.pending=None;self.fail=False
        self.state=dict(phase='active',session_id='running',block_index=0)
        self.center=ControlCenter(self.root,self.transport)
        self.definition=dict(id='custom_card',title='Original',category='learning',kind='daily',description='',streak_enabled=True,progress_enabled=True,progress=dict(current_value=12,target_value=20,unit='topics'),metadata={'existing':'preserve'})
        atomic_json(self.root/'viewer-source.json',dict(authority_device_id='phone'))
        atomic_json(self.root/'achievements.json',dict(ledger=dict(definitions=[self.definition]),summary=[],zone='Europe/Berlin',as_of_ms=1))
    def tearDown(self):self.temp.cleanup()
    def transport(self,p):
        self.calls.append(copy.deepcopy(p));a=p['action']
        if a=='status':return dict(ok=True,connected=self.connected,state=self.state,pending_command=self.pending)
        if a=='devices':return dict(ok=True,devices=[],viewers=[],paired_id='phone')
        if self.fail:return dict(ok=False,error='Acknowledgement timed out.',command_id='transport')
        return dict(ok=True,command_id='transport')
    def draft(self):
        d=copy.deepcopy(self.definition);d['title']='Edited';d['progress']['current_value']=999
        return dict(definition=d,base_hash=definition_hash(self.definition),update_progress=False,update_icon=False)
    def test_config_edit_does_not_overwrite_measured_progress(self):
        result=self.center.handle('deliver',dict(kind='card',draft=self.draft()))
        self.assertEqual(result['state'],'applied');sent=next(p['achievement'] for p in self.calls if p['action']=='achievements')
        self.assertNotIn('progress',sent['definition']);self.assertEqual(sent['definition']['metadata']['existing'],'preserve')
    def test_switch_new_hybrid_draft_to_streak_keeps_hidden_progress_out_of_command(self):
        definition=parse_draft(CARD)['draft'];definition['progress_enabled']=False
        commands=self.center.card_commands(dict(definition=definition,update_icon=False,update_progress=False))
        self.assertNotIn('progress',commands[0]['definition'])
    def test_stale_card_configuration_rejected(self):
        draft=self.draft(); draft['base_hash']='outdated'
        with self.assertRaisesRegex(ValueError,'changed'):self.center.card_commands(draft)
        self.assertFalse(any(p['action']=='achievements' for p in self.calls))
    def test_foreign_phone_cache_not_exposed(self):
        atomic_json(self.root/'viewer-source.json',dict(authority_device_id='old-phone'))
        self.assertEqual(self.center.snapshot()['achievements'],{})
    def test_uncertain_retry_reuses_original_receipt_and_completed_steps(self):
        self.fail=True;result=self.center.handle('deliver',dict(kind='card',draft=self.draft()))
        self.assertEqual(result['state'],'needs_review');receipt=result['requests'][0]['achievement']['id']
        self.fail=False;retry=self.center.handle('deliver',dict(retry_id=result['id']))
        self.assertEqual(retry['state'],'applied');commands=[p['achievement']['id'] for p in self.calls if p['action']=='achievements'];self.assertEqual(commands,[receipt,receipt])
    def test_pending_command_blocks_retry(self):
        self.fail=True;result=self.center.handle('deliver',dict(kind='card',draft=self.draft()));self.fail=False;self.pending={'command_id':'transport'}
        result=self.center.handle('deliver',dict(retry_id=result['id']));self.assertEqual(result['state'],'needs_review');self.assertEqual(len([p for p in self.calls if p['action']=='achievements']),1)
    def test_session_launch_fresh_identity_and_retry_reconciliation(self):
        plan=parse_draft(SESSION)['draft'];self.fail=True;r=self.center.handle('deliver',dict(kind='session',draft=plan));sid=r['requests'][0]['session']['session_id'];self.assertNotEqual(sid,plan['session_id'])
        self.state['session_id']=sid;self.fail=False;r=self.center.handle('deliver',dict(retry_id=r['id']));self.assertEqual(r['state'],'applied');self.assertEqual(len([p for p in self.calls if p['action']=='send']),1)
    def test_draft_storage_never_writes_authoritative_ledger(self):
        before=(self.root/'achievements.json').read_bytes();self.center.handle('save-draft',dict(kind='card',draft=self.draft()));self.assertEqual((self.root/'achievements.json').read_bytes(),before)
        self.assertEqual((self.root/'control-center/drafts.json').stat().st_mode & 0o777,0o600)
    def test_stale_session_controls_and_confirmation(self):
        with self.assertRaisesRegex(ValueError,'changed'):self.center.handle('session-control',dict(operation='pause',session_id='old',block_index=0))
        with self.assertRaisesRegex(ValueError,'Confirm'):self.center.handle('session-control',dict(operation='stop',session_id='running',block_index=0))
    def test_protected_builtin_config_not_upserted(self):
        self.definition['id']='german';atomic_json(self.root/'achievements.json',dict(ledger=dict(definitions=[self.definition]),summary=[]))
        draft=self.draft();draft.update(update_icon=True,icon={'kind':'builtin','name':'book-open'})
        commands=self.center.card_commands(draft);self.assertEqual([c['operation'] for c in commands],['icon_set'])
    def test_settings_and_empty_archive_export(self):
        self.center.handle('settings',DEFAULTS);r=self.center.handle('export',dict(directory=str(self.root)));self.assertEqual(len(r['files']),5);self.assertTrue(all(Path(f['file']).is_file() for f in r['files']))
    def test_partial_batch_does_not_reapply_successful_configuration(self):
        draft=self.draft();draft.update(update_icon=True,icon={'kind':'builtin','name':'cpu'})
        base=self.transport;count=[0]
        def fault(p):
            if p['action']=='achievements':
                count[0]+=1
                if count[0]==2:
                    self.calls.append(copy.deepcopy(p));return dict(ok=False,error='Acknowledgement timed out.')
            return base(p)
        self.center.transport=fault
        r=self.center.handle('deliver',dict(kind='card',draft=draft));self.assertEqual(r['step'],1)
        r=self.center.handle('deliver',dict(retry_id=r['id']));self.assertEqual(r['state'],'applied')
        ops=[p['achievement']['operation'] for p in self.calls if p['action']=='achievements'];self.assertEqual(ops,['upsert','icon_set','icon_set'])
    def test_today_target_preserves_other_objectives(self):
        from datetime import datetime
        from zoneinfo import ZoneInfo
        date=datetime.now(ZoneInfo('Europe/Berlin')).date().isoformat()
        snapshot=json.loads((self.root/'achievements.json').read_text());snapshot['ledger']['targets']=[dict(id='existing',atMs=1,date=date,objectives=[dict(kind='focus_blocks',minimum=3,text='Keep this')])]
        atomic_json(self.root/'achievements.json',snapshot)
        r=self.center.handle('today-target',dict(id='custom_card',minimum=2,confirm=True));self.assertEqual(r['state'],'applied')
        target=r['requests'][0]['achievement'];self.assertEqual(len(target['objectives']),2);self.assertEqual(target['objectives'][0]['text'],'Keep this')
    def test_icon_import_is_bounded_png(self):
        from PIL import Image
        import io
        from focusdeck.control_center import normalize_image
        from focusdeck.icons import validate_icon
        stream=io.BytesIO();Image.new('RGB',(1000,600),'gold').save(stream,format='JPEG')
        validate_icon(normalize_image(stream.getvalue()))

class ControllerGuardTests(unittest.IsolatedAsyncioTestCase):
    async def test_server_checks_observed_block_before_transport(self):
        with tempfile.TemporaryDirectory() as temp:
            server=DeckServer(dict(desktop_id='d'),Path(temp));server.phone=object();server.saved['state']=dict(session_id='s',block_index=1)
            result=await server.control(dict(action='skip_block',expected_session_id='s',expected_block_index=0));self.assertFalse(result['ok']);self.assertIn('changed',result['error'])

import test_focusdeck as baseline

class PanelTransportTests(unittest.IsolatedAsyncioTestCase):
    asyncSetUp=baseline.TransportTests.asyncSetUp
    asyncTearDown=baseline.TransportTests.asyncTearDown
    phone=baseline.TransportTests.phone
    ack=baseline.TransportTests.ack
    start_listener=baseline.TransportTests.start_listener
    async def test_skip_and_end_wire_commands_ack_on_paired_phone(self):
        import asyncio
        from jsonschema import Draft202012Validator
        schema=json.loads((ROOT/'protocol/wire.schema.json').read_text())
        async with await self.phone() as ws:
            self.deck.saved['state']=dict(session_id='running',block_index=0)
            for action in ('skip_block','end_block'):
                task=asyncio.create_task(self.deck.control(dict(action=action,expected_session_id='running',expected_block_index=0)))
                message=json.loads(await ws.recv());Draft202012Validator(schema).validate(message)
                self.assertEqual(message['action'],action);await self.ack(ws,message);self.assertTrue((await task)['ok'])
