package com.codeaque.launcher;

/**
 * When to use the on-device voice vs system TTS, and when the hands-free
 * loop may start the mic. Plain Java so CatalogTest can check it.
 */
public final class NovaVoiceGate {
    private NovaVoiceGate() {}

    /**
     * Only use Kokoro once the pack is on disk AND the native engine has
     * actually loaded. Missing files or a load failure must fall back to
     * the phone's TextToSpeech so she still speaks out of the box.
     */
    public static boolean useOnDevice(boolean filesReady, boolean engineLoaded, String voiceId) {
        if (!filesReady || !engineLoaded) return false;
        return voiceId != null && voiceId.startsWith("kokoro-");
    }

    /**
     * Do not start the mic while she is still speaking (or about to).
     * Starting SpeechRecognizer mid-utterance ducks or stops TTS on many phones
     * and cuts the mouth animation short.
     */
    public static boolean canStartListen(boolean voiceLoop, boolean expanded, boolean talking,
                                         boolean pendingSpeak, boolean muted) {
        if (!voiceLoop || !expanded) return false;
        if (talking || pendingSpeak) return false;
        if (muted) return true;
        return true;
    }
}
