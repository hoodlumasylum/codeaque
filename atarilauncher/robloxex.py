"""Popular Roblox experiences: official place IDs and public thumbnail URLs.

Place IDs were checked against https://www.roblox.com/games/<id>/ on 10 Nov 2026.
Covers are fetched at runtime from Roblox's public thumbnail API and cached on the device.
Nothing official is bundled. Roblox needs its own account and parental controls.
On Linux, Roblox runs via Sober or cloud only.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

from . import covers, paths

THUMB_API = ("https://thumbnails.roblox.com/v1/places/gameicons"
             "?placeIds={id}&returnPolicy=PlaceHolder&size=512x512&format=Png&isCircular=false")
GAME_URL = "https://www.roblox.com/games/{id}/"
DEEP_LINK = "roblox://placeId={id}"
# Only these hosts are accepted for cached pictures.
THUMB_HOSTS = ("tr.rbxcdn.com", "thumbnails.roblox.com")

NOTE = "Needs a Roblox account and parental controls. On Linux, Roblox runs via Sober or cloud only."


@dataclass(frozen=True)
class Experience:
    id: str            # stable CodeAque id
    title: str
    place_id: int      # official Roblox place id


# Verified via the official /games/<placeId>/ page title and canonical URL (Nov 2026).
EXPERIENCES: list[Experience] = [
    Experience("rbxbrook", "Brookhaven", 4924922222),
    Experience("rbxadopt", "Adopt Me!", 920587237),
    Experience("rbxbloxfruits", "Blox Fruits", 2753915549),
    Experience("rbxgarden", "Grow a Garden", 126884695634066),
    Experience("rbxmm2", "Murder Mystery 2", 142823291),
    Experience("rbxtower", "Tower of Hell", 1962086868),
    Experience("rbxnds", "Natural Disaster Survival", 189707),
    Experience("rbxpiggy", "Piggy", 4623386862),
    Experience("rbxdoors", "Doors", 6516141723),
    Experience("rbxjail", "Jailbreak", 606849621),
    Experience("rbxarsenal", "Arsenal", 286090429),
    Experience("rbxbee", "Bee Swarm Simulator", 1537690962),
]
BY_PLACE = {e.place_id: e for e in EXPERIENCES}
BY_ID = {e.id: e for e in EXPERIENCES}


def game_url(place_id: int | str) -> str:
    return GAME_URL.format(id="".join(c for c in str(place_id) if c.isdigit()))


def deep_link(place_id: int | str) -> str:
    return DEEP_LINK.format(id="".join(c for c in str(place_id) if c.isdigit()))


def cache_path(place_id: int | str, root: Path | None = None) -> Path:
    return (root or (paths.data_dir() / "icons")) / f"roblox-{place_id}.png"


def parse_thumb_url(body: bytes | str) -> str:
    """Pick the official thumbnail URL from the public API JSON, or ''."""
    try:
        data = json.loads(body if isinstance(body, str) else body.decode("utf-8", "replace"))
    except (ValueError, UnicodeDecodeError):
        return ""
    rows = data.get("data") if isinstance(data, dict) else None
    if not isinstance(rows, list) or not rows:
        return ""
    url = str(rows[0].get("imageUrl") or "")
    return url if _allowed(url) else ""


def _allowed(url: str) -> bool:
    if not url.startswith("https://"):
        return False
    host = re.sub(r"^https://", "", url).split("/", 1)[0].lower()
    return host in THUMB_HOSTS or host.endswith(".rbxcdn.com")


def find_cached(place_id: int | str, root: Path | None = None) -> str:
    p = cache_path(place_id, root)
    return str(p) if p.is_file() else ""


def fetch_one(place_id: int | str, http: covers.Http | None = None, root: Path | None = None) -> str:
    """Download and cache the official game icon. Returns the file path or ''."""
    pid = "".join(c for c in str(place_id) if c.isdigit())
    if not pid:
        return ""
    dest = cache_path(pid, root)
    if dest.is_file():
        return str(dest)
    http = http or covers.Http()
    meta = http.get(THUMB_API.format(id=pid), max_bytes=200_000)
    url = parse_thumb_url(meta or b"")
    if not url:
        return ""
    data = http.get(url, max_bytes=2_000_000)
    if not data or len(data) < 200:
        return ""
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    tmp.write_bytes(data)
    tmp.replace(dest)
    return str(dest)
