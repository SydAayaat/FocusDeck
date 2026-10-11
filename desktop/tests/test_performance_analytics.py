import copy
import datetime as dt
import json
import tempfile
import unittest
from pathlib import Path
from focusdeck.analytics import dataset, snapshot, validate_snapshot, instant
from focusdeck.performance import score, estimation, plan_records, summary, performance_report, comparison, period_bounds
from focusdeck.common import validate_report
from focusdeck.control_center import ControlCenter
from focusdeck.reports import save_report
from focusdeck.common import atomic_json
from test_calendar_analytics import fixture

ROOT=Path(__file__).resolve().parents[2]
class PerformanceTests(unittest.TestCase):
    def test_shared_golden_score_and_component_coverage(self):
        for case in json.loads((ROOT/'protocol/fixtures/performance-score.json').read_text()):
            with self.subTest(case=case['name']):
                actual=score(case['records'])
                for key,expected in case['expected'].items():
                    if expected is None:self.assertIsNone(actual[key])
                    else:self.assertAlmostEqual(actual[key],expected)
        self.assertTrue(score(json.loads((ROOT/'protocol/fixtures/performance-score.json').read_text())[1]['records'])['partial'])

    def test_signed_errors_and_capped_efficiency(self):
        self.assertEqual(estimation(60000,30000)['signed_error_ms'],-30000)
        self.assertEqual(estimation(60000,30000)['interpretation'],'plan overestimated duration')
        self.assertEqual(estimation(60000,180000)['accuracy'],0)
        self.assertEqual(estimation(60000,0)['accuracy'],0)
        self.assertIsNone(estimation(0,300));self.assertIsNone(estimation(None,300))

    def test_absolute_errors_do_not_cancel_by_category(self):
        cases=json.loads((ROOT/'protocol/fixtures/performance-score.json').read_text());row=cases[0]['records'][0]
        other=dict(row,id='b',actual_ms=54000,estimation=estimation(60000,54000))
        s=summary(dict(days={},performance_records=[row,other]),'2026-10-01','2026-10-01')
        self.assertEqual(s['estimation']['signed_error_ms'],0);self.assertEqual(s['estimation']['absolute_error_ms'],12000)
        self.assertEqual(s['estimation']['accuracy'],90);self.assertEqual(s['consistency']['valid_planned_days'],1)

    def test_snapshots_capture_unstarted_durations_without_mutating_plan(self):
        plan=dict(blocks=[dict(id='focus',title='Focus',type='focus',timer_mode='countdown',duration_seconds=60),dict(id='watch',title='Watch',type='focus',timer_mode='stopwatch')])
        saved=snapshot(plan);validate_snapshot(saved);plan['blocks'][0]['duration_seconds']=600
        self.assertEqual(saved['activities'][0]['planned_ms'],60000);self.assertIsNone(saved['activities'][1]['planned_ms'])
        broken=copy.deepcopy(saved);broken['activities'][1]['planned_ms']=1
        with self.assertRaises(ValueError):validate_snapshot(broken)

    def test_plan_duration_allocation_retains_source_zone_dst_and_totals(self):
        a=dict(id='a',title='Task',type='focus',timer_mode='countdown',category='learning',zone='Europe/Berlin',project='P',status='Not started',planned_ms=25000,actual_start=None,actual_end=None,focus_ms=None,punctuality_ms=None,plan_date=None,
            schedule=dict(planned_start='2026-10-24T23:00:00+02:00',planned_end='2026-10-26T00:00:00+01:00',timezone='Europe/Berlin'))
        rows=plan_records({'a':a},instant('2026-10-30T00:00:00Z'))
        self.assertEqual(sum(r['planned_ms'] for r in rows),25000);self.assertEqual([r['date'] for r in rows],['2026-10-24','2026-10-25'])
        self.assertEqual(rows[1]['planned_ms'],24039)  # 25 of 26 real elapsed hours, integer conservation
        watch=dict(a,id='watch',timer_mode='stopwatch',planned_ms=None)
        self.assertEqual(plan_records({'watch':watch}),[])

    def test_future_scheduled_work_excluded_and_current_score_provisional(self):
        plan=dict(session_id='plan',title='Plan',blocks=[dict(id='a',title='Later',type='focus',duration_seconds=600,schedule=dict(planned_start='2099-10-01T10:00:00Z',planned_end='2099-10-01T10:10:00Z'))])
        d=dataset([],dict(session=plan,phase='ready',recordedTimezone='UTC'))
        s=score(plan_records(d['activities'],instant('2099-10-01T09:00:00Z')))
        self.assertIsNone(s['score']);self.assertEqual(s['future_blocks'],1)
        s=score(plan_records(d['activities'],instant('2099-10-01T11:00:00Z')))
        self.assertEqual(s['score'],0);self.assertTrue(s['provisional'])

    def test_overtime_is_inside_active_and_pauses_keep_their_own_hours(self):
        r=fixture();r['blocks'][0]['overtime_ms']=15000
        d=dataset([r,r]);days=d['days'].values()
        self.assertEqual(sum(x['overtime_ms'] for x in days),15000);self.assertEqual(sum(x['focus_ms'] for x in days),30000)
        self.assertEqual(sum(x['paused_ms'] for x in days),15000);self.assertEqual(sum(x['pause_count'] for x in days),2)
        self.assertEqual(sum(sum(x['hour_pauses']) for x in days),15000)
        self.assertEqual(sum(sum(x['hour_breaks']) for x in days),10000)
        self.assertTrue(all(x['overtime_ms']<=x['focus_ms'] for x in days))
        r['events']=[];r['timeline_coverage']='unavailable';d=dataset([r]);self.assertEqual(sum(x['overtime_unlocated_ms'] for x in d['days'].values()),15000)

    def test_calendar_periods_zero_denominator_missing_comparisons_and_all(self):
        self.assertEqual(period_bounds('week','2026-10-11'),(dt.date(2026,10,5),dt.date(2026,10,11)))
        self.assertEqual(period_bounds('month','2024-02-18')[1],dt.date(2024,2,29))
        d=dataset([fixture()]);p=performance_report(d,'month','2026-10-01','2026-10-31')
        self.assertEqual(p['comparison']['previous_bounds'],['2026-09-01','2026-09-30']);self.assertIsNone(p['comparison']['focus_ms']['percentage_change'])
        self.assertIsNone(performance_report(d,'all','2026-10-08','2026-10-09')['comparison'])
        now=summary(d,'2026-10-08','2026-10-09');old=copy.deepcopy(now);old['focus_ms']=0
        self.assertIsNone(comparison(now,old)['focus_ms']['percentage_change']);self.assertEqual(comparison(now,old)['focus_ms']['absolute_difference'],30000)

    def test_report_copy_reloads_tombstones_and_validates_boundaries(self):
        with tempfile.TemporaryDirectory() as f:
            root=Path(f);r=fixture();save_report(root/'reports',r)
            center=ControlCenter(root,lambda p:dict(ok=True,connected=False,state=None,devices=[],viewers=[],paired_id=None))
            payload=dict(scope='month',begin='2026-10-01',end='2026-10-31')
            self.assertEqual(center.handle('performance-report',payload)['focus_ms'],30000)
            atomic_json(root/'deletions.json',dict(before_ms=0,report_keys=[r['session_id']+':'+str(r['started_at_ms'])],revision='disposable-test'))
            self.assertEqual(center.handle('performance-report',payload)['focus_ms'],0)
            with self.assertRaises(ValueError):center.handle('performance-report',dict(payload,end='2026-10-09'))

    def test_snapshot_extensions_are_accepted_by_production_report_validator(self):
        r=fixture();r['schedule_snapshot']=snapshot(dict(blocks=[dict(id=b['id'],title=b['title'],type=b['type'],duration_seconds=b['planned_ms']//1000) for b in r['blocks']]))
        validate_report(r)

    def test_missing_unstarted_legacy_plan_blocks_cannot_claim_full_coverage(self):
        r=fixture();r['status']='stopped';r['planned_block_count']=3;r['timeline_coverage']='partial'
        data=dataset([r]);x=score(data['performance_records'])
        self.assertTrue(x['partial']);self.assertEqual(x['missing_duration_blocks'],1)

    def test_live_plan_keeps_remaining_work_and_stopwatch_never_creates_efficiency(self):
        plan=dict(session_id='plan',title='Plan',blocks=[dict(id='a',title='Closed',type='focus',duration_seconds=60),dict(id='b',title='Watch',type='focus',timer_mode='stopwatch')])
        state=dict(session=plan,phase='active',startedAtMs=instant('2026-10-01T10:00:00Z'),blockStartedAtMs=instant('2026-10-01T10:00:00Z'),anchorWall=instant('2026-10-01T10:00:10Z'),anchorMono=10000,activeMs=10000,pausedMs=0,budgetMs=60000,index=0,events=[],recordedTimezone='UTC')
        d=dataset([],state);rows=d['performance_records'];self.assertEqual(len(rows),1);self.assertTrue(score(rows)['provisional']);self.assertIsNone(score(rows)['efficiency'])

    def test_percent_changes_are_withheld_for_unequal_or_open_calendar_coverage(self):
        r=fixture();d=dataset([r]);a=summary(d,'2026-10-01','2026-10-31');b=copy.deepcopy(a)
        b['calendar_days']=30
        self.assertFalse(comparison(a,b)['comparable_coverage']);self.assertIsNone(comparison(a,b)['focus_ms']['percentage_change'])
        b=copy.deepcopy(a);b['period_closed']=False
        self.assertFalse(comparison(a,b)['comparable_coverage'])

    def test_additive_snapshot_fields_match_report_schema(self):
        try: from jsonschema import Draft202012Validator
        except ImportError: self.skipTest("Optional jsonschema test dependency unavailable")
        r=fixture();r['schedule_snapshot']=snapshot(dict(blocks=[dict(id=b['id'],title=b['title'],type=b['type'],duration_seconds=b['planned_ms']//1000) for b in r['blocks']]))
        schema=json.loads((ROOT/'protocol/report-v1.schema.json').read_text());Draft202012Validator(schema).validate(r)
