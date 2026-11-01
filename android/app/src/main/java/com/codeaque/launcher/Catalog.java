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
        public Item(String label, String icon, String[] packages, String webUrl, String action) {
            this.label = label; this.icon = icon; this.webUrl = webUrl; this.action = action;
            this.packages = Collections.unmodifiableList(Arrays.asList(packages));
        }
        public String storePackage() { return packages.isEmpty() ? null : packages.get(0); }
        public boolean isAction() { return action != null; }
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

    public static List<Category> categories() {
        List<Category> c = new ArrayList<>();
        c.add(new Category("games", "Games", "pad",
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
        c.add(new Category("apps", "Apps", "grid", MINECRAFT, ROBLOX,
                act("Browser", "globe", "browser"), act("All apps", "grid", "allapps")));
        c.add(new Category("settings", "Settings", "gear",
                act("Wi-Fi", "wifi", "wifi"), act("Bluetooth", "bt", "bluetooth"),
                act("Display", "display", "display"), act("Effects", "sparkle", "effects"),
                act("Home app", "home", "home"), act("Android settings", "gear", "settings")));
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

    private Catalog() {}
}
