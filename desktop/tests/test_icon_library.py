"""Multi-icon import/export, zip bounds, transparency and independent private storage."""
import base64
import io
from pathlib import Path
import tempfile
import unittest
import zipfile
from PIL import Image
from focusdeck.control_center import ControlCenter, normalize_image
from focusdeck.icon_library import read_inputs, read_uploaded, import_entries, export_pack


def png(color=(255,0,0,128)):
    image=Image.new('RGBA',(8,8),(0,0,0,0));image.putpixel((2,2),color)
    stream=io.BytesIO();image.save(stream,format='PNG');return stream.getvalue()

class IconLibraryTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.center=ControlCenter(self.root,lambda p:dict(ok=True,connected=False,devices=[],viewers=[],paired_id=None))
    def tearDown(self):self.temp.cleanup()
    def test_batch_import_deduplicates_content_and_preserves_alpha(self):
        a=png();b=png((0,255,0,255))
        result=self.center.handle('import-icons',dict(files=[dict(name='german.png',data=base64.b64encode(a).decode()),dict(name='guitar.png',data=base64.b64encode(b).decode()),dict(name='duplicate.png',data=base64.b64encode(a).decode())]))
        self.assertEqual(len(result['entries']),2);self.assertEqual(len(result['imported']),2)
        decoded=Image.open(io.BytesIO(base64.b64decode(result['entries'][0]['icon']['data_base64'])))
        self.assertEqual(decoded.getpixel((0,0))[3],0);self.assertEqual(decoded.getpixel((2,2))[3],128)
        self.assertEqual((self.center.private/'icon-library.json').stat().st_mode&0o777,0o600)
        self.assertFalse((self.root/'achievements.json').exists());self.assertFalse((self.center.private/'deliveries.json').exists())
        reopened=ControlCenter(self.root,self.center.transport)
        self.assertEqual(len(reopened.snapshot()['icon_library']),2)
    def test_pack_import_never_extracts_paths_and_ignores_non_icons(self):
        path=self.root/'pack.zip'
        with zipfile.ZipFile(path,'w') as z:
            z.writestr('../../escaped.png',png());z.writestr('folder/guitar.png',png((0,255,0,255)));z.writestr('README.txt','notes')
        files=read_inputs([str(path)]);self.assertEqual(len(files),2);self.assertFalse((self.root.parent/'escaped.png').exists())
        result=self.center.handle('import-icon-paths',dict(paths=[str(path)]));self.assertEqual(len(result['entries']),2)
    def test_bad_batch_is_atomic(self):
        with self.assertRaises(Exception):self.center.handle('import-icons',dict(files=[dict(name='valid.png',data=base64.b64encode(png()).decode()),dict(name='bad.png',data=base64.b64encode(b'bad').decode())]))
        self.assertFalse((self.center.private/'icon-library.json').exists())
    def test_symbolic_link_and_decompression_size_limit(self):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as z:
            item=zipfile.ZipInfo('linked.png');item.external_attr=0o120777<<16;z.writestr(item,'/etc/passwd')
        with self.assertRaisesRegex(ValueError,'symbolic'):read_uploaded([dict(name='pack.zip',data=stream.getvalue())])
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as z:z.writestr('big.png',b'0'*(16*1024*1024+1))
        with self.assertRaisesRegex(ValueError,'16 MiB'):read_uploaded([dict(name='pack.zip',data=stream.getvalue())])
    def test_export_selected_roundtrips_as_a_pack_and_remove_keeps_draft(self):
        entries,ids=import_entries([], [dict(name='../German icon.png',data=png()),dict(name='guitar.png',data=png((0,255,0,255)))],normalize_image)
        from focusdeck.common import atomic_json
        atomic_json(self.center.private/'icon-library.json',entries)
        self.center.handle('save-draft',dict(kind='card',draft=dict(icon=entries[0]['icon'])))
        result=self.center.handle('export-icons',dict(directory=str(self.root),ids=ids))
        with zipfile.ZipFile(result['file']) as z:
            self.assertEqual(len(z.namelist()),2);self.assertTrue(all('/' not in name for name in z.namelist()))
            self.assertTrue(any('German-icon' in name for name in z.namelist()))
        reimported,restored=import_entries([],read_inputs([result['file']]),normalize_image)
        self.assertEqual(set(ids),set(restored))
        self.center.handle('remove-library-icons',dict(ids=ids));self.assertEqual(self.center.snapshot()['icon_library'],[])
        self.assertEqual(self.center.snapshot()['drafts']['card']['icon'],entries[0]['icon'])
    def test_empty_unknown_and_duplicate_export_rejected(self):
        for ids in ([],['absent'],['a','a']):
            with self.subTest(ids=ids),self.assertRaises(ValueError):export_pack([],ids,self.root/'bad.zip')
    def test_unchanged_library_is_not_resent_on_background_observations(self):
        first=self.center.handle('snapshot');second=self.center.handle('snapshot',dict(icon_version=first['icon_version']))
        self.assertNotIn('icon_library',second)
        self.center.handle('import-icons',dict(files=[dict(name='new.png',data=base64.b64encode(png()).decode())]))
        changed=self.center.handle('snapshot',dict(icon_version=first['icon_version']))
        self.assertEqual(len(changed['icon_library']),1);self.assertNotEqual(first['icon_version'],changed['icon_version'])
    def test_sidebar_preference_preserves_display_settings(self):
        before=self.center.settings();self.center.handle('ui',dict(sidebar_collapsed=True));self.assertTrue(self.center.snapshot()['ui']['sidebar_collapsed']);self.assertEqual(before,self.center.settings())
        with self.assertRaises(ValueError):self.center.handle('ui',dict(sidebar_collapsed='yes'))
