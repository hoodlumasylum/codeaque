"""Old pieces of CodeAque that an update must not leave behind (run by install.sh, so every in-app update does it).

  * menu / autostart / desktop shortcuts (~/.local/share/applications, ~/.config/autostart, ~/Desktop) that start ANOTHER copy of
    CodeAque (an old folder, a git checkout, a USB copy) are pointed at this copy's run.sh;
  * an intro video you chose in Settings that is byte-for-byte one of CodeAque's OWN older intros is removed, so the current one plays
    (any other video of yours is kept);
  * other copies of CodeAque on this computer are listed (never deleted) so Settings can say where they are.
Nothing here needs a password. Pure Python, paths injectable for the tests."""
from __future__ import annotations

import hashlib
import os
import re
import sys
from pathlib import Path

# sha256 of every intro video that shipped with CodeAque before (0.4.0, 0.4.1, 0.9.0); the current one is not listed
OLD_INTROS = {
    "610b61964a4544da8a551ca4e8bb01c2d63b4ae11e4c808d77253fe07e8213c7", "602023ee6e14728880e4690cb0e81c8144f64a1038138847456be2c1014e3e02", "61164f0b78877e11d5d690b36b6f99d804a0def684acd049f05400b513e4e822",
}
MARKERS = ("atarilauncher", "atari-launcher", "codeaque", "/atari/run.sh", "atari/run.sh")


def _home(home: Path | None) -> Path:
    return home or Path.home()


def launcher_files(home: Path | None = None) -> list[Path]:
    h = _home(home)
    cfg = Path(os.environ.get("XDG_CONFIG_HOME") or h / ".config") if home is None else h / ".config"
    data = Path(os.environ.get("XDG_DATA_HOME") or h / ".local/share") if home is None else h / ".local/share"
    out: list[Path] = []
    for d in (data / "applications", cfg / "autostart", h / "Desktop", h / "Schreibtisch"):
        if d.is_dir():
            out += sorted(d.glob("*.desktop"))
    return out


def _target(exec_line: str) -> str | None:
    """The CodeAque start path an Exec= line runs, or None when it is not CodeAque."""
    for tok in re.split(r"\s+", exec_line.strip()):
        t = tok.strip("'\"")
        if t.endswith("/run.sh") and any(m in t.lower() for m in ("atari", "codeaque")):
            return t
        if t.endswith("/.venv/bin/python") and any(m in t.lower() for m in ("atari", "codeaque")):
            return t
    return None


def fix_launchers(run_sh: Path, home: Path | None = None) -> list[Path]:
    """Point every CodeAque shortcut that starts another copy at run_sh. Returns the files changed."""
    good = os.path.realpath(str(run_sh))
    changed = []
    for f in launcher_files(home):
        if f.name == "CodeAque-Install.desktop":
            continue
        try:
            text = f.read_text(errors="replace")
        except OSError:
            continue
        m = re.search(r"^Exec=(.*)$", text, re.M)
        if not m:
            continue
        t = _target(m.group(1))
        if not t:
            continue
        real = os.path.realpath(os.path.expanduser(t))
        if real == good or (t.endswith("/run.sh") and real == good):
            continue
        new = text[:m.start()] + "Exec=" + str(run_sh) + text[m.end():]
        try:
            f.write_text(new)
            changed.append(f)
        except OSError:
            pass
    return changed


def _sha(f: Path) -> str:
    h = hashlib.sha256()
    with open(f, "rb") as fh:
        for b in iter(lambda: fh.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def drop_old_intro(data_dir: Path | None = None) -> list[Path]:
    from . import intro, paths
    base = data_dir or paths.data_dir()
    gone = []
    for e in intro.VIDEO_EXT:
        f = base / f"intro{e}"
        try:
            if f.is_file() and _sha(f) in OLD_INTROS:
                f.unlink()
                gone.append(f)
        except OSError:
            pass
    return gone


def other_copies(app_dir: Path, home: Path | None = None) -> list[Path]:
    """Folders with another CodeAque (atarilauncher/ + run.sh) - listed, never touched."""
    h = _home(home)
    seen = []
    cands = [h / n for n in ("atari", "codeaque", "atari-launcher", "Downloads/atari", "Desktop/atari")]
    cands += [h / ".local/share/atari-launcher", Path("/opt/atari"), Path("/opt/codeaque"), Path("/opt/atari-launcher")]
    for c in cands:
        try:
            if (c / "run.sh").is_file() and (c / "atarilauncher").is_dir() and os.path.realpath(c) != os.path.realpath(app_dir):
                seen.append(c)
        except OSError:
            pass
    return seen


def main(argv=None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    app_dir = Path(argv[0]).resolve() if argv else Path(__file__).resolve().parent.parent
    run_sh = app_dir / "run.sh"
    if not run_sh.is_file() or "/atari-update-" in str(app_dir):
        print("  (leftover check skipped: no installed CodeAque folder given)")
        return 0
    for f in fix_launchers(run_sh):
        print(f"  shortcut now starts this copy: {f}")
    for f in drop_old_intro():
        print(f"  removed an old CodeAque intro video: {f}")
    for c in other_copies(app_dir):
        print(f"  note: another (older) CodeAque copy is in {c} - nothing starts it now; you can delete that folder.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
