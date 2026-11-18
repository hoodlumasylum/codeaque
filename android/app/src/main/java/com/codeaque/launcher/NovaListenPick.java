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

    public static boolean shouldFallToBuiltin(boolean builtinReady, boolean alreadyBuiltin,
            int emptyCycles, boolean hardSystemError) {
        if (alreadyBuiltin || !builtinReady) return false;
        if (hardSystemError) return true;
        return emptyCycles >= NovaListenGate.EMPTY_CYCLES_MAX;
    }

    public static boolean shouldOfferBuiltin(boolean builtinReady, boolean recOk,
            boolean onDeviceOk, int emptyCycles) {
        if (builtinReady) return false;
        if (!recOk && !onDeviceOk) return true;
        return emptyCycles >= NovaListenGate.EMPTY_CYCLES_MAX;
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
