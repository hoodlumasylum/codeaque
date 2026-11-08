package com.codeaque.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain-Java parts of the APK self-update (unit-tested on a PC): versions, the release JSON, SHA256SUMS.txt. */
public final class UpdateInfo {
    public static final String API = "https://api.github.com/repos/hoodlumasylum/codeaque/releases/latest";
    public static final String APK = "codeaque-launcher.apk";

    public final String version, apkUrl, sumsUrl;
    public String notes = "";
    /** 0.10.20: the APK asset's own SHA-256 from the release JSON ("digest"), and its API download URL; fallbacks when the
     *  github.com download host fails (it was returning 504s for SHA256SUMS.txt). Either may be null. */
    public String apkDigest, apkApiUrl;   // 0.10.6: the release notes (GitHub "body"), for the pop-up's what's-new line
    UpdateInfo(String version, String apkUrl, String sumsUrl) { this.version = version; this.apkUrl = apkUrl; this.sumsUrl = sumsUrl; }

    /** 0.10.6: one short line for the update pop-up: the first real line of the notes, without markdown. */
    public static String whatsNew(String notes) {
        if (notes == null) return "";
        for (String line : notes.split("\\r?\\n")) {
            String t = line.trim(), low = t.toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("```") || low.startsWith("untested") || low.startsWith("the apk") || low.startsWith("check downloads")) continue;
            t = t.replaceFirst("^[-*+]\\s+", "").replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1").replace("**", "").replace("`", "").replace("__", "").trim();
            if (t.isEmpty()) continue;
            return t.length() <= 140 ? t : t.substring(0, 139).trim() + "\u2026";
        }
        return "";
    }

    static String unescape(String s) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { o.append(c); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': o.append('\n'); break; case 'r': break; case 't': o.append(' '); break;
                case 'u': if (i + 4 < s.length()) { try { o.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; } catch (NumberFormatException e) { o.append('u'); } } break;
                default: o.append(n);
            }
        }
        return o.toString();
    }

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
        UpdateInfo info = new UpdateInfo(t.group(1).replaceFirst("^[vV]", ""), apk, sums);
        String[] chunks = json.split("\"browser_download_url\"");
        for (int i = 1; i < chunks.length; i++) {
            Matcher bu = Pattern.compile("^\\s*:\\s*\"([^\"]+)\"").matcher(chunks[i]);
            if (!bu.find() || !bu.group(1).endsWith("/" + APK)) continue;
            String before = chunks[i - 1];
            Matcher d = Pattern.compile("\"digest\"\\s*:\\s*\"sha256:([0-9a-fA-F]{64})\"").matcher(before);
            while (d.find()) info.apkDigest = d.group(1).toLowerCase();
            Matcher a = Pattern.compile("\"url\"\\s*:\\s*\"(https://api\\.github\\.com/repos/[^\"]+/releases/assets/\\d+)\"").matcher(before);
            while (a.find()) info.apkApiUrl = a.group(1);
        }
        Matcher b = Pattern.compile("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (b.find()) info.notes = unescape(b.group(1));
        return info;
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
