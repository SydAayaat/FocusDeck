"""Explicit preview fixture, never loaded in normal operation."""
import copy
import time
from .control_center import DEFAULTS

class DemoCenter:
    def __init__(self):
        now = int(time.time()*1000)
        definitions = [dict(id='demo_porn_free',title='Porn Free',description='Build a better you. One day at a time.',category='discipline',kind='abstinence',streak_enabled=True,progress_enabled=False),
            dict(id='demo_german', title='German', description='Build your German vocabulary and maintain daily consistency.', category='learning', kind='daily', streak_enabled=True, progress_enabled=True, progress=dict(current_value=200,target_value=1250,unit='words')),
            dict(id='demo_daily_objectives',title='Daily Objectives Complete',description='Complete your daily set of focus objectives.',category='focus',kind='contract',streak_enabled=True,progress_enabled=False),
            dict(id='demo_custom_compute',title='Custom Compute Index',description='Hands-on computer architecture and firmware projects.',category='projects',kind='scheduled',streak_enabled=False,progress_enabled=True,progress=dict(current_value=18,target_value=25,unit='index topics')),
            dict(id='demo_guitar',title='Guitar',description='Practice guitar and improve your skills.',category='learning',kind='daily',streak_enabled=True,progress_enabled=True,progress=dict(current_value=8,target_value=20,unit='sessions')),
            dict(id='demo_cardio',title='Cardio',description='Keep your heart healthy with regular cardio.',category='health',kind='daily',streak_enabled=True,progress_enabled=False),
            dict(id='demo_gym',title='Gym',description='Strength training for a stronger, healthier you.',category='health',kind='daily',streak_enabled=True,progress_enabled=True,progress=dict(current_value=3,target_value=4,unit='workouts')),
            dict(id='demo_deep_work',title='Deep Work Blocks',description='Uninterrupted focus time for meaningful work.',category='college',kind='daily',streak_enabled=False,progress_enabled=True,progress=dict(current_value=2,target_value=4,unit='blocks'))]
        self.data = dict(layouts={},demo=True,service=True,error=None,status=dict(connected=True,state_is_cached=False,pending_command=None,state=dict(phase='active',session_id='demo',title='Calculus • Deep work',block_index=0,block_count=3,timer_mode='countdown',elapsed_active_ms=138000,remaining_ms=1362000,observed_at_ms=now)),
          devices=dict(paired_id='demo-phone',devices=[dict(device_id='demo-phone',name='Galaxy S24 Ultra',role='phone')],viewers=[dict(device_id='demo-tablet',name='Galaxy Tab S9',role='viewer')]),
          observed=dict(state=dict(session=dict(session_id='demo',title='Calculus • Deep work',blocks=[dict(id='b1',type='focus',title='Review lecture notes',timer_mode='countdown',duration_seconds=1500),dict(id='b2',type='break',title='Stretch',duration_seconds=300),dict(id='b3',type='focus',title='Practice problems',timer_mode='stopwatch')]))),
          achievements=dict(as_of_ms=now,zone='Europe/Berlin',ledger=dict(definitions=definitions,events=[],days={},targets=[],cardIcons={},progressHistory=[]),summary=[dict(id=d['id'],current=c,best=b,previous_best=b,record_gap=c-b,average=12.6,new_record=c>b) for d,c,b in zip(definitions,[12,12,5,0,15,7,8,0],[28,54,7,0,14,21,20,0])]),
          reports=[],settings=copy.deepcopy(DEFAULTS),templates=[],drafts={},deliveries=[],ui={},icon_library=[],icons=['book-open','cpu','flame','dumbbell','target','graduation-cap','guitar','leaf'],builtins=[])
        import datetime as dt
        from zoneinfo import ZoneInfo
        today=dt.datetime.now(ZoneInfo('Europe/Berlin')).date()
        store=self.data['achievements']['ledger']
        for i in range(14):
            key=(today-dt.timedelta(days=i)).isoformat()
            store['days'][key]=dict(date=key,zone='Europe/Berlin',activities={'demo_german':25 if i==0 else 1,'demo_gym':1,'demo_guitar':1,'demo_cardio':1},focusBlocks=5,focusMs=5400000,finalized=False)
            store['progressHistory'].append(dict(id='preview-'+str(i),achievementId='demo_german',timestamp=now-i*86400000,previousValue=175-i*10,newValue=200-i*10,delta=25,unit='words',source='Isolated preview',targetValueAtThatTime=1250))
        store['targets']=[dict(id='preview-target',date=today.isoformat(),atMs=now,objectives=[dict(kind='achievement',achievement_id='demo_german',minimum=25),dict(kind='achievement',achievement_id='demo_gym',minimum=1),dict(kind='achievement',achievement_id='demo_guitar',minimum=1),dict(kind='achievement',achievement_id='demo_cardio',minimum=1),dict(kind='focus_blocks',minimum=5),dict(kind='focus_time',minimum=10800000),dict(kind='achievement',achievement_id='demo_deep_work',minimum=4)])]
        store['segments']=[dict(achievementId='demo_german',start=(today-dt.timedelta(days=70)).isoformat(),end=(today-dt.timedelta(days=16)).isoformat(),value=54,personalRecord=True)]
        store['cardSizes']={'demo_gym':'wide'}
        store['cardIcons']={d['id']:dict(icon=dict(kind='builtin',name=name)) for d,name in zip(definitions,['flame','book-open','target','laptop','guitar','heart','dumbbell','book-open'])}
        self.data['known_devices']=[dict(**d,connected=True,last_seen_ms=now) for d in self.data['devices']['devices']+self.data['devices']['viewers']]
        self.data['reports'] = [dict(session_id=f'demo-report-{i}',title=title,status=outcome,started_at_ms=now-i*86400000,finished_at_ms=now-i*86400000+1500000,recorded_timezone='Europe/Berlin',focused_ms=1320000,break_ms=180000,active_ms=1500000,planned_block_count=3,blocks=[dict(id='demo-block',title='Vocabulary practice',type='focus',timer_mode='countdown',active_ms=1320000,outcome='done' if outcome=='complete' else 'skipped')],events=[]) for i,(title,outcome) in enumerate([('German vocabulary','complete'),('Calculus practice','stopped'),('Research reading','complete')])]
        # Complete isolated report evidence for the copy/view workflow.
        for r in self.data['reports']:
            start=r['started_at_ms']; r.update(planned_ms=1500000,paused_ms=0,wall_ms=1500000)
            b=r['blocks'][0]; b.update(planned_ms=1320000,budget_ms=1320000,paused_ms=0,started_at_ms=start,finished_at_ms=start+1320000)
            r['blocks'].append(dict(id='preview-break',title='Stretch',type='break',timer_mode='countdown',active_ms=180000,planned_ms=180000,budget_ms=180000,paused_ms=0,started_at_ms=start+1320000,finished_at_ms=start+1500000,outcome='done'))
            r['planned_block_count']=2
            # Canonical, isolated timing fixtures exercise charts without entering real history.
            r['schedule_snapshot']=dict(session_schedule=dict(planned_start=dt.datetime.fromtimestamp((start+180000)/1000,dt.timezone.utc).isoformat(),timezone='Europe/Berlin'),activities=[dict(id=b['id'],title=b['title'],type=b['type'],category='learning',schedule=dict(planned_start=dt.datetime.fromtimestamp((b['started_at_ms']+180000)/1000,dt.timezone.utc).isoformat(),planned_end=dt.datetime.fromtimestamp((b['finished_at_ms']+180000)/1000,dt.timezone.utc).isoformat(),timezone='Europe/Berlin')) for b in r['blocks']])
            r['timeline_coverage']='complete'
            r['events']=[]
            for index,b in enumerate(r['blocks']):
                for kind,at,active in [('block_started',b['started_at_ms'],0),('block_done' if b['outcome']=='done' else 'block_skipped',b['finished_at_ms'],b['active_ms'])]:
                    r['events'].append(dict(seq=len(r['events'])+1,kind=kind,at_ms=at,mono_ms=at-start,boot=1,block_index=index,phase='active',active_ms=active,paused_ms=0,budget_ms=b['budget_ms']))
            e=r['events'][-1];r['events'].append(dict(e,seq=e['seq']+1,kind='session_complete' if r['status']=='complete' else 'session_stopped',phase=r['status']))
        from .analytics import dataset
        self.data['analytics']=dataset(self.data['reports'])
    def handle(self,action,value=None):
        from .control_center import parse_draft, normalize_image
        import base64
        value=value or {}
        if action=='performance-report':
            from .performance import performance_report
            return performance_report(self.data['analytics'],value['scope'],value['begin'],value['end'])
        if action=='snapshot': return self.data
        if action=='save-layout':
            from .layouts import validate_layout
            self.data['layouts'][value['template']]=copy.deepcopy(validate_layout(value['layout']));return copy.deepcopy(self.data['layouts'])
        if action=='achievement-order':
            self.data['achievements']['ledger']['cardOrder']=value['order'];return dict(state='applied')
        if action=='validate-draft':
            from .common import validate_plan
            from .achievements import validate_command
            if value['kind']=='session': return validate_plan(value['draft'])
            if value['kind']=='batch': return [validate_command(c) for c in value['draft']]
            return validate_command(value['draft'])
        if action=='report-text':
            import json
            from .reports import markdown
            r=next(r for r in self.data['reports'] if f"{r['session_id']}:{r['started_at_ms']}"==value['key'])
            return dict(title=r['title'],text=json.dumps(r,ensure_ascii=False,indent=2) if value.get('format')=='json' else markdown(r))
        if action=='names': return {}
        if action=='parse': return parse_draft(value['text'])
        if action=='settings': self.data['settings']=value;return value
        if action=='save-draft': self.data['drafts'][value['kind']]=value['draft'];return {'saved':True}
        if action=='save-template': self.data['templates'].append(dict(id='preview',name=value['draft']['title'],plan=value['draft']));return {'saved':True}
        if action=='icon': return normalize_image(base64.b64decode(value['data']))
        if action=='ui': self.data['ui']=value;return value
        if action in {'import-icons','import-icon-paths'}:
            from .icon_library import read_inputs, read_uploaded, import_entries
            files=read_inputs(value['paths']) if action=='import-icon-paths' else read_uploaded(value['files'])
            entries, imported=import_entries(self.data['icon_library'], files, normalize_image)
            self.data['icon_library']=entries
            return dict(entries=entries, imported=imported)
        if action=='remove-library-icons':
            self.data['icon_library']=[i for i in self.data['icon_library'] if i['id'] not in value['ids']]
            return dict(entries=self.data['icon_library'])
        raise ValueError('Preview mode: phone writes, pairing and exports are disabled.')
