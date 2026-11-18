package com.codeaque.launcher;

/**
 * 0.10.62: hands-free listen stays calm. The Listening chip does not flash
 * between recognizer restarts, empty NO_MATCH / timeout cycles restart quickly
 * and the loop stays on until he taps Mic or leaves Chat. Plain Java so CatalogTest
 * can check it.
 */
public final class NovaListenGate {
    private NovaListenGate() {}

    public static final long RETRY_START_MS = 200L;
    public static final long RETRY_MAX_MS = 400L;
    /** Hands-free loop keeps restarting; this is only a soft hint, not a stop. */
    public static final int EMPTY_CYCLES_MAX = 1000;
    /** Keep the chip visible this long after listening drops, so a restart cannot flash it off. */
    public static final long HIDE_DEBOUNCE_MS = 800L;

    public static final String TAP_MIC = "Tap Mic to talk";

    /** Delay before the next startListening: 300 ms, then 600, 1200, 2000 (capped). */
    public static long retryDelayMs(int emptyCycles) {
        int n = emptyCycles < 0 ? 0 : emptyCycles;
        long d = RETRY_START_MS;
        for (int i = 0; i < n; i++) {
            if (d >= RETRY_MAX_MS) return RETRY_MAX_MS;
            d = d * 2;
            if (d > RETRY_MAX_MS) d = RETRY_MAX_MS;
        }
        return d;
    }

    public static boolean isEmptyCycle(int code) {
        return code == NovaVoiceGate.ERR_NO_MATCH
                || code == NovaVoiceGate.ERR_SPEECH_TIMEOUT
                || code == NovaVoiceGate.ERR_CLIENT;
    }

    public static int nextEmptyCycles(int current, boolean empty) {
        if (!empty) return 0;
        int n = current + 1;
        return n < 0 ? 0 : n;
    }

    public static boolean shouldRetryEmpty(int emptyCycles) {
        // Continuous / hands-free: never stop the loop because of a timeout
        // or no-match. A Mic tap or leaving Chat is what ends listening.
        return true;
    }

    /**
     * The header chip stays on while hands-free mode is armed, including the
     * gap between a timeout and the next startListening. It hides while she
     * is speaking or thinking, and after the loop has given up.
     */
    public static boolean showListeningChip(boolean voiceLoop, boolean expanded, boolean giveUp,
                                           boolean speakingOrThinking, boolean actuallyListening,
                                           boolean betweenRestarts) {
        if (!voiceLoop || !expanded || giveUp) return false;
        if (speakingOrThinking) return false;
        return actuallyListening || betweenRestarts;
    }

    /** Debounce a hide: once shown, stay shown until hideAfter, unless she starts talking. */
    public static boolean chipVisible(boolean wantShown, boolean forceHide, long now, long hideAfter) {
        if (forceHide) return false;
        if (wantShown) return true;
        return now < hideAfter;
    }

    public static long hideAfter(boolean wantShown, long now, long currentHideAfter) {
        if (wantShown) return now + HIDE_DEBOUNCE_MS;
        return currentHideAfter;
    }

    /**
     * Never restart the recognizer while she is speaking, loading a voice,
     * thinking, or after the loop has given up.
     */
    public static boolean canRestart(boolean voiceLoop, boolean expanded, boolean giveUp,
                                    boolean talking, boolean thinking, boolean jobInFlight,
                                    boolean pendingSpeak) {
        if (!voiceLoop || !expanded || giveUp) return false;
        if (talking || thinking || jobInFlight || pendingSpeak) return false;
        return true;
    }

    public static String idleStatus(boolean giveUp, boolean listeningOrBetween) {
        if (giveUp) return TAP_MIC;
        if (listeningOrBetween) return NovaConvo.LISTENING_TEXT;
        return "";
    }
}
