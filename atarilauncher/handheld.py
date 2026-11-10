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
CATEGORIES = (("steam", "Steam", "monitor"), ("pcgames", "PC Games", "c_pc"), ("store", "Games Store", "cart"), ("world", "World Games", "pad"), ("mygames", "My Games", "pad"), ("games", "Retro", "invader"), ("cloud", "Cloud", "c_cloud"), ("emulators", "Emulators", "invader"),
              ("streams", "Streams", "stream"), ("media", "Media", "film"), ("music", "Music", "music"),
              ("trilogies", "Trilogies", "pad"), ("coop", "Co-op", "pad"), ("downloads", "Downloads", "download"), ("apps", "All Apps", "grid"), ("accounts", "Accounts", "app"), ("settings", "Settings", "gear"))
# 0.9.4: PC games already installed on this machine (shown in My Games); retro ROMs, web pages and apps are elsewhere
PC_GAME_PLATFORMS = ("steam", "heroic", "lutris", "windows", "epic", "gog", "amazon")
SETTINGS_TILES = (("wifi", "Wi-Fi", "wifi"), ("controllers", "Controllers", "pad"), ("addgames", "Add games", "usb"),
                  ("effects", "Reduce effects", "sparkle"), ("rotate", "Rotate screen", "monitor"),
                  ("settings", "All settings", "gear"),
                  ("leave", "Classic home", "exit"), ("power", "Power", "power"), ("ask", "Ask CodeAque", "app"),
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


def _art_url(g) -> str:
    """Portrait cover first (box art / Steam's 600x900 library picture), then the wide header; '' = CodeAque's own tile art."""
    for p in (g.cover, g.header):
        if p and os.path.isfile(p):
            return QUrl.fromLocalFile(p).toString()
    return ""


def picture(gid: str) -> tuple[str, bool]:
    """(file URL, fit) from the user's own installed icons or official store art cached earlier; ('', True) = CodeAque's own icon."""
    from . import icons
    try:
        spec = icons.spec_for(gid)
        if spec is None:
            return "", True
        path, fit = icons.resolve(spec)
    except Exception:
        return "", True
    return (QUrl.fromLocalFile(path).toString() if path and os.path.isfile(path) else ""), fit


def _launch_tile(g, icon: str) -> dict:
    art, fit = _art_url(g), bool(getattr(g, "fit", False))
    if not art:
        art, fit = picture(g.gid)
    return {"key": g.gid, "title": g.title, "icon": icon, "kind": "launch", "gid": g.gid, "items": [],
            "art": art, "fit": fit, "hue": _hue(g.title)}


def _with_picture(tile: dict) -> dict:
    if tile.get("action", "").startswith("store:") and not tile.get("art"):
        tile["art"], tile["fit"] = picture(tile["action"])
    return tile


def _systems(games, store) -> list:
    by: dict = {}
    for g in games:
        if is_retro(g):
            by.setdefault(g.platform, (g.platform_name, []))[1].append(g)
    out = []
    for sid, (name, lst) in sorted(by.items(), key=lambda kv: kv[1][0].lower()):
        lst.sort(key=lambda g: g.title.lower())
        out.append({"key": sid, "title": name, "icon": kind_icon(sid), "kind": "list", "items": [item(g, store) for g in lst],
                    "hue": _hue(sid)})
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


GET_GAMES = {"key": "getgames", "title": "Get Games", "icon": "download", "kind": "action", "action": "getgames", "items": [],
             "hue": 0.53}
# 0.9.6 Accounts: each platform's own app, where you sign in yourself (CodeAque never sees a password)
ACCOUNTS = (("app:steam", "Steam"), ("app:heroic", "Epic · GOG · Amazon (Heroic)"), ("web:xcloud", "Xbox (Cloud Gaming)"),
            ("app:eaapp", "EA app"))


def store_tiles(games) -> list:
    """Games Store: Top Games, then Free (one tap installs: open-source games from Flathub, free-to-play on Steam), then Paid
    (the official stores; you pay the store, the game installs there and shows up in CodeAque). No prices in CodeAque."""
    from . import webapps
    def page(pid, title, icon, hue):
        return _with_picture({"key": "store:" + pid, "title": title, "icon": icon, "kind": "action", "action": "store:" + pid, "items": [], "hue": hue})
    tiles = [page("steamtop", "Top Games (Steam)", "star", 0.6)]
    free = [g for g in games if g.gid.startswith("free:") and "does not work" not in g.title.lower()]
    free.sort(key=lambda g: g.gid != "free:rivals")          # pinned: Marvel Rivals (free on Steam, installs through Steam)
    tiles += [dict(_launch_tile(g, "pad"), title="Free · " + g.title) for g in free]
    tiles += [page("steamfree", "Free on Steam", "star", 0.58), page("epicfree", "Free on Epic", "star", 0.1)]
    tiles += [page(p.id, "Paid · " + p.title, "cart", 0.3 + i * 0.1) for i, p in enumerate(webapps.STORE_PAGES) if p.id not in ("steamtop", "steamfree", "epicfree")]
    return tiles


def world_tiles() -> list:
    """PC World Games: publisher catalogues, English-language searches, and selected games, all on Steam's official store."""
    pages = webapps.WORLD_PAGES + webapps.SELECTED_WORLD_GAMES
    return [_with_picture({"key": "store:" + p.id, "title": p.title, "icon": "c_pc", "kind": "action", "action": "store:" + p.id,
             "items": [], "hue": 0.3 + i * 0.043}) for i, p in enumerate(pages)]


def account_tiles(games) -> list:
    by = {g.gid: g for g in games}
    return [{"key": gid, "title": title, "icon": "app", "kind": "launch", "gid": gid, "items": [], "hue": 0.55 + i * 0.07,
             "art": _art_url(by[gid])} for i, (gid, title) in enumerate(ACCOUNTS) if gid in by]


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
            out.append({"key": "emu:" + eid, "title": emu.name, "icon": "invader", "kind": "action", "action": "emu:" + eid, "items": [],
                        "hue": _hue(eid)})
    return out


def my_games(games, store=None, cfg=None, home=None) -> list:
    """My Games row: installed Steam / Lutris / Heroic / Windows games, then a tile per system with your own ROMs, then emulators."""
    pc = sorted((g for g in games if is_pc_game(g)), key=lambda g: (g.platform != "steam", g.title.lower()))   # Steam first
    tiles = [_launch_tile(g, "c_pc" if g.platform != "steam" else "steam") for g in pc]
    # 0.9.5: your ROM games that have a picture (libretro box art fetched once and cached, or your own) as their own cover tiles
    roms = sorted((g for g in games if is_retro(g) and _art_url(g)), key=lambda g: g.title.lower())[:60]
    tiles += [_launch_tile(g, kind_icon(g.platform)) for g in roms]
    return tiles + _systems(games, store) + installed_emulators(cfg, home)


def all_apps(games) -> list:
    """All Apps: every installed app tile (stores, launchers, media apps), A to Z."""
    lst = [g for g in games if g.gid.startswith(("app:", "tool:", "studio:")) and not g.missing]
    return [_launch_tile(g, "app") for g in sorted(lst, key=lambda g: g.title.lower())]



def trilogy_tiles() -> list:
    """Trilogies tab: Get-on-Steam store shortcuts only (official Steam pages; no game files, no third-party logos)."""
    from . import webapps
    out = []
    for i, p in enumerate(webapps.TRILOGY_PAGES):
        appid = webapps.TRILOGY_APPIDS[p.id]
        out.append({"key": f"steamstore:{appid}", "title": p.title, "icon": "c_pc", "kind": "action",
                    "action": f"steamstore:{appid}", "items": [], "hue": 0.08 + (i % 12) * 0.07,
                    "note": p.note or "", "steam_appid": appid})
    return out


def coop_tiles() -> list:
    """Co-op tab: Hazelight / EA co-op and popular online co-op RPGs (Steam store links only)."""
    from . import webapps
    out = []
    for i, p in enumerate(webapps.COOP_PAGES):
        appid = webapps.COOP_APPIDS[p.id]
        out.append({"key": f"steamstore:{appid}", "title": p.title, "icon": "c_pc", "kind": "action",
                    "action": f"steamstore:{appid}", "items": [], "hue": 0.55 + (i % 12) * 0.05,
                    "note": p.note or "", "steam_appid": appid})
    return out


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
        if key == "steam":      # 0.9.5: Steam at the top: Big Picture, then the Steam games installed here (with their pictures)
            st = sorted((g for g in games if is_pc_game(g) and g.platform == "steam"), key=lambda g: g.title.lower())
            tiles = [{"key": "app:steam", "title": "Steam Big Picture", "icon": "monitor", "kind": "launch", "gid": "app:steam",
                      "items": [], "hue": 0.6}] + [_launch_tile(g, "monitor") for g in st] + [GET_GAMES]
        elif key == "pcgames":   # Lutris, Heroic (Epic / GOG / Amazon) and Windows games installed here
            tiles = [_launch_tile(g, "c_pc") for g in sorted((g for g in games if is_pc_game(g) and g.platform != "steam"),
                                                           key=lambda g: g.title.lower())] + [GET_GAMES]
        elif key == "store":
            tiles = store_tiles(games)
        elif key == "world":
            tiles = world_tiles()
        elif key == "accounts":
            tiles = account_tiles(games)
        elif key == "mygames":
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
            from . import webapps as _wa
            iptv = [g for g in games if g.gid == "app:hypnotix"]
            tiles = [_launch_tile(g, "film") for g in media] + [_launch_tile(g, "live") for g in iptv] + [
                {"key": "store:" + w.id, "title": w.title, "icon": "film", "kind": "action", "action": "store:" + w.id, "items": [],
                 "hue": 0.05 + i * 0.13} for i, w in enumerate(_wa.FREE_TV)]
        elif key == "music":
            tiles = [_launch_tile(g, "music") for g in music]
        elif key == "trilogies":
            tiles = trilogy_tiles()
        elif key == "coop":
            tiles = coop_tiles()
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
