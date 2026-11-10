package com.codeaque.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 0.10.32: official Steam covers and Roblox thumbnails, fetched at runtime and cached.
 *  Nothing official is bundled. Plain Java so checks can run against a mock server. */
public final class StoreArt {
    private StoreArt() { }

    public static final String STEAM = "https://cdn.cloudflare.steamstatic.com/steam/apps/";
    public static final String ROBLOX_THUMB = "https://thumbnails.roblox.com/v1/places/gameicons"
            + "?placeIds=%s&returnPolicy=PlaceHolder&size=512x512&format=Png&isCircular=false";

    static boolean steamHost(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        String host = url.substring(8).split("/")[0].toLowerCase();
        return host.equals("cdn.cloudflare.steamstatic.com") || host.equals("steamcdn-a.akamaihd.net")
                || host.equals("cdn.akamai.steamstatic.com") || host.endsWith(".steamstatic.com");
    }

    static boolean robloxHost(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        String host = url.substring(8).split("/")[0].toLowerCase();
        return host.equals("thumbnails.roblox.com") || host.equals("tr.rbxcdn.com") || host.endsWith(".rbxcdn.com");
    }

    public static File steamFile(File dir, String appid) { return new File(dir, "steam-" + appid + ".jpg"); }
    public static File robloxFile(File dir, String placeId) { return new File(dir, "roblox-" + placeId + ".png"); }

    public static boolean fetchSteam(String appid, File dir) {
        if (appid == null || !appid.matches("\\d+")) return false;
        dir.mkdirs();
        File dest = steamFile(dir, appid);
        if (dest.isFile() && dest.length() > 200) return true;
        for (String name : new String[]{"library_600x900.jpg", "header.jpg"}) {
            String url = STEAM + appid + "/" + name;
            if (save(url, dest) && dest.length() > 200) return true;
        }
        return false;
    }

    public static boolean fetchRoblox(String placeId, File dir) {
        if (placeId == null || !placeId.matches("\\d+")) return false;
        dir.mkdirs();
        File dest = robloxFile(dir, placeId);
        if (dest.isFile() && dest.length() > 200) return true;
        try {
            String json = get(String.format(ROBLOX_THUMB, placeId), 200_000);
            String url = parseThumb(json);
            if (url == null) return false;
            return save(url, dest) && dest.length() > 200;
        } catch (Exception e) { return false; }
    }

    /** Pull imageUrl from the public thumbnail API JSON. */
    public static String parseThumb(String json) {
        if (json == null) return null;
        int k = json.indexOf("\"imageUrl\"");
        if (k < 0) return null;
        int q = json.indexOf('"', json.indexOf(':', k) + 1);
        if (q < 0) return null;
        int q2 = json.indexOf('"', q + 1);
        if (q2 < 0) return null;
        String url = json.substring(q + 1, q2).replace("\\u0026", "&").replace("\\/", "/");
        return robloxHost(url) ? url : null;
    }

    static String get(String url, long max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "CodeAque/0.10.34");
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) { o.write(b, 0, n); if (o.size() > max) throw new java.io.IOException("too big"); }
            return o.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    public static boolean save(String url, File f) {
        if (url == null || !(steamHost(url) || robloxHost(url))) return false;
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
