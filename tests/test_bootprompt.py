from atarilauncher import bootscreen
from tests.test_xemuconfig import make_ui, app  # noqa: F401


def test_prompt_once_after_update_when_menu_is_old(app, tmp_path, monkeypatch):  # noqa: F811
    monkeypatch.setattr(bootscreen, "menu_state", lambda *a, **k: "old")
    monkeypatch.setattr(bootscreen, "menu_entries", lambda *a, **k: ["Linux Mint 22 Cinnamon", "Windows Boot Manager"])
    ui = make_ui(app, tmp_path, monkeypatch, tmp_path / "home")
    try:
        b = ui.backend
        asked = []
        b.pageRequested.connect(asked.append)
        ran = []
        b._boot_reapply_prompt()
        assert asked == ["boot"]
        rows = b.pageRows("boot")
        assert any("NOT up to date" in r["title"] for r in rows)
        b._boot_activate("bootinstall", run=lambda script, what: ran.append(what))
        assert ran == ["Installing the boot screen"]          # one tap after the prompt
        b._boot_reapply_prompt()
        assert asked == ["boot"]                                # only once per version
    finally:
        ui.close()


def test_prompt_once_after_update_when_the_boot_pictures_are_the_old_art(app, tmp_path, monkeypatch):  # noqa: F811
    monkeypatch.setattr(bootscreen, "menu_state", lambda *a, **k: "current")
    monkeypatch.setattr(bootscreen, "menu_entries", lambda *a, **k: ["CodeAque", "Windows"])
    monkeypatch.setattr(bootscreen, "installed", lambda *a, **k: True)
    monkeypatch.setattr(bootscreen, "art_outdated", lambda *a, **k: ["the start-up splash"])
    ui = make_ui(app, tmp_path, monkeypatch, tmp_path / "home")
    try:
        b = ui.backend
        asked, ran = [], []
        b.pageRequested.connect(asked.append)
        b._boot_reapply_prompt()
        assert asked == ["boot"] and "OLD look" in b._boot_msg
        rows = b.pageRows("boot")
        assert any(r.get("id") == "bootinstall" and "OLD look" in r["title"] for r in rows)
        b._boot_activate("bootinstall", run=lambda script, what: ran.append((what, script)))
        assert ran and "grub-1920x1080.png" in ran[0][1]
    finally:
        ui.close()


def test_only_quiet_boot_gets_its_logo_replaced_straight_away(app, tmp_path, monkeypatch):  # noqa: F811
    monkeypatch.setattr(bootscreen, "menu_state", lambda *a, **k: "unknown")
    monkeypatch.setattr(bootscreen, "installed", lambda *a, **k: False)
    monkeypatch.setattr(bootscreen, "art_outdated", lambda *a, **k: ["the quiet boot logo"])
    ui = make_ui(app, tmp_path, monkeypatch, tmp_path / "home")
    try:
        b = ui.backend
        ran = []
        monkeypatch.setattr(b, "_boot_run", lambda script, what: ran.append(script))
        b._boot_reapply_prompt()
        assert ran and "code-logo.png" in ran[0] and "update-initramfs" in ran[0]
    finally:
        ui.close()
