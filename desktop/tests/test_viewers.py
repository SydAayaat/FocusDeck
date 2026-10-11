"""Real TLS isolation, simultaneous viewers, cached snapshots and bounded transfers."""
import asyncio
import copy
import json
import unittest
from websockets.asyncio.client import connect
from websockets.exceptions import InvalidStatus, ConnectionClosed
import test_focusdeck as baseline
import test_achievements as achievement_tests
from focusdeck.common import atomic_json, pairing_info
from focusdeck.reports import save_report
import test_schemas as schema_tests


class ViewerTests(unittest.IsolatedAsyncioTestCase):
    asyncSetUp = baseline.TransportTests.asyncSetUp
    asyncTearDown = baseline.TransportTests.asyncTearDown
    start_listener = baseline.TransportTests.start_listener
    ack = baseline.TransportTests.ack

    async def phone(self):
        ws = await connect(f'wss://127.0.0.1:{self.port}/v1/device', ssl=self.client_tls,
            additional_headers={'Authorization': 'Bearer '+self.cfg['token']})
        await ws.send(json.dumps(dict(protocol_version=1, type='hello', device_id='phone-1', name='Phone',
            role='phone', supported_session_versions=[1,2], supported_achievement_versions=[1,2,3,4], supported_viewer_version=1)))
        self.assertEqual(json.loads(await ws.recv())['type'], 'hello_ack')
        self.assertEqual(json.loads(await ws.recv())['type'], 'viewer_interest')
        return ws

    async def viewer(self, identifier='tablet-1', visible=True, schedule_version=None, achievement_versions=None):
        ws = await connect(f'wss://127.0.0.1:{self.port}/v1/viewer', ssl=self.client_tls,
            additional_headers={'Authorization': 'Bearer '+self.cfg['viewer_token']})
        hello=dict(protocol_version=1,type='hello',device_id=identifier,name=identifier,role='viewer',supported_viewer_version=1)
        if schedule_version is not None:hello['supported_schedule_version']=schedule_version
        if achievement_versions is not None:hello['supported_achievement_versions']=achievement_versions
        await ws.send(json.dumps(hello))
        self.assertEqual(json.loads(await ws.recv())['role'], 'viewer')
        await ws.send(json.dumps(dict(protocol_version=1, type='viewer_presence', visible=visible)))
        return ws

    async def through(self, ws, kind, predicate=lambda m: True):
        async with asyncio.timeout(5):
            while True:
                msg = json.loads(await ws.recv())
                if msg['type'] == kind and predicate(msg): return msg

    async def snapshot(self, ws):
        docs = []; raw = ''; status = None
        async with asyncio.timeout(5):
            while True:
                msg = json.loads(await ws.recv())
                self.assertNotEqual(msg['type'], 'command')
                if msg['type'] == 'viewer_status': status = msg
                elif msg['type'] == 'viewer_chunk':
                    self.assertLessEqual(len(msg['data']), 32000)
                    raw += msg['data']
                    await ws.send(json.dumps(dict(protocol_version=1, type='viewer_ack', transfer_id=msg['transfer_id'], index=msg['index'])))
                    if msg['index'] == msg['count']-1:
                        docs.append((msg['document'], json.loads(raw))); raw = ''
                elif msg['type'] == 'viewer_reports_end': return status, docs

    async def seed(self, phone=None):
        state = dict(state={'phase':'active','session':baseline.plan(),'index':0,'activeMs':42}, clock={'wall':1760000000000,'mono':10000,'boot':2})
        if phone is None:
            await self.deck.viewers.state_received('phone-1', state)
        else:
            await phone.send(json.dumps(dict(protocol_version=1,type='viewer_state',data=state)))
            report = baseline.report()
            await phone.send(json.dumps(dict(protocol_version=1,type='report',report_key='../untrusted:100000',report=report)))
            await self.through(phone, 'report_ack')  # Ordered barrier after durable viewer state.

        value = achievement_tests.AchievementSnapshotTests().snapshot()
        atomic_json(self.deck.root/'achievements.json', value)
        await self.deck.viewers.ledger_received('phone-1')
        save_report(self.deck.root/'reports', baseline.report())
        return state, value

    async def test_phone_and_two_tablets_command_routing_and_snapshots(self):
        async with await self.phone() as phone:
            state, ledger = await self.seed(phone)
            async with await self.viewer() as tablet, await self.viewer('tablet-2') as second:
                # Neither viewer has ACKed its first chunk: delivery cannot hold phone commands.
                pending = asyncio.create_task(self.deck.control(dict(action='start', session=baseline.plan())))
                sent = await self.through(phone, 'command'); self.assertEqual(sent['action'], 'start')
                await self.ack(phone, sent); self.assertTrue((await pending)['ok'])
                for viewer in (tablet, second):
                    status, docs = await self.snapshot(viewer)
                    self.assertTrue(status['phone_connected'])
                    self.assertEqual(dict(docs)['state']['state'], state['state'])
                    self.assertEqual(dict(docs)['ledger']['ledger'], ledger['ledger'])
                    self.assertEqual(dict(docs)['report']['report'], baseline.report())
                devices = await self.deck.control({'action':'devices'})
                self.assertEqual(devices['paired_id'], 'phone-1')
                self.assertEqual(len(devices['viewers']), 2)
                self.assertEqual(self.deck.saved['device_id'], 'phone-1')

    async def test_role_tokens_are_separate_and_legacy_pairing_unchanged(self):
        normal = pairing_info(self.cfg); viewer = pairing_info(self.cfg, True)
        self.assertNotIn('role', normal); self.assertEqual(viewer['role'], 'viewer')
        self.assertNotEqual(normal['token'], viewer['token'])
        for endpoint, token in [('device', viewer['token']), ('viewer', normal['token'])]:
            with self.subTest(endpoint=endpoint), self.assertRaises(InvalidStatus) as caught:
                await connect(f'wss://127.0.0.1:{self.port}/v1/{endpoint}', ssl=self.client_tls,
                    additional_headers={'Authorization':'Bearer '+token})
            self.assertEqual(caught.exception.response.status_code, 401)
        with self.assertRaises(InvalidStatus) as caught:
            await connect(f'wss://127.0.0.1:{self.port}/v1/viewer', ssl=self.client_tls,
                origin='https://example.org', additional_headers={'Authorization':'Bearer '+viewer['token']})
        self.assertEqual(caught.exception.response.status_code,403)

    async def test_viewer_writes_are_closed_without_changing_authority_or_archive(self):
        async with await self.phone():
            await self.seed()
            before = copy.deepcopy(self.deck.saved)
            ledger = (self.deck.root/'achievements.json').read_bytes()
            for kind in ('command', 'report', 'state', 'viewer_state', 'achievement_snapshot', 'history_delete', 'ack'):
                async with await self.viewer(visible=False) as viewer:
                    await viewer.send(json.dumps(dict(protocol_version=1, type=kind)))
                    with self.assertRaises(ConnectionClosed) as caught: await viewer.recv()
                    self.assertEqual(caught.exception.rcvd.code,1008)
                self.assertEqual(self.deck.saved,before)
                self.assertEqual((self.deck.root/'achievements.json').read_bytes(),ledger)
                self.assertEqual(len(list((self.deck.root/'reports').glob('*.json'))),1)

    async def test_background_viewers_stop_fast_phone_interest(self):
        async with await self.phone() as phone, await self.viewer() as viewer:
            await self.through(phone, 'viewer_interest', lambda m:m['enabled'])
            await self.snapshot(viewer)
            await viewer.send(json.dumps(dict(protocol_version=1,type='viewer_presence',visible=False)))
            await self.through(phone, 'viewer_interest', lambda m:not m['enabled'])
            await self.deck.viewers.state_received('phone-1',dict(state={},clock=dict(wall=1,mono=1,boot=1)))
            with self.assertRaises(asyncio.TimeoutError): await asyncio.wait_for(viewer.recv(),.1)
            await viewer.send(json.dumps(dict(protocol_version=1,type='viewer_presence',visible=True)))
            await self.through(phone, 'viewer_interest', lambda m:m['enabled'])
            msg = await self.through(viewer, 'viewer_chunk'); self.assertEqual(msg['document'],'state')

    async def test_offline_reconnect_cache_deletions_and_large_chunked_ledger(self):
        phone = await self.phone(); await self.seed()
        value = json.loads((self.deck.root/'achievements.json').read_text())
        value['ledger']['test_retained_evidence'] = 'x'*100000
        atomic_json(self.deck.root/'achievements.json',value)
        await phone.close()
        async with await self.viewer() as viewer:
            status, docs = await self.snapshot(viewer)
            self.assertFalse(status['phone_connected'])
            self.assertEqual(len(dict(docs)['ledger']['ledger']['test_retained_evidence']),100000)
            await self.deck.merge_deletions(dict(before_ms=0,report_keys=['../untrusted:100000'],revision=''))
            _, docs = await self.snapshot(viewer)
            self.assertFalse(any(kind=='report' for kind,_ in docs))
        # Desktop restart retains offline source metadata and full history/tombstones.
        from focusdeck.server import DeckServer
        restarted = DeckServer(self.cfg,self.deck.root)
        self.assertEqual(restarted.viewers.state['authority_device_id'],'phone-1')
        self.assertEqual(restarted.viewers.metadata['authority_device_id'],'phone-1')

    async def test_legacy_phone_gets_no_viewer_frames_and_other_phone_is_rejected(self):
        async with await baseline.TransportTests.phone(self) as legacy:
            async with await self.viewer() as viewer:
                status, _ = await self.snapshot(viewer)
                self.assertFalse(status['phone_supports_viewer'])
                with self.assertRaises(asyncio.TimeoutError): await asyncio.wait_for(legacy.recv(), .1)
                ws = await connect(f'wss://127.0.0.1:{self.port}/v1/device', ssl=self.client_tls,
                    additional_headers={'Authorization':'Bearer '+self.cfg['token']})
                await ws.send(json.dumps(dict(protocol_version=1,type='hello',device_id='other-phone',name='Other')))
                with self.assertRaises(ConnectionClosed): await ws.recv()
                await ws.close()
                self.assertEqual(self.deck.saved['device_id'],'phone-1')

    async def test_wire_viewer_contracts_and_pairing_schema(self):
        checker = schema_tests.SchemaTests(); checker.setUp()
        for viewer in (False,True):checker.validate('pairing.schema.json',pairing_info(self.cfg,viewer))
        for frame in [dict(type='hello',device_id='tablet',name='Tablet',role='viewer',supported_viewer_version=1),
            dict(type='viewer_status',desktop_id='desktop',authority_device_id=None,phone_connected=False,phone_supports_viewer=False),
            dict(type='viewer_chunk',transfer_id='id',document='ledger',index=0,count=1,data='{}'),
            dict(type='viewer_ack',transfer_id='id',index=0),dict(type='viewer_presence',visible=True),
            dict(type='viewer_interest',enabled=True),dict(type='viewer_state',data=dict(state={},clock=dict(wall=1,mono=1,boot=-1))),
            dict(type='viewer_reports_begin',sync_id='id',desktop_id='pc',authority_device_id='phone'),
            dict(type='viewer_reports_end',sync_id='id',as_of_ms=1)]:
            checker.validate('wire.schema.json',dict(protocol_version=1,**frame))


    async def test_old_viewers_receive_compatible_projection_without_mutating_source(self):
        async with await self.phone() as phone:
            ledger=dict(ledger=dict(definitions=[{'id':'german'}],commands={'order-1':dict(achievement_version=6,operation='layout_order',order=['german'])},cardOrder=['german']),as_of_ms=1,zone='UTC')
            atomic_json(self.deck.root/'achievements.json',ledger);await self.deck.viewers.ledger_received('phone-1')
            from test_calendar_analytics import fixture
            r=fixture('2026-10-09T10:00:00Z');r['schedule_snapshot']=dict(session_schedule=None,activities=[dict(id='a',title='First',type='focus',schedule={'planned_start':'2026-10-09T10:00:00Z'})]);save_report(self.deck.root/'reports',r)
            state=dict(clock=dict(wall=1,mono=1,boot=1),state=dict(session=dict(schedule={'planned_start':'2026-10-09T10:00:00Z'},blocks=[dict(id='a',schedule=None,category='learning',achievement_id='german',index_topic_id=None)])))
            await self.deck.viewers.state_received('phone-1',state)
            old_source=copy.deepcopy(self.deck.viewers.state)
            async with await self.viewer('old') as old:
                _,docs=await self.snapshot(old);values=dict(docs);self.assertNotIn('schedule',values['state']['state']['session']);self.assertEqual(values['ledger']['ledger']['commands'],{});self.assertNotIn('schedule_snapshot',values['report']['report'])
            async with await self.viewer('new',schedule_version=1,achievement_versions=[1,2,3,4,5,6]) as new:
                _,docs=await self.snapshot(new);values=dict(docs);self.assertEqual(values['ledger']['ledger'],ledger['ledger']);self.assertEqual(values['report']['report']['schedule_snapshot'],r['schedule_snapshot']);self.assertIn('schedule',values['state']['state']['session'])
            self.assertEqual(self.deck.viewers.state,old_source);self.assertEqual(json.loads((self.deck.root/'achievements.json').read_text()),ledger)


if __name__ == '__main__': unittest.main()
