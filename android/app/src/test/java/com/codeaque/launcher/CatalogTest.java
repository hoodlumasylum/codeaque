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
        check(Catalog.indexOf(c, "allapps") == c.size() - 2 && c.get(c.size() - 1).id.equals("settings"), "All Apps before Settings");
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
        check(c.get(0).items.get(1).label.equals("Roblox") && c.get(0).items.get(2).label.equals("Minecraft"), "Roblox, Minecraft next");
        check("com.roblox.client".equals(c.get(0).items.get(1).storePackage()) && "com.mojang.minecraftpe".equals(c.get(0).items.get(2).storePackage()), "featured packages");
        check(c.get(0).items.get(3) == Catalog.PLAY_STORE && "com.android.vending".equals(Catalog.PLAY_STORE.storePackage()), "Play Store third on Featured");
        check(c.get(0).items.get(4) == Catalog.GEFORCE_NOW && Catalog.GEFORCE_NOW.webUrl == null && "com.nvidia.geforcenow".equals(Catalog.GEFORCE_NOW.storePackage()), "GeForce NOW fourth, Play page fallback");
        check(Catalog.GFN_WEB.equals("https://play.geforcenow.com") && Catalog.gfnInBrowser(true) && !Catalog.gfnInBrowser(false), "GFN web on handhelds");
        check(c.get(0).items.get(1).webUrl == null && c.get(0).items.get(2).webUrl == null, "featured fall back to the Play Store");
        List<Catalog.Item> kids = c.get(Catalog.indexOf(c, "kids")).items;
        check(kids.size() >= 8 && kids.get(0) == Catalog.KIDS[0], "Kids row");
        for (Catalog.Item i : kids) check(i.webUrl == null && i.storePackage() != null && !i.isAction() && !i.icon.contains("logo"), i.label + " is a Play Store app tile");
        check(Rotation.next("off").equals("0") && Rotation.next("3").equals("off") && Rotation.next(null).equals("0"), "rotation cycle");
        check(Rotation.cmd("off") == null && Rotation.cmd("2") == null && Rotation.cmd("0;reboot") == null
                && Rotation.cmd("1").equals("settings put system accelerometer_rotation 0; settings put system user_rotation 1; wm fixed-to-user-rotation enabled; wm user-rotation lock 1; wm set-ignore-orientation-request true || cmd window set-ignore-orientation-request true"), "rotation cmd");
        check(Rotation.suggest("Anbernic", "RG353M", "rk30board", "rk3566_r") && !Rotation.suggest("Google", "Pixel 7", "panther", "panther"), "rotation suggest");
        List<Catalog.Category> km = Catalog.kidsMode();
        check(km.size() == 7 && km.get(5).id.equals("kthemes") && km.get(3).id.equals("kdc") && km.get(3).items.get(0) == Catalog.DREAMCAST && km.get(4).id.equals("kpsp") && km.get(4).items.get(0) == Catalog.PSP && km.get(6).items.size() == 2 && km.get(6).items.get(0) == Catalog.KIDS_WIFI && km.get(6).items.get(1) == Catalog.KIDS_UPDATE && "update".equals(Catalog.KIDS_UPDATE.action) && "wifi".equals(Catalog.KIDS_WIFI.action) && km.get(0).items.get(0) == Catalog.ROBLOX && !km.get(0).items.contains(Catalog.GEFORCE_NOW) && km.get(0).items.contains(Catalog.MARVEL_RIVALS) && km.get(2).items.get(0) == Catalog.PLAY_STORE, "kids mode rows");
        check(km.get(1).items.size() == Catalog.KIDS.length, "kids mode games");
        for (Catalog.Category k : km) for (Catalog.Item i : k.items) check((!i.isAction() || i.action.startsWith("gfn:") || i.action.equals("wifi") || i.action.equals("update") || i.action.equals("kthemes")) && (!i.isRom() || Catalog.isDreamcastRom(i)), "kids mode has only games: " + i.label);
        playArtChecks();
        check(c.get(0).items.get(5) == Catalog.MARVEL_RIVALS && km.get(0).items.contains(Catalog.MARVEL_RIVALS), "Marvel Rivals on Featured and Kids Play");
        check(Catalog.RIVALS_GFN.equals("https://play.geforcenow.com/games?game-id=4b44eb06-3d68-4ca0-92b8-ce4ffd20dff4&utm_source=codeaque&utm_campaign=launcher"), "GFN deep link");
        check(Catalog.RIVALS_HEADER.startsWith("https://") && Catalog.RIVALS_HEADER.contains("/2767030/"), "Steam header");
        check("Home".equals(Catalog.cleanSsid("\"Home\"")) && Catalog.cleanSsid("<unknown ssid>") == null && Catalog.cleanSsid(null) == null, "ssid");
        dreamcastChecks(c);
        UpdateInfo ui = UpdateInfo.parse("{\"tag_name\":\"v0.10.6\",\"body\":\"## CodeAque\\r\\n\\r\\n- **Pop-up** for \\\"updates\\\"\\n- more\",\"assets\":[{\"browser_download_url\":\"https://x/codeaque-launcher.apk\"},{\"browser_download_url\":\"https://x/SHA256SUMS.txt\"}]}");
        check(ui != null && "0.10.6".equals(ui.version) && "Pop-up for \"updates\"".equals(UpdateInfo.whatsNew(ui.notes)), "update notes line: " + (ui == null ? null : UpdateInfo.whatsNew(ui.notes)));
        check(UpdateInfo.whatsNew("").isEmpty() && UpdateInfo.whatsNew(null).isEmpty(), "no notes");
        Catalog.Item newGame = Catalog.deviceApp("New Game", "com.example.newgame", "com.example.newgame.Main");
        List<Catalog.Category> ka = Catalog.kidsMode(List.of(), List.of(newGame));
        check(ka.get(1).id.equals("kadded") && ka.get(1).items.get(0) == newGame && Catalog.indexOf(km, "kadded") < 0, "My New Games row only when there are some");
        check(Catalog.newPackages(new java.util.HashSet<>(List.of("a", "b")), new java.util.HashSet<>(List.of("a", "b", "d", "c"))).equals(List.of("c", "d")), "installed-since diff");
        // 0.10.1 boot animation
        List<String> sa = new java.util.ArrayList<>(); for (Catalog.Item i : c.get(Catalog.indexOf(c, "settings")).items) sa.add(i.action);
        check(sa.contains("kids") && sa.contains("bootanim") && sa.indexOf("bootanim") == sa.indexOf("update") + 1, "boot animation after Update");
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
                else { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                ex.sendResponseHeaders(200, body.length); ex.getResponseBody().write(body); ex.close();
            });
            srv.createContext("/img/", ex -> { byte[] b = ex.getRequestURI().getPath().endsWith("icon") ? new byte[]{1, 2, 3} : new byte[]{4, 5, 6, 7};
                ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
            srv.start();
            java.io.File dir = java.nio.file.Files.createTempDirectory("playart").toFile();
            check(PlayArt.fetch(base, "game.ok", dir) && PlayArt.iconFile(dir, "game.ok").length() == 3 && PlayArt.featureFile(dir, "game.ok").length() == 4, "icon + feature graphic cached");
            check(PlayArt.fetch(base, "game.iconly", dir) && !PlayArt.featureFile(dir, "game.iconly").exists(), "foreign feature graphic refused, icon kept");
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
            check(PlayArt.listingUrl(PlayArt.PLAY, "com.roblox.client").equals("https://play.google.com/store/apps/details?id=com.roblox.client&hl=en_GB"), "listing url");
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
                    check(!h1.contains("4821") && !h1.equals(h2) && h1.matches("[0-9a-f]{32}:[0-9a-f]{64}"), "salted hash " + h1);
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
                    check(ids.equals(new java.util.HashSet<>(java.util.List.of("kplay", "kadded", "kgames", "kstore", "kdc", "kpsp", "kthemes", "kwifi"))), "kids rows exact " + ids);
                    Catalog.Category added = kk.get(Catalog.indexOf(kk, "kadded"));
                    check(added.items.size() == 1 && added.items.get(0) == game, "only the game stays in My New Games");
                    check(Catalog.kidsBlocked(Catalog.act("All apps", "grid", "allapps")) && Catalog.streamingPkg("tv.twitch.android.app") && Catalog.streamingPkg("com.zhiliaoapp.musically") && Catalog.streamingPkg("tv.pluto.android"), "blocked");
                    for (Catalog.Item k : Catalog.KIDS) check(!Catalog.kidsBlocked(k), "kids game allowed " + k.label);
                    for (Catalog.Item k : new Catalog.Item[]{Catalog.ROBLOX, Catalog.MINECRAFT, Catalog.MARVEL_RIVALS, Catalog.PLAY_STORE, Catalog.GEFORCE_NOW}) check(!Catalog.kidsBlocked(k), "allowed " + k.label);
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
                check(k2.get(Catalog.indexOf(k2, "kpsp")).items.size() == 4 && !k2.get(Catalog.indexOf(k2, "kpsp")).items.contains(Catalog.PSP), "kids psp games only");
                List<Catalog.Category> n2 = Catalog.categories(new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>(), pr2, false, false);
                check(Catalog.indexOf(n2, "psp") > Catalog.indexOf(n2, "dreamcast"), "psp after dreamcast");
            }
            List<Catalog.Category> km = Catalog.kidsMode(roms);
            check(km.get(Catalog.indexOf(km, "kdc")).items.size() == 4 && !km.get(Catalog.indexOf(km, "kdc")).items.contains(Catalog.DREAMCAST), "kids mode dreamcast games only");
            for (Catalog.Category k : km) for (Catalog.Item i : k.items) check(!"romfolder".equals(i.action) && !"files".equals(i.action) && !"filemgr".equals(i.action), "no import in kids mode");
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
    }
}
