package com.codeaque.launcher;

/**
 * Catalog of optional on-device voices for Nova.
 * 0.10.56: default once downloaded is Kokoro Heart (af_heart).
 * Present it only as Nova's voice.
 * Until a grown-up downloads the model, fall back to system TTS.
 */
public final class NovaVoices {
    private NovaVoices() {}

    /** Default on-device voice once the Kokoro model is downloaded. */
    public static final String DEFAULT_VOICE_ID = "kokoro-af_heart";

    public static final String HELLO_LINE = "Hi, I'm Nova. It's good to see you.";

    public static final String ID_KOKORO_HEART = "kokoro-af_heart";
    public static final String ID_KOKORO_BELLA = "kokoro-af_bella";
    public static final String ID_KOKORO_EMMA = "kokoro-bf_emma";
    public static final String ID_PIPER_KRISTIN = "piper-en_US-kristin-medium";
    public static final String ID_PIPER_ALBA = "piper-en_GB-alba-medium";
    public static final String ID_PIPER_CORI = "piper-en_GB-cori-high";

    public static boolean hasLockedDefault() {
        return DEFAULT_VOICE_ID != null && DEFAULT_VOICE_ID.length() > 0;
    }
}
