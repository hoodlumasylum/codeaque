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
        public String boxart() { return isRom() && cls != null ? boxartUrl(cls) : null; }
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
        return new Item(stripExt(name), "cart", new String[0], null, null, null, name, uri);   // cls = the file name (for box art)
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

    /** 0.9.5: libretro-thumbnails system folder for a game file type (only types that belong to one system; "" = no lookup). */
    public static String libretroSystem(String fileName) {
        int d = fileName.lastIndexOf('.');
        String e = d < 0 ? "" : fileName.substring(d + 1).toLowerCase(java.util.Locale.ROOT);
        switch (e) {
            case "nes": return "Nintendo - Nintendo Entertainment System";
            case "sfc": case "smc": return "Nintendo - Super Nintendo Entertainment System";
            case "gb": return "Nintendo - Game Boy";
            case "gbc": return "Nintendo - Game Boy Color";
            case "gba": return "Nintendo - Game Boy Advance";
            case "nds": return "Nintendo - Nintendo DS";
            case "n64": case "z64": case "v64": return "Nintendo - Nintendo 64";
            case "md": case "gen": case "smd": return "Sega - Mega Drive - Genesis";
            case "sms": return "Sega - Master System - Mark III";
            case "gg": return "Sega - Game Gear";
            case "pce": return "NEC - PC Engine - TurboGrafx 16";
            case "a26": return "Atari - 2600";
            case "a78": return "Atari - 7800";
            case "lnx": return "Atari - Lynx";
            case "ws": return "Bandai - WonderSwan";
            case "wsc": return "Bandai - WonderSwan Color";
            case "ngp": return "SNK - Neo Geo Pocket";
            default: return "";
        }
    }

    /** Box art URL on the public libretro thumbnails server for a game file (its name, libretro's character rules), or null. */
    public static String boxartUrl(String fileName) {
        String sys = libretroSystem(fileName);
        if (sys.isEmpty()) return null;
        String n = stripExt(fileName).replaceAll("[&*/:`<>?\\\\|\"]", "_");
        return "https://thumbnails.libretro.com/" + enc(sys) + "/Named_Boxarts/" + enc(n) + ".png";
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    static String stripExt(String n) { int d = n.lastIndexOf('.'); return d > 0 ? n.substring(0, d) : n; }

    /** The known emulator tiles (Retro row): used to show the emulators already installed in My Games. */
    public static List<Item> emulators() {
        for (Category c : categories()) if (c.id.equals("retro")) return c.items;
        return Collections.emptyList();
    }

    /** 0.10.1: the Play Store itself on the Featured row (its own installed icon). */
    public static final Item PLAY_STORE = app("Play Store", "grid", "com.android.vending");

    /** 0.10.2: GeForce NOW on Featured: the app when installed, else its Play page (the Cloud row keeps the web version). */
    public static final Item GEFORCE_NOW = app("GeForce NOW", "cloud", "com.nvidia.geforcenow");
    /** On RK3566 / Anbernic (GammaOS) the app's sign-in fails (nvidia://auth_redirect), so the tile opens the web client. */
    public static final String GFN_WEB = "https://play.geforcenow.com";
    public static boolean gfnInBrowser(boolean handheld) { return handheld; }

    /** 0.10.4: Marvel Rivals on GeForce NOW. Official GFN deep-link format (NVIDIA GeForceNOW-SDK, GfnSdk-Deep-Linking.md);
     *  game-id from GFN's own catalogue (Steam 2767030), checked 7 Nov 2026: the page is "Play Marvel Rivals | NVIDIA GeForce NOW". */
    public static final String RIVALS_GFN_ID = "4b44eb06-3d68-4ca0-92b8-ce4ffd20dff4";
    public static final String RIVALS_GFN = GFN_WEB + "/games?game-id=" + RIVALS_GFN_ID + "&utm_source=codeaque&utm_campaign=launcher";
    /** Runtime-only picture: the official Steam store header for app 2767030 (never bundled). */
    public static final String RIVALS_HEADER = "https://cdn.cloudflare.steamstatic.com/steam/apps/2767030/header.jpg";
    public static final Item MARVEL_RIVALS = act("Marvel Rivals", "cloud", "gfn:rivals");

    /** 0.10.2 Kids mode: only these big rows, nothing retro / ROM / BIOS / Ask / projector / advanced.
     *  Grown-ups leave it by holding Select (or Y / Menu) for a second, which opens KIDS_OFF. */
    public static final Item KIDS_OFF = act("Turn off Kids mode", "gear", "kidsoff");
    public static List<Category> kidsMode() {
        List<Category> c = new ArrayList<>();
        c.add(new Category("kplay", "Play", "pad", ROBLOX, MINECRAFT, GEFORCE_NOW, MARVEL_RIVALS));
        c.add(new Category("kgames", "Kids Games", "invader", KIDS));
        c.add(new Category("kstore", "Play Store", "grid", PLAY_STORE));
        return c;
    }

    /** Kids row (package, PEGI): PEGI 3, except Subway Surfers (PEGI 7) and the three Bandai Namco games below (PEGI 16, 0.10.2). */
    public static final Item[] KIDS = {
            app("Pokémon Playhouse", "cube", "com.pokemon.pokemonplayhouse"),
            app("Pokémon Quest", "cube", "jp.pokemon.pokemonquest"),
            app("Pokémon Smile", "cube", "jp.pokemon.pokemonsmile"),
            app("Pokémon Shuffle", "cube", "jp.pokemon.poketoru"),
            app("Sonic Dash", "cube", "com.sega.sonicdash"),
            app("Hot Wheels Unlimited", "cube", "com.budgestudios.googleplay.HotWheelsUnlimited"),
            app("PAW Patrol Rescue World", "cube", "com.budgestudios.googleplay.PawPatrolRescueWorld"),
            app("Toca Boca World", "cube", "com.tocaboca.tocalifeworld"),
            app("Subway Surfers", "cube", "com.kiloo.subwaysurf"),
            // 0.10.2: official Bandai Namco Dragon Ball games, PEGI 16 on Google Play UK, added with the parent's OK
            app("Dragon Ball Z Dokkan Battle", "cube", "com.bandainamcogames.dbzdokkanww"),
            app("Dragon Ball Legends", "cube", "com.bandainamcoent.dblegends_ww"),
            app("Dragon Ball Gekishin Squadra", "cube", "com.bandainamcoent.dbgekishinsquadra"),
    };

    public static List<Category> categories() {
        return categories(Collections.<Item>emptyList(), Collections.<Item>emptyList(), Collections.<Item>emptyList(), Collections.<Item>emptyList());
    }

    /** 0.9.4 home: My Games (installed games with their icons, installed emulators, the user's own game files) first,
     *  All Apps before Settings. games / apps / emus / roms come from the device and may be empty. */
    /** Projector mode: Cloud (GeForce NOW, Xbox) and Media (Kodi, Free Movies & TV) come first. */
    /** 0.10.1: the boot animation entry only shows where Magisk / root is present. */
    public static List<Category> categories(List<Item> games, List<Item> apps, List<Item> emus, List<Item> roms, boolean projector, boolean magisk) {
        List<Category> c = categories(games, apps, emus, roms, projector);
        if (magisk) return c;
        List<Category> out = new ArrayList<>();
        for (Category k : c) {
            if (!k.id.equals("settings")) { out.add(k); continue; }
            List<Item> keep = new ArrayList<>();
            for (Item i : k.items) if (!"bootanim".equals(i.action)) keep.add(i);
            out.add(new Category(k.id, k.label, k.icon, keep.toArray(new Item[0])));
        }
        return out;
    }

    public static List<Category> categories(List<Item> games, List<Item> apps, List<Item> emus, List<Item> roms, boolean projector) {
        List<Category> c = categories(games, apps, emus, roms);
        if (!projector) return c;
        List<Category> out = new ArrayList<>();
        for (String id : new String[]{"cloud", "media"}) { int i = indexOf(c, id); if (i >= 0) out.add(c.get(i)); }
        for (Category k : c) if (!out.contains(k)) out.add(k);
        return out;
    }

    public static List<Category> categories(List<Item> games, List<Item> apps, List<Item> emus, List<Item> roms) {
        List<Item> mine = new ArrayList<>(games);
        mine.addAll(emus);
        mine.addAll(roms);
        mine.add(act("Add game folder", "image", "romfolder"));
        List<Item> all = new ArrayList<>(apps);
        if (all.isEmpty()) all.add(act("All apps", "grid", "allapps"));
        List<Category> c = new ArrayList<>();
        // 0.9.5: Steam at the top: Steam Link (play from your PC) and the Steam app; Play Store page when not installed
        // 0.10.1: Roblox and Minecraft first on the home (their own icon when installed, else the Play Store page + CodeAque's icon)
        c.add(new Category("steam", "Featured", "display", ROBLOX, MINECRAFT, PLAY_STORE, GEFORCE_NOW, MARVEL_RIVALS, app("Steam Link", "display", "com.valvesoftware.steamlink"),
                app("Steam", "display", "com.valvesoftware.android.steam.community")));
        // 0.10.1 Kids: official Google Play listings only, each checked as PEGI 3 or PEGI 7 in the UK (Nov 2026).
        // Their own icon when installed, otherwise CodeAque's generic icon + label and the Play page. No bundled art.
        c.add(new Category("kids", "Kids", "pad", KIDS));
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
        // 0.9.6: IPTV = your own playlist in VLC; Free Movies & TV = legal free services (their app, else their website)
        c.add(new Category("media", "Media", "film",
                app("Kodi", "film", "org.xbmc.kodi"),
                app("Spotify", "music", "com.spotify.music"),
                app("IPTV (VLC, your own playlist)", "stream", "org.videolan.vlc"),
                web("Pluto TV", "film", "https://pluto.tv/", "tv.pluto.android"),
                web("Tubi", "film", "https://tubitv.com/", "com.tubitv"),
                web("Plex (free)", "film", "https://watch.plex.tv/", "com.plexapp.android"),
                web("Freevee", "film", "https://www.amazon.co.uk/gp/video/storefront?contentId=freetv", "com.amazon.avod.thirdpartyclient"),
                web("BBC iPlayer", "film", "https://www.bbc.co.uk/iplayer", "bbc.iplayer.android"),
                web("ITVX", "film", "https://www.itv.com/", "air.ITVMobilePlayer"),
                web("Channel 4", "film", "https://www.channel4.com/", "com.channel4.ondemand")));
        // 0.9.6: Games Store (Play Store pages; prices stay on Google Play) and Accounts (each service's own app)
        c.add(new Category("store", "Games Store", "grid",
                web("Top Games", "cube", "https://play.google.com/store/games"),
                web("Free Games", "cube", "https://play.google.com/store/search?q=free%20games&c=apps"),
                web("Paid Games", "cube", "https://play.google.com/store/search?q=premium%20games&c=apps")));
        c.add(new Category("accounts", "Accounts", "home",
                app("Steam", "display", "com.valvesoftware.android.steam.community"),
                XBOX,
                app("Google Play Games", "pad", "com.google.android.play.games"),
                act("Android accounts", "gear", "accounts")));
        c.add(new Category("allapps", "All Apps", "grid", all.toArray(new Item[0])));
        c.add(new Category("settings", "Settings", "gear",
                act("Wi-Fi", "wifi", "wifi"), act("Bluetooth", "bt", "bluetooth"),
                act("Theme", "palette", "theme"), act("Wallpaper", "image", "wallpaper"),
                act("Display", "display", "display"), act("Effects", "sparkle", "effects"),
                act("Home app", "home", "home"), act("Game folders", "image", "romfolder"), act("Projector mode", "display", "projector"), act("Update", "download", "update"), act("CodeAque boot animation", "download", "bootanim"), act("Force landscape for games", "display", "rotation"), act("Kids mode", "pad", "kids"), act("Android settings", "gear", "settings")));
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
