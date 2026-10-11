"""Local control-panel operations. No independent timer, cloud service or LAN listener."""
from __future__ import annotations
import asyncio
import base64
import copy
import datetime as dt
import hashlib
import io
import json
import re
import threading
import uuid
from zoneinfo import ZoneInfo
from pathlib import Path
from PIL import Image
from .common import atomic_json, data_dir, read_json, utc_now, validate_plan
from .achievements import validate_command
from .cli import request_daemon
from .history import load_reports, export_archive
from .analytics import dataset
from .icons import validate_icon, ICON_NAMES
from .reports import markdown
from .icon_library import read_inputs, read_uploaded, import_entries, export_pack

BUILTINS = {'porn_free', 'daily_objectives', 'gym', 'cardio', 'german', 'guitar', 'focus_5', 'focus_8', 'focus_3h'}
DEFAULTS = dict(palette='gold', motion='full', density='comfortable', visuals=True, focus_minutes=25, break_minutes=5)
PALETTES = {'gold', 'monochrome', 'cyan', 'blue', 'emerald', 'amethyst', 'rose', 'amber'}


def command(operation, **fields):
    return validate_command(dict(achievement_version=5 if fields.get("definition",{}).get("category")=="index" else 4, operation=operation, id=str(uuid.uuid4()),
                                 timestamp=utc_now(), source='FocusDeck Control Center', **fields))


def definition_hash(definition):
    value = {k: v for k, v in definition.items() if k != 'progress'}
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def read_document(path, default=None):
    """Durable caches/history are unbounded; the wire request limit is not a file limit."""
    if not path.exists(): return default
    with path.open(encoding='utf-8') as source: return json.load(source)


from .draft_input import parse_draft


def normalize_image(data):
    if len(data) > 16 * 1024 * 1024: raise ValueError('Choose an icon up to 16 MiB.')
    with Image.open(io.BytesIO(data)) as source:
        if source.format not in {'PNG', 'JPEG', 'WEBP', 'ICO'} or source.width * source.height > 16000000:
            raise ValueError('Use PNG, JPEG, WebP or ICO, up to 16 megapixels.')
        image = source.convert('RGBA'); image.thumbnail((256, 256), Image.Resampling.LANCZOS)
        output = io.BytesIO(); image.save(output, format='PNG', optimize=True)
        if len(output.getvalue()) > 65536:
            output = io.BytesIO(); image.quantize(colors=128, method=Image.Quantize.FASTOCTREE).save(output, format='PNG', optimize=True)
    return validate_icon(dict(kind='image', data_base64=base64.b64encode(output.getvalue()).decode()))


class ControlCenter:
    def __init__(self, root=None, transport=None):
        self.root = Path(root or data_dir())
        self.private = self.root / 'control-center'
        self.transport = transport or (lambda p: asyncio.run(request_daemon(p)))
        self.lock = threading.RLock()
        self.report_cache = (None, [])
        self.analytics_cache = (None, {})
        self.last_device_write = 0

    def request(self, payload):
        result = self.transport(payload)
        if not result.get('ok'): raise ValueError(result.get('error') or 'The phone rejected this change.')
        return result

    def settings(self):
        return DEFAULTS | read_document(self.private / 'settings.json', {})

    def snapshot(self, icon_version=None):
        try:
            status = self.request({'action': 'status'}); devices = self.request({'action': 'devices'})
            service = True; error = None
        except (OSError, ValueError, TimeoutError) as exc:
            saved = read_json(self.root / 'daemon.json', {})
            status = dict(connected=False, state=saved.get('state'), state_is_cached=True, pending_command=saved.get('pending'))
            devices = dict(devices=[], viewers=[], paired_id=saved.get('device_id'))
            service = False; error = str(exc)
        authority = devices.get('paired_id')
        known = read_document(self.private / 'known-devices.json', {})
        now_ms = int(dt.datetime.now().timestamp() * 1000)
        online = {d['device_id']: d for d in devices.get('devices', []) + devices.get('viewers', [])}
        changed = set(online) != {key for key, d in known.items() if d.get('connected')}
        for key, device in online.items():
            changed = changed or key not in known
            known[key] = dict(device, connected=True, last_seen_ms=now_ms)
        for key in set(known) - set(online): known[key]['connected'] = False
        if changed or now_ms - self.last_device_write > 60000 and online:
            atomic_json(self.private / 'known-devices.json', known); self.last_device_write = now_ms
        metadata = read_json(self.root / 'viewer-source.json', {})
        ledger = read_document(self.root / 'achievements.json', {}) if authority and metadata.get('authority_device_id') == authority else {}
        for definition in ledger.get('ledger', {}).get('definitions', []):
            definition['_base_hash'] = definition_hash(definition)
        observed = read_json(self.root / 'viewer-state.json', {}) or {}
        if observed.get('authority_device_id') != authority: observed = {}
        files = sorted((self.root / 'reports').glob('*.json'))
        stamp = tuple((str(p), p.stat().st_mtime_ns, p.stat().st_size) for p in files)
        tombstone = self.root / 'deletions.json'
        stamp += (('deletions', tombstone.stat().st_mtime_ns if tombstone.exists() else 0),)
        if stamp != self.report_cache[0]:
            reports = load_reports(self.root / 'reports'); self.report_cache = stamp, reports
        live = observed.get('state') or {}
        analytics_stamp = (stamp, json.dumps(live, sort_keys=True), now_ms // 60000)
        if self.analytics_cache[0] != analytics_stamp:
            self.analytics_cache = (analytics_stamp, dataset(self.report_cache[1], live))
        library_path = self.private / 'icon-library.json'
        library_stamp = library_path.stat() if library_path.exists() else None
        current_icon_version = f'{library_stamp.st_mtime_ns}-{library_stamp.st_size}' if library_stamp else 'empty'
        library_result = {} if current_icon_version == icon_version else dict(icon_library=read_document(library_path, []))
        pending = status.get('pending_command')
        # Never put complete command payloads / pairing credentials in the overview.
        status['pending_command'] = {'command_id': pending.get('command_id'), 'action': pending.get('action')} if pending else None
        return dict(service=service, error=error, status=status, devices=devices, known_devices=list(known.values()), observed=observed,
                    achievements=ledger, reports=list(reversed(self.report_cache[1])), analytics=self.analytics_cache[1], settings=self.settings(),
                    layouts=read_document(self.private / "layouts.json", {}),
                    templates=read_document(self.private / 'templates.json', []), drafts=read_document(self.private / 'drafts.json', {}),
                    deliveries=read_document(self.private / 'deliveries.json', [])[-30:], icons=sorted(ICON_NAMES), builtins=sorted(BUILTINS),
                    icon_version=current_icon_version, ui=read_document(self.private / 'ui.json', {}), **library_result)

    def card_commands(self, draft):
        definition = copy.deepcopy(draft['definition'])
        definition.pop('_base_hash', None)
        if not definition.get('progress_enabled', False):
            definition.pop('progress', None)
        snapshot = self.snapshot(); store = snapshot['achievements'].get('ledger', {})
        old = next((d for d in store.get('definitions', []) if d['id'] == definition['id']), None)
        if old and draft.get('base_hash') != old['_base_hash']:
            raise ValueError('The card configuration changed on the phone. Reload it before editing.')
        operations = []
        if draft.get('index_action'):
            mode=draft['index_action']; definition.pop('progress',None)
            fields=dict(document=draft['index_document'])
            if mode=='create': fields.update(definition=definition,configuration=draft.get('index_configuration','HYBRID'))
            elif mode=='migrate': fields.update(achievement_id=definition['id'],configuration=draft.get('index_configuration','HYBRID'),expected_store_revision=draft['store_revision'],confirm=True)
            else: fields.update(achievement_id=definition['id'],expected_index_revision=draft['index_revision'],confirm=True)
            operations.append(validate_command(dict(achievement_version=5,operation='index_'+mode,id=str(uuid.uuid4()),timestamp=utc_now(),source='desktop',**fields)))
        if definition['id'] not in BUILTINS and draft.get('index_action') not in {'create','migrate'}:
            if old:
                if old.get('category') != definition.get('category') or old.get('kind') != definition.get('kind'):
                    raise ValueError('Existing category and measurement kind are fixed. Create a new card to change them.')
                definition.pop('progress', None)
                definition['metadata'] = old.get('metadata', {}) | definition.get('metadata', {})
                definition['created_date'] = old.get('created_date', '')
            operations.append(command('upsert', definition=definition))
        if definition['id'] in store.get('indexes',{}) and draft.get('update_progress'): raise ValueError('Index counts come only from topic evidence.')
        if draft.get('update_progress'):
            if not draft['definition'].get('progress_enabled'):
                raise ValueError('Enable progress before applying a recorded progress update.')
            p = draft['definition'].get('progress') or {}
            operations.append(command('progress_set', achievement_id=definition['id'], **{k: v for k, v in p.items() if k in {'current_value', 'target_value', 'unit'}}))
        if draft.get('update_icon'):
            operations.append(command('icon_set', achievement_id=definition['id'], icon=validate_icon(draft['icon'])))
        if not operations: raise ValueError('Built-in measurement rules are protected. Choose an icon or explicitly update progress.')
        return operations

    def deliver(self, value):
        """Persist exact payloads BEFORE send. Uncertain operations are never auto-retried."""
        journal = read_document(self.private / 'deliveries.json', [])
        if value.get('retry_id'):
            item = next((d for d in journal if d['id'] == value['retry_id']), None)
            if item is None: raise ValueError('Unknown saved delivery.')
            if item['state'] == 'applied': return item
        else:
            kind = value['kind']
            if kind == 'card': requests = [dict(action='achievements', achievement=c) for c in self.card_commands(value['draft'])]
            elif kind == 'batch':
                from .draft_input import batch
                requests = [dict(action='achievements', achievement=c) for c in batch(value['draft'], canonical=True)['draft']]
            elif kind == 'command': requests = [dict(action='achievements', achievement=validate_command(value['draft']))]
            elif kind == 'session':
                plan = copy.deepcopy(validate_plan(value['draft']))
                # Every template launch is a fresh execution. A saved retry keeps this ID.
                plan['session_id'] = str(uuid.uuid4()); plan['created_at'] = utc_now()
                requests = [dict(action='start' if value.get('start') is True else 'send', session=plan)]
            else: raise ValueError('Unknown draft kind.')
            for req in requests:
                if req['action'] != 'achievements': continue
                for previous in journal:
                    for saved in previous['requests']:
                        if saved['action'] == 'achievements' and saved['achievement']['id'] == req['achievement']['id'] and saved['achievement'] != req['achievement']:
                            raise ValueError('This receipt was already used with different fields. Create a new update or retry the saved delivery.')
            item = dict(id=str(uuid.uuid4()), kind=kind, title=value.get('title', kind), state='pending', step=0,
                        created_at=utc_now(), requests=requests, error=None)
            journal.append(item)
        atomic_json(self.private / 'deliveries.json', journal)
        while item['step'] < len(item['requests']):
            req = item['requests'][item['step']]
            try:
                status = self.request({'action': 'status'})
                if status.get('pending_command'): raise ValueError('A command still awaits phone acknowledgement. Reconnect and refresh before retrying.')
                if req['action'] in {'send','start'} and (status.get('state') or {}).get('session_id') == req['session']['session_id']:
                    item['step'] += 1  # Reconcile an uncertain session delivery from authoritative state.
                else:
                    result = self.transport(req)
                    item['transport_id'] = result.get('command_id')
                    if not result.get('ok'): raise ValueError(result.get('error') or 'Phone rejected this operation.')
                    item['step'] += 1
                item['state'] = 'applied' if item['step'] == len(item['requests']) else 'pending'
                item['error'] = None
            except (OSError, ValueError, TimeoutError) as exc:
                item['state'] = 'needs_review'; item['error'] = str(exc)
                atomic_json(self.private / 'deliveries.json', journal)
                return item
            atomic_json(self.private / 'deliveries.json', journal)
        return item

    def selected_report(self, key):
        # Filter through tombstones on every copy/save, even if a report viewer is already open.
        report = next((r for r in load_reports(self.root / 'reports') if f"{r['session_id']}:{r['started_at_ms']}" == key), None)
        if report is None: raise ValueError('Report no longer exists.')
        return report

    def handle(self, action, value=None):
        if action=='index-preview':
            from .index_tracker import import_index,preview
            current=self.snapshot()['achievements'].get('ledger',{}).get('indexes',{}).get((value or {}).get('id'))
            return preview(import_index(value['source']),current)
        if action=='index-evidence':
            from .index_tracker import evidence
            return evidence(self.snapshot()['achievements'],value['id'])
        value = value or {}
        with self.lock:
            if action == 'snapshot': return self.snapshot(value.get('icon_version'))
            if action == 'parse': return parse_draft(value['text'])
            if action == 'icon': return normalize_image(base64.b64decode(value['data'], validate=True))
            if action in {'import-icon-paths', 'import-icons'}:
                files = read_inputs(value['paths']) if action == 'import-icon-paths' else read_uploaded(value['files'])
                entries, imported = import_entries(read_document(self.private / 'icon-library.json', []), files, normalize_image)
                atomic_json(self.private / 'icon-library.json', entries)
                return dict(entries=entries, imported=imported)
            if action == 'remove-library-icons':
                selected = value['ids']
                entries = [i for i in read_document(self.private / 'icon-library.json', []) if i['id'] not in selected]
                atomic_json(self.private / 'icon-library.json', entries)
                return dict(entries=entries)
            if action == 'export-icons':
                destination = Path(value['directory'])
                if not destination.is_dir(): raise ValueError('Choose an existing export folder.')
                folder = destination / ('FocusDeck-icons-' + uuid.uuid4().hex[:8])
                folder.mkdir(mode=0o700)
                return export_pack(read_document(self.private / 'icon-library.json', []), value['ids'], folder / 'FocusDeck-icons.zip')
            if action == 'performance-report':
                from .performance import performance_report, period_bounds
                scope=value.get('scope');begin=value.get('begin');end=value.get('end')
                if scope not in {'day','week','month','year','all'}: raise ValueError('Invalid Analytics scope')
                a,b=dt.date.fromisoformat(begin),dt.date.fromisoformat(end)
                if b<a: raise ValueError('Invalid Analytics boundaries')
                if scope!='all' and period_bounds(scope,a)!=(a,b): raise ValueError('Use calendar period boundaries')
                snap=self.snapshot()  # Re-read tombstones and canonical archives on every copy.
                data=dataset(snap['reports'],(snap.get('observed') or {}).get('state'))
                return performance_report(data,scope,begin,end)
            if action == 'save-layout':
                from .layouts import validate_layout
                template = value.get('template')
                if template not in {p+'@'+f for p in ('overview', 'achievements', 'analytics-day', 'analytics-week', 'analytics-aggregate') for f in ('wide','compact')}: raise ValueError('Invalid page layout')
                layout = validate_layout(value.get('layout'))
                layouts = read_document(self.private / 'layouts.json', {})
                layouts[template] = layout
                atomic_json(self.private / 'layouts.json', layouts)
                return layouts
            if action == 'achievement-order':
                snapshot = self.snapshot()
                c = validate_command(dict(achievement_version=6,operation='layout_order',id=value['receipt_id'],timestamp=value['timestamp'],
                    source='FocusDeck Control Center',order=value['order'],expected_store_revision=value['revision']))
                return self.deliver(dict(kind='command',draft=c,title='Achievement order'))
            if action == 'ui':
                if set(value) != {'sidebar_collapsed'} or type(value['sidebar_collapsed']) is not bool:
                    raise ValueError('Invalid sidebar preference.')
                atomic_json(self.private / 'ui.json', value)
                return value

            if action == 'save-draft':
                if value.get('kind') not in {'card', 'session', 'command', 'batch'}: raise ValueError('Invalid draft type.')
                drafts = read_document(self.private / 'drafts.json', {}); drafts[value['kind']] = value['draft']; drafts['_active'] = value['kind']
                atomic_json(self.private / 'drafts.json', drafts); return dict(saved=True)
            if action == 'save-template':
                draft = validate_plan(value['draft']); templates = read_document(self.private / 'templates.json', [])
                templates.append(dict(id=str(uuid.uuid4()), name=draft['title'], plan=draft, created_at=utc_now()))
                atomic_json(self.private / 'templates.json', templates); return dict(saved=True)
            if action == 'delete-template':
                atomic_json(self.private / 'templates.json', [t for t in read_document(self.private / 'templates.json', []) if t['id'] != value['id']]); return dict(saved=True)
            if action == 'settings':
                settings = value
                if set(settings) != set(DEFAULTS) or settings['palette'] not in PALETTES or settings['motion'] not in {'full', 'reduced', 'off'} or settings['density'] not in {'comfortable', 'compact', 'spacious'}:
                    raise ValueError('Invalid display settings.')
                if type(settings['visuals']) is not bool or any(type(settings[k]) is not int or not 1 <= settings[k] <= 1440 for k in ('focus_minutes', 'break_minutes')): raise ValueError('Invalid default durations.')
                atomic_json(self.private / 'settings.json', settings); return settings
            if action == 'deliver': return self.deliver(value)
            if action == 'card-action':
                operation = value['operation']
                if operation not in {'archive', 'restore', 'delete', 'event', 'initialize', 'streak_break', 'progress_increment'}: raise ValueError('Invalid card operation.')
                fields = dict(achievement_id=value['id'])
                if operation == 'delete':
                    if value.get('confirm') is not True: raise ValueError('Confirm permanent card deletion.')
                    fields['confirm'] = True
                if operation == 'event': fields.update(event='completed', amount=1, text='Manually recorded from desktop')
                if operation in {'initialize', 'streak_break'}:
                    if value.get('confirm') is not True: raise ValueError('Confirm the abstinence event and its actual timestamp.')
                    event = command('event', **fields, event=operation, amount=1, text='Explicitly recorded from desktop')
                    event['timestamp'] = value['timestamp']; validate_command(event)
                    return self.deliver(dict(kind='command', draft=event, title=operation))
                if operation == 'progress_increment': fields['amount'] = value['amount']
                return self.deliver(dict(kind='command', draft=command(operation, **fields), title=operation))
            if action == 'today-target':
                if value.get('confirm') is not True: raise ValueError('Confirm today’s objective.')
                snapshot = self.snapshot(); achievement = next((d for d in snapshot['achievements'].get('ledger', {}).get('definitions', []) if d['id'] == value['id']), None)
                if not achievement or achievement.get('archived') or not achievement.get('streak_enabled', True) or achievement.get('kind') not in {'daily', 'scheduled'}:
                    raise ValueError('Today’s objective requires a visible manual daily/scheduled streak card.')
                today = dt.datetime.now(ZoneInfo(snapshot['achievements']['zone'])).date().isoformat()
                targets = [t for t in snapshot['achievements']['ledger'].get('targets', []) if t['date'] == today]
                latest = max(targets, key=lambda t: (t['atMs'], t['id'])) if targets else {}
                objectives = [o for o in latest.get('objectives', []) if not (o['kind'] == 'achievement' and o.get('achievement_id') == value['id'])]
                objectives.append(dict(kind='achievement', achievement_id=value['id'], minimum=value['minimum'], text=value.get('text', '')))
                return self.deliver(dict(kind='command', draft=command('daily_target', date=today, objectives=objectives), title='Today’s objective'))
            if action == 'session-control':
                operation = value['operation']
                if operation not in {'pause', 'resume', 'next', 'skip_block', 'end_block', 'stop', 'reward_done', 'reward_continue'}: raise ValueError('Invalid session control.')
                if operation in {'stop', 'end_block'} and value.get('confirm') is not True: raise ValueError('Confirm the end action.')
                status = self.request({'action': 'status'}); state = status.get('state') or {}
                if not status.get('connected') or state.get('session_id') != value.get('session_id') or state.get('block_index') != value.get('block_index'):
                    raise ValueError('The observed session changed. Refresh before acting.')
                return self.request(dict(action=operation, expected_session_id=value['session_id'], expected_block_index=value['block_index']))
            if action == 'pairing': return self.request(dict(action='pairing', viewer=bool(value.get('viewer'))))
            if action == 'rename-device':
                names = read_json(self.private / 'device-names.json', {}); names[value['id']] = str(value['name'])[:100]
                atomic_json(self.private / 'device-names.json', names); return names
            if action == 'remove-known-device':
                current = self.snapshot()
                online = current['devices'].get('devices', []) + current['devices'].get('viewers', [])
                if any(d['device_id'] == value['id'] for d in online): raise ValueError('A connected device cannot be removed from saved entries.')
                known = read_document(self.private / 'known-devices.json', {}); known.pop(value['id'], None)
                atomic_json(self.private / 'known-devices.json', known); return dict(removed=True)
            if action == 'names': return read_json(self.private / 'device-names.json', {})
            if action == 'validate-draft':
                if value['kind'] == 'session': return validate_plan(value['draft'])
                if value['kind'] == 'batch':
                    from .draft_input import batch
                    return batch(value['draft'], canonical=True)['draft']
                return validate_command(value['draft'])
            if action == 'restart-service':
                import subprocess
                result = subprocess.run(['systemctl','--user','restart','focusdeck.service'], capture_output=True, text=True, timeout=20)
                if result.returncode: raise ValueError(result.stderr.strip()[:1000] or 'The installed desktop companion could not start.')
                return dict(started=True)
            if action == 'forget-device':
                if value.get('confirm') is not True: raise ValueError('Confirm forgetting the disconnected phone pairing.')
                return self.request(dict(action='forget-device'))
            if action == 'delete-reports':
                if value.get('confirm') is not True: raise ValueError('Confirm permanent report deletion on PC and phone.')
                if value.get('all') is True: payload = dict(action='delete-logs', all=True)
                else:
                    self.selected_report(value['key'])
                    payload = dict(action='delete-logs', report_key=value['key'])
                return self.request(payload)
            if action == 'refresh-logs':
                from . import deletions
                deletions.apply(self.root, deletions.validate(read_json(self.root / 'deletions.json', deletions.empty())))
                return dict(sessions=len(load_reports(self.root / 'reports')))
            if action in {'report-text', 'save-report'}:
                report = self.selected_report(value['key'])
                format = value.get('format', 'text')
                if format not in {'text', 'json'}: raise ValueError('Choose text or JSON output.')
                text = markdown(report) if format == 'text' else json.dumps(report, ensure_ascii=False, indent=2) + '\n'
                if action == 'report-text': return dict(text=text, title=report['title'])
                # Native chooser owns the path and overwrite confirmation; use an atomic UTF-8 save.
                import os, tempfile
                destination = Path(value['path'])
                if destination.resolve().is_relative_to(self.root.resolve()): raise ValueError('Save the copy outside FocusDeck’s private data directory.')
                if not destination.parent.is_dir(): raise ValueError('Choose an existing folder.')
                descriptor, temporary = tempfile.mkstemp(prefix='.focusdeck-report-', dir=destination.parent)
                try:
                    with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
                        output.write(text); output.flush(); os.fsync(output.fileno())
                    os.replace(temporary, destination)
                finally:
                    if os.path.exists(temporary): os.unlink(temporary)
                return dict(file=str(destination))
            if action == 'export':
                destination = Path(value['directory'])
                if not destination.is_dir(): raise ValueError('Choose an existing export folder.')
                folder = destination / ('FocusDeck-export-' + dt.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:6])
                folder.mkdir(mode=0o700)
                selected = self.selected_report(value['key']) if value.get('key') else None
                session_id = selected['session_id'] if selected else None
                results = [export_archive(self.root / 'reports', folder / 'reports.json', format='json', session_id=session_id)]
                results += [export_archive(self.root / 'reports', folder / f'{kind}.csv', kind=kind, session_id=session_id) for kind in ('sessions', 'blocks', 'events', 'intervals')]
                return dict(directory=str(folder), files=results)
            raise ValueError('Unknown control panel action.')
