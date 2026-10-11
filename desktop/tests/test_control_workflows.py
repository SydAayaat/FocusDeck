"""Contract parity, bounded paste, receipt retry and selected-report regressions."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
import yaml
from focusdeck.draft_input import parse_draft, plain_load
from focusdeck.control_center import ControlCenter
from focusdeck.common import atomic_json
from focusdeck.reports import save_report, markdown
from test_focusdeck import report

ROOT=Path(__file__).resolve().parents[2]

class CompleteInputTests(unittest.TestCase):
    def test_every_existing_session_example_round_trips_without_policy_changes(self):
        tested=0
        for path in (ROOT/'examples').glob('*.json'):
            value=json.loads(path.read_text())
            if 'blocks' not in value: continue
            result=parse_draft('FOCUSDECK SESSION\n'+yaml.safe_dump(value,sort_keys=False))
            self.assertEqual(result['draft'],value,path.name);tested+=1
        self.assertGreater(tested,5)
    def test_every_achievement_example_preserves_receipt_and_fields(self):
        for path in (ROOT/'examples/achievements').glob('*.json'):
            value=json.loads(path.read_text())
            if 'operation' not in value: continue
            self.assertEqual(parse_draft('FOCUSDECK UPDATE\n'+yaml.safe_dump(value,sort_keys=False))['draft'],value,path.name)
            self.assertEqual(parse_draft(json.dumps(value))['draft'],value,path.name)
    def test_rewards_with_shortcuts_and_independent_session_eligibility(self):
        value=dict(title='Mixed practice',blocks=[dict(focus='25m',title='Recall',session_weight=2,quality_criteria=['Recall without notes'],reward_policy=dict(enabled=True,selection='first_satisfied',tiers=[dict(condition='completed',min_focus_ratio=85,reward=dict(text='Walk'),alternatives=[dict(text='Stretch')])])),dict(stopwatch=True,title='Review')],session_reward_policy=dict(enabled=True,eligibility=dict(require_all_focus_completed=True,minimum_completed_blocks=2),tiers=[dict(condition='session_completed',reward=dict(text='Tea'))]))
        result=parse_draft('FOCUSDECK SESSION\n'+yaml.safe_dump(value))['draft']
        self.assertEqual(result['session_reward_policy'],value['session_reward_policy']);self.assertEqual(result['blocks'][0]['reward_policy'],value['blocks'][0]['reward_policy'])
        self.assertEqual(result['blocks'][0]['duration_seconds'],1500);self.assertNotIn('duration_seconds',result['blocks'][1])
    def test_milestone_references_and_stopwatch_score_rules_still_validate(self):
        text='FOCUSDECK SESSION\ntitle: Review\nblocks:\n  - stopwatch: true\n    title: Review\n    reward_policy:\n      enabled: true\n      tiers:\n        - condition: completed\n          min_beat_score: 80\n          reward:\n            text: Tea'
        with self.assertRaisesRegex(ValueError,'time-based'):parse_draft(text)
        with self.assertRaisesRegex(ValueError,'unknown'):parse_draft(text.replace('min_beat_score: 80','block_ids: [missing]').replace('condition: completed','condition: module_completed'))
    def test_all_nine_operations_and_short_aliases(self):
        variants=[dict(operation='upsert',definition=dict(id='paper',title='Paper',category='projects',metadata={'method':'Read'})),dict(operation='archive',card='paper'),dict(operation='restore',card='paper'),dict(operation='delete',card='paper',confirm=True),dict(operation='event',card='paper',event='objective_completed',amount=2,text='Actual work',timestamp='2026-10-08T11:00:00+02:00'),dict(operation='daily_target',date='2026-10-08',objectives=[dict(kind='focus_time',minimum=3600000),dict(kind='achievement',achievement_id='paper',minimum=1,text='Review')]),dict(operation='progress_set',card='paper',current=10,target=20,unit='papers'),dict(operation='progress_increment',card='paper',amount=2),dict(operation='icon_set',card='paper',icon=dict(kind='builtin',name='book-open'))]
        result=parse_draft('FOCUSDECK BATCH\n'+yaml.safe_dump(variants))
        self.assertEqual(result['kind'],'batch');self.assertEqual(len(result['draft']),9);self.assertEqual(len({c['id'] for c in result['draft']}),9)
        self.assertEqual(result['draft'][4]['timestamp'],variants[4]['timestamp']);self.assertEqual(result['draft'][6]['current_value'],10)
    def test_duplicate_fields_aliases_and_expansion_rejected(self):
        for value in ['operation: archive\noperation: delete','operation: archive\ncard: paper\nachievement_id: paper','operation: archive\ncard: &ref paper','operation: archive\ncard: !!str paper','a: '+('['*41)+(']'*41)]:
            with self.subTest(value=value),self.assertRaises(ValueError):parse_draft('FOCUSDECK UPDATE\n'+value)
        with self.assertRaises(ValueError):parse_draft('{"title":"A","title":"B"}')
    def test_plain_scalars_keep_dates_names_text_and_numbers_distinct(self):
        value=plain_load('date: 2026-10-08\ntext: yes\nflag: false\nnumber: 12\namount: 0.25\nstring: "12"')
        self.assertEqual(value,dict(date='2026-10-08',text='yes',flag=False,number=12,amount=.25,string='12'))
    def test_no_silent_defaults_for_structured_rewards(self):
        result=parse_draft('FOCUSDECK SESSION\ntitle: Study\nblocks:\n  - focus: 10m\n    title: Read')['draft']
        self.assertNotIn('reward_policy',result['blocks'][0])
    def test_batch_duplicate_receipts_rejected(self):
        cmd=parse_draft('FOCUSDECK UPDATE\noperation: progress_increment\ncard: german\namount: 5')['draft']
        with self.assertRaisesRegex(ValueError,'unique'):parse_draft(json.dumps([cmd,cmd]))

class ReportAndReceiptTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)/'private';self.root.mkdir();self.calls=[];self.failed=False
        self.center=ControlCenter(self.root,self.transport);self.report=report();self.report['title']='Saved study • German';save_report(self.root/'reports',self.report)
        self.key=f"{self.report['session_id']}:{self.report['started_at_ms']}"
    def tearDown(self):self.temp.cleanup()
    def transport(self,p):
        self.calls.append(copy.deepcopy(p))
        if p['action']=='status':return dict(ok=True,connected=True,state={},pending_command=None)
        if self.failed and p['action']=='achievements' and p['achievement']['operation']=='progress_increment':return dict(ok=False,error='Lost ACK')
        return dict(ok=True,phone_sync='pending until reconnect',deleted_pc_reports=1)
    def test_selected_attempt_can_copy_readable_or_exact_json_offline(self):
        self.center.transport=lambda p: (_ for _ in ()).throw(OSError('Offline'))
        self.assertEqual(self.center.handle('report-text',dict(key=self.key))['text'],markdown(self.report))
        self.assertEqual(json.loads(self.center.handle('report-text',dict(key=self.key,format='json'))['text']),self.report)
    def test_text_save_is_utf8_exact_and_private(self):
        path=Path(self.temp.name)/'session.txt'
        self.center.handle('save-report',dict(key=self.key,path=str(path)))
        self.assertEqual(path.read_text(),markdown(self.report));self.assertEqual(path.stat().st_mode&0o777,0o600)
    def test_save_cannot_overwrite_authoritative_data(self):
        with self.assertRaisesRegex(ValueError,'outside'):self.center.handle('save-report',dict(key=self.key,path=str(self.root/'reports'/'original.json')))
    def test_deleted_report_cannot_be_copied_or_saved_from_stale_view(self):
        atomic_json(self.root/'deletions.json',dict(before_ms=0,report_keys=[self.key],revision='deleted'))
        for action in ('report-text','save-report'):
            with self.assertRaisesRegex(ValueError,'no longer'):self.center.handle(action,dict(key=self.key,path=str(Path(self.temp.name)/'copy.txt')))
    def test_history_delete_and_pairing_recovery_require_confirmation(self):
        for action in ('delete-reports','forget-device'):
            with self.assertRaisesRegex(ValueError,'Confirm'):self.center.handle(action,dict(key=self.key))
        self.center.handle('delete-reports',dict(key=self.key,confirm=True));self.assertEqual(self.calls[-1],dict(action='delete-logs',report_key=self.key))
        self.center.handle('forget-device',dict(confirm=True));self.assertEqual(self.calls[-1],dict(action='forget-device'))
    def test_ordered_update_retry_skips_acknowledged_steps_and_retains_receipts(self):
        commands=parse_draft('FOCUSDECK BATCH\n- operation: archive\n  card: paper\n- operation: progress_increment\n  card: german\n  amount: 20')['draft']
        self.failed=True;first=self.center.handle('deliver',dict(kind='batch',draft=commands));self.assertEqual(first['step'],1)
        self.failed=False;retry=self.center.handle('deliver',dict(retry_id=first['id']));self.assertEqual(retry['state'],'applied')
        sent=[p['achievement']['id'] for p in self.calls if p['action']=='achievements'];self.assertEqual(sent,[commands[0]['id'],commands[1]['id'],commands[1]['id']])
    def test_previously_used_receipt_cannot_be_edited_into_another_write(self):
        c=parse_draft('FOCUSDECK UPDATE\noperation: progress_increment\ncard: german\namount: 5')['draft'];self.center.handle('deliver',dict(kind='command',draft=c));c['amount']=6
        with self.assertRaisesRegex(ValueError,'different fields'):self.center.handle('deliver',dict(kind='command',draft=c))
    def test_send_and_start_uses_fresh_plan_and_reconciles_uncertain_retry(self):
        plan=parse_draft('FOCUSDECK SESSION\ntitle: Study\nblocks:\n  - focus: 25m\n    title: Read')['draft']
        result=self.center.handle('deliver',dict(kind='session',draft=plan,start=True))
        request=result['requests'][0]
        self.assertEqual(request['action'],'start');self.assertNotEqual(request['session']['session_id'],plan['session_id'])
    def test_restart_uses_only_installed_owner_service_without_shell(self):
        from unittest.mock import patch
        with patch('subprocess.run') as run:
            run.return_value.returncode=0
            self.assertTrue(self.center.handle('restart-service')['started'])
            self.assertEqual(run.call_args.args[0],['systemctl','--user','restart','focusdeck.service'])
            self.assertNotIn('shell',run.call_args.kwargs)
    def test_contract_editor_catalogue_matches_protocol_sources(self):
        text=(ROOT/'desktop/src/focusdeck/control_ui/contracts.js').read_text();catalogue=json.JSONDecoder().raw_decode(text.split('const draftContracts=',1)[1])[0]
        self.assertEqual(catalogue['session'],json.loads((ROOT/'protocol/session-v2.schema.json').read_text()))
        commands=json.loads((ROOT/'protocol/achievement-command-v4.schema.json').read_text())['oneOf'];self.assertEqual(catalogue['commands'],{c['properties']['operation']['const']:c for c in commands})
