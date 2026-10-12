"""Ephemeral owner-issued invitations. Never persist/log a nonce or permanent credential."""
from __future__ import annotations
import hmac
import secrets
import time
import uuid
from .common import pairing_info, text_field

INVITATION_VERSION = 1
TTL_SECONDS = 120


class InvitationError(ValueError):
    """Fixed user-facing messages, independent of untrusted payloads."""


class PairingInvitations:
    def __init__(self, clock=time.monotonic, wall=time.time):
        self.clock, self.wall = clock, wall
        self.entries = {}
        self.reservation = None

    def create(self, cfg, viewer=False):
        # One outstanding invitation per role; regeneration retires the previous nonce.
        now = self.clock()
        self.entries = {k: v for k, v in self.entries.items() if now-v['deadline'] < 300}
        role = 'viewer' if viewer else 'phone'
        for entry in self.entries.values():
            if entry['public']['role'] == role and entry['status'] == 'pending': entry['status'] = 'revoked'
        pairing = pairing_info(cfg, viewer)
        public = {k: pairing[k] for k in ('host', 'port', 'desktop_id', 'certificate_sha256')}
        public['role'] = role
        public.update(type='focusdeck_pairing_invitation', invitation_version=INVITATION_VERSION,
                      protocol_version=1, invitation_id=str(uuid.uuid4()), nonce=secrets.token_urlsafe(32),
                      expires_at_ms=int((self.wall()+TTL_SECONDS)*1000))
        self.entries[public['invitation_id']] = dict(public=public, deadline=now+TTL_SECONDS, status='pending')
        return public.copy()

    def revoke(self, invitation_id):
        entry = self.entries.get(invitation_id)
        if entry and entry['status'] == 'pending': entry['status'] = 'revoked'

    def reserved_id(self):
        if self.reservation and self.reservation[1] > self.clock(): return self.reservation[0]
        self.reservation = None
        return None

    def redeem(self, request, cfg, known_phone):
        if not isinstance(request, dict) or type(request.get('invitation_version')) is not int or type(request.get('protocol_version')) is not int or request.get('invitation_version') != INVITATION_VERSION or request.get('protocol_version') != 1:
            raise InvitationError('Pairing protocol version mismatch')
        if request.get('type') != 'redeem_invitation': raise InvitationError('Invalid pairing request')
        entry = self.entries.get(request.get('invitation_id'))
        nonce = request.get('nonce')
        if not entry or not isinstance(nonce,str) or not hmac.compare_digest(nonce,entry['public']['nonce']):
            raise InvitationError('Invalid pairing invitation')
        if entry['status'] == 'used': raise InvitationError('Invitation already used; create a new invitation')
        if entry['status'] != 'pending': raise InvitationError('Invitation revoked; create a new invitation')
        if self.clock() >= entry['deadline']: raise InvitationError('Invitation expired; create a new invitation')
        device_id = text_field(request.get('device_id'), 'device_id', 100)
        text_field(request.get('name'), 'name', 100)
        role = entry['public']['role']
        if request.get('role') != role or request.get('desktop_id') != cfg['desktop_id']:
            raise InvitationError('Invitation identity or role mismatch')
        if role == 'phone':
            trusted = known_phone or self.reserved_id()
            if trusted and trusted != device_id: raise InvitationError('A different phone is paired; forget it explicitly on the host first')
            self.reservation = (device_id, self.clock()+TTL_SECONDS)
        entry['status'] = 'used'
        return pairing_info(cfg, role == 'viewer')
