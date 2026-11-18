#!/usr/bin/env python3
"""Render preview-0.10.55-kids-search.png at Code's landscape screenshot size."""
from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
SHOT = Path("/tmp/kids-search-shot.jpg")
OUT = DIST / "preview-0.10.56-kids-search.png"
CUTOUT = ROOT / "app/src/main/assets/nova-face-cutout.png"
CHIPS = ["Puzzle", "Racing", "Adventure", "Learning", "Action", "Creative"]
GOLD = (232, 200, 114, 255)
HINT = "Search for a game"

def font(size, bold=False):
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def query_height(s, density, avail):
    s = max(s, 0.05); density = max(density, 0.5); avail = max(avail, 1)
    gap, chip_min = 8 * s, 28 * s
    max_bar = avail - gap - chip_min
    if max_bar < 32 * s:
        max_bar = max(32 * s, avail * 0.45)
    want = max(52 * s, 44 * density)
    if want > max_bar:
        want = max_bar
    if want < 32 * s and avail >= 32 * s:
        want = min(32 * s, max_bar)
    return max(1, want)

def pad_v(bar_h):
    pad = bar_h * 0.18
    if pad < 4:
        pad = min(4, bar_h * 0.2)
    return pad

def text_size(bar_h, s, density):
    pad = pad_v(bar_h)
    inner = max(1, bar_h - 2 * pad)
    fit = inner / 1.25
    want = min(18 * s, 16 * density)
    if want < 13 * s:
        want = min(13 * s, fit)
    if want > fit:
        want = fit
    return max(1, want)

def pad_h(s, density):
    return max(12 * s, 10 * density)

def chip_height(s, remain):
    want = min(40 * s, max(30 * s, 36 * s))
    if remain > 0 and want > remain:
        want = max(24 * s, remain)
    return want

def main():
    if not SHOT.exists():
        raise SystemExit("missing screenshot " + str(SHOT))
    src = Image.open(SHOT).convert("RGBA")
    W, H = src.size
    s = min(W / 1000.0, H / 720.0)
    d = 2.75
    kids_scale = 1.7
    cats_bottom = H * 0.26
    foot = max(78 * s, 56 * d)
    browse_top = H - foot - 136 * s * kids_scale
    avail = max(32 * s, browse_top - cats_bottom - 16 * s)
    qh = query_height(s, d, avail)
    ts = text_size(qh, s, d)
    ph = pad_h(s, d)
    gap = 8 * s
    bar_top = cats_bottom + 8 * s
    bar = (24 * s, bar_top, W - 24 * s, bar_top + qh)
    chip_top = bar[3] + gap
    ch = chip_height(s, browse_top - 8 * s - chip_top)
    chip_box = (16 * s, chip_top, W - 16 * s, chip_top + ch)

    # Cover the old thin bar and chips with a blur of nearby fire, then draw the new UI.
    y0 = int(max(0, bar[1] - 8))
    y1 = int(min(H, max(chip_box[3] + 8, 430)))
    patch = src.crop((0, y0, W, y1)).filter(ImageFilter.GaussianBlur(6))
    # Prefer sampling a clean fire band above the old bar if present
    sample = src.crop((0, int(bar[1] - 40), W, int(bar[1] - 4)))
    if sample.height > 4:
        sample = sample.resize((W, y1 - y0), Image.Resampling.LANCZOS)
        patch = Image.blend(patch, sample, 0.55)
    src.paste(patch, (0, y0))

    out = src.copy()
    dr = ImageDraw.Draw(out)
    # Bar
    l, t, r, b = [int(round(v)) for v in bar]
    dr.rounded_rectangle((l, t, r, b), radius=int(12 * s), fill=(20, 21, 24, 204), outline=GOLD, width=max(2, int(2 * s)))
    fnt = font(max(12, int(round(ts))), bold=False)
    # Vertically centre the hint inside the bar
    bbox = fnt.getbbox(HINT)
    th = bbox[3] - bbox[1]
    ty = t + (b - t - th) / 2 - bbox[1]
    dr.text((l + int(round(ph)), ty), HINT, font=fnt, fill=(220, 220, 224, 230))

    # Chips
    chips = CHIPS
    cg = 8 * s
    cw = (chip_box[2] - chip_box[0] - cg * (len(chips) + 1)) / len(chips)
    x = chip_box[0] + cg
    cf = font(max(11, int(min(16 * s, ch * 0.48))), bold=True)
    for lab in chips:
        cl, ct, cr, cb = x, chip_box[1] + 2 * s, x + cw, chip_box[3] - 2 * s
        dr.rounded_rectangle((cl, ct, cr, cb), radius=int((cb - ct) / 2), fill=(28, 29, 34, 230), outline=(232, 200, 114, 40), width=1)
        bb = cf.getbbox(lab)
        tw, th = bb[2] - bb[0], bb[3] - bb[1]
        dr.text((cl + (cw - tw) / 2, ct + ((cb - ct) - th) / 2 - bb[1]), lab, font=cf, fill=(255, 255, 255, 255))
        x += cw + cg

    # Cover the old circular bubble (gold ring + dark disc) and place the cut-out only.
    # Measured from Code's 2376x1060 screenshot: gold bbox ~2043,706 — 2375,1020.
    cover = (2000, 680, W, H)
    band = src.crop((1600, 680, 2000, H)).resize((cover[2]-cover[0], cover[3]-cover[1]), Image.Resampling.LANCZOS)
    band = band.filter(ImageFilter.GaussianBlur(4))
    out.paste(band, (cover[0], cover[1]))

    head = Image.open(CUTOUT).convert("RGBA")
    # Wide crop matching NovaFace FX,FY,FW,FH
    FX, FY, FW, FH = 0.04, 0.00, 0.92, 0.90
    hw, hh = head.size
    head = head.crop((round(FX*hw), round(FY*hh), round((FX+FW)*hw), round((FY+FH)*hh)))
    size = 280
    pad = 16
    face = head.resize((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size + pad*2, size + pad*2), (0,0,0,0))
    sh = Image.new("RGBA", canvas.size, (0,0,0,0))
    sm = face.copy()
    a = sm.split()[3].point(lambda v: int(v * 0.16) if v else 0)
    sm = Image.merge("RGBA", (Image.new("L", face.size, 0), Image.new("L", face.size, 0), Image.new("L", face.size, 0), a))
    sh.alpha_composite(sm, (pad+3, pad+4))
    sh = sh.filter(ImageFilter.GaussianBlur(3))
    canvas.alpha_composite(sh)
    canvas.alpha_composite(face, (pad, pad))
    bx = out.width - canvas.width - 20
    by = out.height - canvas.height - 24
    out.alpha_composite(canvas, (bx, by))

    # Keep the small speaker button at the lower-right of the face.
    import math
    d = ImageDraw.Draw(out)
    cx = bx + pad + size - 8
    cy = by + pad + size - 8
    r = 22
    d.ellipse((cx-r, cy-r, cx+r, cy+r), fill=(28, 26, 24, 238), outline=(232, 200, 114, 255), width=3)
    # tiny speaker
    d.rectangle((cx-11, cy-6, cx-3, cy+6), fill=(244, 228, 193, 255))
    d.polygon([(cx-3, cy-6), (cx+8, cy-13), (cx+8, cy+13), (cx-3, cy+6)], fill=(244, 228, 193, 255))

    DIST.mkdir(parents=True, exist_ok=True)
    out.convert("RGB").save(OUT, "PNG")
    print("wrote", OUT, out.size, "barH", round(qh, 1), "text", round(ts, 1), "chipH", round(ch, 1), "s", round(s, 3))

if __name__ == "__main__":
    main()
