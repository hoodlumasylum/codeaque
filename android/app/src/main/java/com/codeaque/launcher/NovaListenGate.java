package com.codeaque.launcher;

/**
 * Hands-free listen stays calm: restart with backoff, stop after repeated
 * instant failures, and debounce the Listening / Mic display so it cannot
 * flicker several times a second. Plain Java so CatalogTest can check it.
 */
public final class NovaListenGate {
    private NovaListenGate() {}

    /** A start that errors in under this many ms is an instant failure. */
    public static final long INSTANT_MS = 500L;
    /** NO_MATCH / SPEECH_TIMEOUT may restart at once only after a real listen this long. */
    public static final long REAL_LISTEN_MS = 1000L;
    /** Stop the loop after this many instant failures in a row. */
    public static final int QUICK_FAIL_STOP = 5;
    /** Hold Listening / Mic and the Your turn pill at least this long. */
    public static final long DISPLAY_DEBOUNCE_MS = 300L;
    /** Wait this long after cancel / error before startListening again. */
    public static final long START_GAP_MS = 80L;

    public static final long RETRY_START_MS = 300L;
    public static final long RETRY_MAX_MS = 4000L;
    public static final int EMPTY_CYCLES_MAX = QUICK_FAIL_STOP;
    public static final long HIDE_DEBOUNCE_MS = 800L;

    public static final String TAP_MIC = "Tap Mic to talk";
    public static final String MIC_BUSY_HINT = "Mic busy, another app may be using it";

    /** SpeechRecognizer error codes (android.speech.SpeechRecognizer). */
    public static final int ERR_NETWORK_TIMEOUT = 1;
    public static final int ERR_NETWORK = 2;
    public static final int ERR_AUDIO = 3;
    public static final int ERR_SERVER = 4;
    public static final int ERR_CLIENT = 5;
    public static final int ERR_SPEECH_TIMEOUT = 6;
    public static final int ERR_NO_MATCH = 7;
    public static final int ERR_RECOGNIZER_BUSY = 8;
    public static final int ERR_INSUFFICIENT_PERMISSIONS = 9;
    public static final int ERR_TOO_MANY_REQUESTS = 10;
    public static final int ERR_SERVER_DISCONNECTED = 11;

    public static String errorName(int code) {
        switch (code) {
            case ERR_NETWORK_TIMEOUT: return "NETWORK_TIMEOUT";
            case ERR_NETWORK: return "NETWORK";
            case ERR_AUDIO: return "AUDIO";
            case ERR_SERVER: return "SERVER";
            case ERR_CLIENT: return "CLIENT";
            case ERR_SPEECH_TIMEOUT: return "SPEECH_TIMEOUT";
            case ERR_NO_MATCH: return "NO_MATCH";
            case ERR_RECOGNIZER_BUSY: return "RECOGNIZER_BUSY";
            case ERR_INSUFFICIENT_PERMISSIONS: return "INSUFFICIENT_PERMISSIONS";
            case ERR_TOO_MANY_REQUESTS: return "TOO_MANY_REQUESTS";
            case ERR_SERVER_DISCONNECTED: return "SERVER_DISCONNECTED";
            default: return "ERR_" + code;
        }
    }

    public static boolean isNoMatchOrTimeout(int code) {
        return code == ERR_NO_MATCH || code == ERR_SPEECH_TIMEOUT
                || code == NovaVoiceGate.ERR_NO_MATCH
                || code == NovaVoiceGate.ERR_SPEECH_TIMEOUT;
    }

    public static boolean isBusyOrAudio(int code) {
        return code == ERR_AUDIO || code == ERR_RECOGNIZER_BUSY
                || code == NovaConvo.ERR_AUDIO || code == NovaConvo.ERR_BUSY
                || code == NovaVoiceGate.ERR_AUDIO
                || code == NovaVoiceGate.ERR_RECOGNIZER_BUSY;
    }

    public static boolean isInstantFailure(long elapsedMs) {
        return elapsedMs >= 0L && elapsedMs < INSTANT_MS;
    }

    public static int nextQuickFails(int current, long elapsedMs) {
        if (isInstantFailure(elapsedMs)) {
            int n = current + 1;
            return n < 0 ? 0 : n;
        }
        return 0;
    }

    /**
     * Delay before the next startListening, or -1 to stop the loop.
     * Immediate quiet restart only for NO_MATCH / SPEECH_TIMEOUT after a
     * real listen of about a second. Instant errors back off 300 ms, then
     * 1 s, 2 s, 4 s, and stop after {@link #QUICK_FAIL_STOP} quick failures.
     */
    public static long restartDelayMs(int code, long elapsedMs, int quickFailsBefore) {
        if (isNoMatchOrTimeout(code) && elapsedMs >= REAL_LISTEN_MS) return 0L;
        if (isInstantFailure(elapsedMs)) {
            int after = nextQuickFails(quickFailsBefore, elapsedMs);
            if (after >= QUICK_FAIL_STOP) return -1L;
            return quickBackoffMs(quickFailsBefore);
        }
        if (isBusyOrAudio(code)) {
            if (quickFailsBefore + 1 >= QUICK_FAIL_STOP) return -1L;
            return quickBackoffMs(quickFailsBefore);
        }
        return RETRY_START_MS;
    }

    public static long quickBackoffMs(int quickFailsBefore) {
        if (quickFailsBefore <= 0) return 300L;
        if (quickFailsBefore == 1) return 1000L;
        if (quickFailsBefore == 2) return 2000L;
        return 4000L;
    }

    public static boolean shouldStop(int code, long elapsedMs, int quickFailsBefore) {
        return restartDelayMs(code, elapsedMs, quickFailsBefore) < 0L;
    }

    public static String stopMessage(int code) {
        if (isBusyOrAudio(code)) return MIC_BUSY_HINT;
        return TAP_MIC;
    }

    /**
     * Older empty-cycle delay: treat each cycle as an instant failure.
     * 300 ms, then 1 s, 2 s, 4 s.
     */
    public static long retryDelayMs(int emptyCycles) {
        int n = emptyCycles < 0 ? 0 : emptyCycles;
        if (n >= QUICK_FAIL_STOP) return RETRY_MAX_MS;
        return quickBackoffMs(n);
    }

    public static boolean isEmptyCycle(int code) {
        return isNoMatchOrTimeout(code)
                || code == NovaVoiceGate.ERR_CLIENT
                || code == ERR_CLIENT
                || isBusyOrAudio(code)
                || code == ERR_TOO_MANY_REQUESTS
                || code == ERR_SERVER_DISCONNECTED
                || code == ERR_SERVER
                || code == ERR_NETWORK
                || code == ERR_NETWORK_TIMEOUT;
    }

    public static int nextEmptyCycles(int current, boolean empty) {
        if (!empty) return 0;
        int n = current + 1;
        return n < 0 ? 0 : n;
    }

    /** Instant-failure path: stop after {@link #QUICK_FAIL_STOP} quick empties. */
    public static boolean shouldRetryEmpty(int emptyCycles) {
        return emptyCycles < QUICK_FAIL_STOP;
    }

    public static boolean shouldRetryEmpty(int code, long elapsedMs, int quickFailsBefore) {
        if (isNoMatchOrTimeout(code) && elapsedMs >= REAL_LISTEN_MS) return true;
        return !shouldStop(code, elapsedMs, quickFailsBefore);
    }

    /**
     * Hold the last shown listening state for {@link #DISPLAY_DEBOUNCE_MS}
     * so the Mic button and Your turn pill do not flash several times a second.
     */
    public static boolean displayListening(boolean wantListening, boolean lastShown,
                                          long now, long lastChangeAt) {
        if (wantListening == lastShown) return lastShown;
        if (lastChangeAt <= 0L) return wantListening;
        if (now - lastChangeAt >= DISPLAY_DEBOUNCE_MS) return wantListening;
        return lastShown;
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
