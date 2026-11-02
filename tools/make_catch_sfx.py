"""Original 'Catch' theme chime: a quick bright four-note arpeggio with a soft bell tail (pure synthesis)."""
import math, struct, wave, sys

def make(path, rate=22050):
    notes = [784.0, 987.8, 1174.7, 1568.0]          # G5 B5 D6 G6
    n = int(0.75 * rate); buf = [0.0] * n
    for k, f in enumerate(notes):
        start = int(k * 0.075 * rate)
        for i in range(start, n):
            t = (i - start) / rate
            env = min(1, t / 0.005) * math.exp(-t * (7 if k < 3 else 4))
            buf[i] += env * (math.sin(2 * math.pi * f * t) + 0.3 * math.sin(2 * math.pi * f * 2.76 * t)) * 0.22
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in buf))

if __name__ == "__main__":
    make(sys.argv[1] if len(sys.argv) > 1 else "android/app/src/main/assets/chime.wav")
