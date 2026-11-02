"""Builds CodeAque's boot-screen pictures from Code's splash (atarilauncher/assets/splash.png, 1672x941, bar and dots emptied):
  background-WxH.png   the splash (Plymouth and the login screen; Plymouth fills the bar and lights the dots live)
  grub-WxH.png         the splash with a dark panel over the bar area, where the GRUB menu ("CodeAque" / "Windows") sits
  code-glow.png        a soft red glow over the bright parts (Plymouth pulses it)
  code-line/track/head, dot.png, select_*.png   bar, dots and the GRUB selection bar in CodeAque red
Run: python scripts/make_boot_assets.py [splash.png] [out_dir]. Needs Pillow. Original art only."""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RED, RED_DARK = (255, 42, 42), (120, 8, 8)
PANEL = (0.24, 0.655, 0.76, 0.885)     # x0, y0, x1, y1 as fractions of the splash: "INITIALISING SYSTEM", the bar and the dots


def fit(art, w, h):
    sw, sh = art.size
    k = max(w / sw, h / sh)
    im = art.resize((round(sw * k), round(sh * k)), Image.LANCZOS)
    l, t = (im.width - w) // 2, (im.height - h) // 2
    return im.crop((l, t, l + w, t + h)).filter(ImageFilter.UnsharpMask(1.2, 60, 2))


def grub_art(art):
    im = art.copy().convert("RGBA")
    w, h = im.size
    x0, y0, x1, y1 = (int(PANEL[0] * w), int(PANEL[1] * h), int(PANEL[2] * w), int(PANEL[3] * h))
    lay = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(h * 0.02), fill=(6, 6, 9, 255), outline=(150, 20, 20, 255), width=max(2, h // 300))
    lay = lay.filter(ImageFilter.GaussianBlur(0.6))
    return Image.alpha_composite(im, lay).convert("RGB")


def art_glow(art, out: Path):
    small = art.resize((art.width // 2, art.height // 2), Image.LANCZOS)
    r, g, b = small.split()
    redness = Image.eval(Image.merge("RGB", (r, g, b)).convert("L"), lambda v: v)
    mask = Image.composite(r, Image.new("L", small.size), r.point(lambda v: 255 if v > 170 else 0))
    mask = Image.eval(mask, lambda v: v)
    del redness, g, b
    glow = Image.new("RGBA", small.size, RED + (0,))
    glow.putalpha(mask.filter(ImageFilter.GaussianBlur(10)).point(lambda v: min(200, int(v * 1.4))))
    glow.save(out / "code-glow.png", optimize=True)


def selection_bar(out: Path):
    for part, size in (("c", (8, 8)), ("n", (8, 3)), ("s", (8, 3)), ("e", (3, 8)), ("w", (3, 8)),
                       ("ne", (3, 3)), ("nw", (3, 3)), ("se", (3, 3)), ("sw", (3, 3))):
        Image.new("RGB", size, RED_DARK if part == "c" else RED).save(out / f"select_{part}.png")


def main():
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "atarilauncher/assets/splash.png")
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "atarilauncher/bootscreen")
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    art = Image.open(src).convert("RGB")
    g = grub_art(art)
    for w, h in ((1920, 1080), (1366, 768)):
        fit(art, w, h).save(out / f"background-{w}x{h}.png", optimize=True)
        fit(g, w, h).save(out / f"grub-{w}x{h}.png", optimize=True)
    selection_bar(out)
    art_glow(art, out)
    Image.new("RGB", (8, 4), RED).save(out / "code-line.png")
    Image.new("RGB", (8, 4), (40, 40, 46)).save(out / "code-track.png")
    hd = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    ImageDraw.Draw(hd).ellipse((0, 0, 13, 13), fill=(255, 190, 190, 255))
    hd.filter(ImageFilter.GaussianBlur(1.2)).save(out / "code-head.png")
    Image.new("RGB", (8, 8), RED).save(out / "dot.png")
    Image.new("RGBA", (1, 1), (0, 0, 0, 0)).save(out / "blank.png")
    print("written to", out)


if __name__ == "__main__":
    main()
