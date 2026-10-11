import copy
import json
from pathlib import Path
import unittest

try:
    from jsonschema import Draft202012Validator, FormatChecker, RefResolver
except ImportError:
    Draft202012Validator = None

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipIf(Draft202012Validator is None, 'Install desktop[test] for full Draft 2020-12 schema checks')
class SchemaTests(unittest.TestCase):
    def setUp(self):
        self.schemas = {p.name: json.loads(p.read_text()) for p in (ROOT/'protocol').glob('*.json')}

    def validate(self, name, value):
        schema=self.schemas[name]
        resolver=RefResolver((ROOT/'protocol').as_uri()+'/', schema, store=self.schemas)
        Draft202012Validator(schema,resolver=resolver,format_checker=FormatChecker()).validate(value)

    def test_all_schemas_are_valid_draft_2020_12(self):
        for name,schema in self.schemas.items():
            with self.subTest(name=name):Draft202012Validator.check_schema(schema)

    def test_all_legacy_stopwatch_and_mixed_example_plans(self):
        for path in (ROOT/'examples').glob('*.json'):
            with self.subTest(path=path.name):self.validate('session.schema.json',json.loads(path.read_text()))

    def test_achievement_examples_and_capability_wire(self):
        from focusdeck.achievements import validate_command
        for path in (ROOT/'examples/achievements').glob('*.json'):
            value=json.loads(path.read_text())
            with self.subTest(path=path.name):
                validate_command(value);self.validate('achievement-command.schema.json',value)
                self.validate('wire.schema.json',dict(protocol_version=1,type='command',command_id='command-1',action='achievements',achievement=value))
        self.validate('wire.schema.json',dict(protocol_version=1,type='hello',device_id='phone',name='FocusDeck',supported_session_versions=[1,2],supported_achievement_versions=[1]))
        self.validate('wire.schema.json',dict(protocol_version=1,type='achievement_snapshot',achievement_version=1,snapshot_id='snapshot',index=0,count=1,data='{}'))

    def test_achievement_schema_rejects_unsupported_combinations(self):
        from jsonschema.exceptions import ValidationError
        value=json.loads((ROOT/'examples/achievements/daily-target.json').read_text())
        for change in ('event','missing_reference','invalid_minimum','version'):
            bad=copy.deepcopy(value)
            if change=='event':bad['event']='completed'
            if change=='missing_reference':bad['objectives'][1].pop('achievement_id')
            if change=='invalid_minimum':bad['objectives'][0]['minimum']=0
            if change=='version':bad['achievement_version']=7
            with self.subTest(change=change),self.assertRaises(ValidationError):self.validate('achievement-command.schema.json',bad)

    def test_achievement_v2_deletion_requires_true_confirmation_and_old_contract_stays_restricted(self):
        from jsonschema.exceptions import ValidationError
        value=dict(achievement_version=2,operation='delete',id='delete-card',timestamp='2026-10-07T12:00:00Z',achievement_id='german',confirm=True)
        self.validate('achievement-command.schema.json',value)
        self.validate('wire.schema.json',dict(protocol_version=1,type='hello',device_id='phone',name='FocusDeck',supported_achievement_versions=[1,2]))
        for edit in ({'confirm':False},{'confirm':1},{'achievement_version':1}):
            with self.subTest(edit=edit),self.assertRaises(ValidationError):self.validate('achievement-command.schema.json',dict(value,**edit))
        value=json.loads((ROOT/'examples/achievements/create-health-card-v2.json').read_text())
        value['achievement_version']=1
        with self.assertRaises(ValidationError):self.validate('achievement-command.schema.json',value)

    def test_progress_contract_three_and_capability_negotiation(self):
        from jsonschema.exceptions import ValidationError
        from focusdeck.achievements import validate_command
        value=dict(achievement_version=3,operation='progress_set',id='total',timestamp='2026-10-07T12:00:00Z',achievement_id='german',current_value=200,target_value=1250,unit='words')
        validate_command(value);self.validate('achievement-command.schema.json',value)
        self.validate('wire.schema.json',dict(protocol_version=1,type='command',command_id='total',action='achievements',achievement=value))
        self.validate('wire.schema.json',dict(protocol_version=1,type='hello',device_id='phone',name='FocusDeck',supported_achievement_versions=[1,2,3]))
        for edit in [{'target_value':0},{'current_value':-1},{'achievement_version':2},{'unit':''}]:
            with self.subTest(edit=edit),self.assertRaises(ValidationError):self.validate('achievement-command.schema.json',dict(value,**edit))
        value=dict(achievement_version=3,operation='upsert',id='create',timestamp='2026-10-07T12:00:00Z',definition=dict(id='course',title='Actual course',category='college',streak_enabled=False,progress_enabled=True,progress=dict(current_value=18,target_value=64,unit='topics')))
        self.validate('achievement-command.schema.json',value)
        for edit in [{'progress_enabled':False},{'kind':'abstinence'},{'progress':{'target_value':0}}]:
            bad=copy.deepcopy(value);bad['definition'].update(edit)
            with self.subTest(edit=edit),self.assertRaises(ValidationError):self.validate('achievement-command.schema.json',bad)

    def test_legacy_and_v2_shared_reports(self):
        for path in (ROOT/'android/app/src/test/resources').glob('*.json'):
            with self.subTest(path=path.name):self.validate('report.schema.json',json.loads(path.read_text()))

    def test_v2_schema_rejects_duration_time_reward_and_break_stopwatch(self):
        from jsonschema.exceptions import ValidationError
        original=json.loads((ROOT/'examples/stopwatch-study.json').read_text())
        for kind in ('duration','mode','time_reward','break'):
            bad=copy.deepcopy(original)
            if kind=='duration':bad['blocks'][0]['duration_seconds']=60
            if kind=='mode':bad['blocks'][0].pop('timer_mode')
            if kind=='time_reward':bad['blocks'][1]['reward_policy']['tiers'][0]['min_beat_score']=100
            if kind=='break':bad['blocks'][0]['type']='break'
            with self.subTest(kind=kind),self.assertRaises(ValidationError):self.validate('session.schema.json',bad)

    def test_wire_capability_and_nullable_stopwatch_state(self):
        self.validate('wire.schema.json',dict(protocol_version=1,type='hello',device_id='phone',name='FocusDeck',supported_session_versions=[1,2]))
        schema=self.schemas['wire.schema.json']
        state_schema=next(x for x in schema['oneOf'] if x.get('properties',{}).get('type',{}).get('const')=='state')
        props=state_schema['properties']['state']['properties']
        state=dict(phase='active',session_id='11111111-1111-4111-8111-111111111111',title='Study',block_index=0,block_count=1,remaining_ms=None,revision=1,observed_at_ms=1000,completed_blocks=0,overtime_ms=None,is_overtime=False,timer_mode='stopwatch',elapsed_active_ms=1000,elapsed_paused_ms=0)
        self.validate('wire.schema.json',dict(protocol_version=1,type='state',state=state))

    def test_v2_report_schema_rejects_nonpositive_and_fractional_countdown_targets(self):
        from jsonschema.exceptions import ValidationError
        original=json.loads((ROOT/'android/app/src/test/resources/mixed-mode-report.json').read_text())
        for field,value in [('planned_ms',0),('budget_ms',0),('planned_ms',120001),('budget_ms',120001),('planned_ms',86401000)]:
            bad=copy.deepcopy(original);bad['blocks'][1][field]=value
            with self.subTest(field=field,value=value),self.assertRaises(ValidationError):self.validate('report.schema.json',bad)


if __name__=='__main__':unittest.main()
