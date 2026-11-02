"""Original 'Power Up' theme sound: a rising charge sweep with a bright shimmer (pure synthesis, no samples)."""
import math, random, struct, wave, sys
from pathlib import Path

def make(path, dur=0.62, rate=22050):
    random.seed(3)
    n = int(dur * rate); out = []; ph = ph2 = 0.0
    for i in range(n):
        t = i / rate; u = t / dur
        f = 180 + 760 * (u ** 1.6)                      # charging sweep
        ph += 2 * math.pi * f / rate; ph2 += 2 * math.pi * f * 1.5 / rate
        env = min(1, t / 0.03) * (1 - u) ** 0.8
        v = 0.55 * math.sin(ph) + 0.25 * math.sin(ph2) + 0.12 * math.sin(3 * ph)
        v += 0.10 * (random.random() * 2 - 1) * u            # crackle that grows
        v *= 0.7 + 0.3 * math.sin(2 * math.pi * 28 * t)      # energy wobble
        out.append(int(max(-1, min(1, v * env * 0.6)) * 32767))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", s) for s in out))

if __name__ == "__main__":
    make(Path(sys.argv[1] if len(sys.argv) > 1 else "android/app/src/main/assets/powerup.wav"))
