"""Calendar analytics from canonical reports; no timers, predictions or inferred schedules.

Dates/hours retain each report's recorded IANA zone (legacy zone absent => explicit UTC).
Monotonic counters own duration; civil-clock discontinuities cannot supply hourly evidence.
Legacy/unlocated residual totals stay on the recorded finish date and are labelled.
"""
from __future__ import annotations
import datetime as dt
import copy
import re
from zoneinfo import ZoneInfo
from .timeline import intervals

UTC = dt.timezone.utc
SCHEDULE_FIELDS = {'planned_start', 'planned_end', 'timezone'}

def instant(value):
    if not isinstance(value, str) or len(value) > 60 or not re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})',value):
        raise ValueError('Scheduled timestamps must be ISO-8601 with an explicit offset')
    try:
        stamp = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
        if stamp.tzinfo is None or 'T' not in value or stamp.timestamp() < 0: raise ValueError()
        return int(stamp.timestamp() * 1000)
    except (ValueError, OverflowError):
        raise ValueError('Scheduled timestamps must be ISO-8601 instants after 1970') from None

def validate_schedule(value):
    if value is None: return
    if not isinstance(value, dict) or set(value) - SCHEDULE_FIELDS or 'planned_start' not in value:
        raise ValueError('schedule requires planned_start and supports planned_end/timezone')
    start = instant(value['planned_start'])
    if value.get('planned_end') is not None and instant(value['planned_end']) <= start:
        raise ValueError('planned_end must be after planned_start')
    if value.get('timezone') is not None:
        if not isinstance(value['timezone'], str) or len(value['timezone']) > 100: raise ValueError('Invalid scheduling timezone')
        try: ZoneInfo(value['timezone'])
        except (ValueError, KeyError): raise ValueError('Use an IANA scheduling timezone') from None

def validate_snapshot(value):
    if value is None: return
    if not isinstance(value, dict) or set(value) != {'session_schedule', 'activities'}: raise ValueError('Invalid schedule snapshot')
    validate_schedule(value['session_schedule'])
    if not isinstance(value['activities'], list) or len(value['activities']) > 100: raise ValueError('Invalid scheduled activities')
    seen = set()
    for b in value['activities']:
        if not isinstance(b, dict) or not {'id','title','type'} <= set(b) or set(b) - {'id', 'title', 'type', 'category', 'achievement_id', 'index_topic_id', 'schedule', 'planned_ms', 'timer_mode'}: raise ValueError('Invalid activity metadata')
        if not isinstance(b.get('id'), str) or not b['id'] or b['id'] in seen: raise ValueError('Invalid activity id')
        seen.add(b['id']); validate_schedule(b.get('schedule'))
        if b.get('planned_ms') is not None and (type(b['planned_ms']) is not int or not 0 <= b['planned_ms'] <= 10**12): raise ValueError('Invalid captured planned duration')
        if b.get('timer_mode') is not None and b['timer_mode'] not in {'countdown','stopwatch'}: raise ValueError('Invalid captured timer mode')
        if b.get('timer_mode') == 'stopwatch' and b.get('planned_ms') is not None: raise ValueError('Stopwatch has no planned duration')
        for k in ('id', 'title', 'category', 'achievement_id', 'index_topic_id'):
            if b.get(k) is not None and (not isinstance(b[k], str) or not 1 <= len(b[k]) <= 200): raise ValueError('Invalid activity '+k)
        if b.get('type') not in {'focus', 'break'}: raise ValueError('Invalid activity type')

def snapshot(plan):
    return copy.deepcopy(dict(session_schedule=plan.get('schedule'), activities=[{k: b[k] for k in
        ('id', 'title', 'type', 'category', 'achievement_id', 'index_topic_id', 'schedule') if k in b} | dict(planned_ms=b.get('duration_seconds')*1000 if b.get('duration_seconds') is not None else None,timer_mode=b.get('timer_mode','countdown')) for b in plan['blocks']]))

def punctuality(schedule, actual):
    return actual - instant(schedule['planned_start']) if schedule and actual is not None else None

def zone_for(report):
    try: return ZoneInfo(report.get('recorded_timezone') or (report.get('schedule_snapshot') or {}).get('session_schedule', {}).get('timezone') or 'UTC')
    except (ValueError, KeyError, AttributeError): return ZoneInfo('UTC')

def local(ms, zone): return dt.datetime.fromtimestamp(ms / 1000, UTC).astimezone(zone)
def millis(stamp): return int(stamp.timestamp() * 1000)
def bounds(day, zone):
    return millis(dt.datetime.combine(day, dt.time(), zone)), millis(dt.datetime.combine(day + dt.timedelta(days=1), dt.time(), zone))

def ticks(day, zone):
    start, end = bounds(day, zone)
    return [dict(label=f'{h:02d}:00', fraction=(millis(dt.datetime.combine(day, dt.time(h), zone))-start)/(end-start)) for h in (0,6,12,18)] + [dict(label='24:00', fraction=1)]

def split(start, end, duration, zone):
    """Split by real hourly boundaries in UTC, including both repeated autumn hours."""
    if end <= start or duration <= 0: return []
    output, cursor, allocated = [], start, 0
    while cursor < end:
        at = local(cursor, zone)
        # The next local hour boundary as an absolute instant: add elapsed time, not civil hours.
        next_hour = cursor + (3600-at.minute*60-at.second)*1000 - at.microsecond//1000
        _, midnight = bounds(at.date(), zone)
        stop = min(end, next_hour, midnight)
        if stop <= cursor: stop = min(end, cursor + 3600000)
        cumulative = duration * (stop-start) // (end-start)
        amount = cumulative - allocated
        output.append((at.date().isoformat(), at.hour, cursor, stop, amount))
        cursor, allocated = stop, cumulative
    return output

def observed_report(state):
    plan=state.get('session')
    if not plan or not state.get('startedAtMs') or state.get('phase') in {'idle','complete','stopped'}: return None
    # Native serialized state uses camelCase except fields explicitly SerialName'd in results/events.
    result=list(state.get('results',[])); index=state.get('index',0)
    if state.get('phase') in {'active','paused'} and index<len(plan['blocks']):
        b=plan['blocks'][index];active=state.get('activeMs',0);paused=state.get('pausedMs',0)
        result.append(dict(id=b['id'],title=b['title'],type=b['type'],active_ms=active,paused_ms=paused,
            started_at_ms=state.get('blockStartedAtMs'),finished_at_ms=state.get('anchorWall'),outcome='In progress',
            planned_ms=b.get('duration_seconds',0)*1000 if b.get('duration_seconds') is not None else None,
            wall_ms=active+paused,budget_ms=state.get('budgetMs'),overtime_ms=None))
    events=list(state.get('events',[]))
    if events and result and state.get('anchorWall') is not None:
        previous=events[-1]
        events.append(dict(previous,seq=previous['seq']+1,kind='observation',at_ms=state['anchorWall'],mono_ms=state.get('anchorMono',previous['mono_ms']),
            block_index=index,phase=state['phase'],active_ms=state.get('activeMs',0),paused_ms=state.get('pausedMs',0)))
    return dict(session_id=plan['session_id'],title=plan['title'],started_at_ms=state['startedAtMs'],finished_at_ms=state.get('anchorWall',state['startedAtMs']),
        focused_ms=sum(b['active_ms'] for b in result if b['type']=='focus'),break_ms=sum(b['active_ms'] for b in result if b['type']=='break'),
        paused_ms=sum(b['paused_ms'] for b in result),blocks=result,events=events,recorded_timezone=state.get('recordedTimezone'),schedule_snapshot=snapshot(plan),observational=True)

def dataset(reports, live_state=None):
    days, seen, activities = {}, set(), {}
    def day(key, zone):
        if key not in days:
            d = dt.date.fromisoformat(key); a,b = bounds(d, zone)
            days[key] = dict(date=key, zones=[], zone_axes={}, has_record=False, incomplete=False, session_focus={}, start_ms=a, end_ms=b, ticks=ticks(d, zone), focus_ms=0, break_ms=0,
                paused_ms=0, other_ms=0, unlocated_ms=0, done=0, session_keys=[], punctuality=[], session_punctuality=[],
                hours=[0]*24, hour_breaks=[0]*24, hour_pauses=[0]*24, hour_activities=[[] for _ in range(24)], overtime_ms=0, pause_count=0, overtime_unlocated_ms=0, hour_coverage=False, segments=[], unscheduled=[])
        out = days[key]
        if zone.key not in out['zones']:
            out['zones'].append(zone.key)
            date=dt.date.fromisoformat(key);a,b=bounds(date,zone)
            out['zone_axes'][zone.key]=dict(start_ms=a,end_ms=b,ticks=ticks(date,zone))
        return out
    observed=observed_report(live_state or {})
    sources=list(reports)+([observed] if observed and not any(r['session_id']==observed['session_id'] and r['started_at_ms']==observed['started_at_ms'] for r in reports) else [])
    for r in sources:
        key = str(r['session_id']) + ':' + str(r['started_at_ms'])
        if key in seen: continue
        seen.add(key); zone = zone_for(r); finish = local(r['finished_at_ms'],zone).date().isoformat()
        metadata = r.get('schedule_snapshot') or dict(session_schedule=None,activities=[])
        meta = {b['id']: b for b in metadata['activities']}
        captured=max(len(r['blocks']),len(meta))
        missing_plan=max(0,(r.get('planned_block_count') or captured)-captured)
        if not missing_plan and r.get('planned_ms') is not None and r['planned_ms']>sum(b.get('planned_ms') or 0 for b in r['blocks']) and not meta: missing_plan=1
        spans = intervals(r)
        remaining = dict(focus_ms=r.get('focused_ms',0),break_ms=r.get('break_ms',0),paused_ms=r.get('paused_ms',0))
        startday = day(local(r['started_at_ms'],zone).date().isoformat(),zone);startday['has_record']=True;startday['session_keys'].append(key);startday['incomplete']=startday['incomplete'] or r.get('timeline_coverage')!='complete' or bool(r.get('observational'))
        sp = punctuality(metadata.get('session_schedule'),r['started_at_ms'])
        if sp is not None: startday['session_punctuality'].append(sp)
        for i,b in enumerate(r['blocks']):
            m = meta.get(b['id'],{}); aid = key + ':' + b['id']; schedule = m.get('schedule')
            p = punctuality(schedule,b.get('started_at_ms'))
            a = dict(missing_plan_blocks=missing_plan if i==0 else 0,type=b['type'],timer_mode=b.get('timer_mode','countdown'),provisional=bool(r.get('observational')),plan_date=local(r['started_at_ms'],zone).date().isoformat(),project=m.get('achievement_id') or r['title'],id=aid,report_key=key,block_id=b['id'],title=b['title'],category=m.get('category') or b['type'],
                achievement_id=m.get('achievement_id'),index_topic_id=m.get('index_topic_id'),schedule=schedule,
                actual_start=b.get('started_at_ms'),actual_end=None if r.get('observational') and b['outcome']=='In progress' else b.get('finished_at_ms'),focus_ms=b.get('active_ms') if b['type']=='focus' else None,
                pause_count=sum(1 for e in r.get('events',[]) if e['kind']=='paused' and e['block_index']==i) if r.get('timeline_coverage')=='complete' else None,elapsed_ms=b.get('wall_ms',b.get('active_ms',0)+b.get('paused_ms',0)),paused_ms=b.get('paused_ms'),
                overtime_ms=b.get('overtime_ms',max(0,b['active_ms']-b['budget_ms']) if b.get('budget_ms') is not None and b['type']=='focus' else None),planned_ms=b.get('planned_ms'),punctuality_ms=p,status=b['outcome'],zone=zone.key)
            activities[aid]=a
            actualday=day(local(b['started_at_ms'],zone).date().isoformat(),zone);actualday['has_record']=True
            actualday['incomplete']=actualday['incomplete'] or r.get('timeline_coverage')!='complete' or bool(r.get('observational'))
            if key not in actualday['session_keys']:actualday['session_keys'].append(key)
            if p is not None: day(local(b['started_at_ms'],zone).date().isoformat(),zone)['punctuality'].append(p)
            if b['outcome']=='done':
                finished=day(local(b['finished_at_ms'],zone).date().isoformat(),zone);finished['done']+=1;finished['has_record']=True;finished['incomplete']=finished['incomplete'] or r.get('timeline_coverage')!='complete'
            if not schedule: day(local(b['started_at_ms'],zone).date().isoformat(),zone)['unscheduled'].append(aid)
        # Include unstarted planned activities without inventing actual timestamps.
        for m in metadata['activities']:
            aid=key+':'+m['id']
            if aid not in activities:
                activities[aid]=dict(type=m['type'],timer_mode=m.get('timer_mode'),provisional=bool(r.get('observational')),plan_date=local(r['started_at_ms'],zone).date().isoformat(),project=m.get('achievement_id') or r['title'],pause_count=None,id=aid,report_key=key,block_id=m['id'],title=m['title'],category=m.get('category') or m['type'],
                    achievement_id=m.get('achievement_id'),index_topic_id=m.get('index_topic_id'),schedule=m.get('schedule'),
                    actual_start=None,actual_end=None,focus_ms=None,elapsed_ms=None,paused_ms=None,overtime_ms=None,planned_ms=m.get('planned_ms'),
                    punctuality_ms=None,status='Not started',zone=zone.key)
        for m in metadata['activities']:
            schedule=m.get('schedule')
            if not schedule or not schedule.get('planned_end'): continue
            planned_zone = ZoneInfo(schedule.get('timezone') or zone.key)
            begin,end=instant(schedule['planned_start']),instant(schedule['planned_end'])
            for date,h,a,b,amount in split(begin,end,end-begin,planned_zone):
                d=day(date,planned_zone); ds,de=bounds(dt.date.fromisoformat(date),planned_zone)
                d['segments'].append(dict(activity_id=key+':'+m['id'],track='planned',kind=m['type'],start_ms=a,end_ms=b,
                    left=(a-ds)/(de-ds),right=(b-ds)/(de-ds),zone=planned_zone.key))
        overtime_remaining={b['id']: min(b.get('overtime_ms',max(0,b['active_ms']-b['budget_ms']) if b.get('budget_ms') is not None else 0) or 0,b['active_ms']) if b['type']=='focus' else 0 for b in r['blocks']}
        for event in r.get('events',[]):
            if event['kind']=='paused': day(local(event['at_ms'],zone).date().isoformat(),zone)['pause_count']+=1
        for span in spans:
            block = r['blocks'][span['block_index']] if span['block_index']<len(r['blocks']) else None
            field = ('focus_ms' if block and block['type']=='focus' else 'break_ms') if span['kind']=='active' else 'paused_ms' if span['kind']=='paused' else 'other_ms'
            amount=span['duration_ms']; begin,end=span['started_at_ms'],span['finished_at_ms']
            before=next((e['active_ms'] for e in r.get('events',[]) if e['seq']==span['start_seq']),0) if block else 0
            # Clock jumps and reboot gaps are never used to invent clock-hour activity.
            if end<=begin or abs(end-begin-amount)>1000: continue
            if field in remaining: amount=min(amount,remaining[field]); remaining[field]-=amount
            ot=0
            if field=='focus_ms' and block:
                threshold=block['active_ms']-(block.get('overtime_ms',max(0,block['active_ms']-block['budget_ms']) if block.get('budget_ms') is not None else 0) or 0)
                ot=min(amount,max(0,before+amount-max(before,threshold)),overtime_remaining[block['id']])
                overtime_remaining[block['id']]-=ot
            ot_parts={}
            if ot:
                ot_start=end-(end-begin)*ot//max(1,amount)
                for date,h,a,b,n in split(ot_start,end,ot,zone): ot_parts[date]=ot_parts.get(date,0)+n
            for date,n in ot_parts.items(): day(date,zone)['overtime_ms']+=n
            for date,h,a,b,n in split(begin,end,amount,zone):
                d=day(date,zone); ds,de=bounds(dt.date.fromisoformat(date),zone);d[field]+=n
                if key not in d['session_keys']: d['session_keys'].append(key)
                if field=='focus_ms': d['hours'][h]+=n
                if field=='break_ms': d['hour_breaks'][h]+=n
                if field=='paused_ms': d['hour_pauses'][h]+=n
                if block and key+':'+block['id'] not in d['hour_activities'][h]: d['hour_activities'][h].append(key+':'+block['id'])
                d['hour_coverage']=True;d['has_record']=True;d['incomplete']=d['incomplete'] or r.get('timeline_coverage')!='complete' or bool(r.get('observational'))
                if field=='focus_ms':d['session_focus'][key]=d['session_focus'].get(key,0)+n
                if block: d['segments'].append(dict(activity_id=key+':'+block['id'],track='actual',kind=field,start_ms=a,end_ms=b,
                    left=(a-ds)/(de-ds),right=(b-ds)/(de-ds),zone=zone.key))
        d=day(finish,zone);d['has_record']=True;d['incomplete']=d['incomplete'] or r.get('timeline_coverage')!='complete' or bool(r.get('observational'))
        d['overtime_ms']+=sum(overtime_remaining.values());d['overtime_unlocated_ms']+=sum(overtime_remaining.values())
        d['session_focus'][key]=d['session_focus'].get(key,0)+remaining['focus_ms']
        if key not in d['session_keys']:d['session_keys'].append(key)
        for field,amount in remaining.items():d[field]+=amount;d['unlocated_ms']+=amount
    # Current imported plan is observational only; no duplicated historical attempts.
    if not observed and live_state and live_state.get('session') and live_state.get('phase') not in {'complete','stopped','idle'}:
        plan=live_state['session']; zone=zone_for({'recorded_timezone':live_state.get('recordedTimezone'),'schedule_snapshot':snapshot(plan)})
        for m in snapshot(plan)['activities']:
            s=m.get('schedule');aid='live:'+plan['session_id']+':'+m['id']
            activities[aid]=dict(type=m['type'],timer_mode=m.get('timer_mode'),provisional=True,plan_date=None,project=m.get('achievement_id') or plan['title'],pause_count=None,id=aid,report_key=None,block_id=m['id'],title=m['title'],category=m.get('category') or m['type'],
                achievement_id=m.get('achievement_id'),index_topic_id=m.get('index_topic_id'),schedule=s,actual_start=None,actual_end=None,
                focus_ms=None,elapsed_ms=None,paused_ms=None,overtime_ms=None,planned_ms=m.get('planned_ms'),punctuality_ms=None,status='Planned',zone=zone.key)
            if s and s.get('planned_end'):
                z=ZoneInfo(s.get('timezone') or zone.key);a,b=instant(s['planned_start']),instant(s['planned_end'])
                for date,h,x,y,n in split(a,b,b-a,z):
                    d=day(date,z);ds,de=bounds(dt.date.fromisoformat(date),z)
                    d['segments'].append(dict(activity_id=aid,track='planned',kind=m['type'],start_ms=x,end_ms=y,left=(x-ds)/(de-ds),right=(y-ds)/(de-ds),zone=z.key))
    for d in days.values():
        merged=[]
        for segment in sorted(d['segments'],key=lambda s:(s['track'],s['zone'],s['activity_id'],s['kind'],s['start_ms'])):
            previous=merged[-1] if merged else None
            if previous and all(previous[k]==segment[k] for k in ('track','zone','activity_id','kind')) and previous['end_ms']==segment['start_ms']:
                previous.update(end_ms=segment['end_ms'],right=segment['right'])
            else:merged.append(dict(segment))
        d['segments']=merged
        d['tracked_ms']=d['focus_ms']+d['break_ms']+d['paused_ms']+d['other_ms']
        d['hour_complete']=d['hour_coverage'] and d['unlocated_ms']==0 and not d['incomplete']
    from .performance import plan_records, score
    records=plan_records(activities)
    grouped={}
    for record in records: grouped.setdefault(record['date'],[]).append(record)
    return dict(days=days,activities=activities,performance_records=records,performance_days={k:score(group) for k,group in grouped.items()},policy='Source-zone calendar dates; legacy missing zones use UTC. Unlocated totals use finish date. Hour averages include only fully located days with complete reports; active checkpoints and partial days are excluded. Absent dates are unknown, not zero.')
