"""CodeAque Handheld Edition: a dedicated small-screen home for a handheld (tested layout: 1280x720).

Top row of categories (Games, Cloud, Emulators, Streams, Media, Music, Downloads, Settings); the chosen category's tiles in a
row at the bottom; a tile with games opens a list. PC game launchers (Steam, Lutris, Heroic, EA app, Rockstar, Windows games)
are left out on purpose. Only CodeAque's own drawings and plain text system names are used - no company logos. Nothing is
bundled or downloaded here (no games, BIOS files or keys)."""
from __future__ import annotations

import glob
import hashlib
import os
from pathlib import Path

from PySide6.QtCore import QUrl

from . import emulators, webapps

VIDEO_IDS = ("netflix", "youtube", "disney", "prime", "twitch")
MUSIC_IDS = ("spotify",)
CATEGORIES = (("mygames", "My Games", "pad"), ("games", "Retro", "invader"), ("cloud", "Cloud", "c_cloud"), ("emulators", "Emulators", "invader"),
              ("streams", "Streams", "stream"), ("media", "Media", "film"), ("music", "Music", "music"),
              ("downloads", "Downloads", "download"), ("apps", "All Apps", "grid"), ("settings", "Settings", "gear"))
# 0.9.4: PC games already installed on this machine (shown in My Games); retro ROMs, web pages and apps are elsewhere
PC_GAME_PLATFORMS = ("steam", "heroic", "lutris", "windows", "epic", "gog", "amazon")
SETTINGS_TILES = (("wifi", "Wi-Fi", "wifi"), ("controllers", "Controllers", "pad"), ("addgames", "Add games", "usb"),
                  ("effects", "Reduce effects", "sparkle"), ("settings", "All settings", "gear"),
                  ("leave", "Classic home", "exit"), ("power", "Power", "power"),
                  ("update", "Update CodeAque", "download"))


def _hue(title: str) -> float:
    return int(hashlib.sha1(title.encode()).hexdigest()[:4], 16) / 65535.0


def is_cloud(g) -> bool:
    return g.gid.startswith("web:") and g.platform == webapps.CLOUD


def is_retro(g) -> bool:
    return g.gid.startswith("rom:")


def item(g, store=None) -> dict:
    art = g.header or g.cover
    return {"gid": g.gid, "title": g.title, "sub": g.platform_name, "platform": g.platform,
            "art": QUrl.fromLocalFile(art).toString() if art else "",
            "cover": QUrl.fromLocalFile(g.cover).toString() if g.cover else "",
            "hue": _hue(g.title), "needsInstall": bool(g.missing), "note": g.note or "",
            "favorite": bool(store.is_favorite(g.gid)) if store is not None else False}


def _launch_tile(g, icon: str) -> dict:
    return {"key": g.gid, "title": g.title, "icon": icon, "kind": "launch", "gid": g.gid, "items": []}


def _systems(games, store) -> list:
    by: dict = {}
    for g in games:
        if is_retro(g):
            by.setdefault(g.platform, (g.platform_name, []))[1].append(g)
    out = []
    for sid, (name, lst) in sorted(by.items(), key=lambda kv: kv[1][0].lower()):
        lst.sort(key=lambda g: g.title.lower())
        out.append({"key": sid, "title": name, "icon": kind_icon(sid), "kind": "list", "items": [item(g, store) for g in lst]})
    return out


# an original generic picture for each kind of machine (never a maker's logo or a look-alike of one)
KIND = {"pad": ("atari2600", "atari5200", "atari7800", "atarijaguar", "nes", "snes", "n64", "genesis", "mastersystem", "pce", "3do"),
        "handheld": ("atarilynx", "gb", "gba", "nds", "n3ds", "gamegear", "psp", "wonderswan", "ngp", "vb", "switch"),
        "disc": ("gamecube", "wii", "wiiu", "segacd", "saturn", "dreamcast", "ps1", "ps2", "ps3", "xbox"),
        "arcade": ("neogeo", "arcade"),
        "computer": ("dos", "scummvm", "amiga", "c64", "msx", "zxspectrum", "amstradcpc")}
CART = ("atari2600", "atari5200", "atari7800", "nes", "snes", "n64", "genesis", "mastersystem")


def kind_icon(sid: str) -> str:
    if sid in CART:
        return "g_cart"
    for k, ids in KIND.items():
        if sid in ids:
            return "g_" + k
    return "g_pad"


def hub(games, store=None) -> list:
    """Every system CodeAque supports, in its usual order, each with the games found for it (most are empty at first)."""
    by: dict = {}
    for g in games:
        if is_retro(g):
            by.setdefault(g.platform, []).append(g)
    out = []
    for sd in emulators.SYSTEMS:
        lst = sorted(by.get(sd.id, []), key=lambda g: g.title.lower())
        short = sd.name.replace(" / Color", "").replace(" (MAME)", "")
        for pre in ("Nintendo ", "Sega "):
            if short.startswith(pre) and len(short) - len(pre) > 3:
                short = short[len(pre):]
        out.append({"key": sd.id, "title": short, "full": sd.name, "icon": kind_icon(sd.id), "kind": "list",
                    "items": [item(g, store) for g in lst]})
    return out


def is_pc_game(g) -> bool:
    return (not g.gid.startswith(("rom:", "web:", "app:", "tool:", "studio:"))) and not g.missing \
        and (g.platform in PC_GAME_PLATFORMS or g.gid.startswith(("steam:", "heroic:", "lutris:", "win:")))


def installed_emulators(cfg=None, home=None) -> list:
    """Emulators already on this machine (PATH, Flatpak or CodeAque's AppImage folder) as tiles that start them."""
    if cfg is None:
        return []
    out = []
    for eid, emu in emulators.EMUS.items():
        try:
            _cmd, how = emulators.find_emu(cfg, eid, home)
        except Exception:
            how = ""
        if how:
            out.append({"key": "emu:" + eid, "title": emu.name, "icon": "invader", "kind": "action", "action": "emu:" + eid, "items": []})
    return out


def my_games(games, store=None, cfg=None, home=None) -> list:
    """My Games row: installed Steam / Lutris / Heroic / Windows games, then a tile per system with your own ROMs, then emulators."""
    pc = sorted((g for g in games if is_pc_game(g)), key=lambda g: g.title.lower())
    tiles = [_launch_tile(g, "c_pc" if g.platform != "steam" else "steam") for g in pc]
    return tiles + _systems(games, store) + installed_emulators(cfg, home)


def all_apps(games) -> list:
    """All Apps: every installed app tile (stores, launchers, media apps), A to Z."""
    lst = [g for g in games if g.gid.startswith(("app:", "tool:", "studio:")) and not g.missing]
    return [_launch_tile(g, "app") for g in sorted(lst, key=lambda g: g.title.lower())]


def categories(games, store=None, cfg=None, home=None) -> list:
    games = list(games)
    cloud = sorted((g for g in games if is_cloud(g)), key=lambda g: [w.id for w in webapps.WEBAPPS].index(g.gid[4:])
                   if g.gid[4:] in [w.id for w in webapps.WEBAPPS] else 99)
    systems = _systems(games, store)
    cloud_tile = {"key": "cloudgaming", "title": "Cloud Gaming", "icon": "c_cloud", "kind": "list",
                  "items": [item(g, store) for g in cloud]}
    video = [g for g in games if g.gid.startswith("web:") and g.gid[4:] in VIDEO_IDS]
    music = [g for g in games if g.gid.startswith("web:") and g.gid[4:] in MUSIC_IDS]
    media = [g for g in games if g.gid in ("app:kodi", "tool:recordings")]
    out = []
    for key, title, icon in CATEGORIES:
        if key == "mygames":
            tiles = my_games(games, store, cfg, home) or [{"key": "addgames", "title": "Add games", "icon": "usb", "kind": "action",
                                                           "action": "addgames", "items": []}]
        elif key == "apps":
            tiles = all_apps(games)
        elif key == "games":
            tiles = [cloud_tile] + systems
        elif key == "cloud":
            tiles = [_launch_tile(g, "c_cloud") for g in cloud]
        elif key == "emulators":
            tiles = hub(games, store)
        elif key == "streams":
            tiles = [_launch_tile(g, "stream") for g in video]
        elif key == "media":
            tiles = [_launch_tile(g, "film") for g in media]
        elif key == "music":
            tiles = [_launch_tile(g, "music") for g in music]
        elif key == "downloads":
            tiles = [{"key": "downloads", "title": "Downloads", "icon": "download", "kind": "action", "action": "downloads", "items": []}]
        else:
            tiles = [{"key": k, "title": t, "icon": i, "kind": "action", "action": k, "items": []} for k, t, i in SETTINGS_TILES]
        out.append({"key": key, "title": title, "icon": icon, "tiles": tiles})
    return out


def battery(root: str = "/sys/class/power_supply") -> dict:
    """The device's own battery: {"present": bool, "percent": int, "charging": bool}."""
    for d in sorted(glob.glob(os.path.join(root, "BAT*"))):
        try:
            pct = int(Path(d, "capacity").read_text().strip())
            st = Path(d, "status").read_text().strip().lower() if Path(d, "status").is_file() else ""
            return {"present": True, "percent": max(0, min(100, pct)), "charging": st in ("charging", "full")}
        except (OSError, ValueError):
            continue
    return {"present": False, "percent": -1, "charging": False}


def online(root: str = "/sys/class/net") -> bool:
    """True when a network link (not loopback) is up."""
    for d in glob.glob(os.path.join(root, "*")):
        name = os.path.basename(d)
        if name == "lo" or name.startswith(("docker", "veth", "virbr")):
            continue
        try:
            if Path(d, "operstate").read_text().strip() == "up":
                return True
        except OSError:
            continue
    return False
