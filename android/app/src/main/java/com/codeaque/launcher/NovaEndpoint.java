package com.codeaque.launcher;

/**
 * Adaptive end-of-speech: stop about 1.8 s after the last real speech
 * (a little longer for a single short word), using a noise floor learned
 * from the room, not a fixed silence gate.
 * Plain Java for CatalogTest.
 */
public final class NovaEndpoint {
    public static final int CONTINUE = 0;
    public static final int END_SILENCE = 1;
    public static final int END_EMPTY = 2;
    public static final int END_MAX = 3;

    public static final long TAIL_MS = 1800L;
    /** A single short word such as "you" must not end the turn after a brief pause. */
    public static final long SHORT_TAIL_MS = 2200L;
    public static final long MIN_UTTERANCE_MS = 800L;
    public static final long NO_SPEECH_MS = NovaReplyQuick.LISTEN_WINDOW_MS;
    public static final long MAX_MS = NovaReplyQuick.LISTEN_MAX_MS;
    public static final long WATCH_MS = 200L;
    public static final long RESULTS_WAIT_MS = 400L;
    /** Do not raise the "another app is using the mic" warning before this. */
    public static final long SILENT_PROOF_MS = 1500L;

    float floor = 0.04f;
    float peak = 0.04f;
    int floorN;
    boolean heard;
    long startAt;
    long lastSpeechAt;
    String lastPartial = "";
    float lastLevel;

    public void start(long now) {
        floor = 0.04f;
        peak = 0.04f;
        floorN = 0;
        heard = false;
        startAt = now;
        lastSpeechAt = 0;
        lastPartial = "";
        lastLevel = 0f;
    }

    public void onLevel(float level, long now) {
        if (level < 0f) level = 0f;
        lastLevel = level;
        boolean speech = isSpeech(level);
        // Learn the floor from pre-speech (and quiet gaps), so room noise
        // is not treated as ongoing voice.
        if (!speech) {
            if (floorN < 10) {
                floor = (floor * floorN + level) / (floorN + 1);
                floorN++;
            } else if (level < floor * 1.8f) {
                floor = floor * 0.85f + level * 0.15f;
            }
            if (floor < 0.004f) floor = 0.004f;
        }
        if (level > peak) peak = level;
        if (speech) {
            heard = true;
            lastSpeechAt = now;
        }
    }

    public void onPartial(String text, long now) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        lastPartial = t;
        heard = true;
        lastSpeechAt = now;
    }

    public boolean isSpeech(float level) {
        float thresh = Math.max(floor + 0.05f, floor * 2.4f);
        return level >= thresh;
    }

    /** 0 at the room floor, 1 at a comfortable voice peak, so the bar moves. */
    public float meter(float level) {
        if (level < 0f) level = 0f;
        float lo = floor;
        float hi = Math.max(Math.max(peak, lo + 0.12f), lo * 3.5f);
        if (hi <= lo + 0.02f) hi = lo + 0.02f;
        float v = (level - lo) / (hi - lo);
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public int decision(long now) {
        if (heard && lastSpeechAt > 0) {
            long tail = shortUtterance(lastPartial) ? SHORT_TAIL_MS : TAIL_MS;
            if (now - startAt < MIN_UTTERANCE_MS) return CONTINUE;
            if (now - lastSpeechAt >= tail) return END_SILENCE;
        }
        if (!heard && now - startAt >= NO_SPEECH_MS) return END_EMPTY;
        if (now - startAt >= MAX_MS) return END_SILENCE;
        return CONTINUE;
    }

    /** One or two very short tokens, e.g. "u", "you", "ok". */
    public static boolean shortUtterance(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        if (t.isEmpty()) return false;
        int words = 1;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == ' ') words++;
        return words <= 2 && t.length() <= 16;
    }

    public boolean heardSpeech() { return heard; }
    public String lastPartial() { return lastPartial == null ? "" : lastPartial; }
    public float floor() { return floor; }
    public float peak() { return peak; }

    /**
     * True only when the capture stayed at the noise floor for a real window
     * and no speech (or partial text) was seen. A screen recorder alone is
     * not enough if the mic still has a moving level.
     */
    public boolean reallySilent(long now) {
        if (heard) return false;
        if (now - startAt < SILENT_PROOF_MS) return false;
        if (peak > floor + 0.06f && peak > 0.05f) return false;
        return lastLevel <= floor * 1.3f || lastLevel < 0.02f;
    }
}
