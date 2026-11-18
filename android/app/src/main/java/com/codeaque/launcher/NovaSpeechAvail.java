package com.codeaque.launcher;

/**
 * When in-app SpeechRecognizer can be used, when to fall back to the system
 * speech prompt, and the copy shown if this phone has no recognition service.
 * Plain Java so CatalogTest can check the wording and the decision table.
 */
public final class NovaSpeechAvail {
    private NovaSpeechAvail() {}

    public static final String NO_SERVICE =
            "No speech service on this phone - install Speech Recognition & Synthesis from Google from Play Store";
    public static final String INSTALL_BTN = "Get speech service";
    public static final String TTS_PACKAGE = "com.google.android.tts";
    public static final String LANGUAGE = "en-GB";
    public static final String LANGUAGE_US = "en-US";

    public static final int USE_RECOGNIZER = 1;
    public static final int USE_PROMPT = 2;
    public static final int NEED_INSTALL = 3;

    /**
     * recAvailable is SpeechRecognizer.isRecognitionAvailable.
     * promptAvailable is a resolvable ACTION_RECOGNIZE_SPEECH activity.
     */
    public static int path(boolean recAvailable, boolean promptAvailable) {
        if (recAvailable) return USE_RECOGNIZER;
        if (promptAvailable) return USE_PROMPT;
        return NEED_INSTALL;
    }

    public static String status(int path, boolean listening, String lastError) {
        if (listening) return "Listening…";
        if (lastError != null && !lastError.isEmpty()) return lastError;
        if (path == NEED_INSTALL) return NO_SERVICE;
        if (path == USE_PROMPT) return "Tap Mic, then speak";
        return "";
    }

    public static boolean showInstallButton(int path) {
        return path == NEED_INSTALL;
    }
}
