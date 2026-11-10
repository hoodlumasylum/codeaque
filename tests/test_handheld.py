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
    assert [c["title"] for c in cats] == ["Steam", "PC Games", "Games Store", "World Games", "My Games", "Retro", "Cloud", "Emulators", "Streams", "Media", "Music", "Trilogies", "Co-op", "Roblox", "Downloads",
                                          "All Apps", "Accounts", "Settings"]
    games = cats[5]["tiles"]
    assert games[0]["title"] == "Cloud Gaming" and games[0]["kind"] == "list" and games[0]["items"]
    assert all(t["icon"].startswith("g_") for t in games[1:])          # original generic pictures, never logos
    hub = cats[7]["tiles"]
    from atarilauncher import emulators
    assert len(hub) == len(emulators.SYSTEMS) and all(t["icon"].startswith("g_") for t in hub)


def test_my_games_has_installed_pc_games_and_all_apps_has_apps():
    cats = {c["key"]: c for c in handheld.categories(demo_library().games)}
    mine = [t.get("gid", "") for t in cats["mygames"]["tiles"]]
    assert mine[0].startswith("steam:")                                  # Steam games already installed, first
    assert all(not t.get("gid", "").startswith("steam:") for t in cats["pcgames"]["tiles"])
    st = cats["steam"]["tiles"]
    modes = [t for t in st if str(t.get("key", "")).startswith("mode:")]
    rest = st[len(modes):]
    assert rest[0]["gid"] == "app:steam" and all(t["gid"].startswith("steam:") for t in rest[1:-1]) and len(rest) > 1
    assert all(t.get("gid", "").startswith(("app:", "tool:", "studio:")) for t in cats["apps"]["tiles"])


def test_installed_emulators_become_tiles(monkeypatch):
    from atarilauncher import emulators
    monkeypatch.setattr(emulators, "find_emu", lambda cfg, eid, home=None: (["retroarch"], "native") if eid == "retroarch" else (["x"], ""))
    tiles = handheld.installed_emulators(object())
    assert [t["action"] for t in tiles] == ["emu:retroarch"]


def test_no_pc_game_launchers_outside_my_games_and_all_apps():
    for c in handheld.categories(demo_library().games):
        if c["key"] in ("steam", "pcgames", "store", "world", "mygames", "apps", "accounts"):
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
        for _ in range(5): ui.key(Qt.Key_E, 100)
        assert hs.property("cat") == 5   # Retro: Cloud Gaming first
        ui.key(Qt.Key_Return, 100)                       # Cloud Gaming -> its list
        assert hs.property("zone") == "list"
        ui.key(Qt.Key_Return, 100)
        assert launched and launched[0].startswith("web:")
        ui.key(Qt.Key_Escape, 100)
        assert hs.property("zone") == "tiles"
        ui.key(Qt.Key_E, 100)                            # RB: next category (Cloud)
        assert hs.property("cat") == 6
        ui.key(Qt.Key_Up, 100); assert hs.property("zone") == "cats"
        ui.key(Qt.Key_Right, 100); assert hs.property("cat") == 7
        ui.key(Qt.Key_F10, 100); assert hs.property("cat") == 17  # Y: Settings (0.10.32: Trilogies + Co-op + Roblox)
        ui.key(Qt.Key_Left, 100); ui.key(Qt.Key_Left, 100); ui.key(Qt.Key_Left, 100)   # tiles: back to Wi-Fi
        for _ in range(7):
            ui.key(Qt.Key_Right, 60)                     # ... Leave Handheld (0.10.32: Box Set Mode tile)
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
    assert keys[-1] == "update" and keys.index("leave") == 7 and "rotate" in keys and "boxset" in keys  # 0.10.32: Box Set before All settings
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


def test_world_games_pc_row_uses_only_official_store_pages():
    from urllib.parse import urlsplit
    from atarilauncher import webapps

    tiles = _cats()["world"]["tiles"]
    assert len(tiles) >= 9
    assert any(t["title"] == "Japanese Games" for t in tiles)
    assert any(t["title"] == "Chinese Games" for t in tiles)
    assert any(t["title"] == "Korean Games" for t in tiles)
    for t in tiles:
        pid = t["action"].removeprefix("store:")
        page = webapps.BY_ID[pid]
        assert t["action"].startswith("store:") and urlsplit(page.url).scheme == "https"
        assert urlsplit(page.url).hostname == "store.steampowered.com"
        assert page.browse and not any(symbol in t["title"] for symbol in ("$", "£", "€", "¥"))
    english = [webapps.BY_ID[t["action"].removeprefix("store:")] for t in tiles if "English support" in t["title"]]
    assert len(english) >= 3
    assert all("supported_lang=english" in page.url for page in english)


def test_world_games_selected_steam_app_ids():
    from atarilauncher import webapps
    for page, appid in zip(webapps.SELECTED_WORLD_GAMES, (650700, 2358720, 1876890, 1868140, 1049590)):
        assert page.url.startswith(f"https://store.steampowered.com/app/{appid}/")


def test_trilogies_are_steam_store_shortcuts_only():
    """0.10.31: Trilogies tab is story-series Get-on-Steam links only (co-op lives on its own tab)."""
    from urllib.parse import urlsplit
    from atarilauncher import webapps

    tiles = _cats()["trilogies"]["tiles"]
    assert len(tiles) == len(webapps.TRILOGY_PAGES) == len(webapps.TRILOGY_APPIDS)
    titles = {t["title"] for t in tiles}
    assert "Uncharted: Legacy of Thieves" in titles
    assert any("Tomb Raider" in t for t in titles)
    assert any("Grand Theft Auto" in t or t.startswith("GTA ") for t in titles)
    assert any("Assassin" in t for t in titles)
    assert "The Talos Principle" in titles and "The Talos Principle 2" in titles
    assert any("Life is Strange" in t for t in titles)
    assert "A Way Out" not in titles and "Baldur's Gate 3" not in titles
    for t in tiles:
        assert t["action"].startswith("steamstore:")
        assert t["kind"] == "action" and t["items"] == []
        assert t["icon"] == "c_pc"
        appid = t["action"].removeprefix("steamstore:")
        assert appid.isdigit() and str(t["steam_appid"]) == appid
        page = next(p for p in webapps.TRILOGY_PAGES if webapps.TRILOGY_APPIDS[p.id] == int(appid))
        assert page.url.startswith(f"https://store.steampowered.com/app/{appid}/")
        assert urlsplit(page.url).hostname == "store.steampowered.com"
        assert page.browse
    notes = {t["title"]: t.get("note", "") for t in tiles}
    assert "strong pc" in notes["Uncharted: Legacy of Thieves"].lower()
    assert "strong pc" in notes["Grand Theft Auto V Legacy"].lower()
    assert "strong pc" in notes["Grand Theft Auto V Enhanced"].lower()
    for page in webapps.TRILOGY_PAGES:
        appid = webapps.TRILOGY_APPIDS[page.id]
        assert page.url.startswith(f"https://store.steampowered.com/app/{appid}/")


def test_coop_tab_steam_shortcuts_and_notes():
    """0.10.31: Co-op tab — Hazelight/EA + RPG co-op, verified Steam ids, strong-PC / EA notes."""
    from urllib.parse import urlsplit
    from atarilauncher import webapps

    tiles = _cats()["coop"]["tiles"]
    assert len(tiles) == len(webapps.COOP_PAGES) == len(webapps.COOP_APPIDS)
    titles = {t["title"] for t in tiles}
    for need in ("A Way Out", "It Takes Two", "Split Fiction", "Baldur's Gate 3", "Elden Ring",
                 "Stardew Valley", "Terraria", "Valheim", "Path of Exile 2", "Diablo IV"):
        assert need in titles
    assert "Diablo III" not in titles   # not on Steam
    notes = {t["title"]: t.get("note", "") for t in tiles}
    assert "ea app" in notes["A Way Out"].lower()
    assert "strong pc" in notes["Baldur's Gate 3"].lower()
    assert "strong pc" in notes["Elden Ring"].lower()
    assert notes["Stardew Valley"] == ""
    for t in tiles:
        appid = t["action"].removeprefix("steamstore:")
        page = next(p for p in webapps.COOP_PAGES if webapps.COOP_APPIDS[p.id] == int(appid))
        assert page.url.startswith(f"https://store.steampowered.com/app/{appid}/")
        assert urlsplit(page.url).hostname == "store.steampowered.com"


def test_handheld_category_row_scrolls_and_never_packs_equal_widths():
    """0.10.31: the QML category row uses a Flickable, not equal-width packing that overlapped on the Win600."""
    q = QML.read_text()
    assert "hhCatsFlick" in q and "Flickable" in q
    assert "width / Math.max(1, hh.cats.length)" not in q
    assert "cramped" in q and "ensureVisible" in q


def test_mode_tiles_and_boxset_and_roblox():
    """0.10.32: big mode tiles on Steam, Roblox official place ids, Box Set = Trilogy + Co-op only."""
    from atarilauncher import robloxex, webapps
    cats = _cats()
    steam = cats["steam"]["tiles"]
    modes = [t for t in steam if str(t.get("key", "")).startswith("mode:")]
    assert [t["title"] for t in modes] == ["Trilogy Mode", "Co-op Mode", "Roblox Mode", "Box Set Mode"]
    assert modes[0]["action"] == "goto:trilogies" and modes[1]["action"] == "goto:coop"
    assert modes[2]["action"] == "goto:roblox" and modes[3]["action"] == "boxset"
    assert "roblox" in (modes[2].get("note") or "").lower()
    # original fallback art may be bundled; official covers are never committed
    for name in ("mode-trilogy.png", "mode-coop.png", "mode-experiences.png", "mode-boxset.png"):
        assert (Path(handheld.__file__).parent / "assets" / name).is_file()
    rbx = cats["roblox"]["tiles"]
    ids = {t["key"].split(":")[1] for t in rbx}
    expected = {str(e.place_id) for e in robloxex.EXPERIENCES}
    assert ids == expected
    assert "4623386862" in ids and "4623386861" not in ids  # Piggy, not Strucid
    assert all(t["action"].startswith("roblox:") for t in rbx)
    box = handheld.boxset_tiles()
    tri = {t["key"] for t in cats["trilogies"]["tiles"]}
    coop = {t["key"] for t in cats["coop"]["tiles"]}
    assert {t["key"] for t in box} == tri | coop
    assert all(t["action"].startswith("steamstore:") for t in box)
    # every shortcut is a verified Steam app id
    for t in cats["trilogies"]["tiles"] + cats["coop"]["tiles"]:
        assert t["steam_appid"] and str(t["steam_appid"]).isdigit()


def test_boxset_boot_setting_survives_leave_and_has_no_pin():
    """Leaving Box Set Mode must not clear the reboot lock; neither path asks for a PIN."""
    from atarilauncher import boxsetboot
    hh = (Path(handheld.__file__).with_name("qml") / "HandheldScreen.qml").read_text()
    main = (Path(handheld.__file__).with_name("qml") / "Main.qml").read_text()
    boxqml = (Path(handheld.__file__).with_name("qml") / "BoxSetScreen.qml").read_text()
    be = Path(handheld.__file__).with_name("backend.py").read_text()
    assert "Lock into Box Set Mode on reboot" in be
    leave = be[be.find("def leaveBoxSet"):be.find("def toggleBoxSetBoot")]
    assert "boxset_boot" not in leave and "leaveBoxSet" in leave
    assert "backend.leaveBoxSet()" in boxqml and "boxSetExit" in boxqml
    assert "holdTimer" in boxqml and "Qt.Key_F10" in boxqml and "Qt.Key_Tab" in boxqml
    assert "r.kind === \"boxsetboot\"" in main
    assert "X-CodeAque-BoxSet" in Path(handheld.__file__).with_name("boxsetboot.py").read_text()
