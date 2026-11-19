package com.codeaque.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fav Sites bookmarks for the Android home. Plain Java so tests can call it without Android.
 * Favicons are fetched once and stored under the given root; a missing file means the globe icon.
 * No third-party logos are bundled.
 */
public final class FavSites {
    public static final int MAX = 80;
    public static final String DDG = "https://duckduckgo.com/";
    private static final Pattern URL_IN_JSON = Pattern.compile("\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern TITLE_IN_JSON = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern ID_IN_JSON = Pattern.compile("\"id\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern ICON_IN_JSON = Pattern.compile("\"icon\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern OBJECT = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);

    public static final class Site {
        public final String id, title, url, icon;
        public Site(String id, String title, String url, String icon) {
            this.id = id; this.title = title; this.url = url; this.icon = icon == null ? "" : icon;
        }
    }

    public static File storeFile(File root) { return new File(root, "sites.json"); }
    public static File iconsDir(File root) { return new File(root, "icons"); }

    public static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        if (text.contains(" ") && !text.contains("://") && !text.toLowerCase(Locale.ROOT).startsWith("www.")) return null;
        if (!text.contains("://")) text = "https://" + text;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")
                || lower.startsWith("about:") || lower.startsWith("chrome:")) return null;
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null;
        try {
            URL u = new URL(text);
            String host = u.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || !host.contains(".")) return null;
            return text;
        } catch (Exception e) { return null; }
    }

    public static String hostOf(String url) {
        try {
            String h = new URL(url).getHost();
            if (h == null) return "";
            h = h.toLowerCase(Locale.ROOT);
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) { return ""; }
    }

    public static String defaultTitle(String url) {
        String h = hostOf(url);
        return h.isEmpty() ? "Site" : h;
    }

    public static String siteId(String url) {
        String n = normalizeUrl(url);
        if (n == null) n = url == null ? "" : url;
        String h = hostOf(n);
        String slug = h.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.length() > 40) slug = slug.substring(0, 40);
        if (slug.isEmpty()) slug = "site";
        return slug + "-" + Integer.toHexString(n.hashCode());
    }

    public static boolean isDuckDuckGo(String url) {
        String h = hostOf(url);
        return h.equals("duckduckgo.com") || h.endsWith(".duckduckgo.com");
    }

    public static List<Site> list(File root) {
        List<Site> out = new ArrayList<>();
        File f = storeFile(root);
        if (!f.isFile()) return out;
        String json;
        try { json = readUtf8(f); } catch (Exception e) { return out; }
        Matcher obj = OBJECT.matcher(json);
        while (obj.find()) {
            String block = obj.group();
            String url = unquote(find(URL_IN_JSON, block));
            url = normalizeUrl(url);
            if (url == null) continue;
            String id = unquote(find(ID_IN_JSON, block));
            if (id == null || id.isEmpty()) id = siteId(url);
            String title = unquote(find(TITLE_IN_JSON, block));
            if (title == null || title.isEmpty()) title = defaultTitle(url);
            String icon = unquote(find(ICON_IN_JSON, block));
            if (icon == null) icon = "";
            if (!icon.isEmpty() && !new File(icon).isFile()) icon = "";
            out.add(new Site(id, title, url, icon));
        }
        return out;
    }

    public static Site get(File root, String id) {
        if (id == null) return null;
        for (Site s : list(root)) if (id.equals(s.id)) return s;
        return null;
    }

    public static Site add(File root, String url, String title) {
        url = normalizeUrl(url);
        if (url == null) return null;
        if (!root.exists() && !root.mkdirs()) return null;
        List<Site> sites = list(root);
        String id = siteId(url);
        String name = title == null || title.trim().isEmpty() ? defaultTitle(url) : title.trim();
        Site existing = null;
        for (Site s : sites) if (id.equals(s.id) || url.equals(s.url)) { existing = s; break; }
        Site site;
        if (existing != null) {
            site = new Site(existing.id, name, url, existing.icon);
            for (int i = 0; i < sites.size(); i++) if (sites.get(i).id.equals(existing.id)) sites.set(i, site);
        } else {
            if (sites.size() >= MAX) sites.remove(0);
            site = new Site(id, name, url, iconPathIfPresent(root, id));
            sites.add(site);
        }
        write(root, sites);
        return site;
    }

    public static boolean remove(File root, String id) {
        List<Site> sites = list(root);
        List<Site> kept = new ArrayList<>();
        for (Site s : sites) if (!s.id.equals(id)) kept.add(s);
        if (kept.size() == sites.size()) return false;
        write(root, kept);
        return true;
    }

    public static File iconFile(File root, String id) {
        File dir = iconsDir(root);
        if (!dir.isDirectory()) return new File(dir, id + ".png");
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.getName().startsWith(id + ".") && f.isFile() && f.length() > 0) return f;
        return new File(dir, id + ".png");
    }

    /** Download a favicon once. Returns the file, or null (caller shows a globe). */
    public static File fetchIcon(File root, String url, String id) {
        String n = normalizeUrl(url);
        if (n == null) return null;
        File have = iconFile(root, id);
        if (have.isFile() && have.length() > 0) return have;
        File dir = iconsDir(root);
        if (!dir.exists() && !dir.mkdirs()) return null;
        String host = hostOf(n);
        String[] candidates = new String[] {
            origin(n) + "/favicon.ico",
            "https://icons.duckduckgo.com/ip3/" + host + ".ico"
        };
        for (String href : candidates) {
            byte[] data = download(href);
            if (data == null || data.length < 16) continue;
            String ext = looksPng(data) ? ".png" : ".ico";
            File dest = new File(dir, id + ext);
            try (FileOutputStream out = new FileOutputStream(dest)) { out.write(data); }
            catch (Exception e) { continue; }
            if (dest.isFile() && dest.length() > 0) return dest;
        }
        return null;
    }

    public static List<Catalog.Item> items(File root) {
        List<Catalog.Item> out = new ArrayList<>();
        out.add(Catalog.DUCKDUCKGO);
        out.add(Catalog.ADD_FAV);
        for (Site s : list(root)) out.add(Catalog.favItem(s.title, s.url, s.id, s.icon));
        return out;
    }

    public static Catalog.Category category(File root) {
        List<Catalog.Item> items = items(root);
        return new Catalog.Category("favsites", "Fav Sites", "globe", items.toArray(new Catalog.Item[0]));
    }

    static String origin(String url) {
        try {
            URL u = new URL(url);
            int port = u.getPort();
            return u.getProtocol() + "://" + u.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) { return url; }
    }

    private static String iconPathIfPresent(File root, String id) {
        File f = iconFile(root, id);
        return f.isFile() ? f.getAbsolutePath() : "";
    }

    private static void write(File root, List<Site> sites) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schema\":1,\"sites\":[");
        for (int i = 0; i < sites.size(); i++) {
            Site s = sites.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"").append(esc(s.id)).append("\",\"title\":\"").append(esc(s.title))
              .append("\",\"url\":\"").append(esc(s.url)).append("\",\"icon\":\"").append(esc(s.icon)).append("\"}");
        }
        sb.append("]}");
        File f = storeFile(root);
        File tmp = new File(root, "sites.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(sb.toString().getBytes("UTF-8"));
        } catch (Exception e) { return; }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(f);
        }
    }

    private static byte[] download(String href) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(href).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CodeAque/1.0");
            c.connect();
            if (c.getResponseCode() >= 400) return null;
            String ct = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
            if (ct.startsWith("text/")) return null;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] b = new byte[4096];
                int n, total = 0;
                while ((n = in.read(b)) > 0 && total < 256_000) { buf.write(b, 0, n); total += n; }
                return buf.toByteArray();
            }
        } catch (Exception e) { return null; }
        finally { if (c != null) c.disconnect(); }
    }

    private static boolean looksPng(byte[] d) {
        return d.length >= 8 && d[0] == (byte) 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
    }

    private static String readUtf8(File f) throws Exception {
        byte[] b = new byte[(int) Math.min(f.length(), 1_000_000)];
        try (FileInputStream in = new FileInputStream(f)) { int n = in.read(b); return new String(b, 0, Math.max(0, n), "UTF-8"); }
    }
    private static String find(Pattern p, String s) { Matcher m = p.matcher(s); return m.find() ? m.group(1) : null; }
    private static String unquote(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
