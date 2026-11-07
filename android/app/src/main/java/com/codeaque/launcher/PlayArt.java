package com.codeaque.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 0.10.3: pictures for Play games that aren't installed, taken at runtime from the game's own official Google Play
 *  listing (never bundled). Plain Java so the build-time checks can run it against a local mock server. */
public final class PlayArt {
    private PlayArt() { }

    public static final String PLAY = "https://play.google.com";
    public static final String IMG_HOST = "https://play-lh.googleusercontent.com/";

    public static String listingUrl(String base, String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) return null;
        return base + "/store/apps/details?id=" + pkg + "&hl=en_GB";
    }

    /** HTTPS to Google's image host only; plain http only for a loopback test server. */
    static boolean allowed(String url, boolean test) {
        if (url == null) return false;
        if (url.startsWith(IMG_HOST)) return true;
        return test && (url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:"));
    }

    private static final Pattern OG = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"");
    private static final Pattern FEATURE = Pattern.compile("<img[^>]*\\bsrc=\"([^\"]+)\"[^>]*\\balt=\"Feature graphic\"");

    /** {feature graphic or null, icon or null}. The icon is the listing's own og:image. The feature graphic is used only
     *  when the page marks it as such (other wide pictures on a listing can belong to other apps). */
    public static String[] parse(String html, boolean test) {
        String f = null, i = null;
        if (html != null) {
            Matcher m = FEATURE.matcher(html); if (m.find()) f = m.group(1).replace("&amp;", "&");
            m = OG.matcher(html); if (m.find()) i = m.group(1).replace("&amp;", "&");
        }
        if (f == null && i != null) f = ownFeature(html, i);
        if (!allowed(f, test)) f = null;
        if (!allowed(i, test)) i = null;
        if (i != null && i.startsWith(IMG_HOST)) i = i.replaceAll("=[^/=]*$", "") + "=s256";
        if (f != null && f.startsWith(IMG_HOST)) f = f.replaceAll("=[^/=]*$", "") + "=w640-h312";
        return new String[]{f, i};
    }

    private static final Pattern WIDE = Pattern.compile("\\[null,\\d+,\\[(?:500,1024|1024,500)\\],\\[null,null,\"([^\"]+)\"");
    /** 0.10.6: the listing's own 1024x500 feature graphic. In the page data it comes right after the app's own icon
     *  (the og:image); other apps' graphics on the page follow their own icons, so only that spot is trusted. */
    static String ownFeature(String html, String iconUrl) {
        String id = iconUrl.replaceAll("=[^/=]*$", "");
        int at = id.lastIndexOf('/'); if (at < 0) return null;
        String key = id.substring(at + 1); if (key.length() < 12) return null;
        int pos = html.indexOf(key + "\"");
        while (pos >= 0 && !html.startsWith("[null,null,\"", Math.max(0, html.lastIndexOf("[null,null,\"", pos)))) pos = html.indexOf(key + "\"", pos + 1);
        if (pos < 0) return null;
        Matcher m = WIDE.matcher(html); m.region(pos, Math.min(html.length(), pos + 600));
        return m.find() ? m.group(1).replace("\\u003d", "=") : null;
    }

    public static File iconFile(File dir, String pkg) { return new File(dir, pkg + ".icon.png"); }
    public static File featureFile(File dir, String pkg) { return new File(dir, pkg + ".feature.png"); }
    public static File missFile(File dir, String pkg) { return new File(dir, pkg + ".miss"); }

    /** Fetch the listing once and cache its pictures in dir. Returns true when at least the icon was saved.
     *  Offline or on any error nothing is saved and the tile keeps CodeAque's own icon and label. */
    public static boolean fetch(String base, String pkg, File dir) {
        boolean test = !PLAY.equals(base);
        if (test && !allowed(base + "/", true)) return false;
        String url = listingUrl(base, pkg);
        if (url == null) return false;
        dir.mkdirs();
        try {
            String[] art = parse(get(url, 2L << 20), test);
            if (art[1] == null) { missFile(dir, pkg).createNewFile(); return false; }
            boolean ok = save(art[1], iconFile(dir, pkg));
            if (art[0] != null) save(art[0], featureFile(dir, pkg));
            return ok;
        } catch (Exception e) { return false; }
    }

    static String get(String url, long max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36");
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) { o.write(b, 0, n); if (o.size() > max) throw new java.io.IOException("too big"); }
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    public static boolean save(String url, File f) {
        File tmp = new File(f.getPath() + ".part");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(15000);
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                if (c.getResponseCode() != 200) return false;
                byte[] b = new byte[16384]; int n; long t = 0;
                while ((n = in.read(b)) > 0) { out.write(b, 0, n); t += n; if (t > 4L << 20) throw new java.io.IOException("too big"); }
            } finally { c.disconnect(); }
            return tmp.length() > 0 && tmp.renameTo(f);
        } catch (Exception e) { tmp.delete(); return false; }
    }
}
