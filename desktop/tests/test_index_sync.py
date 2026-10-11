import asyncio,json
from websockets.asyncio.client import connect
from websockets.exceptions import ConnectionClosed
from focusdeck.common import atomic_json
from test_viewers import ViewerTests
class IndexSyncTests(ViewerTests):
 async def phone(self):
  ws=await connect(f'wss://127.0.0.1:{self.port}/v1/device',ssl=self.client_tls,additional_headers={'Authorization':'Bearer '+self.cfg['token']})
  await ws.send(json.dumps(dict(protocol_version=1,type='hello',device_id='phone-1',name='Phone',role='phone',supported_session_versions=[1,2],supported_achievement_versions=[1,2,3,4,5],supported_viewer_version=1)))
  self.assertIn(5,json.loads(await ws.recv())['supported_achievement_versions']);await ws.recv();return ws
 async def test_index_delivery_and_readonly_snapshot(self):
  async with await self.phone() as phone:
   command=dict(achievement_version=5,operation='index_topic',id='topic-receipt',timestamp='2026-10-08T12:00:00Z',achievement_id='subject',topic_id='1.1',completed=True,expected_index_revision=1)
   pending=asyncio.create_task(self.deck.control(dict(action='achievements',achievement=command)))
   sent=await self.through(phone,'command');self.assertEqual(command,sent['achievement']);await self.ack(phone,sent);self.assertTrue((await pending)['ok'])
   _,snap=await self.seed(phone);snap['ledger']['indexes']={'subject':{'configuration':'HYBRID','document':{'topics':[{'id':'1.1','title':'Actual topic'}]},'events':[{'id':'e','topicId':'1.1','date':'2026-10-08','delta':1}],'revision':2}};atomic_json(self.deck.root/'achievements.json',snap);await self.deck.viewers.ledger_received('phone-1')
   async with await connect(f'wss://127.0.0.1:{self.port}/v1/viewer',ssl=self.client_tls,additional_headers={'Authorization':'Bearer '+self.cfg['viewer_token']}) as tablet:
    await tablet.send(json.dumps(dict(protocol_version=1,type='hello',device_id='tablet-idx',name='Tablet',role='viewer',supported_viewer_version=1,supported_achievement_versions=[1,2,3,4,5])));await tablet.recv();await tablet.send(json.dumps(dict(protocol_version=1,type='viewer_presence',visible=True)))
    _,docs=await self.snapshot(tablet);self.assertEqual(snap['ledger']['indexes'],dict(docs)['ledger']['ledger']['indexes'])
    await tablet.send(json.dumps(dict(protocol_version=1,type='command',action='achievements',achievement=command)))
    with self.assertRaises(ConnectionClosed) as caught:
     while True:await tablet.recv()
    self.assertEqual(1008,caught.exception.rcvd.code)
   async with await self.viewer('old-tablet') as older:
    _,docs=await self.snapshot(older);self.assertNotIn('indexes',dict(docs)['ledger']['ledger']);self.assertIn('indexes',json.loads((self.deck.root/'achievements.json').read_text())['ledger'])
