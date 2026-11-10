"""0.10.33: one-press Fix Steam never deletes games and uses the right console command."""
from pathlib import Path
import subprocess

from atarilauncher import steamrepair
from atarilauncher.launcher import bigpicture_command


def test_fix_steam_repairs_without_touching_games(tmp_path, monkeypatch):
    home = tmp_path / "home"
    steam = home / ".var/app/com.valvesoftware.Steam/.local/share/Steam"
    (steam / "ubuntu12_32").mkdir(parents=True)
    (steam / "ubuntu12_32" / "steam").write_text("bootstrap")
    (steam / "package").mkdir()
    (steam / "package" / "steam_cmd_linux.installed").write_text("x")
    games = steam / "steamapps" / "common" / "Celeste"
    games.mkdir(parents=True)
    (games / "Celeste.exe").write_bytes(b"game")
    lib = steam / "steamapps" / "libraryfolders.vdf"
    lib.write_text('"libraryfolders" { }')
    tmp = tmp_path / "tmp"
    tmp.mkdir()

    calls = []

    def run(cmd, timeout=None, env=None):
        calls.append(list(cmd))
        joined = " ".join(cmd)
        if cmd[:2] == ["flatpak", "info"] and cmd[-1] == "com.valvesoftware.Steam":
            if "--show-runtime" in cmd:
                return subprocess.CompletedProcess(cmd, 0, "org.freedesktop.Platform/x86_64/23.08\n", "")
            return subprocess.CompletedProcess(cmd, 0, "Steam\n", "")
        if cmd[:2] == ["flatpak", "info"] and "GL32.default//23.08" in joined:
            return subprocess.CompletedProcess(cmd, 1, "", "Not installed")
        if cmd[:2] == ["flatpak", "update"]:
            return subprocess.CompletedProcess(cmd, 0, "Updated.\n", "")
        if cmd[:2] == ["flatpak", "install"] and "GL32.default//23.08" in joined:
            return subprocess.CompletedProcess(cmd, 0, "Installed.\n", "")
        return subprocess.CompletedProcess(cmd, 1, "", "unexpected: " + joined)

    seen = []
    steps, verdict = steamrepair.run_fix(
        home=home, run=run, which=lambda n: f"/usr/bin/{n}" if n == "flatpak" else None,
        now=lambda: 1_700_000_000, tmp=tmp, progress=seen.append,
    )
    by_id = {s.id: s for s in steps}

    assert by_id["space"].ok and "free" in by_id["space"].detail.lower()
    assert str(steam) in by_id["space"].detail and str(tmp) in by_id["space"].detail
    assert by_id["steam"].ok and "Flatpak" in by_id["steam"].detail
    assert by_id["gl"].ok and by_id["gl"].changed
    assert any(c[:2] == ["flatpak", "update"] and "com.valvesoftware.Steam" in c for c in calls)
    assert any("org.freedesktop.Platform.GL32.default//23.08" in " ".join(c) for c in calls)
    # A different Platform branch must not be requested.
    assert not any("22.08" in " ".join(c) or "24.08" in " ".join(c) for c in calls)

    assert by_id["bootstrap"].ok and by_id["bootstrap"].changed
    assert not (steam / "ubuntu12_32").exists() and not (steam / "package").exists()
    moved = sorted(p.name for p in steam.iterdir() if p.name.startswith("ubuntu12_32.broken-") or p.name.startswith("package.broken-"))
    assert len(moved) == 2 and all((steam / n).is_dir() for n in moved)
    assert (games / "Celeste.exe").read_bytes() == b"game"
    assert lib.read_text() == '"libraryfolders" { }'
    assert "steamapps were not touched" in by_id["bootstrap"].detail

    assert seen and "free space" in seen[0].lower()
    assert "repaired" in verdict.lower() or "start" in verdict.lower()

    # Native path: enable i386 and install steam-installer / steam-libs-i386 only when missing.
    native_home = tmp_path / "native-home"
    (native_home / ".local/share/Steam/steamapps/common/Keep").mkdir(parents=True)
    (native_home / ".local/share/Steam/steamapps/common/Keep/game.bin").write_text("ok")
    (native_home / ".local/share/Steam/ubuntu12_32").mkdir()
    native_calls = []

    def native_run(cmd, timeout=None, env=None):
        native_calls.append(list(cmd))
        if cmd[:2] == ["dpkg", "--print-foreign-architectures"]:
            return subprocess.CompletedProcess(cmd, 0, "\n", "")
        if cmd[0] == "dpkg-query":
            return subprocess.CompletedProcess(cmd, 1, "", "not installed")
        if cmd[0] == "pkexec" and "steam-libs-i386" in " ".join(cmd) and "steam-installer" in " ".join(cmd) and "dpkg --add-architecture i386" in " ".join(cmd):
            return subprocess.CompletedProcess(cmd, 0, "done\n", "")
        return subprocess.CompletedProcess(cmd, 1, "", "unexpected: " + " ".join(cmd))

    nsteps, _ = steamrepair.run_fix(
        home=native_home, run=native_run, which=lambda n: f"/usr/bin/{n}" if n in ("steam", "dpkg") else None,
        now=lambda: 1_700_000_000, tmp=tmp,
    )
    nids = {s.id: s for s in nsteps}
    assert nids["i386"].ok and nids["i386"].changed
    assert any(c[0] == "pkexec" for c in native_calls)
    assert (native_home / ".local/share/Steam/steamapps/common/Keep/game.bin").read_text() == "ok"
    assert not (native_home / ".local/share/Steam/ubuntu12_32").exists()

    # Console / CodeAque-only launch uses -bigpicture (Flatpak and native).
    assert steamrepair.console_command(["steam"], running=False) == ["steam", "-bigpicture", "-gamepadui"]
    assert steamrepair.console_command(["flatpak", "run", "com.valvesoftware.Steam"], False) == [
        "flatpak", "run", "com.valvesoftware.Steam", "-bigpicture", "-gamepadui",
    ]
    assert steamrepair.console_command(["steam"], running=True) == ["steam", "steam://open/bigpicture"]
    assert bigpicture_command(None, ["steam"], False) == ["steam", "-bigpicture", "-gamepadui"]
    assert bigpicture_command(None, ["flatpak", "run", "com.valvesoftware.Steam"], False) == [
        "flatpak", "run", "com.valvesoftware.Steam", "-bigpicture", "-gamepadui",
    ]
