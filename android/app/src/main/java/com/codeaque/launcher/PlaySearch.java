package com.codeaque.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-launcher Google Play search for Kids mode (0.10.49).
 * Fetches the public Play search page the same way {@link PlayArt} fetches a listing,
 * then keeps only age-appropriate games. Kids never see the raw Play Store search UI —
 * results are shown as CodeAque tiles. A failed or empty Play response falls back to
 * the bundled {@link KidsPlay} catalogue (and any last-good cache the caller holds).
 */
public final class PlaySearch {
    private PlaySearch() { }

    public static final int MAX_RESULTS = 24;
    public static final int MAX_RATING_FETCH = 16;

    public static final class Hit {
        public final String pkg, title;
        public final int pegi;   // 0 = unknown (must not be shown in Kids until rated)
        public Hit(String pkg, String title, int pegi) {
            this.pkg = pkg;
            this.title = title == null ? pkg : title;
            this.pegi = pegi;
        }
        public Catalog.Item item() { return Catalog.app(title, "cube", pkg); }
        public Hit withPegi(int p) { return new Hit(pkg, title, p); }
    }

    public static String norm(String q) {
        if (q == null) return "";
        return q.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static boolean matches(String query, String title, String pkg) {
        String q = norm(query);
        if (q.isEmpty()) return true;
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        if (t.contains(q)) return true;
        if (p.contains(q.replace(" ", ""))) return true;
        for (String w : q.split(" ")) if (w.length() >= 2 && t.contains(w)) return true;
        return false;
    }

    public static String searchUrl(String base, String query) {
        if (base == null) base = PlayArt.PLAY;
        String q = query == null ? "" : query.trim();
        try { q = java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20"); }
        catch (Exception ignored) { }
        String url = base + "/store/search?q=" + q + "&c=apps&hl=en_GB";
        if (PlayArt.PLAY.equals(base) && url.indexOf("gl=") < 0) url = url + "&gl=GB";
        return url;
    }

    private static final Pattern FEATURED = Pattern.compile(
            "<a\\s+href=\"/store/apps/details\\?id=([A-Za-z0-9._]+)\"\\s+aria-label=\"([^\"]+)\"");
    private static final Pattern CARD = Pattern.compile(
            "<a\\s+class=\"[^\"]*\"\\s+href=\"/store/apps/details\\?id=([A-Za-z0-9._]+)\"");
    private static final Pattern ANY_ID = Pattern.compile(
            "/store/apps/details(?:\\?|&amp;|&)id=([A-Za-z0-9._]+)");
    private static final Pattern TITLE_SPAN = Pattern.compile(
            "<span class=\"DdYX5\">([^<]{1,80})</span>");
    private static final Pattern ITEM_RATING = Pattern.compile(
            "itemprop=\"contentRating\"[^>]*>\\s*<span>([^<]+)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEGI = Pattern.compile("PEGI\\s*(3|7|12|16|18)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IARC = Pattern.compile("Rated(?: for)?\\s*(\\d+)\\+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESRB = Pattern.compile(
            "Everyone(?:\\s*10\\+)?|Teen|Mature(?:\\s*17\\+)?|Adults only(?:\\s*18\\+)?", Pattern.CASE_INSENSITIVE);

    /** Parse a Play search HTML page into package/title hits (rating may still be 0). */
    public static List<Hit> parseResults(String html) {
        LinkedHashMap<String, Hit> out = new LinkedHashMap<>();
        if (html == null || html.isEmpty()) return new ArrayList<>();
        Matcher feat = FEATURED.matcher(html);
        while (feat.find()) {
            String pkg = feat.group(1), title = decode(feat.group(2));
            int pegi = ratingIn(html, feat.start(), feat.start() + 5000);
            if (okPkg(pkg)) out.put(pkg, new Hit(pkg, cleanTitle(title, pkg), pegi));
        }
        Matcher cards = CARD.matcher(html);
        while (cards.find()) {
            String pkg = cards.group(1);
            if (!okPkg(pkg) || out.containsKey(pkg)) continue;
            String window = html.substring(cards.start(), Math.min(html.length(), cards.start() + 3500));
            String title = pkg;
            Matcher ts = TITLE_SPAN.matcher(window);
            if (ts.find()) title = decode(ts.group(1));
            else {
                Matcher t2 = Pattern.compile(">([^<]{2,80})<").matcher(window);
                while (t2.find()) {
                    String s = decode(t2.group(1).trim());
                    if (s.isEmpty() || s.equalsIgnoreCase("star") || s.equalsIgnoreCase("stars")) continue;
                    if (s.matches("\\d+(\\.\\d+)?")) continue;
                    title = s; break;
                }
            }
            int pegi = ratingIn(window, 0, window.length());
            out.put(pkg, new Hit(pkg, cleanTitle(title, pkg), pegi));
        }
        if (out.isEmpty()) {
            Matcher any = ANY_ID.matcher(html);
            while (any.find()) {
                String pkg = any.group(1);
                if (okPkg(pkg) && !out.containsKey(pkg)) out.put(pkg, new Hit(pkg, pkg, 0));
            }
        }
        return new ArrayList<>(out.values());
    }

    private static boolean okPkg(String pkg) {
        return pkg != null && pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }

    static String decode(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&#x27;", "'");
    }

    static String cleanTitle(String title, String pkg) {
        if (title == null) return pkg;
        title = decode(title).trim();
        // Featured aria-label sometimes adds a marketing line after a colon; keep a short game name
        // only when it is clearly a slogan ("Minecraft: Dream it, Build it!") — leave "LEGO® Star Wars™: TFA".
        if (title.length() > 60) {
            int c = title.indexOf(':');
            if (c > 2 && c < 40) title = title.substring(0, c).trim();
        }
        return title.isEmpty() ? pkg : title;
    }

    /** First content-rating found in a slice of HTML. 0 if none. */
    static int ratingIn(String html, int from, int to) {
        if (html == null) return 0;
        from = Math.max(0, from); to = Math.min(html.length(), to);
        if (from >= to) return 0;
        String slice = html.substring(from, to);
        Matcher m = ITEM_RATING.matcher(slice);
        if (m.find()) return mapRating(m.group(1));
        return parseContentRating(slice);
    }

    /** Rating from a Play listing (or any HTML snippet). 0 if it cannot be read. */
    public static int parseContentRating(String html) {
        if (html == null || html.isEmpty()) return 0;
        Matcher item = ITEM_RATING.matcher(html);
        if (item.find()) return mapRating(item.group(1));
        Matcher p = PEGI.matcher(html);
        if (p.find()) return mapRating("PEGI " + p.group(1));
        Matcher e = ESRB.matcher(html);
        if (e.find()) return mapRating(e.group(0));
        Matcher i = IARC.matcher(html);
        if (i.find()) return mapRating(i.group(0));
        return 0;
    }

    /**
     * Map a Play content-rating label to a PEGI-like number used by {@link KidsPlay#allowed}.
     * Unknown / unrated → 0 (excluded from Kids until confirmed). Never treats Mature/17+/18 as allowed.
     */
    public static int mapRating(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (s.isEmpty()) return 0;
        if (s.contains("adult") || s.contains("mature") || s.contains("17+") || s.contains("18+")) return 18;
        if (s.contains("pegi 18") || s.contains("usk 18")) return 18;
        if (s.contains("pegi 16") || s.contains("usk 16") || s.contains("teen") || s.contains("16+")) return 16;
        if (s.contains("pegi 12") || s.contains("usk 12") || s.contains("everyone 10") || s.contains("10+") || s.contains("12+")) return 12;
        if (s.contains("pegi 7") || s.contains("usk 6") || s.contains("7+") || s.contains("8+") || s.contains("9+")) return 7;
        if (s.contains("pegi 3") || s.contains("usk 0") || s.contains("usk 6") || s.equals("everyone") || s.contains("3+") || s.contains("4+") || s.contains("0+")) return 3;
        Matcher p = PEGI.matcher(s);
        if (p.find()) return Integer.parseInt(p.group(1));
        return 0;
    }

    /** Fill missing ratings from the bundled catalogue, then drop anything not allowed for {@code maxPegi}. */
    public static List<Hit> filterAllowed(List<Hit> hits, int maxPegi) {
        List<Hit> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (hits == null) return out;
        for (Hit h : hits) {
            if (h == null || !okPkg(h.pkg) || !seen.add(h.pkg)) continue;
            int pegi = h.pegi;
            if (pegi <= 0) {
                KidsPlay.Game g = KidsPlay.byPkg(h.pkg);
                if (g != null) pegi = g.pegi;
            }
            if (pegi <= 0) continue;   // unrated: do not show in Kids
            if (!KidsPlay.allowed(pegi, maxPegi)) continue;
            String title = h.title;
            if (title == null || title.equals(h.pkg)) {
                KidsPlay.Game g = KidsPlay.byPkg(h.pkg);
                if (g != null) title = g.title;
            }
            out.add(new Hit(h.pkg, title, pegi));
            if (out.size() >= MAX_RESULTS) break;
        }
        return out;
    }

    /**
     * Fetch Play search results and age-filter them. Package-private HTTP (same as {@link PlayArt}).
     * Callers run this off the UI thread. Throws on a network / HTTP failure so the caller can fall back.
     */
    public static List<Hit> fetch(String base, String query, int maxPegi) throws Exception {
        if (base == null) base = PlayArt.PLAY;
        String html = PlayArt.get(searchUrl(base, query), 2L << 20);
        List<Hit> raw = parseResults(html);
        List<Hit> rated = new ArrayList<>();
        int fetched = 0;
        for (Hit h : raw) {
            int pegi = h.pegi;
            KidsPlay.Game known = KidsPlay.byPkg(h.pkg);
            if (pegi <= 0 && known != null) pegi = known.pegi;
            if (pegi <= 0 && fetched < MAX_RATING_FETCH && PlayArt.listingUrl(base, h.pkg) != null) {
                fetched++;
                try {
                    String url = PlayArt.listingUrl(base, h.pkg);
                    if (PlayArt.PLAY.equals(base) && url.indexOf("gl=") < 0) url = url + "&gl=GB";
                    pegi = parseContentRating(PlayArt.get(url, 2L << 20));
                } catch (Exception ignored) { pegi = 0; }
            }
            rated.add(h.withPegi(pegi));
        }
        return filterAllowed(rated, maxPegi);
    }

    /**
     * Installed (allowed) first, then Play hits, then bundled-catalogue matches — no duplicates.
     * Titles from the catalogue are preferred when the package is already known.
     */
    public static List<Catalog.Item> merge(List<Catalog.Item> installed, List<Hit> play, List<Catalog.Item> catalog) {
        List<Catalog.Item> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        addAll(out, seen, installed);
        if (play != null) {
            List<Catalog.Item> items = new ArrayList<>();
            for (Hit h : play) items.add(h.item());
            addAll(out, seen, items);
        }
        addAll(out, seen, catalog);
        return out;
    }

    private static void addAll(List<Catalog.Item> out, LinkedHashSet<String> seen, List<Catalog.Item> src) {
        if (src == null) return;
        for (Catalog.Item it : src) {
            if (it == null) continue;
            String pkg = it.storePackage() != null ? it.storePackage() : it.pkg;
            if (pkg == null || pkg.isEmpty() || !seen.add(pkg)) continue;
            out.add(it);
        }
    }
}
