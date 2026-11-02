package com.codeaque.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** What the launcher shows. Plain Java (no Android classes) so it can be unit-tested on a PC.
 *  Only text names and original generic icons: no third-party logos. Nothing is bundled. */
public final class Catalog {

    /** One tile. It opens the first installed package; if none is installed it opens webUrl
     *  (cloud services that work in a browser) or offers the Play Store page for storePackage. */
    public static final class Item {
        public final String label, icon, webUrl, action;
        public final List<String> packages;
        /** 0.9.4: an app found on the device (pkg + activity class, its own icon is drawn) or a ROM file the user picked (uri). */
        public final String pkg, cls, uri;
        public Item(String label, String icon, String[] packages, String webUrl, String action) {
            this(label, icon, packages, webUrl, action, null, null, null);
        }
        public Item(String label, String icon, String[] packages, String webUrl, String action, String pkg, String cls, String uri) {
            this.label = label; this.icon = icon; this.webUrl = webUrl; this.action = action;
            this.packages = Collections.unmodifiableList(Arrays.asList(packages));
            this.pkg = pkg; this.cls = cls; this.uri = uri;
        }
        public boolean isDeviceApp() { return pkg != null && cls != null; }
        public boolean isRom() { return uri != null; }
        public String storePackage() { return packages.isEmpty() ? null : packages.get(0); }
        public boolean isAction() { return action != null; }
        public String key() { return isDeviceApp() ? pkg + "/" + cls : isRom() ? uri : label; }
    }

    public static final class Category {
        public final String id, label, icon;
        public final List<Item> items;
        Category(String id, String label, String icon, Item... items) {
            this.id = id; this.label = label; this.icon = icon;
            this.items = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
        }
    }

    static Item app(String label, String icon, String... pkgs) { return new Item(label, icon, pkgs, null, null); }
    static Item web(String label, String icon, String url, String... pkgs) { return new Item(label, icon, pkgs, url, null); }
    static Item act(String label, String icon, String action) { return new Item(label, icon, new String[0], null, action); }

    // Cloud gaming: the app if it is installed, otherwise the official website in the browser.
    static final Item XBOX = web("Xbox Cloud Gaming", "cloud", "https://www.xbox.com/play", "com.gamepass");
    static final Item GFN = web("GeForce NOW", "cloud", "https://play.geforcenow.com", "com.nvidia.geforcenow");
    static final Item LUNA = web("Amazon Luna", "cloud", "https://luna.amazon.com", "com.amazon.luna");
    static final Item BOOST = web("Boosteroid", "cloud", "https://cloud.boosteroid.com", "com.boosteroid.streaming");
    static final Item RETROARCH = app("RetroArch", "invader", "com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32");
    static final Item MINECRAFT = app("Minecraft", "cube", "com.mojang.minecraftpe");
    static final Item ROBLOX = app("Roblox", "cube", "com.roblox.client");

    /** An installed app or game (from PackageManager): opens itself, shows its own icon. */
    public static Item deviceApp(String label, String pkg, String cls) {
        return new Item(label, "grid", new String[]{pkg}, null, null, pkg, cls, null);
    }

    /** A game file from a folder the user chose (Android's folder picker); opened with an installed emulator. */
    public static Item rom(String name, String uri) {
        return new Item(stripExt(name), "cart", new String[0], null, null, null, null, uri);
    }

    /** File types emulators open. Only files the user already has are listed; nothing is downloaded. */
    static final String[] ROM_EXT = {"nes", "sfc", "smc", "gb", "gbc", "gba", "nds", "n64", "z64", "v64", "md", "gen", "smd", "sms", "gg",
            "pce", "a26", "a78", "lnx", "iso", "cue", "chd", "cso", "pbp", "gdi", "cdi", "rvz", "gcm", "wbfs", "zip", "7z", "3ds", "ws", "wsc", "ngp"};

    public static boolean isRomFile(String name) {
        int d = name.lastIndexOf('.');
        if (d < 0 || name.startsWith(".")) return false;
        String e = name.substring(d + 1).toLowerCase(java.util.Locale.ROOT);
        for (String x : ROM_EXT) if (x.equals(e)) return true;
        return false;
    }

    static String stripExt(String n) { int d = n.lastIndexOf('.'); return d > 0 ? n.substring(0, d) : n; }

    /** The known emulator tiles (Retro row): used to show the emulators already installed in My Games. */
    public static List<Item> emulators() {
        for (Category c : categories()) if (c.id.equals("retro")) return c.items;
        return Collections.emptyList();
    }

    public static List<Category> categories() {
        return categories(Collections.<Item>emptyList(), Collections.<Item>emptyList(), Collections.<Item>emptyList(), Collections.<Item>emptyList());
    }

    /** 0.9.4 home: My Games (installed games with their icons, installed emulators, the user's own game files) first,
     *  All Apps before Settings. games / apps / emus / roms come from the device and may be empty. */
    public static List<Category> categories(List<Item> games, List<Item> apps, List<Item> emus, List<Item> roms) {
        List<Item> mine = new ArrayList<>(games);
        mine.addAll(emus);
        mine.addAll(roms);
        mine.add(act("Add game folder", "image", "romfolder"));
        List<Item> all = new ArrayList<>(apps);
        if (all.isEmpty()) all.add(act("All apps", "grid", "allapps"));
        List<Category> c = new ArrayList<>();
        c.add(new Category("mygames", "My Games", "pad", mine.toArray(new Item[0])));
        c.add(new Category("games", "Games", "cloud",
                act("Cloud Gaming", "cloud", "goto:cloud"), XBOX, GFN, LUNA, RETROARCH, MINECRAFT, ROBLOX));
        c.add(new Category("cloud", "Cloud", "cloud", XBOX, GFN, LUNA, BOOST));
        c.add(new Category("retro", "Retro", "invader", RETROARCH,
                app("PPSSPP", "handheld", "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"),
                app("DuckStation", "disc", "com.github.stenzek.duckstation"),
                app("Dolphin", "disc", "org.dolphinemu.dolphinemu"),
                app("Mupen64Plus FZ", "cart", "org.mupen64plusae.v3.fzurita"),
                app("Flycast", "disc", "com.flycast.emulator"),
                app("DraStic", "handheld", "com.dsemu.drastic")));
        c.add(new Category("streams", "Streams", "stream",
                app("Netflix", "film", "com.netflix.mediaclient"),
                app("YouTube", "stream", "com.google.android.youtube", "com.google.android.youtube.tv"),
                app("TikTok", "stream", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
                app("Twitch", "stream", "tv.twitch.android.app")));
        c.add(new Category("media", "Media", "film",
                app("Kodi", "film", "org.xbmc.kodi"),
                app("Spotify", "music", "com.spotify.music")));
        c.add(new Category("allapps", "All Apps", "grid", all.toArray(new Item[0])));
        c.add(new Category("settings", "Settings", "gear",
                act("Wi-Fi", "wifi", "wifi"), act("Bluetooth", "bt", "bluetooth"),
                act("Theme", "palette", "theme"), act("Wallpaper", "image", "wallpaper"),
                act("Display", "display", "display"), act("Effects", "sparkle", "effects"),
                act("Home app", "home", "home"), act("Game folders", "image", "romfolder"), act("Update", "download", "update"), act("Android settings", "gear", "settings")));
        return c;
    }

    public static int indexOf(List<Category> cats, String id) {
        for (int i = 0; i < cats.size(); i++) if (cats.get(i).id.equals(id)) return i;
        return -1;
    }

    /** "auto" -> "on" -> "off" -> "auto". */
    public static String nextEffects(String cur) {
        return "auto".equals(cur) ? "on" : "on".equals(cur) ? "off" : "auto";
    }

    /** Power-of-two BitmapFactory sample size so the longer side ends up at least `target` pixels. */
    public static int sampleSize(int longSide, int target) {
        int n = 1;
        while (target > 0 && longSide / (n * 2) >= target) n *= 2;
        return n;
    }

    private Catalog() {}
}
