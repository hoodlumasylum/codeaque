"""Ten taps on the CodeAque home logo (within ~5s) toggle CodeAque-only vs Linux desktop.

The switch reuses the existing start-mode path. After five taps a countdown toast is
shown; the tenth opens a confirm page. Kids mode requires the parent PIN to leave
CodeAque-only. Nothing here talks to the real /etc: the start-mode tests' temp layout
and pkexec stand-in are reused."""
import os
from pathlib import Path

import pytest

from atarilauncher import kiosk, modetap
from tests.test_kiosk import box, checks, fake_pkexec  # noqa: F401
from tests.test_startmode import make_ui, turn_on

pytest.importorskip("PySide6")
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
from tests.test_features_ui import wait_for  # noqa: E402
from tests.test_ui_keys import app  # noqa: E402,F401


# ------------------------------------------------------------------ the counter (no Qt)
def test_first_four_taps_are_silent_then_a_countdown():
    c = modetap.Counter()
    ev = None
    for i in range(1, 5):
        ev = c.tap(i * 0.2, "console")
        assert ev["action"] == "silent" and ev["count"] == i and ev["want"] == "desktop"
    ev = c.tap(1.0, "console")
    assert ev == {
        "action": "toast", "count": 5, "want": "desktop", "left": 5,
        "text": "5 more taps to switch to Linux mode",
    }
    ev = c.tap(1.2, "console")
    assert ev["text"] == "4 more taps to switch to Linux mode"
    ev = c.tap(1.4, "console")
    ev = c.tap(1.6, "console")
    ev = c.tap(4.8, "console")  # ninth, still inside the 5s window from tap 1 at 0.2
    assert ev["text"] == "1 more tap to switch to Linux mode"
    ev = c.tap(5.0, "console")
    assert ev == {"action": "confirm", "count": 10, "want": "desktop"}
    assert c.taps == []  # a fresh streak after the confirm opens


def test_the_other_direction_names_codeaque_only_and_a_gap_resets():
    c = modetap.Counter()
    ev = None
    for i in range(9):
        ev = c.tap(i * 0.3, "desktop")
    assert ev["text"] == "1 more tap to switch to CodeAque-only mode"
    # more than 5s after the first tap of this window: start again
    ev = c.tap(8.0, "desktop")
    assert ev["action"] == "silent" and ev["count"] == 1
    ev = c.tap(8.0, "broken")
    assert ev["action"] == "broken" and c.taps == []


# ------------------------------------------------------------------ backend + confirm + kids PIN
def _tap_n(b, n, now=0.0, step=0.2):
    ev = ""
    for i in range(n):
        # stamp the clock so tests do not depend on wall time
        b._logo_taps_now = now + i * step
        ev = b.logoTap()
    return ev


def test_ten_taps_open_a_confirm_then_switch_both_ways(app, tmp_path, monkeypatch, box):
    (tmp_path / "home").mkdir(exist_ok=True)
    monkeypatch.setattr("atarilauncher.branding.ensure_launchers", lambda *a, **k: ["the Mint menu"])
    ui = make_ui(app, tmp_path, monkeypatch, box)
    try:
        b = ui.backend
        layout = b._kiosk_layout
        assert kiosk.read_mode(layout)["next"] == "desktop"
        assert b.pageTitle("modetap") == "SWITCH START MODE"
        assert _tap_n(b, 4) == ""
        assert "Keep tapping the logo" in b.toast and "4 of 10" in b.toast
        assert _tap_n(b, 1, now=0.8) == ""
        assert b.toast == "5 more taps to switch to CodeAque-only mode"
        assert b.toastBig
        b._logo_taps.reset()
        assert _tap_n(b, 10) == "page:modetap"
        rows = b.pageRows("modetap")
        titles = " ".join(r["title"] for r in rows)
        assert "CodeAque-only" in titles and "Yes" in " ".join(r["title"] for r in rows if r.get("id") == "mtyes")
        assert "mtpin" not in [r.get("id") for r in rows]  # not in Kids mode
        assert b.pageActivate("modetap", "mtyes") in ("refresh", "page:startmode")
        assert wait_for(app, lambda: kiosk.enabled(layout) and not b._kiosk_busy, 10), b.toast
        assert kiosk.read_mode(layout)["next"] == "console"
        # and back to Linux
        b._logo_taps.reset()
        assert _tap_n(b, 10) == "page:modetap"
        assert "Linux" in " ".join(r["title"] for r in b.pageRows("modetap"))
        assert b.pageActivate("modetap", "mtno") == "back"
        assert kiosk.read_mode(layout)["next"] == "console"  # cancelled
        assert b.pageActivate("modetap", "mtyes") in ("refresh", "page:startmode")
        assert wait_for(app, lambda: not kiosk.enabled(layout) and not b._kiosk_busy, 10), b.toast
        assert kiosk.read_mode(layout)["next"] == "desktop"
    finally:
        ui.close()


def test_kids_mode_needs_the_parent_pin_to_leave_codeaque_only(app, tmp_path, monkeypatch, box):
    (tmp_path / "home").mkdir(exist_ok=True)
    turn_on(box)
    ui = make_ui(app, tmp_path, monkeypatch, box)
    try:
        b = ui.backend
        layout = b._kiosk_layout
        assert kiosk.read_mode(layout)["next"] == "console"
        b._kids_mode = True
        b._kids_pin = "2468"
        b._logo_taps.reset()
        assert _tap_n(b, 10) == "page:modetap"
        rows = b.pageRows("modetap")
        assert any(r.get("id") == "mtpin" for r in rows)
        assert "parent pin" in " ".join(r["title"] for r in rows).lower()
        # Yes without a PIN just opens the PIN box, and does not switch
        assert b.pageActivate("modetap", "mtyes") == ""
        assert wait_for(app, lambda: b.inputActive, 2) and "PIN" in b.inputTitle
        b.submitInput("0000")
        assert wait_for(app, lambda: (not b.inputActive) and "not right" in b.toast.lower(), 2)
        assert not b._mt_pin_ok and kiosk.enabled(layout)
        b.pageActivate("modetap", "mtpin")
        assert wait_for(app, lambda: b.inputActive, 2)
        b.submitInput("2468")
        assert wait_for(app, lambda: b._mt_pin_ok and "accepted" in b.toast.lower(), 2)
        assert b.pageActivate("modetap", "mtyes") in ("refresh", "page:startmode")
        assert wait_for(app, lambda: not kiosk.enabled(layout) and not b._kiosk_busy, 10), b.toast
        # going *into* CodeAque-only in Kids mode does not ask for the PIN
        b._logo_taps.reset()
        b._mt_pin_ok = False
        assert _tap_n(b, 10) == "page:modetap"
        assert "mtpin" not in [r.get("id") for r in b.pageRows("modetap")]
        assert b.pageActivate("modetap", "mtyes") in ("refresh", "page:startmode")
        assert wait_for(app, lambda: kiosk.enabled(layout) and not b._kiosk_busy, 10), b.toast
    finally:
        ui.close()


def test_kids_mode_without_a_pin_set_cannot_leave_console(app, tmp_path, monkeypatch, box):
    (tmp_path / "home").mkdir(exist_ok=True)
    turn_on(box)
    ui = make_ui(app, tmp_path, monkeypatch, box)
    try:
        b = ui.backend
        b._kids_mode = True
        b._kids_pin = ""
        b._logo_taps.reset()
        assert _tap_n(b, 10) == "page:modetap"
        assert b.pageActivate("modetap", "mtyes") == ""
        assert "parent PIN" in b.toast and kiosk.enabled(b._kiosk_layout)
        assert not b.inputActive
    finally:
        ui.close()
