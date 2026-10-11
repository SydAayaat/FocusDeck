import csv
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from focusdeck import deletions
from focusdeck.common import atomic_json
from focusdeck.reports import save_report,markdown
from focusdeck.clipboard import copy_text,copy_report,select_report
from test_focusdeck import report


class DeletionClipboardTests(unittest.TestCase):
    def test_merge_is_monotone_idempotent_and_hides_exact_attempt(self):
        r=report();key=f"{r['session_id']}:{r['started_at_ms']}"
        a=deletions.merge(deletions.empty(),dict(before_ms=0,report_keys=[key],revision='a'))
        b=deletions.merge(a,dict(before_ms=100,report_keys=['another:1'],revision='b'))
        self.assertEqual(set(b['report_keys']),{key,'another:1'})
        self.assertEqual(deletions.merge(b,a),b);self.assertTrue(deletions.hides(b,r))
        self.assertFalse(deletions.hides(b,{**r,'started_at_ms':r['started_at_ms']+1}))
        self.assertTrue(deletions.hides(dict(before_ms=r['finished_at_ms'],report_keys=[],revision='c'),r))

        with self.assertRaises(ValueError):
            deletions.validate(dict(before_ms=0,report_keys=['Ω'*240+str(i) for i in range(2000)],revision='x'))

    def test_deletion_keeps_unrelated_files_and_rebuilds_all_csvs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=report();name=save_report(root/'reports',r)
            external=root/'reports/notes.md';external.write_text('keep')
            tomb=deletions.persist_merge(root,deletions.empty(),dict(before_ms=0,report_keys=[f"{r['session_id']}:{r['started_at_ms']}"],revision='x'))
            self.assertEqual(deletions.apply(root,tomb),1)
            self.assertEqual(deletions.apply(root,tomb),0);self.assertTrue(external.exists())
            for kind in ('sessions','blocks','events','intervals'):
                self.assertEqual(list(csv.DictReader(io.StringIO((root/f'exports/all-{kind}.csv').read_text(encoding='utf-8-sig')))),[])
            self.assertFalse((root/f'reports/{name}.md').exists())

    def test_intent_remains_durable_when_cleanup_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=report();save_report(root/'reports',r)
            tomb=deletions.persist_merge(root,deletions.empty(),dict(before_ms=0,report_keys=[f"{r['session_id']}:{r['started_at_ms']}"],revision='x'))
            with patch('focusdeck.deletions.refresh_csv',side_effect=OSError('full disk')):
                with self.assertRaises(OSError):deletions.apply(root,tomb)
            self.assertEqual(json.loads((root/'deletions.json').read_text()),tomb)
            deletions.apply(root,tomb);self.assertFalse(list((root/'reports').glob('*.json')))

    def test_clipboard_wayland_utf8_without_shell(self):
        text="# GPT report\nPause from 10:01 → 10:02\n$(no shell) literal\n"
        with patch.dict(os.environ,{'WAYLAND_DISPLAY':'wayland-0'},clear=True),patch('focusdeck.clipboard.shutil.which',return_value='/usr/bin/wl-copy'),patch('focusdeck.clipboard.subprocess.run') as run:
            copy_text(text)
            args,kwargs=run.call_args
            self.assertEqual(args[0],['wl-copy','--type','text/plain;charset=utf-8'])
            self.assertEqual(kwargs['input'],text.encode());self.assertNotIn('shell',kwargs)

    def test_clipboard_x11_and_failures_never_claim_success(self):
        with patch.dict(os.environ,{'DISPLAY':':0'},clear=True),patch('focusdeck.clipboard.shutil.which',side_effect=lambda x:'/bin/xsel' if x=='xsel' else None),patch('focusdeck.clipboard.subprocess.run') as run:
            copy_text('report');self.assertEqual(run.call_args.args[0],['xsel','--clipboard','--input'])
        with patch.dict(os.environ,{},clear=True),self.assertRaises(ValueError):copy_text('report')
        with patch.dict(os.environ,{'WAYLAND_DISPLAY':'w0'},clear=True),patch('focusdeck.clipboard.shutil.which',return_value=None),self.assertRaisesRegex(ValueError,'wl-clipboard'):copy_text('report')
        with patch.dict(os.environ,{'WAYLAND_DISPLAY':'w0'},clear=True),patch('focusdeck.clipboard.shutil.which',return_value='wl-copy'),patch('focusdeck.clipboard.subprocess.run',side_effect=subprocess.CalledProcessError(1,['wl-copy'])),self.assertRaisesRegex(ValueError,'nothing was confirmed'):copy_text('report')

    def test_copy_report_filters_deleted_attempt_and_contains_gpt_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=report();save_report(root/'reports',r)
            with patch('focusdeck.clipboard.copy_text') as copy:
                self.assertEqual(copy_report(root,r['session_id']),r)
                text=copy.call_args.args[0]
                self.assertIn('Report key: ../untrusted:100000',text);self.assertIn('GPT feedback request:',text)
            atomic_json(root/'deletions.json',dict(before_ms=0,report_keys=[f"{r['session_id']}:{r['started_at_ms']}"],revision='d'))
            with self.assertRaises(ValueError):select_report(root,r['session_id'])
            from focusdeck.history import load_reports,export_archive
            self.assertEqual(load_reports(root/'reports'),[])
            export_archive(root/'reports',root/'export.json','json')
            self.assertEqual(json.loads((root/'export.json').read_text()),[])

    def test_delete_all_removes_corrupt_app_managed_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'reports').mkdir()
            path=root/'reports/123456789012345678901234.json';path.write_text('{bad')
            tomb=dict(before_ms=253402300799999,report_keys=[],revision='d')
            self.assertEqual(deletions.apply(root,tomb),1);self.assertFalse(path.exists())
