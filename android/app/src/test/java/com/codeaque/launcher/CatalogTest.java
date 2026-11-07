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
        check(c.get(0).items.get(0).label.equals("Roblox") && c.get(0).items.get(1).label.equals("Minecraft"), "Roblox, Minecraft first");
        check("com.roblox.client".equals(c.get(0).items.get(0).storePackage()) && "com.mojang.minecraftpe".equals(c.get(0).items.get(1).storePackage()), "featured packages");
        check(c.get(0).items.get(2) == Catalog.PLAY_STORE && "com.android.vending".equals(Catalog.PLAY_STORE.storePackage()), "Play Store third on Featured");
        check(c.get(0).items.get(3) == Catalog.GEFORCE_NOW && Catalog.GEFORCE_NOW.webUrl == null && "com.nvidia.geforcenow".equals(Catalog.GEFORCE_NOW.storePackage()), "GeForce NOW fourth, Play page fallback");
        check(Catalog.GFN_WEB.equals("https://play.geforcenow.com") && Catalog.gfnInBrowser(true) && !Catalog.gfnInBrowser(false), "GFN web on handhelds");
        check(c.get(0).items.get(0).webUrl == null && c.get(0).items.get(1).webUrl == null, "featured fall back to the Play Store");
        List<Catalog.Item> kids = c.get(Catalog.indexOf(c, "kids")).items;
        check(kids.size() >= 8 && kids.get(0) == Catalog.KIDS[0], "Kids row");
        for (Catalog.Item i : kids) check(i.webUrl == null && i.storePackage() != null && !i.isAction() && !i.icon.contains("logo"), i.label + " is a Play Store app tile");
        check(Rotation.next("off").equals("0") && Rotation.next("3").equals("off") && Rotation.next(null).equals("0"), "rotation cycle");
        check(Rotation.cmd("off") == null && Rotation.cmd("2") == null && Rotation.cmd("0;reboot") == null
                && Rotation.cmd("1").equals("settings put system accelerometer_rotation 0; settings put system user_rotation 1; wm fixed-to-user-rotation enabled; wm user-rotation lock 1; wm set-ignore-orientation-request true || cmd window set-ignore-orientation-request true"), "rotation cmd");
        check(Rotation.suggest("Anbernic", "RG353M", "rk30board", "rk3566_r") && !Rotation.suggest("Google", "Pixel 7", "panther", "panther"), "rotation suggest");
        List<Catalog.Category> km = Catalog.kidsMode();
        check(km.size() == 5 && km.get(3).id.equals("kdc") && km.get(3).items.get(0) == Catalog.DREAMCAST && km.get(4).items.size() == 2 && km.get(4).items.get(0) == Catalog.KIDS_WIFI && km.get(4).items.get(1) == Catalog.KIDS_UPDATE && "update".equals(Catalog.KIDS_UPDATE.action) && "wifi".equals(Catalog.KIDS_WIFI.action) && km.get(0).items.get(0) == Catalog.ROBLOX && km.get(0).items.contains(Catalog.GEFORCE_NOW) && km.get(2).items.get(0) == Catalog.PLAY_STORE, "kids mode rows");
        check(km.get(1).items.size() == Catalog.KIDS.length, "kids mode games");
        for (Catalog.Category k : km) for (Catalog.Item i : k.items) check((!i.isAction() || i.action.startsWith("gfn:") || i.action.equals("wifi") || i.action.equals("update")) && (!i.isRom() || Catalog.isDreamcastRom(i)), "kids mode has only games: " + i.label);
        playArtChecks();
        check(c.get(0).items.get(4) == Catalog.MARVEL_RIVALS && km.get(0).items.contains(Catalog.MARVEL_RIVALS), "Marvel Rivals on Featured and Kids Play");
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
        check(Palette.next("neon").equals("powerup") && Palette.next("powerup").equals("catch") && Palette.next("catch").equals("christmas") && Palette.next("christmas").equals("neon"), "theme picker cycles");
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
            check(d.items.size() == 5 && d.items.get(0) == Catalog.DREAMCAST, "dreamcast tiles " + d.items.size());
            List<Catalog.Category> km = Catalog.kidsMode(roms);
            check(km.get(Catalog.indexOf(km, "kdc")).items.size() == 5, "kids mode dreamcast games");
            for (Catalog.Category k : km) for (Catalog.Item i : k.items) check(!"romfolder".equals(i.action) && !"files".equals(i.action), "no import in kids mode");
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
