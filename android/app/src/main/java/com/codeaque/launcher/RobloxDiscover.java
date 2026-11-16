package com.codeaque.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 0.10.34 / 0.10.37: public, unauthenticated Roblox explore pages + official fallback.
 *  Kids / Kids 12+ filter on contentMaturity (and a guidelines lookup if it is missing).
 *  No login and no API keys. Plain Java so checks can run on a PC. */
public final class RobloxDiscover {
    private RobloxDiscover() { }

    public static final String EXPLORE_SORTS = "https://apis.roblox.com/explore-api/v1/get-sorts";
    public static final String CACHE_NAME = "roblox-discover.json";
    public static final String GUIDELINES = "https://apis.roblox.com/experience-guidelines-api/experience-guidelines/get-age-recommendation";

    /** Int constants (not a Java enum) so the older D8 dexer can compile this class. */
    public static final class Profile {
        private Profile() { }
        public static final int ALL = 0, KIDS = 1, KIDS_12 = 2;
        public static int kids(String id) { return "kids12".equals(id) ? KIDS_12 : KIDS; }
        public static boolean isKids(int p) { return p != ALL; }
    }

    public static final class Band {
        private Band() { }
        public static final int UNKNOWN = 0, MINIMAL = 1, MILD = 2, TEEN = 3, ADULT = 4;
    }

    /** One explore (or cache) row, with the public age rating when Roblox sent it. */
    public static final class Game {
        public final String placeId, title, universeId, contentMaturity, ageRecommendation;
        public final int minimumAge; // -1 if Roblox did not send one
        public Game(String title, String placeId, String universeId, String contentMaturity, String ageRecommendation, int minimumAge) {
            this.title = title;
            this.placeId = placeId;
            this.universeId = universeId == null ? "" : universeId;
            this.contentMaturity = contentMaturity == null ? "" : contentMaturity;
            this.ageRecommendation = ageRecommendation == null ? "" : ageRecommendation;
            this.minimumAge = minimumAge;
        }
        public Catalog.Item item() { return Catalog.robloxPlace(title, placeId); }
        public Game withRating(String maturity, String rec, int minAge) {
            return new Game(title, placeId, universeId, maturity, rec, minAge);
        }
    }

    public static final class Page {
        public final List<Catalog.Item> items;
        public final List<Game> games;
        public final String nextToken;
        public Page(List<Game> games, String nextToken) {
            this.games = games != null ? games : new ArrayList<Game>();
            this.items = itemsOf(this.games);
            this.nextToken = nextToken != null ? nextToken : "";
        }
        public boolean isEmpty() { return items.isEmpty(); }
    }

    static List<Catalog.Item> itemsOf(List<Game> games) {
        List<Catalog.Item> out = new ArrayList<>();
        if (games == null) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Game g : games) {
            if (g == null || g.placeId == null || g.placeId.isEmpty() || !seen.add(g.placeId)) continue;
            out.add(g.item());
        }
        return out;
    }

    static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT); }

    public static int band(Game g) {
        return g == null ? Band.UNKNOWN : band(g.contentMaturity, g.ageRecommendation, g.minimumAge);
    }

    public static int band(String maturity, String rec, int minAge) {
        String m = norm(maturity), r = norm(rec);
        if (m.contains("restricted") || m.contains("mature") || m.contains("17") || r.contains("17+")
                || r.contains("18+") || r.contains("restricted") || minAge >= 17)
            return Band.ADULT;
        if (m.equals("minimal") || r.contains("minimal") || r.contains("all ages") || r.contains("everyone"))
            return Band.MINIMAL;
        if (m.equals("mild") || r.contains("mild"))
            return Band.MILD;
        if (m.equals("moderate") || m.equals("medium") || r.contains("moderate") || r.contains("12+")
                || r.contains("13+") || (minAge >= 12 && minAge < 17))
            return Band.TEEN;
        return Band.UNKNOWN;
    }

    public static boolean allowed(int p, Game g) {
        if (g == null) return false;
        if (p == Profile.ALL) return true;
        int b = band(g);
        if (p == Profile.KIDS) return b == Band.MINIMAL;
        if (p == Profile.KIDS_12) return b == Band.MINIMAL || b == Band.MILD || b == Band.TEEN;
        return false;
    }

    public static List<Catalog.Item> filter(List<Game> games, int p) {
        List<Catalog.Item> out = new ArrayList<>();
        if (games == null) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Game g : games) {
            if (g == null || g.placeId == null || g.placeId.isEmpty()) continue;
            if (p != Profile.ALL && !allowed(p, g)) continue;
            if (!seen.add(g.placeId)) continue;
            out.add(g.item());
        }
        return out;
    }

    public static boolean hasUsableRatings(List<Game> games) {
        if (games == null) return false;
        for (Game g : games) if (band(g) != Band.UNKNOWN) return true;
        return false;
    }

    public static List<Game> mergeGames(List<Game> have, List<Game> more) {
        List<Game> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (have != null) for (Game g : have) {
            if (g == null || g.placeId == null || g.placeId.isEmpty() || !seen.add(g.placeId)) continue;
            out.add(g);
        }
        if (more != null) for (Game g : more) {
            if (g == null || g.placeId == null || g.placeId.isEmpty() || !seen.add(g.placeId)) continue;
            out.add(g);
        }
        return out;
    }

    public static String newSession() { return UUID.randomUUID().toString(); }

    public static String exploreUrl(String sessionId, String pageToken) {
        String sid = sessionId == null || sessionId.isEmpty() ? newSession() : sessionId;
        String url = EXPLORE_SORTS + "?sessionId=" + enc(sid);
        if (pageToken != null && !pageToken.isEmpty()) url += "&sortsPageToken=" + enc(pageToken);
        return url;
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    public static List<Catalog.Item> fallback() {
        List<Catalog.Item> out = new ArrayList<>();
        for (Catalog.Item i : Catalog.ROBLOX_ITEMS) out.add(i);
        return out;
    }

    /** Parse a get-sorts (or games-only) JSON body. Sponsored rows and paid-access sorts are skipped. */
    public static Page parsePage(String json) {
        if (json == null || json.isEmpty()) return new Page(new ArrayList<Game>(), "");
        Object root = Json.read(json);
        if (!(root instanceof Map)) return new Page(new ArrayList<Game>(), "");
        Map<?, ?> data = (Map<?, ?>) root;
        String token = str(data.get("nextSortsPageToken"));
        if (token.isEmpty()) token = str(data.get("nextPageToken"));
        List<Game> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        Object sorts = data.get("sorts");
        if (sorts instanceof List) {
            for (Object s : (List<?>) sorts) {
                if (!(s instanceof Map)) continue;
                Map<?, ?> sort = (Map<?, ?>) s;
                String type = str(sort.get("contentType"));
                if (!type.isEmpty() && !type.equals("Games") && !type.equals("Game")) continue;
                String sortId = str(sort.get("sortId")).toLowerCase(java.util.Locale.ROOT);
                if (sortId.contains("paid-access") || sortId.contains("paidaccess")) continue;
                Object games = sort.get("games");
                if (!(games instanceof List)) games = sort.get("content");
                addGames(out, seen, games);
            }
        } else if (data.get("games") instanceof List) {
            addGames(out, seen, data.get("games"));
        }
        return new Page(out, token);
    }

    static void addGames(List<Game> out, java.util.Set<String> seen, Object games) {
        if (!(games instanceof List)) return;
        for (Object g : (List<?>) games) {
            if (!(g instanceof Map)) continue;
            Map<?, ?> game = (Map<?, ?>) g;
            if (truthy(game.get("isSponsored"))) continue;
            String place = digits(str(first(game, "rootPlaceId", "placeId", "rootPlaceID")));
            if (place.isEmpty() || !seen.add(place)) continue;
            String title = str(first(game, "name", "title")).trim();
            if (title.isEmpty()) continue;
            String uni = digits(str(first(game, "universeId", "universeID", "id")));
            String mat = str(first(game, "contentMaturity", "content_maturity"));
            String rec = str(first(game, "ageRecommendationDisplayName", "ageRecommendation", "displayNameWithHeaderShort"));
            int minAge = intOr(first(game, "minimumAge", "minimum_age"), -1);
            out.add(new Game(title, place, uni, mat, rec, minAge));
        }
    }

    static int intOr(Object v, int fallback) {
        if (v == null) return fallback;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(str(v).trim()); } catch (Exception e) { return fallback; }
    }

    public static List<Catalog.Item> merge(List<Catalog.Item> have, List<Catalog.Item> more) {
        List<Catalog.Item> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (have != null) {
            for (Catalog.Item i : have) {
                String id = Catalog.robloxPlaceId(i);
                if (id == null || !seen.add(id)) continue;
                out.add(i);
            }
        }
        if (more != null) {
            for (Catalog.Item i : more) {
                String id = Catalog.robloxPlaceId(i);
                if (id == null || !seen.add(id)) continue;
                out.add(i);
            }
        }
        return out;
    }

    public static Page fetchPage(String sessionId, String pageToken) {
        return fetchPage(sessionId, pageToken, false);
    }

    public static Page fetchPage(String sessionId, String pageToken, boolean lookupRatings) {
        try {
            String body = StoreArt.get(exploreUrl(sessionId, pageToken), 2_000_000);
            Page page = parsePage(body);
            if (lookupRatings) page = fillRatings(page);
            return page;
        } catch (Exception e) {
            return new Page(new ArrayList<Game>(), "");
        }
    }

    /** If the explore page had no ratings, look each universe up on the public guidelines API. */
    public static Page fillRatings(Page page) {
        if (page == null || page.games.isEmpty() || hasUsableRatings(page.games)) return page;
        List<Game> out = new ArrayList<>();
        int tried = 0, ok = 0;
        for (Game g : page.games) {
            if (band(g) != Band.UNKNOWN || g.universeId.isEmpty() || tried >= 30) { out.add(g); continue; }
            tried++;
            try {
                String body = postJson(GUIDELINES, "{\"universeId\":" + g.universeId + "}", 200_000);
                Game rated = applyGuidelines(g, body);
                if (band(rated) != Band.UNKNOWN) ok++;
                out.add(rated);
            } catch (Exception e) { out.add(g); }
        }
        if (ok == 0) return page; // leave unchanged so the caller can fall back to the official list
        return new Page(out, page.nextToken);
    }

    public static Game applyGuidelines(Game g, String json) {
        if (g == null || json == null || json.isEmpty()) return g;
        Object root = Json.read(json);
        if (!(root instanceof Map)) return g;
        Map<?, ?> rec = mapPath((Map<?, ?>) root, "ageRecommendationDetails", "summary", "ageRecommendation");
        if (rec == null) rec = mapPath((Map<?, ?>) root, "ageRecommendationDetails", "ageRecommendation");
        if (rec == null) return g;
        String mat = str(first(rec, "contentMaturity", "content_maturity"));
        String name = str(first(rec, "displayNameWithHeaderShort", "displayName", "ageRecommendationDisplayName"));
        int minAge = intOr(first(rec, "minimumAge", "minimum_age"), -1);
        if (mat.isEmpty() && name.isEmpty() && minAge < 0) return g;
        return g.withRating(mat, name, minAge);
    }

    static Map<?, ?> mapPath(Map<?, ?> m, String... keys) {
        Object cur = m;
        for (String k : keys) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(k);
        }
        return cur instanceof Map ? (Map<?, ?>) cur : null;
    }

    static String postJson(String url, String body, long max) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("User-Agent", StoreArt.UA);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/json");
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        c.setRequestProperty("Content-Length", Integer.toString(raw.length));
        try {
            try (java.io.OutputStream os = c.getOutputStream()) { os.write(raw); }
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (code != 200 || in == null) throw new java.io.IOException("HTTP " + code);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n; long t = 0;
            while ((n = in.read(b)) > 0) { o.write(b, 0, n); t += n; if (t > max) throw new java.io.IOException("too big"); }
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    public static List<Catalog.Item> loadCache(File file) {
        if (file == null || !file.isFile()) return new ArrayList<>();
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(file.toPath());
            if (raw.length < 8 || raw.length > 2_000_000) return new ArrayList<>();
            Object root = Json.read(new String(raw, StandardCharsets.UTF_8));
            if (!(root instanceof Map)) return new ArrayList<>();
            Object items = ((Map<?, ?>) root).get("items");
            if (!(items instanceof List)) return new ArrayList<>();
            return itemsOf(gamesFromCache(items));
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public static List<Game> loadGames(File file) {
        if (file == null || !file.isFile()) return new ArrayList<>();
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(file.toPath());
            if (raw.length < 8 || raw.length > 2_000_000) return new ArrayList<>();
            Object root = Json.read(new String(raw, StandardCharsets.UTF_8));
            if (!(root instanceof Map)) return new ArrayList<>();
            Object items = ((Map<?, ?>) root).get("items");
            if (!(items instanceof List)) return new ArrayList<>();
            return gamesFromCache(items);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    static List<Game> gamesFromCache(Object items) {
        List<Game> out = new ArrayList<>();
        if (!(items instanceof List)) return out;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object it : (List<?>) items) {
            if (!(it instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) it;
            String place = digits(str(first(m, "placeId", "place_id", "id")));
            String title = str(first(m, "title", "name")).trim();
            if (place.isEmpty() || title.isEmpty() || !seen.add(place)) continue;
            String uni = digits(str(first(m, "universeId", "universe_id")));
            String mat = str(first(m, "contentMaturity", "content_maturity"));
            String rec = str(first(m, "ageRecommendation", "ageRecommendationDisplayName"));
            int minAge = intOr(first(m, "minimumAge", "minimum_age"), -1);
            out.add(new Game(title, place, uni, mat, rec, minAge));
        }
        return out;
    }

    public static boolean saveCache(File file, List<Catalog.Item> items) {
        List<Game> games = new ArrayList<>();
        if (items != null) {
            for (Catalog.Item it : items) {
                String place = Catalog.robloxPlaceId(it);
                if (place == null) continue;
                games.add(new Game(it.label, place, "", "", "", -1));
            }
        }
        return saveGames(file, games);
    }

    public static boolean saveGames(File file, List<Game> games) {
        if (file == null || games == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"items\":[");
            boolean first = true;
            for (Game g : games) {
                if (g == null || g.placeId == null || g.placeId.isEmpty()) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"title\":").append(Json.quote(g.title))
                  .append(",\"placeId\":").append(Json.quote(g.placeId));
                if (!g.universeId.isEmpty()) sb.append(",\"universeId\":").append(Json.quote(g.universeId));
                if (!g.contentMaturity.isEmpty()) sb.append(",\"contentMaturity\":").append(Json.quote(g.contentMaturity));
                if (!g.ageRecommendation.isEmpty()) sb.append(",\"ageRecommendation\":").append(Json.quote(g.ageRecommendation));
                if (g.minimumAge >= 0) sb.append(",\"minimumAge\":").append(g.minimumAge);
                sb.append('}');
            }
            sb.append("]}");
            File tmp = new File(file.getPath() + ".part");
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(sb.toString());
            }
            if (file.exists() && !file.delete()) { tmp.delete(); return false; }
            return tmp.renameTo(file);
        } catch (Exception e) { return false; }
    }

    static Object first(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            if (m.containsKey(k) && m.get(k) != null) return m.get(k);
        }
        return null;
    }
    static String str(Object v) { return v == null ? "" : String.valueOf(v); }
    static String digits(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') b.append(c);
        }
        return b.toString();
    }
    static boolean truthy(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        return "true".equalsIgnoreCase(str(v));
    }

    /** Tiny JSON reader (objects, arrays, strings, numbers, booleans, null). */
    static final class Json {
        static Object read(String s) { return new P(s).parse(); }
        static String quote(String s) {
            if (s == null) return "\"\"";
            StringBuilder b = new StringBuilder(s.length() + 2);
            b.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c == '\n') b.append("\\n");
                else if (c == '\r') b.append("\\r");
                else if (c == '\t') b.append("\\t");
                else if (c < 32) b.append(String.format("\\u%04x", (int) c));
                else b.append(c);
            }
            return b.append('"').toString();
        }
        static final class P {
            final String s; int i, n;
            P(String s) { this.s = s == null ? "" : s; this.n = this.s.length(); }
            Object parse() { Object v = val(); skip(); return v; }
            Object val() {
                skip();
                if (i >= n) return null;
                char c = s.charAt(i);
                if (c == '{') return obj();
                if (c == '[') return arr();
                if (c == '"') return str();
                if (c == 't' && n - i >= 4 && s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
                if (c == 'f' && n - i >= 5 && s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
                if (c == 'n' && n - i >= 4 && s.startsWith("null", i)) { i += 4; return null; }
                return num();
            }
            Map<String, Object> obj() {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                while (i < n) {
                    skip();
                    if (i < n && s.charAt(i) == '}') { i++; break; }
                    String k = str();
                    skip();
                    if (i < n && s.charAt(i) == ':') i++;
                    m.put(k, val());
                }
                return m;
            }
            List<Object> arr() {
                List<Object> a = new ArrayList<>();
                i++;
                while (i < n) {
                    skip();
                    if (i < n && s.charAt(i) == ']') { i++; break; }
                    a.add(val());
                }
                return a;
            }
            String str() {
                if (i < n && s.charAt(i) == '"') i++;
                StringBuilder b = new StringBuilder();
                while (i < n) {
                    char c = s.charAt(i++);
                    if (c == '"') break;
                    if (c == '\\' && i < n) {
                        char e = s.charAt(i++);
                        if (e == 'u' && i + 4 <= n) {
                            try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                            catch (NumberFormatException ignored) { }
                            i += 4;
                        } else if (e == 'n') b.append('\n');
                        else if (e == 'r') b.append('\r');
                        else if (e == 't') b.append('\t');
                        else b.append(e);
                    } else b.append(c);
                }
                return b.toString();
            }
            Object num() {
                int start = i;
                if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
                while (i < n) {
                    char c = s.charAt(i);
                    if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') i++;
                    else break;
                }
                String t = s.substring(start, i);
                try {
                    if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) return Double.valueOf(t);
                    long v = Long.parseLong(t);
                    if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return Integer.valueOf((int) v);
                    return Long.valueOf(v);
                } catch (NumberFormatException e) { return t; }
            }
            void skip() {
                while (i < n) {
                    char c = s.charAt(i);
                    if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',') i++;
                    else break;
                }
            }
        }
    }
}
