"""Box Set Mode reboot lock (0.10.32).

When the Settings toggle "Lock into Box Set Mode on reboot" is on, CodeAque
starts at login (the existing ~/.config/autostart/atari.desktop mechanism),
full screen, straight into Box Set Mode — no home screen first. The Linux
Mint desktop is covered immediately; if CodeAque-only mode is also on, the
desktop is skipped entirely.

Leaving Box Set Mode (Exit, or hold Start/Select — no PIN) does not clear
the toggle: the next reboot locks back in. Turning the toggle off stops the
reboot lock. The autostart entry is re-applied by win600 --emulators-only
(in-app updates) so it is not dropped.
"""
from __future__ import annotations

from pathlib import Path

DESKTOP_NAME = "atari.desktop"


def autostart_dir(home: Path | None = None) -> Path:
    return (home or Path.home()) / ".config/autostart"


def autostart_path(home: Path | None = None) -> Path:
    return autostart_dir(home) / DESKTOP_NAME


def run_script_path() -> Path:
    return Path(__file__).resolve().parent.parent / "run.sh"


def desktop_text(run: Path) -> str:
    exe = str(run)
    # Full screen so the desktop does not sit in front while CodeAque starts.
    exec_line = exe if exe.endswith(" --fullscreen") else exe + " --fullscreen"
    return (
        "[Desktop Entry]\n"
        "Type=Application\n"
        "Version=1.0\n"
        "Name=CodeAque\n"
        "GenericName=Game Launcher\n"
        "Comment=Start CodeAque at login, straight into Box Set Mode\n"
        f"Exec={exec_line}\n"
        "Icon=atari-launcher\n"
        "Terminal=false\n"
        "Categories=Game;\n"
        "StartupNotify=false\n"
        "X-GNOME-Autostart-enabled=true\n"
        "X-CodeAque-BoxSet=true\n"
    )


def persist(on: bool, *, home: Path | None = None, run: Path | None = None) -> str:
    """When on, write/refresh the login autostart so CodeAque starts full screen.
    When off, leave a general / Win600 autostart in place (only the config flag
    stops the Box Set lock). Returns a short status string."""
    path = autostart_path(home)
    if not on:
        return "Box Set reboot lock off (login autostart left as it is)."
    run = run or run_script_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(desktop_text(run))
    try:
        path.chmod(path.stat().st_mode | 0o111)
    except OSError:
        pass
    return f"Box Set reboot lock on: CodeAque will start at login ({path})."
