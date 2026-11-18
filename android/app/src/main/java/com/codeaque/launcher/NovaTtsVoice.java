package com.codeaque.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.10.54: pick a natural TTS voice and split replies so short pauses sit between
 * sentences. Plain Java so CatalogTest can check the scoring and the splitter.
 */
public final class NovaTtsVoice {
    private NovaTtsVoice() {}

    public static final float PITCH = 1.08f;
    public static final float RATE = 0.92f;
    public static final int PAUSE_MS = 280;

    /**
     * Higher is better. Non-English returns a negative score (skip).
     * Prefers on-device en-GB, then en-US, then other English; female-sounding names;
     * higher quality. Network voices are allowed but ranked below offline packs.
     */
    public static int score(String language, String country, String name, int quality,
                            boolean networkRequired) {
        if (language == null || !language.equalsIgnoreCase("en")) return -1;
        int s = quality;
        if (country != null) {
            String c = country.toUpperCase();
            if (c.equals("GB") || c.equals("UK")) s += 200;
            else if (c.equals("US")) s += 150;
            else s += 100;
        } else s += 80;
        String n = name == null ? "" : name.toLowerCase();
        boolean female = n.contains("female") || n.contains("woman") || n.contains("girl")
                || n.contains("fiona") || n.contains("karen") || n.contains("samantha")
                || n.contains("serena") || n.contains("zira") || n.contains("aria")
                || n.contains("salli") || n.contains("joanna") || n.contains("ivy")
                || n.contains("amy") || n.contains("emma") || n.contains("gbr-1")
                || n.contains("#female") || n.contains("-f-");
        boolean male = (n.contains("male") && !n.contains("female"))
                || n.contains("man") || n.contains("daniel") || n.contains("brian")
                || n.contains("arthur") || n.contains("steve");
        if (female) s += 80;
        else if (male) s -= 40;
        if (networkRequired) s -= 300;
        return s;
    }

    /** Split on sentence-ending punctuation followed by a space or the end. Keeps the mark. */
    public static String[] sentences(String text) {
        if (text == null) return new String[0];
        String t = text.trim();
        if (t.isEmpty()) return new String[0];
        List<String> out = new ArrayList<String>();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            b.append(c);
            if (c == '.' || c == '!' || c == '?') {
                boolean end = i + 1 >= t.length() || Character.isWhitespace(t.charAt(i + 1));
                // Don't split 0.10 or e.g. abbreviations of one letter ("A. Open").
                boolean digits = i > 0 && Character.isDigit(t.charAt(i - 1))
                        && i + 1 < t.length() && Character.isDigit(t.charAt(i + 1));
                if (end && !digits) {
                    String piece = b.toString().trim();
                    if (!piece.isEmpty()) out.add(piece);
                    b.setLength(0);
                    while (i + 1 < t.length() && Character.isWhitespace(t.charAt(i + 1))) i++;
                }
            }
        }
        String tail = b.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        if (out.isEmpty()) out.add(t);
        return out.toArray(new String[0]);
    }

    /** Spoken duration estimate at RATE, plus a pause after each sentence but the last. */
    public static int estimateMs(String s) {
        int words = 1;
        int pauses = 0;
        if (s != null) {
            String t = s.trim();
            if (!t.isEmpty()) {
                words = 1;
                for (int i = 0; i < t.length(); i++) if (t.charAt(i) == ' ') words++;
            }
            String[] parts = sentences(s);
            pauses = Math.max(0, parts.length - 1);
        }
        // ~420 ms/word at 0.92 rate, plus padding so the duration fallback outlives a slow engine.
        return 500 + words * 420 + pauses * PAUSE_MS + 800;
    }
}
