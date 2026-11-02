#!/usr/bin/env bash
# Rebuilds atarilauncher/assets/intro/code-intro.mp4 from Code's splash (atarilauncher/assets/splash.png, with its
# bar and dots emptied): the loading bar fills and the dots light one after another over ~4.5 s at 1280x720,
# with a soft original chime (synthesised here). Needs ffmpeg and python3 (PySide6 from the app's venv for the bar).
set -euo pipefail
cd "$(dirname "$0")/.."
PY=.venv/bin/python; [ -x "$PY" ] || PY=python3
T=$(mktemp -d)
"$PY" - "$T" <<'P'
import math, struct, wave, sys
from PySide6.QtGui import QImage, QGuiApplication, QPainter, QColor, QLinearGradient, QPainterPath
from PySide6.QtCore import Qt, QRectF
app = QGuiApplication(['x', '-platform', 'offscreen'])
out = sys.argv[1]
def rounded(w, h, r, top, bottom, path):
    im = QImage(w, h, QImage.Format_ARGB32); im.fill(Qt.transparent); p = QPainter(im); p.setRenderHint(QPainter.Antialiasing)
    g = QLinearGradient(0, 0, 0, h); g.setColorAt(0, QColor(top)); g.setColorAt(0.35, QColor('#ff1a1a')); g.setColorAt(1, QColor(bottom))
    pp = QPainterPath(); pp.addRoundedRect(QRectF(0, 0, w, h), r, r); p.fillPath(pp, g); p.end(); im.save(path)
rounded(480, 15, 7.5, '#ff6a6a', '#a80000', out + '/bar.png')
rounded(15, 15, 2.2, '#ff7070', '#d00000', out + '/dot.png')
r = 44100; n = int(4.5 * r); b = []
for i in range(n):
    t = i / r; v = 0
    for k, f in enumerate((220, 330, 440, 660)):
        st = 0.3 + k * 0.35
        if t > st:
            tt = t - st; v += 0.18 * math.exp(-tt * 1.4) * math.sin(2 * math.pi * f * tt) * min(1, tt / 0.05)
    b.append(int(max(-1, min(1, v)) * 32767))
w = wave.open(out + '/s.wav', 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(r)
w.writeframes(b''.join(struct.pack('<h', s) for s in b)); w.close()
P
# splash geometry at 1280x720 (from the 1672x941 original): bar inner 400,522 480x15; dots at x 580/615/650/685, y 567, 15 px
F="[0:v]scale=1280:720,format=rgba[bg];[bg]split[bg1][bg2];[bg2]crop=402:30:0:515[cap];"
F+="[bg1][1:v]overlay=x='400-480*(1-min(1\,t/3.6))':y=522[a];[a][cap]overlay=0:515[b];"
F+="[2:v]split=4[d0][d1][d2][d3];"
F+="[b][d0]overlay=580:567:enable='eq(mod(floor(t*2.5)\,4)\,0)'[c0];"
F+="[c0][d1]overlay=615:567:enable='eq(mod(floor(t*2.5)\,4)\,1)'[c1];"
F+="[c1][d2]overlay=650:567:enable='eq(mod(floor(t*2.5)\,4)\,2)'[c2];"
F+="[c2][d3]overlay=685:567:enable='eq(mod(floor(t*2.5)\,4)\,3)',fade=t=in:st=0:d=0.6,fade=t=out:st=4.0:d=0.5,format=yuv420p[v]"
ffmpeg -loglevel error -y -loop 1 -framerate 30 -t 4.5 -i atarilauncher/assets/splash.png -loop 1 -framerate 30 -t 4.5 -i "$T/bar.png" \
  -loop 1 -framerate 30 -t 4.5 -i "$T/dot.png" -i "$T/s.wav" -filter_complex "$F" \
  -map "[v]" -map 3:a -c:v libx264 -crf 20 -preset veryfast -c:a aac -ac 2 -b:a 96k -shortest atarilauncher/assets/intro/code-intro.mp4
rm -rf "$T"
