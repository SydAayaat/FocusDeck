"""Private reusable icon packs: bounded decoding, safe ZIP reads, stable content IDs."""
from __future__ import annotations
import base64
import hashlib
import io
import re
from pathlib import Path
import zipfile
from .icons import validate_icon

MAX_FILES = 100
MAX_TOTAL = 64 * 1024 * 1024
MAX_FILE = 16 * 1024 * 1024
EXTENSIONS = {'.png', '.jpg', '.jpeg', '.webp', '.ico'}


def read_inputs(paths):
    files = []
    total = 0
    if len(paths) > MAX_FILES: raise ValueError('Select up to 100 files.')
    for path in map(Path, paths):
        total += path.stat().st_size
        if total > MAX_TOTAL: raise ValueError('Icon selection exceeds 64 MiB.')
        files.append(dict(name=path.name, data=path.read_bytes()))
    return read_uploaded(files)


def read_uploaded(inputs):
    """Never extract archive members; folder paths/links cannot escape into storage."""
    files = []
    total = 0
    input_total = 0
    if not isinstance(inputs, list) or not 1 <= len(inputs) <= MAX_FILES:
        raise ValueError('Select 1..100 icons or packs.')
    def add(name, data):
        nonlocal total
        total += len(data)
        if len(data) > MAX_FILE or total > MAX_TOTAL or len(files) >= MAX_FILES:
            raise ValueError('Import up to 100 icons, 16 MiB each and 64 MiB total.')
        files.append(dict(name=Path(name).name, data=data))
    for item in inputs:
        name = str(item['name'])
        data = item['data'] if isinstance(item['data'], bytes) else base64.b64decode(item['data'], validate=True)
        input_total += len(data)
        if input_total > MAX_TOTAL: raise ValueError('Icon selection exceeds 64 MiB.')
        if Path(name).suffix.lower() == '.zip':
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for member in archive.infolist():
                    if member.is_dir() or Path(member.filename).suffix.lower() not in EXTENSIONS: continue
                    if member.flag_bits & 1: raise ValueError('Encrypted icon packs are not supported.')
                    if member.file_size > MAX_FILE or total + member.file_size > MAX_TOTAL or len(files) >= MAX_FILES:
                        raise ValueError('Import up to 100 icons, 16 MiB each and 64 MiB total.')
                    if (member.external_attr >> 16) & 0o170000 == 0o120000:
                        raise ValueError('Icon packs cannot contain symbolic links.')
                    add(member.filename, archive.read(member))
        else:
            if Path(name).suffix.lower() not in EXTENSIONS: raise ValueError('Use PNG, JPEG, WebP, ICO or a ZIP pack.')
            add(name, data)
    if not files: raise ValueError('No supported icons were found in the selection.')
    return files


def import_entries(existing, files, normalize):
    if not isinstance(files, list) or not 1 <= len(files) <= MAX_FILES:
        raise ValueError('Select 1..100 icons.')
    entries = {item['id']: item for item in existing}
    imported = []
    total = 0
    # Decode/validate the whole batch before committing anything.
    for file in files:
        data = file.get('data')
        if not isinstance(data, bytes):
            data = base64.b64decode(data, validate=True)
        total += len(data)
        if total > MAX_TOTAL: raise ValueError('Icon selection exceeds 64 MiB.')
        icon = normalize(data)
        digest = hashlib.sha256(base64.b64decode(icon['data_base64'])).hexdigest()
        if digest not in entries:
            name = Path(str(file.get('name') or 'Icon').replace('\\', '/')).stem[:80] or 'Icon'
            entries[digest] = dict(id=digest, name=name, icon=icon)
        if digest not in imported: imported.append(digest)
    if len(entries) > 500: raise ValueError('The saved library supports up to 500 icons. Remove unused icons first.')
    return list(entries.values()), imported


def export_pack(entries, selected, path):
    if not selected or len(selected) > 100 or len(selected) != len(set(selected)):
        raise ValueError('Select 1..100 distinct icons to export.')
    lookup = {item['id']: item for item in entries}
    if any(key not in lookup for key in selected): raise ValueError('A selected icon no longer exists.')
    payload = io.BytesIO()
    with zipfile.ZipFile(payload, 'w', zipfile.ZIP_DEFLATED) as archive:
        for index, key in enumerate(selected, 1):
            entry = lookup[key]
            icon = validate_icon(entry['icon'])
            # Fixed export names are independent of uploaded filenames.
            name = re.sub(r'[^a-zA-Z0-9_-]+', '-', entry['name']).strip('-')[:60] or 'icon'
            archive.writestr(f'{index:03d}-{name}.png', base64.b64decode(icon['data_base64']))
    # Caller chooses a new private export folder, so no unrelated file is overwritten.
    path.write_bytes(payload.getvalue())
    path.chmod(0o600)
    return dict(file=str(path), count=len(selected))
