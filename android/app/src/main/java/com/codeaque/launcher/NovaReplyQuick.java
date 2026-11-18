package com.codeaque.launcher;

/**
 * Instant canned greetings, short replies, and listen-window rules.
 * Plain Java so CatalogTest can cover them.
 */
public final class NovaReplyQuick {
    private NovaReplyQuick() {}

    /** Listen up to this long when no speech is heard, then stop or switch engine. */
    public static final long LISTEN_WINDOW_MS = 15000L;
    /** After speech, wait this long for end-of-speech (trailing silence). */
    public static final long SPEECH_TAIL_MS = 1800L;
    /** Hard cap once the user has started speaking. */
    public static final long LISTEN_MAX_MS = 15000L;
    /** On-device LLM cap — short spoken answers, not essays. */
    public static final int MAX_TOKENS = 40;
    /** Use the brain only if it answers within this budget, then fall back. */
    public static final long BRAIN_BUDGET_MS = 2500L;

    public static final String SPEAKING_TEXT = "Speaking…";
    public static final String NO_SOUND =
            "Mic is getting no sound. Another app may be using it";
    public static final String MISSED_TEXT = "Sorry, I missed that. Say it again?";
    public static final String MISSED_AGAIN = "I still didn't catch that. Please say it once more.";
    public static final int SPEAK_AS_ONE_CHARS = 180;

    public static final String HELLO_KIDS = "Hi! I'm happy to help. What would you like to do?";
    public static final String HELLO_ADULT = "Hi! How can I help?";

    public static boolean isSimpleGreeting(String q) {
        return NovaSmallTalk.isBareGreeting(q);
    }

    public static String cannedHello(boolean kids) {
        return kids ? NovaSmallTalk.HELLO_KIDS : NovaSmallTalk.HELLO_ADULT;
    }

    /** "Thinking… 3s" — elapsed seconds, never empty. */
    public static String thinkingText(long elapsedMs) {
        long s = elapsedMs / 1000L;
        if (s < 0) s = 0;
        return NovaConvo.THINKING_TEXT + " " + s + "s";
    }

    /** Short replies are synthesized in one go so Heart does not pause between sentences. */
    public static boolean speakAsOne(String text) {
        if (text == null) return true;
        String t = text.trim();
        if (t.length() <= SPEAK_AS_ONE_CHARS) return true;
        return afterFirst(t).isEmpty();
    }

    /** Open questions may use the on-device brain; chit-chat and short/unclear lines must not. */
    public static boolean looksOpenQuestion(String q) {
        if (q == null) return false;
        String n = NovaSmallTalk.tidy(q);
        if (n.isEmpty() || isUnclear(n)) return false;
        if (n.equals("how are you") || n.startsWith("how are you ")) return false;
        if (n.startsWith("what") || n.startsWith("why") || n.startsWith("how")
                || n.startsWith("who") || n.startsWith("where") || n.startsWith("when")
                || n.startsWith("which") || n.startsWith("tell me") || n.startsWith("explain")
                || n.startsWith("can you") || n.startsWith("could you")
                || n.startsWith("do you know") || n.startsWith("whats") || n.startsWith("who's"))
            return true;
        return n.length() >= 24;
    }

    public static boolean isUnclear(String q) {
        String n = q == null ? "" : NovaSmallTalk.tidy(q);
        if (n.isEmpty()) return true;
        if (n.length() <= 2) return true;
        if (n.equals("you") || n.equals("the") || n.equals("and") || n.equals("a")
                || n.equals("uh") || n.equals("um") || n.equals("u") || n.equals("ah")
                || n.equals("eh") || n.equals("mm") || n.equals("hmm") || n.equals("er"))
            return true;
        return false;
    }

    public static String firstSentence(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        int cut = -1;
        boolean inQuote = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' || c == '\u201C' || c == '\u201D') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && (c == '.' || c == '!' || c == '?')) {
                cut = i + 1;
                break;
            }
        }
        if (cut <= 0 || cut >= t.length()) return t;
        return t.substring(0, cut).trim();
    }

    public static String afterFirst(String text) {
        String first = firstSentence(text);
        if (text == null) return "";
        String t = text.trim();
        if (first.isEmpty() || first.equals(t)) return "";
        String rest = t.substring(first.length()).trim();
        return rest;
    }

    /** PCM RMS below this (0..1 float) counts as silence. */
    public static final float SILENCE_RMS = 0.004f;

    public static boolean isSilentRms(float rms) {
        return rms < SILENCE_RMS;
    }

    /** Peak absolute PCM in 16-bit counts. All-zeros (or near) for the whole window. */
    public static boolean allZeros(int peakAbs, long frames) {
        return allZeros(peakAbs, frames, false);
    }

    /** Only when the capture never had a voice-like peak. A moving meter means this must be false. */
    public static boolean allZeros(int peakAbs, long frames, boolean hadVoice) {
        if (hadVoice) return false;
        if (frames <= 0) return false;
        return peakAbs < 8;
    }
}
