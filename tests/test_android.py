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
