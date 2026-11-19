"""Backup & Restore: what is packed, what is left out, path rewrite, and restore safety."""
from __future__ import annotations

import json
import os
import tarfile
from datetime import datetime
from pathlib import Path

import pytest

from atarilauncher import backup, usbdrive
from atarilauncher.importer import Drive


def _home(tmp_path: Path) -> Path:
    h = tmp_path / "home" / "code"
    h.mkdir(parents=True)
    (h / "Downloads").mkdir()
    return h


def _seed(home: Path) -> dict[str, Path]:
    """A pretend desktop: CodeAque settings, a fav site, RetroArch saves, a Proton save, plus things that must be skipped."""
    cfg = home / ".config" / "atari"
    data = home / ".local" / "share" / "atari"
    cfg.mkdir(parents=True)
    data.mkdir(parents=True)
    (cfg / "config.toml").write_text(
        'fullscreen = true\nsteamgriddb_key = "SECRETKEY123"\nvolume = 0.6\n', encoding="utf-8"
    )
    (data / "state.json").write_text(json.dumps({"version": 1, "games": {"g1": {"favorite": True}}, "prefs": {"theme": "gold"}}), encoding="utf-8")
    fav = data / "favsites"
    fav.mkdir()
    (fav / "sites.json").write_text('[{"title": "DuckDuckGo", "url": "https://duckduckgo.com"}]', encoding="utf-8")
    (fav / "icons").mkdir()
    (fav / "icons" / "duckduckgo.com.png").write_bytes(b"PNGFAV")
    # RetroArch
    ra = home / ".config" / "retroarch"
    (ra / "saves").mkdir(parents=True)
    (ra / "states").mkdir()
    (ra / "system").mkdir()
    (ra / "saves" / "Hero.srm").write_bytes(b"SAVE")
    (ra / "states" / "Hero.state").write_bytes(b"STATE")
    (ra / "system" / "scph5501.bin").write_bytes(b"BIOS")
    (ra / "retroarch.cfg").write_text(f'savefile_directory = "{home}/.config/retroarch/saves"\n', encoding="utf-8")
    # Proton save (and a shader cache that must be skipped)
    proton = home / ".steam" / "steam" / "steamapps" / "compatdata" / "12345" / "pfx" / "drive_c" / "users" / "steamuser"
    (proton / "Documents" / "TheGame").mkdir(parents=True)
    (proton / "Documents" / "TheGame" / "save.dat").write_bytes(b"PROTON")
    (proton / "AppData" / "Local" / "ShaderCache").mkdir(parents=True)
    (proton / "AppData" / "Local" / "ShaderCache" / "cache.bin").write_bytes(b"CACHE")
    (proton / "AppData" / "Local" / "TheGame").mkdir()
    (proton / "AppData" / "Local" / "TheGame" / "save2.dat").write_bytes(b"APP")
    # Things that must never be packed
    (home / "Games" / "nes").mkdir(parents=True)
    (home / "Games" / "nes" / "Secret.nes").write_bytes(b"NESROM")
    steam_common = home / ".steam" / "steam" / "steamapps" / "common" / "TheGame"
    steam_common.mkdir(parents=True)
    (steam_common / "game.exe").write_bytes(b"GAMEBIN")
    (cfg / "cookies.txt").write_text("browser-login", encoding="utf-8")
    (data / "local-ai" / "runtime" / "bin").mkdir(parents=True)
    (data / "local-ai" / "runtime" / "bin" / "ollama").write_bytes(b"OLLAMA")
    (data / "local-ai" / "notes.txt").write_text("chat notes", encoding="utf-8")
    return {"cfg": cfg, "data": data, "ra": ra}


def test_backup_name_adds_time_when_the_day_file_exists():
    when = datetime(2026, 11, 19, 16, 5)
    assert backup.backup_name(when, []) == "CodeAque-Backup-2026-11-19.tar.gz"
    assert backup.backup_name(when, ["CodeAque-Backup-2026-11-19.tar.gz"]) == "CodeAque-Backup-2026-11-19-1605.tar.gz"
    assert backup.is_backup_name("CodeAque-Backup-2026-11-19.tar.gz")
    assert not backup.is_backup_name("random.tar.gz")


def test_human_size():
    assert backup.human_size(500) == "0.5 KB"
    assert "MB" in backup.human_size(2_000_000)
    assert "GB" in backup.human_size(2_000_000_000)


def test_pick_drive_single_and_none(tmp_path):
    stick = tmp_path / "USB"
    stick.mkdir()
    d = Drive(str(stick), "CODEUSB")
    got, why = backup.pick_drive([d])
    assert got is d and why == ""
    none, msg = backup.pick_drive([])
    assert none is None and "No USB stick" in msg
    a = Drive(str(tmp_path / "A"), "A"); (tmp_path / "A").mkdir()
    b = Drive(str(tmp_path / "B"), "B"); (tmp_path / "B").mkdir()
    got, msg = backup.pick_drive([a, b])
    assert got is None and "Several" in msg


def test_plan_includes_saves_and_excludes_secrets_roms_bios_and_games(tmp_path, monkeypatch):
    home = _home(tmp_path)
    paths = _seed(home)
    monkeypatch.setenv("ATARI_CONFIG_DIR", str(paths["cfg"]))
    monkeypatch.setenv("ATARI_DATA_DIR", str(paths["data"]))
    plan = backup.build_plan(home, config_dir=paths["cfg"], data_dir=paths["data"], include_apps=False)
    labels = [s.label for s in plan.sources]
    assert any("CodeAque settings" in x for x in labels)
    assert any("Fav Sites" in x or "tiles" in x.lower() for x in labels)
    assert any("RetroArch saves" == x for x in labels)
    assert any("Steam save" in x for x in labels)
    files = []
    for s in plan.sources:
        files.extend(backup._iter_files(s.src))
    names = {p.name for p in files}
    rels = {str(p) for p in files}
    assert "Hero.srm" in names and "Hero.state" in names
    assert "sites.json" in names and "save.dat" in names and "save2.dat" in names
    assert "state.json" in names and "config.toml" in names
    assert "scph5501.bin" not in names
    assert "Secret.nes" not in names
    assert "game.exe" not in names
    assert "cookies.txt" not in names
    assert "ollama" not in names
    assert "cache.bin" not in names
    assert "notes.txt" in names
    assert plan.files >= 8


def test_write_and_restore_rewrites_home_and_strips_keys(tmp_path, monkeypatch):
    src_home = _home(tmp_path)
    paths = _seed(src_home)
    monkeypatch.setenv("ATARI_CONFIG_DIR", str(paths["cfg"]))
    monkeypatch.setenv("ATARI_DATA_DIR", str(paths["data"]))
    stick = tmp_path / "USB"
    stick.mkdir()
    res = backup.write_backup(
        stick, home=src_home, config_dir=paths["cfg"], data_dir=paths["data"],
        which=lambda _n: None, when=datetime(2026, 11, 19, 15, 0),
    )
    assert res.ok, res.message
    assert Path(res.path).name == "CodeAque-Backup-2026-11-19.tar.gz"
    assert res.size > 0
    # Inspect the archive: no secret, no bios, no rom, no steam game.
    with tarfile.open(res.path, "r:gz") as tf:
        names = [m.name for m in tf.getmembers() if m.isfile()]
        blob = "\n".join(names)
        assert "manifest.json" in names and "README.txt" in names
        assert any(n.endswith("Hero.srm") for n in names)
        assert "SECRETKEY123" not in tf.extractfile("codeaque/config/config.toml").read().decode()
        assert 'steamgriddb_key = ""' in tf.extractfile("codeaque/config/config.toml").read().decode()
        assert not any("scph5501" in n for n in names)
        assert not any("Secret.nes" in n or "game.exe" in n or "cookies" in n for n in names)
        assert "SECRETKEY123" not in tf.extractfile("README.txt").read().decode()
        man = json.loads(tf.extractfile("manifest.json").read().decode())
        assert man["home"] == str(src_home) and man["format"] == 1

    # Restore onto a different user home (the Win600).
    dest_home = tmp_path / "home" / "win600"
    dest_home.mkdir(parents=True)
    (dest_home / "Downloads").mkdir()
    dest_cfg = dest_home / ".config" / "atari"
    dest_data = dest_home / ".local" / "share" / "atari"
    # Existing file that should be safety-copied, then replaced.
    dest_cfg.mkdir(parents=True)
    dest_data.mkdir(parents=True)
    (dest_cfg / "config.toml").write_text("volume = 0.1\n", encoding="utf-8")
    out = backup.restore_backup(
        Path(res.path), home=dest_home, config_dir=dest_cfg, data_dir=dest_data,
        safety_dir=tmp_path / "safety",
    )
    assert out.ok, out.message
    text = (dest_cfg / "config.toml").read_text(encoding="utf-8")
    assert "SECRETKEY123" not in text
    assert "volume = 0.6" in text
    assert (dest_data / "favsites" / "sites.json").is_file()
    ra_cfg = (dest_home / ".config" / "retroarch" / "retroarch.cfg").read_text(encoding="utf-8")
    assert str(dest_home) in ra_cfg
    assert str(src_home) not in ra_cfg
    assert (dest_home / ".config" / "retroarch" / "saves" / "Hero.srm").read_bytes() == b"SAVE"
    steam_save = dest_home / ".steam" / "steam" / "steamapps" / "compatdata" / "12345" / "pfx" / "drive_c" / "users" / "steamuser" / "Documents" / "TheGame" / "save.dat"
    assert steam_save.read_bytes() == b"PROTON"
    # Safety copy exists and held the old volume.
    safety_files = list((tmp_path / "safety").glob("CodeAque-Backup-*.tar.gz"))
    assert safety_files
    with tarfile.open(safety_files[0], "r:gz") as tf:
        old = tf.extractfile("codeaque/config/config.toml").read().decode()
        assert "volume = 0.1" in old
    # Games were never restored.
    assert not (dest_home / "Games" / "nes" / "Secret.nes").exists()


def test_restore_refuses_unsafe_paths(tmp_path):
    bad = tmp_path / "CodeAque-Backup-2026-01-01.tar.gz"
    with tarfile.open(bad, "w:gz") as tf:
        info = tarfile.TarInfo(name="../../etc/passwd")
        data = b"nope"
        info.size = len(data)
        import io
        tf.addfile(info, io.BytesIO(data))
        man = json.dumps({"format": 1, "home": "/home/x", "entries": [], "apps": []}).encode()
        info2 = tarfile.TarInfo(name="manifest.json")
        info2.size = len(man)
        tf.addfile(info2, io.BytesIO(man))
    dest = tmp_path / "out"
    dest.mkdir()
    res = backup.restore_backup(bad, home=dest, config_dir=dest / "c", data_dir=dest / "d", write_safety=False)
    assert not res.ok and "unsafe" in res.message.lower()


def test_find_backups_on_stick_and_downloads(tmp_path):
    home = _home(tmp_path)
    stick = tmp_path / "USB"
    stick.mkdir()
    a = stick / "CodeAque-Backup-2026-11-01.tar.gz"
    b = home / "Downloads" / "CodeAque-Backup-2026-11-18.tar.gz"
    a.write_bytes(b"1")
    b.write_bytes(b"2")
    (stick / "notes.txt").write_text("no")
    found = backup.find_backups(drives=[Drive(str(stick), "USB")], home=home)
    names = [p.name for p in found]
    assert "CodeAque-Backup-2026-11-18.tar.gz" in names
    assert "CodeAque-Backup-2026-11-01.tar.gz" in names
    os.utime(b, (b.stat().st_atime, b.stat().st_mtime + 10))
    found = backup.find_backups(drives=[Drive(str(stick), "USB")], home=home)
    assert found[0] == b


def test_rewrite_text():
    assert backup.rewrite_text("path=/home/code/games", "/home/code", "/home/win") == "path=/home/win/games"
    assert backup.rewrite_text("same", "/home/code", "/home/code") == "same"


def test_missing_apps_filters_installed(monkeypatch):
    listed = [{"id": "retroarch", "title": "RetroArch"}, {"id": "steam", "title": "Steam"}]
    monkeypatch.setattr(backup.appinstall, "is_installed", lambda t, **k: t.id == "retroarch")
    miss = backup.missing_apps(listed, home=Path("/tmp"), which=lambda _n: None)
    assert [a["id"] for a in miss] == ["steam"]


def test_cli_list_and_backup(tmp_path, monkeypatch, capsys):
    home = _home(tmp_path)
    paths = _seed(home)
    monkeypatch.setenv("ATARI_CONFIG_DIR", str(paths["cfg"]))
    monkeypatch.setenv("ATARI_DATA_DIR", str(paths["data"]))
    monkeypatch.setattr(backup, "find_drives", lambda: [])
    monkeypatch.setattr(Path, "home", classmethod(lambda cls: home))
    # Point default dest at our Downloads by patching Path.home used inside write via explicit dest.
    dest = home / "Downloads"
    rc = backup.main(["backup", "--dest", str(dest)])
    assert rc == 0
    out = capsys.readouterr().out
    assert "Backup saved" in out
    files = list(dest.glob("CodeAque-Backup-*.tar.gz"))
    assert files
    monkeypatch.setattr(backup, "find_backups", lambda **k: files)
    rc = backup.main(["list"])
    assert rc == 0
    assert "CodeAque-Backup-" in capsys.readouterr().out


def test_skip_secret_and_bios_helpers(tmp_path):
    assert backup.skip_reason(tmp_path / "cookies.txt") == "secret"
    assert backup.skip_reason(tmp_path / "scph5501.bin") == "bios"
    assert backup.skip_reason(tmp_path / "Hero.srm") == ""
    d = tmp_path / "system"
    d.mkdir()
    assert backup.skip_reason(d) == "excluded-dir"


# ----- Settings page wiring (needs a Backend, no window) -----
import os
import pytest

pytest.importorskip("PySide6")
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("QT_QUICK_BACKEND", "software")
os.environ["ATARI_NO_GAMEPAD"] = "1"

from PySide6.QtCore import QCoreApplication  # noqa: E402
from atarilauncher import backend as be, config  # noqa: E402
from atarilauncher.store import Store  # noqa: E402


def _bk_backend(tmp_path, monkeypatch, kids=False):
    monkeypatch.setenv("ATARI_DATA_DIR", str(tmp_path / "data"))
    monkeypatch.setenv("ATARI_CONFIG_DIR", str(tmp_path / "conf"))
    QCoreApplication.instance() or QCoreApplication([])
    cfg = config.load_config()
    cfg.steam_enabled = False
    b = be.Backend(cfg=cfg, store=Store(tmp_path / "s.json"), enable_gamepad=False)
    b._kids_mode = kids
    return b


def test_setup_has_backup_row_except_in_kids_mode(tmp_path, monkeypatch):
    b = _bk_backend(tmp_path, monkeypatch, kids=False)
    kinds = [r["kind"] for r in b.setupRows()]
    assert "backuppage" in kinds
    assert kinds.index("makeusbpage") < kinds.index("backuppage") < kinds.index("findgamespage")
    row = next(r for r in b.setupRows() if r["kind"] == "backuppage")
    assert row["title"] == "Backup & Restore"
    assert "Steam Cloud" in row["detail"]
    b2 = _bk_backend(tmp_path, monkeypatch, kids=True)
    assert "backuppage" not in [r["kind"] for r in b2.setupRows()]


def test_backup_page_lists_stick_and_confirm_restore(tmp_path, monkeypatch):
    from atarilauncher.importer import Drive
    b = _bk_backend(tmp_path, monkeypatch)
    stick = tmp_path / "USB"
    stick.mkdir()
    (stick / "CodeAque-Backup-2026-11-19.tar.gz").write_bytes(b"x")
    monkeypatch.setattr(be.backup, "find_drives", lambda: [Drive(str(stick), "WIN600")])
    rows = b.pageRows("backup")
    ids = [r.get("id") for r in rows]
    assert "bkusb:0" in ids
    assert any("WIN600" in (r.get("title") or "") for r in rows)
    assert "bkdownloads" in ids
    assert "bkpick:0" in ids
    assert b.pageTitle("backup") == "BACKUP & RESTORE"
    assert b.pageActivate("backup", "bkpick:0") == "refresh"
    assert b._bk_state == "confirm"
    titles = [r.get("title") for r in b.pageRows("backup")]
    assert "Yes, restore this backup" in titles
    # Start is not run here with a fake archive (it would fail in a thread); cancel path is enough.
    b._bk_state = "running"
    b._bk_frac, b._bk_title = 0.4, "Saving RetroArch saves"
    prog = [r for r in b.pageRows("backup") if r.get("kind") == "progress"]
    assert prog and prog[0].get("big") and prog[0]["value"] == "40%"
