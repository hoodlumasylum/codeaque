"""One-press Fix Steam: diagnose and repair common Steam Runtime / start failures.

The error "Couldn't set up the Steam Runtime. Are you running low on disk space?"
(and the similar "Couldn't start Steam" wording) often appears when there is
plenty of space. Typical causes on Linux Mint 22 / Ubuntu 24.04 (noble):

  * Missing i386 multiarch and 32-bit libraries (libc6:i386, libgl1:i386,
    libgl1-mesa-dri:i386, and related packages)
  * A broken or half-downloaded ~/.local/share/Steam/ubuntu12_32/steam-runtime
  * Flatpak Steam missing the 32-bit GL32 runtime for its Platform version
  * Actually not enough free space at the Steam path or in /tmp

Nothing here deletes steamapps or games. Privileged steps go through pkexec
(and CodeAque's in-app password box via rootauth in CodeAque-only mode).
If pkexec fails, the exact sudo commands are returned so the UI can show them
in large text. All functions accept a ``run`` injectable so tests never touch
the real system.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

FLATPAK_ID = "com.valvesoftware.Steam"
GL32_RUNTIME = "org.freedesktop.Platform.GL32.default"
# Ubuntu 24.04 / Linux Mint 22 (noble): Steam's runtime needs these 32-bit libs.
# steam-libs-i386 is useful when the distro ships it, but it is not always in the
# archive, so it is optional. Prefer the steam-installer package; fall back to steam.
STEAM_PACKAGES = ("steam-installer", "steam")
I386_CORE = ("libc6:i386", "libgl1:i386", "libgl1-mesa-dri:i386")
I386_OPTIONAL = (
    "steam-libs-i386", "libglx-mesa0:i386", "libstdc++6:i386", "libgcc-s1:i386",
    "libx11-6:i386", "libxrandr2:i386", "libxss1:i386",
)
I386_PACKAGES = STEAM_PACKAGES + I386_CORE  # used by older tests / callers
RUNTIME_NAMES = (
    "steam-runtime",
    "steam-runtime.tar.xz",
    "steam-runtime.tar.xz.part",
    "steam-runtime.tar.xz.lzma",
)
MIN_FREE = 500 * 1024 * 1024
MIN_TMP_FREE = 200 * 1024 * 1024

Run = Callable[..., subprocess.CompletedProcess]
Progress = Callable[[str], None]


@dataclass
class Step:
    id: str
    title: str
    ok: bool
    detail: str
    changed: bool = False
    commands: str = ""  # exact sudo commands to show in large text when pkexec failed


def _noop_progress(_msg: str) -> None:
    return


def _run(cmd: list[str], run: Run | None = None, timeout: int = 180, env=None) -> subprocess.CompletedProcess:
    if run is not None:
        try:
            return run(cmd, timeout=timeout, env=env)
        except FileNotFoundError:
            return subprocess.CompletedProcess(cmd, 127, "", f"{cmd[0]} is not installed")
        except subprocess.TimeoutExpired:
            return subprocess.CompletedProcess(cmd, 124, "", "That step timed out")
        except TypeError:
            # A simple test double may only accept the command list.
            return run(cmd)
        except Exception as exc:  # pragma: no cover - safety net
            return subprocess.CompletedProcess(cmd, 1, "", str(exc))
    full, secret, early = cmd, None, None
    try:
        from . import rootauth
        full, secret, early = rootauth.prepare(cmd)
    except Exception:
        full, secret, early = cmd, None, None
    if early is not None:
        why = "The password window was closed, so nothing was changed." if early == 126 else f"Could not use administrator permission (code {early})."
        return subprocess.CompletedProcess(cmd, early, "", why)
    try:
        return subprocess.run(
            full, capture_output=True, text=True, timeout=timeout, env=env,
            input=secret.decode() if secret else None,
            stdin=None if secret else subprocess.DEVNULL,
        )
    except FileNotFoundError:
        return subprocess.CompletedProcess(cmd, 127, "", f"{cmd[0]} is not installed")
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(cmd, 124, "", "That step timed out")
    except Exception as exc:  # pragma: no cover - safety net
        return subprocess.CompletedProcess(cmd, 1, "", str(exc))


def _which(name: str, which: Callable | None = None) -> str:
    return (which or shutil.which)(name) or ""


def _out(r: subprocess.CompletedProcess) -> str:
    return ((r.stdout or "") + "\n" + (r.stderr or "")).strip()


def free_bytes(path: Path) -> int:
    try:
        probe = path if path.exists() else (path.parent if path.parent.exists() else Path("/"))
        return shutil.disk_usage(str(probe)).free
    except OSError:
        return -1


def fmt_bytes(n: int) -> str:
    if n < 0:
        return "unknown"
    if n >= 1024 ** 3:
        return f"{n / 1024 ** 3:.1f} GB"
    return f"{n / 1024 ** 2:.0f} MB"


def steam_roots(home: Path | None = None) -> dict[str, Path]:
    """Known Steam data folders (Flatpak first, then native). Does not create them."""
    home = home or Path.home()
    return {
        "flatpak": home / ".var/app/com.valvesoftware.Steam/.local/share/Steam",
        "native": home / ".local/share/Steam",
        "dotsteam": home / ".steam/steam",
    }


def detect_kind(home: Path | None = None, which: Callable | None = None, run: Run | None = None) -> str:
    """'flatpak', 'native', 'both', or 'missing'."""
    has_fp = bool(_which("flatpak", which))
    installed = False
    if has_fp:
        r = _run(["flatpak", "info", FLATPAK_ID], run=run, timeout=30)
        installed = r.returncode == 0
    native = bool(_which("steam", which))
    if installed and native:
        return "both"
    if installed:
        return "flatpak"
    if native:
        return "native"
    roots = steam_roots(home)
    if roots["flatpak"].is_dir():
        return "flatpak"
    if roots["native"].is_dir() or roots["dotsteam"].is_dir():
        return "native"
    return "missing"


def data_path(home: Path | None = None, kind: str = "") -> Path:
    roots = steam_roots(home)
    kind = kind or detect_kind(home)
    if kind in ("flatpak", "both") and roots["flatpak"].exists():
        return roots["flatpak"]
    if roots["native"].exists():
        return roots["native"]
    if roots["dotsteam"].exists():
        return roots["dotsteam"]
    return roots["flatpak"] if kind == "flatpak" else roots["native"]


def space_ok(home: Path | None = None, kind: str = "", tmp: Path | None = None) -> tuple[bool, str, list[tuple[str, int]]]:
    """(enough, message, [(label, free_bytes), ...])."""
    steam = data_path(home, kind)
    tmp = tmp or Path(os.environ.get("TMPDIR") or "/tmp")
    rows = [("Steam folder (" + str(steam) + ")", free_bytes(steam)),
            ("Temporary folder (" + str(tmp) + ")", free_bytes(tmp))]
    parts, enough = [], True
    for label, n in rows:
        need = MIN_FREE if label.startswith("Steam") else MIN_TMP_FREE
        if n >= 0 and n < need:
            enough = False
            parts.append(f"{label} has only {fmt_bytes(n)} free (needs about {fmt_bytes(need)}).")
        else:
            parts.append(f"{label} has {fmt_bytes(n)} free.")
    if enough:
        return True, "There is enough free space. " + " ".join(parts), rows
    return False, "Not enough free space. " + " ".join(parts) + " Free some space and try again.", rows


def steam_platform_branch(run: Run | None = None) -> str:
    """The org.freedesktop.Platform branch Flatpak Steam is using, e.g. 23.08."""
    r = _run(["flatpak", "info", "--show-runtime", FLATPAK_ID], run=run, timeout=30)
    text = (r.stdout or "").strip() or (r.stderr or "").strip()
    # Typical: org.freedesktop.Platform/x86_64/23.08
    if "org.freedesktop.Platform" in text and "/" in text:
        return text.strip().split("/")[-1].strip()
    r = _run(["flatpak", "info", FLATPAK_ID], run=run, timeout=30)
    for line in ((r.stdout or "") + "\n" + (r.stderr or "")).splitlines():
        low = line.lower()
        if "runtime:" in low and "org.freedesktop.platform" in low:
            bits = line.replace(":", " ").replace("/", " ").split()
            for b in reversed(bits):
                if b[:2].isdigit() and "." in b:
                    return b.strip()
    return ""


def _flatpak_has(ref: str, run: Run | None = None) -> bool:
    return _run(["flatpak", "info", ref], run=run, timeout=30).returncode == 0


def install_gl_runtimes(run: Run | None = None, which: Callable | None = None) -> Step:
    """Update Flatpak Steam and install GL32 for the matching Platform version."""
    if not _which("flatpak", which):
        return Step("gl", "32-bit graphics runtime", False, "flatpak is not installed, so this step was skipped.")
    branch = steam_platform_branch(run)
    details, failed, changed, refs = [], False, False, []
    r = _run(["flatpak", "update", "-y", "--noninteractive", FLATPAK_ID], run=run, timeout=600)
    tail = _out(r)[-240:]
    text = (r.stdout or "") + (r.stderr or "")
    if r.returncode == 0:
        changed = "nothing to do" not in text.lower() and "already up to date" not in text.lower()
        details.append("Updated Flatpak Steam" if changed else "Flatpak Steam is already up to date")
    elif "already" in text.lower() and "up to date" in text.lower():
        details.append("Flatpak Steam is already up to date")
    else:
        failed = True
        details.append("Flatpak update failed: " + tail)
    # Re-read the branch after an update in case Steam moved runtime.
    branch = steam_platform_branch(run) or branch
    if branch:
        ref = f"{GL32_RUNTIME}//{branch}"
        refs.append(ref)
        if not _flatpak_has(ref, run):
            inst = _run(["flatpak", "install", "-y", "--noninteractive", "flathub", ref], run=run, timeout=600)
            text = (inst.stdout or "") + (inst.stderr or "")
            if inst.returncode == 0 or "already installed" in text.lower():
                changed = True
                details.append("Installed " + ref)
            else:
                failed = True
                details.append("Could not install " + ref + ": " + _out(inst)[-200:])
        else:
            details.append(ref + " is already installed")
    else:
        details.append("Could not read Steam's Freedesktop Platform version, so GL32 was not auto-installed.")
    if failed:
        return Step("gl", "32-bit graphics runtime", False,
                    "Steam needs the 32-bit graphics runtime (GL32) for its Platform version. "
                    + " | ".join(details), changed=changed)
    return Step("gl", "32-bit graphics runtime", True,
                "Flatpak Steam and the matching GL32 runtime (" + (branch or "unknown") + "): " + " | ".join(details),
                changed=changed)


def repair_bootstrap(home: Path | None = None, now: Callable[[], float] | None = None) -> Step:
    """Move a broken Steam Runtime aside so Steam can rebuild it. Never touches steamapps or games.

    Only ubuntu12_32/steam-runtime (and a half-downloaded runtime tarball) is moved.
    The rest of ubuntu12_32, including Steam's own bootstrap binary, is left in place.
    """
    home = home or Path.home()
    roots = list(steam_roots(home).values())
    stamp = time.strftime("%Y%m%d-%H%M%S", time.localtime((now or time.time)()))
    moved: list[str] = []
    for root in roots:
        if not root.is_dir():
            continue
        # Never walk or rename steamapps, even if a name looks similar.
        runtime_dir = root / "ubuntu12_32"
        candidates = []
        if runtime_dir.is_dir():
            for name in RUNTIME_NAMES:
                src = runtime_dir / name
                if src.exists() and "steamapps" not in src.parts:
                    candidates.append(src)
        for src in candidates:
            dest = src.with_name(f"{src.name}.broken-{stamp}")
            try:
                src.rename(dest)
                moved.append(f"{src} → {dest}")
            except OSError as exc:
                return Step("bootstrap", "Steam Runtime files", False,
                            f"Could not move {src} aside: {exc}. Your games were not touched.")
    if moved:
        return Step("bootstrap", "Steam Runtime files", True,
                    "Moved a broken Steam Runtime aside so Steam can rebuild it (your games in steamapps were not touched): "
                    + "; ".join(moved), changed=True)
    return Step("bootstrap", "Steam Runtime files", True,
                "No broken Steam Runtime folder needed moving. Games in steamapps were left alone.")


def sudo_command() -> str:
    """One pasteable line for a Terminal if the password window cannot run."""
    pkgs = "steam-installer " + " ".join(I386_CORE)
    return (
        "sudo dpkg --add-architecture i386 && sudo apt update && "
        f"sudo apt install -y {pkgs}"
    )


def native_steam_script() -> str:
    """What pkexec/sudo runs as root: i386 multiarch, Steam, and 32-bit runtime libs."""
    core = " ".join(I386_CORE)
    optional = " ".join(I386_OPTIONAL)
    return (
        "set -e; "
        "dpkg --print-foreign-architectures | grep -qx i386 || dpkg --add-architecture i386; "
        "apt-get update -y; "
        "if apt-get install -y steam-installer; then :; "
        "elif apt-get install -y steam; then :; "
        "else echo 'Could not install steam-installer or steam' >&2; exit 1; fi; "
        f"apt-get install -y {core}; "
        f"for p in {optional}; do apt-get install -y \"$p\" || true; done"
    )


def _dpkg_has_i386(run: Run | None = None) -> bool:
    r = _run(["dpkg", "--print-foreign-architectures"], run=run, timeout=20)
    return "i386" in (r.stdout or "")


def _pkg_installed(name: str, run: Run | None = None) -> bool:
    r = _run(["dpkg-query", "-W", "-f=${Status}", name], run=run, timeout=20)
    return r.returncode == 0 and "install ok installed" in (r.stdout or "")


def _steam_pkg_installed(run: Run | None = None) -> bool:
    return any(_pkg_installed(p, run) for p in STEAM_PACKAGES)


def install_i386(run: Run | None = None, which: Callable | None = None) -> Step:
    """Enable i386, install Steam (steam-installer, or steam), and the 32-bit libs the runtime needs."""
    cmd = sudo_command()
    if not _which("dpkg", which) and run is None:
        return Step("i386", "32-bit Steam libraries", True, "This is not a Debian / Ubuntu system, so this step was skipped.")
    already_arch = _dpkg_has_i386(run)
    missing_core = [p for p in I386_CORE if not _pkg_installed(p, run)]
    have_steam = _steam_pkg_installed(run)
    if already_arch and have_steam and not missing_core:
        return Step("i386", "32-bit Steam libraries", True,
                    "32-bit support is on, Steam is installed, and the libraries the Steam Runtime needs "
                    "(libc6:i386, libgl1:i386, libgl1-mesa-dri:i386) are already present.")
    r = _run(["pkexec", "sh", "-c", native_steam_script()], run=run, timeout=600)
    text = _out(r)
    if r.returncode == 0:
        bits = []
        if not already_arch:
            bits.append("enabled 32-bit (i386) support")
        if not have_steam:
            bits.append("installed Steam")
        if missing_core:
            bits.append("installed " + ", ".join(missing_core))
        what = (", ".join(bits) or "confirmed Steam and the 32-bit runtime libraries").capitalize()
        return Step("i386", "32-bit Steam libraries", True, what + ".", changed=True)
    why = ("The password window was closed, so the 32-bit Steam libraries were not installed. "
           if r.returncode == 126 else
           "Needed 32-bit (i386) support, Steam, and libc6:i386 / libgl1:i386 / libgl1-mesa-dri:i386. ")
    why += "Paste this in a Terminal, then press Fix Steam again:\n" + cmd
    if text and r.returncode not in (126, 127):
        why += "\n(" + text[-200:] + ")"
    return Step("i386", "32-bit Steam libraries", False, why, changed=False, commands=cmd)


def enable_proton(home: Path | None = None, cfg_root: str = "",
                  running: Callable[[], bool] | None = None) -> Step:
    """Turn Steam Play (Proton Experimental) on for every title, without touching games."""
    from . import steamfix
    ok, text = steamfix.set_default_proton(home, cfg_root, running)
    note = (" Individual games can still fail: that is Proton compatibility, not a missing setting.")
    low = text.lower()
    if ok:
        return Step("proton", "Steam Play (Proton) for all games", True, text + note, changed=True)
    if "already" in low or "left as it is" in low:
        return Step("proton", "Steam Play (Proton) for all games", True, text + note)
    if "no config" in low or "sign in once" in low or "has no config" in low:
        return Step("proton", "Steam Play (Proton) for all games", True,
                    text + " After you sign in once, close Steam and press Fix Steam again, or just reopen CodeAque.")
    if "open right now" in low:
        return Step("proton", "Steam Play (Proton) for all games", False,
                    text + " Close Steam, then press Fix Steam again (or Turn Steam Play on) so Windows games can start from CodeAque tiles.")
    return Step("proton", "Steam Play (Proton) for all games", False, text)


def console_command(base: list[str], running: bool = False) -> list[str]:
    """How to start Steam in console / CodeAque-only mode: Big Picture, Flatpak or native."""
    if not base:
        base = ["steam"]
    if running:
        return list(base) + ["steam://open/bigpicture"]
    # -bigpicture is the documented flag; -gamepadui is Steam's newer Big Picture.
    return list(base) + ["-bigpicture", "-gamepadui"]


def run_fix(home: Path | None = None, run: Run | None = None, which: Callable | None = None,
            now: Callable[[], float] | None = None, tmp: Path | None = None,
            progress: Progress | None = None, cfg_root: str = "",
            running: Callable[[], bool] | None = None) -> tuple[list[Step], str]:
    """Diagnose and repair. Returns (steps, verdict). Safe to run again. Never deletes games."""
    home = Path(home) if home else Path.home()
    report = progress or _noop_progress
    kind = detect_kind(home, which=which, run=run)
    steps: list[Step] = []

    report("Checking free space at the Steam folder and in /tmp...")
    ok_space, space_msg, _ = space_ok(home, kind, tmp=tmp)
    steps.append(Step("space", "Free space", ok_space, space_msg))

    if kind == "missing":
        report("Steam is not installed yet. Installing Steam and the 32-bit libraries the Steam Runtime needs...")
        steps.append(install_i386(run=run, which=which))
        kind = detect_kind(home, which=which, run=run)

    if kind == "missing":
        i386 = next((s for s in steps if s.id == "i386"), None)
        detail = ((i386.detail + " ") if i386 else "") + (
            "Steam is not installed yet. If the password window did not work, paste the command shown in large text "
            "into a Terminal, then press Fix Steam again."
        )
        steps.append(Step("steam", "Steam is installed", False, detail,
                          commands=(i386.commands if i386 else sudo_command())))
        cmds = (i386.commands if i386 and i386.commands else sudo_command())
        return steps, "Steam could not be installed yet. " + space_msg + " In a Terminal:  " + cmds

    steps.append(Step("steam", "Steam is installed", True,
                      "Found " + ("Flatpak Steam" if kind == "flatpak" else "native Steam" if kind == "native" else "Flatpak and native Steam") + "."))

    if kind in ("flatpak", "both"):
        report("Updating Flatpak Steam and installing the matching 32-bit GL32 runtime...")
        steps.append(install_gl_runtimes(run=run, which=which))
    if kind in ("native", "both"):
        # Skip a second pkexec if we just installed Steam in the "missing" path above.
        if not any(s.id == "i386" for s in steps):
            report("Enabling 32-bit (i386) support and installing Steam plus libc6:i386, libgl1:i386 and libgl1-mesa-dri:i386...")
            steps.append(install_i386(run=run, which=which))

    report("Moving a broken Steam Runtime aside if one is there (games in steamapps stay)...")
    steps.append(repair_bootstrap(home, now=now))

    report("Turning Steam Play (Proton) on for all games, so Windows titles can start from CodeAque...")
    steps.append(enable_proton(home, cfg_root=cfg_root, running=running))

    report("Finishing...")
    changed = any(s.changed for s in steps)
    failed = [s for s in steps if not s.ok]
    blocking = [s for s in failed if s.id in ("space", "i386", "steam")]
    cmd = next((s.commands for s in failed if s.commands), "")
    if not ok_space:
        verdict = space_msg + " CodeAque did not start Steam, because there may not be enough room."
    elif blocking:
        verdict = "Some steps could not finish: " + "; ".join(s.title + " — " + s.detail for s in blocking)
        if cmd:
            verdict += "  In a Terminal, paste:  " + cmd
    elif failed:
        verdict = "Steam was repaired as far as CodeAque could go. " + "; ".join(s.title + " — " + s.detail for s in failed)
    elif changed:
        verdict = ("Steam was repaired, and Steam Play (Proton) is set as the default for all titles. "
                   "CodeAque will try to start Steam now. Some Windows games may still need a different Proton version.")
    else:
        verdict = ("Everything CodeAque can check looks fine, and Steam Play (Proton) is the default for all titles. "
                   "CodeAque will try to start Steam now. Some Windows games may still need a different Proton version.")
    return steps, verdict
