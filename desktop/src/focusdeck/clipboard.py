"""Paste-ready Markdown through desktop clipboard programs, without a shell."""
import os
import shutil
import subprocess
from .common import read_json
from .history import load_reports
from .reports import markdown
from . import deletions


def select_report(root, session_id=None, report_key=None):
    if session_id and report_key:raise ValueError("Choose --session or --key")
    tombstones=deletions.validate(read_json(root / "deletions.json",deletions.empty()))
    records=[r for r in load_reports(root / "reports") if not deletions.hides(tombstones,r)
             and (not session_id or r['session_id']==session_id)
             and (not report_key or f"{r['session_id']}:{r['started_at_ms']}"==report_key)]
    if not records:raise ValueError("No matching saved report")
    return max(records,key=lambda r:(r['finished_at_ms'],r['started_at_ms']))


def copy_text(text):
    if os.environ.get('WAYLAND_DISPLAY'):
        if not shutil.which('wl-copy'):raise ValueError("Install wl-clipboard (sudo apt install wl-clipboard), then run copy-report from your desktop terminal")
        command=['wl-copy','--type','text/plain;charset=utf-8']
    elif os.environ.get('DISPLAY'):
        if shutil.which('xclip'):command=['xclip','-selection','clipboard','-in']
        elif shutil.which('xsel'):command=['xsel','--clipboard','--input']
        else:raise ValueError("Install xclip or xsel for your X11 desktop clipboard")
    else:raise ValueError("No graphical desktop clipboard found; run copy-report in your desktop terminal")
    try:subprocess.run(command,input=text.encode('utf-8'),check=True,timeout=10,capture_output=True)
    except (subprocess.CalledProcessError,subprocess.TimeoutExpired) as exc:raise ValueError("Desktop clipboard rejected the report; nothing was confirmed copied") from exc


def copy_report(root, session_id=None, report_key=None):
    report=select_report(root,session_id,report_key)
    copy_text(markdown(report))
    return report
