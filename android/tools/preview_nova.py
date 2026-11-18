#!/usr/bin/env python3
"""Render preview-0.10.52-nova-*.png into android/dist/ (no device needed)."""
from __future__ import annotations
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
FACE = ROOT / "app/src/main/assets/nova-face.jpg"
BASE = DIST / "preview-0.10.51-covers.png"
# face_layout.py (normalized on the 1024 portrait)
FX, FY, FW, FH = 0.18, 0.02, 0.66, 0.78
LEX, LEY, LEW, LEH = 0.352, 0.370, 0.108, 0.052
REX, REY, REW, REH = 0.518, 0.344, 0.124, 0.054
LID = (176, 137, 120, 255)

def font(size, bold=False):
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def head_crop(src: Image.Image) -> Image.Image:
    w, h = src.size
    return src.crop((round(FX * w), round(FY * h), round((FX + FW) * w), round((FY + FH) * h)))

def map_eye(box, nx, ny, nw, nh):
    bw, bh = box.size
    x = (nx - FX) / FW
    y = (ny - FY) / FH
    return (x * bw, y * bh, (x + nw / FW) * bw, (y + nh / FH) * bh)

def almond_mask(size, box):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).ellipse(box, fill=255)
    return m

def apply_gaze(head: Image.Image, gx=0.0, gy=0.0) -> Image.Image:
    out = head.copy()
    for e in ((LEX, LEY, LEW, LEH), (REX, REY, REW, REH)):
        box = map_eye(head, *e)
        l, t, r, b = box
        ox, oy = gx * (r - l) * 0.16, gy * (b - t) * 0.18
        shifted = Image.new("RGBA", head.size, (0, 0, 0, 0))
        shifted.paste(head, (round(ox), round(oy)))
        mask = almond_mask(head.size, box)
        out = Image.composite(shifted, out, mask)
    return out

def apply_blink(head: Image.Image, blink: float) -> Image.Image:
    if blink <= 0.01:
        return head
    out = head.copy()
    d = ImageDraw.Draw(out)
    for e in ((LEX, LEY, LEW, LEH), (REX, REY, REW, REH)):
        l, t, r, b = map_eye(head, *e)
        mid = t + (b - t) * 0.55
        up = t + (mid - t) * blink * 1.08
        lo = b - (b - mid) * blink * 1.08
        if up > lo:
            m = (up + lo) * 0.5
            up = lo = m
        mask = almond_mask(head.size, (l, t, r, b))
        lids = Image.new("RGBA", head.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(lids)
        ld.rectangle((l - 2, t - 2, r + 2, up), fill=LID)
        ld.rectangle((l - 2, lo, r + 2, b + 2), fill=LID)
        out = Image.composite(lids, out, mask)
    return out

def circle_bubble(head: Image.Image, size: int, ring=8) -> Image.Image:
    head = head.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    bubble = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bubble.paste(head, (0, 0), mask)
    # soft shadow + gold ring
    canvas = Image.new("RGBA", (size + 16, size + 16), (0, 0, 0, 0))
    sh = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(sh).ellipse((6, 8, 6 + size, 8 + size), fill=140)
    sh = sh.filter(ImageFilter.GaussianBlur(5))
    canvas.paste((0, 0, 0, 160), (0, 0), sh)
    canvas.paste(bubble, (8, 8), bubble)
    d = ImageDraw.Draw(canvas)
    d.ellipse((8, 8, 8 + size - 1, 8 + size - 1), outline=(232, 200, 114, 255), width=ring)
    return canvas

def paste_bubble(bg: Image.Image, bubble: Image.Image, margin=36):
    x = bg.width - bubble.width - margin
    y = bg.height - bubble.height - margin
    bg.alpha_composite(bubble, (x, y))
    return x, y

def load_base(size=None):
    if BASE.exists():
        im = Image.open(BASE).convert("RGBA")
    else:
        im = Image.new("RGBA", (1920, 1080), (16, 18, 24, 255))
    if size and im.size != size:
        im = im.resize(size, Image.Resampling.LANCZOS)
    return im

def panel_card(w, h, answer: str):
    card = Image.new("RGBA", (w, h), (20, 22, 28, 242))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=22, fill=(20, 22, 28, 242), outline=(232, 200, 114, 160), width=2)
    d.text((24, 20), "Nova", font=font(34, True), fill=(244, 228, 193, 255))
    d.text((24, 62), "CodeAque helper  ·  offline", font=font(16), fill=(184, 168, 138, 255))
    chips = ["How do I update?", "Kids mode", "Search", "Screen Time", "Parent PIN"]
    x, y = 24, 100
    for c in chips:
        tw = d.textlength(c, font=font(15))
        d.rounded_rectangle((x, y, x + tw + 20, y + 32), radius=8, fill=(42, 36, 32, 255))
        d.text((x + 10, y + 7), c, font=font(15), fill=(244, 228, 193, 255))
        x += int(tw) + 28
    d.rounded_rectangle((24, 148, w - 24, 190), radius=8, fill=(28, 26, 24, 255))
    d.text((34, 158), "Ask about CodeAque…", font=font(16), fill=(184, 168, 138, 180))
    # wrap answer
    y = 210
    words = answer.split()
    line = ""
    f = font(17)
    maxw = w - 48
    for word in words:
        trial = (line + " " + word).strip()
        if d.textlength(trial, font=f) <= maxw:
            line = trial
        else:
            d.text((24, y), line, font=f, fill=(232, 224, 212, 255))
            y += 24
            line = word
    if line:
        d.text((24, y), line, font=f, fill=(232, 224, 212, 255))
        y += 28
    d.text((24, h - 70), "Hear it     Close", font=font(16, True), fill=(232, 200, 114, 255))
    foot = "I only know simple CodeAque help on this phone. No account, no data collection."
    d.text((24, h - 40), foot, font=font(13), fill=(138, 128, 112, 255))
    return card

def main():
    DIST.mkdir(parents=True, exist_ok=True)
    src = Image.open(FACE).convert("RGBA")
    head = head_crop(src)
    idle = apply_gaze(head, 0.15, -0.05)
    blink = apply_blink(apply_gaze(head, 0.0, 0.0), 1.0)
    look = apply_gaze(head, -0.4, 0.1)

    # 1. bubble on the current home preview
    home = load_base()
    bub = circle_bubble(idle, 220, ring=7)
    paste_bubble(home, bub, margin=40)
    out1 = DIST / "preview-0.10.52-nova-bubble.png"
    home.convert("RGB").save(out1, "PNG")
    print("wrote", out1, home.size)

    # 2. blink close-up
    close = Image.new("RGBA", (900, 900), (16, 18, 24, 255))
    big = circle_bubble(blink, 780, ring=14)
    close.alpha_composite(big, ((900 - big.width) // 2, (900 - big.height) // 2))
    d = ImageDraw.Draw(close)
    d.text((40, 30), "Nova  ·  blink", font=font(32, True), fill=(244, 228, 193, 255))
    out2 = DIST / "preview-0.10.52-nova-blink.png"
    close.convert("RGB").save(out2, "PNG")
    print("wrote", out2)

    # 3. expanded helper on the home
    exp = load_base()
    face = circle_bubble(look, 200, ring=7)
    fx, fy = paste_bubble(exp, face, margin=40)
    card = panel_card(1100, 720,
        "In Kids mode, GET on an age-filtered game opens only that game's Play page so you can install it. "
        "That does not need the parent PIN unless a grown-up turns on Settings > Ask PIN to install games. "
        "Leaving Kids mode, Settings, raw Play browsing, sideloading, and Screen Time still need the PIN.")
    exp.alpha_composite(card, (48, (exp.height - card.height) // 2))
    out3 = DIST / "preview-0.10.52-nova-expanded.png"
    exp.convert("RGB").save(out3, "PNG")
    print("wrote", out3, exp.size)

    # 4. overlay-permission honesty card (settings-like)
    info = load_base()
    # dim
    dim = Image.new("RGBA", info.size, (0, 0, 0, 120))
    info.alpha_composite(dim)
    dlg_w, dlg_h = 980, 560
    dlg = Image.new("RGBA", (dlg_w, dlg_h), (30, 28, 26, 250))
    d = ImageDraw.Draw(dlg)
    d.rounded_rectangle((0, 0, dlg_w - 1, dlg_h - 1), radius=18, fill=(30, 28, 26, 250), outline=(232, 200, 114, 140), width=2)
    d.text((36, 28), "Nova", font=font(32, True), fill=(244, 228, 193, 255))
    body = (
        "Nova is a corner helper for simple CodeAque questions. She works offline, has no account, "
        "and does not collect data.\n\n"
        "Showing her over other games needs the parent PIN and Android's Display over other apps "
        "permission. Some phones, full-screen games, or Kids lock may still hide or block that bubble. "
        "It stays off while Kids mode is on."
    )
    y = 90
    f = font(20)
    for para in body.split("\n"):
        if para == "":
            y += 16
            continue
        line = ""
        for word in para.split():
            trial = (line + " " + word).strip()
            if d.textlength(trial, font=f) <= dlg_w - 72:
                line = trial
            else:
                d.text((36, y), line, font=f, fill=(232, 224, 212, 255))
                y += 28
                line = word
        if line:
            d.text((36, y), line, font=f, fill=(232, 224, 212, 255))
            y += 32
    d.rounded_rectangle((36, y + 8, 68, y + 40), radius=4, outline=(232, 200, 114, 255), width=2)
    d.text((80, y + 12), "Show Nova in CodeAque", font=font(20), fill=(244, 228, 193, 255))
    y += 52
    d.rounded_rectangle((36, y + 8, 68, y + 40), radius=4, outline=(120, 110, 100, 255), width=2)
    d.text((80, y + 12), "Show Nova over other games  (needs parent PIN)", font=font(20), fill=(184, 168, 138, 255))
    d.text((dlg_w - 200, dlg_h - 50), "Cancel      OK", font=font(20, True), fill=(232, 200, 114, 255))
    info.alpha_composite(dlg, ((info.width - dlg_w) // 2, (info.height - dlg_h) // 2))
    out4 = DIST / "preview-0.10.52-nova-overlay.png"
    info.convert("RGB").save(out4, "PNG")
    print("wrote", out4)

if __name__ == "__main__":
    main()
