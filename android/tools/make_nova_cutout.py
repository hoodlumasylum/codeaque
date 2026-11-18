#!/usr/bin/env python3
"""Build nova-face-cutout.png from nova-face.jpg: studio backdrop fully keyed out,
hair kept, soft feather, no dark disc left behind her.
"""
from __future__ import annotations
from collections import deque
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/assets/nova-face.jpg"
OUT = ROOT / "app/src/main/assets/nova-face-cutout.png"

def main():
    src = Image.open(SRC).convert("RGB")
    arr = np.array(src)
    h, w, _ = arr.shape
    r, g, b = arr[:, :, 0].astype(int), arr[:, :, 1].astype(int), arr[:, :, 2].astype(int)
    lum = (0.2126 * r + 0.7152 * g + 0.0722 * b)
    chroma = np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)
    lum_im = Image.fromarray(np.clip(lum, 0, 255).astype(np.uint8), "L")
    rng = np.array(lum_im.filter(ImageFilter.MaxFilter(7))).astype(int) - np.array(
        lum_im.filter(ImageFilter.MinFilter(7))
    ).astype(int)

    # Subject seeds: gold jewellery / feather, skin, bright areas, textured hair.
    gold = (r > 90) & (g > 50) & (r > b + 20)
    skin = (r > 70) & (r > g + 10) & (r > b) & (lum > 110)
    hairish = (lum > 24) & (rng > 16)
    seeds = gold | skin | (lum > 70) | hairish
    sub = np.array(Image.fromarray(seeds.astype(np.uint8) * 255, "L").filter(ImageFilter.MaxFilter(9))) > 0
    sub[gold] = True
    sub[skin] = True

    # Backdrop: near-black, low chroma, low texture (studio, not braids).
    def is_backdrop(x, y):
        return lum[y, x] <= 48 and chroma[y, x] <= 22 and rng[y, x] <= 18

    bg = np.zeros((h, w), dtype=np.uint8)
    q = deque()

    def seed(x, y):
        if 0 <= x < w and 0 <= y < h and not bg[y, x] and not sub[y, x] and is_backdrop(x, y):
            bg[y, x] = 1
            q.append((x, y))

    for x in range(w):
        seed(x, 0)
        seed(x, h - 1)
    for y in range(h):
        seed(0, y)
        seed(w - 1, y)
    # Also seed the wide-crop corners, which used to leave a dark halo.
    for yy in (0, 1, 2, h - 1, h - 2, h - 3):
        for xx in range(0, w, 4):
            seed(xx, yy)
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not bg[ny, nx] and not sub[ny, nx] and is_backdrop(nx, ny):
                bg[ny, nx] = 1
                q.append((nx, ny))

    # Grow the backdrop only into leftover dark fringe (not gold, skin, or textured hair).
    halo = np.array(Image.fromarray(bg * 255, "L").filter(ImageFilter.MaxFilter(13))) > 0
    grow = halo & (~gold) & (~skin) & (lum <= 42) & (chroma <= 20) & (rng <= 20)
    bg[grow] = 1
    bg[gold] = 0
    bg[skin] = 0

    # Hard-kill backdrop, then a short feather only at the subject edge.
    alpha = np.where(bg == 1, 0, 255).astype(np.uint8)
    alpha[gold] = 255
    a_img = Image.fromarray(alpha, "L")
    # Small dilate keeps flyaway hair, then a tight blur for a soft edge — not a dark disc.
    a_img = a_img.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(radius=1.6))
    core = np.array(a_img)
    # Anything that was flooded backdrop must stay fully transparent (blur must not
    # pull a semi-opaque dark halo back in).
    core[bg == 1] = np.minimum(core[bg == 1], 0)
    # Force a firm interior; keep the short feather on the rim.
    core = np.where((core > 200) & (bg == 0), 255, core).astype(np.uint8)

    rgba = src.convert("RGBA")
    rgba.putalpha(Image.fromarray(core, "L"))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    rgba.save(OUT, "PNG")
    print("wrote", OUT, OUT.stat().st_size,
          "opaque", float((core > 250).mean()),
          "trans", float((core < 8).mean()),
          "mid", float(((core >= 8) & (core <= 250)).mean()))

if __name__ == "__main__":
    main()
