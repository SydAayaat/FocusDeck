import unittest,copy,json,tempfile
from pathlib import Path
from focusdeck.index_tracker import import_index,validate_document,leaves,preview,daily_bars,evidence
from focusdeck.achievements import validate_command
from focusdeck.draft_input import parse_draft
class IndexTests(unittest.TestCase):
 def setUp(self):self.doc=import_index('1 Chapter\n1.1 Topic A\n1.2 Topic B')
 def test_exact_ids_titles_hierarchy(self):self.assertEqual(['1.1','1.2'],[x['id'] for x in leaves(self.doc)]);self.assertEqual('1',self.doc['topics'][1]['parent_id'])
 def test_markdown(self):self.assertEqual(self.doc,import_index('# 1 Chapter\n- 1.1 Topic A\n- 1.2 Topic B'))
 def test_no_fabrication(self):
  for text in ['Some curriculum','1.1 Orphan','1 A\n1 B','1 A\n01 B']:
   with self.assertRaises(ValueError):import_index(text)
 def test_json_ids_stable(self):self.assertEqual(self.doc,import_index(json.dumps(self.doc)))
 def test_cycle_rejected(self):
  with self.assertRaises(ValueError):validate_document({'topics':[{'id':'a','title':'A','parent_id':'b'},{'id':'b','title':'B','parent_id':'a'}]})
 def test_duplicate_json_field_rejected(self):
  with self.assertRaises(ValueError):import_index('{"topics":[],"topics":[]}')
 def test_revision_preserves_completion_preview(self):
  l={'document':self.doc,'states':{'1.1':{'completed':True}}};new=copy.deepcopy(self.doc);new['topics'][1]['title']='Renamed';new['topics'].reverse();p=preview(new,l);self.assertEqual(1,p['completed']);self.assertEqual(['1.1'],p['renamed']);self.assertTrue(p['reordered']);self.assertEqual('Topic A',self.doc['topics'][1]['title'])
 def test_removed_topic_no_longer_counts(self):
  l={'document':self.doc,'states':{'1.1':{'completed':True}}};new={'topics':[self.doc['topics'][0],self.doc['topics'][2]]};p=preview(new,l);self.assertEqual(0,p['completed']);self.assertEqual(['1.1'],p['removed'])
 def test_gross_net_replay(self):
  e={'id':'a','date':'2026-10-08','delta':1};l={'events':[e,e,{'id':'b','date':'2026-10-08','delta':-1},{'id':'c','date':'2026-10-08','delta':1}]};bars=daily_bars(l,'2026-10-08');self.assertEqual(14,len(bars));self.assertEqual((2,1),(bars[-1]['gross'],bars[-1]['net']))
 def test_negative_is_visible(self):self.assertEqual(-1,daily_bars({'events':[{'id':'r','date':'2026-10-08','delta':-1}]},'2026-10-08',7)[-1]['net'])
 def test_commands_require_guards(self):
  c=dict(achievement_version=5,operation='index_topic',id='one',timestamp='2026-10-08T12:00:00Z',achievement_id='subject',topic_id='1.1',completed=True,expected_index_revision=1);self.assertEqual(c,validate_command(c));c.pop('expected_index_revision')
  with self.assertRaises(ValueError):validate_command(c)
 def test_explicit_migration(self):
  c=dict(achievement_version=5,operation='index_migrate',id='one',timestamp='2026-10-08T12:00:00Z',achievement_id='custom_compute',document=self.doc,expected_store_revision=5,confirm=True);validate_command(c);c['confirm']=False
  with self.assertRaises(ValueError):validate_command(c)
 def test_readable_gpt_import(self):
  d=parse_draft('FOCUSDECK CARD\ntitle: Subject\ntype: index_hybrid\nmaster_index: |\n  1 Chapter\n  1.1 Topic A\n  1.2 Topic B');self.assertEqual('index_create',d['draft']['operation']);self.assertEqual(5,d['draft']['achievement_version'])
 def test_readable_gpt_topic(self):
  d=parse_draft('FOCUSDECK UPDATE\noperation: index_topic\ncard: subject\ntopic_id: "1.1"\ncompleted: true\nexpected_index_revision: 1');self.assertEqual(5,d['draft']['achievement_version'])
 def test_thousand_topics(self):self.assertEqual(1000,len(leaves(import_index('\n'.join(f'{i} Topic {i}' for i in range(1,1001))))))
 def test_evidence_separates_legacy_daily_topics(self):
  s={'as_of_ms':1,'zone':'Europe/Berlin','ledger':{'indexes':{'subject':{'document':self.doc}},'events':[{'achievementId':'subject','id':'study'}],'progressHistory':[{'achievementId':'subject','newValue':23}]}};e=evidence(s,'subject');self.assertEqual(23,e['legacy_numeric_history'][0]['newValue']);self.assertEqual('study',e['daily_study_events'][0]['id'])
 def test_builder_create_generates_v5_without_numeric_counter(self):
  from focusdeck.control_center import ControlCenter
  with tempfile.TemporaryDirectory() as tmp:
   c=ControlCenter(tmp);c.snapshot=lambda:{'achievements':{'ledger':{}}}
   commands=c.card_commands({'definition':{'id':'subject','title':'Subject','category':'index','kind':'daily','streak_enabled':True,'progress_enabled':True},'index_action':'create','index_document':self.doc})
   self.assertEqual('index_create',commands[0]['operation']);self.assertEqual(5,commands[0]['achievement_version']);self.assertNotIn('progress',commands[0]['definition'])
 def test_builder_migration_preserves_card_identity(self):
  from focusdeck.control_center import ControlCenter
  old={'id':'custom_compute','title':'Custom Compute','category':'projects','kind':'scheduled','_base_hash':'same'}
  with tempfile.TemporaryDirectory() as tmp:
   c=ControlCenter(tmp);c.snapshot=lambda:{'achievements':{'ledger':{'definitions':[old]}}}
   commands=c.card_commands({'definition':old,'base_hash':'same','index_action':'migrate','index_document':self.doc,'store_revision':7})
   self.assertEqual('custom_compute',commands[0]['achievement_id']);self.assertEqual(7,commands[0]['expected_store_revision']);self.assertTrue(commands[0]['confirm']);self.assertEqual(1,len(commands))
if __name__=='__main__':unittest.main()
