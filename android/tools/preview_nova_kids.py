#!/usr/bin/env python3
"""Render preview-0.10.54-*.png of Nova on the Kids home (no device needed)."""
from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
FACE = ROOT / "app/src/main/assets/nova-face.jpg"
CUTOUT = ROOT / "app/src/main/assets/nova-face-cutout.png"
BASE = DIST / "preview-0.10.51-covers.png"
FX, FY, FW, FH = 0.18, 0.02, 0.66, 0.78
LEX, LEY, LEW, LEH = 0.352, 0.370, 0.108, 0.052
REX, REY, REW, REH = 0.518, 0.344, 0.124, 0.054
MX, MY, MW, MH = 0.418, 0.488, 0.195, 0.070
OX, OY, OW, OH = 0.400, 0.470, 0.235, 0.125
MOUTH_FRAMES = [ROOT / f"app/src/main/assets/nova-mouth-{i}.png" for i in (1, 2, 3)]
LID = (176, 137, 120, 255)

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

def load_face():
    src_path = CUTOUT if CUTOUT.exists() else FACE
    return Image.open(src_path).convert("RGBA")

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
    """Cross-fade the pre-rendered mouth frames from her own portrait."""
    if open_ <= 0.04:
        return head
    frames = []
    for pth in MOUTH_FRAMES:
        if pth.exists():
            frames.append(Image.open(pth).convert("RGBA"))
    if not frames:
        return head
    if open_ < 0.33:
        a, b, t = 0, 0, (open_ - 0.04) / 0.29
    elif open_ < 0.66:
        a, b, t = 0, 1, (open_ - 0.33) / 0.33
    else:
        a, b, t = 1, 2, (open_ - 0.66) / 0.34
    t = max(0.0, min(1.0, t))
    box = [int(round(v)) for v in map_box(head, OX, OY, OW, OH)]
    out = head.convert("RGBA")
    def paste(idx, alpha):
        if idx >= len(frames) or alpha <= 0:
            return
        im = frames[idx].resize((max(1, box[2] - box[0]), max(1, box[3] - box[1])), Image.Resampling.LANCZOS)
        if alpha < 0.999:
            ch = im.split()
            im = Image.merge("RGBA", (*ch[:3], ch[3].point(lambda v: int(v * alpha))))
        out.alpha_composite(im, (box[0], box[1]))
    if a == b:
        paste(a, t)
    else:
        paste(a, 1.0)
        if t > 0.02:
            paste(b, t)
    return out

def cutout_bubble(head, size, ring=0):
    """Floating cut-out with a soft drop shadow. No circular mask or gold ring."""
    head = head.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    pad = 18
    canvas = Image.new("RGBA", (size + pad * 2, size + pad * 2), (0, 0, 0, 0))
    # Shadow from the cut-out's own alpha, offset down-right and blurred.
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow = head.copy()
    a = shadow.split()[3].point(lambda v: int(v * 0.45) if v else 0)
    shadow = Image.merge("RGBA", (Image.new("L", head.size, 0), Image.new("L", head.size, 0), Image.new("L", head.size, 0), a))
    sh.alpha_composite(shadow, (pad + 8, pad + 12))
    sh = sh.filter(ImageFilter.GaussianBlur(6))
    canvas.alpha_composite(sh)
    canvas.alpha_composite(head, (pad, pad))
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
    d.rounded_rectangle((24, 206, w - 118, 248), radius=14, fill=(16, 24, 32, 102), outline=(232, 200, 114, 51), width=1)
    d.text((34, 216), "Ask about games or learning…", font=font(16), fill=(184, 192, 200, 160))
    # Mic button (Voice talk)
    d.rounded_rectangle((w - 106, 206, w - 24, 248), radius=14, fill=(32, 40, 48, 110), outline=(232, 200, 114, 140), width=2)
    d.text((w - 92, 216), "Mic", font=font(16, True), fill=(232, 200, 114, 255))
    wrap(d, body, 24, 268, font(18), (236, 240, 244, 255), w - 48, 26)
    # Foot then Ask / Close (48 dp-ish), matching the 0.10.54 panel order
    d.text((24, h - 92), "I can help with games, learning, or a joke. For anything else, ask a grown-up.", font=font(13), fill=(138, 152, 164, 255))
    d.rounded_rectangle((24, h - 72, w // 2 - 12, h - 20), radius=16, fill=(32, 40, 48, 110), outline=(232, 200, 114, 140), width=2)
    d.rounded_rectangle((w // 2 + 4, h - 72, w - 24, h - 20), radius=16, fill=(32, 40, 48, 110), outline=(232, 200, 114, 140), width=2)
    d.text((24 + 36, h - 58), "Ask", font=font(20, True), fill=(232, 200, 114, 255))
    d.text((w // 2 + 40, h - 58), "Close", font=font(20, True), fill=(232, 200, 114, 255))
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


def place_panel_at(bg, card, x, y):
    y = max(36, y)
    region = bg.crop((x, y, x + card.width, y + card.height)).filter(ImageFilter.GaussianBlur(14))
    mask = Image.new("L", card.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, card.width - 1, card.height - 1), radius=28, fill=255)
    glass = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    glass.paste(region, (x, y), mask)
    bg.alpha_composite(glass)
    bg.alpha_composite(card, (x, y))

def draw_edge_tab(bg, side="right", y_frac=1.0):
    tw, th = 44, 120
    miny, maxy = 36, bg.height - th - 36
    y = round(miny + max(0.0, min(1.0, y_frac)) * (maxy - miny))
    x = 0 if side == "left" else bg.width - tw
    tab = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    d = ImageDraw.Draw(tab)
    d.rounded_rectangle((0, 0, tw - 1, th - 1), radius=22, fill=(20, 26, 34, 220), outline=(232, 200, 114, 255), width=3)
    # Peek of Nova's face inside the tab
    try:
        src = load_face()
        peek = cutout_bubble(apply_gaze(head_crop(src), 0.12, -0.04), th - 16, ring=0)
        # crop to tab width so she peeks from the edge
        left = 8 if side == "left" else peek.width - (tw - 8)
        cropped = peek.crop((max(0, left), 0, max(0, left) + tw - 8, peek.height))
        tab.alpha_composite(cropped, (4 if side == "left" else 2, 8))
    except Exception:
        pass
    bg.alpha_composite(tab, (x, y))
    return x, y

def main():
    DIST.mkdir(parents=True, exist_ok=True)
    src = load_face()
    head = head_crop(src)
    idle = apply_gaze(head, 0.12, -0.04)
    talk = apply_mouth(apply_gaze(head, -0.15, 0.05), 0.70)

    home = kids_home()
    bub = cutout_bubble(idle, 220, ring=7)
    paste_bubble(home, bub, margin=40)
    out1 = DIST / "preview-0.10.54-kids-home.png"
    home.convert("RGB").save(out1, "PNG")
    print("wrote", out1, home.size)

    exp = kids_home()
    face = cutout_bubble(talk, 200, ring=7)
    bx = paste_bubble(exp, face, margin=40)
    card = kids_panel(1100, 740, "Chat",
        "Hi, I'm Nova! I can help you find a game, talk about the Learning games, or tell you a joke. If you need anything else, ask a grown-up.")
    place_panel(exp, card, bx, face.width)
    out2 = DIST / "preview-0.10.54-kids-expanded.png"
    exp.convert("RGB").save(out2, "PNG")
    print("wrote", out2, exp.size)

    joke = kids_home()
    face2 = cutout_bubble(talk, 200, ring=7)
    bx = paste_bubble(joke, face2, margin=40)
    card2 = kids_panel(1100, 740, "Chat",
        "Why did the banana go to the doctor? It wasn't peeling well.")
    place_panel(joke, card2, bx, face2.width)
    out3 = DIST / "preview-0.10.54-kids-joke.png"
    joke.convert("RGB").save(out3, "PNG")
    print("wrote", out3)

    moved = kids_home()
    face3 = cutout_bubble(idle, 200, ring=7)
    bx = paste_bubble(moved, face3, margin=40, side="left", y_frac=0.45)
    card3 = kids_panel(1100, 740, "Chat",
        "Hi, I'm Nova! I can help you find a game, talk about the Learning games, or tell you a joke. If you need anything else, ask a grown-up.")
    place_panel(moved, card3, bx, face3.width)
    out4 = DIST / "preview-0.10.54-kids-moved.png"
    moved.convert("RGB").save(out4, "PNG")
    print("wrote", out4)

    # Near-full-screen portrait + Chat (pinch-out)
    big = kids_home()
    face_n = 520
    faceb = cutout_bubble(talk, face_n, ring=8)
    # Portrait on the right (default edge), Chat fills the left — same as a right-edge bubble going big.
    margin = 40
    fx = big.width - faceb.width - margin
    fy = (big.height - faceb.height) // 2
    cardb = kids_panel(big.width - faceb.width - margin * 3, big.height - 80, "Chat",
        "Hi, I'm Nova! I can help you find a game, talk about the Learning games, or tell you a joke. If you need anything else, ask a grown-up.")
    place_panel_at(big, cardb, margin, (big.height - cardb.height) // 2)
    big.alpha_composite(faceb, (fx, fy))
    out5 = DIST / "preview-0.10.54-kids-big.png"
    big.convert("RGB").save(out5, "PNG")
    print("wrote", out5, big.size)

    # Double-tap hide: only a tiny edge tab where she was (bottom-right by default)
    hid = kids_home()
    draw_edge_tab(hid, side="right", y_frac=1.0)
    out6 = DIST / "preview-0.10.54-kids-hidden.png"
    hid.convert("RGB").save(out6, "PNG")
    print("wrote", out6)

    # Kids-mode self-update: Wi-Fi & Update badge + "New update ready" popup, no PIN field
    upd = kids_home()
    bubu = cutout_bubble(idle, 200)
    paste_bubble(upd, bubu, margin=40)
    d = ImageDraw.Draw(upd)
    # Wi-Fi & Update strip
    d.rounded_rectangle((480, 24, 1280, 88), radius=16, fill=(20, 22, 28, 230), outline=(232, 200, 114, 180), width=2)
    d.text((500, 38), "Wi-Fi  &  Update", font=font(28, True), fill=(244, 228, 193, 255))
    d.rounded_rectangle((1020, 36, 1260, 76), radius=14, fill=(196, 80, 48, 230), outline=(232, 200, 114, 180), width=1)
    d.text((1036, 44), "UPDATE AVAILABLE", font=font(16, True), fill=(255, 244, 220, 255))
    # Popup — no PIN field
    pw, ph = 820, 420
    px, py = (upd.width - pw) // 2, (upd.height - ph) // 2
    # dim
    dim = Image.new("RGBA", upd.size, (0, 0, 0, 90))
    upd.alpha_composite(dim)
    card = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    dc = ImageDraw.Draw(card)
    dc.rounded_rectangle((0, 0, pw - 1, ph - 1), radius=24, fill=(24, 28, 36, 245), outline=(232, 200, 114, 180), width=2)
    dc.text((36, 28), "New update ready", font=font(32, True), fill=(244, 228, 193, 255))
    wrap(dc, "CodeAque 0.10.54 is ready. In Kids mode you can install this CodeAque update without the parent PIN. Leaving Kids mode and Settings still need the PIN.",
         36, 90, font(22), (236, 240, 244, 255), pw - 72, 32)
    dc.rounded_rectangle((36, ph - 120, pw // 2 - 16, ph - 36), radius=18, fill=(32, 40, 48, 200), outline=(232, 200, 114, 200), width=2)
    dc.rounded_rectangle((pw // 2 + 8, ph - 120, pw - 36, ph - 36), radius=18, fill=(32, 40, 48, 160), outline=(232, 200, 114, 120), width=2)
    dc.text((36 + 48, ph - 96), "Update now", font=font(26, True), fill=(232, 200, 114, 255))
    dc.text((pw // 2 + 80, ph - 96), "Later", font=font(26, True), fill=(200, 208, 216, 255))
    upd.alpha_composite(card, (px, py))
    out7 = DIST / "preview-0.10.54-kids-update.png"
    upd.convert("RGB").save(out7, "PNG")
    print("wrote", out7)

if __name__ == "__main__":
    main()
