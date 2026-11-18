package com.codeaque.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 0.10.54: optional short fact from Simple English Wikipedia when a Kids question
 * is not in the local set. No API keys. Parse/trim helpers are plain Java for CatalogTest.
 * Grown-ups credit this in About (CC BY-SA); kids answers never name the source.
 */
public final class NovaWiki {
    private NovaWiki() {}

    public static final String SEARCH =
            "https://simple.wikipedia.org/w/api.php?action=opensearch&limit=1&namespace=0&format=json&search=";
    public static final String SUMMARY_PREFIX =
            "https://simple.wikipedia.org/api/rest_v1/page/summary/";
    public static final int TIMEOUT_MS = 4000;
    public static final String USER_AGENT =
            "CodeAque/0.10.63 (Kids helper; https://github.com/hoodlumasylum/codeaque)";

    /** OpenSearch JSON: the first title in the second array, or null. */
    public static String firstOpenSearchTitle(String json) {
        if (json == null) return null;
        int first = json.indexOf('[');
        if (first < 0) return null;
        int second = json.indexOf('[', first + 1);
        if (second < 0) return null;
        String t = nextJsonString(json, second + 1);
        if (t == null) return null;
        t = t.trim();
        return t.isEmpty() ? null : t;
    }

    /** REST summary JSON: the plain "extract" field (not extract_html). */
    public static String extractExtract(String summaryJson) {
        if (summaryJson == null) return null;
        int i = 0;
        while ((i = summaryJson.indexOf("\"extract\"", i)) >= 0) {
            int j = i + 9;
            while (j < summaryJson.length() && Character.isWhitespace(summaryJson.charAt(j))) j++;
            if (j < summaryJson.length() && summaryJson.charAt(j) == ':') {
                String v = nextJsonString(summaryJson, j + 1);
                return v == null ? null : v.trim();
            }
            i = j;
        }
        return null;
    }

    /** Title path for the REST URL (spaces become underscores, then encoded). */
    public static String titlePath(String title) {
        if (title == null) return "";
        return urlEncode(title.trim().replace(' ', '_'));
    }

    public static String urlEncode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    /** Strip URLs and collapse whitespace. Does not add a source credit. */
    public static String stripLinks(String text) {
        if (text == null) return "";
        String t = text.replace('\n', ' ').replace('\r', ' ');
        t = t.replaceAll("(?i)https?://\\S+", " ");
        t = t.replaceAll("(?i)www\\.\\S+", " ");
        return t.replaceAll(" +", " ").trim();
    }

    /** First 1–2 sentences, capped so a spoken reply stays short. */
    public static String firstSentences(String text, int maxSentences) {
        if (text == null) return "";
        String t = stripLinks(text);
        if (t.isEmpty() || maxSentences <= 0) return "";
        StringBuilder b = new StringBuilder();
        int taken = 0, i = 0;
        while (i < t.length() && taken < maxSentences) {
            int end = nextSentenceEnd(t, i);
            String piece = t.substring(i, end).trim();
            if (!piece.isEmpty()) {
                if (b.length() > 0) b.append(' ');
                b.append(piece);
                taken++;
            }
            i = end;
            while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        }
        String out = b.toString().trim();
        if (out.length() > 320) {
            out = out.substring(0, 320);
            int sp = out.lastIndexOf(' ');
            if (sp > 180) out = out.substring(0, sp).trim();
            if (!out.endsWith(".")) out = out + ".";
        }
        return out;
    }

    public static String kidTrim(String extract) {
        if (extract == null) return null;
        String t = firstSentences(extract, 2);
        return t.isEmpty() ? null : t;
    }

    /**
     * Network lookup. Returns a short kid-friendly extract, or null on any failure.
     * The caller must run kidsUnsafe and fall back offline. Intended for a background thread.
     */
    public static String fetchSummary(String question) {
        if (question == null || question.trim().isEmpty()) return null;
        try {
            String search = get(SEARCH + urlEncode(question.trim()));
            String title = firstOpenSearchTitle(search);
            if (title == null) return null;
            String body = get(SUMMARY_PREFIX + titlePath(title));
            return kidTrim(extractExtract(body));
        } catch (Exception e) {
            return null;
        }
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null || code >= 400) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 200_000) break;
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static int nextSentenceEnd(String t, int from) {
        for (int i = from; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                if (i + 1 >= t.length() || Character.isWhitespace(t.charAt(i + 1))) return i + 1;
            }
        }
        return t.length();
    }

    static String nextJsonString(String s, int from) {
        int i = s.indexOf('"', from);
        if (i < 0) return null;
        StringBuilder b = new StringBuilder();
        for (int k = i + 1; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\' && k + 1 < s.length()) {
                char n = s.charAt(++k);
                if (n == 'n') b.append('\n');
                else if (n == 't') b.append('\t');
                else if (n == 'r') b.append('\r');
                else if (n == 'u' && k + 4 < s.length()) {
                    try {
                        b.append((char) Integer.parseInt(s.substring(k + 1, k + 5), 16));
                        k += 4;
                    } catch (Exception e) {
                        b.append(n);
                    }
                } else b.append(n);
            } else if (c == '"') return b.toString();
            else b.append(c);
        }
        return null;
    }
}
