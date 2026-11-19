"""Progress for Steam's own installer and first-run update.

Steam often sits for several minutes downloading its bootstrap / runtime with
only a small or hidden window, which looks like a failure. CodeAque reads
Steam's bootstrap log (and apt / steam stdout) and shows a large progress bar.

The dialog "Couldn't set up the Steam Runtime. Are you running low on disk
space?" can appear while that download is still running. In that case the
status tells the user to wait — it is usually not a full disk.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

LOG_NAMES = (
    "logs/bootstrap_log.txt",
    "logs/updateui_child.txt",
    "logs/stderr.txt",
    "logs/console-linux.txt",
    "logs/console_log.txt",
)

_DOWNLOAD = re.compile(
    r"(?i)(?:\[\s*(\d{1,3})\s*%\s*\]\s*)?downloading update\s*"
    r"\(\s*([\d,.\u00a0\s]+)\s+(?:of|de|von|di|из|sur)\s+([\d,.\u00a0\s]+)\s*K?B\s*\)",
)
_BRACKET_PCT = re.compile(r"\[\s*(\d{1,3})\s*%\s*\]")
_PCT_COMPLETE = re.compile(r"(?i)\b(\d{1,3})\s*%\s*complete")
_APT_PCT = re.compile(r"(?i)Progress:\s*\[\s*(\d{1,3})\s*%")
_KB_NUM = re.compile(r"[^\d]")
_RUNTIME_ERR = re.compile(
    r"(?i)could(?:n['’]?t| not) set up the steam runtime|are you running low on disk space",
)

_STAGES: tuple[tuple[str, str, str], ...] = (
    (r"(?i)verifying installation", "verify", "Steam is verifying the installation..."),
    (r"(?i)checking for available updates", "check", "Steam is checking for updates..."),
    (r"(?i)extracting package", "extract", "Steam is extracting the update..."),
    (r"(?i)installing update", "install", "Steam is installing the update..."),
    (r"(?i)download complete", "downloaded", "Steam finished downloading the update..."),
    (r"(?i)update complete", "ready", "Steam update complete, launching..."),
    (r"(?i)verification complete", "ready", "Steam is ready."),
    (r"(?i)nothing to do", "ready", "Steam is ready."),
    (r"(?i)preparing to update", "prepare", "Steam is preparing its update..."),
    (r"(?i)downloading update(?!\s*\()", "download", "Steam is downloading its update..."),
    (r"(?i)^(?:setting up|unpacking|get:\d|retrieving|fetched )\b", "apt", "Installing Steam packages..."),
)

WAIT_TEXT = (
    "If Steam shows “Couldn't set up the Steam Runtime”, wait — it is still "
    "downloading its update. That message is often wrong while the download is running, and it is usually not a full disk."
)


@dataclass
class Progress:
    stage: str = "start"
    text: str = "Steam is starting..."
    fraction: float | None = None          # 0..1, or None for an animated bar
    percent_text: str = ""
    detail: str = ""
    still_downloading: bool = False
    runtime_error: bool = False
    ready: bool = False

    @property
    def wait_for_download(self) -> bool:
        return self.runtime_error and self.still_downloading

    def row(self, *, big: bool = True) -> dict:
        """A MenuList progress row (kind=progress). fraction -1 means the moving bar."""
        title = self.text
        if self.wait_for_download:
            title = "Steam is downloading its update — please wait"
        return {
            "kind": "progress",
            "title": title,
            "fraction": -1.0 if self.fraction is None else float(self.fraction),
            "value": self.percent_text,
            "detail": (WAIT_TEXT if self.wait_for_download else self.detail) or "",
            "big": big,
        }


def starting(text: str = "Steam is starting... This can take a few minutes the first time.") -> Progress:
    return Progress(stage="start", text=text)


def ready(text: str = "Steam is ready.") -> Progress:
    return Progress(stage="ready", text=text, fraction=1.0, percent_text="100%", ready=True)


def _kb(raw: str) -> int:
    digits = _KB_NUM.sub("", raw or "")
    return int(digits) if digits else 0


def _pct(n: int) -> tuple[float, str]:
    n = max(0, min(100, n))
    return n / 100.0, f"{n}%"


def parse_line(line: str) -> Progress | None:
    """Turn one apt / Steam bootstrap / stdout line into progress, or None if it is uninteresting."""
    s = (line or "").strip()
    if not s:
        return None
    runtime = bool(_RUNTIME_ERR.search(s))

    m = _DOWNLOAD.search(s)
    if m:
        bracket, done_s, total_s = m.group(1), m.group(2), m.group(3)
        done, total = _kb(done_s), _kb(total_s)
        if bracket is not None:
            frac, pct = _pct(int(bracket))
        elif total > 0:
            frac, pct = _pct(int(round(100.0 * min(done, total) / total)))
        else:
            frac, pct = None, ""
        detail = f"{done:,} of {total:,} KB" if total or done else ""
        text = f"Steam is downloading its update... {pct}".rstrip()
        return Progress(stage="download", text=text if pct else "Steam is downloading its update...",
                        fraction=frac, percent_text=pct, detail=detail,
                        still_downloading=True, runtime_error=runtime)

    m = _APT_PCT.search(s) or _PCT_COMPLETE.search(s)
    if m:
        frac, pct = _pct(int(m.group(1)))
        return Progress(stage="apt" if _APT_PCT.search(s) else "download",
                        text=("Installing Steam packages... " + pct if _APT_PCT.search(s)
                              else f"Steam is downloading its update... {pct}"),
                        fraction=frac, percent_text=pct, runtime_error=runtime,
                        still_downloading=not bool(_APT_PCT.search(s)))

    m = _BRACKET_PCT.search(s)
    if m and re.search(r"(?i)download|update|steam", s):
        frac, pct = _pct(int(m.group(1)))
        return Progress(stage="download", text=f"Steam is downloading its update... {pct}",
                        fraction=frac, percent_text=pct, still_downloading=True, runtime_error=runtime)

    for pat, stage, text in _STAGES:
        if re.search(pat, s):
            ready_ = stage == "ready"
            return Progress(stage=stage, text=text, fraction=1.0 if ready_ else None,
                            percent_text="100%" if ready_ else "",
                            still_downloading=stage in ("download", "prepare"),
                            runtime_error=runtime, ready=ready_)

    if runtime:
        return Progress(stage="runtime", text="Steam mentioned the Steam Runtime...",
                        runtime_error=True, still_downloading=False)
    return None


def parse_text(text: str) -> Progress | None:
    """Latest meaningful progress in a block of log / stdout text."""
    latest: Progress | None = None
    saw_runtime = False
    for line in (text or "").splitlines():
        p = parse_line(line)
        if p is None:
            continue
        if p.runtime_error:
            saw_runtime = True
        if latest is not None and p.stage == "runtime" and latest.still_downloading:
            # Keep the download numbers; only mark the runtime warning.
            latest = Progress(**{**latest.__dict__, "runtime_error": True})
            continue
        latest = p
    if latest is not None and saw_runtime:
        latest = Progress(**{**latest.__dict__, "runtime_error": True})
        if latest.wait_for_download:
            latest = Progress(**{**latest.__dict__,
                                 "text": "Steam is downloading its update — please wait",
                                 "detail": WAIT_TEXT})
    return latest


def log_paths(home: Path | None = None) -> list[Path]:
    home = home or Path.home()
    roots = [
        home / ".local/share/Steam",
        home / ".steam/steam",
        home / ".steam/root",
        home / ".var/app/com.valvesoftware.Steam/.local/share/Steam",
        home / "snap/steam/common/.local/share/Steam",
    ]
    seen: set[Path] = set()
    out: list[Path] = []
    for root in roots:
        for name in LOG_NAMES:
            p = root / name
            try:
                key = p.resolve() if p.exists() else p
            except OSError:
                key = p
            if key in seen:
                continue
            seen.add(key)
            out.append(p)
    return out


def read_tail(path: Path, max_bytes: int = 64_000) -> str:
    try:
        size = path.stat().st_size
        with path.open("rb") as fh:
            if size > max_bytes:
                fh.seek(size - max_bytes)
            data = fh.read()
        return data.decode("utf-8", errors="replace")
    except OSError:
        return ""


def from_logs(home: Path | None = None, extra: str = "") -> Progress:
    """Combine every Steam bootstrap log plus optional stdout. Falls back to a starting status."""
    chunks = [read_tail(p) for p in log_paths(home)]
    if extra:
        chunks.append(extra)
    latest = parse_text("\n".join(chunks))
    return latest or starting()


class Watcher:
    """Poll Steam's logs from a start moment so a previous session's download is ignored."""

    def __init__(self, home: Path | None = None, started_at: float | None = None,
                 extra_paths: Iterable[Path] = ()):
        self.home = Path(home) if home else Path.home()
        self.started_at = started_at
        self.extra_paths = [Path(p) for p in extra_paths]
        self._sizes: dict[Path, int] = {}
        self._buf: dict[Path, str] = {}
        self.latest = starting()
        # Snapshot existing sizes so we only read bytes written after we started.
        for p in list(log_paths(self.home)) + self.extra_paths:
            try:
                self._sizes[p] = p.stat().st_size
            except OSError:
                self._sizes[p] = 0

    def poll(self, extra: str = "", ui_ready: bool = False) -> Progress:
        new_bits: list[str] = []
        for p in list(log_paths(self.home)) + self.extra_paths:
            try:
                size = p.stat().st_size
            except OSError:
                continue
            prev = self._sizes.get(p, 0)
            if size < prev:
                prev = 0  # truncated / rotated
            if size > prev:
                try:
                    with p.open("rb") as fh:
                        fh.seek(prev)
                        chunk = fh.read(size - prev).decode("utf-8", errors="replace")
                    new_bits.append(chunk)
                    self._buf[p] = self._buf.get(p, "") + chunk
                except OSError:
                    pass
            self._sizes[p] = size
        if extra:
            new_bits.append(extra)
        parsed = parse_text("\n".join(new_bits)) if new_bits else None
        if parsed is not None:
            if parsed.stage == "runtime" and self.latest.still_downloading:
                self.latest = Progress(**{**self.latest.__dict__, "runtime_error": True,
                                          "text": "Steam is downloading its update — please wait",
                                          "detail": WAIT_TEXT})
            else:
                if self.latest.runtime_error and not parsed.runtime_error:
                    parsed = Progress(**{**parsed.__dict__, "runtime_error": True})
                self.latest = parsed
                if self.latest.wait_for_download:
                    self.latest = Progress(**{**self.latest.__dict__,
                                              "text": "Steam is downloading its update — please wait",
                                              "detail": WAIT_TEXT})
        if ui_ready and not self.latest.still_downloading:
            self.latest = ready()
        elif ui_ready and self.latest.still_downloading:
            # A window can appear while the update is still running — keep the bar.
            pass
        return self.latest
