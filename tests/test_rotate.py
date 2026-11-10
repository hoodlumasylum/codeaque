"""0.10.31: Win600-like portrait panel rotation (app Settings + installer)."""
from pathlib import Path

from atarilauncher import rotate


XRANDR_PORTRAIT = """Screen 0: minimum 320 x 200, current 720 x 1280
DSI-1 connected primary 720x1280+0+0 (normal left inverted right x axis y axis) 0mm x 0mm
   720x1280     60.00*+
HDMI-1 disconnected (normal left inverted right x axis y axis)
"""

XRANDR_ALREADY = """Screen 0: minimum 320 x 200, current 1280 x 720
DSI-1 connected primary 1280x720+0+0 right (normal left inverted right x axis y axis) 0mm x 0mm
   720x1280     60.00*+
"""

XRANDR_NORMAL = """Screen 0: minimum 320 x 200, current 1920 x 1080
eDP-1 connected primary 1920x1080+0+0 (normal left inverted right x axis y axis) 300mm x 190mm
   1920x1080     60.00*+
HDMI-1 disconnected
"""

XINPUT = """⎡ Virtual core pointer                    	id=2	[master pointer  (3)]\n⎜   ↳ Goodix Capacitive TouchScreen       	id=12	[slave  pointer  (2)]\n⎜   ↳ USB Mouse                           	id=9	[slave  pointer  (2)]\n"""


class FakeRun:
    def __init__(self):
        self.calls = []

    def __call__(self, cmd, capture_output=True, text=True, check=False):
        self.calls.append(list(cmd))
        class R:
            returncode = 0
            stdout = ""
            stderr = ""
        r = R()
        if cmd[:1] == ["xrandr"] and len(cmd) == 1:
            r.stdout = self.xrandr
        elif cmd[:2] == ["xinput", "list"]:
            r.stdout = XINPUT
        return r


def test_parse_only_internal_portrait_panels():
    assert rotate.parse_portrait_outputs(XRANDR_PORTRAIT) == ["DSI-1"]
    assert rotate.parse_portrait_outputs(XRANDR_ALREADY) == ["DSI-1"]
    assert rotate.parse_portrait_outputs(XRANDR_NORMAL) == []


def test_auto_rotates_portrait_and_skips_landscape(tmp_path, monkeypatch):
    fr = FakeRun(); fr.xrandr = XRANDR_PORTRAIT
    msg = rotate.apply("auto", run=fr, which=lambda c: "/bin/" + c, home=tmp_path,
                       env={"DISPLAY": ":0", "HOME": str(tmp_path)})
    assert "DSI-1" in msg and "right" in msg
    assert ["xrandr", "--output", "DSI-1", "--rotate", "right"] in fr.calls
    assert (tmp_path / ".config/autostart/codeaque-win600-rotate.desktop").is_file()
    assert (tmp_path / ".config/atari/win600-rotate.sh").is_file()

    fr2 = FakeRun(); fr2.xrandr = XRANDR_NORMAL
    msg2 = rotate.apply("auto", run=fr2, which=lambda c: "/bin/" + c, home=tmp_path,
                        env={"DISPLAY": ":0"})
    assert "left alone" in msg2
    assert not any(c[:1] == ["xrandr"] and "--rotate" in c for c in fr2.calls)


def test_codeaque_rotate_env_left(tmp_path):
    fr = FakeRun(); fr.xrandr = XRANDR_PORTRAIT
    rotate.apply("auto", run=fr, which=lambda c: "/bin/" + c, home=tmp_path,
                 env={"DISPLAY": ":0", "CODEAQUE_ROTATE": "left"})
    assert ["xrandr", "--output", "DSI-1", "--rotate", "left"] in fr.calls


def test_explicit_modes_and_touch(tmp_path):
    fr = FakeRun(); fr.xrandr = XRANDR_PORTRAIT
    rotate.apply("inverted", run=fr, which=lambda c: "/bin/" + c, home=tmp_path,
                 env={"DISPLAY": ":0"})
    assert ["xrandr", "--output", "DSI-1", "--rotate", "inverted"] in fr.calls
    assert any(c[:2] == ["xinput", "map-to-output"] and c[2] == "12" for c in fr.calls)


def test_resolve_direction():
    assert rotate.resolve_direction("auto", has_portrait=True) == "right"
    assert rotate.resolve_direction("auto", has_portrait=False) is None
    assert rotate.resolve_direction("left", has_portrait=True) == "left"
    assert rotate.resolve_direction("normal", has_portrait=False) is None
