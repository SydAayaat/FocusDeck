import copy
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest
from focusdeck.common import validate_plan, validate_report
from focusdeck.rewards import beat_score, rank, rounded, SCORE_DEFAULTS
from focusdeck.history import csv_text, refresh_csv
from focusdeck.reports import markdown, save_report
from focusdeck.timeline import intervals

ROOT = Path(__file__).resolve().parents[2]


def competitive_report():
    reward = dict(tier='strong', actions=[dict(kind='walk', reps=0, seconds=60)], reason='Configured reward eligible',
                  acknowledged=True, skipped=False, acknowledged_at_ms=1100000, local_day='1970-01-01',
                  daily_reps_before={'pushups':0,'squats':0}, daily_physical_seconds_before=0)
    contract = dict(completion={'actions':[dict(kind='mobility',seconds=60)]}, strong={'actions':[dict(kind='walk',seconds=60)]})
    comparison = dict(category='algebra',complexity='medium',method='closed-book',work_units=5,target_revision='v1')
    performance = dict(quality='confirmed',quality_criteria=['Check five answers'],comparison=comparison,wall_ms=95000,
                       pace_index=111.11,focus_ratio=94.74,beat_score=104.8,rank='A',recalibration_needed=False,
                       previous_benchmark=None,new_personal_best=False,pb_delta=None,scoring=copy.deepcopy(SCORE_DEFAULTS),
                       reward=reward,reward_contract=contract)
    def e(seq,kind,ms,phase,active,paused):
        return dict(seq=seq,kind=kind,at_ms=1000000+ms,mono_ms=ms,boot=1,block_index=0,phase=phase,active_ms=active,paused_ms=paused,budget_ms=100000)
    return dict(session_id='competitive', title='Practice',status='complete',planned_ms=100000,active_ms=90000,focused_ms=90000,
                break_ms=0,paused_ms=5000,wall_ms=100000,started_at_ms=1000000,finished_at_ms=1100000,overtime_ms=0,
                timeline_coverage='complete',planned_block_count=1,recorded_timezone='UTC',
                blocks=[dict(id='a',type='focus',title='Five exercises',planned_ms=100000,budget_ms=100000,active_ms=90000,paused_ms=5000,
                             started_at_ms=1000000,finished_at_ms=1095000,outcome='done',overtime_ms=0,performance=performance)],
                events=[e(1,'block_started',0,'active',0,0),e(2,'paused',45000,'paused',45000,0),e(3,'resumed',50000,'active',45000,5000),
                        e(4,'block_done',95000,'active',90000,5000),e(5,'review_started',95000,'quality',90000,5000),
                        e(6,'quality_confirmed',96000,'quality',90000,5000),e(7,'reward_offered',96000,'reward',90000,5000),
                        e(8,'reward_acknowledged',100000,'reward',90000,5000),e(9,'session_complete',100000,'complete',90000,5000)],
                competition=dict(comparison_key='practice',planned_focus_blocks=1,completed_focus_blocks=1,on_or_ahead_count=1,
                                 completion_fraction=1.0,average_pace=111.11,focus_ratio=94.74,beat_score=104.8,rank='A',
                                 previous_benchmark=None,new_personal_best=False,pb_delta=None,
                                 reward_strategy={'style':'physical_competitive','scoring':copy.deepcopy(SCORE_DEFAULTS)},session_reward=None,
                                 reward=dict(tier='none',actions=[],reason='No reward contract',acknowledged=False,skipped=False)))


class RewardTests(unittest.TestCase):
    def test_example_typed_plan_and_legacy_plans(self):
        for path in (ROOT/'examples').glob('*.json'):
            validate_plan(json.loads(path.read_text()))

    def test_reward_plan_rejects_online_rewards_and_coffee_per_block(self):
        p=json.loads((ROOT/'examples/competitive-study.json').read_text())
        for action in [dict(kind='youtube',seconds=60),dict(kind='coffee'),dict(kind='pushups',reps=50,seconds=60),dict(kind='plank',seconds=121)]:
            altered=copy.deepcopy(p);altered['blocks'][0]['reward_contract']={'completion':{'actions':[action]}}
            with self.assertRaises(ValueError):validate_plan(altered)

    def test_permissions_defaults_and_session_only_contract_rules(self):
        p=json.loads((ROOT/'examples/competitive-study.json').read_text())
        p['reward_strategy']['allowed_rewards']=['mobility']
        with self.assertRaises(ValueError):validate_plan(p)
        p=json.loads((ROOT/'examples/competitive-study.json').read_text());p['blocks'][1]['reward_contract']={'completion':{'actions':[dict(kind='walk',seconds=60)]}}
        with self.assertRaises(ValueError):validate_plan(p)

    def test_score_gate_cap_recalibration_and_reachable_ranks(self):
        self.assertEqual(beat_score(100,100,True),100)
        self.assertEqual(beat_score(140,100,True),111)
        self.assertEqual(beat_score(250,100,True),100)
        self.assertEqual(beat_score(250,100,False),0)
        self.assertEqual(rank(111),'S+')
        self.assertEqual(rank(104.8),'A')
        self.assertEqual(rounded(106.665),106.66)
        self.assertEqual(rounded(-106.665),-106.66)
        self.assertEqual(rank(111,{'rank_thresholds':[80,90,100,111]}),'S+')

    def test_reward_report_metrics_and_review_intervals(self):
        r=competitive_report();validate_report(r)
        spans=intervals(r)
        self.assertEqual(sum(s['duration_ms'] for s in spans if s['kind']=='active'),90000)
        self.assertEqual(sum(s['duration_ms'] for s in spans if s['kind']=='paused'),5000)
        self.assertEqual(sum(s['duration_ms'] for s in spans if s['kind']=='quality_review'),1000)
        self.assertEqual(sum(s['duration_ms'] for s in spans if s['kind']=='reward'),4000)

    def test_reject_fabricated_scores_flags_and_reward_acknowledgements(self):
        for key,value in [('beat_score',999),('rank','S+'),('new_personal_best',True),('pace_index',float('nan')),('focus_ratio',101),('recalibration_needed',True)]:
            r=competitive_report();r['blocks'][0]['performance'][key]=value
            with self.assertRaises(ValueError):validate_report(r)
        for scope,key,value in [('competition','completed_focus_blocks',True),('competition','reward_strategy',None),('performance','scoring',[]),('reward','tier',[])]:
            r=competitive_report();target=r['competition'] if scope=='competition' else r['blocks'][0]['performance'] if scope=='performance' else r['blocks'][0]['performance']['reward']
            target[key]=value
            with self.assertRaises(ValueError):validate_report(r)
        r=competitive_report();r['blocks'][0]['performance']['reward']['skipped']=True
        with self.assertRaises(ValueError):validate_report(r)
        r=competitive_report();r['blocks'][0]['performance']['reward']['actions']=[dict(kind='pushups',reps=12,seconds=60)]
        with self.assertRaises(ValueError):validate_report(r)

    def test_quality_gate_cannot_be_bypassed_in_report(self):
        r=competitive_report();r['blocks'][0]['performance']['quality']='incomplete'
        with self.assertRaises(ValueError):validate_report(r)
        r=competitive_report();r['blocks'][0]['performance']['quality']='pending'
        with self.assertRaises(ValueError):validate_report(r)

    def test_session_score_includes_missing_or_incomplete_work(self):
        r=competitive_report();r['status']='stopped';r['events'][-1]['kind']='session_stopped';r['events'][-1]['phase']='stopped';r['planned_block_count']=2
        r['competition'].update(planned_focus_blocks=2,completion_fraction=.5,beat_score=52.4,rank='C')
        validate_report(r)
        r['competition']['beat_score']=104.8
        with self.assertRaises(ValueError):validate_report(r)

    def test_pb_requires_benchmark_delta_and_explicit_comparison(self):
        r=competitive_report();p=r['blocks'][0]['performance'];p.update(previous_benchmark=dict(report_key='old:123',block_id='old',score=100,active_ms=100000),pb_delta=4.8,new_personal_best=True)
        validate_report(r)
        p['comparison']=None
        with self.assertRaises(ValueError):validate_report(r)
        p['comparison']=competitive_report()['blocks'][0]['performance']['comparison'];p['pb_delta']=999
        with self.assertRaises(ValueError):validate_report(r)

    def test_below_pb_is_a_valid_nonpunitive_comparison(self):
        r=competitive_report();p=r['blocks'][0]['performance']
        p.update(previous_benchmark=dict(report_key='old:123',block_id='old',score=110,active_ms=85000),pb_delta=-5.2,new_personal_best=False)
        validate_report(r)
        self.assertIn('"pb_delta": -5.2',markdown(r))

    def test_policy_fields_and_coffee_rules_are_bounded(self):
        for field,value in [('rank_thresholds',[95,85,105,110]),('target_focus',float('inf')),('recalibration_pace',110)]:
            p=json.loads((ROOT/'examples/competitive-study.json').read_text());p['reward_strategy']['scoring']={field:value}
            with self.assertRaises(ValueError):validate_plan(p)
        for field,value in [('minimum_productive_session_seconds',60),('minimum_hours_between_coffee_rewards',0),('latest_allowed_local_time','25:99')]:
            p=json.loads((ROOT/'examples/competitive-study.json').read_text());p['reward_strategy']['coffee_reward'][field]=value
            with self.assertRaises(ValueError):validate_plan(p)

    def test_automatic_csv_and_markdown_preserve_contract_and_acknowledgement(self):
        r=competitive_report();validate_report(r)
        row=next(csv.DictReader(io.StringIO(csv_text([r]))))
        self.assertEqual(row['beat_score'],'104.8');self.assertEqual(row['reward_acknowledged'],'True')
        self.assertEqual(json.loads(row['comparison_json'])['work_units'],5)
        self.assertEqual(json.loads(row['reward_contract_json'])['strong']['actions'][0]['kind'],'walk')
        session=next(csv.DictReader(io.StringIO(csv_text([r],'sessions'))))
        self.assertEqual(session['completed_focus_blocks'],'1');self.assertEqual(session['on_or_ahead_count'],'1')
        text=markdown(r);self.assertIn('BeatScore=104.8 A',text);self.assertIn('NO BENCHMARK YET',text)
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp);save_report(path/'reports',r);save_report(path/'reports',r);refresh_csv(path/'reports',path/'exports')
            rows=list(csv.DictReader(io.StringIO((path/'exports/all-blocks.csv').read_text(encoding='utf-8-sig'))))
            self.assertEqual(len(rows),1);self.assertEqual(rows[0]['quality'],'confirmed')

    def test_competition_fixture_roundtrip_matches_android_resource(self):
        fixture=ROOT/'android/app/src/test/resources/competitive-report.json'
        r=json.loads(fixture.read_text());validate_report(r)
        self.assertEqual(r,competitive_report())
