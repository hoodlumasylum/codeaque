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

/** 0.10.34: public, unauthenticated Roblox explore pages + official fallback.
 *  No login and no API keys. Plain Java so checks can run on a PC. */
public final class RobloxDiscover {
    private RobloxDiscover() { }

    public static final String EXPLORE_SORTS = "https://apis.roblox.com/explore-api/v1/get-sorts";
    public static final String CACHE_NAME = "roblox-discover.json";

    public static final class Page {
        public final List<Catalog.Item> items;
        public final String nextToken;
        public Page(List<Catalog.Item> items, String nextToken) {
            this.items = items != null ? items : new ArrayList<Catalog.Item>();
            this.nextToken = nextToken != null ? nextToken : "";
        }
        public boolean isEmpty() { return items.isEmpty(); }
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
        if (json == null || json.isEmpty()) return new Page(new ArrayList<Catalog.Item>(), "");
        Object root = Json.read(json);
        if (!(root instanceof Map)) return new Page(new ArrayList<Catalog.Item>(), "");
        Map<?, ?> data = (Map<?, ?>) root;
        String token = str(data.get("nextSortsPageToken"));
        if (token.isEmpty()) token = str(data.get("nextPageToken"));
        List<Catalog.Item> out = new ArrayList<>();
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

    static void addGames(List<Catalog.Item> out, java.util.Set<String> seen, Object games) {
        if (!(games instanceof List)) return;
        for (Object g : (List<?>) games) {
            if (!(g instanceof Map)) continue;
            Map<?, ?> game = (Map<?, ?>) g;
            if (truthy(game.get("isSponsored"))) continue;
            String place = digits(str(first(game, "rootPlaceId", "placeId", "rootPlaceID")));
            if (place.isEmpty() || !seen.add(place)) continue;
            String title = str(first(game, "name", "title")).trim();
            if (title.isEmpty()) continue;
            out.add(Catalog.robloxPlace(title, place));
        }
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
        try {
            String body = StoreArt.get(exploreUrl(sessionId, pageToken), 2_000_000);
            return parsePage(body);
        } catch (Exception e) {
            return new Page(new ArrayList<Catalog.Item>(), "");
        }
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
            List<Catalog.Item> out = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Object it : (List<?>) items) {
                if (!(it instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) it;
                String place = digits(str(first(m, "placeId", "place_id", "id")));
                String title = str(first(m, "title", "name")).trim();
                if (place.isEmpty() || title.isEmpty() || !seen.add(place)) continue;
                out.add(Catalog.robloxPlace(title, place));
            }
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public static boolean saveCache(File file, List<Catalog.Item> items) {
        if (file == null || items == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"items\":[");
            boolean first = true;
            for (Catalog.Item it : items) {
                String place = Catalog.robloxPlaceId(it);
                if (place == null) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"title\":").append(Json.quote(it.label))
                  .append(",\"placeId\":").append(Json.quote(place)).append('}');
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
