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
    assert [c["title"] for c in cats] == ["Steam", "PC Games", "Games Store", "My Games", "Retro", "Cloud", "Emulators", "Streams", "Media", "Music", "Downloads",
                                          "All Apps", "Accounts", "Settings"]
    games = cats[4]["tiles"]
    assert games[0]["title"] == "Cloud Gaming" and games[0]["kind"] == "list" and games[0]["items"]
    assert all(t["icon"].startswith("g_") for t in games[1:])          # original generic pictures, never logos
    hub = cats[6]["tiles"]
    from atarilauncher import emulators
    assert len(hub) == len(emulators.SYSTEMS) and all(t["icon"].startswith("g_") for t in hub)


def test_my_games_has_installed_pc_games_and_all_apps_has_apps():
    cats = {c["key"]: c for c in handheld.categories(demo_library().games)}
    mine = [t.get("gid", "") for t in cats["mygames"]["tiles"]]
    assert mine[0].startswith("steam:")                                  # Steam games already installed, first
    assert all(not t.get("gid", "").startswith("steam:") for t in cats["pcgames"]["tiles"])
    st = cats["steam"]["tiles"]
    assert st[0]["gid"] == "app:steam" and all(t["gid"].startswith("steam:") for t in st[1:-1]) and len(st) > 1
    assert all(t.get("gid", "").startswith(("app:", "tool:", "studio:")) for t in cats["apps"]["tiles"])


def test_installed_emulators_become_tiles(monkeypatch):
    from atarilauncher import emulators
    monkeypatch.setattr(emulators, "find_emu", lambda cfg, eid, home=None: (["retroarch"], "native") if eid == "retroarch" else (["x"], ""))
    tiles = handheld.installed_emulators(object())
    assert [t["action"] for t in tiles] == ["emu:retroarch"]


def test_no_pc_game_launchers_outside_my_games_and_all_apps():
    for c in handheld.categories(demo_library().games):
        if c["key"] in ("steam", "pcgames", "store", "mygames", "apps", "accounts"):
            continue
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
        assert hs.property("zone") == "tiles" and hs.property("cat") == 0     # Steam
        for _ in range(4): ui.key(Qt.Key_E, 100)
        assert hs.property("cat") == 4   # Retro: Cloud Gaming first
        ui.key(Qt.Key_Return, 100)                       # Cloud Gaming -> its list
        assert hs.property("zone") == "list"
        ui.key(Qt.Key_Return, 100)
        assert launched and launched[0].startswith("web:")
        ui.key(Qt.Key_Escape, 100)
        assert hs.property("zone") == "tiles"
        ui.key(Qt.Key_E, 100)                            # RB: next category (Cloud)
        assert hs.property("cat") == 5
        ui.key(Qt.Key_Up, 100); assert hs.property("zone") == "cats"
        ui.key(Qt.Key_Right, 100); assert hs.property("cat") == 6
        ui.key(Qt.Key_F10, 100); assert hs.property("cat") == 13  # Y: Settings
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


def test_handheld_settings_offer_the_in_app_updater():
    from atarilauncher import handheld
    keys = [k for k, _, _ in handheld.SETTINGS_TILES]
    assert keys[-1] == "update" and keys.index("leave") == 5            # added last, the other tiles keep their places
    main = (Path(handheld.__file__).with_name("qml") / "Main.qml").read_text()
    assert 'id === "update") win.openUpdate("library")' in main
    hh = (Path(handheld.__file__).with_name("qml") / "HandheldScreen.qml").read_text()
    assert 'objectName: "hhUpdate"' in hh and "backend.updateAvailable" in hh
    # top Update-ready notice must open the same Update screen as Settings (not a label-only pill)
    assert 'objectName: "hhUpdateClick"' in hh
    assert 'hh.menuAction("update")' in hh
    assert "Qt.PointingHandCursor" in hh


def test_classic_topbar_update_pill_opens_the_in_app_updater():
    from atarilauncher import handheld
    qml = Path(handheld.__file__).with_name("qml")
    tb = (qml / "TopBar.qml").read_text()
    main = (qml / "Main.qml").read_text()
    assert "signal updateRequested()" in tb
    assert 'objectName: "tbUpdateClick"' in tb and "tb.updateRequested()" in tb
    assert "onUpdateRequested: win.openUpdate(\"library\")" in main


def test_console_home_is_the_default_without_the_test_override(tmp_path, monkeypatch):
    monkeypatch.delenv("ATARI_HOME_STYLE", raising=False)
    c = config.load_config(tmp_path / "config.toml")
    assert c.home_style == "console"
    c.home_style = "classic"; config.save_config(c, tmp_path / "config.toml")
    assert config.load_config(tmp_path / "config.toml").home_style == "classic"


def test_store_has_top_free_and_paid_and_accounts_open_official_apps():
    cats = {c["key"]: c for c in handheld.categories(demo_library().games)}
    st = cats["store"]["tiles"]
    assert st[0]["action"] == "store:steamtop"
    assert any(t.get("action") == "store:steamfree" for t in st) and any(t["title"].startswith("Paid") for t in st)
    from atarilauncher import webapps
    for p in webapps.STORE_PAGES:
        assert p.url.startswith("https://") and webapps.BY_ID[p.id] is p
    assert all("$" not in t["title"] and "£" not in t["title"] for t in st)
    acc = [t["gid"] for t in cats["accounts"]["tiles"]]
    order = [a[0] for a in handheld.ACCOUNTS]
    assert acc and acc == [g for g in order if g in acc]


def test_get_games_tile_in_steam_and_pc_games():
    cats = {c["key"]: c for c in handheld.categories(demo_library().games)}
    assert cats["steam"]["tiles"][-1]["action"] == "getgames" and cats["pcgames"]["tiles"][-1]["action"] == "getgames"
