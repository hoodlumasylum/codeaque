from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]


def test_christmas_theme_loads_with_snow():
    from atarilauncher import themes
    t = themes.get("christmas")
    assert t.id == "christmas" and t.snow and not themes.problems(t)


def test_snow_and_jingle_are_shipped():
    assert "Snow" in (ROOT / "atarilauncher/qml/qmldir").read_text()
    for p in ("atarilauncher/assets/sfx/jingle.wav", "android/app/src/main/assets/jingle.wav", "tools/make_jingle_sfx.py"):
        assert (ROOT / p).exists(), p


def test_android_can_self_update():
    m = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text()
    assert "REQUEST_INSTALL_PACKAGES" in m and "android.permission.INTERNET" in m and 'versionName="0.9.7"' in m
