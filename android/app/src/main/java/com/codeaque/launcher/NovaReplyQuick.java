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
    public static final long SPEECH_TAIL_MS = 1200L;
    /** Hard cap once the user has started speaking. */
    public static final long LISTEN_MAX_MS = 15000L;
    /** On-device LLM cap — short spoken answers, not essays. */
    public static final int MAX_TOKENS = 40;

    public static final String SPEAKING_TEXT = "Speaking…";
    public static final String NO_SOUND =
            "Mic is getting no sound. Another app may be using it";

    public static final String HELLO_KIDS = "Hi! How can I help you today?";
    public static final String HELLO_ADULT = "Hi! How can I help?";

    public static boolean isSimpleGreeting(String q) {
        if (q == null) return false;
        String n = NovaSpeakGate.norm(q);
        if (n.isEmpty()) return false;
        if (n.equals("hello") || n.equals("hello hello") || n.equals("hellohello")
                || n.equals("hi") || n.equals("hi hi") || n.equals("hey")
                || n.equals("hello there") || n.equals("hi there")
                || n.equals("hey nova") || n.equals("hi nova") || n.equals("hello nova")
                || n.equals("good morning") || n.equals("good afternoon")
                || n.equals("good evening") || n.equals("yo")) return true;
        return n.startsWith("hello hello") || n.startsWith("hi hi");
    }

    public static String cannedHello(boolean kids) {
        return kids ? HELLO_KIDS : HELLO_ADULT;
    }

    /** "Thinking… 3s" — elapsed seconds, never empty. */
    public static String thinkingText(long elapsedMs) {
        long s = elapsedMs / 1000L;
        if (s < 0) s = 0;
        return NovaConvo.THINKING_TEXT + " " + s + "s";
    }

    public static String firstSentence(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        int cut = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
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
