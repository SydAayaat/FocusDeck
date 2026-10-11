"""Bounded local-image import; bytes travel through the existing pinned achievement channel."""
from __future__ import annotations
import base64
import io
from pathlib import Path
from PIL import Image

ICON_NAMES = frozenset("book-open laptop ban trophy dumbbell heart guitar timer flame target graduation-cap shield-check code cpu circuit-board rocket brain activity star check settings house history chart-no-axes-column coffee music leaf mountain zap palette image folder wrench flag sparkles chevron-right chevron-left".split())
MAX_BYTES = 65536
MAX_DIMENSION = 256

def validate_icon(icon):
    if not isinstance(icon,dict):raise ValueError("icon object required")
    kind = icon.get("kind")
    if kind == "default":
        if set(icon)!={"kind"}:raise ValueError("Default icon has no extra fields")
    elif kind == "builtin":
        if set(icon)!={"kind","name"} or icon.get("name") not in ICON_NAMES:raise ValueError("Unknown bundled icon; use focusdeck achievements icons")
    elif kind == "image":
        if set(icon)!={"kind","data_base64"} or not isinstance(icon.get("data_base64"),str) or len(icon["data_base64"])>87384:raise ValueError("Icon must be a bounded PNG")
        try:data=base64.b64decode(icon["data_base64"],validate=True)
        except ValueError:raise ValueError("Invalid icon base64") from None
        if not 33<=len(data)<=MAX_BYTES:raise ValueError("Icon must be 1..64 KiB PNG")
        try:
            with Image.open(io.BytesIO(data)) as im:
                if im.format!="PNG" or not 1<=im.width<=MAX_DIMENSION or not 1<=im.height<=MAX_DIMENSION:raise ValueError("Icon must be PNG up to 256 × 256")
                im.verify()
        except (OSError,SyntaxError):raise ValueError("Invalid PNG icon") from None
    else:raise ValueError("Unsupported icon kind")
    return icon

def import_image(path):
    path=Path(path)
    if not path.is_file() or path.stat().st_size>16*1024*1024:raise ValueError("Choose a local image up to 16 MiB")
    try:
        with Image.open(path) as source:
            if source.format not in {"PNG","JPEG","WEBP"}:raise ValueError("Use PNG, JPEG or WebP; bundled vector icons use --name")
            if source.width*source.height>16000000:raise ValueError("Icon image exceeds 16 megapixels")
            im=source.convert("RGBA")
            im.thumbnail((MAX_DIMENSION,MAX_DIMENSION),Image.Resampling.LANCZOS)
            output=io.BytesIO();im.save(output,format="PNG",optimize=True)
            if len(output.getvalue())>MAX_BYTES:
                output=io.BytesIO();im.quantize(colors=128,method=Image.Quantize.FASTOCTREE).save(output,format="PNG",optimize=True)
            icon={"kind":"image","data_base64":base64.b64encode(output.getvalue()).decode("ascii")}
            return validate_icon(icon)
    except OSError:raise ValueError("Cannot decode local icon image") from None
