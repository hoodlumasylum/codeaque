package com.codeaque.launcher;

/**
 * 0.10.63: how to mute the recognizer start cue without putting the phone
 * into vibrate mode. Plain Java so CatalogTest can cover the rules.
 *
 * Setting STREAM_NOTIFICATION (or STREAM_RING) volume to 0 makes many
 * Android builds switch the ringer to {@code RINGER_MODE_VIBRATE}, and the
 * start cue then vibrates instead of beeping.
 */
public final class NovaListenBeepGate {
    private NovaListenBeepGate() {}

    /** Matches {@link android.media.AudioManager#RINGER_MODE_SILENT}. */
    public static final int MODE_SILENT = 0;
    /** Matches {@link android.media.AudioManager#RINGER_MODE_VIBRATE}. */
    public static final int MODE_VIBRATE = 1;
    /** Matches {@link android.media.AudioManager#RINGER_MODE_NORMAL}. */
    public static final int MODE_NORMAL = 2;

    /** Never duck by writing volume 0 on notification/system/ring streams. */
    public static boolean allowZeroVolumeDuck() { return false; }

    /** The only quiet ringer mode we will set. Never vibrate. */
    public static int modeForDuck() { return MODE_SILENT; }

    /**
     * If a mute attempt left the ringer in vibrate (and it was not already
     * vibrate), the caller must restore the previous mode at once.
     */
    public static boolean mustRevert(int previous, int now) {
        return now == MODE_VIBRATE && previous != MODE_VIBRATE;
    }

    /** Accept the duck only when we actually reached silent. */
    public static boolean duckSucceeded(int now) {
        return now == MODE_SILENT;
    }
}
