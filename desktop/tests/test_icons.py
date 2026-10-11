import base64
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
from focusdeck.icons import import_image, validate_icon, ICON_NAMES
from focusdeck.achievements import validate_command


def command(icon):
    return dict(achievement_version=4,operation="icon_set",id="icon-command",timestamp="2026-10-07T17:00:00Z",achievement_id="german",icon=icon)

class IconTests(unittest.TestCase):
    def test_real_image_normalizes_to_bounded_png_and_retains_alpha(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"icon.png";Image.new("RGBA",(1024,512),(255,0,0,128)).save(p)
            icon=import_image(p);validate_command(command(icon))
            with Image.open(io.BytesIO(base64.b64decode(icon["data_base64"]))) as im:
                self.assertEqual((256,128),im.size);self.assertEqual(128,im.convert("RGBA").getpixel((0,0))[3])
    def test_jpeg_import_and_builtin_names(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"icon.jpg";Image.new("RGB",(64,64)).save(p)
            self.assertEqual("image",import_image(p)["kind"])
        for name in ICON_NAMES:validate_command(command(dict(kind="builtin",name=name)))
    def test_invalid_image_data_dimensions_and_fields_rejected(self):
        output=io.BytesIO();Image.new("RGBA",(257,1)).save(output,format="PNG")
        for icon in [dict(kind="builtin",name="unknown"),dict(kind="default",name="laptop"),dict(kind="image",data_base64="bad"),
                     dict(kind="image",data_base64=base64.b64encode(output.getvalue()).decode()),dict(kind="image",data_base64="A"*90000),
                     dict(kind="image",data_base64=base64.b64encode(b"\x89PNG\r\n\x1a\n"+b"x"*40).decode()),dict(kind="external",url="https://example.com")]:
            with self.subTest(icon=str(icon)[:70]),self.assertRaises(ValueError):validate_icon(icon)
    def test_icon_only_in_contract_four_and_no_other_operation(self):
        c=command(dict(kind="default"));validate_command(c)
        for change in [dict(achievement_version=3),dict(operation="archive"),dict(current_value=4)]:
            with self.assertRaises(ValueError):validate_command(dict(c,**change))
    def test_cli_output_is_exact_complete_retryable_command_no_daemon_call(self):
        from focusdeck.cli import main
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp)/"command.json"
            with patch("sys.argv",["focusdeck","achievements","icon","german","--name","book-open","--command-id","retry-1","--timestamp","2026-10-07T17:00:00Z","--output",str(out)]), patch("focusdeck.cli.request_daemon") as daemon:
                self.assertEqual(0,main());daemon.assert_not_called()
            c=json.loads(out.read_text());validate_command(c);self.assertEqual("retry-1",c["id"]);self.assertEqual("book-open",c["icon"]["name"])
    def test_cli_local_image_payload_transfers_file_bytes_not_path(self):
        from focusdeck.cli import main
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"download.png";out=Path(tmp)/"command.json";Image.new("RGBA",(24,24),(0,255,0,255)).save(p)
            with patch("sys.argv",["focusdeck","achievements","icon","custom_compute","--file",str(p),"--output",str(out)]):self.assertEqual(0,main())
            c=json.loads(out.read_text());self.assertEqual("image",c["icon"]["kind"]);self.assertNotIn(str(p),out.read_text())
