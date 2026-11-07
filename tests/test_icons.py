"""Runtime icons for app tiles: installed icon first, then the download cache, then the coloured-initial fallback. Fake HTTP only."""
import os
import struct
import time
from pathlib import Path

import pytest

from atarilauncher import apps, config, covers, icons, library
from tests.fakeserver import JPG, PNG, Fake


@pytest.fixture
def fake():
    f = Fake()
    yield f
    f.stop()


@pytest.fixture(autouse=True)
def data(tmp_path, monkeypatch):
    monkeypatch.setenv("ATARI_DATA_DIR", str(tmp_path / "data"))


def fetcher(fake, tmp_path, **kw):
    return icons.IconFetcher(covers.Http(timeout=3, pause=0, backoff=0.01), tmp_path / "icons", fake.url + "/flathub", fake.url + "/steam", **kw)


BIG = PNG + b"\0" * 300          # the fake pictures are tiny: pad them over the 200-byte sanity floor


def page(fake, links, path="/"):
    fake.files[path] = ("<html><head>" + links + "</head><body>x</body></html>").encode()


def test_page_icons_prefers_big_touch_icons_then_png_then_svg():
    html = ('<link rel="icon" href="/f16.png" sizes="16x16"><link rel="shortcut icon" href="/favicon.ico">'
            "<link rel='apple-touch-icon' sizes='180x180' href='/t180.png'><link rel=\"icon\" href=\"/a.svg\" sizes=\"any\">"
            '<link rel="mask-icon" href="/mask.svg"><link rel="stylesheet" href="/x.css">')
    got = icons.page_icons(html, "https://example.com/start")
    assert got[0] == "https://example.com/t180.png" and "https://example.com/mask.svg" not in got and "https://example.com/x.css" not in got
    assert got.index("https://example.com/a.svg") < got.index("https://example.com/favicon.ico")


def test_installed_flatpak_icon_beats_the_cache(fake, tmp_path):
    home = tmp_path / "home"
    d = home / ".local/share/flatpak/exports/share/icons/hicolor/128x128/apps"
    d.mkdir(parents=True)
    (d / "tv.kodi.Kodi.png").write_bytes(BIG)
    spec = icons.Spec("app:kodi", "Kodi", flatpak="tv.kodi.Kodi")
    assert icons.local_icon(spec, home) == str(d / "tv.kodi.Kodi.png")
    assert icons.resolve(spec, home, tmp_path / "icons") == (str(d / "tv.kodi.Kodi.png"), True)
    assert fetcher(fake, tmp_path).run([spec], home=home)["skipped"] == 1 and fake.hits == []


def test_native_app_icon_found_through_its_desktop_file(tmp_path):
    home = tmp_path / "home"
    (home / ".local/share/applications").mkdir(parents=True)
    (home / ".local/share/applications/kodi.desktop").write_text("[Desktop Entry]\nName=Kodi\nExec=kodi %U\nIcon=kodi\n")
    ic = home / ".local/share/icons/hicolor/256x256/apps"
    ic.mkdir(parents=True)
    (ic / "kodi.png").write_bytes(BIG)
    assert icons.local_icon(icons.Spec("app:kodi", "Kodi", exes=("kodi",)), home) == str(ic / "kodi.png")
    (home / ".local/share/applications/other.desktop").write_text("[Desktop Entry]\nExec=/opt/foo/bar --x\nIcon=/abs/pic.png\n")
    assert icons.local_icon(icons.Spec("app:zzz", "Z", exes=("nothing",)), home) == ""


# ------------------------------------------------------------------ the library and the screen
def test_library_uses_cached_icons_and_marks_them_to_fit(tmp_path, monkeypatch):
    root = tmp_path / "data" / "icons"
    root.mkdir(parents=True)
    (root / "web_youtube.png").write_bytes(BIG)
    (root / "free_cs2.jpg").write_bytes(JPG + b"\0" * 300)
    cfg = config.default_config()
    cfg.steam_enabled = False
    res = library.build_library(cfg, home=tmp_path, metadata={})
    by = {g.gid: g for g in res.games}
    assert by["web:youtube"].cover == ""          # 0.5.8: no website logos on tiles - CodeAque's own vector picture instead
    assert by["free:cs2"].cover == str(root / "free_cs2.jpg") and not by["free:cs2"].fit      # real Steam art fills the tile
    assert by["web:netflix"].cover == "" and not by["web:netflix"].fit                         # offline / not fetched: coloured initials
    assert by["studio:open"].cover == ""


def test_cover_qml_draws_initials_when_there_is_no_icon():
    qml = (Path(__file__).resolve().parent.parent / "atarilauncher" / "qml" / "Cover.qml").read_text()
    assert "monogram" in qml and "Image.Ready" in qml and "cv.fit" in qml


def test_tiles_use_original_vector_pictures_drawn_at_screen_density():
    q = Path(__file__).resolve().parent.parent / "atarilauncher" / "qml"
    theme, icon, cover = (q / "Theme.qml").read_text(), (q / "Icon.qml").read_text(), (q / "Cover.qml").read_text()
    assert "function glyphFor" in theme and "devicePixelRatio" in icon and "devicePixelRatio" in cover and "Math.max(512" in cover


# ------------------------------------------------------------------ 0.10.0 picture rules
def test_websites_and_apps_are_never_downloaded_only_store_art(fake, tmp_path):
    page(fake, '<link rel="apple-touch-icon" href="/t.png">')
    fake.files["/t.png"] = BIG
    fake.files["/flathub/tv.kodi.Kodi.png"] = BIG
    fake.files["/steam/730/header.jpg"] = JPG + b"\0" * 300
    fake.files["/flathub/org.supertuxproject.SuperTux.png"] = BIG
    specs = [icons.Spec("web:youtube", "YouTube", site=fake.url + "/", kind="web"),
             icons.Spec("app:kodi", "Kodi", flatpak="tv.kodi.Kodi", kind="app"),
             icons.Spec("free:cs2", "Counter-Strike 2", steam_appid="730", kind="free"),
             icons.Spec("free:supertux", "SuperTux", flatpak="org.supertuxproject.SuperTux", kind="free")]
    rep = fetcher(fake, tmp_path).run(specs, home=tmp_path)
    assert rep["got"] == 2
    assert all(h.startswith("/steam/") or h == "/flathub/org.supertuxproject.SuperTux.png" for h in fake.hits), fake.hits
    assert icons.cached("free:cs2", tmp_path / "icons").endswith("free_cs2.jpg")
    assert not icons.cached("web:youtube", tmp_path / "icons") and not icons.cached("app:kodi", tmp_path / "icons")


def test_recipes_websites_get_codeaque_icon_world_games_get_steam_art():
    from atarilauncher import freegames, webapps
    for w in webapps.WEBAPPS:
        s = icons.spec_for(w.gid)
        assert s and s.kind == "web" and not s.site and not icons.downloadable(s)
    for a in apps.APP_DEFS:
        s = icons.spec_for(a.gid)
        assert s is None or (s.kind == "app" and not s.site and not icons.downloadable(s))
    for g in freegames.GAMES:
        assert (icons.spec_for(g.gid) is not None) == (g.kind in ("steam", "flatpak")), g.id
    ids = {w.id: icons.spec_for("store:" + w.id) for w in webapps.SELECTED_WORLD_GAMES}
    assert ids["wgyume"].steam_appid == "650700" and all(icons.downloadable(s) for s in ids.values())
    for w in webapps.WORLD_PAGES + webapps.STORE_PAGES:            # catalogues / searches: no picture to fetch
        assert not icons.downloadable(icons.spec_for("store:" + w.id)), w.id
    assert icons.spec_for("studio:open") is None and icons.spec_for("tool:record") is None


def test_offline_stops_quickly_and_leaves_the_fallback(tmp_path):
    dead = icons.IconFetcher(covers.Http(timeout=1, retries=1, pause=0, backoff=0.01), tmp_path / "icons",
                             "http://127.0.0.1:9/f", "http://127.0.0.1:9/s")
    specs = [icons.Spec(f"free:x{i}", "X", steam_appid=str(100 + i), kind="free") for i in range(6)]
    rep = dead.run(specs, home=tmp_path)
    assert rep["offline"] and rep["got"] == 0
    assert not any(Path(tmp_path / "icons").glob("*"))  or not any(Path(tmp_path / "icons").glob("*.jpg"))


def test_nothing_found_is_remembered_then_retried_later(fake, tmp_path):
    spec = icons.Spec("free:x", "X", steam_appid="999", kind="free")
    assert fetcher(fake, tmp_path).run([spec], home=tmp_path)["missing"] == 1
    n = len(fake.hits)
    assert fetcher(fake, tmp_path).run([spec], home=tmp_path)["missing"] == 1 and len(fake.hits) == n
    fake.files["/steam/999/header.jpg"] = JPG + b"\0" * 300
    assert fetcher(fake, tmp_path, now=lambda: time.time() + 5 * 86400).run([spec], home=tmp_path)["got"] == 1


def test_console_tiles_get_pictures_or_fall_back(tmp_path, monkeypatch):
    pytest.importorskip("PySide6")
    from atarilauncher import handheld
    root = tmp_path / "data" / "icons"
    root.mkdir(parents=True)
    (root / "store_wgyume.jpg").write_bytes(JPG + b"\0" * 300)
    tiles = {t["action"]: t for t in handheld.world_tiles()}
    assert tiles["store:wgyume"]["art"].endswith("store_wgyume.jpg") and tiles["store:wgyume"]["fit"] is False
    assert tiles["store:wgjapan"]["art"] == "" and tiles["store:wgjapan"]["title"]        # catalogue: CodeAque icon + label
    assert tiles["store:wgdave"]["art"] == ""                                              # not fetched yet / offline
    pic = tmp_path / "kodi.png"; pic.write_bytes(BIG)
    monkeypatch.setattr(icons, "local_icon", lambda spec, home=None: str(pic) if spec.gid == "app:kodi" else "")
    assert handheld.picture("app:kodi") == (pic.as_uri().replace("file:///", "file:///"), True) or handheld.picture("app:kodi")[0].endswith("kodi.png")
    assert handheld.picture("app:steam")[0] == "" and handheld.picture("web:netflix") == ("", True)


def test_backend_puts_the_installed_app_icon_on_its_tile(tmp_path, monkeypatch):
    pytest.importorskip("PySide6")
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    from atarilauncher import backend as be
    from atarilauncher.store import Store
    cfg = config.default_config()
    cfg.steam_enabled = False
    cfg.first_run = False
    b = be.Backend(cfg=cfg, store=Store(tmp_path / "s.json"), enable_gamepad=False)
    game = next(g for g in b._all if g.gid == "app:heroic")
    pic = tmp_path / "h.png"; pic.write_bytes(BIG)
    b._on_icon_arrived("app:heroic", str(pic), True)
    assert game.cover == str(pic) and game.fit
    web = next(g for g in b._all if g.gid.startswith("web:"))
    b._on_icon_arrived(web.gid, str(pic), True)
    assert web.cover == ""


def test_console_world_games_row_shows_cached_store_art_without_qml_warnings(tmp_path, monkeypatch):
    pytest.importorskip("PySide6")
    from PySide6.QtCore import Qt
    from PySide6.QtQuick import QQuickItem
    from tests.test_ui_keys import Ui, pump
    from PySide6.QtGui import QGuiApplication
    app = QGuiApplication.instance()
    from PySide6.QtGui import QImage
    root = tmp_path / "data" / "icons"; root.mkdir(parents=True, exist_ok=True)
    img = QImage(64, 64, QImage.Format_RGB32); img.fill(0x3366aa); img.save(str(root / "store_wgyume.png"))
    ui = Ui(app, tmp_path, monkeypatch, config_exists=True)
    try:
        ui.backend.setHandheld(True); pump(app, 200)
        hs = ui.win.findChild(QQuickItem, "handheldScreen")
        cats = [c["key"] for c in hs.property("cats")]
        hs.setProperty("cat", cats.index("world")); pump(app, 300)
        tiles = hs.property("tiles")
        yume = next(t for t in tiles if t["action"] == "store:wgyume")
        assert yume["art"].endswith("store_wgyume.png")
        assert not ui.warnings, ui.warnings
    finally:
        ui.close()
