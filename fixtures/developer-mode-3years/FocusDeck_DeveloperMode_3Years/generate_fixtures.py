#!/usr/bin/env python3
"""Generate safe, deterministic, schema-neutral FocusDeck developer fixtures.

All artifacts are synthetic. NEVER run this against FocusDeck production storage.
The generated files are read-only source data for an isolated visualtest/dev fixture adapter.
"""
from __future__ import annotations
import calendar, collections, datetime as dt, hashlib, json, math, random, statistics, zipfile, sys
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent
ROOT.mkdir(parents=True,exist_ok=True)
SEED = int(sys.argv[1]) if len(sys.argv)>1 else 122026
rng = random.Random(SEED)
TZ = ZoneInfo('Europe/Berlin')
START = dt.date(2023,10,12)
END = dt.date(2026,10,12)
CLOCK = dt.datetime(2026,10,12,23,59,30,tzinfo=TZ)
DAY = dt.timedelta(days=1)
SCHEMA = 'focusdeck.synthetic-fixtures.v1'

PROJECTS = [
    dict(id='proj_ptfs',name='PTfS Exam Preparation',category='College',weight=1.25),
    dict(id='proj_compute',name='Custom Compute SoC',category='Projects',weight=1.10),
    dict(id='proj_german',name='German Speaking Practice',category='Learning',weight=1.15),
    dict(id='proj_embedded',name='Embedded Systems',category='College',weight=1.0),
    dict(id='proj_radar',name='Radar & SDR',category='Projects',weight=1.0),
    dict(id='proj_robotics',name='Robotics & Drones',category='Projects',weight=0.9),
    dict(id='proj_control',name='Control Systems',category='College',weight=0.9),
    dict(id='proj_fpga',name='FPGA Design',category='Projects',weight=0.8),
    dict(id='proj_guitar',name='Guitar Practice',category='Learning',weight=0.45),
]
P_BY_ID={p['id']:p for p in PROJECTS}
TITLES = {
 'proj_ptfs':['OpenMP concepts','MPI practice','Cache-aware optimization','Exam-style problems','Project optimization defense'],
 'proj_compute':['Pipeline review','Cache controller design','RTL simulation','Synthesis analysis','Bus interface'],
 'proj_german':['German conversation','Shadow speaking','Vocabulary practice','Pronunciation exercises','Listening and speaking'],
 'proj_embedded':['RTOS scheduling','Peripheral driver','Interrupt handling','Debugging hardware','Firmware architecture'],
 'proj_radar':['FFT processing','SDR signal chain','Radar range-Doppler','Antenna modelling','DSP experiment'],
 'proj_robotics':['Flight control loop','Sensor fusion','PID tuning','Motor calibration','Navigation algorithms'],
 'proj_control':['State-space analysis','Stability proofs','Controller synthesis','System identification','Simulation study'],
 'proj_fpga':['Timing closure','HDL design','FPGA verification','AXI data path','Resource optimization'],
 'proj_guitar':['Scale practice','Chord transitions','Rhythm practice','Finger exercises','Technique review'],
}
ALL_DATES=[START+DAY*i for i in range((END-START).days+1)]


def iso(t): return t.isoformat(timespec='seconds') if t else None

def at(d,h,m=0): return dt.datetime(d.year,d.month,d.day,h,m,tzinfo=TZ)

def jd(path, data):
    with (ROOT/path).open('w',encoding='utf8') as f:
        json.dump(data,f,ensure_ascii=False,indent=2,sort_keys=False)
        f.write('\n')

def jlines(path, items):
    with (ROOT/path).open('w',encoding='utf8') as f:
        for ob in items:f.write(json.dumps(ob,ensure_ascii=False,separators=(',',':'))+'\n')

def aware_seconds(a,b):
    return int((b.astimezone(dt.timezone.utc)-a.astimezone(dt.timezone.utc)).total_seconds())

# Prefix-shaping comments make status/mapping explicit and prevent accidental production import.
SESSIONS=[]; BLOCKS=[]; EVENTS=[]; HISTORY=[]; FOCUS_SEGMENTS=[]
sequence=0

def make_event(t,category,action,entity,detail=None,session_id=None,project_id=None):
    global sequence
    sequence+=1
    rec=dict(id=f'EV-{sequence:07d}',time=iso(t),date=t.astimezone(TZ).date().isoformat(),
      category=category,action=action,entity_id=entity,session_id=session_id,
      project_id=project_id,synthetic=True)
    if detail:rec['detail']=detail
    EVENTS.append(rec)
    HISTORY.append({**rec,'sequence':sequence})
    return rec


def segments_minus_pauses(start,end,pauses):
    cuts=[]; cursor=start
    for ps,pe in pauses:
        if ps>cursor:cuts.append((cursor,ps))
        cursor=pe
    if end>cursor:cuts.append((cursor,end))
    return cuts


def choose_project(d):
    vals=[]
    for p in PROJECTS:
        w=p['weight']
        if d.month in (3,4,9,10) and p['category']=='College':w*=1.25
        vals.append(w)
    return rng.choices(PROJECTS,weights=vals,k=1)[0]

for di,d in enumerate(ALL_DATES):
    # Long-run seasonality, weekdays, genuine inactive/unknown days.
    weekend = d.weekday()>=5
    prob=0.80 if not weekend else 0.55
    prob += 0.07*math.sin(di/365.25*2*math.pi)
    if rng.random()>prob:continue
    n=1+(rng.random()<0.48)+(rng.random()<0.09)
    n=int(n)
    times=[(8,35),(14,40),(19,10)] if n>=3 else ([(9,40),(18,30)] if n==2 else [(rng.choice([9,13,17,19]),rng.choice([0,10,25,40]))])
    for si in range(n):
        p=choose_project(d)
        plan_start=at(d,*times[si]); sid=f'DEV-{d.strftime("%Y%m%d")}-{si+1:02d}'
        mode=rng.choices(['countdown','stopwatch'],[0.82,0.18])[0]
        bc=rng.choices([1,2,3],[0.16,0.53,0.31])[0]
        if si==2: bc=min(bc,2)
        blocks=[]; planned_cursor=plan_start; previous_end=None
        for bi in range(bc):
            bid=f'{sid}-B{bi+1:02d}'
            length=rng.choice([1200,1500,1800,2100,2400,2700,3000,3300,3600])
            # Infrequent long blocks; avoid long evening sessions.
            if si==2:length=min(length,2700)
            planned_block_start=planned_cursor
            planned_cursor += dt.timedelta(seconds=length)
            if bi<bc-1:planned_cursor+=dt.timedelta(minutes=5)
            outcome=rng.choices(['DONE','SKIPPED','ENDED_EARLY','NOT_STARTED'],[0.85,0.065,0.065,0.02])[0]
            if mode=='stopwatch' and outcome=='DONE':length=0  # stopwatch: no countdown target
            if outcome=='NOT_STARTED' and bi<bc-1:outcome='SKIPPED'
            planned_duration=length if mode=='countdown' else None
            pauses=[]; actual_start=None;actual_end=None;focus_sec=0;pause_sec=0;break_intervals=[]
            if previous_end is not None:
                gap=rng.randrange(2,9)*60
                bs=previous_end;be=bs+dt.timedelta(seconds=gap)
                break_intervals.append(dict(start=iso(bs),end=iso(be),duration_seconds=gap))
                make_event(bs,'block_events','break_started',bid,{'duration_seconds':gap},sid,p['id'])
                make_event(be,'block_events','break_ended',bid,None,sid,p['id'])
            if outcome in ('SKIPPED','NOT_STARTED'):
                action_time=max(planned_block_start,previous_end or planned_block_start)
                make_event(action_time,'block_events','block_skipped' if outcome=='SKIPPED' else 'block_not_started',bid,{'status':outcome},sid,p['id'])
            else:
                offset=rng.randint(-12,19)*60
                if previous_end:
                    earliest=previous_end+dt.timedelta(seconds=sum(q['duration_seconds'] for q in break_intervals))
                    actual_start=max(earliest,planned_block_start+dt.timedelta(seconds=offset))
                else:actual_start=planned_block_start+dt.timedelta(seconds=offset)
                if outcome=='DONE':
                    scale=rng.uniform(0.74,1.35)
                else:scale=rng.uniform(0.24,0.70)
                focus_sec=max(360,int((planned_duration or rng.choice([1800,2400,3000]))*scale/60)*60)
                if si==2:focus_sec=min(focus_sec,3000)
                pause_count=rng.choices([0,1,2],[0.67,0.27,0.06])[0]
                cursor=actual_start
                for k in range(pause_count):
                    before=max(90,focus_sec//(pause_count+1))
                    cursor+=dt.timedelta(seconds=before)
                    duration=rng.randint(2,7)*60
                    pstart=cursor;pend=pstart+dt.timedelta(seconds=duration)
                    pauses.append((pstart,pend));pause_sec+=duration;cursor=pend
                actual_end=actual_start+dt.timedelta(seconds=focus_sec+pause_sec)
                # Last-minute evening session safety (doesn't affect input target logic).
                if actual_end.date()>d or actual_end>at(d,23,45):
                    boundary=at(d,23,45)
                    if actual_start<boundary:
                        actual_end=boundary
                        pauses=[(a,min(b,boundary)) for a,b in pauses if a<boundary]
                        pause_sec=sum(aware_seconds(a,b) for a,b in pauses)
                        focus_sec=aware_seconds(actual_start,actual_end)-pause_sec
                    else:
                        actual_end=actual_start+dt.timedelta(minutes=8);focus_sec=480;pauses=[];pause_sec=0
                make_event(actual_start,'block_events','block_started',bid,{'timer_mode':mode},sid,p['id'])
                for ps,pe in pauses:
                    make_event(ps,'block_events','block_paused',bid,None,sid,p['id'])
                    make_event(pe,'block_events','block_resumed',bid,None,sid,p['id'])
                make_event(actual_end,'block_events','block_completed' if outcome=='DONE' else 'block_ended_early',bid,{'focus_seconds':focus_sec},sid,p['id'])
                if outcome=='DONE' and rng.random()<0.08:
                    make_event(actual_end,'block_events','reward_offered',bid,{'reward':'stretch break'},sid,p['id'])
                    make_event(actual_end+dt.timedelta(seconds=3),'block_events','reward_acknowledged',bid,None,sid,p['id'])
                for seg in segments_minus_pauses(actual_start,actual_end,pauses):FOCUS_SEGMENTS.append((seg[0],seg[1],bid,sid))
            quality_total=5 if outcome in ('DONE','ENDED_EARLY') else None
            quality_met=rng.randint(2,5) if quality_total else None
            b=dict(id=bid,session_id=sid,project_id=p['id'],title=rng.choice(TITLES[p['id']]),order=bi,
                 block_type='focus',timer_mode=mode,status=outcome,planned_start=iso(planned_block_start),
                 planned_duration_seconds=planned_duration,actual_start=iso(actual_start),actual_end=iso(actual_end),
                 active_focus_seconds=focus_sec,paused_seconds=pause_sec,pause_count=len(pauses),
                 pauses=[dict(start=iso(a),end=iso(b)) for a,b in pauses],break_before=break_intervals,
                 start_delta_seconds=aware_seconds(planned_block_start,actual_start) if actual_start else None,
                 duration_delta_seconds=focus_sec-planned_duration if planned_duration is not None and actual_start else None,
                 quality_criteria_total=quality_total,quality_criteria_met=quality_met,
                 synthetic=True)
            BLOCKS.append(b);blocks.append(b)
            if actual_end:previous_end=actual_end
        first=next((b for b in blocks if b['actual_start']),None)
        ends=[b['actual_end'] for b in blocks if b['actual_end']]
        last=max(ends) if ends else None
        done=sum(b['status']=='DONE' for b in blocks)
        skipped=sum(b['status']=='SKIPPED' for b in blocks)
        early=sum(b['status']=='ENDED_EARLY' for b in blocks)
        not_started=sum(b['status']=='NOT_STARTED' for b in blocks)
        status='DONE' if done==len(blocks) else ('ENDED_EARLY' if early else ('FINISHED_WITH_SKIPS' if skipped else 'NOT_STARTED'))
        focus=sum(b['active_focus_seconds'] for b in blocks)
        pause=sum(b['paused_seconds'] for b in blocks)
        breaks=sum(q['duration_seconds'] for b in blocks for q in b['break_before'])
        first_at=dt.datetime.fromisoformat(first['actual_start']) if first else None
        last_at=dt.datetime.fromisoformat(last) if last else None
        wall=aware_seconds(first_at,last_at) if first_at and last_at else 0
        rec=dict(id=sid,project_id=p['id'],title=f'{p["name"]} — {rng.choice(["Deep Work","Practice","Review","Sprint","Study Session"])}',
          source='GPT_GENERATED' if rng.random()<0.37 else 'MANUAL',date=d.isoformat(),timezone='Europe/Berlin',
          planned_start=iso(plan_start),planned_end=iso(planned_cursor),actual_start=iso(first_at),actual_end=iso(last_at),
          timer_mode=mode,status=status,planned_focus_seconds=sum(b['planned_duration_seconds'] or 0 for b in blocks),
          focus_seconds=focus,pause_seconds=pause,break_seconds=breaks,wall_seconds=wall,
          block_count=len(blocks),done_count=done,skipped_count=skipped,ended_early_count=early,not_started_count=not_started,
          start_delta_seconds=aware_seconds(plan_start,first_at) if first_at else None,
          focus_ratio_percent=round(100*focus/(focus+pause),1) if focus+pause>0 else None,
          synthetic=True)
        SESSIONS.append(rec)
        if first_at:make_event(first_at,'sessions','session_started',sid,{'status':status},sid,p['id'])
        if last_at:make_event(last_at,'sessions','session_finished',sid,{'status':status,'focus_seconds':focus},sid,p['id'])
        if rec['source']=='GPT_GENERATED':
            gtime=plan_start-dt.timedelta(minutes=rng.randint(15,110))
            make_event(gtime,'gpt_sessions','gpt_session_generated',sid,
             {'generated_blocks':len(blocks),'planned_focus_seconds':rec['planned_focus_seconds'],'result':'IMPORTED_SYNTHETIC'},sid,p['id'])

# Synthetic standalone commands, statuses, and recurring history types.
for d in ALL_DATES:
    if d.day in (3,17) and rng.random()<0.78:
        p=rng.choice(PROJECTS)
        make_event(at(d,12,10),'gpt_cards','gpt_card_generated',f'CARD-{d:%Y%m%d}-{p["id"]}',
          {'card':p['name'],'operation':'CREATED_SYNTHETIC','kind':rng.choice(['STREAK_ONLY','PROGRESS_ONLY','HYBRID'])},project_id=p['id'])
    if d.day==1 and rng.random()<0.35:
        make_event(at(d,12,20),'status_updates','offline_status_sample',f'STATUS-{d:%Y%m}',
          {'status':'offline_preview_only','network_action':False})

# Three-year focus buckets. Absolute durations are apportioned by actual segments.
HOURS=collections.defaultdict(lambda:[0]*24)
for st,en,bid,sid in FOCUS_SEGMENTS:
    cursor=st
    while cursor<en:
        local=cursor.astimezone(TZ)
        hour_end_utc=(local.replace(minute=0,second=0,microsecond=0).astimezone(dt.timezone.utc)+dt.timedelta(hours=1))
        # Segment boundaries always short, but using UTC handles DST conservatively.
        stop=min(en,hour_end_utc)
        if stop<=cursor:stop=min(en,cursor+dt.timedelta(minutes=1))
        HOURS[local.date().isoformat()][local.hour]+=aware_seconds(cursor,stop)
        cursor=stop

# Snapshot of synthetic activity and truth-status of observed-zero vs unknown days.
DAY_ROWS=[]; DAYMAP={}
SESS_BY_DATE=collections.defaultdict(list)
for s in SESSIONS:SESS_BY_DATE[s['date']].append(s)
for d in ALL_DATES:
    k=d.isoformat();ss=SESS_BY_DATE[k]; hrs=HOURS[k]
    # Set some no-session dates as observed zero and some genuinely unknown.
    evidence='measured' if ss else ('observed_zero' if (d.toordinal()+SEED)%3==0 else 'unknown')
    total=sum(s['focus_seconds'] for s in ss)
    assert sum(hrs)==total,(k,sum(hrs),total)
    cnt=sum(s['block_count'] for s in ss)
    punctual=[s['start_delta_seconds'] for s in ss if s['start_delta_seconds'] is not None]
    row=dict(date=k,evidence=evidence,focus_seconds=total,pause_seconds=sum(s['pause_seconds'] for s in ss),
      break_seconds=sum(s['break_seconds'] for s in ss),wall_seconds=sum(s['wall_seconds'] for s in ss),
      session_count=len(ss),done_session_count=sum(s['status']=='DONE' for s in ss),block_count=cnt,
      done_block_count=sum(s['done_count'] for s in ss),skipped_block_count=sum(s['skipped_count'] for s in ss),
      ended_early_block_count=sum(s['ended_early_count'] for s in ss),
      punctuality_observations=len(punctual),start_delta_sum_seconds=sum(punctual),mean_start_delta_seconds=round(statistics.mean(punctual)) if punctual else None,
      focus_by_hour_seconds=hrs,focus_by_8h_bin_seconds=[sum(hrs[i:i+8]) for i in (0,8,16)],synthetic=True)
    DAY_ROWS.append(row); DAYMAP[k]=row

# Aggregates are DERIVED from daily source totals; never random KPI projections.
PERIOD_ROWS=[]
def aggregate_period(kind,key,rows):
    fields=['focus_seconds','pause_seconds','break_seconds','wall_seconds','session_count','done_session_count','block_count','done_block_count','skipped_block_count','ended_early_block_count','punctuality_observations','start_delta_sum_seconds']
    out={'period':kind,'key':key,'start_date':rows[0]['date'],'end_date':rows[-1]['date'],'days':len(rows),
         'known_days':sum(x['evidence']!='unknown' for x in rows),'unknown_days':sum(x['evidence']=='unknown' for x in rows),
         'synthetic':True}
    for f in fields:out[f]=sum(x[f] for x in rows)
    total_weighted=out['start_delta_sum_seconds']
    out['mean_start_delta_seconds']=round(total_weighted/out['punctuality_observations']) if out['punctuality_observations'] else None
    return out

def pack_period(kind,keyf):
    groups=collections.OrderedDict()
    for r in DAY_ROWS:groups.setdefault(keyf(dt.date.fromisoformat(r['date'])),[]).append(r)
    for k,rows in groups.items():PERIOD_ROWS.append(aggregate_period(kind,k,rows))
pack_period('week',lambda d:f'{d.isocalendar().year}-W{d.isocalendar().week:02d}')
pack_period('month',lambda d:d.strftime('%Y-%m'))
pack_period('year',lambda d:str(d.year))

# Synthetic index hierarchy is not the user's original PTfS syllabus.
INDEXES=[{'id':'index_ptfs_dev','title':'PTfS — Synthetic Demonstration Index','total':126,'sections':7,'category':'INDEX','card_type':'HYBRID'},
 {'id':'index_embedded_dev','title':'Embedded Systems — Synthetic Index','total':64,'sections':8,'category':'INDEX','card_type':'PROGRESS_ONLY'}]
INDEX_TOPICS=[];INDEX_EVENTS=[]
for idx in INDEXES:
    for pos in range(1,idx['total']+1):
        section=(pos-1)*idx['sections']//idx['total']+1
        INDEX_TOPICS.append(dict(id=f'{idx["id"]}-T{pos:03d}',index_id=idx['id'],section_id=f'{idx["id"]}-S{section:02d}',
           title=f'Synthetic practice topic {pos:03d}',ordinal=pos,synthetic=True,source_note='NOT original study-index content'))
TOPICS_BY_IDX={idx['id']:[t['id'] for t in INDEX_TOPICS if t['index_id']==idx['id']] for idx in INDEXES}
TOPIC_CURRENT={idx['id']:set() for idx in INDEXES}
for d in ALL_DATES:
    # Tie index progress to session activity with small, plausible reopens.
    for idx in INDEXES:
        interested=any(s['project_id']==('proj_ptfs' if idx['id']=='index_ptfs_dev' else 'proj_embedded') for s in SESS_BY_DATE[d.isoformat()])
        if not interested or rng.random()>0.27:continue
        current=TOPIC_CURRENT[idx['id']]
        notyet=[x for x in TOPICS_BY_IDX[idx['id']] if x not in current]
        for ti in range(rng.choice([1,1,1,2,3])):
            if current and rng.random()<0.055:
                topic=rng.choice(sorted(current));current.remove(topic);action='topic_reopened'
            elif notyet:
                topic=rng.choice(notyet);notyet.remove(topic);current.add(topic);action='topic_completed'
            else:break
            when=at(d,21,rng.randint(0,50))
            rec=dict(id=f'IDXEV-{len(INDEX_EVENTS)+1:06d}',index_id=idx['id'],topic_id=topic,
              action=action,time=iso(when),date=d.isoformat(),synthetic=True)
            INDEX_EVENTS.append(rec)
            make_event(when,'index_progress',action,topic,{'index_id':idx['id']})

INDEX_SNAPSHOTS=[{**idx,'completed_topics':len(TOPIC_CURRENT[idx['id']]),
    'reopened_events':sum(e['index_id']==idx['id'] and e['action']=='topic_reopened' for e in INDEX_EVENTS),
    'completion_percent':round(100*len(TOPIC_CURRENT[idx['id']])/idx['total'],2),
    'synthetic':True} for idx in INDEXES]

# Achievement raw evidence AND a derivative snapshot. Streaks measured in daily continuity.
ACH_DEFS=[
 dict(id='ach_objectives',title='Daily Objectives',category='Discipline',card_type='STREAK_ONLY',target=None),
 dict(id='ach_focus_blocks',title='Focus Blocks',category='Discipline',card_type='HYBRID',target=2200),
 dict(id='ach_german',title='German',category='Learning',card_type='HYBRID',target=1500),
 dict(id='ach_guitar',title='Guitar',category='Learning',card_type='STREAK_ONLY',target=None),
 dict(id='ach_gym',title='Gym',category='Health',card_type='STREAK_ONLY',target=None),
 dict(id='ach_cardio',title='Cardio',category='Health',card_type='STREAK_ONLY',target=None),
 dict(id='ach_compute',title='Custom Compute',category='Projects',card_type='PROGRESS_ONLY',target=350),
 dict(id='ach_college',title='College',category='College',card_type='HYBRID',target=1200),
 dict(id='ach_ptfs_index',title='PTfS Index',category='INDEX',card_type='HYBRID',target=126),
]
ACH_EVENTS=[];ACH_DAYS=collections.defaultdict(set);ACH_PROGRESS=collections.defaultdict(int)

def add_achievement(d,aid,kind,delta=0):
    row=dict(id=f'ACHEV-{len(ACH_EVENTS)+1:07d}',achievement_id=aid,date=d.isoformat(),time=iso(at(d,22,30)),
             action=kind,delta=delta,synthetic=True)
    ACH_EVENTS.append(row)
    if kind=='daily_confirmed':ACH_DAYS[aid].add(d)
    else:ACH_PROGRESS[aid]+=delta
    if (len(ACH_EVENTS)%4)==0:
        make_event(at(d,22,30),'achievement_changes',kind,aid,{'delta':delta})

for d in ALL_DATES:
    ss=SESS_BY_DATE[d.isoformat()]
    projects={s['project_id'] for s in ss}
    done_blocks=sum(s['done_count'] for s in ss)
    if ss and rng.random()<0.75:add_achievement(d,'ach_objectives','daily_confirmed')
    if done_blocks:
        add_achievement(d,'ach_focus_blocks','progress_delta',done_blocks)
        add_achievement(d,'ach_focus_blocks','daily_confirmed')
    if 'proj_german' in projects or rng.random()<0.35:
        add_achievement(d,'ach_german','daily_confirmed')
        add_achievement(d,'ach_german','progress_delta',rng.randint(1,5))
    if 'proj_guitar' in projects or rng.random()<0.17:add_achievement(d,'ach_guitar','daily_confirmed')
    if rng.random()<0.20:add_achievement(d,'ach_gym','daily_confirmed')
    if rng.random()<0.26:add_achievement(d,'ach_cardio','daily_confirmed')
    if 'proj_compute' in projects and rng.random()<0.72:
        add_achievement(d,'ach_compute','progress_delta',rng.randint(1,3))
    college_sessions=sum(P_BY_ID[s['project_id']]['category']=='College' for s in ss)
    if college_sessions:
        add_achievement(d,'ach_college','daily_confirmed')
        add_achievement(d,'ach_college','progress_delta',college_sessions)
    if any(e['date']==d.isoformat() and e['index_id']=='index_ptfs_dev' for e in INDEX_EVENTS):
        add_achievement(d,'ach_ptfs_index','daily_confirmed')


def streak_summary(days):
    if not days:return 0,0,None
    best=0;cnt=0;prev=None
    for d in sorted(days):
        cnt = cnt+1 if prev is not None and (d-prev).days==1 else 1
        best=max(best,cnt);prev=d
    # Current streak only if last confirmation is today or yesterday (grace window).
    if max(days)<END-dt.timedelta(days=1):return 0,best,max(days).isoformat()
    n=0;x=max(days)
    while x in days:n+=1;x-=DAY
    return n,best,max(days).isoformat()

ACH_SNAP=[]
for a in ACH_DEFS:
    aid=a['id'];current,best,last=streak_summary(ACH_DAYS[aid]);progress=ACH_PROGRESS[aid]
    if aid=='ach_ptfs_index':progress=len(TOPIC_CURRENT['index_ptfs_dev'])
    ACH_SNAP.append({**a,'progress_current':progress,'progress_target':a['target'],
                     'progress_percent':round(100*min(progress,a['target'])/a['target'],2) if a['target'] else None,
                     'current_streak_days':current,'best_streak_days':best,'record_gap_days':current-best,
                     'last_confirmed_date':last,'synthetic':True})

# Explicit simulated states are NOT executable sessions or device pairing actions.
SCENARIOS=[
 dict(id='idle',description='No active session',session=None,network_disabled=True),
 dict(id='active',description='Active countdown and a red recording state',session={
     'id':'SCENE-ACTIVE','title':'Synthetic Deep Work','status':'ACTIVE','timer_mode':'countdown',
     'planned_seconds':5400,'elapsed_seconds':1880,'focus_seconds':1660,'pause_seconds':220,
     'started_at':iso(at(END,21,30)),'synthetic':True},network_disabled=True),
 dict(id='paused',description='Paused countdown and amber indicator',session={
     'id':'SCENE-PAUSED','title':'Synthetic Study Sprint','status':'PAUSED','timer_mode':'countdown',
     'planned_seconds':5400,'elapsed_seconds':2300,'focus_seconds':1880,'pause_seconds':420,
     'started_at':iso(at(END,21,25)),'synthetic':True},network_disabled=True),
 dict(id='ended_early',description='Finished but partial and not credited as DONE',session={'id':'SCENE-EARLY','status':'ENDED_EARLY','synthetic':True},network_disabled=True),
 dict(id='empty',description='All numbers unavailable; distinguish unknown from observed zero',session=None,network_disabled=True),
 dict(id='zero',description='Observed zero activity is displayed as zero, not N/A',session=None,network_disabled=True),
 dict(id='long_text',description='Very long session/card titles and 1.4 font scale for layout stress testing',session={
     'id':'SCENE-LONG','title':'Extremely Long Synthetic Programming Techniques for Supercomputers Optimization Defense Session',
     'status':'DONE','synthetic':True},network_disabled=True)
]

# Data exports
jd('projects.json',PROJECTS);jlines('sessions.jsonl',SESSIONS);jlines('blocks.jsonl',BLOCKS)
jlines('timeline_events.jsonl',sorted(EVENTS,key=lambda e:(e['time'],e['id'])))
jlines('history_activity.jsonl',sorted(HISTORY,key=lambda e:(e['time'],e['sequence']),reverse=True))
jlines('daily_analytics.jsonl',DAY_ROWS);jlines('period_analytics.jsonl',PERIOD_ROWS)
jd('achievement_definitions.json',ACH_DEFS);jlines('achievement_events.jsonl',ACH_EVENTS)
jd('achievement_snapshots.json',ACH_SNAP);jd('index_definitions.json',INDEXES)
jlines('index_topics.jsonl',INDEX_TOPICS);jlines('index_events.jsonl',INDEX_EVENTS)
jd('index_snapshots.json',INDEX_SNAPSHOTS);jd('developer_scenarios.json',SCENARIOS)

# Quick audit: reference links, sums, chronological data, consistency, index events, local time and no production identity.
assert len({s['id'] for s in SESSIONS})==len(SESSIONS)
assert len({b['id'] for b in BLOCKS})==len(BLOCKS)
sbyid={s['id']:s for s in SESSIONS}
for b in BLOCKS:
    assert b['session_id'] in sbyid
    assert b['project_id'] in P_BY_ID
    assert b['active_focus_seconds']>=0
    assert b['paused_seconds']>=0
    if b['actual_start'] and b['actual_end']:
        assert int((dt.datetime.fromisoformat(b['actual_end'])-dt.datetime.fromisoformat(b['actual_start'])).total_seconds()) == b['active_focus_seconds']+b['paused_seconds'],b['id']
    if b['status'] in ('SKIPPED','NOT_STARTED'):assert b['active_focus_seconds']==0
for s in SESSIONS:
    bl=[b for b in BLOCKS if b['session_id']==s['id']]
    assert s['focus_seconds']==sum(b['active_focus_seconds'] for b in bl)
    assert s['done_count']==sum(b['status']=='DONE' for b in bl)
assert sum(r['focus_seconds'] for r in DAY_ROWS)==sum(s['focus_seconds'] for s in SESSIONS)
assert sum(r['focus_seconds'] for r in PERIOD_ROWS if r['period']=='month')==sum(s['focus_seconds'] for s in SESSIONS)
for idx in INDEXES:
    active=set()
    for e in INDEX_EVENTS:
        if e['index_id']!=idx['id']:continue
        if e['action']=='topic_completed':active.add(e['topic_id'])
        else:active.remove(e['topic_id'])
    assert len(active)==len(TOPIC_CURRENT[idx['id']])

COUNTS={'dates':len(ALL_DATES),'sessions':len(SESSIONS),'blocks':len(BLOCKS),
 'timeline_events':len(EVENTS),'history_activity':len(HISTORY),'daily_analytics':len(DAY_ROWS),
 'period_analytics':len(PERIOD_ROWS),'achievement_definitions':len(ACH_DEFS),'achievement_events':len(ACH_EVENTS),
 'index_definitions':len(INDEXES),'index_topics':len(INDEX_TOPICS),'index_events':len(INDEX_EVENTS)}
manifest=dict(format=SCHEMA,version=1,target_android_version='1.22.0',
  fixture_only=True,synthetic=True,source='GENERATED_OFFLINE',
  seed=SEED,timezone='Europe/Berlin',start_date=START.isoformat(),end_date=END.isoformat(),
  fixed_clock=iso(CLOCK),storage_policy='READ_ONLY_EPHEMERAL_ISOLATED_VISUALTEST_ONLY',
  default_fixture='REFERENCE_3Y',
  no_network=True,no_sync=True,no_production_import=True,
  punctuality_sign='actual_start - planned_start; positive means late',
  absent_evidence='unknown (N/A); observed_zero is distinct',
  counts=COUNTS,
  caution=['Schema-neutral generated fixtures. Codex must map to existing repository contracts.',
    'All GPT, card, achievement and device activity is synthetic—not historical fact.',
    'Index topic names are placeholders, not the real 126-topic PTfS index.',
    'No QR pairing keys, credentials, addresses or user records are present.',
    'Do not use this payload with production import, archive restoration or sync routines.',
    'Session scoring and Time Split values must be derived with existing production formulae.'])
files=[]
for f in sorted(ROOT.iterdir()):
    if f.is_file():
        raw=f.read_bytes()
        files.append(dict(path=f.name,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()))
manifest['files']=files
jd('manifest.json',manifest)

readme=f'''# FocusDeck Developer Mode — 3-Year Synthetic Fixture Pack\n\n**Fixture format:** `{SCHEMA}`  
**Target UI release:** Android 1.22.0 (planned)  
**Seed:** `{SEED}` (deterministic)  
**Fixed local test clock:** `{iso(CLOCK)}` (Europe/Berlin)  
**Range:** `{START}` to `{END}` inclusive (**{len(ALL_DATES)} dates**)\n\n## WHAT IS IN THE PACKAGE\n\n- `manifest.json`: file hashes, counts, clock, safety contract and schema marker.\n- `projects.json`: {len(PROJECTS)} synthetic Projects/College/Learning entities.\n- `sessions.jsonl`: {len(SESSIONS)} synthetic sessions (planned/actual timing, stopwatch/countdown, states and totals).\n- `blocks.jsonl`: {len(BLOCKS)} timed focus blocks, pauses, break intervals, estimation and quality evidence.\n- `timeline_events.jsonl`: {len(EVENTS)} timestamped events for timeline/details.\n- `history_activity.jsonl`: chronological activity feed from synthetic session, GPT, Index, achievement and block events.\n- `daily_analytics.jsonl`: {len(DAY_ROWS)} daily summaries with exact 24 hourly bins, three 8-hour bins, status and counts.\n- `period_analytics.jsonl`: {len(PERIOD_ROWS)} weekly/monthly/yearly summaries derived from daily rows.\n- `achievement_definitions.json`, `achievement_events.jsonl`, `achievement_snapshots.json`: nine achievement definitions with evidence and computed progression.\n- `index_definitions.json`, `index_topics.jsonl`, `index_events.jsonl`, `index_snapshots.json`: two SYNTHETIC sample indexes (126 and 64 topics) with replayable topic changes.\n- `developer_scenarios.json`: separate active, paused, idle, early, empty, observed-zero and long-text UI preview presets.\n- `schema_notes.md`: practical field contracts and adapter guidelines.\n- `validate_fixtures.py`: offline archive validator.\n\n## INSTALLATION / LOADING\n\n**NOT a production backup and NOT a native FocusDeck import file.** The exact Kotlin/SQLite schema is unavailable in this chat.\n\nCodex should:\n\n1. Keep the existing `com.hax.focusdeck.visualtest` isolation and developer feature boundaries.\n2. Copy this fixture pack into a development-only assets folder (or unpack it into a debug-only sandbox).\n3. Validate `manifest.json` + SHA256 checksums before loading.\n4. Parse JSONL **streamingly**; do not hold the entire 3-year activity feed in Compose state.\n5. Map the synthetic records through an adapter to the existing repository/viewmodel interfaces, without changing production schema or scoring logic.\n6. Build analytics from the supplied source records or use the provided validated aggregates as fast display caches.\n7. Use immutable, offline fixtures: NO production writes, pairing, transport, cloud sync, archive restore, reward redemptions or external device actions.\n8. Allow separate `REFERENCE_3Y`, `EMPTY`, `ZERO`, `ACTIVE`, `PAUSED`, `LONG_TEXT` states without mutation.\n9. In Developer Mode OFF, restore the genuine production data source without changing any stored record.\n10. Compare the fixture's aggregate/count invariants to the adapter's computed results in tests.\n\n**Don't just write these JSONL files into the app's actual SQL tables.** Their schema is intentionally portable; the adapter must translate records into production view models.\n\n## KEY SEMANTIC RULES\n\n- `start_delta_seconds = actual_start - planned_start`: **positive = late (red)**, **negative = early (green)**.\n- `active_focus_seconds` is measured focus. Paused seconds and explicit break seconds are distinct.\n- `status=DONE` only when every focus block is DONE. Mixed/skipped outcomes do not receive full completion credit.\n- `unknown` days show **N/A**, while `observed_zero` days show 0 activity.\n- Stopwatch focus blocks have `planned_duration_seconds=null`, not an invented countdown.\n- Planned and actual timestamps retain timezone offsets; the display zone is Europe/Berlin.\n- Achievements derive streaks from daily confirmations and progress from event deltas.\n- Index topic completions and reopens are replayable events, not raw counters.\n- Scoring, BeatScore, efficiency, estimation accuracy and reward policies remain owned by the existing FocusDeck implementation.\n- Synthetic activity is strictly fiction and must be visually distinguished in developer tooling.\n\n## VISUAL COVERAGE\n\nThis pack exercises Home, Analytics Day/Week/Month/Year, focus-by-hour charts, productivity calendars, punctuality trends, Plan Execution, Time Split, category project charts, History, Timeline, Session Summary, Achievements, indexes, and empty/long-name edge cases. It also includes source event records for GPT generation feed entries but **does not recreate actual GPT payloads**.\n\n## EXAMPLE STREAMING READER\n\n```python\nimport json\nwith open('sessions.jsonl', encoding='utf-8') as f:\n    for line in f:\n        session = json.loads(line)\n        # Route to a debug-only Kotlin fixture adapter; NEVER production import.\n```\n\n## RELATIONSHIPS\n\n- `session.id` -> `blocks.session_id` and `timeline_events.session_id`.\n- `session.project_id` -> `projects.id`.\n- `achievement_events.achievement_id` -> `achievement_definitions.id`.\n- `index_events.topic_id` -> `index_topics.id`.\n- Period summary rows derive from daily rows, which reconcile to session focus totals.\n\nUse the included manifest and validation script before each integration.\n'''
(ROOT/'README.md').write_text(readme,encoding='utf8')

schema='''# Synthetic contract (format: focusdeck.synthetic-fixtures.v1)\n\nThis is a **schema-neutral dev fixture**, not a production import schema. `synthetic: true` on records is a required provenance flag. Use the separate `.visualtest` sandbox or debug-only read-only adapter.\n\n## Dates and timestamps\nAll ISO timestamps include Europe/Berlin's applicable UTC offset. Day-level dates use local `YYYY-MM-DD`. `fixed_clock` in manifest fixes screenshot comparisons. Sessions may be absent on idle days.\n\n## Sessions and blocks\n`session.id` is stable across files. `session.status` is `DONE`, `FINISHED_WITH_SKIPS`, `ENDED_EARLY`, or `NOT_STARTED`. `session.focus_seconds` equals the sum of all linked block `active_focus_seconds`. `done_count` counts only `DONE` blocks. `planned_focus_seconds` sums only known countdown targets; stopwatches are null.\n\n`block.start_delta_seconds` is actual minus planned, positive=late. A `null` actual time is unknown/unstarted/skipped rather than epoch 0. `pauses[]` is ordered and falls inside `actual_start`/`actual_end`. The elapsed block interval equals focus plus pause durations. `break_before[]` tracks observed break intervals, not arbitrary waiting gaps. Quality totals are mock raw evidence, not scored achievements.\n\n## Activity feed\n`history_activity.jsonl` is reverse-chronological, stable ordered by timestamp + sequence. Names and GPT generation results are synthetic, not actual network requests. `timeline_events.jsonl` stores the same event identities chronologically; event actions are descriptive fixture names to map to the app's existing enums.\n\n## Analytics\n`daily_analytics` includes 24 `focus_by_hour_seconds` bins and three 8-hour bins. Their sums must equal daily `focus_seconds`. `evidence` is `measured`, `observed_zero`, or `unknown`; only observed_zero is genuinely zero evidence.\n`period_analytics` includes ISO weeks, Gregorian months, and calendar years, partially filled at the range endpoints. The totals reconcile with daily records. Mean punctuality is the mean among observed session start deltas; snapshots are safe cache projections, not alternative scoring formulas.\n\n## Achievements\nDefinitions use `STREAK_ONLY`, `PROGRESS_ONLY`, or `HYBRID`. Daily evidence is `daily_confirmed`; numerical progress uses `progress_delta`. `record_gap_days` here is current consecutive days minus best days, and can be negative. Progress may exceed its target (unclamped evidence); `progress_percent` is clamped visually at 100. This does not redefine FocusDeck's production achievement rules.\n\n## Index\nTopic IDs are stable and hierarchy is synthetic. A `topic_completed` event inserts its topic into the current complete set; `topic_reopened` removes it. Topic titles deliberately say `Synthetic practice topic`; they are *not* copied from your original 126-topic PTfS study index. Index snapshot should equal replayed event state.\n\n## Security\nNo token, password, QR pairing secret, real hostname, ADB serial, device ID, user location or account data is included. Do not expose any dev-fixture export as user records or route it to real device synchronization.\n\n## Recommended Kotlin adapter\nUse lazy streaming/storage and map viewmodels with the existing `VisualTestDataProvider` / QA interfaces. Keep strong ownership and a single run-level source switch. This pack is designed for Codex to **load and map** rather than author 3 years of data from scratch.\n'''
(ROOT/'schema_notes.md').write_text(schema,encoding='utf8')

# Verify external package checksums and core numerical invariants without using app state.
verify='''#!/usr/bin/env python3\n"""Read-only validator for FocusDeck synthetic fixture archive directory."""\nfrom pathlib import Path\nfrom collections import defaultdict\nimport json,hashlib,sys\np=Path(sys.argv[1] if len(sys.argv)>1 else Path(__file__).resolve().parent)\nm=json.loads((p/'manifest.json').read_text())\nassert m['synthetic'] and m['no_production_import'] and m['no_sync'] and m['no_network']\nfor file in m['files']:\n    raw=(p/file['path']).read_bytes()\n    assert len(raw)==file['bytes'] and hashlib.sha256(raw).hexdigest()==file['sha256'],file['path']\ndef jl(name):\n    with (p/name).open(encoding='utf8') as fh:\n        return [json.loads(s) for s in fh if s.strip()]\nsessions=jl('sessions.jsonl');blocks=jl('blocks.jsonl');days=jl('daily_analytics.jsonl');periods=jl('period_analytics.jsonl')\nby=defaultdict(list)\nfor b in blocks:by[b['session_id']].append(b)\nassert len(sessions)==m['counts']['sessions']\nassert len(blocks)==m['counts']['blocks']\nfor s in sessions:\n    assert s['focus_seconds']==sum(x['active_focus_seconds'] for x in by[s['id']])\n    assert s['done_count']==sum(x['status']=='DONE' for x in by[s['id']])\nfor d in days:\n    assert sum(d['focus_by_hour_seconds'])==d['focus_seconds']\n    assert sum(d['focus_by_8h_bin_seconds'])==d['focus_seconds']\nassert sum(s['focus_seconds'] for s in sessions)==sum(d['focus_seconds'] for d in days)\nfor kind in ['week','month','year']:\n    assert sum(x['focus_seconds'] for x in periods if x['period']==kind)==sum(d['focus_seconds'] for d in days)\nix=jl('index_events.jsonl'); defs=json.loads((p/'index_definitions.json').read_text());snap=json.loads((p/'index_snapshots.json').read_text())\nfor item in defs:\n    completed=set()\n    for ev in ix:\n        if ev['index_id']!=item['id']:continue\n        if ev['action']=='topic_completed':completed.add(ev['topic_id'])\n        else:completed.remove(ev['topic_id'])\n    assert len(completed)==next(s['completed_topics'] for s in snap if s['id']==item['id'])\nprint('VALID — checksums, safety flags, referential counts, daily/hourly/monthly/weekly/yearly focus totals, block completion, and Index replay.')\nprint(json.dumps(m['counts'],indent=2))\n'''
(ROOT/'validate_fixtures.py').write_text(verify,encoding='utf8')
# Add readme/docs validator hashes to the self-consistent manifest.
files=[]
for f in sorted(ROOT.iterdir()):
    if f.is_file() and f.name!='manifest.json':
        raw=f.read_bytes();files.append(dict(path=f.name,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()))
manifest['files']=files
jd('manifest.json',manifest)

zipfile_path=ROOT.parent / f'FocusDeck_DeveloperMode_3Years_v1.22.0_seed{SEED}.zip'
with zipfile.ZipFile(zipfile_path,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=8) as z:
    for f in sorted(ROOT.iterdir()):
        if f.is_file(): z.write(f,arcname='FocusDeck_DeveloperMode_3Years/'+f.name)
print('DONE',zipfile_path,zipfile_path.stat().st_size)
print('COUNTS',json.dumps(COUNTS))
print('SNAPSHOTS',json.dumps(ACH_SNAP[:3]))
