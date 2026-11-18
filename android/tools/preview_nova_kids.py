#!/usr/bin/env python3
"""Render preview-0.10.53-*.png of Nova on the Kids home (no device needed)."""
from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
FACE = ROOT / "app/src/main/assets/nova-face.jpg"
BASE = DIST / "preview-0.10.51-covers.png"
FX, FY, FW, FH = 0.18, 0.02, 0.66, 0.78
LEX, LEY, LEW, LEH = 0.352, 0.370, 0.108, 0.052
REX, REY, REW, REH = 0.518, 0.344, 0.124, 0.054
MX, MY, MW, MH = 0.418, 0.488, 0.195, 0.070
LID = (176, 137, 120, 255)
LIP_U, LIP_L, INNER, TEETH = (195, 138, 114, 255), (168, 107, 85, 255), (42, 14, 10, 255), (232, 212, 200, 255)

def font(size, bold=False):
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def head_crop(src):
    w, h = src.size
    return src.crop((round(FX * w), round(FY * h), round((FX + FW) * w), round((FY + FH) * h)))

def map_box(head, nx, ny, nw, nh):
    bw, bh = head.size
    x, y = (nx - FX) / FW, (ny - FY) / FH
    return (x * bw, y * bh, (x + nw / FW) * bw, (y + nh / FH) * bh)

def apply_gaze(head, gx=0.0, gy=0.0):
    out = head.copy()
    for e in ((LEX, LEY, LEW, LEH), (REX, REY, REW, REH)):
        box = map_box(head, *e)
        l, t, r, b = box
        ox, oy = gx * (r - l) * 0.16, gy * (b - t) * 0.18
        shifted = Image.new("RGBA", head.size, (0, 0, 0, 0))
        shifted.paste(head, (round(ox), round(oy)))
        mask = Image.new("L", head.size, 0)
        ImageDraw.Draw(mask).ellipse(box, fill=255)
        out = Image.composite(shifted, out, mask)
    return out

def apply_mouth(head, open_=0.7):
    if open_ <= 0.04:
        return head
    out = head.copy()
    d = ImageDraw.Draw(out)
    l, t, r, b = map_box(head, MX, MY, MW, MH)
    h = (b - t) * (0.28 + 0.85 * open_)
    cx, mid = (l + r) / 2, (t + b) / 2 + (b - t) * 0.08
    hole = [l + (r - l) * 0.12, mid - h * 0.45, r - (r - l) * 0.12, mid + h * 0.55]
    d.ellipse(hole, fill=INNER)
    th = (hole[3] - hole[1]) * 0.22
    d.ellipse([hole[0] + 2, hole[1] + 1, hole[2] - 2, hole[1] + th + 1], fill=TEETH)
    d.arc(hole, 10, 170, fill=LIP_L, width=6)
    d.arc(hole, 190, 350, fill=LIP_U, width=5)
    return out

def circle_bubble(head, size, ring=8):
    head = head.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    bubble = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bubble.paste(head, (0, 0), mask)
    canvas = Image.new("RGBA", (size + 16, size + 16), (0, 0, 0, 0))
    sh = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(sh).ellipse((6, 8, 6 + size, 8 + size), fill=140)
    sh = sh.filter(ImageFilter.GaussianBlur(5))
    canvas.paste((0, 0, 0, 160), (0, 0), sh)
    canvas.paste(bubble, (8, 8), bubble)
    ImageDraw.Draw(canvas).ellipse((8, 8, 8 + size - 1, 8 + size - 1), outline=(232, 200, 114, 255), width=ring)
    return canvas

def paste_bubble(bg, bubble, margin=36, side="right", y_frac=1.0):
    if side == "left":
        x = margin
    else:
        x = bg.width - bubble.width - margin
    miny, maxy = margin, bg.height - bubble.height - margin
    y = round(miny + max(0.0, min(1.0, y_frac)) * (maxy - miny))
    bg.alpha_composite(bubble, (x, y))
    return x, y

def kids_home(size=None):
    if BASE.exists():
        im = Image.open(BASE).convert("RGBA")
    else:
        im = Image.new("RGBA", (1920, 1080), (18, 22, 36, 255))
    if size and im.size != size:
        im = im.resize(size, Image.Resampling.LANCZOS)
    # Kids-mode banner so the preview is clearly the Kids home
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((36, 24, 420, 88), radius=16, fill=(20, 22, 28, 230), outline=(232, 200, 114, 180), width=2)
    d.text((56, 38), "Kids mode", font=font(36, True), fill=(244, 228, 193, 255))
    return im

def wrap(d, text, x, y, font_, fill, maxw, lh):
    line, yy = "", y
    for word in text.split():
        trial = (line + " " + word).strip()
        if d.textlength(trial, font=font_) <= maxw:
            line = trial
        else:
            d.text((x, yy), line, font=font_, fill=fill)
            yy += lh
            line = word
    if line:
        d.text((x, yy), line, font=font_, fill=fill)
        yy += lh
    return yy

def kids_panel(w, h, title, body, talking=False):
    # Glassy dark card: home shows through, soft gold edge, title is just "Chat".
    card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=28, fill=(20, 26, 34, 184), outline=(232, 200, 114, 102), width=2)
    d.text((24, 20), "Chat", font=font(30, True), fill=(242, 246, 250, 255))
    d.rounded_rectangle((w - 170, 18, w - 24, 56), radius=16, fill=(32, 40, 48, 90), outline=(232, 200, 114, 68), width=1)
    d.text((w - 154, 26), "Sound on", font=font(16, True), fill=(232, 200, 114, 255))
    chips = ["Find a game", "How do I play?", "Learning"]
    x, y = 24, 80
    for c in chips:
        tw = d.textlength(c, font=font(16))
        d.rounded_rectangle((x, y, x + tw + 24, y + 36), radius=16, fill=(32, 40, 48, 90), outline=(232, 200, 114, 68), width=1)
        d.text((x + 12, y + 8), c, font=font(16), fill=(242, 246, 250, 255))
        x += int(tw) + 32
    d.rounded_rectangle((24, 132, w - 24, 190), radius=18, fill=(32, 40, 48, 90), outline=(232, 200, 114, 102), width=2)
    d.text((44, 148), "Tell me a joke", font=font(22, True), fill=(232, 200, 114, 255))
    d.rounded_rectangle((24, 206, w - 24, 248), radius=14, fill=(16, 24, 32, 102), outline=(232, 200, 114, 51), width=1)
    d.text((34, 216), "Ask about games or learning…", font=font(16), fill=(184, 192, 200, 160))
    wrap(d, body, 24, 268, font(18), (236, 240, 244, 255), w - 48, 26)
    d.text((24, h - 52), "Ask          Close", font=font(16, True), fill=(232, 200, 114, 255))
    d.text((24, h - 28), "I can help with games, learning, or a joke. For anything else, ask a grown-up.", font=font(13), fill=(138, 152, 164, 255))
    return card

def place_panel(bg, card, bubble_xy, bubble_w, gap=16):
    bx, by = bubble_xy
    # Open on the opposite side of the bubble.
    if bx < bg.width * 0.5:
        x = bx + bubble_w + gap
    else:
        x = max(36, bx - gap - card.width)
    y = max(36, (bg.height - card.height) // 2)
    # Soft blur of the home under the glass
    region = bg.crop((x, y, x + card.width, y + card.height)).filter(ImageFilter.GaussianBlur(14))
    mask = Image.new("L", card.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, card.width - 1, card.height - 1), radius=28, fill=255)
    glass = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    glass.paste(region, (x, y), mask)
    bg.alpha_composite(glass)
    bg.alpha_composite(card, (x, y))

def main():
    DIST.mkdir(parents=True, exist_ok=True)
    src = Image.open(FACE).convert("RGBA")
    head = head_crop(src)
    idle = apply_gaze(head, 0.12, -0.04)
    talk = apply_mouth(apply_gaze(head, -0.15, 0.05), 0.85)

    home = kids_home()
    bub = circle_bubble(idle, 220, ring=7)
    paste_bubble(home, bub, margin=40)
    out1 = DIST / "preview-0.10.53-kids-home.png"
    home.convert("RGB").save(out1, "PNG")
    print("wrote", out1, home.size)

    exp = kids_home()
    face = circle_bubble(talk, 200, ring=7)
    bx = paste_bubble(exp, face, margin=40)
    card = kids_panel(1100, 740, "Chat",
        "Hi, I'm Nova! I can help you find a game, talk about the Learning games, or tell you a joke. If you need anything else, ask a grown-up.")
    place_panel(exp, card, bx, face.width)
    out2 = DIST / "preview-0.10.53-kids-expanded.png"
    exp.convert("RGB").save(out2, "PNG")
    print("wrote", out2, exp.size)

    joke = kids_home()
    face2 = circle_bubble(talk, 200, ring=7)
    bx = paste_bubble(joke, face2, margin=40)
    card2 = kids_panel(1100, 740, "Chat",
        "Why did the banana go to the doctor? It wasn't peeling well.")
    place_panel(joke, card2, bx, face2.width)
    out3 = DIST / "preview-0.10.53-kids-joke.png"
    joke.convert("RGB").save(out3, "PNG")
    print("wrote", out3)

    moved = kids_home()
    face3 = circle_bubble(idle, 200, ring=7)
    bx = paste_bubble(moved, face3, margin=40, side="left", y_frac=0.45)
    card3 = kids_panel(1100, 740, "Chat",
        "Hi, I'm Nova! I can help you find a game, talk about the Learning games, or tell you a joke. If you need anything else, ask a grown-up.")
    place_panel(moved, card3, bx, face3.width)
    out4 = DIST / "preview-0.10.53-kids-moved.png"
    moved.convert("RGB").save(out4, "PNG")
    print("wrote", out4)

if __name__ == "__main__":
    main()
