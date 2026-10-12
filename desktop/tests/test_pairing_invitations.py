import asyncio
import hashlib
import json
import os
import ssl
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from focusdeck.common import load_config
from focusdeck.pairing_invitations import PairingInvitations
from focusdeck.server import DeckServer


def redemption(invitation, device='disposable-phone'):
    return dict(type='redeem_invitation',protocol_version=1,invitation_version=1,
                invitation_id=invitation['invitation_id'],nonce=invitation['nonce'],
                desktop_id=invitation['desktop_id'],role=invitation['role'],device_id=device,name='Disposable test')


class InvitationRules(unittest.TestCase):
    def setUp(self):
        self.now=10.0
        self.invites=PairingInvitations(lambda:self.now,lambda:1000.0)
        self.cfg=dict(host='127.0.0.1',port=8765,token='phone-secret'*4,viewer_token='viewer-secret'*4,
                      certificate_sha256='a'*64,desktop_id='disposable-host')
    def test_qr_contains_only_short_lived_secret_and_single_use(self):
        invite=self.invites.create(self.cfg)
        self.assertNotIn('token',invite);self.assertNotIn(self.cfg['token'],json.dumps(invite))
        self.assertEqual(len(invite['nonce']),43)
        self.assertEqual(self.invites.redeem(redemption(invite),self.cfg,None)['token'],self.cfg['token'])
        with self.assertRaisesRegex(ValueError,'already used'):self.invites.redeem(redemption(invite),self.cfg,None)
    def test_expiry_uses_monotonic_time_and_regeneration_revokes(self):
        first=self.invites.create(self.cfg);second=self.invites.create(self.cfg)
        with self.assertRaisesRegex(ValueError,'revoked'):self.invites.redeem(redemption(first),self.cfg,None)
        self.now+=120
        with self.assertRaisesRegex(ValueError,'expired'):self.invites.redeem(redemption(second),self.cfg,None)
    def test_wrong_nonce_role_version_identity_and_trust_never_grant(self):
        invite=self.invites.create(self.cfg)
        for fields in [dict(nonce='z'*43),dict(role='viewer'),dict(invitation_version=2),dict(invitation_version=True),dict(desktop_id='other-host')]:
            with self.assertRaises(ValueError):self.invites.redeem(redemption(invite)|fields,self.cfg,None)
        with self.assertRaisesRegex(ValueError,'different phone'):self.invites.redeem(redemption(invite),self.cfg,'genuine-phone')
        self.assertEqual(self.invites.entries[invite['invitation_id']]['status'],'pending')
    def test_pending_phone_bound_before_regular_hello_and_viewer_role_is_separate(self):
        first=self.invites.create(self.cfg);self.invites.redeem(redemption(first),self.cfg,None)
        second=self.invites.create(self.cfg)
        with self.assertRaisesRegex(ValueError,'different phone'):self.invites.redeem(redemption(second,'racing-phone'),self.cfg,None)
        viewer=self.invites.create(self.cfg,True)
        grant=self.invites.redeem(redemption(viewer,'disposable-viewer'),self.cfg,'genuine-phone')
        self.assertEqual(grant['role'],'viewer');self.assertEqual(grant['token'],self.cfg['viewer_token'])
    def test_restart_invalidates_and_explicit_revoke(self):
        invite=self.invites.create(self.cfg);self.invites.revoke(invite['invitation_id'])
        with self.assertRaisesRegex(ValueError,'revoked'):self.invites.redeem(redemption(invite),self.cfg,None)
        with self.assertRaisesRegex(ValueError,'Invalid'):PairingInvitations().redeem(redemption(invite),self.cfg,None)


class InvitationTls(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        asyncio.get_running_loop().set_debug(False)
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.env=patch.dict(os.environ,FOCUSDECK_CONFIG=str(self.root/'config'));self.env.start()
        self.cfg=load_config('127.0.0.1',8765);self.deck=DeckServer(self.cfg,self.root/'data')
        tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.minimum_version=ssl.TLSVersion.TLSv1_2
        tls.load_cert_chain(self.root/'config/cert.pem',self.root/'config/key.pem')
        self.client=ssl.create_default_context(cafile=str(self.root/'config/cert.pem'));self.client.check_hostname=False
        self.server=await serve(self.deck.device_handler,'127.0.0.1',0,ssl=tls,process_request=self.deck.authenticate,max_size=4096)
        self.cfg['port']=self.server.sockets[0].getsockname()[1]
    async def asyncTearDown(self):
        self.server.close();await self.server.wait_closed();self.env.stop();self.temp.cleanup()
    async def redeem(self,invite,fields=None):
        async with connect(f"wss://127.0.0.1:{self.cfg['port']}/v1/pair",ssl=self.client) as ws:
            cert=ws.transport.get_extra_info('ssl_object').getpeercert(binary_form=True)
            self.assertEqual(hashlib.sha256(cert).hexdigest(),invite['certificate_sha256'])
            await ws.send(json.dumps(redemption(invite)|(fields or {})))
            return json.loads(await ws.recv())
    async def test_disposable_invitation_grant_and_genuine_authenticated_phone_hello(self):
        invitation=(await self.deck.control(dict(action='pair-invite')))['invitation']
        grant=await self.redeem(invitation);self.assertEqual(grant['type'],'pairing_grant')
        async with connect(f"wss://127.0.0.1:{self.cfg['port']}/v1/device",ssl=self.client,
                           additional_headers={'Authorization':'Bearer '+grant['pairing']['token']}) as ws:
            await ws.send(json.dumps(dict(protocol_version=1,type='hello',device_id='disposable-phone',name='Disposable phone')))
            ack=json.loads(await ws.recv());self.assertEqual(ack['type'],'hello_ack');self.assertEqual(ack['desktop_id'],self.cfg['desktop_id'])
            self.assertEqual(self.deck.saved['device_id'],'disposable-phone')
        replay=await self.redeem(invitation);self.assertEqual(replay['type'],'pairing_error');self.assertIn('already used',replay['error'])
    async def test_disposable_viewer_grant_and_authenticated_read_only_hello(self):
        invite=(await self.deck.control(dict(action='pair-invite',viewer=True)))['invitation']
        grant=await self.redeem(invite);self.assertEqual(grant['pairing']['role'],'viewer')
        async with connect(f"wss://127.0.0.1:{self.cfg['port']}/v1/viewer",ssl=self.client,
                           additional_headers={'Authorization':'Bearer '+grant['pairing']['token']}) as ws:
            await ws.send(json.dumps(dict(protocol_version=1,type='hello',device_id='disposable-phone',name='Disposable viewer',role='viewer',supported_viewer_version=1)))
            ack=json.loads(await ws.recv());self.assertEqual(ack['type'],'hello_ack');self.assertEqual(ack['role'],'viewer')
            self.assertIsNone(self.deck.saved['device_id'])
    async def test_bad_invitation_does_not_change_trust_and_never_echoes_nonce(self):
        invite=(await self.deck.control(dict(action='pair-invite')))['invitation']
        reply=await self.redeem(invite,dict(invitation_version=2))
        self.assertEqual(reply['type'],'pairing_error');self.assertNotIn(invite['nonce'],json.dumps(reply));self.assertIsNone(self.deck.saved['device_id'])
        await self.deck.control(dict(action='revoke-invite',invitation_id=invite['invitation_id']))
        self.assertIn('revoked',(await self.redeem(invite))['error'])


class InvitationControlCenter(unittest.TestCase):
    def test_local_qr_render_and_manual_fallback_use_existing_owner_control(self):
        import base64
        from focusdeck.control_center import ControlCenter
        cfg=dict(host='127.0.0.1',port=8765,token='disposable-token'*4,viewer_token='viewer-test'*4,certificate_sha256='a'*64,desktop_id='disposable-host')
        invitation=PairingInvitations().create(cfg)
        calls=[]
        def transport(payload):
            calls.append(payload)
            if payload['action']=='pair-invite':return dict(ok=True,invitation=invitation)
            if payload['action']=='pairing':return dict(ok=True,pairing=cfg)
            if payload['action']=='revoke-invite':return dict(ok=True)
            raise AssertionError(payload)
        with tempfile.TemporaryDirectory() as directory:
            center=ControlCenter(Path(directory),transport)
            result=center.handle('pair-invite',dict(viewer=False))
            self.assertTrue(base64.b64decode(result['qr_image'].split(',',1)[1]).startswith(b'\x89PNG\r\n\x1a\n'))
            self.assertNotIn(cfg['token'],json.dumps(result['invitation']))
            self.assertEqual(list(Path(directory).rglob('*')),[])
            self.assertEqual(center.handle('pairing',dict(viewer=True))['pairing'],cfg)
            center.handle('revoke-invite',dict(invitation_id=invitation['invitation_id']))
            self.assertEqual([c['action'] for c in calls],['pair-invite','pairing','revoke-invite'])
