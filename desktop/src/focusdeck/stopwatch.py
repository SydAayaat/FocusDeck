"""Version 2 completion conditions and untimed report evidence."""
from .common import integer

TIME_FIELDS = {'min_beat_score', 'max_beat_score', 'min_pace_index', 'min_session_score', 'max_session_score'}


def completion_context(report, index=None):
    blocks = report['blocks'] if index is None else report['blocks'][:index + 1]
    ids = {b['id'] for b in blocks if b['type'] == 'focus' and b['outcome'] == 'done'}
    return dict(completed=bool(blocks) and blocks[-1]['outcome'] == 'done', completed_ids=ids,
                all_focus_completed=len(ids) == (report.get('competition') or {}).get('planned_focus_blocks'),
                session_completed=report.get('planned_block_count') == len(report['blocks']) and all(b['outcome'] == 'done' for b in report['blocks']))


def completion_policy(policy, version, stopwatch=False, session=False, ids=None, prefix=None, untimed_only=False):
    if policy is None: return
    # Check shapes before evaluating references: malformed report policies must not crash sync.
    from .conditional import policy as validate_policy
    validate_policy(policy, session)
    for tier in policy.get('tiers', []):
        if version == 1 and (tier.get('condition') not in (None, 'personal_best') or tier.get('block_ids') or tier.get('min_completed_blocks') is not None):
            raise ValueError('Completion conditions require session/report version 2')
        refs = set(tier.get('block_ids', []))
        if ids is not None and not refs <= ids: raise ValueError('module_completed references an unknown/non-focus block ID')
        if prefix is not None and not refs <= prefix: raise ValueError('Milestone cannot reference future blocks')
        if stopwatch and (tier.get('condition') not in ('completed', 'module_completed') or any(tier.get(k) is not None for k in TIME_FIELDS)):
            raise ValueError('Stopwatch reward requires completed/module_completed and cannot use time-based scores')
        if untimed_only and (any(tier.get(k) is not None for k in TIME_FIELDS) or tier.get('condition') == 'personal_best'):
            raise ValueError('Untimed session reward cannot require countdown scores')
    e = policy.get('eligibility', {})
    if version == 1 and (e.get('minimum_completed_blocks', 0) or e.get('require_all_focus_completed', False)):
        raise ValueError('Completion eligibility requires version 2')
    if untimed_only and any(e.get(k, 0) for k in ('minimum_beat_score', 'minimum_session_score', 'minimum_strong_blocks', 'minimum_on_or_above_target_blocks', 'minimum_average_focus_ratio')):
        raise ValueError('Untimed session cannot require countdown scoring')
    if untimed_only and e.get('maximum_over_target_blocks', 100) != 100:
        raise ValueError('Untimed session cannot require countdown classifications')


def validate_completion_plan(plan):
    version = plan['protocol_version']
    ids = {b['id'] for b in plan['blocks'] if b['type'] == 'focus'}
    has_stopwatch = any(b.get('timer_mode') == 'stopwatch' for b in plan['blocks'])
    prefix = set()
    for b in plan['blocks']:
        prefix.add(b['id'])
        stopwatch = b.get('timer_mode') == 'stopwatch'
        if stopwatch and any(b.get(k) is not None for k in ('classification', 'comparison', 'reward_contract')):
            raise ValueError('Stopwatch supports completion reward_policy, not timed classification/comparison/legacy contracts')
        completion_policy(b.get('reward_policy'), version, stopwatch, ids=ids, prefix=prefix)
    if has_stopwatch and (plan.get('session_reward') is not None or (plan.get('reward_strategy') or {}).get('default_contract') is not None):
        raise ValueError('Stopwatch sessions require explicit reward_policy; legacy default/session contracts are unsupported')
    untimed_only = version == 2 and not any(b['type'] == 'focus' and b.get('timer_mode', 'countdown') == 'countdown' for b in plan['blocks'])
    completion_policy(plan.get('session_reward_policy'), version, session=True, ids=ids, untimed_only=untimed_only)


def validate_v2_report(report):
    from .rewards import number, rounded
    import uuid
    try:
        if str(uuid.UUID(report["session_id"])) != report["session_id"].lower(): raise ValueError()
    except ValueError: raise ValueError("v2 report session_id must be UUID") from None
    count = report.get('planned_countdown_focus_blocks')
    untimed = report.get('planned_stopwatch_focus_blocks')
    integer(count, 'planned_countdown_focus_blocks', 0, 100)
    integer(untimed, 'planned_stopwatch_focus_blocks', 0, 100)
    integer(report.get('timed_planned_ms'), 'timed_planned_ms', 0, 604800000)
    if (report.get('planned_ms') is None) != (untimed > 0): raise ValueError('Untimed session planned duration must be null')
    if count < sum(b['type'] == 'focus' and b.get('timer_mode') == 'countdown' for b in report['blocks']) or untimed < sum(b.get('timer_mode') == 'stopwatch' for b in report['blocks']):
        raise ValueError('Invalid planned timer-mode counts')
    if len({b['id'] for b in report['blocks']}) != len(report['blocks']): raise ValueError('Duplicate report block IDs')
    planned = integer(report.get('planned_block_count'), 'planned_block_count', len(report['blocks']), 100)
    if count + untimed > planned: raise ValueError('Focus mode counts exceed planned block count')
    if report['status'] == 'complete' and (planned != len(report['blocks']) or count != sum(b['type']=='focus' and b.get('timer_mode')=='countdown' for b in report['blocks']) or untimed != sum(b.get('timer_mode')=='stopwatch' for b in report['blocks'])):
        raise ValueError('Completed report must include every planned focus block in its mode counts')
    if report['status'] == 'complete' and any(b['outcome'] != 'done' for b in report['blocks']):
        raise ValueError('Completed v2 report requires DONE for every planned block')
    expected_timed = sum(b['planned_ms'] for b in report['blocks'] if b.get('timer_mode')=='countdown')
    if report['timed_planned_ms'] < expected_timed or report['status']=='complete' and report['timed_planned_ms'] != expected_timed:
        raise ValueError('Timed planned portion differs from countdown block evidence')
    if not untimed and report['planned_ms'] != report['timed_planned_ms']: raise ValueError('Countdown planned totals differ')
    c = report.get('competition')
    if not isinstance(c, dict) or c.get('planned_focus_blocks') != count + untimed or not isinstance(c.get('weighted'), dict):
        raise ValueError('v2 report requires consistent focus counts and countdown-only weighted evidence')
    scope = 'all_focus' if not untimed else 'countdown_only' if count else 'none'
    if c.get('score_scope') != scope: raise ValueError('Countdown-only score must be labeled with its score_scope')
    if scope != 'all_focus' and (c.get('previous_benchmark') is not None or c.get('new_personal_best') or c.get('pb_delta') is not None):
        raise ValueError('Mixed/untimed sessions cannot claim a whole-session personal best')
    for index, b in enumerate(report['blocks']):
        if not {'timer_mode','planned_ms','budget_ms','wall_ms','focus_ratio','overtime_ms'} <= b.keys(): raise ValueError('v2 block is missing required timer/completion fields')
        if 'timer_mode' not in b or b['outcome'] not in ('done', 'stopped', 'skipped'): raise ValueError('v2 blocks require timer_mode and DONE/stopped/skipped evidence')
        integer(b.get('wall_ms'), 'block wall_ms', 0, 10**12)
        if b['wall_ms'] != b['active_ms'] + b['paused_ms']: raise ValueError('Wall duration differs from monotonic active+paused evidence')
        focus = rounded(b['active_ms'] / b['wall_ms'] * 100) if b['wall_ms'] else None
        if b.get('focus_ratio') is not None: number(b['focus_ratio'], 0, 100)
        if b.get('focus_ratio') != focus: raise ValueError('Block Focus Ratio differs from rounded monotonic evidence')
        p = b.get('performance')
        if p is not None and not isinstance(p, dict): raise ValueError('v2 performance evidence must be an object')
        if b['type'] == 'focus' and b['outcome'] == 'done' and (p is None or p.get('quality') != 'confirmed'): raise ValueError('v2 DONE focus requires confirmed completion/reward evidence')
        if p:
            completion_policy(p.get('reward_policy'), 2, b['timer_mode'] == 'stopwatch', prefix={x['id'] for x in report['blocks'][:index+1] if x['type'] == 'focus'})
    completion_policy(c.get('session_reward_policy'), 2, session=True, ids={b['id'] for b in report['blocks'] if b['type'] == 'focus'} if report['status'] == 'complete' else None, untimed_only=count == 0)


def validate_stopwatch_performance(block, performance):
    from .rewards import criteria, scoring, receipt, match, boolean
    p = performance
    if block['type'] != 'focus' or block['outcome'] != 'done' or p['quality'] != 'confirmed': raise ValueError('Invalid stopwatch completion evidence')
    if any(p.get(k) is not None for k in ('pace_index', 'beat_score', 'rank', 'performance_class', 'classification', 'comparison', 'reward_contract', 'previous_benchmark', 'pb_delta')) or p['recalibration_needed'] or p['new_personal_best']:
        raise ValueError('Stopwatch cannot contain timed scores, classifications or benchmarks')
    criteria(p.get('quality_criteria', [])); scoring(p['scoring'])
    boolean(p['recalibration_needed']); boolean(p['new_personal_best'])
    match(p['wall_ms'], block['active_ms'] + block['paused_ms']); match(p.get('focus_ratio'), block.get('focus_ratio'))
    receipt(p['reward'], policy_value=p.get('reward_policy'))
