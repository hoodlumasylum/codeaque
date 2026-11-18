package com.codeaque.launcher;

/**
 * Last listen reason and mic level, so Chat and Nova's tab can show a
 * clear diagnosis (permission, no speech service, busy mic, built-in).
 * Plain Java helpers are unit-tested.
 */
public final class NovaListenDiag {
    private NovaListenDiag() {}

    static volatile String reason = "";
    static volatile float level;
    static volatile boolean usingBuiltin;
    static volatile boolean permissionDenied;
    static volatile String lastErrorLine = "";
    static volatile int lastErrorCode;
    public static final String PREF_DEBUG = "novaListenDebug";

    public static void setReason(String r) {
        reason = r == null ? "" : r;
    }

    public static String reason() { return reason; }

    public static void setLevel(float v) {
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        level = v;
    }

    public static float level() { return level; }

    public static void setUsingBuiltin(boolean on) { usingBuiltin = on; }

    public static boolean usingBuiltin() { return usingBuiltin; }

    public static void setPermissionDenied(boolean on) { permissionDenied = on; }

    public static boolean permissionDenied() { return permissionDenied; }

    /**
     * Android SpeechRecognizer onRmsChanged is typically around -2..10 dB.
     * Map a slightly wider window so a quiet room is not a full bar.
     */
    public static float meterFromRmsDb(float rmsdB) {
        float v = (rmsdB + 10f) / 22f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /** PCM RMS in 0..1 (abs/32768). Speech is often 0.01-0.15; scale up. */
    public static float meterFromPcmRms(float rms) {
        if (rms <= 0f) return 0f;
        float v = rms * 8f;
        if (v > 1f) return 1f;
        return v;
    }

    /** Line for Nova's tab and Chat. Never empty when we know a cause. */
    public static String line(String reason, boolean listening, boolean builtin, boolean denied) {
        if (denied) return NovaSpeechAvail.PERMISSION_DENIED;
        if (reason != null && !reason.isEmpty()) return reason;
        if (listening && builtin) return NovaSpeechAvail.USING_BUILTIN;
        if (listening) return "Listening…";
        return "";
    }

    public static String line() {
        return line(reason, false, usingBuiltin, permissionDenied);
    }

    /** Record a recognizer error for logcat and the hidden Chat debug line. */
    public static void setError(int code, long elapsedMs, String extra) {
        lastErrorCode = code;
        String name = NovaListenGate.errorName(code);
        String line = name + " (" + code + ") in " + elapsedMs + " ms";
        if (extra != null && !extra.isEmpty()) line = line + " " + extra;
        lastErrorLine = line;
    }

    public static void setError(int code, long elapsedMs) {
        setError(code, elapsedMs, null);
    }

    public static String lastErrorLine() { return lastErrorLine == null ? "" : lastErrorLine; }

    public static int lastErrorCode() { return lastErrorCode; }
}
