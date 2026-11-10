"""Screen rotation for Win600-like portrait panels (native 720x1280).

Used by the CodeAque app on launch and from Settings > Rotate screen, and by
scripts/install-win600.sh / updates (via atarilauncher.win600). Only touches
internal panels that look like the Win600; never a normal landscape screen.

Directions match xrandr: normal | left | right | inverted.
Setting "auto" = rotate to landscape (right by default, CODEAQUE_ROTATE=left)
only when a portrait panel is detected; leave other screens alone.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
from pathlib import Path

MODES = ("auto", "normal", "left", "right", "inverted")
# Coordinate Transformation Matrix for a touchscreen matching xrandr --rotate
TOUCH_MATRIX = {
    "normal": ("1", "0", "0", "0", "1", "0", "0", "0", "1"),
    "left": ("0", "-1", "1", "1", "0", "0", "0", "0", "1"),
    "right": ("0", "1", "0", "-1", "0", "1", "0", "0", "1"),
    "inverted": ("-1", "0", "1", "0", "-1", "1", "0", "0", "1"),
}
INTERNAL_RE = re.compile(r"^(eDP|DSI|LVDS)", re.I)
PORTRAIT_MODE_RE = re.compile(r"\b720x1280\b")


def default_landscape(env: dict | None = None) -> str:
    """Landscape side for Auto: 'right' (usual Win600) or 'left' via CODEAQUE_ROTATE."""
    env = env or os.environ
    d = (env.get("CODEAQUE_ROTATE") or "right").strip().lower()
    return d if d in ("left", "right") else "right"


def parse_portrait_outputs(xrandr_text: str) -> list[str]:
    """Internal panels (eDP / DSI / LVDS) with a native 720x1280 mode, or already rotated 1280x720."""
    found: list[str] = []
    for block in re.split(r"\n(?=\S)", xrandr_text):
        m = re.match(r"^([\w-]+)\s+connected\b", block)
        if not m or not INTERNAL_RE.match(m.group(1)):
            continue
        name = m.group(1)
        if PORTRAIT_MODE_RE.search(block):
            found.append(name)
            continue
        if re.search(r"connected(?:\s+primary)?\s+1280x720\+\d+\+\d+\s+(left|right|inverted)\b", block):
            found.append(name)
    return found


def list_touch_ids(xinput_text: str) -> list[str]:
    ids: list[str] = []
    for line in xinput_text.splitlines():
        low = line.lower()
        if "master" in low or "keyboard" in low:
            continue
        if not any(k in low for k in ("touch", "goodix", "fts", "elan touch", "finger", "touchscreen")):
            continue
        if "touchpad" in low and "touchscreen" not in low:
            continue
        m = re.search(r"id=(\d+)", line)
        if m:
            ids.append(m.group(1))
    return ids


def _run(cmd: list[str], run=subprocess.run) -> subprocess.CompletedProcess:
    return run(cmd, capture_output=True, text=True, check=False)


def resolve_direction(mode: str, *, has_portrait: bool, env: dict | None = None) -> str | None:
    """Which xrandr direction to apply, or None to leave the display alone."""
    mode = (mode or "auto").lower()
    if mode not in MODES:
        mode = "auto"
    if mode == "auto":
        return default_landscape(env) if has_portrait else None
    if mode == "normal":
        return "normal" if has_portrait else None  # only reset when we know it's our panel
    return mode if has_portrait else None


def apply(mode: str = "auto", *, run=subprocess.run, which=shutil.which,
          home: Path | None = None, env: dict | None = None, persist: bool = True) -> str:
    """Apply screen (+ touch) rotation. Returns a short status string. Safe no-op on normal PCs."""
    env = dict(env or os.environ)
    home = home or Path.home()
    if not which("xrandr"):
        return "Rotate screen: xrandr not found (needs X11)."
    xr = _run(["xrandr"], run=run)
    outputs = parse_portrait_outputs(xr.stdout or "") if xr.returncode == 0 else []
    has = bool(outputs)
    direction = resolve_direction(mode, has_portrait=has, env=env)
    if direction is None:
        return ("Rotate screen: Auto — no Win600-like portrait panel, left alone."
                if (mode or "auto").lower() == "auto"
                else "Rotate screen: no Win600-like portrait panel, left alone.")
    out = env.get("CODEAQUE_OUTPUT") or (outputs[0] if outputs else "")
    if not out:
        return "Rotate screen: no output to rotate."
    bits: list[str] = []
    if env.get("DISPLAY"):
        r = _run(["xrandr", "--output", out, "--rotate", direction], run=run)
        if r.returncode == 0:
            bits.append(f"{out} → {direction}")
        else:
            err = (r.stderr or r.stdout or "").strip() or str(r.returncode)
            bits.append(f"xrandr failed ({err})")
        if which("xinput"):
            xi = _run(["xinput", "list"], run=run)
            for tid in list_touch_ids(xi.stdout or ""):
                mapped = _run(["xinput", "map-to-output", tid, out], run=run)
                if mapped.returncode != 0:
                    _run(["xinput", "set-prop", tid, "Coordinate Transformation Matrix",
                          *TOUCH_MATRIX[direction]], run=run)
                bits.append(f"touch {tid}")
    if persist:
        write_persist(out, direction if direction != "normal" else default_landscape(env),
                      home=home, mode=mode)
        bits.append("saved")
    return "Rotate screen: " + ("; ".join(bits) if bits else "done")


def write_persist(output: str, direction: str, *, home: Path, mode: str = "auto") -> None:
    """Login script + autostart so landscape comes back after reboot (Cinnamon/X11)."""
    if direction not in TOUCH_MATRIX:
        direction = "right"
    script = home / ".config/atari/win600-rotate.sh"
    desktop = home / ".config/autostart/codeaque-win600-rotate.desktop"
    monitors = home / ".config/monitors.xml"
    script.parent.mkdir(parents=True, exist_ok=True)
    desktop.parent.mkdir(parents=True, exist_ok=True)
    matrix = " ".join(TOUCH_MATRIX[direction])
    # Script re-checks for a portrait panel so a later external-only setup is left alone.
    script.write_text(
        "#!/usr/bin/env bash\n"
        "# Written by CodeAque. Rotates a Win600-like portrait panel to landscape.\n"
        f"OUT=\"${{CODEAQUE_OUTPUT:-{output}}}\"\n"
        f"DIR=\"${{CODEAQUE_ROTATE:-{direction}}}\"\n"
        "command -v xrandr >/dev/null || exit 0\n"
        "[ -n \"${DISPLAY:-}\" ] || exit 0\n"
        "xrandr 2>/dev/null | grep -A30 \"^$OUT connected\" | grep -qE '720x1280|1280x720.*(left|right|inverted)' || exit 0\n"
        "xrandr --output \"$OUT\" --rotate \"$DIR\" 2>/dev/null || true\n"
        "if command -v xinput >/dev/null; then\n"
        "  while read -r tid; do\n"
        "    [ -n \"$tid\" ] || continue\n"
        "    xinput map-to-output \"$tid\" \"$OUT\" 2>/dev/null \\\n"
        f"      || xinput set-prop \"$tid\" 'Coordinate Transformation Matrix' {matrix} 2>/dev/null || true\n"
        "  done < <(xinput list 2>/dev/null | grep -iE 'touchscreen|goodix|fts|finger|elan touch' \\\n"
        "            | grep -viE 'touchpad|keyboard|master' | sed -n 's/.*id=\\([0-9]\\+\\).*/\\1/p')\n"
        "fi\n"
    )
    script.chmod(script.stat().st_mode | 0o111)
    # Autostart only when Auto / a fixed rotate is wanted; Normal disables it.
    if mode == "normal":
        if desktop.is_file():
            desktop.unlink()
    else:
        desktop.write_text(
            "[Desktop Entry]\n"
            "Type=Application\n"
            "Name=CodeAque Win600 landscape\n"
            "Comment=Rotate the Win600 portrait panel to landscape (and the touchscreen)\n"
            f"Exec={script}\n"
            "X-GNOME-Autostart-enabled=true\n"
            "NoDisplay=true\n"
        )
    # Cinnamon monitors.xml hint (autostart is the reliable path if EDID details differ).
    rot = direction if mode != "normal" else "normal"
    stub = (
        '<monitors version="2">\n'
        "  <configuration>\n"
        "    <logicalmonitor>\n"
        "      <x>0</x>\n"
        "      <y>0</y>\n"
        "      <primary>yes</primary>\n"
        "      <monitor>\n"
        "        <monitorspec>\n"
        f"          <connector>{output}</connector>\n"
        "          <vendor>unknown</vendor>\n"
        "          <product>unknown</product>\n"
        "          <serial>unknown</serial>\n"
        "        </monitorspec>\n"
        "        <mode>\n"
        "          <width>1280</width>\n"
        "          <height>720</height>\n"
        "          <rate>60</rate>\n"
        "        </mode>\n"
        f"        <rotation>{rot}</rotation>\n"
        "      </monitor>\n"
        "    </logicalmonitor>\n"
        "  </configuration>\n"
        "</monitors>\n"
    )
    if monitors.is_file():
        raw = monitors.read_text(errors="replace")
        if f"<connector>{output}</connector>" in raw:
            if re.search(r"<rotation>\w+</rotation>", raw):
                raw = re.sub(r"<rotation>\w+</rotation>", f"<rotation>{rot}</rotation>", raw, count=1)
            else:
                raw = raw.replace("</mode>", f"</mode>\n        <rotation>{rot}</rotation>", 1)
            monitors.write_text(raw)
    else:
        monitors.parent.mkdir(parents=True, exist_ok=True)
        monitors.write_text(stub)


def label(mode: str) -> str:
    return {"auto": "AUTO", "normal": "NORMAL", "left": "LEFT", "right": "RIGHT",
            "inverted": "INVERTED"}.get((mode or "auto").lower(), "AUTO")
