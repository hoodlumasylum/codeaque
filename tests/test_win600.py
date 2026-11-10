import re
from pathlib import Path

from atarilauncher import config, win600

ROOT = Path(__file__).resolve().parents[1]


def test_set_keys_updates_and_adds(tmp_path):
    p = tmp_path / "a.ini"
    p.write_text("[Display]\nFullscreen=False\nOther=1\n[X]\nk=v\n")
    win600.set_keys(p, {"Display": {"Fullscreen": "True", "New": "2"}, "Y": {"z": "1"}})
    t = p.read_text()
    assert "Fullscreen=True" in t and "Other=1" in t and "New=2" in t and "[Y]\nz=1" in t
    assert t.index("New=2") < t.index("[X]")
    c = tmp_path / "r.cfg"
    win600.set_keys(c, {None: {"video_fullscreen": '"true"'}})
    win600.set_keys(c, {None: {"video_fullscreen": '"true"'}})
    assert c.read_text().count("video_fullscreen") == 1


def test_handheld_turned_on(tmp_path):
    p = tmp_path / "config.toml"
    win600.turn_on_handheld(p)
    assert config.load_config(p).handheld is True


def test_installer_names_are_known_and_no_pc_launchers():
    script = (ROOT / "scripts/install-win600.sh").read_text()
    body = script.split("--yes", 1)[1].split("||", 1)[0]
    names = re.findall(r"[a-z0-9]+", body)
    import subprocess, sys
    out = subprocess.run([sys.executable, "-m", "atarilauncher.installer", "--list"], cwd=ROOT,
                         capture_output=True, text=True).stdout
    known = {l.split()[0] for l in out.splitlines() if l.strip()}
    for n in names:
        assert n in known, n
    for pc in ("steam", "lutris", "heroic", "eaapp", "rockstar", "bottles"):
        assert pc not in names


def test_no_bios_or_roms_bundled():
    t = (ROOT / "scripts/install-win600.sh").read_text().lower()
    assert "no bios" in t


def test_updates_refresh_emulator_settings_on_win600_setups(tmp_path, monkeypatch):
    monkeypatch.setattr(win600, "MARKER", tmp_path / "win600-setup")
    monkeypatch.setattr(win600, "SETTINGS", {tmp_path / "r.cfg": {None: {"video_fullscreen": '"true"'}}})
    monkeypatch.setattr(win600, "turn_on_handheld", lambda *a: (_ for _ in ()).throw(AssertionError("must not change Handheld mode")))
    assert win600.main(["--emulators-only"]) == 0
    assert 'video_fullscreen = "true"' in (tmp_path / "r.cfg").read_text() and not (tmp_path / "win600-setup").exists()
    sh = (ROOT / "install.sh").read_text()
    assert "win600-setup" in sh and "atarilauncher.win600 --emulators-only" in sh


def test_no_terminal_installer_checks_the_release_and_needs_no_terminal():
    d = (ROOT / "packaging/CodeAque-Install.desktop").read_text()
    assert "Terminal=false" in d and "scripts/web-install.sh" in d
    s = (ROOT / "scripts/web-install.sh").read_text()
    assert "sha256sum -c" in s and "--no-system-packages" in s and "pkexec" in s and "sudo" not in s


def test_win600_main_applies_rotation(tmp_path, monkeypatch):
    monkeypatch.setattr(win600, "MARKER", tmp_path / "win600-setup")
    monkeypatch.setattr(win600, "SETTINGS", {})
    monkeypatch.setattr(win600, "turn_on_handheld", lambda *a: None)
    calls = []
    monkeypatch.setattr(win600.rotate, "apply", lambda *a, **k: calls.append(a or ("auto",)) or "ok")
    assert win600.main(["--emulators-only"]) == 0
    assert calls and calls[0][0] == "auto"
