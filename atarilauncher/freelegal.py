"""Free & Legal search for the PC console home.

Queries only three legal sources:

* the Internet Archive advancedsearch API (archive.org), limited to software,
  movies and audio that carry a public-domain or freely licensed ``licenseurl``
* itch.io free games, via official browse / search pages filtered to free
  (opened in the browser, or the itch.io app if it is already installed)
* a curated list of official Linux distribution download pages (Mint, Ubuntu,
  Fedora, Debian, and similar)

Results open in the browser. When an Internet Archive item has a ``.torrent``
file, that file is handed to the existing Transmission tile
(``downloads.open_torrent``). This module never enables or bundles torrent
search plugins, never links to pirate indexes, and is hidden in Kids mode.
"""
from __future__ import annotations

import json
import re
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from . import __version__, apps, downloads, webapps

USER_AGENT = f"CodeAque/{__version__} (+https://github.com/hoodlumasylum/codeaque; Free & Legal search)"
IA_SEARCH = "https://archive.org/advancedsearch.php"
IA_META = "https://archive.org/metadata/{ident}"
IA_DETAILS = "https://archive.org/details/{ident}"
IA_DOWNLOAD = "https://archive.org/download/{ident}/{name}"
ITCH_FREE_BROWSE = "https://itch.io/games/free"
ITCH_FREE_SEARCH = "https://itch.io/games/free?q={query}"
# Media types the Archive search is allowed to return.
IA_MEDIA = ("software", "movies", "audio")
# licenseurl substrings that mean public domain or a free / open licence.
# The user query is never allowed to drop this filter.
IA_LICENSE = (
    "*creativecommons.org*",
    "*creativecommons.org/publicdomain*",
    "*publicdomain*",
    "*public-domain*",
    "*gnu.org/licenses*",
    "*opensource.org/licenses*",
)
# Hosts this feature is allowed to open. Anything else is refused.
ALLOWED_HOSTS = frozenset({
    "archive.org", "www.archive.org",
    "itch.io", "www.itch.io",
    "linuxmint.com", "www.linuxmint.com",
    "ubuntu.com", "www.ubuntu.com",
    "fedoraproject.org", "www.fedoraproject.org",
    "debian.org", "www.debian.org",
    "get.opensuse.org", "www.opensuse.org", "opensuse.org",
    "archlinux.org", "www.archlinux.org",
    "system76.com", "www.system76.com",
    "elementary.io", "www.elementary.io",
    "zorin.com", "www.zorin.com",
})

IDENT_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$")
SAFE_IA_CHARS = re.compile(r"[^A-Za-z0-9 .,'+-]+")


@dataclass(frozen=True)
class Distro:
    id: str
    title: str
    url: str
    aliases: tuple[str, ...]
    note: str = "Official download page. Opens in the browser."


# Official project download pages only (not mirrors or unofficial ISOs).
DISTROS: tuple[Distro, ...] = (
    Distro("mint", "Linux Mint", "https://www.linuxmint.com/download.php",
           ("mint", "linux mint", "cinnamon", "lmde")),
    Distro("ubuntu", "Ubuntu", "https://ubuntu.com/download",
           ("ubuntu", "kubuntu", "xubuntu", "lubuntu")),
    Distro("fedora", "Fedora", "https://fedoraproject.org/workstation/download",
           ("fedora",)),
    Distro("debian", "Debian", "https://www.debian.org/distrib/",
           ("debian",)),
    Distro("opensuse", "openSUSE", "https://get.opensuse.org/",
           ("opensuse", "suse", "open suse")),
    Distro("arch", "Arch Linux", "https://archlinux.org/download/",
           ("arch", "archlinux", "arch linux")),
    Distro("popos", "Pop!_OS", "https://system76.com/pop/download/",
           ("pop", "popos", "pop!_os", "pop os", "system76")),
    Distro("elementary", "elementary OS", "https://elementary.io/",
           ("elementary", "elementary os")),
    Distro("zorin", "Zorin OS", "https://zorin.com/os/download/",
           ("zorin", "zorin os")),
)


@dataclass
class Result:
    source: str          # "archive" | "itch" | "linux"
    title: str
    url: str
    detail: str = ""
    ident: str = ""      # Internet Archive identifier, when source == "archive"
    has_torrent: bool = False
    kind: str = ""       # software / movies / audio, or "linux" / "itch"
    key: str = ""        # stable id used on the page

    def __post_init__(self) -> None:
        if not self.key:
            if self.source == "archive" and self.ident:
                self.key = "ia:" + self.ident
            elif self.source == "linux" and self.ident:
                self.key = "distro:" + self.ident
            elif self.source == "itch":
                self.key = "itch"
            else:
                self.key = self.source


@dataclass
class SearchOut:
    query: str
    results: list[Result] = field(default_factory=list)
    error: str = ""
    notice: str = ""


def sanitize_query(text: str) -> str:
    """Strip Archive operators so a typed query cannot drop the licence filter."""
    cleaned = SAFE_IA_CHARS.sub(" ", (text or "").replace('"', " ").replace(":", " "))
    return " ".join(cleaned.split())[:80]


def allowed_url(url: str) -> bool:
    try:
        parts = urllib.parse.urlsplit(url)
    except ValueError:
        return False
    if parts.scheme != "https":
        return False
    host = (parts.hostname or "").lower()
    if host not in ALLOWED_HOSTS:
        return False
    return True


def itch_url(query: str = "") -> str:
    q = sanitize_query(query)
    if not q:
        return ITCH_FREE_BROWSE
    return ITCH_FREE_SEARCH.format(query=urllib.parse.quote_plus(q))


def match_distros(query: str = "") -> list[Distro]:
    """Curated official Linux download pages, filtered by a typed query."""
    q = sanitize_query(query).lower()
    if not q or q in ("linux", "iso", "distro", "distros", "linux iso", "linux distro"):
        return list(DISTROS)
    out = []
    for d in DISTROS:
        hay = " ".join((d.id, d.title, *d.aliases)).lower()
        if q in hay or any(alias in q or q in alias for alias in (d.id, d.title.lower(), *d.aliases)):
            out.append(d)
    return out


def _ia_license_clause() -> str:
    return "(" + " OR ".join(f"licenseurl:{term}" for term in IA_LICENSE) + ")"


def _ia_query(user: str) -> str:
    media = "mediatype:(" + " OR ".join(IA_MEDIA) + ")"
    q = f"{media} AND {_ia_license_clause()}"
    terms = sanitize_query(user)
    if terms:
        # Phrase search keeps the licence / mediatype filter on the left of AND.
        q += f' AND ("{terms}")'
    return q


def _request(url: str, timeout: float = 20, opener=None) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    open_fn = opener or urllib.request.urlopen
    with open_fn(req, timeout=timeout) as resp:
        return resp.read()


def _has_torrent_format(fmt) -> bool:
    if fmt is None:
        return False
    if isinstance(fmt, str):
        fmt = [fmt]
    return any("torrent" in str(x).lower() for x in fmt)


def _short(text: str, n: int = 160) -> str:
    text = " ".join((text or "").split())
    return text if len(text) <= n else text[: n - 1].rstrip() + "…"


def search_archive(query: str, rows: int = 20, opener=None) -> tuple[list[Result], str]:
    """Internet Archive items that are software/movies/audio and freely licensed."""
    terms = sanitize_query(query)
    if not terms:
        return [], ""
    params = [
        ("q", _ia_query(terms)),
        ("fl[]", "identifier"),
        ("fl[]", "title"),
        ("fl[]", "mediatype"),
        ("fl[]", "description"),
        ("fl[]", "licenseurl"),
        ("fl[]", "format"),
        ("rows", str(max(1, min(int(rows), 50)))),
        ("page", "1"),
        ("output", "json"),
        ("sort[]", "downloads desc"),
    ]
    url = IA_SEARCH + "?" + urllib.parse.urlencode(params)
    try:
        data = json.loads(_request(url, opener=opener).decode("utf-8", "replace"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError, ValueError) as exc:
        return [], f"Could not reach the Internet Archive ({exc}). Check the connection and try again."
    docs = ((data.get("response") or {}).get("docs")) or []
    out: list[Result] = []
    for doc in docs:
        ident = str(doc.get("identifier") or "")
        if not IDENT_RE.match(ident):
            continue
        media = str(doc.get("mediatype") or "")
        if media not in IA_MEDIA:
            continue
        licenseurl = str(doc.get("licenseurl") or "")
        if not licenseurl:
            continue
        item_url = IA_DETAILS.format(ident=ident)
        if not allowed_url(item_url):
            continue
        title = str(doc.get("title") or ident)
        licence = _short(licenseurl.replace("https://", "").replace("http://", ""), 80)
        detail = f"Internet Archive · {media} · {licence}"
        desc = _short(str(doc.get("description") or ""))
        if desc:
            detail = detail + " — " + desc
        out.append(Result(source="archive", title=title, url=item_url, detail=detail,
                          ident=ident, has_torrent=_has_torrent_format(doc.get("format")),
                          kind=media))
    return out, ""


def search(query: str, rows: int = 20, opener=None) -> SearchOut:
    """Search all three legal sources. An empty query lists Linux downloads and itch.io free games."""
    q = sanitize_query(query)
    results: list[Result] = []
    err = ""
    # itch.io first so a game search always has an official free-filtered page.
    results.append(Result(
        source="itch",
        title=("Search itch.io for “%s” (free games only)" % q) if q else "Browse free games on itch.io",
        url=itch_url(q),
        detail="Official itch.io page, already filtered to free games. Opens in the browser, or the itch.io app if it is installed.",
        kind="itch",
        key="itch",
    ))
    for d in match_distros(q):
        results.append(Result(
            source="linux", title=d.title, url=d.url, detail=d.note,
            ident=d.id, kind="linux", key="distro:" + d.id,
        ))
    if q:
        archive, err = search_archive(q, rows=rows, opener=opener)
        results.extend(archive)
        if not archive and not err:
            err = ""  # no matches is fine; the page explains it
    notice = ""
    if q and not any(r.source == "archive" for r in results) and not err:
        notice = "No freely licensed Internet Archive items matched that search. itch.io and Linux downloads are still listed when they apply."
    return SearchOut(query=q, results=results, error=err, notice=notice)


def torrent_file_url(ident: str, opener=None) -> str | None:
    """Return the Archive .torrent URL for an item, or None if it has none."""
    if not IDENT_RE.match(ident or ""):
        return None
    url = IA_META.format(ident=ident)
    try:
        data = json.loads(_request(url, opener=opener).decode("utf-8", "replace"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError, ValueError):
        return None
    for info in data.get("files") or []:
        name = str((info or {}).get("name") or "")
        if name.lower().endswith(".torrent") and ".." not in name and not name.startswith("/"):
            file_url = IA_DOWNLOAD.format(ident=ident, name=urllib.parse.quote(name))
            if allowed_url(file_url):
                return file_url
    return None


def save_torrent(ident: str, dest_dir: Path, opener=None) -> tuple[Path | None, str]:
    """Download an Archive .torrent into dest_dir. Never raises."""
    file_url = torrent_file_url(ident, opener=opener)
    if not file_url:
        return None, "This Internet Archive item has no .torrent file. Open it in the browser instead."
    name = urllib.parse.unquote(urllib.parse.urlsplit(file_url).path.rsplit("/", 1)[-1])
    name = re.sub(r"[^A-Za-z0-9._-]+", "_", name) or (ident + ".torrent")
    if not name.lower().endswith(".torrent"):
        name += ".torrent"
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / name
    try:
        data = _request(file_url, opener=opener)
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as exc:
        return None, f"Could not download the .torrent file ({exc})."
    if len(data) < 16 or b"torrent" not in data[:64].lower() and not data.startswith(b"d"):
        # A BitTorrent file is a bencoded dict (starts with 'd'). Refuse obviously wrong bodies.
        if not data.startswith(b"d"):
            return None, "The download was not a .torrent file, so it was not opened."
    try:
        dest.write_bytes(data)
    except OSError as exc:
        return None, f"Could not save the .torrent file ({exc})."
    return dest, ""


def downloads_dir(home: Path | None = None) -> Path:
    return Path(home or Path.home()) / "Downloads"


def add_torrent_to_transmission(cfg, ident: str, home: Path | None = None, opener=None,
                                which=None, popen=None) -> tuple[bool, str, Path | None]:
    """Save the Archive .torrent and hand it to the existing Transmission app. Never raises."""
    path, err = save_torrent(ident, downloads_dir(home), opener=opener)
    if path is None:
        return False, err, None
    kw = {}
    if which is not None:
        kw["which"] = which
    if popen is not None:
        kw["popen"] = popen
    ok, text = downloads.open_torrent(cfg, path, home, **kw)
    return ok, text, path


def open_url(cfg, url: str, title: str = "Free & Legal", home: Path | None = None,
             which=None, popen=None) -> tuple[bool, str]:
    """Open an allowed official page in a normal browser window. Never raises."""
    if not allowed_url(url):
        return False, "That address is not one of the legal sources this search uses."
    app = webapps.WebApp("freelegal", title, url, browse=True,
                         note="A legal source in a normal browser window.")
    kw = {}
    if which is not None:
        kw["which"] = which
    cmd, name = webapps.build_command(cfg, app, home, **kw)
    if not cmd:
        return False, webapps.HELP_TEXT
    try:
        runner = popen or __import__("subprocess").Popen
        runner(cmd, stdin=__import__("subprocess").DEVNULL,
               stdout=__import__("subprocess").DEVNULL,
               stderr=__import__("subprocess").DEVNULL, start_new_session=True)
    except OSError as exc:
        return False, f"Could not open the browser: {exc}"
    return True, f"Opening {title} in {name}..."


def open_itch(cfg, query: str = "", home: Path | None = None, which=None, popen=None) -> tuple[bool, str]:
    """Open the official itch.io free-games page in the itch app if installed, otherwise the browser."""
    url = itch_url(query)
    app = apps.BY_ID["itch"]
    which = which or __import__("shutil").which
    cmd, how = apps.app_command(cfg, app, home, which)
    if how:
        try:
            runner = popen or __import__("subprocess").Popen
            runner(cmd, stdin=__import__("subprocess").DEVNULL,
                   stdout=__import__("subprocess").DEVNULL,
                   stderr=__import__("subprocess").DEVNULL, start_new_session=True)
            return True, "Opening the itch.io app..."
        except OSError:
            pass
    return open_url(cfg, url, "itch.io free games", home, which=which, popen=popen)


for d in DISTROS:
    assert allowed_url(d.url), d.url
