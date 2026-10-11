"""Owner-private single-instance lock and launcher activation mailbox (no listener)."""
from __future__ import annotations
import fcntl
import json
import os
from pathlib import Path
from uuid import uuid4
from .common import atomic_json


class GuiInstance:
    def __init__(self, private: Path):
        private.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.activation = private / 'gui-activate.json'
        # Observe before attempting the lock: a request during startup is not lost.
        self.seen = self._read()
        self.fd = os.open(private / 'gui.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(self.fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(self.fd)
            self.fd = None

    @property
    def primary(self):
        return self.fd is not None

    def activate(self):
        atomic_json(self.activation, {'nonce': str(uuid4())})

    def _read(self):
        try:
            fd = os.open(self.activation, os.O_RDONLY | os.O_NOFOLLOW)
            with os.fdopen(fd, 'r') as stream:
                value = json.loads(stream.read(1024))
            nonce = value.get('nonce') if isinstance(value, dict) else None
            return nonce if isinstance(nonce, str) and len(nonce) <= 100 else None
        except (OSError, ValueError):
            return None

    def poll(self):
        nonce = self._read()
        if nonce is None or nonce == self.seen:
            return False
        self.seen = nonce
        return True

    def close(self):
        if self.fd is not None:
            os.close(self.fd)
            self.fd = None
