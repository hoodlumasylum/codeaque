"""Rough previews of the boot screen (GRUB menu and Plymouth splash) from atarilauncher/bootscreen, for docs and checking the layout.
The real look depends on GRUB / Plymouth on the computer. Usage: preview_boot.py [out_dir]"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

A = Path(__file__).resolve().parent.parent / "atarilauncher" / "bootscreen"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(n):
    try:
        return ImageFont.truetype(FONT, n)
    except OSError:
        return ImageFont.load_default()


def grub(w=1920, h=1080):
    im = Image.open(A / "grub-1920x1080.png").convert("RGB").resize((w, h))
    d = ImageDraw.Draw(im)
    x, y, mw, ih = int(w * .27), int(h * .68), int(w * .46), 68
    d.rectangle((x, y, x + mw, y + ih), fill=(120, 8, 8), outline=(255, 42, 42), width=3)
    for i, t in enumerate(("CodeAque", "Windows")):
        d.text((x + 18, y + i * (ih + 8) + 12), t, font=font(36), fill=(255, 255, 255) if i == 0 else (232, 234, 242))
    tx = "Starting CodeAque in 5s"
    d.text((w // 2 - d.textlength(tx, font=font(24)) // 2, int(h * .92)), tx, font=font(24), fill=(255, 138, 138))
    return im


def plymouth(w=1920, h=1080, progress=0.55, lit=1):
    im = Image.open(A / "background-1920x1080.png").convert("RGB").resize((w, h))
    k = max(w / 1672, h / 941); bw, bh = 1672 * k, 941 * k; bx, by = (w - bw) / 2, (h - bh) / 2
    d = ImageDraw.Draw(im)
    bx0, by0, bwid, bhh = bx + bw * 523 / 1672, by + bh * 682 / 941, bw * 627 / 1672, bh * 19 / 941
    d.rectangle((bx0, by0, bx0 + bwid * progress, by0 + bhh), fill=(255, 42, 42))
    dx = bx + bw * (757 + 46 * lit) / 1672
    d.rectangle((dx, by + bh * 741 / 941, dx + bhh, by + bh * 741 / 941 + bhh), fill=(255, 42, 42))
    return im


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "docs")
    grub().save(out / "screenshot-boot-menu.png")
    plymouth().save(out / "screenshot-boot-splash.png")
    print("written to", out)
