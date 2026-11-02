"""Original 'Christmas' theme jingle: a quick bell-like five-note figure with a light sleigh-bell shimmer (pure synthesis).
Usage: make_jingle_sfx.py [out.wav]"""
import math, random, struct, wave, sys

def make(path, rate=22050):
    random.seed(25)
    notes = [(0.00, 1318.5), (0.09, 1568.0), (0.18, 1760.0), (0.27, 2093.0), (0.40, 1568.0)]   # E6 G6 A6 C7 G6
    n = int(0.95 * rate); buf = [0.0] * n
    for st, f in notes:
        s0 = int(st * rate)
        for i in range(s0, n):
            t = (i - s0) / rate
            env = min(1, t / 0.004) * math.exp(-t * 5.5)
            buf[i] += env * 0.16 * (math.sin(2 * math.pi * f * t) + 0.45 * math.sin(2 * math.pi * f * 2.4 * t) + 0.2 * math.sin(2 * math.pi * f * 3.9 * t))
    for k in range(10):                                        # little sleigh-bell ticks
        s0 = int((0.02 + k * 0.075) * rate)
        for i in range(s0, min(n, s0 + int(0.05 * rate))):
            t = (i - s0) / rate
            buf[i] += (random.random() * 2 - 1) * 0.05 * math.exp(-t * 90) + 0.04 * math.sin(2 * math.pi * 5200 * t) * math.exp(-t * 60)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in buf))

if __name__ == "__main__":
    make(sys.argv[1] if len(sys.argv) > 1 else "android/app/src/main/assets/jingle.wav")
