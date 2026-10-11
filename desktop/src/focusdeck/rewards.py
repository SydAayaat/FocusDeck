"""Typed reward contracts and independently checked deterministic score evidence."""
from __future__ import annotations
import datetime as dt
import math
from .common import integer, text_field

PHYSICAL = {'pushups', 'squats', 'plank', 'mobility', 'walk', 'stretching'}
KINDS = PHYSICAL | {'guitar', 'coffee'}
TIERS = {'completion', 'target', 'strong', 'personal_best', 'exceptional'}
SCORE_DEFAULTS = dict(rank_thresholds=[85.0, 95.0, 105.0, 110.0], target_pace=95.0, strong_pace=110.0,
                      target_focus=80.0, strong_focus=90.0, recalibration_pace=150.0)
RECEIPT_FIELDS = {'tier', 'actions', 'reason', 'acknowledged', 'skipped', 'acknowledged_at_ms', 'local_day', 'daily_reps_before', 'daily_physical_seconds_before'}
PERFORMANCE_FIELDS = {'quality', 'quality_criteria', 'comparison', 'wall_ms', 'pace_index', 'focus_ratio', 'beat_score', 'rank', 'recalibration_needed', 'previous_benchmark', 'new_personal_best', 'pb_delta', 'scoring', 'reward', 'reward_contract', 'reward_policy', 'performance_class', 'session_weight', 'classification'}
COMPETITION_FIELDS = {'comparison_key', 'planned_focus_blocks', 'completed_focus_blocks', 'on_or_ahead_count', 'completion_fraction', 'average_pace', 'focus_ratio', 'beat_score', 'rank', 'previous_benchmark', 'new_personal_best', 'pb_delta', 'reward_strategy', 'session_reward', 'reward', 'weighted', 'session_reward_policy', 'score_scope'}


def obj(value, fields, required=()):
    if not isinstance(value, dict) or set(value) - set(fields) or set(required) - set(value):
        raise ValueError('Invalid reward object fields')
    return value


def number(value, lo=0, hi=10**15):
    if type(value) not in (float, int) or not math.isfinite(value) or not lo <= value <= hi:
        raise ValueError('Invalid finite reward number')
    return value


def boolean(value):
    if type(value) is not bool:
        raise ValueError('Reward flag must be boolean')


def criteria(value):
    if not isinstance(value, list) or len(value) > 10:
        raise ValueError('Invalid quality criteria')
    for item in value:
        text_field(item, 'quality criterion')


def comparison(value):
    if value is None:
        return
    obj(value, {'category', 'complexity', 'method', 'work_units', 'target_revision'}, {'category', 'complexity', 'method', 'work_units', 'target_revision'})
    for key in ('category', 'complexity', 'method', 'target_revision'):
        text_field(value[key], key, 100)
    integer(value['work_units'], 'work_units', 1, 10000)


def actions(value, session=False):
    if not isinstance(value, list) or not 1 <= len(value) <= 3:
        raise ValueError('Reward needs 1..3 actions')
    seen = set()
    for a in value:
        obj(a, {'kind', 'reps', 'seconds', 'text'}, {'kind'})
        kind = a['kind']
        if kind == 'text':
            text_field(a.get('text'), 'reward text', 1000)
            integer(a.get('reps', 0), 'reps', 0, 0); integer(a.get('seconds', 0), 'seconds', 0, 0)
            if len(value) != 1:
                raise ValueError('Text rewards use one complete description')
            continue
        if a.get('text') is not None:
            raise ValueError('Typed action cannot contain free text')
        if not isinstance(kind, str) or kind not in (KINDS if session else PHYSICAL) or kind in seen:
            raise ValueError('Unsupported or repeated reward kind')
        seen.add(kind)
        reps, seconds = a.get('reps', 0), a.get('seconds', 0)
        integer(reps, 'reps', 0, 20)
        integer(seconds, 'seconds', 0, 900)
        if kind in {'pushups', 'squats'}:
            if not 1 <= reps <= 20 or not 30 <= seconds <= 120:
                raise ValueError('Repetition reset requires reps and estimated duration')
        elif kind == 'coffee':
            if reps or seconds:
                raise ValueError('Coffee is a session-only single reward')
        elif reps or not (600 <= seconds <= 900 if kind == 'guitar' else 30 <= seconds <= 120):
            raise ValueError('Invalid reward duration')
    if sum(a.get('seconds', 0) for a in value if a['kind'] in PHYSICAL) > 120:
        raise ValueError('Physical reset exceeds two minutes')


def contract(value, session=False):
    if value is None:
        return
    obj(value, TIERS)
    present = {k for k, v in value.items() if v is not None}
    if not present or present - ({'completion', 'strong', 'exceptional'} if session else {'completion', 'target', 'strong', 'personal_best'}):
        raise ValueError('Invalid reward tiers')
    for option in value.values():
        if option is None:
            continue
        obj(option, {'actions', 'alternatives'}, {'actions'})
        alternatives = option.get('alternatives', [])
        if not isinstance(alternatives, list) or len(alternatives) > 4:
            raise ValueError('At most four reward alternatives')
        for group in [option['actions']] + alternatives:
            actions(group, session)


def scoring(value=None):
    obj({} if value is None else value, SCORE_DEFAULTS)
    p = SCORE_DEFAULTS | ({} if value is None else value)
    ranks = p['rank_thresholds']
    if not isinstance(ranks, list) or len(ranks) != 4 or any(number(v, 1, 111) is None for v in ranks) or any(a >= b for a, b in zip(ranks, ranks[1:])):
        raise ValueError('Invalid ordered rank thresholds')
    number(p['target_pace'], 80, 100); number(p['strong_pace'], 100, 120)
    number(p['target_focus'], 50, 100); number(p['strong_focus'], p['target_focus'], 100)
    number(p['recalibration_pace'], 125, 200)
    return p


def strategy(value):
    if value is None:
        return
    obj(value, {'style', 'allowed_rewards', 'forbidden_rewards', 'default_contract', 'scoring', 'daily_rep_limits', 'daily_physical_seconds', 'max_consecutive_same_activity', 'coffee_reward'})
    if value.get('style', 'physical_competitive') != 'physical_competitive':
        raise ValueError('Unsupported reward strategy')
    allowed = value.get('allowed_rewards', sorted(KINDS))
    forbidden = value.get('forbidden_rewards', ['youtube', 'social_media', 'chess', 'pornography', 'endless_browsing'])
    if not isinstance(allowed, list) or not 1 <= len(allowed) <= 8 or any(not isinstance(v, str) or v not in KINDS for v in allowed) or len(set(allowed)) != len(allowed):
        raise ValueError('Invalid allowed rewards')
    if not isinstance(forbidden, list) or len(forbidden) > 20:
        raise ValueError('Invalid forbidden rewards')
    for item in forbidden:
        text_field(item, 'forbidden reward', 100)
    scoring(value.get('scoring')); contract(value.get('default_contract'))
    limits = value.get('daily_rep_limits', {'pushups': 40, 'squats': 60})
    obj(limits, {'pushups', 'squats'}, {'pushups', 'squats'})
    for v in limits.values():
        integer(v, 'daily rep limit', 0, 100)
    integer(value.get('daily_physical_seconds', 600), 'daily physical seconds', 0, 1200)
    integer(value.get('max_consecutive_same_activity', 2), 'consecutive activity limit', 1, 3)
    coffee = value.get('coffee_reward', {})
    obj(coffee, {'enabled', 'minimum_productive_session_seconds', 'minimum_hours_between_coffee_rewards', 'latest_allowed_local_time'})
    boolean(coffee.get('enabled', False))
    integer(coffee.get('minimum_productive_session_seconds', 10800), 'coffee productive duration', 10800, 86400)
    integer(coffee.get('minimum_hours_between_coffee_rewards', 3), 'coffee spacing', 1, 24)
    stamp = coffee.get('latest_allowed_local_time', '16:00')
    if not isinstance(stamp, str) or len(stamp) != 5 or stamp[2] != ':':
        raise ValueError('Coffee cutoff must be HH:mm')
    try:
        dt.time.fromisoformat(stamp)
    except ValueError:
        raise ValueError('Invalid coffee cutoff') from None


def permitted(contract_value, strategy_value):
    if contract_value is None:
        return
    allowed = strategy_value.get('allowed_rewards', KINDS)
    forbidden = strategy_value.get('forbidden_rewards', [])
    for option in contract_value.values():
        if option:
            for group in [option['actions']] + option.get('alternatives', []):
                if any(a['kind'] not in allowed or a['kind'] in forbidden for a in group):
                    raise ValueError('Reward not permitted by strategy')


def validate_plan_rewards(plan):
    import json
    from .conditional import policy, classification, coefficients
    if any(k in plan and plan[k] is None for k in ('classification','session_scoring')):raise ValueError('Session classification/scoring cannot be null')
    classification(plan.get('classification')); coefficients(plan.get('session_scoring'))
    policy(plan.get('session_reward_policy'), True, plan.get('reward_strategy') or {})
    if plan.get('session_reward_policy') is not None and plan.get('session_reward') is not None: raise ValueError('Use one session reward policy format')
    s = plan.get('reward_strategy'); strategy(s)
    if (s is not None or plan.get('session_reward_policy') is not None or any(b.get('reward_policy') is not None for b in plan['blocks'])) and len(json.dumps(plan, ensure_ascii=False, separators=(',', ':')).encode('utf-8')) > 131072:
        raise ValueError('Reward plan exceeds 128 KiB')
    contract(plan.get('session_reward'), True)
    if plan.get('comparison_key') is not None:
        text_field(plan['comparison_key'], 'comparison_key', 100)
    if plan.get('session_reward') is not None and s is None:
        raise ValueError('session_reward requires reward_strategy')
    for b in plan['blocks']:
        from .common import integer
        number(b.get('session_weight',1),.01,10); classification(b.get('classification')); policy(b.get('reward_policy'), False, s or {})
        if b.get('reward_policy') is not None and b.get('reward_contract') is not None: raise ValueError('Use one block reward policy format')
        criteria(b.get('quality_criteria', [])); comparison(b.get('comparison')); contract(b.get('reward_contract'))
        if b['type'] != 'focus' and (b.get('quality_criteria') or b.get('comparison') or b.get('reward_contract') or b.get('reward_policy') is not None or b.get('classification') is not None or b.get('session_weight',1)!=1):
            raise ValueError('Break blocks cannot have competition contracts')
        if b.get('reward_contract') is not None and s is None:
            raise ValueError('Reward contracts require reward_strategy')
        if s is not None:
            permitted(b.get('reward_contract'), s)
    if s is not None:
        permitted(s.get('default_contract'), s); permitted(plan.get('session_reward'), s)


def rank(score, policy=None):
    return ['C', 'B', 'A', 'S', 'S+'][sum(score >= x for x in scoring(policy)['rank_thresholds'])]


def rounded(value):
    # Match Kotlin round(value * 100) / 100, including halfway ties.
    return round(value * 100.0) / 100.0


def beat_score(pace, focus, confirmed, policy=None):
    if not confirmed or pace is None or focus is None:
        return 0.0
    score = .55 * min(pace, 120) + .25 * focus + 20
    return rounded(min(score, 100) if pace > scoring(policy)['recalibration_pace'] else score)


def match(value, expected):
    if expected is None:
        if value is not None:
            raise ValueError('Expected unknown reward metric')
    elif abs(number(value, -10**15) - expected) > .011:
        raise ValueError('Reward metric does not match timer evidence')


def benchmark(value):
    if value is None:
        return
    obj(value, {'report_key', 'block_id', 'score', 'active_ms'}, {'report_key', 'score', 'active_ms'})
    text_field(value['report_key'], 'benchmark report key', 250)
    if value.get('block_id') is not None:
        text_field(value['block_id'], 'benchmark block id', 100)
    number(value['score'], 0, 111); integer(value['active_ms'], 'benchmark active_ms', 1, 10**12)


def receipt(value, session=False, contract_value=None, policy_value=None):
    obj(value, RECEIPT_FIELDS, {'tier', 'actions', 'acknowledged', 'skipped'})
    if not isinstance(value['tier'], str) or value['tier'] not in TIERS | {'none'} and not __import__('re').fullmatch(r'tier_[1-9][0-9]?',value['tier']):
        raise ValueError('Invalid earned tier')
    if value['actions']:
        actions(value['actions'], session)
        canonical = lambda group: [(a['kind'], a.get('reps', 0), a.get('seconds', 0), a.get('text')) for a in group]
        if policy_value is not None:
            from .conditional import typed, policy
            policy(policy_value,session)
            index=int(value['tier'].removeprefix('tier_'))-1 if value['tier'].startswith('tier_') else -1
            tiers=policy_value.get('tiers',[])
            if not policy_value['enabled'] or not 0<=index<len(tiers) or canonical(value['actions']) not in [canonical(typed(r,session)) for r in [tiers[index]['reward']]+tiers[index].get('alternatives',[])]:raise ValueError('Earned reward not in conditional policy')
        else:
            option = (contract_value or {}).get(value['tier'])
            if not option or canonical(value['actions']) not in [canonical(g) for g in [option['actions']] + option.get('alternatives', [])]:
                raise ValueError('Earned reward not supplied by planner')
    elif value['actions'] != []:
        raise ValueError('Invalid reward actions')
    if not value['actions'] and value['tier'] != 'none':
        raise ValueError('Empty reward must have tier none')
    boolean(value['acknowledged']); boolean(value['skipped'])
    if value['acknowledged'] and (value['skipped'] or not value['actions']):
        raise ValueError('Conflicting acknowledgement')
    if 'reason' in value:
        text_field(value['reason'], 'reward reason', 250)
    stamp = value.get('acknowledged_at_ms'); day = value.get('local_day')
    if stamp is not None:
        integer(stamp, 'reward acknowledgement timestamp', 0, 253402300799999)
        if not isinstance(day, str) or len(day) != 10:
            raise ValueError('Invalid reward local day')
        dt.date.fromisoformat(day)
    elif day is not None or value['acknowledged'] or value['skipped']:
        raise ValueError('Reward acknowledgement needs timestamp and day')
    reps = value.get('daily_reps_before', {})
    obj(reps, {'pushups', 'squats'})
    for v in reps.values():
        integer(v, 'daily reps before', 0, 10**9)
    integer(value.get('daily_physical_seconds_before', 0), 'daily seconds before', 0, 10**9)


def pb_fields(value, confirmed):
    benchmark(value.get('previous_benchmark')); boolean(value['new_personal_best'])
    previous = value.get('previous_benchmark')
    match(value.get('pb_delta'), rounded(value['beat_score'] - previous['score']) if previous else None)
    if value['new_personal_best'] and (not confirmed or not previous or value['beat_score'] <= previous['score']):
        raise ValueError('PB requires a qualified better comparable benchmark')


def validate_report_rewards(report):
    def reliable_prefix(block_id=None):
        index = len(report['blocks']) - 1 if block_id is None else next((i for i,b in enumerate(report['blocks']) if b['id'] == block_id), -1)
        ends = [i for i,e in enumerate(report.get('events', [])) if e['block_index'] == index and e['kind'] == 'block_done']
        if not ends:
            return False
        prefix = report['events'][:ends[-1]+1]
        return len({e['boot'] for e in prefix}) == 1 and not any(e['kind'] in {'recovered_after_reboot', 'review_recovered_after_reboot'} for e in prefix)
    for b in report['blocks']:
        p = b.get('performance')
        if p is None:
            continue
        obj(p, PERFORMANCE_FIELDS, {'quality', 'wall_ms', 'beat_score', 'rank', 'recalibration_needed', 'new_personal_best', 'scoring', 'reward'})
        if b.get('timer_mode') == 'stopwatch':
            from .stopwatch import validate_stopwatch_performance
            validate_stopwatch_performance(b,p)
            continue
        if p['quality'] not in {'pending', 'confirmed', 'incomplete'} or b['type'] != 'focus' or b['outcome'] != 'done':
            raise ValueError('Invalid competition completion quality')
        if p['quality'] == 'pending' and report['status'] == 'complete':
            raise ValueError('Completed session has a pending quality check')
        criteria(p.get('quality_criteria', [])); comparison(p.get('comparison')); contract(p.get('reward_contract'))
        if not isinstance(p['scoring'], dict):
            raise ValueError('Performance scoring policy must be an object')
        policy = scoring(p['scoring']); receipt(p['reward'], contract_value=p.get('reward_contract'), policy_value=p.get('reward_policy')); boolean(p['recalibration_needed'])
        match(p['wall_ms'], b['active_ms'] + b['paused_ms'] if report.get('report_version',1)==2 else max(0, b['finished_at_ms'] - b['started_at_ms']))
        pending = p['quality'] == 'pending'
        pace = rounded(b['planned_ms'] / b['active_ms'] * 100) if b['active_ms'] else None
        focus = rounded(b['active_ms'] / (b['active_ms'] + b['paused_ms']) * 100) if b['active_ms'] + b['paused_ms'] else None
        match(p.get('pace_index'), None if pending else pace); match(p.get('focus_ratio'), None if pending else focus)
        score = beat_score(pace, focus, p['quality'] == 'confirmed', policy)
        match(p['beat_score'], score)
        if p['rank'] != rank(score, policy) or (not pending and p['recalibration_needed'] != (pace is None or pace > policy['recalibration_pace'])):
            raise ValueError('Invalid score rank or recalibration flag')
        if p.get('previous_benchmark') and p.get('comparison') is None:
            raise ValueError('Block benchmark requires explicit comparison')
        pb_fields(p, p['quality'] == 'confirmed' and not p['recalibration_needed'] and (report.get('timeline_coverage') == 'complete' or reliable_prefix(b['id'])))
        if p['quality'] != 'confirmed' and p['reward']['actions']:
            raise ValueError('Incomplete task cannot receive rewards')
    c = report.get('competition')
    if c is None:
        if any(b.get('performance') is not None for b in report['blocks']):
            raise ValueError('Competition report requires session aggregates')
        return
    obj(c, COMPETITION_FIELDS, COMPETITION_FIELDS - {'comparison_key', 'average_pace', 'focus_ratio', 'previous_benchmark', 'pb_delta', 'session_reward', 'weighted', 'session_reward_policy', 'score_scope'})
    if not isinstance(c['reward_strategy'], dict):
        raise ValueError('Session reward strategy must be an object')
    strategy(c['reward_strategy']); contract(c.get('session_reward'), True); receipt(c['reward'], True, c.get('session_reward'), c.get('session_reward_policy'))
    for block in report['blocks']:
        p = block.get('performance')
        if p:
            permitted(p.get('reward_contract'), c['reward_strategy'])
    permitted(c.get('session_reward'), c['reward_strategy'])
    if c.get('comparison_key') is not None:
        text_field(c['comparison_key'], 'session comparison key', 100)
    planned = integer(c['planned_focus_blocks'], 'planned focus blocks', 0, 100)
    focused = [b for b in report['blocks'] if b['type'] == 'focus']
    if planned < len(focused):
        raise ValueError('Missing planned focus blocks')
    integer(c['completed_focus_blocks'], 'completed focus blocks', 0, 100)
    integer(c['on_or_ahead_count'], 'on or ahead count', 0, 100)
    completed = sum(b.get('performance', {}).get('quality') == 'confirmed' for b in focused if b.get('performance'))
    on_target = sum(b['performance'].get('performance_class') in {'strong','on_target'} if c.get('weighted') is not None else b['performance'].get('quality') == 'confirmed' and b['active_ms'] <= b['planned_ms'] for b in focused if b.get('performance'))
    if c['completed_focus_blocks'] != completed or c['on_or_ahead_count'] != on_target:
        raise ValueError('Invalid completion counts')
    match(c['completion_fraction'], rounded(completed / planned) if planned else 0)
    pace_values = [b['performance']['pace_index'] for b in focused if b.get('performance') and b['performance'].get('pace_index') is not None]
    match(c.get('average_pace'), rounded(sum(pace_values) / len(pace_values)) if pace_values else None)
    active = sum(b['active_ms'] for b in focused); paused = sum(b['paused_ms'] for b in focused)
    match(c.get('focus_ratio'), rounded(active / (active + paused) * 100) if active + paused else None)
    score = rounded(sum((b.get('performance') or {}).get('beat_score') or 0 for b in focused) / planned) if planned else 0
    if c.get('weighted') is not None:
        scored=[b['performance'] for b in focused if b.get('timer_mode','countdown')=='countdown' and b['outcome']=='done' and b.get('performance')]
        weight=sum(p.get('session_weight',1) for p in scored)
        score=rounded(sum(p['beat_score']*p.get('session_weight',1) for p in scored)/weight) if weight else None if report.get('planned_stopwatch_focus_blocks',0)>0 else 0
    match(c['beat_score'], score)
    if c['rank'] != (rank(score, c['reward_strategy'].get('scoring')) if score is not None else None):
        raise ValueError('Invalid session rank')
    if c.get('previous_benchmark') and not c.get('comparison_key'):
        raise ValueError('Session benchmark requires comparison key')
    if score is not None:
        pb_fields(c, planned > 0 and completed == planned and (report.get('timeline_coverage') == 'complete' or reliable_prefix()))
    if any(b['outcome'] == 'skipped' for b in report['blocks']) and (c['reward']['actions'] or c['new_personal_best'] or c.get('previous_benchmark') is not None or c.get('pb_delta') is not None):
        raise ValueError('Skipped session cannot earn a session reward or whole-session PB')
    if c['reward']['actions'] and (not planned or completed != planned):
        raise ValueError('Incomplete session cannot receive a session reward')

REWARD_COLUMNS = ['quality', 'block_wall_ms', 'pace_index', 'focus_ratio', 'beat_score', 'rank', 'recalibration_needed',
                  'comparison_json', 'quality_criteria_json', 'scoring_policy_json', 'reward_contract_json', 'reward_tier', 'reward_actions_json', 'reward_label',
                  'reward_acknowledged', 'reward_skipped', 'reward_acknowledged_at_utc', 'reward_local_day', 'daily_reps_before_json', 'daily_physical_seconds_before',
                  'previous_benchmark_json', 'new_personal_best', 'pb_delta', 'performance_class', 'session_weight', 'classification_json', 'reward_policy_json']
SESSION_REWARD_COLUMNS = ['planned_focus_blocks', 'completed_focus_blocks', 'on_or_ahead_count', 'completion_fraction', 'average_pace', 'session_comparison_key', 'reward_strategy_json', 'raw_weighted_points', 'total_weight', 'normalized_session_score', 'strong_blocks', 'on_target_blocks', 'slightly_over_blocks', 'over_target_blocks', 'average_focus_ratio', 'session_scoring_json', 'session_reward_policy_json']


def reward_label(actions):
    return ' + '.join(a['text'] if a['kind'] == 'text' else f"{a['reps']} {a['kind']}" if a['kind'] in {'pushups', 'squats'} else 'coffee' if a['kind'] == 'coffee' else f"{a['seconds']} sec {a['kind']}" for a in actions) or 'No reward assigned'


def csv_values(value, session=False):
    import json
    if value is None:
        return [None] * (len(REWARD_COLUMNS) + (len(SESSION_REWARD_COLUMNS) if session else 0))
    encode = lambda v: json.dumps(v, ensure_ascii=False, separators=(',', ':')) if v is not None else None
    r = value['reward']
    stamp = r.get('acknowledged_at_ms')
    at = (dt.datetime(1970, 1, 1, tzinfo=dt.timezone.utc) + dt.timedelta(milliseconds=stamp)).isoformat(timespec='milliseconds').replace('+00:00', 'Z') if stamp is not None else None
    row = ['session' if session else value['quality'], None if session else value['wall_ms'], value.get('average_pace' if session else 'pace_index'), value.get('focus_ratio'), value['beat_score'], value['rank'], None if session else value['recalibration_needed'],
           None if session else encode(value.get('comparison')), None if session else encode(value.get('quality_criteria', [])), encode(value['reward_strategy'].get('scoring', SCORE_DEFAULTS) if session else value['scoring']), encode(value.get('session_reward' if session else 'reward_contract')),
           r['tier'], encode(r['actions']), reward_label(r['actions']), r['acknowledged'], r['skipped'], at, r.get('local_day'), encode(r.get('daily_reps_before', {})), r.get('daily_physical_seconds_before', 0),
           encode(value.get('previous_benchmark')), value['new_personal_best'], value.get('pb_delta'), None if session else value.get('performance_class'), None if session else value.get('session_weight'), None if session else encode(value.get('classification')), None if session else encode(value.get('reward_policy'))]
    if session:
        row += [value['planned_focus_blocks'], value['completed_focus_blocks'], value['on_or_ahead_count'], value['completion_fraction'], value.get('average_pace'), value.get('comparison_key'), encode(value['reward_strategy'])]
        w=value.get('weighted') or {}
        row += [w.get('raw_weighted_points'), w.get('total_weight'), w.get('normalized_session_score')] + [w.get('class_counts',{}).get(k) for k in ('strong','on_target','slightly_over','over_target')] + [w.get('average_focus_ratio'),encode(w.get('scoring')),encode(value.get('session_reward_policy'))]
    return row


def summary(report):
    import json
    lines = []
    for b in report['blocks']:
        p = b.get('performance')
        if p is not None:
            lines += ['', f"Block {b['id']}: completion= DONE; class={('N/A' if b.get('timer_mode')=='stopwatch' else p.get('performance_class') or p['quality'])}; weight={p.get('session_weight',1)}; wall={p['wall_ms']} ms; pace={p.get('pace_index') if p.get('pace_index') is not None else 'N/A'}; focus={p.get('focus_ratio') if p.get('focus_ratio') is not None else 'N/A'}%; BeatScore={p['beat_score'] if p['beat_score'] is not None else 'N/A'} {p['rank'] or ''}; recalibration={p['recalibration_needed']}",
                      'Performance and reward evidence: ' + json.dumps(p, ensure_ascii=False, sort_keys=True)]
            lines += ['', f"Reward: {reward_label(p['reward']['actions'])}; tier={p['reward']['tier']}; acknowledged={p['reward']['acknowledged']}"]
    c = report.get('competition')
    if c:
        lines += ['', f"{('Countdown-only scoring; overall completion' if c.get('score_scope')=='countdown_only' else 'Untimed completion (scores N/A)' if c.get('score_scope')=='none' else 'Competition')}: {c['completed_focus_blocks']}/{c['planned_focus_blocks']} focus blocks completed with DONE; {c['on_or_ahead_count']} on/ahead; average pace={c.get('average_pace')}; focus={c.get('focus_ratio')}%; BeatScore={c['beat_score'] if c['beat_score'] is not None else 'N/A'} {c['rank'] or ''}",
                  'Session benchmark: ' + (json.dumps(c['previous_benchmark']) if c.get('previous_benchmark') else 'NO BENCHMARK YET'),
                  f"Session reward: {reward_label(c['reward']['actions'])}; tier={c['reward']['tier']}; acknowledged={c['reward']['acknowledged']}",
                  'Session competition and policy: ' + json.dumps(c, ensure_ascii=False, sort_keys=True),
                  'Completion and reward acknowledgement are self-reports, not verified exercise, attention, learning or caffeine intake.']
    return '\n'.join(lines)
