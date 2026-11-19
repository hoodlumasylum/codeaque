"""0.10.85: Grok Bot tile on the PC console home (official Linux app, hidden in Kids mode)."""
from pathlib import Path
from types import SimpleNamespace

from atarilauncher import grokbot, handheld
from atarilauncher.library import demo_library


def test_official_urls_are_the_documented_ones():
    assert grokbot.DOWNLOAD_URL == "https://cursor.com/download/bot"
    assert grokbot.ANDROID_PACKAGE == "ai.x.grok.bot"
    assert grokbot.PLAY_STORE_URL == "https://play.google.com/store/apps/details?id=ai.x.grok.bot"
    assert "official" in grokbot.NOT_INSTALLED_TOAST.lower()
    assert "web chat" in grokbot.NOT_INSTALLED_TOAST.lower()


def test_installed_command_uses_which_then_official_paths(tmp_path, monkeypatch):
    assert grokbot.installed_command(which=lambda _n: None) is None

    binpath = tmp_path / "grok-bot"
    binpath.write_text("#!/bin/sh\n")
    binpath.chmod(0o755)
    assert grokbot.installed_command(which=lambda n: str(binpath) if n == "grok-bot" else None) == [str(binpath)]

    official = tmp_path / "opt-grok-bot"
    official.write_text("#!/bin/sh\n")
    official.chmod(0o755)
    monkeypatch.setattr(grokbot, "_OFFICIAL_BINARIES", (str(official),))
    assert grokbot.installed_command(which=lambda _n: None) == [str(official)]

    desktop = tmp_path / "grok-bot.desktop"
    desktop.write_text("[Desktop Entry]\nName=Grok Bot\nExec=grok-bot -- %U\n")
    monkeypatch.setattr(grokbot, "_OFFICIAL_BINARIES", ("/no/such/grok-bot",))
    monkeypatch.setattr(grokbot, "_OFFICIAL_DESKTOP", desktop)
    assert grokbot.installed_command(which=lambda n: "/usr/bin/gtk-launch" if n == "gtk-launch" else None) == [
        "gtk-launch", "grok-bot"
    ]


def test_console_home_has_a_grok_bot_tile_hidden_in_kids_mode():
    games = demo_library().games
    cats = {c["key"]: c for c in handheld.categories(games)}
    steam = [t.get("action") or t.get("gid") for t in cats["steam"]["tiles"]]
    assert "grokbot" in steam
    apps = [t.get("action") or t.get("gid") for t in cats["apps"]["tiles"]]
    assert "grokbot" in apps
    tile = next(t for t in cats["steam"]["tiles"] if t.get("action") == "grokbot")
    assert tile["title"] == "Grok Bot" and tile["icon"] == "chat"
    assert not tile.get("art")  # generic glyph, no third-party logo

    hidden = {c["key"]: c for c in handheld.categories(games, kids_mode=True)}
    assert "grokbot" not in [t.get("action") for t in hidden["steam"]["tiles"]]
    assert "grokbot" not in [t.get("action") for t in hidden["apps"]["tiles"]]


def test_open_grok_bot_launches_installed_app_or_official_download(tmp_path, monkeypatch, _qt_app):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.setenv("ATARI_NO_WIFI", "1")
    from atarilauncher import backend as be
    from atarilauncher.config import default_config
    from atarilauncher.store import Store

    cfg = default_config()
    cfg.steam_enabled = False
    cfg.first_run = False
    backend = be.Backend(cfg=cfg, store=Store(tmp_path / "s.json"), enable_gamepad=False)

    launched = []
    monkeypatch.setattr(backend, "_launch_game", lambda g: launched.append(("app", g.gid, g.title)))
    opened = []
    monkeypatch.setattr(backend, "_open_browse_url", lambda url, title, gid="": opened.append((url, title, gid)))
    toasts = []
    monkeypatch.setattr(backend, "show_toast", lambda msg, ms=0: toasts.append(msg))

    monkeypatch.setattr(grokbot, "installed_command", lambda: ["/usr/bin/grok-bot"])
    backend.openGrokBot()
    assert launched == [("app", "app:grokbot", "Grok Bot")]
    assert opened == []

    launched.clear()
    monkeypatch.setattr(grokbot, "installed_command", lambda: None)
    backend.openGrokBot()
    assert launched == []
    assert opened == [(grokbot.DOWNLOAD_URL, "Grok Bot", "web:grokbot")]
    assert any("official" in m.lower() for m in toasts)

    launched.clear(); opened.clear(); toasts.clear()
    backend._kids_mode = True
    backend.openGrokBot()
    assert launched == [] and opened == []
    assert any("Kids mode" in m for m in toasts)


def test_run_installed_starts_the_command(monkeypatch):
    started = []
    procs = []

    class FakeProc:
        def wait(self):
            return 0

    def fake_popen(cmd, **kwargs):
        procs.append((cmd, kwargs.get("start_new_session")))
        return FakeProc()

    monkeypatch.setattr(grokbot, "installed_command", lambda: ["/usr/bin/grok-bot"])
    monkeypatch.setattr(grokbot, "subprocess", SimpleNamespace(Popen=fake_popen, DEVNULL=object()))
    # run_installed imports subprocess.Popen as default; pass popen=
    secs, err = grokbot.run_installed(lambda: started.append(True), env={"PATH": "/usr/bin"}, popen=fake_popen)
    assert secs == 0.0 and err == "" and started == [True]
    assert procs[0][0] == ["/usr/bin/grok-bot"] and procs[0][1] is True
