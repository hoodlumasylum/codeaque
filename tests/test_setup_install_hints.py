"""Setup / Settings list: rows that still need a program installing are grouped at the top."""
import os

import pytest

pytest.importorskip("PySide6")
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("QT_QUICK_BACKEND", "software")
os.environ["ATARI_NO_GAMEPAD"] = "1"

from PySide6.QtCore import QCoreApplication  # noqa: E402

from atarilauncher import backend as be, config, recorder  # noqa: E402
from atarilauncher.store import Store  # noqa: E402


def _backend(tmp_path, monkeypatch, *, ffmpeg=True, steam=True, essentials=True):
    monkeypatch.setenv("ATARI_DATA_DIR", str(tmp_path / "data"))
    monkeypatch.setenv("ATARI_CONFIG_DIR", str(tmp_path / "conf"))
    QCoreApplication.instance() or QCoreApplication([])
    cfg = config.load_config()
    cfg.steam_enabled = False
    b = be.Backend(cfg=cfg, store=Store(tmp_path / "s.json"), enable_gamepad=False)
    monkeypatch.setattr(
        recorder, "why_not",
        lambda *a, **k: "" if ffmpeg else "ffmpeg is not installed yet. Press Install to add it.",
    )
    monkeypatch.setattr(be.Backend, "steam_installed", lambda self: steam)
    monkeypatch.setattr(be.Backend, "_essentials_missing", lambda self: not essentials)
    return b


def _kinds(rows):
    return [r["kind"] for r in rows]


def test_missing_programs_are_grouped_at_the_top(tmp_path, monkeypatch):
    b = _backend(tmp_path, monkeypatch, ffmpeg=False, steam=False, essentials=False)
    rows = b.setupRows()
    kinds = _kinds(rows)
    assert kinds[0] == "done" and kinds[-1] == "done"
    assert kinds[1] == "heading" and rows[1]["title"] == "Needs installing"
    # Original encounter order inside the group: recording, emulators, Steam.
    group = kinds[2:kinds.index("themepage")]
    assert group == ["recnow", "installmenu", "steampage"]
    assert all(rows[2 + i].get("needsInstall") for i in range(3))
    # Those rows are not repeated in the normal list; everything else keeps its usual order.
    rest = kinds[kinds.index("themepage"):-1]
    assert rest[0] == "themepage" and rest[1] == "steam"
    assert "recnow" not in rest and "installmenu" not in rest and "steampage" not in rest
    assert "recaudio" in rest and "recquality" in rest and "recfps" in rest and "recdir" in rest
    rec_i = rest.index("recaudio")
    assert rest[rec_i:rec_i + 4] == ["recaudio", "recquality", "recfps", "recdir"]
    # Recording sound / quality stay below the group, not highlighted.
    for k in ("recaudio", "recquality", "recfps", "recdir", "themepage", "handheld", "padreminder"):
        row = next(r for r in rows if r["kind"] == k)
        assert not row.get("needsInstall")


def test_all_installed_keeps_the_usual_order_and_no_heading(tmp_path, monkeypatch):
    b = _backend(tmp_path, monkeypatch, ffmpeg=True, steam=True, essentials=True)
    rows = b.setupRows()
    kinds = _kinds(rows)
    assert "heading" not in kinds
    assert kinds[0] == "done" and kinds[1] == "themepage" and kinds[2] == "steam" and kinds[-1] == "done"
    assert not any(r.get("needsInstall") for r in rows)
    # recnow / installmenu / steampage sit in their usual place, after pad reminder and before Wi-Fi.
    i = kinds.index("padreminder")
    assert kinds[i:i + 8][0] == "padreminder"
    assert kinds[i + 1] == "recnow"
    assert "installmenu" in kinds[i:] and "steampage" in kinds[i:]
    assert kinds.index("installmenu") < kinds.index("steampage") < kinds.index("wifipage")
    assert kinds.index("recnow") < kinds.index("installmenu")


def test_only_steam_missing_moves_just_that_row(tmp_path, monkeypatch):
    b = _backend(tmp_path, monkeypatch, ffmpeg=True, steam=False, essentials=True)
    rows = b.setupRows()
    kinds = _kinds(rows)
    assert kinds[1] == "heading" and kinds[2] == "steampage"
    assert rows[2].get("needsInstall") and rows[2]["value"] == "Not installed"
    rest = kinds[3:]
    assert rest[0] == "themepage" and "steampage" not in rest
    assert "recnow" in rest and "installmenu" in rest
    assert not next(r for r in rows if r["kind"] == "recnow").get("needsInstall")
    assert not next(r for r in rows if r["kind"] == "installmenu").get("needsInstall")


def test_row_returns_to_its_normal_place_after_install(tmp_path, monkeypatch):
    b = _backend(tmp_path, monkeypatch, ffmpeg=False, steam=False, essentials=False)
    before = _kinds(b.setupRows())
    assert before[1] == "heading" and "recnow" in before[:6]
    # ffmpeg is now installed: the recording row leaves the group and sits back with the other recording rows.
    monkeypatch.setattr(recorder, "why_not", lambda *a, **k: "")
    after = b.setupRows()
    kinds = _kinds(after)
    assert kinds[1] == "heading"  # steam + emulators still need installing
    assert kinds[2:4] == ["installmenu", "steampage"]
    rec_i = kinds.index("recnow")
    assert rec_i > kinds.index("themepage")
    assert kinds[rec_i:rec_i + 5] == ["recnow", "recaudio", "recquality", "recfps", "recdir"]
    assert not next(r for r in after if r["kind"] == "recnow").get("needsInstall")
    # Then everything is installed: the heading disappears and the usual order returns.
    monkeypatch.setattr(be.Backend, "steam_installed", lambda self: True)
    monkeypatch.setattr(be.Backend, "_essentials_missing", lambda self: False)
    done = _kinds(b.setupRows())
    assert "heading" not in done
    assert done[1] == "themepage" and done[2] == "steam"
    assert done.index("recnow") < done.index("installmenu") < done.index("steampage")


def test_wayland_wf_recorder_counts_as_needs_install(tmp_path, monkeypatch):
    b = _backend(tmp_path, monkeypatch, ffmpeg=True, steam=True, essentials=True)
    monkeypatch.setattr(
        recorder, "why_not",
        lambda *a, **k: "This is a Wayland session. Recording there needs wf-recorder (sudo apt install wf-recorder).",
    )
    rows = b.setupRows()
    rec = next(r for r in rows if r["kind"] == "recnow")
    assert rec.get("needsInstall") and rows[1]["kind"] == "heading" and rows[2]["kind"] == "recnow"


def test_no_display_does_not_count_as_needs_install(tmp_path, monkeypatch):
    b = _backend(tmp_path, monkeypatch, ffmpeg=True, steam=True, essentials=True)
    monkeypatch.setattr(recorder, "why_not", lambda *a, **k: "There is no screen to record (no display found).")
    rows = b.setupRows()
    rec = next(r for r in rows if r["kind"] == "recnow")
    assert not rec.get("needsInstall")
    assert "heading" not in _kinds(rows)
    assert rec["value"] == "Needs setup"


def test_heading_is_skipped_and_focus_follows_the_row(_qt_app, tmp_path, monkeypatch):
    """Controller / keyboard: the heading is not a cursor stop, and focus stays on the same row after it moves."""
    from PySide6.QtCore import Qt
    from PySide6.QtQuick import QQuickItem
    from tests.test_ui_keys import Ui, pump

    ui = Ui(_qt_app, tmp_path, monkeypatch)
    try:
        monkeypatch.setattr(
            recorder, "why_not",
            lambda *a, **k: "ffmpeg is not installed yet. Press Install to add it.",
        )
        monkeypatch.setattr(be.Backend, "steam_installed", lambda self: False)
        monkeypatch.setattr(be.Backend, "_essentials_missing", lambda self: True)
        ui.win.refreshSetup(False)
        pump(ui.app, 80)
        assert ui.mode == "setup"
        rows = ui.backend.setupRows()
        assert rows[1]["kind"] == "heading"
        # Cursor starts on DONE. One Down skips the heading and lands on Start recording.
        ui.key(Qt.Key_Down, 40)
        sl = ui.win.findChild(QQuickItem, "setupList")
        assert sl is not None
        idx = sl.property("currentIndex")
        assert rows[idx]["kind"] == "recnow"
        # Move to Install emulators and apps, then mark essentials as installed and refresh keeping focus.
        ui.key(Qt.Key_Down, 40)
        idx = sl.property("currentIndex")
        assert ui.backend.setupRows()[idx]["kind"] == "installmenu"
        monkeypatch.setattr(be.Backend, "_essentials_missing", lambda self: False)
        ui.win.refreshSetup(True)
        pump(ui.app, 80)
        rows = ui.backend.setupRows()
        idx = sl.property("currentIndex")
        assert rows[idx]["kind"] == "installmenu"
        assert not rows[idx].get("needsInstall")
        # The row has left the top group and sits in its usual place (after Theme).
        assert rows[1]["kind"] == "heading"
        theme_i = next(i for i, r in enumerate(rows) if r["kind"] == "themepage")
        assert idx > theme_i
    finally:
        ui.close()
