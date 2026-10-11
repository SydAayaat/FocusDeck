import copy
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from focusdeck.common import validate_plan, validate_report
from focusdeck.conditional import eligible_indices, weighted
from focusdeck.stopwatch import completion_context
from focusdeck.reports import markdown, save_report
from focusdeck.history import csv_text, load_reports, refresh_csv
from focusdeck.clipboard import copy_report

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / 'android/app/src/test/resources/mixed-mode-report.json'


def plan():
    return json.loads((ROOT / 'examples/stopwatch-study.json').read_text())


def report():
    return json.loads(FIXTURE.read_text())


class StopwatchTests(unittest.TestCase):
    def test_untimed_and_mixed_plans_and_old_countdown_compatibility(self):
        for path in (ROOT / 'examples').glob('*.json'):
            with self.subTest(path=path.name): validate_plan(json.loads(path.read_text()))
        p = plan()
        self.assertNotIn('duration_seconds', p['blocks'][0])

    def test_v2_uuid_and_explicit_mode_and_duration_rules(self):
        for field, value in [('timer_mode', 'automatic'), ('duration_seconds', 100), ('duration_seconds', 0)]:
            p = plan(); p['blocks'][0][field] = value
            with self.subTest(field=field,value=value), self.assertRaises(ValueError): validate_plan(p)
        p = plan(); del p['blocks'][0]['timer_mode']
        with self.assertRaisesRegex(ValueError, 'explicit timer_mode'): validate_plan(p)
        p = plan(); p['session_id'] = 'old-arbitrary-id'
        with self.assertRaisesRegex(ValueError, 'UUID'): validate_plan(p)
        p = plan(); p['blocks'][0].update(timer_mode='countdown', duration_seconds=0)
        with self.assertRaisesRegex(ValueError, 'countdown duration_seconds'): validate_plan(p)
        p = plan(); p['blocks'][0]['type'] = 'break'
        with self.assertRaisesRegex(ValueError, 'Break'): validate_plan(p)
        p = plan(); p['protocol_version'] = 1
        with self.assertRaisesRegex(ValueError, 'protocol_version 2'): validate_plan(p)

    def test_stopwatch_rejects_time_thresholds_and_legacy_contracts(self):
        p = plan(); b = p['blocks'][1]
        for field in ('min_beat_score','max_beat_score','min_pace_index','min_session_score','max_session_score'):
            bad = copy.deepcopy(p); bad['blocks'][1]['reward_policy']['tiers'][0][field] = 90
            with self.subTest(field=field), self.assertRaises(ValueError): validate_plan(bad)
        bad = copy.deepcopy(p); bad['blocks'][1]['reward_policy']['tiers'][0]['condition'] = 'personal_best'
        bad['blocks'][1]['reward_policy']['tiers'][0].pop('block_ids')
        with self.assertRaisesRegex(ValueError, 'Stopwatch reward'): validate_plan(bad)
        bad = copy.deepcopy(p); bad['blocks'][1]['classification'] = {}
        with self.assertRaisesRegex(ValueError, 'timed classification'): validate_plan(bad)

    def test_milestone_references_are_explicit_unique_focus_and_prior_blocks(self):
        for refs in ([], ['unknown'], ['ROB1-2.1','ROB1-2.1']):
            bad = plan(); bad['blocks'][1]['reward_policy']['tiers'][0]['block_ids'] = refs
            with self.subTest(refs=refs), self.assertRaises(ValueError): validate_plan(bad)
        bad = plan(); bad['blocks'][0]['reward_policy'] = bad['blocks'][1]['reward_policy']
        with self.assertRaisesRegex(ValueError, 'future blocks'): validate_plan(bad)

    def test_all_stopwatch_session_cannot_require_countdown_scores(self):
        p = plan(); p['session_reward_policy']['tiers'][0]['min_session_score'] = 90
        with self.assertRaisesRegex(ValueError, 'countdown scores'): validate_plan(p)
        p = plan(); p['session_reward_policy']['eligibility']['minimum_beat_score'] = 80
        with self.assertRaisesRegex(ValueError, 'countdown scoring'): validate_plan(p)
        p = plan(); p['session_reward_policy']['eligibility']['maximum_over_target_blocks'] = 0
        with self.assertRaisesRegex(ValueError, 'countdown classifications'): validate_plan(p)

    def test_shared_mixed_report_and_exact_countdown_scoring(self):
        r = validate_report(report())
        self.assertEqual(r['competition']['beat_score'],111.0)
        self.assertEqual(r['competition']['weighted']['total_weight'],2.0)
        self.assertEqual(r['competition']['weighted']['normalized_session_score'],100.0)
        self.assertEqual(r['competition']['focus_ratio'],88.89)
        self.assertEqual(r['competition']['completed_focus_blocks'],2)
        self.assertEqual(r['competition']['score_scope'],'countdown_only')
        sw = r['blocks'][0]
        self.assertEqual((sw['active_ms'],sw['paused_ms'],sw['wall_ms']),(100000,25000,125000))
        self.assertIsNone(sw['planned_ms']); self.assertIsNone(sw['performance']['beat_score'])

    def test_entirely_stopwatch_report_has_no_time_score_and_exports_completion(self):
        r = report(); r['blocks'] = r['blocks'][:1]
        r.update(active_ms=100000, focused_ms=100000, paused_ms=25000,
                 timed_planned_ms=0, planned_block_count=1, planned_countdown_focus_blocks=0,
                 timeline_coverage='unavailable', events=[])
        c = r['competition']
        c.update(planned_focus_blocks=1, completed_focus_blocks=1, on_or_ahead_count=0,
                 average_pace=None, focus_ratio=80.0, beat_score=None, rank=None, score_scope='none')
        c['weighted'] = weighted([], untimed=True)
        c['session_reward_policy']['tiers'][0]['min_completed_blocks'] = 1
        c['session_reward_policy']['eligibility']['minimum_completed_blocks'] = 1
        validate_report(r)
        self.assertIn('Untimed completion', markdown(r))
        self.assertIsNone(c['weighted']['normalized_session_score'])
        rows = list(csv.DictReader(io.StringIO(csv_text([r], kind='sessions'))))
        self.assertEqual(rows[0]['score_scope'], 'none')
        self.assertEqual(rows[0]['beat_score'], '')

    def test_fabricated_stopwatch_score_plan_budget_overtime_or_class_rejected(self):
        for field,value in [('planned_ms',100),('budget_ms',100),('overtime_ms',0)]:
            r = report(); r['blocks'][0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): validate_report(r)
        for field,value in [('beat_score',100),('pace_index',100),('performance_class','strong'),('rank','A')]:
            r = report(); r['blocks'][0]['performance'][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): validate_report(r)

    def test_scope_counts_focus_and_monotonic_wall_are_checked(self):
        for field,value in [('score_scope','all_focus'),('beat_score',100),('completed_focus_blocks',1),('focus_ratio',100)]:
            r=report();r['competition'][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate_report(r)
        r=report();r['blocks'][0]['wall_ms']=124999
        with self.assertRaisesRegex(ValueError,'Wall duration'):validate_report(r)
        r=report();r['blocks'][1]['performance']['quality']='incomplete'
        with self.assertRaisesRegex(ValueError,'confirmed completion'):validate_report(r)
        r=report();r['blocks'][0]['outcome']='stopped'
        with self.assertRaisesRegex(ValueError,'DONE for every'):validate_report(r)

    def test_focus_and_break_totals_must_match_their_block_categories(self):
        from test_focusdeck import report as legacy_report
        for original in (legacy_report(), report()):
            bad = copy.deepcopy(original)
            bad['focused_ms'] -= 1000; bad['break_ms'] += 1000
            with self.subTest(version=bad.get('report_version', 1)), self.assertRaisesRegex(ValueError, 'categories'):
                validate_report(bad)
            with tempfile.TemporaryDirectory() as tmp:
                with self.assertRaises(ValueError): save_report(Path(tmp), bad)
                self.assertFalse(list(Path(tmp).iterdir()))

    def test_malformed_v2_evidence_and_policies_return_validation_errors(self):
        cases = [
            lambda r: r.update(competition=7),
            lambda r: r['blocks'][0].update(performance=7),
            lambda r: r['blocks'][0]['performance'].update(reward_policy='bad'),
            lambda r: r['blocks'][0]['performance'].update(reward_policy={'enabled':True,'tiers':[None]}),
            lambda r: r['competition'].update(session_reward_policy='bad'),
            lambda r: r['competition']['session_reward_policy'].update(eligibility=None),
        ]
        for mutate in cases:
            bad = report(); mutate(bad)
            with self.subTest(mutation=mutate), self.assertRaises(ValueError): validate_report(bad)

    def test_stopwatch_rounding_matches_native_at_an_inclusive_reward_boundary(self):
        from focusdeck.rewards import rounded
        r = report(); b = r['blocks'][0]; p = b['performance']
        r.update(timeline_coverage='unavailable', events=[], active_ms=100107, focused_ms=100107, paused_ms=3893)
        b.update(active_ms=107, paused_ms=3893, wall_ms=4000, focus_ratio=2.68)
        p.update(wall_ms=4000, focus_ratio=2.68)
        p['reward_policy']['tiers'][0]['min_focus_ratio'] = 2.68
        r['competition']['focus_ratio'] = rounded(100107 / 104000 * 100)
        validate_report(r)
        b['focus_ratio'] = 2.67
        with self.assertRaisesRegex(ValueError, 'Focus Ratio'): validate_report(r)

    def test_v2_countdown_report_requires_real_positive_budget_and_whole_second_target(self):
        for planned, budget in ((0,120000), (120001,120001), (120000,119000), (120000,120001), (86401000,86401000)):
            bad = report(); b = bad['blocks'][1]
            b.update(planned_ms=planned, budget_ms=budget)
            with self.subTest(planned=planned, budget=budget), self.assertRaisesRegex(ValueError, 'countdown'):
                validate_report(bad)

    def test_completion_and_focus_thresholds_not_artificial_scores(self):
        p=plan()['session_reward_policy'];w=weighted([],untimed=True)
        ctx=dict(completed=True,completed_ids={'a','b'},all_focus_completed=True,session_completed=True)
        self.assertEqual(eligible_indices(p,None,None,80,False,w,ctx),[0])
        self.assertEqual(eligible_indices(p,None,None,79.99,False,w,ctx),[])
        self.assertEqual(eligible_indices(p,None,None,100,False,w,dict(ctx,session_completed=False)),[])
        self.assertIsNone(w['normalized_session_score'])

    def test_countdown_zero_threshold_compatibility_does_not_assign_stopwatch_metrics(self):
        p = dict(enabled=True, tiers=[dict(min_focus_ratio=0, min_pace_index=0, reward={'text':'Exact reward'})])
        self.assertEqual(eligible_indices(p, 0.0, None, None, False), [0])
        p['tiers'][0].pop('min_pace_index'); p['tiers'][0]['condition'] = 'completed'
        ctx = dict(completed=True, completed_ids={'a'}, all_focus_completed=True, session_completed=True)
        self.assertEqual(eligible_indices(p, None, None, None, False, context=ctx), [])

    def test_highest_focus_tier_and_first_selection(self):
        tiers=[dict(condition='completed',min_focus_ratio=x,reward={'text':str(x)}) for x in (70,90,80)]
        p=dict(enabled=True,tiers=tiers)
        ctx=dict(completed=True,completed_ids={'a'},all_focus_completed=True,session_completed=True)
        self.assertEqual(eligible_indices(p,None,None,95,False,context=ctx),[1,2,0])
        self.assertEqual(eligible_indices(dict(p,selection='first_satisfied'),None,None,95,False,context=ctx),[0,1,2])

    def test_milestone_prefix_evidence_and_missing_done_do_not_earn_reward(self):
        r=report();p=plan()['blocks'][1]['reward_policy']
        p['tiers'][0]['block_ids']=['S1','C1']
        self.assertEqual(eligible_indices(p,None,None,100,False,context=completion_context(r,0)),[])
        self.assertEqual(eligible_indices(p,None,None,100,False,context=completion_context(r,1)),[0])
        r['blocks'][0]['outcome']='stopped'
        self.assertEqual(eligible_indices(p,None,None,100,False,context=completion_context(r,1)),[])

    def test_markdown_csv_archive_and_clipboard_keep_mode_nulls_and_exact_reward(self):
        r=report();text=markdown(r)
        self.assertIn('stopwatch',text);self.assertIn('N/A',text);self.assertIn('Countdown-only scoring',text)
        self.assertIn('Earn one token.',text)
        rows=list(csv.DictReader(io.StringIO(csv_text([r]))))
        self.assertEqual(rows[0]['timer_mode'],'stopwatch');self.assertEqual(rows[0]['planned_ms'],'')
        self.assertEqual(rows[0]['beat_score'],'');self.assertEqual(rows[0]['block_elapsed_wall_ms'],'125000')
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);save_report(root/'reports',r);refresh_csv(root/'reports',root/'exports')
            self.assertEqual(load_reports(root/'reports'),[r])
            with patch('focusdeck.clipboard.copy_text') as mocked:
                copy_report(root,r['session_id']);self.assertIn('Countdown-only scoring',mocked.call_args.args[0])

    def test_unmatched_reward_is_valid_and_invented_text_is_rejected(self):
        r=report();r['blocks'][0]['performance']['reward']['actions'][0]['text']='Invented prize'
        with self.assertRaises(ValueError):validate_report(r)
        r=report();p=r['blocks'][0]['performance'];p['reward']=copy.deepcopy(r['blocks'][1]['performance']['reward'])
        p['reward_policy']['tiers'][0]['min_focus_ratio']=81
        validate_report(r)


if __name__ == '__main__': unittest.main()
