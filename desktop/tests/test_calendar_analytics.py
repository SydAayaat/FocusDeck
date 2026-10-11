"""Source-zone allocation, optional schedules and persistent layout regressions."""
import copy
import datetime as dt
import tempfile
import unittest
from pathlib import Path
from zoneinfo import ZoneInfo
from focusdeck.analytics import bounds, dataset, instant, punctuality, snapshot, split, validate_schedule, validate_snapshot
from focusdeck.common import validate_plan
from focusdeck.control_center import ControlCenter
from focusdeck.layouts import validate_layout
from focusdeck.achievements import validate_command
from test_timeline import detailed_report


def at(value): return instant(value)

def fixture(start='2026-10-08T23:59:40Z', zone='UTC'):
    r=detailed_report();delta=at(start)-r['started_at_ms']
    for field in ('started_at_ms','finished_at_ms'):r[field]+=delta
    for b in r['blocks']:
        for field in ('started_at_ms','finished_at_ms'):b[field]+=delta
    for e in r['events']:e['at_ms']+=delta
    r['recorded_timezone']=zone
    return r


class CalendarAnalyticsTests(unittest.TestCase):
    def test_optional_metadata_does_not_change_duration_only_plans(self):
        import json
        p=json.loads((Path(__file__).resolve().parents[2]/'examples/mixed-mode-study.json').read_text())
        validate_plan(p);plain=copy.deepcopy(p)
        p['schedule']={'planned_start':'2026-10-09T10:00:00+02:00','timezone':'Europe/Berlin'}
        p['blocks'][0]['schedule']={'planned_start':'2026-10-09T10:00:00+02:00','planned_end':'2026-10-09T10:25:00+02:00'}
        p['blocks'][0]['category']='learning';validate_plan(p)
        saved=snapshot(p);p['blocks'][0]['schedule']['planned_start']='2026-10-10T10:00:00+02:00'
        self.assertEqual(saved['activities'][0]['schedule']['planned_start'],'2026-10-09T10:00:00+02:00')
        self.assertNotIn('schedule',snapshot(plain)['activities'][0]);validate_snapshot(saved)

    def test_punctuality_signed_mean_and_missing_comparisons(self):
        schedule={'planned_start':'2026-10-09T10:00:00Z'}
        self.assertEqual(punctuality(schedule,at('2026-10-09T09:52:00Z')),-480000)
        self.assertEqual(punctuality(schedule,at('2026-10-09T10:08:00Z')),480000)
        self.assertEqual(punctuality(schedule,at('2026-10-09T10:00:00Z')),0)
        self.assertIsNone(punctuality(None,1));self.assertIsNone(punctuality(schedule,None))
        r=fixture('2026-10-09T10:00:00Z');r['schedule_snapshot']=dict(session_schedule={'planned_start':'2026-10-09T09:00:00Z'},activities=[dict(id='a',title='First',type='focus',schedule=schedule)])
        d=dataset([r]);day=d['days']['2026-10-09']
        self.assertEqual(day['punctuality'],[0]);self.assertEqual(day['session_punctuality'],[3600000])

    def test_schedule_validation_rejects_ambiguous_instants_and_metadata(self):
        for s in ({'planned_start':'2026-10-09T10:00'}, {'planned_start':'2026-10-09T10:00:00Z','planned_end':'2026-10-09T09:00:00Z'}, {'planned_start':'2026-10-09T10:00:00Z','timezone':'Imaginary/Zone'}):
            with self.subTest(schedule=s),self.assertRaises(ValueError):validate_schedule(s)
        with self.assertRaises(ValueError):validate_snapshot(dict(session_schedule=None,activities=[dict(id='a',type='focus')]))

    def test_midnight_allocation_conserves_every_component_without_replay(self):
        r=fixture();data=dataset([r,copy.deepcopy(r)]);days=list(data['days'].values())
        self.assertEqual(set(data['days']),{'2026-10-08','2026-10-09'})
        self.assertEqual(sum(d['focus_ms'] for d in days),30000)
        self.assertEqual(sum(d['break_ms'] for d in days),10000)
        self.assertEqual(sum(d['paused_ms'] for d in days),15000)
        self.assertEqual(sum(d['tracked_ms'] for d in days),55000)
        self.assertEqual(sum(d['done'] for d in days),2)
        self.assertEqual(sum(sum(d['session_focus'].values()) for d in days),30000)
        self.assertTrue(all(d['hour_complete'] for d in days))

    def test_dst_days_and_repeated_hour_conserve_elapsed_time(self):
        zone=ZoneInfo('Europe/Berlin')
        for date,hours in [('2026-03-29',23),('2026-10-25',25)]:
            start,end=bounds(dt.date.fromisoformat(date),zone);self.assertEqual(end-start,hours*3600000)
            pieces=split(start,end,end-start,zone);self.assertEqual(sum(p[4] for p in pieces),end-start)
            self.assertEqual(len(pieces),hours)
            self.assertEqual(sum(p[4] for p in pieces if p[1]==2),(0 if hours==23 else 2)*3600000)

    def test_source_zone_is_stable_and_mixed_axes_are_explicit(self):
        r=fixture('2026-10-09T00:10:00Z','America/Los_Angeles');d=dataset([r])
        self.assertEqual(set(d['days']),{'2026-10-08'})
        other=fixture('2026-10-08T12:00:00Z','Europe/Berlin');other['session_id']='other'
        mixed=dataset([r,other])['days']['2026-10-08']
        self.assertEqual(set(mixed['zone_axes']),{'America/Los_Angeles','Europe/Berlin'})
        self.assertNotEqual(mixed['zone_axes']['America/Los_Angeles']['start_ms'],mixed['zone_axes']['Europe/Berlin']['start_ms'])

    def test_clock_jump_and_partial_evidence_keep_totals_without_guessed_hours(self):
        r=fixture('2026-10-09T10:00:00Z')
        for e in r['events'][2:]:e['at_ms']-=100000
        data=dataset([r]);days=list(data['days'].values())
        self.assertEqual(sum(d['tracked_ms'] for d in days),55000)
        self.assertGreater(sum(d['unlocated_ms'] for d in days),0)
        self.assertTrue(any(not d['hour_complete'] for d in days))
        legacy=fixture('2026-10-09T10:00:00Z');legacy['events']=[]
        day=dataset([legacy])['days']['2026-10-09'];self.assertEqual(day['unlocated_ms'],55000);self.assertFalse(day['hour_complete'])

    def test_partial_reports_are_excluded_even_when_known_counters_are_located(self):
        r=fixture('2026-10-09T10:00:00Z');r['timeline_coverage']='partial'
        d=dataset([r])['days']['2026-10-09'];self.assertEqual(d['unlocated_ms'],0);self.assertFalse(d['hour_complete']);self.assertEqual(sum(d['hours']),30000)

    def test_planned_only_date_is_not_a_measured_zero(self):
        plan=dict(session_id='planned',title='Plan',blocks=[dict(id='a',title='Task',type='focus',schedule=dict(planned_start='2026-10-10T10:00:00Z',planned_end='2026-10-10T11:00:00Z'))])
        d=dataset([],dict(session=plan,phase='ready',recordedTimezone='UTC'))
        self.assertFalse(d['days']['2026-10-10']['has_record'])
        a=next(iter(d['activities'].values()));self.assertIsNone(a['actual_start']);self.assertIsNone(a['focus_ms'])

    def test_live_checkpoint_does_not_mutate_archive_or_double_count(self):
        r=fixture('2026-10-09T10:00:00Z');state=dict(session=dict(session_id=r['session_id'],title=r['title'],blocks=[dict(id='a',title='First',type='focus',duration_seconds=60)]),phase='active',startedAtMs=r['started_at_ms'],blockStartedAtMs=r['started_at_ms'],anchorWall=r['started_at_ms']+10000,anchorMono=10000,activeMs=10000,pausedMs=0,budgetMs=60000,index=0,events=[r['events'][0]],recordedTimezone='UTC')
        before=copy.deepcopy(state);data=dataset([],state);self.assertEqual(sum(d['focus_ms'] for d in data['days'].values()),10000)
        self.assertEqual(state,before);self.assertIsNone(next(iter(data['activities'].values()))['actual_end'])
        self.assertEqual(sum(d['focus_ms'] for d in dataset([r],state)['days'].values()),r['focused_ms'])


class PersistentLayoutTests(unittest.TestCase):
    def test_bounds_and_malformed_orders(self):
        good=dict(version=1,cards={'timeline':dict(span=6,height=312)},order=['timeline'])
        self.assertEqual(validate_layout(good),good)
        for change in (dict(span=0,height=312),dict(span=12,height=0),dict(span=True,height=120),dict(span=6,height=2401)):
            with self.assertRaises(ValueError):validate_layout(dict(version=1,cards={'x':change},order=[]))
        with self.assertRaises(ValueError):validate_layout(dict(version=1,cards={},order=[{}]))

    def test_width_only_layout_preserves_height_mode_without_changing_legacy_layouts(self):
        for sizing in (False,True):
            layout=dict(version=1,cards={'chart':dict(span=6,height=312,heightSized=sizing)},order=[])
            self.assertEqual(validate_layout(layout),layout)
        for bad in (0,1,'false',None):
            with self.assertRaises(ValueError):
                validate_layout(dict(version=1,cards={'chart':dict(span=6,height=312,heightSized=bad)},order=[]))

    def test_templates_are_separate_and_survive_new_process_state(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);transport=lambda p:dict(ok=True,connected=False,state=None,devices=[],viewers=[],paired_id=None)
            center=ControlCenter(root,transport)
            layout=dict(version=1,cards={'timeline':dict(span=6,height=312)},order=['timeline','sessions'])
            center.handle('save-layout',dict(template='analytics-day@wide',layout=layout))
            center.handle('save-layout',dict(template='overview@compact',layout=dict(version=1,cards={'week':dict(span=12,height=240)},order=[])))
            restored=ControlCenter(root,transport).snapshot()['layouts'];self.assertEqual(restored['analytics-day@wide'],layout)
            self.assertNotIn('analytics-aggregate@wide',restored)
            center.handle('save-layout',dict(template='analytics-day@wide',layout=dict(version=1,cards={},order=[])))
            restored=ControlCenter(root,transport).snapshot()['layouts'];self.assertEqual(restored['analytics-day@wide']['cards'],{});self.assertEqual(restored['overview@compact']['cards']['week']['height'],240)
            with self.assertRaises(ValueError):center.handle('save-layout',dict(template='../bad',layout=layout))

    def test_achievement_order_receipt_has_independent_revision_and_no_dimensions(self):
        c=dict(achievement_version=6,operation='layout_order',id='order-1',timestamp='2026-10-09T10:00:00Z',source='PC',order=['german','gym'],expected_store_revision=12)
        self.assertEqual(validate_command(c),c)
        for changed in (dict(c,order=['gym','gym']),dict(c,order=[{}]),dict(c,expected_store_revision=-1),dict(c,dimensions={'gym':1})):
            with self.assertRaises(ValueError):validate_command(changed)

    def test_week_dimensions_and_reset_do_not_overwrite_month_year_all_template(self):
        with tempfile.TemporaryDirectory() as folder:
            center=ControlCenter(Path(folder),lambda p:dict(ok=True,connected=False,state=None,devices=[],viewers=[],paired_id=None))
            old=dict(version=1,cards={'hourly':dict(span=4,height=312)},order=['hourly'])
            week=dict(version=1,cards={'hourly':dict(span=6,height=240)},order=['hourly'])
            center.handle('save-layout',dict(template='analytics-aggregate@wide',layout=old))
            center.handle('save-layout',dict(template='analytics-week@wide',layout=week))
            self.assertEqual(center.snapshot()['layouts']['analytics-aggregate@wide'],old)
            self.assertEqual(center.snapshot()['layouts']['analytics-week@wide'],week)
            center.handle('save-layout',dict(template='analytics-week@wide',layout=dict(version=1,cards={},order=[])))
            self.assertEqual(center.snapshot()['layouts']['analytics-aggregate@wide'],old)
