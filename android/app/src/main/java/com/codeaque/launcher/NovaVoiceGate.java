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
        // engineLoaded is kept for callers that want to know if it already started.
        // Trying Heart only needs the pack on disk; the speak job loads the engine.
        return NovaVoiceStatus.shouldTryOnDevice(filesReady, voiceId);
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

    /** SpeechRecognizer error codes (android.speech.SpeechRecognizer). */
    public static final int ERR_AUDIO = 3;
    public static final int ERR_CLIENT = 5;
    public static final int ERR_SPEECH_TIMEOUT = 6;
    public static final int ERR_NO_MATCH = 7;
    public static final int ERR_RECOGNIZER_BUSY = 8;

    /** Recreate the recognizer and keep the hands-free loop going. Mic-busy is not retried. */
    public static boolean shouldRecycleRecognizer(int code) {
        if (NovaConvo.isMicBusy(code)) return false;
        return code == ERR_CLIENT || code == ERR_SPEECH_TIMEOUT || code == ERR_NO_MATCH;
    }
}
