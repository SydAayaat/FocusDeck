"""Exact curriculum imports and read-only projections of phone evidence."""
import copy
import datetime as dt
import json
import re

def validate_document(value):
    if not isinstance(value, dict) or set(value) != {'topics'} or not isinstance(value['topics'], list) or not 1 <= len(value['topics']) <= 5000:
        raise ValueError('Index needs {topics: [...]} with 1..5000 nodes')
    nodes = {}; aliases = set()
    for t in value['topics']:
        if not isinstance(t, dict) or set(t)-{'id','title','parent_id'} or not isinstance(t.get('id'), str) or not re.fullmatch(r'[A-Za-z0-9_.:-]{1,100}',t['id']) or not isinstance(t.get('title'),str) or not t['title'].strip() or len(t['title'])>500:
            raise ValueError('Each topic needs an explicit ID and title')
        alias = '.'.join(part.lstrip('0') or '0' for part in t['id'].split('.')) if re.fullmatch(r'\d+(\.\d+)*', t['id']) else t['id']
        if t['id'] in nodes or alias in aliases: raise ValueError('Duplicate or ambiguous topic ID: '+t['id'])
        if t.get('parent_id') is not None and not isinstance(t['parent_id'],str): raise ValueError('parent_id must be an ID or null')
        nodes[t['id']] = t; aliases.add(alias)
    for t in nodes.values():
        seen = {t['id']}; parent = t.get('parent_id')
        while parent is not None:
            if parent not in nodes or parent in seen or len(seen)>=32: raise ValueError('Missing parent, cycle or excessive depth')
            seen.add(parent); parent = nodes[parent].get('parent_id')
    if sum(len(t['id'])+len(t['title'])+len(t.get('parent_id') or '') for t in nodes.values())>130000: raise ValueError('Index document too large')
    return copy.deepcopy(value)

def import_index(source):
    if isinstance(source,dict): return validate_document(source)
    if not isinstance(source,str) or len(source.encode())>512*1024: raise ValueError('Import text must be <=512 KiB')
    if source.lstrip().startswith('{'):
        def unique(pairs):
            d={}
            for k,v in pairs:
                if k in d: raise ValueError('Duplicate JSON field: '+k)
                d[k]=v
            return d
        return validate_document(json.loads(source,object_pairs_hook=unique))
    topics=[]
    for line in source.splitlines():
        if not line.strip(): continue
        m=re.fullmatch(r'\s*(?:#{1,6}\s+|[-*+]\s+)?(\d+(?:\.\d+)*)(?:[.)])?\s+(.+?)\s*',line)
        if not m: raise ValueError('Each nonblank line must start with its exact topic number: '+line[:80])
        key,title=m.groups(); topics.append(dict(id=key,title=title,parent_id=key.rsplit('.',1)[0] if '.' in key else None))
    return validate_document(dict(topics=topics))

def leaves(document):
    parents={t.get('parent_id') for t in document['topics']}; return [t for t in document['topics'] if t['id'] not in parents]

def preview(document, current=None):
    document=validate_document(document); old=(current or {}).get('document',{'topics':[]})
    a={t['id']:t for t in old['topics']}; b={t['id']:t for t in document['topics']}
    return dict(document=document,total=len(leaves(document)),added=[k for k in b if k not in a],removed=[k for k in a if k not in b],
        renamed=[k for k in b if k in a and a[k]['title']!=b[k]['title']],moved=[k for k in b if k in a and a[k].get('parent_id')!=b[k].get('parent_id')],
        reordered=[k for k in b if k in a and list(a).index(k)!=list(b).index(k)],
        trackability_changed=sorted({t['id'] for t in leaves(old)} ^ {t['id'] for t in leaves(document)}),
        completed=sum(bool((current or {}).get('states',{}).get(t['id'],{}).get('completed')) for t in leaves(document)))

def daily_bars(ledger, end, window=14):
    if window not in (7,14,30,90): raise ValueError('Window must be 7/14/30/90 days')
    end=dt.date.fromisoformat(end); events={e['id']:e for e in ledger.get('events',[])}
    rows=[]
    for offset in range(window-1,-1,-1):
        key=(end-dt.timedelta(days=offset)).isoformat(); es=[e for e in events.values() if e['date']==key]
        rows.append(dict(date=key,gross=sum(e['delta']>0 for e in es),net=sum(e['delta'] for e in es),events=es))
    return rows

def evidence(snapshot, card_id):
    store=snapshot.get('ledger',{}); l=store.get('indexes',{}).get(card_id)
    if not l: raise ValueError('Not an Index card')
    return dict(card_id=card_id,as_of_ms=snapshot['as_of_ms'],zone=snapshot['zone'],index=l,
        summary=next((s for s in snapshot.get('summary',[]) if s['id']==card_id),{}),
        legacy_numeric_history=[h for h in store.get('progressHistory',[]) if h['achievementId']==card_id],
        daily_study_events=[e for e in store.get('events',[]) if e['achievementId']==card_id],days=store.get('days',{}),targets=store.get('targets',[]),
        streaks=[s for s in store.get('segments',[]) if s['achievementId']==card_id])

def validate_index_command(c):
    from .achievements import identifier, timestamp
    identifier(c.get('id'));timestamp(c.get('timestamp')); op=c.get('operation')
    extras={'index_create':{'definition','document','configuration'},'index_migrate':{'achievement_id','document','configuration','expected_store_revision','confirm'},
        'index_reconcile':{'achievement_id','document','expected_index_revision','confirm'},'index_topic':{'achievement_id','topic_id','completed','expected_index_revision','note','evidence'}}
    if op not in extras or set(c)-({'achievement_version','id','timestamp','source','operation'}|extras[op]): raise ValueError('Invalid Index operation fields')
    if not isinstance(c.get('source','GPT'),str) or not 1<=len(c.get('source','GPT'))<=100: raise ValueError('Invalid source')
    if 'document' in c: validate_document(c['document'])
    elif op!='index_topic': raise ValueError('document required')
    if op=='index_create':
        d=c.get('definition',{})
        identifier(d.get('id'),definition=True)
        if set(d)-{'id','title','category','kind','description','metadata','daily_participation','streak_enabled','progress_enabled'} or d.get('category')!='index' or d.get('kind','daily') not in ('daily','scheduled') or not isinstance(d.get('title'),str) or not 1<=len(d['title'].strip())<=200 or len(d.get('description',''))>1000: raise ValueError('Invalid Index definition')
        meta=d.get('metadata',{})
        if not isinstance(meta,dict) or len(meta)>30 or any(not isinstance(k,str) or len(k)>100 or not isinstance(v,str) or len(v)>2000 for k,v in meta.items()): raise ValueError('Invalid metadata')
    else: identifier(c.get('achievement_id'),definition=True)
    if c.get('configuration','HYBRID') not in ('HYBRID','PROGRESS_ONLY'): raise ValueError('Invalid configuration')
    for field in ('expected_index_revision','expected_store_revision'):
        if field in c and (type(c[field]) is not int or c[field]<0): raise ValueError('Invalid revision')
    if op in ('index_migrate','index_reconcile') and (c.get('confirm') is not True or ('expected_store_revision' if op=='index_migrate' else 'expected_index_revision') not in c): raise ValueError('Explicit confirmation and revision required')
    if op=='index_topic':
        if not isinstance(c.get('topic_id'),str) or not re.fullmatch(r'[A-Za-z0-9_.:-]{1,100}',c['topic_id']) or type(c.get('completed')) is not bool or 'expected_index_revision' not in c: raise ValueError('Topic ID, boolean completed and revision required')
        for k,n in [('note',2000),('evidence',4000)]:
            if not isinstance(c.get(k,''),str) or len(c.get(k,''))>n: raise ValueError('Invalid '+k)
    if len(json.dumps(c).encode())>768*1024: raise ValueError('Index command too large')
    return c
