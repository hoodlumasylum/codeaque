"""0.9.3: an update must not leave the old look behind (boot pictures, shortcuts to another copy, an old intro, the old header)."""
import hashlib
from pathlib import Path

from atarilauncher import bootscreen, kiosk, staleparts

ROOT = Path(__file__).resolve().parents[1]


def test_boot_pictures_are_codes_splash_not_the_old_gold_art():
    a = bootscreen.ASSETS
    assert not list(a.glob("rain-*.png"))                                 # the old falling hieroglyphs are gone
    assert {"grub-1920x1080.png", "grub-1366x768.png", "dot.png"} <= {p.name for p in a.iterdir()}
    assert "art version 2" in bootscreen.PLYMOUTH_SCRIPT and "rain-" not in bootscreen.PLYMOUTH_SCRIPT
    assert "ankh" not in bootscreen.__doc__.lower() and "#e2b64a" not in bootscreen.theme_txt()
    s = bootscreen.install_script(bootscreen.Layout("/tmp/x"))
    assert "grub-1920x1080.png" in s and "rm -f" in s and "rain-*.png" in s and "code-logo.png" in s


def test_old_boot_pictures_on_the_computer_are_found(tmp_path):
    lay = bootscreen.Layout(str(tmp_path))
    assert bootscreen.art_outdated(lay) == []                                # never installed: nothing to do
    ply = Path(lay.p(bootscreen.PLY_DIR)); ply.mkdir(parents=True)
    (ply / "background-1920x1080.png").write_bytes(b"old gold art")
    (ply / "codeaque.script").write_text("// old")
    q = Path(lay.p(bootscreen.QUIET_DIR)); q.mkdir(parents=True)
    (q / "code-logo.png").write_bytes(b"old logo")
    assert set(bootscreen.art_outdated(lay)) == {"the start-up splash", "the splash's animation", "the quiet boot logo"}
    (ply / "background-1920x1080.png").write_bytes((bootscreen.ASSETS / "background-1920x1080.png").read_bytes())
    (ply / "codeaque.script").write_text(bootscreen.PLYMOUTH_SCRIPT)
    (q / "code-logo.png").write_bytes(bootscreen.LOGO.read_bytes())
    assert bootscreen.art_outdated(lay) == []
    assert "update-initramfs" in bootscreen.quiet_logo_script(lay)


def test_shortcuts_to_another_copy_are_pointed_at_this_one(tmp_path):
    home = tmp_path
    run = home / ".local/share/atari-launcher/run.sh"; run.parent.mkdir(parents=True); run.write_text("#!/bin/sh\n")
    apps = home / ".local/share/applications"; apps.mkdir(parents=True)
    auto = home / ".config/autostart"; auto.mkdir(parents=True)
    (apps / "code.desktop").write_text("[Desktop Entry]\nName=CodeAque\nExec=/home/code/atari/run.sh --fullscreen\n")
    (auto / "atari.desktop").write_text(f"[Desktop Entry]\nExec={run}\n")
    (apps / "firefox.desktop").write_text("[Desktop Entry]\nExec=firefox %u\n")
    changed = staleparts.fix_launchers(run, home)
    assert changed == [apps / "code.desktop"]
    assert f"Exec={run}\n" in (apps / "code.desktop").read_text()
    assert "firefox %u" in (apps / "firefox.desktop").read_text()


def test_an_old_codeaque_intro_is_removed_but_your_own_video_stays(tmp_path):
    import subprocess
    old = subprocess.run(["git", "cat-file", "blob", "e71ef475a61334bfd4b893349c2522dae2ec725a"], cwd=ROOT, capture_output=True).stdout
    if not old:
        old = None
    mine = tmp_path / "intro.webm"; mine.write_bytes(b"my own video")
    assert staleparts.drop_old_intro(tmp_path) == [] and mine.exists()
    if old:
        assert hashlib.sha256(old).hexdigest() in staleparts.OLD_INTROS
        mine.unlink()
        (tmp_path / "intro.mp4").write_bytes(old)
        assert staleparts.drop_old_intro(tmp_path) == [tmp_path / "intro.mp4"]
    cur = (ROOT / "atarilauncher/assets/intro/code-intro.mp4").read_bytes()
    assert hashlib.sha256(cur).hexdigest() not in staleparts.OLD_INTROS


def test_install_runs_the_leftover_check():
    s = (ROOT / "install.sh").read_text()
    assert '-m atarilauncher.staleparts "$APP_DIR"' in s and 'cd "$APP_DIR"' in s and s.index("staleparts") < s.index('say "Done!"')


def test_codeaque_only_mode_that_starts_another_copy_counts_as_outdated(tmp_path):
    lay = kiosk.Layout(root=tmp_path)
    old = tmp_path / "old-atari"; (old / "atarilauncher").mkdir(parents=True); (old / "run.sh").write_text("#!/bin/sh\n")
    (old / "atarilauncher/__init__.py").write_text('__version__ = "0.5.9"\n')
    text = kiosk.render_session_script(old / "run.sh", "cinnamon-session")
    p = lay.p(lay.session_script); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)
    if not kiosk.enabled(lay):
        for f in (lay.p(lay.xsession), lay.p(lay.lightdm_conf)):
            f.parent.mkdir(parents=True, exist_ok=True); f.write_text("x")
    if kiosk.enabled(lay):
        assert kiosk.session_outdated(lay)
        p.write_text(kiosk.render_session_script(kiosk.run_script_path(), "cinnamon-session"))
        assert not kiosk.session_outdated(lay)


def test_update_screen_has_the_neon_look():
    q = (ROOT / "atarilauncher/qml/Main.qml").read_text()
    assert '"UPDATE CODEAQUE"; color: Theme.accent' in q and '"UPDATE CODE";' not in q
    from PIL import Image
    im = Image.open(ROOT / "atarilauncher/assets/banner.jpg").convert("RGB").resize((60, 8))
    px = [im.getpixel((x, y)) for x in range(60) for y in range(8)]
    assert sum(r + g + b for r, g, b in px) / len(px) < 120                 # dark, not the old bright striped picture
