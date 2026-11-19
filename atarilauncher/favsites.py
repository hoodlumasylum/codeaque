"""Fav Sites: bookmarks the user saves while browsing from DuckDuckGo.

CodeAque launches an external browser (not an in-app page), so a star on the
web page itself is not possible. Bookmarks are added from the Fav Sites row
(type or paste an address, or save the last site opened in the DuckDuckGo
window). Favicons are fetched once and cached locally; a generic globe is used
when none is available. No third-party logos are bundled.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import sqlite3
import tempfile
import urllib.error
import urllib.request
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlparse, urlunparse

from . import paths, webapps

SCHEMA = 1
MAX_SITES = 80
TIMEOUT = 8
DDG_HOME = "https://duckduckgo.com/"
USER_AGENT = "CodeAque/1.0 (Fav Sites favicon fetch)"
ICON_CT = ("image/png", "image/x-icon", "image/vnd.microsoft.icon", "image/svg+xml",
           "image/jpeg", "image/webp", "image/gif", "application/octet-stream")
SKIP_HOSTS = {"duckduckgo.com", "www.duckduckgo.com", "start.duckduckgo.com"}
BAD_SCHEMES = {"javascript", "data", "file", "about", "chrome", "chrome-extension", "blob", "mailto"}


@dataclass
class Site:
    id: str
    title: str
    url: str
    icon: str = ""          # local path, or empty for the globe fallback

    def as_dict(self) -> dict:
        return {"id": self.id, "title": self.title, "url": self.url, "icon": self.icon}


def store_dir(root: Path | None = None) -> Path:
    return (root or paths.data_dir()) / "favsites"


def store_path(root: Path | None = None) -> Path:
    return store_dir(root) / "sites.json"


def icons_dir(root: Path | None = None) -> Path:
    return store_dir(root) / "icons"


def hidden_for_kids(kids: bool) -> bool:
    """Kids mode hides the whole Fav Sites row (no bypass)."""
    return bool(kids)


def normalize_url(raw: str) -> str | None:
    """Return a safe http(s) URL, or None if it cannot be used."""
    text = (raw or "").strip()
    if not text:
        return None
    if " " in text and "://" not in text and not text.startswith("www."):
        # a search phrase, not an address
        return None
    if "://" not in text:
        text = "https://" + text
    try:
        p = urlparse(text)
    except ValueError:
        return None
    scheme = (p.scheme or "").lower()
    if scheme in BAD_SCHEMES or scheme not in ("http", "https"):
        return None
    host = (p.hostname or "").strip().rstrip(".")
    if not host or host in ("localhost",) or "." not in host:
        return None
    path = p.path or "/"
    return urlunparse((scheme, p.netloc, path, "", p.query, ""))


def host_of(url: str) -> str:
    try:
        return (urlparse(url).hostname or "").lower().removeprefix("www.")
    except ValueError:
        return ""


def default_title(url: str) -> str:
    h = host_of(url)
    return h or "Site"


def site_id(url: str) -> str:
    n = normalize_url(url) or url
    h = host_of(n) or "site"
    slug = re.sub(r"[^a-z0-9]+", "-", h).strip("-")[:40] or "site"
    digest = hashlib.sha1(n.encode("utf-8")).hexdigest()[:8]
    return f"{slug}-{digest}"


def is_skip_url(url: str) -> bool:
    """DuckDuckGo's own home / search pages are not useful last-site offers."""
    try:
        p = urlparse(url)
    except ValueError:
        return True
    host = (p.hostname or "").lower()
    if host in SKIP_HOSTS or host.endswith(".duckduckgo.com"):
        return True
    return False


def _read(root: Path | None = None) -> list[Site]:
    path = store_path(root)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    rows = data.get("sites") if isinstance(data, dict) else data
    out: list[Site] = []
    if not isinstance(rows, list):
        return []
    for row in rows:
        if not isinstance(row, dict):
            continue
        url = normalize_url(str(row.get("url") or ""))
        if not url:
            continue
        sid = str(row.get("id") or site_id(url))
        title = str(row.get("title") or default_title(url)).strip() or default_title(url)
        icon = str(row.get("icon") or "")
        if icon and not Path(icon).is_file():
            icon = ""
        out.append(Site(sid, title, url, icon))
    return out


def _write(sites: list[Site], root: Path | None = None) -> None:
    d = store_dir(root)
    d.mkdir(parents=True, exist_ok=True)
    path = store_path(root)
    tmp = path.with_suffix(".tmp")
    payload = {"schema": SCHEMA, "sites": [s.as_dict() for s in sites]}
    tmp.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    tmp.replace(path)


def list_sites(root: Path | None = None) -> list[Site]:
    return _read(root)


def get(sid: str, root: Path | None = None) -> Site | None:
    for s in _read(root):
        if s.id == sid:
            return s
    return None


def add(url: str, title: str = "", root: Path | None = None, fetch: bool = True) -> Site | None:
    """Add (or update) a fav site. Returns the site, or None if the address is not usable."""
    url = normalize_url(url)
    if not url:
        return None
    sites = _read(root)
    sid = site_id(url)
    name = (title or "").strip() or default_title(url)
    existing = next((s for s in sites if s.id == sid or s.url == url), None)
    if existing:
        existing.title = name or existing.title
        existing.url = url
        site = existing
    else:
        if len(sites) >= MAX_SITES:
            sites.pop(0)
        site = Site(sid, name, url, "")
        sites.append(site)
    if fetch and not site.icon:
        path = fetch_favicon(url, icons_dir(root))
        if path:
            site.icon = str(path)
    _write(sites, root)
    return site


def remove(sid: str, root: Path | None = None) -> bool:
    sites = _read(root)
    kept = [s for s in sites if s.id != sid]
    if len(kept) == len(sites):
        return False
    _write(kept, root)
    return True


# ----- favicon --------------------------------------------------------------
class _IconParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.hrefs: list[str] = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() != "link":
            return
        d = {k.lower(): (v or "") for k, v in attrs}
        rel = d.get("rel", "").lower()
        href = d.get("href", "")
        if href and ("icon" in rel or rel in ("shortcut icon", "apple-touch-icon")):
            self.hrefs.append(href)


def _open(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    return urllib.request.urlopen(req, timeout=TIMEOUT)  # noqa: S310  - http(s) only, checked by caller


def _download(url: str, dest: Path) -> Path | None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        with _open(url) as resp:
            ct = (resp.headers.get("Content-Type") or "").split(";", 1)[0].strip().lower()
            data = resp.read(256_000)
    except (urllib.error.URLError, TimeoutError, ValueError, OSError):
        return None
    if not data or len(data) < 16:
        return None
    if ct and ct.startswith("text/"):
        return None
    if ct and ct not in ICON_CT and not ct.startswith("image/"):
        return None
    ext = ".ico"
    if "png" in ct or data[:8] == b"\x89PNG\r\n\x1a\n":
        ext = ".png"
    elif "svg" in ct or data.lstrip()[:4] == b"<svg" or data.lstrip()[:5] == b"<?xml":
        ext = ".svg"
    elif "jpeg" in ct or "jpg" in ct or data[:2] == b"\xff\xd8":
        ext = ".jpg"
    elif "webp" in ct:
        ext = ".webp"
    path = dest.with_suffix(ext)
    try:
        path.write_bytes(data)
    except OSError:
        return None
    return path


def _page_icon_hrefs(page_url: str) -> list[str]:
    try:
        with _open(page_url) as resp:
            raw = resp.read(200_000)
            base = resp.geturl() or page_url
    except (urllib.error.URLError, TimeoutError, ValueError, OSError):
        return []
    try:
        html = raw.decode("utf-8", "replace")
    except Exception:
        return []
    p = _IconParser()
    try:
        p.feed(html)
        p.close()
    except Exception:
        return []
    out = []
    for href in p.hrefs:
        try:
            out.append(urljoin(base, href))
        except ValueError:
            continue
    return out


def fetch_favicon(url: str, dest_dir: Path, opener=None) -> Path | None:
    """Download the site's favicon once. Returns the cache path, or None (use the globe)."""
    url = normalize_url(url)
    if not url:
        return None
    host = host_of(url)
    if not host:
        return None
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / (host + ".ico")  # base name; _download replaces the suffix safely
    existing = next((p for p in dest_dir.iterdir() if p.is_file() and p.stat().st_size > 0
                     and (p.stem == host or p.name.startswith(host + "."))), None) if dest_dir.is_dir() else None
    if existing:
        return existing
    p = urlparse(url)
    origin = f"{p.scheme}://{p.netloc}"
    candidates = [origin + "/favicon.ico"] + _page_icon_hrefs(url) + [
        f"https://icons.duckduckgo.com/ip3/{host}.ico",
    ]
    # unique, keep order
    seen = set()
    for href in candidates:
        if href in seen:
            continue
        seen.add(href)
        if (urlparse(href).scheme or "") not in ("http", "https"):
            continue
        got = _download(href, dest)
        if got:
            return got
    return None


# ----- last site visited in the DuckDuckGo / browser profile -----------------
def last_visited_urls(profile: Path, limit: int = 20) -> list[tuple[str, str]]:
    """[(url, title), ...] newest first, from a Chromium or Firefox profile folder."""
    if not profile:
        return []
    rows = _chromium_history(profile, limit) or _firefox_history(profile, limit)
    out = []
    for url, title in rows:
        n = normalize_url(url)
        if not n or is_skip_url(n):
            continue
        out.append((n, (title or "").strip()))
        if len(out) >= limit:
            break
    return out


def last_site(profile: Path | None = None) -> tuple[str, str] | None:
    """The most recent useful page from the DuckDuckGo (browser) profile, if any."""
    prof = profile if profile is not None else (webapps.profile_root() / "browser")
    rows = last_visited_urls(prof, 20)
    return rows[0] if rows else None


def _copy_query(src: Path, sql: str) -> list[tuple]:
    if not src.is_file():
        return []
    tmp = None
    try:
        fd, name = tempfile.mkstemp(prefix="favhist-")
        os.close(fd)
        tmp = Path(name)
        shutil.copy2(src, tmp)
        con = sqlite3.connect(f"file:{tmp}?mode=ro", uri=True)
        try:
            return list(con.execute(sql))
        finally:
            con.close()
    except (OSError, sqlite3.Error):
        return []
    finally:
        if tmp is not None:
            try:
                tmp.unlink()
            except OSError:
                pass


def _chromium_history(profile: Path, limit: int) -> list[tuple[str, str]]:
    for rel in ("Default/History", "History"):
        rows = _copy_query(profile / rel,
                           "SELECT url, IFNULL(title, '') FROM urls ORDER BY last_visit_time DESC LIMIT %d" % (limit * 3))
        if rows:
            return [(str(u), str(t)) for u, t in rows]
    return []


def _firefox_history(profile: Path, limit: int) -> list[tuple[str, str]]:
    # Firefox may nest the real profile one level down
    candidates = [profile / "places.sqlite"]
    try:
        candidates += [p / "places.sqlite" for p in profile.iterdir() if p.is_dir()]
    except OSError:
        pass
    for src in candidates:
        rows = _copy_query(src,
                           "SELECT url, IFNULL(title, '') FROM moz_places WHERE url IS NOT NULL "
                           "ORDER BY COALESCE(last_visit_date, 0) DESC LIMIT %d" % (limit * 3))
        if rows:
            return [(str(u), str(t)) for u, t in rows]
    return []


# ------------------------------------------------------------------ launch dispatcher (console home + classic library)
def resolve_tile(action: str = "", gid: str = "") -> str | None:
    """Map a Fav Sites row tile (action and/or gid) to a launch id the PC dispatcher understands.

    Returns 'duckduckgo', 'addfav', 'fav:<id>', 'web:browser', or None if it is not a Fav Sites tile.
    """
    a = (action or "").strip()
    g = (gid or "").strip()
    for raw in (a, g):
        if raw in ("duckduckgo", "web:browser", "browser"):
            return "duckduckgo"
        if raw == "addfav":
            return "addfav"
        if raw.startswith("favopen:"):
            sid = raw.split(":", 1)[1].strip()
            return ("fav:" + sid) if sid else None
        if raw.startswith("fav:") and raw.split(":", 1)[1].strip():
            return "fav:" + raw.split(":", 1)[1].strip()
    return None
