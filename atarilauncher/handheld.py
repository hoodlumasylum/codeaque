"""Handheld mode: a dedicated small-screen home with only two sections, Cloud Gaming and Retro.

No PC game launchers here (Steam, Lutris, Heroic, EA app, Rockstar, Windows games are left out). Cloud Gaming = the cloud
service tiles plus the film / video streaming tiles that already exist in CodeAque. Retro = the games found for the emulators.
Nothing is bundled or downloaded by this module."""
from __future__ import annotations

from PySide6.QtCore import QUrl

from . import webapps

# the film / video tiles that make sense on a handheld (no social apps, no "go live" or studio pages)
VIDEO_IDS = ("netflix", "youtube", "disney", "prime", "twitch")
SECTIONS = (("cloud", "Cloud Gaming"), ("retro", "Retro"))


def _hue(title: str) -> float:
    import hashlib
    return int(hashlib.sha1(title.encode()).hexdigest()[:4], 16) / 65535.0


def is_cloud(g) -> bool:
    if not g.gid.startswith("web:"):
        return False
    wid = g.gid[4:]
    return g.platform == webapps.CLOUD or wid in VIDEO_IDS


def is_retro(g) -> bool:
    return g.gid.startswith("rom:")


def item(g, store=None) -> dict:
    art = g.header or g.cover
    return {"gid": g.gid, "title": g.title, "sub": g.platform_name, "platform": g.platform,
            "art": QUrl.fromLocalFile(art).toString() if art else "",
            "cover": QUrl.fromLocalFile(g.cover).toString() if g.cover else "",
            "hue": _hue(g.title), "needsInstall": bool(g.missing), "note": g.note or "",
            "favorite": bool(store.is_favorite(g.gid)) if store is not None else False}


def sections(games, store=None) -> list:
    cloud = [g for g in games if is_cloud(g)]
    order = {wid: i for i, wid in enumerate([w.id for w in webapps.WEBAPPS])}
    cloud.sort(key=lambda g: (0 if g.platform == webapps.CLOUD else 1, order.get(g.gid[4:], 99)))
    retro = sorted((g for g in games if is_retro(g)), key=lambda g: (g.platform_name.lower(), g.title.lower()))
    out = []
    for key, title in SECTIONS:
        lst = cloud if key == "cloud" else retro
        out.append({"key": key, "title": title, "items": [item(g, store) for g in lst]})
    return out
