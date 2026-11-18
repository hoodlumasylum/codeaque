package com.codeaque.launcher;

/**
 * How Chat should listen: system recognizer, on-device system recognizer,
 * or the built-in sherpa pack. Plain Java so CatalogTest can cover it.
 */
public final class NovaListenPick {
    private NovaListenPick() {}

    /**
     * EXTRA_PREFER_OFFLINE first breaks phones that have a recognizer but no
     * offline language pack (common without Google). Default is off.
     */
    public static boolean preferOfflineFirst() { return false; }

    /**
     * The on-device system recognizer is often "available" once Speech
     * Recognition & Synthesis is installed, even with no English pack. It
     * then starts, hears nothing, and times out. Prefer the default
     * recognizer from that same package first.
     */
    public static boolean preferOnDeviceFirst() { return false; }


    public static String firstLanguage() { return NovaSpeechAvail.LANGUAGE; }

    /** After a client/server/network error on en-GB, try en-US once. */
    public static String nextLanguage(String current, boolean languageWorthyError) {
        if (!languageWorthyError) return null;
        if (NovaSpeechAvail.LANGUAGE.equals(current)) return NovaSpeechAvail.LANGUAGE_US;
        return null;
    }

    public static boolean languageWorthyError(int code) {
        return code == NovaVoiceGate.ERR_CLIENT
                || code == 2 /* ERROR_NETWORK */
                || code == 4 /* ERROR_SERVER */
                || code == 9; /* ERROR_INSUFFICIENT_PERMISSIONS is not language; left out */
    }

    /** 2 = ERROR_NETWORK, 4 = ERROR_SERVER, 1 = ERROR_NETWORK_TIMEOUT. */
    public static boolean networkOrServer(int code) {
        return code == 1 || code == 2 || code == 4 || code == NovaVoiceGate.ERR_CLIENT;
    }

    /**
     * After one empty system result (or a busy/hard error), switch to the
     * built-in pack if it is on the phone. Do not wait for five deaf cycles.
     */
    public static final int EMPTY_BEFORE_BUILTIN = 1;

    public static boolean shouldFallToBuiltin(boolean builtinReady, boolean alreadyBuiltin,
            int emptyCycles, boolean hardSystemError) {
        return shouldFallToBuiltin(builtinReady, alreadyBuiltin, emptyCycles, hardSystemError, false);
    }

    public static boolean shouldFallToBuiltin(boolean builtinReady, boolean alreadyBuiltin,
            int emptyCycles, boolean hardSystemError, boolean micBusy) {
        if (alreadyBuiltin || !builtinReady) return false;
        if (hardSystemError || micBusy) return true;
        return emptyCycles >= EMPTY_BEFORE_BUILTIN;
    }

    public static boolean shouldOfferBuiltin(boolean builtinReady, boolean recOk,
            boolean onDeviceOk, int emptyCycles) {
        if (builtinReady) return false;
        if (!recOk && !onDeviceOk) return true;
        return emptyCycles >= EMPTY_BEFORE_BUILTIN;
    }

    /** Chat should keep the download button on screen whenever the pack is missing. */
    public static boolean showBuiltinButton(boolean builtinReady, boolean downloading) {
        return !builtinReady && !downloading;
    }

    /**
     * After the hidden-listen pause retry, a still-busy system recognizer
     * should give the built-in pack a try when it is downloaded.
     */
    public static boolean switchBusyToBuiltin(boolean builtinReady, boolean alreadyBuiltin,
            boolean alreadyRetried) {
        return builtinReady && !alreadyBuiltin && alreadyRetried;
    }

    /** Pause the hidden listen service once, then retry a busy mic. */
    public static boolean retryBusyAfterPause(boolean alreadyPausedHidden) {
        return !alreadyPausedHidden;
    }

    /**
     * Use the built-in engine now: forced, no system service, or the pack is
     * ready and the caller already decided the system path failed.
     */
    public static boolean useBuiltinNow(boolean forceBuiltin, boolean builtinReady,
            boolean recOk, boolean onDeviceOk) {
        if (!builtinReady) return false;
        if (forceBuiltin) return true;
        return !recOk && !onDeviceOk;
    }
}
