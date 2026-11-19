"""Ten taps on the home-screen CodeAque logo, within about five seconds, toggle start mode.

The first four taps are silent. From the fifth, a countdown toast is shown
("5 more taps to switch to Linux mode"). The tenth opens a confirm page; the
actual switch reuses the Settings start-mode path (password box, backups, read-back).
"""
from __future__ import annotations

WINDOW = 5.0
NEED = 10
HINT_AFTER = 5


def target_label(want: str) -> str:
    return "Linux mode" if want == "desktop" else "CodeAque-only mode"


def want_from_next(nxt: str) -> str | None:
    """The mode a toggle would switch *to*, or None if start mode is unclear."""
    if nxt == "console":
        return "desktop"
    if nxt == "desktop":
        return "console"
    return None


def countdown_text(left: int, want: str) -> str:
    word = "tap" if left == 1 else "taps"
    return f"{left} more {word} to switch to {target_label(want)}"


class Counter:
    """Sliding 5-second window of logo taps. Pure, so the tests do not need Qt."""

    def __init__(self, window: float = WINDOW, need: int = NEED, hint_after: int = HINT_AFTER) -> None:
        self.window = window
        self.need = need
        self.hint_after = hint_after
        self.taps: list[float] = []

    def reset(self) -> None:
        self.taps.clear()

    def tap(self, now: float, nxt: str) -> dict:
        want = want_from_next(nxt)
        cutoff = now - self.window
        self.taps = [t for t in self.taps if t > cutoff]
        self.taps.append(now)
        n = len(self.taps)
        if want is None:
            self.reset()
            return {"action": "broken", "count": 0}
        if n < self.hint_after:
            return {"action": "silent", "count": n, "want": want}
        left = self.need - n
        if left > 0:
            return {
                "action": "toast",
                "count": n,
                "want": want,
                "left": left,
                "text": countdown_text(left, want),
            }
        self.reset()
        return {"action": "confirm", "count": self.need, "want": want}
