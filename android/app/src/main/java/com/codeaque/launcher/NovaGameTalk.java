package com.codeaque.launcher;

/**
 * Built-in, kid-safe answers about well-known games, plus fuzzy matching
 * for misheard names (roe box, robecks, mine craft). Works with no on-device
 * brain. Plain Java for CatalogTest.
 */
public final class NovaGameTalk {
    private NovaGameTalk() {}

    static final String[][] ALIASES = {
            { "roblox", "roe box", "roebox", "ro box", "row box", "robecks", "robex",
                    "row blocks", "rowblocks", "ro blocks", "roblocks", "robloks",
                    "roblox", "roe blocks", "roblux", "raw blocks", "road blox" },
            { "minecraft", "mine craft", "minekraft", "mindcraft", "minecraf",
                    "mine craft", "mine-craft", "minecrafts" },
            { "sonic dash", "sonicdash", "sonic" },
            { "subway surfers", "subway surfer", "subway survers", "subway" },
            { "toca boca world", "toca boca", "toca", "toka boka" },
            { "paw patrol rescue world", "paw patrol", "pawpatrol" },
            { "pokemon playhouse", "pokemon quest", "pokemon smile", "pokemon shuffle", "pokemon", "pokémon" },
            { "hot wheels unlimited", "hot wheels", "hotwheels" },
    };

    static final String[] KEEP_SHORT = {
            "a", "i", "im", "ok", "no", "go", "my", "me", "we", "do", "is", "to",
            "of", "in", "on", "at", "it", "up", "an", "am", "hi", "if", "or", "be",
            "by", "as", "tv", "pc", "ps", "xr", "us"
    };

    public static String cleanHeard(String raw) {
        String t = NovaSmallTalk.stripFillers(raw);
        t = stripLeadingNoise(t);
        return rewriteGames(t);
    }

    /** Drop stray leading 1–2 letter ASR tokens such as "o" or "ut". */
    public static String stripLeadingNoise(String s) {
        if (s == null) return "";
        String t = NovaSpeakGate.norm(s);
        if (t.isEmpty()) return "";
        String[] w = t.split(" ");
        int i = 0;
        while (i < w.length && isNoiseToken(w[i])) i++;
        if (i == 0) return t;
        if (i >= w.length) return "";
        StringBuilder b = new StringBuilder();
        for (int k = i; k < w.length; k++) {
            if (b.length() > 0) b.append(' ');
            b.append(w[k]);
        }
        return b.toString();
    }

    static boolean isNoiseToken(String w) {
        if (w == null || w.isEmpty()) return true;
        if (w.length() > 2) return false;
        for (int i = 0; i < KEEP_SHORT.length; i++) if (KEEP_SHORT[i].equals(w)) return false;
        return true;
    }

    public static String rewriteGames(String s) {
        if (s == null || s.isEmpty()) return "";
        String[] w = s.split(" ");
        // Longest trailing window first (up to 4 tokens), including the whole line.
        for (int n = Math.min(4, w.length); n >= 1; n--) {
            StringBuilder span = new StringBuilder();
            for (int k = w.length - n; k < w.length; k++) {
                if (span.length() > 0) span.append(' ');
                span.append(w[k]);
            }
            String m = matchName(span.toString());
            if (m != null) {
                StringBuilder head = new StringBuilder();
                for (int k = 0; k < w.length - n; k++) {
                    if (head.length() > 0) head.append(' ');
                    head.append(w[k]);
                }
                if (head.length() == 0) return displayName(m);
                return head + " " + displayName(m);
            }
        }
        return s;
    }

    public static String matchName(String s) {
        if (s == null) return null;
        String c = compact(s);
        if (c.isEmpty()) return null;
        for (int i = 0; i < ALIASES.length; i++) {
            String canon = ALIASES[i][0];
            for (int j = 0; j < ALIASES[i].length; j++) {
                String a = compact(ALIASES[i][j]);
                if (a.isEmpty()) continue;
                if (c.equals(a)) return canon;
                // Fuzzy typo only when the compact length is almost the same.
                // Otherwise "is mine craft" is treated as a typo of "minecraft".
                if (Math.abs(c.length() - a.length()) > 1) continue;
                if (a.length() >= 5 && dist(c, a) <= 2) return canon;
            }
        }
        return null;
    }

    public static String displayName(String canon) {
        if (canon == null || canon.isEmpty()) return "";
        if (canon.equals("roblox")) return "Roblox";
        if (canon.equals("minecraft")) return "Minecraft";
        if (canon.equals("sonic dash")) return "Sonic Dash";
        if (canon.equals("subway surfers")) return "Subway Surfers";
        if (canon.equals("toca boca world")) return "Toca Boca World";
        if (canon.equals("paw patrol rescue world")) return "PAW Patrol";
        if (canon.equals("pokemon playhouse") || canon.equals("pokemon")) return "Pokémon";
        if (canon.equals("hot wheels unlimited")) return "Hot Wheels";
        return canon.substring(0, 1).toUpperCase() + canon.substring(1);
    }

    public static boolean isAboutGame(String q) {
        String n = q == null ? "" : q;
        return n.startsWith("tell me about ") || n.startsWith("tell me of ")
                || n.startsWith("what is ") || n.startsWith("whats ")
                || n.startsWith("what s ") || n.startsWith("what's ")
                || n.startsWith("who is ") || n.startsWith("whats a ")
                || n.startsWith("what is a ") || n.startsWith("about ")
                || n.startsWith("explain ") || n.startsWith("talk about ");
    }

    /** A canned game reply, or null if this is not a known game question. */
    public static String reply(String raw, boolean kids) {
        String cleaned = cleanHeard(raw);
        if (cleaned.isEmpty()) return null;
        String name = null;
        if (isAboutGame(cleaned) || cleaned.startsWith("find ") || cleaned.startsWith("open ")
                || cleaned.startsWith("play ") || cleaned.startsWith("search ")
                || cleaned.startsWith("get ")) {
            name = matchName(trailingTopic(cleaned));
        }
        if (name == null) name = matchName(cleaned);
        if (name == null) return null;
        return blurb(name, kids);
    }

    static String trailingTopic(String n) {
        String[] prefixes = {
                "tell me about ", "tell me of ", "what is a ", "what is ", "whats ",
                "what s ", "who is ", "about ", "explain ", "talk about ",
                "find ", "open ", "play ", "search for ", "search ", "get "
        };
        for (int i = 0; i < prefixes.length; i++) {
            if (n.startsWith(prefixes[i])) return n.substring(prefixes[i].length()).trim();
        }
        return n;
    }

    public static String blurb(String canon, boolean kids) {
        String nice = displayName(canon);
        if (canon.equals("roblox")) {
            return kids
                    ? "Roblox is a game where you can play lots of experiences made by other people. Look for it on the Roblox row, or search at the top."
                    : "Roblox is a platform of user-made experiences. CodeAque has a Roblox row, or use Search.";
        }
        if (canon.equals("minecraft")) {
            return kids
                    ? "Minecraft is a game where you build with blocks and explore a big world. You can find it on the home rows, or search at the top."
                    : "Minecraft is a sandbox building game. Search for it at the top, or look on the home rows.";
        }
        if (canon.equals("sonic dash")) {
            return kids
                    ? "Sonic Dash is a fast running game with Sonic. Search for it at the top, or look along the rows."
                    : "Sonic Dash is a runner starring Sonic. Search for it at the top.";
        }
        if (canon.equals("subway surfers")) {
            return kids
                    ? "Subway Surfers is a running game where you dash along the tracks. Search for it at the top, or look along the rows."
                    : "Subway Surfers is an endless runner. Search for it at the top.";
        }
        if (canon.equals("toca boca world")) {
            return kids
                    ? "Toca Boca World is a play house game where you can dress up and make up stories. Search for it at the top."
                    : "Toca Boca World is a creative play game. Search for it at the top.";
        }
        if (canon.equals("paw patrol rescue world")) {
            return kids
                    ? "PAW Patrol is a game with the rescue pups. Search for it at the top, or look along the rows."
                    : "PAW Patrol Rescue World is on the game rows, or use Search.";
        }
        if (canon.equals("pokemon playhouse") || canon.equals("pokemon")) {
            return kids
                    ? "There are a few Pokémon games here, like Playhouse, Quest, Smile and Shuffle. Search for Pokémon at the top."
                    : "Several Pokémon games are listed in CodeAque. Use Search to find one.";
        }
        if (canon.equals("hot wheels unlimited")) {
            return kids
                    ? "Hot Wheels is a racing game with the little cars. Search for it at the top, or look along the rows."
                    : "Hot Wheels Unlimited is a racing game. Search for it at the top.";
        }
        return kids
                ? nice + " is a game you can look for in CodeAque. Search for it at the top, or look along the rows."
                : nice + " is listed in CodeAque. Use Search at the top, or browse the rows.";
    }

    public static String compact(String s) {
        if (s == null) return "";
        String t = s.toLowerCase();
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
        }
        return b.toString();
    }

    /** Levenshtein distance, capped so long mismatches bail out early. */
    public static int dist(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int best = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int v = prev[j - 1] + cost;
                int del = prev[j] + 1;
                int ins = cur[j - 1] + 1;
                if (del < v) v = del;
                if (ins < v) v = ins;
                cur[j] = v;
                if (v < best) best = v;
            }
            int[] tmp = prev; prev = cur; cur = tmp;
            if (best > 3 && i > 3) {
                // still finish this row; early abandon if already hopeless
                if (best > 6) return 6;
            }
        }
        return prev[m];
    }
}
