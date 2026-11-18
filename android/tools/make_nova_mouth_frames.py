#!/usr/bin/env python3
"""Build feathered talking-mouth overlays from Nova's own portrait, into app assets."""
from __future__ import annotations
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageFilter, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PORTRAIT = ROOT / "app/src/main/assets/nova-face.jpg"
ASSETS = ROOT / "app/src/main/assets"
DIST = ROOT / "dist"
# Overlay box on the 1024 portrait (full lower lip, room to feather).
OX, OY, OW, OH = 0.400, 0.470, 0.235, 0.125
# Mouth corners in portrait pixels (from the crease map).
CORNER_L, CORNER_R = 462, 594

def smooth(arr, k=11):
    k = max(3, int(k) | 1)
    p = k // 2
    return np.convolve(np.pad(arr.astype(np.float64), (p, p), mode="edge"), np.ones(k) / k, mode="valid")

def seam_coeff(rgb):
    h, w, _ = rgb.shape
    xs, ys = [], []
    for x in range(CORNER_L, CORNER_R + 1):
        besty, bestl = None, 10**9
        for y in range(515, 572):
            r, g, b = int(rgb[y, x, 0]), int(rgb[y, x, 1]), int(rgb[y, x, 2])
            lum = r + g + b
            if lum < bestl:
                bestl, besty = lum, y
        if bestl < 160:
            xs.append(x)
            ys.append(besty)
    xs, ys = np.array(xs, float), smooth(np.array(ys, float), 11)
    return np.polyfit(xs, ys, 4)

def is_tooth(r, g, b):
    lum = r + g + b
    return r >= 145 and g >= 108 and lum >= 370 and (r - b) < 95

def is_cavity(r, g, b):
    return (r + g + b) < 110

def sample(src, x, y):
    h, w, _ = src.shape
    if x < 0 or x >= w - 1 or y < 0 or y >= h - 1:
        xi = int(np.clip(round(x), 0, w - 1))
        yi = int(np.clip(round(y), 0, h - 1))
        return src[yi, xi].astype(np.float64)
    x0 = int(math.floor(x)); y0 = int(math.floor(y))
    x1 = min(x0 + 1, w - 1); y1 = min(y0 + 1, h - 1)
    tx, ty = x - x0, y - y0
    c00 = src[y0, x0].astype(np.float64)
    c10 = src[y0, x1].astype(np.float64)
    c01 = src[y1, x0].astype(np.float64)
    c11 = src[y1, x1].astype(np.float64)
    return c00 * (1 - tx) * (1 - ty) + c10 * tx * (1 - ty) + c01 * (1 - tx) * ty + c11 * tx * ty

def mix(a, b, t):
    return a * (1 - t) + b * t

def make_overlay(face, coeff, open_px, scale=2):
    W, H = face.size
    l, top0 = int(round(OX * W)), int(round(OY * H))
    r, btm0 = int(round((OX + OW) * W)), int(round((OY + OH) * H))
    roi = face.crop((l, top0, r, btm0))
    roi2 = roi.resize((roi.width * scale, roi.height * scale), Image.Resampling.LANCZOS)
    src = np.array(roi2)
    h, w, _ = src.shape
    # map full-portrait coords into this 2x ROI
    def to_roi_x(px):
        return (px - l) * scale
    def to_roi_y(py):
        return (py - top0) * scale

    left = to_roi_x(CORNER_L)
    right = to_roi_x(CORNER_R)
    # seam and rest upper/lower edges, in ROI 2x pixels
    seam = np.zeros(w, dtype=np.float64)
    rest_top = np.zeros(w, dtype=np.float64)
    rest_bot = np.zeros(w, dtype=np.float64)
    win = np.zeros(w, dtype=np.float64)
    rgb_full = np.array(face.convert("RGB"))
    for x in range(w):
        px = l + x / scale  # portrait x
        py = float(np.polyval(coeff, px))
        seam[x] = to_roi_y(py)
        if left < x < right:
            tt = (x - left) / (right - left)
            win[x] = math.sin(math.pi * tt) ** 1.65
        # rest opening: walk up from the seam for teeth/cavity
        sy = seam[x]
        top = sy
        # walk up in 2x space, sampling the 2x image
        max_up = 18 * scale / 2
        for i in range(1, int(max_up) + 1):
            yy = sy - i
            if yy < 1: break
            r_, g_, b_ = [int(v) for v in src[int(round(yy)), x, :3]]
            if is_tooth(r_, g_, b_) or is_cavity(r_, g_, b_):
                top = yy
            else:
                if sy - top >= 2:
                    break
        bot = sy
        max_dn = 8 * scale / 2
        for i in range(1, int(max_dn) + 1):
            yy = sy + i
            if yy >= h - 1: break
            r_, g_, b_ = [int(v) for v in src[min(h-1, int(round(yy))), x, :3]]
            if is_tooth(r_, g_, b_) or is_cavity(r_, g_, b_):
                bot = yy
            else:
                if bot - sy >= 1:
                    break
        rest_top[x] = top
        rest_bot[x] = bot

    rest_top = smooth(rest_top, 13)
    rest_bot = smooth(rest_bot, 13)
    win = smooth(win, 9)

    up_shift = open_px * 0.36 * win
    lo_shift = open_px * 0.74 * win
    new_top = rest_top - up_shift
    new_bot = rest_bot + lo_shift

    # colours from the real opening
    cav_s, tooth_s = [], []
    for x in range(w):
        if win[x] < 0.2: continue
        a, c = int(round(rest_top[x])), int(round(rest_bot[x]))
        for y in range(max(0, a), min(h, c + 1)):
            px = src[y, x, :3].astype(np.int16)
            if is_cavity(int(px[0]), int(px[1]), int(px[2])):
                cav_s.append(px)
            elif is_tooth(int(px[0]), int(px[1]), int(px[2])):
                tooth_s.append(px)
    cav = np.median(cav_s, axis=0).astype(np.float64) if cav_s else np.array([40.0, 16.0, 12.0])
    # keep teeth as sampled — warm ivory, never a white blob
    tooth = np.median(tooth_s, axis=0).astype(np.float64) if tooth_s else np.array([200.0, 160.0, 145.0])
    inner = cav * 1.35 + np.array([18.0, 8.0, 6.0])

    out = np.zeros_like(src, dtype=np.float64)
    alpha = np.zeros((h, w), dtype=np.float64)
    fade_up = 28.0 * scale / 2
    fade_dn = 40.0 * scale / 2

    for x in range(w):
        if win[x] <= 0.0 and not (left - 10 <= x <= right + 10):
            continue
        nt, nb = new_top[x], new_bot[x]
        ot, ob = rest_top[x], rest_bot[x]
        us, ls = up_shift[x], lo_shift[x]
        orig_gap = max(1.0, ob - ot)
        new_gap = max(1.0, nb - nt)
        for y in range(h):
            if y < nt:
                dist = nt - y
                fade = 1.0 if dist < 1.5 else max(0.0, 1.0 - (dist - 1.5) / fade_up)
                if fade <= 0: continue
                sy = y + us
                out[y, x] = sample(src, x, sy)
                alpha[y, x] = fade
            elif y > nb:
                dist = y - nb
                fade = 1.0 if dist < 1.5 else max(0.0, 1.0 - (dist - 1.5) / fade_dn)
                if fade <= 0: continue
                sy = y - ls
                out[y, x] = sample(src, x, sy)
                alpha[y, x] = fade
            else:
                # new opening, 0 at upper inner lip, 1 at lower
                t = (y - nt) / new_gap
                # 1) a sliver of inner upper lip
                if t < 0.10:
                    u = t / 0.10
                    sy = ot + u * min(2.0, orig_gap * 0.2)
                    col = sample(src, x, sy)
                    col[:3] = mix(col[:3], inner, 0.4 * (1 - u))
                    out[y, x] = col
                # 2) original opening (real teeth + dark sides), modestly stretched
                elif t < 0.10 + min(0.50, orig_gap / new_gap):
                    span = min(0.50, orig_gap / new_gap)
                    u = (t - 0.10) / max(span, 1e-4)
                    # ease so teeth stay a band under the upper lip, not smeared tall
                    u = u ** 0.85
                    sy = ot + u * orig_gap
                    out[y, x] = sample(src, x, sy)
                # 3) dark cavity, then a thick blend into the inner lower lip
                else:
                    lip_blend = 0.28  # last 28% eases into the lower lip
                    if t < 1.0 - lip_blend:
                        shade = 0.50 + 0.50 * math.sin(math.pi * np.clip(t, 0, 1))
                        mid = 0.62 + 0.38 * win[x]
                        c = cav * (0.58 + 0.42 * shade) * (0.72 + 0.28 * mid)
                        if win[x] < 0.30:
                            c = mix(c, inner, 1.0 - win[x] / 0.30)
                        out[y, x, 0] = c[0]
                        out[y, x, 1] = c[1]
                        out[y, x, 2] = c[2]
                        out[y, x, 3] = 255
                    else:
                        u = (t - (1.0 - lip_blend)) / lip_blend
                        u = u * u * (3 - 2 * u)  # smoothstep
                        sy = ob - (1 - u) * min(3.0, orig_gap * 0.25)
                        col = sample(src, x, sy)
                        # start from cavity, ease into the real lower-lip inner edge
                        cav_col = np.array([cav[0], cav[1], cav[2], 255.0])
                        col = mix(mix(cav_col, np.append(inner, 255.0), 0.35), col, u)
                        out[y, x] = col
                alpha[y, x] = 1.0

    # horizontal fade just outside the corners so the overlay doesn't stamp a box
    for x in range(w):
        if x < left:
            fade = max(0.0, 1.0 - (left - x) / (10.0 * scale / 2))
            alpha[x] if False else None
            alpha[:, x] *= fade
        elif x > right:
            fade = max(0.0, 1.0 - (x - right) / (10.0 * scale / 2))
            alpha[:, x] *= fade

    rgb = np.clip(out, 0, 255).astype(np.uint8)
    # slight blur of colour to hide warp seams
    col_img = Image.fromarray(rgb, "RGBA").filter(ImageFilter.GaussianBlur(radius=0.9))
    a_img = Image.fromarray(np.clip(alpha * 255, 0, 255).astype(np.uint8), "L")
    a_img = a_img.filter(ImageFilter.GaussianBlur(radius=1.4))
    # Extra soften only the opening so the cavity doesn't look cut out.
    gap_m = Image.fromarray(np.clip(alpha * 255, 0, 255).astype(np.uint8), "L")
    gap_core = gap_m.point(lambda v: 255 if v > 250 else 0).filter(ImageFilter.GaussianBlur(radius=2.0))
    soft = col_img.filter(ImageFilter.GaussianBlur(radius=1.6))
    col_img = Image.composite(soft, col_img, gap_core)
    rgba = np.array(col_img)
    rgba[:, :, 3] = np.array(a_img)
    rgba[rgba[:, :, 3] == 0] = 0
    return Image.fromarray(rgba, "RGBA"), (l, top0, r, btm0)

def closeup(face, overlay, box, scale, tag, out_path):
    W, H = face.size
    face_big = face.resize((W * scale, H * scale), Image.Resampling.LANCZOS)
    l, t, r, b = box
    comp = face_big.copy()
    if overlay is not None:
        comp.alpha_composite(overlay, (int(l * scale), int(t * scale)))
    cl = int((OX - 0.03) * W * scale)
    ct = int((OY - 0.05) * H * scale)
    cr = int((OX + OW + 0.03) * W * scale)
    cb = int((OY + OH + 0.08) * H * scale)
    close = comp.crop((cl, ct, cr, cb)).resize(((cr - cl) * 2, (cb - ct) * 2), Image.Resampling.LANCZOS)
    close.convert("RGB").save(out_path)
    return close

def main():
    face = Image.open(PORTRAIT).convert("RGBA")
    rgb = np.array(face.convert("RGB"))
    coeff = seam_coeff(rgb)
    scale = 2
    ASSETS.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(parents=True, exist_ok=True)
    scratch = Path("/tmp/nova-mouth")
    scratch.mkdir(parents=True, exist_ok=True)
    # closed reference
    closed = closeup(face, None, (0, 0, 0, 0), scale, "00", scratch / "closeup-00-closed.png")
    amounts = [("1", 6), ("2", 14), ("3", 22)]
    closes = [closed.convert("RGB")]
    for name, px in amounts:
        ov, box = make_overlay(face, coeff, px, scale=scale)
        path = ASSETS / f"nova-mouth-{name}.png"
        ov.save(path)
        cpath = scratch / f"closeup-{name}.png"
        im = closeup(face, ov, box, scale, name, cpath)
        closes.append(im.convert("RGB"))
        print("wrote", path, "open_px", px, "->", cpath)
    # One contact closeup for review: closed, slightly parted, mid, open.
    w, h = closes[0].size
    # shrink each panel so the strip is easy to open
    pw, ph = 640, int(h * 640 / w)
    sheet = Image.new("RGB", (pw * 4, ph + 36), (18, 22, 28))
    labels = ["Rest", "Slight", "Mid", "Open"]
    from PIL import ImageDraw, ImageFont
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    except Exception:
        font = ImageFont.load_default()
    d = ImageDraw.Draw(sheet)
    for i, (im, lab) in enumerate(zip(closes, labels)):
        tile = im.resize((pw, ph), Image.Resampling.LANCZOS)
        sheet.paste(tile, (i * pw, 36))
        d.text((i * pw + 12, 6), lab, font=font, fill=(242, 246, 250))
    out = DIST / "preview-0.10.53-nova-mouth-closeup.png"
    sheet.save(out, "PNG")
    print("wrote", out)

if __name__ == "__main__":
    main()
