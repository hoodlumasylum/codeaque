package com.codeaque.launcher;

/**
 * Official speaker IDs for csukuangfj/kokoro-multi-lang-v1_0 (54 voices, 0-53).
 * af_heart is 3; zf_* Chinese voices start at 45. v1.1 uses a different table
 * and must not be mixed with this model's voices.bin.
 */
public final class NovaKokoroSpeakers {
    private NovaKokoroSpeakers() {}

    /** Heart, Nova's default on-device voice. */
    public static final int AF_HEART = 3;
    public static final int AF_BELLA = 2;
    public static final int BF_EMMA = 21;

    public static final int COUNT = 54;
    public static final int SAMPLE_RATE = 24000;
    public static final String LANG = "en-us";

    public static int idForVoice(String voiceId) {
        if (NovaVoices.ID_KOKORO_BELLA.equals(voiceId)) return AF_BELLA;
        if (NovaVoices.ID_KOKORO_EMMA.equals(voiceId)) return BF_EMMA;
        return AF_HEART;
    }

    /** Clamp a sid into this model's range; unknown values fall back to Heart, never a zf_* id. */
    public static int safeSid(int sid) {
        if (sid >= 0 && sid < COUNT) return sid;
        return AF_HEART;
    }

    public static boolean isEnglishSpeaker(int sid) {
        // af 0-10, am 11-19, bf 20-23, bm 24-27
        return sid >= 0 && sid <= 27;
    }
}
