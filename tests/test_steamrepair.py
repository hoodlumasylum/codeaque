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
    (steam / "ubuntu12_32" / "steam-runtime").mkdir()
    (steam / "ubuntu12_32" / "steam-runtime" / "run.sh").write_text("broken")
    (steam / "ubuntu12_32" / "steam-runtime.tar.xz.part").write_text("half")
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
    assert (steam / "ubuntu12_32" / "steam").read_text() == "bootstrap"
    assert not (steam / "ubuntu12_32" / "steam-runtime").exists()
    assert not (steam / "ubuntu12_32" / "steam-runtime.tar.xz.part").exists()
    moved = sorted(p.name for p in (steam / "ubuntu12_32").iterdir() if "broken-" in p.name)
    assert "steam-runtime.broken-20231114-221320" in moved or any(n.startswith("steam-runtime.broken-") for n in moved)
    assert any(n.startswith("steam-runtime.tar.xz.part.broken-") for n in moved)
    assert (games / "Celeste.exe").read_bytes() == b"game"
    assert lib.read_text() == '"libraryfolders" { }'
    assert "steamapps were not touched" in by_id["bootstrap"].detail
    assert by_id["proton"].ok  # no config.vdf yet: still a soft success

    assert seen and "free space" in seen[0].lower()
    assert "repaired" in verdict.lower() or "start" in verdict.lower()

    # Native path: enable i386 and install steam-installer / steam-libs-i386 only when missing.
    native_home = tmp_path / "native-home"
    (native_home / ".local/share/Steam/steamapps/common/Keep").mkdir(parents=True)
    (native_home / ".local/share/Steam/steamapps/common/Keep/game.bin").write_text("ok")
    (native_home / ".local/share/Steam/ubuntu12_32/steam-runtime").mkdir(parents=True)
    (native_home / ".local/share/Steam/ubuntu12_32/steam-runtime/run.sh").write_text("broken")
    # a config.vdf so Proton can be turned on
    cfg = native_home / ".local/share/Steam/config/config.vdf"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    cfg.write_text('"InstallConfigStore"\n{\n\t"Software"\n\t{\n\t\t"Valve"\n\t\t{\n\t\t\t"Steam"\n\t\t\t{\n\t\t\t}\n\t\t}\n\t}\n}\n')
    native_calls = []

    def native_run(cmd, timeout=None, env=None):
        native_calls.append(list(cmd))
        if cmd[:2] == ["dpkg", "--print-foreign-architectures"]:
            return subprocess.CompletedProcess(cmd, 0, "\n", "")
        if cmd[0] == "dpkg-query":
            return subprocess.CompletedProcess(cmd, 1, "", "not installed")
        joined = " ".join(cmd)
        if cmd[0] == "pkexec" and "dpkg --add-architecture i386" in joined and "libc6:i386" in joined and "libgl1:i386" in joined and "libgl1-mesa-dri:i386" in joined and "steam-installer" in joined:
            return subprocess.CompletedProcess(cmd, 0, "done\n", "")
        return subprocess.CompletedProcess(cmd, 1, "", "unexpected: " + " ".join(cmd))

    nsteps, _ = steamrepair.run_fix(
        home=native_home, run=native_run, which=lambda n: f"/usr/bin/{n}" if n in ("steam", "dpkg") else None,
        now=lambda: 1_700_000_000, tmp=tmp,
    )
    nids = {s.id: s for s in nsteps}
    assert nids["i386"].ok and nids["i386"].changed
    assert any(c[0] == "pkexec" for c in native_calls)
    assert "libc6:i386" in " ".join(" ".join(c) for c in native_calls)
    assert (native_home / ".local/share/Steam/steamapps/common/Keep/game.bin").read_text() == "ok"
    assert (native_home / ".local/share/Steam/ubuntu12_32").is_dir()
    assert not (native_home / ".local/share/Steam/ubuntu12_32/steam-runtime").exists()
    assert any(p.name.startswith("steam-runtime.broken-") for p in (native_home / ".local/share/Steam/ubuntu12_32").iterdir())
    assert nids["proton"].ok and nids["proton"].changed
    from atarilauncher import steamfix
    data = steamfix.parse(cfg.read_text())
    assert data["InstallConfigStore"]["Software"]["Valve"]["Steam"]["CompatToolMapping"]["0"]["name"] == "proton_experimental"

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


def test_pkexec_refusal_returns_exact_sudo_command(tmp_path):
    home = tmp_path / "home"
    (home / ".local/share/Steam/steamapps").mkdir(parents=True)
    tmp = tmp_path / "tmp"
    tmp.mkdir()

    def run(cmd, timeout=None, env=None):
        if cmd[:2] == ["dpkg", "--print-foreign-architectures"]:
            return subprocess.CompletedProcess(cmd, 0, "", "")
        if cmd[0] == "dpkg-query":
            return subprocess.CompletedProcess(cmd, 1, "", "not installed")
        if cmd[0] == "pkexec":
            return subprocess.CompletedProcess(cmd, 126, "", "The password window was closed")
        return subprocess.CompletedProcess(cmd, 1, "", "unexpected")

    steps, verdict = steamrepair.run_fix(
        home=home, run=run, which=lambda n: f"/usr/bin/{n}" if n in ("steam", "dpkg") else None,
        tmp=tmp,
    )
    by_id = {s.id: s for s in steps}
    assert not by_id["i386"].ok
    cmd = steamrepair.sudo_command()
    assert by_id["i386"].commands == cmd
    assert cmd in by_id["i386"].detail
    assert "sudo dpkg --add-architecture i386" in cmd
    assert "libc6:i386" in cmd and "libgl1:i386" in cmd and "libgl1-mesa-dri:i386" in cmd
    assert cmd in verdict
    # Games path must still exist; runtime clear is user-level and may still run.
    assert (home / ".local/share/Steam/steamapps").is_dir()


def test_fix_installs_steam_when_missing_and_never_deletes_games(tmp_path):
    home = tmp_path / "home"
    home.mkdir()
    tmp = tmp_path / "tmp"
    tmp.mkdir()
    calls = []

    def run(cmd, timeout=None, env=None):
        calls.append(list(cmd))
        if cmd[:2] == ["dpkg", "--print-foreign-architectures"]:
            return subprocess.CompletedProcess(cmd, 0, "", "")
        if cmd[0] == "dpkg-query":
            return subprocess.CompletedProcess(cmd, 1, "", "not installed")
        if cmd[0] == "pkexec":
            return subprocess.CompletedProcess(cmd, 0, "installed\n", "")
        return subprocess.CompletedProcess(cmd, 1, "", "unexpected")

    # After pkexec "installs" Steam, detect_kind still needs a steam binary or data dir.
    # Simulate the package appearing by creating the native folder from the pkexec call.
    def run_and_create(cmd, timeout=None, env=None):
        r = run(cmd, timeout, env)
        if cmd[0] == "pkexec":
            (home / ".local/share/Steam/steamapps/common").mkdir(parents=True)
            (home / ".local/share/Steam/ubuntu12_32/steam-runtime").mkdir(parents=True)
        return r

    which_state = {"steam": False}

    def which(n):
        if n == "dpkg":
            return "/usr/bin/dpkg"
        if n == "steam" and which_state["steam"]:
            return "/usr/bin/steam"
        return None

    def run_flip(cmd, timeout=None, env=None):
        r = run_and_create(cmd, timeout, env)
        if cmd[0] == "pkexec":
            which_state["steam"] = True
        return r

    steps, verdict = steamrepair.run_fix(home=home, run=run_flip, which=which, tmp=tmp, now=lambda: 1_700_000_000)
    by_id = {s.id: s for s in steps}
    assert by_id["i386"].ok and by_id["i386"].changed
    assert by_id["steam"].ok
    assert by_id["bootstrap"].changed
    assert not (home / ".local/share/Steam/ubuntu12_32/steam-runtime").exists()
    assert (home / ".local/share/Steam/steamapps/common").is_dir()
    assert any(c[0] == "pkexec" and "steam-installer" in " ".join(c) for c in calls)


def test_repair_bootstrap_never_touches_steamapps(tmp_path):
    home = tmp_path / "home"
    steam = home / ".local/share/Steam"
    games = steam / "steamapps" / "common" / "The Quarry"
    games.mkdir(parents=True)
    (games / "Quarry.exe").write_bytes(b"game")
    # a folder that must never be renamed even if we iterate names
    (steam / "steamapps" / "steam-runtime").mkdir()
    (steam / "ubuntu12_32" / "steam-runtime").mkdir(parents=True)
    step = steamrepair.repair_bootstrap(home, now=lambda: 1_700_000_000)
    assert step.ok and step.changed
    assert (games / "Quarry.exe").read_bytes() == b"game"
    assert (steam / "steamapps" / "steam-runtime").is_dir()
    assert not (steam / "ubuntu12_32" / "steam-runtime").exists()
