"""Authenticated read-only viewers. The paired phone remains the sole writer."""
import asyncio
import json
import copy
import time
import uuid
from websockets.exceptions import ConnectionClosed
from .common import atomic_json, read_json, text_field
from .history import load_reports


class ViewerHub:
    def __init__(self, server):
        self.server = server
        self.clients = {}
        self.metadata = read_json(server.root / 'viewer-source.json', {})
        self.state = read_json(server.root / 'viewer-state.json', None)

    def notify(self, *kinds):
        for client in self.clients.values():
            client['dirty'].update(kinds); client['wake'].set()

    async def interest(self):
        if self.server.phone and (self.server.device or {}).get('supported_viewer_version') == 1:
            try:
                await self.server.phone.send(json.dumps(self.server.envelope('viewer_interest', enabled=any(c['visible'] for c in self.clients.values()))))
            except ConnectionClosed:
                pass

    async def state_received(self, authority, value):
        if not isinstance(value, dict) or not isinstance(value.get('state'), dict) or not isinstance(value.get('clock'), dict):
            raise ValueError('Invalid viewer state')
        clock = value['clock']
        if set(clock) != {'wall', 'mono', 'boot'} or any(type(clock[k]) is not int for k in clock) or clock['wall'] < 0 or clock['mono'] < 0:
            raise ValueError('Invalid viewer clock')
        self.state = dict(value, authority_device_id=authority, desktop_id=self.server.cfg['desktop_id'])
        await asyncio.to_thread(atomic_json, self.server.root / 'viewer-state.json', self.state)
        self.notify('state')

    async def ledger_received(self, authority):
        self.metadata = dict(authority_device_id=authority)
        await asyncio.to_thread(atomic_json, self.server.root / 'viewer-source.json', self.metadata)
        self.notify('ledger')

    def status(self):
        return self.server.envelope('viewer_status', desktop_id=self.server.cfg['desktop_id'],
            authority_device_id=self.server.saved['device_id'], phone_connected=self.server.phone is not None,
            phone_supports_viewer=(self.server.device or {}).get('supported_viewer_version') == 1)

    async def chunks(self, client, document, value):
        # ASCII JSON makes chunk sizes independent of Unicode encoding. One in-flight chunk.
        raw = await asyncio.to_thread(json.dumps, value, ensure_ascii=True, separators=(',', ':'))
        transfer = str(uuid.uuid4()); count = max(1, (len(raw) + 31999) // 32000)
        for index in range(count):
            await client['ws'].send(json.dumps(self.server.envelope('viewer_chunk', transfer_id=transfer,
                document=document, index=index, count=count, data=raw[index * 32000:(index + 1) * 32000])))
            ack = await asyncio.wait_for(client['acks'].get(), 30)
            if ack != (transfer, index): raise ValueError('Out-of-order viewer acknowledgment')

    async def publish(self, client, kinds):
        server = self.server; authority = server.saved['device_id']
        await client['ws'].send(json.dumps(self.status()))
        if self.state and self.state.get('authority_device_id') == authority:
            state=self.state
            if client.get('schedule_version')!=1:
                state=copy.deepcopy(state)
                plan=state.get('state',{}).get('session')
                if plan:
                    plan.pop('schedule',None)
                    for b in plan.get('blocks',[]):
                        for field in ('schedule','category','achievement_id','index_topic_id'):b.pop(field,None)
            await self.chunks(client, 'state', state)
        if 'ledger' in kinds and authority and self.metadata.get('authority_device_id') == authority:
            path = server.root / 'achievements.json'
            if path.exists():
                # The existing achievement ledger has no history-age or file-size cap.
                def ledger():
                    with path.open(encoding='utf-8') as source: return json.load(source)
                value = await asyncio.to_thread(ledger)
                value=copy.deepcopy(value)
                if 5 not in client.get('achievement_versions',[]):
                    value['ledger'].pop('indexes',None)
                    for c in value['ledger'].get('commands',{}).values():
                        for key in ('document','configuration','expected_index_revision','expected_store_revision','topic_id','completed','note','evidence'): c.pop(key,None)
                if 6 not in client.get('achievement_versions',[]) and 'commands' in value['ledger']:
                    commands=value['ledger'].get('commands',{})
                    value['ledger']['commands']={key:c for key,c in commands.items() if c.get('achievement_version')!=6}
                    for c in value['ledger']['commands'].values():c.pop('order',None)
                await self.chunks(client, 'ledger', dict(desktop_id=server.cfg['desktop_id'], authority_device_id=authority,
                    ledger=value['ledger'], as_of_ms=value['as_of_ms'], zone=value['zone']))
        if 'reports' in kinds and authority:
            # Keep a consistent deletion snapshot and never publish an unfinished batch.
            async with server.archive_lock:
                reports = await asyncio.to_thread(load_reports, server.root / 'reports')
            sync = str(uuid.uuid4())
            await client['ws'].send(json.dumps(server.envelope('viewer_reports_begin', sync_id=sync,
                desktop_id=server.cfg['desktop_id'], authority_device_id=authority)))
            for report in reports:
                if client.get('schedule_version')!=1:report={k:v for k,v in report.items() if k!='schedule_snapshot'}
                await self.chunks(client, 'report', dict(sync_id=sync, report=report))
            await client['ws'].send(json.dumps(server.envelope('viewer_reports_end', sync_id=sync, as_of_ms=int(time.time()*1000))))

    async def worker(self, client):
        while True:
            await client['wake'].wait(); client['wake'].clear()
            if not client['visible']: continue
            kinds = set(client['dirty']); client['dirty'].clear()
            await self.publish(client, kinds)

    async def handle(self, ws):
        identifier = None; worker = None; reader = None
        try:
            hello = json.loads(await asyncio.wait_for(ws.recv(), 10))
            if not isinstance(hello, dict) or hello.get('protocol_version') != 1 or hello.get('type') != 'hello' or hello.get('role') != 'viewer' or hello.get('supported_viewer_version') != 1:
                raise ValueError('Expected read-only viewer hello')
            identifier = text_field(hello.get('device_id'), 'viewer device_id', 100)
            text_field(hello.get('name'), 'viewer name', 100)
            if identifier == self.server.saved['device_id'] or identifier in self.clients or len(self.clients) >= 8:
                raise ValueError('Viewer identity already connected or viewer limit reached')
            client = dict(ws=ws, name=hello['name'], visible=False, dirty={'state', 'ledger', 'reports'}, wake=asyncio.Event(), acks=asyncio.Queue(maxsize=1))
            client['achievement_versions']=hello.get('supported_achievement_versions',[])
            client['schedule_version']=hello.get('supported_schedule_version',0)
            if type(client['schedule_version']) is not int or client['schedule_version'] not in (0,1):raise ValueError('Invalid schedule capability')
            self.clients[identifier] = client
            await ws.send(json.dumps(self.server.envelope('hello_ack', desktop_id=self.server.cfg['desktop_id'], role='viewer', supported_viewer_version=1)))
            client['wake'].set(); worker = asyncio.create_task(self.worker(client))
            await self.interest()
            async def receive():
                async for raw in ws:
                    msg = json.loads(raw)
                    # Viewers cannot send commands, reports, ledgers, state, or deletion intents.
                    if not isinstance(msg, dict) or msg.get('protocol_version') != 1:
                        raise ValueError('Viewer connection is read-only')
                    if msg.get('type') == 'viewer_presence' and type(msg.get('visible')) is bool:
                        client['visible'] = msg['visible']
                        if client['visible']: client['dirty'].add('state'); client['wake'].set()
                        await self.interest()
                        continue
                    if msg.get('type') != 'viewer_ack' or type(msg.get('index')) is not int or msg['index'] < 0:
                        raise ValueError('Viewer connection is read-only')
                    transfer = text_field(msg.get('transfer_id'), 'transfer_id', 100)
                    client['acks'].put_nowait((transfer, msg['index']))
            reader = asyncio.create_task(receive())
            done, pending = await asyncio.wait((worker, reader), return_when=asyncio.FIRST_COMPLETED)
            for task in pending: task.cancel()
            await asyncio.gather(*pending, return_exceptions=True)
            for task in done: task.result()
        except (ValueError, TypeError, KeyError, OSError, asyncio.TimeoutError, asyncio.QueueFull) as exc:
            await ws.close(code=1008, reason=str(exc)[:120])
        except ConnectionClosed:
            pass
        finally:
            tasks = [task for task in (worker, reader) if task is not None]
            for task in tasks: task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            if identifier and self.clients.get(identifier, {}).get('ws') is ws:
                self.clients.pop(identifier); await self.interest()
