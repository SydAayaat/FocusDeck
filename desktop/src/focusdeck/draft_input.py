"""Bounded, deterministic desktop text input compiling to the existing contracts."""
import copy
import json
import re
import uuid
import yaml
from yaml.nodes import MappingNode, SequenceNode, ScalarNode
from .common import utc_now, validate_plan
from .achievements import validate_command


class PlainLoader(yaml.SafeLoader):
    """JSON scalar semantics; dates, yes/no and icon names remain strings."""
    yaml_implicit_resolvers = {k: [(tag, regex) for tag, regex in v
        if tag not in {'tag:yaml.org,2002:timestamp', 'tag:yaml.org,2002:bool', 'tag:yaml.org,2002:int', 'tag:yaml.org,2002:float'}]
        for k, v in yaml.SafeLoader.yaml_implicit_resolvers.items()}
    def construct_mapping(self, node, deep=False):
        result = {}
        for key, value in node.value:
            if not isinstance(key, ScalarNode) or key.tag != 'tag:yaml.org,2002:str':
                raise ValueError('Field names must be text; YAML merge keys are unsupported.')
            name = self.construct_object(key, deep=deep)
            if name in result: raise ValueError(f'Duplicate field: {name}')
            result[name] = self.construct_object(value, deep=deep)
        return result

PlainLoader.add_implicit_resolver('tag:yaml.org,2002:bool', re.compile(r'^(?:true|false)$'), list('tf'))
PlainLoader.add_implicit_resolver('tag:yaml.org,2002:int', re.compile(r'^-?(?:0|[1-9][0-9]*)$'), list('-0123456789'))
PlainLoader.add_implicit_resolver('tag:yaml.org,2002:float', re.compile(r'^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+(?:[eE][+-]?[0-9]+)?|[eE][+-]?[0-9]+)$'), list('-0123456789'))


def plain_load(text):
    # Disallow aliases before construction, avoiding shared mutable policy objects and expansion.
    count = depth = 0
    try:
        for event in yaml.parse(text):
            count += 1
            if count > 20000: raise ValueError('Too many fields in this draft.')
            if isinstance(event, yaml.AliasEvent) or getattr(event, 'anchor', None):
                raise ValueError('YAML anchors and aliases are unsupported; write each policy explicitly.')
            if isinstance(event, (yaml.MappingStartEvent, yaml.SequenceStartEvent)):
                depth += 1
                if depth > 40: raise ValueError('Draft nesting exceeds 40 levels.')
            if isinstance(event, (yaml.MappingEndEvent, yaml.SequenceEndEvent)): depth -= 1
            if getattr(event, 'tag', None): raise ValueError('Explicit YAML tags are unsupported.')
        return yaml.load(text, Loader=PlainLoader)
    except yaml.YAMLError as exc:
        mark = getattr(exc, 'problem_mark', None)
        raise ValueError('Cannot read the indented input' + (f' at line {mark.line+1}' if mark else '') + '. Check indentation and quote text containing a colon.') from None


def envelope(value):
    if not isinstance(value, dict): raise ValueError('An update must contain named fields.')
    value = copy.deepcopy(value)
    canonical = {'achievement_version','id','timestamp','operation'} <= value.keys()
    aliases = {'card': 'achievement_id', 'current': 'current_value', 'target': 'target_value'}
    for old, new in aliases.items():
        if old in value:
            if new in value: raise ValueError(f'Use either {old} or {new}, not both.')
            value[new] = value.pop(old)
    value.setdefault('achievement_version',5 if str(value.get('operation','')).startswith('index_') else 4)
    value.setdefault('id', str(uuid.uuid4()))
    value.setdefault('timestamp', utc_now())
    if not canonical: value.setdefault('source', 'GPT desktop input')
    return validate_command(value)


def plan_input(value):
    if not isinstance(value, dict): raise ValueError('A session must contain named fields.')
    value = copy.deepcopy(value)
    canonical = {'protocol_version','session_id','created_at','blocks'} <= value.keys()
    value.setdefault('protocol_version', 2)
    value.setdefault('session_id', str(uuid.uuid4()))
    value.setdefault('created_at', utc_now())
    if not canonical: value.setdefault('auto_advance', True)
    for i, block in enumerate(value.get('blocks', [])):
        if not isinstance(block, dict): raise ValueError('Each block must contain named fields.')
        block.setdefault('id', f'b{i+1}')
        shortcuts = [k for k in ('focus', 'break', 'stopwatch') if k in block]
        if len(shortcuts) > 1: raise ValueError('Use one timer shortcut per block.')
        if shortcuts:
            key = shortcuts[0]; duration = block.pop(key)
            if any(k in block for k in ('type', 'timer_mode', 'duration_seconds')):
                raise ValueError('Use a timer shortcut or full timer fields, not both.')
            block['type'] = 'break' if key == 'break' else 'focus'
            if key == 'stopwatch':
                if duration is not True: raise ValueError('Use stopwatch: true without a duration.')
                block['timer_mode'] = 'stopwatch'
            else:
                match = re.fullmatch(r'([0-9]+)\s*(m|min|minutes|s|sec|seconds)', str(duration), re.I)
                if not match: raise ValueError('Timer shortcuts need a duration such as 25m or 90s.')
                block['duration_seconds'] = int(match[1]) * (60 if match[2].lower().startswith('m') else 1)
                if key == 'focus': block['timer_mode'] = 'countdown'
        # No policy default for structured input: absence and disabled are distinct contracts.
    return validate_plan(value)


def structured_card(value):
    if not isinstance(value, dict): raise ValueError('A card must contain named fields.')
    d = copy.deepcopy(value)
    cap = d.pop('type', None)
    if cap is not None:
        if cap not in {'streak','progress','hybrid'}: raise ValueError('Card type must be streak, progress, or hybrid.')
        if any(k in d for k in ('streak_enabled','progress_enabled')): raise ValueError('Use type or explicit capability flags, not both.')
        d.update(streak_enabled=cap!='progress', progress_enabled=cap!='streak')
    d.setdefault('id', 'card_'+uuid.uuid4().hex[:12]); d.setdefault('kind','daily')
    for short, full in [('current','current_value'),('target','target_value'),('unit','unit')]:
        if short in d:
            progress = d.setdefault('progress', {})
            if full in progress: raise ValueError(f'Duplicate progress field: {full}')
            progress[full] = d.pop(short)
    if d.get('progress_enabled') and 'progress' not in d: d['progress'] = dict(current_value=0,unit='units')
    envelope(dict(operation='upsert',definition=d))
    return dict(kind='card',draft=d,notice='Review this local card draft before sending.')


def parse_draft(text):
    if not isinstance(text,str) or not text.strip() or len(text.encode()) > 1024*1024: raise ValueError('Paste a FocusDeck draft up to 1 MiB.')
    text = text.strip()
    if text.startswith('```'):
        lines = text.splitlines()
        if lines[-1].strip() != '```': raise ValueError('The pasted code block is incomplete.')
        text = '\n'.join(lines[1:-1]).strip()
    if text.startswith(('{','[')):
        def unique_pairs(pairs):
            out = {}
            for k,v in pairs:
                if k in out: raise ValueError(f'Duplicate field: {k}')
                out[k]=v
            return out
        value=json.loads(text, object_pairs_hook=unique_pairs)
        if isinstance(value,dict) and 'blocks' in value:
            return dict(kind='session',draft=validate_plan(value),notice='Original policies and block IDs retained.')
        if isinstance(value,list): return batch(value, canonical=True)
        return dict(kind='command',draft=validate_command(value),notice='Original command receipt retained. Review before applying.')
    lines=text.splitlines(); heading=lines.pop(0).strip().upper()
    if heading not in {'FOCUSDECK CARD','FOCUSDECK SESSION','FOCUSDECK UPDATE','FOCUSDECK BATCH'}:
        raise ValueError('Start with FOCUSDECK CARD, SESSION, UPDATE or BATCH.')
    body='\n'.join(lines)
    # Keep the original compact Title / timer-line format compatible.
    timer_lines=[line for line in lines if re.match(r'\s*(?:[-*]\s*)?(Focus\s+\d|Break\s+\d|Stopwatch\s*\|)',line,re.I)]
    if heading.endswith('SESSION') and timer_lines:
        fields={}; blocks=[]
        for line in lines:
            line=line.strip()
            if not line: continue
            match=re.fullmatch(r'(?:[-*]\s*)?(Focus|Break)\s+(\d+)\s*(m|min|minutes|s|sec|seconds)\s*\|\s*(.+)',line,re.I)
            watch=re.fullmatch(r'(?:[-*]\s*)?Stopwatch\s*\|\s*(.+)',line,re.I)
            if match:
                kind,amount,unit,title=match.groups(); b=dict(id=f'b{len(blocks)+1}',type=kind.lower(),title=title,duration_seconds=int(amount)*(60 if unit.lower().startswith('m') else 1))
                if b['type']=='focus': b.update(timer_mode='countdown',reward_policy={'enabled':False})
                blocks.append(b)
            elif watch: blocks.append(dict(id=f'b{len(blocks)+1}',type='focus',title=watch[1],timer_mode='stopwatch',reward_policy={'enabled':False}))
            else:
                if ':' not in line: raise ValueError('Cannot read timer line: '+line[:80])
                key,val=line.split(':',1);key=key.strip().lower()
                if key in fields: raise ValueError('Duplicate field: '+key)
                fields[key]=val.strip()
        if set(fields)-{'title'}: raise ValueError('Use the indented blocks format when adding session policies.')
        return dict(kind='session',draft=plan_input(dict(title=fields.get('title',''),blocks=blocks)),notice='Review this session. Rewards are explicitly disabled.')
    value=plain_load(body)
    if heading.endswith('SESSION'):
        return dict(kind='session',draft=plan_input(value),notice='All supplied policies, conditions and IDs are retained. Review before sending.')
    if heading.endswith('CARD'):
        # Legacy capitalized labels map to the documented card shortcuts only.
        if isinstance(value,dict):
            normalized={}
            for k,v in value.items():
                key=k.lower()
                if key in normalized: raise ValueError('Duplicate field: '+key)
                normalized[key]=v
            value=normalized
        if isinstance(value,dict) and value.get('type') in ('index_hybrid','index_progress'):
            from .index_tracker import import_index
            value=dict(value); config='HYBRID' if value.pop('type')=='index_hybrid' else 'PROGRESS_ONLY'
            doc=import_index(value.pop('master_index'));value.setdefault('id','card_'+uuid.uuid4().hex[:12]);value['category']='index';value.setdefault('kind','daily')
            return dict(kind='command',index_template=True,draft=envelope(dict(operation='index_create',definition=value,document=doc,configuration=config)),notice='Review exact named topics; no completion evidence is inferred.')
        return structured_card(value)
    if heading.endswith('BATCH'): return batch(value)
    return dict(kind='command',draft=envelope(value),notice='Review this explicit operation; no phone data has changed.')


def batch(values, canonical=False):
    if not isinstance(values,list) or not 1<=len(values)<=100: raise ValueError('A batch needs 1..100 achievement updates.')
    commands=[validate_command(v) if canonical else envelope(v) for v in values]
    if len({c['id'] for c in commands})!=len(commands): raise ValueError('Batch command IDs must be unique.')
    return dict(kind='batch',draft=commands,notice='Ordered updates are reviewed before delivery. A batch is not an atomic transaction.')
