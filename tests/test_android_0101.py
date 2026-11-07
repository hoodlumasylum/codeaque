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
    assert 'prefs.getString("rotation", "off")' in m and m.count("forceLandscape();") >= 1 and "forceLandscapeThen(i)" in m
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
    assert 'kidsOn ? Catalog.kidsMode(allRoms, kidsNew) :' in m and 'getBoolean("kidsMode", false)' in m


def test_geforce_now_web_client_on_handhelds():
    m = (SRC / "MainActivity.java").read_text()
    assert "it == Catalog.GEFORCE_NOW && Catalog.gfnInBrowser(handheld())" in m
    assert m.index("Catalog.gfnInBrowser(handheld())") < m.index("Intent i = launchIntent(it);")
    assert 'setPackage("com.android.chrome")' in m
    assert '<package android:name="com.android.chrome" />' in (AND / "app/src/main/AndroidManifest.xml").read_text()


def test_play_listing_art_runtime_only():
    a = (SRC / "PlayArt.java").read_text()
    assert 'PLAY = "https://play.google.com"' in a and 'IMG_HOST = "https://play-lh.googleusercontent.com/"' in a
    m = (SRC / "MainActivity.java").read_text()
    assert 'new java.io.File(getCacheDir(), "playart")' in m and "PlayArt.fetch(PlayArt.PLAY, pkg, dir)" in m
    assert "if (!it.isDeviceApp()) return playArt(it, false);" in m
    t = (AND / "app/src/test/java/com/codeaque/launcher/CatalogTest.java").read_text()
    assert "com.sun.net.httpserver.HttpServer" in t and "playArtChecks();" in t
    for p in (AND / "app/src/main").rglob("*"):
        assert not p.name.endswith((".icon.png", ".feature.png"))


def test_rotation_ignores_app_orientation_and_runs_before_launch():
    r = (SRC / "Rotation.java").read_text()
    assert "wm set-ignore-orientation-request true || cmd window set-ignore-orientation-request true" in r
    assert "Orientation Manager" in r
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private boolean forceLandscapeThen(Intent i)", 1)[1].split("\n    }\n", 1)[0]
    assert body.index("su(c, null)") < body.index("start(i)")
    assert "if (!forceLandscapeThen(i)) start(i);" in m


def test_roblox_tile_is_original_drawn_art():
    h = (SRC / "HomeView.java").read_text()
    assert "if (it == Catalog.ROBLOX && cov == null) blockyArt(c);" in h and "private void buddy(" in h
    assets = [p.name.lower() for p in (AND / "app/src/main").rglob("*")]
    assert not any("roblox" in n for n in assets)


def test_marvel_rivals_gfn_tile():
    c = (SRC / "Catalog.java").read_text()
    assert 'act("Marvel Rivals", "cloud", "gfn:rivals")' in c and "GEFORCE_NOW, MARVEL_RIVALS" in c
    assert "game-id=" in c and "utm_source=codeaque&utm_campaign=launcher" in c
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private void openRivals()", 1)[1].split("\n    }\n", 1)[0]
    assert body.index("gfnInBrowser(handheld())") < body.index('getLaunchIntentForPackage("com.nvidia.geforcenow")') < body.rindex("openWeb(Catalog.RIVALS_GFN)")
    assert 'case "gfn:rivals": openRivals();' in m
    assert not any("rivals" in p.name.lower() or "2767030" in p.name for p in (AND / "app/src/main").rglob("*"))


def test_kids_mode_wifi_tile_only():
    c = (SRC / "Catalog.java").read_text()
    km = c.split("public static List<Category> kidsMode()", 1)[1].split("\n    }\n", 1)[0]
    assert 'new Category("kwifi", "Wi-Fi & Update", "wifi", KIDS_WIFI, KIDS_UPDATE)' in c and 'KIDS_WIFI = act("Wi-Fi", "wifi", "wifi")' in c
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private void openWifi()", 1)[1].split("\n    }\n", 1)[0]
    assert body.index("Settings.Panel.ACTION_WIFI") < body.index("Settings.ACTION_WIFI_SETTINGS") and "SDK_INT >= 29" in body
    assert "@Override public String wifi()" in m and "ACCESS_WIFI_STATE" in (AND / "app/src/main/AndroidManifest.xml").read_text()
    assert '"wifi".equals(f.action) ? host.wifi()' in (SRC / "HomeView.java").read_text()


def test_dreamcast_section_own_files_only():
    c = (SRC / "Catalog.java").read_text()
    assert 'DREAMCAST = app("Dreamcast", "disc", "com.flycast.emulator")' in c and "c.add(dreamcast(roms));" in c
    sc = (SRC / "DcScan.java").read_text()
    assert '"ROMs/dc"' in sc and '"roms/dreamcast"' in sc and '"Download"' in sc and ".gdi" in sc and ".cdi" in sc and ".chd" in sc
    m = (SRC / "MainActivity.java").read_text()
    assert "scanDreamcast();" in m and 'setPackage("com.flycast.emulator")' in m and "isExternalStorageManager()" in m
    assert "http" not in sc.lower() and "download(" not in sc.lower()
    names = [p.name.lower() for p in (AND / "app/src/main").rglob("*")]
    assert not any(n.endswith((".gdi", ".cdi", ".chd", ".bin")) or "dc_boot" in n for n in names)


def test_dreamcast_usb_and_own_bios_import():
    sc = (SRC / "DcScan.java").read_text()
    assert '"/storage"' not in sc  # roots come from MainActivity
    m = (SRC / "MainActivity.java").read_text()
    assert 'DcScan.volumeRoots(new java.io.File("/storage"), new java.io.File("/mnt/media_rw"))' in m
    assert "importBios(roots)" in m and "Custom Paths > BIOS Folders" in m
    assert 'FLYCAST_DATA = "Android/data/com.flycast.emulator/files/data"' in sc and '"dc_boot.bin"' in sc and '"dc_flash.bin"' in sc


def test_android_update_popup():
    m = (SRC / "MainActivity.java").read_text()
    pop = m.split("void updatePopup(UpdateInfo u, String mine)", 1)[1].split("\n    }\n", 1)[0]
    assert '"New update ready (v" + u.version + ")"' in pop and '"Update now"' in pop and '"Later"' in pop
    assert "u.version.equals(popupDismissed)" in pop and "install(u)" in pop and "setTextSize(big)" in pop
    assert "Settings" not in pop.replace("SHA256SUMS", "")
    assert "if (!manual) { runOnUiThread(() -> updatePopup(u, mine)); return; }" in m
    assert 'UpdateInfo.newer(prefs.getString("updKnown", "0"), myVersion())' in m


def test_kids_mode_new_games_and_update_tile():
    c = (SRC / "Catalog.java").read_text()
    assert 'KIDS_UPDATE = act("Update CodeAque", "download", "update")' in c and "KIDS_WIFI, KIDS_UPDATE" in c
    assert 'new Category("kadded", "My New Games"' in c and 'act("Kids mode games", "pad", "kidsgames")' in c
    m = (SRC / "MainActivity.java").read_text()
    assert "Intent.ACTION_PACKAGE_ADDED.equals(i.getAction()) && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false)" in m
    assert "m.kidsInstalled(" in m and "kidsDiff();" in m and "kidsStoreOpened();" in m
    assert 'Catalog.kidsMode(allRoms, kidsNew)' in m and "You're up to date!" in m
    ki = m.split("void kidsInstalled(String pkg)", 1)[1].split("\n    }\n", 1)[0]
    assert 'getBoolean("kidsMode", false)' in ki and "getLaunchIntentForPackage(pkg) == null" in ki


def test_featured_wide_covers_and_roblox_art():
    h = (SRC / "HomeView.java").read_text()
    assert 'id.equals("steam") || id.equals("kplay")' in h and "(wide() ? (kids ? 220 : 266)" in h
    art = h.split("private void blockyArt(Canvas c)", 1)[1].split("/** One blocky buddy", 1)[0]
    assert "speed lines" in art and "buddy(c," in art and "drawText" not in art and "Bitmap" not in art
    m = (SRC / "MainActivity.java").read_text()
    assert "if (it == Catalog.GEFORCE_NOW) { android.graphics.Bitmap w = playFeature(it)" in m
    assert "ownFeature(html, i)" in (SRC / "PlayArt.java").read_text()


def test_rivals_tile_uses_own_duat_art():
    m = (SRC / "MainActivity.java").read_text()
    for r in ("duat_realm_s1", "duat_realm_blocks", "duat_realm_bricks"):
        assert f"R.drawable.{r}" in m
        f = AND / f"app/src/main/res/drawable-nodpi/{r}.webp"
        assert f.is_file() and f.stat().st_size < 120_000
    assert "if (it == Catalog.ROBLOX && cov == null) blockyArt(c);" in (SRC / "HomeView.java").read_text()
