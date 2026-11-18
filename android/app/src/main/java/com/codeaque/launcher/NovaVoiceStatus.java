package com.codeaque.launcher;

/**
 * 0.10.58: voice status lines, test-result labels, and the speed slider range.
 * Plain Java so CatalogTest can check it.
 */
public final class NovaVoiceStatus {
    private NovaVoiceStatus() {}

    public static final String PREF_SPEED = "novaVoiceSpeed";
    public static final String PHONE = "Using phone voice";
    public static final String HEART_READY = "Heart ready";
    /** Kokoro default: a little slower than real-time. */
    public static final float DEFAULT_KOKORO = 0.85f;
    /** System TTS default when the slider has not been moved. */
    public static final float DEFAULT_SYSTEM = 0.90f;
    public static final float SPEED_MIN = 0.60f;
    public static final float SPEED_MAX = 1.20f;

    public static String downloading(int percent) {
        int p = percent < 0 ? 0 : (percent > 100 ? 100 : percent);
        return "Downloading " + p + "%";
    }

    public static String failed(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) r = "the engine did not start";
        return "Heart failed: " + r;
    }

    /**
     * Status shown in Nova's tab. filesReady means the pack is on disk;
     * loaded means the engine actually started. A load error is shown even
     * if the files are present.
     */
    public static String of(boolean filesReady, boolean loaded, boolean downloading, int percent, String lastError) {
        if (downloading) return downloading(percent);
        if (loaded) return HEART_READY;
        if (filesReady && lastError != null && lastError.length() > 0) return failed(lastError);
        if (filesReady) return HEART_READY;
        return PHONE;
    }

    public static String testResult(boolean usedHeart) {
        return usedHeart ? "Spoke with Heart" : "Spoke with the phone's voice";
    }

    public static float clampSpeed(float v) {
        if (v < SPEED_MIN) return SPEED_MIN;
        if (v > SPEED_MAX) return SPEED_MAX;
        return v;
    }

    public static float kokoroSpeed(Float pref) {
        return pref == null ? DEFAULT_KOKORO : clampSpeed(pref);
    }

    public static float systemRate(Float pref) {
        return pref == null ? DEFAULT_SYSTEM : clampSpeed(pref);
    }

    /** SeekBar 0..60 maps to 0.60..1.20. Default 0.85 is progress 25. */
    public static int speedToProgress(float speed) {
        return Math.round((clampSpeed(speed) - SPEED_MIN) * 100f);
    }

    public static float progressToSpeed(int progress) {
        if (progress < 0) progress = 0;
        if (progress > 60) progress = 60;
        return clampSpeed(SPEED_MIN + progress / 100f);
    }

    /**
     * Try the on-device voice whenever the pack is on disk and a Kokoro
     * voice is selected. Do not require the engine to be loaded already —
     * the speak job loads it (and falls back if that fails).
     */
    public static boolean shouldTryOnDevice(boolean filesReady, String voiceId) {
        if (!filesReady) return false;
        return voiceId != null && voiceId.startsWith("kokoro-");
    }
}
