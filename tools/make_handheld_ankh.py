"""Draws CodeAque's own winged ankh (an ancient Egyptian symbol, drawn here from scratch) as crisp SVG files:
atarilauncher/assets/hh_ankh.svg (green and gold, the big one) and hh_ankh_gold.svg (all gold, for the Emulators hub)."""
import math
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "atarilauncher" / "assets"


def blade(x0, y0, ang_deg, length, width, fill, op):
    a = math.radians(ang_deg)
    ux, uy = math.cos(a), -math.sin(a)          # along the feather
    nx, ny = -uy, ux                             # across it
    tx, ty = x0 + ux * length, y0 + uy * length
    m1x, m1y = x0 + ux * length * 0.55 + nx * width, y0 + uy * length * 0.55 + ny * width
    m2x, m2y = x0 + ux * length * 0.55 - nx * width * 0.6, y0 + uy * length * 0.55 - ny * width * 0.6
    d = f"M{x0:.1f},{y0:.1f} Q{m1x:.1f},{m1y:.1f} {tx:.1f},{ty:.1f} Q{m2x:.1f},{m2y:.1f} {x0:.1f},{y0:.1f} Z"
    return f"<path d='{d}' fill='url(#{fill})' stroke='#06200c' stroke-width='0.7' opacity='{op:.2f}'/>"


def wing(side, feathers, fill_a, fill_b):
    """One spread wing: a long row of flight feathers fanning out from the ankh's arm, a shorter gold row on top."""
    parts = []
    root = (226, 84)
    for i in range(feathers):                    # flight feathers: from swept up at the top to drooping at the end
        t = i / (feathers - 1)
        ang = 17 - t * 15
        length = 170 - t * 70
        parts.append(blade(root[0] - 4 + t * 4, root[1] - 30 + t * 44, ang, length, 11 - t * 3, fill_a, 0.97))
    for i in range(feathers - 3):                # coverts: shorter, brighter, on top
        t = i / (feathers - 4)
        ang = 15 - t * 12
        parts.append(blade(root[0] - 2, root[1] - 26 + t * 30, ang, 100 - t * 34, 8 - t * 2, fill_b, 0.95))
    g = "".join(parts)
    return g if side > 0 else f"<g transform='translate(400,0) scale(-1,1)'>{g}</g>"


def ankh(fill):
    return (f"<g fill='url(#{fill})' stroke='#3a2a08' stroke-width='1.2'>"
            "<path d='M200,6 C178,6 168,24 168,40 C168,58 182,72 192,80 L170,80 L170,94 L192,94 L192,150 L208,150 L208,94 "
            "L230,94 L230,80 L208,80 C218,72 232,58 232,40 C232,24 222,6 200,6 Z M200,20 C211,20 218,29 218,41 "
            "C218,55 208,66 200,73 C192,66 182,55 182,41 C182,29 189,20 200,20 Z' fill-rule='evenodd'/></g>")


DEFS = """<defs>
<linearGradient id='gold' x1='0' y1='0' x2='0' y2='1'><stop offset='0' stop-color='#fff4c2'/><stop offset='.45' stop-color='#e8c25a'/>
<stop offset='.7' stop-color='#a8771f'/><stop offset='1' stop-color='#f3d67a'/></linearGradient>
<linearGradient id='green' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='#d9ff9a'/><stop offset='.5' stop-color='#46e05a'/>
<stop offset='1' stop-color='#127a2c'/></linearGradient>
<linearGradient id='lime' x1='0' y1='0' x2='1' y2='0'><stop offset='0' stop-color='#9cff6a'/><stop offset='1' stop-color='#f0e070'/></linearGradient>
</defs>"""


def svg(name, a, b, ankh_fill):
    body = wing(1, 11, a, b) + wing(-1, 11, a, b) + ankh(ankh_fill)
    # mirror trick: the wing is drawn for the right side; flip the coordinates around the centre for the left
    body = body.replace("<path d='M", "<path d='M", 1)
    (OUT / name).write_text(f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 160'>{DEFS}"
                            f"<g transform='translate(200,0)'><g transform='translate(-200,0)'>{body}</g></g></svg>")


if __name__ == "__main__":
    svg("hh_ankh.svg", "green", "lime", "gold")
    svg("hh_ankh_gold.svg", "gold", "gold", "gold")
    print("written to", OUT)
