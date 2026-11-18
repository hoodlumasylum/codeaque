package com.codeaque.launcher;

/**
 * Rules for the Chat Mic button. A user tap must always toggle listening;
 * speaking, a Heart job, or a hangover must not swallow it. Plain Java for CatalogTest.
 */
public final class NovaMicTap {
    private NovaMicTap() {}

    public static final String FAIL_FALLBACK = "Couldn't start the mic. Trying built-in…";
    public static final String FAIL_RETRY = "Couldn't start the mic. Try again, or type it.";

    public static boolean isToggleOff(boolean alreadyListening) {
        return alreadyListening;
    }

    /**
     * While she is speaking or thinking, a Mic tap must start a fresh listen,
     * not toggle the recognizer off (which would leave the last prompt in play).
     */
    public static boolean restartInsteadOfToggle(boolean alreadyListening,
            boolean talking, boolean thinking, boolean jobInFlight, boolean pendingSpeak) {
        if (talking || thinking || jobInFlight || pendingSpeak) return true;
        return false;
    }

    /** A user tap cancels speech first so startListen is not skipped. */
    public static boolean cancelSpeechFirst(boolean talking, boolean thinking,
            boolean jobInFlight, boolean pendingSpeak, boolean inHangover) {
        return talking || thinking || jobInFlight || pendingSpeak || inHangover;
    }

    /**
     * Automatic restarts still wait until she has finished speaking.
     * A direct Mic tap is allowed as soon as Chat is open.
     */
    public static boolean allowStart(boolean fromUser, boolean expanded, boolean voiceLoop,
            boolean giveUp, boolean talking, boolean thinking, boolean jobInFlight,
            boolean pendingSpeak, boolean inHangover) {
        if (!expanded) return false;
        if (fromUser) return voiceLoop && !giveUp;
        return NovaListenGate.canRestart(voiceLoop, expanded, giveUp, talking, thinking,
                jobInFlight, pendingSpeak) && !inHangover;
    }

    public static boolean fallbackBuiltin(boolean started, boolean builtinReady) {
        return !started && builtinReady;
    }

    public static String buttonLabel(boolean listening) {
        return listening ? "Listening" : "Mic";
    }

    public static String startedStatus() {
        return NovaConvo.LISTENING_TEXT;
    }
}
