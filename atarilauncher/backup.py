"""Backup and restore CodeAque setup and game saves (not the games themselves).

A backup is one file, ``CodeAque-Backup-YYYY-MM-DD.tar.gz``, usually written to a USB stick.
It holds CodeAque settings, tiles, Fav Sites, covers, emulator saves and save states, Proton
save folders for games that do not use Steam Cloud, and a list of installed apps so the other
computer can reinstall them. Games, ROMs, BIOS, firmware, Steam libraries, passwords, tokens
and browser logins are left out. Steam games have to be downloaded again; Steam Cloud already
keeps many Steam saves.
"""
from __future__ import annotations

import io
import json
import os
import re
import shutil
import tarfile
import tempfile
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from . import appinstall, paths, usbdrive
from . import __version__

FORMAT = 1
ARCHIVE_PREFIX = "CodeAque-Backup"
SAFETY_PREFIX = "CodeAque-Before-Restore"
MANIFEST_NAME = "manifest.json"
README_NAME = "README.txt"

# Proton prefixes only: the Windows-user folders where unsynced saves usually live.
PROTON_USER_DIRS = ("Documents", "AppData", "Saved Games")

# Caches and shader dumps inside those folders (not saves).
CACHE_DIR_NAMES = {
    "cache", "caches", "shadercache", "shadercache2", "dxvk_cache", "gpucache",
    "code cache", "gpu cache", "shader cache", "temp", "tmp", "logs", "log",
    "crashdumps", "crashes", "telemetry", "codex",
}

# Never packed: BIOS/firmware, game files, secrets.
SKIP_DIR_NAMES = {
    "bios", "system", "firmware", "roms", "rom", "iso", "isos", "games", "game",
    "content", "cores", "downloads", "shader_caches", "runtime", "models",
    ".ssh", "cookies", "browser", "chromium", "chrome", "firefox", "brave",
}
SKIP_DIR_NAMES_CI = {n.lower() for n in SKIP_DIR_NAMES}

SECRET_FILE_RE = re.compile(
    r"(?i)("
    r"steamgriddb|"
    r"password|passwd|"
    r"secret|"
    r"credential|"
    r"cookie|"
    r"(^|[^a-z])token([^a-z]|$)|"
    r"wallet|"
    r"id_rsa|"
    r"\.pem$|"
    r"\.key$|"
    r"loginusers|"
    r"ssfn"
    r")"
)

BIOS_FILE_RE = re.compile(
    r"(?i)("
    r"scph.*\.bin$|ps[12].*\.bin$|"
    r"mcpx.*\.bin$|xbox_hdd|eeprom\.bin$|"
    r"bios|\.qcow2$|"
    r"dc_boot|dc_flash|erom|bootrom"
    r")"
)

# Text files that may contain an absolute home path and should be rewritten on restore.
REWRITE_SUFFIXES = {
    ".cfg", ".ini", ".toml", ".json", ".conf", ".xml", ".txt", ".vdf", ".yml", ".yaml",
}

ProgressFn = Callable[[float, str, str], None]  # fraction 0-1 or -1, title, detail


@dataclass
class Source:
    """One folder or file that may go into the archive."""
    kind: str
    label: str
    src: Path
    arc: str  # path inside the archive, relative, posix


@dataclass
class Plan:
    sources: list[Source]
    apps: list[dict]
    bytes: int = 0
    files: int = 0
    skipped: list[str] = field(default_factory=list)


@dataclass
class Result:
    ok: bool
    message: str
    path: str = ""
    size: int = 0
    files: int = 0
    apps: list[dict] = field(default_factory=list)


def human_size(n: int) -> str:
    """A short size for the screen, e.g. 12 MB."""
    n = max(0, int(n or 0))
    for unit, div in (("GB", 1024 ** 3), ("MB", 1024 ** 2), ("KB", 1024)):
        if n >= div or unit == "KB":
            val = n / div
            return f"{val:.1f} {unit}" if val < 10 or unit == "GB" else f"{int(round(val))} {unit}"
    return "0 KB"


def backup_name(when: datetime | None = None, existing: list[str] | None = None) -> str:
    """``CodeAque-Backup-YYYY-MM-DD.tar.gz``, or with -HHMM if that day's file is already there."""
    when = when or datetime.now()
    day = when.strftime("%Y-%m-%d")
    name = f"{ARCHIVE_PREFIX}-{day}.tar.gz"
    names = set(existing or [])
    if name not in names:
        return name
    return f"{ARCHIVE_PREFIX}-{day}-{when.strftime('%H%M')}.tar.gz"


def is_backup_name(name: str) -> bool:
    base = name.lower()
    return base.startswith(ARCHIVE_PREFIX.lower() + "-") and base.endswith(".tar.gz")


def find_drives(lister=None) -> list:
    """Mounted USB sticks / removable drives (same list as Make a CodeAque USB)."""
    try:
        return list((lister or usbdrive.drives)())
    except Exception:
        return []


def pick_drive(drives: list | None = None, need: int = 0) -> tuple[object | None, str]:
    """The best USB stick to write to, or a reason why none is ready."""
    ds = list(drives if drives is not None else find_drives())
    if not ds:
        return None, "No USB stick found. Plug one in and try again, or save to Downloads."
    usable = []
    for d in ds:
        mp = Path(getattr(d, "path", "") or "")
        if not mp.is_dir() or not os.access(mp, os.W_OK):
            continue
        if need:
            try:
                if shutil.disk_usage(mp).free < need:
                    continue
            except OSError:
                continue
        usable.append(d)
    if not usable:
        if need:
            return None, f"No USB stick has {human_size(need)} free. Free some space, or use a bigger stick."
        return None, "The USB stick cannot be written to. Is it locked, or not mounted?"
    if len(usable) == 1:
        return usable[0], ""
    return None, "Several USB sticks are plugged in. Pick one."


def default_downloads(home: Path | None = None) -> Path:
    h = home or Path.home()
    d = h / "Downloads"
    return d if d.is_dir() else h


# ------------------------------------------------------------------ what is included
def _flatpak_home(home: Path, app_id: str) -> Path:
    return home / ".var" / "app" / app_id


def _emu_sources(home: Path) -> list[Source]:
    """Emulator save folders CodeAque knows about (native, Flatpak and Snap). BIOS folders are not listed."""
    h = home
    items: list[tuple[str, str, Path]] = [
        ("emu-ra-saves", "RetroArch saves", h / ".config/retroarch/saves"),
        ("emu-ra-states", "RetroArch save states", h / ".config/retroarch/states"),
        ("emu-ra-cfg", "RetroArch settings", h / ".config/retroarch/retroarch.cfg"),
        ("emu-ra-config", "RetroArch core settings", h / ".config/retroarch/config"),
        ("emu-ra-remap", "RetroArch remaps", h / ".config/retroarch/remaps"),
        ("emu-ds-mem", "DuckStation memory cards", h / ".local/share/duckstation/memcards"),
        ("emu-ds-states", "DuckStation save states", h / ".local/share/duckstation/savestates"),
        ("emu-ds-cfg", "DuckStation settings", h / ".config/duckstation/settings.ini"),
        ("emu-pcsx2-mem", "PCSX2 memory cards", h / ".config/PCSX2/memcards"),
        ("emu-pcsx2-states", "PCSX2 save states", h / ".config/PCSX2/sstates"),
        ("emu-pcsx2-inis", "PCSX2 settings", h / ".config/PCSX2/inis"),
        ("emu-ppsspp-save", "PPSSPP saves", h / ".config/ppsspp/PSP/SAVEDATA"),
        ("emu-ppsspp-state", "PPSSPP save states", h / ".config/ppsspp/PSP/PPSSPP_STATE"),
        ("emu-ppsspp-sys", "PPSSPP settings", h / ".config/ppsspp/PSP/SYSTEM"),
        ("emu-dolphin-gc", "Dolphin GameCube saves", h / ".local/share/dolphin-emu/GC"),
        ("emu-dolphin-states", "Dolphin save states", h / ".local/share/dolphin-emu/StateSaves"),
        ("emu-dolphin-wii", "Dolphin Wii saves", h / ".local/share/dolphin-emu/Wii/title"),
        ("emu-dolphin-shared", "Dolphin Wii shared saves", h / ".local/share/dolphin-emu/Wii/shared2"),
        ("emu-dolphin-cfg", "Dolphin settings", h / ".config/dolphin-emu"),
        ("emu-flycast-save", "Flycast saves", h / ".local/share/flycast"),
        ("emu-flycast-cfg", "Flycast settings", h / ".config/flycast"),
        ("emu-melonds-save", "melonDS saves", h / ".local/share/melonDS"),
        ("emu-melonds-cfg", "melonDS settings", h / ".config/melonDS"),
        ("emu-mgba-save", "mGBA saves", h / ".config/mgba"),
        ("emu-mednafen-save", "Mednafen saves", h / ".mednafen"),
        ("emu-rpcs3-save", "RPCS3 saves", h / ".config/rpcs3/dev_hdd0/home"),
        ("emu-rpcs3-cfg", "RPCS3 settings", h / ".config/rpcs3/config.yml"),
        ("emu-ryujinx-save", "Ryujinx saves", h / ".config/Ryujinx/bis"),
        ("emu-ryujinx-cfg", "Ryujinx settings", h / ".config/Ryujinx/Config.json"),
        ("emu-cemu-save", "Cemu saves", h / ".local/share/Cemu/mlc01/usr/save"),
        ("emu-mame-save", "MAME saves", h / ".mame/sta"),
        ("emu-mame-nvram", "MAME NVRAM", h / ".mame/nvram"),
        ("emu-dosbox-save", "DOSBox saves", h / ".config/dosbox"),
    ]
    # Flatpak mirrors (same relative folders under ~/.var/app/<id>/).
    fp = [
        ("org.libretro.RetroArch", "config/retroarch/saves", "emu-ra-fp-saves", "RetroArch saves (Flatpak)"),
        ("org.libretro.RetroArch", "config/retroarch/states", "emu-ra-fp-states", "RetroArch save states (Flatpak)"),
        ("org.libretro.RetroArch", "config/retroarch/retroarch.cfg", "emu-ra-fp-cfg", "RetroArch settings (Flatpak)"),
        ("org.libretro.RetroArch", "config/retroarch/config", "emu-ra-fp-config", "RetroArch core settings (Flatpak)"),
        ("org.libretro.RetroArch", "config/retroarch/remaps", "emu-ra-fp-remap", "RetroArch remaps (Flatpak)"),
        ("org.duckstation.DuckStation", "data/duckstation/memcards", "emu-ds-fp-mem", "DuckStation memory cards (Flatpak)"),
        ("org.duckstation.DuckStation", "data/duckstation/savestates", "emu-ds-fp-states", "DuckStation save states (Flatpak)"),
        ("org.duckstation.DuckStation", "config/duckstation/settings.ini", "emu-ds-fp-cfg", "DuckStation settings (Flatpak)"),
        ("net.pcsx2.PCSX2", "config/PCSX2/memcards", "emu-pcsx2-fp-mem", "PCSX2 memory cards (Flatpak)"),
        ("net.pcsx2.PCSX2", "config/PCSX2/sstates", "emu-pcsx2-fp-states", "PCSX2 save states (Flatpak)"),
        ("net.pcsx2.PCSX2", "config/PCSX2/inis", "emu-pcsx2-fp-inis", "PCSX2 settings (Flatpak)"),
        ("org.ppsspp.PPSSPP", "config/ppsspp/PSP/SAVEDATA", "emu-ppsspp-fp-save", "PPSSPP saves (Flatpak)"),
        ("org.ppsspp.PPSSPP", "config/ppsspp/PSP/PPSSPP_STATE", "emu-ppsspp-fp-state", "PPSSPP save states (Flatpak)"),
        ("org.DolphinEmu.dolphin-emu", "data/dolphin-emu/GC", "emu-dolphin-fp-gc", "Dolphin GameCube saves (Flatpak)"),
        ("org.DolphinEmu.dolphin-emu", "data/dolphin-emu/StateSaves", "emu-dolphin-fp-states", "Dolphin save states (Flatpak)"),
        ("org.DolphinEmu.dolphin-emu", "data/dolphin-emu/Wii/title", "emu-dolphin-fp-wii", "Dolphin Wii saves (Flatpak)"),
        ("org.DolphinEmu.dolphin-emu", "data/dolphin-emu/Wii/shared2", "emu-dolphin-fp-shared", "Dolphin Wii shared saves (Flatpak)"),
        ("org.DolphinEmu.dolphin-emu", "config/dolphin-emu", "emu-dolphin-fp-cfg", "Dolphin settings (Flatpak)"),
        ("org.flycast.Flycast", "data/flycast", "emu-flycast-fp-save", "Flycast saves (Flatpak)"),
        ("net.kuribo64.melonDS", "data/melonDS", "emu-melonds-fp-save", "melonDS saves (Flatpak)"),
        ("io.mgba.mGBA", "config/mgba", "emu-mgba-fp-save", "mGBA saves (Flatpak)"),
        ("net.rpcs3.RPCS3", "config/rpcs3/dev_hdd0/home", "emu-rpcs3-fp-save", "RPCS3 saves (Flatpak)"),
        ("org.ryujinx.Ryujinx", "config/Ryujinx/bis", "emu-ryujinx-fp-save", "Ryujinx saves (Flatpak)"),
        ("info.cemu.Cemu", "data/Cemu/mlc01/usr/save", "emu-cemu-fp-save", "Cemu saves (Flatpak)"),
    ]
    for app_id, rel, kind, label in fp:
        items.append((kind, label, _flatpak_home(h, app_id) / rel))
    # Snap RetroArch
    items.append(("emu-ra-snap-saves", "RetroArch saves (Snap)", h / "snap/retroarch/current/.config/retroarch/saves"))
    items.append(("emu-ra-snap-states", "RetroArch save states (Snap)", h / "snap/retroarch/current/.config/retroarch/states"))

    out: list[Source] = []
    for kind, label, p in items:
        if p.exists():
            out.append(Source(kind, label, p, "emu/" + kind + ("/" + p.name if p.is_file() else "")))
    return out


def _steam_library_paths(home: Path, extra_libraries=()) -> list[Path]:
    from . import steam as steam_mod
    paths_out: list[Path] = []
    root = steam_mod.find_steam_root(home)
    if root:
        paths_out.append(root)
        for vdf_path in (root / "steamapps" / "libraryfolders.vdf", root / "config" / "libraryfolders.vdf"):
            if vdf_path.is_file():
                try:
                    paths_out += [Path(p) for p in steam_mod.parse_libraryfolders(vdf_path)]
                except Exception:
                    pass
    paths_out += [Path(p).expanduser() for p in (extra_libraries or ())]
    seen: set[str] = set()
    uniq: list[Path] = []
    for p in paths_out:
        try:
            key = os.path.realpath(p)
        except OSError:
            key = str(p)
        if key not in seen:
            seen.add(key)
            uniq.append(p)
    return uniq


def _steam_save_sources(home: Path, extra_libraries=()) -> list[Source]:
    """Proton compatdata user folders only — never the game files in steamapps/common."""
    out: list[Source] = []
    for lib in _steam_library_paths(home, extra_libraries):
        compat = lib / "steamapps" / "compatdata"
        if not compat.is_dir():
            continue
        try:
            appids = sorted(compat.iterdir(), key=lambda p: p.name)
        except OSError:
            continue
        for app_dir in appids:
            if not app_dir.is_dir() or not app_dir.name.isdigit():
                continue
            user = app_dir / "pfx" / "drive_c" / "users" / "steamuser"
            for sub in PROTON_USER_DIRS:
                p = user / sub
                if p.exists():
                    arc = f"steam/compatdata/{app_dir.name}/{sub}"
                    out.append(Source("steam-save", f"Steam save ({app_dir.name} / {sub})", p, arc))
    return out


def _codeaque_sources(home: Path, config_dir: Path | None = None, data_dir: Path | None = None) -> list[Source]:
    cfg = config_dir or paths.config_dir()
    data = data_dir or paths.data_dir()
    out: list[Source] = []
    if cfg.exists():
        out.append(Source("codeaque-config", "CodeAque settings", cfg, "codeaque/config"))
    if data.exists():
        out.append(Source("codeaque-data", "CodeAque tiles, covers, Fav Sites, Ask CodeAque", data, "codeaque/data"))
    # Fav Sites (current or upcoming folder names) if they ever live outside the data dir.
    for label, p, arc in (
        ("Fav Sites", cfg / "favsites", "codeaque/favsites-config"),
        ("Fav Sites", data / "favsites", "codeaque/favsites"),
        ("Fav Sites", data / "fav_sites", "codeaque/fav_sites"),
        ("Fav icons", data / "favicons", "codeaque/favicons"),
        ("Nova data", home / ".config" / "nova", "nova/config"),
        ("Nova data", home / ".local" / "share" / "nova", "nova/data"),
        ("Nova data", data / "nova", "codeaque/nova"),
    ):
        if p.exists() and not _already(out, p):
            out.append(Source("fav-or-nova", label, p, arc))
    return out


def _already(sources: list[Source], path: Path) -> bool:
    try:
        real = os.path.realpath(path)
    except OSError:
        real = str(path)
    for s in sources:
        try:
            if os.path.realpath(s.src) == real:
                return True
            if real.startswith(os.path.realpath(s.src) + os.sep):
                return True
        except OSError:
            continue
    return False


def skip_reason(path: Path, root: Path | None = None) -> str:
    """Why this file or folder is left out, or '' if it may be packed."""
    name = path.name
    low = name.lower()
    if path.is_symlink():
        # Keep the link only when it stays inside the tree being packed.
        if root is not None:
            try:
                dest = path.resolve()
                base = root.resolve()
                if dest == base or str(dest).startswith(str(base) + os.sep):
                    return ""
            except OSError:
                return "symlink"
        return "symlink"
    if SECRET_FILE_RE.search(name):
        return "secret"
    if BIOS_FILE_RE.search(name):
        return "bios"
    if low in SKIP_DIR_NAMES_CI or low in CACHE_DIR_NAMES:
        return "excluded-dir"
    # Big re-downloadable Ask CodeAque / Ollama files inside the data dir.
    parts = {p.lower() for p in path.parts}
    if "local-ai" in parts and low in {"runtime", "models", "ollama.tar.zst", "ollama-unpack.tar"}:
        return "local-ai-runtime"
    if low.endswith((".iso", ".rvz", ".wbfs", ".chd", ".cso", ".gcm", ".n64", ".z64", ".nes", ".sfc", ".smc", ".gba", ".nds", ".3ds", ".nsp", ".xci", ".vso", ".wad")):
        return "rom"
    return ""


def should_include(path: Path, root: Path | None = None) -> bool:
    return not skip_reason(path, root)


def _iter_files(root: Path) -> list[Path]:
    """Files under root (or [root] if it is a file), honouring skip rules. Symlinks out are skipped."""
    if root.is_file():
        return [root] if should_include(root, root.parent) else []
    if not root.is_dir():
        return []
    out: list[Path] = []
    try:
        for dirpath, dirnames, filenames in os.walk(root, followlinks=False):
            dp = Path(dirpath)
            # Prune skipped directories in-place.
            keep: list[str] = []
            for d in dirnames:
                p = dp / d
                if should_include(p, root):
                    keep.append(d)
            dirnames[:] = keep
            for fn in filenames:
                p = dp / fn
                if p.is_file() and not p.is_symlink() and should_include(p, root):
                    out.append(p)
                elif p.is_symlink() and should_include(p, root) and p.exists():
                    out.append(p)
    except OSError:
        pass
    return out


def _file_size(path: Path) -> int:
    try:
        return path.stat().st_size if not path.is_symlink() else 0
    except OSError:
        return 0


def installed_apps(home: Path | None = None, cfg=None, which=None) -> list[dict]:
    """Programs CodeAque knows about that are installed, plus user Flatpaks it may not know."""
    which = which or shutil.which
    home = home or Path.home()
    out: list[dict] = []
    seen: set[str] = set()
    for tid, target in appinstall.TARGETS.items():
        try:
            ok = appinstall.is_installed(target, home=home, which=which, cfg=cfg)
        except Exception:
            ok = False
        if ok:
            rec = {"id": tid, "title": target.title, "flatpak": target.flatpak, "apt": target.apt}
            out.append(rec)
            seen.add(tid)
            if target.flatpak:
                seen.add(target.flatpak)
    # Extra user Flatpaks (best effort; restore can offer fp:<id>).
    if which("flatpak"):
        try:
            p = __import__("subprocess").run(
                ["flatpak", "list", "--app", "--columns=application,name"],
                capture_output=True, text=True, timeout=20, stdin=__import__("subprocess").DEVNULL,
            )
            if p.returncode == 0:
                for line in (p.stdout or "").splitlines():
                    parts = line.split("\t", 1)
                    if not parts or not parts[0].strip():
                        continue
                    fid = parts[0].strip()
                    if fid in seen:
                        continue
                    title = parts[1].strip() if len(parts) > 1 else fid
                    out.append({"id": "fp:" + fid, "title": title, "flatpak": fid, "apt": ""})
                    seen.add(fid)
        except Exception:
            pass
    return out


def build_plan(home: Path | None = None, config_dir: Path | None = None, data_dir: Path | None = None,
               extra_libraries=(), cfg=None, which=None, include_apps: bool = True) -> Plan:
    home = home or Path.home()
    sources = _codeaque_sources(home, config_dir, data_dir)
    sources += _emu_sources(home)
    sources += _steam_save_sources(home, extra_libraries)
    # Drop duplicates (a more specific tree already listed).
    unique: list[Source] = []
    for s in sources:
        if not _already(unique, s.src):
            unique.append(s)
    plan = Plan(sources=unique, apps=installed_apps(home, cfg=cfg, which=which) if include_apps else [])
    for s in unique:
        files = _iter_files(s.src)
        plan.files += len(files)
        plan.bytes += sum(_file_size(f) for f in files)
    return plan


def _scrub_bytes(path: Path, data: bytes) -> bytes:
    """Strip API keys from known config files before they go into the archive."""
    if path.name.lower() == "config.toml":
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            return data
        text = re.sub(r"(?im)^(steamgriddb_key\s*=\s*).*$", r'\1""', text)
        return text.encode("utf-8")
    return data


def _readme(plan: Plan, dest_name: str, when: datetime) -> str:
    apps = ", ".join(a.get("title") or a.get("id") or "" for a in plan.apps[:30]) or "(none found)"
    if len(plan.apps) > 30:
        apps += f" and {len(plan.apps) - 30} more"
    return (
        f"CodeAque backup — {when.strftime('%Y-%m-%d')}\n"
        f"File: {dest_name}\n\n"
        "What is in this file\n"
        "--------------------\n"
        "CodeAque settings, tiles, layout, covers, Fav Sites and Ask CodeAque notes,\n"
        "emulator saves and save states (RetroArch and the other emulators CodeAque knows),\n"
        "Steam game saves that live in Proton folders (for games that do not use Steam Cloud),\n"
        "and a list of programs that were installed, so the other computer can reinstall them.\n\n"
        "What is not in this file\n"
        "------------------------\n"
        "Games, ROM files, BIOS and firmware, Steam game folders, passwords, keys,\n"
        "tokens and browser logins. Steam games need to be downloaded again on the other\n"
        "computer (sign in to Steam there). Steam Cloud already keeps many Steam saves.\n"
        "The Xbox (xemu) hard-disk image is not copied (it is large and holds firmware).\n"
        "The Ask CodeAque model is not copied; set it up again on the other computer if you use it.\n\n"
        f"About this backup: {plan.files} files, {human_size(plan.bytes)} before packing.\n"
        f"Programs listed: {apps}\n\n"
        "To restore: in CodeAque open Settings > Backup & Restore, plug this stick in,\n"
        "and press Restore. Or run:  bash scripts/codeaque-backup.sh restore THISFILE\n"
    )


def write_backup(dest_dir: Path, home: Path | None = None, config_dir: Path | None = None,
                 data_dir: Path | None = None, extra_libraries=(), cfg=None, which=None,
                 when: datetime | None = None, progress: ProgressFn | None = None,
                 cancel=None, name: str | None = None) -> Result:
    """Write one archive into dest_dir. dest_dir must exist and be writable."""
    home = home or Path.home()
    when = when or datetime.now()
    dest_dir = Path(dest_dir)
    try:
        dest_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        return Result(False, f"Could not write to {dest_dir} ({exc}).")
    if progress:
        progress(-1, "Looking at what to save...", "")
    plan = build_plan(home, config_dir, data_dir, extra_libraries, cfg, which)
    if plan.files == 0 and not plan.apps:
        return Result(False, "Nothing was found to back up yet. Set up CodeAque and play a game first.")
    existing = []
    try:
        existing = [p.name for p in dest_dir.iterdir() if p.is_file()]
    except OSError:
        pass
    fname = name or backup_name(when, existing)
    dest = dest_dir / fname
    # Leave a little headroom for the tar gzip wrapper and the manifest.
    need = int(plan.bytes * 1.05) + 1_000_000
    try:
        free = shutil.disk_usage(dest_dir).free
    except OSError as exc:
        return Result(False, f"Could not check free space ({exc}).")
    if free < need:
        return Result(False, f"Not enough free space for this backup ({human_size(need)} needed, {human_size(free)} free).")

    manifest = {
        "format": FORMAT,
        "app": "CodeAque",
        "version": __version__,
        "created": when.astimezone().isoformat(timespec="seconds"),
        "home": str(home),
        "user": home.name,
        "entries": [{"kind": s.kind, "label": s.label, "src": str(s.src), "arc": s.arc,
                     "file": s.src.is_file()} for s in plan.sources],
        "apps": plan.apps,
        "files": plan.files,
        "bytes": plan.bytes,
    }

    done = 0
    total = max(1, plan.files)
    tmp = dest.with_suffix(dest.suffix + ".partial")
    try:
        with tarfile.open(tmp, "w:gz") as tf:
            man = json.dumps(manifest, indent=1).encode("utf-8")
            _add_bytes(tf, MANIFEST_NAME, man)
            _add_bytes(tf, README_NAME, _readme(plan, fname, when).encode("utf-8"))
            for src in plan.sources:
                if cancel is not None and cancel():
                    raise _Cancelled()
                if src.src.is_file():
                    files = _iter_files(src.src)
                    arc_base = src.arc
                    for f in files:
                        data = _read_scrubbed(f)
                        _add_bytes(tf, arc_base, data, mtime=f.stat().st_mtime if f.exists() else None)
                        done += 1
                        if progress:
                            progress(done / total, f"Saving {src.label}", f"{done} of {total} files")
                else:
                    for f in _iter_files(src.src):
                        if cancel is not None and cancel():
                            raise _Cancelled()
                        rel = f.relative_to(src.src).as_posix()
                        data = _read_scrubbed(f)
                        _add_bytes(tf, src.arc.rstrip("/") + "/" + rel, data,
                                   mtime=f.stat().st_mtime if f.exists() else None)
                        done += 1
                        if progress and (done == 1 or done == total or done % 8 == 0):
                            progress(done / total, f"Saving {src.label}", f"{done} of {total} files")
        os.replace(tmp, dest)
    except _Cancelled:
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass
        return Result(False, "Backup cancelled. Nothing was saved.")
    except OSError as exc:
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass
        return Result(False, f"Could not write the backup ({exc}). Is the stick full or write-protected?")
    try:
        size = dest.stat().st_size
    except OSError:
        size = 0
    if progress:
        progress(1.0, "Backup finished", human_size(size))
    return Result(True, f"Backup saved: {dest.name}  ({human_size(size)}, {plan.files} files).",
                  path=str(dest), size=size, files=plan.files, apps=plan.apps)


class _Cancelled(Exception):
    pass


def _add_bytes(tf: tarfile.TarFile, name: str, data: bytes, mtime: float | None = None) -> None:
    info = tarfile.TarInfo(name=name.replace("\\", "/"))
    info.size = len(data)
    info.mtime = int(mtime or time.time())
    info.mode = 0o644
    tf.addfile(info, io.BytesIO(data))


def _read_scrubbed(path: Path) -> bytes:
    try:
        data = path.read_bytes()
    except OSError:
        return b""
    return _scrub_bytes(path, data)


# ------------------------------------------------------------------ find / inspect
def find_backups(roots: list[Path] | None = None, drives: list | None = None,
                 home: Path | None = None) -> list[Path]:
    """Backup files on USB sticks, plus ~/Downloads, newest first."""
    found: list[Path] = []
    seen: set[str] = set()
    places: list[Path] = []
    if roots is not None:
        places.extend(Path(p) for p in roots)
    else:
        for d in (drives if drives is not None else find_drives()):
            places.append(Path(getattr(d, "path", "") or ""))
        places.append(default_downloads(home))
    for folder in places:
        if not folder.is_dir():
            continue
        try:
            kids = list(folder.iterdir())
        except OSError:
            continue
        for p in kids:
            if p.is_file() and is_backup_name(p.name):
                key = os.path.realpath(p)
                if key not in seen:
                    seen.add(key)
                    found.append(p)
    found.sort(key=lambda p: p.stat().st_mtime if p.exists() else 0, reverse=True)
    return found


def read_manifest(archive: Path) -> tuple[dict, str]:
    """(manifest, error). A backup without a manifest still returns a minimal one if the tar opens."""
    archive = Path(archive)
    if not archive.is_file():
        return {}, "That backup file was not found."
    try:
        with tarfile.open(archive, "r:*") as tf:
            try:
                tf.getmember(MANIFEST_NAME)
            except KeyError:
                return {"format": 0, "entries": [], "apps": [], "path": str(archive)}, ""
            f = tf.extractfile(MANIFEST_NAME)
            if f is None:
                return {}, "The backup could not be read."
            data = json.loads(f.read().decode("utf-8"))
            if not isinstance(data, dict):
                return {}, "The backup list was not valid."
            data["path"] = str(archive)
            return data, ""
    except (tarfile.TarError, OSError, UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        return {}, f"The backup could not be opened ({exc})."


def describe(archive: Path) -> str:
    man, err = read_manifest(archive)
    if err:
        return err
    when = str(man.get("created") or "")
    files = man.get("files")
    raw = man.get("bytes")
    apps = man.get("apps") or []
    bits = [Path(archive).name]
    if when:
        bits.append("made " + when.split("T")[0])
    if files:
        bits.append(f"{files} files")
    if raw:
        bits.append(human_size(int(raw)) + " before packing")
    try:
        bits.append("file size " + human_size(archive.stat().st_size))
    except OSError:
        pass
    if apps:
        bits.append(str(len(apps)) + " programs listed")
    home = man.get("home") or ""
    if home:
        bits.append("from " + home)
    return " · ".join(bits)


# ------------------------------------------------------------------ restore
def _safe_arc_name(name: str) -> bool:
    n = name.replace("\\", "/")
    return bool(n) and not n.startswith("/") and ".." not in n.split("/")


def rewrite_text(text: str, old_home: str, new_home: str) -> str:
    """Replace the old computer's home path with this computer's, in both directions of slashes."""
    if not old_home or not new_home or old_home == new_home:
        return text
    text = text.replace(old_home, new_home)
    # Windows-style path inside Proton .ini files, just in case a tool stored a Linux path with backslashes.
    text = text.replace(old_home.replace("/", "\\"), new_home.replace("/", "\\"))
    return text


def _maybe_rewrite(name: str, data: bytes, old_home: str, new_home: str) -> bytes:
    suf = ""
    low = name.lower()
    for s in REWRITE_SUFFIXES:
        if low.endswith(s):
            suf = s
            break
    if not suf or not old_home or old_home == new_home:
        return data
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        return data
    new = rewrite_text(text, old_home, new_home)
    return new.encode("utf-8") if new != text else data


def _dest_for_entry(entry: dict, home: Path, config_dir: Path, data_dir: Path) -> Path | None:
    """Where an archived entry should land on this computer."""
    kind = str(entry.get("kind") or "")
    src = str(entry.get("src") or "")
    arc = str(entry.get("arc") or "")
    old_home = ""  # filled by caller via rewrite of src first
    if kind == "codeaque-config" or arc.rstrip("/") == "codeaque/config":
        return config_dir
    if kind == "codeaque-data" or arc.rstrip("/") == "codeaque/data":
        return data_dir
    if arc.startswith("codeaque/fav"):
        # Keep fav sites next to the data dir (and inside it if that is where they already live).
        name = Path(arc).name
        return data_dir / name
    if arc.startswith("nova/"):
        sub = "config" if "/config" in arc else "data"
        return (home / ".config" / "nova") if sub == "config" else (home / ".local" / "share" / "nova")
    if arc.startswith("codeaque/nova"):
        return data_dir / "nova"
    if kind.startswith("emu-") or arc.startswith("emu/"):
        # Recreate the original path relative to the old home, then under the new home.
        return None  # resolved by src rewrite
    if kind == "steam-save" or arc.startswith("steam/compatdata/"):
        parts = arc.replace("\\", "/").split("/")
        # steam/compatdata/<appid>/<Documents|AppData|Saved Games>/...
        try:
            i = parts.index("compatdata")
            appid, sub = parts[i + 1], parts[i + 2]
        except (ValueError, IndexError):
            return None
        libs = _steam_library_paths(home)
        root = libs[0] if libs else (home / ".steam" / "steam")
        return root / "steamapps" / "compatdata" / appid / "pfx" / "drive_c" / "users" / "steamuser" / sub
    return None


def _mapped_dest(entry: dict, home: Path, old_home: str, config_dir: Path, data_dir: Path) -> Path | None:
    special = _dest_for_entry(entry, home, config_dir, data_dir)
    if special is not None:
        return special
    src = str(entry.get("src") or "")
    if src and old_home and (src == old_home or src.startswith(old_home.rstrip("/") + "/")):
        return Path(rewrite_text(src, old_home, str(home)))
    if src:
        # Last resort: if src is under some /home/<user>/, swap that prefix.
        m = re.match(r"^(/home/[^/]+)(/.*)?$", src)
        if m:
            return Path(str(home) + (m.group(2) or ""))
    return None


def restore_backup(archive: Path, home: Path | None = None, config_dir: Path | None = None,
                   data_dir: Path | None = None, safety_dir: Path | None = None,
                   progress: ProgressFn | None = None, cancel=None,
                   write_safety: bool = True) -> Result:
    """Restore a backup. Writes a safety copy of current files first (unless write_safety is False)."""
    home = home or Path.home()
    config_dir = config_dir or paths.config_dir()
    data_dir = data_dir or paths.data_dir()
    archive = Path(archive)
    man, err = read_manifest(archive)
    if err:
        return Result(False, err)
    old_home = str(man.get("home") or "")
    if write_safety:
        if progress:
            progress(-1, "Saving a safety copy of what is here now...", "")
        safety_dir = Path(safety_dir or data_dir / "restore-safety")
        safety = write_backup(safety_dir, home, config_dir, data_dir, name=None,
                              when=datetime.now(), progress=None, cancel=cancel)
        if not safety.ok:
            return Result(False, "Could not save a safety copy first. Restore was not started. " + safety.message)

    members: list[tarfile.TarInfo] = []
    try:
        tf = tarfile.open(archive, "r:*")
    except (tarfile.TarError, OSError) as exc:
        return Result(False, f"The backup could not be opened ({exc}).")
    try:
        for m in tf.getmembers():
            if m.name in {MANIFEST_NAME, README_NAME}:
                continue
            if not _safe_arc_name(m.name):
                return Result(False, "The backup contains an unsafe path and was not restored.")
            if m.isfile() or m.isdir():
                members.append(m)
        files = [m for m in members if m.isfile()]
        total = max(1, len(files))
        done = 0
        # Map archive prefixes (entry.arc) to destination folders.
        entries = list(man.get("entries") or [])
        # Longest arc prefix first, so steam/compatdata/123/Documents wins over a shorter match.
        entries.sort(key=lambda e: len(str(e.get("arc") or "")), reverse=True)

        def dest_for(arc_name: str) -> Path | None:
            n = arc_name.replace("\\", "/")
            for e in entries:
                prefix = str(e.get("arc") or "").replace("\\", "/").rstrip("/")
                if not prefix:
                    continue
                if n == prefix or n.startswith(prefix + "/"):
                    base = _mapped_dest(e, home, old_home, config_dir, data_dir)
                    if base is None:
                        return None
                    if bool(e.get("file")) or (Path(str(e.get("src") or "")).suffix and n == prefix):
                        return base
                    rest = n[len(prefix):].lstrip("/")
                    return base / rest if rest else base
            return None

        for m in files:
            if cancel is not None and cancel():
                return Result(False, "Restore cancelled. A safety copy is still in " + (str(safety_dir) if write_safety else "place") + ".")
            dest = dest_for(m.name)
            if dest is None:
                done += 1
                continue
            try:
                dest.parent.mkdir(parents=True, exist_ok=True)
                extracted = tf.extractfile(m)
                if extracted is None:
                    done += 1
                    continue
                data = extracted.read()
                data = _maybe_rewrite(m.name, data, old_home, str(home))
                data = _scrub_bytes(dest, data)  # never restore a key we would not back up
                tmp = dest.with_suffix(dest.suffix + ".restore-tmp")
                tmp.write_bytes(data)
                os.replace(tmp, dest)
            except OSError as exc:
                return Result(False, f"Could not write {dest} ({exc}).")
            done += 1
            if progress and (done == 1 or done == total or done % 8 == 0):
                progress(done / total, "Putting your files back...", f"{done} of {total} files")
    finally:
        tf.close()
    apps = list(man.get("apps") or [])
    if progress:
        progress(1.0, "Restore finished", "")
    msg = "Your settings and saves are back on this computer."
    if write_safety and safety_dir is not None:
        msg += " A safety copy of what was here before is in " + str(safety_dir) + "."
    if apps:
        msg += f" {len(apps)} programs were listed, so they can be installed next if they are missing."
    return Result(True, msg, path=str(archive), files=len(files), apps=apps)


def missing_apps(listed: list[dict], home: Path | None = None, cfg=None, which=None) -> list[dict]:
    """Entries from the backup's app list that are not installed here yet."""
    which = which or shutil.which
    home = home or Path.home()
    missing: list[dict] = []
    for rec in listed or []:
        tid = str(rec.get("id") or "")
        target = appinstall.target_for(tid, rec.get("title") or "")
        if target is None:
            continue
        try:
            ok = appinstall.is_installed(target, home=home, which=which, cfg=cfg)
        except Exception:
            ok = False
        if not ok:
            missing.append(rec)
    return missing


# ------------------------------------------------------------------ CLI
def _cli_progress(frac: float, title: str, detail: str) -> None:
    pct = "  --%" if frac < 0 else f"{int(round(frac * 100)):3d}%"
    line = f"\r{pct}  {title}  {detail}"
    print(line[:120].ljust(120), end="", flush=True)


def _cli_end() -> None:
    print()


def main(argv: list[str] | None = None) -> int:
    import argparse
    p = argparse.ArgumentParser(
        prog="codeaque-backup",
        description="Back up or restore CodeAque settings and game saves (not the games themselves).",
    )
    sub = p.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("backup", help="Write a backup file to a USB stick, or to a folder you name.")
    b.add_argument("--dest", help="Folder to write to (default: a USB stick, or ~/Downloads if none).")
    r = sub.add_parser("restore", help="Restore a backup file. A safety copy of current files is made first.")
    r.add_argument("archive", help="Path to CodeAque-Backup-YYYY-MM-DD.tar.gz")
    r.add_argument("--no-safety", action="store_true", help="Do not write a safety copy first (not recommended).")
    sub.add_parser("list", help="Show backup files found on USB sticks and in Downloads.")
    args = p.parse_args(argv)

    if args.cmd == "list":
        found = find_backups()
        if not found:
            print("No CodeAque backup file was found on a USB stick or in Downloads.")
            return 1
        for path in found:
            print(describe(path))
            print("  " + str(path))
        return 0

    if args.cmd == "backup":
        dest = Path(args.dest).expanduser() if args.dest else None
        if dest is None:
            drive, why = pick_drive()
            if drive is not None:
                dest = Path(drive.path)
                print(f"Using USB stick: {getattr(drive, 'label', '') or dest}")
            else:
                dest = default_downloads()
                print(why)
                print(f"Saving to {dest} instead.")
        print("Building the backup. Games, BIOS and passwords are left out.")
        res = write_backup(dest, progress=_cli_progress)
        _cli_end()
        print(res.message)
        return 0 if res.ok else 1

    res = restore_backup(Path(args.archive).expanduser(), progress=_cli_progress,
                         write_safety=not args.no_safety)
    _cli_end()
    print(res.message)
    return 0 if res.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
