import copy
import csv
import io
import json
from pathlib import Path
import unittest
from focusdeck.common import validate_plan, validate_report
from focusdeck.conditional import policy, typed, classification, classify, weighted, eligible_indices, COEFFICIENTS, CLASS_DEFAULTS
from focusdeck.history import csv_text
from focusdeck.reports import markdown, save_report
from focusdeck.clipboard import select_report
from focusdeck.rewards import receipt, reward_label
import tempfile
from test_focusdeck import plan
from test_rewards import competitive_report

ROOT=Path(__file__).resolve().parents[2]


def conditional_report():
    r=competitive_report()
    p=r['blocks'][0]['performance']
    p.update(performance_class='strong', session_weight=1.5, classification=copy.deepcopy(CLASS_DEFAULTS),
             reward_policy={'enabled':True,'tiers':[{'min_beat_score':100,'reward':{'type':'walk','duration_seconds':60}}]})
    p['reward_contract']=None
    p['reward']['tier']='tier_1'
    r['events']=[e for e in r['events'] if e['kind'] not in {'review_started','quality_confirmed'}]
    r['events'][4].update(at_ms=1095000,mono_ms=95000)
    for i,e in enumerate(r['events'],1):e['seq']=i
    r['competition'].update(weighted=weighted(r['blocks']),session_reward_policy={'enabled':False})
    return r


class ConditionalTests(unittest.TestCase):
    def test_policy_works_without_legacy_strategy_and_disabled_block(self):
        p=plan();p['blocks'][0].update(reward_policy={'enabled':False},session_weight=.5)
        p['session_reward_policy']={'enabled':True,'tiers':[{'min_session_score':70,'reward':{'type':'guitar','duration_minutes':10}}]}
        validate_plan(p)

    def test_typed_user_examples_and_bounds(self):
        self.assertEqual(typed({'type':'squats','amount':10})[0],dict(kind='squats',reps=10,seconds=60))
        self.assertEqual(len(typed({'type':'combined','actions':[{'type':'pushups','amount':15},{'type':'walk','duration_seconds':60}]})),2)
        self.assertEqual(typed({'type':'guitar','duration_minutes':15},True)[0]['seconds'],900)
        for reward in [{'type':'squats','amount':True},{'type':'pushups','amount':False},{'type':'pushups','amount':21},
                       {'type':'walk','duration_seconds':True},{'type':'coffee'},{'type':'guitar','duration_minutes':10},
                       {'type':'combined','actions':[{'type':'walk','duration_seconds':90},{'type':'plank','duration_seconds':90}]}]:
            with self.subTest(reward=reward),self.assertRaises(ValueError):typed(reward)

    def test_highest_satisfied_pb_and_explicit_first(self):
        p={'enabled':True,'tiers':[{'min_beat_score':100,'reward':{'type':'squats','amount':10}},
             {'min_beat_score':108,'min_focus_ratio':90,'reward':{'type':'pushups','amount':12}},
             {'condition':'personal_best','reward':{'type':'walk','duration_seconds':60}}]}
        policy(p)
        self.assertEqual(eligible_indices(p,109,119,95,False),[1,0])
        self.assertEqual(eligible_indices(p,109,119,95,True),[2,1,0])
        self.assertEqual(eligible_indices(p,96.7,98,99,False),[])
        self.assertEqual(eligible_indices({**p,'enabled':False},111,120,100,True),[])
        self.assertEqual(eligible_indices({**p,'selection':'first_satisfied'},109,119,95,True),[0,1,2])

    def test_classes_use_original_plan_and_configured_tolerance(self):
        p={'focus_ratio':95,'recalibration_needed':False}
        for ratio,expected in [(.85,'strong'),(1.0,'on_target'),(1.10,'slightly_over'),(1.4,'over_target')]:
            self.assertEqual(classify({'active_ms':ratio*100000,'planned_ms':100000},p),expected)
        self.assertEqual(classify({'active_ms':80000,'planned_ms':100000},{**p,'focus_ratio':60}),'on_target')
        self.assertEqual(classify({'active_ms':30000,'planned_ms':100000},{**p,'recalibration_needed':True}),'on_target')
        self.assertEqual(classify({'active_ms':108000,'planned_ms':100000},p,{'on_target_max_actual_ratio':1.1}),'on_target')

    def test_weighted_points_negative_clamp_counts_and_no_missing_penalty(self):
        classes=['strong','strong','on_target','on_target','slightly_over','over_target']
        blocks=[dict(type='focus',outcome='done',performance=dict(performance_class=k,session_weight=1,focus_ratio=95)) for k in classes]
        w=weighted(blocks)
        self.assertAlmostEqual(w['raw_weighted_points'],3.3)
        self.assertEqual(w['total_weight'],6);self.assertEqual(w['normalized_session_score'],55)
        self.assertEqual(w['class_counts'],dict(strong=2,on_target=2,slightly_over=1,over_target=1))
        blocks[0]['performance']['session_weight']=1.5
        self.assertEqual(weighted(blocks)['total_weight'],6.5)
        blocks.append(dict(type='focus',outcome='stopped',performance=None))
        self.assertEqual(weighted(blocks),weighted(blocks[:-1]))
        self.assertEqual(weighted([blocks[5]])['normalized_session_score'],0)

    def test_session_reward_independent_eligibility_and_tiers(self):
        summary=dict(class_counts=dict(strong=2,on_target=2,slightly_over=1,over_target=1),normalized_session_score=86,average_focus_ratio=94)
        p={'enabled':True,'eligibility':{'minimum_on_or_above_target_blocks':4,'minimum_focus_ratio':90},
           'tiers':[{'min_session_score':70,'reward':{'type':'guitar','duration_minutes':10}},
                    {'min_session_score':85,'reward':{'type':'guitar','duration_minutes':15}},
                    {'min_session_score':95,'reward':{'type':'combined','actions':[{'type':'coffee'},{'type':'guitar','duration_minutes':15}]}}]}
        policy(p,True)
        self.assertEqual(eligible_indices(p,101,None,93,False,summary),[1,0])
        self.assertEqual(eligible_indices(p,101,None,89,False,summary),[])
        self.assertEqual(eligible_indices(p,101,None,93,False,{**summary,'class_counts':dict(strong=1,on_target=2,slightly_over=2,over_target=1)}),[])

    def test_report_score_class_reward_checks_and_paste_ready_evidence(self):
        r=conditional_report();validate_report(r)
        text=markdown(r)
        self.assertIn('class=strong',text);self.assertIn('"normalized_session_score": 100.0',text)
        self.assertNotIn('quality_incomplete',text)
        for target,key,value in [('performance','performance_class','failed'),('performance','session_weight',0),
                                 ('weighted','normalized_session_score',99),('weighted','raw_weighted_points',5),('weighted','scoring',None),('weighted','class_counts',dict(strong=True,on_target=0,slightly_over=0,over_target=0)),('performance','classification',None)]:
            altered=copy.deepcopy(r);d=altered['blocks'][0]['performance'] if target=='performance' else altered['competition']['weighted'];d[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):validate_report(altered)
        altered=copy.deepcopy(r);altered['blocks'][0]['performance']['reward_policy']['tiers'][0]['min_beat_score']=108
        with self.assertRaises(ValueError):validate_report(altered)
        altered=copy.deepcopy(r);altered['blocks'][0]['performance']['reward_policy']['enabled']=False
        with self.assertRaises(ValueError):validate_report(altered)

    def test_session_threshold_validation_and_csv(self):
        r=conditional_report()
        r['competition']['session_reward_policy']={'enabled':True,'eligibility':{'minimum_strong_blocks':2},'tiers':[{'min_session_score':70,'reward':{'type':'guitar','duration_minutes':10}}]}
        r['competition']['reward']=dict(tier='tier_1',actions=[dict(kind='guitar',reps=0,seconds=600)],reason='Configured reward eligible',acknowledged=False,skipped=False)
        with self.assertRaises(ValueError):validate_report(r)
        r['competition']['session_reward_policy']['eligibility']['minimum_strong_blocks']=1
        validate_report(r)
        row=next(csv.DictReader(io.StringIO(csv_text([r],'sessions'))))
        self.assertEqual(row['normalized_session_score'],'100.0');self.assertEqual(row['strong_blocks'],'1')
        block=next(csv.DictReader(io.StringIO(csv_text([r]))))
        self.assertEqual(block['performance_class'],'strong');self.assertEqual(block['session_weight'],'1.5')
        self.assertIn('tiers',json.loads(block['reward_policy_json']))

    def test_config_rejects_invalid_types_order_and_permissions(self):
        for changes in [{'classification':None},{'session_scoring':None},{'classification':{'on_target_max_actual_ratio':.8}},
                        {'session_scoring':{'coefficients':{**COEFFICIENTS,'on_target':1,'strong':.5}}},
                        {'session_reward_policy':{'enabled':True,'eligibility':{'minimum_strong_blocks':True},'tiers':[{'reward':{'type':'guitar','duration_minutes':10}}]}}]:
            p=plan();p.update(changes)
            with self.subTest(changes=changes),self.assertRaises(ValueError):validate_plan(p)
        p=plan();p['reward_strategy']={'allowed_rewards':['walk']}
        p['blocks'][0]['reward_policy']={'enabled':True,'tiers':[{'reward':{'type':'pushups','amount':10}}]}
        with self.assertRaises(ValueError):validate_plan(p)

    def test_shared_fixture(self):
        path=ROOT/'android/app/src/test/resources/conditional-report.json'
        self.assertEqual(json.loads(path.read_text()),conditional_report())


class TextRewardTests(unittest.TestCase):
    def test_gpt_free_text_preserved_without_activity_catalog(self):
        description = '5 pull-ups + move one FocusStack token.\nThen return to work.'
        p=plan()
        p['reward_strategy']={'allowed_rewards':['walk'],'daily_rep_limits':{'pushups':0,'squats':0},'daily_physical_seconds':0}
        p['blocks'][0]['reward_policy']={'enabled':True,'tiers':[{'min_beat_score':100,'reward':{'text':description}}]}
        validate_plan(p)
        self.assertEqual(typed({'text':description}),[dict(kind='text',reps=0,seconds=0,text=description)])
        self.assertEqual(reward_label(typed({'text':description})),description)

    def test_text_policy_ranges_inclusive_boundaries_gaps_pb_and_disabled(self):
        p={'enabled':True,'tiers':[
            {'min_beat_score':100,'max_beat_score':106.99,'reward':{'text':'Walk outside for 2 minutes.'}},
            {'min_beat_score':107,'max_beat_score':111,'reward':{'text':'5 pull-ups + one token.'}}]}
        policy(p)
        for score,expected in [(99.99,[]),(100,[0]),(106.99,[0]),(107,[1]),(111,[1])]:
            self.assertEqual(eligible_indices(p,score,100,100,False),expected)
        self.assertEqual(eligible_indices({**p,'enabled':False},111,120,100,False),[])
        p['tiers'].append({'condition':'personal_best','reward':{'text':'Replace the physical PB card.'}})
        self.assertEqual(eligible_indices(p,108,119,95,True),[2,1])
        self.assertEqual(eligible_indices({**p,'selection':'first_satisfied'},108,119,95,True),[1,2])

    def test_session_text_range_independent_of_block_reward_catalog(self):
        p={'enabled':True,'eligibility':{'minimum_on_or_above_target_blocks':4},'tiers':[
            {'min_session_score':70,'max_session_score':84.99,'reward':{'text':'10 minutes guitar.'}},
            {'min_session_score':85,'reward':{'text':'15 minutes guitar + an outdoor walk.'}}]}
        policy(p,True)
        w=dict(class_counts=dict(strong=2,on_target=2,slightly_over=1,over_target=1),normalized_session_score=84.99,average_focus_ratio=95)
        self.assertEqual(eligible_indices(p,100,None,95,False,w),[0])
        self.assertEqual(eligible_indices(p,100,None,95,False,{**w,'normalized_session_score':85}),[1])
        self.assertEqual(eligible_indices(p,100,None,95,False,{**w,'class_counts':dict(strong=1,on_target=2,slightly_over=2,over_target=1)}),[])

    def test_invalid_text_and_mixed_activity_metadata_rejected(self):
        for value in [{},{'text':''},{'text':'   '},{'text':1},{'text':'x'*1001},
                      {'text':'Walk','amount':1},{'text':'Walk','duration_seconds':60},{'text':'Token','actions':None},{'text':'Token','actions':False},
                      {'type':'walk','text':'Pull-ups','duration_seconds':60},
                      {'text':'Token','actions':[{'text':'Another token'}]}]:
            with self.subTest(value=value),self.assertRaises(ValueError):typed(value)
        for tier in [{'min_beat_score':108,'max_beat_score':100},
                     {'max_beat_score':112},{'max_beat_score':True},{'max_session_score':75}]:
            with self.subTest(tier=tier),self.assertRaises(ValueError):
                policy({'enabled':True,'tiers':[{**tier,'reward':{'text':'One token.'}}]})
        with self.assertRaises(ValueError):
            policy({'enabled':True,'tiers':[{'min_session_score':90,'max_session_score':80,'reward':{'text':'Guitar'}}]},True)
        self.assertEqual(typed({'text':'x'*1000})[0]['text'],'x'*1000)
        self.assertEqual(typed({'type':'text','text':'🟡'*1000})[0]['text'],'🟡'*1000)

    def test_text_receipt_cannot_change_gpt_reward_or_invent_measurements(self):
        pol={'enabled':True,'tiers':[{'reward':{'text':'5 pull-ups + TOKEN'}}]}
        value=dict(tier='tier_1',actions=typed({'text':'5 pull-ups + TOKEN'}),acknowledged=False,skipped=False)
        receipt(value,policy_value=pol)
        altered=copy.deepcopy(value);altered['actions'][0]['text']='20 pull-ups'
        with self.assertRaises(ValueError):receipt(altered,policy_value=pol)
        altered=copy.deepcopy(value);altered['actions'][0]['reps']=5
        with self.assertRaises(ValueError):receipt(altered,policy_value=pol)
        altered=copy.deepcopy(value);altered['actions'].append(dict(kind='walk',reps=0,seconds=60))
        with self.assertRaises(ValueError):receipt(altered,policy_value=pol)

    def test_report_markdown_csv_and_archive_keep_exact_text_and_policy(self):
        description='5 pull-ups + TOKEN, then "return".\nNo extra set.'
        report=conditional_report();p=report['blocks'][0]['performance']
        p['reward_policy']={'enabled':True,'tiers':[{'min_beat_score':100,'max_beat_score':107.99,'reward':{'text':description}}]}
        p['reward']['actions']=typed({'text':description})
        validate_report(report)
        self.assertIn(description,markdown(report))
        row=next(csv.DictReader(io.StringIO(csv_text([report]))))
        self.assertEqual(row['reward_label'],description)
        self.assertEqual(json.loads(row['reward_actions_json'])[0]['text'],description)
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);save_report(root/'reports',report)
            selected=select_report(root,report['session_id'],None)
            self.assertEqual(selected['blocks'][0]['performance']['reward']['actions'][0]['text'],description)
        altered=copy.deepcopy(report);altered['blocks'][0]['performance']['reward_policy']['tiers'][0]['max_beat_score']=100
        with self.assertRaises(ValueError):validate_report(altered)
