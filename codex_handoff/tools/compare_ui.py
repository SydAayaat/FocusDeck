#!/usr/bin/env python3
"""FocusDeck visual QA diagnostic; not a substitute for checking app behavior.

Pillow only: pip install Pillow

python3 tools/compare_ui.py --reference references/home.jpeg \
  --actual artifacts/uiqa/home.png --out artifacts/uiqa/home_compare

The source screenshots are generated mockups, not exact device recordings. Numeric data,
status bar text, navigation systems, widths, crop and viewport may vary. Use this tool
for a side-by-side review and discrepancy finding, never as a sole passing criterion.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageOps, ImageStat


def parse_crop(s: str | None):
    if not s:
        return None
    vals = tuple(int(i.strip()) for i in s.split(","))
    if len(vals) != 4 or vals[2] <= vals[0] or vals[3] <= vals[1]:
        raise ValueError("Crop must be x1,y1,x2,y2 with x2>x1 and y2>y1")
    return vals


def crop_light_gutters(im: Image.Image) -> tuple[Image.Image, tuple[int, int]]:
    """Remove nearly-white side pillars ONLY; never strip intentional dark UI edges."""
    w, h = im.size
    ypts = [round(h*f) for f in (0.13, 0.32, 0.52, 0.74, 0.90)]
    def white_column(x):
        n = 0
        for y in ypts:
            px = im.getpixel((x, min(y,h-1)))
            if min(px) > 242 and max(px)-min(px) < 10:
                n+=1
        return n >= 4
    l=0
    while l < w//3 and white_column(l):l+=1
    r=w
    while r>2*w//3 and white_column(r-1):r-=1
    if l + (w-r) < 7:
        return im,(0,0)
    return im.crop((l,0,r,h)),(l,w-r)


def load_image(path:Path,crop,trim_white):
    im=Image.open(path).convert("RGB")
    source=im.size
    trimmed=(0,0)
    if crop:
        im=im.crop(crop)
    if trim_white:
        im,trimmed=crop_light_gutters(im)
    return im,{"path":str(path),"source_size":source,"used_size":im.size,"crop":crop,"trimmed_white_gutters":trimmed}


def normalize(im:Image.Image,w:int,h:int):
    """ImageOps.fit preserves aspect ratio with center crop for a common viewport."""
    return ImageOps.fit(im,(w,h),Image.Resampling.LANCZOS,centering=(0.5,0.45))


def main():
    p=argparse.ArgumentParser(description="Visual reference vs Android screenshot diagnostics")
    p.add_argument("--reference",required=True,type=Path)
    p.add_argument("--actual",required=True,type=Path)
    p.add_argument("--out",required=True,type=Path)
    p.add_argument("--width",type=int,default=432)
    p.add_argument("--height",type=int,default=936)
    p.add_argument("--reference-crop",help="Optional x1,y1,x2,y2, in original reference pixels")
    p.add_argument("--actual-crop",help="Optional x1,y1,x2,y2, in original capture pixels")
    p.add_argument("--no-trim-white-gutters",action="store_true")
    p.add_argument("--mask-top",type=int,default=0,help="Ignore top N normalized pixels (status bars may differ)")
    p.add_argument("--mask-bottom",type=int,default=0,help="Ignore bottom N normalized pixels")
    a=p.parse_args()
    a.out.mkdir(parents=True,exist_ok=True)
    try:
        ref,rm=load_image(a.reference,parse_crop(a.reference_crop),not a.no_trim_white_gutters)
        got,gm=load_image(a.actual,parse_crop(a.actual_crop),not a.no_trim_white_gutters)
    except Exception as e:
        p.error(str(e))
    x=normalize(ref,a.width,a.height)
    y=normalize(got,a.width,a.height)
    x.save(a.out/"reference_normalized.png")
    y.save(a.out/"actual_normalized.png")
    overlay=Image.blend(x,y,0.5)
    overlay.save(a.out/"overlay.png")
    diff=ImageChops.difference(x,y)
    if a.mask_top or a.mask_bottom:
        draw=ImageDraw.Draw(diff)
        if a.mask_top: draw.rectangle((0,0,a.width,a.mask_top-1),fill=(0,0,0))
        if a.mask_bottom: draw.rectangle((0,a.height-a.mask_bottom,a.width,a.height),fill=(0,0,0))
    ImageEnhance.Contrast(diff).enhance(2.5).save(a.out/"difference.png")
    ar=Image.new("RGB",(a.width*2,a.height+35),(23,25,29))
    ar.paste(x,(0,35));ar.paste(y,(a.width,35));d=ImageDraw.Draw(ar)
    d.text((10,10),"LOCKED REFERENCE",fill=(248,174,35))
    d.text((a.width+10,10),"ACTUAL ANDROID CAPTURE",fill=(40,214,236))
    ar.save(a.out/"side_by_side.png")
    stat=ImageStat.Stat(diff)
    err=sum(stat.mean)/3
    hist=diff.convert("L").histogram()
    total=sum(hist)
    more=sum(hist[32:])
    report={
        "reference":rm,"actual":gm,"normalized_viewport":(a.width,a.height),
        "ignore_top_pixels":a.mask_top,"ignore_bottom_pixels":a.mask_bottom,
        "mean_absolute_channel_difference":round(err,3),
        "pixels_with_grayscale_difference_over_31_pct":round(more/total*100,2) if total else 0,
        "visual_review_required":True,
        "warning":"These numbers are diagnostics, not a pass/fail score. Check geometry, text clipping, card order, timeline thickness and actual interactions; dynamic content and generated reference art will differ.",
        "artifacts":["reference_normalized.png","actual_normalized.png","overlay.png","difference.png","side_by_side.png"]
    }
    (a.out/"report.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))

if __name__=="__main__":main()
