"""0.10.6: the "New update ready" pop-up (PC): shows when the check finds a newer release, Later hides it for this
version until next launch (the badge stays), Update now starts the same update flow as the banner."""
import pytest

pytest.importorskip("PySide6")
from PySide6.QtCore import Qt  # noqa: E402
from PySide6.QtQuick import QQuickItem  # noqa: E402

from atarilauncher import updater  # noqa: E402
from tests.fakeserver import Fake  # noqa: E402
from tests.test_features_ui import conf, installed_copy, wait_for  # noqa: E402
from tests.test_ui_keys import Ui, app, pump  # noqa: E402,F401
from tests.test_updater import publish  # noqa: E402


def test_whats_new_line():
    assert updater.whats_new("## CodeAque 1.0\n\n- **Pop-up** for `updates`, see [notes](https://x)\n- more") == "Pop-up for updates, see notes"
    assert updater.whats_new("") == "" and len(updater.whats_new("x" * 500)) == 140


def popup(ui):
    return ui.win.findChild(QQuickItem, "updatePopup")


def found(ui, app):
    ui.backend._start_check(manual=False)
    return wait_for(app, lambda: ui.backend.updateVersion == "9.9.9", 15)


def test_popup_later_then_update_now(app, tmp_path, monkeypatch):
    f = Fake()
    try:
        monkeypatch.setenv("ATARI_UPDATE_API", publish(f, "9.9.9", body="- **Bigger buttons** everywhere\n- more"))
        monkeypatch.setenv("ATARI_UPDATE_ROOT", str(installed_copy(tmp_path)))
        conf(tmp_path)
        ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
        try:
            assert not ui.backend.updatePopup and not popup(ui).property("visible")
            assert found(ui, app)
            pump(app, 200)
            assert ui.backend.updatePopup and popup(ui).property("visible")
            assert ui.backend.updateWhatsNew == "Bigger buttons everywhere"
            assert "New update ready (v9.9.9)" in ui.win.findChild(QQuickItem, "updPopTitle").property("text")
            ui.key(Qt.Key_Escape)                                       # Later
            assert not ui.backend.updatePopup and not popup(ui).property("visible")
            assert ui.backend.updateAvailable                           # the badge stays
            assert found(ui, app); pump(app, 200)
            assert not ui.backend.updatePopup                           # no nagging again this session
            ui.backend._upd_popup_dismissed = ""                         # as after a new launch
            ui.backend.updateChanged.emit(); pump(app, 100)
            assert popup(ui).property("visible")
            ui.key(Qt.Key_Right); ui.key(Qt.Key_Left)                   # move between the two buttons
            assert popup(ui).property("sel") == 0
            ui.key(Qt.Key_Return)                                       # Update now
            assert ui.mode == "update" and not popup(ui).property("visible")
            assert wait_for(app, lambda: ui.backend.updateState in ("downloading", "verifying", "installing", "done", "error"), 15), ui.backend.updateState
            assert not ui.warnings, ui.warnings
        finally:
            wait_for(app, lambda: not ui.backend.updateBusy, 30)
            ui.close()
    finally:
        f.stop()


def test_popup_buttons_are_big_for_touch():
    from pathlib import Path
    q = (Path(__file__).resolve().parents[1] / "atarilauncher/qml/Main.qml").read_text()
    pop = q.split('objectName: "updatePopup"', 1)[1].split("// ---- keyboard focus", 1)[0]
    assert "width: 300 * win.u; height: 96 * win.u" in pop and "MouseArea" in pop and '"Update now", "Later"' in pop
    assert "backend.startUpdate()" in pop and "backend.dismissUpdatePopup()" in pop
