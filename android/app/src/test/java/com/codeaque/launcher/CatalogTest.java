package com.codeaque.launcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Plain-Java checks (run by android/build.sh test). */
public class CatalogTest {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] a) {
        List<Catalog.Category> c = Catalog.categories();
        check(c.get(0).id.equals("steam") && c.get(1).id.equals("kids") && c.get(2).id.equals("mygames") && c.get(3).id.equals("games") && c.get(3).items.get(0).action.equals("goto:cloud"), "Featured, Kids, My Games, then Games with Cloud Gaming");
        check(Catalog.indexOf(c, "cloud") == 4 && Catalog.indexOf(c, "retro") == 5, "Cloud then Retro");
        List<Catalog.Category> pj = Catalog.categories(List.of(), List.of(), List.of(), List.of(), true);
        check(pj.get(0).id.equals("cloud") && pj.get(1).id.equals("media") && pj.size() == c.size(), "projector order");
        check(Catalog.indexOf(c, "store") > 0 && Catalog.indexOf(c, "accounts") > 0, "Games Store and Accounts");
        for (Catalog.Item i : c.get(Catalog.indexOf(c, "store")).items) check(i.webUrl.startsWith("https://play.google.com/"), "store opens Google Play");
        check(Catalog.indexOf(c, "allapps") == c.size() - 4 && c.get(c.size() - 3).id.equals("settings") && c.get(c.size() - 2).id.equals("nova") && c.get(c.size() - 1).id.equals("power"), "All Apps, Settings, Nova, then Power");
        // 0.9.4: installed games / apps / emulators / the user's own files
        Catalog.Item g = Catalog.deviceApp("Some Game", "com.example.game", "com.example.game.Main");
        Catalog.Item rom = Catalog.rom("My Game.sfc", "content://x/1");
        List<Catalog.Category> d = Catalog.categories(List.of(g), List.of(g), List.of(Catalog.emulators().get(0)), List.of(rom));
        List<Catalog.Item> mine = d.get(Catalog.indexOf(d, "mygames")).items;
        check(mine.get(0) == g && mine.get(1).label.equals("RetroArch") && mine.get(2).label.equals("My Game") && mine.get(2).isRom(), "My Games order");
        check("romfolder".equals(mine.get(mine.size() - 1).action), "My Games ends with Add game folder");
        check(d.get(Catalog.indexOf(d, "allapps")).items.get(0).isDeviceApp(), "All Apps lists device apps");
        check("https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/My%20Game.png".equals(rom.boxart()), "box art url " + rom.boxart());
        check(Catalog.boxartUrl("x.iso") == null && Catalog.boxartUrl("A: B.nes").endsWith("/A_%20B.png"), "box art only for one-system types, libretro characters");
        check(Catalog.isRomFile("a.SFC") && Catalog.isRomFile("b.chd") && !Catalog.isRomFile("notes.txt") && !Catalog.isRomFile(".nes"), "game file types");
        Set<String> want = new HashSet<>(List.of("Xbox Cloud Gaming", "Amazon Luna", "GeForce NOW", "Netflix", "YouTube",
                "TikTok", "Kodi", "RetroArch", "Minecraft", "Roblox"));
        for (Catalog.Category cat : c) for (Catalog.Item i : cat.items) {
            want.remove(i.label);
            check(i.isAction() || i.isRom() || i.webUrl != null || i.storePackage() != null, i.label + " has a package for the Play Store link");
            check(i.webUrl == null || i.webUrl.startsWith("https://"), i.label + " web link is https");
            check(!i.icon.contains("logo"), "no logos");
        }
        check(want.isEmpty(), "missing tiles " + want);
        for (Catalog.Item i : c.get(Catalog.indexOf(c, "cloud")).items) check(i.webUrl != null, i.label + " works in a browser");
        check(c.get(Catalog.indexOf(c, "retro")).items.get(0).label.equals("RetroArch"), "Retro opens RetroArch first");
        check(Catalog.nextEffects("auto").equals("on") && Catalog.nextEffects("off").equals("auto"), "effects cycle");
        // themes: default unchanged and first, Power Up available, picker cycles
        // 0.10.1 Featured: Roblox, Minecraft first; store link when missing, no logos
        // 0.10.26: the big Kids Mode tile comes first, then Roblox, Minecraft, Play Store
        check(c.get(0).items.get(0) == Catalog.KIDS_ON && "kidson".equals(Catalog.KIDS_ON.action) && !LockPin.needsPin("kidson") && LockPin.needsPin("kidsoff"), "Kids Mode tile first, no PIN to go in");
        check(c.get(0).items.get(1) == Catalog.SETTINGS_TILE && "goto:settings".equals(Catalog.SETTINGS_TILE.action) && !LockPin.needsPin("goto:settings"), "0.10.30: Settings tile second on Featured");
        check(c.get(0).items.get(2) == Catalog.INSTALL_KIDS && "installkids".equals(Catalog.INSTALL_KIDS.action), "Install kids games tile");
        check(c.get(0).items.get(3).label.equals("Roblox") && c.get(0).items.get(4).label.equals("Minecraft"), "Roblox, Minecraft next");
        check("com.roblox.client".equals(c.get(0).items.get(3).storePackage()) && "com.mojang.minecraftpe".equals(c.get(0).items.get(4).storePackage()), "featured packages");
        check(c.get(0).items.get(5) == Catalog.PLAY_STORE && "com.android.vending".equals(Catalog.PLAY_STORE.storePackage()), "Play Store on Featured");
        check(c.get(0).items.get(6) == Catalog.GEFORCE_NOW && Catalog.GEFORCE_NOW.webUrl == null && "com.nvidia.geforcenow".equals(Catalog.GEFORCE_NOW.storePackage()), "GeForce NOW on Featured, Play page fallback");
        check(Catalog.GFN_WEB.equals("https://play.geforcenow.com") && Catalog.gfnInBrowser(true) && !Catalog.gfnInBrowser(false), "GFN web on handhelds");
        check(c.get(0).items.get(1).webUrl == null && c.get(0).items.get(3).webUrl == null, "featured fall back to the Play Store");
        List<Catalog.Item> kids = c.get(Catalog.indexOf(c, "kids")).items;
        check(kids.size() >= 8 && kids.get(0) == Catalog.KIDS[0], "Kids row");
        for (Catalog.Item i : kids) check(i.webUrl == null && i.storePackage() != null && !i.isAction() && !i.icon.contains("logo"), i.label + " is a Play Store app tile");
        check(Rotation.next("off").equals("0") && Rotation.next("3").equals("off") && Rotation.next(null).equals("0"), "rotation cycle");
        check(Rotation.cmd("off") == null && Rotation.cmd("2") == null && Rotation.cmd("0;reboot") == null
                && Rotation.cmd("1").equals("settings put system accelerometer_rotation 0; settings put system user_rotation 1; wm fixed-to-user-rotation enabled; wm user-rotation lock 1; wm set-ignore-orientation-request true || cmd window set-ignore-orientation-request true"), "rotation cmd");
        check(Rotation.suggest("Anbernic", "RG353M", "rk30board", "rk3566_r") && !Rotation.suggest("Google", "Pixel 7", "panther", "panther"), "rotation suggest");
        List<Catalog.Category> km = Catalog.kidsMode();
        int kdc = Catalog.indexOf(km, "kdc"), kpsp = Catalog.indexOf(km, "kpsp"), kth = Catalog.indexOf(km, "kthemes"),
            kw = Catalog.indexOf(km, "kwifi"), kp = Catalog.indexOf(km, "kparent"), kst = Catalog.indexOf(km, "kstore"),
            kg = Catalog.indexOf(km, "kgames"), kr = Catalog.indexOf(km, "kroblox");
        check(kp == km.size() - 1 && kth > 0 && kdc < 0 && kpsp < 0 && kg < 0
                && Catalog.indexOf(km, "ksearch") == 0
                && Catalog.indexOf(km, "kpuzzle") == 1 && Catalog.indexOf(km, "kracing") == 2
                && Catalog.indexOf(km, "kadventure") == 3 && Catalog.indexOf(km, "klearning") == 4
                && Catalog.indexOf(km, "kstyle") == 5 && kr == 6 && Catalog.indexOf(km, "kcloud") < 0
                && km.get(kw).items.size() == 2 && km.get(kw).items.get(0) == Catalog.KIDS_WIFI
                && km.get(kw).items.get(1) == Catalog.KIDS_UPDATE && "update".equals(Catalog.KIDS_UPDATE.action)
                && "wifi".equals(Catalog.KIDS_WIFI.action) && km.get(kr).items.get(0) == Catalog.ROBLOX
                && kst < 0 && Catalog.kidsBlocked(Catalog.PLAY_STORE), "kids mode rows");
        for (Catalog.Category k : km) for (Catalog.Item i : k.items)
            check(!i.isRom() && !Catalog.cloudGaming(i) && (!i.isAction() || i.action.startsWith("roblox:") || i.action.equals("goto:roblox") || i.action.equals("wifi") || i.action.equals("update") || i.action.equals("kthemes") || i.action.equals("adultpin") || i.action.equals("settingspin") || i.action.equals("nova") || i.action.equals("novashow") || i.action.equals("novalistenhidden")), "kids mode has only Android games: " + i.label);
        playArtChecks();
        check(c.get(0).items.get(7) == Catalog.MARVEL_RIVALS && Catalog.indexOf(km, "kcloud") < 0, "Marvel Rivals on Featured, not in Kids");
        check(Catalog.RIVALS_GFN.equals("https://play.geforcenow.com/games?game-id=4b44eb06-3d68-4ca0-92b8-ce4ffd20dff4&utm_source=codeaque&utm_campaign=launcher"), "GFN deep link");
        check(Catalog.RIVALS_HEADER.startsWith("https://") && Catalog.RIVALS_HEADER.contains("/2767030/"), "Steam header");
        check("Home".equals(Catalog.cleanSsid("\"Home\"")) && Catalog.cleanSsid("<unknown ssid>") == null && Catalog.cleanSsid(null) == null, "ssid");
        dreamcastChecks(c);
        UpdateInfo ui = UpdateInfo.parse("{\"tag_name\":\"v0.10.6\",\"body\":\"## CodeAque\\r\\n\\r\\n- **Pop-up** for \\\"updates\\\"\\n- more\",\"assets\":[{\"browser_download_url\":\"https://x/codeaque-launcher.apk\"},{\"browser_download_url\":\"https://x/SHA256SUMS.txt\"}]}");
        check(ui != null && "0.10.6".equals(ui.version) && "Pop-up for \"updates\"".equals(UpdateInfo.whatsNew(ui.notes)), "update notes line: " + (ui == null ? null : UpdateInfo.whatsNew(ui.notes)));
        check(UpdateInfo.whatsNew("").isEmpty() && UpdateInfo.whatsNew(null).isEmpty(), "no notes");
        Catalog.Item newGame = Catalog.deviceApp("New Game", "com.example.newgame", "com.example.newgame.Main");
        List<Catalog.Category> ka = Catalog.kidsMode(List.of(), List.of(newGame));
        check(ka.get(Catalog.indexOf(ka, "kadded")).id.equals("kadded") && ka.get(Catalog.indexOf(ka, "kadded")).items.get(0) == newGame && Catalog.indexOf(km, "kadded") < 0, "My New Games row only when there are some");
        check(Catalog.newPackages(new java.util.HashSet<>(List.of("a", "b")), new java.util.HashSet<>(List.of("a", "b", "d", "c"))).equals(List.of("c", "d")), "installed-since diff");
        // 0.10.1 boot animation
        List<String> sa = new java.util.ArrayList<>(); for (Catalog.Item i : c.get(Catalog.indexOf(c, "settings")).items) sa.add(i.action);
        check(sa.contains("kids") && sa.contains("kidsprofile") && sa.contains("screentime") && sa.contains("alwayskids") && sa.contains("kidslock") && sa.contains("lockphone") && sa.contains("removeowner") && sa.contains("bootanim") && sa.indexOf("bootanim") == sa.indexOf("update") + 1, "boot animation after Update");
        List<Catalog.Category> nm = Catalog.categories(List.of(), List.of(), List.of(), List.of(), false, false);
        for (Catalog.Item i : nm.get(Catalog.indexOf(nm, "settings")).items) check(!"bootanim".equals(i.action), "hidden without Magisk");
        check(nm.size() == c.size() && Catalog.categories(List.of(), List.of(), List.of(), List.of(), false, true).get(Catalog.indexOf(c, "settings")).items.size() == sa.size(), "shown with Magisk");
        check(BootAnim.URL.equals("https://github.com/hoodlumasylum/codeaque/releases/download/bootanim-v1.0/codeaque-bootanim-v1.0.zip"), "bootanim url");
        check(BootAnim.SHA256.equals("b1616fad964be479a5c125505defbd1b9b99fa3cef97cff3f09b5ea35b3600c1"), "bootanim sha");
        check(BootAnim.installCmd("/storage/emulated/0/Download/codeaque-bootanim-v1.0 (1).zip".replace(" ", "")).equals("magisk --install-module '/storage/emulated/0/Download/codeaque-bootanim-v1.0(1).zip'"), "install cmd");
        boolean threw = false; try { BootAnim.installCmd("/sdcard/x';reboot;'.zip"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw && !BootAnim.safePath("/a/../b.zip") && !BootAnim.safePath("/a b.zip"), "unsafe paths refused");
        check(BootAnim.removeCmd().endsWith("touch /data/adb/modules/codeaque-bootanim/remove"), "remove cmd");
        check(!BootAnim.available(false, x -> false) && BootAnim.available(true, x -> false) && BootAnim.available(false, x -> x.equals("/system/xbin/su")), "magisk detection");
        try { check(BootAnim.matches(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[0])) == false
                && BootAnim.hex(new byte[]{0, (byte) 255}).equals("00ff"), "sha hex"); } catch (Exception e) { throw new AssertionError(e); }
        check(Palette.byId("nope") == Palette.NEON && Palette.ALL[0] == Palette.NEON, "default theme is Neon");
        check(Palette.NEON.gold == 0xFF3FD4FF && !Palette.NEON.aura && !Palette.NEON.burst, "Neon: cyan accent, calm background");
        check(Palette.POWERUP.name.equals("Power Up") && Palette.POWERUP.aura, "Power Up theme");
        check(Palette.next("neon").equals("powerup") && Palette.next("powerup").equals("catch") && Palette.next("catch").equals("christmas") && Palette.next("christmas").equals("phoenix") && Palette.next("phoenix").equals("neon"), "theme picker cycles");
        check(Palette.CATCH.name.equals("Catch") && Palette.CATCH.burst && "chime".equals(Palette.CATCH.selectSound), "Catch theme");
        check("powerup".equals(Palette.POWERUP.selectSound) && Palette.NEON.selectSound == null, "theme sounds");
        Set<String> acts = new HashSet<>();
        for (Catalog.Item i : c.get(Catalog.indexOf(c, "settings")).items) acts.add(i.action);
        check(acts.contains("theme") && acts.contains("wallpaper"), "Settings has Theme and Wallpaper");
        check(Catalog.sampleSize(4000, 640) == 4 && Catalog.sampleSize(600, 640) == 1, "wallpaper is decoded small");
        check(Palette.XMAS.name.equals("Christmas") && Palette.XMAS.snow && "jingle".equals(Palette.XMAS.selectSound), "Christmas theme");
        // APK self-update
        check(UpdateInfo.newer("0.9.2", "0.9.1") && UpdateInfo.newer("v1.0.0", "0.9.9") && !UpdateInfo.newer("0.9.1", "0.9.1") && !UpdateInfo.newer("0.8.9", "0.9.0"), "versions");
        String json = "{\"tag_name\": \"v0.9.2\", \"assets\": [{\"browser_download_url\": \"https://x/d/v0.9.2/codeaque-launcher.apk\"},"
                + "{\"browser_download_url\": \"https://x/d/v0.9.2/SHA256SUMS.txt\"}]}";
        UpdateInfo u = UpdateInfo.parse(json);
        check(u != null && u.version.equals("0.9.2") && u.apkUrl.endsWith("codeaque-launcher.apk") && u.sumsUrl.endsWith("SHA256SUMS.txt"), "release json");
        check(UpdateInfo.parse("{\"tag_name\": \"v1\"}") == null, "no APK, no update");
        String h = "ab".repeat(32);
        check(h.equals(UpdateInfo.expectedSha("1111  atari-0.9.2.tar.gz\n" + h + "  codeaque-launcher.apk\n")), "sha from sums");
        check(UpdateInfo.expectedSha("zz  codeaque-launcher.apk") == null, "bad sums line ignored");
        check(acts.contains("update"), "Settings has Update");
        {   // 0.10.28 home fix, update badge, version in Settings
            Catalog.VERSION = "9.9.9";
            List<Catalog.Category> c8 = Catalog.categories(List.of(), List.of(), List.of(), List.of(), false, false);
            List<Catalog.Item> st = c8.get(Catalog.indexOf(c8, "settings")).items;
            check(st.get(0).label.equals("CodeAque v9.9.9") && "about".equals(st.get(0).action), "version first in Settings");
            List<String> a8 = new java.util.ArrayList<>(); for (Catalog.Item i : st) a8.add(i.action);
            check(a8.contains("makehome") && a8.contains("hidelaunchers"), "home actions in Settings");
            check(LockPin.needsPin("makehome") && LockPin.needsPin("hidelaunchers") && !LockPin.needsPin("about"), "home actions PIN");
            check(Catalog.MAKE_HOME_CMDS[0].equals("cmd package set-home-activity com.codeaque.launcher/.MainActivity") && Catalog.MAKE_HOME_CMDS[1].startsWith("pm set-home-activity ") && Catalog.ROLE_CMD.equals("cmd role add-role-holder android.app.role.HOME com.codeaque.launcher"), "home cmds");
            check(!Catalog.canHideLauncher("com.codeaque.launcher") && !Catalog.canHideLauncher("com.android.settings") && Catalog.canHideLauncher("com.magneticchen.daijishou"), "hide launchers safe");
            Catalog.updateAvailable = false; check(!Catalog.showsUpdateBadge(Catalog.KIDS_UPDATE), "no badge");
            Catalog.updateAvailable = true; check(Catalog.showsUpdateBadge(Catalog.KIDS_UPDATE) && !Catalog.showsUpdateBadge(Catalog.KIDS_WIFI), "badge");
            Catalog.updateAvailable = false; Catalog.VERSION = "?";
        }
        {   // 0.10.29 touch-era parent tools: Downloads, Lock mode tile hides, Phone / Messages, edit a safe contact
            check(Catalog.LIBRETORRENT.equals("org.proninyaroslav.libretorrent") && "downloads".equals(Catalog.DOWNLOADS.action), "libretorrent");
            check(Catalog.kidsBlocked(Catalog.DOWNLOADS) && !Catalog.kidsBlocked(Catalog.CALL_TEXT), "downloads never in kids, phone is allowed");
            check("Phone / Messages".equals(Catalog.CALL_TEXT.label) && "calltext".equals(Catalog.CALL_TEXT.action), "phone tile");
            java.util.List<String> parentActs = new java.util.ArrayList<>();
            for (Catalog.Item i : c.get(Catalog.indexOf(c, "settings")).items) parentActs.add(i.action);
            check(parentActs.contains("downloads") && parentActs.contains("lock") && parentActs.contains("safecontacts") && parentActs.contains("phonesettings") && parentActs.contains("phoneaccounts") && parentActs.contains("homeapp"), "parent tiles present before lock");
            java.util.List<Catalog.Category> locked = Catalog.applyLockMode(c);
            java.util.List<String> left = new java.util.ArrayList<>();
            for (Catalog.Item i : locked.get(Catalog.indexOf(locked, "settings")).items) left.add(i.action);
            check(!left.contains("lock") && left.contains("phonesettings") && left.contains("phoneaccounts") && left.contains("homeapp") && left.contains("downloads") && left.contains("changepin") && left.contains("safecontacts"), "lock hides Lock mode tile; Phone Settings stay (PIN) " + left);
            // 0.10.30 Grown-ups row in Kids mode and the parent entries (home-screen aliases, long-press shortcuts)
            for (Catalog.Category k : Catalog.kidsMode()) if (k.id.equals("kparent")) check(k.items.size() == 2 && k.items.get(0) == Catalog.ADULT_PIN && k.items.get(1) == Catalog.SETTINGS_PIN, "grown-ups row");
            check("adultpin".equals(Catalog.ADULT_PIN.action) && "settingspin".equals(Catalog.SETTINGS_PIN.action) && LockPin.needsPin("adultpin") && LockPin.needsPin("settingspin"), "grown-ups actions need the PIN");
            check(!Catalog.kidsBlocked(Catalog.ADULT_PIN) && !Catalog.kidsBlocked(Catalog.SETTINGS_PIN) && !Catalog.hiddenInLock("adultpin") && !Catalog.hiddenInLock("settingspin"), "grown-ups tiles shown in Kids mode and Lock mode");
            check("adult".equals(Catalog.entryTarget(null, "com.codeaque.launcher.AdultAlias")) && "settings".equals(Catalog.entryTarget(null, "com.codeaque.launcher.SettingsAlias"))
                    && "adult".equals(Catalog.entryTarget(Catalog.OPEN_ADULT, "com.codeaque.launcher.MainActivity")) && "settings".equals(Catalog.entryTarget(Catalog.OPEN_SETTINGS, null))
                    && Catalog.entryTarget("android.intent.action.MAIN", "com.codeaque.launcher.MainActivity") == null && Catalog.entryTarget(null, null) == null, "entry targets");
            check(Catalog.hiddenInLock("lock") && Catalog.hiddenInLock("settings") && Catalog.hiddenInLock("home") && !Catalog.hiddenInLock("phonesettings") && !Catalog.hiddenInLock("phoneaccounts") && !Catalog.hiddenInLock("homeapp") && !Catalog.hiddenInLock("wifi") && !Catalog.hiddenInLock("downloads"), "hidden actions");
            for (Catalog.Category k : Catalog.kidsMode()) for (Catalog.Item i : k.items) check(!"downloads".equals(i.action), "no downloads row in kids");
            check(LockPin.needsPin("downloads") && LockPin.needsPin("safecontacts") && !LockPin.needsPin("calltext"), "downloads and safe contacts need the PIN when locked");
            java.util.List<SafeContacts.Contact> people = new java.util.ArrayList<>();
            check(SafeContacts.add(people, "Mum", "07700900123") && SafeContacts.update(people, 0, "Mummy", "07700900123") && people.get(0).name.equals("Mummy"), "edit name");
            check(SafeContacts.update(people, 0, "Dad", "+447700900999") && people.get(0).number.equals("+447700900999"), "edit number");
            check(!SafeContacts.update(people, 0, "X", "12") && people.get(0).name.equals("Dad") && !SafeContacts.update(people, 5, "Z", "07700900111") && !SafeContacts.update(null, 0, "A", "07700900111"), "bad edit refused");
            check(SafeContacts.add(people, "Nan", "07700900456") && !SafeContacts.update(people, 1, "Nan2", "07700900999".substring(0, 0) + people.get(0).number), "cannot steal another number");
        }
        homeScrollChecks();
        coverFitChecks();
        novaAndInstallPinChecks();
        System.out.println("CatalogTest: all checks passed");
    }

    /** 0.10.3: a local mock of a Play listing; nothing here touches the internet. */
    static void playArtChecks() {
        try {
            com.sun.net.httpserver.HttpServer srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            int port = srv.getAddress().getPort(); String base = "http://127.0.0.1:" + port;
            final int[] hits = {0};
            srv.createContext("/store/apps/details", ex -> {
                hits[0]++;
                String q = ex.getRequestURI().getQuery(); byte[] body;
                if (q.contains("id=game.ok")) body = ("<html><meta property=\"og:image\" content=\"" + base + "/img/icon\">"
                        + "<img src=\"" + base + "/img/feature\" class=\"x\" alt=\"Feature graphic\"></html>").getBytes("UTF-8");
                else if (q.contains("id=game.iconly")) body = ("<meta property=\"og:image\" content=\"" + base + "/img/icon\">"
                        + "<img src=\"https://evil.example/x.png\" alt=\"Feature graphic\">").getBytes("UTF-8");
                else if (q.contains("id=game.foreign")) body = "<meta property=\"og:image\" content=\"https://evil.example/i.png\">".getBytes("UTF-8");
                else if (q.contains("id=game.shots")) body = ("<meta property=\"og:image\" content=\"" + base + "/img/icon\">"
                        + "<img src=\"" + base + "/img/feature\" class=\"x\" alt=\"Screenshot image\"></html>").getBytes("UTF-8");
                else { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                ex.sendResponseHeaders(200, body.length); ex.getResponseBody().write(body); ex.close();
            });
            srv.createContext("/img/", ex -> { byte[] b = ex.getRequestURI().getPath().endsWith("icon") ? new byte[]{1, 2, 3} : new byte[]{4, 5, 6, 7};
                ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
            srv.start();
            java.io.File dir = java.nio.file.Files.createTempDirectory("playart").toFile();
            check(PlayArt.fetch(base, "game.ok", dir) && PlayArt.iconFile(dir, "game.ok").length() == 3 && PlayArt.featureFile(dir, "game.ok").length() == 4, "icon + feature graphic cached");
            check(PlayArt.fetch(base, "game.iconly", dir) && !PlayArt.featureFile(dir, "game.iconly").exists(), "foreign feature graphic refused, icon kept");
            check(PlayArt.fetch(base, "game.shots", dir) && PlayArt.iconFile(dir, "game.shots").length() == 3 && PlayArt.featureFile(dir, "game.shots").length() == 4, "listing screenshot cached as the cover when there is no feature");
            check(!PlayArt.fetch(base, "game.foreign", dir) && !PlayArt.iconFile(dir, "game.foreign").exists() && PlayArt.missFile(dir, "game.foreign").exists(), "foreign icon refused");
            check(!PlayArt.fetch(base, "game.missing", dir) && !PlayArt.iconFile(dir, "game.missing").exists(), "404 keeps the CodeAque icon");
            check(!PlayArt.fetch(base, "bad id;rm", dir), "bad package id");
            srv.stop(0);
            check(!PlayArt.fetch(base, "game.offline", dir), "offline keeps the CodeAque icon");
            check(!PlayArt.fetch("http://example.com", "game.ok", dir), "only https Play or a loopback test server");
            String data = "<meta property=\"og:image\" content=\"https://play-lh.googleusercontent.com/OWNICONabcdefgh=s0-br30\">"
                    + "[null,2,[1024,500],[null,null,\"https://play-lh.googleusercontent.com/OTHERFEATURE1234\"]]"
                    + "[null,2,[512,512],[null,null,\"https://play-lh.googleusercontent.com/OWNICONabcdefgh\"]],[null,2,[500,1024],[null,null,\"https://play-lh.googleusercontent.com/OWNFEATURE5678\"]]";
            String[] fg = PlayArt.parse(data, false);
            check("https://play-lh.googleusercontent.com/OWNFEATURE5678=w640-h312".equals(fg[0]), "own feature graphic only: " + fg[0]);
            check(PlayArt.parse("<meta property=\"og:image\" content=\"https://play-lh.googleusercontent.com/OWNICONabcdefgh=s0\">[null,2,[1024,500],[null,null,\"https://play-lh.googleusercontent.com/OTHER\"]]", false)[0] == null, "no other app's graphic");
            String[] a = PlayArt.parse("<meta property=\"og:image\" content=\"https://play-lh.googleusercontent.com/abc=s0-br30\">", false);
            check(a[0] == null && "https://play-lh.googleusercontent.com/abc=s256".equals(a[1]), "real Play icon url " + a[1]);
            String[] shot = PlayArt.parse("<meta property=\"og:image\" content=\"https://play-lh.googleusercontent.com/ICONSYLLabcdefgh=s0\">"
                    + "<img src=\"https://play-lh.googleusercontent.com/SHOTMOTORabcdefghijkl=w526-h296-rw\" class=\"x\" alt=\"Screenshot image\">"
                    + "<img src=\"https://play-lh.googleusercontent.com/SIMILARAPPSHOT123456=w526-h296\" alt=\"Screenshot image\">", false);
            check("https://play-lh.googleusercontent.com/SHOTMOTORabcdefghijkl=w640-h312".equals(shot[0]), "first-party screenshot as cover when there is no feature: " + shot[0]);
            String[] both = PlayArt.parse("<img src=\"https://play-lh.googleusercontent.com/FEATWIDEabcdefghijkl\" alt=\"Feature graphic\">"
                    + "<meta property=\"og:image\" content=\"https://play-lh.googleusercontent.com/ICONSYLLabcdefgh=s0\">"
                    + "<img src=\"https://play-lh.googleusercontent.com/SHOTMOTORabcdefghijkl\" alt=\"Screenshot image\">", false);
            check("https://play-lh.googleusercontent.com/FEATWIDEabcdefghijkl=w640-h312".equals(both[0]), "official feature still wins over a screenshot: " + both[0]);
            String[] altFirst = PlayArt.parse("<img alt=\"Screenshot image\" src=\"https://play-lh.googleusercontent.com/ALTSHOTORDER123456\">"
                    + "<meta property=\"og:image\" content=\"https://play-lh.googleusercontent.com/ICONSYLLabcdefgh=s0\">", false);
            check("https://play-lh.googleusercontent.com/ALTSHOTORDER123456=w640-h312".equals(altFirst[0]), "screenshot alt-before-src: " + altFirst[0]);
            check(PlayArt.listingUrl(PlayArt.PLAY, "com.roblox.client").equals("https://play.google.com/store/apps/details?id=com.roblox.client&hl=en_GB"), "listing url");
            check(ArtLoader.PARALLEL == 3 && ArtLoader.MAX_TRIES == 3, "art loader bounds");
            check(ArtLoader.backoffMs(0) == 400 && ArtLoader.backoffMs(1) == 1200 && ArtLoader.backoffMs(2) == 3600, "art backoff");
            check(ArtLoader.OK == 1 && ArtLoader.NONE == 0 && ArtLoader.RETRY == -1, "art status");
            {
                final int[] plays = {0};
                ArtLoader.Backend back = new ArtLoader.Backend() {
                    public int play(String pkg, java.io.File dir) { plays[0]++; return ArtLoader.OK; }
                    public int roblox(String place, java.io.File dir) { return ArtLoader.NONE; }
                    public int steam(String appid, java.io.File dir) { return ArtLoader.NONE; }
                };
                ArtLoader loader = new ArtLoader(back, null);
                java.io.File artDir = java.io.File.createTempFile("art", "d"); artDir.delete(); artDir.mkdirs();
                for (int i = 0; i < 20; i++) loader.requestPlay("com.example.game" + i, artDir);
                check(loader.queued() == 20, "scroll does not drop art requests: " + loader.queued());
                loader.requestPlay("com.example.game0", artDir);   // already queued — still 20, not dropped, not duplicated
                check(loader.queued() == 20, "duplicate play request is not a second slot");
                int ran = 0;
                while (loader.step()) ran++;
                check(ran == 20 && plays[0] == 20 && loader.queued() == 0, "all queued art jobs run");
            }

        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    static void dreamcastChecks(List<Catalog.Category> c) {
        int dc = Catalog.indexOf(c, "dreamcast");
        check(dc == Catalog.indexOf(c, "retro") + 1 && c.get(dc).items.get(0) == Catalog.DREAMCAST && "com.flycast.emulator".equals(Catalog.DREAMCAST.storePackage())
                && Catalog.DREAMCAST.label.equals("Dreamcast"), "Dreamcast section after Retro");
        try {
            java.io.File root = java.nio.file.Files.createTempDirectory("dc").toFile();
            java.io.File sd = java.nio.file.Files.createTempDirectory("sd").toFile();
            new java.io.File(root, "ROMs/dc/Crazy Taxi").mkdirs(); new java.io.File(root, "Download").mkdirs(); new java.io.File(sd, "roms/dreamcast").mkdirs();
            new java.io.File(root, "Pictures").mkdirs();
            for (String f : new String[]{"ROMs/dc/Crazy Taxi/Crazy Taxi.gdi", "ROMs/dc/Crazy Taxi/track01.bin", "ROMs/dc/Sonic.chd", "Download/Shenmue.cdi",
                    "Download/Other.chd", "Download/notes.txt", "Pictures/Hidden.gdi"}) new java.io.File(root, f).createNewFile();
            new java.io.File(sd, "roms/dreamcast/Jet Set.cdi").createNewFile();
            List<java.io.File> got = DcScan.scan(java.util.Arrays.asList(root, sd, null));
            java.util.Set<String> names = new java.util.TreeSet<>(); for (java.io.File f : got) names.add(DcScan.title(f));
            check(names.equals(new java.util.TreeSet<>(java.util.Arrays.asList("Crazy Taxi", "Sonic", "Shenmue", "Jet Set"))), "dreamcast scan " + names);
            List<Catalog.Item> roms = new java.util.ArrayList<>();
            for (java.io.File f : got) roms.add(Catalog.rom(f.getName(), "file://" + f.getAbsolutePath()));
            roms.add(Catalog.rom("Mario.sfc", "content://x/Mario.sfc"));
            Catalog.Category d = Catalog.dreamcast(roms);
            check(d.items.size() == 7 && d.items.get(0) == Catalog.DREAMCAST && d.items.get(1) == Catalog.FILES && d.items.get(2) == Catalog.RESCAN, "dreamcast tiles " + d.items.size());
            {   // 0.10.9: SD card with uppercase ROMS/Dreamcast and ROMS/PSP, games in sub-folders
                java.io.File sdc = java.nio.file.Files.createTempDirectory("sdcard").toFile();
                new java.io.File(sdc, "ROMS/Dreamcast/Crazy Taxi/disc").mkdirs(); new java.io.File(sdc, "ROMS/Dreamcast/Crazy Taxi/disc/crazy.gdi").createNewFile();
                new java.io.File(sdc, "ROMS/Dreamcast/Shen").mkdirs(); new java.io.File(sdc, "ROMS/Dreamcast/Shen/shen.chd").createNewFile();
                new java.io.File(sdc, "ROMS/PSP/Kart").mkdirs(); new java.io.File(sdc, "ROMS/PSP/Kart/kart.iso").createNewFile();
                new java.io.File(sdc, "ROMS/SNES").mkdirs(); new java.io.File(sdc, "ROMS/SNES/x.chd").createNewFile();
                List<String> sc = new java.util.ArrayList<>();
                java.util.Set<String> dn = new java.util.TreeSet<>(); for (java.io.File f : DcScan.scan(java.util.Arrays.asList(sdc), sc)) dn.add(f.getName());
                check(dn.equals(new java.util.TreeSet<>(java.util.Arrays.asList("crazy.gdi", "shen.chd"))), "ROMS/Dreamcast " + dn);
                check(sc.size() == 1 && sc.get(0).endsWith("ROMS/Dreamcast"), "scanned " + sc);
                java.util.Set<String> pn2 = new java.util.TreeSet<>(); for (java.io.File f : PspScan.scan(java.util.Arrays.asList(sdc))) pn2.add(f.getName());
                check(pn2.equals(new java.util.TreeSet<>(java.util.Arrays.asList("kart.iso"))), "ROMS/PSP " + pn2);
            }
            {   // 0.10.10: an SD card labelled "Dreamcast" with game folders at its root
                java.io.File vol = java.nio.file.Files.createTempDirectory("CA86-E82D").toFile();
                for (String g : new String[]{"Crazy Taxi/ct.gdi", "Shenmue Disc 1/s1.chd", "Rayman 2 - The Great Escape/disc/r2.cdi", "Music/song.chd", "DCIM/x.gdi"}) {
                    java.io.File f = new java.io.File(vol, g); f.getParentFile().mkdirs(); f.createNewFile();
                }
                List<java.io.File> got2 = new java.util.ArrayList<>(); List<String> sc2 = new java.util.ArrayList<>();
                DcScan.scanVolumeRoot(vol, "Dreamcast", got2, sc2);
                java.util.Set<String> n2 = new java.util.TreeSet<>(); for (java.io.File f : got2) n2.add(f.getName());
                check(n2.equals(new java.util.TreeSet<>(java.util.Arrays.asList("ct.gdi", "s1.chd", "r2.cdi"))), "volume root " + n2);
                List<java.io.File> got3 = new java.util.ArrayList<>();
                DcScan.scanVolumeRoot(vol, "USB drive", got3, null);
                java.util.Set<String> n3 = new java.util.TreeSet<>(); for (java.io.File f : got3) n3.add(f.getName());
                check(n3.equals(new java.util.TreeSet<>(java.util.Arrays.asList("ct.gdi", "r2.cdi"))), "unlabelled volume: no chd " + n3);
            }
            {   // 0.10.11 fallbacks (MediaStore / root find paths)
                List<java.io.File> fdc = new java.util.ArrayList<>(), fps = new java.util.ArrayList<>();
                DcScan.classify("/storage/emulated/0/ROMS/Dreamcast/Shenmue Disc 1/Shenmue Disc 1.cdi", false, fdc, fps);
                DcScan.classify("/storage/emulated/0/ROMS/Dreamcast/Crazy Taxi/ct.chd", false, fdc, fps);
                DcScan.classify("/storage/CA86-E82D/Jet Grind Radio/jgr.chd", true, fdc, fps);
                DcScan.classify("/storage/CA86-E82D/PSP/GTA.iso", true, fdc, fps);
                DcScan.classify("/storage/emulated/0/ROMS/PSP/x.chd", false, fdc, fps);
                DcScan.classify("/storage/emulated/0/Download/linux.iso", false, fdc, fps);
                DcScan.classify("/storage/emulated/0/Movies/film.iso", false, fdc, fps);
                DcScan.classify("/storage/emulated/0/Download/x.chd", false, fdc, fps);
                check(fdc.size() == 3 && fps.size() == 3, "classify " + fdc + " " + fps);
                check(DcScan.fromRootPath("/data/media/0/ROMS/a.cdi").equals("/storage/emulated/0/ROMS/a.cdi")
                        && DcScan.fromRootPath("/mnt/media_rw/CA86-E82D/a.gdi").equals("/storage/CA86-E82D/a.gdi"), "root paths");
                check(DcScan.probe(new java.io.File("/nonexistent/x")).contains("exists=false") && DcScan.probe(new java.io.File("/nonexistent/x")).contains("list=null"), "probe");
            }
            {   // 0.10.12 clean names + player cover names
                check(Catalog.cleanTitle("Crazy_Taxi (USA) [!]").equals("Crazy Taxi"), "clean " + Catalog.cleanTitle("Crazy_Taxi (USA) [!]"));
                check(Catalog.cleanTitle("Shenmue Disc 1").equals("Shenmue Disc 1") && Catalog.cleanTitle("(USA)").equals("(USA)"), "clean disc");
                check(Catalog.gameRom("Rayman 2 (Europe) (En,Fr).cdi", "file:///x/Rayman 2 (Europe) (En,Fr).cdi").label.equals("Rayman 2"), "gameRom label");
                java.util.List<String> cn = Catalog.coverNames("Shenmue Disc 1.cdi", "Shenmue Disc 1");
                check(cn.get(0).equals("cover.png") && cn.contains("folder.jpg") && cn.contains("boxart.webp") && cn.contains("shenmue disc 1.jpg"), "cover names " + cn);
                Catalog.Item gr = Catalog.gameRom("ct.gdi", "file:///storage/emulated/0/ROMS/Dreamcast/Crazy Taxi/ct.gdi");
                check(Catalog.isDreamcastRom(gr), "gameRom stays a Dreamcast game");
                check("shenmue_disc_1".equals(Catalog.haDcCover("Shenmue Disc 1")) && "shenmue_disc_1".equals(Catalog.haDcCover("SHENMUE")) && "shenmue_disc_2".equals(Catalog.haDcCover("Shenmue (Disc 2)")) && "shenmue_disc_2".equals(Catalog.haDcCover("shenmue_cd2")) && "shenmue_disc_2".equals(Catalog.haDcCover("Shenmue - Disk 02")), "shenmue covers");
                check("jet_grind_radio".equals(Catalog.haDcCover("Jet Set Radio")) && "jet_grind_radio".equals(Catalog.haDcCover("jet-grind_radio")) && "crazy_taxi".equals(Catalog.haDcCover("x", "Crazy Taxi")) && "marvel_vs_capcom".equals(Catalog.haDcCover("Marvel vs. Capcom 2")) && "mortal_kombat_gold".equals(Catalog.haDcCover("Mortal Kombat Gold")) && "prince_of_persia".equals(Catalog.haDcCover("Prince of Persia - Arabian Nights")) && "rayman_2".equals(Catalog.haDcCover("RAYMAN 2")) && Catalog.haDcCover("Rayman", "Soul Calibur") == null, "ha dc covers");
                check("sonic_adventure".equals(Catalog.haDcCover("Sonic Adventure")) && "sonic_adventure".equals(Catalog.haDcCover("SONIC_ADVENTURE (USA)")) && "sonic_adventure_2".equals(Catalog.haDcCover("Sonic Adventure 2")) && "sonic_adventure_2".equals(Catalog.haDcCover("sonic-adventure-II")) && "spider_man".equals(Catalog.haDcCover("Spider-Man")) && "spider_man".equals(Catalog.haDcCover("x", "spiderman")) && "street_fighter_3".equals(Catalog.haDcCover("Street Fighter III - 3rd Strike")) && "street_fighter_3".equals(Catalog.haDcCover("Street Fighter III Third Strike")) && "street_fighter_3".equals(Catalog.haDcCover("street_fighter_3")) && "toy_story_2".equals(Catalog.haDcCover("Toy Story 2 - Buzz Lightyear to the Rescue")) && "toy_story_2".equals(Catalog.haDcCover("Buzz Lightyear to the Rescue")) && Catalog.haDcCover("Sonic Shuffle") == null, "0.10.18 covers");
                {   // 0.10.20 update fallbacks from the release JSON
                    String j = "{\"tag_name\":\"v0.10.20\",\"assets\":[{\"url\":\"https://api.github.com/repos/h/c/releases/assets/11\",\"name\":\"SHA256SUMS.txt\",\"uploader\":{\"url\":\"https://api.github.com/users/h\"},\"digest\":\"sha256:" + "a".repeat(64) + "\",\"browser_download_url\":\"https://github.com/h/c/releases/download/v0.10.20/SHA256SUMS.txt\"},{\"url\":\"https://api.github.com/repos/h/c/releases/assets/22\",\"name\":\"codeaque-launcher.apk\",\"uploader\":{\"url\":\"https://api.github.com/users/h\"},\"digest\":\"sha256:" + "B".repeat(64) + "\",\"browser_download_url\":\"https://github.com/h/c/releases/download/v0.10.20/codeaque-launcher.apk\"}],\"body\":\"x\"}";
                    UpdateInfo ui = UpdateInfo.parse(j);
                    check(ui != null && "b".repeat(64).equals(ui.apkDigest) && "https://api.github.com/repos/h/c/releases/assets/22".equals(ui.apkApiUrl), "apk digest/api url " + (ui == null ? null : ui.apkDigest + " " + ui.apkApiUrl));
                }
                {   // 0.10.21 Lock mode
                    check(LockPin.validPin("1234") && LockPin.validPin("123456") && !LockPin.validPin("123") && !LockPin.validPin("1234567") && !LockPin.validPin("12a4") && !LockPin.validPin(null), "pin format");
                    String h1 = LockPin.newHash("4821"), h2 = LockPin.newHash("4821");
                    check(!h1.equals("4821") && !h1.equals(h2) && h1.matches("[0-9a-f]{32}:[0-9a-f]{64}"), "salted hash " + h1);
                    check(LockPin.check("4821", h1) && LockPin.check("4821", h2) && !LockPin.check("4822", h1) && !LockPin.check("4821", null) && !LockPin.check("4821", "junk"), "pin check");
                    check(LockPin.backoff(4) == 0 && LockPin.backoff(5) == 30_000 && LockPin.backoff(6) == 60_000 && LockPin.backoff(50) == 600_000, "backoff");
                    for (String p : new String[]{"com.android.settings", "com.android.dialer", "com.google.android.dialer", "com.android.contacts", "com.google.android.contacts", "com.android.mms", "com.google.android.apps.messaging", "com.android.phone", "com.android.server.telecom", "com.android.tv.settings"})
                        check(LockPin.blocked(p), "blocked " + p);
                    for (String p : new String[]{"com.roblox.client", "com.mojang.minecraftpe", "com.flycast.emulator", "org.ppsspp.ppsspp", "com.kiloo.subwaysurf", "com.android.vending", null})
                        check(!LockPin.blocked(p), "allowed " + p);
                    check(LockPin.needsPin("settings") && LockPin.needsPin("kidsoff") && LockPin.needsPin("lock") && LockPin.needsPin("filemgr") && LockPin.needsPin("files") && LockPin.needsPin("allapps") && LockPin.needsPin("home")
                            && !LockPin.needsPin("wifi") && !LockPin.needsPin("update") && !LockPin.needsPin("goto:cloud"), "pin actions");
                }
                {   // 0.10.22 Safe contacts
                    java.util.List<SafeContacts.Contact> l = new java.util.ArrayList<>();
                    check(SafeContacts.add(l, "Mum", "07700 900123") && SafeContacts.add(l, "Nan\tx", "+44 (0)7700-900456") && !SafeContacts.add(l, "Bad", "12") && l.size() == 2, "add " + l.size());
                    check(SafeContacts.add(l, "Mummy", "+447700900123") && l.size() == 2 && l.get(0).name.equals("Mummy"), "same number renames");
                    check(SafeContacts.allowed("+44 7700 900123", l) && SafeContacts.allowed("07700900456", l) && !SafeContacts.allowed("07700 900999", l) && !SafeContacts.allowed(null, l) && !SafeContacts.allowed("999", l), "allowed");
                    java.util.List<SafeContacts.Contact> back = SafeContacts.parse(SafeContacts.write(l));
                    check(back.size() == 2 && back.get(1).name.equals("Nan x") && back.get(1).number.equals("+4407700900456"), "round trip " + SafeContacts.write(l));
                    check(SafeContacts.parse(null).isEmpty() && SafeContacts.parse("junk\n\tnope\n").isEmpty(), "parse junk");
                    check(!LockPin.needsPin("calltext") && LockPin.needsPin("safecontacts") && LockPin.needsPin("blockunknown") && LockPin.needsPin("safeonly"), "safe contacts pin");
                    check(Catalog.callRow().items.get(0) == Catalog.CALL_TEXT && "calltext".equals(Catalog.CALL_TEXT.action), "call row");
                }
                {   // 0.10.23 Kids mode PIN for the Play Store
                    check(Catalog.opensStore(Catalog.PLAY_STORE, true) && Catalog.opensStore(Catalog.PLAY_STORE, false), "play store tile");
                    check(Catalog.opensStore(Catalog.KIDS[0], false) && !Catalog.opensStore(Catalog.KIDS[0], true), "Get on uninstalled kids game");
                    check(!Catalog.opensStore(Catalog.KIDS_OFF, false) && !Catalog.opensStore(Catalog.MARVEL_RIVALS, false) && !Catalog.opensStore(null, false), "not store");
                    check(!LockPin.needsPin("changepin") && LockPin.validPin("1234") && LockPin.check("1234", LockPin.newHash("1234")), "change pin / default");
                }
                {   // 0.10.24 no streams / All Apps in Kids mode
                    Catalog.Item yt = Catalog.deviceApp("YouTube", "com.google.android.youtube", "x.Main"), nf = Catalog.deviceApp("Netflix", "com.netflix.mediaclient", "x.Main");
                    Catalog.Item vlc = Catalog.deviceApp("VLC", "org.videolan.vlc", "x.Main"), game = Catalog.deviceApp("Crossy Road", "com.yodo1.crossyroad", "x.Main");
                    java.util.List<Catalog.Category> kk = Catalog.kidsMode(java.util.List.of(), java.util.List.of(yt, nf, vlc, game));
                    java.util.Set<String> ids = new java.util.HashSet<>();
                    for (Catalog.Category k : kk) {
                        ids.add(k.id);
                        for (Catalog.Item i : k.items) {
                            check(!Catalog.kidsBlocked(i), "kids shows " + i.label);
                            check(!"allapps".equals(i.action) && !"filemgr".equals(i.action), "no all apps/files tile " + i.label);
                        }
                    }
                    check(!ids.contains("streams") && !ids.contains("media") && !ids.contains("allapps") && !ids.contains("settings"), "kids rows " + ids);
                    check(ids.containsAll(java.util.List.of("ksearch", "kpuzzle", "kracing", "kadventure", "klearning", "kstyle", "kroblox", "kadded", "kthemes", "kwifi", "knova", "kparent"))
                            && !ids.contains("kcloud") && !ids.contains("kplay") && !ids.contains("kgames") && !ids.contains("retro") && !ids.contains("dreamcast") && !ids.contains("psp"), "kids rows exact " + ids);
                    Catalog.Category added = kk.get(Catalog.indexOf(kk, "kadded"));
                    check(added.items.size() == 1 && added.items.get(0) == game, "only the game stays in My New Games");
                    check(Catalog.kidsBlocked(Catalog.act("All apps", "grid", "allapps")) && Catalog.streamingPkg("tv.twitch.android.app") && Catalog.streamingPkg("com.zhiliaoapp.musically") && Catalog.streamingPkg("tv.pluto.android"), "blocked");
                    for (Catalog.Item k : Catalog.KIDS) check(!Catalog.kidsBlocked(k), "kids game allowed " + k.label);
                    for (Catalog.Item k : new Catalog.Item[]{Catalog.ROBLOX, Catalog.MINECRAFT}) check(!Catalog.kidsBlocked(k), "allowed " + k.label);
                    for (Catalog.Item k : new Catalog.Item[]{Catalog.GEFORCE_NOW, Catalog.XBOX, Catalog.GFN, Catalog.LUNA, Catalog.BOOST, Catalog.MARVEL_RIVALS})
                        check(Catalog.kidsBlocked(k) && Catalog.cloudGaming(k), "cloud blocked in kids " + k.label);
                    check(Catalog.kidsBlocked(Catalog.PLAY_STORE), "play store blocked in kids");
                    // 0.10.37: emulator apps hidden in Kids / Kids 12+, still listed in adult mode
                    Catalog.Item ra = Catalog.deviceApp("RetroArch", "com.retroarch", "x.Main");
                    Catalog.Item pp = Catalog.deviceApp("PPSSPP", "org.ppsspp.ppsspp", "x.Main");
                    Catalog.Item yuzu = Catalog.deviceApp("Yuzu", "org.yuzu.yuzu_emu", "x.Main");
                    Catalog.Item lime = Catalog.deviceApp("Lime3DS", "io.github.lime3ds.android", "x.Main");
                    Catalog.Item temu = Catalog.deviceApp("Temu", "com.einnovation.temu", "x.Main");
                    java.util.List<Catalog.Category> ke = Catalog.kidsMode(java.util.List.of(), java.util.List.of(ra, pp, yuzu, lime, game));
                    Catalog.Category addedEmu = ke.get(Catalog.indexOf(ke, "kadded"));
                    check(addedEmu.items.size() == 1 && addedEmu.items.get(0) == game, "emulators not in My New Games");
                    check(Catalog.kidsEmulator(Catalog.RETROARCH) && Catalog.kidsEmulator(Catalog.PSP) && Catalog.kidsEmulator(Catalog.DREAMCAST)
                            && Catalog.kidsEmulator(ra) && Catalog.kidsEmulator(pp) && Catalog.kidsEmulator(yuzu)
                            && Catalog.emulatorPkg("xyz.aethersx2.android") && Catalog.emulatorPkg("xyz.nethersx2.android")
                            && Catalog.emulatorPkg("com.swordfish.lemuroid") && Catalog.emulatorPkg("skyline.emu")
                            && Catalog.emulatorPkg("me.magnum.melonds") && Catalog.emulatorPkg("com.dsemu.drastic")
                            && Catalog.emulatorPkg("org.ryujinx") && Catalog.emulatorPkg("com.github.stenzek.duckstation")
                            && Catalog.emulatorPkg("org.vita3k.emulator") && Catalog.emulatorPkg("com.seleuco.mame4droid")
                            && Catalog.emulatorPkg("io.mgba.mgba") && Catalog.emulatorPkg("com.explusalpha.snes9xplus")
                            && Catalog.emulatorPkg("org.redream") && Catalog.emulatorPkg("org.mupen64plusae.v3.fzurita"), "known emulators");
                    check(!Catalog.emulatorPkg("com.einnovation.temu") && !Catalog.emulatorPkg("com.roblox.client")
                            && !Catalog.emulatorPkg("com.mojang.minecraftpe") && !Catalog.emulatorPkg(null)
                            && !Catalog.kidsEmulator(Catalog.ROBLOX) && !Catalog.kidsEmulator(temu)
                            && !Catalog.kidsEmulator(game), "games and Temu are not emulators");
                    check(Catalog.kidsBlocked(Catalog.RETROARCH) && Catalog.kidsBlocked(ra) && !Catalog.kidsBlocked(Catalog.ROBLOX), "kids blocks emulator tiles");
                    // approved package still cannot bring an emulator into Kids mode
                    java.util.Set<String> appr = new java.util.HashSet<>(java.util.List.of("com.retroarch"));
                    java.util.List<Catalog.Category> kap = Catalog.kidsMode(java.util.List.of(), java.util.List.of(ra), appr);
                    check(Catalog.indexOf(kap, "kadded") < 0 && Catalog.indexOf(kap, "kemu") < 0, "approved emulator still hidden by default");
                    java.util.Set<String> allowEmu = new java.util.HashSet<>(java.util.List.of("com.retroarch"));
                    java.util.List<Catalog.Category> kemuOn = Catalog.kidsMode(java.util.List.of(), java.util.List.of(ra, game), java.util.Collections.emptySet(), false, allowEmu);
                    check(Catalog.indexOf(kemuOn, "kemu") >= 0 && kemuOn.get(Catalog.indexOf(kemuOn, "kemu")).items.get(0) == ra, "ticked emulator appears");
                    check(!Catalog.kidsBlocked(ra, allowEmu) && Catalog.kidsBlocked(ra, java.util.Collections.emptySet()), "kidsBlocked respects allow list");
                    java.util.List<Catalog.Category> kemu12 = Catalog.kidsMode(java.util.List.of(), java.util.List.of(ra), java.util.Collections.emptySet(), true, java.util.Collections.emptySet());
                    check(Catalog.indexOf(kemu12, "kemu") < 0, "Kids 12+ has its own empty allow list");
                    check(Catalog.ALWAYS_KIDS_DEFAULT && "alwayskids".equals(Catalog.ALWAYS_KIDS.action)
                            && "kidslock".equals(Catalog.KIDS_LOCK.action) && "lockphone".equals(Catalog.LOCK_PHONE.action)
                            && "removeowner".equals(Catalog.REMOVE_OWNER.action)
                            && "exitcodeaque".equals(Catalog.EXIT_CODEAQUE.action)
                            && "phonesettings".equals(Catalog.PHONE_SETTINGS.action)
                            && "phoneaccounts".equals(Catalog.PHONE_ACCOUNTS.action)
                            && "homeapp".equals(Catalog.HOME_APP.action)
                            && Catalog.settingsPkg("com.android.settings") && Catalog.settingsPkg("com.android.tv.settings")
                            && !Catalog.settingsPkg("com.android.vending") && !Catalog.settingsPkg(null)
                            && Catalog.DPM_SET_OWNER.equals("/system/bin/dpm set-device-owner com.codeaque.launcher/.AdminReceiver")
                            && Catalog.DPM_SET_OWNER_FALLBACK.equals("cmd device_policy set-device-owner com.codeaque.launcher/.AdminReceiver")
                            && Catalog.DPM_SET_OWNER_ADB.equals("dpm set-device-owner com.codeaque.launcher/.AdminReceiver"), "always start in Kids; lock phone / remove owner");
                    java.util.List<Catalog.Category> adult = Catalog.categories(java.util.List.of(), java.util.List.of(ra), java.util.List.of(Catalog.RETROARCH), java.util.List.of());
                    check(Catalog.indexOf(adult, "retro") >= 0 && adult.get(Catalog.indexOf(adult, "retro")).items.contains(Catalog.RETROARCH)
                            && adult.get(Catalog.indexOf(adult, "mygames")).items.contains(Catalog.RETROARCH)
                            && adult.get(Catalog.indexOf(adult, "allapps")).items.contains(ra), "adult mode still shows emulators");
                    // normal mode unchanged: Streams, Media and All Apps rows are still there
                    java.util.List<Catalog.Category> nm = Catalog.categories(java.util.List.of(), java.util.List.of(yt), java.util.List.of(), java.util.List.of());
                    check(Catalog.indexOf(nm, "streams") >= 0 && Catalog.indexOf(nm, "media") >= 0 && Catalog.indexOf(nm, "allapps") >= 0, "normal mode unchanged");
                }
                {   // 0.10.25 update pop-up snooze
                    long now = 1_000_000_000L, h = 3600_000L;
                    check(UpdateInfo.snoozed("0.10.26", "0.10.26", now + 4 * h, now) && UpdateInfo.snoozed("0.10.26", "0.10.26", now + h, now), "snoozed");
                    check(!UpdateInfo.snoozed("0.10.26", "0.10.26", now - 1, now) && !UpdateInfo.snoozed("0.10.27", "0.10.26", now + h, now) && !UpdateInfo.snoozed("0.10.26", "", 0, now), "not snoozed");
                    check(!UpdateInfo.snoozed("0.10.26", "0.10.26", now + 48 * h, now), "clock jump never hides it for days");
                    check(UpdateInfo.CHECK_EVERY_MS == 30 * 60_000L && UpdateInfo.RETRY_MS < UpdateInfo.CHECK_EVERY_MS && UpdateInfo.SNOOZE_MS == 4 * h, "timings");
                    check(UpdateInfo.newer("0.10.25", "0.10.24") && UpdateInfo.newer("v0.10.100", "0.10.99") && !UpdateInfo.newer("0.10.24", "0.10.24") && !UpdateInfo.newer("0.10.9", "0.10.24"), "versions");
                }
                for (Catalog.Category k : Catalog.kidsMode()) check(!k.items.contains(Catalog.KIDS_ON), "no Kids Mode tile inside Kids mode");
                {   // 0.10.27 new app placement + parent-approved Kids apps
                    check(Catalog.guessSection(true, -1, "com.x.game").equals("mygames") && Catalog.guessSection(false, 0, "a.b").equals("mygames"), "game guess");
                    check(Catalog.guessSection(false, 2, "a.b").equals("streams") && Catalog.guessSection(false, -1, "com.netflix.mediaclient").equals("streams"), "video guess");
                    check(Catalog.guessSection(false, 1, "a.b").equals("media") && Catalog.guessSection(false, -1, "com.spotify.music").equals("media"), "music guess");
                    check(Catalog.guessSection(false, 7, "com.example.notes").equals("allapps"), "other guess");
                    java.util.Set<String> saved = Catalog.withPlacement(null, "com.a", "media");
                    saved = Catalog.withPlacement(saved, "com.a", "streams"); saved = Catalog.withPlacement(saved, "com.b", "mygames");
                    java.util.Map<String, String> pl = Catalog.placements(saved);
                    check(pl.size() == 2 && pl.get("com.a").equals("streams") && pl.get("com.b").equals("mygames"), "placements " + pl);
                    check(Catalog.placements(java.util.Set.of("x=nowhere", "junk")).isEmpty() && Catalog.withPlacement(saved, "com.a", null).size() == 1, "bad placements ignored / removed");
                    Catalog.Item ya = Catalog.deviceApp("YouTube", "com.google.android.youtube", "x.M"), gm = Catalog.deviceApp("Crossy Road", "com.yodo1.crossyroad", "x.M");
                    java.util.List<Catalog.Category> nc = Catalog.categories(java.util.List.of(gm), java.util.List.of(ya, gm), java.util.List.of(), java.util.List.of());
                    java.util.List<Catalog.Category> moved = Catalog.applyPlacements(nc, java.util.Map.of("com.yodo1.crossyroad", "streams"), java.util.List.of(ya, gm));
                    check(!moved.get(Catalog.indexOf(moved, "mygames")).items.contains(gm) && !moved.get(Catalog.indexOf(moved, "allapps")).items.contains(gm)
                            && moved.get(Catalog.indexOf(moved, "streams")).items.contains(gm) && moved.get(Catalog.indexOf(moved, "allapps")).items.contains(ya), "placement overrides automatic rows");
                    check(Catalog.applyPlacements(nc, java.util.Map.of(), java.util.List.of(ya)) == nc, "no placements = unchanged");
                    java.util.List<Catalog.Category> kz = Catalog.kidsMode(java.util.List.of(), java.util.List.of(ya), java.util.Set.of("com.google.android.youtube"));
                    check(kz.get(Catalog.indexOf(kz, "kadded")).items.contains(ya), "parent-approved video app shows in My New Games");
                    check(Catalog.indexOf(Catalog.kidsMode(java.util.List.of(), java.util.List.of(ya), java.util.Set.of()), "kadded") < 0, "not approved = still filtered");
                    java.util.Set<String> secIds = new java.util.HashSet<>(); for (Catalog.Category k : nc) secIds.add(k.id);
                    for (String sec : Catalog.SECTIONS) check(secIds.contains(sec), "section exists " + sec);
                }
                {   // 0.10.27 Phoenix theme + Kids Themes tile
                    Palette ph = Palette.byId("phoenix");
                    check(ph == Palette.PHOENIX && ph.embers && ph.name.equals("Phoenix") && java.util.Arrays.asList(Palette.ALL).contains(ph) && !Palette.NEON.embers, "phoenix theme");
                    java.util.List<Catalog.Category> kt = Catalog.kidsMode();
                    int ti = Catalog.indexOf(kt, "kthemes");
                    check(ti >= 0 && kt.get(ti).items.size() == 1 && kt.get(ti).items.get(0) == Catalog.KIDS_THEMES && "kthemes".equals(Catalog.KIDS_THEMES.action), "kids themes row: themes only");
                    check(!LockPin.needsPin("kthemes") && !Catalog.kidsBlocked(Catalog.KIDS_THEMES), "kids themes needs no PIN");
                }
                check(Catalog.isViceCity("GTA - Vice City Stories") && Catalog.isViceCity("gta_vice.city") && !Catalog.isViceCity("Liberty City"), "vice city");
            }
            {   // 0.10.7 PSP
                java.io.File pr = java.nio.file.Files.createTempDirectory("psp").toFile();
                new java.io.File(pr, "PSP/GAME/Hero Run").mkdirs(); new java.io.File(pr, "PSP/GAME/Hero Run/EBOOT.PBP").createNewFile();
                new java.io.File(pr, "ROMs/psp").mkdirs(); new java.io.File(pr, "ROMs/psp/Kart.cso").createNewFile(); new java.io.File(pr, "ROMs/psp/Tanks.chd").createNewFile();
                new java.io.File(pr, "Download").mkdirs(); new java.io.File(pr, "Download/Golf.iso").createNewFile(); new java.io.File(pr, "Download/Other.chd").createNewFile();
                new java.io.File(pr, "PSP/SAVEDATA/x").mkdirs(); new java.io.File(pr, "PSP/SAVEDATA/x/S.iso").createNewFile();
                List<java.io.File> pf = PspScan.scan(java.util.Arrays.asList(pr));
                java.util.Set<String> pn = new java.util.TreeSet<>(); for (java.io.File f : pf) pn.add(PspScan.fileName(f));
                check(pn.equals(new java.util.TreeSet<>(java.util.Arrays.asList("Hero Run.pbp", "Kart.cso", "Tanks.chd", "Golf.iso"))), "psp scan " + pn);
                List<Catalog.Item> pr2 = new java.util.ArrayList<>();
                for (java.io.File f : pf) pr2.add(Catalog.rom(PspScan.fileName(f), "file://" + f.getAbsolutePath()));
                pr2.add(Catalog.rom("Crazy.chd", "file:///sdcard/ROMs/dc/Crazy.chd"));
                Catalog.Category ps = Catalog.psp(pr2);
                check(ps.items.size() == 7 && ps.items.get(0) == Catalog.PSP && ps.items.get(1) == Catalog.FILES, "psp tiles " + ps.items.size());
                for (Catalog.Category k : Catalog.kidsMode(pr2)) check(!k.items.contains(Catalog.FILES) && !k.items.contains(Catalog.RESCAN), "no Files in kids mode");
                check(Catalog.dreamcast(pr2).items.size() == 4, "dc keeps its own chd only");
                List<Catalog.Category> k2 = Catalog.kidsMode(pr2);
                check(Catalog.indexOf(k2, "kpsp") < 0 && Catalog.indexOf(k2, "kdc") < 0, "kids hides PSP and Dreamcast ROM tiles");
                List<Catalog.Category> n2 = Catalog.categories(new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>(), pr2, false, false);
                check(Catalog.indexOf(n2, "psp") > Catalog.indexOf(n2, "dreamcast"), "psp after dreamcast");
            }
            List<Catalog.Category> km = Catalog.kidsMode(roms);
            check(Catalog.indexOf(km, "kdc") < 0 && Catalog.indexOf(km, "kpsp") < 0, "kids mode hides dreamcast and PSP ROM tiles");
            for (Catalog.Category k : km) for (Catalog.Item i : k.items) check(!"romfolder".equals(i.action) && !"files".equals(i.action) && !"filemgr".equals(i.action) && !i.isRom(), "no import or ROMs in kids mode");
                    java.io.File storage = java.nio.file.Files.createTempDirectory("storage").toFile();
            new java.io.File(storage, "emulated").mkdirs(); new java.io.File(storage, "self").mkdirs(); new java.io.File(storage, "1A2B-3C4D").mkdirs();
            List<java.io.File> vr = DcScan.volumeRoots(storage, new java.io.File("/nonexistent"));
            check(vr.size() == 1 && vr.get(0).getName().equals("1A2B-3C4D"), "USB / SD volumes");
            java.io.File usb = vr.get(0); new java.io.File(usb, "BIOS/dc").mkdirs();
            try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(new java.io.File(usb, "BIOS/dc/dc_boot.bin"), "rw")) { f.setLength(2097152); }
            try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(new java.io.File(usb, "dc_flash.bin"), "rw")) { f.setLength(131072); }
            try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(new java.io.File(root, "Download/dc_boot.bin"), "rw")) { f.setLength(5); }
            List<java.io.File> bios = DcScan.findBios(java.util.Arrays.asList(root, usb));
            check(bios.size() == 2 && bios.stream().anyMatch(b -> b.getPath().endsWith("BIOS/dc/dc_boot.bin")), "own BIOS found, wrong-size file ignored " + bios);
            String cmd = DcScan.rootCopyCmd("/storage/1A2B-3C4D/BIOS/dc/dc_boot.bin", "/storage/emulated/0/" + DcScan.FLYCAST_DATA);
            check(cmd != null && cmd.contains("cp '/storage/1A2B-3C4D/BIOS/dc/dc_boot.bin' '/storage/emulated/0/Android/data/com.flycast.emulator/files/data/dc_boot.bin'"), "root copy cmd");
            check(DcScan.rootCopyCmd("/x/a'b.bin", "/d") == null && DcScan.rootCopyCmd("/x/a b.bin", "/d") == null, "unsafe paths refused");
        } catch (java.io.IOException e) { throw new AssertionError(e); }

        // 0.10.32 mode tiles, Roblox places, Box Set (adult; hidden in Kids)
        check(c.get(0).items.contains(Catalog.TRILOGY_MODE) && c.get(0).items.contains(Catalog.BOXSET_MODE)
                && c.get(0).items.contains(Catalog.ROBLOX_MODE), "mode tiles on Featured (after existing tiles)");
        check(Catalog.indexOf(c, "trilogies") > 0 && Catalog.indexOf(c, "coop") > 0 && Catalog.indexOf(c, "roblox") > 0, "Trilogies, Co-op, Roblox tabs");
        check(Catalog.TRILOGY_ITEMS.length >= 40 && Catalog.COOP_ITEMS.length >= 20 && Catalog.ROBLOX_ITEMS.length == 12, "verified lists");
        boolean piggy = false, strucid = false;
        for (Catalog.Item i : Catalog.ROBLOX_ITEMS) {
            if ("4623386862".equals(Catalog.robloxPlaceId(i))) piggy = true;
            if ("4623386861".equals(Catalog.robloxPlaceId(i))) strucid = true;
            check(i.webUrl.startsWith("https://www.roblox.com/games/"), "official Roblox page");
        }
        check(piggy && !strucid, "Piggy place id, not Strucid");
        check(Catalog.boxSetItems().length == Catalog.TRILOGY_ITEMS.length + Catalog.COOP_ITEMS.length, "Box Set is Trilogy + Co-op");
        check(Catalog.kidsBlocked(Catalog.BOXSET_MODE) && Catalog.kidsBlocked(Catalog.TRILOGY_MODE) && Catalog.kidsBlocked(Catalog.COOP_MODE), "Box Set / Trilogy / Co-op hidden in Kids");
        for (Catalog.Item i : Catalog.TRILOGY_ITEMS) check(Catalog.kidsBlocked(i), "Steam box-set hidden in Kids");
        List<Catalog.Category> k32 = Catalog.kidsMode();
        check(Catalog.indexOf(k32, "kroblox") >= 0 && k32.get(Catalog.indexOf(k32, "kroblox")).items.get(0) == Catalog.ROBLOX, "Kids has Roblox tab");
        check(!k32.get(0).items.contains(Catalog.BOXSET_MODE) && Catalog.indexOf(k32, "trilogies") < 0 && Catalog.indexOf(k32, "coop") < 0, "Kids has no Box Set / Trilogies / Co-op");
        check(Catalog.steamAppId(Catalog.TRILOGY_ITEMS[0]).equals("1659420"), "Uncharted Steam id");
        String thumb = StoreArt.parseThumb("{\"data\":[{\"imageUrl\":\"https://tr.rbxcdn.com/abc.png\"}]}");
        check("https://tr.rbxcdn.com/abc.png".equals(thumb), "Roblox thumb parse");
        check(StoreArt.parseThumb("{\"data\":[{\"imageUrl\":\"https://evil.example/x.png\"}]}") == null, "reject unknown thumb host");
        robloxDiscoverChecks();
    }

    /** 0.10.34: public explore JSON, official fallback, cache, merge. No network. */
    static void robloxDiscoverChecks() {
        check(RobloxDiscover.EXPLORE_SORTS.startsWith("https://apis.roblox.com/explore-api/"), "explore host");
        String json = "{"
                + "\"nextSortsPageToken\":\"tok-2\","
                + "\"sorts\":["
                + "{\"contentType\":\"Filters\",\"games\":[]},"
                + "{\"contentType\":\"Games\",\"sortId\":\"top-trending\",\"games\":["
                + "{\"rootPlaceId\":4924922222,\"name\":\"Brookhaven\",\"isSponsored\":false},"
                + "{\"rootPlaceId\":1,\"name\":\"Ad Game\",\"isSponsored\":true},"
                + "{\"rootPlaceId\":920587237,\"name\":\"Adopt Me!\"}"
                + "]},"
                + "{\"contentType\":\"Games\",\"sortId\":\"top-paid-access\",\"games\":["
                + "{\"rootPlaceId\":99,\"name\":\"Paid Place\"}"
                + "]},"
                + "{\"contentType\":\"Songs\",\"games\":[{\"rootPlaceId\":77,\"name\":\"A Song\"}]}"
                + "]}";
        RobloxDiscover.Page page = RobloxDiscover.parsePage(json);
        check("tok-2".equals(page.nextToken) && page.items.size() == 2, "parse size " + page.items.size());
        check("4924922222".equals(Catalog.robloxPlaceId(page.items.get(0))) && "Brookhaven".equals(page.items.get(0).label), "first game");
        check("920587237".equals(Catalog.robloxPlaceId(page.items.get(1))), "second game, sponsored skipped");
        check(page.items.get(0).webUrl.startsWith("https://www.roblox.com/games/4924922222/")
                && "roblox:4924922222".equals(page.items.get(0).action), "official deep-link action");
        check(RobloxDiscover.parsePage("not-json").items.isEmpty() && RobloxDiscover.parsePage(null).items.isEmpty(), "bad json");
        check(RobloxDiscover.fallback().size() == Catalog.ROBLOX_ITEMS.length
                && "4924922222".equals(Catalog.robloxPlaceId(RobloxDiscover.fallback().get(0))), "official fallback");
        String url = RobloxDiscover.exploreUrl("sess 1", "tok/2");
        check(url.startsWith(RobloxDiscover.EXPLORE_SORTS + "?sessionId=") && url.contains("sortsPageToken=") && !url.contains(" "), "url encodes");
        List<Catalog.Item> merged = RobloxDiscover.merge(page.items, java.util.List.of(
                Catalog.robloxPlace("Adopt Me!", "920587237"),
                Catalog.robloxPlace("Doors", "6516141723")));
        check(merged.size() == 3 && "6516141723".equals(Catalog.robloxPlaceId(merged.get(2))), "merge dedupes");
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "codeaque-rbx-test");
        dir.mkdirs();
        java.io.File cache = new java.io.File(dir, "roblox-discover.json");
        check(RobloxDiscover.saveCache(cache, page.items), "cache save");
        List<Catalog.Item> loaded = RobloxDiscover.loadCache(cache);
        check(loaded.size() == 2 && "4924922222".equals(Catalog.robloxPlaceId(loaded.get(0))) && "Adopt Me!".equals(loaded.get(1).label), "cache load");
        check(RobloxDiscover.loadCache(new java.io.File(dir, "missing.json")).isEmpty(), "missing cache");
        cache.delete();
        List<Catalog.Category> cats = Catalog.categories();
        List<Catalog.Category> swapped = Catalog.replaceItems(cats, "roblox", page.items);
        check(swapped.get(Catalog.indexOf(swapped, "roblox")).items.size() == 2
                && swapped.get(Catalog.indexOf(cats, "steam")).items.size() == cats.get(Catalog.indexOf(cats, "steam")).items.size(), "replace only roblox row");
        List<Catalog.Category> kids = Catalog.kidsMode();
        check(kids.get(Catalog.indexOf(kids, "kroblox")).items.size() == Catalog.ROBLOX_ITEMS.length + 1
                && kids.get(Catalog.indexOf(kids, "kroblox")).items.get(0) == Catalog.ROBLOX, "kids catalog starts with Roblox app plus official list");
        robloxFilterChecks();
        screenTimeChecks();
        pinActionChecks();
        kidsPlayAndBootAnimChecks();
    }

    /** 0.10.37: Kids / Kids 12+ maturity filters. No network. */
    static void robloxFilterChecks() {
        check(RobloxDiscover.band("minimal", "Maturity: Minimal", 0) == RobloxDiscover.Band.MINIMAL, "minimal");
        check(RobloxDiscover.band("mild", "Maturity: Mild", 0) == RobloxDiscover.Band.MILD, "mild");
        check(RobloxDiscover.band("moderate", "Maturity: Moderate", 0) == RobloxDiscover.Band.TEEN, "moderate is 12+/13+");
        check(RobloxDiscover.band("vng-rating-override", "Age Rating: 12+", 12) == RobloxDiscover.Band.TEEN, "12+ override");
        check(RobloxDiscover.band("restricted", "Maturity: Restricted", 17) == RobloxDiscover.Band.ADULT, "17+");
        check(RobloxDiscover.band("", "", -1) == RobloxDiscover.Band.UNKNOWN, "unrated");
        String json = "{"
                + "\"sorts\":[{\"contentType\":\"Games\",\"sortId\":\"top-trending\",\"games\":["
                + "{\"rootPlaceId\":10,\"universeId\":100,\"name\":\"Min Game\",\"contentMaturity\":\"minimal\",\"ageRecommendationDisplayName\":\"Maturity: Minimal\",\"minimumAge\":0,\"isSponsored\":false},"
                + "{\"rootPlaceId\":11,\"universeId\":101,\"name\":\"Mild Game\",\"contentMaturity\":\"mild\",\"ageRecommendationDisplayName\":\"Maturity: Mild\",\"minimumAge\":0},"
                + "{\"rootPlaceId\":12,\"universeId\":102,\"name\":\"Mod Game\",\"contentMaturity\":\"moderate\",\"ageRecommendationDisplayName\":\"Maturity: Moderate\",\"minimumAge\":0},"
                + "{\"rootPlaceId\":13,\"universeId\":103,\"name\":\"Teen Override\",\"contentMaturity\":\"vng-rating-override\",\"ageRecommendationDisplayName\":\"Age Rating: 12+\",\"minimumAge\":12},"
                + "{\"rootPlaceId\":17,\"universeId\":107,\"name\":\"Adult Game\",\"contentMaturity\":\"restricted\",\"ageRecommendationDisplayName\":\"Maturity: Restricted\",\"minimumAge\":17},"
                + "{\"rootPlaceId\":18,\"universeId\":108,\"name\":\"Unknown Game\"},"
                + "{\"rootPlaceId\":19,\"universeId\":109,\"name\":\"Ad Game\",\"contentMaturity\":\"minimal\",\"isSponsored\":true}"
                + "]},"
                + "{\"contentType\":\"Games\",\"sortId\":\"top-paid-access\",\"games\":["
                + "{\"rootPlaceId\":99,\"universeId\":199,\"name\":\"Paid\",\"contentMaturity\":\"minimal\"}"
                + "]}]}";
        RobloxDiscover.Page page = RobloxDiscover.parsePage(json);
        check(page.games.size() == 6, "parse keeps unsponsored unpaid, including unrated " + page.games.size());
        java.util.List<Catalog.Item> kidsOnly = RobloxDiscover.filter(page.games, RobloxDiscover.Profile.KIDS);
        check(kidsOnly.size() == 1 && "10".equals(Catalog.robloxPlaceId(kidsOnly.get(0))), "kids: minimal only");
        java.util.List<Catalog.Item> k12 = RobloxDiscover.filter(page.games, RobloxDiscover.Profile.KIDS_12);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Catalog.Item i : k12) ids.add(Catalog.robloxPlaceId(i));
        check(ids.contains("10") && ids.contains("11") && ids.contains("12") && ids.contains("13")
                && !ids.contains("17") && !ids.contains("18") && !ids.contains("19") && !ids.contains("99") && k12.size() == 4,
                "kids12: minimal, mild, 12+/13+; no 17+, unrated, sponsored or paid");
        check(RobloxDiscover.filter(page.games, RobloxDiscover.Profile.ALL).size() == 6, "adult list is unfiltered aside from sponsored/paid");
        check(RobloxDiscover.hasUsableRatings(page.games) && !RobloxDiscover.hasUsableRatings(java.util.List.of(
                new RobloxDiscover.Game("X", "1", "2", "", "", -1))), "ratings present / missing");
        String guide = "{\"ageRecommendationDetails\":{\"summary\":{\"ageRecommendation\":{"
                + "\"displayName\":\"Minimal\",\"minimumAge\":0,\"displayNameWithHeaderShort\":\"Maturity: Minimal\",\"contentMaturity\":\"minimal\"}}}}";
        RobloxDiscover.Game raw = new RobloxDiscover.Game("Looked Up", "55", "555", "", "", -1);
        RobloxDiscover.Game rated = RobloxDiscover.applyGuidelines(raw, guide);
        check(RobloxDiscover.band(raw) == RobloxDiscover.Band.UNKNOWN && RobloxDiscover.band(rated) == RobloxDiscover.Band.MINIMAL
                && RobloxDiscover.allowed(RobloxDiscover.Profile.KIDS, rated), "guidelines fill");
        check(RobloxDiscover.Profile.kids("kids12") == RobloxDiscover.Profile.KIDS_12
                && RobloxDiscover.Profile.kids("kids") == RobloxDiscover.Profile.KIDS, "profile parse");
        // rated cache
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "codeaque-rbx-filter");
        dir.mkdirs();
        java.io.File cache = new java.io.File(dir, "roblox-discover.json");
        check(RobloxDiscover.saveGames(cache, page.games), "rated cache save");
        java.util.List<RobloxDiscover.Game> loaded = RobloxDiscover.loadGames(cache);
        check(RobloxDiscover.filter(loaded, RobloxDiscover.Profile.KIDS).size() == 1
                && RobloxDiscover.filter(loaded, RobloxDiscover.Profile.KIDS_12).size() == 4, "rated cache filter");
        cache.delete();
    }

    /** 0.10.37: daily play limit, midnight reset, play start/pause. No Android. */
    static void screenTimeChecks() {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Europe/London");
        java.util.Calendar c = java.util.Calendar.getInstance(tz);
        c.set(2026, java.util.Calendar.NOVEMBER, 16, 10, 0, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        long t0 = c.getTimeInMillis();
        ScreenTime.State off = new ScreenTime.State(0, 0, 0, false, false, "", 0);
        check(ScreenTime.remainingMs(off, t0, tz) == -1 && ScreenTime.label(off, t0, tz).isEmpty() && !ScreenTime.timeUp(off, t0, tz), "off");
        ScreenTime.State s = ScreenTime.setLimit(off, 30, t0, tz);
        check(s.limitMin == 30 && s.usedMs == 0 && "2026-11-16".equals(s.day), "set limit");
        check(ScreenTime.remainingMs(s, t0, tz) == 30 * 60_000L && "30 min left".equals(ScreenTime.label(s, t0, tz)), "full 30");
        s = ScreenTime.startPlay(s, t0, tz);
        check(s.startedAt == t0, "start");
        long t20 = t0 + 20 * 60_000L;
        check("10 min left".equals(ScreenTime.label(s, t20, tz)), "20 min used, 10 left");
        s = ScreenTime.pausePlay(s, t20, tz);
        check(s.startedAt == 0 && s.usedMs == 20 * 60_000L && "10 min left".equals(ScreenTime.label(s, t20, tz)), "paused");
        long t30 = t0 + 30 * 60_000L;
        check(!ScreenTime.timeUp(s, t30, tz) && "10 min left".equals(ScreenTime.label(s, t30, tz)), "paused clock does not keep running");
        s = ScreenTime.startPlay(s, t20, tz);
        check(ScreenTime.timeUp(s, t30, tz) && "Time's up".equals(ScreenTime.label(s, t30, tz)), "used up after play resumes");
        // running session that crosses the limit
        s = ScreenTime.startPlay(ScreenTime.setLimit(off, 15, t0, tz), t0, tz);
        check(ScreenTime.timeUp(s, t0 + 15 * 60_000L, tz), "hits zero while running");
        check(ScreenTime.warn5(s, t0 + 11 * 60_000L, tz) && !ScreenTime.warn5(s, t0 + 9 * 60_000L, tz), "5 min warning window");
        s = ScreenTime.addMinutes(ScreenTime.pausePlay(s, t0 + 15 * 60_000L, tz), 15, t0 + 15 * 60_000L, tz);
        check(!ScreenTime.timeUp(s, t0 + 15 * 60_000L, tz) && "15 min left".equals(ScreenTime.label(s, t0 + 15 * 60_000L, tz)), "parent added 15");
        s = ScreenTime.unlockToday(s, t0 + 16 * 60_000L, tz);
        check(ScreenTime.remainingMs(s, t0 + 16 * 60_000L, tz) == -1 && !ScreenTime.timeUp(s, t0 + 16 * 60_000L, tz), "unlocked today");
        // midnight reset
        long next = ScreenTime.nextMidnight(t0, tz);
        ScreenTime.State day2 = ScreenTime.normalize(s, next, tz);
        check("2026-11-17".equals(day2.day) && day2.usedMs == 0 && !day2.unlocked && day2.limitMin == 15, "reset at local midnight");
        s = ScreenTime.setStrict(day2, true, next, tz);
        check(s.strict, "strict");
        check(ScreenTime.isPlayLaunch(Catalog.ROBLOX) && ScreenTime.isPlayLaunch(Catalog.MINECRAFT)
                && ScreenTime.isPlayLaunch(Catalog.robloxPlace("Doors", "6516141723"))
                && !ScreenTime.isPlayLaunch(Catalog.PLAY_STORE) && !ScreenTime.isPlayLaunch(Catalog.KIDS_WIFI)
                && !ScreenTime.isPlayLaunch(Catalog.KIDS_UPDATE) && !ScreenTime.isPlayLaunch(Catalog.ADULT_PIN), "play vs parent tiles");
        check(ScreenTime.blockedKidPackage("com.android.settings") && ScreenTime.blockedKidPackage("com.android.vending")
                && ScreenTime.blockedKidPackage("com.google.android.packageinstaller") && ScreenTime.blockedKidPackage("com.android.documentsui")
                && !ScreenTime.blockedKidPackage("com.roblox.client") && !ScreenTime.blockedKidPackage(null), "blocked packages");
        check(ScreenTime.parentStorePackage("com.android.vending") && ScreenTime.parentStorePackage("com.google.android.packageinstaller")
                && ScreenTime.parentStorePackage("com.android.packageinstaller") && ScreenTime.parentStorePackage("com.google.android.finsky")
                && !ScreenTime.parentStorePackage("com.android.settings") && !ScreenTime.parentStorePackage("com.android.documentsui")
                && !ScreenTime.parentStorePackage("com.roblox.client") && !ScreenTime.parentStorePackage(null), "parent-store packages are Play only");
        long until = 1_000_000L;
        check(ScreenTime.parentStoreAllowed("com.android.vending", until, until - 1)
                && !ScreenTime.parentStoreAllowed("com.android.vending", until, until)
                && !ScreenTime.parentStoreAllowed("com.android.settings", until, until - 1)
                && !ScreenTime.parentStoreAllowed("com.android.vending", 0, 1), "parent-store window is time-limited and Play-only");
        check(ScreenTime.PARENT_STORE_MS == 10 * 60_000L, "parent-store window is 10 minutes");
        check(ScreenTime.parentUpdatePackage("com.android.packageinstaller") && ScreenTime.parentUpdatePackage("com.google.android.packageinstaller")
                && !ScreenTime.parentUpdatePackage("com.android.vending") && !ScreenTime.parentUpdatePackage("com.android.settings")
                && !ScreenTime.parentUpdatePackage("com.roblox.client") && !ScreenTime.parentUpdatePackage(null), "parent-update packages are the installer only");
        long uUntil = 2_000_000L;
        check(ScreenTime.parentUpdateAllowed("com.android.packageinstaller", uUntil, uUntil - 1)
                && !ScreenTime.parentUpdateAllowed("com.android.packageinstaller", uUntil, uUntil)
                && !ScreenTime.parentUpdateAllowed("com.android.vending", uUntil, uUntil - 1)
                && !ScreenTime.parentUpdateAllowed("com.android.packageinstaller", 0, 1), "parent-update window is time-limited and installer-only");
        check(ScreenTime.PARENT_UPDATE_MS == 10 * 60_000L, "parent-update window is 10 minutes");
        check("com.google.android.inputmethod.latin".equals(ScreenTime.imePackage("com.google.android.inputmethod.latin/.LatinIME"))
                && "com.sec.android.inputmethod".equals(ScreenTime.imePackage("com.sec.android.inputmethod/.SamsungKeypad")), "ime id with slash");
        check("com.example.ime".equals(ScreenTime.imePackage("com.example.ime"))
                && ScreenTime.imePackage("") == null && ScreenTime.imePackage(null) == null, "ime id without slash / empty");
        check(ScreenTime.isImePackage("com.google.android.inputmethod.latin", "com.google.android.inputmethod.latin")
                && !ScreenTime.isImePackage("com.android.vending", "com.google.android.inputmethod.latin")
                && !ScreenTime.isImePackage(null, "com.google.android.inputmethod.latin")
                && !ScreenTime.isImePackage("com.android.vending", null), "ime package match");
        ScreenTime.Hours win = new ScreenTime.Hours(true, 16 * 60, 19 * 60, false, 10 * 60, 18 * 60, 0);
        java.util.Calendar hc = java.util.Calendar.getInstance(tz);
        hc.set(2026, java.util.Calendar.NOVEMBER, 16, 15, 0, 0); hc.set(java.util.Calendar.MILLISECOND, 0);
        long mon15 = hc.getTimeInMillis();
        hc.set(2026, java.util.Calendar.NOVEMBER, 16, 17, 0, 0);
        long mon17 = hc.getTimeInMillis();
        hc.set(2026, java.util.Calendar.NOVEMBER, 16, 19, 0, 0);
        long mon19 = hc.getTimeInMillis();
        ScreenTime.State free = ScreenTime.normalize(off, mon15, tz);
        check(ScreenTime.outsideHours(win, free, mon15, tz) && !ScreenTime.outsideHours(win, free, mon17, tz)
                && ScreenTime.outsideHours(win, free, mon19, tz), "weekday 16:00–19:00 window");
        check(!ScreenTime.playBlocked(free, win, mon17, tz) && ScreenTime.playBlocked(free, win, mon15, tz), "hours block play");
        check("4pm".equals(ScreenTime.formatClock(16 * 60)) && "4:30pm".equals(ScreenTime.formatClock(16 * 60 + 30))
                && "12am".equals(ScreenTime.formatClock(0)) && "12pm".equals(ScreenTime.formatClock(12 * 60))
                && "7pm".equals(ScreenTime.formatClock(19 * 60)), "12-hour clock labels");
        check("16:00".equals(ScreenTime.format24(16 * 60)) && ScreenTime.hoursSummary(win).contains("16:00–19:00"), "24-hour settings labels");
        check("4pm".equals(ScreenTime.backAtLabel(win, mon15, tz)), "back at 4pm later today");
        check("4pm tomorrow".equals(ScreenTime.backAtLabel(win, mon19, tz)), "back at 4pm tomorrow");
        check(!ScreenTime.outsideHours(win, ScreenTime.unlockToday(free, mon15, tz), mon15, tz), "unlock today bypasses hours");
        ScreenTime.Hours over = ScreenTime.overrideHours(win, mon15 + 15 * 60_000L);
        check(!ScreenTime.outsideHours(over, free, mon15 + 5 * 60_000L, tz)
                && ScreenTime.outsideHours(over, free, mon15 + 16 * 60_000L, tz), "PIN override lasts the added minutes");
        ScreenTime.Hours we = new ScreenTime.Hours(true, 16 * 60, 19 * 60, true, 10 * 60, 18 * 60, 0);
        hc.set(2026, java.util.Calendar.NOVEMBER, 21, 9, 0, 0);
        long sat9 = hc.getTimeInMillis();
        hc.set(2026, java.util.Calendar.NOVEMBER, 21, 11, 0, 0);
        long sat11 = hc.getTimeInMillis();
        ScreenTime.State sat = ScreenTime.normalize(off, sat9, tz);
        check(ScreenTime.isWeekend(sat9, tz) && !ScreenTime.isWeekend(mon15, tz)
                && ScreenTime.outsideHours(we, sat, sat9, tz) && !ScreenTime.outsideHours(we, sat, sat11, tz)
                && "10am".equals(ScreenTime.backAtLabel(we, sat9, tz)), "separate weekend window");
        ScreenTime.Hours offH = new ScreenTime.Hours(false, 16 * 60, 19 * 60, false, 10 * 60, 18 * 60, 0);
        check(!ScreenTime.outsideHours(offH, free, mon15, tz) && ScreenTime.inHours(offH, mon15, tz), "hours off never blocks");
        ScreenTime.Hours wrap = new ScreenTime.Hours(true, 20 * 60, 7 * 60, false, 20 * 60, 7 * 60, 0);
        hc.set(2026, java.util.Calendar.NOVEMBER, 16, 21, 0, 0);
        long mon21 = hc.getTimeInMillis();
        hc.set(2026, java.util.Calendar.NOVEMBER, 16, 8, 0, 0);
        long mon8 = hc.getTimeInMillis();
        check(ScreenTime.inHours(wrap, mon21, tz) && !ScreenTime.inHours(wrap, mon8, tz), "window wrapping midnight");
        // 0.10.49 Kids search: Play HTML parse, age filter, installed-first merge, catalogue fallback
        String playHtml = "<a href=\"/store/apps/details?id=com.mojang.minecraftpe\" aria-label=\"Minecraft: Dream it, Build it!\"></a>"
                + "<span itemprop=\"contentRating\"><span>PEGI 7</span></span>"
                + "<a class=\"Si6A0c Gy4nib\" href=\"/store/apps/details?id=com.mojang.minecraftedu\">"
                + "<span class=\"DdYX5\">Minecraft Education</span></a>"
                + "<a class=\"Si6A0c Gy4nib\" href=\"/store/apps/details?id=com.teen.shooter\">"
                + "<span class=\"DdYX5\">Teen Shooter</span></a>"
                + "<a class=\"Si6A0c Gy4nib\" href=\"/store/apps/details?id=com.mature.game\">"
                + "<span class=\"DdYX5\">Mature Game</span></a>";
        java.util.List<PlaySearch.Hit> parsed = PlaySearch.parseResults(playHtml);
        check(parsed.size() >= 3 && "com.mojang.minecraftpe".equals(parsed.get(0).pkg), "search parse featured package first");
        check(parsed.get(0).title.toLowerCase(java.util.Locale.ROOT).contains("minecraft"), "search parse featured title");
        boolean hasEdu = false;
        for (PlaySearch.Hit h : parsed) if ("com.mojang.minecraftedu".equals(h.pkg) && h.title.toLowerCase(java.util.Locale.ROOT).contains("education")) hasEdu = true;
        check(hasEdu, "search parse list-card title");
        check(PlaySearch.parseContentRating("<span itemprop=\"contentRating\"><span>PEGI 7</span></span>") == 7, "listing PEGI 7");
        check(PlaySearch.parseContentRating("PEGI 3") == 3 && PlaySearch.parseContentRating("PEGI 12") == 12
                && PlaySearch.parseContentRating("PEGI 16") == 16 && PlaySearch.parseContentRating("Mature 17+") == 18
                && PlaySearch.parseContentRating("Everyone") == 3 && PlaySearch.parseContentRating("Everyone 10+") == 12
                && PlaySearch.parseContentRating("Teen") == 16 && PlaySearch.parseContentRating("") == 0, "rating map");
        java.util.List<PlaySearch.Hit> rawHits = new java.util.ArrayList<>();
        rawHits.add(new PlaySearch.Hit("com.mojang.minecraftpe", "Minecraft", 7));
        rawHits.add(new PlaySearch.Hit("com.teen.shooter", "Teen Shooter", 16));
        rawHits.add(new PlaySearch.Hit("com.mature.game", "Mature Game", 18));
        rawHits.add(new PlaySearch.Hit("com.unknown.app", "Unknown", 0));
        java.util.List<PlaySearch.Hit> kidsHits = PlaySearch.filterAllowed(rawHits, KidsPlay.maxPegi(false));
        java.util.List<PlaySearch.Hit> k12Hits = PlaySearch.filterAllowed(rawHits, KidsPlay.maxPegi(true));
        check(kidsHits.size() == 1 && "com.mojang.minecraftpe".equals(kidsHits.get(0).pkg), "Kids search drops 12+/16+/17+ and unrated");
        check(k12Hits.size() == 1, "Kids 12+ still drops 16/17+ and unrated");
        java.util.List<KidsPlay.Game> mc = KidsPlay.search("minecraft", KidsPlay.maxPegi(false));
        check(!mc.isEmpty() && mc.get(0).title.toLowerCase(java.util.Locale.ROOT).contains("minecraft"), "catalogue search finds minecraft");
        java.util.List<KidsPlay.Game> lego = KidsPlay.search("lego", KidsPlay.maxPegi(false));
        check(!lego.isEmpty(), "catalogue search finds lego");
        for (KidsPlay.Game g : lego) check(KidsPlay.allowed(g.pegi, 7), "lego results are Kids-safe");
        java.util.List<KidsPlay.Game> racing = KidsPlay.search("racing", KidsPlay.maxPegi(false));
        check(!racing.isEmpty(), "catalogue search finds racing");
        for (KidsPlay.Game g : KidsPlay.search("x", 7)) check(g.pegi != 16 && g.pegi != 18, "catalogue search never returns 16/18 for Kids");
        Catalog.Item instMc = Catalog.app("Minecraft", "cube", "com.mojang.minecraftpe");
        Catalog.Item instOther = Catalog.app("On device", "cube", "com.device.only");
        java.util.List<Catalog.Item> installed = java.util.Arrays.asList(instOther);
        java.util.List<PlaySearch.Hit> playPart = java.util.Arrays.asList(new PlaySearch.Hit("com.mojang.minecraftpe", "Minecraft", 7));
        java.util.List<Catalog.Item> merged = PlaySearch.merge(installed, playPart, KidsPlay.searchItems("minecraft", 7));
        check(!merged.isEmpty() && "com.device.only".equals(merged.get(0).storePackage()), "installed games are listed first");
        check(PlaySearch.searchUrl(PlayArt.PLAY, "lego world").contains("/store/search?q=lego%20world")
                && PlaySearch.searchUrl(PlayArt.PLAY, "lego world").contains("c=apps"), "search url is Play search, not opened to kids");
        check(LockPin.needsPin("kidsprofile") && LockPin.needsPin("screentime") && LockPin.needsPin("alwayskids") && LockPin.needsPin("kidslock") && LockPin.needsPin("lockphone") && LockPin.needsPin("removeowner") && LockPin.needsPin("exitcodeaque") && LockPin.needsPin("phonesettings") && LockPin.needsPin("phoneaccounts") && LockPin.needsPin("homeapp") && !LockPin.needsPin("update"), "PIN for profile, screen time, always-kids, kids lock, device owner, Exit and Phone Settings when locked; Update is not lock-gated");
        java.util.List<String> settingsActs = new java.util.ArrayList<>();
        java.util.List<Catalog.Category> cats39 = Catalog.categories();
        for (Catalog.Item i : cats39.get(Catalog.indexOf(cats39, "settings")).items) settingsActs.add(i.action);
        check(settingsActs.contains("exitcodeaque") && settingsActs.contains("phonesettings") && settingsActs.contains("phoneaccounts") && settingsActs.contains("homeapp") && "exitcodeaque".equals(cats39.get(Catalog.indexOf(cats39, "power")).items.get(0).action), "Phone Settings / Accounts / Home app and Exit in Settings");
        for (Catalog.Category k : Catalog.kidsMode()) {
            check(!"power".equals(k.id), "no Power row in Kids mode");
            for (Catalog.Item i : k.items) check(!"exitcodeaque".equals(i.action) && !"phonesettings".equals(i.action) && !"phoneaccounts".equals(i.action) && !"homeapp".equals(i.action), "no Exit / Phone Settings in Kids mode: " + k.id);
        }
        String accountsStack = "java.lang.IllegalStateException: Not allowed to set the device owner because there are already some accounts on the device\n\tat com.android.server.devicepolicy.DevicePolicyManagerServiceShellCommand.runSetDeviceOwner(DevicePolicyManagerServiceShellCommand.java:257)\n\tat com.android.server.devicepolicy.DevicePolicyManagerServiceShellCommand.onCommand(DevicePolicyManagerServiceShellCommand.java:1)\n";
        check(DpmError.firstLine(accountsStack).equals("Not allowed to set the device owner because there are already some accounts on the device"), "dpm first line is the reason, not the stack");
        check(DpmError.plainReason(accountsStack).contains("account") && !DpmError.plainReason(accountsStack).contains("at com.android") && !DpmError.firstLine(accountsStack).contains("runSetDeviceOwner"), "dpm accounts in plain English, stack trimmed");
        String provisioned = "java.lang.IllegalStateException: Not allowed to set the device owner because the device has already been provisioned\n\tat com.android.server.devicepolicy.DevicePolicyManagerServiceShellCommand.runSetDeviceOwner(DevicePolicyManagerServiceShellCommand.java:257)\n";
        String pr = DpmError.plainReason(provisioned).toLowerCase();
        check((pr.contains("provision") || pr.contains("already")) && !pr.contains("at com.android"), "dpm already provisioned in plain English");
        String users = "java.lang.IllegalStateException: Not allowed to set the device owner because there are multiple users on the device\n\tat com.android.server.devicepolicy.DevicePolicyManagerServiceShellCommand.runSetDeviceOwner(DevicePolicyManagerServiceShellCommand.java:257)\n";
        check(DpmError.plainReason(users).toLowerCase().contains("more than one user") || DpmError.plainReason(users).toLowerCase().contains("multiple users"), "dpm multiple users");
        String execMiss = "su: failed to exec /system/bin/dpm set-device-owner com.codeaque.launcher/.AdminReceiver: No such file or directory\n";
        check(SuRun.isExecMiss(execMiss) && SuRun.isExecMissLine(execMiss.trim()) && !SuRun.isDpmFailure(execMiss), "failed to exec is an exec miss, not a dpm refusal");
        check(SuRun.isExecMiss("sh: dpm: inaccessible or not found") && SuRun.isExecMiss("/system/bin/dpm: not found"), "not found is exec miss");
        check(SuRun.shouldRetry(execMiss, 127) && !SuRun.isSuccess(execMiss, 0), "exec miss is never final / never success, even if waitFor is 0");
        check(SuRun.effectiveRc(execMiss, 0) != 0, "exec miss with waitFor 0 is not treated as ok");
        String mixed = execMiss + accountsStack;
        check(SuRun.isDpmFailure(mixed) && !SuRun.isExecMiss(mixed), "mixed output: dpm exception wins over failed to exec");
        check(DpmError.plainReason(mixed).contains("account") && !DpmError.plainReason(mixed).contains("could not be started"), "plain reason skips exec miss and uses the exception message");
        check(DpmError.firstLine(mixed, true).startsWith("Not allowed to set the device owner because there are already some accounts"), "first dpm line after skipping exec miss");
        check(java.util.Arrays.equals(SuRun.FORM_IDS, new String[]{"stdin", "su -c sh -c", "su 0 /system/bin/sh -c", "su root /system/bin/sh -c", "su -c /system/bin/sh -c", "su -c split-args"}), "su form order, stdin first");
        check(SuRun.usesStdin("stdin") && !SuRun.usesStdin("su -c sh -c"), "only stdin uses su's input");
        String script = SuRun.stdinScript("/system/bin/dpm set-device-owner com.codeaque.launcher/.AdminReceiver");
        check(script.startsWith("/system/bin/dpm set-device-owner com.codeaque.launcher/.AdminReceiver 2>&1; echo __RC=$?") && script.contains("\nexit\n") && !script.contains("sh -c"), "stdin script is plain lines, no sh -c wrapper");
        check(SuRun.parseRcMarker("hello\n__RC=13\n") == 13 && SuRun.parseRcMarker("no marker") == null, "__RC marker");
        check(SuRun.effectiveRc("ok\n__RC=0\n", 1) == 0 && SuRun.effectiveRc("err\n__RC=7\n", 0) == 7, "__RC wins over waitFor");
        String dpmCmd = "/system/bin/dpm set-device-owner com.codeaque.launcher/.AdminReceiver";
        check(java.util.Arrays.equals(SuRun.argv("stdin", "/system/xbin/su", dpmCmd), new String[]{"/system/xbin/su"}), "stdin argv is su with no args");
        String[] shc = SuRun.argv("su -c sh -c", "/system/xbin/su", dpmCmd);
        check(shc.length == 3 && shc[1].equals("-c") && shc[2].startsWith("sh -c ") && shc[2].contains(dpmCmd), "su -c sh -c form");
        check(java.util.Arrays.equals(SuRun.argv("su 0 /system/bin/sh -c", "/system/xbin/su", dpmCmd), new String[]{"/system/xbin/su", "0", "/system/bin/sh", "-c", dpmCmd}), "su 0 /system/bin/sh -c");
        check(java.util.Arrays.equals(SuRun.argv("su root /system/bin/sh -c", "/system/xbin/su", dpmCmd), new String[]{"/system/xbin/su", "root", "/system/bin/sh", "-c", dpmCmd}), "su root /system/bin/sh -c");
        check(java.util.Arrays.equals(SuRun.argv("su -c /system/bin/sh -c", "/system/xbin/su", dpmCmd), new String[]{"/system/xbin/su", "-c", "/system/bin/sh", "-c", dpmCmd}), "su -c /system/bin/sh -c");
        check(java.util.Arrays.equals(SuRun.argv("su -c split-args", "/system/xbin/su", dpmCmd), new String[]{"/system/xbin/su", "-c", "/system/bin/dpm", "set-device-owner", "com.codeaque.launcher/.AdminReceiver"}), "split-args form");
        SuRun.Batch allMiss = new SuRun.Batch();
        allMiss.add(new SuRun.Attempt("/system/xbin/su", "stdin", execMiss, 127));
        allMiss.add(new SuRun.Attempt("/system/xbin/su", "su -c sh -c", execMiss, 127));
        check(!allMiss.success() && !allMiss.dpmFailed() && SuRun.dialogMessage(allMiss).contains("stdin") && SuRun.dialogMessage(allMiss).contains("failed to exec") && SuRun.dialogMessage(allMiss).contains("any su form"), "all-forms dialog lists each short result");
        SuRun.Batch dpmNo = new SuRun.Batch();
        dpmNo.add(new SuRun.Attempt("/system/xbin/su", "stdin", execMiss, 127));
        dpmNo.add(new SuRun.Attempt("/system/xbin/su", "su 0 /system/bin/sh -c", accountsStack, 1));
        check(dpmNo.dpmFailed() && !dpmNo.success() && SuRun.dialogMessage(dpmNo).contains("account") && !SuRun.dialogMessage(dpmNo).contains("any su form"), "when dpm runs and fails, dialog is the exception in plain English");

    }

    /** 0.10.37: empty / wrong / invalid PINs never grant access on the shared verifier used by every PIN dialog. */
    static void pinActionChecks() {
        String h = LockPin.newHash("4821");
        check(PinAction.verify("4821", h) == PinAction.ACCEPTED, "correct pin");
        check(PinAction.verify("0000", h) == PinAction.WRONG && !PinAction.granted(PinAction.verify("0000", h)), "wrong pin");
        check(PinAction.verify("", h) == PinAction.EMPTY && PinAction.verify(null, h) == PinAction.EMPTY, "empty pin");
        check(PinAction.verify("12", h) == PinAction.INVALID && PinAction.verify("12a4", h) == PinAction.INVALID, "invalid pin");
        check(PinAction.verify("4821", null) == PinAction.WRONG && PinAction.verify("4821", "junk") == PinAction.WRONG, "missing hash");
        check(PinAction.verifyNew("2468", "2468") == PinAction.ACCEPTED, "new pin match");
        check(PinAction.verifyNew("2468", "2469") == PinAction.WRONG, "new pin mismatch");
        check(PinAction.verifyNew("", "2468") == PinAction.EMPTY && PinAction.verifyNew(null, "2468") == PinAction.EMPTY, "new pin empty");
        check(PinAction.verifyNew("12", "12") == PinAction.INVALID, "new pin too short");
        check(!PinAction.granted(PinAction.WRONG) && !PinAction.granted(PinAction.EMPTY) && !PinAction.granted(PinAction.INVALID)
                && PinAction.granted(PinAction.ACCEPTED), "only ACCEPTED grants");
    }

    static void kidsPlayAndBootAnimChecks() {
        check(KidsPlay.maxPegi(false) == 7 && KidsPlay.maxPegi(true) == 12 && KidsPlay.ADULT == 18, "pegi caps");
        check(KidsPlay.allowed(3, 7) && KidsPlay.allowed(7, 7) && !KidsPlay.allowed(12, 7) && !KidsPlay.allowed(16, 7), "kids filter");
        check(KidsPlay.allowed(12, 12) && !KidsPlay.allowed(16, 12), "kids 12 filter");
        check(KidsPlay.allowed(16, KidsPlay.ADULT) && KidsPlay.allowed(18, KidsPlay.ADULT) && KidsPlay.allowed(3, KidsPlay.ADULT), "adult no age filter");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (KidsPlay.Game g : KidsPlay.ALL) {
            check(g.pkg.matches("[A-Za-z0-9_.]+") && seen.add(g.pkg), "unique pkg " + g.pkg);
            check(g.pegi == 3 || g.pegi == 7 || g.pegi == 12 || g.pegi == 16 || g.pegi == 18, "rating " + g.pkg);
            check(!KidsPlay.list(g.cat, 7).isEmpty() || g.pegi > 7, "kids categories have PEGI 3/7 titles");
        }
        check(KidsPlay.byPkg("com.mojang.minecraftpe").pegi == 7 && KidsPlay.byPkg("com.sandboxol.blockymods").pegi == 16, "minecraft / blockman go");
        check(KidsPlay.page(KidsPlay.PUZZLE, 7, 0, 8).size() == 8 && KidsPlay.page(KidsPlay.PUZZLE, 7, 80000, 8).isEmpty(), "paging");
        check(KidsPlay.count(KidsPlay.PUZZLE, 7) >= 100 && KidsPlay.count(KidsPlay.RACING, 7) >= 100
                && KidsPlay.count(KidsPlay.ADVENTURE, 7) >= 100 && KidsPlay.count(KidsPlay.LEARNING, 7) >= 100
                && KidsPlay.count(KidsPlay.STYLE, 7) >= 100, "100+ verified kids games per tab");
        java.util.List<String> q = KidsPlay.storeQueue(java.util.List.of("com.king.candycrushsaga", "com.king.candycrushsaga", "com.bad!", "com.mojang.minecraftpe"), p -> "com.mojang.minecraftpe".equals(p));
        check(q.size() == 1 && q.get(0).equals("com.king.candycrushsaga"), "store queue skips installed and dupes");
        // PEGI 16 titles stay out of Kids / Kids 12+ lists
        for (KidsPlay.Game g : KidsPlay.list(KidsPlay.STYLE, 12)) check(g.pegi <= 12, "style kids12");
        boolean adultHas16 = false;
        for (KidsPlay.Game g : KidsPlay.list(KidsPlay.STYLE, KidsPlay.ADULT)) if (g.pegi == 16) adultHas16 = true;
        check(adultHas16, "adult style includes PEGI 16");
        java.util.List<Catalog.Category> adult = Catalog.adultBrowse(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), false, false, null);
        check(Catalog.indexOf(adult, "kpuzzle") == 0 && Catalog.indexOf(adult, "kroblox") >= 0 && Catalog.indexOf(adult, "kcloud") >= 0, "adult browse tabs");
        check(adult.get(Catalog.indexOf(adult, "kcloud")).items.contains(Catalog.MARVEL_RIVALS), "adult cloud includes rivals");
        check(Catalog.kidsBrowseId("ksearch") && Catalog.kidsBrowseId("kpuzzle") && Catalog.kidsBrowseId("kroblox") && !Catalog.kidsBrowseId("kthemes"), "browse ids");
        check(KidsPlay.SEARCH_CHIPS.length >= 6 && "Puzzle".equals(KidsPlay.SEARCH_CHIPS[0])
                && "Action".equals(KidsPlay.SEARCH_CHIPS[4]) && "Creative".equals(KidsPlay.SEARCH_CHIPS[5]), "search chips");
        check(KidsPlay.PUZZLE.equals(KidsPlay.chipCat("Puzzle")) && KidsPlay.STYLE.equals(KidsPlay.chipCat("Creative"))
                && KidsPlay.chipCat("Action") == null, "chip categories");
        int kidsMax = KidsPlay.maxPegi(false);
        java.util.List<Catalog.Item> puzzle = KidsPlay.chipItems("Puzzle", kidsMax, 0, KidsPlay.PAGE);
        java.util.List<Catalog.Item> action = KidsPlay.chipItems("Action", kidsMax, 0, KidsPlay.PAGE);
        java.util.List<Catalog.Item> creative = KidsPlay.chipItems("Creative", kidsMax, 0, KidsPlay.PAGE);
        check(!puzzle.isEmpty() && !action.isEmpty() && !creative.isEmpty(), "chip catalogue is never empty");
        check(KidsPlay.chipItems("Action", kidsMax, KidsPlay.PAGE * 50, KidsPlay.PAGE).isEmpty(), "free-text chip is not paged");
        check(!KidsPlay.chipItems("Puzzle", kidsMax, KidsPlay.PAGE, KidsPlay.PAGE).isEmpty(), "puzzle chip pages");
        for (Catalog.Item it : puzzle) {
            KidsPlay.Game g = KidsPlay.byPkg(it.storePackage());
            check(g != null && KidsPlay.PUZZLE.equals(g.cat) && g.pegi <= kidsMax, "puzzle chip age + category");
        }
        for (Catalog.Item it : creative) {
            KidsPlay.Game g = KidsPlay.byPkg(it.storePackage());
            check(g != null && KidsPlay.STYLE.equals(g.cat) && g.pegi <= kidsMax, "creative chip is style catalogue");
        }
        // BootAnim: backup names, short errors, extract original animation from a tiny module-shaped zip
        check("system-media-bootanimation.zip".equals(BootAnim.backupName("/system/media/bootanimation.zip"))
                && "/system/media/bootanimation.zip".equals(BootAnim.targetFromBackupName("system-media-bootanimation.zip")), "backup name");
        check(BootAnim.shortError("__REMOUNT=fail\nwhatever", 2).contains("could not be unlocked"), "remount short error");
        check(BootAnim.shortError("The backup could not be verified.", 3).contains("backup could not be verified"), "backup short error");
        check(BootAnim.dataLocalInstallCmd("/sdcard/Download/codeaque-bootanimation.zip").contains("/data/local/bootanimation.zip")
                && BootAnim.dataLocalInstallCmd("/sdcard/Download/codeaque-bootanimation.zip").contains("chmod 644"), "data local cmd");
        check(BootAnim.systemInstallCmd("/sdcard/Download/codeaque-bootanimation.zip").contains("codeaque-bootanim-backup")
                && BootAnim.restoreCmd().contains("rm -f /data/local/bootanimation.zip"), "system/restore cmds");
        boolean unsafe = false;
        try { BootAnim.dataLocalInstallCmd("/sdcard/x';reboot;'.zip"); } catch (IllegalArgumentException e) { unsafe = true; }
        check(unsafe, "unsafe path refused");
        check(BootAnim.magiskPresent(true, x -> false) && !BootAnim.magiskPresent(false, x -> false)
                && BootAnim.magiskPresent(false, x -> "/data/adb/magisk/magisk".equals(x)), "magisk present");
    }


    static void homeScrollChecks() {
        HomeScroll s = new HomeScroll();
        s.reset(0, 0);
        s.pointerDown();
        s.pointerMove(0.8f, 10);
        check(s.dragging && s.scroll > 0.79f && s.scroll < 0.81f, "drag moves the row");
        float held = s.scroll;
        s.step(1f / 60f, 3, 10, 12, true);
        s.step(1f / 60f, 3, 10, 12, true);
        check(s.dragging && Math.abs(s.scroll - held) < 0.0001f, "snap does not run against a live finger");
        check(s.tile == 0, "drag does not commit the focused tile");
        check(s.highlight(3, 12) == s.nearest(3, 12), "highlight follows the nearest tile while dragging");
        s.pointerUp(2.4f, 3, 10, 12);
        check(!s.dragging && s.flinging, "lift with velocity starts a fling");
        int frames = 0;
        float last = s.scroll;
        boolean reversed = false;
        while (s.step(1f / 60f, 3, 10, 12, true) && frames < 600) {
            frames++;
            if (s.flinging && s.scroll + 0.0001f < last) reversed = true;
            last = s.scroll;
        }
        check(!s.flinging && !s.dragging, "fling then snap settle");
        check(!reversed, "fling does not reverse mid-coast");
        check(frames > 10 && frames < 400, "settle takes a reasonable number of 60 Hz frames: " + frames);
        check(Math.abs(s.scroll - s.focusTarget(3, 10)) < 0.005f, "settles on the focused tile");
        HomeScroll.Bench b = HomeScroll.benchFling(12, 3, 2.4f, 1f / 60f);
        check(!b.snappedDuringDrag, "bench: no snap during drag");
        check(!b.reversedDuringFling, "bench: fling did not reverse");
        check(b.frames > 10 && b.frames < 400, "bench frames " + b.frames);
        // Honest CPU number for this host, not a phone GPU measurement.
        System.out.println("HomeScroll.benchFling: " + b.frames + " frames (~" + String.format(java.util.Locale.US, "%.0f", b.simMs)
                + " ms simulated at 60 Hz), cpu " + (b.cpuNs / 1_000_000f) + " ms on this machine, final tile " + b.finalTile);
    }

    static void coverFitChecks() {
        int[] src = CoverFit.coverSrc(200, 100, 188, 136);
        // 200x100 into 188x136: fill height, crop width. srcH=100, srcW=100*188/136=138, left=(200-138)/2=31
        check(src[0] == 31 && src[1] == 0 && src[2] == 31 + 138 && src[3] == 100, "wide image is cropped on the sides: " + java.util.Arrays.toString(src));
        int[] tall = CoverFit.coverSrc(100, 200, 188, 136);
        // fill width, crop height. srcW=100, srcH=100*136/188=72, top=(200-72)/2=64
        check(tall[0] == 0 && tall[2] == 100 && tall[1] == 64 && tall[3] == 64 + 72, "tall image is cropped top/bottom: " + java.util.Arrays.toString(tall));
        int[] sq = CoverFit.coverSrc(512, 512, 188, 136);
        int sh = 512 * 136 / 188;
        check(sq[0] == 0 && sq[2] == 512 && sq[3] - sq[1] == sh, "square icon fills a wide tile by cropping top and bottom");
        // 8px transparent padding around a 16x16 opaque block in a 32x32 image
        int w = 32, h = 32;
        int[] px = new int[w * h];
        for (int y = 8; y < 24; y++) for (int x = 8; x < 24; x++) px[y * w + x] = 0xFF3366CC;
        int[] box = CoverFit.contentBox(px, w, h, 16);
        check(box[0] == 8 && box[1] == 8 && box[2] == 24 && box[3] == 24, "transparent padding trimmed: " + java.util.Arrays.toString(box));
        int[] cropped = CoverFit.coverSrcTrimmed(px, w, h, 188, 136, 16);
        // content 16x16 into 188x136: fill width, crop height. srcW=16, srcH=16*136/188=11, top=8+(16-11)/2
        check(cropped[0] == 8 && cropped[2] == 24 && cropped[3] - cropped[1] == 11, "trimmed then centre-cropped: " + java.util.Arrays.toString(cropped));
        int[] empty = CoverFit.contentBox(new int[16], 4, 4, 16);
        check(empty[0] == 0 && empty[2] == 4 && empty[3] == 4, "fully transparent image keeps the full bounds");
        int[] same = CoverFit.coverSrc(188, 136, 188, 136);
        check(same[0] == 0 && same[1] == 0 && same[2] == 188 && same[3] == 136, "already the tile size is a no-op crop");
    }


    static void novaAndInstallPinChecks() {
        List<Catalog.Item> settings = Catalog.categories().get(Catalog.indexOf(Catalog.categories(), "settings")).items;
        java.util.List<String> acts = new java.util.ArrayList<>();
        for (Catalog.Item i : settings) acts.add(i.action);
        check(acts.contains("nova") && acts.contains("installpin"), "Settings has Nova and Ask PIN to install games: " + acts);

        Catalog.Item candy = KidsPlay.byPkg("com.king.candycrushsaga").item();
        check(candy.pegi == 3 && Catalog.opensStore(candy, false) && !Catalog.opensStore(candy, true), "Candy Crush is a GET when not installed");
        check(KidsPlay.ageFilteredGet(candy, false, false) && KidsPlay.ageFilteredGet(candy, false, true), "PEGI 3 GET is age-filtered in Kids and Kids 12+");
        check(!KidsPlay.ageFilteredGet(candy, true, false), "installed game is not a GET");
        check(!KidsPlay.kidsStoreNeedsPin(candy, false, false, false), "default: no PIN for age-filtered GET");
        check(KidsPlay.kidsStoreNeedsPin(candy, false, false, true), "Ask PIN to install games on: GET needs the PIN");
        check(!KidsPlay.kidsStoreNeedsPin(candy, true, false, false), "installed tile is not a store PIN gate");

        KidsPlay.Game gardenscapes = KidsPlay.byPkg("com.playrix.gardenscapes");
        check(gardenscapes != null && gardenscapes.pegi == 16, "Gardenscapes is PEGI 16 in the catalogue");
        Catalog.Item gs = gardenscapes.item();
        check(!KidsPlay.allowed(16, KidsPlay.maxPegi(false)) && !KidsPlay.allowed(16, KidsPlay.maxPegi(true)), "PEGI 16 is not in Kids or Kids 12+");
        check(!KidsPlay.ageFilteredGet(gs, false, false) && !KidsPlay.ageFilteredGet(gs, false, true), "PEGI 16 GET is not age-filtered");
        check(KidsPlay.kidsStoreNeedsPin(gs, false, false, false) && KidsPlay.kidsStoreNeedsPin(gs, false, true, false), "PEGI 16 still needs the PIN");

        // A PEGI 12 catalogue title: allowed only in Kids 12+
        KidsPlay.Game pegi12 = null;
        for (KidsPlay.Game g : KidsPlay.ALL) if (g.pegi == 12) { pegi12 = g; break; }
        check(pegi12 != null, "catalogue has a PEGI 12 title");
        Catalog.Item t12 = pegi12.item();
        check(!KidsPlay.ageFilteredGet(t12, false, false) && KidsPlay.ageFilteredGet(t12, false, true), "PEGI 12 GET is only age-filtered in Kids 12+");
        check(KidsPlay.kidsStoreNeedsPin(t12, false, false, false) && !KidsPlay.kidsStoreNeedsPin(t12, false, true, false), "Kids 12+ can GET a PEGI 12 game without the PIN");

        check(!KidsPlay.ageFilteredGet(Catalog.PLAY_STORE, false, false) && !KidsPlay.ageFilteredGet(Catalog.PLAY_STORE, false, true), "Play Store tile is never an age-filtered GET");
        check(KidsPlay.kidsStoreNeedsPin(Catalog.PLAY_STORE, false, false, false), "raw Play Store still needs the PIN");
        check(KidsPlay.kidsStoreNeedsPin("com.android.vending", false, false), "raw Play package still needs the PIN");
        check(!KidsPlay.ageFilteredGet("unknown.unrated.game", false) && KidsPlay.kidsStoreNeedsPin("unknown.unrated.game", false, false), "unrated package still needs the PIN");

        // Search hit carries pegi when not in the bundled catalogue
        PlaySearch.Hit hit = new PlaySearch.Hit("com.example.kidgame", "Made-up Kids Game", 3);
        Catalog.Item fromSearch = hit.item();
        check(fromSearch.pegi == 3 && KidsPlay.ageFilteredGet(fromSearch, false, false), "age-filtered search GET skips the PIN in Kids");
        PlaySearch.Hit teen = new PlaySearch.Hit("com.example.teengame", "Made-up Teen Game", 12);
        check(!KidsPlay.ageFilteredGet(teen.item(), false, false) && KidsPlay.ageFilteredGet(teen.item(), false, true), "search PEGI 12 follows Kids 12+");

        check(LockPin.needsPin("installpin") && !LockPin.needsPin("novashow") && !LockPin.needsPin("novalistenhidden"), "Ask PIN to install still needs the PIN; Show Nova and listen-while-hidden do not");
        check(!LockPin.needsPin("nova") && LockPin.needsPin("kidsoff") && LockPin.needsPin("screentime") && !LockPin.needsPin("update") && !LockPin.needsPin("kids"), "Nova settings tile is not lock-gated; leaving Kids and Screen Time still are; entering Kids and Update are not");

        String a = NovaHelp.answer("How do I update?");
        check(a.toLowerCase().contains("update") && a.toLowerCase().contains("settings"), "Nova help: update");
        check(NovaHelp.answer("Kids mode").toLowerCase().contains("pin"), "Nova help: Kids mode mentions the PIN");
        check(NovaHelp.answer("search").toLowerCase().contains("search"), "Nova help: search");
        String inst = NovaHelp.answer("Ask PIN to install games");
        check(inst.toLowerCase().contains("get") && inst.toLowerCase().contains("pin"), "Nova help: GET / install PIN");
        check(NovaHelp.answer("").equals(NovaHelp.GREETING), "empty question is a greeting");
        check(!NovaHelp.answer("who are you").toLowerCase().contains("api key"), "no API key pitch in the greeting path");

        NovaAnim.State anim = new NovaAnim.State();
        NovaAnim.reset(anim, 1000);
        check(anim.blink == 0f, "rest lids are fully open");
        check(NovaAnim.blinkAt(-1) == 0f && NovaAnim.blinkAt(NovaAnim.BLINK_TOTAL) == 0f, "blink is 0 outside a blink");
        check(NovaAnim.blinkAt(NovaAnim.CLOSE_MS) == 1f && NovaAnim.blinkAt(NovaAnim.CLOSE_MS + NovaAnim.HOLD_MS / 2) == 1f, "blink is fully closed at the end of close / during hold");
        float midClose = NovaAnim.blinkAt(NovaAnim.CLOSE_MS / 2);
        check(midClose > 0f && midClose < 1f, "close eases in");
        float midOpen = NovaAnim.blinkAt(NovaAnim.CLOSE_MS + NovaAnim.HOLD_MS + NovaAnim.OPEN_MS / 2);
        check(midOpen > 0f && midOpen < 1f, "open eases out");
        check(NovaAnim.inQuad(0) == 0f && NovaAnim.inQuad(1) == 1f && NovaAnim.outQuad(0) == 0f && NovaAnim.outQuad(1) == 1f, "quad easing ends");
        NovaAnim.tick(anim, 1000, 16, 1);
        check(anim.gazeX >= -NovaAnim.GAZE_MAX_X && anim.gazeX <= NovaAnim.GAZE_MAX_X
                && anim.gazeY >= -NovaAnim.GAZE_MAX_Y && anim.gazeY <= NovaAnim.GAZE_MAX_Y, "gaze stays in the socket");
        check(Math.abs(anim.bob) <= NovaAnim.BOB_AMP + 1e-6, "breath amplitude is small");
        NovaAnim.tickTalk(anim, 2000, true);
        check(anim.talking && anim.mouth > 0f, "mouth opens while talking");
        for (int i = 0; i < 20; i++) NovaAnim.tickTalk(anim, 2000 + i * 40, false);
        check(!anim.talking && anim.mouth < 0.05f, "mouth closes when speech ends");

        check(NovaHelp.jokeCount() >= 20, "enough offline jokes");
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        String[] bad = {"http", "www.", "pin", "1234", "poo", "pee", "toilet", "kill", "dead", "hell", "damn", "stupid", "idiot", "god", "jesus", "blood"};
        for (int i = 0; i < NovaHelp.jokeCount(); i++) {
            String j = NovaHelp.jokeAt(i);
            check(j != null && j.length() > 10, "joke " + i);
            check(!unique.contains(j), "duplicate joke: " + j);
            unique.add(j);
            String low = " " + j.toLowerCase().replaceAll("[^a-z]+", " ") + " ";
            check(!NovaHelp.kidsUnsafe(j), "joke must be kid-safe: " + j);
            for (String w : bad) check(!low.contains(" " + w + " "), "joke wording: " + w + " in " + j);
        }
        check(NovaHelp.jokeAt(NovaHelp.jokeCount()).equals(NovaHelp.jokeAt(0)), "jokes wrap");
        check(NovaHelp.looksLikeJoke("Tell me a joke") && NovaHelp.looksLikeJoke("tell me something funny")
                && NovaHelp.looksLikeJoke("joke") && NovaHelp.looksLikeJoke("another joke"), "joke intent");
        check(!NovaHelp.looksLikeJoke("I can tell you a joke")
                && !NovaHelp.looksLikeJoke(NovaHelp.KIDS_GREETING)
                && !NovaHelp.looksLikeJoke("something funny"),
                "her greeting must not look like a joke request");

        String kg = NovaHelp.answer("", true);
        check(kg.equals(NovaHelp.KIDS_GREETING) && !NovaHelp.kidsUnsafe(kg), "kids greeting");
        for (String q : new String[]{"Find a game", "How do I play?", "Learning", "search", "How do I update?", "parent PIN", "leave Kids mode", "Screen Time", "Nova", "sideload"}) {
            String ka = NovaHelp.answer(q, true);
            check(!NovaHelp.kidsUnsafe(ka), "kids answer is safe for: " + q + " -> " + ka);
            check(!ka.toLowerCase().contains("http") && !ka.contains("1234"), "no links or default PIN in kids answer: " + q);
        }
        String games = NovaHelp.answer("Find a game", true).toLowerCase();
        check(games.contains("search") && games.contains("get"), "kids games help");
        String learn = NovaHelp.answer("Learning", true).toLowerCase();
        check(learn.contains("learning"), "kids learning help");
        check(NovaHelp.answer("Tell me a joke", true).toLowerCase().contains("joke"), "typed joke points at the button");
        String[] chips = NovaHelp.kidsChips();
        check(chips.length == 3 && chips[0].contains("game") && chips[2].toLowerCase().contains("learn"), "kids chips are games + learning");
        for (String c : chips) check(!c.toLowerCase().contains("pin") && !c.toLowerCase().contains("settings"), "no admin chips");
        check(NovaHelp.kidsUnsafe("See https://example.com") && NovaHelp.kidsUnsafe("The PIN is 1234") && !NovaHelp.kidsUnsafe(NovaHelp.KIDS_FOOT), "kidsUnsafe detector");
        // Adult help is unchanged and still explains the PIN
        check(NovaHelp.answer("Kids mode", false).toLowerCase().contains("pin"), "adult help still mentions the PIN");
        check(!KidsPlay.kidsStoreNeedsPin(candy, false, false, false), "0.10.53 does not change no-PIN age-filtered GET");

        // Bubble snap: nearer vertical edge, Y kept and clamped, 0..1 fraction survives a resize.
        float[] s = NovaPlace.snap(700, 400, 96, 800, 1280, 0, 0, 0, 0, 14);
        check(s[0] == 800 - 14 - 96 && s[1] == 400, "snap to the nearer right edge, keep Y");
        s = NovaPlace.snap(40, 400, 96, 800, 1280, 0, 0, 0, 0, 14);
        check(s[0] == 14 && s[1] == 400, "snap to the nearer left edge, keep Y");
        s = NovaPlace.snap(100, -20, 96, 800, 1280, 0, 40, 0, 60, 14);
        check(s[1] == 40 + 14, "Y clamps to the top inset plus margin");
        s = NovaPlace.snap(100, 2000, 96, 800, 1280, 0, 40, 0, 60, 14);
        check(s[1] == 1280 - 60 - 14 - 96, "Y clamps to the bottom inset plus margin");
        check(NovaPlace.leftSide(14, 96, 800, 0, 0, 14) && !NovaPlace.leftSide(800 - 14 - 96, 96, 800, 0, 0, 14), "side from X");
        check(NovaPlace.xForSide(true, 96, 800, 0, 0, 14) == 14 && NovaPlace.xForSide(false, 96, 800, 0, 0, 14) == 800 - 14 - 96, "X for a side");
        float frac = NovaPlace.yFrac(400, 96, 1280, 0, 0, 14);
        check(frac > 0f && frac < 1f, "mid-screen Y is a mid fraction");
        float y2 = NovaPlace.yFromFrac(frac, 96, 1920, 0, 0, 14);
        check(Math.abs(NovaPlace.yFrac(y2, 96, 1920, 0, 0, 14) - frac) < 1e-5, "Y fraction survives a taller screen");
        check(NovaPlace.yFromFrac(1f, 96, 1280, 0, 0, 14) == 1280 - 14 - 96, "default fraction 1 is the bottom");
        check(!NovaPlace.movedPast(4, 4, 8) && NovaPlace.movedPast(10, 0, 8), "touch slop");
        check(NovaPlace.clamp(5, 0, 3) == 3 && NovaPlace.clamp(-1, 0, 3) == 0, "clamp");

        check(NovaPlace.isDoubleTap(1000, 1200, NovaPlace.DOUBLE_MS) && !NovaPlace.isDoubleTap(1000, 1300, NovaPlace.DOUBLE_MS), "double-tap window is 250 ms");
        check(!NovaPlace.isDoubleTap(1200, 1000, NovaPlace.DOUBLE_MS), "second tap cannot precede the first");
        check(NovaPlace.span(0, 0, 3, 4) == 5f, "pinch span");
        check(Math.abs(NovaPlace.pinchScale(100, 150) - 1.5f) < 1e-5 && NovaPlace.pinchScale(0, 10) == 1f, "pinch scale");
        check(NovaPlace.goBig(false, 1.3f) && !NovaPlace.goBig(false, 1.1f) && !NovaPlace.goBig(true, 0.7f) && NovaPlace.goBig(true, 1.0f), "pinch-out grows, pinch-in shrinks, unclear keeps the mode");
        check(NovaPlace.showBubble(true, false) && !NovaPlace.showBubble(true, true) && !NovaPlace.showBubble(false, false), "bubble only when the parent allows her and she is not hidden");
        check(NovaPlace.showTab(true, true) && !NovaPlace.showTab(false, true) && !NovaPlace.showTab(true, false), "edge tab only after a double-tap hide while Show Nova is on");

        // 0.10.54: Kids-mode PIN list (CodeAque Update and entering Kids skip the PIN).
        check(!LockPin.kidsActionNeedsPin("update") && !LockPin.kidsActionNeedsPin("wifi")
                && !LockPin.kidsActionNeedsPin("kidson") && !LockPin.kidsActionNeedsPin("kids")
                && !LockPin.kidsActionNeedsPin(null), "Kids self-update, Wi-Fi and entering Kids do not need the PIN");
        check(LockPin.kidsActionNeedsPin("kidsoff") && LockPin.kidsActionNeedsPin("adultpin")
                && LockPin.kidsActionNeedsPin("settingspin") && LockPin.kidsActionNeedsPin("goto:settings")
                && LockPin.kidsActionNeedsPin("kidsprofile"), "leaving Kids, Settings and changing the Kids profile still need the PIN");
        check(!LockPin.kidsActionNeedsPin("theme") && !LockPin.kidsActionNeedsPin("kthemes"), "unknown Kids actions are not PIN-gated by this list");

        // Movement past slop starts a drag; a clean tap / pinch is not a drag-start.
        check(NovaPlace.dragStartsOnMove(false, false, false, true), "move past slop starts a drag");
        check(!NovaPlace.dragStartsOnMove(true, false, false, true), "already dragging does not re-start");
        check(!NovaPlace.dragStartsOnMove(false, true, false, true), "big mode does not start a bubble drag");
        check(!NovaPlace.dragStartsOnMove(false, false, true, true), "pinch does not start a drag");
        check(!NovaPlace.dragStartsOnMove(false, false, false, false), "a still finger is not a drag (tap / double-tap can fire)");

        check(NovaHelp.kidsUnsafe("Don't pee on the floor") && !NovaHelp.kidsUnsafe("It wasn't peeling well."),
                "kidsUnsafe uses word boundaries so pee is blocked and peeling is not");
        check(NovaHelp.kidsUnsafe("kill the dragon") && !NovaHelp.kidsUnsafe("A skill puzzle"), "kill is a word, skill is not");
        check(NovaHelp.KIDS_UNSURE.toLowerCase().contains("grown-up") && !NovaHelp.kidsUnsafe(NovaHelp.KIDS_UNSURE), "unsure fallback is kid-safe");
        check(!NovaHelp.kidsMayAskOnline("Find a game") && !NovaHelp.kidsMayAskOnline("Learning")
                && !NovaHelp.kidsMayAskOnline("Tell me a joke") && !NovaHelp.kidsMayAskOnline("parent PIN")
                && !NovaHelp.kidsMayAskOnline("How do I update?") && !NovaHelp.kidsMayAskOnline(""),
                "local games/learning/joke/admin questions stay offline");
        check(NovaHelp.kidsMayAskOnline("what is a volcano") && NovaHelp.kidsMayAskOnline("why is the sky blue"),
                "an unknown learning question may use Smarter Nova");
        check(!NovaHelp.answer("what is a volcano", true).toLowerCase().contains("wikipedia")
                && !NovaHelp.answer("what is a volcano", true).toLowerCase().contains("gemma")
                && !NovaHelp.KIDS_GREETING.toLowerCase().contains("wikipedia"),
                "kids answers never name a model or Wikipedia");

        String os = "[\"volcano\",[\"Volcano\"],[\"A volcano is a mountain.\"],[\"https://simple.wikipedia.org/wiki/Volcano\"]]";
        check("Volcano".equals(NovaWiki.firstOpenSearchTitle(os)), "opensearch first title");
        check(NovaWiki.firstOpenSearchTitle("[\"q\",[],[],[]]") == null, "empty opensearch title");
        check("A volcano is a mountain that can erupt.".equals(NovaWiki.extractExtract(
                "{\"title\":\"Volcano\",\"extract\":\"A volcano is a mountain that can erupt.\",\"extract_html\":\"<p>no\"}")),
                "summary extract, not extract_html");
        check("Volcano".equals(NovaWiki.titlePath("Volcano")) && "Mount_Etna".equals(NovaWiki.titlePath("Mount Etna")), "title path");
        check("A volcano is a mountain. It can erupt.".equals(NovaWiki.firstSentences(
                "A volcano is a mountain. It can erupt. Scientists watch them closely.", 2)), "two sentences");
        check("Hello there.".equals(NovaWiki.stripLinks("Hello https://example.com there.")), "strip links");
        check("Short fact. Extra sentence three is long for a child.".equals(NovaWiki.kidTrim("Short fact. Extra sentence three is long for a child. More.")), "kidTrim keeps two sentences");
        check(NovaWiki.kidTrim("See www.example.com for more.") == null || !NovaWiki.kidTrim("See www.example.com for more.").toLowerCase().contains("www."),
                "kidTrim does not keep a web address");

        // 0.10.54 follow-up: tuck, voice scoring, expressions, Nova section.
        float[] tc = NovaPlace.tuckSnap(100, 100, 58, 800, 1280, 0, 0, 0, 0, 6);
        check(tc[0] == 6 && tc[1] == 6, "tuck to the nearest top-left corner");
        tc = NovaPlace.tuckSnap(700, 1000, 58, 800, 1280, 0, 0, 0, 0, 6);
        check(tc[0] == 800 - 6 - 58 && tc[1] == 1280 - 6 - 58, "tuck to the nearest bottom-right corner");
        check(NovaPlace.tuckedTop(20, 58, 1280, 0, 0, 6) && !NovaPlace.tuckedTop(1200, 58, 1280, 0, 0, 6), "tuck side is top or bottom");

        check(NovaTtsVoice.score("en", "GB", "en-gb-x-rjs-female", 400, false)
                > NovaTtsVoice.score("en", "US", "en-us-x-sfg-male", 400, false), "prefer a UK female voice over a US male");
        check(NovaTtsVoice.score("en", "GB", "offline", 300, false)
                > NovaTtsVoice.score("en", "GB", "network", 400, true), "prefer an offline voice");
        check(NovaTtsVoice.score("fr", "FR", "female", 500, false) < 0, "skip a non-English voice");
        String[] parts = NovaTtsVoice.sentences("Hello there. How are you? I'm Nova!");
        check(parts.length == 3 && parts[0].startsWith("Hello") && parts[2].contains("Nova"), "sentence split keeps the marks");
        check(NovaTtsVoice.sentences("CodeAque 0.10.54 is ready.").length == 1, "a version number is not a sentence break");
        check(NovaTtsVoice.estimateMs("Hi") > 500 && NovaTtsVoice.estimateMs("one two three four") > NovaTtsVoice.estimateMs("one"), "longer answers last longer");
        check(!LockPin.needsPin("novashow") && !LockPin.needsPin("novavoice") && !LockPin.needsPin("novasmarter") && !LockPin.needsPin("novahide") && !LockPin.needsPin("novalistenhidden"),
                "Nova show/voice/smarter/hide are not lock-gated; overlay still is");
        List<Catalog.Category> cats54 = Catalog.categories();
        int ni = Catalog.indexOf(cats54, "nova");
        check(ni > 0 && "Nova".equals(cats54.get(ni).label) && cats54.get(ni).items.size() >= 5, "grown-ups Settings has a Nova section");
        java.util.List<String> na = new java.util.ArrayList<>();
        for (Catalog.Item i : cats54.get(ni).items) na.add(i.action);
        check(na.contains("nova") && na.contains("novashow") && na.contains("novavoice") && na.contains("novasmarter") && na.contains("novahide") && na.contains("novalistenhidden") && !na.contains("novaoverlay"),
                "Nova section has the panel, toggles, hide and overlay");

        NovaAnim.State ex = new NovaAnim.State();
        NovaAnim.reset(ex, 1000);
        check(ex.smile == 0 && ex.brow == 0, "expressions start at rest");
        boolean sawSmile = false, sawBrow = false;
        for (int i = 0; i < 400; i++) {
            NovaAnim.tick(ex, 1000 + i * 40, 40, 3 + i * 17);
            if (ex.smile > 0.05f) sawSmile = true;
            if (ex.brow > 0.05f) sawBrow = true;
            check(ex.smile >= 0 && ex.smile <= NovaAnim.SMILE_MAX && ex.brow >= 0 && ex.brow <= NovaAnim.BROW_MAX, "expression range");
            check(Math.abs(ex.sway) <= NovaAnim.SWAY_AMP + 1e-4, "sway amplitude");
        }
        check(sawSmile && sawBrow, "idle expressions include a smile and a brow raise");
        NovaAnim.tickTalk(ex, 20000, true);
        NovaAnim.tick(ex, 20000, 16, 1);
        check(ex.talking && ex.smileT >= 0, "she smiles a little while talking");
        // 0.10.55: search bar fits on a short landscape phone; custom keyboard has Hide.
        float sShot = Math.min(2376 / 1000f, 1060 / 720f);
        float dShot = 2.75f;
        float catsBottom = 1060 * 0.26f, foot = Math.max(78 * sShot, 56 * dShot);
        float browseTop = 1060 - foot - 136 * sShot * 1.7f;
        float avail = browseTop - catsBottom - 16 * sShot;
        float qh = KidsSearchLayout.queryHeight(sShot, dShot, avail);
        float ts = KidsSearchLayout.textSize(qh, sShot, dShot);
        float chip = KidsSearchLayout.chipHeight(sShot, avail - qh - KidsSearchLayout.GAP_S * sShot);
        check(qh > 36 * sShot, "search bar is taller than the 0.10.54 36*s box");
        check(KidsSearchLayout.textFits(qh, ts), "search text plus padding fits in the bar");
        check(qh + KidsSearchLayout.GAP_S * sShot + chip <= avail + 0.5f, "chips sit under the bar without overlapping the game row");
        check(ts < qh, "search text is smaller than the bar");

        float sWin = 1f, dWin = 1f;
        float availWin = 720 - 720 * 0.26f - Math.max(78, 56) - 136 - 16;
        float qhW = KidsSearchLayout.queryHeight(sWin, dWin, availWin);
        check(KidsSearchLayout.textFits(qhW, KidsSearchLayout.textSize(qhW, sWin, dWin)), "search text fits on a 1280x720 density-1 screen");

        float sShort = Math.min(1024 / 1000f, 480 / 720f), dShort = 2f;
        float availS = Math.max(40, 480 * 0.4f);
        float qhS = KidsSearchLayout.queryHeight(sShort, dShort, availS);
        check(qhS <= availS && KidsSearchLayout.textFits(qhS, KidsSearchLayout.textSize(qhS, sShort, dShort)),
                "on a short landscape screen the bar still fits its text and the remaining gap");

        check(KidsSearchLayout.lastRowHasHide(KidsSearchLayout.searchKeys(false, false))
                && KidsSearchLayout.lastRowHasHide(KidsSearchLayout.searchKeys(true, false))
                && KidsSearchLayout.lastRowHasHide(KidsSearchLayout.searchKeys(false, true)),
                "custom keyboard has a Hide key on every layout");
        check(KidsSearchLayout.isHideKey("Hide") && !KidsSearchLayout.isHideKey("GO"), "Hide key is recognised");
        check(!KidsSearchLayout.shouldKeepKeyboard(false, false), "a tap on tiles, tabs or the background closes the keyboard");
        check(KidsSearchLayout.shouldKeepKeyboard(true, false), "a tap in the focused text field keeps the keyboard");
        check(KidsSearchLayout.shouldKeepKeyboard(false, true), "a tap on the custom keyboard keeps it");


        // 0.10.56: Heart is Nova's default on-device voice; kid-safe Gemma filter.
        check("kokoro-af_heart".equals(NovaVoices.DEFAULT_VOICE_ID), "default on-device voice is Kokoro Heart");
        check(NovaVoices.hasLockedDefault(), "a default voice id is set");
        check(NovaVoices.HELLO_LINE.contains("Nova"), "hello line introduces Nova");
        String prompt = NovaLlmFilter.prompt("Why is the sky blue?");
        check(prompt.contains("Nova") && prompt.contains("sky blue") && prompt.contains("user"),
                "Gemma prompt is in the instruct format and names Nova");
        check(NovaLlmFilter.filter("  Nova: The sky looks blue because air scatters sunlight. <end_of_turn>")
                .startsWith("The sky looks blue"), "filter strips labels and special tokens");
        check(NovaLlmFilter.filter("Here is a PIN 1234 you can use.") == null, "filter rejects unsafe PIN talk");
        check(NovaLlmFilter.filter("See https://example.com for more.") == null, "filter rejects links");
        check(NovaLlmFilter.firstSentences("One. Two. Three.", 2).equals("One. Two."), "keeps at most two sentences");


        // 0.10.57: always fall back to system TTS; don't start the mic mid-utterance;
        // the mouth flaps from a timer while talking.
        check(!NovaVoiceGate.useOnDevice(false, false, "kokoro-af_heart"), "no files: use system TTS");
        check(NovaVoiceGate.useOnDevice(true, false, "kokoro-af_heart"), "pack on disk: try Heart and load the engine (do not require a prior load)");
        check(NovaVoiceGate.useOnDevice(true, true, "kokoro-af_heart"), "loaded Heart voice may be used");
        check(NovaVoiceStatus.shouldTryOnDevice(true, "kokoro-af_heart") && !NovaVoiceStatus.shouldTryOnDevice(false, "kokoro-af_heart"), "try Heart only when the pack is on disk");
        check(!NovaVoiceGate.canStartListen(true, true, true, false, false), "do not start the mic while she is talking");
        check(!NovaVoiceGate.canStartListen(true, true, false, true, false), "do not start the mic while speech is pending");
        check(NovaVoiceGate.canStartListen(true, true, false, false, false), "mic may start after she finishes");
        NovaAnim.State lips = new NovaAnim.State();
        NovaAnim.reset(lips, 0);
        NovaAnim.tickTalk(lips, 200, true);
        check(lips.talking && lips.mouth > 0.05f, "the mouth opens from a timer while talking");
        NovaAnim.tickTalk(lips, 400, true, 0.6f);
        check(lips.mouth > 0.05f, "the mouth also follows playback amplitude");
        NovaAnim.tickTalk(lips, 600, false);
        check(lips.mouth < 0.5f, "the mouth eases shut when she stops");


        // 0.10.58: overlay gone; double-tap hides until Show Nova; listen after text mode;
        // optional listen-while-hidden; Heart is tried whenever the pack is on disk;
        // slower default speed and clear status lines.
        List<Catalog.Category> kn = Catalog.kidsMode();
        int knova = Catalog.indexOf(kn, "knova");
        int kpar = Catalog.indexOf(kn, "kparent");
        check(knova >= 0 && kpar == kn.size() - 1 && knova == kpar - 1, "Kids mode has a Nova tab before Grown-ups");
        Catalog.Category novaTab = kn.get(knova);
        boolean hasShow = false, hasListen = false, hasPanel = false;
        for (Catalog.Item it : novaTab.items) {
            if ("novashow".equals(it.action)) hasShow = true;
            if ("novalistenhidden".equals(it.action)) hasListen = true;
            if ("nova".equals(it.action)) hasPanel = true;
        }
        check(hasShow && hasListen && hasPanel, "Kids Nova tab has Show Nova and Keep listening while hidden");
        check(!NovaListenHidden.switchOn(null) && !NovaListenHidden.switchOn(Boolean.FALSE), "Keep listening while hidden defaults off");
        check(NovaListenHidden.isHidden(false, false) && !NovaListenHidden.isHidden(true, false), "Show Nova off means she is hidden");
        check(NovaListenHidden.shouldRun(true, true, true) && !NovaListenHidden.shouldRun(false, true, true), "hidden listen runs only with the switch on");
        check(NovaListenHidden.canStartFromUser(true, true, true) && !NovaListenHidden.canStartFromUser(true, false, true), "start hidden listen only in the foreground");
        check(NovaListenHidden.canListenNow(true, false) && !NovaListenHidden.canListenNow(true, true), "mic stays off while she is speaking");
        check(NovaListenHidden.shouldListenAfterTextOff(true, true, false, false), "leaving text mode starts listen when she is not talking");
        check(!NovaListenHidden.shouldListenAfterTextOff(true, true, true, false), "leaving text mode waits if she is still talking");
        check(NovaListenHidden.wantsVoiceLoopAfterTextOff(true, true), "leaving text mode enters the voice loop");
        check(NovaPlace.isImmediateDoubleTap(1000, 1200, NovaPlace.DOUBLE_MS), "a second tap in the window is a double-tap");
        check(!NovaPlace.isImmediateDoubleTap(1000, 2000, NovaPlace.DOUBLE_MS), "a late second tap is not a double-tap");
        check(!NovaPlace.isImmediateDoubleTap(0, 1200, NovaPlace.DOUBLE_MS), "the first tap is a single tap and fires immediately");
        check(NovaVoiceStatus.of(false, false, false, 0, null).equals(NovaVoiceStatus.PHONE), "status: Using phone voice");
        check(NovaVoiceStatus.of(false, false, true, 42, null).equals("Downloading 42%"), "status: Downloading x%");
        check(NovaVoiceStatus.of(true, true, false, 0, null).equals(NovaVoiceStatus.HEART_READY), "status: Heart ready");
        check(NovaVoiceStatus.of(true, false, false, 0, "lib failed").equals("Heart failed: lib failed"), "status: Heart failed: reason");
        check(NovaVoiceStatus.testResult(true).equals("Spoke with Heart") && NovaVoiceStatus.testResult(false).equals("Spoke with the phone's voice"), "test voice names the engine");
        check(Math.abs(NovaVoiceStatus.kokoroSpeed(null) - 0.85f) < 1e-4, "Kokoro default speed is 0.85");
        check(Math.abs(NovaVoiceStatus.systemRate(null) - 0.90f) < 1e-4, "system TTS default rate is 0.90");
        check(Math.abs(NovaVoiceStatus.progressToSpeed(NovaVoiceStatus.speedToProgress(0.85f)) - 0.85f) < 1e-4, "speed slider round-trips 0.85");
        check(NovaVoiceGate.shouldRecycleRecognizer(NovaVoiceGate.ERR_CLIENT)
                && !NovaVoiceGate.shouldRecycleRecognizer(NovaVoiceGate.ERR_RECOGNIZER_BUSY)
                && NovaVoiceGate.shouldRecycleRecognizer(NovaVoiceGate.ERR_NO_MATCH)
                && NovaVoiceGate.shouldRecycleRecognizer(NovaVoiceGate.ERR_SPEECH_TIMEOUT),
                "recycle the recognizer after client/no-match/timeout, but not when the mic is busy");
        check(NovaListenHidden.TOAST_HIDDEN.contains("Nova tab"), "hide toast points at the Nova tab");

        // 0.10.59: Install stays until the installed version is current; Heart sid/lang;
        // speech-service copy; English TTS fallback; listen gate can reopen after a dead callback.
        check(UpdateOffer.shouldShow("0.10.59", "0.10.58") && !UpdateOffer.shouldShow("0.10.59", "0.10.59"), "offer shows only while installed is older");
        check(!UpdateOffer.shouldShow("", "0.10.58") && !UpdateOffer.shouldShow(null, "0.10.58"), "empty offer hides");
        check(UpdateOffer.shouldShow("0.10.59", "0.10.59", 159, 158) && !UpdateOffer.shouldShow("0.10.59", "0.10.58", 159, 159), "versionCode wins when known");
        check(UpdateOffer.bannerLabel("0.10.59").contains("0.10.59") && UpdateOffer.INSTALL.equals("Install"), "banner names the version and Install");
        check(NovaKokoroSpeakers.AF_HEART == 3 && NovaKokoroSpeakers.idForVoice(NovaVoices.ID_KOKORO_HEART) == 3, "Heart is sid 3 on v1.0");
        check(NovaKokoroSpeakers.idForVoice(NovaVoices.ID_KOKORO_BELLA) == 2 && NovaKokoroSpeakers.idForVoice(NovaVoices.ID_KOKORO_EMMA) == 21, "Bella 2 Emma 21");
        check(NovaKokoroSpeakers.safeSid(-1) == 3 && NovaKokoroSpeakers.safeSid(99) == 3, "bad sid falls back to Heart, not a Chinese voice");
        check(NovaKokoroSpeakers.isEnglishSpeaker(3) && !NovaKokoroSpeakers.isEnglishSpeaker(45), "zf_* is not an English speaker");
        check(NovaKokoroSpeakers.LANG.equals("en-us") && NovaKokoroSpeakers.SAMPLE_RATE == 24000, "en-us at 24 kHz");
        check(NovaSpeechAvail.path(true, false) == NovaSpeechAvail.USE_RECOGNIZER, "recognizer wins when available");
        check(NovaSpeechAvail.path(false, true) == NovaSpeechAvail.USE_PROMPT, "system speak-now is the fallback when the pack is not ready");
        check(NovaSpeechAvail.path(false, false) == NovaSpeechAvail.NEED_BUILTIN, "no service means offer built-in listening");
        check(NovaSpeechAvail.NO_SERVICE.contains("Speech Recognition & Synthesis from Google from Play Store"), "exact Play Store wording");
        check(!NovaSpeechAvail.NO_SERVICE.toLowerCase().contains("iphone") && !NovaSpeechAvail.NO_SERVICE.toLowerCase().contains("apple"), "speech copy has no brand words");
        check(NovaSpeechAvail.showInstallButton(NovaSpeechAvail.NEED_INSTALL) && !NovaSpeechAvail.showInstallButton(NovaSpeechAvail.USE_RECOGNIZER), "install button only when needed");
        check(NovaSpeechAvail.status(NovaSpeechAvail.USE_RECOGNIZER, true, null).equals("Listening…"), "status shows Listening");
        check(NovaTtsVoice.preferredLanguageTag(true, true).equals("en-GB") && NovaTtsVoice.preferredLanguageTag(false, true).equals("en-US"), "UK then US");
        check(NovaTtsVoice.isEnglish("en") && NovaTtsVoice.isEnglish("EN") && !NovaTtsVoice.isEnglish("zh"), "only English counts as English");
        check(!NovaTtsVoice.acceptFallbackLocale("zh") && NovaTtsVoice.acceptFallbackLocale("en"), "never fall back to a zh locale");
        check(NovaVoiceGate.canStartListen(true, true, false, false, false) && !NovaVoiceGate.canStartListen(true, true, true, false, false), "listen waits while she is talking");

        // 0.10.60: single-flight speak, ignore her own echo, stable listen gate, engine tags.
        check(NovaSpeakGate.nextGen(0) == 1 && NovaSpeakGate.nextGen(7) == 8, "speak gen increments");
        check(NovaSpeakGate.isCurrent(3, 3) && !NovaSpeakGate.isCurrent(2, 3) && NovaSpeakGate.stale(2, 3), "stale jobs are dropped");
        check(NovaSpeakGate.stale(0, 0), "gen 0 is never current");
        check(NovaSpeakGate.canStartListen(true, true, false, false, false, 2000, 1000), "listen after hangover");
        check(!NovaSpeakGate.canStartListen(true, true, true, false, false, 2000, 0), "no listen while talking");
        check(!NovaSpeakGate.canStartListen(true, true, false, false, true, 2000, 0), "no listen while a speak job is in flight");
        check(!NovaSpeakGate.canStartListen(true, true, false, false, false, 500, 1000), "no listen during hangover");
        check(NovaSpeakGate.sameLine("Hi, I'm Nova! We can find a game.",
                NovaHelp.KIDS_GREETING),
                "a heard slice of her greeting is treated as her echo");
        check(!NovaSpeakGate.sameLine("hello", NovaHelp.KIDS_GREETING), "a real hello is not her greeting");
        check(NovaSpeakGate.shouldDropHeard("how do I play", NovaHelp.KIDS_GREETING, true, false, 5000, 0), "drop any result while she is talking");
        check(NovaSpeakGate.shouldDropHeard("how do I play", NovaHelp.KIDS_FOOT, false, false, 500, 1000), "drop results in the hangover");
        check(!NovaSpeakGate.shouldDropHeard("hello", NovaHelp.KIDS_GREETING, false, false, 2000, 1000), "hello after she finishes is kept");
        check(NovaSpeakGate.speakTag(true).equals("Heart") && NovaSpeakGate.speakTag(false).equals("Phone voice"), "on-screen engine tags");
        check(NovaVoiceStatus.failed("lib failed").equals("Heart failed: lib failed"), "Heart load error is shown in full");
        check(NovaSpeakGate.norm("Hellooo!").equals("hellooo") && NovaSpeakGate.norm("  How do I play? ").equals("how do i play"), "norm strips punctuation");

        // 0.10.61: robust multi-turn loop, mic-busy, mouth from playback RMS, no field jumps, adult scroll.
        check(NovaConvo.runTurns(5) == 5, "five consecutive turns each release their locks");
        NovaConvo.Loop loop = new NovaConvo.Loop();
        loop.field = "Hello";
        loop.startListen();
        loop.onPartial(loop.listenGen, "What");
        loop.onPartial(loop.listenGen, "What's 8 * 8");
        check(loop.field.equals("Hello"), "partial results never write the input field");
        check(loop.caption.equals("What's 8 * 8"), "partials go to the live caption");
        check(loop.onFinal(loop.listenGen, "What's 8 * 8") && loop.status.startsWith("You said:"), "status shows You said");
        check(loop.field.equals("Hello"), "a final result also leaves the input field alone");
        loop.beginReply("Answer");
        check(NovaConvo.THINKING_TEXT.equals(loop.status), "status shows Thinking while she prepares a reply");
        check(NovaConvo.spokenMatchesScreen(loop.lastSpoken, loop.answer), "spoken text matches the on-screen text");
        loop.endReply();
        check(!loop.locksHeld(), "endReply releases every lock");
        loop.startListen();
        loop.micBusy();
        check(NovaConvo.MIC_BUSY_TEXT.equals(loop.status) && !loop.listening, "mic busy shows a friendly message and stops");
        check(NovaConvo.isMicBusy(NovaConvo.ERR_BUSY) && NovaConvo.isMicBusy(NovaConvo.ERR_AUDIO), "busy and audio are mic-busy");
        check(!NovaConvo.shouldRetry(NovaConvo.ERR_BUSY) && !NovaVoiceGate.shouldRecycleRecognizer(NovaVoiceGate.ERR_AUDIO), "a busy mic is not retried");
        check(NovaConvo.shouldRetry(NovaVoiceGate.ERR_NO_MATCH), "no-match may retry");
        loop.beginReply("x");
        loop.timeout();
        check(NovaConvo.FALLBACK_TEXT.equals(loop.lastSpoken) && !loop.locksHeld(), "a reply timeout speaks the canned fallback and releases locks");
        check(NovaHelp.KIDS_GREETING.contains("find a game") && !NovaHelp.KIDS_GREETING.contains("find a games"), "greeting says find a game");
        check(!NovaHelp.KIDS_GREETING.contains("find a game,"), "game is its own sentence so Heart does not say games");
        check(NovaConvo.SILENCE_COMPLETE_MS == 1200L
                && NovaConvo.SILENCE_POSSIBLY_MS == 1200L, "recognizer silence is 1.2s after speech");
        NovaMouth.Ring ring = new NovaMouth.Ring(8);
        ring.add(0, 0.08f);
        ring.add(2000, 0.01f);
        check(Math.abs(ring.atHead(100) - 0.08f) < 1e-4, "mouth uses the RMS the playback head is in");
        check(NovaMouth.mouthFromRms(0.08f) >= 0.22f && NovaMouth.mouthFromRms(0.004f) == 0f, "jaw opens clearly on speech and closes on quiet");
        check(NovaMouth.drained(1000, 1000, false) && !NovaMouth.drained(100, 1000, false), "audio ends when the playback head reaches the last sample");
        check(!HomeDrawGate.bakeOnDrawThread(), "covers are not baked on the UI thread");
        check(!HomeDrawGate.drawHeavyFx(true) && HomeDrawGate.drawHeavyFx(false), "glows and embers pause during a fling");
        check(HomeDrawGate.tileKey("a", 144, 200, 400, 400).equals("a@144x200#400x400"), "tile cache keys are stable sizes");

        // 0.10.62: Listening chip stays on between restarts; empty cycles back off and then give up.
        check(NovaListenGate.retryDelayMs(0) == 300 && NovaListenGate.retryDelayMs(1) == 600
                && NovaListenGate.retryDelayMs(2) == 1200 && NovaListenGate.retryDelayMs(3) == 2000
                && NovaListenGate.retryDelayMs(9) == 2000, "restart delay starts at 300 ms and caps at 2 s");
        check(NovaListenGate.isEmptyCycle(NovaVoiceGate.ERR_NO_MATCH)
                && NovaListenGate.isEmptyCycle(NovaVoiceGate.ERR_SPEECH_TIMEOUT)
                && NovaListenGate.isEmptyCycle(NovaVoiceGate.ERR_CLIENT)
                && !NovaListenGate.isEmptyCycle(NovaConvo.ERR_BUSY), "no-match, timeout and client count as empty cycles");
        check(NovaListenGate.nextEmptyCycles(0, true) == 1 && NovaListenGate.nextEmptyCycles(4, true) == 5
                && NovaListenGate.nextEmptyCycles(3, false) == 0, "empty cycles increment, a real result resets them");
        check(NovaListenGate.shouldRetryEmpty(0) && NovaListenGate.shouldRetryEmpty(4)
                && !NovaListenGate.shouldRetryEmpty(5), "give up after 5 empty cycles");
        check(NovaListenGate.showListeningChip(true, true, false, false, false, true), "chip stays on between restarts");
        check(!NovaListenGate.showListeningChip(true, true, false, true, false, true), "chip hides while she is speaking or thinking");
        check(!NovaListenGate.showListeningChip(true, true, true, false, false, true), "chip hides after the loop gives up");
        check(NovaListenGate.chipVisible(false, false, 1000, 1500) && !NovaListenGate.chipVisible(false, true, 1000, 1500),
                "a hide is debounced unless she starts talking");
        check(NovaListenGate.canRestart(true, true, false, false, false, false, false)
                && !NovaListenGate.canRestart(true, true, false, true, false, false, false)
                && !NovaListenGate.canRestart(true, true, false, false, true, false, false)
                && !NovaListenGate.canRestart(true, true, false, false, false, true, false)
                && !NovaListenGate.canRestart(true, true, true, false, false, false, false),
                "never restart while speaking, thinking, or after giving up");
        check(NovaListenGate.idleStatus(true, false).equals("Tap Mic to talk")
                && NovaListenGate.idleStatus(false, true).equals("Listening…"), "give-up status is Tap Mic to talk");
        // 0.10.68: a user Mic tap must toggle listening even while she is speaking.
        check(NovaMicTap.isToggleOff(true) && !NovaMicTap.isToggleOff(false), "a second Mic tap stops listening");
        check(NovaMicTap.cancelSpeechFirst(true, false, false, false, false)
                && NovaMicTap.cancelSpeechFirst(false, true, false, false, false)
                && NovaMicTap.cancelSpeechFirst(false, false, true, false, false)
                && NovaMicTap.cancelSpeechFirst(false, false, false, true, false)
                && NovaMicTap.cancelSpeechFirst(false, false, false, false, true)
                && !NovaMicTap.cancelSpeechFirst(false, false, false, false, false),
                "a Mic tap cancels speech, a Heart job, or a hangover first");
        check(NovaMicTap.allowStart(true, true, true, false, true, true, true, true, true)
                && !NovaMicTap.allowStart(true, false, true, false, false, false, false, false, false)
                && !NovaMicTap.allowStart(false, true, true, false, true, false, false, false, false)
                && !NovaMicTap.allowStart(false, true, true, false, false, false, false, false, true),
                "user tap starts while she is speaking; auto-restart still waits");
        check(NovaMicTap.fallbackBuiltin(false, true) && !NovaMicTap.fallbackBuiltin(true, true)
                && !NovaMicTap.fallbackBuiltin(false, false),
                "a failed start falls back to built-in listening when the pack is ready");
        check(NovaMicTap.buttonLabel(true).equals("Listening") && NovaMicTap.buttonLabel(false).equals("Mic")
                && NovaMicTap.startedStatus().equals("Listening…")
                && NovaMicTap.FAIL_FALLBACK.contains("built-in")
                && NovaMicTap.FAIL_RETRY.contains("Try again"),
                "Mic shows Listening while on, and a short message on start failure");

        check(NovaChatLayout.replyAboveAskRow() && !NovaChatLayout.replyInterceptsTouches()
                && NovaChatLayout.MIC_MIN_DP >= 48
                && NovaChatLayout.replyMaxLines(200) == 2 && NovaChatLayout.replyMaxLines(400) == 4
                && NovaChatLayout.chromeFits(240, 2),
                "pinned reply sits above the input and Mic stays at least 48 dp");
        check(NovaKidsTalk.SYSTEM.toLowerCase().contains("6-year-old")
                && NovaKidsTalk.SYSTEM.toLowerCase().contains("kind words")
                && NovaKidsTalk.SYSTEM.toLowerCase().contains("do not scold")
                && NovaKidsTalk.SYSTEM.contains("Do not refuse to help")
                && NovaLlmFilter.SYSTEM.equals(NovaKidsTalk.SYSTEM),
                "Kids persona is for a 6-year-old and stays patient if they are rude");
        check(NovaKidsTalk.isRude("shut up you idiot") && NovaKidsTalk.isRude("this is shit")
                && !NovaKidsTalk.isRude("hello nova"), "rude talk is detected");
        check(NovaKidsTalk.stripRude("shut up, what is 2 plus 2").contains("what is 2 plus 2")
                && !NovaKidsTalk.stripRude("this is shit").contains("shit"),
                "rude words are stripped so she can still help");
        String kind = NovaKidsTalk.withPatience("8 times 8 is 64.", true);
        check(kind.startsWith(NovaKidsTalk.KIND_WORDS) && kind.contains("64")
                && !kind.toLowerCase().contains("shit")
                && NovaKidsTalk.withPatience(NovaKidsTalk.STILL_HERE, true).startsWith(NovaKidsTalk.KIND_WORDS),
                "rude child gets a gentle redirect and still gets help");
        check(NovaKidsTalk.sanitizeReply("Don't say shit please").toLowerCase().indexOf("shit") < 0
                && NovaKidsTalk.mentionsFavouriteShow("I love Pokemon")
                && NovaKidsTalk.favouriteShowReply().toLowerCase().contains("pokémon"),
                "replies never repeat swears, and favourite-show talk is friendly");
        check(NovaHelp.KIDS_GREETING.contains("find a game")
                && !NovaHelp.KIDS_GREETING.toLowerCase().contains("tell you a joke")
                && !NovaKidsTalk.looksLikeJoke(NovaHelp.KIDS_GREETING)
                && NovaHelp.KIDS_FOOT.toLowerCase().contains("words"),
                "kids greeting is short and does not say tell you a joke");
        check(NovaIntent.confident("play time is finished", "play time")
                && !NovaIntent.confident("eight times eight", "play time")
                && NovaArith.answer("eight times eight").contains("64"),
                "playtime line stays for genuine playtime; times is arithmetic");

        check(NovaStartup.attachNovaAfterFirstFrame()
                && !NovaStartup.uiMayWaitOnEngineLoad()
                && !NovaStartup.loadHoldsLockDuringNative()
                && NovaStartup.WARMUP_TIMEOUT_MS >= 15_000L,
                "startup paints home first and never waits on Heart or the brain");
        int cycles = 0;
        boolean gaveUp = false;
        for (int i = 0; i < 8; i++) {
            long d = NovaListenGate.retryDelayMs(cycles);
            cycles = NovaListenGate.nextEmptyCycles(cycles, true);
            if (!NovaListenGate.shouldRetryEmpty(cycles)) { gaveUp = true; break; }
            check(d >= 300 && d <= 2000, "each backoff stays in range");
        }
        check(gaveUp && cycles == 5, "five silent restarts then Tap Mic to talk");

        // 0.10.63: built-in ASR path, diagnostics, no vibrate-from-duck, language fallback.
        check(NovaSpeechAvail.path(false, false, false, false, false) == NovaSpeechAvail.NEED_PERMISSION, "no mic permission is its own path");
        check(NovaSpeechAvail.path(false, false, false, true, true) == NovaSpeechAvail.USE_BUILTIN, "built-in wins when the system recognizer is missing");
        check(NovaSpeechAvail.path(true, true, false, true, true) == NovaSpeechAvail.USE_RECOGNIZER, "a system recognizer is still tried first");
        check(NovaSpeechAvail.path(false, true, false, false, true) == NovaSpeechAvail.USE_RECOGNIZER, "on-device system recognizer counts as a system path");
        check(NovaSpeechAvail.status(NovaSpeechAvail.NEED_PERMISSION, false, null).equals(NovaSpeechAvail.PERMISSION_DENIED), "permission denied wording");
        check(NovaSpeechAvail.status(NovaSpeechAvail.USE_BUILTIN, true, null).equals(NovaSpeechAvail.USING_BUILTIN), "listening with built-in wording");
        check(NovaSpeechAvail.status(NovaSpeechAvail.NEED_BUILTIN, false, null).equals(NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN), "need built-in wording");
        check(NovaSpeechAvail.NO_SERVICE_BUILTIN.contains("using built-in"), "using built-in status");
        check(NovaSpeechAvail.NO_SERVICE_NEED_BUILTIN.contains("about 75 MB"), "download size is in the offer");
        check(NovaSpeechAvail.showBuiltinButton(NovaSpeechAvail.NEED_BUILTIN, false)
                && !NovaSpeechAvail.showBuiltinButton(NovaSpeechAvail.NEED_BUILTIN, true), "offer download only when the pack is missing");
        check(NovaSpeechAvail.showInstallButton(NovaSpeechAvail.NEED_BUILTIN), "Play Store remains a secondary offer");
        check(!NovaListenPick.preferOfflineFirst(), "do not start with EXTRA_PREFER_OFFLINE");
        check(NovaListenPick.firstLanguage().equals("en-GB"), "first language is en-GB");
        check(NovaListenPick.nextLanguage("en-GB", true).equals("en-US")
                && NovaListenPick.nextLanguage("en-US", true) == null
                && NovaListenPick.nextLanguage("en-GB", false) == null, "en-US is the one language fallback");
        check(NovaListenPick.useBuiltinNow(false, true, false, false)
                && !NovaListenPick.useBuiltinNow(false, true, true, false)
                && NovaListenPick.useBuiltinNow(true, true, true, false)
                && !NovaListenPick.useBuiltinNow(true, false, false, false), "built-in is the fallback when there is no system service, or when forced");
        check(NovaListenPick.shouldFallToBuiltin(true, false, 1, false)
                && !NovaListenPick.shouldFallToBuiltin(false, false, 5, false)
                && NovaListenPick.shouldFallToBuiltin(true, false, 0, true)
                && !NovaListenPick.shouldFallToBuiltin(true, true, 5, true), "fall to built-in after one empty cycle or a hard error, not twice");
        check(NovaListenPick.shouldOfferBuiltin(false, false, false, 0)
                && !NovaListenPick.shouldOfferBuiltin(true, false, false, 0)
                && NovaListenPick.shouldOfferBuiltin(false, true, false, 1), "offer the pack when there is no service, or after one empty try");
        check(NovaListenPick.retryBusyAfterPause(false) && !NovaListenPick.retryBusyAfterPause(true), "busy mic retries once after pausing hidden listen");
        check(NovaAsrFiles.sizesOk(60_000_000L, 100_000L, 100_000L, 10L)
                && !NovaAsrFiles.sizesOk(50_000_000L, 100_000L, 100_000L, 10L)
                && !NovaAsrFiles.filesReadyAt(new java.io.File("/no/such/nova-asr")), "75 MB int8 pack size gate");
        check(NovaAsrFiles.SIZE_LABEL.contains("75 MB") && NovaAsrFiles.DOWNLOAD_BTN.contains("built-in listening"), "download copy");
        check(NovaAsrFiles.SIZE_LABEL_SMALL.contains("45 MB")
                && NovaAsrFiles.DOWNLOAD_BTN_SMALL.contains("45 MB")
                && NovaAsrFiles.ENCODER_MIN_SMALL == 20_000_000L
                && NovaAsrFiles.ENCODER_SMALL.endsWith(".int8.onnx")
                && NovaAsrFiles.minLength(NovaAsrFiles.ENCODER) >= 60_000_000L
                && NovaAsrFiles.minLength(NovaAsrFiles.ENCODER_SMALL) >= 20_000_000L, "smaller 45 MB pack is offered");
        check(NovaAsrGuard.namesMatch(NovaAsrFiles.ENCODER, NovaAsrFiles.DECODER, NovaAsrFiles.JOINER, NovaAsrFiles.TOKENS)
                && NovaAsrGuard.namesMatch(NovaAsrFiles.ENCODER_SMALL, NovaAsrFiles.DECODER_SMALL, NovaAsrFiles.JOINER_SMALL, NovaAsrFiles.TOKENS)
                && !NovaAsrGuard.namesMatch("encoder.onnx", NovaAsrFiles.DECODER, NovaAsrFiles.JOINER, NovaAsrFiles.TOKENS)
                && !NovaAsrGuard.namesMatch(NovaAsrFiles.ENCODER, NovaAsrFiles.DECODER, NovaAsrFiles.JOINER, "tokens"), "int8 encoder/decoder/joiner/tokens must match");
        check(NovaAsrGuard.canNativeLoad(true, false, false, true)
                && !NovaAsrGuard.canNativeLoad(true, true, false, true)
                && !NovaAsrGuard.canNativeLoad(true, false, true, true)
                && !NovaAsrGuard.canNativeLoad(false, false, false, true)
                && !NovaAsrGuard.canNativeLoad(true, false, false, false), "native load is skipped if files are missing, mid-load, or marked bad");
        check(NovaAsrGuard.numThreads(4096) == 1 && NovaAsrGuard.numThreads(8000) == 2
                && NovaAsrGuard.THREADS == 2 && NovaAsrGuard.LOAD_TIMEOUT_MS >= 15_000L, "1-2 threads and a load timeout");
        try {
            java.io.File tmp = java.io.File.createTempFile("nova-asr-guard", "dir");
            tmp.delete();
            check(tmp.mkdir(), "temp pack dir");
            check(!NovaAsrGuard.diedMidLoad(tmp) && !NovaAsrGuard.isMarkedBad(tmp), "fresh pack has no crash marks");
            check(NovaAsrGuard.writeMark(NovaAsrGuard.loadingMark(tmp)) && NovaAsrGuard.diedMidLoad(tmp), "loading marker is visible");
            NovaAsrGuard.clearMark(NovaAsrGuard.loadingMark(tmp));
            check(!NovaAsrGuard.diedMidLoad(tmp), "loading marker can be cleared");
            check(NovaAsrGuard.writeMark(NovaAsrGuard.badMark(tmp)) && NovaAsrGuard.isMarkedBad(tmp), "bad marker skips the pack");
            check(NovaAsrFiles.usableDir(new java.io.File("/no/such/files-dir")) == null, "missing packs are not usable");
            NovaAsrGuard.clearMark(NovaAsrGuard.badMark(tmp));
            tmp.delete();
        } catch (Exception e) {
            throw new RuntimeException("ASR guard file marks: " + e, e);
        }
        check(Math.abs(NovaListenDiag.meterFromRmsDb(-10f)) < 1e-4
                && NovaListenDiag.meterFromRmsDb(12f) == 1f
                && NovaListenDiag.meterFromRmsDb(1f) > 0.4f, "speech RMS dB maps into 0..1");
        check(NovaListenDiag.meterFromPcmRms(0f) == 0f && NovaListenDiag.meterFromPcmRms(0.2f) == 1f
                && NovaListenDiag.meterFromPcmRms(0.05f) > 0.3f, "PCM RMS is scaled for the meter");
        check(NovaListenDiag.line(null, false, false, true).equals(NovaSpeechAvail.PERMISSION_DENIED)
                && NovaListenDiag.line("", true, true, false).equals(NovaSpeechAvail.USING_BUILTIN), "diag line prefers permission and built-in");
        check(!NovaListenBeepGate.allowZeroVolumeDuck(), "never duck by setting a stream to volume 0");
        check(NovaListenBeepGate.modeForDuck() == NovaListenBeepGate.MODE_SILENT, "duck uses silent, not vibrate");
        check(NovaListenBeepGate.mustRevert(NovaListenBeepGate.MODE_NORMAL, NovaListenBeepGate.MODE_VIBRATE)
                && !NovaListenBeepGate.mustRevert(NovaListenBeepGate.MODE_VIBRATE, NovaListenBeepGate.MODE_VIBRATE)
                && !NovaListenBeepGate.mustRevert(NovaListenBeepGate.MODE_NORMAL, NovaListenBeepGate.MODE_SILENT), "revert if a duck would leave the phone in vibrate");
        check(NovaListenBeepGate.duckSucceeded(NovaListenBeepGate.MODE_SILENT)
                && !NovaListenBeepGate.duckSucceeded(NovaListenBeepGate.MODE_VIBRATE)
                && !NovaListenBeepGate.duckSucceeded(NovaListenBeepGate.MODE_NORMAL), "only silent counts as a successful duck");

        // 0.10.64: Chat always offers built-in listening; busy/silent system rec falls over sooner.
        check(NovaAppVer.NAME.equals("0.10.71") && NovaAppVer.CODE == 171, "shipping version is 0.10.71");
        check(NovaAppVer.label().equals("CodeAque 0.10.71")
                && NovaAppVer.label("0.10.71").equals("CodeAque 0.10.71")
                && NovaAppVer.label(null).equals("CodeAque 0.10.71")
                && NovaAppVer.label("?").equals("CodeAque 0.10.71"), "version label for Chat and Nova's tab");
        check(NovaListenPick.showBuiltinButton(false, false)
                && !NovaListenPick.showBuiltinButton(true, false)
                && !NovaListenPick.showBuiltinButton(false, true), "Get built-in listening stays visible until the pack is ready or downloading");
        check(NovaListenPick.EMPTY_BEFORE_BUILTIN == 1
                && NovaListenPick.shouldFallToBuiltin(true, false, 1, false, false)
                && !NovaListenPick.shouldFallToBuiltin(true, false, 0, false, false)
                && NovaListenPick.shouldFallToBuiltin(true, false, 0, false, true), "one empty result or a busy mic is enough to switch to built-in");
        check(NovaListenPick.switchBusyToBuiltin(true, false, true)
                && !NovaListenPick.switchBusyToBuiltin(true, false, false)
                && !NovaListenPick.switchBusyToBuiltin(false, false, true)
                && !NovaListenPick.switchBusyToBuiltin(true, true, true), "after the busy retry, switch to built-in if it is downloaded and not already in use");
        check(!NovaListenPick.preferOnDeviceFirst(), "do not start with the on-device system recognizer");
        check(NovaSpeechAvail.serviceAlreadyInstalled(true, false, false)
                && NovaSpeechAvail.serviceAlreadyInstalled(false, false, true)
                && !NovaSpeechAvail.serviceAlreadyInstalled(false, false, false), "Speech Recognition & Synthesis already installed counts as a service");
        check(NovaSpeechAvail.showInstallButton(NovaSpeechAvail.NEED_BUILTIN, false)
                && !NovaSpeechAvail.showInstallButton(NovaSpeechAvail.NEED_BUILTIN, true)
                && !NovaSpeechAvail.showInstallButton(NovaSpeechAvail.USE_RECOGNIZER, false), "hide Get speech service when the Google service is already on the phone");
        check(NovaSpeakGate.keepHeart(true) && !NovaSpeakGate.keepHeart(false), "once Heart is downloaded, keep it");
        check(!NovaSpeakGate.allowSystemTts(true) && NovaSpeakGate.allowSystemTts(false), "never mix in the phone's voice after Heart is on the phone");
        check(NovaSpeakGate.speakWaitMs(true) == NovaSpeakGate.HEART_WAIT_MS
                && NovaSpeakGate.HEART_WAIT_MS >= 30000L
                && NovaSpeakGate.speakWaitMs(false) == 12000L, "Heart gets a longer wait than the 12 s reply timer");
        check(NovaSpeakGate.extendHeartWait(true, false, true, false)
                && !NovaSpeakGate.extendHeartWait(true, true, true, false)
                && !NovaSpeakGate.extendHeartWait(true, false, true, true)
                && !NovaSpeakGate.extendHeartWait(false, false, true, false), "extend a slow Heart load once; never cut a started Heart line over to the phone");

        // 0.10.65: instant canned greeting, short replies, real mic meter, built-in default, 8s listen.
        check(NovaAppVer.NAME.equals("0.10.71") && NovaAppVer.CODE == 171, "shipping version is 0.10.71");
        check(NovaConvo.statusLine(NovaConvo.SPEAKING, null).equals(NovaConvo.SPEAKING_TEXT)
                && NovaConvo.statusLine(NovaConvo.SPEAKING, "").equals(NovaReplyQuick.SPEAKING_TEXT)
                && NovaConvo.statusLine(NovaConvo.THINKING, "hello").equals(NovaConvo.THINKING_TEXT)
                && !NovaConvo.statusLine(NovaConvo.SPEAKING, null).equals(NovaConvo.THINKING_TEXT), "while she talks the status is Speaking, never Thinking");
        check(NovaReplyQuick.isSimpleGreeting("Hello")
                && NovaReplyQuick.isSimpleGreeting("hello. hello.")
                && NovaReplyQuick.isSimpleGreeting("Hi")
                && NovaReplyQuick.isSimpleGreeting("Hey Nova")
                && NovaReplyQuick.isSimpleGreeting("o hallo")
                && !NovaReplyQuick.isSimpleGreeting("find a game")
                && !NovaReplyQuick.isSimpleGreeting("u how were you")
                && !NovaReplyQuick.isSimpleGreeting("hello how are you")
                && !NovaReplyQuick.isSimpleGreeting(""), "simple spoken greetings get a canned reply");
        check(NovaSmallTalk.stripFillers("uh, how are you?").equals("how are you")
                && NovaSmallTalk.stripFillers("u how were you").equals("how were you")
                && NovaSmallTalk.tidy("u how were you").equals("how are you")
                && NovaSmallTalk.reply("Uh, how are you?", true).equals(NovaSmallTalk.HOW_ARE_KIDS)
                && NovaSmallTalk.reply("what's your name", true).equals(NovaSmallTalk.NAME_KIDS)
                && NovaSmallTalk.reply("what can you do", true).equals(NovaSmallTalk.CAN_DO_KIDS)
                && NovaSmallTalk.reply("thanks", true).equals(NovaSmallTalk.THANKS_KIDS)
                && NovaSmallTalk.reply("bye", true).equals(NovaSmallTalk.BYE_KIDS)
                && NovaSmallTalk.reply("o hallo", true).equals(NovaSmallTalk.HELLO_KIDS)
                && NovaSmallTalk.reply("find a racing game", true) == null, "small talk after fillers, not a greeting swallow");
        check(NovaSmallTalk.brainFallback("u how were you", true).contains("isn't ready")
                && !NovaSmallTalk.brainFallback("u how were you", true).contains("\"")
                && !NovaSmallTalk.brainFallback("u how were you", true).contains("Hello there")
                && !NovaSmallTalk.brainFallback("u how were you", true).contains("or ask me about a game")
                && NovaSmallTalk.isBareGreetingReply("Hello there! I'm Nova.")
                && !NovaSmallTalk.isBareGreetingReply("I'm really well, thank you! How are you?"),
                "brain fallback is short, has no quotes, and is not the greeting");
        check(NovaGameTalk.stripLeadingNoise("o tell me about roe box").equals("tell me about roe box")
                && NovaGameTalk.stripLeadingNoise("ut robecks").equals("robecks")
                && NovaGameTalk.cleanHeard("o tell me about roe box").equals("tell me about Roblox")
                && NovaGameTalk.cleanHeard("ut robecks").equals("Roblox")
                && NovaGameTalk.cleanHeard("tell me about mine craft").equals("tell me about Minecraft")
                && NovaGameTalk.cleanHeard("row blocks").equals("Roblox")
                && NovaGameTalk.matchName("robecks").equals("roblox")
                && NovaGameTalk.matchName("roe box").equals("roblox")
                && NovaGameTalk.matchName("mine craft").equals("minecraft"),
                "leading ASR noise is stripped and misheard game names are matched");
        String roblox = NovaGameTalk.reply("o tell me about roe box", true);
        check(roblox != null && roblox.contains("Roblox") && !roblox.contains("thinking brain")
                && NovaGameTalk.reply("tell me about Roblox", true).contains("Roblox")
                && NovaGameTalk.reply("ut robecks", true).contains("Roblox")
                && NovaGameTalk.reply("what is mine craft", true).contains("Minecraft")
                && NovaGameTalk.reply("how are you", true) == null,
                "Tell me about Roblox has a built-in answer with no brain");
        check(NovaConvo.brainReadyText(-1).equals("Getting my brain ready…")
                && NovaConvo.brainReadyText(40).equals("Getting my brain ready… 40%")
                && NovaConvo.brainReadyText(100).equals("Getting my brain ready…")
                && NovaConvo.BRAIN_OFFER.contains("Nova tab"),
                "brain-ready status uses a real download percent, never a fake load percent");
        check(NovaConvo.spokenMatchesScreen("Roblox is a game.", "Roblox is a game.")
                && !NovaTtsNorm.forSpeak("I heard you say \"o tell me about roe box\".").contains("\"")
                && NovaTtsNorm.forSpeak("I heard you say \"o tell me about roe box\".").contains("roe box"),
                "quotes are dropped for TTS so quoted words are not skipped");
        check(NovaReplyQuick.firstSentence("I heard you say \"o tell me about roe box\". Next.").equals("I heard you say \"o tell me about roe box\".")
                && NovaReplyQuick.afterFirst("I heard you say \"o tell me about roe box\". Next.").equals("Next."),
                "first sentence does not split on a period inside quotes");
        check(NovaTtsNorm.forSpeak("Hello there! I’m Nova.").contains("I'm")
                && NovaTtsNorm.keepsIm("Hello there! I’m Nova.")
                && NovaTtsNorm.forSpeak("don’t").equals("don't"),
                "curly apostrophes are straightened so I'm is spoken");
        {
            NovaEndpoint ep = new NovaEndpoint();
            ep.start(0);
            for (int i = 0; i < 8; i++) ep.onLevel(0.05f, i * 50L);
            check(!ep.isSpeech(0.05f), "room noise at the floor is not speech");
            ep.onLevel(0.4f, 500);
            check(ep.heardSpeech(), "a voice peak is speech");
            check(ep.decision(1000) == NovaEndpoint.CONTINUE, "do not cut 0.5s after speech");
            check(ep.decision(500 + NovaEndpoint.TAIL_MS) == NovaEndpoint.END_SILENCE, "stop 1.2s after last speech");
            check(ep.meter(0.05f) < ep.meter(0.4f), "meter moves with live level");
            NovaEndpoint silent = new NovaEndpoint();
            silent.start(0);
            for (int i = 0; i < 20; i++) silent.onLevel(0.02f, i * 100L);
            check(!silent.reallySilent(800), "do not warn in the first second");
            check(silent.reallySilent(2000), "all-quiet capture may warn after a real window");
            silent.onLevel(0.5f, 2100);
            check(!silent.reallySilent(4000), "a moving level is not a silent mic");
        }
        check(NovaMicTap.restartInsteadOfToggle(true, true, false, false, false)
                && NovaMicTap.restartInsteadOfToggle(false, true, false, false, false)
                && !NovaMicTap.restartInsteadOfToggle(true, false, false, false, false),
                "a Mic tap while she is speaking starts listening, it does not toggle off");
        check(NovaReplyQuick.cannedHello(true).startsWith("Hi!")
                && NovaReplyQuick.cannedHello(false).startsWith("Hi!"), "canned hello is instant and short");
        check(NovaReplyQuick.thinkingText(0).equals("Thinking… 0s")
                && NovaReplyQuick.thinkingText(3500).equals("Thinking… 3s"), "thinking shows elapsed seconds");
        check(NovaReplyQuick.firstSentence("Hello there. How can I help you today?").equals("Hello there.")
                && NovaReplyQuick.afterFirst("Hello there. How can I help you today?").equals("How can I help you today?")
                && NovaReplyQuick.afterFirst("Just one line!").isEmpty(), "the first sentence streams to TTS first");
        check(NovaReplyQuick.MAX_TOKENS <= 40 && NovaReplyQuick.MAX_TOKENS >= 20, "on-device replies stay short");
        check(NovaReplyQuick.LISTEN_WINDOW_MS == 15000L
                && NovaReplyQuick.SPEECH_TAIL_MS == 1200L
                && NovaReplyQuick.LISTEN_MAX_MS == 15000L, "listen while he talks, stop 1.2s after speech, max 15s");
        check(NovaReplyQuick.isSilentRms(0.001f) && !NovaReplyQuick.isSilentRms(0.05f)
                && NovaReplyQuick.allZeros(0, 16000) && NovaReplyQuick.allZeros(3, 8000)
                && !NovaReplyQuick.allZeros(200, 8000)
                && !NovaReplyQuick.allZeros(0, 16000, true), "all-zero AudioRecord is flagged only when there was no voice");
        check(NovaReplyQuick.NO_SOUND.contains("no sound") && NovaReplyQuick.NO_SOUND.contains("Another app"), "no-sound wording");
        check(!NovaListenPick.preferBuiltinWhenReady(true)
                && !NovaListenPick.useBuiltinNow(false, true, true, true)
                && NovaListenPick.useBuiltinNow(false, true, false, false), "prefer the system recognizer when it can return text; built-in is the fallback");

        check(NovaAsrText.tidy("S EIGHT TIMES EH").equals("s eight times eight"), "built-in ASR text is lowercased and number slips are tidied");
        check(NovaAsrText.tidy("  What IS   Eight  ").equals("what is eight"), "extra spaces are collapsed");
        check(NovaIntent.containsPhrase("eight times eight", "times")
                && !NovaIntent.containsPhrase("eight times eight", "time")
                && NovaIntent.containsPhrase("time is up", "time is up"), "intents match whole words and phrases only");
        check(!NovaIntent.confident("eight times eight", "time", "times up", "finished", "limit")
                && NovaIntent.confident("my time is up", "time is up", "play time")
                && !NovaIntent.confident("what is eight times eight", "time"), "playtime canned line needs a confident phrase, not the word times");
        check(NovaHelp.cannedOrNull("eight times eight", true) == null
                && NovaHelp.cannedOrNull("what is eight times eight", true) == null
                && NovaHelp.cannedOrNull("is my play time finished", true) != null
                && NovaHelp.cannedOrNull("is my play time finished", true).contains("play time is finished"), "eight times eight is not a playtime question");
        check(NovaArith.looksLike("eight times eight")
                && NovaArith.answer("eight times eight").contains("64")
                && NovaArith.answer("what is eight times eight").contains("64")
                && NovaArith.answer("S EIGHT TIMES EH").contains("64")
                && NovaArith.answer("what's 5 plus 3").contains("8")
                && !NovaArith.looksLike("tell me a joke"), "spoken arithmetic answers 64");

        check(NovaSpeakGate.allowInstantPhone(true, false)
                && !NovaSpeakGate.allowInstantPhone(true, true)
                && NovaSpeakGate.allowInstantPhone(false, false), "the greeting may use the phone voice only while Heart is still loading");

        // 0.10.66: category strip is a fixed-height, pixel-aligned row (Kids and adult).
        check(NovaAppVer.NAME.equals("0.10.71") && NovaAppVer.CODE == 171, "shipping version is 0.10.71");
        check(HomeTabs.snap(10.4f) == 10f && HomeTabs.snap(10.5f) == 11f, "tab Y snaps to the pixel grid");
        check(HomeTabs.iconY(720) == HomeTabs.snap(720 * HomeTabs.ICON_Y)
                && HomeTabs.stripTop(720) == HomeTabs.snap(720 * HomeTabs.STRIP_TOP)
                && HomeTabs.stripBot(720) == HomeTabs.snap(720 * HomeTabs.STRIP_BOT), "row edges are whole pixels");
        check(HomeTabs.labelY(100f, 1f) == HomeTabs.labelY(100f, 1f)
                && HomeTabs.iconSize(1.5f) == HomeTabs.iconSize(1.5f)
                && !HomeTabs.selectionScalesIcon()
                && !HomeTabs.selectionChangesLabelSize()
                && !HomeTabs.twoLineLabels(), "icon and label size do not change with selection, and labels stay on one line");
        check(HomeTabs.needsLayout(8, 1280, 1f, 8, 1280, 1f, false) == false
                && HomeTabs.needsLayout(8, 1280, 1f, 8, 1280, 1f, true)
                && HomeTabs.needsLayout(8, 1280, 1f, 8, 1279, 1f, false)
                && HomeTabs.needsLayout(9, 1280, 1f, 8, 1280, 1f, false)
                && !HomeTabs.needsLayout(8, 1280, 1f, 8, 1280, 1.00001f, false), "tab widths are not remeasured on a scroll frame");
        check(HomeTabs.LABEL == 16f && HomeTabs.ICON == 36f, "fixed icon and label sizes for Kids and adult");












    }

}
