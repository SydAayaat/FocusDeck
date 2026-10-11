import copy
import json
import tempfile
import unittest
from focusdeck.achievements import validate_command,query
from focusdeck.common import atomic_json


def command(operation='progress_set',**fields):
    return dict(achievement_version=3,operation=operation,id='progress-1',timestamp='2026-10-07T17:00:00Z',achievement_id='german',**fields)

class ProgressContractTests(unittest.TestCase):
    def test_authoritative_totals_and_dynamic_target(self):
        for value in [command(current_value=200,target_value=1250,unit='words'),command(target_value=146),command(unit='milestones')]:
            self.assertEqual(value,validate_command(value))
    def test_increment(self):self.assertEqual(25,validate_command(command('progress_increment',amount=25))['amount'])
    def test_invalid_numeric_values_and_operations(self):
        for edit in [dict(target_value=0),dict(target_value=-1),dict(target_value=True),dict(current_value=-1),dict(current_value=float('nan')),dict(current_value=float('inf')),dict(target_value=None),dict(unit=''),dict(unit=' '*3),dict(current_value=1e13)]:
            with self.subTest(edit=edit),self.assertRaises(ValueError):validate_command(command(**edit))
        for value in [command(),command('progress_increment',amount=0),command('progress_increment',amount=True),command('progress_increment',amount=2,current_value=3),dict(command(current_value=200),achievement_version=2)]:
            with self.subTest(value=value),self.assertRaises(ValueError):validate_command(value)
    def test_dynamic_progress_and_hybrid_cards_all_categories(self):
        for category in ['discipline','health','learning','focus','projects','college']:
            for streak in [False,True]:
                d=dict(id='actual_user_card',title='Actual configured title',category=category,streak_enabled=streak,progress_enabled=True,progress=dict(current_value=18,target_value=64,unit='topics'))
                c=command('upsert');c.pop('achievement_id');c['definition']=d
                self.assertEqual(c,validate_command(c))
    def test_capability_rejections(self):
        d=dict(id='card',title='Card',category='college',streak_enabled=False,progress_enabled=True,progress=dict(target_value=64))
        c=command('upsert');c.pop('achievement_id');c['definition']=d
        for change in [dict(progress_enabled=False),dict(progress_enabled='yes'),dict(kind='abstinence'),dict(progress={'target_value':0}),dict(progress={'completed_at':1})]:
            b=copy.deepcopy(c);b['definition'].update(change)
            with self.subTest(change=change),self.assertRaises(ValueError):validate_command(b)
        with self.assertRaises(ValueError):validate_command(dict(c,achievement_version=2))
    def test_offline_history_includes_retained_progress_history(self):
        with tempfile.TemporaryDirectory() as root:
            atomic_json(__import__('pathlib').Path(root)/'achievements.json',dict(as_of_ms=1,zone='Europe/Berlin',summary=[dict(id='german',progress_percent=16)],ledger=dict(definitions=[dict(id='german')],segments=[],events=[],days={},targets=[],progressHistory=[dict(achievementId='german',newValue=200),dict(achievementId='other',newValue=2)])))
            q=query(root,'german');self.assertEqual(200,q['progress_history'][0]['newValue']);self.assertEqual(1,len(q['progress_history']))
