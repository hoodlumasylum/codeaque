"""0.10.1: Android boot animation entry and Roblox/Minecraft featured tiles (source checks; untested on a device)."""
from pathlib import Path

AND = Path(__file__).resolve().parents[1] / "android"
SRC = AND / "app/src/main/java/com/codeaque/launcher"
SHA = "b1616fad964be479a5c125505defbd1b9b99fa3cef97cff3f09b5ea35b3600c1"


def test_bootanim_constants_and_flow():
    b = (SRC / "BootAnim.java").read_text()
    assert "https://github.com/hoodlumasylum/codeaque/releases/download/bootanim-v1.0/" in b
    assert SHA in b and '"codeaque-bootanim"' in b and "com.topjohnwu.magisk" in b
    assert "magisk --install-module" in b and "/remove" in b and "UNTESTED" in b
    m = (SRC / "MainActivity.java").read_text()
    assert 'case "bootanim": bootAnim();' in m
    for s in ("BootAnim.matches(md.digest())", "MediaStore.Downloads", "setProgress", "\"Install now\"",
              "\"Remove boot animation\"", "\"Restart now\"", "getLaunchIntentForPackage(BootAnim.MAGISK_PKG)",
              "Install from storage", "bootAnimAvailable()"):
        assert s in m, s
    assert 'new ProcessBuilder("su", "-c", cmd)' in m


def test_bootanim_not_bundled_and_manifest_queries():
    assert not any(p.suffix == ".zip" for p in (AND / "app/src/main").rglob("*"))
    man = (AND / "app/src/main/AndroidManifest.xml").read_text()
    for pkg in ("com.topjohnwu.magisk", "com.roblox.client", "com.mojang.minecraftpe"):
        assert f'<package android:name="{pkg}" />' in man
    assert 'WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28"' in man


def test_featured_roblox_minecraft_first_without_logos():
    c = (SRC / "Catalog.java").read_text()
    assert '"Featured", "display", ROBLOX, MINECRAFT' in c
    assert '"com.roblox.client"' in c and '"com.mojang.minecraftpe"' in c
    h = (SRC / "HomeView.java").read_text()
    assert "cat = 0; catAnim = 0;" in h
    m = (SRC / "MainActivity.java").read_text()
    assert "market://details?id=" in m and "https://play.google.com/store/apps/details?id=" in m
    assets = [p.name.lower() for p in (AND / "app/src/main").rglob("*")]
    assert not any("roblox" in n or "minecraft" in n for n in assets)


KIDS = {  # package: PEGI rating read from play.google.com (en_GB) on 7 Nov 2026
    "com.pokemon.pokemonplayhouse": 3, "jp.pokemon.pokemonquest": 3, "jp.pokemon.pokemonsmile": 3,
    "jp.pokemon.poketoru": 3, "com.sega.sonicdash": 3, "com.budgestudios.googleplay.HotWheelsUnlimited": 3,
    "com.budgestudios.googleplay.PawPatrolRescueWorld": 3, "com.tocaboca.tocalifeworld": 3, "com.kiloo.subwaysurf": 7,
    "com.bandainamcogames.dbzdokkanww": 16, "com.bandainamcoent.dblegends_ww": 16, "com.bandainamcoent.dbgekishinsquadra": 16,
}


def test_kids_row_official_listings_only():
    c = (SRC / "Catalog.java").read_text()
    assert 'new Category("kids", "Kids", "pad", KIDS)' in c
    block = c.split("Item[] KIDS = {", 1)[1].split("};", 1)[0]
    pkgs = [l.split('"')[-2] for l in block.splitlines() if 'app("' in l]
    assert pkgs == list(KIDS)
    assert [p for p, v in KIDS.items() if v not in (3, 7)] == ["com.bandainamcogames.dbzdokkanww", "com.bandainamcoent.dblegends_ww", "com.bandainamcoent.dbgekishinsquadra"]
    for bad in ("pokemonunite", "pokemongo", "pokemoncafemix", "rom", "emu"):
        assert bad not in block.lower()


def test_home_chooser_never_reprompts():
    man = (AND / "app/src/main/AndroidManifest.xml").read_text()
    filters = man.split("<intent-filter>")[1:]
    home = [f for f in filters if "category.HOME" in f]
    assert len(home) == 1 and "category.LAUNCHER" not in home[0] and "LEANBACK" not in home[0]
    assert man.count("<activity ") == 1 and "activity-alias" not in man and 'launchMode="singleTask"' in man
    m = (SRC / "MainActivity.java").read_text()
    assert "RoleManager.ROLE_HOME" in m and "isRoleHeld" in m
    body = m.split("private void maybeAskHomeOnce()", 1)[1].split("\n    }\n", 1)[0]
    assert body.index('getBoolean("homeAsked", false)') < body.index('putBoolean("homeAsked", true)') < body.index("isDefaultHome()") < body.index("createRequestRoleIntent")
    assert "CATEGORY_HOME" not in m.replace("addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY", "")
    assert 'case "home": if (isDefaultHome())' in m
    assert m.count("maybeAskHomeOnce();") == 1


def test_force_landscape_is_opt_in_root_only():
    r = (SRC / "Rotation.java").read_text()
    assert "accelerometer_rotation 0" in r and "wm fixed-to-user-rotation enabled" in r and "user_rotation" in r and 'MODES = {"off", "0", "1", "3"}' in r
    m = (SRC / "MainActivity.java").read_text()
    assert 'prefs.getString("rotation", "off")' in m and m.count("forceLandscape();") >= 2
    body = m.split("private void forceLandscape()", 1)[1].split("\n    }\n", 1)[0]
    assert "c == null || !bootAnimAvailable()" in body
    sug = m.split("private void maybeSuggestRotation()", 1)[1].split("\n    }\n", 1)[0]
    assert "putString" not in sug  # only suggests, never switches on
    assert 'act("Force landscape for games", "display", "rotation")' in (SRC / "Catalog.java").read_text()


def test_play_store_tile_on_featured():
    c = (SRC / "Catalog.java").read_text()
    assert '"Featured", "display", ROBLOX, MINECRAFT, PLAY_STORE,' in c and '"com.android.vending"' in c
    m = (SRC / "MainActivity.java").read_text()
    assert "Play Store not installed" in m
    assert '<package android:name="com.android.vending" />' in (AND / "app/src/main/AndroidManifest.xml").read_text()


def test_geforce_now_on_featured():
    c = (SRC / "Catalog.java").read_text()
    assert "ROBLOX, MINECRAFT, PLAY_STORE, GEFORCE_NOW," in c and 'app("GeForce NOW", "cloud", "com.nvidia.geforcenow")' in c
    assert '<package android:name="com.nvidia.geforcenow" />' in (AND / "app/src/main/AndroidManifest.xml").read_text()


def test_kids_mode_big_and_locked_down():
    c = (SRC / "Catalog.java").read_text()
    km = c.split("public static List<Category> kidsMode()", 1)[1].split("\n    }\n", 1)[0]
    assert '"Play", "pad", ROBLOX, MINECRAFT, GEFORCE_NOW' in km and '"Kids Games", "invader", KIDS' in km and "PLAY_STORE" in km
    for bad in ("RETROARCH", "romfolder", "settings", "ask", "projector", "k56", "bios"):
        assert bad not in km.lower().replace("kids games", "")
    h = (SRC / "HomeView.java").read_text()
    assert "KIDS_SCALE = 1.7f" in h and "(kids ? 26 : 15) * s" in h
    assert "onKeyLongPress" in h and "host.open(Catalog.KIDS_OFF)" in h and "e.startTracking()" in h
    m = (SRC / "MainActivity.java").read_text()
    assert 'case "kids": kidsMode(true);' in m and 'case "kidsoff": kidsMode(false);' in m
    assert 'kidsOn ? Catalog.kidsMode() :' in m and 'getBoolean("kidsMode", false)' in m


def test_geforce_now_web_client_on_handhelds():
    m = (SRC / "MainActivity.java").read_text()
    assert "it == Catalog.GEFORCE_NOW && Catalog.gfnInBrowser(handheld())" in m
    assert m.index("Catalog.gfnInBrowser(handheld())") < m.index("Intent i = launchIntent(it);")
    assert 'setPackage("com.android.chrome")' in m
    assert '<package android:name="com.android.chrome" />' in (AND / "app/src/main/AndroidManifest.xml").read_text()
