"""GPT-controlled thresholds and weighted execution outcomes. No completion gate UI."""
from .rewards import obj, number, actions, rounded, PHYSICAL, KINDS, boolean

CLASS_DEFAULTS = dict(strong_max_actual_ratio=.90, on_target_max_actual_ratio=1.05, slightly_over_max_actual_ratio=1.25, strong_min_focus_ratio=80.0)
COEFFICIENTS = dict(strong=1.0, on_target=.75, slightly_over=.20, over_target=-.40)
ELIGIBILITY = dict(minimum_strong_blocks=0, minimum_on_or_above_target_blocks=0, maximum_over_target_blocks=100,
                   minimum_focus_ratio=0.0, minimum_average_focus_ratio=0.0, minimum_beat_score=0.0, minimum_session_score=0.0, minimum_completed_blocks=0, require_all_focus_completed=False)
POLICY_FIELDS = {'enabled','tiers','selection','eligibility'}
TIER_FIELDS = {'min_beat_score','max_beat_score','min_session_score','max_session_score','min_focus_ratio','min_pace_index','condition','reward','alternatives','block_ids','min_completed_blocks'}


def typed(value, session=False):
    obj(value, {'type','text','amount','duration_seconds','duration_minutes','actions'})
    kind = value.get('type', 'text')
    if kind == 'text':
        from .common import text_field
        text_field(value.get('text'), 'reward text', 1000)
        if any(value.get(k) is not None for k in ('amount','duration_seconds','duration_minutes')) or value.get('actions', []) != []:
            raise ValueError('Text rewards contain only text, not typed activity measurements')
        return [dict(kind='text', reps=0, seconds=0, text=value['text'])]
    if value.get('text') is not None:
        raise ValueError('Use text or a legacy typed activity, not both')
    if kind == 'combined':
        children=value.get('actions', [])
        if any(value.get(k) is not None for k in ('amount','duration_seconds','duration_minutes')) or not isinstance(children,list) or not 1<=len(children)<=3 or any(not isinstance(a,dict) or a.get('type')=='combined' for a in children):
            raise ValueError('Invalid combined reward')
        output=[a for child in children for a in typed(child,session)]
    else:
        if not isinstance(kind,str) or kind not in (KINDS if session else PHYSICAL) or value.get('actions',[]) or (value.get('duration_seconds') is not None and value.get('duration_minutes') is not None):
            raise ValueError('Unsupported reward type or nested actions')
        minutes=value.get('duration_minutes')
        if minutes is not None:
            from .common import integer
            integer(minutes,'duration_minutes',1,15)
        seconds=value.get('duration_seconds')
        if seconds is None: seconds=minutes*60 if minutes is not None else 60 if kind in {'pushups','squats'} else 0
        from .common import integer
        amount=value.get('amount')
        if amount is not None:integer(amount,'amount',0,20)
        output=[dict(kind=kind,reps=0 if amount is None else amount,seconds=seconds)]
    actions(output,session)
    return output


def classification(value=None):
    obj({} if value is None else value, CLASS_DEFAULTS)
    p=CLASS_DEFAULTS | ({} if value is None else value)
    number(p['strong_max_actual_ratio'],.5,1);number(p['on_target_max_actual_ratio'],1,1.2)
    number(p['slightly_over_max_actual_ratio'],p['on_target_max_actual_ratio'],2)
    number(p['strong_min_focus_ratio'],0,100)
    return p


def coefficients(value=None):
    obj({} if value is None else value, {'coefficients'})
    output=(value or {}).get('coefficients',COEFFICIENTS)
    obj(output,COEFFICIENTS,COEFFICIENTS)
    for v in output.values():number(v,-1,1)
    values=[output[k] for k in COEFFICIENTS]
    if values[0]<=0 or any(a<b for a,b in zip(values,values[1:])):raise ValueError('Outcome coefficients must be ordered with positive strong maximum')
    return output


def policy(value,session=False,strategy=None):
    if value is None:return
    from .common import integer
    obj(value,POLICY_FIELDS,{'enabled'});boolean(value['enabled'])
    tiers=value.get('tiers',[])
    if not isinstance(tiers,list) or len(tiers)>20 or (value['enabled'] and not tiers):raise ValueError('Enabled reward policy requires 1..20 tiers')
    if value.get('selection','highest_satisfied') not in {'highest_satisfied','first_satisfied'}:raise ValueError('Unsupported reward selection rule')
    eligibility=value.get('eligibility',{})
    obj(eligibility,ELIGIBILITY)
    e=ELIGIBILITY|eligibility
    for k in ('minimum_strong_blocks','minimum_on_or_above_target_blocks','maximum_over_target_blocks'):integer(e[k],k,0,100)
    integer(e['minimum_completed_blocks'],'minimum_completed_blocks',0,100)
    boolean(e['require_all_focus_completed'])
    for k in ELIGIBILITY.keys()-{'minimum_strong_blocks','minimum_on_or_above_target_blocks','maximum_over_target_blocks','minimum_completed_blocks','require_all_focus_completed'}:number(e[k],0,111 if k=='minimum_beat_score' else 100)
    if not session and e!=ELIGIBILITY:raise ValueError('Eligibility counts apply only to session rewards')
    for t in tiers:
        obj(t,TIER_FIELDS,{'reward'})
        for k,hi in [('min_beat_score',111),('max_beat_score',111),('min_session_score',100),('max_session_score',100),('min_focus_ratio',100),('min_pace_index',120)]:
            if t.get(k) is not None:number(t[k],0,hi)
        for metric in ('beat_score','session_score'):
            if t.get('min_'+metric) is not None and t.get('max_'+metric) is not None and t['min_'+metric]>t['max_'+metric]:
                raise ValueError('Reward range minimum exceeds maximum')
        if not session and any(t.get(k) is not None for k in ('min_session_score','max_session_score')) or session and t.get('min_pace_index') is not None:raise ValueError('Wrong block/session threshold')
        if t.get('condition') not in (None,'personal_best','completed','module_completed','session_completed'):raise ValueError('Unsupported reward condition')
        if t.get('condition')=='session_completed' and not session:raise ValueError('session_completed applies only to session rewards')
        if t.get('min_completed_blocks') is not None:
            if not session:raise ValueError('min_completed_blocks applies only to session rewards')
            integer(t['min_completed_blocks'],'min_completed_blocks',1,100)
        ids=t.get('block_ids',[])
        if not isinstance(ids,list) or any(not isinstance(x,str) or not x.strip() or len(x)>100 for x in ids) or len(set(ids))!=len(ids) or (not 1<=len(ids)<=100 if t.get('condition')=='module_completed' else bool(ids)):
            raise ValueError('module_completed requires unique block_ids; other conditions cannot have block_ids')
        alternatives=t.get('alternatives',[])
        if not isinstance(alternatives,list) or len(alternatives)>4:raise ValueError('At most four reward alternatives')
        for reward in [t['reward']]+alternatives:
            output=typed(reward,session)
            if strategy is not None and any(a['kind'] != 'text' and (a['kind'] not in strategy.get('allowed_rewards',KINDS) or a['kind'] in strategy.get('forbidden_rewards',[])) for a in output):raise ValueError('Conditional reward not permitted by strategy')


def classify(block,performance,config=None):
    p=classification(config);ratio=block['active_ms']/block['planned_ms'] if block['planned_ms'] else float('inf')
    if ratio<=p['strong_max_actual_ratio'] and (performance.get('focus_ratio') or 0)>=p['strong_min_focus_ratio'] and not performance['recalibration_needed']:return 'strong'
    if ratio<=p['on_target_max_actual_ratio']:return 'on_target'
    if ratio<=p['slightly_over_max_actual_ratio']:return 'slightly_over'
    return 'over_target'


def weighted(blocks,config=None,untimed=False):
    co=coefficients(config)
    done=[b['performance'] for b in blocks if b.get('timer_mode','countdown')=='countdown' and b['type']=='focus' and b['outcome']=='done' and (b.get('performance') or {}).get('performance_class') is not None]
    weight=sum(p.get('session_weight',1.0) for p in done)
    points=sum(p.get('session_weight',1.0)*co[p['performance_class']] for p in done)
    focus=[p['focus_ratio'] for p in done if p.get('focus_ratio') is not None]
    return dict(raw_weighted_points=points,total_weight=weight,normalized_session_score=rounded(max(0,min(100,points/(weight*co['strong'])*100))) if weight else None if untimed else 0.0,
                class_counts={k:sum(p['performance_class']==k for p in done) for k in co},average_focus_ratio=rounded(sum(focus)/len(focus)) if focus else None,scoring={'coefficients':co})


def eligible_indices(value, beat, pace, focus, pb, summary=None, context=None):
    if not value['enabled']: return []
    context=context or dict(completed=False,completed_ids=set(),all_focus_completed=False,session_completed=False)
    e=ELIGIBILITY|value.get('eligibility',{})
    def lower(v,t):return t is None or v is not None and v>=t
    def upper(v,t):return t is None or v is not None and v<=t
    # Countdown historically accepted unknown metrics against an explicit zero minimum.
    tier_focus = focus if focus is not None or beat is None else 0.0
    tier_pace = pace if pace is not None or beat is None else 0.0
    if summary is not None and (summary['class_counts']['strong']<e['minimum_strong_blocks'] or summary['class_counts']['strong']+summary['class_counts']['on_target']<e['minimum_on_or_above_target_blocks'] or summary['class_counts']['over_target']>e['maximum_over_target_blocks'] or (focus or 0)<e['minimum_focus_ratio'] or (summary['average_focus_ratio'] or 0)<e['minimum_average_focus_ratio'] or e['minimum_beat_score']>0 and not lower(beat,e['minimum_beat_score']) or e['minimum_session_score']>0 and not lower(summary['normalized_session_score'],e['minimum_session_score']) or len(context['completed_ids'])<e['minimum_completed_blocks'] or e['require_all_focus_completed'] and not context['all_focus_completed']):return []
    def condition(t):
        c=t.get('condition')
        return c is None or (c=='personal_best' and pb) or (c=='completed' and context['completed']) or (c=='module_completed' and context['completed'] and set(t['block_ids'])<=context['completed_ids']) or (c=='session_completed' and context['session_completed'])
    matches=[i for i,t in enumerate(value.get('tiers',[])) if lower(beat,t.get('min_beat_score')) and upper(beat,t.get('max_beat_score')) and lower((summary or {}).get('normalized_session_score'),t.get('min_session_score')) and upper((summary or {}).get('normalized_session_score'),t.get('max_session_score')) and lower(tier_focus,t.get('min_focus_ratio')) and lower(tier_pace,t.get('min_pace_index')) and (t.get('min_completed_blocks') is None or len(context['completed_ids'])>=t['min_completed_blocks']) and condition(t)]
    if value.get('selection','highest_satisfied')=='first_satisfied':return matches
    def priority(i):
        t=value['tiers'][i]
        return (t.get('condition')=='personal_best',t.get('min_session_score' if summary is not None else 'min_beat_score') or 0,t.get('min_beat_score') or 0,(t.get('min_focus_ratio') or 0) if beat is None or t.get('condition') in {'completed','module_completed','session_completed'} else 0,t.get('min_completed_blocks') or 0,i)
    return sorted(matches,key=priority,reverse=True)


def validate_report(report):
    from .rewards import match
    from .stopwatch import completion_context, completion_policy
    for index,b in enumerate(report['blocks']):
        p=b.get('performance')
        if not p:continue
        number(p.get('session_weight',1),.01,10)
        policy(p.get('reward_policy'))
        completion_policy(p.get('reward_policy'),report.get('report_version',1), b.get('timer_mode')=='stopwatch',prefix={x['id'] for x in report['blocks'][:index+1] if x['type']=='focus'})
        if p.get('performance_class') is not None:
            if not isinstance(p.get('classification'),dict):raise ValueError('Execution class requires saved classification policy')
            if p['quality']!='confirmed' or p['performance_class']!=classify(b,p,p.get('classification')):raise ValueError('Invalid execution class')
        r=p['reward']
        if p.get('reward_policy') is not None and r['actions']:
            tier_index=int(r['tier'].removeprefix('tier_'))-1
            if tier_index not in eligible_indices(p['reward_policy'],p['beat_score'],None if p['recalibration_needed'] else p.get('pace_index'),p.get('focus_ratio'),p['new_personal_best'],context=completion_context(report,index)):raise ValueError('Reward threshold not satisfied')
    c=report.get('competition')
    if c is None:return
    for b in report['blocks']:
        if b.get('performance'):policy(b['performance'].get('reward_policy'),False,c['reward_strategy'])
    w=c.get('weighted');policy(c.get('session_reward_policy'),True,c['reward_strategy'])
    completion_policy(c.get('session_reward_policy'),report.get('report_version',1),session=True)
    if c.get('session_reward_policy') is not None and w is None:raise ValueError('Conditional session reward requires weighted metrics')
    if w is not None:
        if any(b.get('timer_mode','countdown')=='countdown' and b['type']=='focus' and b['outcome']=='done' and not (b.get('performance') or {}).get('performance_class') for b in report['blocks']):raise ValueError('Weighted report requires all DONE block execution classes')
        obj(w,{'raw_weighted_points','total_weight','normalized_session_score','class_counts','average_focus_ratio','scoring'},{'raw_weighted_points','total_weight','normalized_session_score','class_counts','average_focus_ratio','scoring'})
        from .common import integer
        if not isinstance(w['scoring'],dict):raise ValueError('Weighted scoring must be an object')
        obj(w['class_counts'],COEFFICIENTS,COEFFICIENTS)
        for k,v in w['class_counts'].items():integer(v,k,0,100)
        expected=weighted(report['blocks'],w['scoring'],report.get('planned_stopwatch_focus_blocks',0)>0)
        for k in ('raw_weighted_points','total_weight','normalized_session_score','average_focus_ratio'):match(w[k],expected[k])
        if w['class_counts']!=expected['class_counts']:raise ValueError('Weighted counts differ from block evidence')
        co=c.get('session_reward_policy');r=c['reward']
        if co is not None and r['actions']:
            index=int(r['tier'].removeprefix('tier_'))-1
            if index not in eligible_indices(co,c['beat_score'],c.get('average_pace'),c.get('focus_ratio'),c['new_personal_best'],w,completion_context(report)):raise ValueError('Session reward eligibility not satisfied')
