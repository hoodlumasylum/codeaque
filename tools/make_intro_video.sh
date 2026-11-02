#!/usr/bin/env bash
# Rebuilds atarilauncher/assets/intro/code-intro.mp4: the white CodeAque logo fading in with a cyan glow,
# over charcoal, with a soft original four-note chime (synthesised here). Needs ffmpeg and python3.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'P'
import math, struct, wave
r = 44100; n = int(4.5 * r); b = []
for i in range(n):
    t = i / r; v = 0
    for k, f in enumerate((220, 330, 440, 660)):
        st = 0.3 + k * 0.35
        if t > st:
            tt = t - st; v += 0.18 * math.exp(-tt * 1.4) * math.sin(2 * math.pi * f * tt) * min(1, tt / 0.05)
    b.append(int(max(-1, min(1, v)) * 32767))
w = wave.open('/tmp/codeaque-intro.wav', 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(r)
w.writeframes(b''.join(struct.pack('<h', s) for s in b)); w.close()
P
ffmpeg -loglevel error -y -f lavfi -i "color=c=0x0d0f13:s=1280x720:d=4.5:r=30" -loop 1 -t 4.5 -i atarilauncher/assets/codeaque-logo.png -i /tmp/codeaque-intro.wav \
  -filter_complex "[1:v]scale=900:-1,format=rgba,split[l][g];[g]colorchannelmixer=rr=0.25:gg=0.83:bb=1,gblur=sigma=28[gl];[0:v][gl]overlay=(W-w)/2:(H-h)/2[a];[a][l]overlay=(W-w)/2:(H-h)/2,fade=t=in:st=0:d=1.2,fade=t=out:st=3.8:d=0.7,format=yuv420p[v]" \
  -map "[v]" -map 2:a -c:v libx264 -crf 22 -preset veryfast -c:a aac -ac 2 -b:a 96k -shortest atarilauncher/assets/intro/code-intro.mp4
