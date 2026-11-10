"""One-press Fix Steam: diagnose and repair common "Couldn't start Steam" causes.

The error "Couldn't start Steam. Are you running low on disk space?" often appears
when there is plenty of space. Typical causes on Linux Mint / the Win600:

  * Flatpak Steam (com.valvesoftware.Steam) missing the 32-bit GL32 runtime
    for the same org.freedesktop.Platform version Steam is using
  * A corrupted first-run bootstrap (ubuntu12_32 / package)
  * Native .deb Steam without i386 / steam-libs-i386
  * Actually not enough free space at the Steam path or in /tmp

Nothing here deletes steamapps or games. Privileged steps go through pkexec
(and CodeAque's in-app password box via rootauth in CodeAque-only mode).
All functions accept a ``run`` injectable so tests never touch the real system.
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
I386_PACKAGES = ("steam-installer", "steam-libs-i386")
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
    """Move a corrupted first-run bootstrap aside so Steam can rebuild it. Never touches steamapps or games."""
    home = home or Path.home()
    roots = list(steam_roots(home).values())
    stamp = time.strftime("%Y%m%d-%H%M%S", time.localtime((now or time.time)()))
    moved: list[str] = []
    for root in roots:
        if not root.is_dir():
            continue
        for name in ("ubuntu12_32", "package"):
            src = root / name
            if not src.exists():
                continue
            if src.name == "steamapps" or src.name.lower().startswith("steamapps"):
                continue
            dest = root / f"{name}.broken-{stamp}"
            try:
                src.rename(dest)
                moved.append(f"{src} → {dest}")
            except OSError as exc:
                return Step("bootstrap", "First-run Steam files", False,
                            f"Could not move {src} aside: {exc}. Your games were not touched.")
    if moved:
        return Step("bootstrap", "First-run Steam files", True,
                    "Moved the first-run Steam files aside so they can be rebuilt (your games in steamapps were not touched): "
                    + "; ".join(moved), changed=True)
    return Step("bootstrap", "First-run Steam files", True,
                "No first-run Steam folder needed moving. Games in steamapps were left alone.")


def _dpkg_has_i386(run: Run | None = None) -> bool:
    r = _run(["dpkg", "--print-foreign-architectures"], run=run, timeout=20)
    return "i386" in (r.stdout or "")


def _pkg_installed(name: str, run: Run | None = None) -> bool:
    r = _run(["dpkg-query", "-W", "-f=${Status}", name], run=run, timeout=20)
    return r.returncode == 0 and "install ok installed" in (r.stdout or "")


def install_i386(run: Run | None = None, which: Callable | None = None) -> Step:
    """For the native .deb Steam: enable i386 and install steam-installer / steam-libs-i386 if missing (pkexec)."""
    if not _which("dpkg", which) and run is None:
        return Step("i386", "32-bit Steam libraries", True, "This is not a Debian / Ubuntu system, so this step was skipped.")
    already_arch = _dpkg_has_i386(run)
    missing = [p for p in I386_PACKAGES if not _pkg_installed(p, run)]
    if already_arch and not missing:
        return Step("i386", "32-bit Steam libraries", True,
                    "32-bit support is on, and steam-installer and steam-libs-i386 are already installed.")
    script = (
        "set -e; "
        "dpkg --print-foreign-architectures | grep -qx i386 || dpkg --add-architecture i386; "
        "apt-get update -y; "
        "apt-get install -y steam-installer steam-libs-i386"
    )
    r = _run(["pkexec", "sh", "-c", script], run=run, timeout=600)
    text = _out(r)
    if r.returncode == 0:
        return Step("i386", "32-bit Steam libraries", True,
                    "Enabled 32-bit (i386) support and installed steam-installer / steam-libs-i386"
                    + ((" (was missing: " + ", ".join(missing) + ")") if missing else "") + ".",
                    changed=True)
    if r.returncode == 126:
        return Step("i386", "32-bit Steam libraries", False,
                    "The password window was closed, so the 32-bit Steam libraries were not installed. Press Fix Steam again when you are ready.")
    return Step("i386", "32-bit Steam libraries", False,
                "Needed steam-installer / steam-libs-i386 and 32-bit (i386) support. A password box appears for this. "
                + text[-240:], changed=False)


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
            progress: Progress | None = None) -> tuple[list[Step], str]:
    """Diagnose and repair. Returns (steps, verdict). Safe to run again. Never deletes games."""
    home = Path(home) if home else Path.home()
    report = progress or _noop_progress
    kind = detect_kind(home, which=which, run=run)
    steps: list[Step] = []

    report("Checking free space at the Steam folder and in /tmp...")
    ok_space, space_msg, _ = space_ok(home, kind, tmp=tmp)
    steps.append(Step("space", "Free space", ok_space, space_msg))

    if kind == "missing":
        steps.append(Step("steam", "Steam is installed", False,
                          "Steam is not installed yet. Install it from Settings > Install emulators and apps "
                          "(choose everything), or from Software Manager, then sign in and use Settings > Steam games > Rescan."))
        return steps, "Steam is not installed, so nothing was repaired. " + space_msg

    steps.append(Step("steam", "Steam is installed", True,
                      "Found " + ("Flatpak Steam" if kind == "flatpak" else "native Steam" if kind == "native" else "Flatpak and native Steam") + "."))

    if kind in ("flatpak", "both"):
        report("Updating Flatpak Steam and installing the matching 32-bit GL32 runtime...")
        steps.append(install_gl_runtimes(run=run, which=which))
        report("Moving broken first-run Steam files aside (games in steamapps stay)...")
        steps.append(repair_bootstrap(home, now=now))
    if kind in ("native", "both"):
        report("Checking 32-bit (i386) support and steam-installer / steam-libs-i386...")
        steps.append(install_i386(run=run, which=which))
        if kind == "native":
            report("Moving broken first-run Steam files aside (games in steamapps stay)...")
            steps.append(repair_bootstrap(home, now=now))

    report("Finishing...")
    changed = any(s.changed for s in steps)
    failed = [s for s in steps if not s.ok]
    if not ok_space:
        verdict = space_msg + " CodeAque did not start Steam, because there may not be enough room."
    elif failed:
        verdict = "Some steps could not finish: " + "; ".join(s.title + " — " + s.detail for s in failed)
    elif changed:
        verdict = "Steam was repaired. CodeAque will try to start it in Big Picture now."
    else:
        verdict = "Everything CodeAque can check looks fine. CodeAque will try to start Steam in Big Picture now."
    return steps, verdict
