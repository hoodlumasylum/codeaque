"""Makes CodeAque's own soft Handheld-mode sounds (pure sine tones with soft edges) into atarilauncher/assets/sfx/.
Run: python3 tools/make_handheld_sfx.py   (no samples from anywhere else are used)."""
import math
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "atarilauncher" / "assets" / "sfx"


def tone(notes, length, vol=0.22, decay=18.0, shimmer=0.0):
    n = int(RATE * length)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / 0.004) * math.exp(-decay * t)
        v = 0.0
        for start, f in notes:
            if t >= start:
                tt = t - start
                e2 = min(1.0, tt / 0.004) * math.exp(-decay * tt)
                v += math.sin(2 * math.pi * f * tt) * e2 + shimmer * math.sin(2 * math.pi * f * 2.0 * tt) * e2 * 0.5
        out.append(max(-1.0, min(1.0, v * vol * (1.0 if notes else env))))
    return out


def save(name, samples):
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * 32000)) for s in samples))


if __name__ == "__main__":
    save("hhmove", tone([(0, 1318.5)], 0.09, vol=0.16, decay=45))                       # soft tick: E6
    save("hhcat", tone([(0, 880.0), (0.045, 1174.7)], 0.22, vol=0.16, decay=22, shimmer=0.3))   # A5 -> D6
    save("hhselect", tone([(0, 987.8), (0.05, 1318.5), (0.1, 1975.5)], 0.42, vol=0.15, decay=10, shimmer=0.4))  # B5 E6 B6
    save("hhback", tone([(0, 1174.7), (0.05, 880.0)], 0.22, vol=0.14, decay=22))         # D6 -> A5
    print("written to", OUT)
