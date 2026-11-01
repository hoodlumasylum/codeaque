"""Draws CodeAque's own flowing ribbons for Handheld mode (gold and emerald light), seamless left-to-right.
Run: python3 tools/make_handheld_waves.py  ->  atarilauncher/assets/hh_wave1.png, hh_wave2.png"""
import math
from pathlib import Path
from PIL import Image, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "atarilauncher" / "assets"
W, H = 2048, 512


def ribbon(name, colour, lines, amp, periods, phase_step, width):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for k in range(lines):
        ph = k * phase_step
        a = amp * (1 - k / (lines * 1.6))
        for x in range(W):
            t = x / W * 2 * math.pi
            y = H / 2 + a * math.sin(periods * t + ph) + a * 0.35 * math.sin(2 * periods * t - ph * 1.7)
            for dy in range(-width, width + 1):
                yy = int(y + dy)
                if 0 <= yy < H:
                    al = int(255 * (1 - abs(dy) / (width + 1)) * (0.55 if k else 1.0) * (1 - k / lines * 0.6))
                    r, g, b, a0 = px[x, yy]
                    px[x, yy] = (colour[0], colour[1], colour[2], min(255, a0 + al // 3))
    img = img.filter(ImageFilter.GaussianBlur(2.2))
    img.save(OUT / name, optimize=True)


if __name__ == "__main__":
    ribbon("hh_wave1.png", (232, 194, 90), 9, 120, 2, 0.22, 2)     # gold
    ribbon("hh_wave2.png", (60, 200, 150), 7, 90, 3, 0.30, 2)      # emerald
    print("written to", OUT)
