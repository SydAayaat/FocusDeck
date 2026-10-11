"""Durable tombstones shared by phone and desktop; derived files are retryable."""
import hashlib
import json
import re
import uuid
from pathlib import Path
from .common import atomic_json, read_json, integer, text_field
from .history import refresh_csv


def empty():return dict(before_ms=0,report_keys=[],revision='')

def validate(value):
    if not isinstance(value,dict) or set(value)!={'before_ms','report_keys','revision'}:raise ValueError('Invalid history deletion record')
    integer(value['before_ms'],'before_ms',0,253402300799999)
    keys=value['report_keys']
    if not isinstance(keys,list) or len(keys)>3000:raise ValueError('Invalid deleted report keys')
    for k in keys:text_field(k,'report key',250)
    if len(set(keys))!=len(keys):raise ValueError('Repeated deleted report key')
    if not isinstance(value['revision'],str) or len(value['revision'])>100:raise ValueError('Invalid deletion revision')
    if len(json.dumps(value,ensure_ascii=False,separators=(',',':')).encode('utf-8'))>786432:raise ValueError('Deletion metadata exceeds sync limit')
    return value


def merge(current,incoming):
    validate(current);validate(incoming)
    keys=sorted(set(current['report_keys'])|set(incoming['report_keys']));before=max(current['before_ms'],incoming['before_ms'])
    if len(keys)>3000:raise ValueError('Deletion record limit reached')
    if before==current['before_ms'] and set(keys)==set(current['report_keys']):return current
    return validate(dict(before_ms=before,report_keys=keys,revision=str(uuid.uuid4())))


def hides(deletions,report):
    return f"{report['session_id']}:{report['started_at_ms']}" in deletions['report_keys'] or deletions['before_ms']>0 and report['finished_at_ms']<=deletions['before_ms']


def apply(root,deletions):
    validate(deletions);root=Path(root);reports=root/'reports'
    hashes={hashlib.sha256(k.encode()).hexdigest()[:24] for k in deletions['report_keys']}
    count=0
    for path in reports.glob('*.json'):
        if not re.fullmatch(r'[a-f0-9]{24}\.json',path.name):continue
        try:r=read_json(path);hidden=hides(deletions,r)
        except (ValueError,TypeError,KeyError):hidden=deletions['before_ms']>0 and path.stat().st_mtime_ns//300000<=deletions['before_ms']
        if path.stem in hashes or hidden:
            path.unlink(missing_ok=True);path.with_suffix('.md').unlink(missing_ok=True);count+=1
    for path in reports.glob('*.md'):
        if re.fullmatch(r'[a-f0-9]{24}\.md',path.name) and (path.stem in hashes or deletions['before_ms']>0 and path.stat().st_mtime_ns//300000<=deletions['before_ms'] and not path.with_suffix('.json').exists()):path.unlink(missing_ok=True)
    refresh_csv(reports,root/'exports')
    return count


def persist_merge(root,current,incoming):
    result=merge(current,incoming)
    atomic_json(Path(root)/'deletions.json',result) # intent precedes physical cleanup
    return result
