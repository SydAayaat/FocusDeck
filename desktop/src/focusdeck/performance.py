"""Deterministic factual performance projections; no policy/reward changes or storage.

Weights: completion .60, punctuality .25, estimation .15. Missing components
are omitted and remaining weights normalized. Late credit reaches zero at 15m.
All components use eligible captured planned active duration as their weight.
"""
from __future__ import annotations
import datetime as dt
import math
import time
from zoneinfo import ZoneInfo
from .analytics import instant, local, split

POLICY = ('100 × C × (0.60 + 0.25P + 0.15E) / available component weights. '
          'C = DONE planned milliseconds / eligible planned milliseconds. '
          'P = weighted max(0, 1 − max(0, actual start − planned start)/15 minutes). '
          'E = weighted max(0, 1 − abs(actual active − planned active)/planned active) for closed DONE focus blocks. '
          'Missing P/E omitted with weights normalized; coverage disclosed. Future starts excluded. '
          'Stopwatch and unknown/zero planned durations excluded. BeatScore remains independent.')

def estimation(planned, actual):
    if planned is None or planned <= 0 or actual is None: return None
    delta=actual-planned
    return dict(planned_ms=planned,actual_ms=actual,signed_error_ms=delta,absolute_error_ms=abs(delta),
                percentage_error=100*delta/planned,accuracy=100*max(0,1-abs(delta)/planned),
                interpretation='plan underestimated duration' if delta>0 else 'plan overestimated duration' if delta<0 else 'matched estimate')

def plan_records(activities, now_ms=None):
    now_ms=int(time.time()*1000) if now_ms is None else now_ms
    records=[]
    for a in activities.values():
        if a.get('type')!='focus' or a.get('timer_mode')=='stopwatch': continue
        schedule=a.get('schedule');planned=a.get('planned_ms');start=instant(schedule['planned_start']) if schedule else a.get('actual_start')
        z=ZoneInfo((schedule or {}).get('timezone') or a['zone'])
        date=local(start,z).date().isoformat() if start is not None else a.get('plan_date')
        if date is None: continue
        future=start is not None and start>now_ms
        done=a['status']=='done' and a.get('actual_end') is not None and a['actual_end']<=now_ms
        estimate=estimation(planned,a.get('focus_ms')) if done else None
        weights={date:planned}
        if planned and schedule and schedule.get('planned_end'):
            begin,end=instant(schedule['planned_start']),instant(schedule['planned_end'])
            weights={}
            for d,h,x,y,n in split(begin,end,planned,z): weights[d]=weights.get(d,0)+n
        for date,weight in weights.items():
            records.append(dict(id=a['id'],report_key=a.get('report_key'),title=a['title'],project=a.get('project'),category=a['category'],date=date,
                planned_ms=weight,original_planned_ms=planned,actual_ms=a.get('focus_ms'),done=done,eligible=not future,
                provisional=bool(a.get('provisional')) or local(now_ms,z).date().isoformat()<=date,
                start_deviation_ms=a.get('punctuality_ms'),estimation=estimate,missing_plan_blocks=a.get('missing_plan_blocks',0),achievement_id=a.get('achievement_id'),index_topic_id=a.get('index_topic_id'),planned_start_ms=instant(schedule['planned_start']) if schedule else None,planned_end_ms=instant(schedule['planned_end']) if schedule and schedule.get('planned_end') else None,actual_start_ms=a.get('actual_start'),actual_end_ms=a.get('actual_end'),status=a['status'],source_zone=a['zone'],paused_ms=a.get('paused_ms'),overtime_ms=a.get('overtime_ms'),pause_count=a.get('pause_count')))
    return records

def score(records):
    rows=[r for r in records if r['eligible']]
    valid=[r for r in rows if r['planned_ms'] is not None and r['planned_ms']>0]
    total=sum(r['planned_ms'] for r in valid);completed=sum(r['planned_ms'] for r in valid if r['done'])
    c=completed/total if total else None
    p_rows=[r for r in valid if r['start_deviation_ms'] is not None]
    e_rows=[r for r in valid if r['estimation'] is not None]
    pw=sum(r['planned_ms'] for r in p_rows);ew=sum(r['planned_ms'] for r in e_rows)
    p=sum(r['planned_ms']*max(0,1-max(0,r['start_deviation_ms'])/900000) for r in p_rows)/pw if pw else None
    e=sum(r['planned_ms']*r['estimation']['accuracy']/100 for r in e_rows)/ew if ew else None
    denominator=.6+(.25 if p is not None else 0)+(.15 if e is not None else 0)
    value=100*c*(.6+(.25*p if p is not None else 0)+(.15*e if e is not None else 0))/denominator if c is not None else None
    gaps={}
    for r in rows: gaps[r.get('report_key')]=max(gaps.get(r.get('report_key'),0),r.get('missing_plan_blocks',0))
    missing=len(rows)-len(valid)+sum(gaps.values())
    return dict(score=value,completion=c,punctuality=p,efficiency=e,eligible_planned_ms=total,completed_planned_ms=completed,
        punctuality_planned_ms=pw,estimation_planned_ms=ew,punctuality_coverage=pw/total if total else None,
        estimation_coverage=ew/total if total else None,missing_duration_blocks=missing,future_blocks=len(records)-len(rows),
        partial=missing>0 or (bool(total) and (pw<total or ew<total)),provisional=any(r['provisional'] for r in rows),
        contributing_ids=sorted({r['id'] for r in valid}),policy=POLICY)

def period_bounds(scope, anchor):
    if isinstance(anchor,str): anchor=dt.date.fromisoformat(anchor)
    if scope=='day': return anchor,anchor
    if scope=='week':
        start=anchor-dt.timedelta(days=anchor.weekday());return start,start+dt.timedelta(days=6)
    if scope=='month':
        start=anchor.replace(day=1);end=(start.replace(year=start.year+1,month=1) if start.month==12 else start.replace(month=start.month+1))-dt.timedelta(days=1);return start,end
    if scope=='year': return anchor.replace(month=1,day=1),anchor.replace(month=12,day=31)
    raise ValueError('Invalid period')

def previous_bounds(scope, begin):
    if scope=='all': return None
    return period_bounds(scope,dt.date.fromisoformat(begin)-dt.timedelta(days=1))

def summary(data, begin, end):
    days=[d for d in data['days'].values() if d['has_record'] and begin<=d['date']<=end]
    rows=[r for r in data.get('performance_records',[]) if begin<=r['date']<=end]
    unique={r['id']:r for r in rows if r['eligible']};estimates=[r for r in unique.values() if r['estimation'] is not None]
    def bias(items):
        return dict(samples=len(items),planned_ms=sum(r['original_planned_ms'] for r in items),actual_ms=sum(r['actual_ms'] for r in items),
            signed_error_ms=sum(r['estimation']['signed_error_ms'] for r in items),absolute_error_ms=sum(r['estimation']['absolute_error_ms'] for r in items),
            accuracy=sum(r['original_planned_ms']*r['estimation']['accuracy'] for r in items)/sum(r['original_planned_ms'] for r in items) if items else None)
    grouped={}
    for row in rows: grouped.setdefault(row['date'],[]).append(row)
    daily={date:score(group) for date,group in grouped.items()}
    valid=[s for s in daily.values() if s['score'] is not None and s['missing_duration_blocks']==0 and not s['provisional']]
    values=[s['score'] for s in valid];mean=sum(values)/len(values) if values else None
    closed=all(end<local(int(time.time()*1000),ZoneInfo(z)).date().isoformat() for z in {z for d in days for z in d['zones']} or {'UTC'})
    length=(dt.date.fromisoformat(end)-dt.date.fromisoformat(begin)).days+1
    return dict(period_closed=closed,calendar_days=length,begin=begin,end=end,execution=score(rows),focus_ms=sum(d['focus_ms'] for d in days),break_ms=sum(d['break_ms'] for d in days),paused_ms=sum(d['paused_ms'] for d in days),
        overtime_ms=sum(d.get('overtime_ms',0) for d in days),overtime_unlocated_ms=sum(d.get('overtime_unlocated_ms',0) for d in days),pause_count=sum(d.get('pause_count',0) for d in days) if days and all(not d['incomplete'] for d in days) else None,
        done_blocks=sum(d['done'] for d in days),recorded_days=len(days),complete_days=sum(d['hour_complete'] for d in days),unlocated_ms=sum(d['unlocated_ms'] for d in days),
        estimation=bias(estimates),estimation_by_category={c:bias([r for r in estimates if r['category']==c]) for c in sorted({r['category'] for r in estimates})},
        estimation_by_project={c:bias([r for r in estimates if r['project']==c]) for c in sorted({r['project'] for r in estimates if r['project'] is not None})},
        start_deviations=[dict(id=r['id'],date=r['date'],deviation_ms=r['start_deviation_ms']) for r in unique.values() if r['start_deviation_ms'] is not None],
        consistency=dict(threshold=80,valid_planned_days=len(valid),planned_days_with_done=sum(s['completed_planned_ms']>0 for s in valid),average_daily_score=mean,
            days_meeting_threshold=sum(v>=80 for v in values),proportion_meeting_threshold=sum(v>=80 for v in values)/len(values) if values else None,
            score_standard_deviation=math.sqrt(sum((v-mean)**2 for v in values)/len(values)) if values else None,excluded_partial_or_provisional_days=len(daily)-len(valid)),daily=daily,contributions=list(unique.values()))

def comparison(current, previous):
    fields=('focus_ms','paused_ms','overtime_ms')
    comparable=bool(current['recorded_days'] and previous['recorded_days'] and current['complete_days']==current['recorded_days'] and previous['complete_days']==previous['recorded_days'] and current['period_closed'] and previous['period_closed'] and current['complete_days']*previous['calendar_days']==previous['complete_days']*current['calendar_days'])
    x,y=current['execution'],previous['execution']
    score_coverage=x['missing_duration_blocks']==y['missing_duration_blocks']==0 and x['punctuality_coverage']==y['punctuality_coverage'] and x['estimation_coverage']==y['estimation_coverage']
    def diff(a,b,allowed=True): return dict(current=a,previous=b,absolute_difference=a-b if a is not None and b is not None else None,
        percentage_change=(a-b)/b*100 if comparable and allowed and a is not None and b not in (None,0) else None)
    return dict(current_bounds=[current['begin'],current['end']],previous_bounds=[previous['begin'],previous['end']],comparable_coverage=comparable,
        **{field:diff(current[field] if current['recorded_days'] else None,previous[field] if previous['recorded_days'] else None) for field in fields},execution_score=diff(current['execution']['score'],previous['execution']['score'],score_coverage),
        estimation_accuracy=diff(current['estimation']['accuracy'],previous['estimation']['accuracy']),consistency=diff(current['consistency']['average_daily_score'],previous['consistency']['average_daily_score'],score_coverage))

def performance_report(data, scope, begin, end):
    current=summary(data,begin,end);prev=previous_bounds(scope,begin)
    current['comparison']=comparison(current,summary(data,*(d.isoformat() for d in prev))) if prev else None
    current.update(scope=scope,narrative_source='deterministic factual summary',policy=POLICY)
    return current
