package com.codeaque.launcher;

/**
 * Canned-intent matching: whole words or phrases only, with a simple
 * confidence gate so "eight times eight" does not look like "time is up".
 */
public final class NovaIntent {
    private NovaIntent() {}

    public static String norm(String s) {
        return NovaHelp.norm(s);
    }

    /** True if key appears as a whole word or whole phrase in q (already normed or not). */
    public static boolean containsPhrase(String q, String key) {
        if (q == null || key == null) return false;
        String n = norm(q);
        String k = norm(key);
        if (n.isEmpty() || k.isEmpty()) return false;
        int from = 0;
        while (from <= n.length()) {
            int i = n.indexOf(k, from);
            if (i < 0) return false;
            boolean left = i == 0 || n.charAt(i - 1) == ' ';
            int end = i + k.length();
            boolean right = end == n.length() || n.charAt(end) == ' ';
            if (left && right) return true;
            from = i + 1;
        }
        return false;
    }

    public static int wordCount(String q) {
        String n = norm(q);
        if (n.isEmpty()) return 0;
        int nWords = 1;
        for (int i = 0; i < n.length(); i++) if (n.charAt(i) == ' ') nWords++;
        return nWords;
    }

    /**
     * A short weak word (time, limit, game, find, …) is not enough when the
     * question clearly has other content. Longer phrases pass.
     */
    public static boolean confident(String q, String... keys) {
        if (keys == null) return false;
        String n = norm(q);
        String best = null;
        int bestLen = 0;
        for (int i = 0; i < keys.length; i++) {
            String k = norm(keys[i]);
            if (k.isEmpty()) continue;
            if (containsPhrase(n, k) && k.length() > bestLen) {
                best = k;
                bestLen = k.length();
            }
        }
        if (best == null) return false;
        int qWords = wordCount(n);
        int kWords = wordCount(best);
        if (bestLen <= 5 && qWords > kWords + 1) return false;
        return true;
    }
}
