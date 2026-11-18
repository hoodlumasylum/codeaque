#!/usr/bin/env python3
"""Build nova-face-cutout.png from nova-face.jpg: studio backdrop keyed out, hair kept, soft feather."""
from __future__ import annotations
from collections import deque
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/assets/nova-face.jpg"
OUT = ROOT / "app/src/main/assets/nova-face-cutout.png"
FX, FY, FW, FH = 0.18, 0.02, 0.66, 0.78

def main():
    src = Image.open(SRC).convert("RGB")
    arr = np.array(src)
    h, w, _ = arr.shape
    r, g, b = arr[:,:,0].astype(int), arr[:,:,1].astype(int), arr[:,:,2].astype(int)
    lum = r + g + b
    lum_im = Image.fromarray(np.clip(lum, 0, 255).astype(np.uint8), "L")
    rng = np.array(lum_im.filter(ImageFilter.MaxFilter(7))).astype(int) - np.array(lum_im.filter(ImageFilter.MinFilter(7))).astype(int)
    chroma = np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)
    gold = (r > 90) & (g > 50) & (r > b + 20)
    skin = (r > 70) & (r > g + 10) & (r > b) & (lum > 110)
    hairish = (lum > 28) & (rng > 18)
    seeds = gold | skin | (lum > 70) | hairish
    sub_a = np.array(Image.fromarray(seeds.astype(np.uint8) * 255, "L").filter(ImageFilter.MaxFilter(9))) > 0
    sub_a[gold] = True
    x0, y0 = int(FX * w), int(FY * h)
    x1, y1 = int((FX + FW) * w), int((FY + FH) * h)
    bg = np.zeros((h, w), dtype=np.uint8)
    q = deque()
    def ok(x, y):
        return lum[y, x] <= 50 and chroma[y, x] <= 18 and rng[y, x] <= 16
    def seed(x, y):
        if 0 <= x < w and 0 <= y < h and not bg[y, x] and not sub_a[y, x] and ok(x, y):
            bg[y, x] = 1
            q.append((x, y))
    for x in range(w):
        seed(x, 0); seed(x, h - 1)
    for y in range(h):
        seed(0, y); seed(w - 1, y)
    for yy in range(y0, y0 + 80):
        for xx in range(x0, x0 + 80):
            seed(xx, yy)
        for xx in range(x1 - 80, x1):
            seed(xx, yy)
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not bg[ny, nx] and not sub_a[ny, nx] and ok(nx, ny):
                bg[ny, nx] = 1
                q.append((nx, ny))
    halo = np.array(Image.fromarray(bg * 255, "L").filter(ImageFilter.MaxFilter(11))) > 0
    grow = halo & (~gold) & (~skin) & (lum <= 55) & (chroma <= 22) & (lum < 90)
    bg[grow] = 1
    bg[gold] = 0
    alpha = np.where(bg == 1, 0, 255).astype(np.uint8)
    alpha[gold] = 255
    a_img = Image.fromarray(alpha, "L").filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(radius=3.4))
    core = np.array(a_img)
    core = np.where(core > 155, 255, core).astype(np.uint8)
    rgba = src.convert("RGBA")
    rgba.putalpha(Image.fromarray(core, "L"))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    rgba.save(OUT, "PNG")
    print("wrote", OUT, OUT.stat().st_size, "opaque", float((core > 250).mean()), "trans", float((core < 8).mean()))

if __name__ == "__main__":
    main()
