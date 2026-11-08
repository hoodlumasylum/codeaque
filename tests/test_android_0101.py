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
    # never fires a HOME intent; 0.10.21 Lock mode only queries Home apps and pins its own HOME filter (device owner)
    assert "CATEGORY_HOME" not in m.replace("addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY", "").replace(
        "queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)", "").replace("home.addCategory(Intent.CATEGORY_HOME);", "")
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
    assert '"Play", "pad", ROBLOX, MINECRAFT, MARVEL_RIVALS)' in km and "GEFORCE_NOW" not in km.split("KIDS_WIFI", 1)[0] and '"Kids Games", "invader", KIDS' in km and "PLAY_STORE" in km
    for bad in ("RETROARCH", "romfolder", "settings", "ask", "projector", "k56", "bios"):
        assert bad not in km.lower().replace("kids games", "")
    h = (SRC / "HomeView.java").read_text()
    assert "KIDS_SCALE = 1.7f" in h and "(kids ? 34 : 20) * s" in h
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
    assert '"roms/dc"' in sc and '"roms/dreamcast"' in sc and '"download"' in sc and 'equalsIgnoreCase(part)' in sc and ".gdi" in sc and ".cdi" in sc and ".chd" in sc
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


def test_files_tile_not_in_kids():
    c = (SRC / "Catalog.java").read_text()
    assert 'FILES = act("Files", "folder", "filemgr")' in c and "dreamcast(roms, false)" in c
    assert "Copy games to a folder called Dreamcast (PSP games to PSP), BIOS to BIOS" in c and "Copy PSP games to a folder called PSP" in c
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private void openFiles()", 1)[1].split("private boolean tryStart", 1)[0]
    i = [body.index(x) for x in ("com.android.documentsui", "vnd.android.document/root", "CATEGORY_APP_FILES", "INTERNAL_STORAGE_SETTINGS")]
    assert i == sorted(i)
    assert 'case "filemgr": if (!prefs.getBoolean("kidsMode", false)) openFiles();' in m


def test_psp_section_mirrors_dreamcast():
    c = (SRC / "Catalog.java").read_text()
    assert 'PSP = app("PSP", "handheld", "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")' in c
    assert 'new Category("kpsp"' in c and "psp(roms, false)" in c and "if (files) { l.add(FILES); l.add(RESCAN); }" in c and "c.add(psp(roms));" in c
    s = (SRC / "PspScan.java").read_text()
    assert "http" not in s.lower() and '"psp/game"' in s and '"roms/psp"' in s
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private void openInPpsspp(", 1)[1].split("/** Opens one game file directly in Flycast", 1)[0]
    assert "Intent.ACTION_VIEW" in body and 'store("org.ppsspp.ppsspp")' in body and "getLaunchIntentForPackage(pkg)" in body
    assert m.index("Catalog.isPspRom(it)") < m.index("Catalog.isDreamcastRom(it)) { forceLandscape(); openInFlycast")
    assert not any("psp" in p.name.lower() or "sony" in p.name.lower() for p in (AND / "app/src/main/res").rglob("*"))


def test_rescan_and_empty_note_grown_ups_only():
    m = (SRC / "MainActivity.java").read_text()
    assert 'case "rescan": if (!prefs.getBoolean("kidsMode", false)) rescanGames();' in m
    note = m.split("public String scanNote(String catId)", 1)[1].split("\n    }\n", 1)[0]
    assert note.index('prefs.getBoolean("kidsMode", false)') < note.index("scanNotes.get")
    assert '"No games found in: "' in m and "sm.getStorageVolumes()" in m
    assert '"roms/dreamcast"' in (SRC / "DcScan.java").read_text() and "MAX_DEPTH = 3" in (SRC / "PspScan.java").read_text()


def test_dreamcast_volume_root_scan():
    m = (SRC / "MainActivity.java").read_text()
    assert "DcScan.scanVolumeRoot(r, volLabels.get(k), dcFound, dcDirs);" in m and "v.getDescription(this)" in m
    sc = (SRC / "DcScan.java").read_text()
    assert '"dcim"' in sc and '"android"' in sc and '"audiobooks"' in sc


def test_scan_fallbacks_and_details():
    m = (SRC / "MainActivity.java").read_text()
    assert "MediaStore.Files.getContentUri(\"external\")" in m and "find /data/media/0 /mnt/media_rw" in m
    assert "(rescanAsked || prefs.getBoolean(\"rootScanOk\", false))) {   // root" in m   # su: a grown-up's Rescan, or once Magisk has said yes
    assert 'case "scandetails": if (!prefs.getBoolean("kidsMode", false)) showScanDetails();' in m
    assert 'android:name="android.permission.MANAGE_EXTERNAL_STORAGE"' in (AND / "app/src/main/AndroidManifest.xml").read_text()


def test_game_covers_own_pictures_or_title_card():
    m = (SRC / "MainActivity.java").read_text()
    assert "if (Catalog.isDreamcastRom(it) || Catalog.isPspRom(it)) return gameCover(it);" in m
    body = m.split("private android.graphics.Bitmap gameCover(", 1)[1].split("private java.io.File coverFile(", 1)[0]
    assert "http" not in body and "su(\"ls -1" in body
    h = (SRC / "HomeView.java").read_text()
    card = h.split("private void titleCard(", 1)[1].split("private void drawCoverFrame(", 1)[0]
    assert "drawBitmap" not in card and "Sega" not in card and "Sony" not in card
    assert 'id.equals("kdc")' in h and 'id.equals("kpsp")' in h


def test_vice_city_own_cover():
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private android.graphics.Bitmap gameCover(", 1)[1].split("private java.io.File coverFile(", 1)[0]
    assert body.index("pick = f") < body.index("R.drawable.ha_vice_city") and "if (b == null && Catalog.isPspRom(it)" in body
    f = AND / "app/src/main/res/drawable-nodpi/ha_vice_city.webp"
    assert f.is_file() and f.stat().st_size < 120_000


def test_scan_cache_shared_with_kids_rows():
    m = (SRC / "MainActivity.java").read_text()
    sd = m.split("private void scanDreamcast()", 1)[1]
    assert sd.index('prefs.getStringSet("gameScanCache"') < sd.index("if (dcScanning || !canReadFiles()) return;")
    assert 'prefs.edit().putStringSet("gameScanCache", cache)' in m and "kept the last good list" in m
    assert "Catalog.kidsMode(allRoms, kidsNew)" in m and "allRoms.addAll(dcFiles)" in m


def test_kids_rows_games_only():
    c = (SRC / "Catalog.java").read_text()
    assert 'new Category("kdc", dc.label, dc.icon, kidsGamesOnly(dc.items))' in c and 'new Category("kpsp", ps.label, ps.icon, kidsGamesOnly(ps.items))' in c
    assert "return (g.isEmpty() ? items : g).toArray(new Item[0]);" in c


def test_ha_dreamcast_covers():
    m = (SRC / "MainActivity.java").read_text()
    body = m.split("private android.graphics.Bitmap gameCover(", 1)[1].split("private static int haDcRes(", 1)[0]
    assert body.index("pick = f") < body.index("Catalog.haDcCover(") and "if (b == null && Catalog.isDreamcastRom(it))" in body
    for n in ["crazy_taxi", "jet_grind_radio", "marvel_vs_capcom", "mortal_kombat_gold", "prince_of_persia", "rayman_2", "shenmue_disc_1", "shenmue_disc_2"]:
        f = AND / f"app/src/main/res/drawable-nodpi/ha_{n}.webp"
        assert f.is_file() and f.stat().st_size < 120_000


def test_kids_games_wide_covers():
    h = (SRC / "HomeView.java").read_text()
    m = (SRC / "MainActivity.java").read_text()
    assert 'id.equals("kgames")' in h and "host.wideCover(it)" in h and "Bitmap wideCover(Catalog.Item item);" in h
    body = m.split("public android.graphics.Bitmap wideCover(", 1)[1].split("private android.graphics.Bitmap playIconFor(", 1)[0]
    assert body.index("playFeature(pkg)") < body.index("iconBanner(ai)")


def test_ha_dreamcast_covers_0118():
    m = (SRC / "MainActivity.java").read_text()
    for n in ["sonic_adventure", "sonic_adventure_2", "spider_man", "street_fighter_3", "toy_story_2"]:
        assert f"R.drawable.ha_{n};" in m
        f = AND / f"app/src/main/res/drawable-nodpi/ha_{n}.webp"
        assert f.is_file() and f.stat().st_size < 120_000


def test_big_bold_tile_names():
    h = (SRC / "HomeView.java").read_text()
    assert "private void tileName(" in h and '"sans-serif-black"' in h
    tiles = h.split("private void tiles(", 1)[1].split("private void titleCard(", 1)[0]
    cover = h.split("private void coverTile(", 1)[1].split("private void blockyArt(", 1)[0]
    assert "tileName(c, lab" in tiles and "tileName(c, lab" in cover and "0.48f" in cover


def test_robust_update_download():
    m = (SRC / "MainActivity.java").read_text()
    f = m.split("private static void fetch(", 1)[1].split("static final class FatalHttp", 1)[0]
    assert "attempt < 4" in f and "setInstanceFollowRedirects(false)" in f and "User-Agent" in f and "Location" in f
    i = m.split("private void install(UpdateInfo u)", 1)[1].split("// ---------- 0.10.1", 1)[0]
    assert "u.apkDigest" in i and "u.apkApiUrl" in i and "getSimpleName()" in i


def test_lock_mode():
    m = (SRC / "MainActivity.java").read_text()
    man = (AND / "app/src/main/AndroidManifest.xml").read_text()
    op = m.split("public void open(Catalog.Item it) {", 1)[1].split("private void store(", 1)[0]
    assert op.index("LockPin.needsPin(it.action)") < op.index("action(it.action); return;") and "LockPin.blocked(it.pkg)" in op
    assert "blockedTarget(i)" in op and 'case "lock": lockToggle();' in m and "lockResume();" in m.split("protected void onResume()", 1)[1][:400]
    assert 'putString("lockPin", LockPin.newHash(p))' in m and "startLockTask()" in m and "setLockTaskPackages" in m
    assert ".CodeAqueAdmin" in man and "BIND_DEVICE_ADMIN" in man and "category.HOME" in man
    assert 'act("Lock mode", "gear", "lock")' in (SRC / "Catalog.java").read_text()


def test_safe_contacts():
    m = (SRC / "MainActivity.java").read_text()
    man = (AND / "app/src/main/AndroidManifest.xml").read_text()
    assert "if (hasPhone()) cs.add(" in m and "FEATURE_TELEPHONY" in m
    ct = m.split("private void callText()", 1)[1].split("private String[] phoneApps()", 1)[0]
    assert "EditText" not in ct and "safeList()" in ct            # no free number box for the kid
    sl = m.split("private void safeLaunch(Intent i)", 1)[1].split("private void safeCall(", 1)[0]
    assert sl.index("SafeContacts.allowed(") < sl.index("startActivity(")
    imp = m.split("private void importSafe()", 1)[1][:400]
    assert "requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}" in imp
    assert "else if (!locked()) safeLaunch(new Intent(Intent.ACTION_DIAL" in m        # no dialer fallback while locked
    assert "CALL_PHONE" in man and "READ_CONTACTS" in man and "BIND_SCREENING_SERVICE" in man and "BIND_CALL_REDIRECTION_SERVICE" in man
    assert 'android.hardware.telephony" android:required="false"' in man


def test_kids_pin_play_store_and_exit():
    m = (SRC / "MainActivity.java").read_text()
    oc = m.split("protected void onCreate(", 1)[1][:400]
    assert "ensurePin();" in oc and 'DEFAULT_PIN = "1234"' in m and "LockPin.newHash(DEFAULT_PIN)" in m
    op = m.split("public void open(Catalog.Item it) {", 1)[1][:900]
    assert op.index("kidsPinGate(it)") < op.index("action(it.action)")
    g = m.split("private boolean kidsPinGate(", 1)[1].split("private boolean locked()", 1)[0]
    assert '"kidsoff".equals(it.action)' in g and "Catalog.opensStore(" in g
    st = m.split("private void store(String pkg) {", 1)[1][:300]
    assert 'getBoolean("kidsMode", false) && pinSet() && !pinFresh()' in st
    assert "Still using 1234" in m.split("private void askPin(", 1)[1][:2500]
    cp = m.split("private void changePin()", 1)[1].split("private boolean homePromptShown;", 1)[0]
    assert cp.index('LockPin.check(cur.getText()') < cp.index('putString("lockPin", LockPin.newHash(p))')
    assert 'remove("lockPin")' not in m and "kidsPinSetup" not in m
    assert 'act("Change PIN", "gear", "changepin")' in (SRC / "Catalog.java").read_text()


def test_kids_no_streams_or_all_apps():
    m = (SRC / "MainActivity.java").read_text()
    op = m.split("public void open(Catalog.Item it) {", 1)[1][:500]
    assert 'getBoolean("kidsMode", false) && Catalog.kidsBlocked(it)' in op
    assert 'if (prefs.getBoolean("kidsMode", false)) { toast("Not available in Kids mode."); return; }' in m.split("private void allApps() {", 1)[1][:200]
