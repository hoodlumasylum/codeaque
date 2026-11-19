"""Fav Sites: add / remove bookmarks, favicon cache, last DuckDuckGo URL, Kids mode hides the row."""
import json
import sqlite3
from pathlib import Path

import pytest

from atarilauncher import favsites, handheld
from atarilauncher.library import demo_library


def test_normalize_url_accepts_http_and_fills_https():
    assert favsites.normalize_url("https://example.com/path") == "https://example.com/path"
    assert favsites.normalize_url("Example.COM") == "https://Example.COM/"
    assert favsites.normalize_url("www.bbc.co.uk") == "https://www.bbc.co.uk/"
    assert favsites.normalize_url("") is None
    assert favsites.normalize_url("javascript:alert(1)") is None
    assert favsites.normalize_url("file:///etc/passwd") is None
    assert favsites.normalize_url("not a url at all") is None
    assert favsites.normalize_url("http://localhost/secret") is None


def test_add_list_remove_and_icon_fallback(tmp_path, monkeypatch):
    root = tmp_path / "data"
    monkeypatch.setattr(favsites, "fetch_favicon", lambda url, dest_dir, opener=None: None)
    a = favsites.add("https://example.com/news", "Example News", root=root, fetch=True)
    assert a is not None and a.title == "Example News" and a.icon == "" and a.id.startswith("example-com-")
    b = favsites.add("example.com/news", "Example News", root=root, fetch=False)
    assert b.id == a.id
    assert [s.url for s in favsites.list_sites(root)] == ["https://example.com/news"]
    assert favsites.remove(a.id, root=root) and favsites.list_sites(root) == []
    assert favsites.add("javascript:x", root=root) is None


def test_fetch_favicon_uses_the_cache_and_a_globe_when_nothing_works(tmp_path, monkeypatch):
    dest = tmp_path / "icons"
    # first call: serve a tiny PNG
    png = b"\x89PNG\r\n\x1a\n" + b"\x00" * 32

    class FakeResp:
        def __init__(self, data, ct="image/png"):
            self._data = data
            self.headers = {"Content-Type": ct}
        def read(self, n=None):
            return self._data[: n or len(self._data)]
        def geturl(self):
            return "https://example.com/"
        def __enter__(self):
            return self
        def __exit__(self, *a):
            return False

    def open_ok(url, headers=None, timeout=0):
        return FakeResp(png)

    monkeypatch.setattr(favsites, "_open", lambda url: open_ok(url))
    path = favsites.fetch_favicon("https://example.com/", dest)
    assert path is not None and path.is_file() and path.read_bytes().startswith(b"\x89PNG")
    # second call does not hit the network
    monkeypatch.setattr(favsites, "_open", lambda url: (_ for _ in ()).throw(AssertionError("should use cache")))
    again = favsites.fetch_favicon("https://example.com/", dest)
    assert again == path

    dest2 = tmp_path / "empty"
    monkeypatch.setattr(favsites, "_open", lambda url: (_ for _ in ()).throw(favsites.urllib.error.URLError("no")))
    assert favsites.fetch_favicon("https://missing.example/", dest2) is None


def test_last_site_reads_chromium_history_and_skips_duckduckgo(tmp_path):
    hist = tmp_path / "Default"
    hist.mkdir()
    db = hist / "History"
    con = sqlite3.connect(db)
    con.execute("CREATE TABLE urls (url TEXT, title TEXT, last_visit_time INTEGER)")
    con.executemany("INSERT INTO urls VALUES (?,?,?)", [
        ("https://duckduckgo.com/?q=cats", "cats at DuckDuckGo", 30),
        ("https://duckduckgo.com/", "DuckDuckGo", 20),
        ("https://en.wikipedia.org/wiki/Cat", "Cat - Wikipedia", 40),
        ("https://en.wikipedia.org/wiki/Dog", "Dog - Wikipedia", 10),
    ])
    con.commit(); con.close()
    rows = favsites.last_visited_urls(tmp_path, 10)
    assert rows[0] == ("https://en.wikipedia.org/wiki/Cat", "Cat - Wikipedia")
    assert all("duckduckgo" not in u for u, _ in rows)
    assert favsites.last_site(tmp_path)[0].endswith("/wiki/Cat")


def test_fav_sites_row_has_duckduckgo_and_add_and_hides_in_kids_mode(tmp_path, monkeypatch):
    root = tmp_path / "data"
    monkeypatch.setattr(favsites, "store_dir", lambda r=None: root)
    monkeypatch.setattr(favsites, "store_path", lambda r=None: root / "sites.json")
    monkeypatch.setattr(favsites, "icons_dir", lambda r=None: root / "icons")
    favsites.add("https://example.org/", "Example", root=root, fetch=False)
    cats = {c["key"]: c for c in handheld.categories(demo_library().games, kids_mode=False, fav_root=root)}
    assert "favsites" in cats
    titles = [t["title"] for t in cats["favsites"]["tiles"]]
    assert titles[:2] == ["DuckDuckGo", "Add fav site"]
    assert "Example" in titles
    saved = [t for t in cats["favsites"]["tiles"] if t.get("removable")]
    assert saved and saved[0]["action"].startswith("favopen:")
    hidden = handheld.categories(demo_library().games, kids_mode=True, fav_root=root)
    assert "favsites" not in [c["key"] for c in hidden]
    # DuckDuckGo itself must not sneak into another Kids-visible row as a browser bypass
    for c in hidden:
        for t in c["tiles"]:
            assert t.get("action") not in ("duckduckgo", "addfav")
            assert not str(t.get("action", "")).startswith("favopen:")
            assert t.get("gid") != "web:browser"



def test_every_fav_sites_row_tile_id_resolves_to_a_launch_action(tmp_path, monkeypatch):
    """Every tile on the Fav Sites row (DuckDuckGo, Add fav site, and each saved site) must be a
    recognised launch id. A missing mapping is what showed 'Unknown app' on Code's PC."""
    from atarilauncher import launcher, webapps
    from atarilauncher.config import default_config
    from atarilauncher.library import Game

    root = tmp_path / "data"
    monkeypatch.setattr(favsites, "store_dir", lambda r=None: root)
    monkeypatch.setattr(favsites, "store_path", lambda r=None: root / "sites.json")
    monkeypatch.setattr(favsites, "icons_dir", lambda r=None: root / "icons")
    site = favsites.add("https://example.org/", "Example", root=root, fetch=False)
    assert site is not None
    tiles = None
    for c in handheld.categories(demo_library().games, kids_mode=False, fav_root=root):
        if c["key"] == "favsites":
            tiles = c["tiles"]
            break
    assert tiles, "the Fav Sites row must be present"
    kinds = []
    for t in tiles:
        kind = favsites.resolve_tile(t.get("action") or "", t.get("gid") or "")
        assert kind, "unrecognised Fav Sites tile: " + repr(t)
        kinds.append(kind)
    assert "duckduckgo" in kinds and "addfav" in kinds
    assert any(k == "fav:" + site.id for k in kinds)

    # The launch dispatcher must send fav: and DuckDuckGo to the browser, never 'Unknown app'
    started = []
    opened = []

    real_popen = launcher.subprocess.Popen

    def fake_popen(cmd, *a, **kw):
        argv = list(cmd) if not isinstance(cmd, str) else [cmd]
        # ctypes.util.find_library also uses Popen; only record browser launches
        if argv and Path(str(argv[0])).name in {"firefox", "firefox-esr", "xdg-open",
                                                "chromium", "chromium-browser", "google-chrome", "google-chrome-stable"}:
            opened.append(argv)
            class P:
                def wait(self):
                    return 0
                def poll(self):
                    return 0
                def __enter__(self):
                    return self
                def __exit__(self, *exc):
                    return False
            return P()
        return real_popen(cmd, *a, **kw)

    monkeypatch.setattr(launcher.subprocess, "Popen", fake_popen)
    monkeypatch.setattr(webapps, "build_command", lambda cfg, wa, **k: (["firefox", wa.url], "Firefox"))
    monkeypatch.setattr(launcher, "_check_executable", lambda *a, **k: None)
    monkeypatch.setenv("ATARI_HINT_DELAY", "0")
    cfg = default_config()
    g = Game(gid="fav:" + site.id, title=site.title, platform=webapps.STREAMING,
             platform_name="Fav Sites", meta={"browse_url": site.url})
    secs, err = launcher.run_game(cfg, g, on_started=lambda: started.append(1))
    assert "Unknown app" not in (err or "")
    assert opened and site.url in " ".join(opened[0]), opened
    assert started == [1]

    opened.clear(); started.clear()
    g2 = Game(gid="web:browser", title="DuckDuckGo", platform=webapps.STREAMING, platform_name="Streaming")
    secs, err = launcher.run_game(cfg, g2, on_started=lambda: started.append(1))
    assert "Unknown app" not in (err or "")
    assert opened and started == [1]
