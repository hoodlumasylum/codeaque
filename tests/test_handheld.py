"""0.7.0 CodeAque Handheld Edition: categories, no PC launchers, pad-only navigation, Settings toggle and --handheld."""
from pathlib import Path

from PySide6.QtCore import Qt

from atarilauncher import handheld, config, __main__ as entry
from atarilauncher.library import demo_library
from tests.test_ui_keys import app, Ui, pump  # noqa: F401

QML = Path(handheld.__file__).parent / "qml" / "HandheldScreen.qml"


def _cats():
    return {c["key"]: c for c in handheld.categories(demo_library().games)}


def test_categories_match_the_layout_and_games_starts_with_cloud_gaming():
    cats = handheld.categories(demo_library().games)
    assert [c["title"] for c in cats] == ["Games", "Cloud", "Emulators", "Streams", "Media", "Music", "Downloads", "Settings"]
    games = cats[0]["tiles"]
    assert games[0]["title"] == "Cloud Gaming" and games[0]["kind"] == "list" and games[0]["items"]
    assert all(t["icon"].startswith("g_") for t in games[1:])          # original generic pictures, never logos
    hub = cats[2]["tiles"]
    from atarilauncher import emulators
    assert len(hub) == len(emulators.SYSTEMS) and all(t["icon"].startswith("g_") for t in hub)


def test_no_pc_game_launchers_anywhere():
    for c in handheld.categories(demo_library().games):
        for t in c["tiles"]:
            gids = [t.get("gid", "")] + [i["gid"] for i in t["items"]]
            for g in gids:
                assert not g.startswith(("steam:", "win:", "app:eaapp", "app:rockstar", "app:steam", "app:lutris", "app:heroic")), g


def test_cloud_has_only_cloud_services():
    tiles = _cats()["cloud"]["tiles"]
    assert tiles and all(t["gid"].startswith("web:") for t in tiles)
    assert {"web:xcloud", "web:geforcenow", "web:luna"} <= {t["gid"] for t in tiles}


def test_no_third_party_names_for_logos_or_sony_words_in_the_screen():
    q = QML.read_text()
    assert "PSP" not in q and "PlayStation" not in q and "XMB" not in q.upper().replace("XMB-", "")
    assert "Handheld Edition" in q


def test_flag_and_setting(tmp_path):
    assert entry.parse_args(["--handheld"]).handheld
    p = tmp_path / "config.toml"
    c = config.load_config(p); assert c.handheld is False
    c.handheld = True; config.save_config(c, p)
    assert config.load_config(p).handheld is True


def test_battery_and_online_read_sys(tmp_path):
    b = tmp_path / "BAT0"; b.mkdir()
    (b / "capacity").write_text("77\n"); (b / "status").write_text("Discharging\n")
    assert handheld.battery(str(tmp_path)) == {"present": True, "percent": 77, "charging": False}
    assert handheld.battery(str(tmp_path / "none"))["present"] is False
    n = tmp_path / "net"; (n / "lo").mkdir(parents=True); (n / "lo" / "operstate").write_text("unknown")
    assert handheld.online(str(n)) is False
    (n / "wlan0").mkdir(); (n / "wlan0" / "operstate").write_text("up")
    assert handheld.online(str(n)) is True


def test_pad_navigation_opens_a_list_and_launches(app, tmp_path, monkeypatch):
    ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
    try:
        from PySide6.QtQuick import QQuickItem
        launched = []
        monkeypatch.setattr(ui.backend, "launchGid", lambda gid: launched.append(gid))
        ui.backend.setHandheld(True)
        pump(app, 200)
        hs = ui.win.findChild(QQuickItem, "handheldScreen")
        assert hs.property("visible") and ui.mode == "library"
        assert hs.property("zone") == "tiles" and hs.property("cat") == 0
        ui.key(Qt.Key_Return, 100)                       # Cloud Gaming -> its list
        assert hs.property("zone") == "list"
        ui.key(Qt.Key_Return, 100)
        assert launched and launched[0].startswith("web:")
        ui.key(Qt.Key_Escape, 100)
        assert hs.property("zone") == "tiles"
        ui.key(Qt.Key_E, 100)                            # RB: next category (Cloud)
        assert hs.property("cat") == 1
        ui.key(Qt.Key_Up, 100); assert hs.property("zone") == "cats"
        ui.key(Qt.Key_Right, 100); assert hs.property("cat") == 2
        ui.key(Qt.Key_F10, 100); assert hs.property("cat") == 7  # Y: Settings
        ui.key(Qt.Key_Left, 100); ui.key(Qt.Key_Left, 100); ui.key(Qt.Key_Left, 100)   # tiles: back to Wi-Fi
        for _ in range(5):
            ui.key(Qt.Key_Right, 60)                     # ... Leave Handheld
        ui.key(Qt.Key_Return, 150)
        assert ui.backend.handheld is False and not hs.property("visible")
        assert not ui.warnings, ui.warnings
    finally:
        ui.close()


def test_settings_row(app, tmp_path, monkeypatch):
    ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
    try:
        row = next(r for r in ui.backend.setupRows() if r["kind"] == "handheld")
        assert row["value"] == "OFF"
        ui.backend.setHandheld(True)
        assert next(r for r in ui.backend.setupRows() if r["kind"] == "handheld")["value"] == "ON"
    finally:
        ui.close()
