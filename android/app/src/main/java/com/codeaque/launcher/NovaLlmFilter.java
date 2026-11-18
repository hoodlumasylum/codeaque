package com.codeaque.launcher;

/**
 * Kid-safe prompt and output filter for the on-device model. Plain Java so CatalogTest
 * can check it without android.jar. She only ever presents as Nova.
 */
public final class NovaLlmFilter {
    private NovaLlmFilter() {}

    public static final String SYSTEM = NovaKidsTalk.SYSTEM;

    /** Gemma 3 instruction prompt. The model must reply as Nova. */
    public static String prompt(String question) {
        String q = question == null ? "" : question.trim();
        if (q.length() > 400) q = q.substring(0, 400);
        return "<start_of_turn>user\n" + SYSTEM + "\n\nChild: " + q + "<end_of_turn>\n"
                + "<start_of_turn>model\n";
    }

    /**
     * Clean a model reply: trim, drop special tokens, keep one or two sentences,
     * reject unsafe text. Returns null if the reply should not be shown.
     */
    public static String filter(String raw) {
        if (raw == null) return null;
        String t = raw.replace('\r', ' ').trim();
        t = t.replace("<end_of_turn>", " ").replace("<start_of_turn>", " ");
        t = t.replace("<eos>", " ").replace("<bos>", " ");
        t = t.replace('\n', ' ');
        while (t.contains("  ")) t = t.replace("  ", " ");
        t = t.trim();
        if (t.isEmpty()) return null;
        // Drop a leading "Nova:" label if the model added one.
        if (t.regionMatches(true, 0, "Nova:", 0, 5)) t = t.substring(5).trim();
        t = NovaKidsTalk.sanitizeReply(t);
        t = firstSentences(t, 2);
        if (t.isEmpty()) return null;
        if (NovaHelp.kidsUnsafe(t)) return null;
        if (tooLong(t)) t = firstSentences(t, 1);
        t = NovaKidsTalk.sanitizeReply(t);
        if (t.isEmpty() || NovaHelp.kidsUnsafe(t)) return null;
        return t;
    }

    public static String firstSentences(String t, int max) {
        if (t == null) return "";
        String[] parts = NovaTtsVoice.sentences(t);
        if (parts.length == 0) return t.trim();
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int i = 0; i < parts.length && n < max; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(p);
            n++;
        }
        return b.toString().trim();
    }

    static boolean tooLong(String t) {
        if (t.length() > 320) return true;
        int words = 1;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == ' ') words++;
        return words > 60;
    }
}
