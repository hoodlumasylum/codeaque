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
        public String key() {
            if (isDeviceApp()) return pkg + "/" + cls;
            if (isRom()) return uri;
            if (action != null && action.startsWith("roblox:")) return action;  // 0.10.34: unique even when two games share a name
            return label;
        }
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
    public static final Item MINECRAFT = app("Minecraft", "cube", "com.mojang.minecraftpe");
    public static final Item ROBLOX = app("Roblox", "cube", "com.roblox.client");

    /** An installed app or game (from PackageManager): opens itself, shows its own icon. */
    public static Item deviceApp(String label, String pkg, String cls) {
        return new Item(label, "grid", new String[]{pkg}, null, null, pkg, cls, null);
    }

    /** A game file from a folder the user chose (Android's folder picker); opened with an installed emulator. */
    /** 0.10.12: a Dreamcast / PSP game found by the scan, labelled with its clean name. */
    /** 0.10.15 Kids mode: a Dreamcast / PSP row shows only its games; the emulator tile only when there are none. */
    static Item[] kidsGamesOnly(List<Item> items) {
        List<Item> g = new ArrayList<>();
        for (Item i : items) if (i.isRom()) g.add(i);
        return (g.isEmpty() ? items : g).toArray(new Item[0]);
    }
    public static boolean isViceCity(String label) { return label != null && label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "").contains("vicecity"); }
    /** 0.10.16: Hoodlum Asylum's own Dreamcast covers, matched loosely (any case, no punctuation/spaces). Returns a key or null. */
    public static String haDcCover(String... names) {
        for (String raw : names) {
            if (raw == null) continue;
            String n = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (n.contains("shenmue")) {
                if (n.matches(".*shenmue.*(disc|disk|cd|d)0?(2|ii|two).*")) return "shenmue_disc_2";
                if (n.contains("shenmue2") || n.contains("shenmueii")) continue;
                return "shenmue_disc_1";
            }
            if (n.contains("sonicadventure2") || n.contains("sonicadventureii") || n.contains("sonicadv2")) return "sonic_adventure_2";   // 0.10.18 (before plain Sonic Adventure)
            if (n.contains("sonicadventure")) return "sonic_adventure";
            if (n.contains("spiderman")) return "spider_man";
            if (n.contains("streetfighteriii") || n.contains("streetfighter3") || n.contains("thirdstrike")) return "street_fighter_3";
            if (n.contains("toystory2") || n.contains("toystoryii") || n.contains("buzzlightyear")) return "toy_story_2";
            if (n.contains("crazytaxi")) return "crazy_taxi";
            if (n.contains("jetgrindradio") || n.contains("jetsetradio")) return "jet_grind_radio";
            if (n.contains("marvelvscapcom") || n.contains("marvelvcapcom") || n.contains("marvelversuscapcom")) return "marvel_vs_capcom";
            if (n.contains("mortalkombatgold")) return "mortal_kombat_gold";
            if (n.contains("princeofpersia")) return "prince_of_persia";
            if (n.contains("rayman2") || n.contains("raymanii")) return "rayman_2";
        }
        return null;
    }
    public static Item gameRom(String name, String uri) {
        return new Item(cleanTitle(stripExt(name)), "cart", new String[0], null, null, null, name, uri);
    }
    /** "Crazy_Taxi (USA) [!]" -> "Crazy Taxi"; keeps "Disc 1" so multi-disc games stay apart. */
    public static String cleanTitle(String n) {
        String t = n.replace('_', ' ').replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", " ");
        t = t.replaceAll("(?i)\\b(gdi|cdi|chd|iso|cso)\\b$", "").replaceAll("\\s+", " ").trim();
        t = t.replaceAll("\\s*-\\s*$", "");
        return t.isEmpty() ? n : t;
    }
    /** 0.10.12: player-supplied cover file names next to a game (checked case-insensitively, in this order). */
    public static java.util.List<String> coverNames(String fileName, String folderName) {
        String stem = stripExt(fileName);
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String b : new String[]{"cover", "folder", "boxart", stem, folderName})
            for (String e : new String[]{"png", "jpg", "jpeg", "webp"}) if (b != null && !b.isEmpty()) l.add((b + "." + e).toLowerCase(java.util.Locale.ROOT));
        return l;
    }

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
    /** "\"Home\"" -> Home; Android's placeholders ("<unknown ssid>", empty) -> null. */
    public static String cleanSsid(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        if (s.isEmpty() || s.equalsIgnoreCase("<unknown ssid>") || s.equals("0x")) return null;
        return s.length() > 32 ? s.substring(0, 32) : s;
    }
    /** 0.10.5 Dreamcast: Flycast (labelled "Dreamcast") then the player's own .gdi / .cdi / .chd files. No BIOS, no games, no art. */
    public static final Item DREAMCAST = app("Dreamcast", "disc", "com.flycast.emulator");
    public static boolean isDreamcastRom(Item i) {
        if (isPspRom(i)) return false;
        return i.isRom() && i.cls != null && DcScan.dreamcastFile(i.cls, i.uri != null && i.uri.startsWith("file:"));
    }
    /** 0.10.7: opens the system file manager, e.g. to copy games / BIOS from a USB stick. Never in Kids mode. */
    public static final Item FILES = act("Files", "folder", "filemgr");
    public static final Item RESCAN = act("Rescan", "download", "rescan");   // 0.10.9, never in Kids mode
    public static final String DC_COPY_HINT = "Copy games to a folder called Dreamcast (PSP games to PSP), BIOS to BIOS";
    /** 0.10.7 PSP: PPSSPP (or PPSSPP Gold) labelled "PSP", then the player's own .iso / .cso / .pbp (and .chd in PSP folders). */
    public static final Item PSP = app("PSP", "handheld", "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold");
    public static final String PSP_HINT = "Copy PSP games to a folder called PSP";
    /** Hint under the non-game tiles of the Dreamcast / handheld sections (null elsewhere). */
    public static String copyHint(String catId, Item f) {
        if (f.isRom()) return null;
        if ("dreamcast".equals(catId) || "kdc".equals(catId)) return DC_COPY_HINT;
        if ("psp".equals(catId) || "kpsp".equals(catId)) return PSP_HINT;
        return null;
    }
    public static boolean isPspRom(Item i) {
        if (!i.isRom() || i.cls == null || i.uri == null || !i.uri.startsWith("file:")) return false;
        return PspScan.pspFile(i.cls, PspScan.inPspFolder(i.uri));
    }
    public static Category psp(List<Item> roms) { return psp(roms, true); }
    public static Category psp(List<Item> roms, boolean files) {
        List<Item> l = new ArrayList<>(); l.add(PSP); if (files) { l.add(FILES); l.add(RESCAN); }   // 0.10.8: Files here too (never in Kids mode)
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Item r : roms) if (isPspRom(r) && names.add(r.label.toLowerCase(java.util.Locale.ROOT))) l.add(r);
        return new Category("psp", "PSP", "handheld", l.toArray(new Item[0]));
    }
    public static Category dreamcast(List<Item> roms) { return dreamcast(roms, true); }
    public static Category dreamcast(List<Item> roms, boolean files) {
        List<Item> l = new ArrayList<>(); l.add(DREAMCAST); if (files) { l.add(FILES); l.add(RESCAN); }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Item r : roms) if (isDreamcastRom(r) && names.add(r.label.toLowerCase(java.util.Locale.ROOT))) l.add(r);
        return new Category("dreamcast", "Dreamcast", "disc", l.toArray(new Item[0]));
    }
    /** 0.10.29: Phone / Messages (only on devices that can make calls). Kids mode shows this tile and nothing else
     *  about the phone: the parent's safe contacts only, no keypad and no other people. */
    public static final Item CALL_TEXT = act("Phone / Messages", "phone", "calltext");
    public static Category callRow() { return new Category("calls", "Phone", "phone", CALL_TEXT); }
    /** 0.10.29: adult-mode Downloads tile. Opens LibreTorrent when it is installed, otherwise its store page.
     *  Never a link to a ROM, piracy or IPTV site. Hidden from Kids mode (see kidsBlocked). */
    public static final String LIBRETORRENT = "org.proninyaroslav.libretorrent";
    public static final Item DOWNLOADS = act("Downloads", "download", "downloads");
    /** 0.10.23: does opening this tile go to the Play Store (the Play Store tile, or "Get" on a game that isn't installed)? */
    public static boolean opensStore(Item it, boolean installed) {
        if (it == null || it.isAction() || it.isRom() || it.isDeviceApp()) return false;
        if (it == PLAY_STORE) return true;
        if (it.packages.contains("com.android.vending")) return true;
        return !installed && it.webUrl == null && it.storePackage() != null;
    }
    /** 0.10.24: streaming, TV, IPTV, video and music apps never show or open in Kids mode (normal mode unchanged). */
    static final String[] STREAM_WORDS = {"youtube", "netflix", "tiktok", "musically", "ugc.trill", "twitch", "iptv", "videolan", "vlc",
            "kodi", "xbmc", "plex", "tubi", "pluto", "iplayer", "bbc.", "avod", "primevideo", "disney", "hulu", "crunchyroll", "spotify",
            "itv", "channel4", "all4", "my5", "skygo", "nowtv", "dazn", "paramount", "hbo", "max.android", "dailymotion", "vimeo",
            "smarttube", "stremio", "tivimate", "perfectplayer", "mxtech", "streaming", "stream"};
    public static boolean streamingPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("boosteroid") || p.contains("geforcenow") || p.contains("steamlink") || p.contains("xcloud") || p.contains("gamepass")) return false;   // cloud gaming, not video
        for (String w : STREAM_WORDS) if (p.contains(w)) return true;
        return false;
    }
    /** 0.10.24: things Kids mode never shows or opens: All Apps, and streaming / video apps. */
    public static boolean kidsBlocked(Item it) {
        if (it == null) return false;
        if ("allapps".equals(it.action) || "downloads".equals(it.action) || "goto:media".equals(it.action) || "goto:streams".equals(it.action)) return true;
        if ("boxset".equals(it.action) || "boxsetboot".equals(it.action) || "goto:trilogies".equals(it.action) || "goto:coop".equals(it.action)) return true;
        if (it.action != null && it.action.startsWith("steamstore:")) return true;
        if (it.isDeviceApp() && streamingPkg(it.pkg)) return true;
        for (String p : it.packages) if (streamingPkg(p)) return true;
        return false;
    }
    /** 0.10.26: the big "Kids Mode" tile, first on the normal home: one tap, no PIN to go in (leaving needs the PIN). */
    public static final Item KIDS_ON = act("Kids Mode", "pad", "kidson");
    /** 0.10.30: a plain Settings tile next to Kids Mode on the grown-up home (jumps to the Settings row, like Y). */
    public static final Item SETTINGS_TILE = act("Settings", "gear", "goto:settings");
    /** 0.10.30: Kids mode "Grown-ups" row. Both ask for the parent PIN, then Adult mode opens (Kids mode off)
     *  on the first row or on Settings. Also used by the CodeAque Adult / CodeAque Settings home-screen icons. */
    public static final Item ADULT_PIN = act("Adult mode", "pad", "adultpin");
    public static final Item SETTINGS_PIN = act("Settings", "gear", "settingspin");
    public static final Item[] KIDS_PARENT = {ADULT_PIN, SETTINGS_PIN};
    public static final String OPEN_ADULT = "com.codeaque.launcher.OPEN_ADULT", OPEN_SETTINGS = "com.codeaque.launcher.OPEN_SETTINGS";
    /** 0.10.30: home-screen aliases, turned off while Lock Mode is on. */
    public static final String[] ALIASES = {".AdultAlias", ".SettingsAlias"};
    /** 0.10.30: which parent entry opened CodeAque: "adult", "settings" or null (a normal start or Home press).
     *  cls = the launched component (an activity-alias keeps its own name); action = a long-press shortcut's action. */
    public static String entryTarget(String action, String cls) {
        if (OPEN_ADULT.equals(action) || (cls != null && cls.endsWith(".AdultAlias"))) return "adult";
        if (OPEN_SETTINGS.equals(action) || (cls != null && cls.endsWith(".SettingsAlias"))) return "settings";
        return null;
    }
    // ---------- 0.10.27 where a newly installed app goes (the parent's choice overrides the automatic row) ----------
    /** Normal-mode rows an app can be put in, in picker order. */
    public static final String[] SECTIONS = {"mygames", "steam", "games", "cloud", "retro", "streams", "media", "store", "accounts", "allapps"};
    public static String sectionLabel(String id) {
        switch (id) {
            case "mygames": return "My Games"; case "steam": return "Featured"; case "games": return "Games"; case "cloud": return "Cloud";
            case "retro": return "Retro"; case "streams": return "Streams"; case "media": return "Media"; case "store": return "Games Store";
            case "accounts": return "Accounts"; default: return "All Apps";
        }
    }
    /** Smart first choice: games -> My Games; video / streaming -> Streams; music, audio, photos -> Media; else All Apps.
     *  appCategory = ApplicationInfo.category (-1 = none; 0 game, 1 audio, 2 video, 3 image). */
    public static String guessSection(boolean game, int appCategory, String pkg) {
        if (game || appCategory == 0) return "mygames";
        if (appCategory == 2) return "streams";
        if (appCategory == 1 || appCategory == 3) return "media";
        if (pkg != null) {
            String p = pkg.toLowerCase(java.util.Locale.ROOT);
            if (p.contains("spotify") || p.contains("music") || p.contains("audio") || p.contains("podcast") || p.contains("radio")) return "media";
            if (streamingPkg(p)) return "streams";
        }
        return "allapps";
    }
    /** Saved as "pkg=section" strings. */
    public static java.util.Map<String, String> placements(java.util.Set<String> saved) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (saved != null) for (String e : saved) { int i = e.lastIndexOf('='); if (i > 0 && java.util.Arrays.asList(SECTIONS).contains(e.substring(i + 1))) m.put(e.substring(0, i), e.substring(i + 1)); }
        return m;
    }
    public static java.util.Set<String> withPlacement(java.util.Set<String> saved, String pkg, String section) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (saved != null) for (String e : saved) if (!e.startsWith(pkg + "=")) out.add(e);
        if (section != null) out.add(pkg + "=" + section);
        return out;
    }
    /** Moves each placed installed app out of the rows it would land in automatically and into its chosen row. */
    public static List<Category> applyPlacements(List<Category> cats, java.util.Map<String, String> place, List<Item> apps) {
        if (place.isEmpty()) return cats;
        java.util.Map<String, Item> byPkg = new java.util.HashMap<>();
        for (Item a : apps) if (a.isDeviceApp() && place.containsKey(a.pkg)) byPkg.put(a.pkg, a);
        if (byPkg.isEmpty()) return cats;
        List<Category> out = new ArrayList<>();
        for (Category k : cats) {
            if (!java.util.Arrays.asList(SECTIONS).contains(k.id)) { out.add(k); continue; }
            List<Item> l = new ArrayList<>();
            for (Item i : k.items) if (!(i.isDeviceApp() && byPkg.containsKey(i.pkg))) l.add(i);
            for (Item a : byPkg.values()) if (k.id.equals(place.get(a.pkg))) l.add(a);
            out.add(new Category(k.id, k.label, k.icon, l.toArray(new Item[0])));
        }
        return out;
    }

    public static final Item KIDS_THEMES = act("Themes", "palette", "kthemes");
    public static final Item KIDS_WIFI = act("Wi-Fi", "wifi", "wifi");
    /** Packages in after that weren't in before (sorted). */
    public static List<String> newPackages(java.util.Set<String> before, java.util.Set<String> after) {
        List<String> out = new ArrayList<>();
        for (String p : after) if (!before.contains(p)) out.add(p);
        Collections.sort(out); return out;
    }
    /** 0.10.28: set by MainActivity before categories are built; shown in Settings. */
    public static volatile String VERSION = "?";
    /** 0.10.28: a newer release is known; draws the "Update available" badge on the update tiles. */
    public static volatile boolean updateAvailable;
    public static boolean showsUpdateBadge(Item it) { return updateAvailable && it != null && "update".equals(it.action); }
    /** 0.10.28: root commands that make CodeAque the Home app (tried in order until one works). */
    public static final String HOME_ACTIVITY = "com.codeaque.launcher/.MainActivity";
    public static final String[] MAKE_HOME_CMDS = {
            "cmd package set-home-activity " + HOME_ACTIVITY,
            "pm set-home-activity " + HOME_ACTIVITY,
    };
    public static final String ROLE_CMD = "cmd role add-role-holder android.app.role.HOME com.codeaque.launcher";
    /** 0.10.28: packages "Hide other launchers" must never disable (CodeAque itself, Android's Settings / fallback home). */
    public static boolean canHideLauncher(String pkg) {
        return pkg != null && !pkg.equals("com.codeaque.launcher") && !pkg.equals("com.android.settings") && !pkg.equals("android") && !pkg.startsWith("com.android.provision");
    }
    /** 0.10.29: tiles removed while Lock mode is on, so a child has no button for them. The parent way out is
     *  seven quick taps on the CodeAque logo, then the PIN (Turn off Lock Mode, or Open Android Settings). */
    public static boolean hiddenInLock(String action) {
        return "lock".equals(action) || "settings".equals(action) || "home".equals(action);
    }
    public static List<Category> applyLockMode(List<Category> cats) {
        List<Category> out = new ArrayList<>();
        for (Category k : cats) {
            boolean drop = false;
            for (Item i : k.items) if (hiddenInLock(i.action)) { drop = true; break; }
            if (!drop) { out.add(k); continue; }
            List<Item> keep = new ArrayList<>();
            for (Item i : k.items) if (!hiddenInLock(i.action)) keep.add(i);
            out.add(new Category(k.id, k.label, k.icon, keep.toArray(new Item[0])));
        }
        return out;
    }

    public static final Item KIDS_UPDATE = act("Update CodeAque", "download", "update");
    public static final Item KIDS_OFF = act("Turn off Kids mode", "gear", "kidsoff");
    public static List<Category> kidsMode() { return kidsMode(Collections.<Item>emptyList()); }
    /** roms = the player's own game files; only the Dreamcast ones are shown, and no folder-import tiles. */
    public static List<Category> kidsMode(List<Item> roms) { return kidsMode(roms, Collections.<Item>emptyList()); }
    /** added = games installed during a Kids mode session (0.10.6), shown as their own row. */
    public static List<Category> kidsMode(List<Item> roms, List<Item> added) { return kidsMode(roms, added, Collections.<String>emptySet()); }
    /** approved = packages a parent added to Kids mode on purpose (0.10.27): shown even if they are video apps. */
    public static List<Category> kidsMode(List<Item> roms, List<Item> added, java.util.Set<String> approved) {
        List<Category> c = new ArrayList<>();
        c.add(new Category("kplay", "Play", "pad", ROBLOX, MINECRAFT, MARVEL_RIVALS, ROBLOX_MODE));
        c.add(new Category("kroblox", "Roblox", "cube", ROBLOX_ITEMS));
        List<Item> newGames = new ArrayList<>();   // 0.10.24: no streaming / TV / video apps in Kids mode
        for (Item a : added) if (!kidsBlocked(a) || (a.isDeviceApp() && approved.contains(a.pkg))) newGames.add(a);
        if (!newGames.isEmpty()) c.add(new Category("kadded", "My New Games", "pad", newGames.toArray(new Item[0])));
        c.add(new Category("kgames", "Kids Games", "invader", KIDS));
        c.add(new Category("kstore", "Play Store", "grid", PLAY_STORE));
        Category dc = dreamcast(roms, false); c.add(new Category("kdc", dc.label, dc.icon, kidsGamesOnly(dc.items)));   // 0.10.15: games only
        Category ps = psp(roms, false); c.add(new Category("kpsp", ps.label, ps.icon, kidsGamesOnly(ps.items)));   // 0.10.7 / 0.10.15
        c.add(new Category("kthemes", "Themes", "palette", KIDS_THEMES));   // 0.10.27: kids can change the look, nothing else
        c.add(new Category("kwifi", "Wi-Fi & Update", "wifi", KIDS_WIFI, KIDS_UPDATE));   // Wi-Fi (0.10.5) and Update CodeAque (0.10.6), nothing else
        c.add(new Category("kparent", "Grown-ups", "gear", KIDS_PARENT));   // 0.10.30: Adult mode and the grown-up menu, both behind the PIN
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


    // ---------- 0.10.32 Trilogies / Co-op / Roblox / Box Set (Steam store links and official Roblox places only) ----------
    static Item steamStore(String label, String appid) {
        return new Item(label, "pad", new String[0], "https://store.steampowered.com/app/" + appid + "/", "steamstore:" + appid);
    }
    static Item robloxPlace(String label, String placeId) {
        return new Item(label, "cube", new String[0], "https://www.roblox.com/games/" + placeId + "/", "roblox:" + placeId);
    }
    public static String steamAppId(Item it) {
        return it != null && it.action != null && it.action.startsWith("steamstore:") ? it.action.substring(11) : null;
    }
    public static String robloxPlaceId(Item it) {
        return it != null && it.action != null && it.action.startsWith("roblox:") ? it.action.substring(7) : null;
    }
    public static String modeAsset(Item it) {
        if (it == null) return null;
        if (it == TRILOGY_MODE) return "mode-trilogy.png";
        if (it == COOP_MODE) return "mode-coop.png";
        if (it == ROBLOX_MODE) return "mode-experiences.png";
        if (it == BOXSET_MODE) return "mode-boxset.png";
        return null;
    }
    public static final Item TRILOGY_MODE = act("Trilogy Mode", "pad", "goto:trilogies");
    public static final Item COOP_MODE = act("Co-op Mode", "pad", "goto:coop");
    public static final Item ROBLOX_MODE = act("Roblox Mode", "cube", "goto:roblox");
    public static final Item BOXSET_MODE = act("Box Set Mode", "pad", "boxset");
    public static final Item BOXSET_BOOT = act("Lock into Box Set Mode on reboot", "gear", "boxsetboot");
    public static final Item[] TRILOGY_ITEMS = {
            steamStore("Uncharted: Legacy of Thieves", "1659420"),
            steamStore("Tomb Raider I-III Remastered", "2478970"),
            steamStore("Tomb Raider IV-VI Remastered", "2525380"),
            steamStore("Tomb Raider: Legend", "7000"),
            steamStore("Tomb Raider: Anniversary", "8000"),
            steamStore("Tomb Raider: Underworld", "8140"),
            steamStore("Tomb Raider (2013)", "203160"),
            steamStore("Rise of the Tomb Raider", "391220"),
            steamStore("Shadow of the Tomb Raider", "750920"),
            steamStore("Lara Croft and the Guardian of Light", "35130"),
            steamStore("Lara Croft and the Temple of Osiris", "289690"),
            steamStore("Lara Croft GO", "540840"),
            steamStore("Grand Theft Auto III", "12100"),
            steamStore("Grand Theft Auto: Vice City", "12110"),
            steamStore("Grand Theft Auto: San Andreas", "12120"),
            steamStore("GTA III \u2013 The Definitive Edition", "1546970"),
            steamStore("GTA Vice City \u2013 The Definitive Edition", "1546990"),
            steamStore("GTA San Andreas \u2013 The Definitive Edition", "1547000"),
            steamStore("Grand Theft Auto IV: Complete Edition", "12210"),
            steamStore("GTA: Episodes from Liberty City", "12220"),
            steamStore("Grand Theft Auto V Legacy", "271590"),
            steamStore("Grand Theft Auto V Enhanced", "3240220"),
            steamStore("Assassin's Creed", "15100"),
            steamStore("Assassin's Creed II", "33230"),
            steamStore("Assassin's Creed Brotherhood", "48190"),
            steamStore("Assassin's Creed Revelations", "201870"),
            steamStore("Assassin's Creed III", "208480"),
            steamStore("Assassin's Creed III Remastered", "911400"),
            steamStore("Assassin's Creed IV Black Flag", "242050"),
            steamStore("Assassin's Creed Rogue", "311560"),
            steamStore("Assassin's Creed Unity", "289650"),
            steamStore("Assassin's Creed Syndicate", "368500"),
            steamStore("Assassin's Creed Origins", "582160"),
            steamStore("Assassin's Creed Odyssey", "812140"),
            steamStore("Assassin's Creed Valhalla", "2208920"),
            steamStore("Assassin's Creed Mirage", "3035570"),
            steamStore("Assassin's Creed Shadows", "3159330"),
            steamStore("The Talos Principle", "257510"),
            steamStore("The Talos Principle 2", "835960"),
            steamStore("The Talos Principle: Reawakened", "2806640"),
            steamStore("Life is Strange (Episode 1)", "319630"),
            steamStore("Life is Strange Remastered", "1265920"),
            steamStore("Life is Strange: Before the Storm", "554620"),
            steamStore("Life is Strange: Before the Storm Remastered", "1265930"),
            steamStore("Life is Strange 2", "532210"),
            steamStore("The Awesome Adventures of Captain Spirit", "845070"),
            steamStore("Life is Strange: True Colors", "936790"),
            steamStore("Life is Strange: Double Exposure", "1874000"),
            steamStore("Life is Strange: Reunion", "2624870")
    };
    public static final Item[] COOP_ITEMS = {
            steamStore("A Way Out", "1222700"),
            steamStore("It Takes Two", "1426210"),
            steamStore("Split Fiction", "2001120"),
            steamStore("Divinity: Original Sin 2", "435150"),
            steamStore("Baldur's Gate 3", "1086940"),
            steamStore("Diablo IV", "2344520"),
            steamStore("Diablo II: Resurrected", "2536520"),
            steamStore("Borderlands 2", "49520"),
            steamStore("Borderlands 3", "397540"),
            steamStore("Grim Dawn", "219990"),
            steamStore("Torchlight II", "200710"),
            steamStore("Dark Souls III", "374320"),
            steamStore("Elden Ring", "1245620"),
            steamStore("Monster Hunter: World", "582010"),
            steamStore("Path of Exile", "238960"),
            steamStore("Path of Exile 2", "2694490"),
            steamStore("Remnant II", "1282100"),
            steamStore("Tiny Tina's Wonderlands", "1286680"),
            steamStore("Dragon's Dogma 2", "2054970"),
            steamStore("Stardew Valley", "413150"),
            steamStore("Terraria", "105600"),
            steamStore("Valheim", "892970")
    };
    public static final Item[] ROBLOX_ITEMS = {
            robloxPlace("Brookhaven", "4924922222"),
            robloxPlace("Adopt Me!", "920587237"),
            robloxPlace("Blox Fruits", "2753915549"),
            robloxPlace("Grow a Garden", "126884695634066"),
            robloxPlace("Murder Mystery 2", "142823291"),
            robloxPlace("Tower of Hell", "1962086868"),
            robloxPlace("Natural Disaster Survival", "189707"),
            robloxPlace("Piggy", "4623386862"),
            robloxPlace("Doors", "6516141723"),
            robloxPlace("Jailbreak", "606849621"),
            robloxPlace("Arsenal", "286090429"),
            robloxPlace("Bee Swarm Simulator", "1537690962")
    };
    public static Item[] boxSetItems() {
        Item[] a = new Item[TRILOGY_ITEMS.length + COOP_ITEMS.length];
        System.arraycopy(TRILOGY_ITEMS, 0, a, 0, TRILOGY_ITEMS.length);
        System.arraycopy(COOP_ITEMS, 0, a, TRILOGY_ITEMS.length, COOP_ITEMS.length);
        return a;
    }

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
        c.add(new Category("steam", "Featured", "display", KIDS_ON, SETTINGS_TILE, ROBLOX, MINECRAFT, PLAY_STORE, GEFORCE_NOW, MARVEL_RIVALS, app("Steam Link", "display", "com.valvesoftware.steamlink"),
                app("Steam", "display", "com.valvesoftware.android.steam.community"),
                TRILOGY_MODE, COOP_MODE, ROBLOX_MODE, BOXSET_MODE));
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
        c.add(dreamcast(roms));   // 0.10.5
        c.add(psp(roms));         // 0.10.7
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
        c.add(new Category("trilogies", "Trilogies", "pad", TRILOGY_ITEMS));
        c.add(new Category("coop", "Co-op", "pad", COOP_ITEMS));
        c.add(new Category("roblox", "Roblox", "cube", ROBLOX_ITEMS));
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
                act("CodeAque v" + VERSION, "gear", "about"),   // 0.10.28: version shown clearly, first in Settings
                act("Wi-Fi", "wifi", "wifi"), act("Bluetooth", "bt", "bluetooth"),
                act("Theme", "palette", "theme"), act("Wallpaper", "image", "wallpaper"),
                act("Display", "display", "display"), act("Effects", "sparkle", "effects"),
                act("Home app", "home", "home"), act("Make CodeAque home", "home", "makehome"), act("Hide other launchers", "home", "hidelaunchers"), act("Game folders", "image", "romfolder"), act("Find Dreamcast games", "disc", "files"), act("Rescan for games", "download", "rescan"), act("Scan details", "disc", "scandetails"), act("Kids mode games", "pad", "kidsgames"), act("Sort apps", "grid", "sortapps"), act("Projector mode", "display", "projector"), act("Update", "download", "update"), act("CodeAque boot animation", "download", "bootanim"), act("Force landscape for games", "display", "rotation"), act("Kids mode", "pad", "kids"), BOXSET_MODE, BOXSET_BOOT, DOWNLOADS, act("Lock mode", "gear", "lock"), act("Change PIN", "gear", "changepin"), act("Safe contacts", "gear", "safecontacts"), act("Block unknown callers", "gear", "blockunknown"), act("Only call safe numbers", "gear", "safeonly"), act("Android settings", "gear", "settings")));
        return c;
    }

    /** 0.10.34: swap the tiles in one row (Roblox explore results) and keep the rest of the home as it is. */
    public static List<Category> replaceItems(List<Category> cats, String id, List<Item> items) {
        if (cats == null || id == null) return cats;
        Item[] arr = (items == null ? new Item[0] : items.toArray(new Item[0]));
        List<Category> out = new ArrayList<>(cats.size());
        for (Category c : cats) {
            if (id.equals(c.id)) out.add(new Category(c.id, c.label, c.icon, arr));
            else out.add(c);
        }
        return out;
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
