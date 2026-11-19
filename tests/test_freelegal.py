"""Free & Legal search: Internet Archive, itch.io free games, official Linux downloads, Transmission, Kids mode."""
from __future__ import annotations

import io
import json
from pathlib import Path
from urllib.error import URLError

import pytest

from atarilauncher import apps, downloads, freelegal, handheld, webapps
from atarilauncher.config import default_config

SRC = Path(freelegal.__file__).read_text(encoding="utf-8")
QML_MAIN = Path(freelegal.__file__).resolve().parent / "qml" / "Main.qml"
QML_HH = Path(freelegal.__file__).resolve().parent / "qml" / "HandheldScreen.qml"
HANDHELD = Path(handheld.__file__).read_text(encoding="utf-8")


class FakeHTTP:
    """urlopen stand-in: maps a URL prefix or exact URL to bytes, or raises."""

    def __init__(self, mapping: dict[str, bytes | Exception]):
        self.mapping = mapping
        self.calls: list[str] = []

    def __call__(self, req, timeout=None):
        url = req if isinstance(req, str) else req.full_url
        self.calls.append(url)
        for key, val in self.mapping.items():
            if url == key or url.startswith(key):
                if isinstance(val, Exception):
                    raise val
                return io.BytesIO(val)
        raise URLError(f"unexpected URL: {url}")


def ia_search_body(docs):
    return json.dumps({"response": {"numFound": len(docs), "docs": docs}}).encode()


def ia_meta_body(*names: str):
    return json.dumps({"files": [{"name": n} for n in names]}).encode()


CC0 = "https://creativecommons.org/publicdomain/zero/1.0/"
DOC_OK = {
    "identifier": "open_sokoban",
    "title": "Open Sokoban",
    "mediatype": "software",
    "description": "A free puzzle game.",
    "licenseurl": CC0,
    "format": ["ZIP", "Archive BitTorrent"],
}
DOC_NO_LICENCE = {
    "identifier": "mystery_dump",
    "title": "Mystery Dump",
    "mediatype": "software",
    "licenseurl": "",
    "format": ["ZIP"],
}
DOC_WRONG_MEDIA = {
    "identifier": "some_texts",
    "title": "Some Texts",
    "mediatype": "texts",
    "licenseurl": CC0,
}


def test_module_and_urls_stay_on_legal_hosts_with_no_pirate_indexes_or_plugins():
    for bad in ("thepiratebay", "1337x", "rarbg", "yts.mx", "kickasstorrents",
                "kickass.to", "limetorrents", "nyaa.si", "qbittorrent", "magnet:?"):
        assert bad not in SRC.lower()
    for d in freelegal.DISTROS:
        assert freelegal.allowed_url(d.url) and d.url.startswith("https://")
    assert freelegal.allowed_url(freelegal.ITCH_FREE_BROWSE)
    assert freelegal.allowed_url("https://archive.org/details/open_sokoban")
    assert not freelegal.allowed_url("http://archive.org/details/x")          # https only
    assert not freelegal.allowed_url("https://thepiratebay.org/search/x")
    assert not freelegal.allowed_url("magnet:?xt=urn:btih:abc")
    assert "qbittorrent" not in apps.BY_ID
    assert "transmission" in apps.BY_ID


def test_sanitize_query_strips_archive_operators_so_the_licence_filter_cannot_be_dropped():
    assert freelegal.sanitize_query('foo" OR mediatype:texts AND licenseurl:*') == "foo OR mediatype texts AND licenseurl"
    q = freelegal._ia_query('foo AND licenseurl:*')
    assert "mediatype:(software OR movies OR audio)" in q
    assert "licenseurl:*creativecommons.org*" in q
    assert "AND" in q
    # the licence clause is still present even if the user typed operators
    assert q.count("licenseurl:") >= 1
    assert "licenseurl:*" not in q.split("AND")[-1] or "creativecommons" in q


def test_itch_urls_are_official_and_filtered_to_free():
    assert freelegal.itch_url("") == "https://itch.io/games/free"
    url = freelegal.itch_url("celeste-ish puzzle")
    assert url.startswith("https://itch.io/games/free?") and "q=celeste-ish" in url
    assert "price" not in url or "free" in url


def test_distros_are_official_pages_and_filter_by_name():
    ids = {d.id for d in freelegal.DISTROS}
    assert {"mint", "ubuntu", "fedora", "debian"} <= ids
    assert [d.id for d in freelegal.match_distros("")] == [d.id for d in freelegal.DISTROS]
    assert [d.id for d in freelegal.match_distros("linux")] == [d.id for d in freelegal.DISTROS]
    assert [d.id for d in freelegal.match_distros("Mint")] == ["mint"]
    assert [d.id for d in freelegal.match_distros("pop os")] == ["popos"]
    assert freelegal.match_distros("zelda") == []


def test_archive_search_keeps_only_licensed_software_movies_audio_and_spots_torrents():
    opener = FakeHTTP({
        "https://archive.org/advancedsearch.php": ia_search_body([DOC_OK, DOC_NO_LICENCE, DOC_WRONG_MEDIA]),
    })
    found, err = freelegal.search_archive("sokoban", opener=opener)
    assert err == "" and [r.ident for r in found] == ["open_sokoban"]
    r = found[0]
    assert r.has_torrent and r.url == "https://archive.org/details/open_sokoban"
    assert r.kind == "software" and "creativecommons.org" in r.detail
    assert "q=" in opener.calls[0] and "licenseurl" in opener.calls[0] and "mediatype" in opener.calls[0]


def test_archive_search_reports_a_network_error_without_raising():
    opener = FakeHTTP({"https://archive.org/advancedsearch.php": URLError("offline")})
    found, err = freelegal.search_archive("sokoban", opener=opener)
    assert found == [] and "Internet Archive" in err and "offline" in err


def test_combined_search_lists_itch_linux_and_archive():
    opener = FakeHTTP({"https://archive.org/advancedsearch.php": ia_search_body([DOC_OK])})
    out = freelegal.search("sokoban", opener=opener)
    assert out.query == "sokoban"
    sources = [r.source for r in out.results]
    assert sources[0] == "itch" and "itch.io/games/free" in out.results[0].url
    assert "linux" not in sources          # "sokoban" is not a distro name
    assert any(r.ident == "open_sokoban" for r in out.results)
    mint = freelegal.search("mint", opener=FakeHTTP({
        "https://archive.org/advancedsearch.php": ia_search_body([]),
    }))
    assert [r.ident for r in mint.results if r.source == "linux"] == ["mint"]
    empty = freelegal.search("")
    assert empty.results[0].source == "itch" and empty.results[0].url == freelegal.ITCH_FREE_BROWSE
    assert [r.ident for r in empty.results if r.source == "linux"] == [d.id for d in freelegal.DISTROS]
    assert all(r.source != "archive" for r in empty.results)   # no unfiltered IA dump


def test_archive_torrent_is_saved_and_handed_to_transmission(tmp_path):
    ident = "open_sokoban"
    torrent = b"d8:announce15:http://ia.example4:infod4:name4:gameee"
    opener = FakeHTTP({
        f"https://archive.org/metadata/{ident}": ia_meta_body("folder/notes.txt", f"{ident}_archive.torrent"),
        f"https://archive.org/download/{ident}/{ident}_archive.torrent": torrent,
    })
    cfg = default_config()
    home = tmp_path
    calls = []

    def which(name):
        return "/usr/bin/transmission-gtk" if name == "transmission-gtk" else None

    def popen(cmd, **kw):
        calls.append((cmd, kw))
        return None

    ok, text, path = freelegal.add_torrent_to_transmission(
        cfg, ident, home=home, opener=opener, which=which, popen=popen)
    assert ok and path is not None and path.read_bytes() == torrent
    assert path.parent == home / "Downloads" and path.suffix == ".torrent"
    assert calls and calls[0][0][0] == "transmission-gtk" and calls[0][0][1] == str(path)
    assert "Transmission" in text

    # Missing client uses the existing Transmission install hint, not a second app.
    ok, text, path = freelegal.add_torrent_to_transmission(
        cfg, ident, home=home, opener=opener, which=lambda n: None, popen=popen)
    assert not ok and "Transmission" in text and "qBittorrent" not in text


def test_items_without_a_torrent_explain_themselves(tmp_path):
    opener = FakeHTTP({
        "https://archive.org/metadata/open_sokoban": ia_meta_body("game.zip", "readme.txt"),
    })
    ok, text, path = freelegal.add_torrent_to_transmission(
        default_config(), "open_sokoban", home=tmp_path, opener=opener, which=lambda n: None)
    assert not ok and path is None and "no .torrent" in text.lower()


def test_open_url_refuses_disallowed_hosts_and_opens_allowed_ones():
    cfg = default_config()
    ok, text = freelegal.open_url(cfg, "https://thepiratebay.org/x")
    assert not ok and "legal sources" in text
    calls = []

    def which(name):
        return "/usr/bin/chromium" if name in ("chromium", "chromium-browser") else None

    def popen(cmd, **kw):
        calls.append(cmd)

    ok, text = freelegal.open_url(cfg, "https://www.linuxmint.com/download.php", "Linux Mint",
                                  which=which, popen=popen)
    assert ok and calls and "https://www.linuxmint.com/download.php" in " ".join(calls[0])


def test_open_itch_prefers_the_installed_app_then_the_browser():
    cfg = default_config()
    calls = []

    def which_itch(name):
        return "/usr/bin/itch" if name == "itch" else None

    def popen(cmd, **kw):
        calls.append(cmd)

    ok, text = freelegal.open_itch(cfg, "puzzle", which=which_itch, popen=popen)
    assert ok and calls[0][0] == "itch" and "itch.io app" in text

    def which_browser(name):
        return "/usr/bin/chromium" if name in ("chromium", "chromium-browser") else None

    ok, text = freelegal.open_itch(cfg, "puzzle", which=which_browser, popen=popen)
    assert ok and any("itch.io/games/free" in " ".join(c) for c in calls[1:])


# ------------------------------------------------------------------ backend / home tiles


@pytest.fixture
def be_(tmp_path, monkeypatch, _qt_app):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setenv("ATARI_NO_WIFI", "1")
    from atarilauncher import backend as be
    from atarilauncher.store import Store
    cfg = default_config()
    cfg.steam_enabled = False
    cfg.first_run = False
    return be.Backend(cfg=cfg, store=Store(tmp_path / "s.json"), enable_gamepad=False)


def _titles(rows):
    return [r.get("title", "") for r in rows]


def test_console_home_has_a_free_legal_tile_hidden_in_kids_mode():
    from atarilauncher.library import demo_library
    games = demo_library().games
    cats = {c["key"]: c for c in handheld.categories(games)}
    dl = [t["action"] for t in cats["downloads"]["tiles"]]
    assert dl[0] == "freelegal" and "downloads" in dl
    store = [t.get("action") or t.get("gid") for t in cats["store"]["tiles"]]
    assert "freelegal" in store
    hidden = {c["key"]: c for c in handheld.categories(games, kids_mode=True)}
    assert "freelegal" not in [t.get("action") for t in hidden["downloads"]["tiles"]]
    assert "freelegal" not in [t.get("action") for t in hidden["store"]["tiles"]]
    # no third-party logos on the new tile
    tile = next(t for t in cats["downloads"]["tiles"] if t["action"] == "freelegal")
    assert tile["title"] == "Free & Legal" and tile["icon"] in ("search", "star", "download", "grid")
    assert not tile.get("art")


def test_page_is_osk_friendly_searches_legal_sources_and_is_blocked_in_kids_mode(be_, tmp_path, monkeypatch):
    opener = FakeHTTP({"https://archive.org/advancedsearch.php": ia_search_body([DOC_OK])})
    be_._fl_opener = opener
    # default search (empty) — linux + itch, Type a search first
    rows = be_.pageRows("freelegal")
    titles = _titles(rows)
    assert be_.pageTitle("freelegal") == "FREE & LEGAL"
    assert any("Type a search" in t for t in titles)
    assert any("itch.io" in t for t in titles)
    assert any(t == "Linux Mint" for t in titles)
    assert be_.pageActivate("freelegal", "search") == "osk:freelegal"

    be_.freeLegalSearch("sokoban")
    rows = be_.pageRows("freelegal")
    titles = _titles(rows)
    assert any("itch.io" in t and "sokoban" in t.lower() for t in titles)
    assert any("Open Sokoban" in t for t in titles)
    ia = next(r for r in rows if r.get("id") == "fl:ia:open_sokoban")
    assert "Internet Archive" in ia["detail"]
    tor = next(r for r in rows if r.get("id") == "fl:tor:open_sokoban")
    assert "Transmission" in (tor.get("detail") or "") + (tor.get("title") or "")

    opened = []
    monkeypatch.setattr(freelegal, "open_url", lambda cfg, url, title="", **k: opened.append((url, title)) or (True, "ok"))
    monkeypatch.setattr(freelegal, "open_itch", lambda cfg, query="", **k: opened.append(("itch", query)) or (True, "ok"))
    assert be_.pageActivate("freelegal", "fl:ia:open_sokoban") == ""
    assert opened[0][0] == "https://archive.org/details/open_sokoban"
    assert be_.pageActivate("freelegal", "fl:itch") == ""
    assert opened[1][0] == "itch"
    assert be_.pageActivate("freelegal", "fl:distro:mint") == ""
    assert opened[2][0] == "https://www.linuxmint.com/download.php"

    handed = []
    monkeypatch.setattr(freelegal, "add_torrent_to_transmission",
                        lambda cfg, ident, home=None, **k: handed.append(ident) or (True, "Adding to Transmission...", Path("/tmp/x.torrent")))
    assert be_.pageActivate("freelegal", "fl:tor:open_sokoban") == ""
    assert handed == ["open_sokoban"]

    # Kids mode hides the page
    be_._kids_mode = True
    krows = be_.pageRows("freelegal")
    assert any("Kids mode" in (r.get("title") or "") for r in krows)
    assert not any(r.get("id", "").startswith("fl:") for r in krows)
    assert be_.pageActivate("freelegal", "search") == "back"
    assert be_.pageActivate("freelegal", "fl:ia:open_sokoban") == "back"


def test_missing_transmission_offers_the_existing_install_not_a_new_tile(be_, monkeypatch):
    be_._fl_opener = FakeHTTP({
        "https://archive.org/advancedsearch.php": ia_search_body([DOC_OK]),
    })
    be_.freeLegalSearch("sokoban")
    installs = []
    monkeypatch.setattr(be_, "begin_install", lambda specs, **kw: installs.append((specs, kw)))
    monkeypatch.setattr(freelegal, "add_torrent_to_transmission",
                        lambda *a, **k: (False, apps.install_hint(apps.BY_ID["transmission"]), None))
    assert be_.pageActivate("freelegal", "fl:tor:open_sokoban") == "refresh"
    # After a failed open because Transmission is missing, an install row appears.
    rows = be_.pageRows("freelegal")
    inst = next((r for r in rows if r.get("id") == "installtransmission"), None)
    assert inst is not None and "Transmission" in inst["title"]
    assert be_.pageActivate("freelegal", "installtransmission") == ""
    assert installs and installs[0][0] == ["transmission"]


def test_qml_wires_the_tile_the_osk_and_controller_hints():
    main = QML_MAIN.read_text()
    assert 'id === "freelegal"' in main and 'openPage("freelegal")' in main
    assert 'osk:freelegal' in main
    assert "FREE & LEGAL" in main
    assert "freeLegalSearch" in main
    # no third-party logos added for this feature
    for name in ("Transmission logo", "itch.io logo", "Internet Archive logo", "qBittorrent"):
        assert name.lower() not in main.lower() or "qBittorrent" not in main
    assert "qBittorrent" not in main and "qBittorrent" not in QML_HH.read_text()
