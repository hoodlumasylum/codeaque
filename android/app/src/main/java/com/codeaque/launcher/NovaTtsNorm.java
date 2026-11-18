package com.codeaque.launcher;

/**
 * Make TTS/Heart see the same words as the screen. Curly quotes and odd
 * apostrophes otherwise split contractions such as "I'm".
 * Plain Java for CatalogTest.
 */
public final class NovaTtsNorm {
    private NovaTtsNorm() {}

    public static String forSpeak(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\u2018': case '\u2019': case '\u201A': case '\u201B':
                case '\u2032': case '\u0060': case '\u00B4':
                    b.append('\'');
                    break;
                case '"':
                case '\u201C': case '\u201D': case '\u201E': case '\u201F':
                case '\u00AB': case '\u00BB':
                    // Drop quotes. Kokoro/espeak was skipping words inside
                    // a quoted span, so the spoken line did not match the bubble.
                    break;
                case '\u2013': case '\u2014': case '\u2212':
                    b.append('-');
                    break;
                case '\u2026':
                    b.append("...");
                    break;
                case '\u00A0':
                    b.append(' ');
                    break;
                default:
                    b.append(c);
            }
        }
        return b.toString();
    }

    /** True when a straight or curly apostrophe is present in a contraction. */
    public static boolean keepsIm(String spoken) {
        if (spoken == null) return false;
        String t = forSpeak(spoken).toLowerCase();
        return t.contains("i'm") || t.contains("i am");
    }
}
