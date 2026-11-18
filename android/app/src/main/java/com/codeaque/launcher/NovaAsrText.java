package com.codeaque.launcher;

/**
 * Tidies built-in and system recognizer text. The small zipformer pack often
 * emits uppercase tokens like "S EIGHT TIMES EH".
 */
public final class NovaAsrText {
    private NovaAsrText() {}

    public static String tidy(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        t = t.replace('\n', ' ').replace('\t', ' ');
        StringBuilder b = new StringBuilder(t.length());
        boolean space = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 'A' && c <= 'Z') c = (char) (c + 32);
            if (c == ' ' || c == '\u00a0') {
                if (b.length() > 0 && !space) { b.append(' '); space = true; }
            } else {
                b.append(c);
                space = false;
            }
        }
        int n = b.length();
        if (n > 0 && b.charAt(n - 1) == ' ') b.setLength(n - 1);
        return fixCommonNumberSlips(b.toString());
    }

    /** Tidy plus drop a stray leading "s" / clipped token for display and matching. */
    public static String cleanHeardLive(String raw) {
        return NovaGameTalk.stripLeadingNoise(tidy(raw));
    }

    /** Cheap repairs next to arithmetic words, e.g. trailing "eh" for "eight". */
    static String fixCommonNumberSlips(String t) {
        if (t == null || t.isEmpty()) return "";
        String[] w = t.split(" ");
        for (int i = 0; i < w.length; i++) {
            if (isOp(w[i])) {
                if (i + 1 < w.length) w[i + 1] = slipNumber(w[i + 1]);
                if (i > 0) w[i - 1] = slipNumber(w[i - 1]);
            }
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            if (i > 0) b.append(' ');
            b.append(w[i]);
        }
        return b.toString();
    }

    static boolean isOp(String w) {
        return "times".equals(w) || "time".equals(w) || "plus".equals(w) || "minus".equals(w)
                || "x".equals(w) || "+".equals(w) || "-".equals(w)
                || "multiplied".equals(w) || "divided".equals(w);
    }

    static String slipNumber(String w) {
        if (w == null) return "";
        if (w.equals("eh") || w.equals("ate") || w.equals("ait")) return "eight";
        if (w.equals("for") ) return w; // do not rewrite "for" globally
        if (w.equals("to") || w.equals("too")) return "two";
        if (w.equals("won")) return "one";
        if (w.equals("tree")) return "three";
        if (w.equals("sicks") || w.equals("sax")) return "six";
        if (w.equals("niner")) return "nine";
        if (w.equals("sever") || w.equals("sevin") || w.equals("sevem")
                || w.equals("sevn") || w.equals("siven")) return "seven";
        if (w.equals("time")) return "times";
        return w;
    }
}
