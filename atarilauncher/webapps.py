"""Streaming & Social: web apps (YouTube, Netflix, Instagram, ...) shown as tiles and started in a full-screen "app" window
of a browser on this PC, each with its own private profile folder so you sign in once per app.

CodeAque uses no company logos: the tiles are plain text/colour tiles. Nothing is downloaded or bundled here.
Order of browsers: Google Chrome, Chromium, Brave, ungoogled-Chromium (as `--app=URL --start-fullscreen`), then Firefox
(`--kiosk`). Netflix, Prime Video and Disney+ need Widevine DRM: Google Chrome has it built in, Firefox downloads it after you
accept its prompt; on Linux those services stream at about 720p (Widevine "L3"), never 4K/HDR.
"""
from __future__ import annotations

import shlex
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from . import donate, emulators, paths

STREAMING = "streaming"
STREAMING_NAME = "Streaming & Social"
CLOUD = "cloud"
CLOUD_NAME = "Cloud Gaming"


@dataclass(frozen=True)
class WebApp:
    id: str
    title: str
    url: str
    drm: bool = False
    note: str = ""
    background: bool = False    # keeps running behind CodeAque (music): Esc twice brings CodeAque back and leaves it playing
    browse: bool = False        # a normal browser window (address bar, back / forward) instead of a full-screen app window
    mic: bool = False           # records with the microphone: the browser is told to allow it (no pop-up a controller cannot answer)
    studio: bool = False        # listed in the Studio area (next to the built-in Studio) instead of Streaming & Social
    cloud: bool = False         # a cloud gaming service: listed on the Cloud Gaming tile

    @property
    def gid(self) -> str:
        return f"web:{self.id}"


DRM_NOTE = ("Needs Widevine DRM (Google Chrome has it; in Firefox accept its \"Play DRM content\" prompt). "
            "On Linux this streams at about 720p, not 4K.")
WEBAPPS: list[WebApp] = [
    WebApp("browser", "Web Browser", "https://duckduckgo.com/", browse=True,
           note="A normal browser window with an address bar and back / forward buttons, starting at a neutral search engine. "
                "Press Esc twice (or hold Guide + B on a controller) to return to CodeAque. Files you download land in ~/Downloads - "
                "Options > Downloads moves them into your games. CodeAque does not recommend or link any download site."),
    WebApp("twitchlive", "Twitch (go live)", "https://dashboard.twitch.tv/stream-manager", browse=True,
           note="Twitch's Creator Dashboard in a browser window: your stream key is under Settings > Stream. Pair it with OBS (Options > Go Live)."),
    WebApp("tiktoklive", "TikTok LIVE (go live)", "https://www.tiktok.com/live", browse=True,
           note="TikTok's web LIVE page in a browser window. TikTok LIVE Studio is Windows-only, so on Linux you stream with OBS and "
                "the server URL + stream key TikTok shows here (only for accounts that qualify)."),
    WebApp("youtubelive", "YouTube Live (go live)", "https://studio.youtube.com", browse=True,
           note="YouTube Studio in a browser window: Create > Go live > Stream shows your stream key. Pair it with OBS (Options > Go Live)."),
    WebApp("spotify", "Spotify (web player)", "https://open.spotify.com", True, background=True,
           note="Spotify's web player in its own window (sign in once; needs a Spotify account). Music needs Widevine: Google Chrome has it "
                "built in. It keeps playing behind CodeAque - press Esc twice (or Guide + B) to come back and use the music buttons in the "
                "top bar (they need playerctl)."),
    WebApp("bandlab", "BandLab (music studio)", "https://www.bandlab.com/studio", mic=True, studio=True,
           note="BandLab is a free music studio in the browser (record, loops, mixing; no Linux app). It opens in its own window and "
                "remembers your login. The microphone is allowed for recording: pick it in BandLab's own audio settings. Sign in once; "
                "a mouse helps. Press Esc twice (or Guide + B) to come back to CodeAque."),
    WebApp("youtube", "YouTube", "https://www.youtube.com/tv",
           note="The TV layout of YouTube - big buttons, works with arrow keys and Enter. Sign in once."),
    WebApp("netflix", "Netflix", "https://www.netflix.com", True, DRM_NOTE),
    WebApp("disney", "Disney+", "https://www.disneyplus.com", True, DRM_NOTE),
    WebApp("prime", "Prime Video", "https://www.primevideo.com", True, DRM_NOTE),
    WebApp("twitch", "Twitch", "https://www.twitch.tv", note="Live streams. A mouse or touch screen helps."),
    WebApp("instagram", "Instagram", "https://www.instagram.com", note="Made for phones: a mouse or touch screen helps."),
    WebApp("facebook", "Facebook", "https://www.facebook.com", note="A mouse or touch screen helps."),
    WebApp("tiktok", "TikTok", "https://www.tiktok.com", note="A mouse or touch screen helps."),
]
# Cloud gaming: the services on Samsung's TV Gaming Hub that have a web player (checked October 2026; Utomik closed in January
# 2025, so it is not here). Each opens full screen in its own browser profile; controllers work through the browser.
CLOUD_NOTE = (" You need your own account (and the service's own subscription where it has one). Games stream from the internet: "
              "a wired connection or 5 GHz Wi-Fi works best. Press Esc twice (or hold Guide + B) to come back to CodeAque.")
WEBAPPS += [
    WebApp("xcloud", "Xbox Cloud Gaming", "https://www.xbox.com/play", cloud=True,
           note="Xbox Cloud Gaming in the browser (needs Game Pass Ultimate or a game you own that supports cloud play)." + CLOUD_NOTE),
    WebApp("geforcenow", "GeForce NOW", "https://play.geforcenow.com", cloud=True,
           note="NVIDIA's cloud PC: plays games you own on Steam, Epic, Ubisoft and others, on NVIDIA's computers. Free tier with "
                "waiting times; games with anti-cheat run there too, because they run on NVIDIA's PCs." + CLOUD_NOTE),
    WebApp("luna", "Amazon Luna", "https://luna.amazon.com", cloud=True,
           note="Amazon's cloud gaming. Some games come with Prime; more with Luna+." + CLOUD_NOTE),
    WebApp("boosteroid", "Boosteroid", "https://cloud.boosteroid.com", cloud=True,
           note="Independent cloud gaming: plays games you own on Steam, Epic and other stores (subscription)." + CLOUD_NOTE),
    WebApp("antstream", "Antstream Arcade", "https://vision.antstream.com", cloud=True,
           note="Over a thousand classic arcade and home-computer games, streamed. Its web player is in open beta and made for Chrome."
                + CLOUD_NOTE),
    WebApp("blacknut", "Blacknut", "https://app.blacknut.com", cloud=True,
           note="Family cloud gaming with hundreds of games in one subscription (Chrome works best)." + CLOUD_NOTE),
]
# The optional "Support CodeAque" donate page: a browser window opened by a button in Settings / Options. It is NOT a tile (not in WEBAPPS).
SUPPORT = WebApp("support", donate.TITLE, donate.DONATE_URL, browse=True, note=donate.NOTE)
# 0.9.6 Games Store pages (official stores only; prices stay on the stores' own pages). Opened by the Games Store tab only.
STORE_PAGES: list[WebApp] = [
    WebApp("steamtop", "Top Games (Steam)", "https://store.steampowered.com/charts/topselling/global", browse=True),
    WebApp("steamfree", "Free on Steam", "https://store.steampowered.com/genre/Free%20to%20Play/", browse=True),
    WebApp("epicfree", "Free on Epic", "https://store.epicgames.com/free-games", browse=True),
    WebApp("steamstore", "Steam Store", "https://store.steampowered.com/", browse=True),
    WebApp("gogstore", "GOG", "https://www.gog.com/en/games", browse=True),
    WebApp("epicstore", "Epic Games Store", "https://store.epicgames.com/", browse=True),
    WebApp("humble", "Humble Store", "https://www.humblebundle.com/store", browse=True),
]
WORLD_PAGES: list[WebApp] = [
    WebApp("wgjapan", "Japanese Games", "https://store.steampowered.com/search/?developer=KADOKAWA%20CORPORATION", browse=True),
    WebApp("wgchina", "Chinese Games", "https://store.steampowered.com/search/?developer=Hypergryph", browse=True),
    WebApp("wgchina2", "More Chinese Games", "https://store.steampowered.com/search/?developer=24%20Entertainment", browse=True),
    WebApp("wgkorea", "Korean Games", "https://store.steampowered.com/search/?developer=NEOWIZ", browse=True),
    WebApp("wgkorea2", "More Korean Games", "https://store.steampowered.com/search/?developer=NEXON%20Korea%20Corporation", browse=True),
    WebApp("wgjapanen", "Japanese · English support", "https://store.steampowered.com/search/?supported_lang=english&developer=KADOKAWA%20CORPORATION", browse=True,
           note="Steam's English-language filter. The game's own Steam page lists English subtitles and voice audio separately."),
    WebApp("wgchinaen", "Chinese · English support", "https://store.steampowered.com/search/?supported_lang=english&developer=Hypergryph", browse=True,
           note="Steam's English-language filter. The game's own Steam page lists English subtitles and voice audio separately."),
    WebApp("wgchinaen2", "More Chinese · English support", "https://store.steampowered.com/search/?supported_lang=english&developer=24%20Entertainment", browse=True,
           note="Steam's English-language filter. The game's own Steam page lists English subtitles and voice audio separately."),
    WebApp("wgkoreaen", "Korean · English support", "https://store.steampowered.com/search/?supported_lang=english&developer=NEOWIZ", browse=True,
           note="Steam's English-language filter. The game's own Steam page lists English subtitles and voice audio separately."),
    WebApp("wgkoreaen2", "More Korean · English support", "https://store.steampowered.com/search/?supported_lang=english&developer=NEXON%20Korea%20Corporation", browse=True,
           note="Steam's English-language filter. The game's own Steam page lists English subtitles and voice audio separately."),
]
SELECTED_WORLD_GAMES: list[WebApp] = [
    WebApp("wgyume", "Japan · Yume Nikki", "https://store.steampowered.com/app/650700/Yume_Nikki/", browse=True),
    WebApp("wgblackmyth", "China · Black Myth: Wukong", "https://store.steampowered.com/app/2358720/Black_Myth_Wukong/", browse=True),
    WebApp("wgsword", "China · Wandering Sword", "https://store.steampowered.com/app/1876890/Wandering_Sword/", browse=True),
    WebApp("wgdave", "Korea · DAVE THE DIVER", "https://store.steampowered.com/app/1868140/DAVE_THE_DIVER/", browse=True),
    WebApp("wgeternal", "Korea · Eternal Return", "https://store.steampowered.com/app/1049590/Eternal_Return/", browse=True),
]

# 0.10.31 Trilogies tab: story series Steam store links only (no game files, no third-party art).
# Co-op games live in COOP_PAGES / the Co-op tab. openSteamStore prefers steam://store/<id> when Steam is installed.
STRONG_PC_NOTE = "Needs a strong PC, try cloud gaming."
EA_APP_NOTE = "May need the EA app alongside Steam."
TRILOGY_PAGES: list[WebApp] = [
    # Uncharted
    WebApp("truncharted", "Uncharted: Legacy of Thieves", "https://store.steampowered.com/app/1659420/", browse=True, note=STRONG_PC_NOTE),
    # Tomb Raider
    WebApp("trtri", "Tomb Raider I-III Remastered", "https://store.steampowered.com/app/2478970/", browse=True),
    WebApp("trivvi", "Tomb Raider IV-VI Remastered", "https://store.steampowered.com/app/2525380/", browse=True),
    WebApp("trlegend", "Tomb Raider: Legend", "https://store.steampowered.com/app/7000/", browse=True),
    WebApp("tranniv", "Tomb Raider: Anniversary", "https://store.steampowered.com/app/8000/", browse=True),
    WebApp("trunder", "Tomb Raider: Underworld", "https://store.steampowered.com/app/8140/", browse=True),
    WebApp("tr2013", "Tomb Raider (2013)", "https://store.steampowered.com/app/203160/", browse=True),
    WebApp("trrise", "Rise of the Tomb Raider", "https://store.steampowered.com/app/391220/", browse=True),
    WebApp("trshadow", "Shadow of the Tomb Raider", "https://store.steampowered.com/app/750920/", browse=True),
    WebApp("trlara_gol", "Lara Croft and the Guardian of Light", "https://store.steampowered.com/app/35130/", browse=True),
    WebApp("trlara_osiris", "Lara Croft and the Temple of Osiris", "https://store.steampowered.com/app/289690/", browse=True),
    WebApp("trlarago", "Lara Croft GO", "https://store.steampowered.com/app/540840/", browse=True),
    # Grand Theft Auto
    WebApp("trgta3", "Grand Theft Auto III", "https://store.steampowered.com/app/12100/", browse=True),
    WebApp("trgta_vc", "Grand Theft Auto: Vice City", "https://store.steampowered.com/app/12110/", browse=True),
    WebApp("trgta_sa", "Grand Theft Auto: San Andreas", "https://store.steampowered.com/app/12120/", browse=True),
    WebApp("trgta3de", "GTA III – The Definitive Edition", "https://store.steampowered.com/app/1546970/", browse=True),
    WebApp("trgtavcde", "GTA Vice City – The Definitive Edition", "https://store.steampowered.com/app/1546990/", browse=True),
    WebApp("trgtasade", "GTA San Andreas – The Definitive Edition", "https://store.steampowered.com/app/1547000/", browse=True),
    WebApp("trgta4", "Grand Theft Auto IV: Complete Edition", "https://store.steampowered.com/app/12210/", browse=True),
    WebApp("trgta_eflc", "GTA: Episodes from Liberty City", "https://store.steampowered.com/app/12220/", browse=True),
    WebApp("trgtav", "Grand Theft Auto V Legacy", "https://store.steampowered.com/app/271590/", browse=True, note=STRONG_PC_NOTE),
    WebApp("trgtave", "Grand Theft Auto V Enhanced", "https://store.steampowered.com/app/3240220/", browse=True, note=STRONG_PC_NOTE),
    # Assassin's Creed (main games on Steam)
    WebApp("trac1", "Assassin's Creed", "https://store.steampowered.com/app/15100/", browse=True),
    WebApp("trac2", "Assassin's Creed II", "https://store.steampowered.com/app/33230/", browse=True),
    WebApp("tracbros", "Assassin's Creed Brotherhood", "https://store.steampowered.com/app/48190/", browse=True),
    WebApp("tracrevel", "Assassin's Creed Revelations", "https://store.steampowered.com/app/201870/", browse=True),
    WebApp("trac3", "Assassin's Creed III", "https://store.steampowered.com/app/208480/", browse=True),
    WebApp("trac3r", "Assassin's Creed III Remastered", "https://store.steampowered.com/app/911400/", browse=True),
    WebApp("trac4", "Assassin's Creed IV Black Flag", "https://store.steampowered.com/app/242050/", browse=True),
    WebApp("tracrogue", "Assassin's Creed Rogue", "https://store.steampowered.com/app/311560/", browse=True),
    WebApp("tracunity", "Assassin's Creed Unity", "https://store.steampowered.com/app/289650/", browse=True),
    WebApp("tracsynd", "Assassin's Creed Syndicate", "https://store.steampowered.com/app/368500/", browse=True),
    WebApp("tracorigins", "Assassin's Creed Origins", "https://store.steampowered.com/app/582160/", browse=True),
    WebApp("tracodyssey", "Assassin's Creed Odyssey", "https://store.steampowered.com/app/812140/", browse=True),
    WebApp("tracvalhalla", "Assassin's Creed Valhalla", "https://store.steampowered.com/app/2208920/", browse=True),
    WebApp("tracmirage", "Assassin's Creed Mirage", "https://store.steampowered.com/app/3035570/", browse=True),
    WebApp("tracshadows", "Assassin's Creed Shadows", "https://store.steampowered.com/app/3159330/", browse=True),
    # Mystery & Puzzles — The Talos Principle
    WebApp("trtalos", "The Talos Principle", "https://store.steampowered.com/app/257510/", browse=True),
    WebApp("trtalos2", "The Talos Principle 2", "https://store.steampowered.com/app/835960/", browse=True),
    WebApp("trtalosrw", "The Talos Principle: Reawakened", "https://store.steampowered.com/app/2806640/", browse=True),
    # Life is Strange
    WebApp("trlis", "Life is Strange (Episode 1)", "https://store.steampowered.com/app/319630/", browse=True),
    WebApp("trlisrem", "Life is Strange Remastered", "https://store.steampowered.com/app/1265920/", browse=True),
    WebApp("trlisbts", "Life is Strange: Before the Storm", "https://store.steampowered.com/app/554620/", browse=True),
    WebApp("trlisbtsr", "Life is Strange: Before the Storm Remastered", "https://store.steampowered.com/app/1265930/", browse=True),
    WebApp("trlis2", "Life is Strange 2", "https://store.steampowered.com/app/532210/", browse=True),
    WebApp("trliscs", "The Awesome Adventures of Captain Spirit", "https://store.steampowered.com/app/845070/", browse=True),
    WebApp("trlistc", "Life is Strange: True Colors", "https://store.steampowered.com/app/936790/", browse=True),
    WebApp("trlisde", "Life is Strange: Double Exposure", "https://store.steampowered.com/app/1874000/", browse=True),
    WebApp("trlisreun", "Life is Strange: Reunion", "https://store.steampowered.com/app/2624870/", browse=True),
]
# app id for each Trilogies page (kept next to the list so tests can check both)
TRILOGY_APPIDS: dict[str, int] = {
    "truncharted": 1659420, "trtri": 2478970, "trivvi": 2525380, "trlegend": 7000, "tranniv": 8000,
    "trunder": 8140, "tr2013": 203160, "trrise": 391220, "trshadow": 750920, "trlara_gol": 35130,
    "trlara_osiris": 289690, "trlarago": 540840, "trgta3": 12100, "trgta_vc": 12110, "trgta_sa": 12120,
    "trgta3de": 1546970, "trgtavcde": 1546990, "trgtasade": 1547000, "trgta4": 12210, "trgta_eflc": 12220,
    "trgtav": 271590, "trgtave": 3240220, "trac1": 15100, "trac2": 33230, "tracbros": 48190,
    "tracrevel": 201870, "trac3": 208480, "trac3r": 911400, "trac4": 242050, "tracrogue": 311560,
    "tracunity": 289650, "tracsynd": 368500, "tracorigins": 582160, "tracodyssey": 812140,
    "tracvalhalla": 2208920, "tracmirage": 3035570, "tracshadows": 3159330,
    "trtalos": 257510, "trtalos2": 835960, "trtalosrw": 2806640,
    "trlis": 319630, "trlisrem": 1265920, "trlisbts": 554620, "trlisbtsr": 1265930, "trlis2": 532210,
    "trliscs": 845070, "trlistc": 936790, "trlisde": 1874000, "trlisreun": 2624870,
}

# 0.10.31 Co-op tab: Hazelight / EA co-op + popular online co-op RPGs (Steam store links only).
# Diablo III is not on Steam, so it is left out. Diablo II: Resurrected is listed as the Infernal Edition page on Steam.
COOP_PAGES: list[WebApp] = [
    # Story co-op (may need the EA app)
    WebApp("coop_awayout", "A Way Out", "https://store.steampowered.com/app/1222700/", browse=True, note=EA_APP_NOTE),
    WebApp("coop_itt", "It Takes Two", "https://store.steampowered.com/app/1426210/", browse=True, note=EA_APP_NOTE),
    WebApp("coop_split", "Split Fiction", "https://store.steampowered.com/app/2001120/", browse=True, note=EA_APP_NOTE),
    # RPG co-op
    WebApp("coop_dos2", "Divinity: Original Sin 2", "https://store.steampowered.com/app/435150/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_bg3", "Baldur's Gate 3", "https://store.steampowered.com/app/1086940/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_d4", "Diablo IV", "https://store.steampowered.com/app/2344520/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_d2r", "Diablo II: Resurrected", "https://store.steampowered.com/app/2536520/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_bl2", "Borderlands 2", "https://store.steampowered.com/app/49520/", browse=True),
    WebApp("coop_bl3", "Borderlands 3", "https://store.steampowered.com/app/397540/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_grim", "Grim Dawn", "https://store.steampowered.com/app/219990/", browse=True),
    WebApp("coop_tl2", "Torchlight II", "https://store.steampowered.com/app/200710/", browse=True),
    WebApp("coop_ds3", "Dark Souls III", "https://store.steampowered.com/app/374320/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_elden", "Elden Ring", "https://store.steampowered.com/app/1245620/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_mhw", "Monster Hunter: World", "https://store.steampowered.com/app/582010/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_poe", "Path of Exile", "https://store.steampowered.com/app/238960/", browse=True),
    WebApp("coop_poe2", "Path of Exile 2", "https://store.steampowered.com/app/2694490/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_rem2", "Remnant II", "https://store.steampowered.com/app/1282100/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_tina", "Tiny Tina's Wonderlands", "https://store.steampowered.com/app/1286680/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_dd2", "Dragon's Dogma 2", "https://store.steampowered.com/app/2054970/", browse=True, note=STRONG_PC_NOTE),
    WebApp("coop_stardew", "Stardew Valley", "https://store.steampowered.com/app/413150/", browse=True),
    WebApp("coop_terraria", "Terraria", "https://store.steampowered.com/app/105600/", browse=True),
    WebApp("coop_valheim", "Valheim", "https://store.steampowered.com/app/892970/", browse=True),
]
COOP_APPIDS: dict[str, int] = {
    "coop_awayout": 1222700, "coop_itt": 1426210, "coop_split": 2001120,
    "coop_dos2": 435150, "coop_bg3": 1086940, "coop_d4": 2344520, "coop_d2r": 2536520,
    "coop_bl2": 49520, "coop_bl3": 397540, "coop_grim": 219990, "coop_tl2": 200710,
    "coop_ds3": 374320, "coop_elden": 1245620, "coop_mhw": 582010, "coop_poe": 238960,
    "coop_poe2": 2694490, "coop_rem2": 1282100, "coop_tina": 1286680, "coop_dd2": 2054970,
    "coop_stardew": 413150, "coop_terraria": 105600, "coop_valheim": 892970,
}

BY_ID = {w.id: w for w in WEBAPPS}
BY_ID[SUPPORT.id] = SUPPORT
UNREAL = WebApp("unrealpage", "Unreal Engine for Linux (Epic's page)", "https://www.unrealengine.com/en-US/linux", browse=True,
                note="Epic's official download page. Sign in there with your own free Epic account.")
BY_ID[UNREAL.id] = UNREAL
XEMU_HDD = WebApp("xemuhdd", "xemu: Required Files (free ready-made hard disk)", "https://xemu.app/docs/required-files/", browse=True,
                  note="xemu's own page. Its ready-made 8 GB disk has no Microsoft files in it; download it there yourself.")
BY_ID[XEMU_HDD.id] = XEMU_HDD
# 0.9.6 Media > Free Movies & TV: legal free (ad-supported) services, their official websites only.
FREE_TV: list[WebApp] = [
    WebApp("plutotv", "Pluto TV", "https://pluto.tv/", True),
    WebApp("tubi", "Tubi", "https://tubitv.com/", True),
    WebApp("plexfree", "Plex (free movies & TV)", "https://watch.plex.tv/", True),
    WebApp("freevee", "Freevee", "https://www.amazon.co.uk/gp/video/storefront?contentId=freetv", True, note="Amazon Freevee; free with ads. UK storefront."),
    WebApp("iplayer", "BBC iPlayer", "https://www.bbc.co.uk/iplayer", True, note="UK only; needs a TV licence for live TV."),
    WebApp("itvx", "ITVX", "https://www.itv.com/", True, note="UK only."),
    WebApp("channel4", "Channel 4", "https://www.channel4.com/", True, note="UK only."),
]
for _sp in STORE_PAGES + WORLD_PAGES + SELECTED_WORLD_GAMES + TRILOGY_PAGES + COOP_PAGES + FREE_TV:
    BY_ID[_sp.id] = _sp


@dataclass(frozen=True)
class Browser:
    id: str
    name: str
    exes: tuple
    flatpak: str
    kind: str             # chromium | firefox
    drm: bool             # plays Netflix & co. without extra steps


BROWSERS: list[Browser] = [
    Browser("chrome", "Google Chrome", ("google-chrome-stable", "google-chrome"), "com.google.Chrome", "chromium", True),
    Browser("chromium", "Chromium", ("chromium", "chromium-browser"), "org.chromium.Chromium", "chromium", False),
    Browser("brave", "Brave", ("brave-browser", "brave"), "com.brave.Browser", "chromium", True),
    Browser("ungoogled", "ungoogled-Chromium", (), "io.github.ungoogled_software.ungoogled_chromium", "chromium", False),
    Browser("firefox", "Firefox", ("firefox",), "org.mozilla.firefox", "firefox", True),
]
HELP_TEXT = ("Streaming apps open in a web browser window. Install one with Settings > Emulators > Install emulators and apps "
             "(it adds Google Chrome), or install Chrome / Chromium / Firefox yourself.")


def profile_root() -> Path:
    return paths.data_dir() / "webapps"


def find_browser(cfg=None, home: Path | None = None, which: Callable = shutil.which,
                 drm: bool = False) -> tuple[Browser | None, str]:
    """(browser, how) with how = 'native' | 'flatpak'. Browsers that handle DRM come first for DRM apps."""
    order = sorted(BROWSERS, key=lambda b: (not b.drm,) if drm else (0,))   # stable: keeps the table order otherwise
    for b in order:
        for exe in b.exes:
            if which(exe):
                return b, "native"
        if emulators.flatpak_installed(b.flatpak, home):
            return b, "flatpak"
    return None, ""


def build_command(cfg, app: WebApp, home: Path | None = None, which: Callable = shutil.which,
                  root: Path | None = None) -> tuple[list[str], str]:
    """(command, browser name). A command in config ([emulators] browser = "...") replaces the browser:
    CodeAque adds --app=<url> --start-fullscreen --user-data-dir=<profile> to it. Returns ([], '') when no browser exists."""
    root = root or profile_root()
    prof = root / app.id
    custom = (cfg.emulator_commands.get("browser") or "").strip() if cfg is not None else ""
    if custom:
        return shlex.split(custom) + (browse_flags(app, prof) if app.browse else chromium_flags(app, prof)), "your browser"
    b, how = find_browser(cfg, home, which, app.drm)
    if b is None:
        return [], ""
    if how == "flatpak":
        base = ["flatpak", "run", f"--filesystem={root}", b.flatpak]
    else:
        base = [next(e for e in b.exes if which(e))]
    if app.browse:
        flags = (browse_flags(app, prof) if b.kind == "chromium" else ["--no-remote", "--profile", str(prof), app.url])
    else:
        flags = chromium_flags(app, prof) if b.kind == "chromium" else [
            "--no-remote", "--profile", str(prof), "--kiosk", app.url]
    return base + flags, b.name


def browse_flags(app: WebApp, prof: Path) -> list[str]:
    """A normal window (toolbar visible), maximised, with its own profile so it never touches your everyday browser."""
    return ["--new-window", "--start-maximized", f"--user-data-dir={prof}", "--no-first-run", "--no-default-browser-check", app.url]


MIC_FLAG = "--use-fake-ui-for-media-stream"       # Chromium: allow microphone / camera requests without the pop-up
FIREFOX_MIC_PREFS = 'user_pref("media.navigator.permission.disabled", true);\n'


def chromium_flags(app: WebApp, prof: Path) -> list[str]:
    return [f"--app={app.url}", "--start-fullscreen", f"--user-data-dir={prof}", "--no-first-run",
            "--no-default-browser-check"] + ([MIC_FLAG] if app.mic else [])


def prepare_profile(app: WebApp, prof: Path) -> None:
    """Firefox reads user.js from the profile: allow the microphone for apps that record. (Chromium gets a flag instead.)"""
    if not app.mic:
        return
    try:
        prof.mkdir(parents=True, exist_ok=True)
        f = prof / "user.js"
        if not f.exists() or FIREFOX_MIC_PREFS not in f.read_text(errors="replace"):
            with open(f, "a", encoding="utf-8") as fh:
                fh.write(FIREFOX_MIC_PREFS)
    except OSError:
        pass


def status(cfg, app: WebApp, home: Path | None = None, which: Callable = shutil.which) -> tuple[bool, str]:
    cmd, name = build_command(cfg, app, home, which)
    if not cmd:
        return False, HELP_TEXT
    b, _ = find_browser(cfg, home, which, app.drm)
    extra = ""
    if app.drm and b is not None and not b.drm:
        extra = f" {b.name} may not play DRM video (Netflix etc.) - Google Chrome does."
    return True, f"Opens in {name}." + extra
