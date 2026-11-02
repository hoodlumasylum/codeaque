package com.codeaque.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain-Java parts of the APK self-update (unit-tested on a PC): versions, the release JSON, SHA256SUMS.txt. */
public final class UpdateInfo {
    public static final String API = "https://api.github.com/repos/hoodlumasylum/codeaque/releases/latest";
    public static final String APK = "codeaque-launcher.apk";

    public final String version, apkUrl, sumsUrl;
    UpdateInfo(String version, String apkUrl, String sumsUrl) { this.version = version; this.apkUrl = apkUrl; this.sumsUrl = sumsUrl; }

    static int[] parts(String v) {
        String[] p = v.replaceFirst("^[vV]", "").split("[^0-9]+");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < p.length; i++) try { out[i] = Integer.parseInt(p[i]); } catch (NumberFormatException ignored) { }
        return out;
    }

    public static boolean newer(String latest, String current) {
        int[] a = parts(latest), b = parts(current);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    /** Reads tag_name and the APK + SHA256SUMS.txt download links from GitHub's release JSON (no JSON library needed). */
    public static UpdateInfo parse(String json) {
        Matcher t = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!t.find()) return null;
        String apk = null, sums = null;
        Matcher u = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        while (u.find()) {
            String url = u.group(1);
            if (url.endsWith("/" + APK)) apk = url;
            else if (url.endsWith("/SHA256SUMS.txt")) sums = url;
        }
        if (apk == null || sums == null) return null;
        return new UpdateInfo(t.group(1).replaceFirst("^[vV]", ""), apk, sums);
    }

    /** The expected SHA-256 of the APK from SHA256SUMS.txt ("<hash>  codeaque-launcher.apk"), or null. */
    public static String expectedSha(String sums) {
        for (String line : sums.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 2 && f[1].replaceFirst("^\\*", "").equals(APK) && f[0].matches("[0-9a-fA-F]{64}")) return f[0].toLowerCase();
        }
        return null;
    }

    private UpdateInfo() { this(null, null, null); }
}
