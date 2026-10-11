import copy
import csv
import io
import json
from pathlib import Path
import tempfile
import unittest

from focusdeck.common import validate_report
from focusdeck.history import csv_text, load_reports
from focusdeck.reports import markdown, save_report
from focusdeck.timeline import intervals
from focusdeck.stopwatch import completion_context
from test_stopwatch import report
from test_timeline import detailed_report
import test_schemas


def skipped_report():
    r = report()
    r['status'] = 'stopped'
    r['blocks'][0].update(outcome='skipped', performance=None)
    c = r['competition']
    c.update(completed_focus_blocks=1, completion_fraction=.5)
    c['reward'].update(tier='none', actions=[], acknowledged=False, acknowledged_at_ms=None,
                       local_day=None, reason='Session ended with skipped or incomplete blocks')
    events = [e for e in r['events'] if not (e['block_index'] == 0 and e['kind'] in ('reward_offered', 'reward_acknowledged', 'reward_continued'))
              and not e['kind'].startswith('session_reward_')]
    for i, e in enumerate(events, 1):
        e['seq'] = i
        if e['block_index'] == 0 and e['kind'] == 'block_done': e['kind'] = 'block_skipped'
    events[-1].update(kind='session_stopped', phase='stopped')
    r['events'] = events
    return r


class SkipEndTests(unittest.TestCase):
    def test_skipped_stopwatch_retains_focus_but_no_completion_or_reward(self):
        r = validate_report(skipped_report())
        self.assertEqual(r['focused_ms'], 200000)
        self.assertEqual(r['competition']['completed_focus_blocks'], 1)
        self.assertFalse(completion_context(r)['session_completed'])
        self.assertIsNone(r['blocks'][0]['performance'])
        self.assertEqual(sum(i['duration_ms'] for i in intervals(r) if i['kind']=='active'), r['active_ms'])

    def test_ended_block_has_no_completion_credit_and_retains_time(self):
        r=skipped_report(); r['blocks'][0]['outcome']='stopped'
        for e in r['events']:
            if e['kind']=='block_skipped': e['kind']='block_stopped'
        validate_report(r)
        self.assertEqual(r['competition']['completed_focus_blocks'],1)
        self.assertIn('Ended early', markdown(r))
        self.assertEqual(sum(i['duration_ms'] for i in intervals(r) if i['kind']=='active'), r['active_ms'])

    def test_export_archive_schema_and_csv_preserve_skipped_evidence(self):
        r = skipped_report(); validate_report(r)
        if test_schemas.Draft202012Validator is not None:
            checker=test_schemas.SchemaTests(); checker.setUp(); checker.validate('report.schema.json', r)
        self.assertIn('| skipped |', markdown(r))
        row=next(csv.DictReader(io.StringIO(csv_text([r], 'sessions'))))
        self.assertEqual(row['skipped_blocks'], '1'); self.assertEqual(row['done_blocks'], '1')
        with tempfile.TemporaryDirectory() as d:
            save_report(Path(d)/'reports', r)
            self.assertEqual(load_reports(Path(d)/'reports'), [r])

    def test_skipped_cannot_claim_complete_done_performance_or_session_reward(self):
        for change in ('complete', 'performance', 'reward', 'boundary'):
            r=skipped_report()
            if change=='complete': r['status']='complete'
            if change=='performance': r['blocks'][0]['performance']=report()['blocks'][0]['performance']
            if change=='reward': r['competition']['reward']=report()['competition']['reward']
            if change=='boundary': r['events'][next(i for i,e in enumerate(r['events']) if e['kind']=='block_skipped')]['kind']='block_stopped'
            with self.subTest(change=change), self.assertRaises(ValueError): validate_report(r)

    def test_legacy_countdown_skip_has_exact_closed_timeline(self):
        r=detailed_report(); r['status']='stopped'; r['blocks'][0]['outcome']='skipped'
        r['events'][5]['kind']='block_skipped'; r['events'][-1].update(kind='session_stopped',phase='stopped')
        validate_report(r)
        self.assertEqual(sum(i['duration_ms'] for i in intervals(r)),55000)
        if test_schemas.Draft202012Validator is not None:
            checker=test_schemas.SchemaTests(); checker.setUp(); checker.validate('report.schema.json', r)


if __name__ == '__main__': unittest.main()
