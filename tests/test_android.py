import shutil
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
AND = ROOT / "android"


@pytest.mark.skipif(not shutil.which("javac"), reason="needs a JDK")
def test_android_catalogue_checks():
    r = subprocess.run(["bash", str(AND / "build.sh"), "test"], capture_output=True, text=True, timeout=120)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "all checks passed" in r.stdout


def test_android_launcher_is_a_home_app_without_logos():
    man = (AND / "app/src/main/AndroidManifest.xml").read_text()
    assert "android.intent.category.HOME" in man
    assets = [p.name for p in (AND / "app/src/main/assets").iterdir()]
    assert not any(n.endswith((".rom", ".bin", ".zip", ".iso")) for n in assets)
    src = (AND / "app/src/main/java/com/codeaque/launcher/HomeView.java").read_text()
    assert "PlayStation" not in src and "PSP" not in src


SRC = AND / "app/src/main/java/com/codeaque/launcher"


def test_themes_are_original_and_default_is_unchanged():
    pal = (SRC / "Palette.java").read_text()
    assert '"Power Up"' in pal and '"Catch"' in pal and '"Neon"' in pal
    assert "ALL = {NEON, POWERUP, CATCH}" in pal                  # Neon is the default
    everything = " ".join(p.read_text(errors="ignore") for p in AND.rglob("*") if p.is_file() and p.suffix in (".java", ".xml", ".md", ".gradle", ".sh"))
    everything += (ROOT / "docs/android.md").read_text() + (ROOT / "README.md").read_text()
    low = everything.lower()
    for bad in ("dragon ball", "dbz", "goku", "saiyan", "kamehameha", "pokemon", "pokémon", "poke ball", "pokeball", "pikachu"):
        assert bad not in low, bad


def test_theme_sounds_are_generated_by_our_scripts():
    for name, tool in (("powerup.wav", "make_powerup_sfx.py"), ("chime.wav", "make_catch_sfx.py")):
        assert (AND / "app/src/main/assets" / name).stat().st_size > 1000
        assert (ROOT / "tools" / tool).exists()


def test_settings_offer_theme_and_wallpaper():
    main = (SRC / "MainActivity.java").read_text()
    assert "ACTION_OPEN_DOCUMENT" in main and "takePersistableUriPermission" in main and '"theme"' in main


def test_no_egyptian_art_left_in_the_defaults():
    assert not (ROOT / "atarilauncher/assets/hh_ankh.svg").exists()
    assert (AND / "app/src/main/assets/logo.png").exists()
    for p in [*(ROOT / "atarilauncher/qml").glob("*.qml"), *SRC.glob("*.java")]:
        t = p.read_text().lower()
        for bad in ("ankh", "anubis", "pyramid", "hieroglyph"):
            assert bad not in t, (p.name, bad)


def test_android_shows_codes_splash_with_a_live_bar_and_can_be_skipped():
    assert (AND / "app/src/main/assets/splash.jpg").exists()
    src = (SRC / "HomeView.java").read_text()
    assert "drawSplash" in src and "627 * k * prog" in src and "(757 + d * 46)" in src   # bar fill + dots over the art
    assert "any button skips the splash" in src
