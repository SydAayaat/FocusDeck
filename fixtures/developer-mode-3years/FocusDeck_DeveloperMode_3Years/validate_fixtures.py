#!/usr/bin/env python3
"""Read-only validator for FocusDeck synthetic fixture archive directory."""
from pathlib import Path
from collections import defaultdict
import json,hashlib,sys
p=Path(sys.argv[1] if len(sys.argv)>1 else Path(__file__).resolve().parent)
m=json.loads((p/'manifest.json').read_text())
assert m['synthetic'] and m['no_production_import'] and m['no_sync'] and m['no_network']
for file in m['files']:
    raw=(p/file['path']).read_bytes()
    assert len(raw)==file['bytes'] and hashlib.sha256(raw).hexdigest()==file['sha256'],file['path']
def jl(name):
    with (p/name).open(encoding='utf8') as fh:
        return [json.loads(s) for s in fh if s.strip()]
sessions=jl('sessions.jsonl');blocks=jl('blocks.jsonl');days=jl('daily_analytics.jsonl');periods=jl('period_analytics.jsonl')
by=defaultdict(list)
for b in blocks:by[b['session_id']].append(b)
assert len(sessions)==m['counts']['sessions']
assert len(blocks)==m['counts']['blocks']
for s in sessions:
    assert s['focus_seconds']==sum(x['active_focus_seconds'] for x in by[s['id']])
    assert s['done_count']==sum(x['status']=='DONE' for x in by[s['id']])
for d in days:
    assert sum(d['focus_by_hour_seconds'])==d['focus_seconds']
    assert sum(d['focus_by_8h_bin_seconds'])==d['focus_seconds']
assert sum(s['focus_seconds'] for s in sessions)==sum(d['focus_seconds'] for d in days)
for kind in ['week','month','year']:
    assert sum(x['focus_seconds'] for x in periods if x['period']==kind)==sum(d['focus_seconds'] for d in days)
ix=jl('index_events.jsonl'); defs=json.loads((p/'index_definitions.json').read_text());snap=json.loads((p/'index_snapshots.json').read_text())
for item in defs:
    completed=set()
    for ev in ix:
        if ev['index_id']!=item['id']:continue
        if ev['action']=='topic_completed':completed.add(ev['topic_id'])
        else:completed.remove(ev['topic_id'])
    assert len(completed)==next(s['completed_topics'] for s in snap if s['id']==item['id'])
print('VALID — checksums, safety flags, referential counts, daily/hourly/monthly/weekly/yearly focus totals, block completion, and Index replay.')
print(json.dumps(m['counts'],indent=2))
