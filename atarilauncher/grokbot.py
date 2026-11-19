"""Grok Bot tile: open the official desktop app, or the official download page.

Official sources (checked 19 Nov 2026):
- Linux desktop app: https://cursor.com/download/bot  (.deb, .rpm, AppImage)
- Docs: https://docs.x.ai/grok-bot/get-started and https://docs.x.ai/grok-bot/faq
  (talk to Grok Bot in the desktop or phone app; there is no official web chat app)
- The official .deb installs the command ``grok-bot`` (``/usr/bin/grok-bot`` ->
  ``/opt/Grok Bot/grok-bot``) and ``/usr/share/applications/grok-bot.desktop``.
- Android: Google Play, package ``ai.x.grok.bot``.

CodeAque never handles a Grok Bot / Cursor login or API key. You sign in inside
the official app yourself.
"""
from __future__ import annotations

import os
import shutil
import subprocess
from collections.abc import Callable
from pathlib import Path

# Official download page that lists the Linux packages (and other desktops).
DOWNLOAD_URL = "https://cursor.com/download/bot"
ANDROID_PACKAGE = "ai.x.grok.bot"
PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=ai.x.grok.bot"

# Paths written by the official .deb (postinst + desktop file), not third-party ports.
_OFFICIAL_BINARIES = (
    "/usr/bin/grok-bot",
    "/opt/Grok Bot/grok-bot",
)
_OFFICIAL_DESKTOP = Path("/usr/share/applications/grok-bot.desktop")

NOT_INSTALLED_TOAST = (
    "Grok Bot is not installed yet. The official Linux download page is opening "
    "in the browser. There is no official web chat app — install the Linux app "
    "from that page, then this tile will open it."
)


def installed_command(which: Callable[[str], str | None] = shutil.which) -> list[str] | None:
    """Command that starts the official Linux app, or None if it is not installed."""
    found = which("grok-bot")
    if found and os.path.isfile(found) and os.access(found, os.X_OK):
        return [found]
    for p in _OFFICIAL_BINARIES:
        if os.path.isfile(p) and os.access(p, os.X_OK):
            return [p]
    if which("gtk-launch") and _OFFICIAL_DESKTOP.is_file():
        return ["gtk-launch", "grok-bot"]
    return None


def run_installed(on_started, env: dict | None = None, popen=subprocess.Popen) -> tuple[float, str]:
    """Start the installed official app and wait until it closes. Used by the launcher."""
    cmd = installed_command()
    if not cmd:
        from .launcher import LaunchError
        raise LaunchError("Grok Bot is not installed.")
    from . import launcher, paths
    try:
        paths.data_dir().mkdir(parents=True, exist_ok=True)
        logfh = open(paths.log_file(), "ab")
    except OSError:
        logfh = subprocess.DEVNULL
    try:
        proc = popen(cmd, env=env if env is not None else launcher.clean_env(),
                     stdout=logfh, stderr=logfh, stdin=subprocess.DEVNULL,
                     start_new_session=True)
    except OSError as exc:
        raise launcher.LaunchError(f"Could not start Grok Bot: {exc}") from exc
    on_started()
    try:
        proc.wait()
    finally:
        if logfh is not subprocess.DEVNULL:
            try:
                logfh.close()
            except OSError:
                pass
    return 0.0, ""
